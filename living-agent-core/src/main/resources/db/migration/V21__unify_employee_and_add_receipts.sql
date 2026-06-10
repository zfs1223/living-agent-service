-- V21: 统一员工体系 - 在 enterprise_employees 表中添加从 employees 表合并的字段
-- 同时创建 employee_execution_receipts 表

-- ========== enterprise_employees 新增字段 ==========

ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS employee_type VARCHAR(31);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS hire_date DATE;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS model VARCHAR(255);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS brain_domain VARCHAR(255);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS max_concurrent_tasks INTEGER;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS skills TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS origin VARCHAR(20);

-- 索引
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_employee_type ON enterprise_employees(employee_type);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_status ON enterprise_employees(status);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_brain_domain ON enterprise_employees(brain_domain);

-- ========== employee_execution_receipts 表 ==========

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_receipt_receipt_id ON employee_execution_receipts(receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_execution_id ON employee_execution_receipts(execution_id);
CREATE INDEX IF NOT EXISTS idx_receipt_dispatch_id ON employee_execution_receipts(dispatch_id);
CREATE INDEX IF NOT EXISTS idx_receipt_employee_code ON employee_execution_receipts(employee_code);
CREATE INDEX IF NOT EXISTS idx_receipt_status ON employee_execution_receipts(status);
CREATE INDEX IF NOT EXISTS idx_receipt_created_at ON employee_execution_receipts(created_at);

-- ========== 数据迁移：从 employees 表到 enterprise_employees 表 ==========

-- 迁移数字员工
INSERT INTO enterprise_employees (
    employee_id, name, phone, email, department_id, department_name, position,
    identity, access_level, is_founder, active,
    employee_type, status, hire_date, metadata,
    model, brain_domain, max_concurrent_tasks, skills, capabilities, origin,
    created_at, updated_at
)
SELECT
    e.id,
    e.name,
    e.phone,
    e.email,
    e.department_id,
    e.department,
    e.position,
    CASE WHEN e.employee_type = 'DIGITAL' THEN 'digital_employee' ELSE 'human_employee' END,
    CASE
        WHEN e.employee_type = 'DIGITAL' THEN 'DEPARTMENT'
        ELSE 'CHAT_ONLY'
    END,
    FALSE,
    CASE WHEN e.status IN ('ACTIVE', 'ONLINE') THEN TRUE ELSE FALSE END,
    e.employee_type,
    e.status,
    e.hire_date,
    e.metadata,
    d.model,
    d.brain_domain,
    d.max_concurrent_tasks,
    d.skills,
    d.capabilities,
    d.origin,
    COALESCE(e.created_at, CURRENT_TIMESTAMP),
    COALESCE(e.updated_at, CURRENT_TIMESTAMP)
FROM employees e
LEFT JOIN employees d ON e.id = d.id
WHERE e.employee_type = 'DIGITAL'
  AND NOT EXISTS (
    SELECT 1 FROM enterprise_employees ee WHERE ee.employee_id = e.id
  );

-- 迁移人类员工
INSERT INTO enterprise_employees (
    employee_id, name, phone, email, department_id, department_name, position,
    identity, access_level, is_founder, active,
    employee_type, status, hire_date, metadata,
    created_at, updated_at
)
SELECT
    e.id,
    e.name,
    h.phone,
    h.email,
    e.department_id,
    e.department,
    e.position,
    'human_employee',
    CASE
        WHEN h.role IS NOT NULL AND h.role = 'CHAIRMAN' THEN 'FULL'
        WHEN h.role IS NOT NULL THEN 'DEPARTMENT'
        ELSE 'CHAT_ONLY'
    END,
    CASE WHEN h.role IS NOT NULL AND h.role = 'CHAIRMAN' THEN TRUE ELSE FALSE END,
    CASE WHEN e.status IN ('ACTIVE', 'ONLINE') THEN TRUE ELSE FALSE END,
    e.employee_type,
    e.status,
    e.hire_date,
    e.metadata,
    COALESCE(e.created_at, CURRENT_TIMESTAMP),
    COALESCE(e.updated_at, CURRENT_TIMESTAMP)
FROM employees e
LEFT JOIN employees h ON e.id = h.id
WHERE e.employee_type = 'HUMAN'
  AND NOT EXISTS (
    SELECT 1 FROM enterprise_employees ee WHERE ee.employee_id = e.id
  );
