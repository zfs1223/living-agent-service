import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchJson } from '../services/api';

interface Props {
    employeeId: string;
    employee: any;
}

const originLabels: Record<string, { label: string; icon: string; color: string }> = {
    human: { label: '真实员工', icon: '👤', color: '#3b82f6' },
};

const HumanEmployeeSettings: React.FC<Props> = ({ employeeId, employee }) => {
    const { data: permissions, isLoading: permLoading } = useQuery({
        queryKey: ['agent-permissions', employeeId],
        queryFn: () => fetchJson<any>(`/agents/${encodeURIComponent(employeeId)}/permissions`).catch(() => null),
        enabled: !!employeeId,
    });

    const emp = employee || {};
    const originInfo = originLabels.human;

    return (
        <div style={{ padding: '0 0 24px 0' }}>
            <div style={{
                background: 'linear-gradient(135deg, #1e3a5f 0%, #1a2744 100%)',
                borderRadius: 12,
                padding: 20,
                marginBottom: 20,
                border: '1px solid rgba(59,130,246,0.2)',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                    <span style={{ fontSize: 32 }}>{originInfo.icon}</span>
                    <div>
                        <div style={{ fontSize: 18, fontWeight: 600, color: '#e2e8f0' }}>
                            {emp.name || '未知员工'}
                            <span style={{
                                marginLeft: 8,
                                fontSize: 12,
                                padding: '2px 8px',
                                borderRadius: 4,
                                background: originInfo.color,
                                color: '#fff',
                            }}>
                                {originInfo.label}
                            </span>
                        </div>
                        <div style={{ fontSize: 13, color: '#94a3b8', marginTop: 2 }}>
                            {emp.title || emp.position || ''} · {emp.department || ''}
                        </div>
                    </div>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                <InfoCard title="📋 基本信息" items={[
                    { label: '姓名', value: emp.name },
                    { label: '职位', value: emp.title || emp.position },
                    { label: '部门', value: emp.department },
                    { label: '状态', value: emp.status },
                    { label: '邮箱', value: emp.email || '-' },
                    { label: '手机', value: emp.phone || '-' },
                ]} />
                <InfoCard title="🔐 权限信息" items={[
                    { label: '身份类型', value: emp.identity || '-' },
                    { label: '访问级别', value: permissions?.access_level || emp.access_level || '-' },
                    { label: '可见范围', value: permissions?.scope_type || '-' },
                    { label: '认证方式', value: emp.auth_provider || '-' },
                ]} loading={permLoading} />
            </div>

            <div style={{
                marginTop: 16,
                padding: 16,
                background: 'rgba(30,58,95,0.3)',
                borderRadius: 8,
                border: '1px solid rgba(59,130,246,0.1)',
                fontSize: 13,
                color: '#94a3b8',
            }}>
                💡 真实员工的配置由企业身份系统（钉钉/飞书/企微/OA）管理，此处仅展示基本信息。如需修改，请联系管理员。
            </div>
        </div>
    );
};

const InfoCard: React.FC<{
    title: string;
    items: { label: string; value: string | undefined }[];
    loading?: boolean;
}> = ({ title, items, loading }) => (
    <div style={{
        background: 'rgba(30,41,59,0.5)',
        borderRadius: 8,
        padding: 16,
        border: '1px solid rgba(148,163,184,0.1)',
    }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#e2e8f0', marginBottom: 12 }}>{title}</div>
        {loading ? (
            <div style={{ color: '#64748b', fontSize: 13 }}>加载中...</div>
        ) : (
            items.map((item, i) => (
                <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: i < items.length - 1 ? '1px solid rgba(148,163,184,0.08)' : 'none' }}>
                    <span style={{ color: '#94a3b8', fontSize: 13 }}>{item.label}</span>
                    <span style={{ color: '#e2e8f0', fontSize: 13 }}>{item.value || '-'}</span>
                </div>
            ))
        )}
    </div>
);

export default HumanEmployeeSettings;
