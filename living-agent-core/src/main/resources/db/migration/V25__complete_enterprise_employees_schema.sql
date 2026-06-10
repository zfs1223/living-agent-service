-- V25: Complete enterprise_employees schema - add all missing columns from EnterpriseEmployeeEntity

-- Add all missing columns
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS employee_type VARCHAR(31);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS hire_date DATE;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS model VARCHAR(255);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS brain_domain VARCHAR(255);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS max_concurrent_tasks INTEGER;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS skills TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS capabilities TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS tools TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS roles TEXT;
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS origin VARCHAR(20);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS responsibility_card_id VARCHAR(64);

-- Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_employee_type ON enterprise_employees(employee_type);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_status ON enterprise_employees(status);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_brain_domain ON enterprise_employees(brain_domain);
CREATE INDEX IF NOT EXISTS idx_enterprise_employees_origin ON enterprise_employees(origin);

-- Update trigger for auto-update updated_at
DROP TRIGGER IF EXISTS update_enterprise_employees_updated_at ON enterprise_employees;
CREATE TRIGGER update_enterprise_employees_updated_at BEFORE UPDATE ON enterprise_employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
