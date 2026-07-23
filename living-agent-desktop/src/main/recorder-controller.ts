/**
 * 操作录制控制器
 *
 * 职责：
 * 1. 管理 recorder.py 子进程的启动/停止
 * 2. 通过 stdin/stdout 与 recorder.py 通信（JSON 行协议）
 * 3. 将录制事件转发到渲染进程（备注请求、步骤更新等）
 * 4. 管理录制状态
 *
 * 设计原则：录制与执行分离
 * - recorder.py 独立于 service.py
 * - 录制期间临时运行，录制结束后退出
 * - 不影响 win_automation 执行引擎
 */
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { app, BrowserWindow } from 'electron';
import { existsSync } from 'fs';
import { winAutomationService } from './win-automation-service';

/** 录制配置 */
export interface RecorderConfig {
  target_app: string;   // 目标应用名称
  note_mode: 'all' | 'key' | 'summary';  // 备注模式
}

/** 录制步骤 */
export interface RecorderStep {
  index: number;
  operation: string;
  args: Record<string, unknown>;
  target: Record<string, unknown>;
  note: string;
  timestamp: string;
}

/** 录制结果 */
export interface RecorderResult {
  steps: RecorderStep[];
  meta: {
    app: string;
    recorded_at: string;
    duration_seconds: number;
    step_count: number;
    note_mode: string;
  };
}

class OperationRecorderController {
  private recorderProcess: ChildProcess | null = null;
  private responseBuffer = '';
  private _recording = false;
  private _paused = false;
  private _stepCount = 0;
  private _currentSteps: RecorderStep[] = [];
  private _pendingNoteIndex: number | null = null;
  private mainWindow: BrowserWindow | null = null;

  /** 是否正在录制 */
  get isRecording(): boolean {
    return this._recording;
  }

  /** 是否暂停 */
  get isPaused(): boolean {
    return this._paused;
  }

  /** 当前步骤数 */
  get stepCount(): number {
    return this._stepCount;
  }

  /** 当前待备注步骤索引 */
  get pendingNoteIndex(): number | null {
    return this._pendingNoteIndex;
  }

  /** 当前已录制的步骤 */
  get currentSteps(): RecorderStep[] {
    return [...this._currentSteps];
  }

  /** 设置主窗口引用（用于向渲染进程发送事件） */
  setMainWindow(win: BrowserWindow | null): void {
    this.mainWindow = win;
  }

  /**
   * 启动录制
   */
  async start(config: RecorderConfig): Promise<{ success: boolean; error?: string }> {
    if (this._recording) {
      return { success: false, error: '已在录制中' };
    }

    const { scriptPath, pythonExe, cwd } = this.resolvePaths();

    if (!existsSync(scriptPath)) {
      return { success: false, error: `录制脚本不存在: ${scriptPath}` };
    }
    if (!existsSync(pythonExe)) {
      return { success: false, error: 'Python 未安装，无法启动录制' };
    }

    try {
      this.recorderProcess = spawn(pythonExe, [scriptPath], {
        cwd,
        stdio: ['pipe', 'pipe', 'pipe'],
        windowsHide: true,
        env: {
          ...process.env,
          PYTHONPATH: cwd,
          PYTHONIOENCODING: 'utf-8'
        }
      });

      this.recorderProcess.stdout?.on('data', (data: Buffer) => {
        this.handleStdout(data);
      });

      this.recorderProcess.stderr?.on('data', (data: Buffer) => {
        console.log('[Recorder]', data.toString('utf-8').trim());
      });

      this.recorderProcess.on('close', (code) => {
        console.log('[Recorder] Process exited with code', code);
        this.recorderProcess = null;
        if (this._recording) {
          this._recording = false;
          this._paused = false;
          this.sendToRenderer('recorder:status', {
            recording: false,
            paused: false,
            stepCount: this._stepCount
          });
        }
      });

      this.recorderProcess.on('error', (err) => {
        console.error('[Recorder] Process error:', err.message);
        this.recorderProcess = null;
        this._recording = false;
      });

      // 等待 ready 信号
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
          reject(new Error('Recorder startup timeout'));
        }, 5000);

        const readyHandler = (data: Buffer) => {
          const text = data.toString('utf-8');
          if (text.includes('"type":"ready"')) {
            clearTimeout(timeout);
            this.recorderProcess?.stdout?.off('data', readyHandler);
            resolve();
          }
        };

        this.recorderProcess?.stdout?.on('data', readyHandler);
      });

      // 发送 start 命令
      this.sendCommand({ cmd: 'start', config });

      this._recording = true;
      this._paused = false;
      this._stepCount = 0;
      this._currentSteps = [];
      this._pendingNoteIndex = null;

      return { success: true };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  }

  /**
   * 停止录制
   */
  async stop(): Promise<RecorderResult | null> {
    if (!this._recording || !this.recorderProcess) {
      return null;
    }

    // 发送 stop 命令
    this.sendCommand({ cmd: 'stop' });

    // 等待 result 事件（最多5秒）
    return new Promise((resolve) => {
      const timeout = setTimeout(() => {
        this.forceStop();
        resolve(null);
      }, 5000);

      const originalHandler = this.resultHandler;
      this.resultHandler = (result: RecorderResult) => {
        clearTimeout(timeout);
        this.resultHandler = originalHandler;
        this._recording = false;
        this._paused = false;
        // 延迟关闭进程
        setTimeout(() => {
          if (this.recorderProcess) {
            try {
              this.recorderProcess.kill('SIGTERM');
            } catch { /* ignore */ }
            this.recorderProcess = null;
          }
        }, 500);
        resolve(result);
      };
    });
  }

  /** 结果回调（内部使用） */
  private resultHandler: ((result: RecorderResult) => void) | null = null;

  /**
   * 暂停录制
   */
  pause(): void {
    if (this._recording && !this._paused) {
      this.sendCommand({ cmd: 'pause' });
      this._paused = true;
      this.sendToRenderer('recorder:status', {
        recording: true,
        paused: true,
        stepCount: this._stepCount
      });
    }
  }

  /**
   * 继续录制
   */
  resume(): void {
    if (this._recording && this._paused) {
      this.sendCommand({ cmd: 'resume' });
      this._paused = false;
      this.sendToRenderer('recorder:status', {
        recording: true,
        paused: false,
        stepCount: this._stepCount
      });
    }
  }

  /**
   * 为步骤添加备注
   */
  setNote(index: number, text: string): void {
    this.sendCommand({ cmd: 'note', index, text });
    // 更新本地缓存
    if (index > 0 && index <= this._currentSteps.length) {
      this._currentSteps[index - 1].note = text;
    }
    this._pendingNoteIndex = null;
    this.sendToRenderer('recorder:note-set', { index, text });
  }

  /**
   * 跳过备注
   */
  skipNote(): void {
    this.sendCommand({ cmd: 'skip_note' });
    this._pendingNoteIndex = null;
    this.sendToRenderer('recorder:note-skipped', {});
  }

  /**
   * 强制停止（清理进程）
   */
  forceStop(): void {
    if (this.recorderProcess) {
      try {
        this.recorderProcess.kill('SIGTERM');
      } catch { /* ignore */ }
      this.recorderProcess = null;
    }
    this._recording = false;
    this._paused = false;
    this._stepCount = 0;
    this._pendingNoteIndex = null;
    this.sendToRenderer('recorder:status', {
      recording: false,
      paused: false,
      stepCount: 0
    });
  }

  // ============================================================
  // 内部方法
  // ============================================================

  /**
   * 解析脚本与 Python 解释器路径
   * 复用 win-automation-service 的路径解析逻辑
   */
  private resolvePaths(): { scriptPath: string; pythonExe: string; cwd: string } {
    const isPackaged = app.isPackaged;

    const resourcesRoot = isPackaged
      ? path.join(process.resourcesPath, 'win-automation')
      : path.join(__dirname, '..', '..', 'resources', 'win-automation');

    const scriptPath = path.join(resourcesRoot, 'recorder.py');
    const cwd = resourcesRoot;

    // 复用 win-automation-service 已解析的 Python 路径
    // 通过访问其私有方法获取（或直接用同一路径逻辑）
    const embeddedPython = path.join(process.resourcesPath, 'python', 'python.exe');

    let pythonExe: string;
    if (isPackaged && existsSync(embeddedPython)) {
      pythonExe = embeddedPython;
    } else {
      // 使用与 win-automation-service 相同的 Python
      // 简化：直接使用 'python'，因为 win-automation-service 已验证 Python 可用
      pythonExe = 'python';
    }

    return { scriptPath, pythonExe, cwd };
  }

  /**
   * 向 recorder.py 发送命令
   */
  private sendCommand(cmd: Record<string, unknown>): void {
    if (!this.recorderProcess?.stdin) {
      console.error('[Recorder] Cannot send command: process not available');
      return;
    }
    try {
      this.recorderProcess.stdin.write(JSON.stringify(cmd) + '\n');
    } catch (e) {
      console.error('[Recorder] Failed to send command:', e);
    }
  }

  /**
   * 处理 stdout 数据
   */
  private handleStdout(data: Buffer): void {
    this.responseBuffer += data.toString('utf-8');

    const lines = this.responseBuffer.split('\n');
    this.responseBuffer = lines.pop() || '';

    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const event = JSON.parse(line);
        this.handleEvent(event);
      } catch (e) {
        console.error('[Recorder] Failed to parse event:', line, e);
      }
    }
  }

  /**
   * 处理来自 recorder.py 的事件
   */
  private handleEvent(event: Record<string, unknown>): void {
    const type = event.type as string;

    switch (type) {
      case 'status': {
        this._recording = event.recording as boolean;
        this._paused = event.paused as boolean;
        this._stepCount = event.step_count as number;
        this.sendToRenderer('recorder:status', {
          recording: this._recording,
          paused: this._paused,
          stepCount: this._stepCount
        });
        break;
      }

      case 'step': {
        const step = event as unknown as RecorderStep;
        this._currentSteps.push(step);
        this._stepCount = this._currentSteps.length;
        this.sendToRenderer('recorder:step', step);
        break;
      }

      case 'step_update': {
        const idx = event.index as number;
        const op = event.operation as string;
        if (idx > 0 && idx <= this._currentSteps.length) {
          this._currentSteps[idx - 1].operation = op;
        }
        this.sendToRenderer('recorder:step-update', { index: idx, operation: op });
        break;
      }

      case 'note_request': {
        this._pendingNoteIndex = event.index as number;
        this.sendToRenderer('recorder:note-request', {
          index: event.index,
          operation: event.operation,
          suggestion: event.suggestion
        });
        break;
      }

      case 'note_set':
      case 'note_skipped': {
        this._pendingNoteIndex = null;
        break;
      }

      case 'result': {
        const result = event as unknown as RecorderResult;
        this._currentSteps = result.steps;
        this.sendToRenderer('recorder:result', result);
        if (this.resultHandler) {
          this.resultHandler(result);
        }
        break;
      }

      case 'error': {
        console.error('[Recorder] Error:', event.message);
        this.sendToRenderer('recorder:error', { message: event.message });
        break;
      }

      case 'warning': {
        console.warn('[Recorder] Warning:', event.message);
        this.sendToRenderer('recorder:warning', { message: event.message });
        break;
      }

      case 'ready': {
        // 已在 start() 中处理
        break;
      }

      default: {
        console.warn('[Recorder] Unknown event type:', type);
      }
    }
  }

  /**
   * 向渲染进程发送事件
   */
  private sendToRenderer(channel: string, data: unknown): void {
    if (this.mainWindow && !this.mainWindow.isDestroyed()) {
      this.mainWindow.webContents.send(channel, data);
    }
  }
}

export const operationRecorder = new OperationRecorderController();
