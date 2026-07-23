/**
 * P17: 个人助理技能绑定管理
 *
 * 显示每个个人助理的已绑定技能，支持添加/移除
 */
import { useState } from 'react';
import type { SkillInfo, AgentInfo } from './SkillMarketPage';

interface AgentSkillBindingProps {
  agents: AgentInfo[];
  skills: SkillInfo[];
  onBind: (agentId: string, skillId: string) => Promise<void>;
  onUnbind: (agentId: string, skillId: string) => Promise<void>;
}

export default function AgentSkillBinding({ agents, skills, onBind, onUnbind }: AgentSkillBindingProps) {
  const [expandedAgent, setExpandedAgent] = useState<string | null>(null);
  const [addingSkillFor, setAddingSkillFor] = useState<string | null>(null);
  const [selectedSkill, setSelectedSkill] = useState('');
  const [binding, setBinding] = useState(false);

  // 只显示个人助理
  const personalAgents = agents.filter(a =>
    (a as any).origin === 'personal' || (a as any).type === 'personal'
  );

  const handleBind = async (agentId: string) => {
    if (!selectedSkill) return;
    setBinding(true);
    try {
      await onBind(agentId, selectedSkill);
      setAddingSkillFor(null);
      setSelectedSkill('');
    } finally {
      setBinding(false);
    }
  };

  if (personalAgents.length === 0) {
    return (
      <div className="agent-skill-binding__empty">
        暂无个人助理，请先创建个人助理
      </div>
    );
  }

  return (
    <div className="agent-skill-binding">
      {personalAgents.map(agent => {
        const isExpanded = expandedAgent === agent.id;
        const agentSkills = skills.filter(s => agent.skills?.includes(s.id));

        return (
          <div key={agent.id} className="agent-binding-card">
            <div
              className="agent-binding-card__header"
              onClick={() => setExpandedAgent(isExpanded ? null : agent.id)}
            >
              <span className="agent-binding-card__name">{agent.name}</span>
              <span className="agent-binding-card__count">
                {agentSkills.length} 个技能
              </span>
              <span className="agent-binding-card__expand">
                {isExpanded ? '▼' : '▶'}
              </span>
            </div>

            {isExpanded && (
              <div className="agent-binding-card__body">
                {/* 已绑定技能列表 */}
                {agentSkills.length > 0 ? (
                  <div className="agent-binding-card__skills">
                    {agentSkills.map(skill => (
                      <div key={skill.id} className="agent-binding-card__skill">
                        <span className="agent-binding-card__skill-name">{skill.name}</span>
                        <span className="agent-binding-card__skill-desc">{skill.description || ''}</span>
                        <button
                          className="agent-binding-card__remove-btn"
                          onClick={() => onUnbind(agent.id, skill.id)}
                        >
                          ✕
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="agent-binding-card__no-skills">
                    暂未绑定技能
                  </div>
                )}

                {/* 添加技能 */}
                {addingSkillFor === agent.id ? (
                  <div className="agent-binding-card__add-form">
                    <select
                      className="agent-binding-card__skill-select"
                      value={selectedSkill}
                      onChange={e => setSelectedSkill(e.target.value)}
                    >
                      <option value="">选择技能...</option>
                      {skills
                        .filter(s => !agent.skills?.includes(s.id))
                        .map(s => (
                          <option key={s.id} value={s.id}>{s.name}</option>
                        ))}
                    </select>
                    <button
                      className="agent-binding-card__add-confirm"
                      disabled={!selectedSkill || binding}
                      onClick={() => handleBind(agent.id)}
                    >
                      {binding ? '绑定中...' : '确认'}
                    </button>
                    <button
                      className="agent-binding-card__add-cancel"
                      onClick={() => { setAddingSkillFor(null); setSelectedSkill(''); }}
                    >
                      取消
                    </button>
                  </div>
                ) : (
                  <button
                    className="agent-binding-card__add-btn"
                    onClick={() => setAddingSkillFor(agent.id)}
                  >
                    + 添加技能
                  </button>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}