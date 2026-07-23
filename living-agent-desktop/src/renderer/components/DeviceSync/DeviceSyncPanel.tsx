/**
 * P27: 多设备会话同步管理
 *
 * 支持多设备（手机/桌面/Web）会话同步、未读消息同步
 * - 设备列表管理
 * - 会话状态同步
 * - 未读消息标记
 */
import { useState, useEffect } from 'react';
import './DeviceSyncPanel.css';

interface ConnectedDevice {
  id: string;
  type: 'desktop' | 'web' | 'mobile';
  name: string;
  lastActive: string;
  isCurrent: boolean;
  status: 'online' | 'offline';
}

interface SyncedConversation {
  id: string;
  title: string;
  unreadCount: number;
  lastMessage: string;
  lastActivity: string;
  syncedAt: string;
}

interface DeviceSyncPanelProps {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
  onSyncUpdate?: () => void;
}

export default function DeviceSyncPanel({ backendUrl, hasToken, currentUser, onSyncUpdate }: DeviceSyncPanelProps) {
  const [devices, setDevices] = useState<ConnectedDevice[]>([]);
  const [syncedConversations, setSyncedConversations] = useState<SyncedConversation[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState<string | null>(null);

  useEffect(() => {
    if (hasToken) {
      loadDevices();
      loadSyncedConversations();
    }
  }, [backendUrl, hasToken]);

  const loadDevices = async () => {
    try {
      // 实际应调用 /api/user/devices API
      // 模拟数据
      setDevices([
        { id: 'device-1', type: 'desktop', name: '桌面客户端', lastActive: new Date().toISOString(), isCurrent: true, status: 'online' },
        { id: 'device-2', type: 'web', name: 'Web 浏览器', lastActive: new Date(Date.now() - 3600000).toISOString(), isCurrent: false, status: 'offline' },
        { id: 'device-3', type: 'mobile', name: '手机 App', lastActive: new Date(Date.now() - 7200000).toISOString(), isCurrent: false, status: 'offline' }
      ]);
    } catch (err) {
      console.error('[DeviceSync] 加载设备失败:', err);
    }
  };

  const loadSyncedConversations = async () => {
    setLoading(true);
    try {
      // 实际应调用 /api/conversations/synced API
      // 模拟数据
      await new Promise(resolve => setTimeout(resolve, 300));
      setSyncedConversations([
        { id: 'conv-1', title: '技术部讨论', unreadCount: 3, lastMessage: '最新消息：部署完成', lastActivity: new Date().toISOString(), syncedAt: new Date().toISOString() },
        { id: 'conv-2', title: '产品需求评审', unreadCount: 0, lastMessage: '已确认需求', lastActivity: new Date(Date.now() - 3600000).toISOString(), syncedAt: new Date(Date.now() - 1800000).toISOString() },
        { id: 'conv-3', title: '客服部支持', unreadCount: 12, lastMessage: '客户问题待处理', lastActivity: new Date(Date.now() - 1800000).toISOString(), syncedAt: new Date().toISOString() }
      ]);
      setLastSyncTime(new Date().toISOString());
    } catch (err) {
      console.error('[DeviceSync] 加载会话失败:', err);
    } finally {
      setLoading(false);
    }
  };

  const syncNow = async () => {
    setSyncing(true);
    try {
      // 实际应调用 /api/sync/pull API
      await new Promise(resolve => setTimeout(resolve, 1000));
      await loadSyncedConversations();
      onSyncUpdate?.();
    } finally {
      setSyncing(false);
    }
  };

  const logoutDevice = async (deviceId: string) => {
    try {
      // 实际应调用 /api/user/devices/{id}/logout API
      setDevices(prev => prev.filter(d => d.id !== deviceId));
    } catch (err) {
      console.error('[DeviceSync] 登出设备失败:', err);
    }
  };

  const getDeviceIcon = (type: string) => {
    switch (type) {
      case 'desktop': return '🖥️';
      case 'web': return '🌐';
      case 'mobile': return '📱';
      default: return '💻';
    }
  };

  const totalUnread = syncedConversations.reduce((sum, c) => sum + c.unreadCount, 0);

  return (
    <div className="device-sync-panel">
      <div className="device-sync-panel__header">
        <h2>🔄 多设备同步</h2>
        <div className="sync-status">
          {lastSyncTime && (
            <span className="sync-time">
              上次同步: {new Date(lastSyncTime).toLocaleString()}
            </span>
          )}
          <button
            className="sync-btn"
            onClick={syncNow}
            disabled={syncing}
          >
            {syncing ? '同步中...' : '🔄 立即同步'}
          </button>
        </div>
      </div>

      {/* 设备列表 */}
      <div className="device-sync-panel__devices">
        <h3>已连接设备</h3>
        <div className="device-list">
          {devices.map(device => (
            <div key={device.id} className={`device-item ${device.isCurrent ? 'device-item--current' : ''}`}>
              <span className="device-icon">{getDeviceIcon(device.type)}</span>
              <div className="device-info">
                <div className="device-name">
                  {device.name}
                  {device.isCurrent && <span className="current-badge">当前</span>}
                </div>
                <div className="device-meta">
                  <span className={`device-status ${device.status}`}>
                    {device.status === 'online' ? '🟢 在线' : '⚫ 离线'}
                  </span>
                  <span className="device-last-active">
                    {new Date(device.lastActive).toLocaleString()}
                  </span>
                </div>
              </div>
              {!device.isCurrent && (
                <button
                  className="device-logout"
                  onClick={() => logoutDevice(device.id)}
                  title="登出此设备"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* 同步的会话 */}
      <div className="device-sync-panel__conversations">
        <h3>
          同步的会话
          {totalUnread > 0 && (
            <span className="unread-badge">{totalUnread} 未读</span>
          )}
        </h3>
        
        {loading ? (
          <div className="sync-loading">加载中...</div>
        ) : (
          <div className="conversation-list">
            {syncedConversations.map(conv => (
              <div key={conv.id} className={`conversation-item ${conv.unreadCount > 0 ? 'conversation-item--unread' : ''}`}>
                <div className="conversation-info">
                  <div className="conversation-title">{conv.title}</div>
                  <div className="conversation-last-message">{conv.lastMessage}</div>
                  <div className="conversation-meta">
                    <span className="conversation-time">
                      {new Date(conv.lastActivity).toLocaleString()}
                    </span>
                    <span className="conversation-synced">
                      同步于 {new Date(conv.syncedAt).toLocaleString()}
                    </span>
                  </div>
                </div>
                {conv.unreadCount > 0 && (
                  <div className="conversation-unread">{conv.unreadCount}</div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 同步设置 */}
      <div className="device-sync-panel__settings">
        <h3>同步设置</h3>
        <div className="sync-options">
          <label className="sync-option">
            <input type="checkbox" defaultChecked />
            <span>自动同步未读消息</span>
          </label>
          <label className="sync-option">
            <input type="checkbox" defaultChecked />
            <span>同步会话状态</span>
          </label>
          <label className="sync-option">
            <input type="checkbox" />
            <span>同步草稿内容</span>
          </label>
          <label className="sync-option">
            <input type="checkbox" defaultChecked />
            <span>设备离线时推送通知</span>
          </label>
        </div>
      </div>
    </div>
  );
}