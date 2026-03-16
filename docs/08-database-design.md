# 数据库架构设计

> 企业级数据库架构

---

## 一、数据库选型

| 数据库 | 用途 | 存储层级 | 状态 |
|--------|------|----------|------|
| **PostgreSQL** | 企业主数据库 | L2/L3 层 | 🔜 必需 |
| **Qdrant** | 向量数据库 | L2/L3 层 | 🔜 必需 |
| **SQLite** | 本地存储 | L1 层 | ✅ 已实现 |
| **Redis** | 分布式缓存 | 缓存层 | 🔜 推荐 |

---

## 二、核心表设计

### 2.1 统一员工表

```sql
CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,
    employee_type VARCHAR(20) NOT NULL,        -- HUMAN / DIGITAL
    
    -- 认证信息
    auth_id VARCHAR(255) NOT NULL,
    auth_provider VARCHAR(50),                 -- DINGTALK/FEISHU/OA/SYSTEM
    
    -- 基本信息
    name VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    icon VARCHAR(500),
    email VARCHAR(200),
    phone VARCHAR(50),
    
    -- 组织信息
    department_id VARCHAR(100),
    department VARCHAR(100),
    roles TEXT[],
    manager_id VARCHAR(255),
    
    -- 能力信息
    capabilities TEXT[],
    skills TEXT[],
    tools TEXT[],
    access_level VARCHAR(20) DEFAULT 'DEPARTMENT',
    
    -- 人格配置
    persona JSONB,
    
    -- 类型特有配置
    human_config JSONB,
    digital_config JSONB,
    
    -- 状态
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- 统计
    task_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP
);
```

### 2.2 知识条目表

```sql
CREATE TABLE knowledge_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(255) UNIQUE NOT NULL,
    
    -- 内容
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    
    -- 分类
    knowledge_type VARCHAR(50) NOT NULL,       -- BEST_PRACTICE/EXPERIENCE/RULE/FACT
    domain VARCHAR(100),                       -- 所属领域/部门
    layer INTEGER DEFAULT 1,                   -- 层级 (1/2/3)
    
    -- 向量
    embedding VECTOR(1024),                    -- pgvector
    
    -- 评估
    importance DECIMAL(3,2) DEFAULT 0.5,
    validity DECIMAL(3,2) DEFAULT 1.0,
    access_count INTEGER DEFAULT 0,
    usefulness_score INTEGER DEFAULT 0,
    
    -- 元数据
    tags TEXT[],
    metadata JSONB,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
```

### 2.3 进化信号表

```sql
CREATE TABLE evolution_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 信号信息
    signal_type VARCHAR(50) NOT NULL,          -- ERROR/OPPORTUNITY/STABILITY/...
    severity VARCHAR(20) NOT NULL,             -- CRITICAL/HIGH/MEDIUM/LOW
    source VARCHAR(100),                       -- 来源
    
    -- 内容
    description TEXT NOT NULL,
    context JSONB,
    
    -- 关联
    skill_id VARCHAR(100),
    brain_id VARCHAR(100),
    
    -- 处理状态
    status VARCHAR(20) DEFAULT 'PENDING',      -- PENDING/PROCESSING/RESOLVED/ESCALATED
    strategy VARCHAR(50),                      -- REPAIR/OPTIMIZE/INNOVATE/DEFER/ESCALATE
    
    -- 时间戳
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);
```

### 2.4 进化事件表

```sql
CREATE TABLE evolution_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 关联
    signal_id VARCHAR(100),
    skill_id VARCHAR(100),
    
    -- 执行信息
    strategy VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,               -- SUCCESS/FAILED/SKIPPED/DEFERRED
    
    -- 结果
    description TEXT,
    changes JSONB,
    
    -- 时间戳
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    duration_ms INTEGER
);
```

### 2.5 主动预判任务表

```sql
CREATE TABLE proactive_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 任务信息
    task_name VARCHAR(200) NOT NULL,
    task_type VARCHAR(50) NOT NULL,            -- TIME_DRIVEN/EVENT_DRIVEN/PATTERN_DRIVEN/RISK_DRIVEN
    
    -- 触发配置
    trigger_config JSONB NOT NULL,
    condition_config JSONB,
    
    -- 动作配置
    action_type VARCHAR(50) NOT NULL,          -- EXECUTE/NOTIFY/SUGGEST/PREPARE
    action_config JSONB NOT NULL,
    
    -- 目标
    target_config JSONB,
    
    -- 状态
    enabled BOOLEAN DEFAULT TRUE,
    last_executed_at TIMESTAMP,
    next_execute_at TIMESTAMP,
    
    -- 统计
    execute_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.6 用户行为模式表

```sql
CREATE TABLE user_behavior_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    
    -- 行为模式
    pattern_type VARCHAR(50) NOT NULL,         -- LOGIN_TIME/QUERY_FREQUENCY/TOOL_USAGE/WORKFLOW
    pattern_data JSONB NOT NULL,
    
    -- 统计信息
    occurrence_count INTEGER DEFAULT 1,
    last_occurred_at TIMESTAMP,
    confidence_score DECIMAL(3,2),
    
    -- 预测配置
    prediction_enabled BOOLEAN DEFAULT TRUE,
    prediction_config JSONB,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, pattern_type)
);
```

### 2.7 数字员工模板表

```sql
CREATE TABLE employee_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 基础信息
    name VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    icon VARCHAR(10),
    version VARCHAR(20),
    category VARCHAR(50),
    tags TEXT[],
    
    -- 模板内容
    template_content JSONB NOT NULL,
    
    -- 来源
    source VARCHAR(20) NOT NULL,               -- BUILTIN/GENERATED/CUSTOMIZED
    generated_from TEXT,
    
    -- 使用统计
    usage_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.8 员工人格表

```sql
CREATE TABLE employee_personalities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,
    
    -- 人格参数
    rigor DECIMAL(3,2) DEFAULT 0.5,            -- 严谨度 (0-1)
    creativity DECIMAL(3,2) DEFAULT 0.5,       -- 创造力 (0-1)
    risk_tolerance DECIMAL(3,2) DEFAULT 0.5,   -- 风险容忍 (0-1)
    obedience DECIMAL(3,2) DEFAULT 0.5,        -- 服从度 (0-1)
    
    -- 来源
    source VARCHAR(20) DEFAULT 'TEMPLATE',     -- TEMPLATE/INFERRED/DEPARTMENT/MANUAL
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
```

### 2.9 数字员工工作记录表

```sql
CREATE TABLE digital_employee_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 员工关联
    employee_id VARCHAR(255) NOT NULL,
    neuron_id VARCHAR(255),
    
    -- 工作记录
    task_type VARCHAR(50) NOT NULL,            -- CODE_REVIEW/DATA_ANALYSIS/...
    task_description TEXT,
    input_data JSONB,
    output_data JSONB,
    
    -- 执行信息
    status VARCHAR(20) NOT NULL,               -- SUCCESS/FAILED/TIMEOUT
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    
    -- 质量评估
    quality_score DECIMAL(3,2),
    feedback TEXT,
    
    -- 关联
    related_channel VARCHAR(255),
    related_skill VARCHAR(100),
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
```

### 2.10 员工生命周期事件表

```sql
CREATE TABLE employee_lifecycle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 员工信息
    employee_id VARCHAR(255) NOT NULL,
    employee_type VARCHAR(20) NOT NULL,        -- HUMAN/DIGITAL
    
    -- 事件信息
    event_type VARCHAR(50) NOT NULL,           -- CREATED/ACTIVATED/DORMANT/TERMINATED/...
    event_source VARCHAR(50),                  -- SYSTEM/MANUAL/EVOLUTION
    description TEXT,
    
    -- 上下文
    context JSONB,
    
    -- 时间戳
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
```

### 2.11 绩效考核表

```sql
CREATE TABLE performance_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 员工信息
    employee_id VARCHAR(255) NOT NULL,
    employee_name VARCHAR(100),
    department VARCHAR(100),
    position VARCHAR(100),
    
    -- 考核周期
    period VARCHAR(20) NOT NULL,               -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    -- 得分
    total_score DECIMAL(5,2),
    grade VARCHAR(10),                         -- S/A/B/C/D
    
    -- 详细得分
    indicator_scores JSONB,
    
    -- 自动采集数据
    auto_collected_data JSONB,
    
    -- 人工评价
    manual_evaluations JSONB,
    
    -- 综合评价
    summary TEXT,
    strengths TEXT[],
    improvements TEXT[],
    
    -- 状态
    status VARCHAR(20) DEFAULT 'COMPLETED',
    
    -- 时间戳
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    
    UNIQUE(employee_id, period, start_date, end_date),
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
```

### 2.12 公司运营指标表

```sql
CREATE TABLE company_indicators (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    indicator_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 基本信息
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),                      -- FINANCIAL/BUSINESS/EFFICIENCY/RISK/INNOVATION
    description TEXT,
    
    -- 目标配置
    target_value DECIMAL(15,2),
    target_period VARCHAR(20),                 -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    
    -- 预警配置
    alert_threshold DECIMAL(5,4),              -- 偏离阈值
    alert_level VARCHAR(20),                   -- INFO/WARNING/CRITICAL
    
    -- 数据来源
    source_system VARCHAR(50),
    source_config JSONB,
    
    -- 当前值
    current_value DECIMAL(15,2),
    current_value_updated_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.13 部门绩效表

```sql
CREATE TABLE department_performances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    performance_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 部门信息
    department_id VARCHAR(100) NOT NULL,
    department_name VARCHAR(100),
    
    -- 考核周期
    period VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    -- 得分
    total_score DECIMAL(5,2),
    rank INTEGER,
    
    -- 详细指标
    indicator_scores JSONB,
    
    -- 团队成员绩效汇总
    member_summary JSONB,
    
    -- 时间戳
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(department_id, period, start_date, end_date)
);
```

### 2.14 CEO预警表

```sql
CREATE TABLE ceo_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 预警信息
    level VARCHAR(20) NOT NULL,                -- INFO/WARNING/CRITICAL
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    
    -- 关联
    related_indicator VARCHAR(100),
    related_department VARCHAR(100),
    related_employee VARCHAR(255),
    
    -- 建议
    suggestion TEXT,
    
    -- 状态
    status VARCHAR(20) DEFAULT 'PENDING',      -- PENDING/ACKNOWLEDGED/RESOLVED
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 三、向量存储配置

### 3.1 Qdrant Collection 设计

```
Collection: brain-domain-knowledge
├── 向量维度: 1024 (BGE-M3)
├── 距离度量: Cosine
├── 索引类型: HNSW
└── 命名空间: 按部门隔离

Collection: shared-knowledge
├── 向量维度: 1024
├── 距离度量: Cosine
├── 索引类型: HNSW
└── 命名空间: 全局
```

### 3.2 混合检索

```java
public interface VectorStore {
    // 向量检索
    List<KnowledgeEntry> vectorSearch(float[] query, int limit);
    
    // 关键词检索
    List<KnowledgeEntry> keywordSearch(String query, int limit);
    
    // 混合检索
    List<KnowledgeEntry> hybridSearch(String textQuery, float[] vectorQuery, int limit);
}
```

---

## 四、实施路线图

```
Phase 1: 基础建设 (当前)
├── ✅ SQLite 本地存储
├── ✅ Rust 向量存储
└── 🔜 PostgreSQL 集成

Phase 2: 企业级增强 (知识量 > 10万条)
├── 🔜 Qdrant 向量数据库集成
├── 🔜 PostgreSQL 知识库迁移
└── 🔜 知识进化历史记录

Phase 3: 分布式扩展 (多实例部署)
├── 🔜 Redis 分布式缓存
├── 🔜 Kafka 消息队列
└── 🔜 多租户支持
```

---

## 五、相关文档

- [05-knowledge-system.md](./05-knowledge-system.md) - 知识体系
- [06-evolution-system.md](./06-evolution-system.md) - 进化系统
- [07-unified-employee-model.md](./07-unified-employee-model.md) - 统一员工模型
- [10-operation-assessment.md](./10-operation-assessment.md) - 运营评判系统
- [11-architecture-analysis.md](./11-architecture-analysis.md) - 架构分析报告
