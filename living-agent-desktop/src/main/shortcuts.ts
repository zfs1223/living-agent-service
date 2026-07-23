/**
 * 全局快捷键
 * - Ctrl+Shift+M：打开主窗口
 * - Ctrl+Shift+T：打开任务中心
 * - Ctrl+Shift+C：快速接取最优先任务
 * - Ctrl+Shift+S：截图（P2）
 * - Alt+Space：唤起 Quick View 悬浮对话（P7，短按）/ 触发语音输入（P10，长按 2s）
 * - Ctrl+Shift+I：选中文本并提问（P6/P7，唤起 Quick View 并携带选中文本）
 * - Ctrl+Shift+V：语音输入开关（P10，替代长按 Alt+Space 的备选方案）
 */
import { globalShortcut } from 'electron';
import { showMainWindow } from './window';
import { showFloatingTaskBoard, hideFloatingTaskBoard } from './floating-task-board';
import { claimTopPriorityTask } from './task-board-tray';
import { triggerRegionScreenshot } from './screenshot/screenshot-service';
import { showQuickView, showQuickViewWithSelection, toggleQuickView } from './quick-view/quick-view-window';
import { getMainWindow } from './window';

// P10: Alt+Space 长按检测
let altSpacePressTime = 0;
let altSpaceLongPressTimer: ReturnType<typeof setTimeout> | null = null;
const LONG_PRESS_THRESHOLD = 2000; // 2s 长按阈值

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
  },
  // P2: 截图快捷键
  {
    accelerator: 'CommandOrControl+Shift+S',
    action: () => triggerRegionScreenshot(),
    label: '截图'
  },
  // P7/P10: Alt+Space 短按=Quick View，长按 2s=语音输入
  {
    accelerator: 'Alt+Space',
    action: () => {
      // 短按：toggle Quick View
      toggleQuickView();
    },
    label: '唤起 Quick View（短按）/ 语音输入（长按 2s）'
  },
  // P7: Ctrl+Shift+I 唤起 Quick View 并携带选中文本
  {
    accelerator: 'CommandOrControl+Shift+I',
    action: () => {
      const { clipboard } = require('electron');
      const selectionText = clipboard.readText() || '';
      showQuickViewWithSelection(selectionText);
    },
    label: '选中文本并提问（Quick View）'
  },
  // P10: Ctrl+Shift+V 语音输入开关（备选方案，不依赖长按）
  {
    accelerator: 'CommandOrControl+Shift+V',
    action: () => {
      const mainWindow = getMainWindow();
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('voice-input:toggle');
      }
    },
    label: '语音输入开关（P10）'
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
