-- V8__artifact_records.sql
-- 创建 artifact 记录表，支持任务产物的数据库持久化

CREATE TABLE IF NOT EXISTS artifact_records (
    id BIGSERIAL PRIMARY KEY,
    artifact_id VARCHAR(100) NOT NULL UNIQUE,
    execution_id VARCHAR(100) NOT NULL,
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
    metadata_json JSONB
);

-- 索引优化查询性能
CREATE INDEX idx_artifact_execution_id ON artifact_records(execution_id);
CREATE INDEX idx_artifact_department ON artifact_records(department);
CREATE INDEX idx_artifact_employee_code ON artifact_records(owner_employee_code);
CREATE INDEX idx_artifact_type ON artifact_records(type);
CREATE INDEX idx_artifact_created_at ON artifact_records(created_at DESC);
CREATE INDEX idx_artifact_department_type ON artifact_records(department, type);

COMMENT ON TABLE artifact_records IS '任务产物记录表，存储执行过程中生成的文件产物元数据';
COMMENT ON COLUMN artifact_records.artifact_id IS '产物唯一标识';
COMMENT ON COLUMN artifact_records.execution_id IS '关联的执行ID';
COMMENT ON COLUMN artifact_records.department IS '所属部门';
COMMENT ON COLUMN artifact_records.owner_employee_code IS '所属员工代码';
COMMENT ON COLUMN artifact_records.type IS '产物类型：html/css/js/markdown/report等';
COMMENT ON COLUMN artifact_records.path IS '文件存储路径';
COMMENT ON COLUMN artifact_records.size_bytes IS '文件大小（字节）';
COMMENT ON COLUMN artifact_records.sha256 IS '文件SHA256校验和';
COMMENT ON COLUMN artifact_records.metadata_json IS '附加元数据（JSON格式）';
