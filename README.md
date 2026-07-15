# Living Agent Service

<div align="center">

**企业级生命智能体自治系统**

*仿生神经元架构 · LLM 自主决策 · 企业级治理 · 持续进化成长*

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Rust](https://img.shields.io/badge/Rust-1.85-red.svg)](https://www.rust-lang.org/)
[![Electron](https://img.shields.io/badge/Electron-42-blue.svg)](https://www.electronjs.org/)
[![React](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev/)
[![License](https://img.shields.io/badge/License-Enterprise-blue.svg)](LICENSE)

</div>

---

## 项目定位

Living Agent Service 是一个面向企业的自治智能体平台。它将 LLM 的自主推理能力与企业管理的规范性深度融合——每个智能体像"数字员工"一样拥有独立人格、技能、决策能力和成长路径，同时严格运行在企业定义的权限边界、审批流程、合规策略和成本管控之下。

这不是一个简单的聊天机器人框架，而是一套让 AI **自主工作、自我进化、受制度约束、对结果负责** 的企业级基础设施。

---

## 核心设计理念

**LLM 自主性** — 智能体具备感知、决策、执行、学习的完整能力闭环。智能前台（model_daemon.py）预加载 Qwen3/Qwen3.5/Sherpa/MeloTTS/CAM++ 全部模型，未登录用户即可使用闲聊、语音和公共工具。业务任务由 LLM 从模型池中自主选择最佳模型，大脑通过 ReAct 循环驱动数字员工执行工具，模型健康状态实时监控并自动熔断降级。智能体不只是被动响应，还能主动预判需求、发现机会、规避风险。

**企业管理规范性** — 数字员工与人类员工统一建模，共享同一套组织架构、权限体系、审批流程和绩效标准。所有自主行为都有审计记录，所有支出都有成本核算，所有技能都有安全审查。固定数字员工禁止直连（origin=fixed → 禁止 /ws/agent），只能通过部门大脑、项目互动群、协调群协作。企业的制度不会因为执行者是 AI 而打折扣。

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          客户端层 (Client Layer)                             │
│                                                                             │
│  ┌─────────────────────┐   ┌─────────────────────┐   ┌────────────────┐    │
│  │   React 前端 (SPA)   │   │ Electron 桌面客户端  │   │  Claude CLI    │    │
│  │  Vite + Zustand     │   │  系统托盘/全局快捷键  │   │  代理接入      │    │
│  │  中/英国际化         │   │  任务通知/本地同步    │   │  API 兼容      │    │
│  └────────┬────────────┘   └────────┬────────────┘   └───────┬────────┘    │
└───────────┼──────────────────────────┼─────────────────────────┼────────────┘
            │ WebSocket + REST          │ WebSocket + REST         │ REST Proxy
┌───────────┴──────────────────────────┴─────────────────────────┴────────────┐
│                        网关层 (Gateway Layer)                                │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  AgentWebSocketHandler    │  DepartmentWebSocketHandler              │   │
│  │  /ws/agent (1:1 对话)     │  /ws/dept/* (部门频道)                   │   │
│  │                           │  /ws/enterprise (董事长频道)              │   │
│  │                           │  /ws/public (公共访客)                    │   │
│  ├──────────────────────────────────────────────────────────────────────┤   │
│  │  REST Controllers (40+ API 分组)                                     │   │
│  │  认证 · 任务 · 员工 · 技能 · 知识 · 审批 · 项目 · 模型池 · 进化     │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴─────────────────────────────────────────┐
│                        核心引擎 (Core Engine)                                │
│                                                                             │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
│  │ Neuron │  │ Brain  │  │ Channel│  │ Memory │  │Evolution│  │Employee│  │
│  │ 神经元  │  │  大脑  │  │  通道  │  │  记忆  │  │  进化   │  │  员工  │  │
│  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
│  │Knowledge│  │Security│  │Provider│  │ Sandbox│  │Proactive│  │Project │  │
│  │  知识   │  │  安全  │  │ 适配器 │  │  沙箱  │  │ 主动预判│  │  项目  │  │
│  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │ JNI 桥接
┌───────────────────────────────────┴─────────────────────────────────────────┐
│                    Rust 高性能原生引擎 (Native Engine)                        │
│                                                                             │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  │
│  │ Audio  │  │Channel │  │ Memory │  │Knowledge│  │Security│  │Compact │  │
│  │Opus/VAD│  │MPSC/BC │  │ SQLite │  │向量存储 │  │ 沙箱   │  │对话压缩│  │
│  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────┴─────────────────────────────────────────┐
│                        基础设施层 (Infrastructure)                            │
│                                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │PostgreSQL│ │  Qdrant  │ │   Redis  │ │  Kafka   │ │   MemOS 2.0.7   │  │
│  │ 关系存储  │ │ 向量数据库│ │ 分布式缓存│ │ 消息队列 │ │ 记忆系统+Neo4j  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌───────────────────────────┐  │
│  │ Jenkins  │ │  GitLab  │ │ OpenProject  │ │    RuView WiFi 感知       │  │
│  │  CI/CD   │ │ 代码管理  │ │  项目管理     │ │ 人员检测/生命体征/行为识别│  │
│  └──────────┘ └──────────┘ └──────────────┘ └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 智能前台架构

`model_daemon.py` 是独立的"智能前台"服务，所有模型能力在守护进程内部加载，不依赖外部认证。

```
┌─────────────────────────────────────────────────────────┐
│                   智能前台 (model_daemon.py)              │
│                                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Qwen3    │ │ Qwen3.5  │ │ Sherpa   │ │ MeloTTS  │   │
│  │ 0.6B闲聊 │ │ 2B工具   │ │ ONNX ASR │ │   TTS    │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐ ┌──────────────────────────────────────┐   │
│  │  CAM++   │ │  公共工具: 天气/时间/翻译/计算/百科   │   │
│  │  声纹    │ │  (wttr.in API + 本地模型推理)        │   │
│  └──────────┘ └──────────────────────────────────────┘   │
│                                                         │
│  未登录用户 → /ws/public → chatPublic() → 直接使用     │
│  已登录用户 → Java ModelManager → NamedPipe → 调用     │
└─────────────────────────────────────────────────────────┘
```

**权限边界**：智能前台只处理公共工具（天气/时间/翻译等），公司内部管理（员工/部门/财务）由 Java 端 ToolNeuron 处理，需登录认证。未登录用户可使用语音（ASR/TTS 在守护进程内部加载，不需要认证）。

---

## LLM 自主决策与模型池

LLM 是整个系统的"灵魂"。除闲聊使用固定的轻量模型外，所有业务决策、任务执行和工具调用都由 LLM **从模型池中自主选择最合适的模型**——系统不硬编码任何业务模型，而是让智能体根据自身能力评估和任务需求动态决策。

### 模型选择机制

```
用户输入
  │
  ├─ 闲聊/问候/简单问答 ──────────► Qwen3Neuron (固定 qwen3-0.6b, 本地 GGUF)
  │                                  不走模型池，低延迟快速响应
  │
  └─ 业务任务/复杂推理/工具调用 ──► MainBrain / DepartmentBrain
                                      │
                                      ▼
                              BrainModelResolver (三级解析)
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                  ▼
              第1级: DB分配      第2级: Selector      第3级: 全局默认
           (brain_model_       (各大脑偏好能力      (推荐模型 > 最高
            assignments表)      标签匹配评分)        可用模型)
                    │                 │                  │
                    └─────────────────┴─────────────────┘
                                      │
                                      ▼
                            BrainReActEngine (ReAct 循环)
                              LLM 自主决策 + 工具执行
                              (由数字员工执行具体工具)
```

`BrainAutoAssigner` 在启动时自动为 9 个大脑分配最佳模型——基于能力标签匹配（如 TechBrain 偏好"代码、编程、架构"，FinanceBrain 偏好"财务、预算、计算"）、上下文窗口大小、性能评分和运行时成功率综合打分。`ModelHealthRegistry` 持续监控模型健康状态，连续 3 次调用失败即触发熔断（5 分钟冷却期），自动切换到模型池中评分最高的可用替代模型。

### 模型池

`ModelPoolManager` 管理所有可用的 LLM 供应商和模型，支持 18 种供应商协议（Ollama、Qwen/DashScope、Anthropic、DeepSeek、OpenAI、vLLM、SGLang、OpenRouter 等）。启动时自动发现本地 Ollama 模型，对每个模型执行能力评定（`ModelCapabilityAssessor`）和性能实测（`ModelPerformanceAssessor`），评定结果写入数据库供自动分配使用。

### 工具执行

工具调用不走独立的"工具神经元"，而是由大脑层的 `BrainReActEngine` 驱动 ReAct 循环——LLM 自主决定调用哪个工具、传入什么参数，然后由对应的数字员工在 Java 端执行具体的 Tool 实现。76 个技能定义了工具的能力边界和使用规范，数字员工在编制约束下完成实际执行。`Qwen3.5-2B` 作为本地轻量模型保留为兜底备用，在模型池所有模型不可用时提供降级响应能力。

---

## 九大部门大脑 · 76 个技能 · 32 个数字员工

### 部门大脑

每个部门配备专属智能大脑，负责该领域的任务理解、员工调度和质量把控。

| 部门大脑 | 技能数 | 核心能力 |
|---------|--------|---------|
| **TechBrain** (技术部) | 25 | 代码审查、架构设计、CI/CD、浏览器自动化、Docker、前端设计、LangGraph、RAG、MCP 构建 |
| **AdminBrain** (行政部) | 15 | 公文写作 (docx/pptx/xlsx)、品牌规范、内部通讯、Slack/钉钉/飞书/Discord/Notion 集成 |
| **CoreBrain** (核心层) | 10 | Tavily 搜索、知识图谱、主动代理、模式预判、风险预判、事件通知、自我改进 |
| **OpsBrain** (运营部) | 9 | 定时任务、数据聚合、周报生成、项目风险监控、入职自动化、合同监控、智能家居 |
| **SalesBrain** (销售部) | 4 | 销售自动化、CRM 集成、SEO 审计、头脑风暴 |
| **FinanceBrain** (财务部) | 4 | 账单自动化、预算管理、发票处理、财务 API 网关 |
| **HrBrain** (人力资源部) | 3 | HR 专业助手、招聘自动化、绩效管理 |
| **CsBrain** (客服部) | 3 | 客服系统、工单集成、客户门户 |
| **LegalBrain** (法务部) | 3 | 法律顾问、合规检查、合同管理 |

### 32 个固定数字员工

每个数字员工具备独立人格参数（严谨度、创造力、风险容忍度、服从度），可继承或覆盖部门默认人格。

| 部门 | 员工数 | 角色 |
|------|--------|------|
| 技术部 | 10 | 代码审查员、架构师、DevOps 工程师、运维工程师等 |
| 财务部 | 4 | 财务会计、报销审核员、成本核算员、预算管理员 |
| 运营部 | 4 | 数据分析师、运营专员、任务调度员、流程管理员 |
| 销售部 | 3 | 销售代表、市场专员、渠道经理 |
| 行政部 | 3 | 行政助理、文档管理员、文案策划 |
| 人力资源 | 2 | 招聘专员、绩效管理员 |
| 客服部 | 2 | 客服专员、工单处理员 |
| 法务部 | 2 | 合同审查员、合规专员 |
| 跨部门 | 2 | 协调员、战略规划师 |

---

## 神经元架构

整个系统采用仿生神经元设计——每个功能单元是一个独立的 Neuron，通过 Channel 进行消息通信，模拟生物神经系统的并行协作模式。

核心组件包括 `AbstractNeuron`（神经元基类，定义生命周期和通道绑定）、`NeuronRegistry`（全局注册表）、`ChannelManager`（通道管理器，支持单播、广播、优先级队列、轮询分发）。Rust 原生层提供了基于 crossbeam 的高性能 MPSC 通道和广播通道实现，消息吞吐超过 1M msg/s。

通道拓扑在 `application.yml` 中声明式定义，分为三层：感知层（`perception/audio`、`perception/text`、`perception/sensor`）、调度层（`dispatch/tech`、`dispatch/hr` 等 9 个部门通道）、输出层（`output/text`、`output/speech`）。

感知模块包含 4 个神经元：`EarNeuron`（ASR 语音识别，支持 sherpa-ncnn 和 FunASR）、`MouthNeuron`（TTS 语音合成，支持 Kokoro 和 MeloTTS）、`TextNeuron`（文本预处理）、`PerceptionSensorNeuron`（WiFi CSI 物理感知，集成 RuView 服务实现人员检测、跌倒告警和闯入检测）。

---

## 闭环体系

系统建立了 65 个闭环的四层验证架构，确保从流程正确性到用户业务的每个环节都有完整的 input→processing→output→feedback→improvement 链路。

| 层级 | 闭环数 | 关注点 | 示例 |
|------|--------|--------|------|
| **L1 流程正确性** | 14 | 核心业务流程的输入→处理→输出→反馈→改进 | WebSocket对话、审批、任务分配、模型健康监控 |
| **L2 覆盖完整性** | 10 | 被遗漏的支撑流程闭环 | 前端交互、DB持久化、Rust JNI、Python守护进程 |
| **L2 业务** | 1 | 业务层跨模块闭环 | Claude CLI工具闭环 |
| **L3 生命体自洽** | 14 | 无需人工+兼容人工的自洽生命智能体 | 自愈、经济自治、降级链路、执行回执、固定员工SOP |
| **L4 用户业务** | 26 | 用户可感知的业务功能生命周期 | 认证、项目管理、技能管理、预算、绩效考核 |

所有闭环均为 ✅ 完整闭环，已通过代码逐环节核验。主脑六步决策法（意图识别→路由决策→需求就绪评估→任务规划→员工分派→执行与交付）贯穿 L1-L4 全链路。核心决策链路遵循 **LLM-first / Rule-fallback** 原则——LLM 自主决策优先，规则降级兜底。

---

## LLM 自主能力

### 自主进化系统

智能体能够从自身运行中学习并自我改进。`EvolutionEngine` 从 8 种信号中提取进化触发条件——`ERROR`（执行错误）、`OPPORTUNITY`（新机会）、`STABILITY`（稳定运行）、`DRIFT`（行为漂移）、`CAPABILITY_GAP`（能力缺口）、`PERFORMANCE`（性能变化）、`USER_REQUEST`（用户请求）、`SYSTEM_EVENT`（系统事件），然后通过 5 种策略做出进化决策：`REPAIR`（修复）、`OPTIMIZE`（优化）、`INNOVATE`（创新）、`DEFER`（延迟）、`ESCALATE`（上报）。

`SkillGenerator` 能根据进化决策自动生成新技能定义（SKILL.md），经 `SkillVetter` 安全审查后注册到技能系统。审查结果分四级：APPROVED（通过）、APPROVED_WITH_WARNINGS（带警告通过）、QUARANTINED（隔离观察）、REJECTED（拒绝）。

### 主动预判（贾维斯模式）

不等待用户指令，主动预测需求并提前准备：

| 预判器 | 机制 | 场景 |
|--------|------|------|
| **PatternPredictor** | 基于用户行为模式分析 | 登录后推荐常用功能、会前自动准备材料 |
| **RiskPredictor** | 基于风险指标监控 | 项目延期预警、预算超支提醒 |
| **EventDrivenNotifier** | 基于系统事件触发 | 新员工入职流程自动启动、里程碑达成通知 |
| **ProactiveAgent** | 综合主动行为引擎 | 定时任务调度、周期性报告生成 |

### 三层知识库体系

知识从个人经验逐层晋升为企业资产，每个层级有独立的存储和检索机制：

```
L1: 神经元私有知识 (Rust SQLite)         → 个人经验、对话历史、工作笔记
         ↓ 晋升验证 (质量评分 + 去重检测)
L2: 大脑领域知识 (PostgreSQL + Qdrant)    → 部门最佳实践、业务规则、技术规范
         ↓ 跨部门评审 (多大脑投票验证)
L3: 共享知识库 (PostgreSQL + Qdrant)      → 公司制度、通用知识、行业经验
```

知识具备完整生命周期：获取 → 验证 → 晋升 → 衰退 → 清理。Rust 原生层实现了内存向量存储和余弦相似度搜索，支持 LRU 缓存加速高频知识检索。

---

## 企业级治理

### 统一员工模型

人类员工与数字员工共享同一套数据模型和管理流程。差异仅在于信息传递方式——人类员工通过互动式响应（需要人工参与），数字员工通过自主式处理（自动执行和传递）。

统一属性包括：认证 ID、名称、部门、角色、权限、技能清单、人格配置。编制定义岗位的能力边界和工具授权，员工实例基于编制创建，所有实例严格遵循编制约束。

数据库 schema 包含 75 张表，覆盖企业组织、员工管理、知识体系、模型池、任务项目、会话对话、技能注册、进化记录、绩效评估、薪酬管理、经济信用、预算管理、安全审计等 15 个功能域。

### 安全与合规

多层次安全防护确保智能体的自主行为始终在企业可控范围内。

**执行层安全** — Rust 原生沙箱隔离进程执行，资源限制（512MB 内存、80% CPU、300 秒超时），路径白名单管控，网络访问限制。`BashSecurityValidator` 对命令进行威胁类型检测和注入攻击防护。

**权限控制** — 四级访问权限（`AccessLevel` 枚举），部门级访问控制（`DepartmentAccessService`），技能可见性按 `AccessLevel` 分级，技能 scope 支持 global / department / personal / private 四级作用域。

| 级别 | level | 可访问大脑 | 可用模型 | 知识库 |
|------|-------|-----------|---------|--------|
| CHAT_ONLY | 0 | 无 | Qwen3-0.6B | 否 |
| LIMITED | 1 | AdminBrain + CsBrain | Qwen3 + Qwen3.5 | 是 |
| DEPARTMENT | 2 | 本部门全部大脑 | Qwen3 + Qwen3.5 | 是 |
| FULL | 3 | 所有大脑 + MainBrain | Qwen3 + Qwen3.5 | 是 |

**审计追踪** — 所有任务执行有完整记录（`task_executions` 表），所有命令有审计日志，审批流支持多步骤审批链（`approval_steps` / `approval_records` 表），Token 消耗有成本核算（`TokenCostEstimator` 区分云端和本地推理费用）。

**合规管理** — `compliance` 模块处理合规检查，`LegalBrain` 配备合同管理和合规审查技能，`FinanceBrain` 支持发票处理和预算管控，项目核算按项目独立追踪收支。

### 认证与授权

Spring Security 管理 HTTP 层认证，WebSocket 层在处理器内部完成 Token 验证。Token 提取支持三级回退：`Sec-WebSocket-Protocol` 头 → `Authorization` 头 → URL 查询参数。`UnifiedAuthService` 统一验证 Token 并返回 `AuthContext`（含 employeeId、accessLevel、department、tenantId）。支持手机验证码登录、SSO 单点登录、邀请码机制。

桌面客户端通过 Electron `safeStorage` API（Windows DPAPI）加密存储 Token。

---

## 实时通信

WebSocket 层基于 Spring Boot WebSocket（纯 JSON 协议）构建，支持 4 类端点：

| 端点 | 处理器 | 用途 |
|------|--------|------|
| `/ws/agent` | AgentWebSocketHandler | Agent 1:1 直聊 |
| `/ws/dept/*` | DepartmentWebSocketHandler | 部门频道（tech/hr/finance 等） |
| `/ws/enterprise` | DepartmentWebSocketHandler | 企业级（董事长）频道 |
| `/ws/public` | DepartmentWebSocketHandler | 公共访客频道 |

连接管理使用 `ConcurrentHashMap` 多层索引 + `ConnectionRegistry` 统一注册表，支持按 taskKey、executionId、projectKey、conversationId 做反向查找。全局连接上限 500，每部门上限 50。

心跳机制：服务端守护线程每 30 秒扫描僵尸连接（60 秒无活动则强制关闭），客户端每 30 秒发送 ping。Agent 频道支持会话挂起/恢复（5 分钟 TTL），部门频道通过 `EventQueueService` 实现断连期间事件持久化和重连后回放。`SessionPersistenceService` 将连接上下文持久化到数据库，支持跨重启恢复。

消息类型覆盖完整场景：客户端→服务器（text、audio、control、ping、abort、CHAT、TYPING），服务器→客户端（connected、done、thinking、chunk、progress、artifact、error、pong、execution_progress、execution_event、employee_task_update 等）。

---

## 技能系统

76 个技能以声明式 SKILL.md 文件定义（YAML frontmatter 格式），支持热重载和安全审查。

`SkillLoader` 扫描三个来源（内置 `resources/skills`、配置 `config/skills`、数据 `data/skills`），解析 frontmatter 后经 `SkillVetter` 安全审查。`SkillHotReloader` 使用 Java `WatchService` 监听文件变更，1 秒去抖后自动重新注册。`SkillBindingService` 管理技能与神经元的绑定关系，核心技能（`tavily-search`、`find-skills`、`proactive-agent`、`weather`）自动绑定到每个神经元。

技能 scope 权限模型：`global`（全员可见）、`department:{dept}`（部门内可见）、`personal`（个人添加）、`private:{id}`（私有）、`evolved`（进化生成）。

---

## Rust 高性能原生引擎

核心性能敏感模块用 Rust 实现，通过 JNI 与 Java 服务集成，编译为 cdylib 动态库。

| 模块 | 实现内容 | 关键能力 |
|------|---------|---------|
| **audio** | Opus 编解码、VAD 语音活动检测、音频重采样 | 基于能量阈值的三态 VAD，rubato 重采样 |
| **channel** | MPSC 通道、广播通道 (crossbeam) | 优先级消息队列，高吞吐低延迟 |
| **memory** | SQLite 记忆存储后端 | 模糊搜索、会话过滤、统计查询 |
| **knowledge** | 内存向量存储 + SQLite 持久化 | 余弦相似度搜索、LRU 缓存、多相似度算法 |
| **security** | 沙箱配置、命令安全校验、路径/网络白名单 | 三级安全策略，Bash 威胁检测 |
| **compact** | 对话压缩和摘要生成 | 提取关键请求、待办事项、文件引用，token 估算 |

---

## 客户端

### React 前端 (SPA)

基于 React 19 + Vite + TypeScript + Zustand 构建的单页应用，支持中/英国际化（i18next）和暗/亮主题。包含 20+ 个页面和 17 个组件，40+ 个 API 分组覆盖完整后端功能。

核心页面包括：企业仪表盘（CEO 视角）、部门详情（办公楼层视图 + 员工工位卡 + 活动时间线）、Agent 管理（创建/配置/对话/工具/触发器/审批/关系图）、项目管理、任务中心、审批流、模型池管理、知识管理、社区广场等。

### Electron 桌面客户端

基于 Electron 42 + Vite + React 的 Windows 桌面应用，打包为 NSIS 安装包。主进程包含 18 个模块，提供系统托盘、全局快捷键（Ctrl+Shift+M/T/F/C）、OS 原生通知、本地制品同步等企业级桌面功能。

核心能力：ClientId 持久标识（UUID v4 + 主机名 + OS 用户），Token 加密存储（DPAPI），后端健康监测（30 秒 HTTP 轮询），任务面板（托盘徽章 + 悬浮窗 + 快捷领取），本地文件同步（按年月/executionId 组织，SHA-256 校验）。

启动默认智能前台闲聊模式（无需登录），登录后切换到办公模式（部门聊天/企业频道/任务/审批等）。登录支持手机验证码和声纹识别两种方式。侧边栏按键根据登录状态动态显示/隐藏。

---

## 企业集成

系统通过 Docker Compose 编排完整的企业工具链，所有集成均可通过环境变量配置启用。

| 集成 | 用途 | 配置方式 |
|------|------|----------|
| **飞书** | 企业消息/HR 通知/员工应用 | `FEISHU_ENABLED` + App ID/Secret |
| **钉钉** | 消息通道 | 技能层集成 (dingtalk_channel) |
| **Jenkins** | CI/CD 流水线自动化 | `JENKINS_BASE_URL` + API Token |
| **GitLab** | 代码管理和版本控制 | `GITLAB_BASE_URL` + Access Token |
| **OpenProject** | 项目管理（Jira 免费替代） | `OPENPROJECT_BASE_URL` + API Token |
| **Claude CLI** | Anthropic 模型代理接入 | `CLAUDE_CLI_PROXY_ENABLED` + 虚拟模型映射 |
| **Tavily** | AI 搜索引擎 | `TAVILY_API_KEY` |
| **和天气** | 天气查询（QWeather/OpenWeatherMap） | `WEATHER_*_KEY` |
| **RuView** | WiFi CSI 物理感知 | `RUVIEW_POLLING_ENABLED` + API URL |
| **pywinauto** | Windows 桌面应用自动化（金蝶KIS等） | `scripts/windows_automation/` FastAPI 服务，端口 8765 |

---

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| **后端框架** | Java 21, Spring Boot 3.4, Spring Security, Spring Data JPA, Spring Kafka |
| **HTTP 客户端** | OkHttp 4.12, Jackson 2.18 |
| **数据库** | PostgreSQL 15 (pgvector), HikariCP 连接池 |
| **向量数据库** | Qdrant 1.15 |
| **缓存** | Redis 7 (Lettuce) |
| **消息队列** | Kafka 7.5 + Zookeeper |
| **记忆系统** | MemOS 2.0.7 + Neo4j 5.26 |
| **原生引擎** | Rust 1.85, JNI, crossbeam, rubato, opus |
| **前端** | React 19, Vite 6, TypeScript, Zustand, TanStack Query, recharts, i18next |
| **桌面客户端** | Electron 42, Vite 7, React 19, ws |
| **AI 模型** | 模型池自主选择 (Ollama/DashScope/Anthropic/DeepSeek 等 18 种供应商)，闲聊固定 Qwen3-0.6B |
| **ASR/TTS** | sherpa-ncnn, FunASR / Kokoro, MeloTTS |
| **容器化** | Docker, Docker Compose |

---

## 模块结构

```
living-agent-service/
├── living-agent-core/           # 核心引擎 — 770+ 个 Java 类
│   ├── neuron/                  #   神经元系统 (AbstractNeuron, NeuronRegistry)
│   ├── brain/                   #   9 个部门大脑 (TechBrain, HrBrain, ...)
│   ├── channel/                 #   通道通信 (ChannelManager, 单播/广播/优先级)
│   ├── evolution/               #   进化系统 (EvolutionEngine, 8 信号 5 策略)
│   ├── knowledge/               #   三层知识管理 (L1/L2/L3 生命周期)
│   ├── memory/                  #   记忆系统 (MemoryService, SQLite, MemOS)
│   ├── security/                #   安全体系 (SecurityPolicy, 三级自主权限)
│   ├── employee/                #   统一员工模型 (Digital + Human Employee)
│   ├── autonomy/                #   自主行为引擎 (BountyHunter, 赚钱驱动)
│   ├── proactive/               #   主动预判 (4 大预判器)
│   ├── approval/                #   审批流 (多步审批链)
│   ├── compliance/              #   合规管理
│   ├── sandbox/                 #   沙箱执行
│   ├── provider/                #   LLM 适配器 (ProviderRegistry)
│   ├── proxy/                   #   Claude CLI 代理
│   ├── session/                 #   会话持久化 (ConnectionContext, EventQueue)
│   ├── project/                 #   项目管理
│   ├── budget/                  #   预算和成本核算
│   ├── model/                   #   模型池管理
│   └── tool/                    #   工具集成
│
├── living-agent-native/         # Rust 原生引擎
│   ├── audio/                   #   Opus 编解码, VAD, 重采样
│   ├── channel/                 #   MPSC/广播通道 (crossbeam)
│   ├── memory/                  #   SQLite 记忆后端
│   ├── knowledge/               #   向量存储 + 相似度搜索
│   ├── security/                #   沙箱 + Bash 命令校验
│   ├── compact/                 #   对话压缩和摘要
│   └── jni/                     #   JNI 桥接层 (6 个绑定)
│
├── living-agent-perception/     # 感知模块
│   ├── ear/                     #   EarNeuron (ASR: sherpa-ncnn, FunASR)
│   ├── mouth/                   #   MouthNeuron (TTS: Kokoro, MeloTTS)
│   ├── text/                    #   TextNeuron (文本预处理)
│   └── sensor/                  #   PerceptionSensorNeuron (WiFi CSI 感知)
│
├── living-agent-skill/          # 技能系统 — 76 个技能
│   ├── loader/                  #   SkillLoader (YAML frontmatter 解析)
│   ├── hotreload/               #   SkillHotReloader (WatchService 热重载)
│   ├── registry/                #   SkillRegistryImpl (三来源注册)
│   ├── service/                 #   SkillBindingService, SkillService
│   └── resources/skills/        #   技能定义文件
│       ├── core/     (10)       #   搜索、知识图谱、主动代理、预判器...
│       ├── tech/     (25)       #   代码审查、架构、CI/CD、Docker、HF...
│       ├── admin/    (15)       #   docx/pptx/xlsx、飞书、Slack、Notion...
│       ├── ops/      (9)        #   定时任务、数据聚合、周报、风险监控...
│       ├── sales/    (4)        #   CRM、SEO、销售自动化、头脑风暴
│       ├── finance/  (4)        #   账单、预算、发票、API 网关
│       ├── hr/       (3)        #   HR 助手、招聘、绩效
│       ├── cs/       (3)        #   客服、工单、客户门户
│       └── legal/    (3)        #   法律顾问、合规、合同
│
├── living-agent-gateway/        # 网关服务
│   ├── websocket/               #   WebSocket 处理器 (Agent + Department)
│   │                              #   ConnectionRegistry, 心跳, 会话挂起
│   ├── controller/              #   REST API (40+ 分组)
│   ├── config/                  #   WebSocketConfig, SecurityConfig
│   └── service/                 #   业务服务层
│
├── living-agent-app/            # 应用启动
│   ├── LivingAgentApplication   #   Spring Boot 入口
│   └── application.yml          #   全局配置 (模型/通道/记忆/安全)
│
├── scripts/python/             # 智能前台 (model_daemon.py)
│   ├── model_daemon.py         #   核心守护进程: Qwen3/Qwen35/Sherpa/MeloTTS/CAM++
│   ├── asr/                    #   Sherpa-ONNX ASR 语音识别
│   ├── tts/                    #   MeloTTS 语音合成
│   └── speaker/                #   CAM++ 声纹识别
│
├── scripts/windows_automation/ # Windows 桌面应用自动化 (pywinauto + FastAPI)
│
├── living-agent-desktop/        # Electron 桌面客户端
│   └── src/
│       ├── main/                #   18 个主进程模块
│       ├── renderer/            #   React 渲染层 (设置/任务面板)
│       ├── preload/             #   contextBridge
│       └── shared/              #   类型和常量
│
├── frontend/                    # React 前端 (SPA)
│   └── src/
│       ├── pages/               #   20+ 页面
│       ├── components/          #   17 组件
│       ├── services/            #   40+ API 分组
│       ├── stores/              #   Zustand 状态
│       └── i18n/                #   中/英国际化
│
├── documents/                    # 企业制度、数字员工职责卡、治理文档
├── image/                        # Docker 镜像构建辅助、离线镜像资源
├── docker-compose.yml           # 容器编排 (15+ 服务)
├── init-db/                     # 数据库初始化 (75 张表, 种子数据)
└── docs/                        # 30+ 设计文档
```

---

## 快速部署

### 环境要求

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 8 核 | 16 核+ |
| 内存 | 16 GB | 64 GB+ |
| GPU | RTX 3060 12GB | RTX 4090 / A100 |
| 存储 | 100 GB SSD | 500 GB NVMe |

### Docker 部署

```bash
# 1. 克隆项目
git clone https://github.com/zfs1223/living-agent-service.git
cd living-agent-service

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 设置 POSTGRES_PASSWORD 等必要配置

# 3. 启动核心服务
docker compose up -d living-agent-service postgres redis qdrant kafka zookeeper

# 4. 启动前端 (可选)
docker compose up -d living-agent-frontend

# 5. 启动完整模式 (含 MemOS 记忆系统)
docker compose --profile full up -d
```

### 源码编译

```bash
# 编译
mvn clean package -DskipTests

# 启动
java -jar living-agent-app/target/living-agent-app.jar
```

### 桌面客户端

```bash
cd living-agent-desktop

# 安装依赖
npm install

# 开发模式
npm run dev

# 打包 Windows 安装包
npm run build
```

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| living-agent-service | 8382 | 主服务 (REST + WebSocket) |
| model_daemon HTTP | 8392 | 智能前台 OpenAI 兼容 API (内部) |
| llama-server Qwen3 | 8393 | 闲聊模型推理 (内部, ctx=512) |
| llama-server Qwen3.5 | 8394 | 工具路由模型推理 (内部, ctx=4096) |
| living-agent-frontend | 8383 | React 前端 |
| Jenkins | 8384 | CI/CD |
| GitLab | 8385 | 代码管理 |
| OpenProject | 8386 | 项目管理 |
| RuView Sensing | 8387 | WiFi 感知 REST |
| RuView WS | 8389 | WiFi 感知 WebSocket |
| MemOS API | 8381 | 记忆系统 |
| Neo4j HTTP | 7475 | 图数据库 |
| Neo4j Bolt | 7688 | 图数据库连接 |
| Qdrant HTTP | 6333 | 向量数据库 |
| PostgreSQL | 5432 | 关系数据库 |
| Redis | 6379 | 缓存 |
| Kafka | 9092 | 消息队列 |

---

## 文档资源

| 文档 | 说明 |
|------|------|
| [代码结构与文件指南](docs/CODE_STRUCTURE_AND_FILE_GUIDE.md) | 全量代码结构、文件功能、模块关系 |
| [闭环改进方案索引](docs/IMPROVEMENT_PLAN_INDEX.md) | 65 个闭环四层架构(L1-L4)验证与改进 |
| [权限与入口矩阵](docs/权限与入口矩阵.md) | 登录/身份/通道/语音/流程/审计统一规则 |
| [核心架构设计](docs/core/02-core-architecture.md) | 整体架构与模块设计 |
| [主脑执行规则](docs/core/MAINBRAIN_EXECUTION_RULES.md) | 六步决策法与 LLM-first 降级规则 |
| [员工模型](docs/core/03-employee-model.md) | 统一员工模型（人类+数字） |
| [安全权限](docs/core/06-security-permission.md) | 多层次安全与合规体系 |
| [语音对话方案](docs/IMPROVEMENT_PLAN_VOICE_DIALOGUE.md) | 智能前台语音/声纹功能完整方案 |
| [前端/桌面端闭环](docs/IMPROVEMENT_PLAN_FRONTEND_DESKTOP_LOOP_GAPS.md) | 前端+桌面端闭环断点分析与修复 |
| [固定员工行动SOP](docs/FIXED_EMPLOYEE_ACTION_SOP_IMPROVEMENT_PLAN.md) | 7 阶段行动规范与子流程闭环 |

---

## 路线图

**已完成** — 65 个闭环全部完整（L1 流程正确性 14 + L2 覆盖完整性 10 + L2 业务 1 + L3 生命体自洽 14 + L4 用户业务 26），LLM 模型池自主决策，九大部门大脑，76 个技能，神经元通信系统，自主进化引擎，主动预判，Rust 原生组件，统一员工模型，32 个固定数字员工，WebSocket 实时通信，企业集成（飞书/Jenkins/GitLab/OpenProject），Claude CLI 代理，WiFi 物理感知，Windows 桌面应用自动化，桌面客户端，智能前台架构（model_daemon.py 独立服务 + 5 个公共工具），语音对话闭环（ASR→LLM→TTS），声纹识别（CAM++ + 声纹登录），固定员工禁止直连防护。

**进行中** — 多租户联邦架构设计、边缘部署优化、联邦学习。

**规划中** — 语音开关前端UI、桌面端 WebSocket 统一化（消除 OfficeChatPage 自建连接）。

---

---

<div align="center">

**Living Agent Service** — 让 AI 成为企业真正的数字员工

*Where LLM Autonomy Meets Enterprise Governance*

</div>
