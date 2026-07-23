/**
 * P30: 部门特色快捷功能入口栏
 *
 * 根据用户部门显示不同的快捷功能入口
 * - tech: 代码审查、CI/CD 触发、错误诊断
 * - hr: 简历解析、面试安排、入职办理
 * - finance: 发票识别、报销审批、预算查询
 * - sales: 客户跟进、报价生成、商机管理
 * - cs: 工单处理、话术推荐、FAQ 搜索
 * - admin: 会议预订、资产盘点、采购申请
 * - legal: 合同审查、风险标注、条款比对
 * - ops: 报表生成、趋势分析、异常预警
 */
import { useMemo } from 'react';
import './DeptQuickActions.css';

/** 部门快捷功能配置 */
const DEPT_ACTIONS: Record<string, { label: string; actions: { key: string; icon: string; label: string; prompt?: string }[] }> = {
  tech: {
    label: '技术部',
    actions: [
      { key: 'code_review', icon: '🔍', label: '代码审查', prompt: '请审查这段代码：' },
      { key: 'ci_trigger', icon: '🚀', label: 'CI/CD 触发', prompt: '触发 CI 构建：' },
      { key: 'error_diag', icon: '🐛', label: '错误诊断', prompt: '诊断以下错误：' },
      { key: 'pr_create', icon: '📝', label: '创建 PR', prompt: '为以下变更创建 PR：' },
    ],
  },
  hr: {
    label: '人力资源',
    actions: [
      { key: 'resume_parse', icon: '📄', label: '简历解析', prompt: '解析这份简历：' },
      { key: 'interview_schedule', icon: '📅', label: '面试安排', prompt: '安排面试：' },
      { key: 'onboard', icon: '👋', label: '入职办理', prompt: '办理入职：' },
      { key: 'policy_query', icon: '📋', label: '政策查询', prompt: '查询人事政策：' },
    ],
  },
  finance: {
    label: '财务部',
    actions: [
      { key: 'invoice_scan', icon: '🧾', label: '发票识别', prompt: '识别这张发票：' },
      { key: 'reimburse', icon: '💳', label: '报销审批', prompt: '处理报销：' },
      { key: 'budget_query', icon: '💰', label: '预算查询', prompt: '查询预算：' },
      { key: 'report_gen', icon: '📊', label: '报表生成', prompt: '生成财务报表：' },
    ],
  },
  sales: {
    label: '销售部',
    actions: [
      { key: 'customer_follow', icon: '👤', label: '客户跟进', prompt: '跟进客户：' },
      { key: 'quote_gen', icon: '💲', label: '报价生成', prompt: '生成报价单：' },
      { key: 'opportunity', icon: '🎯', label: '商机管理', prompt: '管理商机：' },
      { key: 'contract', icon: '📝', label: '合同起草', prompt: '起草合同：' },
    ],
  },
  cs: {
    label: '客服部',
    actions: [
      { key: 'ticket', icon: '🎫', label: '工单处理', prompt: '处理工单：' },
      { key: 'faq', icon: '❓', label: 'FAQ 搜索', prompt: '搜索 FAQ：' },
      { key: 'script', icon: '💬', label: '话术推荐', prompt: '推荐回复话术：' },
      { key: 'complaint', icon: '⚠️', label: '投诉处理', prompt: '处理投诉：' },
    ],
  },
  admin: {
    label: '行政部',
    actions: [
      { key: 'meeting', icon: '📅', label: '会议预订', prompt: '预订会议室：' },
      { key: 'asset', icon: '🖥️', label: '资产盘点', prompt: '盘点资产：' },
      { key: 'purchase', icon: '🛒', label: '采购申请', prompt: '申请采购：' },
      { key: 'visitor', icon: '🚪', label: '访客登记', prompt: '登记访客：' },
    ],
  },
  legal: {
    label: '法务部',
    actions: [
      { key: 'contract_review', icon: '⚖️', label: '合同审查', prompt: '审查这份合同：' },
      { key: 'risk_mark', icon: '⚠️', label: '风险标注', prompt: '标注风险条款：' },
      { key: 'clause_compare', icon: '📋', label: '条款比对', prompt: '比对条款差异：' },
      { key: 'template', icon: '📝', label: '合同模板', prompt: '使用合同模板：' },
    ],
  },
  ops: {
    label: '运营部',
    actions: [
      { key: 'report', icon: '📊', label: '报表生成', prompt: '生成运营报表：' },
      { key: 'trend', icon: '📈', label: '趋势分析', prompt: '分析趋势：' },
      { key: 'anomaly', icon: '🔔', label: '异常预警', prompt: '设置预警：' },
      { key: 'campaign', icon: '🎯', label: '活动策划', prompt: '策划运营活动：' },
    ],
  },
};

interface DeptQuickActionsProps {
  department: string;
  onAction: (action: { key: string; prompt: string }) => void;
}

export default function DeptQuickActions({ department, onAction }: DeptQuickActionsProps) {
  const config = useMemo(() => {
    const dept = (department || 'tech').toLowerCase();
    return DEPT_ACTIONS[dept] || DEPT_ACTIONS['tech'];
  }, [department]);

  return (
    <div className="dept-quick-actions">
      <div className="dept-quick-actions__header">
        <span className="dept-quick-actions__label">{config.label}快捷功能</span>
      </div>
      <div className="dept-quick-actions__grid">
        {config.actions.map(action => (
          <button
            key={action.key}
            className="dept-quick-actions__btn"
            onClick={() => onAction({ key: action.key, prompt: action.prompt || action.label })}
          >
            <span className="dept-quick-actions__icon">{action.icon}</span>
            <span className="dept-quick-actions__text">{action.label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}