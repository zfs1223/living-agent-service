/**
 * 我的任务页面
 * 显示我接取的任务列表，可以提交任务结果
 */

import { useState, useEffect } from 'react';
import { taskApi, creditApi } from '../services/api';
import { useAuthStore } from '../stores';
import { useToastStore } from '../stores/toastStore';
import './MyTasks.css';

interface MyTask {
    taskId: string;
    taskType: string;
    description: string;
    priority: number;
    requiredCapability: string;
    status: string;
    checkedOutAt: string;
    completedAt?: string;
    reward?: number;
}

export default function MyTasks() {
    const [tasks, setTasks] = useState<MyTask[]>([]);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'active' | 'completed'>('active');
    const [submittingTaskId, setSubmittingTaskId] = useState<string | null>(null);
    const [submitResult, setSubmitResult] = useState('');
    const [myCredits, setMyCredits] = useState(0);
    const user = useAuthStore((s) => s.user);

    const employeeId = user?.id || '';

    useEffect(() => {
        if (employeeId) {
            loadMyTasks();
            loadMyCredits();
        }
    }, [employeeId, activeTab]);

    const loadMyTasks = async () => {
        setLoading(true);
        try {
            const response = await taskApi.getMyTasks();
            if (Array.isArray(response)) {
                // 根据状态过滤任务
                const filtered = response.filter((task: MyTask) => {
                    if (activeTab === 'active') {
                        return task.status === 'CHECKED_OUT' || task.status === 'IN_PROGRESS';
                    } else {
                        return task.status === 'COMPLETED' || task.status === 'REVIEWED';
                    }
                });
                setTasks(filtered);
            } else {
                setTasks([]);
            }
        } catch (error) {
            console.error('加载任务失败:', error);
            setTasks([]);
        } finally {
            setLoading(false);
        }
    };

    const loadMyCredits = async () => {
        try {
            const response = await creditApi.getBalance();
            if (response && typeof response.balance === 'number') {
                setMyCredits(response.balance);
            }
        } catch (error) {
            console.error('加载积分失败:', error);
        }
    };

    const handleSubmitTask = async (taskId: string) => {
        if (!submitResult.trim()) {
            useToastStore.getState().showToast('请输入任务结果', 'info');
            return;
        }

        setSubmittingTaskId(taskId);
        try {
            const result = await taskApi.submitTask(taskId, submitResult);
            if (result) {
                useToastStore.getState().showToast('任务提交成功！等待审核', 'success');
                setSubmitResult('');
                loadMyTasks();
            }
        } catch (error) {
            console.error('提交任务失败:', error);
            useToastStore.getState().showToast('提交失败，请重试', 'error');
        } finally {
            setSubmittingTaskId(null);
        }
    };

    const getStatusLabel = (status: string) => {
        const labels: Record<string, { text: string; color: string }> = {
            'CHECKED_OUT': { text: '进行中', color: '#1890ff' },
            'IN_PROGRESS': { text: '处理中', color: '#fa8c16' },
            'COMPLETED': { text: '已完成', color: '#52c41a' },
            'REVIEWED': { text: '已审核', color: '#722ed1' }
        };
        return labels[status] || { text: status, color: '#999' };
    };

    const getPriorityLabel = (priority: number) => {
        if (priority >= 5) return { text: '紧急', color: '#f5222d' };
        if (priority >= 3) return { text: '高', color: '#fa8c16' };
        if (priority >= 2) return { text: '中', color: '#1890ff' };
        return { text: '低', color: '#52c41a' };
    };

    if (!employeeId) {
        return (
            <div className="my-tasks-page">
                <div className="login-prompt">
                    <h2>请先登录</h2>
                    <p>登录后即可查看和管理您的任务</p>
                </div>
            </div>
        );
    }

    return (
        <div className="my-tasks-page" style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(59,130,246,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(59,130,246,0.85)' }} />
                            个人任务中心
                        </div>
                        <h1 style={{ fontSize: '28px', fontWeight: 700, margin: 0, letterSpacing: '-0.04em', color: 'var(--text-primary)' }}>我的任务</h1>
                        <p style={{ margin: '10px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75, maxWidth: '68ch' }}>
                            在这里查看自己接取的任务、提交结果并跟踪审核状态。
                        </p>
                    </div>
                    <div style={{ padding: '14px 16px', borderRadius: '18px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)', minWidth: '180px' }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>我的积分</div>
                        <div style={{ fontSize: '28px', fontWeight: 700, marginTop: '6px', color: 'var(--success)' }}>{myCredits}</div>
                    </div>
                </div>
            </div>

            <div className="tabs">
                <button
                    className={`tab ${activeTab === 'active' ? 'active' : ''}`}
                    onClick={() => setActiveTab('active')}
                >
                    进行中 ({tasks.filter(t => t.status === 'CHECKED_OUT' || t.status === 'IN_PROGRESS').length})
                </button>
                <button
                    className={`tab ${activeTab === 'completed' ? 'active' : ''}`}
                    onClick={() => setActiveTab('completed')}
                >
                    已完成 ({tasks.filter(t => t.status === 'COMPLETED' || t.status === 'REVIEWED').length})
                </button>
            </div>

            {loading ? (
                <div className="loading-state">加载任务列表...</div>
            ) : tasks.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-icon">📋</div>
                    <p>{activeTab === 'active' ? '暂无进行中的任务' : '暂无已完成的任务'}</p>
                    <span>
                        {activeTab === 'active'
                            ? '前往公共任务栏接取任务开始赚取积分'
                            : '完成任务后将显示在这里'}
                    </span>
                </div>
            ) : (
                <div className="task-list">
                    {tasks.map(task => {
                        const status = getStatusLabel(task.status);
                        const priority = getPriorityLabel(task.priority);
                        const isSubmitting = submittingTaskId === task.taskId;

                        return (
                            <div key={task.taskId} className="task-card">
                                <div className="task-header">
                                    <div className="task-badges">
                                        <span
                                            className="status-badge"
                                            style={{ backgroundColor: status.color + '20', color: status.color }}
                                        >
                                            {status.text}
                                        </span>
                                        <span
                                            className="priority-badge"
                                            style={{ backgroundColor: priority.color + '20', color: priority.color }}
                                        >
                                            {priority.text}
                                        </span>
                                    </div>
                                    <span className="task-date">
                                        {new Date(task.checkedOutAt).toLocaleDateString()}
                                    </span>
                                </div>

                                <div className="task-body">
                                    <h3 className="task-type">{task.taskType}</h3>
                                    <p className="task-description">{task.description}</p>

                                    <div className="task-meta">
                                        <div className="meta-item">
                                            <span className="meta-label">所需能力</span>
                                            <span className="meta-value">{task.requiredCapability}</span>
                                        </div>
                                        {task.reward && (
                                            <div className="meta-item">
                                                <span className="meta-label">奖励积分</span>
                                                <span className="meta-value reward">{task.reward}</span>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {activeTab === 'active' && (
                                    <div className="task-submit">
                                        <textarea
                                            placeholder="请输入任务结果..."
                                            value={submitResult}
                                            onChange={(e) => setSubmitResult(e.target.value)}
                                            rows={3}
                                        />
                                        <button
                                            className="submit-btn"
                                            onClick={() => handleSubmitTask(task.taskId)}
                                            disabled={isSubmitting || !submitResult.trim()}
                                        >
                                            {isSubmitting ? '提交中...' : '提交任务'}
                                        </button>
                                    </div>
                                )}

                                {activeTab === 'completed' && task.completedAt && (
                                    <div className="task-completed-info">
                                        <span className="completed-label">完成时间</span>
                                        <span className="completed-date">
                                            {new Date(task.completedAt).toLocaleString()}
                                        </span>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
