/**
 * P7 Quick View 消息转发控制器
 * - Quick View 不建立独立 WebSocket，所有消息通过主窗口转发
 * - 消息流：Quick View → IPC → 主进程 → 主窗口 OfficeChatPage → WS
 * - 响应流：WS 响应 → 主窗口 → 主进程 → IPC → Quick View
 */
import { getQuickViewWindow } from './quick-view-window';
import { getMainWindow } from '../window';

/**
 * 转发 Quick View 的聊天消息到主窗口
 */
export function forwardMessageToMainWindow(data: { content: string; attachments?: any[]; metadata?: any }): void {
  const mainWindow = getMainWindow();
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('quickview:forward-message', data);
  }
}

/**
 * 转发主窗口的 AI 响应到 Quick View
 */
export function forwardResponseToQuickView(data: { content: string; trace?: any[]; attachments?: any[] }): void {
  const qv = getQuickViewWindow();
  if (qv && !qv.isDestroyed()) {
    qv.webContents.send('quickview:response', data);
  }
}

/**
 * 同步部门锁定状态到 Quick View
 */
export function syncDepartmentToQuickView(data: { department: string; locked: boolean }): void {
  const qv = getQuickViewWindow();
  if (qv && !qv.isDestroyed()) {
    qv.webContents.send('quickview:set-department', data);
  }
}

/**
 * 推送主动服务通知到 Quick View
 */
export function pushProactiveNotification(data: { type: string; title: string; body: string }): void {
  const qv = getQuickViewWindow();
  if (qv && !qv.isDestroyed()) {
    qv.webContents.send('quickview:proactive-notification', data);
  }
}

/**
 * 触发截图工具
 */
export function triggerScreenshot(): void {
  const { triggerRegionScreenshot } = require('../screenshot/screenshot-service');
  triggerRegionScreenshot();
}
