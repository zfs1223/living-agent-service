/**
 * Electron 系统级通知实现 - P84 会议预约与通知 / 闭环 44
 *
 * <p>接收主进程转发的会议通知，触发操作系统级弹窗提醒。
 * 支持"立即加入"/"稍后提醒"/"查看详情"/"接受邀请"等操作按钮。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */

import { Notification, nativeImage, app } from 'electron';
import path from 'path';

/** 会议通知载荷 */
export interface MeetingReminderPayload {
  notificationId: string;
  type: 'MEETING_INVITE' | 'MEETING_REMINDER' | 'MEETING_STARTED' | 'MEETING_CANCELLED';
  title: string;
  content: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  metadata: {
    scheduleId: string;
    roomName?: string;
    actionUrl?: string;
  };
}

/**
 * 显示系统级通知（统一入口）
 */
export function showSystemNotification(payload: MeetingReminderPayload): void {
  const notification = new Notification({
    title: payload.title,
    body: payload.content,
    icon: nativeImage.createFromPath(
      path.join(app.getAppPath(), 'assets', 'logo.png')
    ),
    urgency: payload.priority === 'CRITICAL' ? 'critical' : 'normal',
    closeButtonText: '忽略',
    actions: buildActions(payload),
  });

  notification.on('action', (_event, index) => {
    handleAction(payload, index);
  });

  notification.on('click', () => {
    handleDefaultAction(payload);
  });

  notification.show();

  // 紧急通知时播放提示音
  if (payload.priority === 'CRITICAL') {
    playNotificationSound();
  }
}

/**
 * 根据通知类型构建操作按钮
 */
function buildActions(payload: MeetingReminderPayload): Electron.NotificationAction[] {
  switch (payload.type) {
    case 'MEETING_REMINDER':
    case 'MEETING_STARTED':
      return [
        { type: 'button', text: '立即加入' },
        { type: 'button', text: '稍后提醒' },
      ];
    case 'MEETING_INVITE':
      return [
        { type: 'button', text: '查看详情' },
        { type: 'button', text: '接受邀请' },
      ];
    default:
      return [];
  }
}

/**
 * 处理通知按钮点击
 */
async function handleAction(payload: MeetingReminderPayload, index: number): Promise<void> {
  const { showMainWindow, sendToRenderer } = await import('../window');

  switch (payload.type) {
    case 'MEETING_REMINDER':
    case 'MEETING_STARTED':
      if (index === 0) {
        // 立即加入
        showMainWindow();
        sendToRenderer('navigate', payload.metadata.actionUrl || `/meeting/${payload.metadata.scheduleId}/join`);
      } else if (index === 1) {
        // 稍后提醒（5分钟后）
        scheduleDelayedReminder(payload, 5 * 60 * 1000);
      }
      break;

    case 'MEETING_INVITE':
      if (index === 0) {
        // 查看详情
        showMainWindow();
        sendToRenderer('navigate', `/meeting/schedule/${payload.metadata.scheduleId}`);
      } else if (index === 1) {
        // 接受邀请
        await acceptInvitation(payload.metadata.scheduleId);
      }
      break;
  }
}

/**
 * 默认操作（点击通知主体）
 */
async function handleDefaultAction(payload: MeetingReminderPayload): Promise<void> {
  const { showMainWindow, sendToRenderer } = await import('../window');
  showMainWindow();

  if (payload.metadata.actionUrl) {
    sendToRenderer('navigate', payload.metadata.actionUrl);
  } else {
    sendToRenderer('navigate', `/meeting/schedule/${payload.metadata.scheduleId}`);
  }
}

/**
 * 播放提示音
 */
function playNotificationSound(): void {
  // 跨平台提示音：macOS 系统默认、Windows 通过 electron-notification-center
  // Linux 使用 libnotify
  if (process.platform === 'darwin') {
    // macOS 使用系统默认提示音
    const { exec } = require('child_process');
    exec('afplay /System/Library/Sounds/Glass.aiff');
  }
}

/**
 * 延迟提醒（5分钟后再次提醒）
 */
function scheduleDelayedReminder(payload: MeetingReminderPayload, delayMs: number): void {
  setTimeout(() => {
    showSystemNotification({
      ...payload,
      notificationId: `delayed_${Date.now()}_${payload.notificationId}`,
      title: '再次提醒：' + payload.title,
    });
  }, delayMs);
}

/**
 * 接受会议邀请
 */
async function acceptInvitation(scheduleId: string): Promise<void> {
  // TODO: 调用 /api/meeting-schedules/{id}/accept
  console.log(`[P84] Accepting meeting invitation: ${scheduleId}`);
}
