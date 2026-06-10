import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores';
import { approvalApi } from '../services/api';
import { type ApprovalRecord, type ApprovalStatus, type ApprovalType } from '../types';
import {
    IconClipboardCheck,
    IconClock,
    IconCheck,
    IconX,
    IconFileText,
    IconCurrencyDollar,
    IconShoppingCart,
    IconContract,
    IconFolder,
    IconPlus,
    IconChevronRight,
    IconUser,
    IconCalendar,
} from '@tabler/icons-react';

const statusColors: Record<ApprovalStatus, string> = {
    pending: 'var(--warning)',
    approved: 'var(--success)',
    rejected: 'var(--error)',
    cancelled: 'var(--text-tertiary)',
};

const typeIcons: Record<ApprovalType, React.ReactNode> = {
    leave: <IconUser size={16} />,
    expense: <IconCurrencyDollar size={16} />,
    purchase: <IconShoppingCart size={16} />,
    contract: <IconContract size={16} />,
    project: <IconFolder size={16} />,
    other: <IconFileText size={16} />,
};

export default function Approvals() {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const user = useAuthStore((s) => s.user);

    const statusLabels: Record<ApprovalStatus, string> = {
        pending: t('approvals.pending'),
        approved: t('approvals.approved'),
        rejected: t('approvals.rejected'),
        cancelled: t('approvals.cancelled'),
    };

    const typeLabels: Record<ApprovalType, string> = {
        leave: t('approvals.typeLeave'),
        expense: t('approvals.typeExpense'),
        purchase: t('approvals.typePurchase'),
        contract: t('approvals.typeContract'),
        project: t('approvals.typeProject'),
        other: t('approvals.typeOther'),
    };
    
    const [selectedStatus, setSelectedStatus] = useState<string>('');
    const [selectedType, setSelectedType] = useState<string>('');
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [selectedApproval, setSelectedApproval] = useState<ApprovalRecord | null>(null);
    const [activeTab, setActiveTab] = useState<'all' | 'my-pending'>('all');
    
    const { data: allApprovals = [], isLoading: loadingAll } = useQuery({
        queryKey: ['approvals', selectedStatus, selectedType],
        queryFn: () => approvalApi.list(selectedStatus || undefined, selectedType || undefined),
        enabled: activeTab === 'all',
    });
    
    const { data: myPending = [], isLoading: loadingPending } = useQuery({
        queryKey: ['my-pending-approvals'],
        queryFn: () => approvalApi.getMyPending(),
        enabled: activeTab === 'my-pending',
    });
    
    const approvals = activeTab === 'all' ? allApprovals : myPending;
    const isLoading = activeTab === 'all' ? loadingAll : loadingPending;
    
    const createMutation = useMutation({
        mutationFn: (data: any) => approvalApi.create(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['approvals'] });
            queryClient.invalidateQueries({ queryKey: ['my-pending-approvals'] });
            setShowCreateModal(false);
        },
    });
    
    const approveMutation = useMutation({
        mutationFn: ({ approvalId, stepId, comment }: { approvalId: string; stepId: string; comment?: string }) =>
            approvalApi.approve(approvalId, stepId, comment),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['approvals'] });
            queryClient.invalidateQueries({ queryKey: ['my-pending-approvals'] });
            setSelectedApproval(null);
        },
    });
    
    const rejectMutation = useMutation({
        mutationFn: ({ approvalId, stepId, comment }: { approvalId: string; stepId: string; comment?: string }) =>
            approvalApi.reject(approvalId, stepId, comment),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['approvals'] });
            queryClient.invalidateQueries({ queryKey: ['my-pending-approvals'] });
            setSelectedApproval(null);
        },
    });

    const pendingCount = activeTab === 'my-pending' ? (myPending as ApprovalRecord[]).length : (allApprovals as ApprovalRecord[]).filter(a => a.status === 'pending').length;
    const approvedCount = (allApprovals as ApprovalRecord[]).filter(a => a.status === 'approved').length;
    const rejectedCount = (allApprovals as ApprovalRecord[]).filter(a => a.status === 'rejected').length;

    return (
        <div className="page-container" style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(250,204,21,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--warning)', boxShadow: '0 0 18px rgba(250,204,21,0.85)' }} />
                            {t('approvals.commandCenter')}
                        </div>
                        <h1 className="page-title" style={{ margin: 0, fontSize: '28px', lineHeight: 1.1 }}>{t('approvals.title')}</h1>
                        <p className="page-subtitle" style={{ marginTop: '10px', maxWidth: '68ch', lineHeight: 1.75 }}>
                            {t('approvals.subtitle')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '320px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('approvals.pending')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: 'var(--warning)' }}>{pendingCount}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('approvals.approved')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: 'var(--success)' }}>{approvedCount}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('approvals.rejected')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: 'var(--error)' }}>{rejectedCount}</div>
                        </div>
                        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)} style={{ height: '100%', justifyContent: 'center' }}>
                            <IconPlus size={16} stroke={1.5} />
                            <span>{t('approvals.newRequest')}</span>
                        </button>
                    </div>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: '12px' }}>
                {[
                    { label: t('approvals.pending'), value: pendingCount, color: 'var(--warning)' },
                    { label: t('approvals.approved'), value: approvedCount, color: 'var(--success)' },
                    { label: t('approvals.rejected'), value: rejectedCount, color: 'var(--error)' },
                    { label: t('approvals.currentMode'), value: activeTab === 'all' ? t('approvals.allApprovals') : t('approvals.mine'), color: 'var(--text-primary)' },
                ].map((item) => (
                    <div key={item.label} style={{ padding: '14px 16px', borderRadius: '18px', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)' }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{item.label}</div>
                        <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: item.color }}>{item.value}</div>
                    </div>
                ))}
            </div>
            
            <div className="tabs-container" style={{ background: 'rgba(255,255,255,0.03)', padding: '8px', borderRadius: '18px', border: '1px solid var(--border-subtle)' }}>
                <button
                    className={`tab-button ${activeTab === 'all' ? 'active' : ''}`}
                    onClick={() => setActiveTab('all')}
                >
                    {t('approvals.allApprovalsTab')}
                </button>
                <button
                    className={`tab-button ${activeTab === 'my-pending' ? 'active' : ''}`}
                    onClick={() => setActiveTab('my-pending')}
                >
                    {t('approvals.myPendingTab')}
                    {(myPending as ApprovalRecord[]).length > 0 && (
                        <span className="tab-badge">{(myPending as ApprovalRecord[]).length}</span>
                    )}
                </button>
            </div>
            
            {activeTab === 'all' && (
                <div className="page-filters">
                    <select
                        className="form-select"
                        value={selectedStatus}
                        onChange={(e) => setSelectedStatus(e.target.value)}
                    >
                        <option value="">{t('approvals.allStatus')}</option>
                        {Object.entries(statusLabels).map(([status, label]) => (
                            <option key={status} value={status}>{label}</option>
                        ))}
                    </select>
                    <select
                        className="form-select"
                        value={selectedType}
                        onChange={(e) => setSelectedType(e.target.value)}
                    >
                        <option value="">{t('approvals.allTypes')}</option>
                        {Object.entries(typeLabels).map(([type, label]) => (
                            <option key={type} value={type}>{label}</option>
                        ))}
                    </select>
                </div>
            )}
            
            {isLoading ? (
                <div className="loading-state">{t('approvals.loading')}</div>
            ) : (approvals as ApprovalRecord[]).length === 0 ? (
                <div className="empty-state">
                    <IconClipboardCheck size={48} stroke={1} />
                    <p>{activeTab === 'my-pending' ? t('approvals.noPendingApprovals') : t('approvals.noApprovalsYet')}</p>
                </div>
            ) : (
                <div className="approval-list">
                    {(approvals as ApprovalRecord[]).map((approval) => (
                        <div
                            key={approval.id}
                            className="approval-card"
                            onClick={() => setSelectedApproval(approval)}
                        >
                            <div className="approval-card-icon">
                                {typeIcons[approval.type]}
                            </div>
                            <div className="approval-card-content">
                                <div className="approval-card-header">
                                    <h3 className="approval-card-title">{approval.title}</h3>
                                    <span
                                        className="approval-status-badge"
                                        style={{ background: statusColors[approval.status] }}
                                    >
                                        {statusLabels[approval.status]}
                                    </span>
                                </div>
                                <p className="approval-card-desc">{approval.description || ''}</p>
                                <div className="approval-card-meta">
                                    <span className="approval-meta-item">
                                        <IconUser size={14} />
                                        {approval.applicant_name || 'Unknown'}
                                    </span>
                                    <span className="approval-meta-item">
                                        <IconCalendar size={14} />
                                        {new Date(approval.created_at).toLocaleDateString()}
                                    </span>
                                    {approval.amount && (
                                        <span className="approval-meta-item amount">
                                            ¥{approval.amount.toLocaleString()}
                                        </span>
                                    )}
                                </div>
                                {approval.status === 'pending' && (
                                    <div className="approval-progress">
                                        <div className="approval-progress-bar">
                                            <div
                                                className="approval-progress-fill"
                                                style={{ width: `${(approval.current_step / approval.total_steps) * 100}%` }}
                                            />
                                        </div>
                                        <span className="approval-progress-text">
                                            {approval.current_step}/{approval.total_steps}
                                        </span>
                                    </div>
                                )}
                            </div>
                            <IconChevronRight size={20} className="approval-card-arrow" />
                        </div>
                    ))}
                </div>
            )}
            
            {showCreateModal && (
                <CreateApprovalModal
                    onClose={() => setShowCreateModal(false)}
                    onSubmit={(data) => createMutation.mutate(data)}
                    loading={createMutation.isPending}
                />
            )}
            
            {selectedApproval && (
                <ApprovalDetailModal
                    approval={selectedApproval}
                    onClose={() => setSelectedApproval(null)}
                    onApprove={(stepId, comment) => approveMutation.mutate({
                        approvalId: selectedApproval.id,
                        stepId,
                        comment,
                    })}
                    onReject={(stepId, comment) => rejectMutation.mutate({
                        approvalId: selectedApproval.id,
                        stepId,
                        comment,
                    })}
                    loading={approveMutation.isPending || rejectMutation.isPending}
                    currentUserId={user?.id}
                />
            )}
        </div>
    );
}

function CreateApprovalModal({ onClose, onSubmit, loading }: {
    onClose: () => void;
    onSubmit: (data: any) => void;
    loading: boolean;
}) {
    const { t } = useTranslation();
    const [form, setForm] = useState({
        type: 'other' as ApprovalType,
        title: '',
        description: '',
        amount: '',
    });

    const typeLabels: Record<ApprovalType, string> = {
        leave: t('approvals.typeLeave'),
        expense: t('approvals.typeExpense'),
        purchase: t('approvals.typePurchase'),
        contract: t('approvals.typeContract'),
        project: t('approvals.typeProject'),
        other: t('approvals.typeOther'),
    };
    
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        onSubmit({
            ...form,
            amount: form.amount ? parseFloat(form.amount) : undefined,
        });
    };
    
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{t('approvals.newApprovalRequest')}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>{t('approvals.type')}</label>
                            <select
                                className="form-select"
                                value={form.type}
                                onChange={(e) => setForm({ ...form, type: e.target.value as ApprovalType })}
                            >
                                {Object.entries(typeLabels).map(([type, label]) => (
                                    <option key={type} value={type}>{label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="form-group">
                            <label>{t('approvals.approvalTitle')}</label>
                            <input
                                className="form-input"
                                value={form.title}
                                onChange={(e) => setForm({ ...form, title: e.target.value })}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('approvals.description')}</label>
                            <textarea
                                className="form-textarea"
                                value={form.description}
                                onChange={(e) => setForm({ ...form, description: e.target.value })}
                                rows={3}
                            />
                        </div>
                        {(form.type === 'expense' || form.type === 'purchase') && (
                            <div className="form-group">
                                <label>{t('approvals.amount')}</label>
                                <input
                                    type="number"
                                    className="form-input"
                                    value={form.amount}
                                    onChange={(e) => setForm({ ...form, amount: e.target.value })}
                                    placeholder="0.00"
                                />
                            </div>
                        )}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-ghost" onClick={onClose}>
                            {t('approvals.cancel')}
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={loading}>
                            {loading ? '...' : t('approvals.submit')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function ApprovalDetailModal({ approval, onClose, onApprove, onReject, loading, currentUserId }: {
    approval: ApprovalRecord;
    onClose: () => void;
    onApprove: (stepId: string, comment?: string) => void;
    onReject: (stepId: string, comment?: string) => void;
    loading: boolean;
    currentUserId?: string;
}) {
    const { t } = useTranslation();
    const [comment, setComment] = useState('');

    const statusLabels: Record<ApprovalStatus, string> = {
        pending: t('approvals.pending'),
        approved: t('approvals.approved'),
        rejected: t('approvals.rejected'),
        cancelled: t('approvals.cancelled'),
    };

    const typeLabels: Record<ApprovalType, string> = {
        leave: t('approvals.typeLeave'),
        expense: t('approvals.typeExpense'),
        purchase: t('approvals.typePurchase'),
        contract: t('approvals.typeContract'),
        project: t('approvals.typeProject'),
        other: t('approvals.typeOther'),
    };
    const { data: steps = [] } = useQuery({
        queryKey: ['approval-steps', approval.id],
        queryFn: () => approvalApi.getSteps(approval.id),
    });
    
    const currentStep = (steps as any[]).find((s: any) => s.status === 'pending' && s.approver_id === currentUserId);
    
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content modal-lg" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <div>
                        <h2>{approval.title}</h2>
                        <span
                            className="approval-status-badge"
                            style={{ background: statusColors[approval.status] }}
                        >
                            {statusLabels[approval.status]}
                        </span>
                    </div>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <div className="modal-body">
                    <div className="approval-detail-section">
                        <h3>{t('approvals.requestInfo')}</h3>
                        <div className="approval-detail-grid">
                            <div className="detail-item">
                                <span className="detail-label">{t('approvals.type')}</span>
                                <span className="detail-value">{typeLabels[approval.type]}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{t('approvals.applicant')}</span>
                                <span className="detail-value">{approval.applicant_name || '-'}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{t('approvals.submitted')}</span>
                                <span className="detail-value">{new Date(approval.created_at).toLocaleString()}</span>
                            </div>
                            {approval.amount && (
                                <div className="detail-item">
                                    <span className="detail-label">{t('approvals.amount')}</span>
                                    <span className="detail-value">¥{approval.amount.toLocaleString()}</span>
                                </div>
                            )}
                        </div>
                        {approval.description && (
                            <div className="detail-full">
                                <span className="detail-label">{t('approvals.description')}</span>
                                <p className="detail-text">{approval.description}</p>
                            </div>
                        )}
                    </div>
                    
                    <div className="approval-detail-section">
                        <h3>{t('approvals.approvalFlow')}</h3>
                        <div className="approval-steps">
                            {(steps as any[]).map((step: any, index: number) => (
                                <div key={step.id} className={`approval-step ${step.status}`}>
                                    <div className="step-number">{index + 1}</div>
                                    <div className="step-content">
                                        <div className="step-header">
                                            <span className="step-approver">{step.approver_name || '审批人'}</span>
                                            <span className={`step-status step-status-${step.status}`}>
                                                {step.status === 'approved' ? t('approvals.approved') :
                                                 step.status === 'rejected' ? t('approvals.rejected') :
                                                 t('approvals.pendingApproval')}
                                            </span>
                                        </div>
                                        {step.comment && (
                                            <p className="step-comment">{step.comment}</p>
                                        )}
                                        {step.acted_at && (
                                            <span className="step-time">{new Date(step.acted_at).toLocaleString()}</span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                    
                    {currentStep && approval.status === 'pending' && (
                        <div className="approval-action-section">
                            <h3>{t('approvals.yourAction')}</h3>
                            <div className="form-group">
                                <label>{t('approvals.comment')}</label>
                                <textarea
                                    className="form-textarea"
                                    value={comment}
                                    onChange={(e) => setComment(e.target.value)}
                                    rows={2}
                                    placeholder={t('approvals.commentPlaceholder')}
                                />
                            </div>
                            <div className="approval-actions">
                                <button
                                    className="btn btn-success"
                                    onClick={() => onApprove(currentStep.id, comment)}
                                    disabled={loading}
                                >
                                    <IconCheck size={16} />
                                    {t('approvals.approve')}
                                </button>
                                <button
                                    className="btn btn-danger"
                                    onClick={() => onReject(currentStep.id, comment)}
                                    disabled={loading}
                                >
                                    <IconX size={16} />
                                    {t('approvals.reject')}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
                <div className="modal-footer">
                    <button className="btn btn-ghost" onClick={onClose}>
                        {t('approvals.close')}
                    </button>
                </div>
            </div>
        </div>
    );
}
