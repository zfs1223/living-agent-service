import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { approvalApi } from '../../services/api';
import { useToastStore } from '../../stores/toastStore';

// ─── Approvals Tab ────────────────────────────────
export default function ApprovalsTab({ approvals, resolveApproval }: { approvals: any[]; resolveApproval: any }) {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const [approvalSubTab, setApprovalSubTab] = useState<'all' | 'pending' | 'resolved' | 'my-pending' | 'my-initiated'>('all');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [typeFilter, setTypeFilter] = useState<string>('all');
    const [selectedApproval, setSelectedApproval] = useState<any | null>(null);
    const [approvalSteps, setApprovalSteps] = useState<any[]>([]);
    const [comment, setComment] = useState('');
    const [loadingSteps, setLoadingSteps] = useState(false);

    const [myPendingApprovals, setMyPendingApprovals] = useState<any[]>([]);
    const [loadingMyPending, setLoadingMyPending] = useState(false);
    const [myInitiatedApprovals, setMyInitiatedApprovals] = useState<any[]>([]);
    const [loadingMyInitiated, setLoadingMyInitiated] = useState(false);

    // Load my pending approvals
    const loadMyPending = async () => {
        setLoadingMyPending(true);
        try {
            const data = await approvalApi.getMyPending();
            setMyPendingApprovals(Array.isArray(data) ? data : []);
        } catch { setMyPendingApprovals([]); }
        setLoadingMyPending(false);
    };

    // Load my initiated approvals
    const loadMyInitiated = async () => {
        setLoadingMyInitiated(true);
        try {
            const data = await approvalApi.getMyApprovals();
            setMyInitiatedApprovals(Array.isArray(data) ? data : []);
        } catch { setMyInitiatedApprovals([]); }
        setLoadingMyInitiated(false);
    };

    const filteredApprovals = (approvalSubTab === 'my-pending' ? myPendingApprovals : approvalSubTab === 'my-initiated' ? myInitiatedApprovals : approvals).filter((a: any) => {
        if (approvalSubTab === 'pending') return a.status === 'pending';
        if (approvalSubTab === 'resolved') return a.status !== 'pending';
        return true;
    }).filter((a: any) => {
        if (statusFilter !== 'all') return a.status === statusFilter;
        return true;
    }).filter((a: any) => {
        if (typeFilter !== 'all') return a.type === typeFilter;
        return true;
    });

    const pendingCount = approvals.filter((a: any) => a.status === 'pending').length;
    const approvedCount = approvals.filter((a: any) => a.status === 'approved').length;
    const rejectedCount = approvals.filter((a: any) => a.status === 'rejected').length;

    const openDetail = async (approval: any) => {
        setSelectedApproval(approval);
        setLoadingSteps(true);
        try {
            const steps = await approvalApi.getSteps(approval.id);
            setApprovalSteps(steps || []);
        } catch {
            setApprovalSteps([]);
        }
        setLoadingSteps(false);
    };

    const handleApprove = async (stepId: string) => {
        try {
            await approvalApi.approve(selectedApproval.id, stepId, comment || undefined);
            qc.invalidateQueries({ queryKey: ['approvals'] });
            setSelectedApproval(null);
            setComment('');
        } catch (e: any) {
            useToastStore.getState().showToast(e.message || 'Approve failed', 'error');
        }
    };

    const handleReject = async (stepId: string) => {
        try {
            await approvalApi.reject(selectedApproval.id, stepId, comment || undefined);
            qc.invalidateQueries({ queryKey: ['approvals'] });
            setSelectedApproval(null);
            setComment('');
        } catch (e: any) {
            useToastStore.getState().showToast(e.message || 'Reject failed', 'error');
        }
    };

    const handleCancel = async () => {
        try {
            await approvalApi.cancel(selectedApproval.id);
            qc.invalidateQueries({ queryKey: ['approvals'] });
            setSelectedApproval(null);
            setComment('');
        } catch (e: any) {
            useToastStore.getState().showToast(e.message || 'Cancel failed', 'error');
        }
    };

    const STATUS_STYLES: Record<string, { label: string; bg: string; color: string }> = {
        pending: { label: t('enterprise.approval.statusPending', '待审批'), bg: 'rgba(245,158,11,0.12)', color: 'rgb(245,158,11)' },
        approved: { label: t('enterprise.approval.statusApproved', '已通过'), bg: 'rgba(34,197,94,0.12)', color: 'rgb(34,197,94)' },
        rejected: { label: t('enterprise.approval.statusRejected', '已拒绝'), bg: 'rgba(239,68,68,0.12)', color: 'rgb(239,68,68)' },
        cancelled: { label: t('enterprise.approval.statusCancelled', '已取消'), bg: 'rgba(107,114,128,0.12)', color: 'rgb(107,114,128)' },
    };
    const TYPE_LABELS: Record<string, string> = {
        leave: t('enterprise.approval.typeLeave', '请假'),
        expense: t('enterprise.approval.typeExpense', '报销'),
        purchase: t('enterprise.approval.typePurchase', '采购'),
        contract: t('enterprise.approval.typeContract', '合同'),
        project: t('enterprise.approval.typeProject', '项目'),
        other: t('enterprise.approval.typeOther', '其他'),
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div>
                    <h3>{t('enterprise.tabs.approvals', '审批')}</h3>
                    <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {t('enterprise.approval.description', '管理审批流程、查看审批详情和处理待审批项。')}
                    </p>
                </div>
                <div style={{ display: 'flex', gap: '8px', flexShrink: 0 }}>
                    <span className="badge badge-warning">{t('enterprise.approval.pendingCount', '{{count}} 待审批', { count: pendingCount })}</span>
                    <span className="badge badge-success">{t('enterprise.approval.approvedCount', '{{count}} 已通过', { count: approvedCount })}</span>
                    <span className="badge badge-error">{t('enterprise.approval.rejectedCount', '{{count}} 已拒绝', { count: rejectedCount })}</span>
                </div>
            </div>

            {/* Sub-tabs */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
                {([['all', t('enterprise.approval.all', '全部')], ['pending', t('enterprise.approval.pending', '待审批')], ['my-pending', t('enterprise.approval.myPending', '我待审批')], ['my-initiated', t('enterprise.approval.myInitiated', '我发起的')], ['resolved', t('enterprise.approval.resolved', '已处理')]] as const).map(([key, label]) => (
                    <button key={key} onClick={() => { setApprovalSubTab(key as any); if (key === 'my-pending') loadMyPending(); if (key === 'my-initiated') loadMyInitiated(); }} style={{
                        padding: '4px 14px', borderRadius: '12px', fontSize: '12px', fontWeight: 500, cursor: 'pointer', border: 'none',
                        background: approvalSubTab === key ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                        color: approvalSubTab === key ? '#fff' : 'var(--text-secondary)', transition: 'all 0.15s',
                    }}>{label}</button>
                ))}
                {/* Status filter */}
                <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{
                    marginLeft: '8px', padding: '4px 8px', borderRadius: '8px', fontSize: '11px',
                    border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                }}>
                    <option value="all">{t('enterprise.approval.allStatus', '所有状态')}</option>
                    <option value="pending">{t('enterprise.approval.statusPending', '待审批')}</option>
                    <option value="approved">{t('enterprise.approval.statusApproved', '已通过')}</option>
                    <option value="rejected">{t('enterprise.approval.statusRejected', '已拒绝')}</option>
                    <option value="cancelled">{t('enterprise.approval.statusCancelled', '已取消')}</option>
                </select>
                {/* Type filter */}
                <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)} style={{
                    padding: '4px 8px', borderRadius: '8px', fontSize: '11px',
                    border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                }}>
                    <option value="all">{t('enterprise.approval.allTypes', '所有类型')}</option>
                    {Object.entries(TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
            </div>

            {/* Approval list */}
            {filteredApprovals.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>{t('common.noData')}</div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {filteredApprovals.map((a: any) => {
                        const statusStyle = STATUS_STYLES[a.status] || STATUS_STYLES.pending;
                        return (
                            <div key={a.id} className="card" style={{ padding: '12px 16px', cursor: 'pointer' }} onClick={() => openDetail(a)}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                                            <span style={{ fontWeight: 500, fontSize: '14px' }}>{a.title || a.action_type || a.type || t('enterprise.approval.untitled', '审批项')}</span>
                                            <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: statusStyle.bg, color: statusStyle.color }}>{statusStyle.label}</span>
                                            {a.type && TYPE_LABELS[a.type] && (
                                                <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)' }}>{TYPE_LABELS[a.type]}</span>
                                            )}
                                        </div>
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>
                                            {a.agent_name || a.requester_name || `Agent ${a.agent_id?.slice(0, 8) || ''}`} · {new Date(a.created_at).toLocaleString()}
                                        </div>
                                    </div>
                                    {a.status === 'pending' && (
                                        <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }} onClick={e => e.stopPropagation()}>
                                            <button className="btn btn-primary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => resolveApproval.mutate({ id: a.id, action: 'approve' })}>
                                                {t('enterprise.approval.approve', '通过')}
                                            </button>
                                            <button className="btn btn-danger" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => resolveApproval.mutate({ id: a.id, action: 'reject' })}>
                                                {t('enterprise.approval.reject', '拒绝')}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Detail modal */}
            {selectedApproval && (
                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => { setSelectedApproval(null); setComment(''); }}>
                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '560px', maxWidth: '95vw', maxHeight: '85vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0 }}>{t('enterprise.approval.detail', '审批详情')}</h3>
                            <button onClick={() => { setSelectedApproval(null); setComment(''); }} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                        </div>
                        {/* Basic info */}
                        <div style={{ marginBottom: '16px' }}>
                            <div style={{ fontWeight: 500, fontSize: '16px', marginBottom: '8px' }}>{selectedApproval.title || selectedApproval.type}</div>
                            <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                                {(() => { const s = STATUS_STYLES[selectedApproval.status] || STATUS_STYLES.pending; return <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: s.bg, color: s.color }}>{s.label}</span>; })()}
                                {selectedApproval.type && TYPE_LABELS[selectedApproval.type] && <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '4px', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)' }}>{TYPE_LABELS[selectedApproval.type]}</span>}
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>
                                {t('enterprise.approval.requester', '申请人')}: {selectedApproval.agent_name || selectedApproval.requester_name || '-'} · {new Date(selectedApproval.created_at).toLocaleString()}
                            </div>
                            {selectedApproval.description && (
                                <div style={{ marginTop: '8px', padding: '12px', borderRadius: '8px', background: 'var(--bg-tertiary)', fontSize: '13px' }}>
                                    {selectedApproval.description}
                                </div>
                            )}
                        </div>
                        {/* Steps timeline */}
                        <div style={{ marginBottom: '16px' }}>
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                                {t('enterprise.approval.steps', '审批步骤')}
                            </div>
                            {loadingSteps ? (
                                <div style={{ textAlign: 'center', padding: '20px', color: 'var(--text-tertiary)' }}>{t('common.loading')}</div>
                            ) : approvalSteps.length > 0 ? (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    {approvalSteps.map((step: any, idx: number) => {
                                        const stepStatus = step.status || (idx < approvalSteps.length - 1 ? 'completed' : 'pending');
                                        const stepStyle = STATUS_STYLES[stepStatus] || STATUS_STYLES.pending;
                                        return (
                                            <div key={step.id || idx} style={{ display: 'flex', gap: '12px', alignItems: 'flex-start', padding: '8px 12px', borderRadius: '8px', background: 'var(--bg-tertiary)' }}>
                                                <div style={{ width: '24px', height: '24px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '12px', background: stepStatus === 'completed' ? 'rgba(34,197,94,0.2)' : stepStatus === 'pending' ? 'rgba(245,158,11,0.2)' : 'rgba(239,68,68,0.2)', flexShrink: 0 }}>
                                                    {stepStatus === 'completed' ? '✓' : stepStatus === 'pending' ? '⏳' : '✕'}
                                                </div>
                                                <div style={{ flex: 1 }}>
                                                    <div style={{ fontWeight: 500, fontSize: '13px' }}>{step.name || step.approver_name || `${t('enterprise.approval.step', '步骤')} ${idx + 1}`}</div>
                                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                                        {step.approver_name || step.approver_id?.slice(0, 8) || '-'}
                                                        {step.updated_at && ` · ${new Date(step.updated_at).toLocaleString()}`}
                                                    </div>
                                                    {step.comment && <div style={{ fontSize: '12px', marginTop: '4px', color: 'var(--text-secondary)' }}>{step.comment}</div>}
                                                </div>
                                                <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: stepStyle.bg, color: stepStyle.color }}>{stepStyle.label}</span>
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('enterprise.approval.noSteps', '暂无审批步骤信息')}</div>
                            )}
                        </div>
                        {/* Comment & actions */}
                        {selectedApproval.status === 'pending' && (
                            <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                                <textarea className="form-input" value={comment} onChange={e => setComment(e.target.value)}
                                    placeholder={t('enterprise.approval.commentPlaceholder', '添加评论（可选）...')}
                                    style={{ minHeight: '60px', resize: 'vertical', fontSize: '13px', marginBottom: '8px' }} />
                                <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                    {approvalSteps.length > 0 && <>
                                        <button className="btn btn-primary" onClick={() => {
                                            const currentStep = approvalSteps.find((s: any) => s.status === 'pending');
                                            if (currentStep) handleApprove(currentStep.id);
                                        }}>{t('enterprise.approval.approve', '通过')}</button>
                                        <button className="btn btn-danger" onClick={() => {
                                            const currentStep = approvalSteps.find((s: any) => s.status === 'pending');
                                            if (currentStep) handleReject(currentStep.id);
                                        }}>{t('enterprise.approval.reject', '拒绝')}</button>
                                    </>}
                                    <button className="btn btn-secondary" style={{ color: 'var(--text-tertiary)' }} onClick={() => {
                                        if (confirm(t('enterprise.approval.cancelConfirm', '确定要取消此审批吗？'))) handleCancel();
                                    }}>{t('enterprise.approval.cancel', '取消审批')}</button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
