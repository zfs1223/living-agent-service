/**
 * P17: 技能市场页面
 *
 * 分类浏览所有可用技能，支持搜索、筛选、查看详情、绑定到个人助理
 */
import { useState, useEffect, useCallback } from 'react';
import SkillDetail from './SkillDetail';
import AgentSkillBinding from './AgentSkillBinding';
import './SkillMarketPage.css';

/** 技能分类 */
const SKILL_CATEGORIES = [
  { key: 'all', label: '全部', icon: '📦' },
  { key: 'tech', label: '技术', icon: '💻' },
  { key: 'communication', label: '沟通', icon: '💬' },
  { key: 'data', label: '数据', icon: '📊' },
  { key: 'automation', label: '自动化', icon: '🤖' },
  { key: 'department', label: '部门专属', icon: '🏢' },
];

export interface SkillInfo {
  id: string;
  name: string;
  description?: string;
  category?: string;
  enabled?: boolean;
  version?: string;
  tags?: string[];
  rating?: number;
  executionCount?: number;
  lastExecutedAt?: string;
}

export interface AgentInfo {
  id: string;
  name: string;
  skills?: string[];
}

interface SkillMarketPageProps {
  hasToken: boolean;
  backendUrl?: string;
}

export default function SkillMarketPage({ hasToken, backendUrl }: SkillMarketPageProps) {
  const [skills, setSkills] = useState<SkillInfo[]>([]);
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('all');
  const [selectedSkill, setSelectedSkill] = useState<SkillInfo | null>(null);
  const [showBinding, setShowBinding] = useState(false);
  const [tab, setTab] = useState<'market' | 'binding'>('market');

  const loadData = useCallback(async () => {
    if (!hasToken) { setLoading(false); return; }
    setLoading(true);
    setError('');
    try {
      const [skillList, agentList] = await Promise.all([
        window.livingAgentAPI.skill.list().catch(() => []),
        window.livingAgentAPI.agent.list().catch(() => []),
      ]);
      setSkills(Array.isArray(skillList) ? skillList : []);
      setAgents(Array.isArray(agentList) ? agentList : []);
    } catch (e: any) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [hasToken]);

  useEffect(() => { loadData(); }, [loadData]);

  // 搜索和分类过滤
  const filteredSkills = skills.filter(s => {
    const matchesCategory = activeCategory === 'all' || s.category === activeCategory;
    const matchesSearch = !searchQuery ||
      s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (s.description || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
      (s.tags || []).some(t => t.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  const handleBindSkill = async (agentId: string, skillId: string) => {
    try {
      await window.livingAgentAPI.skill.bind(agentId, skillId);
      // 刷新 agents 列表
      const agentList = await window.livingAgentAPI.agent.list().catch(() => []);
      setAgents(Array.isArray(agentList) ? agentList : []);
    } catch (e) {
      console.error('[SkillMarket] 绑定技能失败:', e);
    }
  };

  const handleUnbindSkill = async (agentId: string, skillId: string) => {
    try {
      await window.livingAgentAPI.skill.unbind(agentId, skillId);
      const agentList = await window.livingAgentAPI.agent.list().catch(() => []);
      setAgents(Array.isArray(agentList) ? agentList : []);
    } catch (e) {
      console.error('[SkillMarket] 解绑技能失败:', e);
    }
  };

  if (!hasToken) {
    return <div className="skill-market__empty">请先登录</div>;
  }
  if (loading) {
    return <div className="skill-market__empty">加载中...</div>;
  }
  if (error) {
    return <div className="skill-market__error">{error}</div>;
  }

  return (
    <div className="skill-market">
      {/* 标题栏 + Tab 切换 */}
      <div className="skill-market__header">
        <h1 className="skill-market__title">🛠️ 技能市场</h1>
        <div className="skill-market__tabs">
          <button
            className={`skill-market__tab ${tab === 'market' ? 'active' : ''}`}
            onClick={() => setTab('market')}
          >
            技能浏览
          </button>
          <button
            className={`skill-market__tab ${tab === 'binding' ? 'active' : ''}`}
            onClick={() => setTab('binding')}
          >
            助理技能管理
          </button>
        </div>
      </div>

      {/* 技能浏览 Tab */}
      {tab === 'market' && (
        <>
          {/* 搜索栏 */}
          <div className="skill-market__search">
            <input
              type="text"
              className="skill-market__search-input"
              placeholder="搜索技能名称、描述或标签..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          {/* 分类标签 */}
          <div className="skill-market__categories">
            {SKILL_CATEGORIES.map(cat => (
              <button
                key={cat.key}
                className={`skill-market__category ${activeCategory === cat.key ? 'active' : ''}`}
                onClick={() => setActiveCategory(cat.key)}
              >
                <span className="skill-market__category-icon">{cat.icon}</span>
                <span>{cat.label}</span>
              </button>
            ))}
          </div>

          {/* 技能列表 */}
          <div className="skill-market__grid">
            {filteredSkills.length === 0 && (
              <div className="skill-market__empty">暂无技能</div>
            )}
            {filteredSkills.map(skill => (
              <div
                key={skill.id || skill.name}
                className="skill-card"
                onClick={() => setSelectedSkill(skill)}
              >
                <div className="skill-card__header">
                  <span className="skill-card__name">{skill.name || skill.id}</span>
                  {skill.version && (
                    <span className="skill-card__version">v{skill.version}</span>
                  )}
                </div>
                {skill.description && (
                  <div className="skill-card__desc">{skill.description}</div>
                )}
                <div className="skill-card__footer">
                  {skill.category && (
                    <span className="skill-card__category">{skill.category}</span>
                  )}
                  {skill.tags && skill.tags.length > 0 && (
                    <div className="skill-card__tags">
                      {skill.tags.slice(0, 3).map(tag => (
                        <span key={tag} className="skill-card__tag">{tag}</span>
                      ))}
                    </div>
                  )}
                  <span className={`skill-card__status ${skill.enabled !== false ? 'enabled' : 'disabled'}`}>
                    {skill.enabled !== false ? '● 已启用' : '○ 未启用'}
                  </span>
                </div>
                {skill.rating !== undefined && (
                  <div className="skill-card__rating">
                    {'★'.repeat(Math.round(skill.rating))}{'☆'.repeat(5 - Math.round(skill.rating))}
                    <span className="skill-card__rating-count">({skill.executionCount || 0}次)</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {/* 助理技能绑定 Tab */}
      {tab === 'binding' && (
        <AgentSkillBinding
          agents={agents}
          skills={skills}
          onBind={handleBindSkill}
          onUnbind={handleUnbindSkill}
        />
      )}

      {/* 技能详情弹窗 */}
      {selectedSkill && (
        <SkillDetail
          skill={selectedSkill}
          agents={agents}
          onClose={() => setSelectedSkill(null)}
          onBind={handleBindSkill}
          onUnbind={handleUnbindSkill}
        />
      )}
    </div>
  );
}