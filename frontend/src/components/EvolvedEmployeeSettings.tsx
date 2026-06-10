import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchJson } from '../services/api';

interface Props {
    employeeId: string;
    employee: any;
}

const EvolvedEmployeeSettings: React.FC<Props> = ({ employeeId, employee }) => {
    const { data: metrics } = useQuery({
        queryKey: ['agent-metrics', employeeId],
        queryFn: () => fetchJson<any>(`/agents/${encodeURIComponent(employeeId)}/metrics`).catch(() => null),
        enabled: !!employeeId,
    });

    const { data: permissions } = useQuery({
        queryKey: ['agent-permissions', employeeId],
        queryFn: () => fetchJson<any>(`/agents/${encodeURIComponent(employeeId)}/permissions`).catch(() => null),
        enabled: !!employeeId,
    });

    const emp = employee || {};
    const personality = emp.personality || { rigor: 0.5, creativity: 0.5, riskTolerance: 0.5, obedience: 0.5 };

    return (
        <div style={{ padding: '0 0 24px 0' }}>
            <div style={{
                background: 'linear-gradient(135deg, #3b1f6e 0%, #1a2744 100%)',
                borderRadius: 12,
                padding: 20,
                marginBottom: 20,
                border: '1px solid rgba(168,85,247,0.2)',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                    <span style={{ fontSize: 32 }}>🧬</span>
                    <div>
                        <div style={{ fontSize: 18, fontWeight: 600, color: '#e2e8f0' }}>
                            {emp.name || '进化员工'}
                            <span style={{
                                marginLeft: 8,
                                fontSize: 12,
                                padding: '2px 8px',
                                borderRadius: 4,
                                background: '#a855f7',
                                color: '#fff',
                            }}>
                                进化生成
                            </span>
                        </div>
                        <div style={{ fontSize: 13, color: '#94a3b8', marginTop: 2 }}>
                            {emp.title || ''} · {emp.department || ''}
                        </div>
                    </div>
                </div>
                <div style={{ fontSize: 12, color: '#c4b5fd', background: 'rgba(168,85,247,0.1)', padding: '8px 12px', borderRadius: 6 }}>
                    此数字员工由AI进化引擎自动生成，核心配置由系统管理。部分设置可调整。
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                <InfoCard title="🧬 进化信息" items={[
                    { label: '来源', value: 'AI进化引擎' },
                    { label: '人格来源', value: 'INFERRED (AI推断)' },
                    { label: '部门', value: emp.department },
                    { label: '创建时间', value: emp.created_at ? new Date(emp.created_at).toLocaleString() : '-' },
                    { label: '最后活跃', value: emp.last_active_at ? new Date(emp.last_active_at).toLocaleString() : '-' },
                ]} />

                <InfoCard title="🧠 人格特征" items={[
                    { label: '严谨性', value: `${Math.round((personality.rigor || 0.5) * 100)}%` },
                    { label: '创造力', value: `${Math.round((personality.creativity || 0.5) * 100)}%` },
                    { label: '风险承受', value: `${Math.round((personality.riskTolerance || 0.5) * 100)}%` },
                    { label: '服从度', value: `${Math.round((personality.obedience || 0.5) * 100)}%` },
                ]} />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 16 }}>
                <InfoCard title="📊 运行指标" items={[
                    { label: '任务总数', value: String(metrics?.totalTasks ?? emp.task_count ?? 0) },
                    { label: '成功数', value: String(metrics?.successfulTasks ?? emp.success_count ?? 0) },
                    { label: '成功率', value: `${metrics?.successRate != null ? Math.round(metrics.successRate * 100) : 0}%` },
                    { label: '访问级别', value: permissions?.access_level || '-' },
                ]} />

                <InfoCard title="🔧 能力与工具" items={[
                    ...(emp.capabilities || []).map((c: string) => ({ label: '能力', value: c })),
                    ...(emp.tools || []).slice(0, 5).map((t: string) => ({ label: '工具', value: t })),
                ].slice(0, 8)} />
            </div>

            <div style={{
                marginTop: 16,
                padding: 16,
                background: 'rgba(59,31,110,0.3)',
                borderRadius: 8,
                border: '1px solid rgba(168,85,247,0.1)',
                fontSize: 13,
                color: '#c4b5fd',
            }}>
                ⚠️ 进化员工的核心配置（人格、能力）由AI进化引擎管理，不建议手动修改。通道配置和生命周期策略可调整。
            </div>
        </div>
    );
};

const InfoCard: React.FC<{
    title: string;
    items: { label: string; value: string }[];
}> = ({ title, items }) => (
    <div style={{
        background: 'rgba(30,41,59,0.5)',
        borderRadius: 8,
        padding: 16,
        border: '1px solid rgba(148,163,184,0.1)',
    }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#e2e8f0', marginBottom: 12 }}>{title}</div>
        {items.map((item, i) => (
            <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: i < items.length - 1 ? '1px solid rgba(148,163,184,0.08)' : 'none' }}>
                <span style={{ color: '#94a3b8', fontSize: 13 }}>{item.label}</span>
                <span style={{ color: '#e2e8f0', fontSize: 13 }}>{item.value || '-'}</span>
            </div>
        ))}
    </div>
);

export default EvolvedEmployeeSettings;
