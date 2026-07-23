/**
 * P9: 多 Agent 并行卡片容器
 *
 * 显示多个部门大脑并行执行的实时进度
 */
import { useState, useEffect } from 'react';
import AgentCard, { type AgentCardData, type AgentStatus } from './AgentCard';
import './AgentProgressCards.css';

interface AgentProgressCardsProps {
  agents?: AgentCardData[];
  onInterrupt?: (agentId: string) => void;
  onRedirect?: (agentId: string) => void;
  onClose?: () => void;
}

export default function AgentProgressCards({
  agents = [],
  onInterrupt,
  onRedirect,
  onClose
}: AgentProgressCardsProps) {
  const [collapsed, setCollapsed] = useState(false);

  // 计算统计信息
  const runningCount = agents.filter(a => a.status === 'running').length;
  const completedCount = agents.filter(a => a.status === 'completed').length;
  const failedCount = agents.filter(a => a.status === 'failed').length;
  const totalProgress = agents.length > 0
    ? Math.round(agents.reduce((sum, a) => sum + (a.progress || 0), 0) / agents.length)
    : 0;

  // 全部完成或失败后自动折叠
  useEffect(() => {
    if (agents.length > 0 && runningCount === 0) {
      const timer = setTimeout(() => setCollapsed(true), 2000);
      return () => clearTimeout(timer);
    }
  }, [agents.length, runningCount]);

  if (agents.length === 0) return null;

  return (
    <div className={`agent-progress-cards ${collapsed ? 'agent-progress-cards--collapsed' : ''}`}>
      <div className="agent-progress-cards__header">
        <div className="agent-progress-cards__title">
          <span className="agent-progress-cards__icon">🤖</span>
          <span>并行执行</span>
          <span className="agent-progress-cards__count">{agents.length}</span>
        </div>

        <div className="agent-progress-cards__stats">
          {runningCount > 0 && (
            <span className="agent-progress-cards__stat agent-progress-cards__stat--running">
              🔄 {runningCount} 执行中
            </span>
          )}
          {completedCount > 0 && (
            <span className="agent-progress-cards__stat agent-progress-cards__stat--completed">
              ✅ {completedCount} 完成
            </span>
          )}
          {failedCount > 0 && (
            <span className="agent-progress-cards__stat agent-progress-cards__stat--failed">
              ❌ {failedCount} 失败
            </span>
          )}
        </div>

        <div className="agent-progress-cards__progress">
          <div className="agent-progress-cards__progress-bar">
            <div
              className="agent-progress-cards__progress-fill"
              style={{ width: `${totalProgress}%` }}
            />
          </div>
          <span className="agent-progress-cards__progress-text">{totalProgress}%</span>
        </div>

        <div className="agent-progress-cards__actions">
          <button
            className="agent-progress-cards__toggle"
            onClick={() => setCollapsed(!collapsed)}
            title={collapsed ? '展开' : '折叠'}
          >
            {collapsed ? '◀' : '▼'}
          </button>
          {onClose && (
            <button
              className="agent-progress-cards__close"
              onClick={onClose}
              title="关闭"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {!collapsed && (
        <div className="agent-progress-cards__content">
          {agents.map(agent => (
            <AgentCard
              key={agent.agentId}
              data={agent}
              onInterrupt={onInterrupt}
              onRedirect={onRedirect}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// 导出类型供外部使用
export { type AgentCardData, type AgentStatus };