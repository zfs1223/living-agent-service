-- Living Agent Service Database Schema
-- PostgreSQL 15+
-- 合并自 schema.sql 基础定义 + V1~V27 增量迁移
-- 生成时间: 2026-06-15

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================
-- Helper Functions
-- ============================================

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 1. Enterprise / Organization Tables
-- ============================================

-- Departments Table (from schema.sql, richer than V1/V4)
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

CREATE INDEX IF NOT EXISTS idx_departments_parent ON enterprise_departments(parent_id);
CREATE INDEX IF NOT EXISTS idx_departments_code ON enterprise_departments(code);
CREATE INDEX IF NOT EXISTS idx_departments_target_brain ON enterprise_departments(target_brain);
CREATE INDEX IF NOT EXISTS idx_departments_name_trgm ON enterprise_departments USING gin(name gin_trgm_ops);

-- Employees Table (schema.sql base + V21/V23/V25 columns folded in)
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
    tenant_id VARCHAR(64),
    sync_source VARCHAR(32),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    -- V21: 统一员工体系字段
    employee_type VARCHAR(31),
    status VARCHAR(20),
    hire_date DATE,
    metadata TEXT,
    model VARCHAR(255),
    brain_domain VARCHAR(255),
    max_concurrent_tasks INTEGER,
    skills TEXT,
    capabilities TEXT,
    origin VARCHAR(20),
    -- V23: 职责字段
    roles TEXT,
    tools TEXT,
    responsibility_card_id VARCHAR(64),
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) 
        REFERENCES enterprise_departments(department_id) ON DELETE SET NULL,
    CONSTRAINT uk_employee_phone UNIQUE (phone),
    CONSTRAINT uk_employee_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_employees_department ON enterprise_employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employees_identity ON enterprise_employees(identity);
CREATE INDEX IF NOT EXISTS idx_employees_access_level ON enterprise_employees(access_level);
CREATE INDEX IF NOT EXISTS idx_employees_active ON enterprise_employees(active);
CREATE INDEX IF NOT EXISTS idx_employees_founder ON enterprise_employees(is_founder);
CREATE INDEX IF NOT EXISTS idx_employees_oauth ON enterprise_employees(oauth_provider, oauth_user_id);
CREATE INDEX IF NOT EXISTS idx_employees_name_trgm ON enterprise_employees USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_phone ON enterprise_employees(phone);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_email ON enterprise_employees(LOWER(email));
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_voice_print ON enterprise_employees(voice_print_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_tenant ON enterprise_employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_employee_type ON enterprise_employees(employee_type);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_status ON enterprise_employees(status);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_brain_domain ON enterprise_employees(brain_domain);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_origin ON enterprise_employees(origin);

-- Department Brain Mapping Table (schema.sql original, different from V4's department_brain_bindings)
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

-- Department Brain Bindings Table (V4: 部门大脑绑定，含模型配置)
CREATE TABLE IF NOT EXISTS department_brain_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id VARCHAR(64) NOT NULL REFERENCES enterprise_departments(department_id) ON DELETE CASCADE,
    brain_id VARCHAR(100) NOT NULL,
    brain_name VARCHAR(100),
    binding_type VARCHAR(20) DEFAULT 'PRIMARY',
    model_id UUID,
    model_configured BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_department_brain_binding UNIQUE (department_id, brain_id)
);

CREATE INDEX IF NOT EXISTS idx_dept_brain_bindings_dept ON department_brain_bindings(department_id);
CREATE INDEX IF NOT EXISTS idx_dept_brain_bindings_brain ON department_brain_bindings(brain_id);

-- Employee Sync Log Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_sync_log_source ON employee_sync_log(sync_source);
CREATE INDEX IF NOT EXISTS idx_sync_log_started ON employee_sync_log(started_at);

-- Tenants Table (V6)
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    name_en VARCHAR(200),
    description TEXT,
    website VARCHAR(500),
    owner_id VARCHAR(100),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenants_owner ON tenants(owner_id);
CREATE INDEX IF NOT EXISTS idx_tenants_active ON tenants(active);

-- ============================================
-- 2. Employee / Worker Tables
-- ============================================

-- Employees Table (V1: SINGLE_TABLE 继承策略)
CREATE TABLE IF NOT EXISTS employees (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50),
    department_id VARCHAR(64),
    status VARCHAR(20),
    position VARCHAR(50),
    hire_date DATE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    metadata VARCHAR(500),
    employee_type VARCHAR(31) NOT NULL,
    -- DigitalEmployeeEntity 字段
    model VARCHAR(255),
    brain_domain VARCHAR(255),
    max_concurrent_tasks INTEGER,
    skills VARCHAR(255),
    capabilities VARCHAR(255),
    origin VARCHAR(20),
    -- HumanEmployeeEntity 字段
    email VARCHAR(255),
    phone VARCHAR(255),
    role VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_employees_department ON employees(department);
CREATE INDEX IF NOT EXISTS idx_employees_department_id ON employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employees_status ON employees(status);
CREATE INDEX IF NOT EXISTS idx_employees_employee_type ON employees(employee_type);

-- Fixed Employee Definition Table (V5)
CREATE TABLE IF NOT EXISTS fixed_employee_definition (
    code VARCHAR(16) PRIMARY KEY,
    employee_id VARCHAR(100) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE SET NULL,
    name_zh VARCHAR(100) NOT NULL,
    name_en VARCHAR(100),
    title_zh VARCHAR(100) NOT NULL,
    title_en VARCHAR(100),
    department_code VARCHAR(50) NOT NULL,
    department_name VARCHAR(100),
    neuron_id VARCHAR(100),
    channel VARCHAR(100),
    roles JSONB DEFAULT '[]'::jsonb,
    capabilities JSONB DEFAULT '[]'::jsonb,
    tools JSONB DEFAULT '[]'::jsonb,
    required_skills JSONB DEFAULT '[]'::jsonb,
    personality JSONB DEFAULT '{}'::jsonb,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_department ON fixed_employee_definition(department_code);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_active ON fixed_employee_definition(active);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_definition_employee ON fixed_employee_definition(employee_id);

-- Fixed Employee Profile Table (V5)
CREATE TABLE IF NOT EXISTS fixed_employee_profile (
    code VARCHAR(16) PRIMARY KEY REFERENCES fixed_employee_definition(code) ON DELETE CASCADE,
    employee_id VARCHAR(100) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE CASCADE,
    display_name_zh VARCHAR(100) NOT NULL,
    display_name_en VARCHAR(100),
    summary_zh TEXT,
    summary_en TEXT,
    traits JSONB DEFAULT '[]'::jsonb,
    tool_tags JSONB DEFAULT '[]'::jsonb,
    long_term_memory JSONB DEFAULT '{}'::jsonb,
    preferences JSONB DEFAULT '{}'::jsonb,
    current_task TEXT,
    status VARCHAR(32) DEFAULT 'active',
    last_active_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_profile_status ON fixed_employee_profile(status);
CREATE INDEX IF NOT EXISTS idx_fixed_employee_profile_employee ON fixed_employee_profile(employee_id);

-- Fixed Employee Persona Table (V5)
CREATE TABLE IF NOT EXISTS fixed_employee_persona (
    code VARCHAR(16) PRIMARY KEY REFERENCES fixed_employee_definition(code) ON DELETE CASCADE,
    employee_id VARCHAR(100) UNIQUE REFERENCES enterprise_employees(employee_id) ON DELETE CASCADE,
    icon VARCHAR(32) DEFAULT '🤖',
    hair VARCHAR(32) DEFAULT 'short',
    glasses BOOLEAN DEFAULT FALSE,
    badge_style VARCHAR(32) DEFAULT 'classic',
    stance VARCHAR(32) DEFAULT 'focused',
    outfit VARCHAR(32) DEFAULT 'default',
    accent_color VARCHAR(32) DEFAULT '#58a6ff',
    face VARCHAR(32) DEFAULT 'neutral',
    skin_tone VARCHAR(32) DEFAULT '#f5d0b1',
    body_shape VARCHAR(32) DEFAULT 'default',
    clothing_variant VARCHAR(32) DEFAULT 'standard',
    accessory_variant VARCHAR(32) DEFAULT 'none',
    badge_label VARCHAR(100),
    avatar_style JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fixed_employee_persona_outfit ON fixed_employee_persona(outfit);

-- Employee Personalities Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_personality_employee ON employee_personalities(employee_id);

-- Digital Employee Records Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_digital_rec_employee ON digital_employee_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_digital_rec_type ON digital_employee_records(task_type);
CREATE INDEX IF NOT EXISTS idx_digital_rec_status ON digital_employee_records(status);
CREATE INDEX IF NOT EXISTS idx_digital_rec_created ON digital_employee_records(created_at);

-- Employee Lifecycle Events Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_lifecycle_employee ON employee_lifecycle_events(employee_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_type ON employee_lifecycle_events(event_type);
CREATE INDEX IF NOT EXISTS idx_lifecycle_occurred ON employee_lifecycle_events(occurred_at);

-- Employee Capabilities Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_cap_employee ON employee_capabilities(employee_id);
CREATE INDEX IF NOT EXISTS idx_cap_name ON employee_capabilities(capability_name);

-- Employee Skills Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_skill_employee ON employee_skills(employee_id);
CREATE INDEX IF NOT EXISTS idx_skill_name ON employee_skills(skill_name);

-- Employee Tools Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_tool_employee ON employee_tools(employee_id);
CREATE INDEX IF NOT EXISTS idx_tool_name ON employee_tools(tool_name);

-- Employee Templates Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_template_department ON employee_templates(department);
CREATE INDEX IF NOT EXISTS idx_template_role ON employee_templates(role);
CREATE INDEX IF NOT EXISTS idx_template_active ON employee_templates(is_active);

-- Employee Execution Receipts Table (V21)
CREATE TABLE IF NOT EXISTS employee_execution_receipts (
    id BIGSERIAL PRIMARY KEY,
    receipt_id VARCHAR(100) NOT NULL UNIQUE,
    execution_id VARCHAR(500) NOT NULL,
    dispatch_id VARCHAR(100),
    assignment_id VARCHAR(100),
    employee_code VARCHAR(100),
    employee_neuron_id VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    summary TEXT,
    received_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB,
    worktree_path VARCHAR(500),
    diff_path VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    department VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_receipt_receipt_id ON employee_execution_receipts(receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_execution_id ON employee_execution_receipts(execution_id);
CREATE INDEX IF NOT EXISTS idx_receipt_dispatch_id ON employee_execution_receipts(dispatch_id);
CREATE INDEX IF NOT EXISTS idx_receipt_employee_code ON employee_execution_receipts(employee_code);
CREATE INDEX IF NOT EXISTS idx_receipt_status ON employee_execution_receipts(status);
CREATE INDEX IF NOT EXISTS idx_receipt_created_at ON employee_execution_receipts(created_at);
CREATE INDEX IF NOT EXISTS idx_receipt_department ON employee_execution_receipts(department);

-- ============================================
-- 3. Knowledge Tables
-- ============================================

-- Knowledge Entries Table (schema.sql, richer version)
CREATE TABLE IF NOT EXISTS knowledge_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entry_id VARCHAR(64) UNIQUE NOT NULL,
    key VARCHAR(256) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    knowledge_type VARCHAR(32) NOT NULL DEFAULT 'FACT',
    importance VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    validity VARCHAR(16) NOT NULL DEFAULT 'PERMANENT',
    scope VARCHAR(16) NOT NULL DEFAULT 'SHARED',
    scope_identifier VARCHAR(128),
    brain_domain VARCHAR(50),
    neuron_id VARCHAR(128),
    owner_id VARCHAR(64),
    department_id VARCHAR(64),
    tags TEXT[],
    source VARCHAR(64),
    confidence DOUBLE PRECISION DEFAULT 1.0,
    relevance DOUBLE PRECISION,
    access_count INTEGER DEFAULT 0,
    verified BOOLEAN,
    promoted_from VARCHAR(256),
    status VARCHAR(20),
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    vector_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_entry_id ON knowledge_entries(entry_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_type ON knowledge_entries(knowledge_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_importance ON knowledge_entries(importance);
CREATE INDEX IF NOT EXISTS idx_knowledge_scope ON knowledge_entries(scope, scope_identifier);
CREATE INDEX IF NOT EXISTS idx_knowledge_owner ON knowledge_entries(owner_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_department ON knowledge_entries(department_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_brain ON knowledge_entries(brain_domain);
CREATE INDEX IF NOT EXISTS idx_knowledge_tags ON knowledge_entries USING gin(tags);
CREATE INDEX IF NOT EXISTS idx_knowledge_expires ON knowledge_entries(expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS knowledge_tags (
    knowledge_id UUID NOT NULL,
    tag_name VARCHAR(128) NOT NULL,
    tag_value VARCHAR(512),
    PRIMARY KEY (knowledge_id, tag_name),
    CONSTRAINT fk_knowledge_tags FOREIGN KEY (knowledge_id)
        REFERENCES knowledge_entries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_knowledge_tags_knowledge_id ON knowledge_tags(knowledge_id);

CREATE TABLE IF NOT EXISTS knowledge_metadata (
    knowledge_id UUID NOT NULL,
    meta_key VARCHAR(128) NOT NULL,
    meta_value VARCHAR(2048),
    PRIMARY KEY (knowledge_id, meta_key),
    CONSTRAINT fk_knowledge_metadata FOREIGN KEY (knowledge_id)
        REFERENCES knowledge_entries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_knowledge_metadata_knowledge_id ON knowledge_metadata(knowledge_id);

-- Knowledge Evolution Log Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_evolution_entry ON knowledge_evolution_log(entry_id);
CREATE INDEX IF NOT EXISTS idx_evolution_type ON knowledge_evolution_log(evolution_type);

-- ============================================
-- 4. Model Pool Tables (V2 + V9)
-- ============================================

-- Model Providers Table
CREATE TABLE IF NOT EXISTS model_providers (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    base_url VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    supports_tool_choice BOOLEAN DEFAULT TRUE,
    default_max_tokens INTEGER DEFAULT 4096,
    auto_discover_models BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_providers_protocol ON model_providers(protocol);
CREATE INDEX IF NOT EXISTS idx_providers_enabled ON model_providers(enabled);

-- LLM Models Table (V2 base + V9 columns folded in)
CREATE TABLE IF NOT EXISTS llm_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(50) NOT NULL REFERENCES model_providers(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    context_window INTEGER DEFAULT 32768,
    max_output_tokens INTEGER DEFAULT 4096,
    supports_vision BOOLEAN DEFAULT FALSE,
    supports_reasoning BOOLEAN DEFAULT FALSE,
    temperature DECIMAL(4,2),
    enabled BOOLEAN DEFAULT TRUE,
    recommended BOOLEAN DEFAULT FALSE,
    best_for TEXT,
    input_types VARCHAR(50) DEFAULT 'text',
    -- V9: 模型能力评定字段
    capability_tags VARCHAR(500),
    performance_score INTEGER,
    parameter_size VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_llm_model UNIQUE (provider_id, model_name)
);

CREATE INDEX IF NOT EXISTS idx_llm_models_provider ON llm_models(provider_id);
CREATE INDEX IF NOT EXISTS idx_llm_models_enabled ON llm_models(enabled);
CREATE INDEX IF NOT EXISTS idx_llm_models_recommended ON llm_models(recommended);

-- Brain Model Assignments Table (V2)
CREATE TABLE IF NOT EXISTS brain_model_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brain_id VARCHAR(100) NOT NULL UNIQUE,
    brain_name VARCHAR(200),
    brain_type VARCHAR(50),
    model_id UUID REFERENCES llm_models(id) ON DELETE SET NULL,
    assigned_by VARCHAR(100),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_brain_assignments_brain ON brain_model_assignments(brain_id);
CREATE INDEX IF NOT EXISTS idx_brain_assignments_model ON brain_model_assignments(model_id);
CREATE INDEX IF NOT EXISTS idx_brain_assignments_type ON brain_model_assignments(brain_type);

-- Brain Model Change History Table (V3)
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

CREATE INDEX IF NOT EXISTS idx_brain_history_brain_id ON brain_model_change_history(brain_id);
CREATE INDEX IF NOT EXISTS idx_brain_history_source ON brain_model_change_history(source);
CREATE INDEX IF NOT EXISTS idx_brain_history_created_at ON brain_model_change_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_brain_history_brain_source ON brain_model_change_history(brain_id, source);

-- ============================================
-- 5. Task / Project Tables
-- ============================================

-- Tasks Table (V1; V10 field lengths applied)
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    task_type VARCHAR(50),
    description TEXT,
    priority INTEGER DEFAULT 0,
    required_capability VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_out_at TIMESTAMP WITH TIME ZONE,
    assigned_to VARCHAR(100),
    completed_at TIMESTAMP WITH TIME ZONE,
    user_id VARCHAR(100),
    tenant_id VARCHAR(100),
    task_key VARCHAR(500),
    execution_id VARCHAR(500),
    conversation_id VARCHAR(100),
    department_code VARCHAR(50),
    source_type VARCHAR(50),
    source_session_id VARCHAR(100),
    project_id VARCHAR(100),
    submission_result TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE,
    reviewer_id VARCHAR(100),
    review_conclusion TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    readiness_status VARCHAR(30),
    clarification_questions TEXT,
    clarification_answer TEXT,
    clarification_requested_at TIMESTAMP WITH TIME ZONE,
    blocking_issues TEXT,
    clarification_round INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_task_task_id ON tasks(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assigned_status ON tasks(assigned_to, status);
CREATE INDEX IF NOT EXISTS idx_task_user_id ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_task_task_key ON tasks(task_key);
CREATE INDEX IF NOT EXISTS idx_task_execution_id ON tasks(execution_id);
CREATE INDEX IF NOT EXISTS idx_task_project_id ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_task_department_status ON tasks(department_code, status);

-- Projects Table (V1; V10 field lengths applied)
CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    description TEXT,
    status VARCHAR(30) NOT NULL,
    current_phase VARCHAR(50),
    owner_department VARCHAR(50),
    manager_id VARCHAR(100),
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    progress DOUBLE PRECISION DEFAULT 0.0,
    tenant_id VARCHAR(100),
    creator_user_id VARCHAR(100),
    project_key VARCHAR(200),
    source_task_key VARCHAR(500),
    source_conversation_id VARCHAR(100),
    data_namespace VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_project_project_id ON projects(project_id);
CREATE INDEX IF NOT EXISTS idx_project_status ON projects(status);
CREATE INDEX IF NOT EXISTS idx_project_department ON projects(owner_department);
CREATE INDEX IF NOT EXISTS idx_project_manager ON projects(manager_id);
CREATE INDEX IF NOT EXISTS idx_project_project_key ON projects(project_key);

-- Artifact Records Table (V1/V8 combined; V10 field lengths applied)
CREATE TABLE IF NOT EXISTS artifact_records (
    id BIGSERIAL PRIMARY KEY,
    artifact_id VARCHAR(100) NOT NULL UNIQUE,
    execution_id VARCHAR(500) NOT NULL,
    department VARCHAR(50),
    owner_employee_code VARCHAR(100),
    owner_employee_neuron_id VARCHAR(200),
    type VARCHAR(50) NOT NULL,
    path VARCHAR(500) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary TEXT,
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata_json JSONB,
    task_id VARCHAR(100),
    project_id VARCHAR(100),
    -- Artifact visibility fields (V20260604)
    visibility VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT',
    created_by VARCHAR(100),
    participant_ids TEXT,
    viewer_departments TEXT,
    visible_to_leader BOOLEAN NOT NULL DEFAULT TRUE
);

-- visibility 枚举值：
--   PRIVATE    - 仅创建者 + participants 可见
--   DEPARTMENT - 本部门全员可见
--   PUBLIC     - 跨部门公开（需审批或标记）
--   RESTRICTED - 仅指定 viewer_departments 可见

CREATE INDEX IF NOT EXISTS idx_artifact_execution_id ON artifact_records(execution_id);
CREATE INDEX IF NOT EXISTS idx_artifact_department ON artifact_records(department);
CREATE INDEX IF NOT EXISTS idx_artifact_employee_code ON artifact_records(owner_employee_code);
CREATE INDEX IF NOT EXISTS idx_artifact_type ON artifact_records(type);
CREATE INDEX IF NOT EXISTS idx_artifact_created_at ON artifact_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_department_type ON artifact_records(department, type);
CREATE INDEX IF NOT EXISTS idx_artifact_task_id ON artifact_records(task_id);
CREATE INDEX IF NOT EXISTS idx_artifact_project_id ON artifact_records(project_id);
CREATE INDEX IF NOT EXISTS idx_artifact_visibility ON artifact_records(visibility);
CREATE INDEX IF NOT EXISTS idx_artifact_created_by ON artifact_records(created_by);
CREATE INDEX IF NOT EXISTS idx_artifact_department_visibility ON artifact_records(department, visibility);

-- Code Review States Table (V16)
CREATE TABLE IF NOT EXISTS code_review_states (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL UNIQUE,
    project_id VARCHAR(100),
    execution_id VARCHAR(500),
    stage VARCHAR(50) NOT NULL,
    review_round INTEGER DEFAULT 0,
    developer_employee_code VARCHAR(100),
    reviewer_employee_code VARCHAR(100),
    worktree_path VARCHAR(500),
    diff_path VARCHAR(500),
    review_report_path VARCHAR(500),
    final_summary_path VARCHAR(500),
    review_findings_json JSONB,
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_review_states_stage ON code_review_states(stage);
CREATE INDEX IF NOT EXISTS idx_review_states_execution_id ON code_review_states(execution_id);
CREATE INDEX IF NOT EXISTS idx_review_states_developer ON code_review_states(developer_employee_code);
CREATE INDEX IF NOT EXISTS idx_review_states_reviewer ON code_review_states(reviewer_employee_code);

-- Internal Reviews Table (部门内审查持久化)
CREATE TABLE IF NOT EXISTS internal_reviews (
    id BIGSERIAL PRIMARY KEY,
    review_id VARCHAR(100) NOT NULL UNIQUE,
    todo_item_id VARCHAR(200) NOT NULL,
    author_code VARCHAR(100),
    reviewer_code VARCHAR(100),
    execution_id VARCHAR(500),
    review_round INTEGER DEFAULT 0,
    max_rounds INTEGER DEFAULT 3,
    status VARCHAR(50) NOT NULL,
    result VARCHAR(50),
    quality_score DOUBLE PRECISION,
    review_comment TEXT,
    revision_notes TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_internal_review_todo_item_id ON internal_reviews(todo_item_id);
CREATE INDEX IF NOT EXISTS idx_internal_review_status ON internal_reviews(status);

-- Department Deliverables Table (M-DA: 部门交付物持久化)
CREATE TABLE IF NOT EXISTS department_deliverables (
    id BIGSERIAL PRIMARY KEY,
    deliverable_id VARCHAR(200) NOT NULL UNIQUE,
    department VARCHAR(50) NOT NULL,
    plan_id VARCHAR(200),
    objective TEXT,
    status VARCHAR(50) NOT NULL,
    items_json JSONB,
    summary TEXT,
    issues_json JSONB,
    overall_quality_score DOUBLE PRECISION,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_deliverable_department ON department_deliverables(department);
CREATE INDEX IF NOT EXISTS idx_deliverable_plan_id ON department_deliverables(plan_id);

-- Intervention Decisions Table (16.3: 干预决策持久化)
CREATE TABLE IF NOT EXISTS intervention_decisions (
    id BIGSERIAL PRIMARY KEY,
    decision_id VARCHAR(100) NOT NULL UNIQUE,
    session_id VARCHAR(100),
    conversation_id VARCHAR(100),
    operation_type VARCHAR(200) NOT NULL,
    operation_details JSONB,
    source_neuron_id VARCHAR(200),
    source_channel_id VARCHAR(200),
    risk_level VARCHAR(20),
    risk_score DOUBLE PRECISION,
    risk_factors JSONB,
    impact_level VARCHAR(20),
    impact_score DOUBLE PRECISION,
    impact_scope JSONB,
    intervention_type VARCHAR(30),
    ai_decision TEXT,
    human_decision TEXT,
    final_decision TEXT,
    status VARCHAR(30) NOT NULL,
    department VARCHAR(64),
    assigned_to VARCHAR(100),
    responded_by VARCHAR(100),
    timeout_seconds INTEGER,
    escalation_level INTEGER,
    learning_applied BOOLEAN,
    learning_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_intervention_decision_id ON intervention_decisions(decision_id);
CREATE INDEX IF NOT EXISTS idx_intervention_status ON intervention_decisions(status);
CREATE INDEX IF NOT EXISTS idx_intervention_department ON intervention_decisions(department);
CREATE INDEX IF NOT EXISTS idx_intervention_created ON intervention_decisions(created_at);

-- ============================================
-- 6. Session / Conversation Tables
-- ============================================

-- Department Conversations Table (V1; V10 field lengths applied)
CREATE TABLE IF NOT EXISTS department_conversations (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL UNIQUE,
    conversation_key VARCHAR(500),
    tenant_id VARCHAR(100),
    owner_user_id VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    last_message_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE,
    active_task_key VARCHAR(500),
    active_execution_id VARCHAR(500),
    retention_policy VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    destroyed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_conv_conversation_id ON department_conversations(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conv_owner_dept_status ON department_conversations(owner_user_id, department_code, status);
CREATE INDEX IF NOT EXISTS idx_conv_department_status ON department_conversations(department_code, status);
CREATE INDEX IF NOT EXISTS idx_conv_tenant ON department_conversations(tenant_id);

-- Department Chat Messages Table (V1; V10 field lengths applied)
CREATE TABLE IF NOT EXISTS department_chat_messages (
    id VARCHAR(36) PRIMARY KEY,
    department VARCHAR(50) NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(100),
    content TEXT,
    role VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    request_id VARCHAR(100),
    brain_id VARCHAR(100),
    model VARCHAR(50),
    intent VARCHAR(50),
    neuron VARCHAR(50),
    status VARCHAR(50),
    conversation_id VARCHAR(100),
    task_key VARCHAR(500),
    execution_id VARCHAR(500),
    message_type VARCHAR(30),
    tenant_id VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_dept_timestamp ON department_chat_messages(department, timestamp);
CREATE INDEX IF NOT EXISTS idx_dept_user ON department_chat_messages(department, user_id);
CREATE INDEX IF NOT EXISTS idx_msg_conversation_id ON department_chat_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_msg_conversation_timestamp ON department_chat_messages(conversation_id, timestamp);

-- Session Contexts Table (V12; V20 field lengths applied)
CREATE TABLE IF NOT EXISTS session_contexts (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    tenant_id VARCHAR(64),
    department_code VARCHAR(32),
    task_key VARCHAR(500),
    execution_id VARCHAR(500),
    project_id VARCHAR(100),
    project_key VARCHAR(128),
    conversation_id VARCHAR(100),
    connected_at TIMESTAMP WITH TIME ZONE,
    last_activity TIMESTAMP WITH TIME ZONE,
    attributes_json TEXT,
    
    CONSTRAINT chk_session_id CHECK (session_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_sess_user_id ON session_contexts(user_id);
CREATE INDEX IF NOT EXISTS idx_sess_tenant_id ON session_contexts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sess_conversation_id ON session_contexts(conversation_id);
CREATE INDEX IF NOT EXISTS idx_sess_last_activity ON session_contexts(last_activity);
-- V28: 反向索引支持 (InMemoryConnectionRegistry 重启恢复)
CREATE INDEX IF NOT EXISTS idx_sess_task_key ON session_contexts(task_key);
CREATE INDEX IF NOT EXISTS idx_sess_execution_id ON session_contexts(execution_id);
CREATE INDEX IF NOT EXISTS idx_sess_project_key ON session_contexts(project_key);

-- Pending Events Table (V13)
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

-- User Sessions Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_session_employee ON user_sessions(employee_id);
CREATE INDEX IF NOT EXISTS idx_session_speaker ON user_sessions(speaker_id);
CREATE INDEX IF NOT EXISTS idx_session_active ON user_sessions(active);
CREATE INDEX IF NOT EXISTS idx_session_expires ON user_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_session_started ON user_sessions(started_at);

-- ============================================
-- 7. Skills Table (V1/V17 combined)
-- ============================================

CREATE TABLE IF NOT EXISTS skills (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(50),
    target_brain VARCHAR(50),
    content TEXT,
    skill_path VARCHAR(500),
    scope VARCHAR(16) DEFAULT 'global',
    owner_id VARCHAR(64),
    department_id VARCHAR(64),
    metadata_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_skills_scope ON skills(scope);
CREATE INDEX IF NOT EXISTS idx_skills_owner ON skills(owner_id);
CREATE INDEX IF NOT EXISTS idx_skills_dept ON skills(department_id);
CREATE INDEX IF NOT EXISTS idx_skills_category ON skills(category);
CREATE INDEX IF NOT EXISTS idx_skills_target_brain ON skills(target_brain);

-- ============================================
-- 8. Evolution Tables
-- ============================================

-- Evolution Tier History Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_evolution_tier_employee ON evolution_tier_history(employee_id);
CREATE INDEX IF NOT EXISTS idx_evolution_tier_created ON evolution_tier_history(created_at);

-- Hardware Upgrades Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_hardware_upg_employee ON hardware_upgrades(employee_id);
CREATE INDEX IF NOT EXISTS idx_hardware_upg_status ON hardware_upgrades(status);

-- Evolution Results Table (V19)
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

-- Evolution Feedback Table (V19)
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

-- Evolution Audit Logs Table (V19)
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

-- ============================================
-- 9. Proactive Task Tables (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_task_type ON proactive_task_config(task_type);
CREATE INDEX IF NOT EXISTS idx_task_trigger ON proactive_task_config(trigger_type);
CREATE INDEX IF NOT EXISTS idx_task_enabled ON proactive_task_config(enabled);
CREATE INDEX IF NOT EXISTS idx_task_next_run ON proactive_task_config(next_run_at) WHERE enabled = TRUE;

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

CREATE INDEX IF NOT EXISTS idx_execution_task ON proactive_execution_log(task_id);
CREATE INDEX IF NOT EXISTS idx_execution_status ON proactive_execution_log(status);
CREATE INDEX IF NOT EXISTS idx_execution_started ON proactive_execution_log(started_at);

-- ============================================
-- 10. Performance Assessment Tables
-- ============================================

-- Performance Assessments Table (schema.sql, richer version)
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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assessment_employee ON performance_assessments(employee_id);
CREATE INDEX IF NOT EXISTS idx_assessment_period ON performance_assessments(assessment_period);
CREATE INDEX IF NOT EXISTS idx_assessment_date ON performance_assessments(period_start_date, period_end_date);
CREATE INDEX IF NOT EXISTS idx_assessment_score ON performance_assessments(overall_score DESC);

-- Company Indicators Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_indicator_category ON company_indicators(category);
CREATE INDEX IF NOT EXISTS idx_indicator_active ON company_indicators(is_active);

-- Performance Indicators Table (V19, different from company_indicators)
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

-- Performance Trend Snapshots Table (V19)
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

-- Department Performance Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_dept_perf_department ON department_performances(department_id);
CREATE INDEX IF NOT EXISTS idx_dept_perf_period ON department_performances(assessment_period);
CREATE INDEX IF NOT EXISTS idx_dept_perf_date ON department_performances(period_start_date, period_end_date);

-- ============================================
-- 11. CEO Alert Tables (schema.sql)
-- ============================================

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

CREATE INDEX IF NOT EXISTS idx_ceo_alert_type ON ceo_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_ceo_alert_severity ON ceo_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_ceo_alert_acknowledged ON ceo_alerts(acknowledged);
CREATE INDEX IF NOT EXISTS idx_ceo_alert_triggered ON ceo_alerts(triggered_at);

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

CREATE INDEX IF NOT EXISTS idx_ceo_rec_category ON ceo_recommendations(category);
CREATE INDEX IF NOT EXISTS idx_ceo_rec_priority ON ceo_recommendations(priority);
CREATE INDEX IF NOT EXISTS idx_ceo_rec_status ON ceo_recommendations(status);

-- ============================================
-- 12. Compensation Tables (V19)
-- ============================================

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

-- ============================================
-- 13. Economic / Credit Tables (schema.sql)
-- ============================================

CREATE TABLE IF NOT EXISTS credit_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,
    balance_cents BIGINT DEFAULT 0,
    total_earned_cents BIGINT DEFAULT 0,
    total_exchanged_cents BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_credit_balance ON credit_accounts(balance_cents);
CREATE INDEX IF NOT EXISTS idx_credit_employee ON credit_accounts(employee_id);

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

CREATE INDEX IF NOT EXISTS idx_credit_trans_employee ON credit_transactions(employee_id);
CREATE INDEX IF NOT EXISTS idx_credit_trans_type ON credit_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_credit_trans_created ON credit_transactions(created_at);

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

CREATE INDEX IF NOT EXISTS idx_income_employee ON income_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_income_status ON income_records(status, created_at);
CREATE INDEX IF NOT EXISTS idx_income_source ON income_records(source_type);

-- Budget Allocations Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_budget_owner ON budget_allocations(owner_id);
CREATE INDEX IF NOT EXISTS idx_budget_type ON budget_allocations(budget_type);
CREATE INDEX IF NOT EXISTS idx_budget_period ON budget_allocations(period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_budget_active ON budget_allocations(is_active);

-- Budget Transactions Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_budget_trans_allocation ON budget_transactions(allocation_id);
CREATE INDEX IF NOT EXISTS idx_budget_trans_type ON budget_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_budget_trans_created ON budget_transactions(created_at);

-- Payout Accounts Table (schema.sql)
CREATE TABLE IF NOT EXISTS payout_accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    account_name VARCHAR(100),
    account_type VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    account_identifier VARCHAR(256) NOT NULL,
    owner_id VARCHAR(100),
    owner_type VARCHAR(32) DEFAULT 'ENTERPRISE',
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payout_owner ON payout_accounts(owner_id);
CREATE INDEX IF NOT EXISTS idx_payout_type ON payout_accounts(account_type);
CREATE INDEX IF NOT EXISTS idx_payout_provider ON payout_accounts(provider);
CREATE INDEX IF NOT EXISTS idx_payout_active ON payout_accounts(is_active);
CREATE INDEX IF NOT EXISTS idx_payout_default ON payout_accounts(is_default);

-- ============================================
-- 14. Misc Infrastructure Tables
-- ============================================

-- User Behavior Pattern Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_behavior_user ON user_behavior_pattern(user_id);
CREATE INDEX IF NOT EXISTS idx_behavior_type ON user_behavior_pattern(pattern_type);

-- Risk Prediction Log Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_risk_indicator ON risk_prediction_log(indicator_id);
CREATE INDEX IF NOT EXISTS idx_risk_level ON risk_prediction_log(risk_level);
CREATE INDEX IF NOT EXISTS idx_risk_acknowledged ON risk_prediction_log(acknowledged);
CREATE INDEX IF NOT EXISTS idx_risk_created ON risk_prediction_log(created_at);

-- Notification Queue Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_notification_status ON notification_queue(status);
CREATE INDEX IF NOT EXISTS idx_notification_scheduled ON notification_queue(scheduled_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_notification_type ON notification_queue(notification_type);

-- MainBrain Growth Records Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_growth_type ON mainbrain_growth_records(growth_type);
CREATE INDEX IF NOT EXISTS idx_growth_category ON mainbrain_growth_records(category);
CREATE INDEX IF NOT EXISTS idx_growth_created ON mainbrain_growth_records(created_at);

-- MainBrain Personality Evolution Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_personality_dimension ON mainbrain_personality_evolution(personality_dimension);
CREATE INDEX IF NOT EXISTS idx_personality_created ON mainbrain_personality_evolution(created_at);

-- Cross Department Coordination Cases Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_cross_case_status ON cross_department_cases(status);
CREATE INDEX IF NOT EXISTS idx_cross_case_departments ON cross_department_cases USING gin(departments);
CREATE INDEX IF NOT EXISTS idx_cross_case_brains ON cross_department_cases USING gin(brains_involved);
CREATE INDEX IF NOT EXISTS idx_cross_case_success ON cross_department_cases(success);
CREATE INDEX IF NOT EXISTS idx_cross_case_created ON cross_department_cases(created_at);

-- MainBrain Capability Registry Table (schema.sql)
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

CREATE INDEX IF NOT EXISTS idx_capability_category ON mainbrain_capabilities(category);
CREATE INDEX IF NOT EXISTS idx_capability_level ON mainbrain_capabilities(level);

-- ============================================
-- 15. Security / Access Audit Tables
-- ============================================

-- Access Audit Logs Table (V24, replaces old singular access_audit_log)
CREATE TABLE IF NOT EXISTS access_audit_logs (
    log_id VARCHAR(50) NOT NULL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    employee_name VARCHAR(255),
    resource VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500),
    timestamp BIGINT NOT NULL,
    session_id VARCHAR(100),
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_employee_id ON access_audit_logs(employee_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON access_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_resource_action ON access_audit_logs(resource, action);

-- ============================================
-- 16. Speaker / Voice Print Tables (schema.sql)
-- ============================================

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

CREATE INDEX IF NOT EXISTS idx_speaker_name ON speaker_profiles(name);
CREATE INDEX IF NOT EXISTS idx_speaker_employee ON speaker_profiles(employee_id);
CREATE INDEX IF NOT EXISTS idx_speaker_active ON speaker_profiles(active);

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

CREATE INDEX IF NOT EXISTS idx_voiceprint_reg_speaker ON voiceprint_registration_log(speaker_id);
CREATE INDEX IF NOT EXISTS idx_voiceprint_reg_employee ON voiceprint_registration_log(employee_id);
CREATE INDEX IF NOT EXISTS idx_voiceprint_reg_created ON voiceprint_registration_log(created_at);

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

CREATE INDEX IF NOT EXISTS idx_voiceprint_verif_speaker ON voiceprint_verification_log(speaker_id);
CREATE INDEX IF NOT EXISTS idx_voiceprint_verif_employee ON voiceprint_verification_log(employee_id);
CREATE INDEX IF NOT EXISTS idx_voiceprint_verif_created ON voiceprint_verification_log(created_at);
CREATE INDEX IF NOT EXISTS idx_voiceprint_verif_verified ON voiceprint_verification_log(verified);

-- ============================================
-- 17. User Profile Tables (schema.sql)
-- ============================================

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

CREATE INDEX IF NOT EXISTS idx_user_profile_employee ON user_profiles(employee_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_speaker ON user_profiles(speaker_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_digital ON user_profiles(digital_id);

-- ============================================
-- 18. Heartbeat Tables (schema.sql)
-- ============================================

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

CREATE INDEX IF NOT EXISTS idx_heartbeat_employee ON heartbeat_runs(employee_id);
CREATE INDEX IF NOT EXISTS idx_heartbeat_status ON heartbeat_runs(status);
CREATE INDEX IF NOT EXISTS idx_heartbeat_source ON heartbeat_runs(wake_source);
CREATE INDEX IF NOT EXISTS idx_heartbeat_scheduled ON heartbeat_runs(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_heartbeat_created ON heartbeat_runs(created_at);

-- ============================================
-- 19. Configuration Tables (schema.sql)
-- ============================================

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

CREATE INDEX IF NOT EXISTS idx_config_type ON config_versions(config_type);
CREATE INDEX IF NOT EXISTS idx_config_key ON config_versions(config_key);
CREATE INDEX IF NOT EXISTS idx_config_active ON config_versions(is_active);
CREATE INDEX IF NOT EXISTS idx_config_changed ON config_versions(changed_at);

-- ============================================
-- 20. Windows Automation Nodes (V14)
-- ============================================

CREATE TABLE IF NOT EXISTS windows_automation_nodes (
    node_id VARCHAR(64) PRIMARY KEY,
    ip_address VARCHAR(64) NOT NULL,
    port INTEGER DEFAULT 8765,
    hostname VARCHAR(128),
    cpu_count INTEGER,
    memory_gb DECIMAL(5,1),
    applications TEXT,
    description VARCHAR(256),
    status VARCHAR(16) DEFAULT 'offline',
    last_heartbeat TIMESTAMP,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id VARCHAR(64),
    user_id VARCHAR(64),
    enabled BOOLEAN DEFAULT TRUE,
    client_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_wan_tenant ON windows_automation_nodes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wan_status ON windows_automation_nodes(status);
CREATE INDEX IF NOT EXISTS idx_wan_user ON windows_automation_nodes(user_id);
CREATE INDEX IF NOT EXISTS idx_wan_client_id ON windows_automation_nodes(client_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_wan_client_id_unique ON windows_automation_nodes(client_id) WHERE client_id IS NOT NULL;

-- ============================================
-- 20b. Client Device Registry & User Binding (V26)
-- ============================================

-- 客户端设备注册表
CREATE TABLE IF NOT EXISTS client_device_registry (
    client_id       VARCHAR(100) PRIMARY KEY,
    hostname        VARCHAR(100) NOT NULL,
    platform        VARCHAR(20) NOT NULL,
    os_user         VARCHAR(100),
    mac_address     VARCHAR(50),
    ip_address      VARCHAR(50),
    app_version     VARCHAR(20),
    first_seen_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) DEFAULT 'active',
    node_id         VARCHAR(100),
    tenant_id       VARCHAR(100),
    applications    TEXT,
    UNIQUE (hostname, mac_address)
);

CREATE INDEX IF NOT EXISTS idx_cdr_hostname ON client_device_registry(hostname);
CREATE INDEX IF NOT EXISTS idx_cdr_mac_address ON client_device_registry(mac_address);
CREATE INDEX IF NOT EXISTS idx_cdr_status ON client_device_registry(status);
CREATE INDEX IF NOT EXISTS idx_cdr_last_seen ON client_device_registry(last_seen_at);

-- 客户端与用户的临时绑定表
CREATE TABLE IF NOT EXISTS client_user_binding (
    client_id       VARCHAR(100) NOT NULL,
    user_id         VARCHAR(100) NOT NULL,
    access_level    INT NOT NULL DEFAULT 0,
    department_code VARCHAR(50),
    tenant_id       VARCHAR(100),
    bound_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_cub_user_id ON client_user_binding(user_id);
CREATE INDEX IF NOT EXISTS idx_cub_client_id ON client_user_binding(client_id);
CREATE INDEX IF NOT EXISTS idx_cub_last_active ON client_user_binding(last_active_at);

-- 客户端操作审计日志表
CREATE TABLE IF NOT EXISTS client_operation_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    client_id       VARCHAR(100) NOT NULL,
    user_id         VARCHAR(100) NOT NULL,
    target_client_id VARCHAR(100),
    target_node_id  VARCHAR(100),
    action          VARCHAR(50) NOT NULL,
    operation_type  VARCHAR(50) NOT NULL,
    parameters      TEXT,
    result          VARCHAR(20) NOT NULL,
    error_message   TEXT,
    executed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms     BIGINT
);

CREATE INDEX IF NOT EXISTS idx_coal_client_id ON client_operation_audit_log(client_id);
CREATE INDEX IF NOT EXISTS idx_coal_user_id ON client_operation_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_coal_executed_at ON client_operation_audit_log(executed_at);
CREATE INDEX IF NOT EXISTS idx_coal_action ON client_operation_audit_log(action);

-- ============================================
-- 21. Autonomy Trace Events (V18 + V22)
-- ============================================

CREATE TABLE IF NOT EXISTS autonomy_trace_events (
    id UUID PRIMARY KEY,
    trace_id VARCHAR(64),
    request_id VARCHAR(100) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    actor VARCHAR(128),
    summary TEXT,
    data TEXT,
    -- V22: 统一 Trace 关联键
    task_key VARCHAR(100),
    execution_id VARCHAR(500),
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trace_request_id ON autonomy_trace_events (request_id);
CREATE INDEX IF NOT EXISTS idx_trace_stage ON autonomy_trace_events (stage);
CREATE INDEX IF NOT EXISTS idx_trace_actor ON autonomy_trace_events (actor);
CREATE INDEX IF NOT EXISTS idx_trace_timestamp ON autonomy_trace_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_trace_task_key ON autonomy_trace_events (task_key);
CREATE INDEX IF NOT EXISTS idx_trace_execution_id ON autonomy_trace_events (execution_id);
-- P18-A: trace_id unique constraint for write verification
CREATE UNIQUE INDEX IF NOT EXISTS idx_trace_trace_id_unique ON autonomy_trace_events (trace_id);

-- ============================================
-- 21.2. Runtime Events Table (B-0-4)
-- ============================================
CREATE TABLE IF NOT EXISTS runtime_events (
    id UUID PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,
    scope_key VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(64),
    event_type VARCHAR(128) NOT NULL,
    data TEXT,
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_re_scope ON runtime_events (scope);
CREATE INDEX IF NOT EXISTS idx_re_scope_key ON runtime_events (scope, scope_key);
CREATE INDEX IF NOT EXISTS idx_re_tenant ON runtime_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_re_type ON runtime_events (event_type);
CREATE INDEX IF NOT EXISTS idx_re_timestamp ON runtime_events (timestamp);

-- ============================================
-- 21.2.1. Department Execution Results Table (B-0-1)
-- ============================================
CREATE TABLE IF NOT EXISTS department_execution_results (
    id UUID PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    batch_id VARCHAR(100),
    department VARCHAR(64),
    status VARCHAR(32),
    dispatched_assignments TEXT,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_der_execution_id ON department_execution_results (execution_id);
CREATE INDEX IF NOT EXISTS idx_der_batch_id ON department_execution_results (batch_id);
CREATE INDEX IF NOT EXISTS idx_der_department ON department_execution_results (department);
CREATE INDEX IF NOT EXISTS idx_der_status ON department_execution_results (status);

-- ============================================
-- 21.2.2. Notifications Table (B-1-11)
-- ============================================
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    notification_id VARCHAR(64) NOT NULL UNIQUE,
    department VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    metadata_json TEXT,
    timestamp TIMESTAMP NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_notif_dept ON notifications (department);
CREATE INDEX IF NOT EXISTS idx_notif_dept_unread ON notifications (department, read);
CREATE INDEX IF NOT EXISTS idx_notif_timestamp ON notifications (timestamp);

-- ============================================
-- 21.2.3. Brain Boundary Audit Table (T-1-5)
-- ============================================
CREATE TABLE IF NOT EXISTS brain_boundary_audit (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    brain_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(128) NOT NULL,
    result VARCHAR(32) NOT NULL,
    violation_type VARCHAR(64),
    message TEXT
);

CREATE INDEX IF NOT EXISTS idx_bba_brain ON brain_boundary_audit (brain_id);
CREATE INDEX IF NOT EXISTS idx_bba_timestamp ON brain_boundary_audit (timestamp);
CREATE INDEX IF NOT EXISTS idx_bba_result ON brain_boundary_audit (result);

-- ============================================
-- 21.3. Ledger Transaction Tables (V27)
-- LedgerService 持久化表 - P0-1 修复
-- ============================================

-- 员工账本交易记录表（用于存储员工余额、收入记录、奖励记录）
CREATE TABLE IF NOT EXISTS ledger_transaction (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(64)  NOT NULL UNIQUE,
    employee_id     VARCHAR(128) NOT NULL,
    source_type     VARCHAR(32)  NOT NULL,   -- INCOME / REWARD / DEBIT / ACHIEVEMENT / PENDING_INCOME
    source_id       VARCHAR(128),
    amount_cents    INTEGER      NOT NULL,   -- 正数=入账，负数=出账
    balance_after   INTEGER      NOT NULL,   -- 操作后余额快照
    status          VARCHAR(16)  NOT NULL,   -- RECEIVED / PENDING
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_employee ON ledger_transaction(employee_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_source   ON ledger_transaction(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_ledger_status   ON ledger_transaction(status);

COMMENT ON TABLE ledger_transaction IS '员工账本交易记录表 - LedgerService 持久化';
COMMENT ON COLUMN ledger_transaction.transaction_id IS '业务交易ID，如 inc_xxx / txn_xxx';
COMMENT ON COLUMN ledger_transaction.employee_id IS '员工ID，格式 employee://...';
COMMENT ON COLUMN ledger_transaction.source_type IS '收入来源类型：INCOME=任务收入，REWARD=奖励，DEBIT=消费，ACHIEVEMENT=成就奖金，PENDING_INCOME=待确认收入';
COMMENT ON COLUMN ledger_transaction.amount_cents IS '金额（单位：分），正数入账，负数出账';
COMMENT ON COLUMN ledger_transaction.balance_after IS '操作后的余额快照，用于快速查询当前余额';
COMMENT ON COLUMN ledger_transaction.status IS '状态：RECEIVED=已入账，PENDING=待确认';

-- ============================================
-- 21.5. Service Admin Tables (V27)
-- ============================================

-- 主脑管理员凭据表（加密存储外部服务的 admin 凭据）
CREATE TABLE IF NOT EXISTS service_admin_credential (
    id BIGSERIAL PRIMARY KEY,
    service_type VARCHAR(32) NOT NULL,          -- 服务类型: gitlab/openproject/jenkins/memos
    credential_key VARCHAR(128) NOT NULL,       -- 凭据标识（如 root_token、admin_api_key）
    credential_value TEXT NOT NULL,             -- 加密后的凭据值
    metadata JSONB,                             -- 额外元数据（如 scope、expiry）
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (service_type, credential_key)
);

CREATE INDEX IF NOT EXISTS idx_admin_credential_service ON service_admin_credential(service_type, active);

-- 员工外部账号映射表（记录员工在各外部服务中的账号信息）
CREATE TABLE IF NOT EXISTS employee_external_account (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(16) NOT NULL,         -- 员工编码（如 T01、H01）
    service_type VARCHAR(32) NOT NULL,          -- 服务类型: gitlab/openproject/jenkins
    external_user_id VARCHAR(128),              -- 外部服务中的用户ID
    external_username VARCHAR(128),             -- 外部服务中的用户名
    external_token TEXT,                        -- 加密后的员工访问令牌
    external_metadata JSONB,                    -- 额外信息（如 group_id、project_id、role）
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (employee_code, service_type)
);

CREATE INDEX IF NOT EXISTS idx_external_account_employee ON employee_external_account(employee_code, active);
CREATE INDEX IF NOT EXISTS idx_external_account_service ON employee_external_account(service_type, active);

-- 服务初始化状态表（记录 ServiceAdminBootstrap 的执行进度，支持幂等重试）
CREATE TABLE IF NOT EXISTS service_admin_bootstrap_state (
    id BIGSERIAL PRIMARY KEY,
    service_type VARCHAR(32) NOT NULL,          -- 服务类型
    step_name VARCHAR(128) NOT NULL,            -- 步骤名称（如 create_group、create_user）
    status VARCHAR(32) NOT NULL,                -- 状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
    detail TEXT,                                -- 详细信息或错误原因
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (service_type, step_name)
);

CREATE INDEX IF NOT EXISTS idx_bootstrap_state_status ON service_admin_bootstrap_state(status);

-- ============================================
-- 21.6. Plan Approval Requests Table (V27)
-- 计划审批请求持久化表 - P2-3 修复
-- ============================================

CREATE TABLE IF NOT EXISTS plan_approval_requests (
    request_id VARCHAR(50) PRIMARY KEY,
    submitter_neuron_id VARCHAR(255),
    plan_text TEXT,
    plan_type VARCHAR(32),
    status VARCHAR(16) DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE,
    deadline_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_plan_approval_status ON plan_approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_plan_approval_submitter ON plan_approval_requests(submitter_neuron_id);
CREATE INDEX IF NOT EXISTS idx_plan_approval_submitted_at ON plan_approval_requests(submitted_at);

COMMENT ON TABLE plan_approval_requests IS '计划审批请求持久化表';
COMMENT ON COLUMN plan_approval_requests.request_id IS '请求ID(主键)';
COMMENT ON COLUMN plan_approval_requests.submitter_neuron_id IS '提交者神经元ID';
COMMENT ON COLUMN plan_approval_requests.plan_text IS '计划文本内容';
COMMENT ON COLUMN plan_approval_requests.plan_type IS '计划类型(CODE_CHANGE/ARCHITECTURE_DECISION/DEPLOYMENT_PLAN/DATA_MIGRATION/SECURITY_CHANGE/GENERAL)';
COMMENT ON COLUMN plan_approval_requests.status IS '审批状态(PENDING/APPROVED/REJECTED/EXPIRED)';
COMMENT ON COLUMN plan_approval_requests.submitted_at IS '提交时间';
COMMENT ON COLUMN plan_approval_requests.deadline_ms IS '截止时间(毫秒)';

-- ============================================
-- 21.7. Approval Workflow Tables (V27)
-- 审批流程定义与实例持久化
-- ============================================

-- 审批流程定义表
CREATE TABLE IF NOT EXISTS approval_workflows (
    id BIGSERIAL PRIMARY KEY,
    workflow_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200),
    description TEXT,
    steps_json JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_workflow_id ON approval_workflows(workflow_id);
CREATE INDEX IF NOT EXISTS idx_approval_workflow_enabled ON approval_workflows(enabled);

-- 审批实例表
CREATE TABLE IF NOT EXISTS approval_instances (
    id BIGSERIAL PRIMARY KEY,
    instance_id VARCHAR(100) NOT NULL UNIQUE,
    workflow_id VARCHAR(100),
    business_type VARCHAR(64),
    business_id VARCHAR(200),
    title VARCHAR(500),
    description TEXT,
    status VARCHAR(30) NOT NULL,
    current_step INTEGER NOT NULL DEFAULT 0,
    submitter_id VARCHAR(200),
    records_json JSONB,
    context_json TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_approval_instance_id ON approval_instances(instance_id);
CREATE INDEX IF NOT EXISTS idx_approval_submitter ON approval_instances(submitter_id);
CREATE INDEX IF NOT EXISTS idx_approval_status ON approval_instances(status);
CREATE INDEX IF NOT EXISTS idx_approval_workflow_inst ON approval_instances(workflow_id);
CREATE INDEX IF NOT EXISTS idx_approval_business ON approval_instances(business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_approval_created_at ON approval_instances(created_at);

-- ============================================
-- 21.8. DAG Task Tables (P2-3)
-- 任务依赖图持久化
-- ============================================

-- DAG 任务表
CREATE TABLE IF NOT EXISTS dag_tasks (
    task_id VARCHAR(50) PRIMARY KEY,
    subject VARCHAR(255),
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    blocked_by TEXT,  -- JSON array of task IDs
    blocks TEXT,      -- JSON array of task IDs
    assignee VARCHAR(255),
    worktree VARCHAR(100),
    role VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dag_task_status ON dag_tasks(status);
CREATE INDEX IF NOT EXISTS idx_dag_task_assignee ON dag_tasks(assignee);
CREATE INDEX IF NOT EXISTS idx_dag_task_role ON dag_tasks(role);

COMMENT ON TABLE dag_tasks IS 'DAG 任务持久化表(P2-3)';
COMMENT ON COLUMN dag_tasks.task_id IS '任务ID(主键)';
COMMENT ON COLUMN dag_tasks.subject IS '任务主题';
COMMENT ON COLUMN dag_tasks.description IS '任务描述';
COMMENT ON COLUMN dag_tasks.status IS '任务状态(PENDING/IN_PROGRESS/COMPLETED/FAILED/CANCELLED)';
COMMENT ON COLUMN dag_tasks.blocked_by IS '阻塞依赖(JSON数组:被哪些任务阻塞)';
COMMENT ON COLUMN dag_tasks.blocks IS '阻塞下游(JSON数组:阻塞哪些任务)';
COMMENT ON COLUMN dag_tasks.assignee IS '分配给谁';
COMMENT ON COLUMN dag_tasks.worktree IS '工作树路径';
COMMENT ON COLUMN dag_tasks.role IS '角色标识';
COMMENT ON COLUMN dag_tasks.created_at IS '创建时间';
COMMENT ON COLUMN dag_tasks.updated_at IS '更新时间';

-- ============================================
-- 21.9. Visitors Table
-- 前台访客签到持久化
-- ============================================

CREATE TABLE IF NOT EXISTS visitors (
    id BIGSERIAL PRIMARY KEY,
    visitor_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    purpose VARCHAR(255),
    contact VARCHAR(100),
    host_employee_id VARCHAR(100),
    check_in_time TIMESTAMP NOT NULL,
    check_out_time TIMESTAMP,
    status VARCHAR(32) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_visitor_status ON visitors (status);
CREATE INDEX IF NOT EXISTS idx_visitor_checkin ON visitors (check_in_time);

-- ============================================
-- 22. Views (from V4)
-- ============================================

-- View: Department Summary with Brain Status
CREATE OR REPLACE VIEW v_department_summary AS
SELECT 
    d.department_id,
    d.code,
    d.name,
    d.target_brain AS brain,
    b.brain_id AS brain_id,
    CASE WHEN bb.id IS NOT NULL THEN TRUE ELSE FALSE END AS brain_running,
    CASE WHEN bb.model_configured THEN TRUE ELSE FALSE END AS model_configured,
    d.member_count,
    d.manager_id,
    d.manager_name
FROM enterprise_departments d
LEFT JOIN department_brain_bindings bb ON d.department_id = bb.department_id AND bb.binding_type = 'PRIMARY'
LEFT JOIN brain_model_assignments b ON d.target_brain = b.brain_name;

-- View: Active Employees with Department Info
CREATE OR REPLACE VIEW v_active_employees AS
SELECT 
    e.employee_id,
    e.name,
    e.department_id,
    d.code AS department_code,
    d.name AS department_name,
    CASE WHEN e.active THEN '在线' ELSE '离线' END AS status,
    'human' AS origin,
    e.position AS title,
    e.avatar_url,
    e.access_level
FROM enterprise_employees e
LEFT JOIN enterprise_departments d ON e.department_id = d.department_id
WHERE e.active = TRUE;

-- ============================================
-- 23. All Triggers
-- ============================================

-- Enterprise tables
CREATE TRIGGER update_departments_updated_at BEFORE UPDATE ON enterprise_departments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON enterprise_employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_enterprise_departments_updated_at BEFORE UPDATE ON enterprise_departments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_enterprise_employees_updated_at BEFORE UPDATE ON enterprise_employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dept_brain_bindings_updated_at BEFORE UPDATE ON department_brain_bindings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Knowledge tables
CREATE TRIGGER update_knowledge_updated_at BEFORE UPDATE ON knowledge_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Proactive tables
CREATE TRIGGER update_task_config_updated_at BEFORE UPDATE ON proactive_task_config
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Behavior tables
CREATE TRIGGER update_behavior_pattern_updated_at BEFORE UPDATE ON user_behavior_pattern
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Notification tables
CREATE TRIGGER update_notification_updated_at BEFORE UPDATE ON notification_queue
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Cross department
CREATE TRIGGER update_cross_case_updated_at BEFORE UPDATE ON cross_department_cases
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Capability
CREATE TRIGGER update_capability_updated_at BEFORE UPDATE ON mainbrain_capabilities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Credit accounts
CREATE TRIGGER update_credit_accounts_updated_at BEFORE UPDATE ON credit_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Employee personality
CREATE TRIGGER update_employee_personalities_updated_at BEFORE UPDATE ON employee_personalities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Employee capabilities
CREATE TRIGGER update_employee_capabilities_updated_at BEFORE UPDATE ON employee_capabilities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Employee skills
CREATE TRIGGER update_employee_skills_updated_at BEFORE UPDATE ON employee_skills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Speaker profiles
CREATE TRIGGER update_speaker_profiles_updated_at BEFORE UPDATE ON speaker_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- User profiles
CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Payout accounts
CREATE TRIGGER update_payout_accounts_updated_at BEFORE UPDATE ON payout_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Budget allocations
CREATE TRIGGER update_budget_allocations_updated_at BEFORE UPDATE ON budget_allocations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Skills (V17)
CREATE TRIGGER update_skills_updated_at BEFORE UPDATE ON skills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Fixed employee (V5)
CREATE TRIGGER update_fixed_employee_definition_updated_at BEFORE UPDATE ON fixed_employee_definition
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fixed_employee_profile_updated_at BEFORE UPDATE ON fixed_employee_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fixed_employee_persona_updated_at BEFORE UPDATE ON fixed_employee_persona
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Model pool (V2)
CREATE TRIGGER update_model_providers_updated_at BEFORE UPDATE ON model_providers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_brain_model_assignments_updated_at BEFORE UPDATE ON brain_model_assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Compensation (V19)
CREATE TRIGGER update_compensation_plans_updated_at BEFORE UPDATE ON compensation_plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compensation_accounts_updated_at BEFORE UPDATE ON compensation_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compensation_records_updated_at BEFORE UPDATE ON compensation_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Performance (V19)
CREATE TRIGGER update_performance_indicators_updated_at BEFORE UPDATE ON performance_indicators
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_performance_assessments_updated_at BEFORE UPDATE ON performance_assessments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_performance_trend_snapshots_updated_at BEFORE UPDATE ON performance_trend_snapshots
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Evolution (V19)
CREATE TRIGGER update_evolution_results_updated_at BEFORE UPDATE ON evolution_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_evolution_feedback_updated_at BEFORE UPDATE ON evolution_feedback
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_evolution_audit_logs_updated_at BEFORE UPDATE ON evolution_audit_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 24. Initial Data Inserts
-- ============================================

-- Insert default departments (合并 schema.sql + V4)
INSERT INTO enterprise_departments (department_id, name, code, target_brain, description)
VALUES 
    ('dept_tech', '技术部', 'TECH', 'TechBrain', '负责产品研发和技术支持'),
    ('dept_hr', '人力资源部', 'HR', 'HrBrain', '负责招聘、培训、绩效管理'),
    ('dept_finance', '财务部', 'FIN', 'FinanceBrain', '负责财务管理和报销审批'),
    ('dept_admin', '行政部', 'ADMIN', 'AdminBrain', '负责行政事务和文档管理'),
    ('dept_sales', '销售部', 'SALES', 'SalesBrain', '负责销售和市场拓展'),
    ('dept_cs', '客服部', 'CS', 'CsBrain', '负责客户服务和工单处理'),
    ('dept_legal', '法务部', 'LEGAL', 'LegalBrain', '负责合同审查和合规管理'),
    ('dept_ops', '运营部', 'OPS', 'OpsBrain', '负责运营策略和数据分析'),
    ('dept_main', '综合管理', 'MAIN', 'MainBrain', '负责跨部门协调和战略规划')
ON CONFLICT (department_id) DO NOTHING;

-- Insert default proactive tasks
INSERT INTO proactive_task_config (task_id, name, task_type, trigger_type, cron_expression, brain_domain, enabled)
VALUES 
    ('task_weekly_report', '周报自动生成', 'REPORT', 'SCHEDULED', '0 17 ? * FRI', 'TechBrain', TRUE),
    ('task_contract_reminder', '合同到期提醒', 'REMINDER', 'SCHEDULED', '0 9 * * ?', 'LegalBrain', TRUE),
    ('task_system_health', '系统健康检查', 'CHECK', 'SCHEDULED', '0 */5 * * *', 'TechBrain', TRUE)
ON CONFLICT (task_id) DO NOTHING;

-- Insert initial performance indicators
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

-- Insert default payout account
INSERT INTO payout_accounts (account_id, account_name, account_type, provider, account_identifier, owner_type, is_default, is_active, verified)
VALUES 
    ('payout_enterprise_default', '董事长默认收款账户', 'BANK_ACCOUNT', 'BANK', '', 'ENTERPRISE', TRUE, TRUE, FALSE)
ON CONFLICT (account_id) DO NOTHING;

-- Insert default model providers (V2) — 仅保留 ollama（本地部署，无需 API Key）
INSERT INTO model_providers (id, display_name, protocol, base_url, enabled, default_max_tokens)
VALUES 
    ('ollama', 'Ollama', 'OPENAI_COMPATIBLE', 'http://localhost:11434/v1', TRUE, 4096)
ON CONFLICT (id) DO NOTHING;

-- 默认模型不写入种子数据，由用户通过模型池管理界面自行配置添加
-- 模型池支持动态注册多个供应商（OpenAI、Ollama、DeepSeek 等）和多个模型

-- 大脑模型分配不写入种子数据，由 BrainModelAssigner 在运行时动态分配
-- 如需初始化默认分配，通过 API 调用 ModelPoolController.assignBrainModel() 完成

-- ============================================
-- 25. Fixed Digital Employee Seed Data
-- 合并 V5 插入数据 + V7 最终标识符格式
-- employee_id: employee://digital/{dept}/{role}/001
-- neuron_id: neuron://{dept}/{role}/001
-- channel: channel://{dept}/{role}
-- ============================================

-- ===== 25.1 enterprise_employees 基础记录（V7最终格式） =====
INSERT INTO enterprise_employees (
    employee_id, name, department_id, department_name, position,
    identity, access_level, avatar_url, join_date, active, tenant_id, sync_source,
    employee_type, origin
)
VALUES
    -- 技术部 (10人)
    ('employee://digital/tech/code-reviewer/001', '真砺', 'dept_tech', '技术部', '代码审查员', 'digital_employee', 'DEPARTMENT', '💻', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/architect/001', '真构', 'dept_tech', '技术部', '架构师', 'digital_employee', 'DEPARTMENT', '🧠', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/devops/001', '真捷', 'dept_tech', '技术部', 'DevOps工程师', 'digital_employee', 'DEPARTMENT', '🛠️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/ops/001', '真稳', 'dept_tech', '技术部', '运维工程师', 'digital_employee', 'DEPARTMENT', '🖥️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/model-admin/001', '真模', 'dept_tech', '技术部', 'AI模型管理员', 'digital_employee', 'DEPARTMENT', '🤖', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/state-admin/001', '真续', 'dept_tech', '技术部', '状态管理员', 'digital_employee', 'DEPARTMENT', '📡', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/security/001', '真盾', 'dept_tech', '技术部', '安全工程师', 'digital_employee', 'DEPARTMENT', '🛡️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/config-admin/001', '真策', 'dept_tech', '技术部', '配置管理员', 'digital_employee', 'DEPARTMENT', '⚙️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/frontend/001', '真绘', 'dept_tech', '技术部', '前端工程师', 'digital_employee', 'DEPARTMENT', '🎨', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/tech/backend/001', '真栈', 'dept_tech', '技术部', '后端工程师', 'digital_employee', 'DEPARTMENT', '🗄️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 财务部 (4人)
    ('employee://digital/finance/accountant/001', '真账', 'dept_finance', '财务部', '财务会计', 'digital_employee', 'DEPARTMENT', '💰', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/finance/auditor/001', '真审', 'dept_finance', '财务部', '报销审核员', 'digital_employee', 'DEPARTMENT', '🧾', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/finance/cost-accountant/001', '真算', 'dept_finance', '财务部', '成本核算员', 'digital_employee', 'DEPARTMENT', '📊', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/finance/budget-admin/001', '真预', 'dept_finance', '财务部', '预算管理员', 'digital_employee', 'DEPARTMENT', '🏦', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 运营部 (4人)
    ('employee://digital/ops/analyst/001', '真析', 'dept_ops', '运营部', '数据分析师', 'digital_employee', 'DEPARTMENT', '📈', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/ops/operator/001', '真营', 'dept_ops', '运营部', '运营专员', 'digital_employee', 'DEPARTMENT', '🚚', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/ops/scheduler/001', '真度', 'dept_ops', '运营部', '任务调度员', 'digital_employee', 'DEPARTMENT', '⏱️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/ops/process-admin/001', '真流', 'dept_ops', '运营部', '流程管理员', 'digital_employee', 'DEPARTMENT', '🧩', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 销售部 (3人)
    ('employee://digital/sales/representative/001', '真拓', 'dept_sales', '销售部', '销售代表', 'digital_employee', 'DEPARTMENT', '📣', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/sales/marketer/001', '真宣', 'dept_sales', '销售部', '市场专员', 'digital_employee', 'DEPARTMENT', '🎯', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/sales/channel-manager/001', '真联', 'dept_sales', '销售部', '渠道经理', 'digital_employee', 'DEPARTMENT', '🤝', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 人力资源 (2人)
    ('employee://digital/hr/recruiter/001', '真才', 'dept_hr', '人力资源部', '招聘专员', 'digital_employee', 'DEPARTMENT', '👥', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/hr/performance/001', '真绩', 'dept_hr', '人力资源部', '绩效管理员', 'digital_employee', 'DEPARTMENT', '📝', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 客服部 (2人)
    ('employee://digital/cs/agent/001', '真晴', 'dept_cs', '客服部', '客服专员', 'digital_employee', 'DEPARTMENT', '🎧', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/cs/ticket-handler/001', '真修', 'dept_cs', '客服部', '工单处理员', 'digital_employee', 'DEPARTMENT', '🧰', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 行政部 (3人)
    ('employee://digital/admin/assistant/001', '真序', 'dept_admin', '行政部', '行政助理', 'digital_employee', 'DEPARTMENT', '📋', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/admin/doc-manager/001', '真典', 'dept_admin', '行政部', '文档管理员', 'digital_employee', 'DEPARTMENT', '📚', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/admin/copywriter/001', '真笔', 'dept_admin', '行政部', '文案策划', 'digital_employee', 'DEPARTMENT', '✍️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 法务部 (2人)
    ('employee://digital/legal/contract-reviewer/001', '真律', 'dept_legal', '法务部', '合同审查员', 'digital_employee', 'DEPARTMENT', '⚖️', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/legal/compliance/001', '真规', 'dept_legal', '法务部', '合规专员', 'digital_employee', 'DEPARTMENT', '📜', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    -- 综合管理 (2人)
    ('employee://digital/main/coordinator/001', '真合', 'dept_main', '综合管理', '协调员', 'digital_employee', 'DEPARTMENT', '🎯', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED'),
    ('employee://digital/main/strategist/001', '真略', 'dept_main', '综合管理', '战略规划师', 'digital_employee', 'DEPARTMENT', '🧭', CURRENT_TIMESTAMP, TRUE, 'default', 'fixed_employee_seed', 'DIGITAL', 'FIXED')
ON CONFLICT (employee_id) DO UPDATE SET
    name = EXCLUDED.name,
    department_id = EXCLUDED.department_id,
    department_name = EXCLUDED.department_name,
    position = EXCLUDED.position,
    identity = EXCLUDED.identity,
    avatar_url = EXCLUDED.avatar_url,
    employee_type = EXCLUDED.employee_type,
    origin = EXCLUDED.origin,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- ===== 25.2 fixed_employee_definition（V5数据 + V7最终格式） =====
INSERT INTO fixed_employee_definition (code, employee_id, name_zh, name_en, title_zh, title_en, department_code, department_name, neuron_id, channel, roles, capabilities, tools, required_skills, personality)
VALUES
    ('T01','employee://digital/tech/code-reviewer/001','真砺','Zhen Li','代码审查员','Code Reviewer','tech','技术部','neuron://tech/code-reviewer/001','channel://tech/code-review','["审查","规范","安全"]','["代码质量","规范检查","安全审查"]','["gitlab","github"]','["code-review","security"]','{"conscientiousness":0.95,"openness":0.65,"risk_tolerance":0.2,"agreeableness":0.8}'),
    ('T02','employee://digital/tech/architect/001','真构','Zhen Gou','架构师','Architect','tech','技术部','neuron://tech/architect/001','channel://tech/architecture','["架构","设计","评审"]','["架构设计","技术选型","方案评审"]','["gitlab","jira"]','["architecture"]','{"conscientiousness":0.9,"openness":0.75}'),
    ('T03','employee://digital/tech/devops/001','真捷','Zhen Jie','DevOps工程师','DevOps Engineer','tech','技术部','neuron://tech/devops/001','channel://tech/devops','["部署","流水线","自动化"]','["CI/CD","容器部署","自动化运维"]','["jenkins","docker","gitlab","claude_cli"]','["devops"]','{"openness":0.72,"conscientiousness":0.82}'),
    ('T04','employee://digital/tech/ops/001','真稳','Zhen Wen','运维工程师','Operations Engineer','tech','技术部','neuron://tech/ops/001','channel://tech/ops','["监控","资源","调度"]','["监控告警","资源调度","心跳服务"]','["proactive_agent","docker"]','["ops"]','{"conscientiousness":0.88}'),
    ('T05','employee://digital/tech/model-admin/001','真模','Zhen Mo','AI模型管理员','AI Model Manager','tech','技术部','neuron://tech/model-admin/001','channel://tech/model','["模型","适配","监控"]','["模型注册","模型切换","性能监控"]','["huggingface"]','["model-management"]','{"conscientiousness":0.86,"openness":0.78}'),
    ('T06','employee://digital/tech/state-admin/001','真续','Zhen Xu','状态管理员','State Manager','tech','技术部','neuron://tech/state-admin/001','channel://tech/state','["会话","持久化","恢复"]','["状态维护","会话恢复","上下文管理"]','["knowledge_graph"]','["state"]','{"agreeableness":0.72,"conscientiousness":0.8}'),
    ('T07','employee://digital/tech/security/001','真盾','Zhen Dun','安全工程师','Security Engineer','tech','技术部','neuron://tech/security/001','channel://tech/security','["安全","隔离","审计"]','["沙箱执行","风险防护","安全审计"]','["self_improving"]','["security"]','{"conscientiousness":0.96}'),
    ('T08','employee://digital/tech/config-admin/001','真策','Zhen Ce','配置管理员','Configuration Manager','tech','技术部','neuron://tech/config-admin/001','channel://tech/config','["配置","版本","回滚"]','["版本控制","配置审计","回滚"]','["notion"]','["config"]','{"conscientiousness":0.84}'),
    ('T09','employee://digital/tech/frontend/001','真绘','Zhen Hui','前端工程师','Frontend Engineer','tech','技术部','neuron://tech/frontend/001','channel://tech/frontend','["UI","交互","体验"]','["前端交互","UI优化","体验设计"]','["gitlab","browser_automation"]','["frontend"]','{"extroversion":0.76,"openness":0.82}'),
    ('T10','employee://digital/tech/backend/001','真栈','Zhen Zhan','后端工程师','Backend Engineer','tech','技术部','neuron://tech/backend/001','channel://tech/backend','["API","数据库","性能"]','["API设计","数据库优化","服务性能"]','["gitlab","knowledge_graph"]','["backend"]','{"conscientiousness":0.82}'),
    ('F01','employee://digital/finance/accountant/001','真账','Zhen Zhang','财务会计','Accountant','finance','财务部','neuron://finance/accountant/001','channel://finance/accounting','["账务","报表","税务"]','["账务处理","财务报表","税务整理"]','["invoice_processing"]','["finance"]','{"conscientiousness":0.94}'),
    ('F02','employee://digital/finance/auditor/001','真审','Zhen Shen','报销审核员','Expense Auditor','finance','财务部','neuron://finance/auditor/001','channel://finance/audit','["审批","核验","合规"]','["报销审批","发票核验","合规检查"]','["invoice_processing","browser_automation"]','["audit"]','{"conscientiousness":0.93}'),
    ('F03','employee://digital/finance/cost-accountant/001','真算','Zhen Suan','成本核算员','Cost Analyst','finance','财务部','neuron://finance/cost-accountant/001','channel://finance/cost','["成本","核算","分析"]','["成本分析","项目核算","Token成本"]','["summarize"]','["costing"]','{"conscientiousness":0.86}'),
    ('F04','employee://digital/finance/budget-admin/001','真预','Zhen Yu','预算管理员','Budget Manager','finance','财务部','neuron://finance/budget-admin/001','channel://finance/budget','["预算","预警","报告"]','["预算管理","超支预警","预算报告"]','["budget_management"]','["budget"]','{"conscientiousness":0.9}'),
    ('O01','employee://digital/ops/analyst/001','真析','Zhen Xi','数据分析师','Data Analyst','ops','运营部','neuron://ops/analyst/001','channel://ops/analysis','["分析","报表","预测"]','["数据分析","趋势预测","报表生成"]','["summarize"]','["analytics"]','{"openness":0.72,"conscientiousness":0.82}'),
    ('O02','employee://digital/ops/operator/001','真营','Zhen Ying','运营专员','Operations Specialist','ops','运营部','neuron://ops/operator/001','channel://ops/daily','["运营","活动","用户"]','["日常运营","用户运营","活动执行"]','["notion","summarize"]','["operations"]','{"extroversion":0.76,"agreeableness":0.78}'),
    ('O03','employee://digital/ops/scheduler/001','真度','Zhen Du','任务调度员','Task Scheduler','ops','运营部','neuron://ops/scheduler/001','channel://ops/schedule','["调度","分配","冲突"]','["任务检出","任务分配","冲突避免"]','["proactive_agent"]','["scheduler"]','{"conscientiousness":0.88}'),
    ('O04','employee://digital/ops/process-admin/001','真流','Zhen Liu','流程管理员','Process Manager','ops','运营部','neuron://ops/process-admin/001','channel://ops/process','["流程","队列","优先级"]','["流程队列","优先级调度","流程维护"]','["proactive_agent"]','["process"]','{"conscientiousness":0.82}'),
    ('S01','employee://digital/sales/representative/001','真拓','Zhen Tuo','销售代表','Sales Representative','sales','销售部','neuron://sales/representative/001','channel://sales/reps','["开发","跟进","签约"]','["客户开发","客户跟进","签约推进"]','["notion","slack"]','["sales"]','{"extroversion":0.86,"agreeableness":0.76}'),
    ('S02','employee://digital/sales/marketer/001','真宣','Zhen Xuan','市场专员','Marketing Specialist','sales','销售部','neuron://sales/marketer/001','channel://sales/market','["调研","推广","品牌"]','["市场调研","品牌推广","内容传播"]','["summarize","searxng"]','["marketing"]','{"openness":0.84,"extroversion":0.78}'),
    ('S03','employee://digital/sales/channel-manager/001','真联','Zhen Lian','渠道经理','Channel Manager','sales','销售部','neuron://sales/channel-manager/001','channel://sales/channel','["渠道","集成","协同"]','["渠道管理","平台集成","伙伴协同"]','["github","browser_automation"]','["channel"]','{"extroversion":0.72,"conscientiousness":0.8}'),
    ('H01','employee://digital/hr/recruiter/001','真才','Zhen Cai','招聘专员','Recruiter','hr','人力资源部','neuron://hr/recruiter/001','channel://hr/recruit','["招聘","筛选","面试"]','["人才筛选","招聘管理","面试安排"]','["notion","slack"]','["recruiting"]','{"agreeableness":0.86,"extroversion":0.76}'),
    ('H02','employee://digital/hr/performance/001','真绩','Zhen Ji','绩效管理员','Performance Manager','hr','人力资源部','neuron://hr/performance/001','channel://hr/performance','["绩效","培训","发展"]','["绩效考核","培训发展","员工成长"]','["notion","summarize"]','["performance"]','{"conscientiousness":0.9}'),
    ('C01','employee://digital/cs/agent/001','真晴','Zhen Qing','客服专员','Support Specialist','cs','客服部','neuron://cs/agent/001','channel://cs/support','["咨询","解答","投诉"]','["客户咨询","问题解答","投诉处理"]','["notion","slack"]','["support"]','{"agreeableness":0.9,"extroversion":0.72}'),
    ('C02','employee://digital/cs/ticket-handler/001','真修','Zhen Xiu','工单处理员','Ticket Handler','cs','客服部','neuron://cs/ticket-handler/001','channel://cs/ticket','["工单","跟踪","升级"]','["工单跟踪","服务升级","问题闭环"]','["notion","jira"]','["ticket"]','{"conscientiousness":0.84}'),
    ('A01','employee://digital/admin/assistant/001','真序','Zhen Xu','行政助理','Administrative Assistant','admin','行政部','neuron://admin/assistant/001','channel://admin/affairs','["行政","日程","会议"]','["行政事务","日程安排","会议协调"]','["notion","slack"]','["admin"]','{"agreeableness":0.82}'),
    ('A02','employee://digital/admin/doc-manager/001','真典','Zhen Dian','文档管理员','Document Manager','admin','行政部','neuron://admin/doc-manager/001','channel://admin/docs','["文档","档案","归档"]','["文档管理","档案整理","知识归档"]','["office","notion"]','["docs"]','{"conscientiousness":0.86}'),
    ('A03','employee://digital/admin/copywriter/001','真笔','Zhen Bi','文案策划','Copy Planner','admin','行政部','neuron://admin/copywriter/001','channel://admin/content','["文案","内容","品牌"]','["文案创作","品牌传播","内容策划"]','["office","summarize"]','["copy"]','{"openness":0.82,"extroversion":0.74}'),
    ('L01','employee://digital/legal/contract-reviewer/001','真律','Zhen Lv','合同审查员','Contract Reviewer','legal','法务部','neuron://legal/contract-reviewer/001','channel://legal/contract','["合同","风险","条款"]','["合同审查","风险识别","条款建议"]','["office","summarize"]','["legal"]','{"conscientiousness":0.96}'),
    ('L02','employee://digital/legal/compliance/001','真规','Zhen Gui','合规专员','Compliance Specialist','legal','法务部','neuron://legal/compliance/001','channel://legal/compliance','["合规","政策","预警"]','["合规检查","政策解读","风险预警"]','["summarize"]','["compliance"]','{"conscientiousness":0.92}'),
    ('M01','employee://digital/main/coordinator/001','真合','Zhen He','协调员','Coordinator','main','综合管理','neuron://main/coordinator/001','channel://main/coord','["协调","调配","解决"]','["跨部门协调","资源调配","问题解决"]','["slack","proactive_agent"]','["coordination"]','{"agreeableness":0.86,"extroversion":0.78}'),
    ('M02','employee://digital/main/strategist/001','真略','Zhen Lue','战略规划师','Strategy Planner','main','综合管理','neuron://main/strategist/001','channel://main/strategy','["战略","目标","决策"]','["战略规划","目标管理","决策支持"]','["summarize"]','["strategy"]','{"openness":0.84,"conscientiousness":0.86}')
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    name_zh = EXCLUDED.name_zh,
    name_en = EXCLUDED.name_en,
    title_zh = EXCLUDED.title_zh,
    title_en = EXCLUDED.title_en,
    department_code = EXCLUDED.department_code,
    department_name = EXCLUDED.department_name,
    neuron_id = EXCLUDED.neuron_id,
    channel = EXCLUDED.channel,
    roles = EXCLUDED.roles,
    capabilities = EXCLUDED.capabilities,
    tools = EXCLUDED.tools,
    required_skills = EXCLUDED.required_skills,
    personality = EXCLUDED.personality,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- ===== 25.3 fixed_employee_profile（V5） =====
INSERT INTO fixed_employee_profile (code, employee_id, display_name_zh, display_name_en, summary_zh, summary_en, traits, tool_tags, status, last_active_at)
SELECT code, employee_id, name_zh, name_en, title_zh || '，长期固定数字员工画像。', title_en || ', persistent fixed digital employee profile.', roles, tools, 'active', CURRENT_TIMESTAMP
FROM fixed_employee_definition
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    display_name_zh = EXCLUDED.display_name_zh,
    display_name_en = EXCLUDED.display_name_en,
    summary_zh = EXCLUDED.summary_zh,
    summary_en = EXCLUDED.summary_en,
    traits = EXCLUDED.traits,
    tool_tags = EXCLUDED.tool_tags,
    status = 'active',
    last_active_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

-- ===== 25.4 fixed_employee_persona（V5） =====
INSERT INTO fixed_employee_persona (code, employee_id, icon, hair, glasses, badge_style, stance, outfit, accent_color, face, body_shape, clothing_variant, accessory_variant, badge_label)
VALUES
    ('T01','employee://digital/tech/code-reviewer/001','💻','short',TRUE,'shield','strict','tech','#34d399','serious','default','engineer','glasses','Code Review'),
    ('T02','employee://digital/tech/architect/001','🧠','side',TRUE,'classic','focused','tech','#22d3ee','neutral','slim','architect','glasses','Architecture'),
    ('T03','employee://digital/tech/devops/001','🛠️','cap',FALSE,'compact','busy','tech','#10b981','neutral','broad','devops','cap','DevOps'),
    ('T04','employee://digital/tech/ops/001','🖥️','short',FALSE,'classic','busy','tech','#14b8a6','neutral','broad','ops','headset','Ops'),
    ('T05','employee://digital/tech/model-admin/001','🤖','clean',TRUE,'text','focused','tech','#8b5cf6','neutral','slim','model','glasses','Model Core'),
    ('T06','employee://digital/tech/state-admin/001','📡','clean',FALSE,'round','calm','tech','#38bdf8','neutral','default','state','badge','State'),
    ('T07','employee://digital/tech/security/001','🛡️','short',TRUE,'shield','strict','tech','#0ea5e9','serious','broad','security','glasses','Security'),
    ('T08','employee://digital/tech/config-admin/001','⚙️','clean',FALSE,'compact','focused','tech','#60a5fa','neutral','default','config','badge','Config'),
    ('T09','employee://digital/tech/frontend/001','🎨','side',FALSE,'classic','friendly','tech','#a78bfa','smile','compact','frontend','badge','Frontend'),
    ('T10','employee://digital/tech/backend/001','🗄️','short',TRUE,'classic','focused','tech','#22c55e','neutral','default','backend','glasses','Backend'),
    ('F01','employee://digital/finance/accountant/001','💰','bun',TRUE,'classic','strict','finance','#60a5fa','serious','slim','formal','glasses','Finance'),
    ('F02','employee://digital/finance/auditor/001','🧾','clean',TRUE,'shield','focused','finance','#38bdf8','serious','slim','audit','glasses','Audit'),
    ('F03','employee://digital/finance/cost-accountant/001','📊','short',FALSE,'text','busy','finance','#2563eb','neutral','default','analyst','badge','Costing'),
    ('F04','employee://digital/finance/budget-admin/001','🏦','side',TRUE,'compact','focused','finance','#1d4ed8','neutral','slim','budget','glasses','Budget'),
    ('O01','employee://digital/ops/analyst/001','📈','short',TRUE,'classic','focused','ops','#f59e0b','neutral','default','analyst','glasses','Analytics'),
    ('O02','employee://digital/ops/operator/001','🚚','cap',FALSE,'compact','busy','ops','#fb923c','smile','broad','field','cap','Operations'),
    ('O03','employee://digital/ops/scheduler/001','⏱️','clean',TRUE,'round','focused','ops','#f97316','serious','default','scheduler','glasses','Scheduler'),
    ('O04','employee://digital/ops/process-admin/001','🧩','short',FALSE,'text','calm','ops','#ea580c','neutral','default','process','badge','Process'),
    ('S01','employee://digital/sales/representative/001','📣','side',FALSE,'classic','friendly','sales','#fb7185','smile','compact','sales','badge','Sales'),
    ('S02','employee://digital/sales/marketer/001','🎯','curly',FALSE,'compact','busy','sales','#f43f5e','smile','compact','marketing','badge','Marketing'),
    ('S03','employee://digital/sales/channel-manager/001','🤝','short',TRUE,'shield','focused','sales','#be123c','neutral','default','channel','glasses','Channel'),
    ('H01','employee://digital/hr/recruiter/001','👥','bun',FALSE,'classic','friendly','hr','#f472b6','smile','compact','hr','badge','Hiring'),
    ('H02','employee://digital/hr/performance/001','📝','clean',TRUE,'text','strict','hr','#ec4899','serious','slim','performance','glasses','HR'),
    ('C01','employee://digital/cs/agent/001','🎧','short',FALSE,'round','friendly','support','#a78bfa','smile','compact','support','headset','Support'),
    ('C02','employee://digital/cs/ticket-handler/001','🧰','cap',TRUE,'compact','busy','support','#8b5cf6','neutral','broad','ticket','cap','Tickets'),
    ('A01','employee://digital/admin/assistant/001','📋','bun',FALSE,'classic','calm','admin','#c084fc','smile','compact','admin','badge','Admin'),
    ('A02','employee://digital/admin/doc-manager/001','📚','clean',TRUE,'text','focused','admin','#a855f7','neutral','slim','docs','glasses','Docs'),
    ('A03','employee://digital/admin/copywriter/001','✍️','side',FALSE,'compact','friendly','admin','#d946ef','smile','compact','copy','badge','Copy'),
    ('L01','employee://digital/legal/contract-reviewer/001','⚖️','clean',TRUE,'shield','strict','legal','#f87171','serious','slim','legal','glasses','Legal'),
    ('L02','employee://digital/legal/compliance/001','📜','bun',TRUE,'classic','focused','legal','#ef4444','neutral','slim','compliance','glasses','Compliance'),
    ('M01','employee://digital/main/coordinator/001','🎯','short',FALSE,'round','friendly','default','#60a5fa','smile','default','coord','badge','Coord'),
    ('M02','employee://digital/main/strategist/001','🧭','side',TRUE,'shield','focused','default','#38bdf8','neutral','slim','strategy','glasses','Strategy')
ON CONFLICT (code) DO UPDATE SET
    employee_id = EXCLUDED.employee_id,
    icon = EXCLUDED.icon,
    hair = EXCLUDED.hair,
    glasses = EXCLUDED.glasses,
    badge_style = EXCLUDED.badge_style,
    stance = EXCLUDED.stance,
    outfit = EXCLUDED.outfit,
    accent_color = EXCLUDED.accent_color,
    face = EXCLUDED.face,
    body_shape = EXCLUDED.body_shape,
    clothing_variant = EXCLUDED.clothing_variant,
    accessory_variant = EXCLUDED.accessory_variant,
    badge_label = EXCLUDED.badge_label,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================
-- 26. Messages Table (MessageController)
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    recipient_id VARCHAR(100) NOT NULL,
    sender_id VARCHAR(100),
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_msg_recipient ON messages (recipient_id);
CREATE INDEX IF NOT EXISTS idx_msg_recipient_unread ON messages (recipient_id, read_at);
CREATE INDEX IF NOT EXISTS idx_msg_created_at ON messages (created_at);