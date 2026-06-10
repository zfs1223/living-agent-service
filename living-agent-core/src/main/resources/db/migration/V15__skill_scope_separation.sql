-- V15: 技能作用域分离（global/evolved/personal）
ALTER TABLE skills ADD COLUMN IF NOT EXISTS scope VARCHAR(16) DEFAULT 'global';
ALTER TABLE skills ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);
ALTER TABLE skills ADD COLUMN IF NOT EXISTS department_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_skills_scope ON skills(scope);
CREATE INDEX IF NOT EXISTS idx_skills_owner ON skills(owner_id);
CREATE INDEX IF NOT EXISTS idx_skills_dept ON skills(department_id);

-- 将现有技能全部标记为项目内置
UPDATE skills SET scope = 'global' WHERE scope IS NULL;
