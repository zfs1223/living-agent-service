/**
 * 技能执行引擎
 *
 * 职责：
 * 1. 接收服务器转发的技能执行请求
 * 2. 根据技能ID查找对应的本地脚本
 * 3. 通过 Python/Node 子进程执行技能脚本
 * 4. 遵循 JSON 行协议通信（stdin/stdout）
 * 5. 执行结果回传给服务器
 *
 * 架构模式：复用闭环6（win-automation）的双层架构
 * - 服务器侧：LLM推理决策 → WebSocket转发
 * - 桌面端侧：本地执行脚本 → 结果回传
 *
 * 安全约束：
 * - 只执行 personalSafe=true 的技能（后端已过滤）
 * - 读写限制在 workspaceDir（用户数据目录）
 * - 不触碰服务器数据库/API
 */
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { app } from 'electron';
import { existsSync, mkdirSync } from 'fs';

interface SkillExecuteRequest {
  id: number;
  skillId: string;
  args: Record<string, unknown>;
  workspaceDir?: string;
}

interface SkillExecuteResponse {
  id: number;
  success: boolean;
  result?: unknown;
  error?: string;
}

class SkillExecutorService {
  private pendingRequests = new Map<number, {
    resolve: (value: unknown) => void;
    reject: (reason: Error) => void;
    timer: NodeJS.Timeout;
  }>();
  private requestIdCounter = 0;
  private activeProcesses = new Map<number, ChildProcess>();

  /**
   * 执行技能脚本
   *
   * 技能脚本位置：
   * - 打包环境：process.resourcesPath/skills/{skillId}/execute.py
   * - 开发环境：resources/skills/{skillId}/execute.py
   *
   * 如果技能脚本不存在，回退到 win-automation service.py 的通用操作
   *
   * @param skillId      技能ID（如 "web-search", "weather-query"）
   * @param args         技能参数
   * @param workspaceDir 隔离工作区目录
   * @param timeoutMs    超时毫秒，默认60s（技能执行可能更耗时）
   */
  async execute(
    skillId: string,
    args: Record<string, unknown> = {},
    workspaceDir?: string,
    timeoutMs = 60_000
  ): Promise<unknown> {
    const id = ++this.requestIdCounter;

    // 确保工作区目录存在
    if (workspaceDir) {
      try {
        mkdirSync(workspaceDir, { recursive: true });
      } catch {
        // 目录可能已存在
      }
    }

    // 查找技能脚本路径
    const scriptPath = this.resolveSkillScript(skillId);

    if (scriptPath && existsSync(scriptPath)) {
      // 技能脚本存在：启动独立子进程执行
      return this.executeSkillScript(id, scriptPath, skillId, args, workspaceDir, timeoutMs);
    } else {
      // 技能脚本不存在：回退到 win-automation service.py 通用执行
      // 将技能ID作为 operation 传递给 service.py
      return this.executeViaWinAutomation(id, skillId, args, timeoutMs);
    }
  }

  /**
   * 解析技能脚本路径
   * 开发环境：项目 resources/skills/{skillId}/execute.py
   * 打包环境：process.resourcesPath/skills/{skillId}/execute.py
   */
  private resolveSkillScript(skillId: string): string | null {
    const isPackaged = app.isPackaged;
    const skillsRoot = isPackaged
      ? path.join(process.resourcesPath, 'skills')
      : path.join(__dirname, '..', '..', 'resources', 'skills');

    return path.join(skillsRoot, skillId, 'execute.py');
  }

  /**
   * 通过独立子进程执行技能脚本
   * 每个技能脚本启动独立子进程，执行完毕后退出，避免状态污染
   */
  private executeSkillScript(
    id: number,
    scriptPath: string,
    skillId: string,
    args: Record<string, unknown>,
    workspaceDir?: string,
    timeoutMs: number = 60_000
  ): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          const proc = this.activeProcesses.get(id);
          if (proc) {
            try { proc.kill('SIGTERM'); } catch { /* 忽略 */ }
            this.activeProcesses.delete(id);
          }
          reject(new Error('Skill execution timeout'));
        }
      }, timeoutMs);

      this.pendingRequests.set(id, { resolve, reject, timer });

      const pythonExe = this.findPython();
      const cwd = path.dirname(scriptPath);

      const env = {
        ...process.env,
        SKILL_ID: skillId,
        WORKSPACE_DIR: workspaceDir || '',
        PYTHONPATH: cwd,
        PYTHONIOENCODING: 'utf-8',
      };

      const proc = spawn(pythonExe, [scriptPath], {
        cwd,
        stdio: ['pipe', 'pipe', 'pipe'],
        windowsHide: true,
        env,
      });

      this.activeProcesses.set(id, proc);

      // 按行解析 stdout JSON 响应
      let buffer = '';
      proc.stdout?.on('data', (data: Buffer) => {
        buffer += data.toString('utf-8');
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (!line.trim()) continue;
          try {
            const response = JSON.parse(line) as SkillExecuteResponse;
            const pending = this.pendingRequests.get(response.id || id);
            if (pending) {
              this.pendingRequests.delete(response.id || id);
              clearTimeout(pending.timer);
              if (response.success) {
                pending.resolve(response.result);
              } else {
                pending.reject(new Error(response.error || 'Skill execution failed'));
              }
            }
          } catch {
            // 非JSON行可能是调试输出，忽略
          }
        }
      });

      proc.stderr?.on('data', (data: Buffer) => {
        console.log(`[SkillExecutor:${skillId}]`, data.toString('utf-8').trim());
      });

      proc.on('close', (code) => {
        this.activeProcesses.delete(id);
        if (code !== 0) {
          const pending = this.pendingRequests.get(id);
          if (pending) {
            this.pendingRequests.delete(id);
            clearTimeout(pending.timer);
            reject(new Error(`Skill script exited with code ${code}`));
          }
        }
      });

      proc.on('error', (err) => {
        this.activeProcesses.delete(id);
        const pending = this.pendingRequests.get(id);
        if (pending) {
          this.pendingRequests.delete(id);
          clearTimeout(pending.timer);
          reject(new Error(`Failed to start skill script: ${err.message}`));
        }
      });

      // 发送执行请求到子进程 stdin
      try {
        const request: SkillExecuteRequest = { id, skillId, args, workspaceDir };
        proc.stdin?.write(JSON.stringify(request) + '\n');
      } catch (e) {
        this.pendingRequests.delete(id);
        clearTimeout(timer);
        reject(new Error('Failed to send request to skill script: ' + (e as Error).message));
      }
    });
  }

  /**
   * 回退：通过 win-automation service.py 执行技能
   * 将 skillId 作为 operation，args 作为参数传递
   * 这确保了即使技能没有独立脚本，仍可通过通用自动化能力执行
   */
  private async executeViaWinAutomation(
    id: number,
    skillId: string,
    args: Record<string, unknown>,
    timeoutMs: number
  ): Promise<unknown> {
    const { winAutomationService } = await import('./win-automation-service');
    const result = await winAutomationService.execute(skillId, args, timeoutMs);
    return result;
  }

  /**
   * 查找 Python 解释器路径
   * 简化实现：优先内嵌 Python，回退到系统 Python
   * 复杂搜索逻辑由 win-automation-service.ts 处理
   */
  private findPython(): string {
    // 优先使用内嵌 Python
    const embeddedPython = path.join(process.resourcesPath, 'python', 'python.exe');
    if (app.isPackaged && existsSync(embeddedPython)) {
      return embeddedPython;
    }
    // 开发环境回退到系统 Python
    return 'python';
  }

  /**
   * 停止所有活跃进程
   */
  stop(): void {
    this.activeProcesses.forEach((proc) => {
      try { proc.kill('SIGTERM'); } catch { /* 忽略 */ }
    });
    this.activeProcesses.clear();
    this.pendingRequests.forEach(({ timer }) => clearTimeout(timer));
    this.pendingRequests.clear();
  }
}

export const skillExecutor = new SkillExecutorService();
