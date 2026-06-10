-- V19: 补充缺失的 Entity 对应表
-- 补偿系统: compensation_plans, compensation_accounts, compensation_records
-- 绩效系统: performance_indicators, performance_assessments, performance_trend_snapshots
-- 进化系统: evolution_results, evolution_feedback, evolution_audit_logs

-- =============================================
-- 补偿计划表 (CompensationPlanEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS compensation_plans (
    id UUID NOT NULL PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL UNIQUE,
    department_id VARCHAR(64),
    employee_type VARCHAR(64),
    rules_json TEXT DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compensation_plan_id ON compensation_plans(plan_id);
CREATE INDEX IF NOT EXISTS idx_compensation_plan_department ON compensation_plans(department_id);
CREATE INDEX IF NOT EXISTS idx_compensation_plan_employee_type ON compensation_plans(employee_type);

CREATE TRIGGER update_compensation_plans_updated_at BEFORE UPDATE ON compensation_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 补偿账户表 (CompensationAccountEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS compensation_accounts (
    id UUID NOT NULL PRIMARY KEY,
    employee_id VARCHAR(64) NOT NULL UNIQUE,
    plan_id VARCHAR(64),
    balance INTEGER DEFAULT 0,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    last_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compensation_account_employee ON compensation_accounts(employee_id);
CREATE INDEX IF NOT EXISTS idx_compensation_account_plan ON compensation_accounts(plan_id);

CREATE TRIGGER update_compensation_accounts_updated_at BEFORE UPDATE ON compensation_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 补偿记录表 (CompensationRecordEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS compensation_records (
    id UUID NOT NULL PRIMARY KEY,
    record_id VARCHAR(64) NOT NULL UNIQUE,
    employee_id VARCHAR(64) NOT NULL,
    points INTEGER,
    type VARCHAR(32),
    reason TEXT,
    source_task_id VARCHAR(64),
    source_review_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compensation_record_employee ON compensation_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_compensation_record_type ON compensation_records(type);
CREATE INDEX IF NOT EXISTS idx_compensation_record_created_at ON compensation_records(created_at);

CREATE TRIGGER update_compensation_records_updated_at BEFORE UPDATE ON compensation_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 绩效指标表 (PerformanceIndicatorEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS performance_indicators (
    id UUID NOT NULL PRIMARY KEY,
    indicator_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    category VARCHAR(32),
    weight DOUBLE PRECISION DEFAULT 1.0,
    target_value DOUBLE PRECISION DEFAULT 0.0,
    calculation_method VARCHAR(128),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performance_indicator_id ON performance_indicators(indicator_id);
CREATE INDEX IF NOT EXISTS idx_performance_indicator_category ON performance_indicators(category);
CREATE INDEX IF NOT EXISTS idx_performance_indicator_enabled ON performance_indicators(enabled);

CREATE TRIGGER update_performance_indicators_updated_at BEFORE UPDATE ON performance_indicators
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 绩效评估表 (PerformanceAssessmentEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS performance_assessments (
    id UUID NOT NULL PRIMARY KEY,
    assessment_id VARCHAR(64) NOT NULL UNIQUE,
    employee_id VARCHAR(64) NOT NULL,
    employee_name VARCHAR(128),
    period_type VARCHAR(32),
    overall_score DOUBLE PRECISION,
    grade VARCHAR(16),
    dimension_scores_json TEXT DEFAULT '{}',
    comment TEXT,
    assessed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performance_assessment_id ON performance_assessments(assessment_id);
CREATE INDEX IF NOT EXISTS idx_performance_employee_id ON performance_assessments(employee_id);
CREATE INDEX IF NOT EXISTS idx_performance_period ON performance_assessments(period_type);
CREATE INDEX IF NOT EXISTS idx_performance_assessed_at ON performance_assessments(assessed_at);

CREATE TRIGGER update_performance_assessments_updated_at BEFORE UPDATE ON performance_assessments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 绩效趋势快照表 (PerformanceTrendSnapshotEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS performance_trend_snapshots (
    id UUID NOT NULL PRIMARY KEY,
    employee_id VARCHAR(64) NOT NULL,
    date DATE,
    score DOUBLE PRECISION,
    grade VARCHAR(16),
    period VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performance_trend_employee ON performance_trend_snapshots(employee_id);
CREATE INDEX IF NOT EXISTS idx_performance_trend_date ON performance_trend_snapshots(date);
CREATE INDEX IF NOT EXISTS idx_performance_trend_period ON performance_trend_snapshots(period);

CREATE TRIGGER update_performance_trend_snapshots_updated_at BEFORE UPDATE ON performance_trend_snapshots
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 进化结果表 (EvolutionResultEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS evolution_results (
    id UUID NOT NULL PRIMARY KEY,
    result_id VARCHAR(64) UNIQUE,
    signal_id VARCHAR(64),
    decision_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    strategy VARCHAR(128),
    action VARCHAR(128),
    generated_skill_id VARCHAR(128),
    immediate_effective BOOLEAN,
    error_message TEXT,
    execution_time_ms BIGINT,
    timestamp BIGINT,
    metadata_json TEXT DEFAULT '{}',
    brain_id VARCHAR(255),
    brain_type VARCHAR(50),
    department VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evolution_result_id ON evolution_results(result_id);
CREATE INDEX IF NOT EXISTS idx_evolution_signal_id ON evolution_results(signal_id);
CREATE INDEX IF NOT EXISTS idx_evolution_status ON evolution_results(status);
CREATE INDEX IF NOT EXISTS idx_evolution_created_at ON evolution_results(created_at);

CREATE TRIGGER update_evolution_results_updated_at BEFORE UPDATE ON evolution_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 进化反馈表 (EvolutionFeedbackEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS evolution_feedback (
    id UUID NOT NULL PRIMARY KEY,
    result_id VARCHAR(64) NOT NULL,
    feedback_type VARCHAR(64),
    score DOUBLE PRECISION,
    comment TEXT,
    source VARCHAR(64),
    metadata_json TEXT DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evolution_feedback_result_id ON evolution_feedback(result_id);
CREATE INDEX IF NOT EXISTS idx_evolution_feedback_type ON evolution_feedback(feedback_type);
CREATE INDEX IF NOT EXISTS idx_evolution_feedback_created_at ON evolution_feedback(created_at);

CREATE TRIGGER update_evolution_feedback_updated_at BEFORE UPDATE ON evolution_feedback
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================
-- 进化审计日志表 (EvolutionAuditLogEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS evolution_audit_logs (
    id UUID NOT NULL PRIMARY KEY,
    result_id VARCHAR(64),
    event_type VARCHAR(64),
    payload_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evolution_audit_result_id ON evolution_audit_logs(result_id);
CREATE INDEX IF NOT EXISTS idx_evolution_audit_event_type ON evolution_audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_evolution_audit_created_at ON evolution_audit_logs(created_at);

CREATE TRIGGER update_evolution_audit_logs_updated_at BEFORE UPDATE ON evolution_audit_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
