/**
 * 任务中心悬浮窗
 * - 320×480，无边框，透明，alwaysOnTop
 * - 默认隐藏，用户主动展开
 * - 折叠态：仅显示待接取数
 * - 展开态：显示任务列表 + 一键接取
 */
import { BrowserWindow, screen } from 'electron';
import { join } from 'path';

let floatingWindow: BrowserWindow | null = null;
let isExpanded = false;

function getPosition(): { x: number; y: number } {
  const display = screen.getPrimaryDisplay();
  const { width, height } = display.workAreaSize;
  return {
    x: width - 340,           // 距离右侧 20px
    y: height - 500           // 距离底部 20px
  };
}

export function initFloatingTaskBoard(): void {
  // 默认不创建，等用户主动展开
}

export function showFloatingTaskBoard(): void {
  if (floatingWindow && !floatingWindow.isDestroyed()) {
    floatingWindow.show();
    floatingWindow.focus();
    return;
  }

  const { x, y } = getPosition();
  floatingWindow = new BrowserWindow({
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
    show: false,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  // 加载悬浮窗页面（独立 HTML）
  if (process.env['ELECTRON_RENDERER_URL']) {
    floatingWindow.loadURL(`${process.env['ELECTRON_RENDERER_URL']}/floating.html`);
  } else {
    floatingWindow.loadFile(join(__dirname, '../renderer/floating.html'));
  }

  floatingWindow.once('ready-to-show', () => {
    floatingWindow?.show();
  });

  // 点击窗口外部自动隐藏（可选）
  floatingWindow.on('blur', () => {
    if (isExpanded) {
      // 展开态保留焦点
    } else {
      floatingWindow?.hide();
    }
  });

  floatingWindow.on('closed', () => {
    floatingWindow = null;
  });
}

export function hideFloatingTaskBoard(): void {
  floatingWindow?.hide();
}

export function toggleFloatingTaskBoard(): void {
  if (!floatingWindow || floatingWindow.isDestroyed()) {
    showFloatingTaskBoard();
  } else if (floatingWindow.isVisible()) {
    hideFloatingTaskBoard();
  } else {
    showFloatingTaskBoard();
  }
}

export function setFloatingExpanded(expanded: boolean): void {
  isExpanded = expanded;
  if (expanded) {
    floatingWindow?.setSize(320, 480);
  } else {
    floatingWindow?.setSize(80, 80);
  }
}

export function getFloatingWindow(): BrowserWindow | null {
  return floatingWindow;
}

export function destroyFloatingTaskBoard(): void {
  floatingWindow?.destroy();
  floatingWindow = null;
}
