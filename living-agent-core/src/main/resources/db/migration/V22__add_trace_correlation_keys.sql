-- V22: P1-6.2 统一 Trace 关联键 - 添加 task_key 和 execution_id 字段
-- 使 AutonomyTraceService 和 RuntimeEventStore 可通过 taskKey/executionId 交叉查询

ALTER TABLE autonomy_trace_events ADD COLUMN IF NOT EXISTS task_key VARCHAR(100);
ALTER TABLE autonomy_trace_events ADD COLUMN IF NOT EXISTS execution_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_trace_task_key ON autonomy_trace_events (task_key);
CREATE INDEX IF NOT EXISTS idx_trace_execution_id ON autonomy_trace_events (execution_id);

-- 从已有 data JSONB 中提取 taskKey 和 executionId 回填（如果 data 中包含这些字段）
-- 注意：此回填为尽力而为，data 字段可能为空或不含这些键
UPDATE autonomy_trace_events
SET task_key = data::jsonb->>'taskKey'
WHERE task_key IS NULL
  AND data IS NOT NULL
  AND data::jsonb->>'taskKey' IS NOT NULL;

UPDATE autonomy_trace_events
SET execution_id = data::jsonb->>'executionId'
WHERE execution_id IS NULL
  AND data IS NOT NULL
  AND data::jsonb->>'executionId' IS NOT NULL;
