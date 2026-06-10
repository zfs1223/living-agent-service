-- Organization Model Migration
-- Adds tables for enterprise departments and employees organization

-- Enterprise Departments Table
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

CREATE INDEX idx_departments_code ON enterprise_departments(code);
CREATE INDEX idx_departments_parent ON enterprise_departments(parent_id);
CREATE INDEX idx_departments_brain ON enterprise_departments(target_brain);

-- Enterprise Employees Table
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

CREATE INDEX idx_enterprise_employees_department ON enterprise_employees(department_id);
CREATE INDEX idx_enterprise_employees_phone ON enterprise_employees(phone);
CREATE INDEX idx_enterprise_employees_email ON enterprise_employees(LOWER(email));
CREATE INDEX idx_enterprise_employees_oauth ON enterprise_employees(oauth_provider, oauth_user_id);
CREATE INDEX idx_enterprise_employees_voice_print ON enterprise_employees(voice_print_id);
CREATE INDEX idx_enterprise_employees_identity ON enterprise_employees(identity);
CREATE INDEX idx_enterprise_employees_active ON enterprise_employees(active);
CREATE INDEX idx_enterprise_employees_tenant ON enterprise_employees(tenant_id);

-- Triggers for auto-update
CREATE TRIGGER update_enterprise_departments_updated_at BEFORE UPDATE ON enterprise_departments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_enterprise_employees_updated_at BEFORE UPDATE ON enterprise_employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Department Brain Binding Table
CREATE TABLE IF NOT EXISTS department_brain_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id VARCHAR(36) NOT NULL REFERENCES enterprise_departments(department_id) ON DELETE CASCADE,
    brain_id VARCHAR(100) NOT NULL,
    brain_name VARCHAR(100),
    binding_type VARCHAR(20) DEFAULT 'PRIMARY',
    model_id UUID REFERENCES llm_models(id) ON DELETE SET NULL,
    model_configured BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_department_brain UNIQUE (department_id, brain_id)
);

CREATE INDEX idx_dept_brain_bindings_dept ON department_brain_bindings(department_id);
CREATE INDEX idx_dept_brain_bindings_brain ON department_brain_bindings(brain_id);

-- Trigger for auto-update
CREATE TRIGGER update_dept_brain_bindings_updated_at BEFORE UPDATE ON department_brain_bindings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Initial Department Data
INSERT INTO enterprise_departments (department_id, name, code, target_brain, member_count)
VALUES 
    ('dept_tech', '技术部', 'tech', 'TechBrain', 0),
    ('dept_hr', '人力资源部', 'hr', 'HrBrain', 0),
    ('dept_finance', '财务部', 'finance', 'FinanceBrain', 0),
    ('dept_sales', '销售部', 'sales', 'SalesBrain', 0),
    ('dept_admin', '行政部', 'admin', 'AdminBrain', 0),
    ('dept_cs', '客服部', 'cs', 'CsBrain', 0),
    ('dept_legal', '法务部', 'legal', 'LegalBrain', 0),
    ('dept_ops', '运营部', 'ops', 'OpsBrain', 0),
    ('dept_main', '综合管理', 'main', 'MainBrain', 0)
ON CONFLICT (department_id) DO NOTHING;

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
