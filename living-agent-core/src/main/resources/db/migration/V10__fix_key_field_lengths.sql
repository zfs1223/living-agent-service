-- V10: Fix execution_id and related field length constraints
-- execution_id format: exec://{taskKey}/{timestamp}-{uuid} can exceed 100 chars
-- task_key format: task://{tenant}/{user}/{type}/{timestamp}-{uuid} can exceed 200 chars

-- tasks table
ALTER TABLE tasks ALTER COLUMN execution_id TYPE VARCHAR(500);
ALTER TABLE tasks ALTER COLUMN task_key TYPE VARCHAR(500);

-- department_chat_messages table
ALTER TABLE department_chat_messages ALTER COLUMN execution_id TYPE VARCHAR(500);
ALTER TABLE department_chat_messages ALTER COLUMN task_key TYPE VARCHAR(500);

-- department_conversations table
ALTER TABLE department_conversations ALTER COLUMN active_execution_id TYPE VARCHAR(500);
ALTER TABLE department_conversations ALTER COLUMN active_task_key TYPE VARCHAR(500);
ALTER TABLE department_conversations ALTER COLUMN conversation_key TYPE VARCHAR(500);

-- artifact_records table
ALTER TABLE artifact_records ALTER COLUMN execution_id TYPE VARCHAR(500);

-- projects table (source_task_key references task_key)
ALTER TABLE projects ALTER COLUMN source_task_key TYPE VARCHAR(500);
