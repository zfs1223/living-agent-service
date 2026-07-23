/**
 * P8: Trace 可视化组件
 *
 * 显示主脑六步决策的 8 阶段 Trace 时间线
 */
import { useState, useEffect } from 'react';
import TraceStep, { type TraceStepData, type TraceStatus } from './TraceStep';
import './TraceVisualizer.css';

interface TraceVisualizerProps {
  traceId?: string;
  steps?: TraceStepData[];
  onClose?: () => void;
}

// 标准 8 阶段 Trace
const STANDARD_STAGES = [
  'intake_classified',
  'main_brain_planned',
  'brain_routed',
  'department_plan_created',
  'employee_assigned',
  'employee_execution_started',
  'employee_execution_completed',
  'result_aggregated'
];

export default function TraceVisualizer({ traceId, steps = [], onClose }: TraceVisualizerProps) {
  const [currentSteps, setCurrentSteps] = useState<TraceStepData[]>([]);

  // 初始化标准阶段（全部 pending）
  useEffect(() => {
    if (steps.length === 0) {
      // 没有传入 steps，显示标准空 Trace
      setCurrentSteps(
        STANDARD_STAGES.map(stage => ({
          stage,
          status: 'pending' as TraceStatus
        }))
      );
    } else {
      // 有传入 steps，合并标准阶段
      const stepMap = new Map(steps.map(s => [s.stage, s]));
      setCurrentSteps(
        STANDARD_STAGES.map(stage =>
          stepMap.get(stage) || { stage, status: 'pending' as TraceStatus }
        )
      );
    }
  }, [steps]);

  // 计算整体进度
  const completedCount = currentSteps.filter(s => s.status === 'done').length;
  const failedCount = currentSteps.filter(s => s.status === 'failed').length;
  const progress = Math.round((completedCount / currentSteps.length) * 100);

  return (
    <div className="trace-visualizer">
      <div className="trace-visualizer__header">
        <div className="trace-visualizer__title">
          <span className="trace-visualizer__icon">📊</span>
          <span>执行 Trace</span>
          {traceId && <span className="trace-visualizer__id">#{traceId.slice(0, 8)}</span>}
        </div>
        <div className="trace-visualizer__progress">
          <div className="trace-visualizer__progress-bar">
            <div
              className={`trace-visualizer__progress-fill ${failedCount > 0 ? 'trace-visualizer__progress-fill--failed' : ''}`}
              style={{ width: `${progress}%` }}
            />
          </div>
          <span className="trace-visualizer__progress-text">
            {completedCount}/{currentSteps.length}
          </span>
        </div>
        {onClose && (
          <button className="trace-visualizer__close" onClick={onClose} title="关闭">
            ✕
          </button>
        )}
      </div>

      <div className="trace-visualizer__content">
        {currentSteps.map((step, index) => (
          <TraceStep
            key={step.stage}
            data={step}
            index={index}
            isLast={index === currentSteps.length - 1}
          />
        ))}
      </div>

      {failedCount > 0 && (
        <div className="trace-visualizer__footer">
          <div className="trace-visualizer__error-hint">
            ⚠️ 有 {failedCount} 个阶段执行失败
          </div>
        </div>
      )}
    </div>
  );
}

// 导出类型供外部使用
export { type TraceStepData, type TraceStatus };