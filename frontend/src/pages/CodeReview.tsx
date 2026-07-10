import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { codeReviewApi } from '../services/api';

type ReviewStatus = 'DEVELOPER_WRITING' | 'CODE_SUBMITTED' | 'REVIEWER_REVIEWING' | 'REVIEW_APPROVED' | 'CHANGES_REQUESTED';

interface Review {
    id: string;
    title: string;
    status: ReviewStatus;
    submitter?: string;
    reviewer?: string;
    createdAt: string;
    updatedAt: string;
}

const statusColor = (s: ReviewStatus) => {
    switch (s) {
        case 'REVIEW_APPROVED': return 'var(--success)';
        case 'CHANGES_REQUESTED': return 'var(--error)';
        case 'CODE_SUBMITTED':
        case 'REVIEWER_REVIEWING': return 'var(--accent-primary)';
        default: return 'var(--text-tertiary)';
    }
};

const statusLabel = (s: ReviewStatus, t: any) => {
    switch (s) {
        case 'DEVELOPER_WRITING': return t('codeReview.writing', '编写中');
        case 'CODE_SUBMITTED': return t('codeReview.submitted', '已提交');
        case 'REVIEWER_REVIEWING': return t('codeReview.reviewing', '审查中');
        case 'REVIEW_APPROVED': return t('codeReview.approved', '已通过');
        case 'CHANGES_REQUESTED': return t('codeReview.changesRequested', '需修改');
        default: return s;
    }
};

type TabKey = 'pending' | 'approved' | 'changes';

export default function CodeReview() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [activeTab, setActiveTab] = useState<TabKey>('pending');
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [comment, setComment] = useState('');

    const statusFilter: Record<TabKey, string | undefined> = {
        pending: undefined,
        approved: 'REVIEW_APPROVED',
        changes: 'CHANGES_REQUESTED',
    };

    const { data: reviews, isLoading, error } = useQuery<Review[]>({
        queryKey: ['code-reviews', activeTab],
        queryFn: () => codeReviewApi.listReviews(statusFilter[activeTab]) as Promise<Review[]>,
    });

    const { data: detail } = useQuery({
        queryKey: ['code-review', selectedId],
        queryFn: () => codeReviewApi.getReview(selectedId!),
        enabled: !!selectedId,
    });

    const approveMutation = useMutation({
        mutationFn: (id: string) => codeReviewApi.approveReview(id, comment || undefined),
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['code-reviews'] }); setSelectedId(null); setComment(''); },
    });

    const requestChangesMutation = useMutation({
        mutationFn: ({ id, comment: c }: { id: string; comment: string }) => codeReviewApi.requestChanges(id, c),
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['code-reviews'] }); setSelectedId(null); setComment(''); },
    });

    const submitMutation = useMutation({
        mutationFn: (id: string) => codeReviewApi.submitCode(id, {}),
        onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['code-reviews'] }); setSelectedId(null); },
    });

    const tabs: { key: TabKey; label: string }[] = [
        { key: 'pending', label: t('codeReview.pending', '待审查') },
        { key: 'approved', label: t('codeReview.approved', '已通过') },
        { key: 'changes', label: t('codeReview.changes', '需修改') },
    ];

    if (error) {
        return (
            <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
                {t('codeReview.loadError', '代码审查功能暂不可用，请确认后端服务已就绪')}
                <div style={{ marginTop: 8, fontSize: 11, color: 'var(--text-tertiary)' }}>{String(error)}</div>
            </div>
        );
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: 'var(--text-primary)' }}>
                {t('codeReview.title', '代码审查')}
            </h2>

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 4 }}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        className={`btn ${activeTab === tab.key ? 'btn-primary' : 'btn-ghost'}`}
                        onClick={() => { setActiveTab(tab.key); setSelectedId(null); }}
                        style={{ fontSize: 13, padding: '6px 14px' }}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Content */}
            {isLoading ? (
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
                    {t('common.loading')}
                </div>
            ) : !reviews || reviews.length === 0 ? (
                <div style={{ padding: 32, textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
                    {t('codeReview.noReviews', '暂无审查记录')}
                </div>
            ) : (
                <div style={{ display: 'grid', gridTemplateColumns: selectedId ? '1fr 1fr' : '1fr', gap: 16 }}>
                    {/* Review list */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 16,
                        overflow: 'hidden',
                    }}>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: '2fr 1fr 1fr 1fr',
                            padding: '10px 16px',
                            fontSize: 11,
                            color: 'var(--text-tertiary)',
                            fontWeight: 500,
                            textTransform: 'uppercase' as const,
                            letterSpacing: '0.05em',
                            borderBottom: '1px solid var(--border-subtle)',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span>{t('codeReview.title', '标题')}</span>
                            <span>{t('codeReview.status', '状态')}</span>
                            <span>{t('codeReview.submitter', '提交人')}</span>
                            <span>{t('codeReview.time', '时间')}</span>
                        </div>
                        <div style={{ maxHeight: 480, overflowY: 'auto' }}>
                            {reviews.map((review) => (
                                <div
                                    key={review.id}
                                    onClick={() => setSelectedId(review.id)}
                                    style={{
                                        display: 'grid',
                                        gridTemplateColumns: '2fr 1fr 1fr 1fr',
                                        alignItems: 'center',
                                        gap: 12,
                                        padding: '10px 16px',
                                        fontSize: 13,
                                        cursor: 'pointer',
                                        background: selectedId === review.id ? 'var(--bg-hover)' : 'transparent',
                                        transition: 'background 120ms ease',
                                    }}
                                    onMouseEnter={e => { if (selectedId !== review.id) (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
                                    onMouseLeave={e => { if (selectedId !== review.id) (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                                >
                                    <span style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{review.title || review.id}</span>
                                    <span style={{ color: statusColor(review.status), display: 'flex', alignItems: 'center', gap: 4 }}>
                                        <span style={{ width: 6, height: 6, borderRadius: '50%', background: statusColor(review.status), display: 'inline-block' }} />
                                        {statusLabel(review.status, t)}
                                    </span>
                                    <span style={{ color: 'var(--text-secondary)' }}>{review.submitter || '-'}</span>
                                    <span style={{ color: 'var(--text-tertiary)', fontSize: 11 }}>
                                        {review.createdAt ? new Date(review.createdAt).toLocaleDateString() : '-'}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Detail panel */}
                    {selectedId && detail && (
                        <div style={{
                            border: '1px solid var(--border-subtle)',
                            borderRadius: 16,
                            overflow: 'hidden',
                            display: 'flex',
                            flexDirection: 'column',
                        }}>
                            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.03)' }}>
                                <h3 style={{ margin: 0, fontSize: 14, fontWeight: 500, color: 'var(--text-primary)' }}>
                                    {(detail as any).title || selectedId}
                                </h3>
                                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>
                                    {t('codeReview.status', '状态')}：{statusLabel((detail as any).status as ReviewStatus, t)}
                                </div>
                            </div>

                            {/* Code diff placeholder */}
                            <div style={{
                                flex: 1,
                                padding: 16,
                                background: 'var(--bg-tertiary)',
                                fontFamily: 'monospace',
                                fontSize: 12,
                                color: 'var(--text-secondary)',
                                minHeight: 120,
                                whiteSpace: 'pre-wrap',
                                overflow: 'auto',
                            }}>
                                {(detail as any).codeDiff || (detail as any).diff || t('codeReview.noDiff', '暂无代码差异')}
                            </div>

                            {/* Comments & Actions */}
                            <div style={{ padding: 12, borderTop: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: 8 }}>
                                <textarea
                                    value={comment}
                                    onChange={e => setComment(e.target.value)}
                                    placeholder={t('codeReview.commentPlaceholder', '输入评论...')}
                                    style={{
                                        width: '100%',
                                        minHeight: 60,
                                        padding: 8,
                                        borderRadius: 8,
                                        border: '1px solid var(--border-subtle)',
                                        background: 'var(--bg-secondary)',
                                        color: 'var(--text-primary)',
                                        fontSize: 13,
                                        resize: 'vertical',
                                        fontFamily: 'inherit',
                                    }}
                                />
                                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                                    {(detail as any).status === 'DEVELOPER_WRITING' && (
                                        <button
                                            className="btn btn-primary"
                                            onClick={() => submitMutation.mutate(selectedId)}
                                            disabled={submitMutation.isPending}
                                        >
                                            {t('codeReview.submit', '提交代码')}
                                        </button>
                                    )}
                                    {(detail as any).status === 'CODE_SUBMITTED' || (detail as any).status === 'REVIEWER_REVIEWING' ? (
                                        <>
                                            <button
                                                className="btn btn-primary"
                                                onClick={() => approveMutation.mutate(selectedId)}
                                                disabled={approveMutation.isPending}
                                            >
                                                {t('codeReview.approve', '通过')}
                                            </button>
                                            <button
                                                className="btn btn-danger"
                                                onClick={() => requestChangesMutation.mutate({ id: selectedId, comment: comment || '请修改' })}
                                                disabled={requestChangesMutation.isPending}
                                            >
                                                {t('codeReview.requestChanges', '退回修改')}
                                            </button>
                                        </>
                                    ) : null}
                                    <button className="btn btn-ghost" onClick={() => { setSelectedId(null); setComment(''); }}>
                                        {t('common.close', '关闭')}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
