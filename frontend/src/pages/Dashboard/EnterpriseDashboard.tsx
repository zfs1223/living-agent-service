import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { dashboardApi } from '../../services/dashboardApi';
import { useAuthStore } from '../../stores';
import type { EnterpriseSummary, DepartmentHealth, RiskAlert } from '../../services/dashboardApi';
import VitalSignsDashboard from '../../components/VitalSignsDashboard';

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
    coin: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="5.5" />
            <path d="M8 5.5v5M6.5 6.5c0-.8.7-1.5 1.5-1.5s1.5.7 1.5 1.5S8.8 8 8 8s-1.5.7-1.5 1.5S7.2 11 8 11s1.5-.7 1.5-1.5" />
        </svg>
    ),
    activity: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M1 8h3l2-5 3 10 2-5h4" />
        </svg>
    ),
    alertTriangle: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8 2L1 14h14L8 2z" />
            <path d="M8 6v4M8 11.5v.5" />
        </svg>
    ),
    trendingUp: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M1.5 11.5l4-4 3 3 6-6" />
            <path d="M10.5 4.5h4v4" />
        </svg>
    ),
    settings: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="2" />
            <path d="M13.5 8a5.5 5.5 0 01-1.2 3.3l1.2 1.2-1.4 1.4-1.2-1.2A5.5 5.5 0 018 13.5 5.5 5.5 0 014.7 12.3l-1.2 1.2-1.4-1.4 1.2-1.2A5.5 5.5 0 012.5 8 5.5 5.5 0 018 2.5a5.5 5.5 0 013.3 1.2l1.2-1.2 1.4 1.4-1.2 1.2A5.5 5.5 0 0113.5 8z" />
        </svg>
    ),
    plus: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <path d="M8 3v10M3 8h10" />
        </svg>
    ),
};

function StatsBar({ data }: { data: EnterpriseSummary | undefined }) {
    const { t } = useTranslation();

    if (!data) {
        return (
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: '1px',
                background: 'var(--border-subtle)',
                borderRadius: '20px',
                overflow: 'hidden',
                border: '1px solid var(--border-subtle)',
                padding: '16px',
                color: 'var(--text-tertiary)'
            }}>
                {t('common.loading')}
            </div>
        );
    }

    const { employeeMetrics, taskMetrics, costAnalysis } = data;

    const stats = [
        { icon: Icons.users, label: t('dashboard.stats.totalEmployees'), value: employeeMetrics.totalEmployees, sub: t('dashboard.stats.activeEmployees', { count: employeeMetrics.activeEmployees }) },
        { icon: Icons.tasks, label: t('dashboard.stats.pendingTasks'), value: taskMetrics.pendingTasks, sub: t('dashboard.stats.completedToday', { count: taskMetrics.completedToday }) },
        { icon: Icons.zap, label: t('dashboard.stats.todayTokens'), value: taskMetrics.totalTokensToday, sub: t('dashboard.stats.allAgentsTotal') },
        { icon: Icons.trendingUp, label: t('dashboard.stats.healthScore'), value: `${data.systemHealth.healthScore.toFixed(1)}%`, sub: data.systemHealth.status },
    ];

    const enterpriseSignals = [
        { icon: Icons.activity, label: t('dashboard.enterprise.totalTasks', 'Total Tasks'), value: taskMetrics.totalTasks, sub: t('dashboard.enterprise.totalTasksSub', 'All agent tasks') },
        { icon: Icons.alertTriangle, label: t('dashboard.enterprise.riskSignals', 'Risk Signals'), value: data.riskAlerts.length, sub: t('dashboard.enterprise.riskSignalsSub', 'Needs attention') },
        { icon: Icons.coin, label: t('dashboard.enterprise.costHint', 'Cost / Tokens'), value: costAnalysis.totalCosts, sub: t('dashboard.enterprise.costHintSub', 'Today') },
        { icon: Icons.users, label: t('dashboard.enterprise.digitalEmployees', 'Digital Employees'), value: employeeMetrics.digitalEmployees, sub: t('dashboard.enterprise.digitalEmployeesSub', 'Running') },
    ];

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: '1px',
                background: 'var(--border-subtle)',
                borderRadius: '20px',
                overflow: 'hidden',
                border: '1px solid var(--border-subtle)',
            }}>
                {stats.map((s, i) => (
                    <div key={i} style={{
                        background: 'var(--bg-secondary)',
                        padding: '16px 20px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '2px',
                    }}>
                        <div style={{
                            fontSize: '12px',
                            color: 'var(--text-tertiary)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            marginBottom: '4px',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.7 }}>{s.icon}</span>
                            {s.label}
                        </div>
                        <div style={{
                            fontSize: '24px',
                            fontWeight: 600,
                            color: 'var(--text-primary)',
                            letterSpacing: '-0.02em'
                        }}>
                            {s.value}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{s.sub}</div>
                    </div>
                ))}
            </div>

            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: '1px',
                background: 'var(--border-subtle)',
                borderRadius: '20px',
                overflow: 'hidden',
                border: '1px solid var(--border-subtle)',
            }}>
                {enterpriseSignals.map((s, i) => (
                    <div key={i} style={{
                        background: 'var(--bg-secondary)',
                        padding: '16px 20px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '2px',
                    }}>
                        <div style={{
                            fontSize: '12px',
                            color: 'var(--text-tertiary)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            marginBottom: '4px',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.7 }}>{s.icon}</span>
                            {s.label}
                        </div>
                        <div style={{
                            fontSize: '24px',
                            fontWeight: 600,
                            color: 'var(--text-primary)',
                            letterSpacing: '-0.02em'
                        }}>
                            {s.value}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{s.sub}</div>
                    </div>
                ))}
            </div>
        </div>
    );
}

function DepartmentHealthTable({ departments }: { departments: DepartmentHealth[] }) {
    const { t } = useTranslation();
    const navigate = useNavigate();

    if (!departments || departments.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                {t('dashboard.noDepartments')}
            </div>
        );
    }

    const statusColor = (status: string) => {
        switch (status) {
            case 'HEALTHY': return 'var(--success)';
            case 'WARNING': return 'var(--warning)';
            case 'CRITICAL': return 'var(--error)';
            default: return 'var(--text-tertiary)';
        }
    };

    return (
        <div style={{
            border: '1px solid var(--border-subtle)',
            borderRadius: '20px',
            overflow: 'hidden',
        }}>
            <div style={{
                display: 'grid',
                gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr 1fr',
                padding: '10px 16px',
                fontSize: '11px',
                color: 'var(--text-tertiary)',
                fontWeight: 500,
                textTransform: 'uppercase' as const,
                letterSpacing: '0.05em',
                borderBottom: '1px solid var(--border-subtle)',
                background: 'rgba(255,255,255,0.03)',
            }}>
                <span>{t('dashboard.table.department')}</span>
                <span>{t('dashboard.table.members')}</span>
                <span>{t('dashboard.table.activeMembers')}</span>
                <span>{t('dashboard.table.todayTasks')}</span>
                <span>{t('dashboard.table.healthScore')}</span>
                <span>{t('dashboard.table.status')}</span>
            </div>
            <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {departments.map((dept) => (
                    <div
                        key={dept.code}
                        onClick={() => navigate(`/departments/${encodeURIComponent(dept.code)}/overview`)}
                        style={{
                            display: 'grid',
                            gridTemplateColumns: '2fr 1fr 1fr 1fr 1fr 1fr',
                            alignItems: 'center',
                            gap: '16px',
                            padding: '12px 16px',
                            fontSize: '13px',
                            cursor: 'pointer',
                            transition: 'background 120ms ease',
                        }}
                        onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
                        onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                    >
                        <span style={{ fontWeight: 500 }}>{dept.name}</span>
                        <span style={{ color: 'var(--text-secondary)' }}>{dept.memberCount}</span>
                        <span style={{ color: 'var(--text-secondary)' }}>{dept.activeMembers}</span>
                        <span style={{ color: 'var(--text-secondary)' }}>{dept.todayTasks}</span>
                        <span style={{ fontWeight: 500 }}>{dept.healthScore.toFixed(1)}%</span>
                        <span style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            color: statusColor(dept.status),
                        }}>
                            <span style={{
                                width: '6px',
                                height: '6px',
                                borderRadius: '50%',
                                background: statusColor(dept.status),
                            }} />
                            {dept.status}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function RiskAlertsPanel({ alerts }: { alerts: RiskAlert[] }) {
    const { t } = useTranslation();

    if (!alerts || alerts.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                {t('dashboard.noAlerts')}
            </div>
        );
    }

    const levelColor = (level: string) => {
        switch (level) {
            case 'CRITICAL': return 'var(--error)';
            case 'WARNING': return 'var(--warning)';
            case 'INFO': return 'var(--accent-primary)';
            default: return 'var(--text-tertiary)';
        }
    };

    return (
        <div style={{
            border: '1px solid var(--border-subtle)',
            borderRadius: '20px',
            overflow: 'hidden',
        }}>
            <div style={{
                padding: '12px 16px',
                borderBottom: '1px solid var(--border-subtle)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                background: 'rgba(255,255,255,0.03)',
            }}>
                <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
                    <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.alertTriangle}</span>
                    {t('dashboard.riskAlerts')}
                </h3>
                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{alerts.length} {t('dashboard.alerts')}</span>
            </div>
            <div style={{ padding: '4px', maxHeight: '320px', overflowY: 'auto' }}>
                {alerts.map((alert) => (
                    <div key={alert.alertId} style={{
                        display: 'flex',
                        gap: '12px',
                        padding: '10px 12px',
                        fontSize: '13px',
                        alignItems: 'flex-start',
                        borderLeft: `3px solid ${levelColor(alert.level)}`,
                        margin: '4px 8px',
                    }}>
                        <div style={{ flex: 1 }}>
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                marginBottom: '4px',
                            }}>
                                <span style={{
                                    fontSize: '11px',
                                    fontWeight: 500,
                                    padding: '1px 6px',
                                    borderRadius: 'var(--radius-sm)',
                                    background: levelColor(alert.level),
                                    color: '#fff',
                                }}>
                                    {alert.level}
                                </span>
                                <span style={{ fontWeight: 500 }}>{alert.title}</span>
                            </div>
                            <div style={{ color: 'var(--text-secondary)', fontSize: '12px' }}>
                                {alert.message}
                            </div>
                            {alert.department && (
                                <div style={{ color: 'var(--text-tertiary)', fontSize: '11px', marginTop: '4px' }}>
                                    {t('dashboard.department')}: {alert.department}
                                </div>
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default function EnterpriseDashboard() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);

    const { data: summary, isLoading, error } = useQuery({
        queryKey: ['enterprise-summary'],
        queryFn: () => dashboardApi.getEnterpriseSummary(),
        refetchInterval: 30000,
        enabled: !!user,  // 只有用户已认证时才调用
    });

    if (isLoading) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                    {t('common.loading')}
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                <div style={{
                    textAlign: 'center',
                    padding: '60px',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '24px',
                    background: 'var(--bg-secondary)',
                    color: 'var(--error)',
                }}>
                    {t('dashboard.loadError')}: {String(error)}
                </div>
            </div>
        );
    }

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
                            {t('dashboard.enterprise.title', '企业经营总览')}
                        </h1>
                        <p style={{ margin: '12px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75, maxWidth: '68ch' }}>
                            {t('dashboard.enterprise.subtitle', '这里汇总公司运转、任务执行、数字员工产出、风险信号和成本趋势，帮助企业管理者快速判断业务健康度与战略节奏。')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gap: '10px', minWidth: '260px', alignSelf: 'stretch' }}>
                        <div style={{ padding: '14px 16px', borderRadius: '18px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>{t('dashboard.enterprise.focus', 'Current Focus')}</div>
                            <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>{t('dashboard.enterprise.focusValue', '公司运行与收益监控')}</div>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.enterprise.systemHealth', 'System Health')}</div>
                                <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{summary?.systemHealth.healthScore.toFixed(1)}%</div>
                            </div>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.enterprise.activeRisks', 'Active Risks')}</div>
                                <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: (summary?.riskAlerts.length ?? 0) > 0 ? 'var(--warning)' : 'var(--success)' }}>{summary?.riskAlerts.length ?? 0}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <StatsBar data={summary} />

            {/* 系统健康体征 */}
            <div style={{
                border: '1px solid var(--border-subtle)',
                borderRadius: '20px',
                overflow: 'hidden',
                background: 'rgba(255,255,255,0.02)',
            }}>
                <div style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    background: 'rgba(255,255,255,0.03)',
                }}>
                    <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                        {t('dashboard.vitalSigns', '系统健康')}
                    </h3>
                </div>
                <div style={{ padding: '12px' }}>
                    <VitalSignsDashboard />
                </div>
            </div>

            <div style={{
                border: '1px solid var(--border-subtle)',
                borderRadius: '20px',
                overflow: 'hidden',
                background: 'rgba(255,255,255,0.02)',
            }}>
                <div style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    background: 'rgba(255,255,255,0.03)',
                }}>
                    <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)' }}>
                        <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.users}</span>
                        {t('dashboard.departmentHealth', 'Department Health')}
                    </h3>
                    <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('dashboard.sortedByHealth', 'Sorted by health score')}</span>
                </div>
                <div style={{ padding: '8px' }}>
                    <DepartmentHealthTable departments={summary?.departmentHealth ?? []} />
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1.15fr 0.85fr', gap: '18px' }}>
                <RiskAlertsPanel alerts={summary?.riskAlerts ?? []} />

                <div style={{
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '20px',
                    background: 'rgba(255,255,255,0.02)',
                    overflow: 'hidden',
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
                        <button className="btn btn-secondary" onClick={() => navigate('/departments')} style={{ justifyContent: 'space-between' }}>
                            <span>{t('nav.departments', 'Departments')}</span>
                            <span>↗</span>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
