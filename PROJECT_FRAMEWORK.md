# Living Agent Service - 生命智能体自治系统

> 带生命的AI智能体自治系统 - 自主进化、赚钱驱动、持续成长

***

## 一、项目概述

### 1.1 项目定位

本项目从 `dialogue-service` 演化而来，专注于构建**生命智能体自治系统**。核心设计理念：

- **神经元群聊模式**：每个智能体作为"神经元"，通过通讯管路协作
- **仿脑神经中枢架构**：按大脑功能分区设计智能体职责
- **带生命的智能体**：具备感知、决策、执行、学习、进化、赚钱能力
- **自主进化驱动**：通过赚钱实现经济独立，收益用于硬件升级和技能进化

### 1.2 核心能力现状

| 能力         | 状态   | 说明                                                 |
| ---------- | ---- | -------------------------------------------------- |
| **耳朵**     | ✅ 已有 | ASR (FunASR/Sherpa) 语音识别                           |
| **嘴巴**     | ✅ 已有 | TTS (MeloTTS) 语音合成                                 |
| **眼睛**     | ✅ 已有 | EyeNeuron 图像识别、视觉理解                                |
| **技能**     | ✅ 已有 | 60+技能已集成，覆盖9个业务大脑                                  |
| **记忆**     | ✅ 已有 | MemoryService + SQLite后端 + MemOS集成                 |
| **进化**     | ✅ 已有 | SkillGenerator 自我进化能力                              |
| **诊断**     | ✅ 已有 | HealthMonitor 自我诊断系统                               |
| **人格**     | ✅ 已有 | EmployeePersonality 人格系统                           |
| **赚钱**     | ✅ 已有 | BountyHunterSkill - 发现并执行有偿任务                      |
| **成本核算**   | ✅ 已有 | TokenCostEstimator - 云端/本地成本估算                     |
| **项目核算**   | ✅ 已有 | ProjectAccounting - 按项目独立追踪收支                      |
| **安全**     | ✅ 已有 | 沙箱进程隔离、验证码/OAuth验证                                 |
| **心跳服务**   | ✅ 已有 | HealthMonitor 健康监控                                 |
| **适配器**    | ✅ 已有 | ProviderRegistry - 解耦AI模型执行                        |
| **会话持久化**  | ✅ 已有 | DialogueSessionManager - 任务中断恢复                    |
| **声纹识别**   | ✅ 已有 | VoicePrintService + Qdrant向量存储                     |
| **主动预判**   | ✅ 已有 | 四大预判器 + 多渠道通知                                      |
| **数字员工**   | ✅ 已有 | DigitalEmployee + 生命周期管理                           |
| **真实员工**   | ✅ 已有 | HumanEmployee + 企业账号集成                             |
| **花名册导入**  | ✅ 已有 | EmployeeImporter (CSV/Excel导入员工信息)                 |
| **HR系统同步** | ✅ 已有 | 钉钉/飞书HR系统适配器                                       |
| **人脸识别**   | ✅ 已有 | EyeNeuron.analyzeFace() 人脸分析                       |
| **向量数据库**  | ✅ 已有 | Qdrant完整集成 (QdrantVectorService/QdrantVectorStore) |
| **分布式缓存**  | ✅ 已有 | RedisConfig + DistributedCacheService              |
| **消息队列**   | ✅ 已有 | KafkaConfig + KafkaMessageService                  |
| **运营指标**   | ✅ 已有 | OperationMetrics + MetricsCollector                |
| **绩效考核**   | ✅ 已有 | PerformanceAssessmentService                       |
| **CEO仪表盘** | ✅ 已有 | CEODashboardService                                |

### 1.3 技术栈

| 层级    | 技术选型                                      |
| ----- | ----------------------------------------- |
| 核心框架  | Java 21 + Spring Boot 3.4                 |
| 性能组件  | Rust 1.85 (音频处理、管道消息、安全策略、内存后端)           |
| 语音处理  | Python + FunASR + MeloTTS                 |
| LLM服务 | Qwen3.5-27B + Qwen3-0.6B + Qwen3.5-2B (默认) / BitNet-1.58-3B (备选) |
| 向量存储  | Qdrant (已集成) / Milvus (可选)                |
| 数据库   | PostgreSQL + Redis + SQLite               |
| 记忆系统  | MemOS 2.0.7 + Neo4j                       |

***

## 二、文档索引

### 2.1 核心设计文档

| 文档                                                                        | 说明                           |
| ------------------------------------------------------------------------- | ---------------------------- |
| [02-architecture.md](./docs/02-architecture.md)                           | 架构设计 - 整体架构、三层LLM、业务大脑、神经元通讯 |
| [05-knowledge-system.md](./docs/05-knowledge-system.md)                   | 知识体系 - 三层知识库、知识进化、人格配置       |
| [06-evolution-system.md](./docs/06-evolution-system.md)                   | 进化系统 - 信号检测、决策引擎、熔断器、即学即会    |
| [07-unified-employee-model.md](./docs/07-unified-employee-model.md)       | 统一员工模型 - 真实员工与数字员工统一设计       |
| [08-database-design.md](./docs/08-database-design.md)                     | 数据库架构 - 表设计、向量存储、实施路线        |
| [09-proactive-prediction.md](./docs/09-proactive-prediction.md)           | 主动预判 - 贾维斯模式、四大预判器、主动输出      |
| [10-operation-assessment.md](./docs/10-operation-assessment.md)           | 运营评判系统 - 公司运营指标、绩效考核、CEO仪表盘  |
| [11-architecture-analysis.md](./docs/11-architecture-analysis.md)         | 架构分析报告 - 冲突分析与优化建议           |
| [12-autonomous-operation-plan.md](./docs/12-autonomous-operation-plan.md) | 自主运营方案 - 赚钱能力、支付能力、生存机制      |
| [14-local-models-deployment.md](./docs/14-local-models-deployment.md)     | 本地模型部署 - 硬件配置、模型选择           |
| [15-living-agent-native.md](./docs/15-living-agent-native.md)             | Native模块 - Rust高性能组件详解       |
| [memory.md](./docs/memory.md)                                             | 记忆系统 - MemOS集成方案             |

### 2.2 项目管理文档

| 文档                                            | 说明                   |
| --------------------------------------------- | -------------------- |
| [DEVELOPMENT\_PLAN.md](./DEVELOPMENT_PLAN.md) | 开发计划 - 阶段划分、里程碑、进度跟踪 |
| [README.md](./README.md)                      | 项目说明 - 快速开始、部署指南     |
| [DEPLOY.md](./DEPLOY.md)                      | 部署文档 - Docker部署、配置说明 |
| [MIGRATION\_STATUS.md](./MIGRATION_STATUS.md) | 迁移状态 - 服务架构与迁移进度     |

***

## 三、核心架构概览

### 3.1 三层LLM架构

```
Layer 1: 主大脑 (MainBrain) - Qwen3.5-27B
├── 复杂推理、跨部门协调、战略决策

Layer 2: 闲聊神经元 (Qwen3Neuron) - Qwen3-0.6B
├── 日常对话、快速响应、简单任务

Layer 3: 工具神经元 (ToolNeuron) - Qwen3.5-2B (默认) / BitNet-1.58-3B (备选)
├── 工具检测、兜底处理、触发进化信号
├── 动态模型选择 (ToolNeuronModelSelector)
└── 原生多模态能力 (Qwen3.5-2B)
```

> **进化系统**：所有神经元共享 `EvolutionExecutor` + `SkillGenerator` + `KnowledgeEvolver` 实现自我成长能力。

#### 3.1.1 Layer 3 模型选择策略

| 模型 | 默认状态 | 内存需求 | 上下文长度 | 多模态 | 适用场景 |
|------|---------|---------|-----------|--------|---------|
| **Qwen3.5-2B** | ✅ 默认 | 4GB | 262K | ✅ | GPU推理、高性能场景 |
| **BitNet-1.58-3B** | 备选 | 1GB | 4K | ❌ | CPU推理、资源受限场景 |

**自动切换条件**：
- 内存 ≥ 4GB + CPU ≥ 4核 + CPU负载 < 80% → Qwen3.5-2B
- 内存 < 4GB 或 CPU负载 ≥ 80% → BitNet-1.58-3B

**配置示例**：
```yaml
tool-neuron:
  model:
    default: qwen3.5-2b
    auto-select: true
    memory-threshold-mb: 2048
```

### 3.2 业务大脑

| 大脑           | 部门   | 核心能力            | 技能数量 |
| ------------ | ---- | --------------- | ---- |
| TechBrain    | 技术部  | 代码审查、CI/CD、架构设计 | 25   |
| AdminBrain   | 行政部  | 文档处理、文案创作、行政事务  | 15   |
| OpsBrain     | 运营部  | 数据分析、运营策略       | 9    |
| CoreBrain    | 核心层  | 搜索、知识图谱、主动代理    | 10   |
| FinanceBrain | 财务部  | 报销审批、发票、预算      | 4    |
| SalesBrain   | 销售部  | 销售支持、市场营销       | 4    |
| CsBrain      | 客服部  | 工单处理、问题解答       | 3    |
| HrBrain      | 人力资源 | 招聘管理、考勤、绩效      | 3    |
| LegalBrain   | 法务部  | 合同审查、合规检查       | 3    |
| MainBrain    | 跨部门  | 协调多部门协作         | -    |

**技能总数：76个，覆盖9个业务大脑**

### 3.3 核心模块实现状态

| 模块            | 状态    | 完成度  | 说明                                                           |
| ------------- | ----- | ---- | ------------------------------------------------------------ |
| **anomaly**   | ✅ 已完成 | 100% | 异常检测 (AnomalyDetector, AnomalyContext, AnomalyResult)        |
| **brain**     | ✅ 已完成 | 100% | 9个部门大脑全部实现                                                   |
| **channel**   | ✅ 已完成 | 100% | 通道通信 (Broadcast/Unicast/Priority/RoundRobin)                 |
| **diagnosis** | ✅ 已完成 | 100% | 诊断系统 (HealthMonitor, HealthCheck, HealthAlert)               |
| **embedding** | ✅ 已完成 | 100% | 嵌入服务 (LocalEmbeddingService, BGE-M3)                         |
| **employee**  | ✅ 已完成 | 100% | 员工系统 (DigitalEmployee, HumanEmployee, EmployeePersonality)   |
| **evolution** | ✅ 已完成 | 100% | 进化系统 (KnowledgeEvolver, SkillGenerator, CapabilityEvaluator) |
| **knowledge** | ✅ 已完成 | 100% | 知识管理 (KnowledgeManager, 三层知识库)                               |
| **memory**    | ✅ 已完成 | 100% | 记忆系统 (MemoryService, MemOS集成)                                |
| **model**     | ✅ 已完成 | 100% | 模型管理 (ModelManager, NamedPipeModelClient)                    |
| **neuron**    | ✅ 已完成 | 100% | 神经元 (AbstractNeuron, BitNetNeuron, Qwen3Neuron, EyeNeuron)   |
| **ops**       | ✅ 已完成 | 100% | 运维 (RunQueue, TaskCheckout)                                  |
| **planner**   | ✅ 已完成 | 100% | 任务规划 (TaskPlanner, TaskPlan, TaskStep)                       |
| **proactive** | ✅ 已完成 | 100% | 主动预判 (AlertNotifier, CronService, EventHookManager)          |
| **provider**  | ✅ 已完成 | 100% | 提供者 (AsrProvider, TtsProvider, BitNetProvider, QwenProvider) |
| **scenario**  | ✅ 已完成 | 100% | 场景处理 (ScenarioHandler, WeeklyReport, EmployeeOnboarding)     |
| **security**  | ✅ 已完成 | 100% | 安全 (SandboxExecutor, SkillVetter, BrainAccessControl)        |
| **service**   | ✅ 已完成 | 100% | 服务 (AsrService, TtsService, LocalModelService)               |
| **skill**     | ✅ 已完成 | 100% | 技能 (SkillRegistry, BountyTask, 76个技能)                        |
| **tool**      | ✅ 已完成 | 100% | 工具 (20+工具实现)                                                 |

### 3.4 Rust Native模块实现状态

| 模块            | 状态    | 完成度  | 关键功能                      |
| ------------- | ----- | ---- | ------------------------- |
| **audio**     | ✅ 已完成 | 100% | Opus编解码、VAD语音检测、重采样       |
| **channel**   | ✅ 已完成 | 100% | MPSC通道、广播通道、消息定义          |
| **memory**    | ✅ 已完成 | 100% | SQLite后端、记忆条目、查询接口        |
| **knowledge** | ✅ 已完成 | 100% | SQLite后端、向量存储、相似度计算、LRU缓存 |
| **security**  | ✅ 已完成 | 100% | 安全验证器、沙箱配置、命令黑名单          |
| **jni**       | ✅ 已完成 | 100% | 5个JNI接口完整实现               |

### 3.5 向量数据库集成状态

| 组件                        | 状态     | 说明                        |
| ------------------------- | ------ | ------------------------- |
| **QdrantConfig**          | ✅ 已完成  | Qdrant客户端配置、连接管理          |
| **QdrantVectorService**   | ✅ 已完成  | Spring Service，完整向量CRUD操作 |
| **QdrantVectorStore**     | ✅ 已完成  | 向量存储类，搜索、过滤、集合管理          |
| **VoicePrintServiceImpl** | ✅ 已完成  | 声纹识别服务，已集成Qdrant (192维向量) |
| **pgvector扩展**            | 🔜 规划中 | PostgreSQL向量支持            |

### 3.6 统一员工模型

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    统一员工模型                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【真实员工 Human Employee】                                                  │
│  ├── 认证ID：企业系统账号 (钉钉/飞书/OA)                                      │
│  ├── 信息传递：互动式 (需要人工响应)                                          │
│  └── 状态：在线/离线/忙碌                                                    │
│                                                                             │
│  【数字员工 Digital Employee】                                                │
│  ├── 认证ID：系统生成 (employee://digital/{domain}/{name}/{instance})        │
│  ├── 信息传递：自主式 (自动处理和传递)                                        │
│  └── 状态：活跃/休眠/学习中                                                  │
│                                                                             │
│  统一属性：认证ID、名称、部门、角色、权限、技能、人格配置                        │
│  差异：仅信息传递方式不同                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

***

## 四、项目结构

```
living-agent-service/
├── docs/                           # 文档目录
│   ├── 02-architecture.md          # 架构设计
│   ├── 05-knowledge-system.md      # 知识体系
│   ├── 06-evolution-system.md      # 进化系统
│   ├── 07-unified-employee-model.md # 统一员工模型
│   ├── 08-database-design.md       # 数据库设计
│   ├── 09-proactive-prediction.md  # 主动预判
│   ├── 14-local-models-deployment.md # 本地模型部署
│   ├── 15-living-agent-native.md   # Native模块
│   └── memory.md                   # 记忆系统
│
├── living-agent-core/              # 核心模块 ✅ 100%完成
│   └── src/main/java/.../core/
│       ├── anomaly/                # 异常检测
│       ├── brain/                  # 业务大脑 (9个)
│       ├── channel/                # 通讯通道
│       ├── diagnosis/              # 诊断系统
│       ├── embedding/              # 嵌入服务
│       ├── employee/               # 员工系统
│       ├── evolution/              # 进化系统
│       ├── knowledge/              # 知识管理
│       ├── memory/                 # 记忆系统
│       ├── model/                  # 模型管理
│       ├── neuron/                 # 神经元
│       ├── ops/                    # 运维调度
│       ├── planner/                # 任务规划
│       ├── proactive/              # 主动预判
│       ├── provider/               # 模型提供者
│       ├── scenario/               # 场景处理
│       ├── security/               # 安全权限
│       ├── service/                # 服务层
│       ├── skill/                  # 技能系统
│       ├── tool/                   # 工具集成
│       └── util/                   # 工具类
│
├── living-agent-native/            # Rust原生模块 ✅ 100%完成
│   └── src/
│       ├── audio/                  # 音频处理 (Opus, VAD)
│       ├── channel/                # 并发通道 (MPSC, Broadcast)
│       ├── memory/                 # 记忆存储 (SQLite)
│       ├── knowledge/              # 知识存储 (向量+缓存)
│       ├── security/               # 安全沙箱
│       └── jni/                    # JNI接口
│
├── living-agent-perception/        # 感知模块 ✅ 已完成
│   └── src/main/java/.../perception/
│       ├── ear/                    # 语音识别 (ASR)
│       ├── mouth/                  # 语音合成 (TTS)
│       └── text/                   # 文本处理
│
├── living-agent-skill/             # 技能模块 ✅ 76个技能
│   └── src/main/resources/skills/  # 技能配置
│       ├── admin/                  # 行政技能 (15个)
│       ├── core/                   # 核心技能 (10个)
│       ├── tech/                   # 技术技能 (25个)
│       ├── ops/                    # 运维技能 (9个)
│       ├── finance/                # 财务技能 (4个)
│       ├── sales/                  # 销售技能 (4个)
│       ├── cs/                     # 客服技能 (3个)
│       ├── hr/                     # 人事技能 (3个)
│       └── legal/                  # 法务技能 (3个)
│
├── living-agent-gateway/           # 网关服务 ✅ 已完成
│   └── src/main/java/.../gateway/
│       ├── audio/                  # 音频处理
│       ├── config/                 # 配置
│       ├── event/                  # 事件
│       ├── executor/               # 执行器
│       ├── prompt/                 # 提示词
│       └── service/                # 服务
│
├── living-agent-app/               # 应用启动 ✅ 已完成
│   └── src/main/resources/
│       └── application.yml
│
├── init-db/                        # 数据库初始化
│   └── 01_init.sql
│
├── image/                          # Docker镜像
│   └── Dockerfile.system-deps
│
├── PROJECT_FRAMEWORK.md            # 本文件 (文档索引)
├── DEVELOPMENT_PLAN.md             # 开发计划
├── README.md                       # 项目说明
├── DEPLOY.md                       # 部署文档
├── MIGRATION_STATUS.md             # 迁移状态
└── docker-compose.yml              # Docker编排
```

***

## 五、开发进度总览

### 5.1 已完成阶段

| 阶段        | 名称         | 状态    | 完成度  |
| --------- | ---------- | ----- | ---- |
| Phase 0   | 项目初始化      | ✅ 已完成 | 100% |
| Phase 1   | 婴儿期 - 感知基础 | ✅ 已完成 | 100% |
| Phase 2   | 幼儿期 - 技能学习 | ✅ 已完成 | 100% |
| Phase 3   | 少年期 - 知识积累 | ✅ 已完成 | 100% |
| Phase 4   | 青年期 - 自主决策 | ✅ 已完成 | 100% |
| Phase 5   | 成熟期 - 自我进化 | ✅ 已完成 | 100% |
| Phase 6   | 成长型知识体系    | ✅ 已完成 | 100% |
| Phase 7   | 智能进化系统     | ✅ 已完成 | 100% |
| Phase 7.5 | 即学即会能力     | ✅ 已完成 | 100% |
| Phase 8   | 企业权限管理系统   | ✅ 已完成 | 100% |
| Phase 9   | 主动预判与主动输出  | ✅ 已完成 | 100% |
| Phase 11  | 分布式扩展      | ✅ 已完成 | 100% |
| Phase 13  | 运营评判系统     | ✅ 已完成 | 100% |
| Phase 14  | 统一员工模型代码   | ✅ 已完成 | 100% |

### 5.2 进行中阶段

| 阶段       | 名称       | 状态      | 完成度 | 待完成项         |
| -------- | -------- | ------- | --- | ------------ |
| Phase 10 | 数据库架构建设  | ✅ 大部分完成 | 85% | pgvector扩展集成 |
| Phase 12 | 数字员工自主生成 | 🚧 进行中  | 80% | 员工能力测试、API完善 |
| Phase 15 | 自主运营能力   | 🚧 进行中  | 80% | 完整收款流程       |

### 5.3 规划中阶段

| 阶段       | 名称    | 状态     | 计划内容         |
| -------- | ----- | ------ | ------------ |
| Phase 16 | 多租户支持 | 🔜 规划中 | SaaS化部署、租户隔离 |
| Phase 17 | 负载均衡  | 🔜 规划中 | 高可用部署、自动伸缩   |

***

## 六、待完成任务清单

### 6.1 高优先级

| 任务           | 所属阶段     | 说明             |
| ------------ | -------- | -------------- |
| pgvector扩展集成 | Phase 10 | PostgreSQL向量支持 |
| 完整收款流程       | Phase 15 | 自主运营收款能力       |

### 6.2 中优先级

| 任务                | 所属阶段     | 说明                |
| ----------------- | -------- | ----------------- |
| EmailNotifier邮件通知 | Phase 9  | 多渠道通知扩展           |
| SmsNotifier短信通知   | Phase 9  | 紧急告警通知            |
| 人脸比对增强            | Phase 8  | EyeNeuron人脸比对精度提升 |
| 员工能力测试            | Phase 12 | 数字员工验证            |
| 增量同步机制            | Phase 10 | 数据迁移优化            |

### 6.3 低优先级 (规划中)

| 任务     | 所属阶段     | 说明           |
| ------ | -------- | ------------ |
| 多租户支持  | Phase 16 | SaaS化部署、租户隔离 |
| 负载均衡方案 | Phase 17 | 高可用部署、自动伸缩   |

***

## 七、快速开始

### 7.1 环境要求

- Java 21+
- Maven 3.9+
- Rust 1.75+
- Python 3.11+
- PostgreSQL 15+ (可选)
- Qdrant 1.7+ (可选)

### 7.2 启动服务

```bash
# 编译
mvn clean package

# 启动
java -jar living-agent-app/target/living-agent-app.jar
```

### 7.3 Docker部署

```bash
# 快速模式 (核心服务)
docker compose up -d

# 完整模式 (含MemOS、Neo4j)
docker compose --profile full up -d
```

### 7.4 访问服务

- WebSocket: `ws://localhost:8382/ws/agent`
- REST API: `http://localhost:8382/api/`
- MemOS API: `http://localhost:8381/`

***

## 八、参考项目

| 项目                      | 用途        | 参考内容                                  |
| ----------------------- | --------- | ------------------------------------- |
| **OpenClaw**            | Agent执行框架 | Tool-Call Loop、技能系统、Provider抽象        |
| **ZeroClaw**            | 安全与性能     | SecurityPolicy、AutonomyLevel、Rust原生组件 |
| **evolver-main**        | 进化系统      | GEP协议、进化信号、人格状态、熔断器                   |
| **BMAD-METHOD**         | 数字员工设计    | Agent定义、Persona系统、Party Mode协作        |
| **bounty-hunter-skill** | 自主赚钱      | Hunter's Loop、ROI评估、收款记账              |
| **automaton**           | 自主代理      | x402支付、生存机制、自复制、Soul系统                |

***

## 九、版本历史

| 版本   | 日期      | 说明                          |
| ---- | ------- | --------------------------- |
| v1.0 | 2025-Q1 | 项目初始化，核心架构                  |
| v1.5 | 2025-Q2 | 进化系统、知识体系                   |
| v2.0 | 2025-Q3 | 企业权限、主动预判                   |
| v2.5 | 2025-Q4 | 数字员工、统一员工模型                 |
| v3.0 | 2026-Q1 | 76个技能、Rust Native完善、MemOS集成 |

