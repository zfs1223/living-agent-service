-- Living Agent Service 数据库初始化脚本
-- PostgreSQL 数据库结构

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================
-- 企业权限管理表
-- ============================================

-- 员工表 (支持数字员工和人类员工继承)
CREATE TABLE IF NOT EXISTS employees (
    id VARCHAR(36) PRIMARY KEY,
    employee_type VARCHAR(20) NOT NULL DEFAULT 'DIGITAL',
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    position VARCHAR(100),
    hire_date DATE,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- DigitalEmployeeEntity 字段
    model VARCHAR(100),
    brain_domain VARCHAR(50),
    max_concurrent_tasks INTEGER,
    skills TEXT,
    capabilities TEXT,
    
    -- HumanEmployeeEntity 字段
    email VARCHAR(100),
    phone VARCHAR(20),
    role VARCHAR(50)
);

CREATE INDEX idx_employees_department ON employees(department);
CREATE INDEX idx_employees_status ON employees(status);
CREATE INDEX idx_employees_type ON employees(employee_type);

-- 访问审计日志表
CREATE TABLE IF NOT EXISTS access_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    log_id VARCHAR(50) UNIQUE NOT NULL,
    employee_id VARCHAR(50),
    employee_name VARCHAR(100),
    resource VARCHAR(200),
    action VARCHAR(50),
    granted BOOLEAN,
    reason TEXT,
    session_id VARCHAR(100),
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_employee ON access_audit_logs(employee_id);
CREATE INDEX idx_audit_logs_created_at ON access_audit_logs(created_at);

-- 人员变动记录表
CREATE TABLE IF NOT EXISTS employee_changes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    change_id VARCHAR(50) UNIQUE NOT NULL,
    employee_id VARCHAR(50),
    employee_name VARCHAR(100),
    change_type VARCHAR(30),
    detected_from TEXT,
    details TEXT,
    original_value VARCHAR(200),
    new_value VARCHAR(200),
    confidence DECIMAL(3,2) DEFAULT 0.5,
    status VARCHAR(20) DEFAULT 'PENDING',
    confirmed_by VARCHAR(50),
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP
);

CREATE INDEX idx_employee_changes_employee ON employee_changes(employee_id);
CREATE INDEX idx_employee_changes_status ON employee_changes(status);

-- ============================================
-- 知识库表
-- ============================================

-- 知识条目表
CREATE TABLE IF NOT EXISTS knowledge_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key VARCHAR(200) UNIQUE NOT NULL,
    content TEXT,
    knowledge_type VARCHAR(30) DEFAULT 'FACT',
    importance VARCHAR(20) DEFAULT 'MEDIUM',
    validity VARCHAR(20) DEFAULT 'PERMANENT',
    brain_domain VARCHAR(50),
    neuron_id VARCHAR(100),
    confidence DECIMAL(3,2) DEFAULT 0.5,
    relevance_score DECIMAL(3,2) DEFAULT 0.5,
    access_count INTEGER DEFAULT 0,
    is_verified BOOLEAN DEFAULT false,
    expires_at TIMESTAMP,
    tags JSONB DEFAULT '{}',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_domain ON knowledge_entries(brain_domain);
CREATE INDEX idx_knowledge_type ON knowledge_entries(knowledge_type);
CREATE INDEX idx_knowledge_importance ON knowledge_entries(importance);

-- 经验记录表
CREATE TABLE IF NOT EXISTS experiences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    context VARCHAR(200),
    content TEXT,
    outcome TEXT,
    success_rate DECIMAL(3,2),
    brain_domain VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_experiences_domain ON experiences(brain_domain);

-- 最佳实践表
CREATE TABLE IF NOT EXISTS best_practices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    steps JSONB DEFAULT '[]',
    prerequisites JSONB DEFAULT '[]',
    success_rate DECIMAL(3,2) DEFAULT 0.5,
    domain VARCHAR(50),
    tags JSONB DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_best_practices_domain ON best_practices(domain);

-- ============================================
-- 进化系统表
-- ============================================

-- 进化事件表
CREATE TABLE IF NOT EXISTS evolution_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id VARCHAR(50) UNIQUE NOT NULL,
    signal_type VARCHAR(30),
    signal_category VARCHAR(20),
    content TEXT,
    source VARCHAR(100),
    brain_domain VARCHAR(50),
    confidence DECIMAL(3,2),
    strategy VARCHAR(20),
    priority VARCHAR(20),
    outcome TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evolution_events_domain ON evolution_events(brain_domain);
CREATE INDEX idx_evolution_events_type ON evolution_events(signal_type);

-- 大脑人格记录表
CREATE TABLE IF NOT EXISTS brain_personalities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brain_name VARCHAR(50) UNIQUE NOT NULL,
    rigor DECIMAL(3,2) DEFAULT 0.5,
    creativity DECIMAL(3,2) DEFAULT 0.5,
    risk_tolerance DECIMAL(3,2) DEFAULT 0.5,
    obedience DECIMAL(3,2) DEFAULT 0.5,
    mutation_count INTEGER DEFAULT 0,
    last_mutation_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化默认人格
INSERT INTO brain_personalities (brain_name, rigor, creativity, risk_tolerance, obedience)
VALUES 
    ('MainBrain', 0.7, 0.5, 0.4, 0.85),
    ('TechBrain', 0.8, 0.6, 0.5, 0.7),
    ('HrBrain', 0.9, 0.3, 0.3, 0.9),
    ('FinanceBrain', 0.95, 0.2, 0.2, 0.95),
    ('SalesBrain', 0.5, 0.7, 0.6, 0.6),
    ('CsBrain', 0.6, 0.5, 0.4, 0.8),
    ('AdminBrain', 0.7, 0.4, 0.3, 0.8),
    ('LegalBrain', 0.95, 0.2, 0.2, 0.95),
    ('OpsBrain', 0.6, 0.6, 0.5, 0.7)
ON CONFLICT (brain_name) DO NOTHING;

-- ============================================
-- 健康监控表
-- ============================================

-- 健康检查记录表
CREATE TABLE IF NOT EXISTS health_checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    status VARCHAR(20),
    components JSONB DEFAULT '{}',
    issues JSONB DEFAULT '[]',
    metrics JSONB DEFAULT '{}',
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_health_checks_status ON health_checks(status);
CREATE INDEX idx_health_checks_time ON health_checks(checked_at);

-- 健康告警表
CREATE TABLE IF NOT EXISTS health_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id VARCHAR(50) UNIQUE NOT NULL,
    component VARCHAR(100),
    severity VARCHAR(20),
    message TEXT,
    acknowledged BOOLEAN DEFAULT false,
    acknowledged_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP
);

CREATE INDEX idx_health_alerts_component ON health_alerts(component);
CREATE INDEX idx_health_alerts_acknowledged ON health_alerts(acknowledged);

-- ============================================
-- 会话和对话表
-- ============================================

-- 会话表
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(50),
    department VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP
);

CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);

-- 对话消息表
CREATE TABLE IF NOT EXISTS conversation_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(100),
    role VARCHAR(20),
    content TEXT,
    neuron_id VARCHAR(100),
    brain_id VARCHAR(100),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversation_session ON conversation_messages(session_id);
CREATE INDEX idx_conversation_time ON conversation_messages(created_at);

-- ============================================
-- 触发器：自动更新 updated_at
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_updated_at BEFORE UPDATE ON knowledge_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_best_practices_updated_at BEFORE UPDATE ON best_practices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_brain_personalities_updated_at BEFORE UPDATE ON brain_personalities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sessions_updated_at BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 视图：常用查询
-- ============================================

-- 活跃员工视图
CREATE OR REPLACE VIEW active_employees AS
SELECT * FROM employees WHERE is_active = true AND identity IN ('INTERNAL_ACTIVE', 'INTERNAL_PROBATION');

-- 知识统计视图
CREATE OR REPLACE VIEW knowledge_stats AS
SELECT 
    brain_domain,
    knowledge_type,
    COUNT(*) as total_count,
    AVG(confidence) as avg_confidence,
    AVG(relevance_score) as avg_relevance
FROM knowledge_entries
GROUP BY brain_domain, knowledge_type;

-- 进化统计视图
CREATE OR REPLACE VIEW evolution_stats AS
SELECT 
    brain_domain,
    signal_type,
    COUNT(*) as event_count,
    AVG(confidence) as avg_confidence
FROM evolution_events
GROUP BY brain_domain, signal_type;
