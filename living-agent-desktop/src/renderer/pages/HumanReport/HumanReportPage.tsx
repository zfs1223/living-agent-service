/**
 * P15: 人类员工周期性汇报入口
 *
 * 人类员工填写周期性汇报（日报/周报/月报）
 * 后端 API: POST /api/human-employee/reports
 */
import { useState } from 'react';
import './HumanReportPage.css';

type ReportType = 'daily' | 'weekly' | 'monthly';

export default function HumanReportPage({ backendUrl, hasToken, currentUser }: {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
}) {
  const [reportType, setReportType] = useState<ReportType>('daily');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [history, setHistory] = useState<any[]>([]);

  const handleSubmit = async () => {
    if (!content.trim()) return;
    setSubmitting(true);
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const res = await fetch(`${backendUrl}/api/human-employee/reports`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reportType,
          content,
          employeeId: currentUser?.id,
        }),
      });
      if (res.ok) {
        setContent('');
        setSubmitted(true);
        setTimeout(() => setSubmitted(false), 3000);
      }
    } catch (e) {
      console.error('[HumanReport] 提交失败:', e);
    } finally {
      setSubmitting(false);
    }
  };

  if (!hasToken) {
    return <div className="human-report__empty">请先登录</div>;
  }

  return (
    <div className="human-report">
      <div className="human-report__header">
        <h1>📝 周期性汇报</h1>
        <div className="human-report__type-selector">
          <button
            className={`human-report__type ${reportType === 'daily' ? 'active' : ''}`}
            onClick={() => setReportType('daily')}
          >
            📅 日报
          </button>
          <button
            className={`human-report__type ${reportType === 'weekly' ? 'active' : ''}`}
            onClick={() => setReportType('weekly')}
          >
            📆 周报
          </button>
          <button
            className={`human-report__type ${reportType === 'monthly' ? 'active' : ''}`}
            onClick={() => setReportType('monthly')}
          >
            🗓️ 月报
          </button>
        </div>
      </div>

      {submitted && (
        <div className="human-report__success">
          ✅ 汇报已提交
        </div>
      )}

      <div className="human-report__editor">
        <textarea
          className="human-report__textarea"
          placeholder={getPlaceholder(reportType)}
          value={content}
          onChange={e => setContent(e.target.value)}
          rows={12}
        />
        <div className="human-report__actions">
          <span className="human-report__char-count">{content.length} 字</span>
          <button
            className="human-report__submit"
            disabled={!content.trim() || submitting}
            onClick={handleSubmit}
          >
            {submitting ? '提交中...' : '提交汇报'}
          </button>
        </div>
      </div>

      <div className="human-report__tips">
        <h3>💡 汇报模板</h3>
        <pre className="human-report__template">{getTemplate(reportType)}</pre>
      </div>
    </div>
  );
}

function getPlaceholder(type: ReportType): string {
  switch (type) {
    case 'daily':
      return '请填写今日工作内容、进展、问题及明日计划...';
    case 'weekly':
      return '请填写本周工作总结、下周计划、风险及需要协调的事项...';
    case 'monthly':
      return '请填写本月工作总结、下月计划、重点项目进展及资源需求...';
    default:
      return '';
  }
}

function getTemplate(type: ReportType): string {
  switch (type) {
    case 'daily':
      return `【今日完成】
1. ...
2. ...

【进行中任务】
- 任务A: 进度 80%
- 任务B: 进度 50%

【遇到问题】
- ...

【明日计划】
1. ...
2. ...`;

    case 'weekly':
      return `【本周工作总结】
1. 重点项目进展
2. 常规工作完成情况
3. 协作事项

【下周计划】
1. 优先级 P0 任务
2. 常规工作安排

【风险与问题】
- ...

【需要协调】
- ...`;

    case 'monthly':
      return `【本月工作总结】
一、重点项目进展
二、关键指标达成
三、团队协作情况

【下月工作计划】
一、重点目标
二、关键任务
三、里程碑节点

【资源需求】
- ...

【风险与建议】
- ...`;

    default:
      return '';
  }
}