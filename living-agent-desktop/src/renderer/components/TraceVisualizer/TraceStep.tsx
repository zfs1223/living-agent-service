/**
 * P8: Trace 单步卡片组件
 *
 * 显示单个 Trace 阶段的状态和信息
 */
import { useState } from 'react';
import './TraceVisualizer.css';

export type TraceStatus = 'pending' | 'running' | 'done' | 'failed';

export interface TraceStepData {
  stage: string;
  status: TraceStatus;
  timestamp?: string;
  message?: string;
  details?: Record<string, unknown>;
}

interface TraceStepProps {
  data: TraceStepData;
  index: number;
  isLast: boolean;
}

const STAGE_LABELS: Record<string, string> = {
  'intake_classified': '意图识别',
  'main_brain_planned': '主脑规划',
  'brain_routed': '路由分发',
  'department_plan_created': '部门计划',
  'employee_assigned': '员工分派',
  'employee_execution_started': '开始执行',
  'employee_execution_completed': '执行完成',
  'result_aggregated': '结果汇总'
};

const STATUS_ICONS: Record<TraceStatus, string> = {
  pending: '⏳',
  running: '🔄',
  done: '✅',
  failed: '❌'
};

const STATUS_COLORS: Record<TraceStatus, string> = {
  pending: '#8c8c8c',
  running: '#1890ff',
  done: '#52c41a',
  failed: '#ff4d4f'
};

export default function TraceStep({ data, index, isLast }: TraceStepProps) {
  const [expanded, setExpanded] = useState(false);

  const stageLabel = STAGE_LABELS[data.stage] || data.stage;
  const statusIcon = STATUS_ICONS[data.status];
  const statusColor = STATUS_COLORS[data.status];

  const hasDetails = data.details && Object.keys(data.details).length > 0;

  return (
    <div className={`trace-step trace-step--${data.status}`}>
      <div className="trace-step__header" onClick={() => hasDetails && setExpanded(!expanded)}>
        <div className="trace-step__index" style={{ background: statusColor }}>
          {index + 1}
        </div>
        <div className="trace-step__icon" style={{ color: statusColor }}>
          {statusIcon}
        </div>
        <div className="trace-step__content">
          <div className="trace-step__stage">{stageLabel}</div>
          {data.message && <div className="trace-step__message">{data.message}</div>}
        </div>
        {data.timestamp && (
          <div className="trace-step__time">
            {new Date(data.timestamp).toLocaleTimeString()}
          </div>
        )}
        {hasDetails && (
          <div className={`trace-step__expand ${expanded ? 'trace-step__expand--expanded' : ''}`}>
            ▼
          </div>
        )}
      </div>

      {expanded && hasDetails && (
        <div className="trace-step__details">
          {Object.entries(data.details || {}).map(([key, value]) => (
            <div key={key} className="trace-step__detail-row">
              <span className="trace-step__detail-key">{key}:</span>
              <span className="trace-step__detail-value">
                {typeof value === 'object' ? JSON.stringify(value) : String(value)}
              </span>
            </div>
          ))}
        </div>
      )}

      {!isLast && <div className="trace-step__connector" style={{ background: statusColor }} />}
    </div>
  );
}