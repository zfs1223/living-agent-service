import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { autonomousApi } from '../services/autonomousApi';
import type { Opportunity, ActiveHunt, ROIResult, PayoutAccount, PayoutRecord, IncomeRecord, EvolutionTierInfo } from '../services/autonomousApi';

const TIER_COLORS: Record<string, string> = {
    EVOLVING: '#22c55e',
    NORMAL: '#3b82f6',
    SAVING: '#f59e0b',
    MINIMAL: '#6b7280',
};

const TYPE_LABELS: Record<string, string> = {
    GITHUB_BOUNTY: 'GitHub Bounty',
    GITHUB_ISSUE: 'GitHub Issue',
    FREELANCE_PROJECT: 'Freelance',
    BUG_BOUNTY: 'Bug Bounty',
    INTERNAL_TASK: 'Internal',
};

const STATUS_COLORS: Record<string, string> = {
    IN_PROGRESS: '#3b82f6',
    COMPLETED: '#22c55e',
    FAILED: '#ef4444',
    REJECTED: '#6b7280',
};

const ROI_COLORS: Record<string, string> = {
    HUNT: '#22c55e',
    PASS: '#ef4444',
    CONSULT: '#f59e0b',
};

type TabKey = 'overview' | 'bounty' | 'payout' | 'ledger' | 'evolution';

export default function Autonomous() {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const [activeTab, setActiveTab] = useState<TabKey>('overview');

    const { data: overview, isLoading: overviewLoading } = useQuery({
        queryKey: ['autonomous-overview'],
        queryFn: () => autonomousApi.getOverview(),
    });

    const { data: opportunities = [], isLoading: oppsLoading } = useQuery({
        queryKey: ['autonomous-opportunities'],
        queryFn: () => autonomousApi.getOpportunities(),
        enabled: activeTab === 'bounty',
    });

    const { data: activeHunts = [] } = useQuery({
        queryKey: ['autonomous-active-hunts'],
        queryFn: () => autonomousApi.getActiveHunts(),
        enabled: activeTab === 'bounty',
    });

    const { data: payoutAccounts = [] } = useQuery({
        queryKey: ['autonomous-payout-accounts'],
        queryFn: () => autonomousApi.getPayoutAccounts(),
        enabled: activeTab === 'payout',
    });

    const { data: payoutHistory = [] } = useQuery({
        queryKey: ['autonomous-payout-history'],
        queryFn: () => autonomousApi.getPayoutHistory(),
        enabled: activeTab === 'payout',
    });

    const { data: payoutSummary } = useQuery({
        queryKey: ['autonomous-payout-summary'],
        queryFn: () => autonomousApi.getPayoutSummary(),
        enabled: activeTab === 'payout',
    });

    const { data: ledgerHistory = [] } = useQuery({
        queryKey: ['autonomous-ledger-history'],
        queryFn: () => autonomousApi.getLedgerHistory(50),
        enabled: activeTab === 'ledger',
    });

    const { data: evolutionTier } = useQuery({
        queryKey: ['autonomous-evolution-tier'],
        queryFn: () => autonomousApi.getEvolutionTier(),
        enabled: activeTab === 'evolution',
    });

    const discoverMut = useMutation({
        mutationFn: () => autonomousApi.discoverOpportunities({ scanGitHub: true }),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['autonomous-opportunities'] }),
    });

    const tabs: { key: TabKey; label: string }[] = [
        { key: 'overview', label: t('autonomous.tabs.overview', '总览') },
        { key: 'bounty', label: t('autonomous.tabs.bounty', '赏金猎取') },
        { key: 'payout', label: t('autonomous.tabs.payout', '收款管理') },
        { key: 'ledger', label: t('autonomous.tabs.ledger', '账本') },
        { key: 'evolution', label: t('autonomous.tabs.evolution', '进化追踪') },
    ];

    const formatCents = (cents: number) => `¥${(cents / 100).toFixed(2)}`;

    return (
        <div style={{ maxWidth: '960px', margin: '0 auto', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
                <h1 style={{ fontSize: '20px', fontWeight: 600, margin: 0 }}>
                    {t('autonomous.title', '经济自治')}
                </h1>
            </div>

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid var(--border-subtle)', marginBottom: '20px' }}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        onClick={() => setActiveTab(tab.key)}
                        style={{
                            background: 'none', border: 'none', cursor: 'pointer',
                            padding: '10px 16px', fontSize: '13px', fontWeight: 500,
                            color: activeTab === tab.key ? 'var(--text-primary)' : 'var(--text-tertiary)',
                            borderBottom: activeTab === tab.key ? '2px solid var(--accent-primary)' : '2px solid transparent',
                            marginBottom: '-1px', transition: 'all 0.15s',
                        }}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Overview Tab */}
            {activeTab === 'overview' && (
                overviewLoading ? <LoadingSpinner /> : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '12px' }}>
                        <StatCard label={t('autonomous.creditBalance', '积分余额')} value={overview ? String(overview.creditBalance) : '0'} />
                        <StatCard label={t('autonomous.totalEarned', '累计赚取')} value={overview ? formatCents(overview.totalEarned) : '¥0'} />
                        <StatCard label={t('autonomous.performanceScore', '绩效分数')} value={overview ? overview.performanceScore.toFixed(1) : '0'} />
                        <StatCard label={t('autonomous.ledgerBalance', '账本余额')} value={overview ? formatCents(overview.ledgerBalance) : '¥0'} />
                        <StatCard
                            label={t('autonomous.evolutionTier', '进化等级')}
                            value={overview?.tierName || '-'}
                            color={TIER_COLORS[overview?.tier || '']}
                        />
                        <StatCard label={t('autonomous.accumulatedFunds', '累计资金')} value={overview ? formatCents(overview.accumulatedFunds) : '¥0'} />
                        <StatCard label={t('autonomous.activeHunts', '进行中猎取')} value={overview ? String(overview.activeHunts) : '0'} />
                        <StatCard label={t('autonomous.discoveredOpps', '已发现机会')} value={overview ? String(overview.discoveredOpportunities) : '0'} />
                        <StatCard label={t('autonomous.pendingPayout', '待收款项')} value={overview ? formatCents(overview.pendingPayout) : '¥0'} />
                        <StatCard label={t('autonomous.totalCollected', '已收总额')} value={overview ? formatCents(overview.totalCollected) : '¥0'} />
                        <StatCard label={t('autonomous.successfulPayouts', '成功收款')} value={overview ? String(overview.successfulPayouts) : '0'} />
                    </div>
                )
            )}

            {/* Bounty Tab */}
            {activeTab === 'bounty' && (
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                        <button
                            className="btn btn-primary"
                            onClick={() => discoverMut.mutate()}
                            disabled={discoverMut.isPending}
                            style={{ fontSize: '13px', padding: '6px 16px' }}
                        >
                            {discoverMut.isPending ? t('autonomous.scanning', '扫描中...') : t('autonomous.discover', '扫描机会')}
                        </button>
                    </div>

                    {/* Active Hunts */}
                    {activeHunts.length > 0 && (
                        <div style={{ marginBottom: '20px' }}>
                            <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                                {t('autonomous.activeHunts', '进行中的猎取')} ({activeHunts.length})
                            </h3>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {activeHunts.map((hunt: ActiveHunt) => (
                                    <div key={hunt.huntId} style={{
                                        padding: '10px 14px', borderRadius: '8px',
                                        background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                                    }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <span style={{ fontSize: '13px', fontWeight: 500 }}>{hunt.opportunity.title}</span>
                                            <span style={{ fontSize: '11px', color: STATUS_COLORS[hunt.status] || 'var(--text-tertiary)' }}>
                                                {hunt.status}
                                            </span>
                                        </div>
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                            {TYPE_LABELS[hunt.opportunity.type] || hunt.opportunity.type} · {formatCents(hunt.opportunity.payoutCents)}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Discovered Opportunities */}
                    <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                        {t('autonomous.discoveredOpportunities', '已发现机会')} ({opportunities.length})
                    </h3>
                    {oppsLoading ? <LoadingSpinner /> : opportunities.length === 0 ? (
                        <EmptyState message={t('autonomous.noOpportunities', '暂无发现的机会，点击扫描')} />
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {opportunities.map((opp: Opportunity) => (
                                <OpportunityCard key={opp.opportunityId} opportunity={opp} formatCents={formatCents} />
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Payout Tab */}
            {activeTab === 'payout' && (
                <div>
                    {/* Summary */}
                    {payoutSummary && (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '10px', marginBottom: '20px' }}>
                            <StatCard label={t('autonomous.totalCollected', '已收总额')} value={formatCents(payoutSummary.totalCollected)} />
                            <StatCard label={t('autonomous.pendingAmount', '待收金额')} value={formatCents(payoutSummary.pendingAmount)} />
                            <StatCard label={t('autonomous.successfulPayouts', '成功')} value={String(payoutSummary.successfulPayouts)} />
                            <StatCard label={t('autonomous.pendingPayouts', '待处理')} value={String(payoutSummary.pendingPayouts)} />
                            <StatCard label={t('autonomous.thisMonth', '本月收入')} value={formatCents(payoutSummary.thisMonthCollected)} />
                        </div>
                    )}

                    {/* Accounts */}
                    <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                        {t('autonomous.payoutAccounts', '收款账户')} ({payoutAccounts.length})
                    </h3>
                    {payoutAccounts.length === 0 ? (
                        <EmptyState message={t('autonomous.noAccounts', '暂无收款账户')} />
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px' }}>
                            {(payoutAccounts as any[]).map((acct: any) => (
                                <div key={acct.accountId} style={{
                                    padding: '10px 14px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                }}>
                                    <div>
                                        <span style={{ fontSize: '13px', fontWeight: 500 }}>{acct.accountName}</span>
                                        <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginLeft: '8px' }}>
                                            {acct.provider} · {acct.accountType}
                                        </span>
                                    </div>
                                    <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                                        {acct.isDefault && (
                                            <span style={{ fontSize: '11px', padding: '2px 6px', borderRadius: '4px', background: 'var(--accent-primary)', color: '#fff' }}>
                                                {t('autonomous.default', '默认')}
                                            </span>
                                        )}
                                        {acct.verified && (
                                            <span style={{ fontSize: '11px', color: '#22c55e' }}>✓</span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* History */}
                    <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                        {t('autonomous.payoutHistory', '收款记录')}
                    </h3>
                    {payoutHistory.length === 0 ? (
                        <EmptyState message={t('autonomous.noPayoutHistory', '暂无收款记录')} />
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {(payoutHistory as any[]).map((rec: any) => (
                                <div key={rec.payoutId} style={{
                                    padding: '8px 14px', borderRadius: '6px',
                                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                }}>
                                    <div>
                                        <span style={{ fontSize: '13px' }}>{rec.sourceType}</span>
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginLeft: '8px' }}>
                                            {rec.createdAt ? new Date(rec.createdAt).toLocaleDateString() : ''}
                                        </span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                        <span style={{ fontSize: '13px', fontWeight: 500 }}>{formatCents(rec.amount)}</span>
                                        <span style={{
                                            fontSize: '11px', padding: '2px 6px', borderRadius: '4px',
                                            background: rec.status === 'COMPLETED' ? 'rgba(34,197,94,0.15)' : 'rgba(245,158,11,0.15)',
                                            color: rec.status === 'COMPLETED' ? '#22c55e' : '#f59e0b',
                                        }}>
                                            {rec.status}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Ledger Tab */}
            {activeTab === 'ledger' && (
                <div>
                    {ledgerHistory.length === 0 ? (
                        <EmptyState message={t('autonomous.noLedgerHistory', '暂无账本记录')} />
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {(ledgerHistory as any[]).map((rec: any) => (
                                <div key={rec.incomeId} style={{
                                    padding: '8px 14px', borderRadius: '6px',
                                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                }}>
                                    <div>
                                        <span style={{ fontSize: '13px', fontWeight: 500 }}>{rec.sourceType}</span>
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginLeft: '8px' }}>
                                            {rec.sourceId}
                                        </span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                        <span style={{ fontSize: '13px', fontWeight: 500, color: '#22c55e' }}>
                                            +{formatCents(rec.amountCents)}
                                        </span>
                                        <span style={{
                                            fontSize: '11px', padding: '2px 6px', borderRadius: '4px',
                                            background: rec.status === 'CONFIRMED' ? 'rgba(34,197,94,0.15)' : 'rgba(245,158,11,0.15)',
                                            color: rec.status === 'CONFIRMED' ? '#22c55e' : '#f59e0b',
                                        }}>
                                            {rec.status}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Evolution Tab */}
            {activeTab === 'evolution' && (
                <div>
                    {evolutionTier ? (
                        <div style={{
                            padding: '24px', borderRadius: '12px',
                            background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                            textAlign: 'center',
                        }}>
                            <div style={{
                                fontSize: '48px', marginBottom: '12px',
                                color: TIER_COLORS[evolutionTier.tier] || 'var(--text-primary)',
                            }}>
                                {evolutionTier.tier === 'EVOLVING' ? '🚀' :
                                 evolutionTier.tier === 'NORMAL' ? '⚡' :
                                 evolutionTier.tier === 'SAVING' ? '积蓄' : '🌱'}
                            </div>
                            <div style={{ fontSize: '24px', fontWeight: 700, color: TIER_COLORS[evolutionTier.tier] || 'var(--text-primary)' }}>
                                {evolutionTier.tierName}
                            </div>
                            <div style={{ fontSize: '14px', color: 'var(--text-tertiary)', marginTop: '8px' }}>
                                {evolutionTier.description}
                            </div>
                            <div style={{ fontSize: '18px', fontWeight: 600, marginTop: '16px' }}>
                                {t('autonomous.accumulatedFunds', '累计资金')}: {formatCents(evolutionTier.accumulatedFunds)}
                            </div>

                            {/* Tier Progress Bar */}
                            <div style={{ marginTop: '20px', maxWidth: '400px', margin: '20px auto 0' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    <span>MINIMAL</span><span>SAVING</span><span>NORMAL</span><span>EVOLVING</span>
                                </div>
                                <div style={{ height: '6px', borderRadius: '3px', background: 'var(--bg-tertiary)', position: 'relative' }}>
                                    <div style={{
                                        height: '100%', borderRadius: '3px',
                                        background: TIER_COLORS[evolutionTier.tier] || '#3b82f6',
                                        width: `${
                                            evolutionTier.tier === 'MINIMAL' ? '12.5' :
                                            evolutionTier.tier === 'SAVING' ? '37.5' :
                                            evolutionTier.tier === 'NORMAL' ? '62.5' : '100'
                                        }%`,
                                        transition: 'width 0.5s ease',
                                    }} />
                                </div>
                            </div>
                        </div>
                    ) : (
                        <EmptyState message={t('autonomous.noEvolutionData', '暂无进化数据')} />
                    )}
                </div>
            )}
        </div>
    );
}

function OpportunityCard({ opportunity, formatCents }: { opportunity: Opportunity; formatCents: (c: number) => string }) {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const [roi, setRoi] = useState<ROIResult | null>(null);

    const roiMut = useMutation({
        mutationFn: () => autonomousApi.evaluateROI(opportunity.opportunityId),
        onSuccess: (data) => setRoi(data),
    });

    return (
        <div style={{
            padding: '12px 14px', borderRadius: '8px',
            background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
        }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: '13px', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {opportunity.title}
                    </div>
                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        <span>{TYPE_LABELS[opportunity.type] || opportunity.type}</span>
                        <span>· {opportunity.sourceType}</span>
                        <span>· {formatCents(opportunity.payoutCents)}</span>
                        {opportunity.riskLevel && <span>· {opportunity.riskLevel}</span>}
                    </div>
                    {opportunity.description && (
                        <div style={{ fontSize: '12px', color: 'var(--text-quaternary)', marginTop: '4px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {opportunity.description}
                        </div>
                    )}
                </div>
                <button
                    className="btn btn-ghost"
                    onClick={() => roiMut.mutate()}
                    disabled={roiMut.isPending}
                    style={{ fontSize: '12px', padding: '4px 10px', marginLeft: '8px', flexShrink: 0 }}
                >
                    {roiMut.isPending ? '...' : t('autonomous.evaluateROI', 'ROI')}
                </button>
            </div>
            {roi && (
                <div style={{
                    marginTop: '8px', padding: '8px 10px', borderRadius: '6px',
                    background: 'var(--bg-tertiary)', display: 'flex', gap: '12px', flexWrap: 'wrap', fontSize: '12px',
                }}>
                    <span style={{ color: ROI_COLORS[roi.decision] || 'var(--text-primary)', fontWeight: 600 }}>
                        {roi.decision}
                    </span>
                    <span>{t('autonomous.profitMargin', '利润率')}: {(roi.profitMargin * 100).toFixed(1)}%</span>
                    <span>{t('autonomous.complexity', '复杂度')}: {roi.complexity}/10</span>
                    <span>{t('autonomous.estimatedCost', '预估成本')}: {formatCents(roi.estimatedCostCents)}</span>
                </div>
            )}
        </div>
    );
}

function StatCard({ label, value, color }: { label: string; value: string; color?: string }) {
    return (
        <div style={{
            padding: '14px 16px', borderRadius: '8px',
            background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
        }}>
            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>{label}</div>
            <div style={{ fontSize: '18px', fontWeight: 600, color: color || 'var(--text-primary)' }}>{value}</div>
        </div>
    );
}

function LoadingSpinner() {
    return (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
            加载中...
        </div>
    );
}

function EmptyState({ message }: { message: string }) {
    return (
        <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
            {message}
        </div>
    );
}
