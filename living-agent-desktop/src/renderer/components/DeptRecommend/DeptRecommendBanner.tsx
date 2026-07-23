/**
 * P11: 部门路由推荐组件
 *
 * 根据文件类型/内容关键词自动推荐最合适的部门
 */
import { useState, useEffect } from 'react';
import './DeptRecommend.css';

export interface DeptRecommendation {
  department: string;
  departmentName: string;
  confidence: number; // 0-100
  reason: string;
}

interface DeptRecommendBannerProps {
  recommendation: DeptRecommendation | null;
  onAccept?: () => void;
  onDismiss?: () => void;
  currentDept: string;
}

// 文件类型到部门的映射规则
export const FILE_TYPE_DEPT_MAP: Record<string, { dept: string; reason: string }[]> = {
  // 财务/运营：Excel/CSV
  'xlsx': [
    { dept: 'finance', reason: 'Excel 文件通常用于财务报表或数据分析' },
    { dept: 'ops', reason: '数据报表可能需要运营分析' }
  ],
  'csv': [
    { dept: 'ops', reason: 'CSV 数据文件适合运营分析' },
    { dept: 'finance', reason: '数据文件可能包含财务信息' }
  ],
  // 法务/HR：Word/PDF
  'pdf': [
    { dept: 'legal', reason: 'PDF 文档通常包含合同或法律文件' },
    { dept: 'hr', reason: 'PDF 可能是简历或人事文档' }
  ],
  'doc': [
    { dept: 'hr', reason: 'Word 文档可能是简历或人事材料' },
    { dept: 'legal', reason: 'Word 文档可能包含合同草案' }
  ],
  'docx': [
    { dept: 'hr', reason: 'Word 文档可能是简历或人事材料' },
    { dept: 'legal', reason: 'Word 文档可能包含合同草案' }
  ],
  // 客服/销售：图片（截图）
  'png': [
    { dept: 'cs', reason: '截图通常来自客户问题反馈' },
    { dept: 'sales', reason: '截图可能包含客户聊天或商机信息' }
  ],
  'jpg': [
    { dept: 'cs', reason: '截图通常来自客户问题反馈' },
    { dept: 'sales', reason: '截图可能包含客户聊天或商机信息' }
  ],
  'jpeg': [
    { dept: 'cs', reason: '截图通常来自客户问题反馈' },
    { dept: 'sales', reason: '截图可能包含客户聊天或商机信息' }
  ],
  // 技术：代码文件
  'js': [{ dept: 'tech', reason: 'JavaScript 代码文件' }],
  'ts': [{ dept: 'tech', reason: 'TypeScript 代码文件' }],
  'py': [{ dept: 'tech', reason: 'Python 代码文件' }],
  'java': [{ dept: 'tech', reason: 'Java 代码文件' }],
  'go': [{ dept: 'tech', reason: 'Go 代码文件' }],
  'rs': [{ dept: 'tech', reason: 'Rust 代码文件' }],
};

// 部门代码到名称的映射
const DEPT_NAMES: Record<string, string> = {
  tech: '技术部',
  hr: '人力资源',
  finance: '财务部',
  sales: '销售部',
  cs: '客服部',
  admin: '行政部',
  legal: '法务部',
  ops: '运营部',
  core: '核心部'
};

/**
 * 根据文件扩展名推荐部门
 */
export function recommendByFileType(fileName: string): DeptRecommendation | null {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  const rules = FILE_TYPE_DEPT_MAP[ext];

  if (!rules || rules.length === 0) return null;

  const topRule = rules[0];
  return {
    department: topRule.dept,
    departmentName: DEPT_NAMES[topRule.dept] || topRule.dept,
    confidence: rules.length === 1 ? 90 : 70,
    reason: topRule.reason
  };
}

/**
 * 根据内容关键词推荐部门
 */
export function recommendByKeywords(text: string): DeptRecommendation | null {
  const lowerText = text.toLowerCase();

  // 关键词匹配
  const keywordRules: { keywords: string[]; dept: string; reason: string }[] = [
    { keywords: ['合同', '协议', '条款', '违约'], dept: 'legal', reason: '检测到合同相关关键词' },
    { keywords: ['简历', '面试', '招聘', '薪酬'], dept: 'hr', reason: '检测到人事相关关键词' },
    { keywords: ['发票', '报销', '预算', '成本'], dept: 'finance', reason: '检测到财务相关关键词' },
    { keywords: ['客户', '报价', '商机', '线索'], dept: 'sales', reason: '检测到销售相关关键词' },
    { keywords: ['投诉', '退款', '售后', '工单'], dept: 'cs', reason: '检测到客服相关关键词' },
    { keywords: ['错误', 'bug', '崩溃', '日志', '代码'], dept: 'tech', reason: '检测到技术相关关键词' },
    { keywords: ['roi', '转化', '漏斗', '增长'], dept: 'ops', reason: '检测到运营相关关键词' },
  ];

  for (const rule of keywordRules) {
    if (rule.keywords.some(kw => lowerText.includes(kw))) {
      return {
        department: rule.dept,
        departmentName: DEPT_NAMES[rule.dept] || rule.dept,
        confidence: 80,
        reason: rule.reason
      };
    }
  }

  return null;
}

export default function DeptRecommendBanner({
  recommendation,
  onAccept,
  onDismiss,
  currentDept
}: DeptRecommendBannerProps) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (recommendation) {
      setVisible(true);
    }
  }, [recommendation]);

  if (!recommendation || !visible) return null;

  // 如果推荐的就是当前部门，不显示
  if (recommendation.department === currentDept) return null;

  return (
    <div className="dept-recommend">
      <div className="dept-recommend__content">
        <span className="dept-recommend__icon">🎯</span>
        <div className="dept-recommend__text">
          <span className="dept-recommend__label">推荐部门：</span>
          <span className="dept-recommend__dept">{recommendation.departmentName}</span>
          <span className="dept-recommend__reason">{recommendation.reason}</span>
        </div>
      </div>
      <div className="dept-recommend__actions">
        <button
          className="dept-recommend__btn dept-recommend__btn--accept"
          onClick={() => {
            onAccept?.();
            setVisible(false);
          }}
        >
          切换
        </button>
        <button
          className="dept-recommend__btn dept-recommend__btn--dismiss"
          onClick={() => {
            onDismiss?.();
            setVisible(false);
          }}
        >
          继续当前
        </button>
      </div>
    </div>
  );
}