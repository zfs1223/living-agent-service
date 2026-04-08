# 系统概述

> Living Agent Service - 生命智能体自治系统

## 一、项目定位

本项目专注于构建**生命智能体自治系统**。核心设计理念：

- **神经元群聊模式**：每个智能体作为"神经元"，通过通讯管路协作
- **仿脑神经中枢架构**：按大脑功能分区设计智能体职责
- **带生命的智能体**：具备感知、决策、执行、学习、进化、赚钱能力
- **自主进化驱动**：通过赚钱实现经济独立，收益用于硬件升级和技能进化

## 二、核心能力

| 能力 | 状态 | 说明 |
|------|------|------|
| **耳朵** | ✅ | ASR (FunASR/Sherpa) 语音识别 |
| **嘴巴** | ✅ | TTS (MeloTTS) 语音合成 |
| **眼睛** | ✅ | EyeNeuron 图像识别、视觉理解 |
| **技能** | ✅ | 76个技能已集成，覆盖9个业务大脑 |
| **记忆** | ✅ | MemoryService + SQLite后端 + MemOS集成 |
| **进化** | ✅ | SkillGenerator 自我进化能力 |
| **诊断** | ✅ | HealthMonitor 自我诊断系统 |
| **人格** | ✅ | EmployeePersonality 人格系统 |
| **赚钱** | ✅ | BountyHunterSkill - 发现并执行有偿任务 |
| **成本核算** | ✅ | TokenCostEstimator - 云端/本地成本估算 |
| **项目核算** | ✅ | ProjectAccounting - 按项目独立追踪收支 |
| **安全** | ✅ | 沙箱进程隔离、验证码/OAuth验证 |
| **心跳服务** | ✅ | HealthMonitor 健康监控 |
| **适配器** | ✅ | ProviderRegistry - 解耦AI模型执行 |
| **会话持久化** | ✅ | DialogueSessionManager - 任务中断恢复 |
| **声纹识别** | ✅ | VoicePrintService + Qdrant向量存储 |
| **主动预判** | ✅ | 四大预判器 + 多渠道通知 |
| **数字员工** | ✅ | DigitalEmployee + 生命周期管理 |
| **真实员工** | ✅ | HumanEmployee + 企业账号集成 |
| **花名册导入** | ✅ | EmployeeImporter (CSV/Excel导入员工信息) |
| **HR系统同步** | ✅ | 钉钉/飞书HR系统适配器 |
| **人脸识别** | ✅ | EyeNeuron.analyzeFace() 人脸分析 |
| **向量数据库** | ✅ | Qdrant完整集成 |
| **分布式缓存** | ✅ | RedisConfig + DistributedCacheService |
| **消息队列** | ✅ | KafkaConfig + KafkaMessageService |
| **运营指标** | ✅ | OperationMetrics + MetricsCollector |
| **绩效考核** | ✅ | PerformanceAssessmentService |
| **CEO仪表盘** | ✅ | CEODashboardService |
| **合规管理** | 🟡 | 合规检查技能已有，审计日志待扩展持久化 |
| **ERP适配** | ✅ | HrSyncAdapter + DingTalkSyncAdapter + FeishuSyncAdapter |
| **人工干预** | 🟡 | 干预决策设计完成，待实现引擎和界面 |
| **项目管理** | ✅ | ProjectController + ProjectService - 项目全生命周期管理 |
| **任务管理API** | ✅ | TaskController - 任务调度REST API |
| **审批流程** | ✅ | ApprovalController + ApprovalService - 工作流引擎 |

## 三、技术栈

| 层级 | 技术选型 |
|------|----------|
| 核心框架 | Java 21 + Spring Boot 3.4 |
| 性能组件 | Rust 1.85 (音频处理、管道消息、安全策略、内存后端) |
| 语音处理 | Python + FunASR + MeloTTS |
| LLM服务 | Qwen3.5-27B（可配置） + Qwen3-0.6B + Qwen3.5-2B (默认) / BitNet-1.58-3B (备选) |
| 向量存储 | Qdrant (已集成) / Milvus (可选) |
| 数据库 | PostgreSQL + Redis + SQLite |
| 记忆系统 | MemOS 2.0.7 + Neo4j |

## 四、项目结构

```
living-agent-service/
├── docs/                           # 文档目录
├── living-agent-core/              # 核心模块
│   └── src/main/java/.../core/
│       ├── anomaly/                # 异常检测
│       ├── approval/               # 审批流程
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
│       ├── project/                # 项目管理
│       ├── provider/               # 模型提供者
│       ├── scenario/               # 场景处理
│       ├── security/               # 安全权限
│       ├── service/                # 服务层
│       ├── skill/                  # 技能系统
│       ├── tool/                   # 工具集成
│       └── util/                   # 工具类
├── living-agent-native/            # Rust原生模块
│   └── src/
│       ├── audio/                  # 音频处理 (Opus, VAD)
│       ├── channel/                # 并发通道 (MPSC, Broadcast)
│       ├── memory/                 # 记忆存储 (SQLite)
│       ├── knowledge/              # 知识存储 (向量+缓存)
│       ├── security/               # 安全沙箱
│       └── jni/                    # JNI接口
├── living-agent-perception/        # 感知模块
│   └── src/main/java/.../perception/
│       ├── ear/                    # 语音识别 (ASR)
│       ├── mouth/                  # 语音合成 (TTS)
│       └── text/                   # 文本处理
├── living-agent-skill/             # 技能模块
│   └── src/main/resources/skills/  # 技能配置
├── living-agent-gateway/           # 网关服务
│   └── src/main/java/.../gateway/
│       ├── audio/                  # 音频处理
│       ├── config/                 # 配置
│       ├── controller/             # REST API控制器
│       ├── event/                  # 事件
│       ├── executor/               # 执行器
│       ├── prompt/                 # 提示词
│       └── service/                # 服务
└── living-agent-app/               # 应用启动
```

## 五、文档索引

### 核心设计文档

| 文档 | 说明 |
|------|------|
| [01-system-overview.md](./01-system-overview.md) | 系统概述 |
| [02-core-architecture.md](./02-core-architecture.md) | 核心架构设计 |
| [03-employee-model.md](./03-employee-model.md) | 统一员工模型 |
| [04-knowledge-system.md](./04-knowledge-system.md) | 知识体系 |
| [05-evolution-system.md](./05-evolution-system.md) | 进化系统 |
| [06-security-permission.md](./06-security-permission.md) | 安全与权限 |
| [07-deployment-operations.md](./07-deployment-operations.md) | 部署与运维 |

### 参考文档

| 文档 | 说明 |
|------|------|
| [references/API_REFERENCE.md](./references/API_REFERENCE.md) | API接口参考 |
| [guides/DEPLOY.md](./guides/DEPLOY.md) | 部署指南 |
| [planning/DEVELOPMENT_PLAN.md](./planning/DEVELOPMENT_PLAN.md) | 开发计划 |
