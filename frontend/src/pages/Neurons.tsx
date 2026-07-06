import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { neuronApi } from '../services/api';

const STATUS_COLORS: Record<string, string> = {
    active: '#22c55e',
    idle: '#f59e0b',
    error: '#ef4444',
    offline: '#6b7280',
    busy: '#3b82f6',
};

const TYPE_LABELS: Record<string, string> = {
    brain: 'Brain',
    chat: 'Chat',
    tool: 'Tool',
    perception: 'Perception',
};

export default function Neurons() {
    const { t } = useTranslation();
    const [expandedId, setExpandedId] = useState<string | null>(null);
    const [search, setSearch] = useState('');
    const [filterDomain, setFilterDomain] = useState('');
    const [filterType, setFilterType] = useState('');
    const [filterStatus, setFilterStatus] = useState('');

    const { data: neurons = [], isLoading } = useQuery({
        queryKey: ['neurons'],
        queryFn: () => neuronApi.list(),
        refetchInterval: 15000,
    });

    const { data: statusData } = useQuery({
        queryKey: ['neuron-status', expandedId],
        queryFn: () => neuronApi.getStatus(expandedId!),
        enabled: !!expandedId,
    });

    const { data: metricsData } = useQuery({
        queryKey: ['neuron-metrics', expandedId],
        queryFn: () => neuronApi.getMetrics(expandedId!),
        enabled: !!expandedId,
    });

    // 提取唯一域和类型用于过滤
    const domains = [...new Set((neurons as any[]).map((n: any) => n.domain).filter(Boolean))];
    const types = [...new Set((neurons as any[]).map((n: any) => n.type).filter(Boolean))];
    const statuses = [...new Set((neurons as any[]).map((n: any) => n.status).filter(Boolean))];

    // 过滤逻辑
    const filtered = (neurons as any[]).filter((n: any) => {
        if (search) {
            const q = search.toLowerCase();
            const matchName = (n.name || '').toLowerCase().includes(q);
            const matchId = (n.id || '').toLowerCase().includes(q);
            const matchDomain = (n.domain || '').toLowerCase().includes(q);
            if (!matchName && !matchId && !matchDomain) return false;
        }
        if (filterDomain && n.domain !== filterDomain) return false;
        if (filterType && n.type !== filterType) return false;
        if (filterStatus && n.status !== filterStatus) return false;
        return true;
    });

    const extractNeuronId = (neuron: any): string => {
        return neuron.id || neuron.neuronId || neuron.neuron_id || '';
    };

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
                <h1 style={{ fontSize: '20px', fontWeight: 600, margin: 0 }}>{t('neurons.title', '神经元管理')}</h1>
                <span style={{ fontSize: '13px', color: 'var(--text-tertiary)' }}>
                    {filtered.length} / {(neurons as any[]).length}
                </span>
            </div>

            {/* 搜索和过滤 */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
                <input
                    type="text"
                    placeholder={t('neurons.search', '搜索神经元...')}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={{
                        flex: 1,
                        minWidth: '180px',
                        padding: '8px 12px',
                        borderRadius: '8px',
                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                        background: 'var(--bg-secondary)',
                        color: 'var(--text-primary, #fff)',
                        fontSize: '13px',
                        outline: 'none',
                    }}
                />
                <select
                    value={filterDomain}
                    onChange={(e) => setFilterDomain(e.target.value)}
                    style={{
                        padding: '8px 12px',
                        borderRadius: '8px',
                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                        background: 'var(--bg-secondary)',
                        color: 'var(--text-primary, #fff)',
                        fontSize: '13px',
                        outline: 'none',
                    }}
                >
                    <option value="">{t('neurons.allDomains', '全部域')}</option>
                    {domains.map((d) => (
                        <option key={d} value={d}>{d}</option>
                    ))}
                </select>
                <select
                    value={filterType}
                    onChange={(e) => setFilterType(e.target.value)}
                    style={{
                        padding: '8px 12px',
                        borderRadius: '8px',
                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                        background: 'var(--bg-secondary)',
                        color: 'var(--text-primary, #fff)',
                        fontSize: '13px',
                        outline: 'none',
                    }}
                >
                    <option value="">{t('neurons.allTypes', '全部类型')}</option>
                    {types.map((tp) => (
                        <option key={tp} value={tp}>{TYPE_LABELS[tp] || tp}</option>
                    ))}
                </select>
                <select
                    value={filterStatus}
                    onChange={(e) => setFilterStatus(e.target.value)}
                    style={{
                        padding: '8px 12px',
                        borderRadius: '8px',
                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                        background: 'var(--bg-secondary)',
                        color: 'var(--text-primary, #fff)',
                        fontSize: '13px',
                        outline: 'none',
                    }}
                >
                    <option value="">{t('neurons.allStatus', '全部状态')}</option>
                    {statuses.map((s) => (
                        <option key={s} value={s}>{s}</option>
                    ))}
                </select>
            </div>

            {isLoading && (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                    {t('common.loading', '加载中...')}
                </div>
            )}

            {!isLoading && filtered.length === 0 && (
                <div style={{
                    textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                    background: 'var(--bg-secondary)', borderRadius: '12px',
                }}>
                    <div style={{ fontSize: '13px', marginBottom: '12px' }}>
                        {t('neurons.empty', '暂无神经元数据')}
                    </div>
                </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                {filtered.map((neuron: any) => {
                    const nid = extractNeuronId(neuron);
                    const isExpanded = expandedId === nid;
                    const statusColor = STATUS_COLORS[neuron.status] || 'var(--text-tertiary)';

                    return (
                        <div key={nid} style={{ borderRadius: '8px', overflow: 'hidden' }}>
                            <div
                                onClick={() => setExpandedId(isExpanded ? null : nid)}
                                style={{
                                    padding: '14px 16px',
                                    borderRadius: '8px',
                                    background: isExpanded ? 'rgba(224,238,238,0.06)' : 'transparent',
                                    cursor: 'pointer',
                                    borderLeft: isExpanded ? '3px solid var(--accent)' : '3px solid transparent',
                                    transition: 'background 0.15s',
                                }}
                            >
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                    <span style={{
                                        width: '8px', height: '8px', borderRadius: '50%',
                                        background: statusColor, flexShrink: 0,
                                    }} />
                                    <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                        {neuron.name || neuron.neuronId || nid}
                                    </span>
                                    <span style={{
                                        fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                        background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                    }}>
                                        {neuron.domain || '-'}
                                    </span>
                                    <span style={{
                                        fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                        background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                    }}>
                                        {TYPE_LABELS[neuron.type] || neuron.type || '-'}
                                    </span>
                                    <span style={{
                                        marginLeft: 'auto', fontSize: '11px', padding: '2px 8px',
                                        borderRadius: '999px', color: statusColor,
                                        background: `${statusColor}18`,
                                    }}>
                                        {neuron.status || 'unknown'}
                                    </span>
                                    <span style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>
                                        {isExpanded ? '▼' : '▶'}
                                    </span>
                                </div>
                                {neuron.id && (
                                    <div style={{
                                        fontSize: '12px', color: 'var(--text-tertiary)',
                                        paddingLeft: '16px',
                                    }}>
                                        {neuron.id}
                                    </div>
                                )}
                            </div>

                            {isExpanded && (
                                <div style={{
                                    padding: '12px 16px 16px', marginLeft: '19px',
                                    borderLeft: '2px solid var(--accent)',
                                }}>
                                    {/* 状态信息 */}
                                    <div style={{ marginBottom: '12px' }}>
                                        <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>
                                            {t('neurons.status', '状态')}
                                        </div>
                                        {statusData ? (
                                            <div style={{
                                                background: 'var(--bg-secondary)', borderRadius: '8px',
                                                padding: '12px', fontSize: '13px',
                                            }}>
                                                {(typeof statusData === 'object') && Object.entries(statusData as Record<string, any>).map(([key, val]) => (
                                                    <div key={key} style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0' }}>
                                                        <span style={{ color: 'var(--text-tertiary)' }}>{key}</span>
                                                        <span style={{ color: 'var(--text-secondary)' }}>
                                                            {typeof val === 'object' ? JSON.stringify(val) : String(val ?? '-')}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div style={{ fontSize: '13px', color: 'var(--text-tertiary)' }}>
                                                {t('neurons.loadingStatus', '加载状态中...')}
                                            </div>
                                        )}
                                    </div>

                                    {/* 指标信息 */}
                                    <div>
                                        <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>
                                            {t('neurons.metrics', '指标')}
                                        </div>
                                        {metricsData ? (
                                            <div style={{
                                                background: 'var(--bg-secondary)', borderRadius: '8px',
                                                padding: '12px', fontSize: '13px',
                                            }}>
                                                {(typeof metricsData === 'object') && Object.entries(metricsData as Record<string, any>).map(([key, val]) => (
                                                    <div key={key} style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0' }}>
                                                        <span style={{ color: 'var(--text-tertiary)' }}>{key}</span>
                                                        <span style={{ color: 'var(--text-secondary)' }}>
                                                            {typeof val === 'object' ? JSON.stringify(val) : String(val ?? '-')}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div style={{ fontSize: '13px', color: 'var(--text-tertiary)' }}>
                                                {t('neurons.loadingMetrics', '加载指标中...')}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
