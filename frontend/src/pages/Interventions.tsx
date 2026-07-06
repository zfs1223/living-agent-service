import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { interventionApi, interventionExtendedApi } from '../services/api';

type SubTab = 'pending' | 'rules' | 'all';

export default function Interventions() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [subTab, setSubTab] = useState<SubTab>('pending');
    const [responseData, setResponseData] = useState('');
    const [escalateReason, setEscalateReason] = useState('');
    const [activeIntervention, setActiveIntervention] = useState<string | null>(null);
    const [showRegisterRule, setShowRegisterRule] = useState(false);
    const [newRule, setNewRule] = useState({ name: '', description: '', type: '', config: '' });

    // 统计数据
    const { data: statistics } = useQuery({
        queryKey: ['intervention-statistics'],
        queryFn: () => interventionExtendedApi.getStatistics(),
    });

    // 待处理干预
    const { data: pendingDecisions = [], isLoading: loadingPending } = useQuery({
        queryKey: ['intervention-pending'],
        queryFn: () => interventionExtendedApi.getPendingDecisions(),
        enabled: subTab === 'pending',
    });

    // 干预规则
    const { data: rules = [], isLoading: loadingRules } = useQuery({
        queryKey: ['intervention-rules'],
        queryFn: () => interventionExtendedApi.listRules(),
        enabled: subTab === 'rules',
    });

    // 所有干预
    const { data: interventions = [], isLoading: loadingAll } = useQuery({
        queryKey: ['interventions'],
        queryFn: () => interventionApi.list(),
        enabled: subTab === 'all',
    });

    // Mutations
    const respondMutation = useMutation({
        mutationFn: ({ id, response }: { id: string; response: string }) =>
            interventionExtendedApi.respond(id, response),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['intervention-pending'] });
            queryClient.invalidateQueries({ queryKey: ['intervention-statistics'] });
            setActiveIntervention(null);
            setResponseData('');
        },
    });

    const escalateMutation = useMutation({
        mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
            interventionExtendedApi.escalate(id, reason),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['intervention-pending'] });
            queryClient.invalidateQueries({ queryKey: ['intervention-statistics'] });
            setActiveIntervention(null);
            setEscalateReason('');
        },
    });

    const registerRuleMutation = useMutation({
        mutationFn: (data: any) => interventionExtendedApi.registerRule(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['intervention-rules'] });
            setShowRegisterRule(false);
            setNewRule({ name: '', description: '', type: '', config: '' });
        },
    });

    const unregisterRuleMutation = useMutation({
        mutationFn: (ruleId: string) => interventionExtendedApi.unregisterRule(ruleId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['intervention-rules'] });
        },
    });

    const stats = statistics as Record<string, any> | undefined;
    const pendingCount = (pendingDecisions as any[]).length;

    const tabItems: { key: SubTab; label: string; badge?: number }[] = [
        { key: 'pending', label: t('interventions.pending', '待处理'), badge: pendingCount > 0 ? pendingCount : undefined },
        { key: 'rules', label: t('interventions.rules', '规则管理') },
        { key: 'all', label: t('interventions.allRecords', '全部记录') },
    ];

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
            <h1 style={{ fontSize: '20px', fontWeight: 600, margin: 0, marginBottom: '20px' }}>
                {t('interventions.title', '干预系统')}
            </h1>

            {/* 统计卡片 */}
            <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                gap: '10px', marginBottom: '20px',
            }}>
                {[
                    { label: t('interventions.totalInterventions', '总干预数'), value: stats?.total ?? stats?.totalInterventions ?? '-', color: 'var(--accent)' },
                    { label: t('interventions.pendingCount', '待处理'), value: stats?.pending ?? pendingCount ?? '-', color: '#f59e0b' },
                    { label: t('interventions.escalatedCount', '已升级'), value: stats?.escalated ?? '-', color: '#ef4444' },
                    { label: t('interventions.resolvedCount', '已解决'), value: stats?.resolved ?? stats?.completed ?? '-', color: '#22c55e' },
                ].map((item) => (
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

            {/* Tab 切换 */}
            <div style={{
                display: 'flex', gap: '2px', marginBottom: '16px',
                background: 'var(--bg-secondary)', borderRadius: '8px', padding: '3px',
            }}>
                {tabItems.map((tab) => (
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
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                        }}
                    >
                        {tab.label}
                        {tab.badge !== undefined && (
                            <span style={{
                                fontSize: '11px', padding: '1px 6px', borderRadius: '999px',
                                background: subTab === tab.key ? 'rgba(255,255,255,0.25)' : '#f59e0b',
                                color: subTab === tab.key ? '#fff' : '#000',
                            }}>
                                {tab.badge}
                            </span>
                        )}
                    </button>
                ))}
            </div>

            {/* 待处理干预 */}
            {subTab === 'pending' && (
                <div>
                    {loadingPending && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingPending && (pendingDecisions as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('interventions.noPending', '暂无待处理干预')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(pendingDecisions as any[]).map((item: any) => {
                            const iid = item.id || item.interventionId || '';
                            const isActive = activeIntervention === iid;
                            return (
                                <div key={iid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                                        <span style={{
                                            width: '8px', height: '8px', borderRadius: '50%',
                                            background: '#f59e0b', flexShrink: 0,
                                        }} />
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {item.title || item.type || iid}
                                        </span>
                                        <span style={{
                                            marginLeft: 'auto', fontSize: '11px',
                                            padding: '2px 8px', borderRadius: '999px',
                                            color: '#f59e0b', background: 'rgba(245,158,11,0.12)',
                                        }}>
                                            {item.status || 'pending'}
                                        </span>
                                    </div>
                                    {item.description && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px', lineHeight: 1.5 }}>
                                            {item.description}
                                        </div>
                                    )}
                                    {item.created_at && (
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>
                                            {new Date(item.created_at).toLocaleString()}
                                        </div>
                                    )}

                                    {/* 操作区域 */}
                                    {isActive ? (
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px' }}>
                                            <div>
                                                <textarea
                                                    placeholder={t('interventions.enterResponse', '输入回复内容...')}
                                                    value={responseData}
                                                    onChange={(e) => setResponseData(e.target.value)}
                                                    rows={2}
                                                    style={{
                                                        width: '100%', padding: '8px 12px', borderRadius: '8px',
                                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                                        fontSize: '13px', resize: 'vertical', outline: 'none',
                                                        boxSizing: 'border-box',
                                                    }}
                                                />
                                            </div>
                                            <div>
                                                <input
                                                    type="text"
                                                    placeholder={t('interventions.escalateReason', '升级原因（可选）')}
                                                    value={escalateReason}
                                                    onChange={(e) => setEscalateReason(e.target.value)}
                                                    style={{
                                                        width: '100%', padding: '8px 12px', borderRadius: '8px',
                                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                                        fontSize: '13px', outline: 'none',
                                                        boxSizing: 'border-box',
                                                    }}
                                                />
                                            </div>
                                            <div style={{ display: 'flex', gap: '8px' }}>
                                                <button
                                                    onClick={() => respondMutation.mutate({ id: iid, response: responseData })}
                                                    disabled={!responseData.trim() || respondMutation.isPending}
                                                    style={{
                                                        padding: '6px 14px', borderRadius: '6px', border: 'none',
                                                        background: 'var(--accent)', color: '#fff', fontSize: '13px',
                                                        cursor: responseData.trim() ? 'pointer' : 'not-allowed',
                                                        opacity: responseData.trim() ? 1 : 0.5,
                                                    }}
                                                >
                                                    {respondMutation.isPending ? '...' : t('interventions.respond', '回复')}
                                                </button>
                                                <button
                                                    onClick={() => escalateMutation.mutate({ id: iid, reason: escalateReason || undefined })}
                                                    disabled={escalateMutation.isPending}
                                                    style={{
                                                        padding: '6px 14px', borderRadius: '6px',
                                                        border: '1px solid rgba(239,68,68,0.3)',
                                                        background: 'rgba(239,68,68,0.1)', color: '#ef4444',
                                                        fontSize: '13px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {escalateMutation.isPending ? '...' : t('interventions.escalate', '升级')}
                                                </button>
                                                <button
                                                    onClick={() => { setActiveIntervention(null); setResponseData(''); setEscalateReason(''); }}
                                                    style={{
                                                        padding: '6px 14px', borderRadius: '6px',
                                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                        background: 'transparent', color: 'var(--text-secondary)',
                                                        fontSize: '13px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {t('interventions.cancel', '取消')}
                                                </button>
                                            </div>
                                        </div>
                                    ) : (
                                        <button
                                            onClick={() => setActiveIntervention(iid)}
                                            style={{
                                                padding: '4px 12px', borderRadius: '6px',
                                                border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                background: 'transparent', color: 'var(--accent)',
                                                fontSize: '12px', cursor: 'pointer',
                                            }}
                                        >
                                            {t('interventions.takeAction', '处理')}
                                        </button>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* 规则管理 */}
            {subTab === 'rules' && (
                <div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '12px' }}>
                        <button
                            onClick={() => setShowRegisterRule(!showRegisterRule)}
                            style={{
                                padding: '6px 14px', borderRadius: '6px', border: 'none',
                                background: 'var(--accent)', color: '#fff', fontSize: '13px', cursor: 'pointer',
                            }}
                        >
                            {showRegisterRule ? t('interventions.cancel', '取消') : t('interventions.registerRule', '注册规则')}
                        </button>
                    </div>

                    {showRegisterRule && (
                        <div style={{
                            padding: '14px 16px', borderRadius: '8px',
                            background: 'var(--bg-secondary)', marginBottom: '12px',
                        }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <input
                                    type="text"
                                    placeholder={t('interventions.ruleName', '规则名称')}
                                    value={newRule.name}
                                    onChange={(e) => setNewRule({ ...newRule, name: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                />
                                <input
                                    type="text"
                                    placeholder={t('interventions.ruleDescription', '规则描述')}
                                    value={newRule.description}
                                    onChange={(e) => setNewRule({ ...newRule, description: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                />
                                <input
                                    type="text"
                                    placeholder={t('interventions.ruleType', '规则类型')}
                                    value={newRule.type}
                                    onChange={(e) => setNewRule({ ...newRule, type: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                />
                                <textarea
                                    placeholder={t('interventions.ruleConfig', '规则配置（JSON）')}
                                    value={newRule.config}
                                    onChange={(e) => setNewRule({ ...newRule, config: e.target.value })}
                                    rows={3}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', resize: 'vertical', outline: 'none',
                                    }}
                                />
                                <button
                                    onClick={() => {
                                        const data: any = { name: newRule.name, description: newRule.description, type: newRule.type };
                                        if (newRule.config) {
                                            try { data.config = JSON.parse(newRule.config); } catch { data.config = newRule.config; }
                                        }
                                        registerRuleMutation.mutate(data);
                                    }}
                                    disabled={!newRule.name.trim() || registerRuleMutation.isPending}
                                    style={{
                                        padding: '8px 16px', borderRadius: '6px', border: 'none',
                                        background: 'var(--accent)', color: '#fff', fontSize: '13px',
                                        cursor: newRule.name.trim() ? 'pointer' : 'not-allowed',
                                        opacity: newRule.name.trim() ? 1 : 0.5,
                                        alignSelf: 'flex-start',
                                    }}
                                >
                                    {registerRuleMutation.isPending ? '...' : t('interventions.submit', '提交')}
                                </button>
                            </div>
                        </div>
                    )}

                    {loadingRules && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingRules && (rules as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('interventions.noRules', '暂无干预规则')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(rules as any[]).map((rule: any) => {
                            const rid = rule.id || rule.ruleId || rule.rule_id || '';
                            return (
                                <div key={rid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                    display: 'flex', alignItems: 'center', gap: '10px',
                                }}>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {rule.name || rid}
                                        </div>
                                        {rule.description && (
                                            <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                                                {rule.description}
                                            </div>
                                        )}
                                        {rule.type && (
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                                marginTop: '4px', display: 'inline-block',
                                            }}>
                                                {rule.type}
                                            </span>
                                        )}
                                    </div>
                                    <button
                                        onClick={() => unregisterRuleMutation.mutate(rid)}
                                        disabled={unregisterRuleMutation.isPending}
                                        style={{
                                            padding: '4px 10px', borderRadius: '6px',
                                            border: '1px solid rgba(239,68,68,0.3)',
                                            background: 'rgba(239,68,68,0.1)', color: '#ef4444',
                                            fontSize: '12px', cursor: 'pointer', flexShrink: 0,
                                        }}
                                    >
                                        {t('interventions.unregister', '注销')}
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* 全部记录 */}
            {subTab === 'all' && (
                <div>
                    {loadingAll && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingAll && (interventions as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('interventions.noRecords', '暂无干预记录')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(interventions as any[]).map((item: any) => {
                            const iid = item.id || '';
                            const statusColor = item.status === 'resolved' || item.status === 'completed'
                                ? '#22c55e'
                                : item.status === 'escalated'
                                    ? '#ef4444'
                                    : item.status === 'pending'
                                        ? '#f59e0b'
                                        : 'var(--text-tertiary)';
                            return (
                                <div key={iid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {item.title || item.type || iid}
                                        </span>
                                        <span style={{
                                            marginLeft: 'auto', fontSize: '11px', padding: '2px 8px',
                                            borderRadius: '999px', color: statusColor,
                                            background: `${statusColor}18`,
                                        }}>
                                            {item.status || 'unknown'}
                                        </span>
                                    </div>
                                    {item.description && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                                            {item.description}
                                        </div>
                                    )}
                                    {item.created_at && (
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                            {new Date(item.created_at).toLocaleString()}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}
