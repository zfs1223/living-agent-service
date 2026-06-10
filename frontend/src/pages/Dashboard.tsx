import { useState, useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { agentApi, taskApi, activityApi } from '../services/api';
import { useAuthStore } from '../stores';
import { usePolling } from '../hooks/usePolling';
import EnterpriseDashboard from './Dashboard/EnterpriseDashboard';
import type { Agent, Task } from '../types';

/* ────── Inline SVG Icons (monochrome) ────── */

const Icons = {
    users: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="6" cy="5" r="2.5" />
            <path d="M1.5 14v-1a3.5 3.5 0 017 0v1" />
            <circle cx="11.5" cy="5.5" r="2" />
            <path d="M14.5 14v-.5a3 3 0 00-3-3" />
        </svg>
    ),
    tasks: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="2" width="12" height="12" rx="2" />
            <path d="M5.5 8l2 2 3.5-3.5" />
        </svg>
    ),
    zap: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8.5 1.5L3 9h4.5l-.5 5.5L13 7H8.5l.5-5.5z" />
        </svg>
    ),
    clock: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="6" />
            <path d="M8 4.5V8l2.5 1.5" />
        </svg>
    ),
    activity: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M1 8h3l2-5 3 10 2-5h4" />
        </svg>
    ),
    trendingUp: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M1.5 11.5l4-4 3 3 6-6" />
            <path d="M10.5 4.5h4v4" />
        </svg>
    ),
    coin: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="5.5" />
            <path d="M8 5.5v5M6.5 6.5c0-.8.7-1.5 1.5-1.5s1.5.7 1.5 1.5S8.8 8 8 8s-1.5.7-1.5 1.5S7.2 11 8 11s1.5-.7 1.5-1.5" />
        </svg>
    ),
    plus: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <path d="M8 3v10M3 8h10" />
        </svg>
    ),
    bot: (
        <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="5" width="12" height="10" rx="2" />
            <circle cx="7" cy="10" r="1" fill="currentColor" stroke="none" />
            <circle cx="11" cy="10" r="1" fill="currentColor" stroke="none" />
            <path d="M9 2v3M6 2h6" />
        </svg>
    ),
};

/* ────── Helpers ────── */

const timeAgo = (dateStr: string | undefined, t: any) => {
    if (!dateStr) return '-';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return t('dashboard.justNow');
    if (mins < 60) return t('dashboard.minutesAgo', { count: mins });
    const hours = Math.floor(mins / 60);
    if (hours < 24) return t('dashboard.hoursAgo', { count: hours });
    return t('dashboard.daysAgo', { count: Math.floor(hours / 24) });
};

const priorityColor = (p: string) => {
    switch (p) {
        case 'urgent': return 'var(--error)';
        case 'high': return 'var(--warning)';
        case 'medium': return 'var(--accent-primary)';
        default: return 'var(--text-tertiary)';
    }
};

const statusLabel = (s: string, t: any) => {
    switch (s) {
        case 'running': return t('dashboard.status.running');
        case 'idle': return t('dashboard.status.idle');
        case 'stopped': return t('dashboard.status.stopped');
        case 'error': return t('dashboard.status.error');
        case 'creating': return t('dashboard.status.creating');
        case 'disconnected': return t('dashboard.status.disconnected');
        default: return s;
    }
};

const statusColor = (s: string) => {
    switch (s) {
        case 'running': return 'var(--status-running)';
        case 'idle': return 'var(--status-idle)';
        case 'error': return 'var(--status-error)';
        case 'stopped': return 'var(--status-stopped)';
        default: return 'var(--text-tertiary)';
    }
};

const formatTokens = (n: number) => {
    if (!n) return '0';
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
    return String(n);
};

/* ────── Summary Stats Bar ────── */

function StatsBar({ agents, allTasks }: { agents: Agent[]; allTasks: Task[] }) {
    const { t } = useTranslation();
    const totalAgents = agents.length;
    const activeAgents = agents.filter(a => a.status === 'running' || a.status === 'idle').length;
    const pendingTasks = allTasks.filter(t => t.status === 'pending' || t.status === 'doing').length;
    const completedToday = allTasks.filter(t => {
        if (t.status !== 'done' || !t.completed_at) return false;
        const today = new Date();
        const completed = new Date(t.completed_at);
        return completed.toDateString() === today.toDateString();
    }).length;
    const totalTokensToday = agents.reduce((sum, a) => sum + (a.tokens_used_today || 0), 0);
    const recentlyActive = agents.filter(a => {
        if (!a.last_active_at) return false;
        return Date.now() - new Date(a.last_active_at).getTime() < 3600000;
    }).length;
    const atRiskAgents = agents.filter(a => a.status === 'error' || a.status === 'disconnected' || a.status === 'stopped').length;
    const totalTasks = allTasks.length;

    const stats = [
        { icon: Icons.users, label: t('dashboard.stats.agents'), value: totalAgents, sub: t('dashboard.stats.online', { count: activeAgents }) },
        { icon: Icons.tasks, label: t('dashboard.stats.activeTasks'), value: pendingTasks, sub: t('dashboard.stats.completedToday', { count: completedToday }) },
        { icon: Icons.zap, label: t('dashboard.stats.todayTokens'), value: formatTokens(totalTokensToday), sub: t('dashboard.stats.allAgentsTotal') },
        { icon: Icons.clock, label: t('dashboard.stats.recentlyActive'), value: recentlyActive, sub: t('dashboard.stats.lastHour') },
    ];

    const enterpriseSignals = [
        { icon: Icons.activity, label: t('dashboard.enterprise.totalTasks', 'Total Tasks'), value: totalTasks, sub: t('dashboard.enterprise.totalTasksSub', 'All agent tasks currently tracked') },
        { icon: Icons.users, label: t('dashboard.enterprise.atRisk', 'Risk / Offline'), value: atRiskAgents, sub: t('dashboard.enterprise.atRiskSub', 'Needs attention') },
        { icon: Icons.coin, label: t('dashboard.enterprise.costHint', 'Cost / Tokens'), value: formatTokens(totalTokensToday), sub: t('dashboard.enterprise.costHintSub', 'Today') },
        { icon: Icons.trendingUp, label: t('dashboard.enterprise.execution', 'Execution'), value: activeAgents, sub: t('dashboard.enterprise.executionSub', 'Running or idle') },
    ];

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1px',
                background: 'var(--border-subtle)', borderRadius: '20px',
                overflow: 'hidden',
                border: '1px solid var(--border-subtle)',
            }}>
            {stats.map((s, i) => (
                <div key={i} style={{
                    background: 'var(--bg-secondary)', padding: '16px 20px',
                    display: 'flex', flexDirection: 'column', gap: '2px',
                }}>
                    <div style={{
                        fontSize: '12px', color: 'var(--text-tertiary)',
                        display: 'flex', alignItems: 'center', gap: '6px',
                        marginBottom: '4px',
                    }}>
                        <span style={{ display: 'flex', opacity: 0.7 }}>{s.icon}</span> {s.label}
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>
                        {s.value}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{s.sub}</div>
                </div>
            ))}
            </div>

            <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1px',
                background: 'var(--border-subtle)', borderRadius: '20px',
                overflow: 'hidden',
                border: '1px solid var(--border-subtle)',
            }}>
            {enterpriseSignals.map((s, i) => (
                <div key={i} style={{
                    background: 'var(--bg-secondary)', padding: '16px 20px',
                    display: 'flex', flexDirection: 'column', gap: '2px',
                }}>
                    <div style={{
                        fontSize: '12px', color: 'var(--text-tertiary)',
                        display: 'flex', alignItems: 'center', gap: '6px',
                        marginBottom: '4px',
                    }}>
                        <span style={{ display: 'flex', opacity: 0.7 }}>{s.icon}</span> {s.label}
                    </div>
                    <div style={{ fontSize: '24px', fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>
                        {s.value}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{s.sub}</div>
                </div>
            ))}
            </div>
        </div>
    );
}

/* ────── Agent Row ────── */

function AgentRow({ agent, tasks, recentActivity }: {
    agent: Agent;
    tasks: Task[];
    recentActivity: any[];
}) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const pendingTasks = tasks.filter(t => t.status === 'pending' || t.status === 'doing');
    const latestActivity = recentActivity[0];

    // Token usage bar
    const maxTokens = agent.max_tokens_per_day || 0;
    const usedTokens = agent.tokens_used_today || 0;
    const tokenPct = maxTokens > 0 ? Math.min(100, (usedTokens / maxTokens) * 100) : 0;

    return (
        <div
            onClick={() => navigate(`/agents/${agent.id}`)}
            style={{
                display: 'grid',
                gridTemplateColumns: '220px 1fr 150px 100px',
                alignItems: 'center', gap: '16px',
                padding: '12px 16px',
                borderRadius: 'var(--radius-md)',
                cursor: 'pointer', transition: 'background 120ms ease',
            }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
        >
            {/* Agent Info */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
                <div style={{
                    width: '32px', height: '32px', borderRadius: 'var(--radius-md)',
                    background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: 'var(--text-tertiary)', flexShrink: 0,
                }}>
                    {Icons.bot}
                </div>
                <div style={{ minWidth: 0 }}>
                    <div style={{
                        fontWeight: 500, fontSize: '13px', display: 'flex',
                        alignItems: 'center', gap: '8px', color: 'var(--text-primary)',
                    }}>
                        {agent.name}
                        <span style={{
                            display: 'inline-flex', alignItems: 'center', gap: '4px',
                            fontSize: '11px', fontWeight: 400,
                            color: statusColor(agent.status),
                        }}>
                            <span style={{
                                width: '6px', height: '6px', borderRadius: '50%',
                                background: statusColor(agent.status),
                                display: 'inline-block',
                            }} />
                            {statusLabel(agent.status, t)}
                        </span>
                    </div>
                    <div style={{
                        fontSize: '12px', color: 'var(--text-tertiary)',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    }}>
                        {agent.role_description || '-'}
                    </div>
                </div>
            </div>

            {/* Latest Activity / Tasks */}
            <div style={{ minWidth: 0 }}>
                {latestActivity ? (
                    <div style={{
                        fontSize: '12px', color: 'var(--text-secondary)',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    }}>
                        <span style={{ color: 'var(--text-tertiary)', marginRight: '6px' }}>
                            {timeAgo(latestActivity.created_at, t)}
                        </span>
                        {latestActivity.summary}
                    </div>
                ) : (
                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('dashboard.noActivity')}</div>
                )}
                {pendingTasks.length > 0 && (
                    <div style={{ display: 'flex', gap: '4px', marginTop: '4px', flexWrap: 'wrap' }}>
                        {pendingTasks.slice(0, 3).map(t => (
                            <span key={t.id} style={{
                                fontSize: '11px', padding: '1px 6px',
                                borderRadius: 'var(--radius-sm)', background: 'var(--bg-tertiary)',
                                color: 'var(--text-secondary)', maxWidth: '140px',
                                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                display: 'inline-flex', alignItems: 'center', gap: '3px',
                            }}>
                                <span style={{ width: '4px', height: '4px', borderRadius: '50%', background: priorityColor(t.priority), flexShrink: 0 }} />
                                {t.title}
                            </span>
                        ))}
                        {pendingTasks.length > 3 && (
                            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', padding: '1px 4px' }}>
                                +{pendingTasks.length - 3}
                            </span>
                        )}
                    </div>
                )}
            </div>

            {/* Token Usage */}
            <div>
                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '3px' }}>
                    {formatTokens(usedTokens)}
                    {maxTokens > 0 && <span style={{ opacity: 0.6 }}> / {formatTokens(maxTokens)}</span>}
                </div>
                {maxTokens > 0 ? (
                    <div style={{
                        height: '3px', background: 'var(--bg-tertiary)',
                        borderRadius: '2px', overflow: 'hidden',
                    }}>
                        <div style={{
                            height: '100%', borderRadius: '2px',
                            width: `${tokenPct}%`,
                            background: tokenPct > 80 ? 'var(--error)' : tokenPct > 50 ? 'var(--warning)' : 'var(--text-tertiary)',
                            transition: 'width 0.3s',
                        }} />
                    </div>
                ) : (
                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', opacity: 0.5 }}>{t('dashboard.noLimit')}</div>
                )}
            </div>

            {/* Last Active */}
            <div style={{ textAlign: 'right', fontSize: '12px', color: 'var(--text-tertiary)' }}>
                {timeAgo(agent.last_active_at, t)}
            </div>
        </div>
    );
}

/* ────── Recent Activity Feed ────── */

function ActivityFeed({ activities, agents }: { activities: any[]; agents: Agent[] }) {
    const { t } = useTranslation();
    const agentMap = new Map(agents.map(a => [a.id, a]));

    if (activities.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                {t('dashboard.noActivity')}
            </div>
        );
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
            {activities.map((act, i) => {
                const agent = agentMap.get(act.agent_id);
                return (
                    <div key={act.id || i} style={{
                        display: 'flex', gap: '12px', padding: '7px 12px',
                        fontSize: '13px', alignItems: 'flex-start',
                    }}>
                        <span style={{
                            color: 'var(--text-tertiary)', whiteSpace: 'nowrap',
                            fontFamily: 'var(--font-mono)', fontSize: '11px',
                            minWidth: '52px', paddingTop: '2px',
                        }}>
                            {timeAgo(act.created_at, t)}
                        </span>
                        <span style={{
                            fontSize: '11px', padding: '1px 6px',
                            borderRadius: 'var(--radius-sm)', background: 'var(--bg-tertiary)',
                            color: 'var(--text-secondary)', whiteSpace: 'nowrap', flexShrink: 0,
                            fontWeight: 500,
                        }}>
                            {agent?.name || act.agent_id?.slice(0, 6)}
                        </span>
                        <span style={{
                            color: 'var(--text-secondary)', flex: 1,
                            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                        }}>
                            {act.summary}
                        </span>
                    </div>
                );
            })}
        </div>
    );
}

/* ────── Main Dashboard ────── */

export default function Dashboard() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);
    const currentTenant = localStorage.getItem('current_tenant_id') || '';

    if (!user) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                    {t('common.loading')}
                </div>
            </div>
        );
    }

    if (user.identity === 'INTERNAL_ENTERPRISE' || user.access_level === 'FULL') {
        return <EnterpriseDashboard />;
    }

    if (user.department_code) {
        navigate(`/departments/${encodeURIComponent(user.department_code)}/overview`, { replace: true });
        return null;
    }

    if (user.access_level === 'LIMITED' || user.access_level === 'CHAT_ONLY') {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                <div style={{
                    borderRadius: '24px',
                    padding: '22px',
                    background: 'linear-gradient(135deg, rgba(24,144,255,0.12), rgba(12,18,28,0.82) 45%, rgba(5,6,10,0.96) 100%)',
                    border: '1px solid rgba(255,255,255,0.08)',
                    boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
                }}>
                    <h1 style={{ fontSize: '28px', lineHeight: 1.1, fontWeight: 700, margin: 0, color: 'var(--text-primary)', letterSpacing: '-0.04em' }}>
                        {t('dashboard.workspace.title', '个人工作台')}
                    </h1>
                    <p style={{ margin: '12px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75 }}>
                        {t('dashboard.workspace.subtitle', '查看您的任务和可访问的数字员工')}
                    </p>
                </div>
            </div>
        );
    }

    const { data: agents = [], isLoading } = useQuery({
        queryKey: ['agents', currentTenant],
        queryFn: () => agentApi.list(currentTenant || undefined),
        refetchInterval: 15000,
    });

    // Fetch tasks & activities for all agents
    const [allTasks, setAllTasks] = useState<Task[]>([]);
    const [allActivities, setAllActivities] = useState<any[]>([]);
    const [agentActivities, setAgentActivities] = useState<Record<string, any[]>>({});

    const fetchData = useCallback(async () => {
        try {
            const taskResults = await Promise.allSettled(agents.map(a => taskApi.list(a.id)));
            const tasks: Task[] = [];
            taskResults.forEach(r => { if (r.status === 'fulfilled') tasks.push(...r.value); });
            setAllTasks(tasks);
        } catch (e) { console.error('Failed to fetch tasks:', e); }

        try {
            const actResults = await Promise.allSettled(agents.map(a => activityApi.list(a.id, 5)));
            const activities: any[] = [];
            const perAgent: Record<string, any[]> = {};
            actResults.forEach((r, i) => {
                if (r.status === 'fulfilled') {
                    perAgent[agents[i].id] = r.value;
                    activities.push(...r.value.map((v: any) => ({ ...v, agent_id: agents[i].id })));
                }
            });
            activities.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
            setAllActivities(activities.slice(0, 20));
            setAgentActivities(perAgent);
        } catch (e) { console.error('Failed to fetch activities:', e); }
    }, [agents]);

    useEffect(() => { if (agents.length > 0) fetchData(); }, [agents.length, fetchData]);

    usePolling(fetchData, 30000, agents.length > 0);

    // Group tasks by agent
    const tasksByAgent = new Map<string, Task[]>();
    allTasks.forEach(t => {
        if (!tasksByAgent.has(t.agent_id)) tasksByAgent.set(t.agent_id, []);
        tasksByAgent.get(t.agent_id)!.push(t);
    });

    const activeAgents = agents.filter(a => a.status === 'running' || a.status === 'idle').length;
    const atRiskAgents = agents.filter(a => a.status === 'error' || a.status === 'disconnected' || a.status === 'stopped').length;

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(24,144,255,0.12), rgba(12,18,28,0.82) 45%, rgba(5,6,10,0.96) 100%)',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
                position: 'relative',
                overflow: 'hidden',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '16px', marginBottom: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '720px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(24,144,255,0.9)' }} />
                            {t('dashboard.enterprise.overviewBadge', 'Enterprise Operations Overview')}
                        </div>
                        <h1 style={{ fontSize: '28px', lineHeight: 1.1, fontWeight: 700, margin: 0, color: 'var(--text-primary)', letterSpacing: '-0.04em' }}>
                            {t('dashboard.enterprise.title', '董事长经营总览')}
                        </h1>
                        <p style={{ margin: '12px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75, maxWidth: '68ch' }}>
                            {t('dashboard.enterprise.subtitle', '这里汇总公司运转、任务执行、数字员工产出、风险信号和成本趋势，帮助董事长快速判断业务健康度与战略节奏。')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gap: '10px', minWidth: '260px', alignSelf: 'stretch' }}>
                        <div style={{ padding: '14px 16px', borderRadius: '18px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>{t('dashboard.enterprise.focus', 'Current Focus')}</div>
                            <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>{t('dashboard.enterprise.focusValue', '公司运行与收益监控')}</div>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.enterprise.activeAgents', 'Active Agents')}</div>
                                <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{activeAgents}</div>
                            </div>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.enterprise.riskAgents', 'Risk')}</div>
                                <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: atRiskAgents > 0 ? 'var(--warning)' : 'var(--success)' }}>{atRiskAgents}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {isLoading ? (
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                    {t('common.loading')}
                </div>
            ) : agents.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '80px', border: '1px solid var(--border-subtle)', borderRadius: '24px', background: 'var(--bg-secondary)' }}>
                    <div style={{ color: 'var(--text-tertiary)', marginBottom: '4px', fontSize: '32px' }}>
                        {Icons.bot}
                    </div>
                    <div style={{ color: 'var(--text-secondary)', marginBottom: '16px', fontSize: '14px' }}>
                        {t('dashboard.noAgents')}
                    </div>
                    <button className="btn btn-primary" onClick={() => navigate('/agents/new')}>
                        {Icons.plus} {t('nav.newAgent')}
                    </button>
                </div>
            ) : (
                <>
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: '24px',
                        overflow: 'hidden',
                        background: 'rgba(255,255,255,0.02)',
                    }}>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: '220px 1fr 150px 100px',
                            padding: '10px 16px',
                            fontSize: '11px', color: 'var(--text-tertiary)', fontWeight: 500,
                            textTransform: 'uppercase' as const, letterSpacing: '0.05em',
                            borderBottom: '1px solid var(--border-subtle)',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span>{t('dashboard.table.agent')}</span>
                            <span>{t('dashboard.table.latestActivity')}</span>
                            <span>Token</span>
                            <span style={{ textAlign: 'right' }}>{t('dashboard.table.active')}</span>
                        </div>

                        <div style={{ maxHeight: '350px', overflowY: 'auto' }}>
                            {agents
                                .sort((a, b) => {
                                    const aActive = a.status === 'running' || a.status === 'idle' ? 1 : 0;
                                    const bActive = b.status === 'running' || b.status === 'idle' ? 1 : 0;
                                    if (aActive !== bActive) return bActive - aActive;
                                    const aTime = a.last_active_at ? new Date(a.last_active_at).getTime() : 0;
                                    const bTime = b.last_active_at ? new Date(b.last_active_at).getTime() : 0;
                                    return bTime - aTime;
                                })
                                .map(agent => (
                                    <AgentRow
                                        key={agent.id}
                                        agent={agent}
                                        tasks={tasksByAgent.get(agent.id) || []}
                                        recentActivity={agentActivities[agent.id] || []}
                                    />
                                ))}
                        </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1.15fr 0.85fr', gap: '18px' }}>
                        <div style={{
                            border: '1px solid var(--border-subtle)',
                            borderRadius: '24px', overflow: 'hidden', background: 'rgba(255,255,255,0.02)',
                        }}>
                            <div style={{
                                padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                background: 'rgba(255,255,255,0.03)',
                            }}>
                                <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
                                    <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.activity}</span>
                                    {t('dashboard.globalActivity')}
                                </h3>
                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.recentCount', { count: 20 })}</span>
                            </div>
                            <div style={{ padding: '4px', maxHeight: '320px', overflowY: 'auto' }}>
                                <ActivityFeed activities={allActivities} agents={agents} />
                            </div>
                        </div>

                        <div style={{
                            border: '1px solid var(--border-subtle)',
                            borderRadius: '24px', background: 'rgba(255,255,255,0.02)', overflow: 'hidden',
                        }}>
                            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.03)' }}>
                                <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                    {t('dashboard.enterprise.quickActions', 'Quick Actions')}
                                </h3>
                            </div>
                            <div style={{ padding: '14px', display: 'grid', gap: '10px' }}>
                                <button className="btn btn-secondary" onClick={() => navigate('/enterprise')} style={{ justifyContent: 'space-between' }}>
                                    <span>{t('dashboard.enterprise.settings', 'Company Settings')}</span>
                                    <span>↗</span>
                                </button>
                                <button className="btn btn-secondary" onClick={() => navigate('/agents/new')} style={{ justifyContent: 'space-between' }}>
                                    <span>{t('nav.newAgent')}</span>
                                    <span>+</span>
                                </button>
                                <button className="btn btn-secondary" onClick={() => navigate('/projects')} style={{ justifyContent: 'space-between' }}>
                                    <span>{t('nav.projects', 'Projects')}</span>
                                    <span>↗</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
