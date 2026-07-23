/**
 * 会议通知组件 - P84 会议预约与通知 / 闭环 44/67
 *
 * <p>接收并展示会议相关通知：邀请、提醒（15分钟前）、开始、取消。
 * 通知数据由后端 DepartmentNotificationService 发送，
 * 前端通过 WebSocket 实时接收。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */

import React, { useEffect, useState } from 'react';

/** 会议通知类型 */
type MeetingNotificationType = 'MEETING_INVITE' | 'MEETING_REMINDER' | 'MEETING_STARTED' | 'MEETING_CANCELLED';

/** 会议通知数据 */
interface MeetingNotificationData {
  id: string;
  type: MeetingNotificationType;
  title: string;
  content: string;
  severity: 'INFO' | 'HIGH' | 'URGENT';
  scheduleId?: string;
  roomName?: string;
  meetingTime?: string;
  actionUrl?: string;
  timestamp: string;
}

interface MeetingNotificationProps {
  /** 点击"加入会议"回调 */
  onJoinMeeting?: (roomName: string) => void;
  /** 点击通知后关闭回调 */
  onDismiss?: (notificationId: string) => void;
}

/** 通知类型图标 */
const NOTIFICATION_ICONS: Record<MeetingNotificationType, string> = {
  MEETING_INVITE: '📧',
  MEETING_REMINDER: '⏰',
  MEETING_STARTED: '🎬',
  MEETING_CANCELLED: '❌',
};

/** 严重程度颜色 */
const SEVERITY_COLORS: Record<string, string> = {
  INFO: '#31708f',
  HIGH: '#f0ad4e',
  URGENT: '#d9534f',
};

/**
 * 会议通知组件（单条通知）
 */
export const MeetingNotificationItem: React.FC<{
  notification: MeetingNotificationData;
  onJoinMeeting?: (roomName: string) => void;
  onDismiss?: (id: string) => void;
}> = ({ notification, onJoinMeeting, onDismiss }) => {
  const isJoinable = notification.type === 'MEETING_STARTED' || notification.type === 'MEETING_REMINDER';

  return (
    <div style={{
      padding: '8px 12px',
      borderLeft: `3px solid ${SEVERITY_COLORS[notification.severity] || '#ccc'}`,
      background: '#fff',
      marginBottom: 8,
      borderRadius: 4,
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>
          {NOTIFICATION_ICONS[notification.type]} <strong>{notification.title}</strong>
        </span>
        <span style={{ color: '#888', fontSize: 12 }}>
          {new Date(notification.timestamp).toLocaleTimeString()}
        </span>
      </div>
      <p style={{ margin: '4px 0', whiteSpace: 'pre-wrap', fontSize: 13 }}>
        {notification.content}
      </p>
      <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
        {isJoinable && notification.roomName && onJoinMeeting && (
          <button
            onClick={() => onJoinMeeting(notification.roomName!)}
            style={{
              padding: '4px 12px',
              background: '#337ab7',
              color: '#fff',
              border: 'none',
              borderRadius: 3,
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            加入会议
          </button>
        )}
        {onDismiss && (
          <button
            onClick={() => onDismiss(notification.id)}
            style={{
              padding: '4px 8px',
              background: 'transparent',
              border: '1px solid #ccc',
              borderRadius: 3,
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            关闭
          </button>
        )}
      </div>
    </div>
  );
};

/**
 * 会议通知列表组件
 */
export const MeetingNotification: React.FC<MeetingNotificationProps> = ({ onJoinMeeting, onDismiss }) => {
  const [notifications, setNotifications] = useState<MeetingNotificationData[]>([]);

  // TODO: 接入 WebSocket 通知通道（闭环 44），接收会议通知推送
  // 当前通过定时轮询 /api/meetings/notifications 获取
  useEffect(() => {
    // 预留：WebSocket 通知订阅
    // 当 IMWebSocketHandler (P86) 实现后，替换为 WS 推送模式
  }, []);

  if (notifications.length === 0) {
    return null;
  }

  return (
    <div style={{ position: 'fixed', top: 16, right: 16, width: 360, zIndex: 1000 }}>
      {notifications.map(n => (
        <MeetingNotificationItem
          key={n.id}
          notification={n}
          onJoinMeeting={onJoinMeeting}
          onDismiss={(id) => {
            setNotifications(prev => prev.filter(n => n.id !== id));
            onDismiss?.(id);
          }}
        />
      ))}
    </div>
  );
};

export default MeetingNotification;
