/**
 * P24: 通道健康监控可视化
 *
 * 显示 WebSocket 通道健康状态与自愈合过程
 */
import { useState, useEffect } from 'react';
import './ChannelHealth.css';

export type ChannelType = 'AGENT' | 'DEPT' | 'ENTERPRISE' | 'PUBLIC';
export type ChannelHealthStatus = 'healthy' | 'degraded' | 'unhealthy' | 'offline';

export interface ChannelHealthInfo {
  channel: ChannelType;
  status: ChannelHealthStatus;
  connectionCount: number;
  latencyMs: number;
  selfHealingCount: number;
  lastEvent?: string;
}

interface ChannelHealthIndicatorProps {
  channels: ChannelHealthInfo[];
  onChannelClick?: (channel: ChannelHealthInfo) => void;
}

const CHANNEL_ICONS: Record<ChannelType, string> = {
  AGENT: '🤖',
  DEPT: '💬',
  ENTERPRISE: '🌐',
  PUBLIC: '🌍',
};

const CHANNEL_LABELS: Record<ChannelType, string> = {
  AGENT: 'Agent 通道',
  DEPT: '部门通道',
  ENTERPRISE: '企业频道',
  PUBLIC: '公共频道',
};

const STATUS_COLORS: Record<ChannelHealthStatus, string> = {
  healthy: '#52c41a',
  degraded: '#faad14',
  unhealthy: '#ff4d4f',
  offline: '#d9d9d9',
};

export default function ChannelHealthIndicator({ channels, onChannelClick }: ChannelHealthIndicatorProps) {
  const [expanded, setExpanded] = useState(false);
  const [selectedChannel, setSelectedChannel] = useState<ChannelHealthInfo | null>(null);

  // 计算整体健康状态
  const overallStatus: ChannelHealthStatus = channels.some(c => c.status === 'unhealthy')
    ? 'unhealthy'
    : channels.some(c => c.status === 'degraded')
      ? 'degraded'
      : channels.every(c => c.status === 'offline')
        ? 'offline'
        : 'healthy';

  const overallColor = STATUS_COLORS[overallStatus];

  return (
    <div className="channel-health">
      {/* 折叠状态：整体健康点 */}
      <div
        className="channel-health__overall"
        onClick={() => setExpanded(!expanded)}
        title={`通道状态: ${overallStatus}`}
      >
        <span
          className="channel-health__dot"
          style={{ background: overallColor }}
        />
        <span className="channel-health__label">通道</span>
      </div>

      {/* 展开状态：详细面板 */}
      {expanded && (
        <div className="channel-health__panel">
          <div className="channel-health__header">
            <span className="channel-health__title">通道健康监控</span>
            <button
              className="channel-health__close"
              onClick={() => setExpanded(false)}
            >
              ✕
            </button>
          </div>

          <div className="channel-health__channels">
            {channels.map(channel => (
              <div
                key={channel.channel}
                className={`channel-health__item ${channel.status}`}
                onClick={() => {
                  setSelectedChannel(channel);
                  onChannelClick?.(channel);
                }}
              >
                <span className="channel-health__item-icon">{CHANNEL_ICONS[channel.channel]}</span>
                <span className="channel-health__item-label">{CHANNEL_LABELS[channel.channel]}</span>
                <span
                  className="channel-health__item-dot"
                  style={{ background: STATUS_COLORS[channel.status] }}
                />
              </div>
            ))}
          </div>

          {/* 选中通道详情 */}
          {selectedChannel && (
            <div className="channel-health__detail">
              <div className="channel-detail__header">
                <span>{CHANNEL_ICONS[selectedChannel.channel]} {CHANNEL_LABELS[selectedChannel.channel]}</span>
                <span
                  className="channel-detail__status"
                  style={{ color: STATUS_COLORS[selectedChannel.status] }}
                >
                  {selectedChannel.status}
                </span>
              </div>
              <div className="channel-detail__stats">
                <div className="channel-stat">
                  <span className="channel-stat__label">连接数</span>
                  <span className="channel-stat__value">{selectedChannel.connectionCount}</span>
                </div>
                <div className="channel-stat">
                  <span className="channel-stat__label">延迟</span>
                  <span className="channel-stat__value">{selectedChannel.latencyMs}ms</span>
                </div>
                <div className="channel-stat">
                  <span className="channel-stat__label">自愈合次数</span>
                  <span className="channel-stat__value">{selectedChannel.selfHealingCount}</span>
                </div>
              </div>
              {selectedChannel.lastEvent && (
                <div className="channel-detail__event">
                  最近事件: {selectedChannel.lastEvent}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}