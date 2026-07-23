/**
 * P7 悬浮 Quick View 窗口管理
 * - 320×480，底部居中，可拖拽
 * - alwaysOnTop + frame: false + transparent
 * - blur + 3s 延迟隐藏，输入态不隐藏
 * - click-through 非交互区
 */
import { BrowserWindow, screen } from 'electron';
import { join } from 'path';

let quickViewWindow: BrowserWindow | null = null;
let isTyping = false;
let hideTimeout: NodeJS.Timeout | null = null;

function getPosition(): { x: number; y: number } {
  const display = screen.getPrimaryDisplay();
  const { width, height } = display.workAreaSize;
  return {
    x: Math.round((width - 320) / 2),
    y: height - 480 - 100
  };
}

export function initQuickView(): void {
  // 默认不创建，等用户触发
}

export function showQuickView(): void {
  if (quickViewWindow && !quickViewWindow.isDestroyed()) {
    quickViewWindow.show();
    quickViewWindow.focus();
    return;
  }
  const { x, y } = getPosition();
  quickViewWindow = new BrowserWindow({
    width: 320,
    height: 480,
    x,
    y,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    movable: true,
    show: false,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  // 加载 Quick View 页面
  if (process.env['ELECTRON_RENDERER_URL']) {
    quickViewWindow.loadURL(`${process.env['ELECTRON_RENDERER_URL']}/quick-view.html`);
  } else {
    quickViewWindow.loadFile(join(__dirname, '../renderer/quick-view.html'));
  }

  quickViewWindow.once('ready-to-show', () => {
    quickViewWindow?.show();
  });

  // 自动隐藏：blur 事件 + 3s 延迟
  quickViewWindow.on('blur', () => {
    if (isTyping) return; // 输入态不隐藏
    if (hideTimeout) clearTimeout(hideTimeout);
    hideTimeout = setTimeout(() => {
      quickViewWindow?.hide();
    }, 3000);
  });

  quickViewWindow.on('focus', () => {
    if (hideTimeout) {
      clearTimeout(hideTimeout);
      hideTimeout = null;
    }
  });

  quickViewWindow.on('closed', () => {
    quickViewWindow = null;
    if (hideTimeout) {
      clearTimeout(hideTimeout);
      hideTimeout = null;
    }
  });
}

export function showQuickViewWithSelection(text: string): void {
  showQuickView();
  // 等 ready-to-show 后发送选中文本
  setTimeout(() => {
    if (quickViewWindow && !quickViewWindow.isDestroyed()) {
      quickViewWindow.webContents.send('quickview:set-selection', { text });
    }
  }, 100);
}

export function hideQuickView(): void {
  quickViewWindow?.hide();
}

export function toggleQuickView(): void {
  if (!quickViewWindow || quickViewWindow.isDestroyed()) {
    showQuickView();
  } else if (quickViewWindow.isVisible()) {
    hideQuickView();
  } else {
    showQuickView();
  }
}

export function setQuickViewTyping(typing: boolean): void {
  isTyping = typing;
}

export function getQuickViewWindow(): BrowserWindow | null {
  return quickViewWindow;
}

export function destroyQuickView(): void {
  if (hideTimeout) {
    clearTimeout(hideTimeout);
    hideTimeout = null;
  }
  quickViewWindow?.destroy();
  quickViewWindow = null;
}
