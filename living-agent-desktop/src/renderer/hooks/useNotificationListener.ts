/**
 * WebSocket 通知监听 Hook - P84 会议预约与通知 / 闭环 44
 *
 * <p>监听主进程转发的通知消息，同步更新应用内通知中心 Store。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */

import { useEffect } from 'react';

/** 通知数据结构 */
export interface AppNotification {
  notificationId: string;
  type: string;
  title: string;
  content: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  department?: string;
  timestamp: string;
  read: boolean;
  metadata?: Record<string, string>;
}

/** 通知 Store 接口（由外部提供实现） */
export interface NotificationStore {
  notifications: AppNotification[];
  addNotification: (notification: AppNotification) => void;
  markAsRead: (notificationId: string) => void;
  unreadCount: number;
}

// 全局通知 Store 引用（由 App.tsx 注入）
let _notificationStore: NotificationStore | null = null;

/**
 * 设置通知 Store（在 App 初始化时调用）
 */
export function setNotificationStore(store: NotificationStore): void {
  _notificationStore = store;
}

/**
 * 通知监听 Hook
 *
 * <p>在组件挂载时订阅通知通道，卸载时取消订阅。</p>
 * <p>收到通知后：
 * 1. 转发给主进程显示系统级弹窗
 * 2. 添加到应用内通知中心 Store</p>
 */
export function useNotificationListener(): void {
  useEffect(() => {
    // 监听主进程转发的通知
    if (typeof window !== 'undefined' && (window as any).livingAgentAPI?.message?.onNotification) {
      const unsubscribe = (window as any).livingAgentAPI.message.onNotification(
        (notification: AppNotification) => {
          // 转发给主进程显示系统级通知
          if ((window as any).livingAgentAPI?.message?.showSystemNotification) {
            (window as any).livingAgentAPI.message.showSystemNotification(notification);
          }

          // 添加到应用内通知中心
          if (_notificationStore) {
            _notificationStore.addNotification({
              ...notification,
              read: false,
              timestamp: notification.timestamp || new Date().toISOString(),
            });
          }
        }
      );

      return () => {
        if (typeof unsubscribe === 'function') {
          unsubscribe();
        }
      };
    }
  }, []);
}
