-- V20: 修正 SessionContextEntity 关联字段长度不足
-- taskKey: VARCHAR(128) -> VARCHAR(500)  (task_key 格式: task://{tenant}/{user}/{type}/{timestamp}-{uuid} 可超过128字符)
-- executionId: VARCHAR(64) -> VARCHAR(500)  (execution_id 格式: exec://{taskKey}/{timestamp}-{uuid} 可超过64字符)
-- projectId: VARCHAR(64) -> VARCHAR(100)  (project_id 需要与其他表保持一致)
-- conversationId: VARCHAR(64) -> VARCHAR(100)  (conversation_id 需要与其他表保持一致)

ALTER TABLE session_contexts ALTER COLUMN task_key TYPE VARCHAR(500);
ALTER TABLE session_contexts ALTER COLUMN execution_id TYPE VARCHAR(500);
ALTER TABLE session_contexts ALTER COLUMN project_id TYPE VARCHAR(100);
ALTER TABLE session_contexts ALTER COLUMN conversation_id TYPE VARCHAR(100);
