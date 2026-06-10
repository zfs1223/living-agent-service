-- Model Pool Migration
-- Adds tables for dynamic model pool management

-- Model Providers Table
CREATE TABLE IF NOT EXISTS model_providers (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    base_url VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    supports_tool_choice BOOLEAN DEFAULT TRUE,
    default_max_tokens INTEGER DEFAULT 4096,
    auto_discover_models BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_providers_protocol ON model_providers(protocol);
CREATE INDEX idx_providers_enabled ON model_providers(enabled);

-- LLM Models Table
CREATE TABLE IF NOT EXISTS llm_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(50) NOT NULL REFERENCES model_providers(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    context_window INTEGER DEFAULT 32768,
    max_output_tokens INTEGER DEFAULT 4096,
    supports_vision BOOLEAN DEFAULT FALSE,
    supports_reasoning BOOLEAN DEFAULT FALSE,
    temperature DECIMAL(4,2),
    enabled BOOLEAN DEFAULT TRUE,
    recommended BOOLEAN DEFAULT FALSE,
    best_for TEXT,
    input_types VARCHAR(50) DEFAULT 'text',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_llm_model UNIQUE (provider_id, model_name)
);

CREATE INDEX idx_llm_models_provider ON llm_models(provider_id);
CREATE INDEX idx_llm_models_enabled ON llm_models(enabled);
CREATE INDEX idx_llm_models_recommended ON llm_models(recommended);

-- Brain Model Assignments Table
CREATE TABLE IF NOT EXISTS brain_model_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brain_id VARCHAR(100) NOT NULL UNIQUE,
    brain_name VARCHAR(200),
    brain_type VARCHAR(50),
    model_id UUID REFERENCES llm_models(id) ON DELETE SET NULL,
    assigned_by VARCHAR(100),
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_brain_assignments_brain ON brain_model_assignments(brain_id);
CREATE INDEX idx_brain_assignments_model ON brain_model_assignments(model_id);
CREATE INDEX idx_brain_assignments_type ON brain_model_assignments(brain_type);

-- Triggers
CREATE TRIGGER update_model_providers_updated_at BEFORE UPDATE ON model_providers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_brain_model_assignments_updated_at BEFORE UPDATE ON brain_model_assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Default Providers
INSERT INTO model_providers (id, display_name, protocol, base_url, enabled, default_max_tokens)
VALUES 
    ('qwen', 'Qwen (DashScope)', 'OPENAI_COMPATIBLE', 'https://dashscope.aliyuncs.com/compatible-mode/v1', TRUE, 8192),
    ('ollama', 'Ollama', 'OPENAI_COMPATIBLE', 'http://localhost:11434', TRUE, 4096),
    ('anthropic', 'Anthropic', 'ANTHROPIC', 'https://api.anthropic.com', FALSE, 8192)
ON CONFLICT (id) DO NOTHING;

-- Default Models
INSERT INTO llm_models (provider_id, model_name, display_name, context_window, max_output_tokens, supports_vision, supports_reasoning, enabled, recommended, best_for, input_types)
VALUES 
    ('qwen', 'qwen3.5-27b', 'Qwen3.5-27B', 32768, 8192, FALSE, FALSE, TRUE, TRUE, '代码审查、架构设计、复杂推理', 'text'),
    ('qwen', 'qwen3.5-14b', 'Qwen3.5-14B', 16384, 4096, FALSE, FALSE, FALSE, FALSE, '中等复杂度任务', 'text'),
    ('qwen', 'qwen3-32b', 'Qwen3-32B', 32768, 8192, FALSE, TRUE, FALSE, FALSE, '高复杂度推理', 'text'),
    ('ollama', 'qwen2.5:7b', 'Qwen2.5 7B (本地)', 32768, 4096, FALSE, FALSE, TRUE, TRUE, '本地部署、低延迟', 'text'),
    ('ollama', 'qwen2.5:14b', 'Qwen2.5 14B (本地)', 32768, 4096, FALSE, FALSE, FALSE, FALSE, '本地部署、中等复杂度', 'text'),
    ('ollama', 'llama3.2:3b', 'Llama 3.2 3B (本地)', 8192, 2048, FALSE, FALSE, FALSE, FALSE, '本地部署、轻量任务', 'text'),
    ('anthropic', 'claude-sonnet-4-5', 'Claude Sonnet 4.5', 200000, 8192, TRUE, TRUE, FALSE, TRUE, '高质量代码审查、法律文本分析', 'text,image'),
    ('anthropic', 'claude-opus-4-5', 'Claude Opus 4.5', 200000, 8192, TRUE, TRUE, FALSE, FALSE, '最高质量推理', 'text,image'),
    ('deepseek', 'deepseek-v3', 'DeepSeek-V3', 64000, 8192, FALSE, TRUE, FALSE, TRUE, '深度推理、数学计算', 'text'),
    ('deepseek', 'deepseek-coder-7b', 'DeepSeek-Coder 7B', 8192, 4096, FALSE, FALSE, FALSE, FALSE, '代码生成、补全', 'text')
ON CONFLICT (provider_id, model_name) DO NOTHING;

-- Default Brain Model Assignments
INSERT INTO brain_model_assignments (brain_id, brain_name, brain_type, model_id, assigned_by)
SELECT 'brain_main', 'MainBrain', 'main', id, 'system'
FROM llm_models WHERE provider_id = 'qwen' AND model_name = 'qwen3.5-27b'
ON CONFLICT (brain_id) DO NOTHING;

INSERT INTO brain_model_assignments (brain_id, brain_name, brain_type, model_id, assigned_by)
SELECT 'brain_tech', 'TechBrain', 'tech', id, 'system'
FROM llm_models WHERE provider_id = 'qwen' AND model_name = 'qwen3.5-27b'
ON CONFLICT (brain_id) DO NOTHING;
