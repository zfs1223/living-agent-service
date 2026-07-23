/**
 * P9: 单个 Agent 进度卡片组件
 *
 * 显示单个部门大脑的执行状态和进度
 */
import { useState } from 'react';
import './AgentProgressCards.css';

export type AgentStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

export interface AgentCardData {
  agentId: string;
  agentName: string;
  department?: string;
  task?: string;
  status: AgentStatus;
  progress?: number; // 0-100
  message?: string;
  startedAt?: string;
  completedAt?: string;
}

interface AgentCardProps {
  data: AgentCardData;
  onInterrupt?: (agentId: string) => void;
  onRedirect?: (agentId: string) => void;
}

const STATUS_COLORS: Record<AgentStatus, string> = {
  pending: '#8c8c8c',
  running: '#1890ff',
  completed: '#52c41a',
  failed: '#ff4d4f',
  cancelled: '#faad14'
};

const STATUS_LABELS: Record<AgentStatus, string> = {
  pending: '等待中',
  running: '执行中',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消'
};

const DEPARTMENT_ICONS: Record<string, string> = {
  tech: '🧠',
  hr: '👥',
  finance: '💰',
  sales: '📈',
  cs: '🎧',
  admin: '🛠️',
  legal: '⚖️',
  ops: '🚚',
  core: '⭐'
};

export default function AgentCard({ data, onInterrupt, onRedirect }: AgentCardProps) {
  const [expanded, setExpanded] = useState(false);

  const statusColor = STATUS_COLORS[data.status];
  const statusLabel = STATUS_LABELS[data.status];
  const deptIcon = data.department ? DEPARTMENT_ICONS[data.department] || '🤖' : '🤖';

  return (
    <div className={`agent-card agent-card--${data.status}`}>
      <div className="agent-card__header" onClick={() => setExpanded(!expanded)}>
        <div className="agent-card__icon" style={{ background: statusColor }}>
          {deptIcon}
        </div>

        <div className="agent-card__info">
          <div className="agent-card__name">{data.agentName}</div>
          {data.task && <div className="agent-card__task">{data.task}</div>}
        </div>

        <div className="agent-card__status" style={{ color: statusColor }}>
          {statusLabel}
        </div>

        {data.status === 'running' && (
          <div className="agent-card__progress-bar">
            <div
              className="agent-card__progress-fill"
              style={{ width: `${data.progress || 0}%`, background: statusColor }}
            />
          </div>
        )}

        <div className={`agent-card__expand ${expanded ? 'agent-card__expand--expanded' : ''}`}>
          ▼
        </div>
      </div>

      {expanded && (
        <div className="agent-card__details">
          {data.message && (
            <div className="agent-card__message">{data.message}</div>
          )}

          <div className="agent-card__meta">
            <span className="agent-card__meta-item">
              <span className="agent-card__meta-label">ID:</span>
              <span className="agent-card__meta-value">{data.agentId.slice(0, 12)}...</span>
            </span>
            {data.startedAt && (
              <span className="agent-card__meta-item">
                <span className="agent-card__meta-label">开始:</span>
                <span className="agent-card__meta-value">
                  {new Date(data.startedAt).toLocaleTimeString()}
                </span>
              </span>
            )}
            {data.completedAt && (
              <span className="agent-card__meta-item">
                <span className="agent-card__meta-label">完成:</span>
                <span className="agent-card__meta-value">
                  {new Date(data.completedAt).toLocaleTimeString()}
                </span>
              </span>
            )}
          </div>

          {(data.status === 'running' || data.status === 'pending') && onInterrupt && (
            <div className="agent-card__actions">
              <button
                className="agent-card__btn agent-card__btn--interrupt"
                onClick={(e) => {
                  e.stopPropagation();
                  onInterrupt(data.agentId);
                }}
              >
                ⏹ 中断
              </button>
              {onRedirect && (
                <button
                  className="agent-card__btn agent-card__btn--redirect"
                  onClick={(e) => {
                    e.stopPropagation();
                    onRedirect(data.agentId);
                  }}
                >
                  ↻ 重定向
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}