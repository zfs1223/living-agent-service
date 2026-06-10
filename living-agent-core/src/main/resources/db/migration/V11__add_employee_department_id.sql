-- Add department_id column to employees table for brain binding lookup
ALTER TABLE employees ADD COLUMN IF NOT EXISTS department_id VARCHAR(64);

-- Backfill department_id from department name mapping
UPDATE employees SET department_id = 'tech' WHERE department = '技术部' AND department_id IS NULL;
UPDATE employees SET department_id = 'finance' WHERE department = '财务部' AND department_id IS NULL;
UPDATE employees SET department_id = 'ops' WHERE department = '运营部' AND department_id IS NULL;
UPDATE employees SET department_id = 'sales' WHERE department = '销售部' AND department_id IS NULL;
UPDATE employees SET department_id = 'hr' WHERE department IN ('人力资源', '人力资源部') AND department_id IS NULL;
UPDATE employees SET department_id = 'cs' WHERE department = '客服部' AND department_id IS NULL;
UPDATE employees SET department_id = 'admin' WHERE department = '行政部' AND department_id IS NULL;
UPDATE employees SET department_id = 'legal' WHERE department = '法务部' AND department_id IS NULL;
UPDATE employees SET department_id = 'core' WHERE department IN ('跨部门协调', '跨部门') AND department_id IS NULL;

-- Also update enterprise_employees.department_id from department_name mapping
UPDATE enterprise_employees SET department_id = 'dept_tech' WHERE department_name = '技术部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_finance' WHERE department_name = '财务部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_ops' WHERE department_name = '运营部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_sales' WHERE department_name = '销售部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_hr' WHERE department_name IN ('人力资源', '人力资源部') AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_cs' WHERE department_name = '客服部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_admin' WHERE department_name = '行政部' AND department_id IS NULL;
UPDATE enterprise_employees SET department_id = 'dept_legal' WHERE department_name = '法务部' AND department_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_employees_department_id ON employees(department_id);
