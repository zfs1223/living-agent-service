-- V23: 添加员工职责字段和审批记录外键约束

-- 为企业员工表添加职责相关字段
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS roles VARCHAR(1000);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS capabilities VARCHAR(2000);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS tools VARCHAR(2000);
ALTER TABLE enterprise_employees ADD COLUMN IF NOT EXISTS responsibility_card_id VARCHAR(64);

-- 为执行回执添加 projectId 字段（如果回执有独立表）
-- 注意：EmployeeExecutionReceipt 是 record 类型，projectId 通过 metadata 传递
-- 如果有独立的回执表，添加列：
-- ALTER TABLE execution_receipts ADD COLUMN IF NOT EXISTS project_id VARCHAR(100);

-- 为审批记录添加外键约束（如果审批表存在）
DO $$
BEGIN
    -- 审批记录添加 project_id 外键
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'approval_instances') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'approval_instances' AND column_name = 'project_id') THEN
            ALTER TABLE approval_instances ADD COLUMN project_id VARCHAR(100);
        END IF;
    END IF;
END
$$;
