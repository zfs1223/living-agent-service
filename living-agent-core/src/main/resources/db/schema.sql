-- Living Agent Service Database Schema
-- PostgreSQL 15+

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================
-- Enterprise Tables
-- ============================================

-- Departments Table
CREATE TABLE IF NOT EXISTS enterprise_departments (
    department_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(32) UNIQUE,
    parent_id VARCHAR(64),
    manager_id VARCHAR(64),
    manager_name VARCHAR(64),
    target_brain VARCHAR(32),
    member_count INTEGER DEFAULT 0,
    description TEXT,
    sync_source VARCHAR(32),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_parent_department FOREIGN KEY (parent_id) 
        REFERENCES enterprise_departments(department_id) ON DELETE SET NULL
);

CREATE INDEX idx_departments_parent ON enterprise_departments(parent_id);
CREATE INDEX idx_departments_code ON enterprise_departments(code);
CREATE INDEX idx_departments_target_brain ON enterprise_departments(target_brain);
CREATE INDEX idx_departments_name_trgm ON enterprise_departments USING gin(name gin_trgm_ops);

-- Employees Table
CREATE TABLE IF NOT EXISTS enterprise_employees (
    employee_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(128),
    department_id VARCHAR(64),
    department_name VARCHAR(128),
    position VARCHAR(64),
    identity VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_ACTIVE',
    access_level VARCHAR(16) NOT NULL DEFAULT 'DEPARTMENT',
    is_founder BOOLEAN DEFAULT FALSE,
    voice_print_id VARCHAR(64),
    oauth_provider VARCHAR(32),
    oauth_user_id VARCHAR(128),
    avatar_url VARCHAR(512),
    join_date TIMESTAMP WITH TIME ZONE,
    leave_date TIMESTAMP WITH TIME ZONE,
    active BOOLEAN DEFAULT TRUE,
    sync_source VARCHAR(32),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) 
        REFERENCES enterprise_departments(department_id) ON DELETE SET NULL,
    CONSTRAINT uk_employee_phone UNIQUE (phone),
    CONSTRAINT uk_employee_email UNIQUE (email)
);

CREATE INDEX idx_employees_department ON enterprise_employees(department_id);
CREATE INDEX idx_employees_identity ON enterprise_employees(identity);
CREATE INDEX idx_employees_access_level ON enterprise_employees(access_level);
CREATE INDEX idx_employees_active ON enterprise_employees(active);
CREATE INDEX idx_employees_founder ON enterprise_employees(is_founder);
CREATE INDEX idx_employees_oauth ON enterprise_employees(oauth_provider, oauth_user_id);
CREATE INDEX idx_employees_name_trgm ON enterprise_employees USING gin(name gin_trgm_ops);

-- Department Brain Mapping Table
CREATE TABLE IF NOT EXISTS department_brain_mapping (
    id SERIAL PRIMARY KEY,
    department_id VARCHAR(64) NOT NULL,
    brain_name VARCHAR(32) NOT NULL,
    access_level VARCHAR(16) NOT NULL DEFAULT 'DEPARTMENT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_mapping_department FOREIGN KEY (department_id) 
        REFERENCES enterprise_departments(department_id) ON DELETE CASCADE,
    CONSTRAINT uk_department_brain UNIQUE (department_id, brain_name)
);

-- Employee Sync Log Table
CREATE TABLE IF NOT EXISTS employee_sync_log (
    id SERIAL PRIMARY KEY,
    sync_id VARCHAR(64) NOT NULL,
    sync_source VARCHAR(32) NOT NULL,
    sync_type VARCHAR(16) NOT NULL,
    total_count INTEGER DEFAULT 0,
    created_count INTEGER DEFAULT 0,
    updated_count INTEGER DEFAULT 0,
    deleted_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    errors TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_log_source ON employee_sync_log(sync_source);
CREATE INDEX idx_sync_log_started ON employee_sync_log(started_at);

-- Access Audit Log Table
CREATE TABLE IF NOT EXISTS access_audit_log (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(64),
    employee_name VARCHAR(64),
    resource VARCHAR(256) NOT NULL,
    action VARCHAR(32) NOT NULL,
    granted BOOLEAN NOT NULL,
    reason VARCHAR(256),
    session_id VARCHAR(64),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    request_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_employee ON access_audit_log(employee_id);
CREATE INDEX idx_audit_resource ON access_audit_log(resource);
CREATE INDEX idx_audit_granted ON access_audit_log(granted);
CREATE INDEX idx_audit_created ON access_audit_log(created_at);

-- ============================================
-- Knowledge Tables
-- ============================================

-- Knowledge Entries Table
CREATE TABLE IF NOT EXISTS knowledge_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_id VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    knowledge_type VARCHAR(32) NOT NULL DEFAULT 'FACT',
    importance VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    validity VARCHAR(16) NOT NULL DEFAULT 'PERMANENT',
    scope VARCHAR(16) NOT NULL DEFAULT 'SHARED',
    owner_id VARCHAR(64),
    department_id VARCHAR(64),
    brain_domain VARCHAR(32),
    tags TEXT[],
    source VARCHAR(64),
    confidence DECIMAL(3,2) DEFAULT 1.0,
    access_count INTEGER DEFAULT 0,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    vector_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

CREATE INDEX idx_knowledge_type ON knowledge_entries(knowledge_type);
CREATE INDEX idx_knowledge_importance ON knowledge_entries(importance);
CREATE INDEX idx_knowledge_scope ON knowledge_entries(scope);
CREATE INDEX idx_knowledge_owner ON knowledge_entries(owner_id);
CREATE INDEX idx_knowledge_department ON knowledge_entries(department_id);
CREATE INDEX idx_knowledge_brain ON knowledge_entries(brain_domain);
CREATE INDEX idx_knowledge_tags ON knowledge_entries USING gin(tags);
CREATE INDEX idx_knowledge_expires ON knowledge_entries(expires_at) WHERE expires_at IS NOT NULL;

-- Knowledge Evolution Log Table
CREATE TABLE IF NOT EXISTS knowledge_evolution_log (
    id BIGSERIAL PRIMARY KEY,
    evolution_id VARCHAR(64) NOT NULL,
    entry_id VARCHAR(64) NOT NULL,
    evolution_type VARCHAR(32) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    reason TEXT,
    triggered_by VARCHAR(64),
    confidence_before DECIMAL(3,2),
    confidence_after DECIMAL(3,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evolution_entry ON knowledge_evolution_log(entry_id);
CREATE INDEX idx_evolution_type ON knowledge_evolution_log(evolution_type);

-- ============================================
-- Proactive Task Tables
-- ============================================

-- Proactive Task Config Table
CREATE TABLE IF NOT EXISTS proactive_task_config (
    id SERIAL PRIMARY KEY,
    task_id VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    task_type VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    cron_expression VARCHAR(64),
    event_type VARCHAR(64),
    condition_expr TEXT,
    brain_domain VARCHAR(32),
    parameters JSONB,
    target_users TEXT[],
    notify_channels TEXT[],
    priority INTEGER DEFAULT 5,
    enabled BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE,
    run_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_type ON proactive_task_config(task_type);
CREATE INDEX idx_task_trigger ON proactive_task_config(trigger_type);
CREATE INDEX idx_task_enabled ON proactive_task_config(enabled);
CREATE INDEX idx_task_next_run ON proactive_task_config(next_run_at) WHERE enabled = TRUE;

-- Proactive Execution Log Table
CREATE TABLE IF NOT EXISTS proactive_execution_log (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    result JSONB,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_execution_task ON proactive_execution_log(task_id);
CREATE INDEX idx_execution_status ON proactive_execution_log(status);
CREATE INDEX idx_execution_started ON proactive_execution_log(started_at);

-- ============================================
-- User Behavior Pattern Table
-- ============================================

CREATE TABLE IF NOT EXISTS user_behavior_pattern (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    pattern_type VARCHAR(32) NOT NULL,
    pattern_data JSONB NOT NULL,
    confidence DECIMAL(3,2) DEFAULT 0.0,
    sample_count INTEGER DEFAULT 0,
    last_matched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_user_pattern UNIQUE (user_id, pattern_type)
);

CREATE INDEX idx_behavior_user ON user_behavior_pattern(user_id);
CREATE INDEX idx_behavior_type ON user_behavior_pattern(pattern_type);

-- ============================================
-- Risk Prediction Log Table
-- ============================================

CREATE TABLE IF NOT EXISTS risk_prediction_log (
    id BIGSERIAL PRIMARY KEY,
    prediction_id VARCHAR(64) NOT NULL,
    indicator_id VARCHAR(64) NOT NULL,
    indicator_name VARCHAR(128),
    current_value DECIMAL(10,4),
    risk_level VARCHAR(16) NOT NULL,
    probability DECIMAL(3,2),
    recommendation TEXT,
    context JSONB,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(64),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_risk_indicator ON risk_prediction_log(indicator_id);
CREATE INDEX idx_risk_level ON risk_prediction_log(risk_level);
CREATE INDEX idx_risk_acknowledged ON risk_prediction_log(acknowledged);
CREATE INDEX idx_risk_created ON risk_prediction_log(created_at);

-- ============================================
-- Notification Queue Table
-- ============================================

CREATE TABLE IF NOT EXISTS notification_queue (
    id BIGSERIAL PRIMARY KEY,
    notification_id VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    priority INTEGER DEFAULT 5,
    target_users TEXT[],
    channels TEXT[],
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_status ON notification_queue(status);
CREATE INDEX idx_notification_scheduled ON notification_queue(scheduled_at) WHERE status = 'PENDING';
CREATE INDEX idx_notification_type ON notification_queue(notification_type);

-- ============================================
-- MainBrain Growth Tables
-- ============================================

-- MainBrain Growth Records Table
CREATE TABLE IF NOT EXISTS mainbrain_growth_records (
    id BIGSERIAL PRIMARY KEY,
    record_id VARCHAR(64) UNIQUE NOT NULL,
    growth_type VARCHAR(32) NOT NULL,
    category VARCHAR(32),
    description TEXT NOT NULL,
    metrics JSONB,
    previous_value DECIMAL(10,4),
    current_value DECIMAL(10,4),
    improvement_rate DECIMAL(5,4),
    source VARCHAR(64),
    related_task_id VARCHAR(64),
    related_employee_id VARCHAR(64),
    confidence DECIMAL(3,2) DEFAULT 1.0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_growth_type ON mainbrain_growth_records(growth_type);
CREATE INDEX idx_growth_category ON mainbrain_growth_records(category);
CREATE INDEX idx_growth_created ON mainbrain_growth_records(created_at);

-- MainBrain Personality Evolution Table
CREATE TABLE IF NOT EXISTS mainbrain_personality_evolution (
    id BIGSERIAL PRIMARY KEY,
    evolution_id VARCHAR(64) UNIQUE NOT NULL,
    personality_dimension VARCHAR(32) NOT NULL,
    previous_value DECIMAL(3,2) NOT NULL,
    new_value DECIMAL(3,2) NOT NULL,
    change_reason TEXT,
    trigger_event VARCHAR(64),
    trigger_data JSONB,
    effectiveness_score DECIMAL(3,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_personality_dimension ON mainbrain_personality_evolution(personality_dimension);
CREATE INDEX idx_personality_created ON mainbrain_personality_evolution(created_at);

-- Cross Department Coordination Cases Table
CREATE TABLE IF NOT EXISTS cross_department_cases (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    departments TEXT[] NOT NULL,
    brains_involved TEXT[] NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    priority INTEGER DEFAULT 5,
    complexity INTEGER DEFAULT 5,
    initiator_id VARCHAR(64),
    initiator_name VARCHAR(64),
    assigned_neurons TEXT[],
    workflow_steps JSONB,
    current_step INTEGER DEFAULT 0,
    result_summary TEXT,
    lessons_learned TEXT,
    success BOOLEAN,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cross_case_status ON cross_department_cases(status);
CREATE INDEX idx_cross_case_departments ON cross_department_cases USING gin(departments);
CREATE INDEX idx_cross_case_brains ON cross_department_cases USING gin(brains_involved);
CREATE INDEX idx_cross_case_success ON cross_department_cases(success);
CREATE INDEX idx_cross_case_created ON cross_department_cases(created_at);

-- MainBrain Capability Registry Table
CREATE TABLE IF NOT EXISTS mainbrain_capabilities (
    id SERIAL PRIMARY KEY,
    capability_id VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    description TEXT,
    level INTEGER DEFAULT 1,
    experience_points INTEGER DEFAULT 0,
    usage_count INTEGER DEFAULT 0,
    success_rate DECIMAL(3,2) DEFAULT 0.0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    related_skills TEXT[],
    related_tools TEXT[],
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_capability_category ON mainbrain_capabilities(category);
CREATE INDEX idx_capability_level ON mainbrain_capabilities(level);

-- ============================================
-- Functions and Triggers
-- ============================================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to tables
CREATE TRIGGER update_departments_updated_at BEFORE UPDATE ON enterprise_departments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON enterprise_employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_updated_at BEFORE UPDATE ON knowledge_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_task_config_updated_at BEFORE UPDATE ON proactive_task_config
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_behavior_pattern_updated_at BEFORE UPDATE ON user_behavior_pattern
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_updated_at BEFORE UPDATE ON notification_queue
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cross_case_updated_at BEFORE UPDATE ON cross_department_cases
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_capability_updated_at BEFORE UPDATE ON mainbrain_capabilities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Initial Data
-- ============================================

-- Insert default departments
INSERT INTO enterprise_departments (department_id, name, code, target_brain, description)
VALUES 
    ('dept_tech', '技术部', 'TECH', 'TechBrain', '负责产品研发和技术支持'),
    ('dept_hr', '人力资源部', 'HR', 'HrBrain', '负责招聘、培训、绩效管理'),
    ('dept_finance', '财务部', 'FIN', 'FinanceBrain', '负责财务管理和报销审批'),
    ('dept_admin', '行政部', 'ADMIN', 'AdminBrain', '负责行政事务和文档管理'),
    ('dept_sales', '销售部', 'SALES', 'SalesBrain', '负责销售和市场拓展'),
    ('dept_cs', '客服部', 'CS', 'CsBrain', '负责客户服务和工单处理'),
    ('dept_legal', '法务部', 'LEGAL', 'LegalBrain', '负责合同审查和合规管理'),
    ('dept_ops', '运营部', 'OPS', 'OpsBrain', '负责运营策略和数据分析')
ON CONFLICT (department_id) DO NOTHING;

-- Insert default proactive tasks
INSERT INTO proactive_task_config (task_id, name, task_type, trigger_type, cron_expression, brain_domain, enabled)
VALUES 
    ('task_weekly_report', '周报自动生成', 'REPORT', 'SCHEDULED', '0 17 ? * FRI', 'TechBrain', TRUE),
    ('task_contract_reminder', '合同到期提醒', 'REMINDER', 'SCHEDULED', '0 9 * * ?', 'LegalBrain', TRUE),
    ('task_system_health', '系统健康检查', 'CHECK', 'SCHEDULED', '0 */5 * * *', 'TechBrain', TRUE)
ON CONFLICT (task_id) DO NOTHING;

-- ============================================
-- Performance Assessment Tables
-- ============================================

-- Performance Assessments Table
CREATE TABLE IF NOT EXISTS performance_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id VARCHAR(64) UNIQUE NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    employee_name VARCHAR(64),
    department_id VARCHAR(64),
    assessment_period VARCHAR(16) NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL,
    grade VARCHAR(2) NOT NULL,
    dimension_scores JSONB,
    indicators JSONB,
    comment TEXT,
    assessed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assessment_employee ON performance_assessments(employee_id);
CREATE INDEX idx_assessment_period ON performance_assessments(assessment_period);
CREATE INDEX idx_assessment_date ON performance_assessments(period_start_date, period_end_date);
CREATE INDEX idx_assessment_score ON performance_assessments(overall_score DESC);

-- Company Indicators Table
CREATE TABLE IF NOT EXISTS company_indicators (
    id SERIAL PRIMARY KEY,
    indicator_id VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    category VARCHAR(32) NOT NULL,
    weight DECIMAL(5,4) DEFAULT 1.0,
    target_value DECIMAL(10,4),
    actual_value DECIMAL(10,4),
    unit VARCHAR(32),
    calculation_method VARCHAR(64),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_indicator_category ON company_indicators(category);
CREATE INDEX idx_indicator_active ON company_indicators(is_active);

-- Department Performance Table
CREATE TABLE IF NOT EXISTS department_performances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id VARCHAR(64) NOT NULL,
    department_name VARCHAR(128),
    assessment_period VARCHAR(16) NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    efficiency_score DECIMAL(5,2),
    success_rate DECIMAL(5,2),
    task_completion_rate DECIMAL(5,2),
    average_response_time_ms BIGINT,
    employee_count INTEGER,
    active_employee_count INTEGER,
    total_tasks_completed BIGINT,
    total_tasks_in_progress INTEGER,
    rank_in_company INTEGER,
    change_from_previous DECIMAL(5,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dept_perf_department ON department_performances(department_id);
CREATE INDEX idx_dept_perf_period ON department_performances(assessment_period);
CREATE INDEX idx_dept_perf_date ON department_performances(period_start_date, period_end_date);

-- CEO Alerts Table
CREATE TABLE IF NOT EXISTS ceo_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(64) UNIQUE NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    department_id VARCHAR(64),
    department_name VARCHAR(128),
    employee_id VARCHAR(64),
    suggested_action TEXT,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(64),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    triggered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ceo_alert_type ON ceo_alerts(alert_type);
CREATE INDEX idx_ceo_alert_severity ON ceo_alerts(severity);
CREATE INDEX idx_ceo_alert_acknowledged ON ceo_alerts(acknowledged);
CREATE INDEX idx_ceo_alert_triggered ON ceo_alerts(triggered_at);

-- CEO Recommendations Table
CREATE TABLE IF NOT EXISTS ceo_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id VARCHAR(64) UNIQUE NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    impact TEXT,
    priority VARCHAR(16) NOT NULL,
    action_items JSONB,
    related_alert_id VARCHAR(64),
    status VARCHAR(16) DEFAULT 'PENDING',
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ceo_rec_category ON ceo_recommendations(category);
CREATE INDEX idx_ceo_rec_priority ON ceo_recommendations(priority);
CREATE INDEX idx_ceo_rec_status ON ceo_recommendations(status);

-- Employee Templates Table
CREATE TABLE IF NOT EXISTS employee_templates (
    id SERIAL PRIMARY KEY,
    template_id VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    department VARCHAR(64),
    role VARCHAR(64),
    capabilities JSONB,
    skills JSONB,
    tools JSONB,
    personality JSONB,
    learning_config JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_template_department ON employee_templates(department);
CREATE INDEX idx_template_role ON employee_templates(role);
CREATE INDEX idx_template_active ON employee_templates(is_active);

-- ============================================
-- Credit and Incentive System Tables
-- ============================================

-- Credit Accounts Table
CREATE TABLE IF NOT EXISTS credit_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,
    balance_cents BIGINT DEFAULT 0,
    total_earned_cents BIGINT DEFAULT 0,
    total_exchanged_cents BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_balance ON credit_accounts(balance_cents);
CREATE INDEX idx_credit_employee ON credit_accounts(employee_id);

-- Credit Transactions Table
CREATE TABLE IF NOT EXISTS credit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(64) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    related_task_id VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_trans_employee ON credit_transactions(employee_id);
CREATE INDEX idx_credit_trans_type ON credit_transactions(transaction_type);
CREATE INDEX idx_credit_trans_created ON credit_transactions(created_at);

-- Income Records Table
CREATE TABLE IF NOT EXISTS income_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    income_id VARCHAR(64) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(200),
    amount_cents BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    received_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_income_employee ON income_records(employee_id);
CREATE INDEX idx_income_status ON income_records(status, created_at);
CREATE INDEX idx_income_source ON income_records(source_type);

-- ============================================
-- Evolution and Hardware Upgrade Tables
-- ============================================

-- Evolution Tier History Table
CREATE TABLE IF NOT EXISTS evolution_tier_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    history_id VARCHAR(64) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    from_tier VARCHAR(20) NOT NULL,
    to_tier VARCHAR(20) NOT NULL,
    reason TEXT,
    triggered_by VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evolution_tier_employee ON evolution_tier_history(employee_id);
CREATE INDEX idx_evolution_tier_created ON evolution_tier_history(created_at);

-- Hardware Upgrades Table
CREATE TABLE IF NOT EXISTS hardware_upgrades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    upgrade_id VARCHAR(64) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    upgrade_type VARCHAR(30) NOT NULL,
    hardware_name VARCHAR(100) NOT NULL,
    cost_cents BIGINT NOT NULL,
    benefit TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_hardware_upg_employee ON hardware_upgrades(employee_id);
CREATE INDEX idx_hardware_upg_status ON hardware_upgrades(status);

-- ============================================
-- Employee Extended Tables
-- ============================================

-- Employee Personalities Table
CREATE TABLE IF NOT EXISTS employee_personalities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,
    rigor DECIMAL(3,2) DEFAULT 0.5,
    creativity DECIMAL(3,2) DEFAULT 0.5,
    risk_tolerance DECIMAL(3,2) DEFAULT 0.5,
    obedience DECIMAL(3,2) DEFAULT 0.5,
    source VARCHAR(20) DEFAULT 'TEMPLATE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_personality_employee ON employee_personalities(employee_id);

-- Digital Employee Records Table
CREATE TABLE IF NOT EXISTS digital_employee_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id VARCHAR(100) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    neuron_id VARCHAR(255),
    task_type VARCHAR(50) NOT NULL,
    task_description TEXT,
    input_data JSONB,
    output_data JSONB,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms INTEGER,
    quality_score DECIMAL(3,2),
    feedback TEXT,
    related_channel VARCHAR(255),
    related_skill VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_digital_rec_employee ON digital_employee_records(employee_id);
CREATE INDEX idx_digital_rec_type ON digital_employee_records(task_type);
CREATE INDEX idx_digital_rec_status ON digital_employee_records(status);
CREATE INDEX idx_digital_rec_created ON digital_employee_records(created_at);

-- Employee Lifecycle Events Table
CREATE TABLE IF NOT EXISTS employee_lifecycle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    employee_id VARCHAR(255) NOT NULL,
    employee_type VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_source VARCHAR(50),
    description TEXT,
    context JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lifecycle_employee ON employee_lifecycle_events(employee_id);
CREATE INDEX idx_lifecycle_type ON employee_lifecycle_events(event_type);
CREATE INDEX idx_lifecycle_occurred ON employee_lifecycle_events(occurred_at);

-- Employee Capabilities Table
CREATE TABLE IF NOT EXISTS employee_capabilities (
    id SERIAL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    capability_name VARCHAR(128) NOT NULL,
    proficiency_level INTEGER DEFAULT 1,
    experience_points INTEGER DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_employee_capability UNIQUE (employee_id, capability_name)
);

CREATE INDEX idx_cap_employee ON employee_capabilities(employee_id);
CREATE INDEX idx_cap_name ON employee_capabilities(capability_name);

-- Employee Skills Table
CREATE TABLE IF NOT EXISTS employee_skills (
    id SERIAL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    skill_level INTEGER DEFAULT 1,
    usage_count INTEGER DEFAULT 0,
    success_rate DECIMAL(3,2) DEFAULT 0.0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_employee_skill UNIQUE (employee_id, skill_name)
);

CREATE INDEX idx_skill_employee ON employee_skills(employee_id);
CREATE INDEX idx_skill_name ON employee_skills(skill_name);

-- Employee Tools Table
CREATE TABLE IF NOT EXISTS employee_tools (
    id SERIAL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    access_level VARCHAR(16) DEFAULT 'USE',
    usage_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_employee_tool UNIQUE (employee_id, tool_name)
);

CREATE INDEX idx_tool_employee ON employee_tools(employee_id);
CREATE INDEX idx_tool_name ON employee_tools(tool_name);

-- ============================================
-- Additional Triggers
-- ============================================

CREATE TRIGGER update_credit_accounts_updated_at BEFORE UPDATE ON credit_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employee_personalities_updated_at BEFORE UPDATE ON employee_personalities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employee_capabilities_updated_at BEFORE UPDATE ON employee_capabilities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employee_skills_updated_at BEFORE UPDATE ON employee_skills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Initial Performance Indicators
-- ============================================

INSERT INTO company_indicators (indicator_id, name, description, category, weight, target_value, calculation_method)
VALUES 
    ('ind_task_completion', '任务完成率', '已完成任务占总任务的比例', 'TASK_COMPLETION', 0.25, 95.0, 'completed_tasks / total_tasks * 100'),
    ('ind_success_rate', '成功率', '成功完成任务占已完成任务的比例', 'QUALITY', 0.20, 98.0, 'success_tasks / completed_tasks * 100'),
    ('ind_response_time', '平均响应时间', '任务平均响应时间(毫秒)', 'EFFICIENCY', 0.15, 5000.0, 'avg(response_time_ms)'),
    ('ind_collaboration', '协作指数', '跨部门协作任务参与率', 'COLLABORATION', 0.10, 80.0, 'collab_tasks / total_tasks * 100'),
    ('ind_innovation', '创新指数', '创新建议采纳率', 'INNOVATION', 0.10, 50.0, 'adopted_suggestions / total_suggestions * 100'),
    ('ind_learning', '学习指数', '新技能掌握率', 'LEARNING', 0.10, 70.0, 'new_skills_learned / target_skills * 100'),
    ('ind_communication', '沟通指数', '沟通任务满意度', 'COMMUNICATION', 0.05, 90.0, 'avg(satisfaction_score)'),
    ('ind_reliability', '可靠性指数', '按时完成任务比例', 'RELIABILITY', 0.05, 95.0, 'on_time_tasks / total_tasks * 100')
ON CONFLICT (indicator_id) DO NOTHING;

-- ============================================
-- Speaker Profile Tables (Voice Print)
-- ============================================

-- Speaker Profiles Table
CREATE TABLE IF NOT EXISTS speaker_profiles (
    speaker_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100),
    embedding BYTEA,
    embedding_dimension INTEGER DEFAULT 192,
    employee_id VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    match_count INTEGER DEFAULT 0,
    last_matched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    metadata VARCHAR(2000)
);

CREATE INDEX idx_speaker_name ON speaker_profiles(name);
CREATE INDEX idx_speaker_employee ON speaker_profiles(employee_id);
CREATE INDEX idx_speaker_active ON speaker_profiles(active);

-- Voice Print Registration Log Table
CREATE TABLE IF NOT EXISTS voiceprint_registration_log (
    id BIGSERIAL PRIMARY KEY,
    registration_id VARCHAR(64) UNIQUE NOT NULL,
    speaker_id VARCHAR(100) NOT NULL,
    employee_id VARCHAR(100),
    name VARCHAR(100),
    audio_duration_ms INTEGER,
    embedding_dimension INTEGER,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voiceprint_reg_speaker ON voiceprint_registration_log(speaker_id);
CREATE INDEX idx_voiceprint_reg_employee ON voiceprint_registration_log(employee_id);
CREATE INDEX idx_voiceprint_reg_created ON voiceprint_registration_log(created_at);

-- Voice Print Verification Log Table
CREATE TABLE IF NOT EXISTS voiceprint_verification_log (
    id BIGSERIAL PRIMARY KEY,
    verification_id VARCHAR(64) UNIQUE NOT NULL,
    speaker_id VARCHAR(100),
    employee_id VARCHAR(100),
    verified BOOLEAN NOT NULL,
    similarity DECIMAL(5,4),
    threshold DECIMAL(5,4),
    audio_duration_ms INTEGER,
    challenge_text VARCHAR(256),
    asr_matched BOOLEAN,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voiceprint_verif_speaker ON voiceprint_verification_log(speaker_id);
CREATE INDEX idx_voiceprint_verif_employee ON voiceprint_verification_log(employee_id);
CREATE INDEX idx_voiceprint_verif_created ON voiceprint_verification_log(created_at);
CREATE INDEX idx_voiceprint_verif_verified ON voiceprint_verification_log(verified);

-- Trigger for speaker_profiles
CREATE TRIGGER update_speaker_profiles_updated_at BEFORE UPDATE ON speaker_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- User Profile Tables
-- ============================================

-- User Profiles Table (关联表，扩展信息)
-- 基础员工信息在 enterprise_employees 表中
CREATE TABLE IF NOT EXISTS user_profiles (
    profile_id VARCHAR(64) PRIMARY KEY,
    employee_id VARCHAR(100) UNIQUE,
    speaker_id VARCHAR(100) UNIQUE,
    digital_id VARCHAR(200) UNIQUE,
    personality_config JSONB,
    behavior_preferences JSONB,
    knowledge_association JSONB,
    usage_statistics JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_profile_employee ON user_profiles(employee_id);
CREATE INDEX idx_user_profile_speaker ON user_profiles(speaker_id);
CREATE INDEX idx_user_profile_digital ON user_profiles(digital_id);

-- Trigger for user_profiles
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Payout Account Tables
-- ============================================

-- Payout Accounts Table (Chairman Configurable)
CREATE TABLE IF NOT EXISTS payout_accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    account_name VARCHAR(100),
    account_type VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    account_identifier VARCHAR(256) NOT NULL,
    owner_id VARCHAR(100),
    owner_type VARCHAR(32) DEFAULT 'CHAIRMAN',
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payout_owner ON payout_accounts(owner_id);
CREATE INDEX idx_payout_type ON payout_accounts(account_type);
CREATE INDEX idx_payout_provider ON payout_accounts(provider);
CREATE INDEX idx_payout_active ON payout_accounts(is_active);
CREATE INDEX idx_payout_default ON payout_accounts(is_default);

-- Trigger for payout_accounts
CREATE TRIGGER update_payout_accounts_updated_at BEFORE UPDATE ON payout_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Heartbeat Tables
-- ============================================

-- Heartbeat Runs Table
CREATE TABLE IF NOT EXISTS heartbeat_runs (
    run_id VARCHAR(64) PRIMARY KEY,
    employee_id VARCHAR(100) NOT NULL,
    wake_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(16) DEFAULT 'NORMAL',
    context TEXT,
    max_duration_seconds INTEGER,
    allowed_actions TEXT[],
    require_success BOOLEAN DEFAULT FALSE,
    actions_taken TEXT[],
    actual_duration_seconds INTEGER,
    result_message TEXT,
    error_message TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_heartbeat_employee ON heartbeat_runs(employee_id);
CREATE INDEX idx_heartbeat_status ON heartbeat_runs(status);
CREATE INDEX idx_heartbeat_source ON heartbeat_runs(wake_source);
CREATE INDEX idx_heartbeat_scheduled ON heartbeat_runs(scheduled_at);
CREATE INDEX idx_heartbeat_created ON heartbeat_runs(created_at);

-- ============================================
-- Session Management Tables
-- ============================================

-- User Sessions Table
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    employee_id VARCHAR(100),
    speaker_id VARCHAR(100),
    identity VARCHAR(32) NOT NULL,
    access_level VARCHAR(16) NOT NULL,
    auth_method VARCHAR(32),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    device_info JSONB,
    location_info JSONB,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    end_reason VARCHAR(32),
    active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_session_employee ON user_sessions(employee_id);
CREATE INDEX idx_session_speaker ON user_sessions(speaker_id);
CREATE INDEX idx_session_active ON user_sessions(active);
CREATE INDEX idx_session_expires ON user_sessions(expires_at);
CREATE INDEX idx_session_started ON user_sessions(started_at);

-- ============================================
-- Configuration Version Control Tables
-- ============================================

-- Config Versions Table
CREATE TABLE IF NOT EXISTS config_versions (
    version_id VARCHAR(64) PRIMARY KEY,
    config_type VARCHAR(32) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    version_number INTEGER NOT NULL,
    config_value JSONB NOT NULL,
    previous_value JSONB,
    change_reason TEXT,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    CONSTRAINT uk_config_version UNIQUE (config_type, config_key, version_number)
);

CREATE INDEX idx_config_type ON config_versions(config_type);
CREATE INDEX idx_config_key ON config_versions(config_key);
CREATE INDEX idx_config_active ON config_versions(is_active);
CREATE INDEX idx_config_changed ON config_versions(changed_at);

-- ============================================
-- Budget Control Tables
-- ============================================

-- Budget Allocations Table
CREATE TABLE IF NOT EXISTS budget_allocations (
    allocation_id VARCHAR(64) PRIMARY KEY,
    budget_type VARCHAR(32) NOT NULL,
    owner_id VARCHAR(100),
    owner_type VARCHAR(32) DEFAULT 'DEPARTMENT',
    period VARCHAR(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    allocated_amount_cents BIGINT NOT NULL,
    used_amount_cents BIGINT DEFAULT 0,
    reserved_amount_cents BIGINT DEFAULT 0,
    alert_threshold DECIMAL(5,4) DEFAULT 0.8,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_budget_allocation UNIQUE (budget_type, owner_id, period, period_start)
);

CREATE INDEX idx_budget_owner ON budget_allocations(owner_id);
CREATE INDEX idx_budget_type ON budget_allocations(budget_type);
CREATE INDEX idx_budget_period ON budget_allocations(period_start, period_end);
CREATE INDEX idx_budget_active ON budget_allocations(is_active);

-- Budget Transactions Table
CREATE TABLE IF NOT EXISTS budget_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) UNIQUE NOT NULL,
    allocation_id VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    description TEXT,
    related_entity_type VARCHAR(32),
    related_entity_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_budget_allocation FOREIGN KEY (allocation_id) 
        REFERENCES budget_allocations(allocation_id) ON DELETE CASCADE
);

CREATE INDEX idx_budget_trans_allocation ON budget_transactions(allocation_id);
CREATE INDEX idx_budget_trans_type ON budget_transactions(transaction_type);
CREATE INDEX idx_budget_trans_created ON budget_transactions(created_at);

-- Trigger for budget_allocations
CREATE TRIGGER update_budget_allocations_updated_at BEFORE UPDATE ON budget_allocations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Initial Payout Accounts (Chairman Default)
-- ============================================

INSERT INTO payout_accounts (account_id, account_name, account_type, provider, account_identifier, owner_type, is_default, is_active, verified)
VALUES 
    ('payout_chairman_default', '董事长默认收款账户', 'BANK_ACCOUNT', 'BANK', '', 'CHAIRMAN', TRUE, TRUE, FALSE)
ON CONFLICT (account_id) DO NOTHING;
