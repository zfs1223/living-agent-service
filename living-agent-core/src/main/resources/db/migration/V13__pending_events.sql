-- 待补发事件表
CREATE TABLE IF NOT EXISTS pending_events (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    session_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_pending_session ON pending_events(session_id);
CREATE INDEX IF NOT EXISTS idx_pending_timestamp ON pending_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_pending_sent ON pending_events(sent) WHERE sent = FALSE;
