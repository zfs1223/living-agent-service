import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { officeApi, officeExtendedApi } from '../services/api';
import type { OfficeDepartmentAgentSnapshot } from '../services/officeExtendedApi';

type SubTab = 'overview' | 'areas' | 'agents' | 'departments';

const AGENT_STATUS_COLORS: Record<string, string> = {
    active: '#22c55e',
    idle: '#f59e0b',
    error: '#ef4444',
    offline: '#6b7280',
    busy: '#3b82f6',
};

export default function Office() {
    const { t } = useTranslation();
    const [subTab, setSubTab] = useState<SubTab>('overview');
    const [selectedDept, setSelectedDept] = useState('');

    const { data: status, isLoading: statusLoading } = useQuery({
        queryKey: ['office-status'],
        queryFn: () => officeExtendedApi.getStatus(),
        refetchInterval: 30000,
    });

    const { data: offices = [], isLoading: officesLoading } = useQuery({
        queryKey: ['offices'],
        queryFn: () => officeApi.list(),
    });

    const { data: agents = [], isLoading: agentsLoading } = useQuery({
        queryKey: ['office-agents'],
        queryFn: () => officeExtendedApi.listAgents(),
        enabled: subTab === 'agents' || subTab === 'overview',
    });

    const { data: areas = [], isLoading: areasLoading } = useQuery({
        queryKey: ['office-areas'],
        queryFn: () => officeExtendedApi.listAreas(),
        enabled: subTab === 'areas',
    });

    const { data: deptStatus, isLoading: deptLoading } = useQuery({
        queryKey: ['office-department', selectedDept],
        queryFn: () => officeExtendedApi.getDepartmentStatus(selectedDept),
        enabled: subTab === 'departments' && !!selectedDept,
    });

    const { data: yesterdayMemo } = useQuery({
        queryKey: ['office-yesterday-memo'],
        queryFn: () => officeExtendedApi.getYesterdayMemo(),
    });

    const statusData = status as Record<string, any> | undefined;
    const deptData = deptStatus as Record<string, any> | undefined;

    const tabItems: { key: SubTab; label: string }[] = [
        { key: 'overview', label: t('office.overview', '总览') },
        { key: 'areas', label: t('office.areas', '区域') },
        { key: 'agents', label: t('office.agents', '坐席') },
        { key: 'departments', label: t('office.departments', '部门') },
    ];

    const DEPARTMENTS = ['tech', 'hr', 'finance', 'sales', 'cs', 'admin', 'legal', 'ops'];

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
            <h1 style={{ fontSize: '20px', fontWeight: 600, margin: 0, marginBottom: '20px' }}>
                {t('office.title', '办公室管理')}
            </h1>

            {/* Status Overview Cards */}
            <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                gap: '10px', marginBottom: '20px',
            }}>
                {[
                    { label: t('office.totalOffices', '办公区数'), value: (offices as any[]).length, color: 'var(--accent)' },
                    { label: t('office.activeAgents', '活跃坐席'), value: statusData?.activeAgents ?? '-', color: '#22c55e' },
                    { label: t('office.totalAgents', '总坐席数'), value: statusData?.totalAgents ?? (agents as any[]).length ?? '-', color: '#3b82f6' },
                    { label: t('office.status', '系统状态'), value: statusData?.status ?? '-', color: statusData?.status === 'ok' ? '#22c55e' : '#f59e0b' },
                ].map(item => (
                    <div key={item.label} style={{
                        padding: '12px 14px', borderRadius: '8px',
                        background: 'var(--bg-secondary)',
                    }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{item.label}</div>
                        <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '4px', color: item.color }}>
                            {item.value}
                        </div>
                    </div>
                ))}
            </div>

            {/* Yesterday Memo */}
            {yesterdayMemo && (
                <div style={{
                    padding: '12px 16px', borderRadius: '8px', marginBottom: '16px',
                    background: 'rgba(59,130,246,0.08)', borderLeft: '3px solid #3b82f6',
                    fontSize: '13px', color: 'var(--text-secondary)',
                }}>
                    <div style={{ fontWeight: 600, marginBottom: '4px', color: 'var(--text-primary)' }}>
                        {t('office.yesterdayMemo', '昨日备忘')}
                    </div>
                    {typeof yesterdayMemo === 'string' ? yesterdayMemo : JSON.stringify(yesterdayMemo)}
                </div>
            )}

            {/* Tab Bar */}
            <div style={{
                display: 'flex', gap: '2px', marginBottom: '16px',
                background: 'var(--bg-secondary)', borderRadius: '8px', padding: '3px',
            }}>
                {tabItems.map(tab => (
                    <button
                        key={tab.key}
                        onClick={() => setSubTab(tab.key)}
                        style={{
                            flex: 1, padding: '8px 12px', borderRadius: '6px',
                            border: 'none', cursor: 'pointer',
                            background: subTab === tab.key ? 'var(--accent)' : 'transparent',
                            color: subTab === tab.key ? '#fff' : 'var(--text-secondary)',
                            fontSize: '13px', fontWeight: subTab === tab.key ? 600 : 400,
                            transition: 'all 0.15s',
                        }}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Overview */}
            {subTab === 'overview' && (
                <div>
                    <div style={{ marginBottom: '16px' }}>
                        <h3 style={{ fontSize: '14px', fontWeight: 600, margin: '0 0 8px' }}>
                            {t('office.agentsOverview', '坐席概览')}
                        </h3>
                        {agentsLoading ? (
                            <div style={{ textAlign: 'center', padding: '20px', color: 'var(--text-tertiary)' }}>
                                {t('common.loading', '加载中...')}
                            </div>
                        ) : (agents as any[]).length === 0 ? (
                            <div style={{
                                textAlign: 'center', padding: '40px 20px', color: 'var(--text-tertiary)',
                                background: 'var(--bg-secondary)', borderRadius: '12px',
                            }}>
                                {t('office.noAgents', '暂无坐席数据')}
                            </div>
                        ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                {(agents as OfficeDepartmentAgentSnapshot[]).map((agent, idx) => {
                                    const statusColor = AGENT_STATUS_COLORS[agent.status || ''] || 'var(--text-tertiary)';
                                    return (
                                        <div key={agent.id || agent.agentId || idx} style={{
                                            padding: '12px 16px', borderRadius: '8px',
                                            background: 'var(--bg-secondary)',
                                            display: 'flex', alignItems: 'center', gap: '8px',
                                        }}>
                                            <span style={{
                                                width: '8px', height: '8px', borderRadius: '50%',
                                                background: statusColor, flexShrink: 0,
                                            }} />
                                            <span style={{ fontWeight: 600, fontSize: '13px' }}>
                                                {agent.agentName || agent.name || agent.agentId}
                                            </span>
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                color: statusColor, background: `${statusColor}18`,
                                            }}>
                                                {agent.status || 'unknown'}
                                            </span>
                                            {agent.currentTask || agent.current_task ? (
                                                <span style={{
                                                    fontSize: '11px', color: 'var(--text-tertiary)',
                                                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                                }}>
                                                    {agent.currentTask || agent.current_task}
                                                </span>
                                            ) : null}
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Areas */}
            {subTab === 'areas' && (
                <div>
                    {areasLoading ? (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    ) : (areas as any[]).length === 0 ? (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('office.noAreas', '暂无区域数据')}
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                            {(areas as any[]).map((area: any, idx: number) => (
                                <div key={area.id || area.areaId || idx} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {area.name || area.id || `Area ${idx + 1}`}
                                        </span>
                                        {area.type && (
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                            }}>
                                                {area.type}
                                            </span>
                                        )}
                                        {area.capacity !== undefined && (
                                            <span style={{
                                                marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)',
                                            }}>
                                                {t('office.capacity', '容量')}: {area.capacity}
                                            </span>
                                        )}
                                    </div>
                                    {area.description && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                                            {area.description}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Agents */}
            {subTab === 'agents' && (
                <div>
                    {agentsLoading ? (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    ) : (agents as any[]).length === 0 ? (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('office.noAgents', '暂无坐席数据')}
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                            {(agents as OfficeDepartmentAgentSnapshot[]).map((agent, idx) => {
                                const statusColor = AGENT_STATUS_COLORS[agent.status || ''] || 'var(--text-tertiary)';
                                const taskState = agent.taskState;
                                const taskStateColor = taskState === 'running' ? '#22c55e'
                                    : taskState === 'error' ? '#ef4444'
                                    : taskState === 'complete' ? '#3b82f6'
                                    : 'var(--text-tertiary)';
                                return (
                                    <div key={agent.id || agent.agentId || idx} style={{
                                        padding: '14px 16px', borderRadius: '8px',
                                        background: 'var(--bg-secondary)',
                                    }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                                            <span style={{
                                                width: '8px', height: '8px', borderRadius: '50%',
                                                background: statusColor, flexShrink: 0,
                                            }} />
                                            <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                                {agent.agentName || agent.name || agent.agentId}
                                            </span>
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                color: statusColor, background: `${statusColor}18`,
                                            }}>
                                                {agent.status || 'unknown'}
                                            </span>
                                            {taskState && (
                                                <span style={{
                                                    fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                    color: taskStateColor, background: `${taskStateColor}18`,
                                                }}>
                                                    {taskState}
                                                </span>
                                            )}
                                        </div>
                                        {(agent.currentTask || agent.current_task) && (
                                            <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                                                {t('office.currentTask', '当前任务')}: {agent.currentTask || agent.current_task}
                                            </div>
                                        )}
                                        <div style={{ display: 'flex', gap: '12px', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                            {agent.fromZone && <span>{t('office.from', '从')}: {agent.fromZone}</span>}
                                            {agent.toZone && <span>{t('office.to', '到')}: {agent.toZone}</span>}
                                            {agent.updated_at && <span>{new Date(agent.updated_at).toLocaleString()}</span>}
                                            {agent.last_active_at && <span>{t('office.lastActive', '最后活跃')}: {new Date(agent.last_active_at).toLocaleString()}</span>}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}

            {/* Departments */}
            {subTab === 'departments' && (
                <div>
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '16px' }}>
                        {DEPARTMENTS.map(dept => (
                            <button
                                key={dept}
                                onClick={() => setSelectedDept(dept)}
                                style={{
                                    padding: '6px 12px', borderRadius: '6px',
                                    border: selectedDept === dept ? '1px solid var(--accent)' : '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                    background: selectedDept === dept ? 'rgba(224,238,238,0.12)' : 'transparent',
                                    color: selectedDept === dept ? 'var(--accent)' : 'var(--text-secondary)',
                                    fontSize: '12px', cursor: 'pointer', transition: 'all 0.15s',
                                }}
                            >
                                {dept.toUpperCase()}
                            </button>
                        ))}
                    </div>

                    {!selectedDept && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('office.selectDepartment', '请选择一个部门')}
                        </div>
                    )}

                    {selectedDept && deptLoading && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}

                    {selectedDept && deptData && !deptLoading && (
                        <div>
                            <div style={{
                                padding: '12px 16px', borderRadius: '8px',
                                background: 'var(--bg-secondary)', marginBottom: '12px',
                            }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <span style={{ fontWeight: 600, fontSize: '15px' }}>
                                        {deptData.departmentName || deptData.department || selectedDept}
                                    </span>
                                    <span style={{
                                        fontSize: '12px', padding: '2px 8px', borderRadius: '999px',
                                        background: deptData.status === 'active' ? 'rgba(34,197,94,0.12)' : 'rgba(107,114,128,0.12)',
                                        color: deptData.status === 'active' ? '#22c55e' : '#6b7280',
                                    }}>
                                        {deptData.status || 'unknown'}
                                    </span>
                                </div>
                                {deptData.updatedAt && (
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {t('office.updatedAt', '更新于')}: {new Date(deptData.updatedAt).toLocaleString()}
                                    </div>
                                )}
                            </div>

                            {deptData.agents && (deptData.agents as OfficeDepartmentAgentSnapshot[]).length > 0 && (
                                <div>
                                    <h3 style={{ fontSize: '13px', fontWeight: 600, margin: '0 0 8px', color: 'var(--text-secondary)' }}>
                                        {t('office.departmentAgents', '部门坐席')}
                                    </h3>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                        {(deptData.agents as OfficeDepartmentAgentSnapshot[]).map((agent, idx) => {
                                            const statusColor = AGENT_STATUS_COLORS[agent.status || ''] || 'var(--text-tertiary)';
                                            return (
                                                <div key={agent.id || idx} style={{
                                                    padding: '10px 16px', borderRadius: '8px',
                                                    background: 'var(--bg-secondary)',
                                                    display: 'flex', alignItems: 'center', gap: '8px',
                                                }}>
                                                    <span style={{
                                                        width: '6px', height: '6px', borderRadius: '50%',
                                                        background: statusColor, flexShrink: 0,
                                                    }} />
                                                    <span style={{ fontSize: '13px', fontWeight: 500 }}>
                                                        {agent.agentName || agent.name || agent.agentId}
                                                    </span>
                                                    <span style={{
                                                        fontSize: '11px', color: statusColor,
                                                    }}>
                                                        {agent.status || 'unknown'}
                                                    </span>
                                                    {agent.message && (
                                                        <span style={{
                                                            fontSize: '11px', color: 'var(--text-tertiary)',
                                                            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                                        }}>
                                                            {agent.message}
                                                        </span>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            {deptData.events && (deptData.events as any[]).length > 0 && (
                                <div style={{ marginTop: '12px' }}>
                                    <h3 style={{ fontSize: '13px', fontWeight: 600, margin: '0 0 8px', color: 'var(--text-secondary)' }}>
                                        {t('office.recentEvents', '近期事件')}
                                    </h3>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                        {(deptData.events as any[]).slice(0, 10).map((event: any, idx: number) => (
                                            <div key={idx} style={{
                                                padding: '8px 16px', borderRadius: '6px',
                                                background: 'var(--bg-secondary)', fontSize: '12px',
                                                color: 'var(--text-secondary)',
                                            }}>
                                                {event.message || event.type || JSON.stringify(event)}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
