/**
 * P17: 技能详情弹窗
 *
 * 显示技能完整信息、参数说明、进化历史、绑定到助理
 */
import { useState, useEffect } from 'react';
import type { SkillInfo, AgentInfo } from './SkillMarketPage';

interface SkillDetailProps {
  skill: SkillInfo;
  agents: AgentInfo[];
  onClose: () => void;
  onBind: (agentId: string, skillId: string) => Promise<void>;
  onUnbind: (agentId: string, skillId: string) => Promise<void>;
}

export default function SkillDetail({ skill, agents, onClose, onBind, onUnbind }: SkillDetailProps) {
  const [evolution, setEvolution] = useState<any[]>([]);
  const [loadingEvolution, setLoadingEvolution] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState('');
  const [binding, setBinding] = useState(false);

  // 加载技能进化历史
  useEffect(() => {
    if (!skill.id) return;
    setLoadingEvolution(true);
    // 调用 /api/skills/{id}/evolution
    fetch(`/api/skills/${encodeURIComponent(skill.id)}/evolution`)
      .then(res => res.ok ? res.json() : { data: [] })
      .then(data => setEvolution(data.data || data || []))
      .catch(() => setEvolution([]))
      .finally(() => setLoadingEvolution(false));
  }, [skill.id]);

  // 已绑定此技能的助理
  const boundAgents = agents.filter(a => a.skills?.includes(skill.id));

  const handleBind = async () => {
    if (!selectedAgent) return;
    setBinding(true);
    try {
      await onBind(selectedAgent, skill.id);
      setSelectedAgent('');
    } finally {
      setBinding(false);
    }
  };

  return (
    <div className="skill-detail-overlay" onClick={onClose}>
      <div className="skill-detail" onClick={e => e.stopPropagation()}>
        {/* 头部 */}
        <div className="skill-detail__header">
          <h2>{skill.name || skill.id}</h2>
          <button className="skill-detail__close" onClick={onClose}>✕</button>
        </div>

        {/* 基本信息 */}
        <div className="skill-detail__info">
          {skill.version && <div className="skill-detail__field"><span className="skill-detail__label">版本</span> v{skill.version}</div>}
          {skill.category && <div className="skill-detail__field"><span className="skill-detail__label">分类</span> {skill.category}</div>}
          <div className="skill-detail__field">
            <span className="skill-detail__label">状态</span>
            <span className={skill.enabled !== false ? 'skill-detail__enabled' : 'skill-detail__disabled'}>
              {skill.enabled !== false ? '已启用' : '未启用'}
            </span>
          </div>
          {skill.rating !== undefined && (
            <div className="skill-detail__field">
              <span className="skill-detail__label">评分</span>
              {'★'.repeat(Math.round(skill.rating))}{'☆'.repeat(5 - Math.round(skill.rating))} ({skill.rating.toFixed(1)})
            </div>
          )}
          {skill.executionCount !== undefined && (
            <div className="skill-detail__field"><span className="skill-detail__label">执行次数</span> {skill.executionCount}次</div>
          )}
        </div>

        {/* 描述 */}
        {skill.description && (
          <div className="skill-detail__section">
            <h3>说明</h3>
            <p className="skill-detail__description">{skill.description}</p>
          </div>
        )}

        {/* 标签 */}
        {skill.tags && skill.tags.length > 0 && (
          <div className="skill-detail__section">
            <h3>标签</h3>
            <div className="skill-detail__tags">
              {skill.tags.map(tag => <span key={tag} className="skill-detail__tag">{tag}</span>)}
            </div>
          </div>
        )}

        {/* 绑定到助理 */}
        <div className="skill-detail__section">
          <h3>绑定助理</h3>

          {/* 已绑定的助理 */}
          {boundAgents.length > 0 && (
            <div className="skill-detail__bound-agents">
              {boundAgents.map(agent => (
                <div key={agent.id} className="skill-detail__bound-agent">
                  <span>{agent.name}</span>
                  <button
                    className="skill-detail__unbind-btn"
                    onClick={() => onUnbind(agent.id, skill.id)}
                  >
                    解绑
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* 添加绑定 */}
          <div className="skill-detail__bind-form">
            <select
              className="skill-detail__agent-select"
              value={selectedAgent}
              onChange={e => setSelectedAgent(e.target.value)}
            >
              <option value="">选择助理...</option>
              {agents
                .filter(a => !a.skills?.includes(skill.id))
                .map(agent => (
                  <option key={agent.id} value={agent.id}>{agent.name}</option>
                ))}
            </select>
            <button
              className="skill-detail__bind-btn"
              disabled={!selectedAgent || binding}
              onClick={handleBind}
            >
              {binding ? '绑定中...' : '绑定'}
            </button>
          </div>
        </div>

        {/* 进化历史 */}
        <div className="skill-detail__section">
          <h3>进化历史</h3>
          {loadingEvolution ? (
            <div className="skill-detail__loading">加载中...</div>
          ) : evolution.length === 0 ? (
            <div className="skill-detail__empty">暂无进化记录</div>
          ) : (
            <div className="skill-detail__evolution">
              {evolution.map((record: any, i: number) => (
                <div key={i} className="skill-detail__evolution-item">
                  <div className="skill-detail__evolution-version">
                    v{record.version || record.toVersion || '?'}
                  </div>
                  <div className="skill-detail__evolution-desc">
                    {record.description || record.changes || '无描述'}
                  </div>
                  <div className="skill-detail__evolution-time">
                    {record.timestamp || record.createdAt || ''}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}