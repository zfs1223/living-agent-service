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

const statusLabels: Record<ApprovalStatus, string> = {
    pending: '待审批',
    approved: '已通过',
    rejected: '已拒绝',
    cancelled: '已取消',
};

const typeIcons: Record<ApprovalType, React.ReactNode> = {
    leave: <IconUser size={16} />,
    expense: <IconCurrencyDollar size={16} />,
    purchase: <IconShoppingCart size={16} />,
    contract: <IconContract size={16} />,
    project: <IconFolder size={16} />,
    other: <IconFileText size={16} />,
};

const typeLabels: Record<ApprovalType, string> = {
    leave: '请假',
    expense: '报销',
    purchase: '采购',
    contract: '合同',
    project: '项目',
    other: '其他',
};

export default function Approvals() {
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language?.startsWith('zh');
    const queryClient = useQueryClient();
    const user = useAuthStore((s) => s.user);
    
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

    return (
        <div className="page-container">
            <div className="page-header">
                <div>
                    <h1 className="page-title">{isChinese ? '审批中心' : 'Approval Center'}</h1>
                    <p className="page-subtitle">{isChinese ? '处理审批流程和申请' : 'Process approval workflows and requests'}</p>
                </div>
                <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
                    <IconPlus size={16} stroke={1.5} />
                    <span>{isChinese ? '发起申请' : 'New Request'}</span>
                </button>
            </div>
            
            <div className="tabs-container">
                <button
                    className={`tab-button ${activeTab === 'all' ? 'active' : ''}`}
                    onClick={() => setActiveTab('all')}
                >
                    {isChinese ? '所有审批' : 'All Approvals'}
                </button>
                <button
                    className={`tab-button ${activeTab === 'my-pending' ? 'active' : ''}`}
                    onClick={() => setActiveTab('my-pending')}
                >
                    {isChinese ? '待我审批' : 'My Pending'}
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
                        <option value="">{isChinese ? '所有状态' : 'All Status'}</option>
                        {Object.entries(statusLabels).map(([status, label]) => (
                            <option key={status} value={status}>{label}</option>
                        ))}
                    </select>
                    <select
                        className="form-select"
                        value={selectedType}
                        onChange={(e) => setSelectedType(e.target.value)}
                    >
                        <option value="">{isChinese ? '所有类型' : 'All Types'}</option>
                        {Object.entries(typeLabels).map(([type, label]) => (
                            <option key={type} value={type}>{label}</option>
                        ))}
                    </select>
                </div>
            )}
            
            {isLoading ? (
                <div className="loading-state">{isChinese ? '加载中...' : 'Loading...'}</div>
            ) : (approvals as ApprovalRecord[]).length === 0 ? (
                <div className="empty-state">
                    <IconClipboardCheck size={48} stroke={1} />
                    <p>{activeTab === 'my-pending' ? (isChinese ? '暂无待审批项目' : 'No pending approvals') : (isChinese ? '暂无审批记录' : 'No approvals yet')}</p>
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
                    isChinese={isChinese}
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
                    isChinese={isChinese}
                    currentUserId={user?.id}
                />
            )}
        </div>
    );
}

function CreateApprovalModal({ onClose, onSubmit, loading, isChinese }: {
    onClose: () => void;
    onSubmit: (data: any) => void;
    loading: boolean;
    isChinese: boolean;
}) {
    const [form, setForm] = useState({
        type: 'other' as ApprovalType,
        title: '',
        description: '',
        amount: '',
    });
    
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
                    <h2>{isChinese ? '发起申请' : 'New Approval Request'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>{isChinese ? '申请类型' : 'Type'}</label>
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
                            <label>{isChinese ? '标题' : 'Title'}</label>
                            <input
                                className="form-input"
                                value={form.title}
                                onChange={(e) => setForm({ ...form, title: e.target.value })}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{isChinese ? '描述' : 'Description'}</label>
                            <textarea
                                className="form-textarea"
                                value={form.description}
                                onChange={(e) => setForm({ ...form, description: e.target.value })}
                                rows={3}
                            />
                        </div>
                        {(form.type === 'expense' || form.type === 'purchase') && (
                            <div className="form-group">
                                <label>{isChinese ? '金额' : 'Amount'}</label>
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
                            {isChinese ? '取消' : 'Cancel'}
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={loading}>
                            {loading ? '...' : (isChinese ? '提交' : 'Submit')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function ApprovalDetailModal({ approval, onClose, onApprove, onReject, loading, isChinese, currentUserId }: {
    approval: ApprovalRecord;
    onClose: () => void;
    onApprove: (stepId: string, comment?: string) => void;
    onReject: (stepId: string, comment?: string) => void;
    loading: boolean;
    isChinese: boolean;
    currentUserId?: string;
}) {
    const [comment, setComment] = useState('');
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
                        <h3>{isChinese ? '申请信息' : 'Request Info'}</h3>
                        <div className="approval-detail-grid">
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '类型' : 'Type'}</span>
                                <span className="detail-value">{typeLabels[approval.type]}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '申请人' : 'Applicant'}</span>
                                <span className="detail-value">{approval.applicant_name || '-'}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '提交时间' : 'Submitted'}</span>
                                <span className="detail-value">{new Date(approval.created_at).toLocaleString()}</span>
                            </div>
                            {approval.amount && (
                                <div className="detail-item">
                                    <span className="detail-label">{isChinese ? '金额' : 'Amount'}</span>
                                    <span className="detail-value">¥{approval.amount.toLocaleString()}</span>
                                </div>
                            )}
                        </div>
                        {approval.description && (
                            <div className="detail-full">
                                <span className="detail-label">{isChinese ? '描述' : 'Description'}</span>
                                <p className="detail-text">{approval.description}</p>
                            </div>
                        )}
                    </div>
                    
                    <div className="approval-detail-section">
                        <h3>{isChinese ? '审批流程' : 'Approval Flow'}</h3>
                        <div className="approval-steps">
                            {(steps as any[]).map((step: any, index: number) => (
                                <div key={step.id} className={`approval-step ${step.status}`}>
                                    <div className="step-number">{index + 1}</div>
                                    <div className="step-content">
                                        <div className="step-header">
                                            <span className="step-approver">{step.approver_name || '审批人'}</span>
                                            <span className={`step-status step-status-${step.status}`}>
                                                {step.status === 'approved' ? (isChinese ? '已通过' : 'Approved') :
                                                 step.status === 'rejected' ? (isChinese ? '已拒绝' : 'Rejected') :
                                                 (isChinese ? '待审批' : 'Pending')}
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
                            <h3>{isChinese ? '审批操作' : 'Your Action'}</h3>
                            <div className="form-group">
                                <label>{isChinese ? '审批意见' : 'Comment'}</label>
                                <textarea
                                    className="form-textarea"
                                    value={comment}
                                    onChange={(e) => setComment(e.target.value)}
                                    rows={2}
                                    placeholder={isChinese ? '可选填写审批意见' : 'Optional comment'}
                                />
                            </div>
                            <div className="approval-actions">
                                <button
                                    className="btn btn-success"
                                    onClick={() => onApprove(currentStep.id, comment)}
                                    disabled={loading}
                                >
                                    <IconCheck size={16} />
                                    {isChinese ? '通过' : 'Approve'}
                                </button>
                                <button
                                    className="btn btn-danger"
                                    onClick={() => onReject(currentStep.id, comment)}
                                    disabled={loading}
                                >
                                    <IconX size={16} />
                                    {isChinese ? '拒绝' : 'Reject'}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
                <div className="modal-footer">
                    <button className="btn btn-ghost" onClick={onClose}>
                        {isChinese ? '关闭' : 'Close'}
                    </button>
                </div>
            </div>
        </div>
    );
}
