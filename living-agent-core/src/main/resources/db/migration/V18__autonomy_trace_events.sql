-- Autonomy Trace Events 持久化表
CREATE TABLE IF NOT EXISTS autonomy_trace_events (
    id UUID PRIMARY KEY,
    trace_id VARCHAR(64),
    request_id VARCHAR(100) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    actor VARCHAR(128),
    summary TEXT,
    data TEXT,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trace_request_id ON autonomy_trace_events (request_id);
CREATE INDEX IF NOT EXISTS idx_trace_stage ON autonomy_trace_events (stage);
CREATE INDEX IF NOT EXISTS idx_trace_actor ON autonomy_trace_events (actor);
CREATE INDEX IF NOT EXISTS idx_trace_timestamp ON autonomy_trace_events (timestamp);
