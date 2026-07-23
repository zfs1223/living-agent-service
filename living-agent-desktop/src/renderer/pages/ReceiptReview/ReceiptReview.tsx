/**
 * P22: 执行回执审核可视化
 *
 * 显示员工执行回执列表、详情，支持审核操作
 * 后端 API: 通过 ApprovalController 处理 execution_receipt 类型审批
 */
import { useState, useEffect, useCallback } from 'react';
import './ReceiptReview.css';

export interface ExecutionReceipt {
  receiptId: string;
  executionId: string;
  employeeCode: string;
  employeeNeuronId?: string;
  department: string;
  status: 'COMPLETED' | 'FAILED' | 'NEEDS_APPROVAL' | 'NEEDS_REVIEW';
  summary?: string;
  outputArtifacts?: string;
  executionTimeMs?: number;
  createdAt: string;
  updatedAt?: string;
  needsHumanReview?: boolean;
  reviewResult?: string;
  reviewComment?: string;
}

interface ReceiptReviewProps {
  backendUrl: string;
  hasToken: boolean;
}

export default function ReceiptReview({ backendUrl, hasToken }: ReceiptReviewProps) {
  const [receipts, setReceipts] = useState<ExecutionReceipt[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedReceipt, setSelectedReceipt] = useState<ExecutionReceipt | null>(null);
  const [filter, setFilter] = useState<'all' | 'needs_review' | 'completed' | 'failed'>('needs_review');
  const [reviewComment, setReviewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const loadReceipts = useCallback(async () => {
    if (!hasToken || !backendUrl) return;
    setLoading(true);
    setError('');
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const headers = { Authorization: `Bearer ${token}` };

      // 获取待审核的回执（通过审批列表过滤）
      const res = await fetch(`${backendUrl}/api/approvals?status=pending&businessType=execution_receipt`, { headers });
      if (res.ok) {
        const data = await res.json();
        const approvals = data.data || data || [];
        // 转换为回执格式
        const receiptList: ExecutionReceipt[] = approvals.map((a: any) => ({
          receiptId: a.businessId || a.id,
          executionId: a.businessId || '',
          employeeCode: a.metadata?.employeeCode || '',
          department: a.department || '',
          status: 'NEEDS_REVIEW',
          summary: a.description || '',
          createdAt: a.createdAt || new Date().toISOString(),
        }));
        setReceipts(receiptList);
      }
    } catch (e: any) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [backendUrl, hasToken]);

  useEffect(() => { loadReceipts(); }, [loadReceipts]);

  const filteredReceipts = receipts.filter(r => {
    if (filter === 'all') return true;
    if (filter === 'needs_review') return r.status === 'NEEDS_REVIEW' || r.status === 'NEEDS_APPROVAL';
    return r.status.toLowerCase() === filter;
  });

  const handleApprove = async (receipt: ExecutionReceipt) => {
    if (!reviewComment.trim()) {
      alert('请填写审核意见');
      return;
    }
    setSubmitting(true);
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      // 找到对应的审批实例
      const res = await fetch(`${backendUrl}/api/approvals?status=pending&businessType=execution_receipt`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const data = await res.json();
      const approvals = data.data || data || [];
      const approval = approvals.find((a: any) => a.businessId === receipt.receiptId);
      if (!approval) {
        throw new Error('未找到对应审批');
      }

      await fetch(`${backendUrl}/api/approvals/${approval.id}/approve`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ comment: reviewComment }),
      });
      setReviewComment('');
      setSelectedReceipt(null);
      loadReceipts();
    } catch (e: any) {
      alert('审核失败: ' + e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleReject = async (receipt: ExecutionReceipt) => {
    if (!reviewComment.trim()) {
      alert('请填写驳回原因');
      return;
    }
    setSubmitting(true);
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const res = await fetch(`${backendUrl}/api/approvals?status=pending&businessType=execution_receipt`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const data = await res.json();
      const approvals = data.data || data || [];
      const approval = approvals.find((a: any) => a.businessId === receipt.receiptId);
      if (!approval) {
        throw new Error('未找到对应审批');
      }

      await fetch(`${backendUrl}/api/approvals/${approval.id}/reject`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ comment: reviewComment }),
      });
      setReviewComment('');
      setSelectedReceipt(null);
      loadReceipts();
    } catch (e: any) {
      alert('驳回失败: ' + e.message);
    } finally {
      setSubmitting(false);
    }
  };

  if (!hasToken) return <div className="receipt-review__empty">请先登录</div>;
  if (loading) return <div className="receipt-review__empty">加载中...</div>;
  if (error) return <div className="receipt-review__error">{error}</div>;

  return (
    <div className="receipt-review">
      <div className="receipt-review__header">
        <h1>📋 执行回执审核</h1>
        <div className="receipt-review__filters">
          <button
            className={`receipt-review__filter ${filter === 'needs_review' ? 'active' : ''}`}
            onClick={() => setFilter('needs_review')}
          >
            待审核 ({receipts.filter(r => r.status === 'NEEDS_REVIEW' || r.status === 'NEEDS_APPROVAL').length})
          </button>
          <button
            className={`receipt-review__filter ${filter === 'completed' ? 'active' : ''}`}
            onClick={() => setFilter('completed')}
          >
            已通过
          </button>
          <button
            className={`receipt-review__filter ${filter === 'failed' ? 'active' : ''}`}
            onClick={() => setFilter('failed')}
          >
            已驳回
          </button>
          <button
            className={`receipt-review__filter ${filter === 'all' ? 'active' : ''}`}
            onClick={() => setFilter('all')}
          >
            全部
          </button>
        </div>
      </div>

      <div className="receipt-review__body">
        {/* 回执列表 */}
        <div className="receipt-review__list">
          {filteredReceipts.length === 0 && (
            <div className="receipt-review__empty-list">暂无回执</div>
          )}
          {filteredReceipts.map(receipt => (
            <div
              key={receipt.receiptId}
              className={`receipt-card ${selectedReceipt?.receiptId === receipt.receiptId ? 'selected' : ''}`}
              onClick={() => setSelectedReceipt(receipt)}
            >
              <div className="receipt-card__header">
                <span className="receipt-card__id">{receipt.receiptId.slice(0, 12)}...</span>
                <span className={`receipt-card__status receipt-card__status--${receipt.status.toLowerCase()}`}>
                  {receipt.status === 'NEEDS_REVIEW' || receipt.status === 'NEEDS_APPROVAL' ? '待审核' :
                   receipt.status === 'COMPLETED' ? '已通过' : '已驳回'}
                </span>
              </div>
              <div className="receipt-card__info">
                <span className="receipt-card__employee">👤 {receipt.employeeCode || '未知员工'}</span>
                <span className="receipt-card__department">🏢 {receipt.department || '未知部门'}</span>
              </div>
              <div className="receipt-card__summary">
                {receipt.summary?.slice(0, 100) || '无摘要'}
                {receipt.summary && receipt.summary.length > 100 ? '...' : ''}
              </div>
              <div className="receipt-card__time">
                {new Date(receipt.createdAt).toLocaleString()}
              </div>
            </div>
          ))}
        </div>

        {/* 回执详情 */}
        {selectedReceipt && (
          <div className="receipt-review__detail">
            <div className="receipt-detail__header">
              <h2>回执详情</h2>
              <button className="receipt-detail__close" onClick={() => setSelectedReceipt(null)}>✕</button>
            </div>

            <div className="receipt-detail__info">
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">回执 ID</span>
                <span className="receipt-detail__value">{selectedReceipt.receiptId}</span>
              </div>
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">执行 ID</span>
                <span className="receipt-detail__value">{selectedReceipt.executionId}</span>
              </div>
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">员工</span>
                <span className="receipt-detail__value">{selectedReceipt.employeeCode}</span>
              </div>
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">部门</span>
                <span className="receipt-detail__value">{selectedReceipt.department}</span>
              </div>
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">状态</span>
                <span className={`receipt-detail__status receipt-detail__status--${selectedReceipt.status.toLowerCase()}`}>
                  {selectedReceipt.status}
                </span>
              </div>
              <div className="receipt-detail__field">
                <span className="receipt-detail__label">创建时间</span>
                <span className="receipt-detail__value">{new Date(selectedReceipt.createdAt).toLocaleString()}</span>
              </div>
            </div>

            <div className="receipt-detail__section">
              <h3>执行摘要</h3>
              <div className="receipt-detail__summary">
                {selectedReceipt.summary || '无摘要'}
              </div>
            </div>

            {(selectedReceipt.status === 'NEEDS_REVIEW' || selectedReceipt.status === 'NEEDS_APPROVAL') && (
              <div className="receipt-detail__review">
                <h3>审核操作</h3>
                <textarea
                  className="receipt-detail__comment"
                  placeholder="请输入审核意见..."
                  value={reviewComment}
                  onChange={e => setReviewComment(e.target.value)}
                  rows={4}
                />
                <div className="receipt-detail__actions">
                  <button
                    className="receipt-detail__approve"
                    disabled={submitting || !reviewComment.trim()}
                    onClick={() => handleApprove(selectedReceipt)}
                  >
                    ✅ 通过
                  </button>
                  <button
                    className="receipt-detail__reject"
                    disabled={submitting || !reviewComment.trim()}
                    onClick={() => handleReject(selectedReceipt)}
                  >
                    ❌ 驳回
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}