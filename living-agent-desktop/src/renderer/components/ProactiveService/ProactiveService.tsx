/**
 * P16: 主动服务推送组件
 *
 * 显示 LAS 主动推送的服务摘要、习惯建议、通知等
 */
import { useState, useEffect, useCallback } from 'react';
import './ProactiveService.css';

export interface ProactiveDigest {
  summary: string;
  suggestions: string[];
  urgentItems?: string[];
  timestamp: string;
}

export interface ProactiveNotification {
  id: string;
  type: 'reminder' | 'suggestion' | 'alert' | 'degradation' | 'recovery';
  title: string;
  content: string;
  priority: 'high' | 'medium' | 'low';
  createdAt: string;
  read?: boolean;
  /** P26: 降级/恢复通知附加字段 */
  degradedService?: string;
  degradationReason?: string;
  estimatedRecoveryTime?: string;
  affectedScope?: string;
}

interface ProactiveServiceProps {
  backendUrl?: string;
  hasToken?: boolean;
  onNotificationClick?: (notification: ProactiveNotification) => void;
  onDismiss?: () => void;
}

export default function ProactiveService({
  backendUrl,
  hasToken,
  onNotificationClick,
  onDismiss
}: ProactiveServiceProps) {
  const [digest, setDigest] = useState<ProactiveDigest | null>(null);
  const [notifications, setNotifications] = useState<ProactiveNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  // 加载主动服务数据
  const loadData = useCallback(async () => {
    if (!backendUrl || !hasToken) return;

    setLoading(true);
    setError(null);
    try {
      const token = await window.livingAgentAPI.auth.getToken();

      // 并行获取摘要和通知
      const [digestRes, notifRes] = await Promise.all([
        window.livingAgentAPI.proactive.digest(),
        window.livingAgentAPI.proactive.notifications()
      ]);

      setDigest(digestRes);
      setNotifications(notifRes || []);
    } catch (err) {
      console.error('[ProactiveService] 加载失败:', err);
      setError('加载失败');
    } finally {
      setLoading(false);
    }
  }, [backendUrl, hasToken]);

  useEffect(() => {
    loadData();
    // 每 5 分钟刷新一次
    const interval = setInterval(loadData, 5 * 60 * 1000);
    return () => clearInterval(interval);
  }, [loadData]);

  const unreadCount = notifications.filter(n => !n.read).length;

  if (!hasToken || loading) return null;

  return (
    <div className={`proactive-service ${expanded ? 'proactive-service--expanded' : ''}`}>
      {/* 折叠状态：只显示未读数量 */}
      {!expanded && unreadCount > 0 && (
        <div className="proactive-service__badge" onClick={() => setExpanded(true)}>
          <span className="proactive-service__icon">🔔</span>
          <span className="proactive-service__count">{unreadCount}</span>
        </div>
      )}

      {/* 展开状态：显示摘要和通知列表 */}
      {expanded && (
        <div className="proactive-service__panel">
          <div className="proactive-service__header">
            <span className="proactive-service__title">主动服务</span>
            <button
              className="proactive-service__close"
              onClick={() => {
                setExpanded(false);
                onDismiss?.();
              }}
            >
              ✕
            </button>
          </div>

          {error && (
            <div className="proactive-service__error">{error}</div>
          )}

          {/* 摘要区域 */}
          {digest && (
            <div className="proactive-service__digest">
              <div className="proactive-service__digest-title">📋 今日摘要</div>
              <div className="proactive-service__digest-summary">{digest.summary}</div>
              {digest.suggestions && digest.suggestions.length > 0 && (
                <div className="proactive-service__suggestions">
                  <div className="proactive-service__suggestions-title">建议</div>
                  <ul>
                    {digest.suggestions.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                </div>
              )}
              {digest.urgentItems && digest.urgentItems.length > 0 && (
                <div className="proactive-service__urgent">
                  <div className="proactive-service__urgent-title">⚠️ 紧急</div>
                  <ul>
                    {digest.urgentItems.map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* 通知列表 */}
          {notifications.length > 0 && (
            <div className="proactive-service__notifications">
              <div className="proactive-service__notifications-title">
                通知 ({unreadCount} 条未读)
              </div>
              <div className="proactive-service__notification-list">
                {notifications.slice(0, 5).map(notif => (
                  <div
                    key={notif.id}
                    className={`proactive-notification ${!notif.read ? 'proactive-notification--unread' : ''} ${notif.type === 'degradation' ? 'proactive-notification--degradation' : ''}`}
                    onClick={() => onNotificationClick?.(notif)}
                  >
                    <div className="proactive-notification__header">
                      <span className="proactive-notification__type">
                        {notif.type === 'reminder' ? '⏰' : notif.type === 'alert' ? '🚨' : notif.type === 'degradation' ? '⚠️' : notif.type === 'recovery' ? '✅' : '💡'}
                      </span>
                      <span className="proactive-notification__title">{notif.title}</span>
                    </div>
                    <div className="proactive-notification__content">{notif.content}</div>
                    {/* P26: 降级/恢复通知附加信息 */}
                    {notif.type === 'degradation' && notif.degradedService && (
                      <div className="proactive-notification__degradation-details">
                        <div className="degradation-detail"><strong>服务:</strong> {notif.degradedService}</div>
                        {notif.degradationReason && <div className="degradation-detail"><strong>原因:</strong> {notif.degradationReason}</div>}
                        {notif.estimatedRecoveryTime && <div className="degradation-detail"><strong>预计恢复:</strong> {notif.estimatedRecoveryTime}</div>}
                        {notif.affectedScope && <div className="degradation-detail"><strong>影响范围:</strong> {notif.affectedScope}</div>}
                      </div>
                    )}
                    <div className="proactive-notification__time">
                      {new Date(notif.createdAt).toLocaleString()}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 刷新按钮 */}
          <div className="proactive-service__footer">
            <button className="proactive-service__refresh" onClick={loadData}>
              刷新
            </button>
          </div>
        </div>
      )}
    </div>
  );
}