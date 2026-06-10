-- V1: 初始化基础表
-- 包含 V10/V11/V15 引用的所有基础表，确保从空库执行 Flyway 不会失败

-- =============================================
-- 辅助函数：自动更新 updated_at 字段
-- V2/V4 等后续迁移的 TRIGGER 依赖此函数
-- =============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================
-- employees 表 (EmployeeEntity + DigitalEmployeeEntity + HumanEmployeeEntity)
-- SINGLE_TABLE 继承策略，employee_type 为鉴别列
-- V11 引用: ALTER TABLE employees ADD COLUMN department_id
-- =============================================
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

-- =============================================
-- tasks 表 (TaskEntity)
-- V10 引用: ALTER TABLE tasks ALTER COLUMN execution_id/task_key
-- =============================================
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
    blocking_issues TEXT
);

CREATE INDEX IF NOT EXISTS idx_task_task_id ON tasks(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assigned_status ON tasks(assigned_to, status);
CREATE INDEX IF NOT EXISTS idx_task_user_id ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_task_task_key ON tasks(task_key);
CREATE INDEX IF NOT EXISTS idx_task_execution_id ON tasks(execution_id);
CREATE INDEX IF NOT EXISTS idx_task_project_id ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_task_department_status ON tasks(department_code, status);

-- =============================================
-- projects 表 (ProjectEntity)
-- V10 引用: ALTER TABLE projects ALTER COLUMN source_task_key
-- =============================================
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

-- =============================================
-- department_conversations 表 (DepartmentConversationEntity)
-- V10 引用: ALTER TABLE department_conversations ALTER COLUMN active_execution_id/active_task_key/conversation_key
-- =============================================
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

-- =============================================
-- department_chat_messages 表 (DepartmentChatMessageEntity)
-- V10 引用: ALTER TABLE department_chat_messages ALTER COLUMN execution_id/task_key
-- =============================================
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

-- =============================================
-- artifact_records 表 (ArtifactRecordEntity)
-- V10 引用: ALTER TABLE artifact_records ALTER COLUMN execution_id
-- V8 原始创建，此处提前定义以确保 V10 可执行
-- =============================================
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
    project_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_artifact_execution_id ON artifact_records(execution_id);
CREATE INDEX IF NOT EXISTS idx_artifact_department ON artifact_records(department);
CREATE INDEX IF NOT EXISTS idx_artifact_employee_code ON artifact_records(owner_employee_code);
CREATE INDEX IF NOT EXISTS idx_artifact_type ON artifact_records(type);
CREATE INDEX IF NOT EXISTS idx_artifact_created_at ON artifact_records(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_department_type ON artifact_records(department, type);
CREATE INDEX IF NOT EXISTS idx_artifact_task_id ON artifact_records(task_id);
CREATE INDEX IF NOT EXISTS idx_artifact_project_id ON artifact_records(project_id);

-- =============================================
-- enterprise_departments 表
-- V4 原始创建，enterprise_employees 的 department_id 外键引用此表
-- 必须在 enterprise_employees 之前创建
-- =============================================
CREATE TABLE IF NOT EXISTS enterprise_departments (
    department_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    parent_id VARCHAR(36) REFERENCES enterprise_departments(department_id) ON DELETE SET NULL,
    manager_id VARCHAR(36),
    manager_name VARCHAR(100),
    target_brain VARCHAR(50),
    member_count INTEGER DEFAULT 0,
    description TEXT,
    sync_source VARCHAR(50),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_departments_code ON enterprise_departments(code);
CREATE INDEX IF NOT EXISTS idx_departments_parent ON enterprise_departments(parent_id);
CREATE INDEX IF NOT EXISTS idx_departments_brain ON enterprise_departments(target_brain);

-- =============================================
-- enterprise_employees 表
-- V4 原始创建，V11 引用: UPDATE enterprise_employees SET department_id
-- 此处提前定义以确保 V11 可执行
-- =============================================
CREATE TABLE IF NOT EXISTS enterprise_employees (
    employee_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    department_id VARCHAR(36) REFERENCES enterprise_departments(department_id) ON DELETE SET NULL,
    department_name VARCHAR(100),
    position VARCHAR(100),
    identity VARCHAR(50),
    access_level VARCHAR(20),
    is_founder BOOLEAN DEFAULT FALSE,
    voice_print_id VARCHAR(100),
    oauth_provider VARCHAR(50),
    oauth_user_id VARCHAR(100),
    avatar_url VARCHAR(500),
    join_date TIMESTAMP WITH TIME ZONE,
    leave_date TIMESTAMP WITH TIME ZONE,
    active BOOLEAN DEFAULT TRUE,
    tenant_id VARCHAR(64),
    sync_source VARCHAR(50),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_enterprise_employees_department ON enterprise_employees(department_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_phone ON enterprise_employees(phone);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_email ON enterprise_employees(LOWER(email));
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_oauth ON enterprise_employees(oauth_provider, oauth_user_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_voice_print ON enterprise_employees(voice_print_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_identity ON enterprise_employees(identity);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_active ON enterprise_employees(active);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_tenant ON enterprise_employees(tenant_id);

-- =============================================
-- knowledge_entries 表 (KnowledgeEntryEntity)
-- =============================================
CREATE TABLE IF NOT EXISTS knowledge_entries (
    id UUID NOT NULL PRIMARY KEY,
    entry_id VARCHAR(64) UNIQUE,
    key VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    knowledge_type VARCHAR(32),
    importance VARCHAR(16),
    validity VARCHAR(16),
    scope VARCHAR(16),
    scope_identifier VARCHAR(128),
    brain_domain VARCHAR(50),
    neuron_id VARCHAR(128),
    owner_id VARCHAR(64),
    department_id VARCHAR(64),
    vector_id VARCHAR(64),
    confidence DOUBLE PRECISION,
    relevance DOUBLE PRECISION,
    access_count INTEGER,
    verified BOOLEAN,
    source VARCHAR(64),
    promoted_from VARCHAR(256),
    status VARCHAR(20),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_entry_id ON knowledge_entries(entry_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_scope ON knowledge_entries(scope, scope_identifier);
CREATE INDEX IF NOT EXISTS idx_knowledge_brain ON knowledge_entries(brain_domain);
CREATE INDEX IF NOT EXISTS idx_knowledge_type ON knowledge_entries(knowledge_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_expires ON knowledge_entries(expires_at);

-- =============================================
-- knowledge_tags 表 (KnowledgeEntryEntity @ElementCollection)
-- =============================================
CREATE TABLE IF NOT EXISTS knowledge_tags (
    knowledge_id UUID NOT NULL REFERENCES knowledge_entries(id) ON DELETE CASCADE,
    tag_name VARCHAR(255) NOT NULL,
    tag_value VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_tags_knowledge_id ON knowledge_tags(knowledge_id);

-- =============================================
-- knowledge_metadata 表 (KnowledgeEntryEntity @ElementCollection)
-- =============================================
CREATE TABLE IF NOT EXISTS knowledge_metadata (
    knowledge_id UUID NOT NULL REFERENCES knowledge_entries(id) ON DELETE CASCADE,
    meta_key VARCHAR(255) NOT NULL,
    meta_value VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_metadata_knowledge_id ON knowledge_metadata(knowledge_id);

-- =============================================
-- skills 表 (SkillImpl / Skill 接口)
-- V15 引用: ALTER TABLE skills ADD COLUMN scope/owner_id/department_id
-- 必须在 V15 之前创建
-- =============================================
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

-- 自动更新 updated_at 的触发器
CREATE TRIGGER update_skills_updated_at BEFORE UPDATE ON skills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
