/**
 * 全局快捷键
 * - Ctrl+Shift+M：打开主窗口
 * - Ctrl+Shift+T：打开任务中心
 * - Ctrl+Shift+C：快速接取最优先任务
 */
import { globalShortcut } from 'electron';
import { showMainWindow } from './window';
import { showFloatingTaskBoard, hideFloatingTaskBoard } from './floating-task-board';
import { claimTopPriorityTask } from './task-board-tray';

const SHORTCUTS = [
  {
    accelerator: 'CommandOrControl+Shift+M',
    action: () => showMainWindow(),
    label: '打开主窗口'
  },
  {
    accelerator: 'CommandOrControl+Shift+T',
    action: () => {
      showMainWindow();
      // 渲染层接收后跳转到 /task-board
      const { getMainWindow } = require('./window');
      const w = getMainWindow();
      if (w) w.webContents.send('navigate', '/task-board');
    },
    label: '打开任务中心'
  },
  {
    accelerator: 'CommandOrControl+Shift+F',
    action: () => showFloatingTaskBoard(),
    label: '显示任务中心悬浮窗'
  },
  {
    accelerator: 'CommandOrControl+Shift+C',
    action: () => claimTopPriorityTask(),
    label: '快速接取最优先任务'
  }
];

export function initShortcuts(): void {
  for (const s of SHORTCUTS) {
    try {
      const ok = globalShortcut.register(s.accelerator, s.action);
      if (!ok) {
        console.warn(`[shortcuts] Failed to register: ${s.accelerator} (${s.label})`);
      }
    } catch (e) {
      console.error(`[shortcuts] Error registering ${s.accelerator}:`, e);
    }
  }
}

export function destroyShortcuts(): void {
  globalShortcut.unregisterAll();
}
