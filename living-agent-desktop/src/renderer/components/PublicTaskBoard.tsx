/**
 * 桌面端独立公共任务栏组件
 *
 * 设计原则：
 * - 不依赖 frontend/web 端任何文件（不 import ../../frontend/...）
 * - 状态、API 调用、样式全部内联或本地 CSS
 * - 通过 window.livingAgentAPI（preload 暴露的 IPC）调用后端
 * - 与 web 端组件功能一致：列表、积分、接取；差异在于鉴权状态检测方式
 */
import { useEffect, useState } from 'react';
import type { PublicTask } from '@shared/types';
import './PublicTaskBoard.css';

interface PublicTaskBoardProps {
  /** 可选：按部门过滤 */
  department?: string;
}

interface CreditBalance {
  balance: number;
}

const DIFFICULTY_LABELS: Record<string, { text: string; color: string }> = {
  BEGINNER: { text: '入门', color: '#52c41a' },
  INTERMEDIATE: { text: '中级', color: '#1890ff' },
  ADVANCED: { text: '高级', color: '#fa8c16' },
  EXPERT: { text: '专家', color: '#f5222d' },
  MASTER: { text: '大师', color: '#722ed1' }
};

function getDifficultyLabel(difficulty: string) {
  return DIFFICULTY_LABELS[difficulty] || { text: difficulty, color: '#999' };
}

function getPriorityLabel(priority: number) {
  if (priority >= 5) return { text: '紧急', color: '#f5222d' };
  if (priority >= 3) return { text: '高', color: '#fa8c16' };
  if (priority >= 2) return { text: '中', color: '#1890ff' };
  return { text: '低', color: '#52c41a' };
}

export default function PublicTaskBoard({ department }: PublicTaskBoardProps) {
  const [tasks, setTasks] = useState<PublicTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [claimingTaskId, setClaimingTaskId] = useState<string | null>(null);
  const [myCredits, setMyCredits] = useState(0);
  const [hasToken, setHasToken] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    void loadPublicTasks();
    void loadMyCredits();
    void checkAuth();
  }, [department]);

  async function checkAuth() {
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      setHasToken(!!token);
    } catch {
      setHasToken(false);
    }
  }

  async function loadPublicTasks() {
    setLoading(true);
    setErrorMsg(null);
    try {
      const list = await window.livingAgentAPI.taskBoard.list(department);
      setTasks(Array.isArray(list) ? list : []);
    } catch (e) {
      console.error('[desktop] 加载公共任务失败:', e);
      setErrorMsg(String(e));
      setTasks([]);
    } finally {
      setLoading(false);
    }
  }

  async function loadMyCredits() {
    try {
      const balance: CreditBalance = await window.livingAgentAPI.credits.getBalance();
      setMyCredits(typeof balance?.balance === 'number' ? balance.balance : 0);
    } catch (e) {
      console.warn('[desktop] 加载积分失败（未登录？）:', e);
      setMyCredits(0);
    }
  }

  async function handleClaim(task: PublicTask) {
    if (!hasToken) {
      alert('请先在主窗口登录');
      return;
    }
    if (
      !confirm(
        `确定要接取任务 "${task.description}" 吗？\n预计耗时: ${task.estimatedHours}小时\n奖励: ${task.reward}积分`
      )
    ) {
      return;
    }

    setClaimingTaskId(task.taskId);
    try {
      await window.livingAgentAPI.taskBoard.claim(task.taskId);
      alert('任务接取成功！请前往"我的任务"查看');
      setTasks((prev) => prev.filter((t) => t.taskId !== task.taskId));
      // 接取成功后刷新积分
      void loadMyCredits();
    } catch (e) {
      console.error('[desktop] 接取任务失败:', e);
      alert('接取任务失败：' + String(e));
    } finally {
      setClaimingTaskId(null);
    }
  }

  if (loading) {
    return (
      <div className="public-task-board desktop-public-task-board">
        <div className="loading-state">加载任务列表...</div>
      </div>
    );
  }

  return (
    <div className="public-task-board desktop-public-task-board">
      <div className="board-header">
        <h3>公共任务栏</h3>
        {hasToken && (
          <div className="my-credits">
            我的积分: <span className="credit-value">{myCredits}</span>
          </div>
        )}
      </div>

      <div className="board-description">
        <p>这里显示固定数字员工无法处理的任务，普通员工可以接取并完成以获得积分奖励。</p>
      </div>

      {errorMsg && (
        <div className="error-banner" role="alert">
          加载任务失败：{errorMsg}
        </div>
      )}

      {tasks.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">📋</div>
          <p>暂无公共任务</p>
          <span>当前没有待接取的任务，请稍后再来查看</span>
        </div>
      ) : (
        <div className="task-list">
          {tasks.map((task) => {
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
                    onClick={() => handleClaim(task)}
                    disabled={!hasToken || isClaiming}
                  >
                    {isClaiming ? '接取中...' : hasToken ? '接取任务' : '请先登录'}
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
