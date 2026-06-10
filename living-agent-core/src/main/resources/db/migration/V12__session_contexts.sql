-- 会话上下文持久化表
CREATE TABLE IF NOT EXISTS session_contexts (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    tenant_id VARCHAR(64),
    department_code VARCHAR(32),
    task_key VARCHAR(128),
    execution_id VARCHAR(64),
    project_id VARCHAR(64),
    project_key VARCHAR(128),
    conversation_id VARCHAR(64),
    connected_at TIMESTAMP WITH TIME ZONE,
    last_activity TIMESTAMP WITH TIME ZONE,
    attributes_json TEXT,
    
    CONSTRAINT chk_session_id CHECK (session_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_sess_user_id ON session_contexts(user_id);
CREATE INDEX IF NOT EXISTS idx_sess_tenant_id ON session_contexts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sess_conversation_id ON session_contexts(conversation_id);
CREATE INDEX IF NOT EXISTS idx_sess_last_activity ON session_contexts(last_activity);
