-- V17: 创建 skills 表
-- V15 引用了 skills 表（ALTER TABLE skills ADD COLUMN scope/owner_id/department_id）
-- 但 skills 表本身从未在 Flyway 中创建，导致从空库执行 V15 失败
-- 此迁移创建 skills 表，字段基于 SkillImpl 和 Skill 接口定义

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
