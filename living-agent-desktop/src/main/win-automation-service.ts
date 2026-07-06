/**
 * Windows 自动化服务管理器
 *
 * 职责：
 * 1. 启动 Python 子进程（内嵌 UIAutomation/PowerShell/注册表等控制能力）
 * 2. 通过 stdin/stdout 进行协议通信（JSON 行协议）
 * 3. 处理响应并回调
 * 4. 不使用独立认证，所有请求来自后端认证后的转发
 *
 * 技术栈借鉴 Windows-MCP：
 * - UIAutomation API (comtypes)
 * - PowerShell 执行
 * - 注册表操作 (PowerShell cmdlets)
 * - 文件系统操作 (Python fs)
 * - 进程管理 (psutil)
 * - 截图 (dxcam/mss)
 *
 * 详细设计：docs/WINDOWS_MCP_INTEGRATION_PLAN.md §3.1
 */
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { app } from 'electron';
import { existsSync } from 'fs';

interface AutomationRequest {
  id: number;
  operation: string;
  args: Record<string, unknown>;
}

interface AutomationResponse {
  id: number;
  success: boolean;
  result?: unknown;
  error?: string;
}

class WindowsAutomationService {
  private automationProcess: ChildProcess | null = null;
  private pendingRequests = new Map<number, {
    resolve: (value: unknown) => void;
    reject: (reason: Error) => void;
    timer: NodeJS.Timeout;
  }>();
  private responseBuffer = '';
  private requestIdCounter = 0;
  private starting: Promise<void> | null = null;
  private startError: string | null = null;  // 记录启动失败原因

  /**
   * 启动 Windows 自动化服务子进程
   * 仅在 Windows 平台启动；其他平台静默跳过。
   */
  async start(): Promise<void> {
    if (process.platform !== 'win32') {
      console.log('[WinAutomation] Non-Windows platform, skip starting');
      this.startError = 'Non-Windows platform';
      return;
    }
    if (this.automationProcess || this.starting) {
      return this.starting ?? Promise.resolve();
    }

    this.starting = this.doStart();
    try {
      await this.starting;
    } finally {
      this.starting = null;
    }
  }

  private async doStart(): Promise<void> {
    const { scriptPath, pythonExe, cwd } = this.resolvePaths();

    if (!existsSync(scriptPath)) {
      this.startError = `Script not found: ${scriptPath}. Please ensure resources/win-automation/service.py exists.`;
      console.error('[WinAutomation] START FAILED:', this.startError);
      return;
    }
    if (!existsSync(pythonExe)) {
      this.startError = `Python not found: ${pythonExe}. Please install Python and add it to PATH, or run: pip install -r requirements.txt`;
      console.error('[WinAutomation] START FAILED:', this.startError);
      return;
    }

    console.log('[WinAutomation] Starting service:', scriptPath);
    this.startError = null;  // 清除之前的错误
    this.automationProcess = spawn(pythonExe, [scriptPath], {
      cwd,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
      env: {
        ...process.env,
        PYTHONPATH: cwd,
        PYTHONIOENCODING: 'utf-8'
      }
    });

    this.automationProcess.stdout?.on('data', (data: Buffer) => {
      this.handleStdout(data);
    });

    this.automationProcess.stderr?.on('data', (data: Buffer) => {
      console.log('[WinAutomation]', data.toString('utf-8').trim());
    });

    this.automationProcess.on('close', (code) => {
      console.log('[WinAutomation] Process exited with code', code);
      this.automationProcess = null;
      this.startError = `Automation process exited (code=${code}). Service needs restart.`;
      const err = new Error('Automation process exited');
      this.pendingRequests.forEach(({ reject, timer }) => {
        clearTimeout(timer);
        reject(err);
      });
      this.pendingRequests.clear();
    });

    this.automationProcess.on('error', (err) => {
      console.error('[WinAutomation] Process error:', err.message);
      this.automationProcess = null;
    });

    console.log('[WinAutomation] Windows automation service started');
  }

  /**
   * 解析脚本与 Python 解释器路径
   * 开发环境：项目 resources 目录 + 系统 Python
   * 打包环境：process.resourcesPath 下的 win-automation 与内嵌 python
   */
  private resolvePaths(): { scriptPath: string; pythonExe: string; cwd: string } {
    const isPackaged = app.isPackaged;

    // 开发环境路径解析：
    // __dirname = living-agent-desktop/dist/main/
    // ../ → dist/
    // ../ → living-agent-desktop/
    // resources/win-automation → 正确路径
    const resourcesRoot = isPackaged
      ? path.join(process.resourcesPath, 'win-automation')
      : path.join(__dirname, '..', '..', 'resources', 'win-automation');

    const scriptPath = path.join(resourcesRoot, 'service.py');
    const cwd = resourcesRoot;

    console.log('[WinAutomation] Path resolved: scriptPath=' + scriptPath + ', exists=' + existsSync(scriptPath));

    // 打包后优先使用内嵌 Python；开发环境动态查找系统 Python
    const embeddedPython = path.join(process.resourcesPath, 'python', 'python.exe');
    
    let pythonExe: string;
    if (isPackaged && existsSync(embeddedPython)) {
      pythonExe = embeddedPython;
    } else {
      // 开发环境：动态查找 Python
      // 注意：Electron 进程环境与用户 shell 环境的 PATH 可能不同
      // 使用多种方式查找 Python 安装路径
      pythonExe = this.findPython();
    }

    console.log('[WinAutomation] Python resolved: pythonExe=' + pythonExe);

    return { scriptPath, pythonExe, cwd };
  }

  /**
   * 动态查找 Python 解释器路径
   * 优先级：PATH > Windows 注册表 > 常见安装路径
   * 要求：Python 3.10+（service.py 使用 match/case 语法）
   */
  private findPython(): string {
    const MIN_VERSION = 10; // Python 3.10+ 最低要求

    // 1. 尝试从 PATH 查找（通过 where 命令）
    try {
      const whereResult = this.execSync('where.exe python', { timeout: 5000 });
      if (whereResult) {
        const paths = whereResult.split('\n')
          .filter(p => p.trim() && !p.includes('WindowsApps'));
        // 选择版本 >= 3.10 的 Python
        for (const p of paths) {
          if (this.checkPythonVersion(p.trim(), MIN_VERSION)) {
            return p.trim();
          }
        }
      }
    } catch (e) {
      // where 命令失败，继续尝试其他方式
    }

    // 2. 尝试从 Windows 注册表查找 Python 安装路径
    try {
      const regResult = this.execSync(
        'reg query "HKLM\\SOFTWARE\\Python\\PythonCore" /s /v InstallPath 2>nul & ' +
        'reg query "HKCU\\SOFTWARE\\Python\\PythonCore" /s /v InstallPath 2>nul',
        { timeout: 5000 }
      );
      if (regResult) {
        // 解析所有匹配的 InstallPath
        const matches = regResult.matchAll(/InstallPath\s+REG_SZ\s+(.+)/g);
        for (const match of matches) {
          const installPath = match[1].trim();
          const pythonPath = path.join(installPath, 'python.exe');
          if (existsSync(pythonPath) && this.checkPythonVersion(pythonPath, MIN_VERSION)) {
            return pythonPath;
          }
        }
      }
    } catch (e) {
      // 注册表查询失败，继续尝试其他方式
    }

    // 3. 尝试常见安装路径（优先高版本）
    // 按版本号降序排列，优先选择高版本
    const commonPaths = [
      // Python 3.14 (最新)
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Python', 'Python314', 'python.exe'),
      'C:\\Program Files\\Python314\\python.exe',
      'C:\\Python314\\python.exe',
      // Python 3.13
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Python', 'Python313', 'python.exe'),
      'C:\\Program Files\\Python313\\python.exe',
      'C:\\Python313\\python.exe',
      // Python 3.12
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Python', 'Python312', 'python.exe'),
      'C:\\Program Files\\Python312\\python.exe',
      'C:\\Python312\\python.exe',
      // Python 3.11
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Python', 'Python311', 'python.exe'),
      'C:\\Program Files\\Python311\\python.exe',
      'C:\\Python311\\python.exe',
      // Python 3.10 (最低要求)
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Python', 'Python310', 'python.exe'),
      'C:\\Program Files\\Python310\\python.exe',
      'C:\\Python310\\python.exe',
    ];

    for (const p of commonPaths) {
      if (p && existsSync(p) && this.checkPythonVersion(p, MIN_VERSION)) {
        return p;
      }
    }

    // 4. 最后回退到 'python'（依赖 PATH），但需要检查版本
    if (this.checkPythonVersion('python', MIN_VERSION)) {
      return 'python';
    }

    // 5. 找不到合适版本，返回错误提示
    console.error('[WinAutomation] No Python 3.10+ found. Please install Python 3.10 or higher.');
    return 'python'; // 返回默认值，启动时会失败并显示错误
  }

  /**
   * 检查 Python 版本是否满足最低要求
   * @param pythonPath Python 可执行文件路径
   * @param minVersion 最低主版本号（如 10 表示 Python 3.10+）
   */
  private checkPythonVersion(pythonPath: string, minVersion: number): boolean {
    try {
      const result = this.execSync(
        `"${pythonPath}" --version`,
        { timeout: 3000 }
      );
      if (result) {
        // 解析版本号，如 "Python 3.14.6" → 14
        const match = result.match(/Python\s+3\.(\d+)/);
        if (match) {
          const minorVersion = parseInt(match[1], 10);
          console.log(`[WinAutomation] Found Python 3.${minorVersion} at: ${pythonPath}`);
          return minorVersion >= minVersion;
        }
      }
    } catch (e) {
      // 版本检查失败，假设不满足要求
    }
    return false;
  }

  /**
   * 同步执行命令并返回输出
   */
  private execSync(cmd: string, options: { timeout: number }): string | null {
    try {
      const result = require('child_process').execSync(cmd, {
        encoding: 'utf-8',
        timeout: options.timeout,
        windowsHide: true,
      });
      return result;
    } catch (e) {
      return null;
    }
  }

  /**
   * 处理 stdout 数据（按行解析 JSON 响应）
   */
  private handleStdout(data: Buffer): void {
    this.responseBuffer += data.toString('utf-8');

    const lines = this.responseBuffer.split('\n');
    this.responseBuffer = lines.pop() || '';

    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const response = JSON.parse(line) as AutomationResponse;
        const pending = this.pendingRequests.get(response.id);
        if (pending) {
          this.pendingRequests.delete(response.id);
          clearTimeout(pending.timer);
          if (response.success) {
            pending.resolve(response.result);
          } else {
            pending.reject(new Error(response.error || 'Operation failed'));
          }
        }
      } catch (e) {
        console.error('[WinAutomation] Failed to parse response:', line, e);
      }
    }
  }

  /**
   * 执行自动化操作
   * @param operation 操作类型（如 click, type, shell, registry_get 等）
   * @param args 操作参数
   * @param timeoutMs 超时毫秒，默认 30s
   */
  async execute(
    operation: string,
    args: Record<string, unknown> = {},
    timeoutMs = 30_000
  ): Promise<unknown> {
    if (!this.automationProcess || !this.automationProcess.stdin) {
      // 提供详细的启动失败原因
      const errorMsg = this.startError
        ? `Automation service not started: ${this.startError}`
        : 'Automation service not started. Please restart the application.';
      throw new Error(errorMsg);
    }

    const id = ++this.requestIdCounter;
    const request: AutomationRequest = { id, operation, args };

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          reject(new Error('Operation timeout'));
        }
      }, timeoutMs);

      this.pendingRequests.set(id, { resolve, reject, timer });

      try {
        if (!this.automationProcess?.stdin) {
          throw new Error('Automation stdin not available');
        }
        this.automationProcess.stdin.write(JSON.stringify(request) + '\n');
      } catch (e) {
        this.pendingRequests.delete(id);
        clearTimeout(timer);
        reject(new Error('Failed to send request: ' + (e as Error).message));
      }
    });
  }

  /**
   * 停止服务
   */
  stop(): void {
    if (this.automationProcess) {
      try {
        this.automationProcess.kill('SIGTERM');
      } catch {
        // ignore
      }
      this.automationProcess = null;
    }
    this.pendingRequests.forEach(({ timer }) => clearTimeout(timer));
    this.pendingRequests.clear();
    this.responseBuffer = '';
    this.startError = null;
  }

  /**
   * 检查服务是否运行
   */
  isRunning(): boolean {
    return this.automationProcess !== null && !this.automationProcess.killed;
  }

  /**
   * 获取启动失败原因（用于诊断）
   */
  getStartError(): string | null {
    return this.startError;
  }
}

export const winAutomationService = new WindowsAutomationService();
