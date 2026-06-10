/**
 * 窗口管理
 * - 主窗口：完整应用
 * - 任务中心悬浮窗：常驻桌面
 */
import { app, BrowserWindow, shell, screen } from 'electron';
import { join } from 'path';

let mainWindow: BrowserWindow | null = null;

const MAIN_WINDOW_OPTIONS = {
  width: 1440,
  height: 900,
  minWidth: 1024,
  minHeight: 700,
  show: false,
  autoHideMenuBar: false,
  title: 'Living Agent',
  webPreferences: {
    preload: join(__dirname, '../preload/index.js'),
    sandbox: false,         // 允许主进程 Node.js 集成（生产可改为 true + contextBridge）
    contextIsolation: true, // 严格隔离
    nodeIntegration: false, // 禁用 NodeIntegration
    webSecurity: true
  }
} as const;

export function createMainWindow(): BrowserWindow {
  mainWindow = new BrowserWindow(MAIN_WINDOW_OPTIONS);

  // dev 模式加载 Vite 开发服务器
  if (process.env['ELECTRON_RENDERER_URL']) {
    mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL']);
    // 自动打开 DevTools
    if (process.env['NODE_ENV'] === 'development') {
      mainWindow.webContents.openDevTools({ mode: 'detach' });
    }
  } else {
    // 生产模式加载打包后的 HTML
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'));
  }

  // 外部链接在系统浏览器打开
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // 准备就绪后显示（避免白屏闪烁）
  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();
  });

  // 关闭时最小化到托盘（可选）
  mainWindow.on('close', (event) => {
    if (!(app as any).isQuitting) {
      event.preventDefault();
      mainWindow?.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  return mainWindow;
}

export function getMainWindow(): BrowserWindow | null {
  return mainWindow;
}

export function showMainWindow(): void {
  if (!mainWindow) {
    mainWindow = createMainWindow();
    return;
  }
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

export function hideMainWindow(): void {
  mainWindow?.hide();
}

export function appOnReady(window: BrowserWindow): void {
  // 暴露给 index.ts 的回调钩子
  void window;
}

export function appOnActivate(callback: () => void): void {
  app.on('activate', callback);
}

export function appOnBeforeQuit(callback: () => void): void {
  app.on('before-quit', () => {
    (app as any).isQuitting = true;
    callback();
  });
}

export function sendToMainWindow(channel: string, ...args: any[]): void {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, ...args);
  }
}

/* ============ 屏幕工具 ============ */

export function getPrimaryDisplaySize(): { width: number; height: number } {
  const display = screen.getPrimaryDisplay();
  return {
    width: display.workAreaSize.width,
    height: display.workAreaSize.height
  };
}
