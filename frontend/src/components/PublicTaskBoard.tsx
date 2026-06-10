/**
 * 公共任务栏组件
 * 显示所有公开可接取的任务
 */

import { useState, useEffect } from 'react';
import { taskApi, creditApi } from '../services/api';
import { useAuthStore } from '../stores';
import { useToastStore } from '../stores/toastStore';
import './PublicTaskBoard.css';

interface PublicTask {
    taskId: string;
    taskType: string;
    description: string;
    priority: number;
    requiredCapability: string;
    difficulty: string;
    estimatedHours: number;
    reward: number;
    createdAt: string;
}

interface PublicTaskBoardProps {
    department?: string;
}

export default function PublicTaskBoard({ department }: PublicTaskBoardProps) {
    const [tasks, setTasks] = useState<PublicTask[]>([]);
    const [loading, setLoading] = useState(true);
    const [claimingTaskId, setClaimingTaskId] = useState<string | null>(null);
    const [myCredits, setMyCredits] = useState(0);
    const user = useAuthStore((s) => s.user);

    const isLoggedIn = !!user;
    const employeeId = user?.id || '';

    useEffect(() => {
        loadPublicTasks();
        if (isLoggedIn) {
            loadMyCredits();
        }
    }, [department]);

    const loadPublicTasks = async () => {
        setLoading(true);
        try {
            const response = await taskApi.getPublicTasks(department);
            if (Array.isArray(response)) {
                setTasks(response);
            } else {
                setTasks([]);
            }
        } catch (error) {
            console.error('加载公共任务失败:', error);
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

    const handleClaimTask = async (task: PublicTask) => {
        if (!isLoggedIn) {
            useToastStore.getState().showToast('请先登录', 'info');
            return;
        }

        if (!confirm(`确定要接取任务 "${task.description}" 吗？\n预计耗时: ${task.estimatedHours}小时\n奖励: ${task.reward}积分`)) {
            return;
        }

        setClaimingTaskId(task.taskId);
        try {
            const result = await taskApi.claimTask(task.taskId);
            if (result) {
                useToastStore.getState().showToast('任务接取成功！请前往"我的任务"查看', 'success');
                // 从列表中移除已接取的任务
                setTasks(prev => prev.filter(t => t.taskId !== task.taskId));
            }
        } catch (error) {
            console.error('接取任务失败:', error);
            useToastStore.getState().showToast('接取任务失败，请重试', 'error');
        } finally {
            setClaimingTaskId(null);
        }
    };

    const getDifficultyLabel = (difficulty: string) => {
        const labels: Record<string, { text: string; color: string }> = {
            'BEGINNER': { text: '入门', color: '#52c41a' },
            'INTERMEDIATE': { text: '中级', color: '#1890ff' },
            'ADVANCED': { text: '高级', color: '#fa8c16' },
            'EXPERT': { text: '专家', color: '#f5222d' },
            'MASTER': { text: '大师', color: '#722ed1' }
        };
        return labels[difficulty] || { text: difficulty, color: '#999' };
    };

    const getPriorityLabel = (priority: number) => {
        if (priority >= 5) return { text: '紧急', color: '#f5222d' };
        if (priority >= 3) return { text: '高', color: '#fa8c16' };
        if (priority >= 2) return { text: '中', color: '#1890ff' };
        return { text: '低', color: '#52c41a' };
    };

    if (loading) {
        return (
            <div className="public-task-board">
                <div className="loading-state">加载任务列表...</div>
            </div>
        );
    }

    return (
        <div className="public-task-board">
            <div className="board-header">
                <h3>公共任务栏</h3>
                {isLoggedIn && (
                    <div className="my-credits">
                        我的积分: <span className="credit-value">{myCredits}</span>
                    </div>
                )}
            </div>

            <div className="board-description">
                <p>这里显示固定数字员工无法处理的任务，普通员工可以接取并完成以获得积分奖励。</p>
            </div>

            {tasks.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-icon">📋</div>
                    <p>暂无公共任务</p>
                    <span>当前没有待接取的任务，请稍后再来查看</span>
                </div>
            ) : (
                <div className="task-list">
                    {tasks.map(task => {
                        const difficulty = getDifficultyLabel(task.difficulty);
                        const priority = getPriorityLabel(task.priority);
                        const isClaiming = claimingTaskId === task.taskId;

                        return (
                            <div key={task.taskId} className="task-card">
                                <div className="task-header">
                                    <span
                                        className="difficulty-badge"
                                        style={{ backgroundColor: difficulty.color + '20', color: difficulty.color }}
                                    >
                                        {difficulty.text}
                                    </span>
                                    <span
                                        className="priority-badge"
                                        style={{ backgroundColor: priority.color + '20', color: priority.color }}
                                    >
                                        {priority.text}
                                    </span>
                                </div>

                                <div className="task-body">
                                    <h4 className="task-type">{task.taskType}</h4>
                                    <p className="task-description">{task.description}</p>

                                    <div className="task-meta">
                                        <div className="meta-item">
                                            <span className="meta-label">所需能力</span>
                                            <span className="meta-value">{task.requiredCapability}</span>
                                        </div>
                                        <div className="meta-item">
                                            <span className="meta-label">预计耗时</span>
                                            <span className="meta-value">{task.estimatedHours} 小时</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="task-footer">
                                    <div className="task-reward">
                                        <span className="reward-label">奖励</span>
                                        <span className="reward-value">{task.reward} 积分</span>
                                    </div>
                                    <button
                                        className="claim-btn"
                                        onClick={() => handleClaimTask(task)}
                                        disabled={!isLoggedIn || isClaiming}
                                    >
                                        {isClaiming ? '接取中...' : isLoggedIn ? '接取任务' : '请先登录'}
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
