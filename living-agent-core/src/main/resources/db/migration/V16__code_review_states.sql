-- V16: 代码审查状态持久化表
CREATE TABLE IF NOT EXISTS code_review_states (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(100) NOT NULL UNIQUE,
    project_id      VARCHAR(100),
    execution_id    VARCHAR(500),
    stage           VARCHAR(50) NOT NULL,
    review_round    INTEGER DEFAULT 0,
    developer_employee_code VARCHAR(100),
    reviewer_employee_code  VARCHAR(100),
    worktree_path   VARCHAR(500),
    diff_path       VARCHAR(500),
    review_report_path VARCHAR(500),
    final_summary_path  VARCHAR(500),
    review_findings_json JSONB,
    metadata_json   JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_review_states_stage ON code_review_states(stage);
CREATE INDEX IF NOT EXISTS idx_review_states_execution_id ON code_review_states(execution_id);
CREATE INDEX IF NOT EXISTS idx_review_states_developer ON code_review_states(developer_employee_code);
CREATE INDEX IF NOT EXISTS idx_review_states_reviewer ON code_review_states(reviewer_employee_code);
