-- V9__add_model_capability_fields.sql
-- 为模型能力评定服务添加字段

ALTER TABLE llm_models ADD COLUMN IF NOT EXISTS capability_tags VARCHAR(500);
ALTER TABLE llm_models ADD COLUMN IF NOT EXISTS performance_score INTEGER;
ALTER TABLE llm_models ADD COLUMN IF NOT EXISTS parameter_size VARCHAR(20);

COMMENT ON COLUMN llm_models.capability_tags IS '模型能力标签，如: coding, reasoning, frontend, creative, chat 等';
COMMENT ON COLUMN llm_models.performance_score IS '模型综合性能评分 0-100';
COMMENT ON COLUMN llm_models.parameter_size IS '模型参数量（如 9B, 72B 等，用于评估能力）';
