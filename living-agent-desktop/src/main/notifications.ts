/**
 * OS 通知
 * - 支持点击回调（"查看任务" / "直接接取"）
 * - 支持免打扰时段
 */
import { Notification } from 'electron';
import { SHARED_CONSTANTS } from '../shared/constants';

const notifHistory = new Set<string>();
const MAX_HISTORY = 200;

export interface NotifyOptions {
  title: string;
  body: string;
  actions?: Array<{ text: string; callback: () => void }>;
  silent?: boolean;
  urgency?: 'low' | 'normal' | 'critical';
}

function recordNotification(id: string): void {
  notifHistory.add(id);
  if (notifHistory.size > MAX_HISTORY) {
    const arr = [...notifHistory];
    notifHistory.clear();
    arr.slice(-100).forEach((x) => notifHistory.add(x));
  }
}

export function notify(options: NotifyOptions): void {
  if (!Notification.isSupported()) {
    console.warn('[notify] System notifications not supported');
    return;
  }

  const n = new Notification({
    title: options.title,
    body: options.body,
    silent: options.silent ?? false,
    urgency: options.urgency ?? 'normal',
    closeButtonText: '关闭'
  });

  if (options.actions && options.actions.length > 0) {
    // Windows 10+ supports action buttons
    options.actions.forEach((a, idx) => {
      n.on('action', (_event, actionIndex) => {
        // Electron 42+：actionIndex 在第二参数（Event<NotificationActionEventParams> 中也有 actionIndex 字段）
        if (actionIndex === idx) a.callback();
      });
    });
  }

  n.on('click', () => {
    // 默认点击：触发第一个 action
    options.actions?.[0]?.callback();
  });

  n.show();
  recordNotification(`${options.title}|${options.body}|${Date.now()}`);
}

/**
 * 检查是否在免打扰时段
 */
export function isInQuietHours(start: string, end: string): boolean {
  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();

  const [sh, sm] = start.split(':').map(Number);
  const [eh, em] = end.split(':').map(Number);
  const startMin = sh * 60 + sm;
  const endMin = eh * 60 + em;

  if (startMin < endMin) {
    return cur >= startMin && cur < endMin;
  } else {
    // 跨天（如 22:00 - 08:00）
    return cur >= startMin || cur < endMin;
  }
}

export { SHARED_CONSTANTS };
