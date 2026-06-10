-- Evolution System Enhancement Migration
-- Adds tables for brain model change history and evolution scheduler support

-- Brain Model Change History Table
CREATE TABLE IF NOT EXISTS brain_model_change_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brain_id VARCHAR(255) NOT NULL,
    brain_name VARCHAR(255) NOT NULL,
    brain_type VARCHAR(50) NOT NULL,
    model_id UUID NOT NULL,
    model_name VARCHAR(255),
    source VARCHAR(50) NOT NULL,
    changed_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT
);

CREATE INDEX idx_brain_history_brain_id ON brain_model_change_history(brain_id);
CREATE INDEX idx_brain_history_source ON brain_model_change_history(source);
CREATE INDEX idx_brain_history_created_at ON brain_model_change_history(created_at DESC);
CREATE INDEX idx_brain_history_brain_source ON brain_model_change_history(brain_id, source);
