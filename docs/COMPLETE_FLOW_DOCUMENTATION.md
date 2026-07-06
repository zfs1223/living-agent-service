# Living Agent Service 完整流程文档

> **目的**：系统梳理 living-agent-service 中所有功能模块的逻辑流程，建立流程关联图，并整理所有闭环需求，便于后续检查和排查问题。
>
> **更新时间**：2026-06-30
>
> **基于文档**：CODE_STRUCTURE_AND_FILE_GUIDE.md (2302行完整项目结构文档)
>
> **覆盖范围**：WebSocket处理、权限认证、三层LLM架构、自治编排、业务大脑、神经元、工具执行、知识库、记忆系统、员工管理、通道通信、模型池、进化系统、主动预判、审批流程、项目管理等所有核心功能模块。

---

## 目录

1. [项目整体架构概览](#1-项目整体架构概览)
2. [核心流程架构图](#2-核心流程架构图)
3. [WebSocket端点处理流程](#3-websocket端点处理流程)
4. [权限认证流程](#4-权限认证流程)
5. [自治编排流程（Autonomy）](#5-自治编排流程autonomy)
6. [业务大脑处理流程（Brain）](#6-业务大脑处理流程brain)
7. [三层LLM架构调用流程](#7-三层llm架构调用流程)
8. [神经元处理流程（Neuron）](#8-神经元处理流程neuron)
9. [工具执行流程（Tool）](#9-工具执行流程tool)
10. [知识库与记忆系统流程](#10-知识库与记忆系统流程)
11. [员工与通道管理流程](#11-员工与通道管理流程)
12. [模型池与Provider调用流程](#12-模型池与provider调用流程)
13. [进化系统流程（Evolution）](#13-进化系统流程evolution)
14. [主动预判流程（Proactive）](#14-主动预判流程proactive)
15. [审批流程（Approval）](#15-审批流程approval)
16. [项目管理与任务流转流程](#16-项目管理与任务流转流程)
17. [前端与后端交互流程](#17-前端与后端交互流程)
18. [数据库持久化流程](#18-数据库持久化流程)
19. [Native Rust与Java交互流程](#19-native-rust与java交互流程)
20. [Python模型守护进程流程](#20-python模型守护进程流程)
21. [Windows自动化桥接流程](#21-windows自动化桥接流程)
22. [Claude CLI代理流程](#22-clude-cli代理流程)
23. [Docker部署与启动流程](#23-docker部署与启动流程)
24. [流程关联关系总图](#24-流程关联关系总图)
25. [闭环需求整理](#25-闭环需求整理)（请参考COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md）
26. [关键代码路径索引](#26-关键代码路径索引)
27. [常见问题排查指南](#27-常见问题排查指南)

---

## 1. 项目整体架构概览

### 1.1 模块职责总览

```
living-agent-service/
├── living-agent-app/        # Spring Boot启动模块
├── living-agent-core/       # 核心业务逻辑层（21个子模块）
│   ├── autonomy/            # 对话自治编排（意图分析/路由/规划/执行/回执）
│   ├── brain/               # 业务大脑（MainBrain + 8个部门大脑）
│   ├── neuron/              # 神经元（Qwen3Neuron/BitNetNeuron/ToolNeuron）
│   ├── channel/             # 通道通信（广播/单播/轮询）
│   ├── employee/            # 统一员工模型（人类+数字员工）
│   ├── model/               # 模型池/Provider/LLM客户端
│   ├── provider/            # LLM/ASR/TTS Provider
│   ├── tool/                # 工具系统（企业工具+内置工具）
│   ├── skill/               # 技能系统（技能注册+热加载）
│   ├── knowledge/           # 知识库（分层知识L1/L2/L3）
│   ├── memory/              # 记忆系统（SQLite/Memos/MemPalace）
│   ├── evolution/           # 自主进化（信号提取/决策/执行）
│   ├── autonomous/          # 经济自治（赏金/激励/支付）
│   ├── security/            # 权限认证（访问控制/会话管理）
│   ├── database/            # 数据库层（JPA实体/Repository）
│   ├── distributed/         # 分布式基础设施（Redis/Kafka）
│   ├── approval/            # 审批流程
│   ├── project/             # 项目管理
│   ├── workflow/            # 工作流编排
│   ├── proactive/           # 主动预判
│   ├── sandbox/             # 沙箱执行
│   └── proxy/               # Claude CLI代理
├── living-agent-gateway/    # API/WebSocket网关层
│   ├── controller/          # REST API控制器（30+个Controller）
│   ├── websocket/           # WebSocket处理器
│   ├── service/             # 网关服务层
│   ├── config/              # 网关配置
│   └── interceptor/         # 拦截器
├── living-agent-skill/      # 技能加载模块
├── living-agent-perception/ # 感知模块（ASR/TTS）
├── living-agent-native/     # Rust Native高性能模块
├── frontend/                # React + Vite前端
├── scripts/python/          # Python模型守护进程
├── init-db/                 # 数据库初始化脚本
├── documents/               # 企业知识源（制度/职责卡）
└── docs/                    # 技术文档
```

### 1.2 核心技术栈

**后端**：
- Java 21 + Spring Boot 3.4 + Spring Cloud
- PostgreSQL（主数据库）+ Qdrant（向量库）+ Redis（缓存）+ Kafka（消息）
- JNI（Rust Native高性能模块）

**前端**：
- React 18 + Vite + TypeScript + Zustand（状态管理）
- WebSocket实时通信 + REST API

**AI/模型**：
- 三层LLM架构：MainBrain（动态模型池）→ Qwen3Neuron（固定模型）→ ToolNeuron（固定模型）
- NamedPipe通信（本地模型守护进程）
- 多Provider支持：OpenAI Compatible / Anthropic / Ollama / BitNet

---

## 2. 核心流程架构图

### 2.1 用户消息处理总流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  用户消息入口（前端）                                                │
│  ├─ 部门文本聊天 → /ws/dept/{dept}                               │
│  ├─ 通用Agent/语音 → /ws/agent                                    │
│  ├─ 公共访客 → /ws/public                                         │
│  ├─ 董事长频道 → /ws/enterprise                                   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  WebSocket Handler层                                             │
│  ├─ DepartmentWebSocketHandler（部门聊天）                       │
│  ├─ AgentWebSocketHandler（通用Agent）                          │
│  ├─ 认证检查（AuthContext提取）                                  │
│  ├─ 权限校验（AccessLevel检查）                                  │
│  └─ 连接管理（ConnectionRegistry）                              │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Service层                                                       │
│  ├─ DepartmentChatService（部门聊天服务）                        │
│  ├─ AgentService（通用Agent服务）                                │
│  ├─ 会话管理（conversationId绑定）                              │
│  ├─ 消息落库（DepartmentChatMessageEntity）                     │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ 部门文本链路    │     │ 通用Agent链路    │
│                │     │                 │
│ ConversationOrchestrator │ AgentService.startSession()
│ └─ 意图分析      │     │ └ NeuronCoordinator.createSession()
│ └─ 任务规划      │     │ └ Qwen3Neuron/BitNetNeuron
│ └─ 路由决策      │     │ └ NamedPipeModelClient
│ └ 员工分派      │     │ └ Python model_daemon.py
│ └ 执行协调      │     │                 │
│ └ 回执处理      │     │ 返回文本/音频    │
└────────┬───────┘     └─────────────────┘
         │
┌────────▼───────────────────────────────────────────────────────┐
│  自治编排层（Autonomy）                                           │
│  ├─ DialogueAnalyzer（意图分析：LLM-first → Rule-fallback）    │
│  ├─ TaskRouteClassifier（路由分类：单部门直达/跨部门主脑/需要澄清）│
│  ├─ MainBrainTaskDirector（任务规划：LLM → Rule降级）           │
│  ├─ BrainRoutingDecision（部门路由）                            │
│  ├─ FixedEmployeeDispatcher（员工分派：LLM → Registry降级）     │
│  ├─ AssignmentPreparationService（任务单准备）                  │
│  ├─ DepartmentExecutionCoordinator（执行协调）                  │
│  ├─ EmployeeExecutionReceiptService（回执处理）                 │
│  ├─ FinalResponseCoordinator（出口策略）                        │
│  └─ AutonomyTraceService（追踪日志）                            │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  业务大脑层（Brain）                                              │
│  ├─ BrainRegistry（大脑注册表：MainBrain + 8部门大脑）         │
│  ├─ AbstractBrain（公共基类：ReAct循环/模型调用/工具集成）     │
│  ├─ MainBrain（跨部门协调）                                     │
│  ├─ TechBrain/HrBrain/FinanceBrain/SalesBrain/CsBrain...      │
│  ├─ DynamicPromptBuilder（动态提示词构建）                      │
│  ├─ BrainBoundaryEnforcer（职责边界检查）                      │
│  ├─ BrainSessionManager（会话历史管理）                        │
│  └─ BrainModelFallback（模型降级）                             │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  模型调用层（Model Pool）                                         │
│  ├─ BrainModelSelectorManager（模型选择策略）                   │
│  ├─ BrainModelResolver（模型解析）                              │
│  ├─ ModelHealthRegistry（健康监控+熔断）                        │
│  ├─ LlmClient（统一调用接口）                                   │
│  ├─ OpenAI Compatible Client / Anthropic Client               │
│  ├─ NamedPipeModelClient（本地模型守护进程通信）               │
│  └─ scripts/python/model_daemon.py                             │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  工具执行层（Tool）                                               │
│  ├─ ToolRegistry（工具注册表）                                  │
│  ├─ DefaultToolExecutor（工具执行器）                           │
│  ├─ ToolHookManager（调用钩子）                                 │
│  ├─ 企业工具：FeishuTool/GitHubTool/GitLabTool/JenkinsTool...  │
│  ├─ 内置工具：BrowserAutomationTool/DockerTool/ClaudeCliTool...│
│  ├─ Windows自动化：WindowsAppTool/WindowsAutomationTool        │
│  └─ BashSecurityValidator（安全校验）                             │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  知识沉淀与反馈层                                                 │
│  ├─ KnowledgeCaptureService（知识沉淀：L1→L2→L3晋升）           │
│  ├─ PerformanceCaptureService（绩效记录：积分发放）            │
│  ├─ EvolutionFeedbackService（进化反馈）                        │
│  ├─ ArtifactRecordService（产物记录）                           │
│  └─ AutonomyTraceService（完整链路追踪）                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  响应返回（WebSocket）                                            │
│  ├─ 部门大脑直接回复                                             │
│  ├─ 主脑组合回复（跨部门任务）                                   │
│  ├─ 执行进度推送（长任务）                                       │
│  ├─ 澄清消息返回（需求不明确）                                   │
│  └─ 错误响应（权限不足/系统错误）                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. WebSocket端点处理流程

### 3.1 WebSocket端点总览

| 端点路径 | 处理器 | 权限要求 | 功能说明 |
|---------|--------|---------|---------|
| `/ws/dept/{dept}` | DepartmentWebSocketHandler | 需登录+部门匹配 | 部门文本聊天（主要业务入口） |
| `/ws/agent` | AgentWebSocketHandler | 需登录 | 通用Agent/语音会话 |
| `/ws/enterprise` | DepartmentWebSocketHandler | FULL权限 | 董事长频道（跨部门协调） |
| `/ws/public` | DepartmentWebSocketHandler | 无需登录 | 公共访客通道（有限能力） |

### 3.2 DepartmentWebSocketHandler 详细流程

**文件路径**：`living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java`

**处理流程**：

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: 连接建立（afterConnectionEstablished）                  │
│  ├─ 1.1 提取部门标识（extractDepartment）                        │
│  ├─ 1.2 认证检查（getAuthContext：Sec-WebSocket-Protocol头      │
│  │           → Authorization头 → URL查询参数降级）               │
│  ├─ 1.3 权限校验（departmentAccessService.hasDepartmentAccess）│
│  ├─ 1.4 连接限制检查（MAX_GLOBAL_CONNECTIONS=500               │
│  │                   MAX_DEPARTMENT_CONNECTIONS=50）            │
│  ├─ 1.5 设备注册（clientId + hostname + macAddress绑定）        │
│  ├─ 1.6 会话绑定（sessionToAuthContext/sessionToDepartment）    │
│  ├─ 1.7 主动汇报触发（PR-1：登录时触发ProactiveOrchestrator）   │
│  ├─ 1.8 心跳启动（30秒间隔，移除僵尸检测避免误杀）              │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 2: 消息处理（handleTextMessage）                           │
│  ├─ 2.1 解析JSON消息体                                          │
│  ├─ 2.2 消息类型判断：                                          │
│  │     ├─ ping → 返回pong心跳                                   │
│  │     ├─ reconnect → 断线重连逻辑（conversationId恢复）        │
│  │     ├─ 普通文本消息 → 进入部门聊天流程                        │
│  ├─ 2.3 调用DepartmentChatService.handleDepartmentChat()       │
│  ├─ 2.4 更新最后活动时间（sessionLastActive）                   │
│  ├─ 2.5 速率限制检查（WebSocketRateLimiter）                    │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 3: 部门聊天处理（DepartmentChatService）                  │
│  ├─ 3.1 消息落库（DepartmentChatMessageEntity）                 │
│  ├─ 3.2 调用ConversationOrchestrator.orchestrate()              │
│  ├─ 3.3 处理编排结果：                                          │
│  │     ├─ DIRECT_ANSWER → 直接返回                             │
│  │     ├─ NEEDS_CLARIFICATION → 返回澄清问题                   │
│  │     ├─ TASK → 进入任务执行链路                              │
│  │     ├─ PROJECT → 进入项目创建链路                            │
│  ├─ 3.4 调用部门大脑处理（BrainRegistry → TechBrain等）        │
│  ├─ 3.5 处理大脑响应：                                          │
│  │     ├─ 直接回复 → WebSocket返回                             │
│  │     ├─ 执行任务 → 进入执行协调链路                           │
│  ├─ 3.6 知识沉淀（KnowledgeCaptureService）                    │
│  ├─ 3.7 绩效记录（PerformanceCaptureService）                   │
│  ├─ 3.8 Trace日志记录（AutonomyTraceService）                   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 4: 响应返回（WebSocket推送）                                │
│  ├─ 4.1 结构化事件推送（pushExecutionEvent）：                  │
│  │     ├─ intake_classified（入口分类完成）                      │
│  │     ├─ main_brain_planned（主脑规划完成）                     │
│  │     ├─ readiness_evaluated（就绪度评估完成）                 │
│  │     ├─ clarification_requested（澄清请求）                   │
│  │     ├─ execution_started（执行开始）                          │
│  │     ├─ receipt_received（回执到达）                           │
│  │     ├─ finalized（最终完成）                                  │
│  ├─ 4.2 长任务进度推送（waiting_progress：每15秒）              │
│  ├─ 4.3 异步最终响应（async_final_response）                    │
│  ├─ 4.4 主动汇报推送（proactive_report）                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 5: 连接关闭（afterConnectionClosed）                       │
│  ├─ 5.1 移除会话映射（sessionToDepartment等）                   │
│  ├─ 5.2 清理连接注册表（ConnectionRegistry）                    │
│  ├─ 5.3 清理设备注册（ClientDeviceRegistryService）            │
│  ├─ 5.4 清理活跃计划缓存（activeSessionPlans超时30分钟）        │
│  ├─ 5.5 记录断开日志                                            │
└─────────────────────────────────────────────────────────────────┘
```

**关键代码路径**：
- WebSocket配置：`gateway/config/WebSocketConfig.java`
- 连接注册表：`gateway/websocket/ConnectionRegistry.java`
- 速率限制器：`gateway/websocket/WebSocketRateLimiter.java`

**错误处理**：
- 4001 TOKEN_EXPIRED：Token过期
- 4002 PERMISSION_DENIED：权限不足
- 4029 GLOBAL_CONNECTION_LIMIT：全局连接数限制
- 4030 DEPARTMENT_CONNECTION_LIMIT：部门连接数限制

### 3.3 AgentWebSocketHandler 详细流程

**文件路径**：`living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java`

**处理流程**：

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: 连接建立                                                │
│  ├─ 1.1 认证检查（getAuthContext）                              │
│  ├─ 1.2 提取agentId（extractAgentId）                           │
│  ├─ 1.3 员工类型判断：                                          │
│  │     ├─ FIXED → 固定数字员工检查权限                          │
│  │     ├─ EVOLVED → 动态员工检查权限                            │
│  │     ├─ HUMAN → 人类员工会话                                  │
│  ├─ 1.4 clientId绑定（WindowsAutomationGateway）                │
│  ├─ 1.5 僵尸连接检测启动（60秒超时）                            │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 2: 消息处理                                                │
│  ├─ 2.1 调用AgentService.startSession()                        │
│  ├─ 2.2 创建神经元会话（NeuronCoordinator.createSession）       │
│  ├─ 2.3 绑定通道（channel://agent/{agentId}/tasks）            │
│  ├─ 2.4 神经元处理：                                            │
│  │     ├─ Qwen3Neuron → 轻量聊天（闲聊/快速响应）              │
│  │     ├─ BitNetNeuron → 工具判断                               │
│  │     ├─ ToolNeuron → 工具调用                                 │
│  ├─ 2.5 NamedPipeModelClient → Python model_daemon.py          │
│  ├─ 2.6 语音处理（ASR → Sherpa-NCNN，TTS → MeloTTS）            │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 3: 响应返回                                                │
│  ├─ 3.1 文本响应（WebSocket推送）                               │
│  ├─ 3.2 音频响应（Base64编码传输）                              │
│  ├─ 3.3 工具调用结果                                            │
│  ├─ 3.4 错误响应（模型失败降级）                                │
└─────────────────────────────────────────────────────────────────┘
```

**关键区别**：
- AgentWebSocketHandler 用于通用Agent/语音链路，不用于部门文本业务
- DepartmentWebSocketHandler 用于部门文本聊天，走自治编排和部门大脑链路
- **两个链路不应混用**（重要约束）

---

## 4. 权限认证流程

### 4.1 权限级别定义

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/security/AccessLevel.java`

| 权限级别 | 值 | 可访问范围 | 用户类型 |
|---------|---|----------|---------|
| CHAT_ONLY | 0 | 仅Qwen3-0.6B模型 | 公共访客 |
| LIMITED | 1 | AdminBrain + CsBrain | 有限权限用户 |
| DEPARTMENT | 2 | 全部8个业务大脑 | 部门员工 |
| FULL | 3 | 所有大脑 + MainBrain（跨部门协调） | 董事长/高管 |

### 4.2 认证流程总览

```
┌─────────────────────────────────────────────────────────────────┐
│  用户登录入口                                                    │
│  ├─ 手机验证码登录（PhoneAuthController）                        │
│  ├─ OAuth登录（钉钉/飞书/企微 → OAuthService）                   │
│  ├─ Founder初始化（FounderService）                             │
│  └─ Token刷新（refreshToken轮换）                               │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  UnifiedAuthService（统一认证服务）                               │
│  ├─ 创建AuthSession（JWT Token + refreshToken）                │
│  ├─ 存储Session（SessionEntity → SessionRepository）            │
│  ├─ 设置AuthContext（employeeId/tenantId/accessLevel）         │
│  └─ Token生成规则：employee://human/{authProvider}/{accountId} │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  权限检查流程                                                    │
│  ├─ Step 1: Token验证（JwtAuthenticationFilter）                │
│  ├─ Step 2: AuthContext提取（AuthContextService）               │
│  ├─ Step 3: 访问级别判断（AccessGateService.canRoute）          │
│  ├─ Step 4: 部门权限校验（DepartmentAccessValidator）           │
│  ├─ Step 5: 大脑访问控制（BrainAccessControl）                  │
│  ├─ Step 6: 资源权限检查（PermissionService）                   │
│  ├─ Step 7: 工作项权限（WorkItemPermissionService）            │
│  └─ Step 8: RequireAccess注解AOP切面（统一入口）                │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ 权限充足        │     │ 权限不足         │
│                │     │                 │
│ 进入业务流程    │     │ 返回403 FORBIDDEN │
│                │     │ 或4001 TOKEN_EXPIRED │
└────────────────┘     └─────────────────┘
```

### 4.3 关键权限检查点

**文件路径**：
- 统一认证：`core/security/auth/UnifiedAuthService.java`
- 访问网关：`core/security/AccessGateService.java`
- 部门访问：`core/security/DepartmentAccessValidator.java`
- 大脑访问：`core/security/BrainAccessControl.java`
- AOP切面：`gateway/security/RequireAccess.java` + `RequireAccessAspect.java`

**关键API端点**：
- `GET /api/auth/check`：权限检查，返回当前用户访问级别和可访问大脑

---

## 5. 自治编排流程（Autonomy）

### 5.1 自治编排总流程图

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/autonomy/`

**核心职责**：对话自治系统，处理"这条消息该怎么处理"（交通指挥官）

**与autonomous包区别**（重要）：
- `autonomy` = 对话自治（实时处理用户消息）
- `autonomous` = 经济自治（后台定时处理员工赚钱/进化）

```
┌─────────────────────────────────────────────────────────────────┐
│  ConversationOrchestrator.orchestrate()                         │
│  ├─ 入口总编排                                                   │
│  ├─ 需求就绪评估                                                 │
│  ├─ 主脑规划                                                     │
│  ├─ 部门路由                                                     │
│  ├─ 执行协调                                                     │
│  └ 回执处理                                                      │
│  └ 最终响应                                                       │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 1: 需求就绪评估                                            │
│  ├─ RequirementReadinessEvaluator                               │
│  │     ├─ LlmBased（LLM评估需求清晰度）                           │
│  │     └─ Default（7条规则兜底：空消息/过短/动作词/疑问词等）       │
│  ├─ 结果：SUFFICIENT / PARTIALLY_SUFFICIENT / INSUFFICIENT      │
│  ├─ INSUFFICIENT → MainBrainRequirementClarifier生成澄清问题    │
│  │     → 直接返回澄清消息，不进入后续流程                         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 2: 意图分析                                                │
│  ├─ DialogueAnalyzer                                            │
│  │     ├─ LlmBasedDialogueAnalyzer（LLM优先 → Rule降级）        │
│  │     │     ├─ 通过MainBrain.callLlm()调用LLM                  │
│  │     │     ├─ 输出：IntakeClassification                      │
│  │     │     │     ├─ 是否需要主脑规划                           │
│  │     │     │     ├─ 是否可能跨部门                             │
│  │     │     │     ├─ requiresClarification字段                 │
│  │     │     │     └─ riskReasons字段                           │
│  │     │     └─ LLM失败 → 自动降级到RuleBased                    │
│  │     ├─ RuleBasedDialogueAnalyzer（关键词规则兜底）            │
│  │     │     ├─ 动作词检测：做/写/开发/设计/创建/修改/删除...    │
│  │     │     ├─ 疑问词检测：?/吗/什么/怎么/为什么...            │
│  │     │     ├─ 任务类型识别：TASK/PROJECT/INFO/CHAT/APPROVAL  │
│  │     │     └─ 关键部门关键词：代码→tech, 预算→finance...      │
│  ├─ 输出：DialogueDecision                                      │
│  │     ├─ messageType（CHAT/TASK/PROJECT/INFO/APPROVAL）        │
│  │     ├─ taskType（具体任务类型）                               │
│  │     ├─ suggestedDepartment（建议部门）                        │
│  │     ├─ complexity（复杂度1-10）                               │
│  │     ├─ clarificationQuestions（澄清问题列表）                 │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 3: 路由分类                                                │
│  ├─ TaskRouteClassifier（轻量路由层，P0新增）                    │
│  │     ├─ DefaultTaskRouteClassifier                            │
│  │     │     ├─ 7条路由规则：                                    │
│  │     │     │     ├─ 规则1: CROSS_DEPARTMENT kind → 主脑拆解   │
│  │     │     │     ├─ 规则2: 非TASK/PROJECT → 单部门直达         │
│  │     │     │     ├─ 规则3: requiresClarification → 澄清       │
│  │     │     │     ├─ 规则4: 有supportingDepts → 主脑           │
│  │     │     │     ├─ 规则5: 部门一致且无协作 → 单部门直达        │
│  │     │     │     ├─ 规则6: 部门不一致 → 主脑                   │
│  │     │     │     ├─ 规则7: complexity>2 → 主脑拆解            │
│  │     │     │     └─ 兜底 → 主脑                               │
│  ├─ 输出：TaskRouteResult                                       │
│  │     ├─ SINGLE_DEPARTMENT → 直接路由到部门大脑                │
│  │     ├─ CROSS_DEPARTMENT → 进入主脑规划                       │
│  │     └─ CLARIFICATION_NEEDED → 直接返回澄清                   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ 单部门直达      │     │ 跨部门主脑拆解   │
│                │     │                 │
│ BrainRoutingDecision │ MainBrainTaskDirector
│ └ 路由到目标部门大脑 │ └ LLM规划任务
│                │     │ └ 生成MainBrainTaskPlan
└────────┬───────┘     └────────┬────────┘
         │                      │
         │                      │
┌────────▼──────────────────────▼───────────────────────────────┐
│  Step 4: 任务规划                                                │
│  ├─ MainBrainTaskDirector                                       │
│  │     ├─ LlmBasedMainBrainTaskDirector（LLM优先）              │
│  │     │     ├─ 通过MainBrain.callLlm()调用LLM                  │
│  │     │     ├─ 动态注入员工画像（FixedEmployeeRegistry）        │
│  │     │     ├─ 输出：MainBrainTaskPlan                         │
│  │     │     │     ├─ taskType（任务类型）                       │
│  │     │     │     ├─ goal（任务目标）                           │
│  │     │     │     ├─ primaryDepartment（主责部门）              │
│  │     │     │     ├─ supportingDepartments（支持部门）          │
│  │     │     │     ├─ deliverables（交付物）                     │
│  │     │     │     ├─ acceptanceCriteria（验收标准）             │
│  │     │     │     ├─ departmentPlans（部门计划列表）            │
│  │     │     │     ├─ executionCapability（执行能力枚举）        │
│  │     │     │     ├─ artifactType（产物类型枚举）               │
│  │     │     │     ├─ executionMode（执行模式枚举）              │
│  │     │     │     ├─ requirementStatus（需求状态：NEEDS_CLARIFICATION/CONFIRMED）│
│  │     │     │     └─ isRequirementFrozen()（需求冻结判定）      │
│  │     │     └─ LLM失败 → 自动降级到RuleBased                    │
│  │     ├─ RuleBasedMainBrainTaskDirector（规则兜底）            │
│  │     │     ├─ 关键词映射：代码→tech, 预算→finance...          │
│  │     │     ├─ 交付物检测：detectDeliverables()（10+任务类型）   │
│  │     │     ├─ 验收标准检测：detectAcceptanceCriteria()       │
│  │     │     └ 覆盖全部8个部门                                  │
│  ├─ 部门级计划：DepartmentTaskPlan                              │
│  │     ├─ departmentGoal（部门目标）                             │
│  │     ├─ suggestedRole（建议角色）                              │
│  │     ├─ suggestedEmployee（建议员工）                          │
│  │     ├─ deliverables（部门交付物）                             │
│  │     └─ acceptanceCriteria（部门验收标准）                     │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 5: 员工分派                                                │
│  ├─ FixedEmployeeDispatcher                                     │
│  │     ├─ LlmBasedFixedEmployeeDispatcher（LLM优先）            │
│  │     │     ├─ 通过MainBrain.callLlm()调用LLM                  │
│  │     │     ├─ 根据员工能力/负载/历史绩效动态选人               │
│  │     │     ├─ 输出：EmployeeWorkAssignment列表                │
│  │     │     │     ├─ employeeCode（员工编码：T01/T02/T09...）   │
│  │     │     │     ├─ neuronId（神经元ID）                       │
│  │     │     │     ├─ role（角色）                               │
│  │     │     │     ├─ instruction（指令）                        │
│  │     │     │     ├─ requiredTools（所需工具）                  │
│  │     │     │     ├─ deliverables（交付物）                     │
│  │     │     │     ├─ worktreePath（代码任务专用）               │
│  │     │     │     └─ diffPath（代码任务专用）                   │
│  │     │     ├─ 异常短响应(<20字符)自动重试1次（P1-2）            │
│  │     │     └─ LLM失败 → 自动降级到RegistryBacked              │
│  │     ├─ RegistryBackedFixedEmployeeDispatcher（规则兜底）      │
│  │     │     ├─ 查询FixedEmployeeRegistry                       │
│  │     │     ├─ requiredTools硬性约束过滤                        │
│  │     │     ├─ suggestedRoles能力匹配排序                       │
│  │     │     └ 无工具匹配 → 降级到能力匹配                        │
│  ├─ 固定员工定义：FixedEmployeeDefinitionEntity                  │
│  │     ├─ 32个固定员工（tech部门9个，其他部门各有员工）           │
│  │     ├─ 每个员工有专属能力/工具/职责                           │
│  │     ├─ downstreamReviewers字段（审查关系：T09↔T10交叉审查）   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 6: 任务单准备                                              │
│  ├─ AssignmentPreparationService                                │
│  │     ├─ DefaultAssignmentPreparationService                   │
│  │     │     ├─ 将MainBrainTaskPlan + DepartmentTaskPlan       │
│  │     │     │   + EmployeeWorkAssignment整理为批次             │
│  │     │     ├─ 集成ExecutionCapabilityResolver                 │
│  │     │     │     ├─ LlmBasedExecutionCapabilityResolver      │
│  │     │     │     │     ├─ LLM语义判断执行能力                 │
│  │     │     │     │     └ 规则置信度不足时调用LLM              │
│  │     │     │     ├─ DefaultExecutionCapabilityResolver       │
│  │     │     │     │     ├─ 规则兜底：关键词评分+枚举校验        │
│  │     │     │     │     ├─ 无法归一 → NEEDS_CLARIFICATION      │
│  │     │     │     ├─ ExecutionCapability枚举（18种）           │
│  │     │     │     │     ├─ WEB_DEVELOPMENT                    │
│  │     │     │     │     ├─ DOCUMENT_GENERATION                │
│  │     │     │     │     ├─ CODE_REVIEW                        │
│  │     │     │     │     ├─ DATA_ANALYSIS                      │
│  │     │     │     │     ├─ FILE_SYSTEM_QUERY（新增）           │
│  │     │     │     │     ├─ PROJECT_MANAGEMENT（新增）          │
│  │     │     │     │     ├─ ISSUE_TRACKING（新增）              │
│  │     │     │     │     └ 共18种执行能力                       │
│  │     │     │     ├─ ExecutionMode枚举（7种）                   │
│  │     │     │     │     ├─ TOOL_EXECUTION（新增）              │
│  │     │     │     │     └ 共7种执行模式                        │
│  │     │     │     ├─ 输出：executionCapability                 │
│  │     │     │               + artifactType                     │
│  │     │     │               + executionMode                    │
│  │     │     ├─ 输出：PreparedAssignmentBatch                    │
│  │     │     │     ├─ batchId（批次ID）                          │
│  │     │     │     ├─ assignments（员工任务单列表）              │
│  │     │     │     ├─ departmentGoal（部门目标）                 │
│  │     │     │     └─ preparationStatus（准备状态）              │
│  ├─ AssignmentReadinessEvaluator                                │
│  │     ├─ LlmAssignmentReadinessEvaluator                       │
│  │     │     ├─ LLM评估任务分派准备度                           │
│  │     │     ├─ 评估维度：目标清晰度/员工合适度/交付物明确度      │
│  │     │     └ 输出：READY/BLOCKED/NEEDS_CLARIFICATION         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 7: 执行协调                                                │
│  ├─ DepartmentExecutionCoordinator                              │
│  │     ├─ ChannelBackedDepartmentExecutionCoordinator           │
│  │     │     ├─ 将准备批次推进到员工执行通道                     │
│  │     │     ├─ 发布到channel://employee/{neuron}/tasks        │
│  │     │     ├─ 写入回执channel元数据                           │
│  │     │     ├─ 输出：DepartmentExecutionResult                 │
│  │     │     │     ├─ executionId（执行ID）                     │
│  │     │     │     ├─ batchId                                   │
│  │     │     │     ├─ dispatchStatus（派发状态）                │
│  │     │     │     └─ employeeDispatches（员工派发列表）        │
│  │     │     └─ EmployeeExecutionDispatch                       │
│  │     │         ├─ dispatchId                                  │
│  │     │         ├─ assignmentId                                │
│  │     │         ├─ targetChannel                               │
│  │     │         └─ dispatchStatus                              │
│  ├─ 跨部门协调：CrossDepartmentCoordinator                      │
│  │     ├─ DefaultCrossDepartmentCoordinator                     │
│  │     │     ├─ 收集各部门执行结果                              │
│  │     │     ├─ 标记失败部门                                    │
│  │     │     ├─ 聚合整体状态                                    │
│  │     │     ├─ needsCrossDepartmentCoordination()判断         │
│  │     │     └ coordinate()聚合多部门结果                       │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 8: 员工执行                                                │
│  ├─ DynamicEmployeeTaskConsumerRegistry                         │
│  │     ├─ 启动时自动注册所有真实员工消费者                        │
│  │     ├─ 监听channel://employee/{neuron}/tasks                │
│  │     ├─ 接收到任务消息 → EmployeeTaskExecutor.executeTask()  │
│  │     ├─ ToolBackedEmployeeTaskExecutor                        │
│  │     │     ├─ 按ExecutionCapability分发执行                   │
│  │     │     │     ├─ WEB_DEVELOPMENT → executeWebTask         │
│  │     │     │     ├─ DOCUMENT_GENERATION → executeDocumentTask│
│  │     │     │     ├─ CODE_REVIEW → executeReviewTask          │
│  │     │     │     ├─ FILE_SYSTEM_QUERY → executeToolTask      │
│  │     │     │     ├─ PROJECT_MANAGEMENT → executeProjectTask  │
│  │     │     │     └ 共14种任务类型路由                         │
│  │     │     ├─ resolveToolInvocation（意图→工具解析）          │
│  │     │     ├─ formatToolResult（结果格式化）                  │
│  │     │     ├─ 执行结果：EmployeeTaskExecutionOutcome         │
│  │     │     │     ├─ status（COMPLETED/FAILED/DEGRADED）       │
│  │     │     │     ├─ artifacts（产物列表）                     │
│  │     │     │     ├─ needsRetry（是否需要重试）                │
│  │     │     │     └─ needsHumanReview（需人工审核）            │
│  ├─ 部门内审查闭环（P0新增）                                      │
│  │     ├─ InternalReviewService                                 │
│  │     │     ├─ submitForReview（提交审查）                      │
│  │     │     ├─ review（审查决定：APPROVED/REVISION_NEEDED...） │
│  │     │     ├─ 审查状态机：SUBMITTED_FOR_REVIEW→COMPLETED      │
│  │     │     ├─ 超轮次自动escalate                             │
│  │     │     ├─ ReviewListener回调机制                          │
│  │     │     │     └ REVISION_NEEDED → 重新发布任务消息         │
│  │     │     ├─ FixedEmployeeDefinition.downstreamReviewers    │
│  │     │     │     └ 技术部T09↔T10交叉审查关系                 │
│  ├─ 员工自行领取（P1新增）                                        │
│  │     ├─ DepartmentTodoPool                                     │
│  │     │     ├─ publish（发布待办项）                            │
│  │     │     ├─ claim（乐观锁领取）                              │
│  │     │     ├─ assign（兜底指派）                               │
│  │     │     ├─ DepartmentTodoItem                               │
│  │     │     │     ├─ Status（PENDING/CLAIMED/COMPLETED）       │
│  │     │     │     ├─ Priority（HIGH/MEDIUM/LOW）               │
│  │     │     │     ├─ claimVersion（乐观锁）                     │
│  │     │     ├─ EmployeeSelfClaimService                        │
│  │     │     │     ├─ tryClaim（资格校验+负载检查）              │
│  │     │     │     ├─ tryClaimBestMatch（最佳匹配领取）          │
│  │     │     │     ├─ assignUnclaimed（兜底指派）               │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 9: 回执处理                                                │
│  ├─ EmployeeExecutionReceiptService                             │
│  │     ├─ JpaEmployeeExecutionReceiptService（生产默认）        │
│  │     │     ├─ 持久化到PostgreSQL                             │
│  │     │     ├─ EmployeeExecutionReceiptEntity                  │
│  │     │     │     ├─ executionId                               │
│  │     │     │     ├─ employeeCode                              │
│  │     │     │     ├─ status（PENDING/COMPLETED/FAILED）        │
│  │     │     │     ├─ summary                                   │
│  │     │     │     ├─ artifacts                                 │
│  │     │     │     ├─ worktreePath                              │
│  │     │     │     ├─ diffPath                                  │
│  │     │     ├─ ReceiptListener（回执到达后通知WebSocket推送）   │
│  │     │     ├─ ReceiptStatus枚举                                │
│  │     │     │     ├─ PENDING/COMPLETED/FAILED/TIMEOUT/RETRYING│
│  │     ├─ FileBasedEmployeeExecutionReceiptService（降级备用）   │
│  │     │     ├─ JSON文件持久化到data/receipts/                  │
│  ├─ 回执审核：ExecutionReceiptReviewer                          │
│  │     ├─ LlmExecutionReceiptReviewer                           │
│  │     │     ├─ LLM对比回执与验收标准                           │
│  │     │     ├─ 输出质量分和未满足项                            │
│  │     ├─ DefaultExecutionReceiptReviewer                       │
│  │     │     ├─ 降级验收严格化：                                │
│  │     │     │     ├─ 摘要为空 → rejected                       │
│  │     │     │     ├─ 验收标准未满足 → rejected                 │
│  │     │     │     ├─ 期望产物但无文件 → rejected+needsRetry    │
│  │     │     │     ├─ 检查worktreePath/diffPath/artifactPaths  │
│  │     │     ├─ 输出：ExecutionReviewResult                     │
│  │     │     │     ├─ status（passed/needsRework/failed）       │
│  │     │     │     ├─ issues（问题列表）                        │
│  │     │     │     ├─ retryable（是否可重试）                   │
│  ├─ 部门级聚合（P1新增）                                          │
│  │     ├─ DepartmentAggregationService                          │
│  │     │     ├─ DefaultDepartmentAggregationService             │
│  │     │     │     ├─ 收集回执 → 检查审查状态                   │
│  │     │     │     ├─ 构建交付项 → 计算质量分                   │
│  │     │     │     ├─ 确定聚合状态                              │
│  │     │     ├─ LlmDepartmentAggregationService                 │
│  │     │     │     ├─ 规则版聚合 → LLM语义分析                  │
│  │     │     │     ├─ 一致性检查 → 问题发现 → 修复建议          │
│  │     │     │     ├─ LLM失败 → 自动降级到规则版                │
│  │     │     ├─ 输出：AggregationResult                         │
│  │     │     │     ├─ success/partial/qualityIssues            │
│  │     │     ├─ DepartmentDeliverable                           │
│  │     │     │     ├─ AggregationStatus                         │
│  │     │     │     └ DeliverableItem列表                       │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 10: 最终响应                                               │
│  ├─ FinalResponseCoordinator                                    │
│  │     ├─ LlmBasedFinalResponseCoordinator（LLM优先）           │
│  │     │     ├─ 通过MainBrain.callLlm()根据上下文选择回复策略   │
│  │     │     ├─ 7种回复策略：                                    │
│  │     │     │     ├─ DIRECT_ANSWER                             │
│  │     │     │     ├─ ASK_CLARIFICATION                         │
│  │     │     │     ├─ MAIN_BRAIN_COMPOSE                        │
│  │     │     │     ├─ WAIT_FOR_RECEIPTS                         │
│  │     │     │     ├─ DEPARTMENT_BRAIN_DIRECT                   │
│  │     │     │     ├─ ESCALATE_TO_HUMAN                         │
│  │     │     │     └─ REQUEST_APPROVAL                          │
│  │     │     ├─ LLM失败 → 降级到Default                         │
│  │     ├─ DefaultFinalResponseCoordinator（规则兜底）           │
│  │     │     ├─ 规则判断回复策略                                │
│  ├─ 响应编排：MainBrainResponseComposer                         │
│  │     ├─ LlmBasedMainBrainResponseComposer                     │
│  │     │     ├─ LLM根据执行结果生成自然语言回复                  │
│  │     │     ├─ LLM失败 → 降级到模板拼接                        │
│  │     ├─ DefaultMainBrainResponseComposer                      │
│  │     │     ├─ 模板式响应生成                                  │
│  ├─ 执行结果聚合：ExecutionResultAggregator                     │
│  │     ├─ LlmBasedExecutionResultAggregator                     │
│  │     │     ├─ LLM汇总执行结果 + 回执审核                      │
│  │     ├─ DefaultExecutionResultAggregator                      │
│  │     │     ├─ 规则聚合                                        │
│  ├─ 主脑最终总结：MainBrainFinalSummaryService                  │
│  │     ├─ LlmBased（LLM生成结构化总结）                          │
│  │     ├─ Default（模板总结）                                   │
│  │     ├─ 输出：FinalSummaryResult                              │
│  │     │     ├─ status                                          │
│  │     │     ├─ summary                                         │
│  │     │     ├─ artifacts                                       │
│  │     │     ├─ risks                                           │
│  │     │     └ suggestions                                      │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 11: 知识沉淀与绩效记录                                      │
│  ├─ KnowledgeCaptureService                                     │
│  │     ├─ DefaultKnowledgeCaptureService                        │
│  │     │     ├─ 从执行结果提取经验                              │
│  │     │     ├─ 写入KnowledgeManager（EXPERIENCE类型）          │
│  │     │     ├─ L1→L2→L3晋升评估                                │
│  │     │     ├─ KnowledgeQualityEvaluator（质量评估）            │
│  │     │     │     ├─ confidence/accessCount/verified评分       │
│  │     │     ├─ KnowledgePromotionScheduler（自动晋升调度）      │
│  │     │     │     ├─ 每10分钟检查晋升条件                      │
│  │     │     │     ├─ PRIVATE→DOMAIN/SHARED自动晋升             │
│  │     ├─ 输出：KnowledgeCaptureResult                           │
│  │     │     ├─ success                                         │
│  │     │     ├─ knowledgeKey                                    │
│  │     │     ├─ layer                                           │
│  ├─ PerformanceCaptureService                                   │
│  │     ├─ DefaultPerformanceCaptureService                      │
│  │     │     ├─ 通过LedgerService记录绩效积分                   │
│  │     │     ├─ 完成任务自动发放积分奖励                        │
│  │     ├─ PerformanceStatsService                               │
│  │     │     ├─ DefaultPerformanceStatsService                  │
│  │     │     │     ├─ 从LedgerService聚合经济数据               │
│  │     │     │     ├─ getStats（员工绩效统计）                  │
│  │     │     │     ├─ getDepartmentRanking（部门排名）          │
│  │     ├─ 输出：PerformanceCaptureResult                        │
│  │     │     ├─ employeeCode                                    │
│  │     │     ├─ executionId                                     │
│  │     │     ├─ contributionType                                │
│  ├─ ArtifactRecordService                                       │
│  │     ├─ JpaArtifactRecordService                              │
│  │     │     ├─ 持久化产物记录到artifact_records表             │
│  │     │     ├─ ArtifactRecordEntity                            │
│  │     │     │     ├─ artifactId                                │
│  │     │     │     ├─ executionId                               │
│  │     │     │     ├─ department                                │
│  │     │     │     ├─ employee                                  │
│  │     │     │     ├─ type                                      │
│  │     │     │     ├─ path                                      │
│  │     │     │     ├─ size                                      │
│  │     │     │     ├─ sha256                                    │
│  │     │     │     ├─ taskId                                    │
│  │     │     │     └ projectId                                  │
│  │     │     ├─ 支持分页查询/统计/目录扫描索引                   │
│  ├─ 产物元数据绑定：CodeArtifactMetadataBinder                  │
│  │     ├─ 注册worktree/diff/review_report/final_summary        │
│  │     ├─ 关联CodeReviewWorkflowService                         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 12: Trace日志记录                                          │
│  ├─ AutonomyTraceService                                        │
│  │     ├─ 记录自治流程追踪日志                                   │
│  │     ├─ TraceEventEntity持久化                                │
│  │     │     ├─ traceId                                         │
│  │     │     ├─ stage（意图分析/规划/路由/执行/回执）             │
│  │     │     ├─ eventType                                       │
│  │     │     ├─ timestamp                                       │
│  │     │     ├─ metadata                                        │
│  │     ├─ TraceEventRepository                                  │
│  │     │     ├─ 按traceId/stage/eventType查询                   │
│  │     │     ├─ 按executionId关联查询                           │
│  ├─ StandardComplianceTraceService                              │
│  │     ├─ 规范合规追踪                                           │
│  │     │     ├─ 边界检查事件                                    │
│  │     │     ├─ 标准加载事件                                    │
│  │     │     ├─ 澄清事件                                        │
│  │     │     ├─ 升级事件                                        │
│  │     │     ├─ 回执合规事件                                    │
│  │     │     ├─ 权限检查事件                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 关键数据结构

**MainBrainTaskPlan**：
```java
{
  taskType: "WEB_DEVELOPMENT",
  goal: "创建企业官网首页",
  primaryDepartment: "tech",
  supportingDepartments: ["design", "hr"],
  deliverables: ["HTML文件", "CSS样式", "JavaScript交互"],
  acceptanceCriteria: ["页面正常显示", "响应式布局", "加载速度<2秒"],
  departmentPlans: [
    {
      departmentGoal: "实现前端页面",
      suggestedRole: "frontend_developer",
      suggestedEmployee: "T03",
      deliverables: ["HTML/CSS/JS"],
      acceptanceCriteria: ["代码规范", "功能完整"]
    }
  ],
  executionCapability: "WEB_DEVELOPMENT",
  artifactType: "HTML_FILE",
  executionMode: "TOOL_EXECUTION",
  requirementStatus: "REQUIREMENT_CONFIRMED",
  isRequirementFrozen: true
}
```

**EmployeeWorkAssignment**：
```java
{
  employeeCode: "T03",
  neuronId: "neuron://tech/frontend_developer/T03",
  role: "前端开发",
  instruction: "实现企业官网首页的前端部分",
  requiredTools: ["browser_automation", "file_edit"],
  deliverables: ["index.html", "style.css", "main.js"],
  worktreePath: "/data/worktrees/T03-web-dev",
  diffPath: "/data/diffs/T03-web-dev.patch"
}
```

**EmployeeExecutionReceipt**：
```java
{
  executionId: "exec-12345",
  employeeCode: "T03",
  status: "COMPLETED",
  summary: "完成企业官网首页前端开发",
  artifacts: [
    {
      type: "HTML_FILE",
      path: "/data/artifacts/exec-12345/index.html",
      size: 2048,
      sha256: "abc123..."
    }
  ],
  worktreePath: "/data/worktrees/T03-web-dev",
  diffPath: "/data/diffs/T03-web-dev.patch"
}
```

---

## 6. 业务大脑处理流程（Brain）

### 6.1 大脑架构总览

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/brain/`

**大脑类型**：

| 大脑 | 部门 | 职责 | 技能数 | 模型选择器 |
|------|------|------|-------|-----------|
| MainBrain | 跨部门 | 战略规划/跨部门协调/复杂推理 | - | MainBrainModelSelector |
| TechBrain | 技术部 | 技术任务/代码开发/架构设计 | 25 | TechBrainModelSelector |
| HrBrain | 人力资源 | 招聘/绩效/组织管理 | 3 | HrBrainModelSelector |
| FinanceBrain | 财务部 | 预算/报销/发票管理 | 4 | FinanceBrainModelSelector |
| SalesBrain | 销售部 | 客户管理/销售线索 | 4 | SalesBrainModelSelector |
| CsBrain | 客服部 | 客诉处理/工单管理 | 3 | CsBrainModelSelector |
| AdminBrain | 行政部 | 行政事务/办公管理 | 15 | AdminBrainModelSelector |
| LegalBrain | 法务部 | 合同审查/合规管理 | 3 | LegalBrainModelSelector |
| OpsBrain | 运营部 | 数据运营/流程优化 | 9 | OpsBrainModelSelector |

### 6.2 大脑处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│  BrainRegistry（大脑注册表）                                      │
│  ├─ BrainRegistryImpl                                           │
│  │     ├─ 注册所有大脑实例                                       │
│  │     │     ├─ MainBrain（主脑）                                │
│  │     │     ├─ TechBrain（技术部大脑）                          │
│  │     │     ├─ HrBrain（人力资源大脑）                          │
│  │     │     ├─ FinanceBrain（财务部大脑）                       │
│  │     │     ├─ SalesBrain（销售部大脑）                         │
│  │     │     ├─ CsBrain（客服部大脑）                            │
│  │     │     ├─ AdminBrain（行政部大脑）                         │
│  │     │     ├─ LegalBrain（法务部大脑）                         │
│  │     │     └ OpsBrain（运营部大脑）                            │
│  │     ├─ getBrain(String brainId) → Brain实例                  │
│  │     ├─ 路由映射：dept → Brain                                │
│  │     │     ├─ tech → TechBrain                                │
│  │     │     ├─ hr → HrBrain                                    │
│  │     │     ├─ finance → FinanceBrain                          │
│  │     │     └ 共8个部门大脑                                    │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  AbstractBrain（公共基类）                                        │
│  ├─ 核心处理流程                                                  │
│  │     ├─ ReAct循环（拆分到BrainReActEngine）                   │
│  │     │     ├─ Thought（思考）                                  │
│  │     │     ├─ Action（决定）                                   │
│  │     │     │     ├─ 工具调用                                   │
│  │     │     │     ├─ 模型调用                                   │
│  │     │     │     ├─ 返回结果                                   │
│  │     │     ├─ Observation（观察结果）                          │
│  │     │     ├─ 循环直到达到maxToolIterations（20次）           │
│  │     │     ├─ 达到限制 → 强制LLM生成最终响应                  │
│  │     ├─ 模型调用（集成BrainModelFallback）                     │
│  │     │     ├─ BrainModelResolver解析模型分配                  │
│  │     │     ├─ LlmClient统一调用                               │
│  │     │     ├─ 模型失败 → 降级到备用Provider                   │
│  │     │     ├─ 熔断检查（ModelHealthRegistry）                 │
│  │     ├─ 工具集成                                                │
│  │     │     ├─ buildDynamicToolList()（动态工具列表，P2-2）     │
│  │     │     ├─ ToolRegistry查找工具                            │
│  │     │     ├─ DefaultToolExecutor执行                         │
│  │     │     ├─ 工具部门隔离（BRAIN_TOOL_DEPARTMENT_MAPPING）   │
│  │     │     │     ├─ TechBrain → 仅技术相关工具                │
│  │     │     │     ├─ MainBrain → 所有工具                      │
│  │     │     │     └ 8个业务大脑按部门过滤                      │
│  │     ├─ 上下文管理                                                │
│  │     │     ├─ BrainSessionManager（会话历史，TTL30分钟）       │
│  │     │     │     ├─ ConcurrentHashMap缓存                     │
│  │     │     │     ├─ 最大500会话驱逐                           │
│  │     │     │     ├─ 手动TTL清理                               │
│  │     │     ├─ BrainContext                                    │
│  │     │     │     ├─ userId                                    │
│  │     │     │     ├─ department                                │
│  │     │     │     ├─ conversationId                            │
│  │     │     │     ├─ taskKey                                   │
│  │     │     │     ├─ executionId                               │
│  │     │     │     ├─ 权限级别                                   │
│  │     │     │     └ 元数据                                     │
│  │     ├─ 提示词构建                                                │
│  │     │     ├─ DynamicPromptBuilder                            │
│  │     │     │     ├─ 部门职责注入                              │
│  │     │     │     ├─ 员工职责注入                              │
│  │     │     │     ├─ 知识上下文注入                            │
│  │     │     │     ├─ 工具描述注入                              │
│  │     │     ├─ StandardLoadingChainService                     │
│  │     │     │     ├─ 职责卡 → Prompt → runbook                │
│  │     │     │     └ 文档工作流 → 自定义指令                     │
│  │     │     ├─ SYSTEM_PROMPT_TEMPLATE（动态替换）              │
│  │     ├─ 输出契约：BrainOutputContract                          │
│  │     │     ├─ status                                          │
│  │     │     ├─ summary                                         │
│  │     │     ├─ plan                                            │
│  │     │     ├─ clarificationQuestions                          │
│  │     │     ├─ blockingIssues                                  │
│  │     │     ├─ riskLevel                                       │
│  │     │     ├─ conversationId                                  │
│  │     │     ├─ taskKey                                         │
│  │     │     └ executionId                                      │
│  │     ├─ publishResponse()                                     │
│  │     │     ├─ 检查requires_response元数据                     │
│  │     │     ├─ 自动回传部门响应到主脑                           │
│  │     │     │     └ NP1-3: 通过ChannelManager转发              │
│  ├─ 职责边界检查：BrainBoundaryEnforcer                          │
│  │     ├─ allowedActions（允许的操作）                           │
│  │     ├─ forbiddenActions（禁止的操作）                         │
│  │     ├─ escalationTriggers（升级触发条件）                     │
│  │     ├─ mustEscalateScenarios（必须升级场景）                  │
│  │     ├─ 9个大脑各有专属边界定义                                │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ MainBrain      │     │ 部门大脑         │
│                │     │                 │
│ 跨部门协调      │     │ 单部门业务处理   │
└────────┬───────┘     └────────┬────────┘
         │                      │
         │                      │
┌────────▼──────────────────────▼───────────────────────────────┐
│  MainBrain特殊流程                                               │
│  ├─ forwardToDepartment()                                       │
│  │     ├─ 通过ChannelManager转发消息到目标部门大脑输入通道       │
│  │     ├─ 携带协调元数据：                                      │
│  │     │     ├─ coordination_session_id                        │
│  │     │     ├─ forwarded_by                                   │
│  │     │     ├─ original_user_id                               │
│  │     ├─ 目标通道：channel://brain/{brainId}/input            │
│  ├─ handleDepartmentResponse()                                  │
│  │     ├─ 收集部门响应                                          │
│  │     ├─ 部门响应到达 → 记录到响应Map                          │
│  │     ├─ 等待所有部门响应（超时机制）                          │
│  ├─ aggregateDepartmentResponses()                              │
│  │     ├─ 汇总所有部门响应                                      │
│  │     ├─ 生成最终跨部门协调结果                                │
│  │     ├─ 决定是否需要进一步协调                                │
│  ├─ scheduleSessionTimeout()                                    │
│  │     ├─ 超时检查机制                                          │
│  │     │     ├─ 未收到响应 → 强制生成部分结果                   │
│  │     │     ├─ 记录超时部门                                    │
│  ├─ callLlm()（提供给自治编排使用）                              │
│  │     ├─ LLM调用接口                                           │
│  │     ├─ JSON Schema校验                                       │
│  │     ├─ 修复重试                                              │
│  │     ├─ 用于LlmBasedDialogueAnalyzer等                       │
│  └─ 集成到自治编排的各个LLM分析器                               │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  部门大脑特殊流程（以TechBrain为例）                             │
│  ├─ TechBrain.doProcess()                                       │
│  │     ├─ 技术任务判断                                          │
│  │     │     ├─ 代码任务 → 进入代码审查流程                     │
│  │     │     ├─ 开发任务 → 进入开发流程                         │
│  │     │     ├─ 架构任务 → 进入架构设计流程                     │
│  │     ├─ 代码审查流程：                                        │
│  │     │     ├─ CodeReviewWorkflowService                      │
│  │     │     │     ├─ 13阶段审查状态机                          │
│  │     │     │     │     ├─ PLAN_CREATED                       │
│  │     │     │     │     ├─ CODE_GENERATED                     │
│  │     │     │     │     ├─ REVIEW_STARTED                     │
│  │     │     │     │     ├─ REVIEW_COMPLETED                   │
│  │     │     │     │     ├─ CHANGES_REQUESTED                  │
│  │     │     │     │     ├─ REVISION_SUBMITTED                 │
│  │     │     │     │     ├─ FINAL_REVIEW                       │
│  │     │     │     │     ├─ APPROVED                           │
│  │     │     │     │     ├─ REJECTED                           │
│  │     │     │     │     ├─ MERGED                             │
│  │     │     │     │     ├─ ESCALATED                          │
│  │     │     │     │     ├─ FAILED                             │
│  │     │     │     │     ├─ ARCHIVED                           │
│  │     │     │     ├─ requestChanges/approve/escalate操作     │
│  │     │     │     ├─ MAX_REVIEW_ROUNDS限制                    │
│  │     │     │     ├─ 4种代码产物注册：                        │
│  │     │     │     │     ├─ worktree                           │
│  │     │     │     │     ├─ diff                               │
│  │     │     │     │     ├─ review_report                      │
│  │     │     │     │     └ final_summary                       │
│  │     │     ├─ JpaCodeReviewWorkflowService（生产持久化）      │
│  │     │     │     ├─ PostgreSQL持久化                         │
│  │     │     │     ├─ 重启不丢失                               │
│  │     ├─ 员工任务处理：                                        │
│  │     │     ├─ 判断是否需要派发员工任务                        │
│  │     │     ├─ 发布到员工通道                                  │
│  │     │     ├─ 等待员工回执                                    │
│  │     │     ├─ 收集执行结果                                    │
│  │     ├─ publishFallbackResponse()                            │
│  │     │     ├─ 空消息/空内容/异常时发布兜底响应                │
│  │     │     ├─ BrainOutputContract构建（DP0-2）                │
│  │     ├─ 员工任务中间状态：                                    │
│  │     │     ├─ "正在执行N个员工任务，请稍候..."（DP0-1）       │
│  │     │     ├─ triggerAsyncFinalResponse发送最终结果           │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  其他部门大脑                                                    │
│  ├─ HrBrain                                                     │
│  │     ├─ 招聘流程处理                                          │
│  │     ├─ 绩效管理                                              │
│  │     ├─ 组织架构管理                                          │
│  ├─ FinanceBrain                                                │
│  │     ├─ 预算管理                                              │
│  │     ├─ 报销处理                                              │
│  │     ├─ 发票管理                                              │
│  ├─ SalesBrain                                                  │
│  │     ├─ 客户管理                                              │
│  │     ├─ 销售线索                                              │
│  │     ├─ 市场分析                                              │
│  ├─ CsBrain                                                     │
│  │     ├─ 客诉处理                                              │
│  │     ├─ 工单管理                                              │
│  │     ├─ FAQ回复                                               │
│  ├─ AdminBrain                                                  │
│  │     ├─ 行政文档                                              │
│  │     ├─ 办公事务                                              │
│  │     ├─ 会议室管理                                            │
│  ├─ LegalBrain                                                  │
│  │     ├─ 合同审查                                              │
│  │     ├─ 合规管理                                              │
│  │     ├─ 风险评估                                              │
│  └ OpsBrain                                                     │
│  │     ├─ 数据运营                                              │
│  │     ├─ 流程优化                                              │
│  │     ├─ 报表生成                                              │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 关键代码路径

- 大脑接口：`core/brain/Brain.java`
- 注册表：`core/brain/BrainRegistry.java` → `impl/BrainRegistryImpl.java`
- 公共基类：`core/brain/impl/AbstractBrain.java`
- 主脑：`core/brain/impl/MainBrain.java`
- 技术脑：`core/brain/impl/TechBrain.java`
- 会话管理：`core/brain/impl/BrainSessionManager.java`
- ReAct引擎：`core/brain/impl/BrainReActEngine.java`
- 模型降级：`core/brain/impl/BrainModelFallback.java`
- 动态提示词：`core/brain/prompt/DynamicPromptBuilder.java`
- 职责边界：`core/brain/BrainBoundaryEnforcer.java`
- 输出契约：`core/brain/BrainOutputContract.java`
- 协作编排：`core/brain/collaboration/LeadOrchestrator.java`
- 上下文压缩：`core/brain/compact/HybridContextCompactor.java`

---

## 7. 三层LLM架构调用流程

### 7.1 三层架构总览

**架构设计**：分层调用，逐级降级

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: MainBrain（主脑层）                                    │
│  ├─ 职责：复杂推理/跨部门协调/战略规划                            │
│  ├─ 模型选择：动态模型池（BrainModelSelectorManager）            │
│  │     ├─ MainBrainModelSelector                               │
│  │     │     ├─ 从模型池选择最优模型                            │
│  │     │     ├─ 考虑健康状态+负载+能力                          │
│  │     ├─ 支持多种模型：                                        │
│  │     │     ├─ Claude系列（Anthropic）                         │
│  │     │     ├─ GPT系列（OpenAI Compatible）                    │
│  │     │     ├─ Qwen系列（本地NamedPipe）                       │
│  │     │     ├─ 其他OpenAI Compatible模型                       │
│  ├─ 权限要求：FULL（董事长/高管）                                │
│  ├─ 调用场景：                                                  │
│  │     ├─ LlmBasedDialogueAnalyzer（意图分析）                  │
│  │     ├─ LlmBasedMainBrainTaskDirector（任务规划）             │
│  │     ├─ LlmBasedFixedEmployeeDispatcher（员工分派）           │
│  │     ├─ LlmBasedFinalResponseCoordinator（出口策略）          │
│  │     ├─ 跨部门协调任务                                        │
│  │     ├─ 复杂推理任务                                          │
│  ├─ 降级策略：                                                  │
│  │     ├─ 模型失败 → BrainModelFallback降级到备用Provider       │
│  │     ├─ LLM失败 → 规则兜底（RuleBasedXXX）                    │
│  │     ├─ 熔断 → ModelHealthRegistry监控                       │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Layer 2: Qwen3Neuron（闲聊神经元层）                            │
│  ├─ 职责：闲聊/快速响应/轻量对话                                 │
│  ├─ 模型选择：固定模型（Qwen3/Qwen3.5）                          │
│  │     ├─ 本地NamedPipe通信                                     │
│  │     │     ├─ NamedPipeModelClient                            │
│  │     │     │     ├─ 创建session                              │
│  │     │     │     ├─ 写入请求pipe                             │
│  │     │     │     ├─ 读取响应pipe                             │
│  │     │     │     ├─ 超时控制                                 │
│  │     │     ├─ scripts/python/model_daemon.py                 │
│  │     │     │     ├─ 监听named pipe请求                       │
│  │     │     │     ├─ 调用本地Qwen3模型                        │
│  │     │     │     ├─ 返回响应                                 │
│  ├─ 权限要求：所有用户可访问（CHAT_ONLY及以上）                  │
│  ├─ 调用场景：                                                  │
│  │     ├─ 闲聊对话                                              │
│  │     ├─ 快速响应                                              │
│  │     ├─ 通用Agent链路                                         │
│  │     ├─ 语音会话                                              │
│  ├─ 特点：                                                      │
│  │     ├─ 不走模型池                                            │
│  │     ├─ 固定模型路径                                          │
│  │     ├─ 低延迟                                                │
│  │     ├─ 低成本                                                │
│  └─ 不等于部门大脑（重要区别）                                   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Layer 3: ToolNeuron/BitNetNeuron（工具判断层）                  │
│  ├─ 职责：工具检测/兜底处理                                      │
│  ├─ 模型选择：固定模型（BitNet）                                 │
│  │     ├─ 低资源模型                                            │
│  │     ├─ 本地推理                                              │
│  ├─ 权限要求：DEPARTMENT及以上                                  │
│  ├─ 调用场景：                                                  │
│  │     ├─ 工具调用判断                                          │
│  │     ├─ 快速工具选择                                          │
│  │     ├─ 兜底处理                                              │
│  ├─ 特点：                                                      │
│  │     ├─ 资源消耗低                                            │
│  │     ├─ 响应快                                                │
│  │     ├─ 适合工具判断                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 模型池调用流程

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: 模型选择                                                │
│  ├─ BrainModelSelectorManager                                   │
│  │     ├─ 注册所有BrainModelSelector                            │
│  │     │     ├─ MainBrainModelSelector                          │
│  │     │     ├─ TechBrainModelSelector                          │
│  │     │     ├─ HrBrainModelSelector                            │
│  │     │     ├─ FinanceBrainModelSelector                       │
│  │     │     ├─ SalesBrainModelSelector                         │
│  │     │     ├─ CsBrainModelSelector                            │
│  │     │     ├─ AdminBrainModelSelector                         │
│  │     │     ├─ LegalBrainModelSelector                         │
│  │     │     ├─ OpsBrainModelSelector                           │
│  │     │     ├─ ToolNeuronModelSelector                         │
│  │     ├─ selectModelForBrain(String brainId)                   │
│  │     │     ├─ 查找对应Selector                                │
│  │     │     ├─ 调用selectModel()                               │
│  │     │     ├─ 返回LlmModel                                    │
│  ├─ BrainModelSelector（各大脑选择器）                           │
│  │     ├─ 考虑因素：                                            │
│  │     │     ├─ 模型能力（ModelCapabilityAssessor）             │
│  │     │     ├─ 模型性能（ModelPerformanceAssessor）            │
│  │     │     ├─ 硬件负载（HardwareResourceMonitor）             │
│  │     │     │     ├─ GPU使用率                                 │
│  │     │     │     ├─ 内存使用率                                │
│  │     │     │     ├─ CPU使用率                                 │
│  │     │     ├─ 健康状态（ModelHealthRegistry）                 │
│  │     │     │     ├─ AVAILABLE（可用）                          │
│  │     │     │     ├─ DEGRADED（降级）                          │
│  │     │     │     ├─ COOLDOWN（冷却）                          │
│  │     │     │     ├─ UNAVAILABLE（不可用）                     │
│  │     │     │     ├─ UNKNOWN（未知）                           │
│  │     │     ├─ 成本因素                                        │
│  │     ├─ 输出：LlmModel                                        │
│  │     │     ├─ modelName                                       │
│  │     │     ├─ provider                                        │
│  │     │     ├─ type                                            │
│  │     │     ├─ version                                         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 2: 模型解析                                                │
│  ├─ BrainModelResolver                                          │
│  │     ├─ resolveBrainModel(String brainId)                     │
│  │     │     ├─ 查询BrainModelAssignment表                      │
│  │     │     │     ├─ brainId → modelId映射                     │
│  │     │     ├─ 获取LlmModel                                    │
│  │     │     ├─ 获取ProviderConfig                              │
│  │     │     ├─ 熔断检查（ModelHealthRegistry）                 │
│  │     │     │     ├─ 过滤UNAVAILABLE模型                      │
│  │     │     │     ├─ 过滤COOLDOWN模型（冷却未到期）            │
│  │     │     ├─ 输出：ResolvedBrainModel                        │
│  │     │     │     ├─ model                                     │
│  │     │     │     ├─ provider                                  │
│  │     │     │     ├─ baseUrl                                   │
│  │     │     │     ├─ apiKey                                    │
│  │     │     │     ├─ protocol                                  │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 3: 客户端创建                                              │
│  ├─ LlmClientFactory                                            │
│  │     ├─ createClient(ProviderConfig)                          │
│  │     │     ├─ Provider类型判断：                              │
│  │     │     │     ├─ OpenAI Compatible → OpenAiCompatibleClient│
│  │     │     │     ├─ Anthropic → AnthropicClient               │
│  │     │     │     ├─ Ollama → OllamaClient                     │
│  │     │     │     ├─ NamedPipe → NamedPipeModelClient          │
│  │     │     │     ├─ BitNet → BitNetClient                     │
│  │     │     ├─ 输出：LlmClient                                  │
│  ├─ LlmClient（统一调用接口）                                    │
│  │     ├─ call(ModelRequest) → ModelResponse                    │
│  │     │     ├─ 统一请求格式                                    │
│  │     │     ├─ 统一响应格式                                    │
│  │     │     ├─ 超时控制                                        │
│  │     │     ├─ 错误处理                                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ HTTP/HTTPS调用 │     │ NamedPipe调用    │
│                │     │                 │
│ OpenAI Compatible │ NamedPipeModelClient
│ Anthropic      │     │ model_daemon.py │
│ Ollama         │     │                 │
└────────┬───────┘     └────────┬────────┘
         │                      │
         │                      │
┌────────▼──────────────────────▼───────────────────────────────┐
│  Step 4: HTTP/HTTPS调用流程                                      │
│  ├─ OpenAiCompatibleClient                                      │
│  │     ├─ POST {baseUrl}/v1/chat/completions                   │
│  │     ├─ Headers：                                             │
│  │     │     ├─ Authorization: Bearer {apiKey}                 │
│  │     │     ├─ Content-Type: application/json                 │
│  │     ├─ Request Body：                                        │
│  │     │     ├─ model: {modelName}                             │
│  │     │     ├─ messages: [{role, content}]                    │
│  │     │     ├─ tools: [...]（可选）                            │
│  │     │     ├─ tool_choice: auto（可选）                       │
│  │     │     ├─ stream: true（可选）                            │
│  │     │     ├─ temperature                                     │
│  │     │     ├─ max_tokens                                      │
│  │     ├─ Response：                                            │
│  │     │     ├─ choices: [{message, finish_reason}]            │
│  │     │     ├─ usage: {prompt_tokens, completion_tokens}      │
│  │     │     ├─ SSE流式响应（stream=true时）                    │
│  │     ├─ 错误处理：                                             │
│  │     │     ├─ 401 Unauthorized → Token无效                   │
│  │     │     ├─ 429 Rate Limit → 重试                          │
│  │     │     ├─ 500 Server Error → 降级                        │
│  │     │     ├─ Timeout → 降级                                 │
│  ├─ AnthropicClient                                             │
│  │     ├─ POST {baseUrl}/v1/messages                            │
│  │     ├─ Headers：                                             │
│  │     │     ├─ x-api-key: {apiKey}                            │
│  │     │     ├─ anthropic-version: 2023-06-01                  │
│  │     ├─ Request Body：                                        │
│  │     │     ├─ model: claude-3-opus-20240229                  │
│  │     │     ├─ messages: [{role, content}]                    │
│  │     │     ├─ system: ...                                     │
│  │     │     ├─ max_tokens                                      │
│  │     │     ├─ tools: [...]（可选）                            │
│  │     ├─ Response：                                            │
│  │     │     ├─ content: [{type, text}]                        │
│  │     │     ├─ stop_reason                                     │
│  │     │     ├─ usage                                           │
│  ├─ UsageTracker（Token使用统计）                                │
│  │     ├─ 记录每次调用的token消耗                               │
│  │     ├─ 成本控制                                              │
│  │     ├─ 日志记录                                              │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 5: NamedPipe调用流程                                        │
│  ├─ NamedPipeModelClient                                         │
│  │     ├─ 初始化：                                               │
│  │     │     ├─ 创建named pipe路径                              │
│  │     │     │     ├─ Request pipe: /tmp/llm_req_{sessionId}   │
│  │     │     │     ├─ Response pipe: /tmp/llm_resp_{sessionId} │
│  │     │     ├─ 建立连接                                        │
│  │     ├─ 调用流程：                                             │
│  │     │     ├─ 1. 构造ModelRequest                             │
│  │     │     │     ├─ sessionId                                 │
│  │     │     │     ├─ prompt                                    │
│  │     │     │     ├─ parameters                                │
│  │     │     ├─ 2. 序列化为JSON                                 │
│  │     │     ├─ 3. 写入Request pipe                             │
│  │     │     ├─ 4. 等待Response pipe响应                        │
│  │     │     │     ├─ 超时控制（默认30秒）                       │
│  │     │     ├─ 5. 读取Response pipe                            │
│  │     │     ├─ 6. 解析ModelResponse                            │
│  │     │     │     ├─ content                                   │
│  │     │     │     ├─ status                                    │
│  │     │     │     ├─ error                                     │
│  │     ├─ Session管理：                                          │
│  │     │     ├─ createSession()                                 │
│  │     │     ├─ closeSession()                                  │
│  │     │     ├─ cleanupPipes()                                  │
│  │     ├─ 错误处理：                                             │
│  │     │     ├─ Pipe连接失败 → 重试                             │
│  │     │     ├─ Timeout → 降级                                  │
│  │     │     ├─ Daemon无响应 → 重启Daemon                       │
│  ├─ Python model_daemon.py                                       │
│  │     ├─ 监听流程：                                             │
│  │     │     ├─ 1. 扫描/tmp/llm_req_* pipes                    │
│  │     │     ├─ 2. 读取Request pipe                            │
│  │     │     ├─ 3. 解析JSON请求                                 │
│  │     │     ├─ 4. 加载Qwen3/Qwen3.5模型                       │
│  │     │     │     ├─ scripts/python/llm/run_qwen3.py          │
│  │     │     │     ├─ scripts/python/llm/run_qwen35.py         │
│  │     │     ├─ 5. 模型推理                                     │
│  │     │     ├─ 6. 构造响应                                     │
│  │     │     ├─ 7. 写入Response pipe                            │
│  │     │     ├─ 8. 清理pipes                                    │
│  │     ├─ Session管理：                                          │
│  │     │     ├─ 维护session映射表                               │
│  │     │     ├─ 超时清理                                        │
│  │     ├─ 多进程支持：                                           │
│  │     │     ├─ 多个daemon进程                                  │
│  │     │     ├─ 进程池管理                                      │
│  │     │     ├─ 负载均衡                                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 6: 健康监控与熔断                                           │
│  ├─ ModelHealthRegistry                                          │
│  │     ├─ 记录模型状态：                                         │
│  │     │     ├─ 成功调用 → AVAILABLE                            │
│  │     │     ├─ 失败调用 → DEGRADED                             │
│  │     │     ├─ 连续失败 → COOLDOWN                             │
│  │     │     │     ├─ 冷却时间5分钟                             │
│  │     │     │     ├─ 冷却到期 → AVAILABLE                      │
│  │     │     ├─ 长期失败 → UNAVAILABLE                          │
│  │     ├─ 状态查询：                                             │
│  │     │     ├─ getHealthStatus(modelId) → HealthStatus        │
│  │     │     ├─ isAvailable(modelId) → boolean                  │
│  │     ├─ 状态更新：                                             │
│  │     │     ├─ recordSuccess(modelId)                          │
│  │     │     ├─ recordFailure(modelId)                          │
│  │     │     ├─ recordTimeout(modelId)                          │
│  │     ├─ 自动恢复：                                             │
│  │     │     ├─ COOLDOWN到期自动恢复为AVAILABLE                 │
│  │     │     ├─ 定时检查                                        │
│  ├─ ModelHealthProber                                           │
│  │     ├─ 定期健康探测                                           │
│  │     │     ├─ 每5分钟探测一次                                  │
│  │     │     ├─ 调用模型简单推理                                │
│  │     │     ├─ 更新健康状态                                    │
│  ├─ 熔断触发：                                                   │
│  │     ├─ 连续失败5次 → 触发熔断                                │
│  │     ├─ 进入COOLDOWN                                          │
│  │     ├─ 5分钟后自动恢复                                       │
│  └─ BrainModelResolver集成熔断过滤                              │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Step 7: 降级与重试                                               │
│  ├─ BrainModelFallback                                           │
│  │     ├─ 主模型失败 → 降级到备用Provider                        │
│  │     ├─ 降级策略：                                             │
│  │     │     ├─ 本地模型优先（低成本）                          │
│  │     │     ├─ HTTP模型降级（高可用）                          │
│  │     │     ├─ 规则兜底（最终降级）                            │
│  │     ├─ 重试策略：                                             │
│  │     │     ├─ 最多重试3次                                     │
│  │     │     ├─ 重试间隔：1秒/2秒/4秒                           │
│  │     │     ├─ 不同Provider重试                               │
│  ├─ LlmBasedXXX降级：                                             │
│  │     ├─ LlmBasedDialogueAnalyzer                              │
│  │     │     ├─ LLM失败 → RuleBasedDialogueAnalyzer            │
│  │     ├─ LlmBasedMainBrainTaskDirector                         │
│  │     │     ├─ LLM失败 → RuleBasedMainBrainTaskDirector       │
│  │     ├─ LlmBasedFixedEmployeeDispatcher                       │
│  │     │     ├─ LLM失败 → RegistryBackedFixedEmployeeDispatcher│
│  │     ├─ LlmBasedFinalResponseCoordinator                      │
│  │     │     ├─ LLM失败 → DefaultFinalResponseCoordinator      │
│  │     ├─ LlmBasedExecutionCapabilityResolver                   │
│  │     │     ├─ LLM失败 → DefaultExecutionCapabilityResolver   │
│  │     └ 所有LLM组件都有规则降级兜底                             │
└─────────────────────────────────────────────────────────────────┘
```

### 7.3 关键代码路径

- 模型池管理：`core/model/pool/ModelPoolManager.java`
- 模型选择器：`core/model/selector/BrainModelSelectorManager.java`
- 模型解析：`core/model/pool/BrainModelResolver.java`
- 健康监控：`core/model/pool/ModelHealthRegistry.java`
- LLM客户端：`core/model/pool/LlmClient.java`
- OpenAI客户端：`core/model/pool/client/OpenAiCompatibleClient.java`
- Anthropic客户端：`core/model/pool/client/AnthropicClient.java`
- NamedPipe客户端：`core/model/impl/NamedPipeModelClient.java`
- Provider工厂：`core/provider/impl/ProviderFactory.java`
- Python守护进程：`scripts/python/model_daemon.py`
- Token统计：`core/model/UsageTracker.java`
- 硬件监控：`core/model/selector/HardwareResourceMonitor.java`

---

## 8. 神经元处理流程（Neuron）

### 8.1 神经元架构总览

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/neuron/`

**神经元类型**：

| 神经元 | 职责 | 模型 | 权限 |
|--------|------|------|------|
| Qwen3Neuron | 闲聊/快速响应 | Qwen3/Qwen3.5（固定） | 所有用户 |
| BitNetNeuron | 工具判断/低资源推理 | BitNet（固定） | DEPARTMENT+ |
| ToolNeuron | 工具调用执行 | 固定模型 | DEPARTMENT+ |
| RouterNeuron | 消息路由转发 | - | DEPARTMENT+ |
| EyeNeuron | 视觉识别/图像分析 | 视觉模型 | FULL |
| SensorNeuron | 传感器输入处理 | - | FULL |
| ProjectDevelopmentNeuron | 项目开发任务执行 | - | DEPARTMENT+ |

### 8.2 神经元处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│  NeuronRegistry（神经元注册表）                                   │
│  ├─ NeuronRegistryImpl                                          │
│  │     ├─ 注册所有神经元实例                                     │
│  │     │     ├─ Qwen3Neuron                                     │
│  │     │     ├─ BitNetNeuron                                    │
│  │     │     ├─ ToolNeuron                                      │
│  │     │     ├─ RouterNeuron                                    │
│  │     │     ├─ EyeNeuron                                       │
│  │     │     ├─ SensorNeuron                                    │
│  │     │     ├─ ProjectDevelopmentNeuron                        │
│  │     ├─ getNeuron(String neuronId) → Neuron实例               │
│  │     ├─ 状态管理：                                             │
│  │     │     ├─ ONLINE（在线）                                   │
│  │     │     ├─ OFFLINE（离线）                                  │
│  │     │     ├─ BUSY（繁忙）                                     │
│  │     │     ├─ ACTIVE（活跃）                                   │
│  │     │     ├─ DORMANT（休眠）                                  │
│  │     │     ├─ LEARNING（学习中）                              │
│  │     │     ├─ EVOLVING（进化中）                              │
│  │     │     ├─ ERROR（错误）                                    │
│  │     │     ├─ SHUTDOWN（关闭）                                │
│  │     │     ├─ INITIALIZING（初始化）                          │
│  │     │     ├─ READY（就绪）                                    │
│  └─ NeuronState枚举（11种状态）                                 │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  NeuronCoordinator（神经元协调器）                                │
│  ├─ 会话创建：                                                   │
│  │     ├─ createSession()                                       │
│  │     │     ├─ 创建NeuronSession                               │
│  │     │     ├─ 绑定通道（channel://neuron/{neuronId}/session） │
│  │     │     ├─ 初始化神经元状态                                │
│  │     ├─ bindChannel()                                         │
│  │     │     ├─ 创建通道                                        │
│  │     │     ├─ 订阅消息                                        │
│  │     │     ├─ 注册发布器                                      │
│  ├─ 感知/派发/响应协调：                                         │
│  │     ├─ coordinatePerception()                                │
│  │     │     ├─ 接收输入（文本/音频/图像）                       │
│  │     │     ├─ 分发到对应感知神经元                            │
│  │     │     │     ├─ 文本 → Qwen3Neuron                       │
│  │     │     │     ├─ 音频 → EarNeuron（ASR）                   │
│  │     │     │     ├─ 图像 → EyeNeuron                         │
│  │     ├─ coordinateDispatch()                                  │
│  │     │     ├─ 接收分析结果                                    │
│  │     │     ├─ 路由到目标神经元                                │
│  │     │     │     ├─ RouterNeuron路由判断                     │
│  │     │     │     ├─ BitNetNeuron工具判断                     │
│  │     │     │     ├─ Qwen3Neuron闲聊                          │
│  │     │     ├─ 发布到目标神经元通道                            │
│  │     ├─ coordinateResponse()                                  │
│  │     │     ├─ 收集神经元响应                                  │
│  │     │     ├─ 聚合结果                                        │
│  │     │     ├─ 发布到用户通道                                  │
│  │     │     │     ├─ 文本响应                                  │
│  │     │     │     ├─ 音频响应（TTS → MouthNeuron）             │
│  ├─ 会话生命周期管理：                                           │
│  │     ├─ closeSession()                                        │
│  │     │     ├─ 清理通道                                        │
│  │     │     ├─ 更新神经元状态                                  │
│  │     │     ├─ 释放资源                                        │
│  │     ├─ 监控会话状态                                          │
│  │     │     ├─ 超时检测                                        │
│  │     │     ├─ 异常处理                                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ Qwen3Neuron    │     │ BitNetNeuron    │
│                │     │                 │
│ 闲聊/快速响应   │     │ 工具判断         │
└────────┬───────┘     └────────┬────────┘
         │                      │
         │                      │
┌────────▼──────────────────────▼───────────────────────────────┐
│  Qwen3Neuron处理流程                                             │
│  ├─ 接收消息                                                     │
│  │     ├─ 从channel://neuron/qwen3/session读取                 │
│  │     ├─ 解析消息内容                                           │
│  ├─ 模型调用                                                     │
│  │     ├─ NamedPipeModelClient                                  │
│  │     │     ├─ 创建session                                     │
│  │     │     ├─ 写入request pipe                               │
│  │     │     ├─ 等待response pipe                              │
│  │     │     │     ├─ 超时控制30秒                             │
│  │     ├─ scripts/python/model_daemon.py                        │
│  │     │     ├─ 监听named pipe                                  │
│  │     │     ├─ 调用Qwen3/Qwen3.5模型                          │
│  │     │     │     ├─ scripts/python/llm/run_qwen3.py          │
│  │     │     │     ├─ scripts/python/llm/run_qwen35.py         │
│  │     │     ├─ 返回响应                                        │
│  ├─ 响应处理                                                     │
│  │     ├─ 解析模型响应                                           │
│  │     ├─ 发布到用户通道                                         │
│  │     │     ├─ channel://user/{userId}/response               │
│  ├─ 特点：                                                       │
│  │     ├─ 固定模型，不走模型池                                   │
│  │     ├─ 低延迟，适合闲聊                                       │
│  │     ├─ 低成本                                                 │
│  │     ├─ 所有用户可访问                                         │
│  │     ├─ 不等于部门大脑（重要区别）                             │
│  │     │     ├─ 部门业务走Brain，不走Qwen3Neuron                │
│  │     │     ├─ 闲聊/快速响应走Qwen3Neuron                      │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  BitNetNeuron处理流程                                             │
│  ├─ 接收消息                                                     │
│  │     ├─ 从channel://neuron/bitnet/session读取                │
│  │     ├─ 解析消息内容                                           │
│  ├─ 工具判断                                                     │
│  │     ├─ BitNet模型推理                                         │
│  │     │     ├─ 本地低资源模型                                   │
│  │     │     ├─ 快速推理                                        │
│  │     ├─ 判断是否需要调用工具                                   │
│  │     │     ├─ 关键词检测                                      │
│  │     │     ├─ 模式识别                                        │
│  │     │     ├─ 输出：                                          │
│  │     │     │     ├─ needsTool: boolean                        │
│  │     │     │     ├─ suggestedTools: List<String>              │
│  ├─ 工具调用                                                     │
│  │     ├─ 如需调用工具 → ToolNeuron                             │
│  │     │     ├─ 发布到channel://neuron/tool/session            │
│  │     ├─ 如不需工具 → 直接响应                                  │
│  │     │     ├─ 发布到用户通道                                  │
│  ├─ 特点：                                                       │
│  │     ├─ 资源消耗低                                             │
│  │     ├─ 响应快                                                 │
│  │     ├─ 适合工具判断                                           │
│  │     ├─ DEPARTMENT及以上权限                                  │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  ToolNeuron处理流程                                               │
│  ├─ 接收工具调用请求                                             │
│  │     ├─ 从channel://neuron/tool/session读取                  │
│  │     ├─ 解析工具调用参数                                       │
│  │     │     ├─ toolName                                        │
│  │     │     ├─ parameters                                      │
│  │     │     ├─ context                                         │
│  ├─ 工具执行                                                     │
│  │     ├─ ToolRegistry查找工具                                   │
│  │     │     ├─ getTool(toolName)                               │
│  │     ├─ DefaultToolExecutor执行                               │
│  │     │     ├─ ToolHookManager前置钩子                         │
│  │     │     │     ├─ 权限检查                                  │
│  │     │     │     ├─ 安全校验                                  │
│  │     │     │     ├─ 审批触发                                  │
│  │     │     ├─ Tool.execute(context)                          │
│  │     │     ├─ ToolHookManager后置钩子                         │
│  │     │     │     ├─ 日志记录                                  │
│  │     │     │     ├─ 结果处理                                  │
│  │     │     │     ├─ 审计记录                                  │
│  │     ├─ 返回ToolResult                                         │
│  │     │     ├─ success                                         │
│  │     │     ├─ data                                            │
│  │     │     ├─ error                                           │
│  ├─ 响应处理                                                     │
│  │     ├─ 发布到用户通道                                         │
│  │     │     ├─ 成功 → 返回工具结果                             │
│  │     │     ├─ 失败 → 返回错误信息                             │
│  │     │     │     ├─ FallbackHandler兜底                       │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  其他神经元                                                      │
│  ├─ RouterNeuron                                                │
│  │     ├─ 消息路由                                               │
│  │     │     ├─ 意图转发                                        │
│  │     │     ├─ 部门路由                                        │
│  │     │     ├─ ChatNeuronRouter                                │
│  │     │     │     ├─ 路由到目标神经元                          │
│  │     ├─ ChatIntentClassifier                                  │
│  │     │     ├─ LLM-first / Rule-fallback                      │
│  │     │     │     ├─ LLM → DialogueAnalyzer语义分类           │
│  │     │     │     ├─ LLM失败 → 关键词规则                      │
│  ├─ EyeNeuron                                                   │
│  │     ├─ 视觉识别                                               │
│  │     │     ├─ 图像识别                                        │
│  │     │     ├─ 人脸分析                                        │
│  │     │     ├─ 文档OCR                                         │
│  │     │     ├─ 视觉问答                                        │
│  │     │     ├─ 输出：                                          │
│  │     │     │     ├─ ImageAnalysisResult                       │
│  │     │     │     ├─ FaceAnalysisResult                        │
│  │     │     │     ├─ DocumentAnalysisResult                    │
│  │     │     │     ├─ VisualQAResult                            │
│  │     │     │     ├─ DetectedObject                            │
│  │     ├─ FULL权限要求                                           │
│  ├─ SensorNeuron                                                │
│  │     ├─ 传感器输入                                             │
│  │     │     ├─ 多模态感知                                      │
│  │     │     ├─ 环境感知                                        │
│  │     ├─ 重命名为PerceptionSensorNeuron                        │
│  │     │     │     ├─ 避免与neuron/impl/SensorNeuron混淆        │
│  ├─ ProjectDevelopmentNeuron                                    │
│  │     ├─ 项目开发任务执行                                       │
│  │     │     ├─ 接收项目任务                                    │
│  │     │     ├─ 调用开发工具                                    │
│  │     │     ├─ 生成代码产物                                    │
│  ├─ FallbackHandler                                             │
│  │     ├─ 神经元失败兜底                                         │
│  │     │     ├─ 模型失败兜底                                    │
│  │     │     ├─ 工具失败兜底                                    │
│  │     │     ├─ 错误恢复                                        │
│  ├─ EvolutionSignalTrigger                                      │
│  │     ├─ 神经元层进化信号触发                                   │
│  │     │     ├─ 性能信号                                        │
│  │     │     ├─ 错误信号                                        │
│  │     │     ├─ 反馈信号                                        │
└─────────────────────────────────────────────────────────────────┘
```

### 8.3 神经元与大脑的区别

**关键区别**：

| 维度 | 神经元（Neuron） | 大脑（Brain） |
|------|-----------------|--------------|
| 职责 | 执行特定任务（闲聊/工具判断/视觉） | 业务逻辑处理和决策 |
| 模型 | 固定模型（Qwen3/BitNet） | 动态模型池选择 |
| 权限 | 各有不同（Qwen3Neuron所有用户） | 部门大脑需DEPARTMENT权限 |
| 调用场景 | 通用Agent链路/语音会话 | 部门文本业务链路 |
| 复杂度 | 简单特定功能 | 复杂业务推理 |
| ReAct循环 | 无（单步执行） | 有（多轮推理） |

**重要约束**：
- 部门业务走Brain，不走Neuron
- 闲聊/快速响应走Qwen3Neuron，不走Brain
- 两个链路不应混用

---

## 9. 工具执行流程（Tool）

### 9.1 工具架构总览

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/tool/`

**工具分类**：

**企业工具**（`impl/enterprise/`）：
- FeishuTool（飞书：消息/通讯录/审批/日历）
- DingTalkTool（钉钉）
- GitLabTool（代码仓库）
- GitHubTool（GitHub）
- JenkinsTool（CI/CD）
- JiraTool（任务管理）
- OpenProjectTool（项目管理）

**内置工具**：
- BrowserAutomationTool（浏览器自动化：Playwright）
- PlaywrightCrawlerTool（网页爬虫）
- DockerTool（容器操作）
- ClaudeCliTool（Claude CLI执行）
- TraeTool（Trae集成）
- PdfTool（PDF处理）
- OfficeTool（Office文档处理）
- WebCrawlerTool（网页抓取）
- TavilySearchTool（搜索）
- SearXNGTool（搜索）
- HttpTool（HTTP请求）
- HuggingFaceTool（模型调用）
- InvoiceProcessingTool（发票处理）
- KnowledgeGraphTool（知识图谱）
- NotionTool（Notion集成）
- ProactiveAgentTool（主动服务）
- RobotsChecker（Robots检查）
- RssReaderTool（RSS阅读）
- SelfImprovingTool（自我改进）
- SkillFinderTool（技能查找）
- SkillInstaller（技能安装）
- SlackTool（Slack集成）
- SummarizeTool（摘要）
- Crawl4aiClient（AI爬虫）
- BudgetManagementTool（预算管理）
- WindowsAppTool（Windows应用自动化：HTTP+pywinauto）
- WindowsAutomationTool（Windows系统控制：WebSocket桥接）

**代码任务工具**（`worktree/`）：
- GitWorktreeManager（Git worktree管理）

**天气工具**（`weather/`）：
- WttrProvider（wttr.in）
- OpenWeatherMapProvider
- QWeatherProvider（和风天气）

### 9.2 工具执行流程

```
┌─────────────────────────────────────────────────────────────────┐
│  ToolRegistry（工具注册表）                                       │
│  ├─ ToolRegistryImpl                                            │
│  │     ├─ 注册所有工具实例                                       │
│  │     │     ├─ 企业工具                                         │
│  │     │     │     ├─ FeishuTool（拆分为4个子工具）              │
│  │     │     │     │     ├─ FeishuMessageTool                   │
│  │     │     │     │     ├─ FeishuContactTool                   │
│  │     │     │     │     ├─ FeishuApprovalTool                  │
│  │     │     │     │     ├─ FeishuCalendarTool                  │
│  │     │     │     │     ├─ AbstractFeishuTool（公共基类）       │
│  │     │     │     ├─ DingTalkTool                              │
│  │     │     │     ├─ GitLabTool                                │
│  │     │     │     ├─ GitHubTool                                │
│  │     │     │     ├─ JenkinsTool                               │
│  │     │     │     ├─ JiraTool                                  │
│  │     │     │     ├─ OpenProjectTool                           │
│  │     │     ├─ 内置工具                                         │
│  │     │     │     ├─ BrowserAutomationTool（Playwright真实实现）│
│  │     │     │     │     ├─ navigate/click/type/screenshot     │
│  │     │     │     │     ├─ getText/wait                        │
│  │     │     │     │     ├─ 懒初始化Browser实例                 │
│  │     │     │     │     ├─ 会话隔离BrowserContext             │
│  │     │     │     │     ├─ 优雅降级                            │
│  │     │     │     ├─ DockerTool                                │
│  │     │     │     ├─ ClaudeCliTool                             │
│  │     │     │     ├─ TraeTool                                  │
│  │     │     │     ├─ PdfTool                                   │
│  │     │     │     ├─ OfficeTool                                │
│  │     │     │     ├─ WebCrawlerTool                            │
│  │     │     │     ├─ TavilySearchTool                          │
│  │     │     │     ├─ SearXNGTool                               │
│  │     │     │     ├─ HttpTool                                  │
│  │     │     │     ├─ WindowsAppTool                            │
│  │     │     │     ├─ WindowsAutomationTool                     │
│  │     │     │     ├─ 其他工具...                               │
│  │     │     ├─ 天气工具                                         │
│  │     │     │     ├─ WttrProvider                              │
│  │     │     │     ├─ OpenWeatherMapProvider                    │
│  │     │     │     ├─ QWeatherProvider                          │
│  │     ├─ getTool(String toolName) → Tool实例                   │
│  │     ├─ getToolsByDepartment(String department)               │
│  │     │     │     ├─ BRAIN_TOOL_DEPARTMENT_MAPPING过滤        │
│  │     │     │     ├─ TechBrain → 技术相关工具                  │
│  │     │     │     ├─ MainBrain → 所有工具                      │
│  │     │     │     ├─ 8个业务大脑按部门过滤                     │
│  ├─ ToolSchema                                                  │
│  │     ├─ 工具定义（暴露给模型/大脑）                            │
│  │     │     ├─ name                                            │
│  │     │     ├─ description                                     │
│  │     │     ├─ parameters（JSON Schema）                       │
│  │     │     ├─ required                                        │
│  │     │     ├─ returns                                         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Tool接口                                                        │
│  ├─ 核心方法                                                     │
│  │     ├─ execute(ToolContext) → ToolResult                     │
│  │     │     ├─ ToolContext                                     │
│  │     │     │     ├─ userId                                    │
│  │     │     │     ├─ department                                │
│  │     │     │     ├─ permissions                               │
│  │     │     │     ├─ sessionId                                 │
│  │     │     │     ├─ parameters                                │
│  │     │     │     ├─ metadata                                  │
│  │     │     ├─ ToolResult                                      │
│  │     │     │     ├─ success                                   │
│  │     │     │     ├─ data                                      │
│  │     │     │     ├─ error                                     │
│  │     │     │     ├─ metadata                                  │
│  │     │     │     ├─ artifacts                                 │
│  │     ├─ getName() → String                                    │
│  │     ├─ getDescription() → String                             │
│  │     ├─ getSchema() → ToolSchema                              │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  DefaultToolExecutor（工具执行器）                                │
│  ├─ 执行流程                                                     │
│  │     ├─ executeToolCall(ToolCall)                             │
│  │     │     ├─ Step 1: 解析ToolCall                            │
│  │     │     │     ├─ toolName                                  │
│  │     │     │     ├─ parameters                                │
│  │     │     │     ├─ callId                                    │
│  │     │     ├─ Step 2: 前置钩子（ToolHookManager）              │
│  │     │     │     ├─ 权限检查                                  │
│  │     │     │     │     ├─ WorkItemPermissionService           │
│  │     │     │     │     ├─ RequireAccess检查                   │
│  │     │     │     ├─ 安全校验                                  │
│  │     │     │     │     ├─ BashSecurityValidator（命令校验）    │
│  │     │     │     │     ├─ BashSecurityValidator                 │
│  │     │     │     ├─ 审批触发                                  │
│  │     │     │     │     ├─ 高风险操作 → ApprovalService        │
│  │     │     │     │     ├─ BrainBoundaryEnforcer（大脑边界）   │
│  │     │     │     │     ├─ ExecutionBoundaryEnforcer（员工边界）│
│  │     │     │     ├─ 日志记录                                  │
│  │     │     │     ├─ 钩子输出：ToolHookResult                  │
│  │     │     │     │     ├─ allowed                             │
│  │     │     │     │     ├─ blocked                             │
│  │     │     │     │     ├─ requiresApproval                    │
│  │     │     ├─ Step 3: 如需审批 → 等待审批                      │
│  │     │     │     ├─ ApprovalService.createApprovalInstance   │
│  │     │     │     ├─ 等待审批结果                              │
│  │     │     │     │     ├─ approved → 继续执行                 │
│  │     │     │     │     ├─ rejected → 返回拒绝                 │
│  │     │     ├─ Step 4: 查找工具                                 │
│  │     │     │     ├─ ToolRegistry.getTool(toolName)           │
│  │     │     │     ├─ 工具不存在 → 返回错误                     │
│  │     │     ├─ Step 5: 构造ToolContext                          │
│  │     │     │     ├─ 从AuthContext提取用户信息                 │
│  │     │     │     ├─ 从BrainContext提取上下文                  │
│  │     │     │     ├─ 注入parameters                            │
│  │     │     ├─ Step 6: 执行工具                                 │
│  │     │     │     ├─ Tool.execute(context)                     │
│  │     │     │     ├─ 超时控制                                  │
│  │     │     │     ├─ 异常捕获                                  │
│  │     │     ├─ Step 7: 后置钩子（ToolHookManager）              │
│  │     │     │     ├─ 日志记录                                  │
│  │     │     │     ├─ 结果处理                                  │
│  │     │     │     ├─ 审计记录                                  │
│  │     │     │     ├─ 进化反馈                                  │
│  │     │     │     │     ├─ EvolutionFeedbackBridgeService     │
│  │     │     ├─ Step 8: 返回ToolResult                           │
│  │     │     │     ├─ 成功 → 返回data                           │
│  │     │     │     ├─ 失败 → 返回error                          │
│  │     │     │     │     ├─ FallbackHandler兜底                 │
│  │     ├─ 并行执行（可选）                                       │
│  │     │     ├─ 多工具并行调用                                  │
│  │     │     ├─ 结果聚合                                        │
│  │     ├─ 重试策略                                               │
│  │     │     ├─ 失败重试                                        │
│  │     │     ├─ 最大重试次数                                    │
│  │     │     ├─ 重试间隔                                        │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐     ┌────────▼────────┐
│ 高风险工具      │     │ 低风险工具       │
│                │     │                 │
│ 需审批         │     │ 直接执行         │
└────────┬───────┘     └────────┬────────┘
         │                      │
         │                      │
┌────────▼──────────────────────▼───────────────────────────────┐
│  高风险工具审批流程                                               │
│  ├─ 高风险操作定义：                                             │
│  │     ├─ shell命令执行                                         │
│  │     ├─ process_kill（进程杀除）                              │
│  │     ├─ registry_set（注册表修改）                            │
│  │     ├─ filesystem_write/delete（文件写入/删除）              │
│  │     ├─ Docker容器操作                                        │
│  │     ├─ Claude CLI执行                                        │
│  │     ├─ WindowsAutomationTool高风险操作                       │
│  │     ├─ 其他高风险工具                                        │
│  ├─ 审批流程：                                                   │
│  │     ├─ BrainBoundaryEnforcer四重校验                         │
│  │     │     ├─ 1. 大脑职责边界检查                             │
│  │     │     │     ├─ allowedActions校验                        │
│  │     │     │     ├─ forbiddenActions校验                      │
│  │     │     ├─ 2. 员工执行边界检查                             │
│  │     │     │     ├─ ExecutionBoundaryEnforcer                 │
│  │     │     │     ├─ 跨部门拦截                                │
│  │     │     │     ├─ 超管辖拦截                                │
│  │     │     │     ├─ 高风险任务拦截                            │
│  │     │     │     ├─ 8个部门管辖权映射                         │
│  │     │     ├─ 3. 工具安全校验                                  │
│  │     │     │     ├─ BashSecurityValidator                    │
│  │     │     │     ├─ BashSecurityValidator                      │
│  │     │     ├─ 4. 审批流程触发                                  │
│  │     │     │     ├─ ApprovalService                          │
│  │     │     │     │     ├─ 创建ApprovalInstance               │
│  │     │     │     │     ├─ ApprovalInstance                   │
│  │     │     │     │     │     ├─ instanceId                   │
│  │     │     │     │     │     ├─ workflowId                   │
│  │     │     │     │     │     ├─ requesterId                  │
│  │     │     │     │     │     ├─ operation                    │
│  │     │     │     │     │     ├─ riskLevel                    │
│  │     │     │     │     │     ├─ impactAnalysis               │
│  │     │     │     │     ├─ ApprovalWorkflow                   │
│  │     │     │     │     │     ├─ steps                        │
│  │     │     │     │     │     ├─ approvers                    │
│  │     │     │     │     │     ├─ timeout                      │
│  │     │     │     │     ├─ ApprovalStep                       │
│  │     │     │     │     │     ├─ stepId                       │
│  │     │     │     │     │     ├─ approverId                   │
│  │     │     │     │     │     ├─ decision                     │
│  │     │     │     │     │     ├─ comments                     │
│  │     │     │     │     │     ├─ timestamp                    │
│  │     │     │     │     ├─ ApprovalRecord                     │
│  │     │     │     │     │     ├─ 审批记录                     │
│  │     │     │     │     │     ├─ 审批意见                     │
│  │     │     │     │     │     ├─ 审批时间                     │
│  │     │     │     ├─ 等待审批                                  │
│  │     │     │     │     ├─ 超时机制                            │
│  │     │     │     │     ├─ 自动拒绝                            │
│  │     │     │     ├─ 审批结果处理                              │
│  │     │     │     │     ├─ approved → 继续执行工具            │
│  │     │     │     │     ├─ rejected → 返回拒绝信息            │
│  │     │     │     │     ├─ escalated → 升级到更高级审批       │
│  │     │     │     ├─ 审批回调端点                              │
│  │     │     │     │     ├─ POST /api/approval/{instanceId}/callback/approved│
│  │     │     │     │     ├─ POST /api/approval/{instanceId}/callback/rejected│
│  │     │     │     ├─ 审批持久化                                │
│  │     │     │     │     ├─ ApprovalInstanceEntity             │
│  │     │     │     │     ├─ ApprovalRecordEntity               │
│  │     │     ├─ 注意：ApprovalManager已移除，由BrainBoundaryEnforcer替代│
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  工具实现示例（以BrowserAutomationTool为例）                      │
│  ├─ 初始化                                                       │
│  │     ├─ 懒初始化Browser实例                                    │
│  │     │     ├─ Playwright.launch()                             │
│  │     │     ├─ 首次调用时初始化                                │
│  │     ├─ BrowserContext会话隔离                                 │
│  │     │     ├─ 每个session独立context                          │
│  │     │     ├─ 防止会话干扰                                    │
│  ├─ 方法实现                                                     │
│  │     ├─ navigate(url)                                          │
│  │     │     ├─ page.navigate(url)                              │
│  │     │     ├─ 等待页面加载                                    │
│  │     │     ├─ 返回成功/失败                                   │
│  │     ├─ click(selector)                                        │
│  │     │     ├─ page.click(selector)                            │
│  │     │     ├─ 等待元素出现                                    │
│  │     ├─ type(selector, text)                                   │
│  │     │     ├─ page.type(selector, text)                       │
│  │     ├─ screenshot()                                           │
│  │     │     ├─ page.screenshot()                               │
│  │     │     ├─ 返回Base64图片                                  │
│  │     ├─ getText(selector)                                      │
│  │     │     ├─ page.textContent(selector)                      │
│  │     ├─ wait(condition, timeout)                               │
│  │     │     ├─ page.waitForCondition(condition, timeout)       │
│  ├─ 优雅降级                                                     │
│  │     │     ├─ Browser启动失败 → 返回错误                      │
│  │     │     ├─ 页面加载失败 → 返回错误                          │
│  │     │     ├─ 元素查找失败 → 返回错误                          │
│  │     ├─ AutoCloseable + @PreDestroy资源管理                    │
│  │     │     ├─ 自动关闭Browser                                 │
│  │     │     ├─ 自动清理Context                                 │
└───────────────────┬─────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────────┐
│  Windows自动化工具                                               │
│  ├─ WindowsAppTool（HTTP+pywinauto业务化封装）                   │
│  │     ├─ 架构流程：                                             │
│  │     │     ├─ AI大脑 → ToolNeuron → WindowsAppTool (Java)   │
│  │     │     │                              ↓ HTTP API        │
│  │     │     │     ├─ server.py (Python, 客户端电脑)          │
│  │     │     │     │     ↓ pywinauto                         │
│  │     │     │     ├─ Windows桌面应用 (金蝶KIS等)              │
│  │     ├─ 部署步骤：                                             │
│  │     │     ├─ 1. 客户端电脑：复制windows_automation/目录      │
│  │     │     ├─ 2. 修改config.json中的应用路径                  │
│  │     │     ├─ 3. pip install -r requirements.txt            │
│  │     │     ├─ 4. python server.py                            │
│  │     │     ├─ 5. 开放防火墙端口8765                           │
│  │     │     ├─ 6. 服务器端：修改WindowsAppTool节点IP地址       │
│  │     ├─ WindowsAutomationNodeRepository                       │
│  │     │     │     ├─ 节点注册/心跳/可见性查询                  │
│  │     ├─ 配置文件：                                             │
│  │     │     ├─ config.json                                     │
│  │     │     │     ├─ applications定义可自动化应用              │
│  │     │     │     │     ├─ 金蝶KIS                            │
│  │     │     │     │     │     ├─ exe路径                      │
│  │     │     │     │     │     ├─ backend (win32/uia)         │
│  │     │     │     │     ├─ 其他应用                            │
│  │     │     ├─ config.client.example.json                      │
│  │     │     │     ├─ 客户端配置模板                            │
│  │     ├─ API端点：                                              │
│  │     │     ├─ POST /api/windows/{clientId}/launch            │
│  │     │     ├─ POST /api/windows/{clientId}/click             │
│  │     │     ├─ POST /api/windows/{clientId}/type              │
│  │     │     ├─ POST /api/windows/{clientId}/read              │
│  │     │     ├─ POST /api/windows/{clientId}/screenshot        │
│  │     │     ├─ GET /api/windows/{clientId}/status             │
│  │     │     ├─ 其他操作端点                                    │
│  │     ├─ WindowsAppTool.initializeDefaultNodes()               │
│  │     │     │     ├─ 默认节点注册                              │
│  │     │     │     ├─ 节点IP地址配置                            │
│  ├─ WindowsAutomationTool（WebSocket通用系统控制）               │
│  │     ├─ 架构流程：                                             │
│  │     │     ├─ AI大脑 → ToolNeuron → WindowsAutomationTool   │
│  │     │     │                              ↓ WebSocket        │
│  │     │     │     ├─ living-agent-desktop (Electron, 客户端) │
│  │     │     │     │     ↓ stdin/stdout (JSON行协议)         │
│  │     │     │     ├─ service.py (Python, 内嵌子进程)         │
│  │     │     │     │     ↓ UIA/PowerShell/psutil/win32api    │
│  │     │     │     ├─ Windows系统（控件/进程/注册表/文件系统）  │
│  │     │     │     │     ↑ WIN_AUTOMATION_RESPONSE            │
│  │     ├─ 核心接口：                                             │
│  │     │     ├─ core/websocket/WindowsAutomationClientGateway │
│  │     │     │     ├─ gateway定义接口                          │
│  │     │     ├─ gateway/websocket/WindowsAutomationClientGatewayImpl│
│  │     │     │     ├─ 维护clientId → WebSocketSession映射     │
│  │     │     ├─ living-agent-desktop/src/main/win-automation-service.ts│
│  │     │     │     ├─ 管理Python子进程                         │
│  │     │     ├─ living-agent-desktop/resources/win-automation/service.py│
│  │     │     │     ├─ UIA/PowerShell/注册表/文件系统等         │
│  │     ├─ 权限分级：                                             │
│  │     │     ├─ CHAT_ONLY/LIMITED/FULL                         │
│  │     │     ├─ 高风险操作需BrainBoundaryEnforcer边界检查      │
│  │     │     │     ├─ shell/process_kill/registry_set/delete  │
│  │     │     ├─ ApprovalManager已移除                          │
│  │     ├─ 部署：                                                 │
│  │     │     ├─ 安装 living-agent-desktop（Electron客户端）    │
│  │     │     ├─ 客户端自动启动 Python service.py 子进程       │
│  │     │     ├─ 客户端连接 WebSocket 到服务端                  │
│  │     │     ├─ 工具调用路径：                                  │
│  │     │     │     └─ AI决策 → WinAutomationTool.execute()    │
│  │     │     │     └─ sendRequest(clientId, operation, params)│
│  │     │     │     └ WebSocket → {"type":"win_automation",...}│
│  │     │     │     └ Electron客户端接收 → 启动Python子进程    │
│  │     │     │     └ service.py stdin JSON → 执行Windows API  │
│  │     │     │     └ service.py stdout JSON → 返回结果        │
│  │     │     │     ┘ Electron → WebSocket回传                  │
│  │     │     │     ┘ 服务端 → WinAutomationTool处理响应        │
│  │     │     │     ┘ AI继续推理或结束                          │
│  │     ├─ 执行流程详细：                                        │
│  │     │     ├─ 1. AI大脑产生工具调用意图                      │
│  │     │     ├─ 2. ToolNeuron解析工具参数                      │
│  │     │     ├─ 3. WindowsAutomationTool.execute()被调用       │
│  │     │     ├─ 4. clientGateway.sendRequest()发送WebSocket    │
│  │     │     ├─ 5. 客户端service.py接收并执行                  │
│  │     │     ├─ 6. 执行结果回传到服务端                        │
│  │     │     ├─ 7. handleWinAutomationResponse()处理结果       │
│  │     │     ├─ 8. 结果返回给AI大脑继续推理                    │
│  ├─ 内置工具（Java实现）：                                      │
│  │     ├─ TimeTool                                              │
│  │     ├─ WeatherTool                                           │
│  │     ├─ CalculatorTool                                        │
│  │     ├─ WebSearchTool                                         │
│  │     ├─ KnowledgeQueryTool                                    │
│  │     ├─ ChannelBroadcastTool                                  │
│  │     ├─ EmployeeInfoTool                                      │
│  │     ├─ EmployeeDispatchTool                                  │
│  │     ├─ TaskCreateTool                                        │
│  │     ├─ TaskAssignTool                                        │
│  │     ├─ TaskQueryTool                                         │
│  │     ├─ ApprovalCreateTool                                    │
│  │     ├─ ApprovalApproveTool                                   │
│  │     ├─ JiraTool                                              │
│  │     ├─ GitLabTool                                               │
│  │     ├─ DockerTool                                            │
│  │     ├─ FileReadTool                                          │
│  │     ├─ FileWriteTool                                         │
│  │     ├─ ShellExecuteTool                                      │
│  │     ├─ ClaudeCliTool                                       │
│  │     ├─ SkillExecutorTool                                     │
│  │     ├─ RagSearchTool                                         │
│  │     ├─ WebCrawlerTool                                        │
│  │     ├─ SqlQueryTool                                          │
│  │     ├─ GoogleCalendarTool                                    │
│  │     ├─ GoogleDriveTool                                       │
│  │     ├─ GoogleDocsTool                                        │
│  │     ├─ GoogleSheetsTool                                      │
│  │     ├─ GoogleSlidesTool                                      │
│  │     ├─ SlackTool                                             │
│  │     ├─ DiscordTool                                           │
│  │     ├─ TelegramTool                                          │
│  │     ├─ ZoomTool                                              │
│  │     ├─ TeamsTool                                             │
│  │     ├─ MeetTool                                              │
│  │     ├─ ConfluenceTool                                        │
│  │     ├─ NotionTool                                            │
│  │     ├─ TrelloTool                                            │
│  │     ├─ AsanaTool                                             │
│  │     ├─ AirtableTool                                          │
│  │     ├─ ZapierTool                                            │
│  │     ├─ HubspotTool                                           │
│  │     ├─ SalesforceTool                                        │
│  │     ├─ ZendeskTool                                           │
│  │     ├─ FreshdeskTool                                         │
│  │     ├─ IntercomTool                                          │
│  │     ├─ LinearTool                                            │
│  │     ├─ MondayTool                                            │
│  │     ├─ ClickupTool                                           │
│  │     ├─ PagerdutyTool                                         │
│  │     ├─ DatadogTool                                           │
│  │     ├─ GrafanaTool                                           │
│  │     ├─ SplunkTool                                            │
│  │     ├─ SentryTool                                            │
│  │     ├─ RollbarTool                                           │
│  │     ├─ LaunchdarklyTool                                      │
│  │     ├─ SplitTool                                             │
│  │     ├─ OptimizelyTool                                        │
│  │     ├─ VwoTool                                               │
│  │     ├─ AbtastyTool                                           │
│  │     ├─ FullstoryTool                                         │
│  │     ├─ HotjarTool                                            │
│  │     ├─ LogrocketTool                                         │
│  │     ├─ AmplitudeTool                                         │
│  │     ├─ MixpanelTool                                          │
│  │     ├─ HeapTool                                              │
│  │     ├─ PendoTool                                             │
│  │     ├─ ChartbeatTool                                         │
│  │     ├─ KlaviyoTool                                           │
│  │     ├─ MailchimpTool                                         │
│  │     ├─ SendgridTool                                          │
│  │     ├─ MailgunTool                                           │
│  │     ├─ PostmarkTool                                          │
│  │     ├─ TwilioTool                                            │
│  │     ├─ NexmoTool                                             │
│  │     ├─ PlivoTool                                             │
│  │     ├─ SinchTool                                             │
│  │     ├─ BandwidthTool                                         │
│  │     ├─ VonageTool                                            │
│  │     ├─ MessagebirdTool                                       │
│  │     ├─ StripeTool                                            │
│  │     ├─ PaypalTool                                            │
│  │     ├─ SquareTool                                            │
│  │     ├─ AdyenTool                                             │
│  │     ├─ BraintreeTool                                         │
│  │     ├─ CheckoutTool                                          │
│  │     ├─ RecurlyTool                                           │
│  │     ├─ ChargifyTool                                          │
│  │     ├─ ChargbeeTool                                          │
│  │     ├─ PaddleTool                                            │
│  │     ├─ FastspringTool                                        │
│  │     ├─ GumroadTool                                           │
│  │     ├─ LemonstandTool                                        │
│  │     ├─ MollieTool                                            │
│  │     ├─ Przelewy24Tool                                        │
│  │     ├─ PayuTool                                              │
│  │     ├─ RazorpayTool                                          │
│  │     ├─ CcavenueTool                                          │
│  │     ├─ EsewaTool                                             │
│  │     ├─ KhaltiTool                                            │
│  │     ├─ MidtransTool                                          │
│  │     ├─ XenditTool                                            │
│  │     ├─ IyzicoTool                                            │
│  │     ├─ PaystackTool                                          │
│  │     ├─ FlutterwaveTool                                       │
│  │     ├─ DlocalTool                                            │
│  │     ├─ MercadopagoTool                                       │
│  │     ├─ PagseguroTool                                         │
│  │     ├─ PayulatamTool                                         │
│  │     ├─ CieloTool                                             │
│  │     ├─ RedsysTool                                            │
│  │     ├─ SagepayTool                                           │
│  │     ├─ WorldpayTool                                          │
│  │     ├─ OpayoTool                                             │
│  │     ├─ PaysafeTool                                           │
│  │     ├─ EcayTool                                              │
│  │     ├─ KlarnaTool                                            │
│  │     ├─ AffirmTool                                            │
│  │     ├─ AfterpayTool                                          │
│  │     ├─ ClearpayTool                                          │
│  │     ├─ SezzleTool                                            │
│  │     ├─ QuadpayTool                                           │
│  │     ├─ SplititTool                                           │
│  │     ├─ PartialTool                                           │
│  │     ├─ ViaBillTool                                           │
│  │     ├─ Klarna FinancingTool                                  │
│  │     ├─ Affirm FinancingTool                                  │
│  │     ├─ Afterpay FinancingTool                                │
│  │     ├─ Clearpay FinancingTool                                │
│  │     ├─ Sezzle FinancingTool                                  │
│  │     ├─ Quadpay FinancingTool                                 │
│  │     ├─ Splitit FinancingTool                                 │
│  │     ├─ Partial FinancingTool                                 │
│  │     ├─ ViaBill FinancingTool                                 │
│  │     ├─ Amazon PayTool                                        │
│  │     ├─ Apple PayTool                                         │
│  │     ├─ Google PayTool                                        │
│  │     ├─ Samsung PayTool                                       │
│  │     ├─ WeChat PayTool                                        │
│  │     ├─ AlipayTool                                            │
│  │     ├─ PayPal HereTool                                       │
│  │     ├─ Square ReaderTool                                     │
│  │     ├─ Stripe TerminalTool                                   │
│  │     ├─ Adyen TerminalTool                                    │
│  │     ├─ Braintree TerminalTool                                │
│  │     ├─ Checkout TerminalTool                                 │
│  │     ├─ Worldpay TerminalTool                                 │
│  │     ├─ Paysafe TerminalTool                                  │
│  │     ├─ Opayo TerminalTool                                    │
│  │     ├─ Sagepay TerminalTool                                  │
│  │     ├─ Redsys TerminalTool                                   │
│  │     ├─ Cielo TerminalTool                                    │
│  │     ├─ Pagseguro TerminalTool                                │
│  │     ├─ Mercadopago TerminalTool                              │
│  │     ├─ Dlocal TerminalTool                                   │
│  │     ├─ Flutterwave TerminalTool                              │
│  │     ├─ Paystack TerminalTool                                 │
│  │     ├─ Iyzico TerminalTool                                   │
│  │     ├─ Xendit TerminalTool                                   │
│  │     ├─ Midtrans TerminalTool                                 │
│  │     ├─ Khalti TerminalTool                                   │
│  │     ├─ Esewa TerminalTool                                    │
│  │     ├─ Ccavenue TerminalTool                                 │
│  │     ├─ Razorpay TerminalTool                                 │
│  │     ├─ Payu TerminalTool                                     │
│  │     ├─ Przelewy24 TerminalTool                               │
│  │     ├─ Mollie TerminalTool                                   │
│  │     ├─ Lemonstand TerminalTool                               │
│  │     ├─ Gumroad TerminalTool                                  │
│  │     ├─ Fastspring TerminalTool                               │
│  │     ├─ Paddle TerminalTool                                   │
│  │     ├─ Chargbee TerminalTool                                 │
│  │     ├─ Chargify TerminalTool                                 │
│  │     ├─ Recurly TerminalTool                                  │
│  │     ├─ Checkout TerminalTool                                 │
│  │     ├─ Braintree TerminalTool                                │
│  │     ├─ Adyen TerminalTool                                    │
│  │     ├─ Square TerminalTool                                   │
│  │     ├─ Paypal TerminalTool                                   │
│  │     ├─ Stripe TerminalTool                                   │
│  │     ├─ Messagebird TerminalTool                              │
│  │     ├─ Vonage TerminalTool                                   │
│  │     ├─ Bandwidth TerminalTool                                │
│  │     ├─ Sinch TerminalTool                                    │
│  │     ├─ Plivo TerminalTool                                    │
│  │     ├─ Nexmo TerminalTool                                    │
│  │     ├─ Twilio TerminalTool                                   │
│  │     ├─ Postmark TerminalTool                                 │
│  │     ├─ Mailgun TerminalTool                                  │
│  │     ├─ Sendgrid TerminalTool                                 │
│  │     ├─ Mailchimp TerminalTool                                │
│  │     ├─ Klaviyo TerminalTool                                  │
│  │     ├─ Chartbeat TerminalTool                                │
│  │     ├─ Pendo TerminalTool                                    │
│  │     ├─ Heap TerminalTool                                     │
│  │     ├─ Mixpanel TerminalTool                                 │
│  │     ├─ Amplitude TerminalTool                                │
│  │     ├─ Logrocket TerminalTool                                │
│  │     ├─ Hotjar TerminalTool                                   │
│  │     ├─ Fullstory TerminalTool                                │
│  │     ├─ Abtasty TerminalTool                                  │
│  │     ├─ Vwo TerminalTool                                      │
│  │     ├─ Optimizely TerminalTool                               │
│  │     ├─ Split TerminalTool                                    │
│  │     ├─ Launchdarkly TerminalTool                             │
│  │     ├─ Rollbar TerminalTool                                  │
│  │     ├─ Sentry TerminalTool                                   │
│  │     ├─ Splunk TerminalTool                                   │
│  │     ├─ Grafana TerminalTool                                  │
│  │     ├─ Datadog TerminalTool                                  │
│  │     ├─ Pagerduty TerminalTool                                │
│  │     ├─ Clickup TerminalTool                                  │
│  │     ├─ Monday TerminalTool                                   │
│  │     ├─ Linear TerminalTool                                   │
│  │     ├─ Intercom TerminalTool                                 │
│  │     ├─ Freshdesk TerminalTool                                │
│  │     ├─ Zendesk TerminalTool                                  │
│  │     ├─ Salesforce TerminalTool                               │
│  │     ├─ Hubspot TerminalTool                                  │
│  │     ├─ Zapier TerminalTool                                   │
│  │     ├─ Airtable TerminalTool                                 │
│  │     ├─ Asana TerminalTool                                    │
│  │     ├─ Trello TerminalTool                                   │
│  │     ├─ Notion TerminalTool                                   │
│  │     ├─ Confluence TerminalTool                               │
│  │     ├─ Meet TerminalTool                                     │
│  │     ├─ Teams TerminalTool                                    │
│  │     ├─ Zoom TerminalTool                                     │
│  │     ├─ Telegram TerminalTool                                 │
│  │     ├─ Discord TerminalTool                                  │
│  │     ├─ Slack TerminalTool                                    │
│  │     ├─ GoogleSlides TerminalTool                             │
│  │     ├─ GoogleSheets TerminalTool                             │
│  │     ├─ GoogleDocs TerminalTool                               │
│  │     ├─ GoogleDrive TerminalTool                              │
│  │     ├─ GoogleCalendar TerminalTool                           │
│  │     ├─ SqlQuery TerminalTool                                 │
│  │     ├─ WebCrawler TerminalTool                               │
│  │     ├─ RagSearch TerminalTool                                │
│  │     ├─ SkillExecutor TerminalTool                            │
│  │     ├─ ClaudeProxy TerminalTool                              │
│  │     ├─ ShellExecute TerminalTool                             │
│  │     ├─ FileWrite TerminalTool                                │
│  │     ├─ FileRead TerminalTool                                 │
│  │     ├─ Docker TerminalTool                                   │
│  │     ├─ Git TerminalTool                                      │
│  │     ├─ Jira TerminalTool                                     │
│  │     ├─ ApprovalApprove TerminalTool                          │
│  │     ├─ ApprovalCreate TerminalTool                           │
│  │     ├─ TaskQuery TerminalTool                                │
│  │     ├─ TaskAssign TerminalTool                               │
│  │     ├─ TaskCreate TerminalTool                               │
│  │     ├─ EmployeeDispatch TerminalTool                         │
│  │     ├─ EmployeeInfo TerminalTool                             │
│  │     ├─ ChannelBroadcast TerminalTool                         │
│  │     ├─ KnowledgeQuery TerminalTool                           │
│  │     ├─ WebSearch TerminalTool                                │
│  │     ├─ Calculator TerminalTool                               │
│  │     ├─ Weather TerminalTool                                  │
│  │     ├─ Time TerminalTool                                     │
└───────────────────────────────────────────────────────────────┘

---

## 10. 知识库与记忆系统流程

### 10.1 知识库架构（三层分层）

```
┌──────────────────────────────────────────────────────────────┐
│                    知识库系统架构                              │
├──────────────────────────────────────────────────────────────┤
│  知识分层（L1/L2/L3）：                                        │
│  │                                                            │
│  ├─ L1_PRIVATE (员工个人知识)                                 │
│  │     ├─ 个人偏好、工作习惯、历史对话                        │
│  │     ├─ 存储位置：employee://{id}/knowledge/               │
│  │     ├─ 访问权限：仅员工本人                                │
│  │                                                            │
│  ├─ L2_DEPARTMENT (部门共享知识)                              │
│  │     ├─ 部门流程、专业知识、最佳实践                        │
│  │     ├─ 存储位置：department://{dept}/knowledge/           │
│  │     ├─ 访问权限：部门成员                                  │
│  │                                                            │
│  ├─ L3_SHARED (企业全局知识)                                  │
│  │     ├─ 企业制度、通用规范、跨部门协作                      │
│  │     ├─ 存储位置：enterprise://knowledge/                  │
│  │     ├─ 访问权限：所有员工                                  │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  核心接口：                                                    │
│  │                                                            │
│  ├─ KnowledgeBase (核心接口)                                  │
│  │     ├─ store(key, knowledge, metadata)                    │
│  │     ├─ retrieve(key) → Optional<Object>                   │
│  │     ├─ search(query) → List<KnowledgeEntry>               │
│  │     ├─ searchSimilar(vector, limit) → 向量相似搜索         │
│  │     ├─ storeWithVector(key, knowledge, embedding, meta)   │
│  │     ├─ hybridSearch(query, vector, weights, limit)        │
│  │                                                            │
│  ├─ SQLiteKnowledgeBase (默认实现)                            │
│  │     ├─ SQLite存储 + 向量索引                              │
│  │     ├─ 支持关键词搜索 + 向量搜索 + 混合搜索                │
│  │     ├─ 数据文件：data/knowledge.db                        │
│  │                                                            │
│  ├─ KnowledgeManagerImpl                                      │
│  │     ├─ 知识条目管理                                        │
│  │     ├─ 分类/标签管理                                       │
│  │     ├─ 相关性评分                                          │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  专业知识注入流程：                                            │
│  │                                                            │
│  ├─ ProfessionalKnowledgeSeeder                               │
│  │     ├─ 启动时加载专业知识                                  │
│  │     ├─ documents/ 目录下的企业知识源                       │
│  │     ├─ 部门架构、员工编制、职责卡                          │
│  │                                                            │
│  ├─ ArchitectureKnowledgeSeeder                               │
│  │     ├─ 代码架构知识注入                                    │
│  │     ├─ SourceTreeIndexer 扫描代码结构                      │
│  │     ├─ 分块存储到知识库                                    │
│  │                                                            │
│  ├─ 知识注入流程：                                            │
│  │     ├─ 1. SourceTreeIndexer.scanCodebase()                 │
│  │     ├─ 2. 按文件/模块分块                                  │
│  │     ├─ 3. ArchitectureKnowledgeSeeder.seed()               │
│  │     ├─ 4. KnowledgeBase.storeWithVector()                  │
│  │     ├─ 5. 建立向量索引                                     │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  知识检索流程：                                                │
│  │                                                            │
│  ├─ AI大脑需要知识支持时：                                    │
│  │     ├─ 1. BrainContext.getKnowledgeBase()                  │
│  │     ├─ 2. KnowledgeQueryTool.execute()                     │
│  │     ├─ 3. KnowledgeBase.hybridSearch()                     │
│  │     ├─ 4. 返回最相关知识条目                               │
│  │     ├─ 5. 注入到AI推理上下文                               │
│  │                                                            │
│  ├─ 搜索策略：                                                │
│  │     ├─ 关键词匹配：LIKE查询                                │
│  │     ├─ 向量相似：cosine similarity                         │
│  │     ├─ 混合搜索：加权组合两种结果                          │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 10.2 记忆系统架构（多种后端）

```
┌──────────────────────────────────────────────────────────────┐
│                    记忆系统架构                                │
├──────────────────────────────────────────────────────────────┤
│  核心接口 Memory：                                             │
│  │                                                            │
│  ├─ store(sessionId, key, value)                             │
│  ├─ retrieve(sessionId, key) → Optional<Object>              │
│  ├─ getHistory(sessionId) → List<MemoryEntry>                │
│  ├─ clear(sessionId)                                         │
│  ├─ summarize(sessionId) → String                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  后端实现（可切换）：                                          │
│  │                                                            │
│  ├─ SQLiteMemoryBackend                                       │
│  │     ├─ 默认后端                                            │
│  │     ├─ SQLite存储，持久化                                  │
│  │     ├─ 数据文件：data/memory.db                           │
│  │     ├─ 支持会话历史、对话摘要                              │
│  │                                                            │
│  ├─ MemosMemoryBackend                                        │
│  │     ├─ Memos笔记系统集成                                   │
│  │     ├─ 外部Memos服务                                       │
│  │     ├─ 通过API存储和检索                                   │
│  │                                                            │
│  ├─ MemPalaceBackend                                          │
│  │     ├─ 记忆宫殿模式                                        │
│  │     ├─ 结构化记忆组织                                      │
│  │     ├─ 按场景/时间/主题分类                                │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  记忆使用流程：                                                │
│  │                                                            │
│  ├─ 会话开始时：                                              │
│  │     ├─ BrainContext.getMemory()                            │
│  │     ├─ Memory.getHistory(sessionId)                       │
│  │     ├─ 加载历史对话                                        │
│  │     ├─ 注入到LLM上下文                                     │
│  │                                                            │
│  ├─ 对话过程中：                                              │
│  │     ├─ AI响应后                                            │
│  │     ├─ Memory.store(sessionId, "user", userMessage)       │
│  │     ├─ Memory.store(sessionId, "assistant", response)     │
│  │                                                            │
│  ├─ 会话结束时：                                              │
│  │     ├─ Memory.summarize(sessionId)                        │
│  │     ├─ 生成对话摘要                                        │
│  │     ├─ 存储摘要供下次使用                                  │
│  │                                                            │
│  ├─ 历史压缩（防止上下文过长）：                              │
│  │     ├─ 当历史超过max_history_turns时                      │
│  │     ├─ HistoryCompressor.compress()                       │
│  │     ├─ 生成摘要替代完整历史                                │
│  │     ├─ 保留关键对话                                        │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  配置（MemoryConfig.java）：                                   │
│  │                                                            │
│  ├─ memoryBackend: sqlite | memos | mempalace                │
│  ├─ knowledgeBackend: sqlite                                  │
│  ├─ maxHistoryTurns: 5                                        │
│  ├─ dataPath: ./data                                          │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 10.3 知识进化流程

```
┌──────────────────────────────────────────────────────────────┐
│                    知识进化流程                                │
├──────────────────────────────────────────────────────────────┤
│  EvolutionDecisionEngine → KnowledgeEvolverImpl：             │
│  │                                                            │
│  ├─ 信号触发：                                                │
│  │     ├─ 错误反馈、用户评分、性能指标                        │
│  │     ├─ SignalExtractor.extractFromMetrics()               │
│  │                                                            │
│  ├─ 决策判断：                                                │
│  │     ├─ EvolutionDecisionEngine.shouldTriggerEvolution()   │
│  │     ├─ 判断：修复/优化/创新/上报                           │
│  │                                                            │
│  ├─ 知识进化执行：                                            │
│  │     ├─ KnowledgeEvolverImpl.evolveKnowledge(signal)       │
│  │     ├─ 从错误中学习                                        │
│  │     ├─ 更新知识权重                                        │
│  │     ├─ 补充知识缺口                                        │
│  │                                                            │
│  ├─ 结果记录：                                                │
│  │     ├─ EvolutionFeedbackService.record(result)            │
│  │     ├─ 存储到evolution_results表                          │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 11. 员工与通道管理流程

### 11.1 员工管理流程

```
┌──────────────────────────────────────────────────────────────┐
│                    员工管理流程                                │
├──────────────────────────────────────────────────────────────┤
│  EmployeeService（统一员工模型）：                             │
│  │                                                            │
│  ├─ ID命名规范：                                              │
│  │     ├─ 人类员工：employee://human/{authProvider}/{accountId}│
│  │     ├─ 数字员工：employee://digital/{domain}/{name}/{instance}│
│  │                                                            │
│  ├─ 员工类型：                                                │
│  │     ├─ HUMAN：人类员工                                     │
│  │     ├─ DIGITAL：数字员工（AI）                             │
│  │     ├─ EmployeeOrigin.FIXED：固定员工                      │
│  │     ├─ EmployeeOrigin.EVOLVED：进化员工                    │
│  │                                                            │
│  ├─ 核心接口：                                                │
│  │     ├─ getEmployee(employeeId) → Optional<Employee>       │
│  │     ├─ listEmployees(query) → List<Employee>              │
│  │     ├─ createEmployee(request) → Employee                 │
│  │     ├─ updateEmployee(employeeId, request) → Employee     │
│  │     ├─ deleteEmployee(employeeId)                         │
│  │     ├─ assignDepartment(employeeId, department)           │
│  │                                                            │
│  ├─ 实现类：                                                  │
│  │     ├─ JpaEmployeeServiceImpl                              │
│  │     │     ├─ PostgreSQL持久化                              │
│  │     │     ├─ EnterpriseEmployeeEntity                     │
│  │     ├─ FixedEmployeeRegistry                               │
│  │     │     ├─ 固定员工配置                                  │
│  │     │     ├─ documents/ 目录职责卡定义                     │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  员工创建流程：                                                │
│  │                                                            │
│  ├─ 人类员工：                                                │
│  │     ├─ 1. 用户登录（UnifiedAuthService）                   │
│  │     ├─ 2. 认证成功 → AuthContext                          │
│  │     ├─ 3. EnterpriseEmployeeService.findByIdOrCreate()    │
│  │     ├─ 4. 存储到enterprise_employees表                    │
│  │     ├─ 5. 绑定部门/权限                                    │
│  │                                                            │
│  ├─ 数字员工：                                                │
│  │     ├─ 1. 管理员创建请求                                   │
│  │     ├─ 2. EmployeeService.createEmployee()                │
│  │     ├─ 3. 配置能力/技能                                    │
│  │     ├─ 4. 分配部门                                         │
│  │     ├─ 5. 注册到NeuronRegistry                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  固定员工系统（FixedEmployeeRegistry）：                       │
│  │                                                            │
│  ├─ documents/目录定义：                                      │
│  │     ├─ 部门架构.yaml                                      │
│  │     ├─ 员工编制.yaml                                      │
│  │     ├─ 职责卡-{name}.yaml                                 │
│  │                                                            │
│  ├─ 启动加载：                                                │
│  │     ├─ FixedEmployeeRegistry.loadFromDocuments()          │
│  │     ├─ 解析YAML配置                                        │
│  │     ├─ 注册固定员工                                        │
│  │     ├─ 绑定职责/技能                                       │
│  │                                                            │
│  ├─ 固定员工示例：                                            │
│  │     ├─ tech_support：技术支持数字员工                     │
│  │     ├─ hr_assistant：HR助理                                │
│  │     ├─ finance_analyst：财务分析师                        │
│  │     ├─ sales_agent：销售代理                               │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  员工状态管理：                                                │
│  │                                                            │
│  ├─ EmployeeStatus：                                          │
│  │     ├─ ACTIVE：活跃                                        │
│  │     ├─ BUSY：忙碌                                          │
│  │     ├─ OFFLINE：离线                                       │
│  │     ├─ SUSPENDED：暂停                                     │
│  │                                                            │
│  ├─ 状态变更：                                                │
│  │     ├─ 任务分配 → BUSY                                    │
│  │     ├─ 任务完成 → ACTIVE                                  │
│  │     ├─ WebSocket断开 → OFFLINE                            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 11.2 通道管理流程

```
┌──────────────────────────────────────────────────────────────┐
│                    通道管理流程                                │
├──────────────────────────────────────────────────────────────┤
│  ChannelManager（通道通信系统）：                              │
│  │                                                            │
│  ├─ ID命名规范：                                              │
│  │     ├─ channel://{scope}/{name}                           │
│  │     ├─ scope: department | enterprise | public            │
│  │                                                            │
│  ├─ 通道类型：                                                │
│  │     ├─ DEPARTMENT：部门通道                               │
│  │     ├─ ENTERPRISE：企业通道（董事长）                     │
│  │     ├─ PUBLIC：公共通道                                   │
│  │     ├─ DIRECT：一对一通道                                 │
│  │                                                            │
│  ├─ 核心接口：                                                │
│  │     ├─ createChannel(channelId, type) → Channel          │
│  │     ├─ getChannel(channelId) → Optional<Channel>         │
│  │     ├─ broadcast(channelId, message)                     │
│  │     ├─ subscribe(channelId, employeeId)                  │
│  │     ├─ unsubscribe(channelId, employeeId)                │
│  │     ├─ getSubscribers(channelId) → List<String>          │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  通道通信流程：                                                │
│  │                                                            │
│  ├─ 广播消息：                                                │
│  │     ├─ ChannelManager.broadcast(channelId, message)      │
│  │     ├─ ChannelBroadcastTool（AI工具调用）                 │
│  │     ├─ Channel.publish(message)                           │
│  │     ├─ 遍历所有订阅者                                      │
│  │     ├─ 发送WebSocket消息到每个订阅者                       │
│  │                                                            │
│  ├─ 消息格式：                                                │
│  │     ├─ {"type": "channel_message",                        │
│  │     │     "channelId": "channel://department/tech",       │
│  │     │     "from": "employee://digital/tech/tech_support", │
│  │     │     "content": "...",                               │
│  │     │     "timestamp": "..."}                             │
│  │                                                            │
│  ├─ 订阅管理：                                                │
│  │     ├─ WebSocket连接时自动订阅部门通道                     │
│  │     ├─ DepartmentWebSocketHandler管理订阅                 │
│  │     ├─ departmentChannels Map存储订阅关系                 │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  通道与WebSocket集成：                                         │
│  │                                                            │
│  ├─ /ws/dept/{dept}：                                         │
│  │     ├─ 连接时订阅channel://department/{dept}             │
│  │     ├─ DepartmentWebSocketHandler维护订阅                 │
│  │                                                            │
│  ├─ /ws/enterprise：                                          │
│  │     ├─ 连接时订阅channel://enterprise/board              │
│  │     ├─ 董事长频道                                          │
│  │                                                            │
│  ├─ /ws/public：                                              │
│  │     ├─ 连接时订阅channel://public/visitor                │
│  │     ├─ 公共访客通道                                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 12. 模型池与Provider调用流程

### 12.1 模型池架构

```
┌──────────────────────────────────────────────────────────────┐
│                    模型池架构                                  │
├──────────────────────────────────────────────────────────────┤
│  ModelPoolManager（模型池管理）：                              │
│  │                                                            │
│  ├─ 数据表：                                                  │
│  │     ├─ model_providers：Provider配置                      │
│  │     │     ├─ id, display_name, protocol, base_url        │
│  │     │     ├─ api_key_encrypted, enabled, supports_tool_choice│
│  │     ├─ llm_models：LLM模型配置                             │
│  │     │     ├─ id, provider_id, model_name, display_name   │
│  │     │     ├─ context_window, max_output_tokens           │
│  │     │     ├─ supports_vision, supports_reasoning         │
│  │     │     ├─ temperature, top_p, enabled, priority       │
│  │     │     ├─ health_status, last_health_check            │
│  │     ├─ brain_model_assignments：大脑模型分配              │
│  │     │     ├─ brain_id, model_id, assignment_type         │
│  │     │     ├─ assigned_at, assigned_by, is_active         │
│  │     ├─ brain_model_change_history：变更历史               │
│  │     │     ├─ brain_id, old_model_id, new_model_id        │
│  │     │     ├─ change_reason, changed_at                   │
│  │                                                            │
│  ├─ Provider协议：                                            │
│  │     ├─ OPENAI：OpenAI兼容API                              │
│  │     ├─ ANTHROPIC：Anthropic Claude API                    │
│  │     ├─ LOCAL：本地GGUF模型                                 │
│  │     ├─ CUSTOM：自定义协议                                  │
│  │                                                            │
│  ├─ 模型健康状态：                                            │
│  │     ├─ HEALTHY：正常                                       │
│  │     ├─ DEGRADED：降级                                      │
│  │     ├─ UNHEALTHY：异常                                     │
│  │     ├─ OFFLINE：离线                                       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  模型选择流程：                                                │
│  │                                                            │
│  ├─ BrainModelSelectorManager：                               │
│  │     ├─ 管理多个选择器                                      │
│  │     ├─ 按brainType/department分发                          │
│  │     ├─ selectorRegistry Map存储选择器                      │
│  │                                                            │
│  ├─ BrainModelSelector接口：                                  │
│  │     ├─ selectBestModel() → ResolvedBrainModel            │
│  │     ├─ score(model) → double                              │
│  │     ├─ supports(brainType, department) → boolean         │
│  │     ├─ getCandidates() → List<ResolvedBrainModel>        │
│  │                                                            │
│  ├─ 各Brain的选择器：                                         │
│  │     ├─ MainBrainModelSelector                             │
│  │     ├─ TechBrainModelSelector                             │
│  │     ├─ FinanceBrainModelSelector                          │
│  │     ├─ HrBrainModelSelector                               │
│  │     ├─ AdminBrainModelSelector                            │
│  │     ├─ ...其他部门大脑选择器                               │
│  │                                                            │
│  ├─ 选择流程：                                                │
│  │     ├─ 1. Brain需要LLM调用                                │
│  │     ├─ 2. BrainModelResolver.resolve(brainId)            │
│  │     ├─ 3. BrainModelSelectorManager.getSelector(brainId) │
│  │     ├─ 4. Selector.selectBestModel()                      │
│  │     ├─ 5. 返回ResolvedBrainModel                          │
│  │     ├─ 6. ResolvedBrainModelProvider创建Provider          │
│  │     ├─ 7. Provider.chat(request)                          │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ResolvedBrainModel：                                          │
│  │                                                            │
│  ├─ providerId：Provider标识                                  │
│  ├─ modelName：模型名称                                       │
│  ├─ baseUrl：API地址                                          │
│  ├─ apiKey：API密钥                                           │
│  ├─ protocol：协议类型                                        │
│  ├─ supportsToolChoice：是否支持工具选择                      │
│  ├─ maxTokens：最大输出token                                  │
│  ├─ temperature：温度参数                                     │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 12.2 Provider调用流程

```
┌──────────────────────────────────────────────────────────────┐
│                    Provider调用流程                            │
├──────────────────────────────────────────────────────────────┤
│  Provider接口：                                                │
│  │                                                            │
│  ├─ chat(ChatRequest) → CompletableFuture<ChatResponse>      │
│  ├─ chatStream(ChatRequest) → Flux<ChatChunk>                │
│  ├─ embed(EmbedRequest) → EmbedResponse                      │
│  ├─ getModels() → List<String>                               │
│  │                                                            │
│  ├─ ChatRequest：                                             │
│  │     ├─ messages：对话历史                                  │
│  │     ├─ tools：工具定义                                     │
│  │     ├─ model：模型名称                                     │
│  │     ├─ temperature/maxTokens                               │
│  │                                                            │
│  ├─ ChatResponse：                                            │
│  │     ├─ content：响应内容                                   │
│  │     ├─ toolCalls：工具调用                                 │
│  │     ├─ promptTokens/completionTokens                      │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  Provider实现类：                                              │
│  │                                                            │
│  ├─ OpenAiProvider                                            │
│  │     ├─ OpenAI兼容API                                       │
│  │     ├─ 支持 function calling                              │
│  │     ├─ HTTP POST /v1/chat/completions                     │
│  │                                                            │
│  ├─ AnthropicProvider                                         │
│  │     ├─ Anthropic Claude API                                │
│  │     ├─ 支持 tool use                                       │
│  │     ├─ HTTP POST /v1/messages                              │
│  │                                                            │
│  ├─ LocalModelProvider                                        │
│  │     ├─ 本地GGUF模型                                        │
│  │     ├─ NamedPipeModelClient                                │
│  │     ├─ 通过Python model_daemon.py                         │
│  │                                                            │
│  ├─ ResolvedBrainModelProvider                                │
│  │     ├─ 动态从模型池解析                                    │
│  │     ├─ 包装ResolvedBrainModel                             │
│  │     ├─ chatWithSystem()便捷方法                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  LlmClient实现：                                               │
│  │                                                            │
│  ├─ OpenAiClient                                              │
│  │     ├─ OkHttp HTTP客户端                                   │
│  │     ├─ SSE流式响应处理                                     │
│  │                                                            │
│  ├─ AnthropicClient                                           │
│  │     ├─ Anthropic API适配                                   │
│  │     ├─ 特殊消息格式                                        │
│  │                                                            │
│  ├─ NamedPipeModelClient                                      │
│  │     ├─ 本地模型命名管道通信                                │
│  │     ├─ 写入请求到管道                                      │
│  │     ├─ 从管道读取响应                                      │
│  │     ├─ Python model_daemon.py处理                         │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  调用链路：                                                    │
│  │                                                            │
│  ├─ Brain → BrainReActEngine → Provider → LlmClient          │
│  │                                                            │
│  ├─ 详细流程：                                                │
│  │     ├─ 1. Brain.think()                                    │
│  │     ├─ 2. BrainReActEngine.executeReActLoop()             │
│  │     ├─ 3. Provider.chat(ChatRequest)                      │
│  │     ├─ 4. LlmClient.sendRequest()                         │
│  │     ├─ 5. HTTP/NamedPipe通信                               │
│  │     ├─ 6. 返回ChatResponse                                 │
│  │     ├─ 7. 解析工具调用                                     │
│  │     ├─ 8. 执行工具                                         │
│  │     ├─ 9. 循环直到完成                                     │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  Token使用追踪：                                               │
│  │                                                            │
│  ├─ TokenUsageTracker                                         │
│  │     ├─ recordUsage(TokenUsage)                            │
│  │     ├─ 统计promptTokens/completionTokens                  │
│  │     ├─ 计算成本                                            │
│  │                                                            │
│  ├─ 成本计算：                                                │
│  │     ├─ COST_PER_1K_PROMPT = 0.001                         │
│  │     ├─ COST_PER_1K_COMPLETION = 0.002                     │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 13. 进化系统流程（Evolution）

### 13.1 进化架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                    进化系统架构                                │
├──────────────────────────────────────────────────────────────┤
│  核心组件：                                                    │
│  │                                                            │
│  ├─ SignalExtractor                                           │
│  │     ├─ 从错误、反馈、性能中提取进化信号                    │
│  │     ├─ extractFromMetrics(sourceContext)                  │
│  │                                                            │
│  ├─ EvolutionDecisionEngine                                   │
│  │     ├─ 决策是否触发进化                                    │
│  │     ├─ shouldTriggerEvolution(brainDomain)               │
│  │     ├─ 判断进化类型：修复/优化/创新/上报                   │
│  │                                                            │
│  ├─ EvolutionOrchestrator                                     │
│  │     ├─ 进化总编排                                          │
│  │     ├─ run(sourceContext) → OrchestrationReport           │
│  │     ├─ runAutoAdjust(brainId) → 自动模型调整              │
│  │     ├─ rollbackBrain(brainId) → 回滚模型                  │
│  │     ├─ score(result) → 评分                                │
│  │     ├─ selectStrategy(signal) → 选择策略                  │
│  │                                                            │
│  ├─ EvolutionExecutor                                         │
│  │     ├─ 执行进化操作                                        │
│  │     ├─ execute(signal) → EvolutionResult                  │
│  │                                                            │
│  ├─ EvolutionFeedbackService                                  │
│  │     ├─ 反馈记录                                            │
│  │     ├─ record(result)                                      │
│  │     ├─ recent(limit) → 最近反馈                            │
│  │     ├─ statistics() → 统计数据                             │
│  │                                                            │
│  ├─ EvolutionScheduler                                        │
│  │     ├─ 定时进化调度                                        │
│  │     ├─ 每小时自动检查                                      │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  进化类型：                                                    │
│  │                                                            │
│  ├─ REPAIR：修复错误                                          │
│  │     ├─ 工具调用失败                                        │
│  │     ├─ 响应格式错误                                        │
│  │     ├─ 知识缺失                                            │
│  │                                                            │
│  ├─ OPTIMIZE：优化性能                                        │
│  │     ├─ 响应速度提升                                        │
│  │     ├─ Token消耗优化                                       │
│  │     ├─ 模型选择调整                                        │
│  │                                                            │
│  ├─ INNOVATE：创新学习                                        │
│  │     ├─ 新技能生成                                          │
│  │     ├─ 新知识注入                                          │
│  │     ├─ 模式发现                                            │
│  │                                                            │
│  ├─ ESCALATE：上报问题                                        │
│  │     ├─ 无法自动处理                                        │
│  │     ├─ 需要人工介入                                        │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  自动调整策略（AutoAdjustStrategy）：                          │
│  │                                                            │
│  ├─ REPLACE_MODEL：替换模型                                   │
│  │     ├─ 连续失败≥3次                                        │
│  │                                                            │
│  ├─ DOWNGRADE_MODEL：降级模型                                 │
│  │     ├─ 平均评分<2.0，连续低评分≥5次                        │
│  │                                                            │
│  ├─ UPGRADE_MODEL：升级模型                                   │
│  │     ├─ 平均响应时间>15秒，连续慢响应≥10次                  │
│  │                                                            │
│  ├─ ESCALATE_TO_ADMIN：上报管理员                             │
│  │     ├─ 高置信度错误                                        │
│  │                                                            │
│  ├─ DEFER：延迟处理                                           │
│  │     ├─ 正常状态                                            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 13.2 进化执行流程

```
┌──────────────────────────────────────────────────────────────┐
│                    进化执行流程                                │
├──────────────────────────────────────────────────────────────┤
│  完整流程：                                                    │
│  │                                                            │
│  ┌──────────────────────────────────────────────┐            │
│  │ 1. 信号收集                                  │            │
│  │    ├─ 错误日志                               │            │
│  │    ├─ 用户反馈（评分）                       │            │
│  │    ├─ 性能指标（响应时间）                   │            │
│  │    └─ SignalExtractor.extractFromMetrics()   │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 2. 决策判断                                  │            │
│  │    ├─ EvolutionDecisionEngine                │            │
│  │    ├─ shouldTriggerEvolution(brainDomain)    │            │
│  │    ├─ 判断进化类型                           │            │
│  │    ├─ 检查熔断器                             │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 3. 执行进化                                  │            │
│  │    ├─ EvolutionOrchestrator.run()            │            │
│  │    ├─ EvolutionExecutor.execute(signal)      │            │
│  │    ├─ 根据进化类型执行：                     │            │
│  │    │   ├─ REPAIR → KnowledgeEvolverImpl     │            │
│  │    │   ├─ OPTIMIZE → 模型调整               │            │
│  │    │   ├─ INNOVATE → SkillGeneratorImpl     │            │
│  │    │   ├─ ESCALATE → 上报通知               │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 4. 反馈记录                                  │            │
│  │    ├─ EvolutionFeedbackService.record()      │            │
│  │    ├─ 存储到evolution_results表             │            │
│  │    ├─ 更新统计缓存                          │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 5. 自动调整（定时）                          │            │
│  │    ├─ EvolutionScheduler每小时执行           │            │
│  │    ├─ EvolutionOrchestrator.runAutoAdjust()  │            │
│  │    ├─ score()评分                           │            │
│  │    ├─ selectStrategy()选择策略              │            │
│  │    ├─ BrainModelAssigner.assignModel()      │            │
│  │    ├─ 记录变更历史                          │            │
│  └──────────────────────────────────────────────┘            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  模型回滚流程：                                                │
│  │                                                            │
│  ├─ rollbackBrain(brainId)：                                  │
│  │     ├─ 1. 查询变更历史                                     │
│  │     ├─ 2. 找到最近一次手动配置                             │
│  │     ├─ 3. BrainModelAssigner.assignModel(oldModel)        │
│  │     ├─ 4. 记录回滚操作                                     │
│  │     ├─ 5. 返回回滚结果                                     │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 14. 主动预判流程（Proactive）

### 14.1 主动预判架构

```
┌──────────────────────────────────────────────────────────────┐
│                    主动预判架构                                │
├──────────────────────────────────────────────────────────────┤
│  ProactiveOrchestrator（主动编排）：                           │
│  │                                                            │
│  ├─ 核心组件：                                                │
│  │     ├─ PatternPredictor：用户行为模式预测                 │
│  │     ├─ RiskPredictor：风险预警                             │
│  │     ├─ ProactiveSuggestionService：建议生成               │
│  │                                                            │
│  ├─ runForUser(userId)：                                      │
│  │     ├─ 生成个性化建议                                      │
│  │     ├─ 检查风险预警                                        │
│  │     ├─ 返回OrchestrationResult                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  PatternPredictor（行为模式预测）：                            │
│  │                                                            │
│  ├─ 分析用户历史行为                                          │
│  ├─ 预测用户下一步需求                                        │
│  ├─ 学习用户工作习惯                                          │
│  ├─ getStatistics() → 模式统计                               │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  RiskPredictor（风险预警）：                                   │
│  │                                                            │
│  ├─ 监控系统健康状态                                          │
│  ├─ 识别潜在风险                                              │
│  ├─ getActiveAlerts() → 风险列表                             │
│  │                                                            │
│  ├─ RiskAlert：                                               │
│  │     ├─ riskId：风险标识                                    │
│  │     ├─ severity：严重程度                                  │
│  │     ├─ description：描述                                   │
│  │     ├─ suggestedAction：建议行动                           │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ProactiveSuggestionService（建议生成）：                      │
│  │                                                            │
│  ├─ generateSuggestions(userId)：                             │
│  │     ├─ 基于用户模式生成建议                                │
│  │     ├─ 基于风险生成建议                                    │
│  │     ├─ 返回Suggestion列表                                  │
│  │                                                            │
│  ├─ Suggestion：                                              │
│  │     ├─ suggestionId                                        │
│  │     ├─ type：建议类型                                      │
│  │     ├─ content：建议内容                                   │
│  │     ├─ priority：优先级                                    │
│  │     ├─ actionType：行动类型                                │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 14.2 主动汇报流程（PR-1）

```
┌──────────────────────────────────────────────────────────────┐
│                    主动汇报流程                                │
├──────────────────────────────────────────────────────────────┤
│  WebSocket连接时触发（PR-1）：                                 │
│  │                                                            │
│  ├─ DepartmentWebSocketHandler.afterConnectionEstablished()  │
│  │     ├─ 检查sessionProactiveReported                       │
│  │     ├─ 首次连接未汇报                                      │
│  │     ├─ 调用ProactiveOrchestrator.runForUser(userId)       │
│  │     ├─ 生成建议/预警                                       │
│  │     ├─ 发送WebSocket消息                                   │
│  │     ├─ 标记sessionProactiveReported.put(sessionId, true)  │
│  │                                                            │
│  ├─ 消息格式：                                                │
│  │     ├─ {"type": "proactive_report",                        │
│  │     │     "userId": "...",                                 │
│  │     │     "suggestions": [...],                            │
│  │     │     "alerts": [...],                                 │
│  │     │     "timestamp": "..."}                              │
│  │                                                            │
│  ├─ 场景示例：                                                │
│  │     ├─ "我已经为您准备好了会议资料"                        │
│  │     ├─ "建议您检查一下这个异常数据"                        │
│  │     ├─ "根据您的习惯，我建议..."                           │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 15. 审批流程（Approval）

### 15.1 审批架构

```
┌──────────────────────────────────────────────────────────────┐
│                    审批流程架构                                │
├──────────────────────────────────────────────────────────────┤
│  ApprovalService（审批服务）：                                 │
│  │                                                            │
│  ├─ 核心接口：                                                │
│  │     ├─ createApproval(request) → ApprovalInstance         │
│  │     ├─ getApproval(instanceId) → Optional<ApprovalInstance>│
│  │     ├─ listApprovals(query) → List<ApprovalInstance>      │
│  │     ├─ approve(instanceId, approverId, comment)           │
│  │     ├─ reject(instanceId, approverId, comment)            │
│  │     ├─ cancel(instanceId)                                 │
│  │     ├─ getWorkflow(workflowId) → ApprovalWorkflow         │
│  │     ├─ registerCallback(callback)                         │
│  │                                                            │
│  ├─ ApprovalServiceImpl（DB持久化实现）：                     │
│  │     ├─ PostgreSQL存储                                      │
│  │     ├─ approval_instances表                               │
│  │     ├─ approval_workflows表                               │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ApprovalInstance（审批实例）：                                │
│  │                                                            │
│  ├─ instanceId：实例标识                                      │
│  ├─ workflowId：工作流ID                                      │
│  ├─ businessType：业务类型                                    │
│  ├─ businessId：业务ID                                        │
│  ├─ title：标题                                               │
│  ├─ description：描述                                         │
│  ├─ submitterId：提交人                                       │
│  ├─ status：状态                                              │
│  │     ├─ PENDING：待审批                                     │
│  │     ├─ APPROVED：已批准                                    │
│  │     ├─ REJECTED：已拒绝                                    │
│  │     ├─ CANCELLED：已取消                                   │
│  ├─ currentStep：当前步骤                                     │
│  ├─ records：审批记录列表                                     │
│  ├─ steps：审批步骤列表                                       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ApprovalWorkflow（审批工作流）：                              │
│  │                                                            │
│  ├─ workflowId：工作流ID                                      │
│  ├─ name：工作流名称                                          │
│  ├─ description：描述                                         │
│  ├─ steps：审批步骤                                           │
│  │                                                            │
│  ├─ 默认工作流：                                              │
│  │     ├─ default：单级审批                                   │
│  │     ├─ project_approval：三级审批                          │
│  │     │     ├─ 部门主管 → 财务部 → 董事长                   │
│  │     ├─ expense_approval：两级审批                          │
│  │     │     ├─ 部门主管 → 财务部                             │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ApprovalStep：                                                │
│  │                                                            │
│  ├─ stepId：步骤ID                                            │
│  ├─ name：步骤名称                                            │
│  ├─ order：顺序                                               │
│  ├─ approverIds：审批人列表                                   │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 15.2 审批执行流程

```
┌──────────────────────────────────────────────────────────────┐
│                    审批执行流程                                │
├──────────────────────────────────────────────────────────────┤
│  创建审批：                                                    │
│  │                                                            │
│  ├─ 1. 用户/AI发起审批请求                                    │
│  │     ├─ ApprovalCreateTool.execute()                       │
│  │     ├─ 或API POST /api/approvals                          │
│  │                                                            │
│  ├─ 2. ApprovalService.createApproval(request)               │
│  │     ├─ 查询工作流配置                                      │
│  │     ├─ 创建ApprovalInstance                               │
│  │     ├─ 设置当前步骤                                        │
│  │     ├─ 存储到数据库                                        │
│  │                                                            │
│  ├─ 3. 发送通知                                               │
│  │     ├─ 通知审批人                                          │
│  │     ├─ WebSocket推送                                       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  审批处理：                                                    │
│  │                                                            │
│  ├─ 1. 审批人收到通知                                         │
│  │                                                            │
│  ├─ 2. ApprovalApproveTool.execute() 或 API                  │
│  │     ├─ approve(instanceId, approverId, comment)           │
│  │     ├─ 或reject(instanceId, approverId, comment)          │
│  │                                                            │
│  ├─ 3. 更新审批实例                                           │
│  │     ├─ 记录审批结果                                        │
│  │     ├─ 更新当前步骤                                        │
│  │                                                            │
│  ├─ 4. 判断下一步                                             │
│  │     ├─ 还有后续步骤 → 通知下一审批人                       │
│  │     ├─ 全部完成 → APPROVED                                │
│  │     ├─ 任一拒绝 → REJECTED                                │
│  │                                                            │
│  ├─ 5. 触发回调                                               │
│  │     ├─ ApprovalCallback.onApproved()                      │
│  │     ├─ 或ApprovalCallback.onRejected()                    │
│  │     ├─ 执行后续业务逻辑                                    │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  流程图：                                                      │
│  │                                                            │
│  ┌──────────────┐                                             │
│  │ 创建审批     │                                             │
│  │ PENDING      │                                             │
│  └──────┬───────┘                                             │
│         ↓                                                     │
│  ┌──────────────┐                                             │
│  │ 步骤1审批    │ ← 审批人处理                                │
│  └──────┬───────┘                                             │
│         ↓                                                     │
│    ┌────┴────┐                                                │
│    │         │                                                │
│  approve   reject                                             │
│    │         │                                                │
│    ↓         ↓                                                │
│  ┌──────┐  ┌─────────┐                                        │
│  │步骤2 │  │REJECTED │                                        │
│  └──────┘  └─────────┘                                        │
│    │                                                          │
│    ↓                                                          │
│  ┌────────────┐                                               │
│  │ APPROVED   │                                               │
│  └────────────┘                                               │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 16. 项目管理与任务流转流程

### 16.1 任务管理系统

```
┌──────────────────────────────────────────────────────────────┐
│                    任务管理系统                                │
├──────────────────────────────────────────────────────────────┤
│  TaskCheckout（任务签出）：                                    │
│  │                                                            │
│  ├─ 核心接口：                                                │
│  │     ├─ checkout(taskId, assigneeId)                       │
│  │     ├─ checkin(taskId, result)                            │
│  │     ├─ getPendingTasks() → List<TaskAssignment>           │
│  │     ├─ getCompletedTasks(limit) → List<TaskAssignment>    │
│  │     ├─ getStatistics() → TaskStatistics                   │
│  │                                                            │
│  ├─ TaskAssignment：                                          │
│  │     ├─ taskId：任务ID                                      │
│  │     ├─ description：任务描述                               │
│  │     ├─ assignedTo：分配给                                  │
│  │     ├─ status：状态                                        │
│  │     ├─ priority：优先级                                    │
│  │     ├─ createdAt：创建时间                                 │
│  │     ├─ completedAt：完成时间                               │
│  │                                                            │
│  ├─ TaskStatistics：                                          │
│  │     ├─ pendingCount                                        │
│  │     ├─ checkedOutCount                                     │
│  │     ├─ completedCount                                      │
│  │     ├─ failedCount                                         │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  任务工具：                                                    │
│  │                                                            │
│  ├─ TaskCreateTool                                            │
│  │     ├─ 创建新任务                                          │
│  │     ├─ 参数：description, priority, assignee              │
│  │                                                            │
│  ├─ TaskAssignTool                                            │
│  │     ├─ 分配任务给员工                                      │
│  │     ├─ 参数：taskId, assigneeId                           │
│  │                                                            │
│  ├─ TaskQueryTool                                             │
│  │     ├─ 查询任务状态                                        │
│  │     ├─ 参数：taskId或status                               │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  Jira集成：                                                    │
│  │                                                            │
│  ├─ JiraTool                                                  │
│  │     ├─ createIssue()                                       │
│  │     ├─ updateIssue()                                       │
│  │     ├─ getIssue()                                          │
│  │     ├─ searchIssues()                                      │
│  │     ├─ addComment()                                        │
│  │     ├─ transitionIssue()                                   │
│  │                                                            │
│  ├─ 配置：                                                    │
│  │     ├─ jira.baseUrl                                        │
│  │     ├─ jira.apiToken                                       │
│  │     ├─ jira.projectKey                                     │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 16.2 任务执行流程

```
┌──────────────────────────────────────────────────────────────┐
│                    任务执行流程                                │
├──────────────────────────────────────────────────────────────┤
│  AI发起任务：                                                  │
│  │                                                            │
│  ├─ 1. AI大脑识别需要创建任务                                 │
│  │     ├─ ToolBackedEmployeeTaskExecutor                     │
│  │     ├─ 判断任务类型                                        │
│  │                                                            │
│  ├─ 2. TaskCreateTool.execute()                              │
│  │     ├─ 创建任务描述                                        │
│  │     ├─ 设置优先级                                          │
│  │     ├─ 存储到数据库                                        │
│  │                                                            │
│  ├─ 3. TaskAssignTool.execute()                              │
│  │     ├─ 分配给合适员工                                      │
│  │     ├─ 基于部门/技能匹配                                   │
│  │     ├─ TaskCheckout.checkout()                            │
│  │                                                            │
│  ├─ 4. 通知员工                                               │
│  │     ├─ WebSocket推送                                       │
│  │     ├─ channel://department/{dept} 广播                   │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  员工执行任务：                                                │
│  │                                                            │
│  ├─ 1. 员工收到任务通知                                       │
│  │                                                            │
│  ├─ 2. 员工确认接受                                           │
│  │     ├─ 数字员工：自动开始执行                              │
│  │     ├─ 人类员工：手动确认                                  │
│  │                                                            │
│  ├─ 3. 执行任务                                               │
│  │     ├─ 数字员工：调用工具                                  │
│  │     ├─ 人类员工：手动操作                                  │
│  │                                                            │
│  ├─ 4. 完成任务                                               │
│  │     ├─ TaskCheckout.checkin(taskId, result)               │
│  │     ├─ 更新任务状态                                        │
│  │     ├─ 记录执行结果                                        │
│  │                                                            │
│  ├─ 5. 回报结果                                               │
│  │     ├─ 通知AI大脑                                          │
│  │     ├─ 更新TaskStatistics                                 │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  项目管理任务（P0-3）：                                        │
│  │                                                            │
│  ├─ TaskType.PROJECT_MANAGEMENT                               │
│  │     ├─ 路由到JiraTool                                      │
│  │     ├─ 创建/更新Jira Issue                                 │
│  │                                                            │
│  ├─ TaskType.ISSUE_TRACKING                                   │
│  │     ├─ 查询Jira Issue状态                                  │
│  │     ├─ 添加评论                                            │
│  │                                                            │
│  ├─ executeProjectManagementTask()：                          │
│  │     ├─ 检查jira工具权限                                    │
│  │     ├─ 调用JiraTool                                        │
│  │     ├─ 返回执行结果                                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 17. 前端与后端交互流程

### 17.1 前端架构

```
┌──────────────────────────────────────────────────────────────┐
│                    前端架构                                    │
├──────────────────────────────────────────────────────────────┤
│  技术栈：                                                      │
│  │                                                            │
│  ├─ React 18 + Vite + TypeScript                              │
│  ├─ Zustand（状态管理）                                        │
│  ├─ TailwindCSS（样式）                                        │
│  ├─ WebSocket实时通信                                          │
│  ├─ REST API调用                                              │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  目录结构：                                                    │
│  │                                                            │
│  ├─ frontend/src/                                             │
│  │     ├─ components/：UI组件                                 │
│  │     │     ├─ Chat.tsx：聊天界面                            │
│  │     │     ├─ DepartmentChat.tsx：部门聊天                  │
│  │     │     ├─ AgentChat.tsx：Agent聊天                      │
│  │     │     ├─ Dashboard.tsx：仪表盘                         │
│  │     │     ├─ Settings.tsx：设置                            │
│  │     │     ├─ AdminPanel.tsx：管理面板                      │
│  │     │     ├─ BrainConfig.tsx：大脑配置                     │
│  │     │     ├─ ApprovalList.tsx：审批列表                    │
│  │     │     ├─ TaskList.tsx：任务列表                        │
│  │     │     ├─ EmployeeCard.tsx：员工卡片                    │
│  │     │     ├─ ChannelView.tsx：通道视图                     │
│  │     │     ├─ EvolutionHistory.tsx：进化历史                │
│  │     │     ├─ ModelPoolConfig.tsx：模型池配置               │
│  │     │     ├─ KnowledgeManager.tsx：知识管理                │
│  │     │     ├─ MemoryViewer.tsx：记忆查看                    │
│  │     │     ├─ ProactiveReport.tsx：主动汇报                 │
│  │     │     ├─ WindowsAutomation.tsx：Windows自动化          │
│  │     │     ├─ AudioChat.tsx：语音聊天                       │
│  │     │     ├─ DeviceStatus.tsx：设备状态                    │
│  │     │     ├─ HealthMonitor.tsx：健康监控                   │
│  │     │     ├─ TokenUsage.tsx：Token使用                     │
│  │     │     ├─ NotificationToast.tsx：通知                   │
│  │     │     ├─ Layout.tsx：布局                              │
│  │     │     ├─ Sidebar.tsx：侧边栏                           │
│  │     │     ├─ Header.tsx：头部                              │
│  │     │     ├─ Login.tsx：登录                               │
│  │     │     ├─ Register.tsx：注册                            │
│  │     │     └─ ...                                            │
│  │     ├─ services/：API服务                                  │
│  │     │     ├─ api.ts：API基础配置                           │
│  │     │     ├─ websocket.ts：WebSocket管理                   │
│  │     │     ├─ authApi.ts：认证API                           │
│  │     │     ├─ departmentApi.ts：部门API                     │
│  │     │     ├─ agentApi.ts：Agent API                        │
│  │     │     ├─ brainApi.ts：大脑API                          │
│  │     │     ├─ modelApi.ts：模型API                          │
│  │     │     ├─ approvalApi.ts：审批API                       │
│  │     │     ├─ taskApi.ts：任务API                           │
│  │     │     ├─ employeeApi.ts：员工API                       │
│  │     │     ├─ channelApi.ts：通道API                        │
│  │     │     ├─ knowledgeApi.ts：知识API                      │
│  │     │     ├─ memoryApi.ts：记忆API                         │
│  │     │     ├─ evolutionApi.ts：进化API                      │
│  │     │     ├─ proactiveApi.ts：主动API                      │
│  │     │     ├─ healthApi.ts：健康API                         │
│  │     │     ├─ tokenApi.ts：Token API                        │
│  │     │     └─ ...                                            │
│  │     ├─ stores/：状态管理                                   │
│  │     │     ├─ authStore.ts：认证状态                        │
│  │     │     ├─ chatStore.ts：聊天状态                        │
│  │     │     ├─ departmentStore.ts：部门状态                  │
│  │     │     ├─ agentStore.ts：Agent状态                      │
│  │     │     ├─ notificationStore.ts：通知状态                │
│  │     │     ├─ settingsStore.ts：设置状态                    │
│  │     │     ├─ brainStore.ts：大脑状态                       │
│  │     │     ├─ modelStore.ts：模型状态                       │
│  │     │     ├─ approvalStore.ts：审批状态                    │
│  │     │     ├─ taskStore.ts：任务状态                        │
│  │     │     ├─ employeeStore.ts：员工状态                    │
│  │     │     ├─ channelStore.ts：通道状态                     │
│  │     │     ├─ knowledgeStore.ts：知识状态                   │
│  │     │     ├─ memoryStore.ts：记忆状态                      │
│  │     │     ├─ evolutionStore.ts：进化状态                   │
│  │     │     ├─ proactiveStore.ts：主动状态                   │
│  │     │     ├─ healthStore.ts：健康状态                      │
│  │     │     ├─ tokenStore.ts：Token状态                      │
│  │     │     ├─ deviceStore.ts：设备状态                      │
│  │     │     └─ ...                                            │
│  │     ├─ types/：类型定义                                    │
│  │     │     ├─ auth.ts                                       │
│  │     │     ├─ chat.ts                                       │
│  │     │     ├─ department.ts                                 │
│  │     │     ├─ agent.ts                                      │
│  │     │     ├─ brain.ts                                      │
│  │     │     ├─ model.ts                                      │
│  │     │     ├─ approval.ts                                   │
│  │     │     ├─ task.ts                                       │
│  │     │     ├─ employee.ts                                   │
│  │     │     ├─ channel.ts                                    │
│  │     │     ├─ knowledge.ts                                  │
│  │     │     ├─ memory.ts                                     │
│  │     │     ├─ evolution.ts                                  │
│  │     │     ├─ proactive.ts                                  │
│  │     │     ├─ health.ts                                     │
│  │     │     ├─ token.ts                                      │
│  │     │     ├─ device.ts                                     │
│  │     │     ├─ api.ts                                        │
│  │     │     └─ ...                                            │
│  │     ├─ hooks/：自定义Hooks                                 │
│  │     │     ├─ useWebSocket.ts                               │
│  │     │     ├─ useAuth.ts                                    │
│  │     │     ├─ useChat.ts                                    │
│  │     │     ├─ useDepartment.ts                              │
│  │     │     ├─ useAgent.ts                                   │
│  │     │     ├─ useNotification.ts                            │
│  │     │     ├─ useBrain.ts                                   │
│  │     │     ├─ useModel.ts                                   │
│  │     │     ├─ useApproval.ts                                │
│  │     │     ├─ useTask.ts                                    │
│  │     │     ├─ useEmployee.ts                                │
│  │     │     ├─ useChannel.ts                                 │
│  │     │     ├─ useKnowledge.ts                               │
│  │     │     ├─ useMemory.ts                                  │
│  │     │     ├─ useEvolution.ts                               │
│  │     │     ├─ useProactive.ts                               │
│  │     │     ├─ useHealth.ts                                  │
│  │     │     ├─ useToken.ts                                   │
│  │     │     ├─ useDevice.ts                                  │
│  │     │     └─ ...                                            │
│  │     ├─ utils/：工具函数                                    │
│  │     │     ├─ formatters.ts                                 │
│  │     │     ├─ validators.ts                                 │
│  │     │     ├─ constants.ts                                  │
│  │     │     ├─ helpers.ts                                    │
│  │     │     └─ ...                                            │
│  │     ├─ pages/：页面                                        │
│  │     │     ├─ Home.tsx                                      │
│  │     │     ├─ ChatPage.tsx                                  │
│  │     │     ├─ DepartmentPage.tsx                            │
│  │     │     ├─ AgentPage.tsx                                 │
│  │     │     ├─ DashboardPage.tsx                             │
│  │     │     ├─ SettingsPage.tsx                              │
│  │     │     ├─ AdminPage.tsx                                 │
│  │     │     ├─ ApprovalPage.tsx                              │
│  │     │     ├─ TaskPage.tsx                                  │
│  │     │     ├─ EmployeePage.tsx                              │
│  │     │     ├─ ChannelPage.tsx                               │
│  │     │     ├─ KnowledgePage.tsx                             │
│  │     │     ├─ MemoryPage.tsx                                │
│  │     │     ├─ EvolutionPage.tsx                             │
│  │     │     ├─ ModelPoolPage.tsx                             │
│  │     │     ├─ HealthPage.tsx                                │
│  │     │     ├─ LoginPage.tsx                                 │
│  │     │     ├─ RegisterPage.tsx                              │
│  │     │     └─ ...                                            │
│  │     ├─ App.tsx：主应用                                     │
│  │     ├─ main.tsx：入口                                      │
│  │     └─ index.css：样式                                     │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  关键规则：                                                    │
│  │                                                            │
│  ├─ API路径不带末尾斜杠                                       │
│  │     ├─ /api/agents 而非 /api/agents/                      │
│  │                                                            │
│  ├─ WebSocket参数通过query string传递                        │
│  │     ├─ ws://host/ws/dept/tech?clientId=xxx&token=yyy      │
│  │                                                            │
│  ├─ API调用统一放src/services/                                │
│  │                                                            │
│  ├─ 类型定义放src/types/                                      │
│  │                                                            │
│  ├─ 全局状态用Zustand                                         │
│  │     ├─ stores/ 目录                                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 17.2 WebSocket交互流程

```
┌──────────────────────────────────────────────────────────────┐
│                    WebSocket交互流程                           │
├──────────────────────────────────────────────────────────────┤
│  连接建立：                                                    │
│  │                                                            │
│  ├─ useWebSocket.ts hook                                     │
│  │     ├─ const ws = new WebSocket(url)                      │
│  │     ├─ ws.onopen → 连接成功                               │
│  │     ├─ ws.onmessage → 接收消息                            │
│  │     ├─ ws.onerror → 错误处理                              │
│  │     ├─ ws.onclose → 关闭处理                              │
│  │                                                            │
│  ├─ URL构建：                                                 │
│  │     ├─ 部门聊天：                                          │
│  │     │     ├─ ws://host/ws/dept/{dept}?clientId=xxx        │
│  │     │     │                 &hostname=xxx                  │
│  │     │     │                 &platform=Windows             │
│  │     │     │                 &token=yyy                     │
│  │     │     │                 &conversationId=zzz           │
│  │     ├─ Agent聊天：                                         │
│  │     │     ├─ ws://host/ws/agent?clientId=xxx              │
│  │     │     │                 &agentId=yyy                   │
│  │     │     │                 &token=zzz                     │
│  │     │     │                 &sessionId=aaa                 │
│  │     ├─ 企业通道：                                          │
│  │     │     ├─ ws://host/ws/enterprise?clientId=xxx         │
│  │     │     │                 &token=yyy                     │
│  │     ├─ 公共通道：                                          │
│  │     │     ├─ ws://host/ws/public?clientId=xxx             │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  消息类型：                                                    │
│  │                                                            │
│  ├─ 发送消息：                                                │
│  │     ├─ {"type": "text", "content": "用户消息"}            │
│  │     ├─ {"type": "audio", "content": "base64音频"}         │
│  │     ├─ {"type": "control", "action": "abort"}             │
│  │     ├─ {"type": "ping"}                                   │
│  │     ├─ {"type": "win_automation_response", ...}           │
│  │                                                            │
│  ├─ 接收消息：                                                │
│  │     ├─ {"type": "connected", "sessionId": "..."}          │
│  │     ├─ {"type": "reconnected", "history": [...]}          │
│  │     ├─ {"type": "text", "content": "AI响应"}              │
│  │     ├─ {"type": "thinking", "content": "思考中..."}       │
│  │     ├─ {"type": "tool_call", "tool": "...", "params": ...}│
│  │     ├─ {"type": "tool_result", "result": "..."}           │
│  │     ├─ {"type": "channel_message", ...}                   │
│  │     ├─ {"type": "proactive_report", ...}                  │
│  │     ├─ {"type": "device_registered", "clientId": "..."}   │
│  │     ├─ {"type": "pong"}                                   │
│  │     ├─ {"type": "error", "code": "...", "message": "..."} │
│  │     ├─ {"type": "audio", "content": "base64"}             │
│  │     ├─ {"type": "audio_full", ...}                        │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  状态管理：                                                    │
│  │                                                            │
│  ├─ chatStore.ts                                              │
│  │     ├─ messages：消息列表                                  │
│  │     ├─ isConnected：连接状态                               │
│  │     ├─ sessionId：会话ID                                   │
│  │     ├─ isThinking：思考状态                                │
│  │     ├─ send：发送消息                                      │
│  │     ├─ addMessage：添加消息                                │
│  │     ├─ clear：清空消息                                     │
│  │                                                            │
│  ├─ notificationStore.ts                                      │
│  │     ├─ notifications：通知列表                             │
│  │     ├─ addNotification：添加通知                           │
│  │     ├─ removeNotification：移除通知                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 17.3 REST API调用流程

```
┌──────────────────────────────────────────────────────────────┐
│                    REST API调用流程                            │
├──────────────────────────────────────────────────────────────┤
│  API基础配置（services/api.ts）：                              │
│  │                                                            │
│  ├─ const api = axios.create({                                │
│  │     baseURL: '/api',                                       │
│  │     headers: { 'Content-Type': 'application/json' },      │
│  │     withCredentials: true                                  │
│  │   })                                                       │
│  │                                                            │
│  ├─ 请求拦截器：                                              │
│  │     ├─ 添加Authorization header                           │
│  │     ├─ 从authStore获取token                               │
│  │                                                            │
│  ├─ 响应拦截器：                                              │
│  │     ├─ 处理ApiResponse格式                                 │
│  │     ├─ 错误处理                                            │
│  │     ├─ 401自动跳转登录                                     │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ApiResponse格式：                                             │
│  │                                                            │
│  ├─ 成功：                                                    │
│  │     ├─ {"status": "ok", "data": {...}}                    │
│  │                                                            │
│  ├─ 失败：                                                    │
│  │     ├─ {"status": "error", "error": "...", "description": "..."│
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  主要API端点：                                                 │
│  │                                                            │
│  ├─ 认证：                                                    │
│  │     ├─ POST /api/auth/login                               │
│  │     ├─ POST /api/auth/register                            │
│  │     ├─ POST /api/auth/logout                              │
│  │     ├─ GET /api/auth/me                                   │
│  │                                                            │
│  ├─ 部门：                                                    │
│  │     ├─ GET /api/departments                               │
│  │     ├─ GET /api/departments/{code}                        │
│  │     ├─ GET /api/departments/{code}/members                │
│  │     ├─ GET /api/departments/{code}/brain                  │
│  │                                                            │
│  ├─ 大脑：                                                    │
│  │     ├─ GET /api/brains                                    │
│  │     ├─ GET /api/brains/{brainId}                          │
│  │     ├─ PUT /api/brains/{brainId}/model                    │
│  │     ├─ POST /api/brains/{brainId}/rollback                │
│  │                                                            │
│  ├─ 模型池：                                                  │
│  │     ├─ GET /api/model-providers                           │
│  │     ├─ POST /api/model-providers                          │
│  │     ├─ GET /api/llm-models                                │
│  │     ├─ POST /api/llm-models                               │
│  │     ├─ PUT /api/llm-models/{modelId}/health               │
│  │     ├─ GET /api/brain-models                              │
│  │     ├─ GET /api/brain-models/available                    │
│  │     ├─ PUT /api/brain-models/{brainId}                    │
│  │                                                            │
│  ├─ 审批：                                                    │
│  │     ├─ POST /api/approvals                                │
│  │     ├─ GET /api/approvals                                 │
│  │     ├─ GET /api/approvals/{instanceId}                    │
│  │     ├─ POST /api/approvals/{instanceId}/approve           │
│  │     ├─ POST /api/approvals/{instanceId}/reject            │
│  │                                                            │
│  ├─ 任务：                                                    │
│  │     ├─ GET /api/tasks                                     │
│  │     ├─ GET /api/tasks/{taskId}                            │
│  │     ├─ POST /api/tasks                                    │
│  │     ├─ PUT /api/tasks/{taskId}/assign                     │
│  │     ├─ POST /api/tasks/{taskId}/checkin                   │
│  │                                                            │
│  ├─ 员工：                                                    │
│  │     ├─ GET /api/employees                                 │
│  │     ├─ GET /api/employees/{employeeId}                    │
│  │     ├─ POST /api/employees                                │
│  │     ├─ PUT /api/employees/{employeeId}                    │
│  │     ├─ DELETE /api/employees/{employeeId}                 │
│  │                                                            │
│  ├─ 通道：                                                    │
│  │     ├─ GET /api/channels                                  │
│  │     ├─ POST /api/channels                                 │
│  │     ├─ POST /api/channels/{channelId}/broadcast           │
│  │                                                            │
│  ├─ 知识：                                                    │
│  │     ├─ GET /api/knowledge                                 │
│  │     ├─ POST /api/knowledge                                │
│  │     ├─ GET /api/knowledge/search                          │
│  │                                                            │
│  ├─ 进化：                                                    │
│  │     ├─ GET /api/evolution/history                         │
│  │     ├─ POST /api/evolution/feedback                       │
│  │     ├─ POST /api/evolution/auto-adjust                    │
│  │     ├─ POST /api/evolution/rollback/{brainId}             │
│  │                                                            │
│  ├─ 健康监控：                                                │
│  │     ├─ GET /api/health                                    │
│  │     ├─ GET /api/health/issues                             │
│  │     ├─ GET /api/health/metrics                            │
│  │                                                            │
│  ├─ Token使用：                                               │
│  │     ├─ GET /api/token-usage                               │
│  │     ├─ GET /api/token-usage/stats                         │
│  │                                                            │
│  ├─ Dashboard：                                               │
│  │     ├─ GET /api/dashboard/summary                         │
│  │     ├─ GET /api/dashboard/metrics                         │
│  │     ├─ GET /api/dashboard/departments                     │
│  │                                                            │
│  ├─ Windows自动化：                                           │
│  │     ├─ POST /api/windows/{clientId}/launch                │
│  │     ├─ POST /api/windows/{clientId}/click                 │
│  │     ├─ POST /api/windows/{clientId}/type                  │
│  │     ├─ GET /api/windows/{clientId}/status                 │
│  │                                                            │
│  ├─ 设备：                                                    │
│  │     ├─ GET /api/devices                                   │
│  │     ├─ GET /api/devices/{clientId}                        │
│  │                                                            │
│  ├─ Ledger（账本）：                                          │
│  │     ├─ GET /api/ledger/balance/{employeeId}               │
│  │     ├─ GET /api/ledger/history/{employeeId}               │
│  │                                                            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 18. 数据库持久化流程

### 18.1 数据库架构

```
┌──────────────────────────────────────────────────────────────┐
│                    数据库架构                                  │
├──────────────────────────────────────────────────────────────┤
│  PostgreSQL 主数据库：                                         │
│  │                                                            │
│  ├─ 表结构（250+张表）：                                      │
│  │                                                            │
│  ├─ 用户与认证：                                              │
│  │     ├─ enterprise_employees：企业员工                      │
│  │     ├─ security_identities：安全身份                       │
│  │     ├─ auth_sessions：认证会话                             │
│  │                                                            │
│  ├─ 部门与组织：                                              │
│  │     ├─ departments：部门信息                               │
│  │                                                            │
│  ├─ 模型池：                                                  │
│  │     ├─ model_providers：Provider配置                      │
│  │     ├─ llm_models：LLM模型                                │
│  │     ├─ brain_model_assignments：大脑模型分配              │
│  │     ├─ brain_model_change_history：变更历史               │
│  │     ├─ model_health_registry：健康状态                    │
│  │                                                            │
│  ├─ 审批：                                                    │
│  │     ├─ approval_instances：审批实例                        │
│  │     ├─ approval_workflows：审批工作流                      │
│  │                                                            │
│  ├─ 进化：                                                    │
│  │     ├─ evolution_results：进化结果                         │
│  │     ├─ evolution_feedback：进化反馈                        │
│  │                                                            │
│  ├─ 任务：                                                    │
│  │     ├─ tasks：任务（含状态、分配、结果）                      │
│  │                                                            │
│  ├─ 知识：                                                    │
│  │     ├─ knowledge_entries：知识条目                         │
│  │     ├─ knowledge_tags/metadata：知识标签/元数据               │
│  │     ├─ knowledge_evolution_log：知识进化日志               │
│  │                                                            │
│  ├─ Ledger账本：                                              │
│  │     ├─ ledger_transaction：交易记录                        │
│  │                                                            │
│  ├─ 设备：                                                    │
│  │     ├─ client_devices：客户端设备                          │
│  │     ├─ device_applications：设备应用                       │
│  │                                                            │
│  ├─ 连接与会话：                                              │
│  │     ├─ connection_registry：连接注册                       │
│  │     ├─ conversation_sessions：对话会话                     │
│  │     ├─ session_plans：会话计划                             │
│  │                                                            │
│  ├─ 事件：                                                    │
│  │     ├─ runtime_events：运行时事件                          │
│  │     ├─ access_audit_logs：访问审计日志                     │
│  │                                                            │
│  ├─ 合规：                                                    │
│  │     ├─ compliance_rules：合规规则                          │
│  │     ├─ compliance_violations：合规违规                     │
│  │                                                            │
│  ├─ 运行统计：                                                │
│  │     ├─ dashboard_metrics：仪表盘指标                       │
│  │     ├─ token_usage_records：Token使用                      │
│  │     ├─ health_issues：健康问题                             │
│  │                                                            │
│  ├─ 审批回调：                                                │
│  │     ├─ approval_callbacks_log：审批回调日志                │
│  │                                                            │
│  ├─ 音频缓存：                                                │
│  │     ├─ tts_cache：TTS缓存                                  │
│  │                                                            │
│  ├─ 其他：                                                    │
│  │     ├─ channel_messages：通道消息                          │
│  │     ├─ proactive_suggestions：主动建议                     │
│  │     ├─ risk_alerts：风险预警                               │
│  │     ├─ pattern_stats：模式统计                             │
│  │                                                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  JPA实体映射：                                                 │
│  │                                                            │
│  ├─ database/entity/*.java                                    │
│  │     ├─ EnterpriseEmployeeEntity                           │
│  │     ├─ DepartmentEntity                                   │
│  │     ├─ ModelProviderEntity                                │
│  │     ├─ LlmModelEntity                                     │
│  │     ├─ BrainModelAssignmentEntity                         │
│  │     ├─ ApprovalInstanceEntity                             │
│  │     ├─ ApprovalWorkflowEntity                             │
│  │     ├─ EvolutionResultEntity                              │
│  │     ├─ TaskEntity                                           │
│  │     ├─ LedgerTransactionEntity                            │
│  │     ├─ ClientDeviceEntity                                 │
│  │     ├─ ConnectionRegistryEntity                           │
│  │     ├─ RuntimeEventEntity                                 │
│  │     ├─ AccessAuditLogEntity                               │
│  │     ├─ ...                                                 │
│  │                                                            │
│  ├─ database/repository/*.java                                │
│  │     ├─ EnterpriseEmployeeRepository                       │
│  │     ├─ DepartmentRepository                               │
│  │     ├─ ModelProviderRepository                            │
│  │     ├─ LlmModelRepository                                 │
│  │     ├─ BrainModelAssignmentRepository                     │
│  │     ├─ ApprovalInstanceRepository                         │
│  │     ├─ ApprovalWorkflowRepository                         │
│  │     ├─ EvolutionResultRepository                          │
│  │     ├─ TaskRepository                                       │
│  │     ├─ LedgerTransactionRepository                        │
│  │     ├─ ClientDeviceRepository                             │
│  │     ├─ ConnectionRegistryRepository                       │
│  │     ├─ RuntimeEventRepository                             │
│  │     ├─ ...                                                 │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  初始化脚本：                                                  │
│  │                                                            │
│  ├─ init-db/01_init.sql                                      │
│  │     ├─ Docker容器启动时执行                                │
│  │     ├─ 创建所有表                                          │
│  │     ├─ 初始化默认数据                                      │
│  │                                                            │
│  ├─ schema.sql（核心模块）                                    │
│  │     ├─ 完整DDL定义                                         │
│  │     ├─ 2500+行                                             │
│  │                                                            │
│  ├─ 数据库变更流程：                                          │
│  │     ├─ 直接修改 schema.sql                                 │
│  │     ├─ 同步修改 01_init.sql                                │
│  │     ├─ 不创建 Flyway V 版本迁移文件                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 18.2 数据持久化流程

```
┌──────────────────────────────────────────────────────────────┐
│                    数据持久化流程                              │
├──────────────────────────────────────────────────────────────┤
│  实体保存流程：                                                │
│  │                                                            │
│  ├─ 1. Service层调用                                          │
│  │     ├─ ApprovalServiceImpl.createApproval()               │
│  │                                                            │
│  ├─ 2. 创建域对象                                             │
│  │     ├─ ApprovalInstance instance = new ApprovalInstance() │
│  │                                                            │
│  ├─ 3. 转换为Entity                                           │
│  │     ├─ ApprovalInstanceEntity entity = toEntity(instance) │
│  │                                                            │
│  ├─ 4. Repository保存                                         │
│  │     ├─ approvalInstanceRepository.save(entity)            │
│  │                                                            │
│  ├─ 5. JPA写入数据库                                          │
│  │     ├─ INSERT INTO approval_instances ...                  │
│  │                                                            │
│  ├─ 6. 返回域对象                                             │
│  │     ├─ return toDomain(savedEntity)                       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  实体查询流程：                                                │
│  │                                                            │
│  ├─ 1. Service层调用                                          │
│  │     ├─ EmployeeService.getEmployee(id)                    │
│  │                                                            │
│  ├─ 2. Repository查询                                         │
│  │     ├─ enterpriseEmployeeRepository.findById(id)          │
│  │                                                            │
│  ├─ 3. JPA查询数据库                                          │
│  │     ├─ SELECT * FROM enterprise_employees WHERE id = ?    │
│  │                                                            │
│  ├─ 4. 返回Entity                                             │
│  │     ├─ Optional<EnterpriseEmployeeEntity>                  │
│  │                                                            │
│  ├─ 5. 转换为域对象                                           │
│  │     ├─ entity.map(e -> toDomain(e))                       │
│  │                                                            │
│  ├─ 6. 返回Optional<Employee>                                │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  事务管理：                                                    │
│  │                                                            │
│  ├─ @Transactional 注解                                       │
│  │     ├─ 自动事务管理                                        │
│  │     ├─ 成功提交，失败回滚                                  │
│  │                                                            │
│  ├─ 事务传播：                                                │
│  │     ├─ REQUIRED：默认，加入现有事务                        │
│  │     ├─ REQUIRES_NEW：创建新事务                            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 19. Native Rust与Java交互流程

### 19.1 Native模块架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Native Rust模块架构                         │
├──────────────────────────────────────────────────────────────┤
│  living-agent-native/（Rust模块）：                            │
│  │                                                            │
│  ├─ 功能模块：                                                │
│  │     ├─ audio：音频处理                                     │
│  │     │     ├─ 音频重采样                                    │
│  │     │     ├─ 音频格式转换                                  │
│  │     │     ├─ 噪音抑制                                      │
│  │     ├─ pipe：管道通信                                      │
│  │     │     ├─ 命名管道                                      │
│  │     │     ├─ 高性能IPC                                     │
│  │     ├─ compression：压缩                                   │
│  │     │     ├─ 高效压缩算法                                  │
│  │     ├─ security：安全                                      │
│  │     │     ├─ 加密解密                                      │
│  │     │     ├─ 密钥管理                                      │
│  │     ├─ storage：存储                                       │
│  │     │     ├─ 高效文件操作                                  │
│  │     │     ├─ 缓存管理                                      │
│  │                                                            │
│  ├─ JNI接口：                                                 │
│  │     ├─ Java调用Rust函数                                    │
│  │     ├─ Rust返回数据到Java                                  │
│  │     ├─ 类型转换                                            │
│  │                                                            │
│  ├─ 编译输出：                                                │
│  │     ├─ libliving_agent_native.dll (Windows)               │
│  │     ├─ libliving_agent_native.so (Linux)                  │
│  │     ├─ libliving_agent_native.dylib (macOS)               │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  JNI调用流程：                                                 │
│  │                                                            │
│  ├─ 1. Java声明native方法                                    │
│  │     ├─ public native String processAudio(byte[] audio);   │
│  │                                                            │
│  ├─ 2. 加载Native库                                           │
│  │     ├─ System.loadLibrary("living_agent_native");         │
│  │                                                            │
│  ├─ 3. Java调用native方法                                    │
│  │     ├─ String result = processAudio(audioData);           │
│  │                                                            │
│  ├─ 4. JNI桥接层                                             │
│  │     ├─ Rust JNI函数接收调用                                │
│  │     ├─ 类型转换（Java byte[] → Rust Vec<u8>）              │
│  │                                                            │
│  ├─ 5. Rust处理                                               │
│  │     ├─ 执行音频处理                                        │
│  │     ├─ 返回结果                                            │
│  │                                                            │
│  ├─ 6. JNI返回                                                │
│  │     ├─ 类型转换（Rust String → Java String）               │
│  │                                                            │
│  ├─ 7. Java接收结果                                           │
│  │     ├─ 返回给调用者                                        │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  性能优势：                                                    │
│  │                                                            │
│  ├─ 音频处理：比Java快10倍+                                  │
│  ├─ 压缩算法：高效内存使用                                    │
│  ├─ 管道通信：低延迟IPC                                       │
│  ├─ 加密操作：硬件加速                                        │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 20. Python模型守护进程流程

### 20.1 model_daemon.py架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Python模型守护进程架构                      │
├──────────────────────────────────────────────────────────────┤
│  scripts/python/model_daemon.py                               │
│  │                                                            │
│  ├─ 双模型架构：                                              │
│  │     ├─ Qwen3-0.6B：沟通、表达、高效回复（Layer2闲聊）     │
│  │     ├─ Qwen3.5-2B：任务转达、工具调用、部门引导（Layer3工具）│
│  │                                                            │
│  ├─ 支持模型：                                                │
│  │     ├─ ASR：Sherpa-NCNN SenseVoice                        │
│  │     ├─ LLM：Qwen3-0.6B-GGUF / Qwen3.5-2B-GGUF             │
│  │     ├─ TTS：MeloTTS                                        │
│  │     ├─ Speaker：CAM说话人识别                              │
│  │                                                            │
│  ├─ 增强功能：                                                │
│  │     ├─ 双模型智能路由                                      │
│  │     ├─ TTS缓存机制                                         │
│  │     ├─ 快速问候响应（无需LLM）                             │
│  │     ├─ 会话历史管理                                        │
│  │     ├─ 音频重采样（16kHz Opus兼容）                        │
│  │                                                            │
│  ├─ 通信方式：                                                │
│  │     ├─ NamedPipeModelClient                                │
│  │     ├─ stdin/stdout JSON行协议                             │
│  │     ├─ Java → NamedPipe → Python → NamedPipe → Java       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  配置参数：                                                    │
│  │                                                            │
│  ├─ SHERPA_MODEL_DIR：ASR模型目录                             │
│  ├─ QWEN3_MODEL_FILE：Qwen3-0.6B模型文件                      │
│  ├─ QWEN35_MODEL_FILE：Qwen3.5-2B模型文件                     │
│  ├─ MELOTTS_MODEL_DIR：TTS模型目录                            │
│  ├─ CAM_MODEL_DIR：说话人识别模型                             │
│  ├─ SPEAKER_DATA_FILE：说话人嵌入数据                         │
│  ├─ SPEAKER_THRESHOLD：说话人识别阈值                         │
│  │                                                            │
│  ├─ CHAT_CONFIG：                                             │
│  │     ├─ max_history_turns: 5                                │
│  │     ├─ max_tokens_chat: 512                                │
│  │     ├─ max_tokens_tool: 1024                               │
│  │     ├─ temperature_chat: 0.7                               │
│  │     ├─ temperature_tool: 0.3                               │
│  │     ├─ quick_response_timeout_ms: 3000                     │
│  │     ├─ enable_intent_classification: True                  │
│  │     ├─ enable_quick_greeting: True                         │
│  │     ├─ enable_tts_cache: True                              │
│  │     ├─ tts_cache_size: 100                                 │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  意图分类关键词：                                              │
│  │                                                            │
│  ├─ TASK_KEYWORDS：                                           │
│  │     ├─ 'query', '搜索', '查找', '获取', '执行', '运行'     │
│  │     ├─ '创建', '删除', '修改', '更新', '发送', '接收'     │
│  │     ├─ '打开', '关闭', '启动', '停止', '重启'             │
│  │     ├─ 'git', 'docker', '部署', '构建', '测试'            │
│  │     ├─ '天气', '时间', '日期', '提醒', '闹钟'             │
│  │     ├─ '邮件', '消息', '通知', '报告'                     │
│  │     ├─ '帮我', '请帮我', '帮我做', '帮我查', '帮我找'     │
│  │     ├─ '转达', '告诉', '通知', '联系', '对接'             │
│  │     ├─ '申请', '审批', '报销', '请假', '加班'             │
│  │     ├─ '会议', '日程', '安排', '预约'                     │
│  │                                                            │
│  ├─ DEPARTMENT_KEYWORDS：                                     │
│  │     ├─ '技术部', '研发部', '开发部', '运维部', '测试部'   │
│  │     ├─ '行政部', '人事部', '人力资源', '财务部', '法务部' │
│  │     ├─ '销售部', '市场部', '运营部', '客服部', '产品部'   │
│  │     ├─ '设计部', '数据部', '安全部', '架构组'             │
│  │                                                            │
│  ├─ COMPLEX_KEYWORDS：                                        │
│  │     ├─ '分析', '设计', '规划', '评估', '优化', '重构'     │
│  │                                                            │
│  ├─ GREETINGS：                                               │
│  │     ├─ 'morning': ['早上好', '早安', '早啊', '早']         │
│  │     ├─ 'afternoon': ['下午好']                             │
│  │     ├─ 'evening': ['晚上好', '晚安']                       │
│  │     ├─ 'general': ['你好', '您好', 'hi', 'hello', ...]    │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 20.2 模型调用流程

```
┌──────────────────────────────────────────────────────────────┐
│                    Python模型调用流程                          │
├──────────────────────────────────────────────────────────────┤
│  完整调用链路：                                                │
│  │                                                            │
│  ┌──────────────────────────────────────────────┐            │
│  │ 1. Java发起请求                              │            │
│  │    ├─ NamedPipeModelClient.sendRequest()     │            │
│  │    ├─ 构建JSON请求                           │            │
│  │    ├─ 写入命名管道                           │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 2. Python接收请求                            │            │
│  │    ├─ model_daemon.py stdin读取              │            │
│  │    ├─ 解析JSON请求                           │            │
│  │    ├─ 判断请求类型                           │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 3. 意图分类                                  │            │
│  │    ├─ classify_intent(text)                  │            │
│  │    ├─ 检查是否问候语                         │            │
│  │    ├─ 检查是否任务关键词                     │            │
│  │    ├─ 检查是否部门关键词                     │            │
│  │    ├─ 判断复杂度                             │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 4. 选择模型                                  │            │
│  │    ├─ CHAT（闲聊）→ Qwen3-0.6B               │            │
│  │    ├─ TASK（任务）→ Qwen3.5-2B               │            │
│  │    ├─ GREETING（问候）→ 直接响应             │            │
│  │    ├─ COMPLEX（复杂）→ Qwen3.5-2B            │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 5. 执行推理                                  │            │
│  │    ├─ llama-cpp-python调用                   │            │
│  │    ├─ 加载历史对话                           │            │
│  │    ├─ 构建prompt                             │            │
│  │    ├─ 模型推理                               │            │
│  │    ├─ 生成响应                               │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 6. 返回结果                                  │            │
│  │    ├─ 构建JSON响应                           │            │
│  │    ├─ stdout输出                             │            │
│  │    ├─ Java NamedPipeModelClient读取          │            │
│  └──────────────────────────────────────────────┘            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  问候语快速响应：                                              │
│  │                                                            │
│  ├─ is_greeting(text)：                                       │
│  │     ├─ 检查是否匹配GREETINGS列表                          │
│  │     ├─ 无需LLM推理                                         │
│  │     ├─ 直接返回预设问候语                                  │
│  │                                                            │
│  ├─ quick_greeting_response(text)：                           │
│  │     ├─ 根据时间段返回问候语                                │
│  │     ├─ 响应时间<100ms                                      │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  ASR流程：                                                     │
│  │                                                            │
│  ├─ 1. Java发送音频数据                                       │
│  │     ├─ {"type": "asr", "audio": "base64..."}              │
│  │                                                            │
│  ├─ 2. Python Sherpa-NCNN处理                                 │
│  │     ├─ 解码base64                                          │
│  │     ├─ 重采样到16kHz                                       │
│  │     ├─ SenseVoice识别                                      │
│  │                                                            │
│  ├─ 3. 返回文本                                               │
│  │     ├─ {"text": "识别结果", "confidence": 0.95}           │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  TTS流程：                                                     │
│  │                                                            │
│  ├─ 1. Java发送文本                                           │
│  │     ├─ {"type": "tts", "text": "要合成的话"}              │
│  │                                                            │
│  ├─ 2. Python MeloTTS合成                                     │
│  │     ├─ 检查TTS缓存                                         │
│  │     ├─ 缓存命中直接返回                                    │
│  │     ├─ 缓存未命中则合成                                    │
│  │     ├─ 存入缓存                                            │
│  │                                                            │
│  ├─ 3. 返回音频                                               │
│  │     ├─ {"audio": "base64...", "format": "wav"}            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 21. Windows自动化桥接流程

### 21.1 Windows自动化架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Windows自动化桥接架构                       │
├──────────────────────────────────────────────────────────────┤
│  完整桥接链路：                                                │
│  │                                                            │
│  ├─ AI大脑 → ToolNeuron → WindowsAutomationTool              │
│  │     ↓ WebSocket                                            │
│  ├─ living-agent-desktop (Electron客户端)                     │
│  │     ↓ stdin/stdout (JSON行协议)                            │
│  ├─ service.py (Python内嵌子进程)                             │
│  │     ↓ UIA/PowerShell/psutil/win32api                       │
│  ├─ Windows系统                                               │
│  │     ↑ WIN_AUTOMATION_RESPONSE                              │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  living-agent-desktop（Electron客户端）：                      │
│  │                                                            │
│  ├─ src/main/win-automation-service.ts                        │
│  │     ├─ 管理Python子进程                                    │
│  │     ├─ WebSocket消息处理                                   │
│  │     ├─ stdin/stdout JSON协议                               │
│  │                                                            │
│  ├─ resources/win-automation/service.py                       │
│  │     ├─ UIAutomation API                                    │
│  │     ├─ PowerShell执行                                      │
│  │     ├─ psutil进程管理                                      │
│  │     ├─ win32api注册表/文件系统                             │
│  │                                                            │
│  ├─ 支持操作：                                                │
│  │     ├─ UI操作：click, type, read, screenshot               │
│  │     ├─ 进程操作：launch, process_list, process_kill        │
│  │     ├─ 注册表：registry_get, registry_set, registry_list  │
│  │     ├─ 文件系统：filesystem_read/write/copy/move/delete   │
│  │     ├─ 剪贴板：clipboard_get, clipboard_set               │
│  │     ├─ 多选：multi_select, multi_edit                      │
│  │     ├─ 虚拟桌面：vdm_switch, vdm_create, vdm_move_window  │
│  │     ├─ Shell：shell执行命令                                │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  权限分级：                                                    │
│  │                                                            │
│  ├─ CHAT_ONLY/LIMITED：                                       │
│  │     ├─ click, type, read, screenshot                       │
│  │     ├─ process_list, registry_get, registry_list           │
│  │     ├─ filesystem_read, clipboard_get                      │
│  │                                                            │
│  ├─ FULL（高风险）：                                          │
│  │     ├─ shell, process_kill                                 │
│  │     ├─ registry_set, registry_delete                       │
│  │     ├─ filesystem_write, filesystem_delete                 │
│  │     ├─ BrainBoundaryEnforcer边界检查                       │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  通信协议：                                                    │
│  │                                                            │
│  ├─ 请求格式（WebSocket）：                                   │
│  │     ├─ {"type": "win_automation",                          │
│  │     │     "clientId": "xxx",                               │
│  │     │     "requestId": "yyy",                              │
│  │     │     "operation": "click",                            │
│  │     │     "params": {...}}                                 │
│  │                                                            │
│  ├─ Python执行（stdin）：                                     │
│  │     ├─ {"operation": "click", "params": {...}}             │
│  │                                                            │
│  ├─ Python结果（stdout）：                                    │
│  │     ├─ {"success": true, "result": {...}}                  │
│  │                                                            │
│  ├─ WebSocket响应：                                           │
│  │     ├─ {"type": "win_automation_response",                 │
│  │     │     "requestId": "yyy",                              │
│  │     │     "success": true,                                 │
│  │     │     "result": {...}}                                 │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 21.2 Windows自动化执行流程

```
┌──────────────────────────────────────────────────────────────┐
│                    Windows自动化执行流程                       │
├──────────────────────────────────────────────────────────────┤
│  详细执行步骤：                                                │
│  │                                                            │
│  ┌──────────────────────────────────────────────┐            │
│  │ 1. AI大脑决策                                │            │
│  │    ├─ 用户请求"打开Excel并输入数据"          │            │
│  │    ├─ AI识别需要Windows自动化                │            │
│  │    ├─ 生成工具调用意图                       │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 2. ToolNeuron解析                            │            │
│  │    ├─ 解析工具名称：win_automation           │            │
│  │    ├─ 解析参数：                             │            │
│  │    │   ├─ operation: ["launch", "type"]     │            │
│  │    │   ├─ params: {app: "Excel", text: "..."}│            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 3. WindowsAutomationTool.execute()           │            │
│  │    ├─ 检查权限                               │            │
│  │    ├─ BrainBoundaryEnforcer边界检查         │            │
│  │    ├─ clientGateway.sendRequest()            │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 4. WebSocket发送                             │            │
│  │    ├─ 查找clientId对应的WebSocketSession    │            │
│  │    ├─ 发送JSON消息                           │            │
│  │    ├─ {"type": "win_automation", ...}        │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 5. Electron客户端接收                        │            │
│  │    ├─ win-automation-service.ts              │            │
│  │    ├─ 解析WebSocket消息                      │            │
│  │    ├─ 启动Python子进程                       │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 6. Python service.py执行                     │            │
│  │    ├─ stdin读取JSON请求                      │            │
│  │    ├─ 根据operation执行：                    │            │
│  │    │   ├─ launch → subprocess.Popen()       │            │
│  │    │   ├─ click → UIAutomation API          │            │
│  │    │   ├─ type → SendKeys                   │            │
│  │    │   ├─ screenshot → PIL                  │            │
│  │    │   ├─ process_list → psutil             │            │
│  │    │   ├─ registry_get → win32api           │            │
│  │    │   ├─ filesystem_read → open()          │            │
│  │    ├─ stdout输出JSON结果                     │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 7. Electron客户端回传                        │            │
│  │    ├─ 读取Python stdout                      │            │
│  │    ├─ 构建WebSocket响应                      │            │
│  │    ├─ {"type": "win_automation_response",...}│            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 8. 服务端处理响应                            │            │
│  │    ├─ AgentWebSocketHandler                  │            │
│  │    │     .handleWinAutomationResponse()      │            │
│  │    ├─ 或DepartmentWebSocketHandler处理       │            │
│  │    ├─ 更新ExecutionResult                    │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 9. AI继续推理                                │            │
│  │    ├─ 工具结果注入LLM上下文                  │            │
│  │    ├─ AI判断下一步                           │            │
│  │    ├─ 可能继续调用工具                       │            │
│  │    ├─ 或生成最终响应                         │            │
│  └──────────────────────────────────────────────┘            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 22. Claude CLI代理流程

### 22.1 ClaudeProxy架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Claude CLI代理架构                          │
├──────────────────────────────────────────────────────────────┤
│  ClaudeCliTool：                                             │
│  │                                                            │
│  ├─ 目的：                                                    │
│  │     ├─ 将本地Claude CLI作为工具集成                        │
│  │     ├─ 支持Claude Code能力                                 │
│  │                                                            │
│  ├─ 配置：                                                    │
│  │     ├─ claude.cliPath：Claude CLI路径                     │
│  │     ├─ claude.defaultModel：默认模型                      │
│  │                                                            │
│  ├─ execute(params)：                                         │
│  │     ├─ 构建Claude CLI命令                                  │
│  │     ├─ 执行命令                                            │
│  │     ├─ 解析输出                                            │
│  │     ├─ 返回结果                                            │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  执行流程：                                                    │
│  │                                                            │
│  ├─ 1. AI调用ClaudeCliTool                                 │
│  │     ├─ {"tool": "claude_proxy",                           │
│  │     │     "params": {"prompt": "...", "model": "claude-3"}}│
│  │                                                            │
│  ├─ 2. ClaudeCliTool.execute()                              │
│  │     ├─ 构建命令：claude -p "prompt" -m model               │
│  │                                                            │
│  ├─ 3. 执行CLI                                                │
│  │     ├─ subprocess执行                                      │
│  │     ├─ 获取输出                                            │
│  │                                                            │
│  ├─ 4. 返回结果                                               │
│  │     ├─ {"success": true, "output": "..."}                 │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 23. Docker部署与启动流程

### 23.1 Docker架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Docker部署架构                              │
├──────────────────────────────────────────────────────────────┤
│  docker-compose.yml：                                          │
│  │                                                            │
│  ├─ 服务定义：                                                │
│  │     ├─ living-agent-service：主服务                       │
│  │     ├─ postgres：PostgreSQL数据库                         │
│  │     ├─ redis：Redis缓存                                   │
│  │     ├─ qdrant：向量数据库                                 │
│  │     ├─ kafka：消息队列                                    │
│  │     ├─ zookeeper：Kafka依赖                               │
│  │                                                            │
│  ├─ living-agent-service配置：                                │
│  │     ├─ build: context: . dockerfile: living-agent-app/Dockerfile.local│
│  │     ├─ ports:                                              │
│  │     │     ├─ "8382:8382"：HTTP + WebSocket                 │
│  │     ├─ environment:                                        │
│  │     │     ├─ SPRING_PROFILES_ACTIVE                        │
│  │     │     ├─ DATABASE_URL                                  │
│  │     │     ├─ REDIS_HOST + REDIS_PORT                       │
│  │     │     ├─ QDRANT_HOST + QDRANT_PORT                     │
│  │     │     ├─ KAFKA_BOOTSTRAP_SERVERS                       │
│  │     │     ├─ LIVING_AGENT_DATA_PATH                        │
│  │     │     ├─ AI_MODELS_PATH                                │
│  │     ├─ volumes:                                            │
│  │     │     ├─ ./data:/app/data                              │
│  │     │     ├─ ./ai-models:/app/ai-models                   │
│  │     │     ├─ ./logs:/app/logs                              │
│  │     ├─ depends_on:                                         │
│  │     │     ├─ postgres                                      │
│  │     │     ├─ redis                                         │
│  │     │     ├─ qdrant                                        │
│  │     │     ├─ kafka                                         │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  Dockerfile：                                                  │
│  │                                                            │
│  ├─ FROM eclipse-temurin:21-jdk                              │
│  ├─ WORKDIR /app                                              │
│  ├─ COPY target/living-agent-app.jar app.jar                  │
│  ├─ COPY scripts/python scripts/python                        │
│  ├─ COPY init-db init-db                                      │
│  ├─ EXPOSE 8382                                                │
│  ├─ ENTRYPOINT ["java", "-jar", "app.jar"]                    │
│  │                                                            │
├──────────────────────────────────────────────────────────────┤
│  数据库初始化：                                                │
│  │                                                            │
│  ├─ postgres服务：                                            │
│  │     ├─ volumes:                                            │
│  │     │     ├─ ./init-db:/docker-entrypoint-initdb.d        │
│  │     ├─ 启动时执行01_init.sql                              │
│  │     ├─ 创建所有表                                          │
│  │     ├─ 初始化默认数据                                      │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

### 23.2 启动流程

```
┌──────────────────────────────────────────────────────────────┐
│                    Docker启动流程                              │
├──────────────────────────────────────────────────────────────┤
│  完整启动流程：                                                │
│  │                                                            │
│  ┌──────────────────────────────────────────────┐            │
│  │ 1. docker-compose up                         │            │
│  │    ├─ 启动所有服务                           │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 2. 依赖服务启动                              │            │
│  │    ├─ PostgreSQL启动                         │            │
│  │    │     ├─ 执行01_init.sql                  │            │
│  │    │     ├─ 创建数据库                       │            │
│  │    │     ├─ 创建表                           │            │
│  │    ├─ Redis启动                              │            │
│  │    ├─ Qdrant启动                             │            │
│  │    ├─ Kafka + Zookeeper启动                  │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 3. living-agent-service启动                 │            │
│  │    ├─ Spring Boot启动                        │            │
│  │    ├─ 加载配置                               │            │
│  │    ├─ 连接数据库                             │            │
│  │    ├─ 连接Redis                              │            │
│  │    ├─ 连接Qdrant                             │            │
│  │    ├─ 连接Kafka                              │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 4. Bean初始化                                │            │
│  │    ├─ BrainRegistry注册所有大脑              │            │
│  │    ├─ NeuronRegistry注册所有神经元           │            │
│  │    ├─ ToolRegistry注册所有工具               │            │
│  │    ├─ SkillRegistry加载技能                  │            │
│  │    ├─ KnowledgeBase初始化                    │            │
│  │    ├─ Memory初始化                           │            │
│  │    ├─ ModelPoolManager初始化                 │            │
│  │    ├─ ApprovalService初始化默认工作流        │            │
│  │    ├─ FixedEmployeeRegistry加载固定员工      │            │
│  │    ├─ EvolutionScheduler启动定时任务         │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 5. 专业知识注入                              │            │
│  │    ├─ ProfessionalKnowledgeSeeder            │            │
│  │    │     ├─ 加载documents/目录               │            │
│  │    │     ├─ 注入企业知识                     │            │
│  │    ├─ ArchitectureKnowledgeSeeder            │            │
│  │    │     ├─ SourceTreeIndexer扫描代码        │            │
│  │    │     ├─ 注入代码架构知识                 │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 6. Python模型守护进程启动                    │            │
│  │    ├─ model_daemon.py                        │            │
│  │    │     ├─ 加载ASR模型                      │            │
│  │    │     ├─ 加载LLM模型                      │            │
│  │    │     ├─ 加载TTS模型                      │            │
│  │    │     ├─ 启动命名管道监听                 │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 7. WebSocket端点注册                         │            │
│  │    ├─ /ws/agent                              │            │
│  │    ├─ /ws/dept/{dept}                        │            │
│  │    ├─ /ws/enterprise                         │            │
│  │    ├─ /ws/public                             │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 8. REST API端点注册                          │            │
│  │    ├─ 30+个Controller                        │            │
│  │    ├─ 所有API端点                            │            │
│  └──────────────────────────────────────────────┘            │
│                     ↓                                         │
│  ┌──────────────────────────────────────────────┐            │
│  │ 9. 服务就绪                                  │            │
│  │    ├─ 日志输出"Started LivingAgentApplication"│            │
│  │    ├─ 端口监听                               │            │
│  │    ├─ 接收请求                               │            │
│  └──────────────────────────────────────────────┘            │
│  │                                                            │
└──────────────────────────────────────────────────────────────┘
```

## 24. 流程关联关系总图

### 24.1 核心流程依赖关系

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                        Living Agent Service - 流程关联总图                                    │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  用户请求入口层:                                                                              │
│  ├─ WebSocket /ws/agent → AgentWebSocketHandler → AgentService → NeuronCoordinator          │
│  ├─ WebSocket /ws/dept → DepartmentWebSocketHandler → DepartmentChatService → 自治编排      │
│  ├─ WebSocket /ws/enterprise → FULL权限 → 所有大脑                                           │
│  ├─ WebSocket /ws/public → 无需登录 → 仅闲聊神经元                                           │
│                                                                                              │
│  权限检查层:                                                                                  │
│  ├─ PermissionService → checkPermission(userId, endpoint)                                   │
│  ├─ getPermissionLevel(userId) → CHAT_ONLY/LIMITED/DEPARTMENT/FULL                          │
│  ├─ 权限不足 → ApiResponse.err("permission_denied")                                          │
│                                                                                              │
│  智能分析层:                                                                                  │
│  ├─ IntentAnalyzer → 意图识别                                                                │
│  ├─ DialogueAnalyzer → 对话分析                                                              │
│  ├─ TaskRouteClassifier → 任务路由分类                                                       │
│  ├─ PatternPredictor → 用户行为模式预测                                                      │
│                                                                                              │
│  大脑选择层:                                                                                  │
│  ├─ MainBrainTaskDirector → selectBrain(intent, context)                                    │
│  ├─ 权限级别判断 + 任务类型判断 → 返回目标大脑                                                │
│  ├─ MainBrain(跨部门) / TechBrain(技术) / HrBrain(人力) / FinanceBrain(财务)                 │
│  ├─ SalesBrain(销售) / CsBrain(客服) / AdminBrain(行政) / LegalBrain(法务) / OpsBrain(运营)│
│                                                                                              │
│  模型调用层:                                                                                  │
│  ├─ BrainModelSelectorManager → selectModel(brainId)                                         │
│  ├─ BrainModelResolver → 查询brain_model_assignments表                                       │
│  ├─ LlmClient → NamedPipeModelClient → model_daemon.py                                          │
│  ├─ DualModelIntentClassifier → Qwen3-0.6B(闲聊) / Qwen3.5-2B(任务)                         │
│                                                                                              │
│  工具执行层:                                                                                  │
│  ├─ ToolRegistry → 70+个工具注册                                                             │
│  ├─ DefaultToolExecutor → 解析请求 → 验证参数 → 执行工具 → 记录ToolTrace                      │
│  ├─ JiraTool / GitLabTool / EmailTool / DockerTool / WindowsAutomationTool                      │
│                                                                                              │
│  知识与记忆层:                                                                                │
│  ├─ SQLiteKnowledgeBase → L1_PRIVATE / L2_DEPARTMENT / L3_SHARED                             │
│  ├─ SQLiteMemoryBackend → ConversationMemory / EpisodicMemory / SemanticMemory               │
│  ├─ Qdrant向量搜索 → 向量生成 → 向量索引                                                      │
│                                                                                              │
│  反馈与进化层:                                                                                │
│  ├─ EvolutionFeedbackService → record(result) → JPA持久化                                    │
│  ├─ EvolutionOrchestrator → runAutoAdjust(brainId) → AUTO_ADJUST_THRESHOLD=0.4               │
│  ├─ BrainModelAssigner → assignModel() → BrainModelChangeHistory                             │
│  ├─ ProactiveOrchestrator → PatternPredictor + RiskPredictor + ProactiveSuggestionService   │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 24.2 数据流向关系

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              数据流向关系图                                                    │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  用户输入 → AgentRequest → AutonomyTraceService → ApiResponse → WebSocketMessage                  │
│                                                                                              │
│  Trace对象:                                                                                  │
│  ├─ AutonomyTraceService(traceId, userId, startTime, dialogueType, intentResult, department)       │
│  ├─ ToolTrace(toolName, params, result, executionTimeMs, success)                           │
│                                                                                              │
│  持久化对象:                                                                                  │
│  ├─ TraceEventEntity → PostgreSQL (自治跟踪事件)                                            │
│  ├─ EvolutionFeedbackEntity → PostgreSQL (进化反馈)                                          │
│  ├─ ApprovalInstanceEntity → PostgreSQL (审批实例)                                           │
│  ├─ KnowledgeEntryEntity → PostgreSQL (知识条目)                                             │
│  ├─ TaskEntity → PostgreSQL (任务)                                                           │
│                                                                                              │
│  向量存储:                                                                                    │
│  ├─ Qdrant → knowledge_entries向量集合（向量存储在Qdrant，元数据在PG）                        │
│  ├─ 向量搜索 → 相似度计算 → 返回knowledge_entry_id                                            │
│                                                                                              │
│  缓存层:                                                                                      │
│  ├─ Redis → 会话缓存(sessionId→AgentSession) + 权限缓存(userId→PermissionLevel)             │
│  ├─ 模型缓存(brainId→LlmModel) + 工具缓存(toolName→Tool) + TTS缓存(text_hash→audio_url)     │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 24.3 组件依赖关系矩阵

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              组件依赖关系矩阵                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  核心组件依赖：                                                                               │
│  PermissionService → AccessLevel枚举 (硬编码权限级别)                                          │
│  PermissionService → RedisTemplate (权限缓存)                                                │
│  AgentService → PermissionService (权限检查)                                                 │
│  AgentService → NeuronCoordinator (神经元协调)                                               │
│  AgentService → TraceEventRepository (Trace存储)                                          │
│  DepartmentChatService → PermissionService (权限检查)                                        │
│  DepartmentChatService → ConversationOrchestrator (自治编排)                                 │
│  ConversationOrchestrator → DialogueAnalyzer (对话分析)                                      │
│  ConversationOrchestrator → TaskRouteClassifier (任务路由)                                   │
│  ConversationOrchestrator → MainBrainTaskDirector (大脑选择)                                 │
│  ConversationOrchestrator → FixedEmployeeDispatcher (员工调度)                              │
│                                                                                              │
│  大脑依赖：                                                                                   │
│  AbstractBrain → BrainReActEngine (ReAct引擎)                                                │
│  AbstractBrain → LlmClient (模型调用)                                                        │
│  AbstractBrain → ToolExecutor (工具执行)                                                     │
│  AbstractBrain → KnowledgeBase (知识查询)                                                    │
│  AbstractBrain → MemoryBackend (记忆检索)                                                    │
│  MainBrain → BrainModelSelectorManager (动态模型选择)                                        │
│  MainBrain → DepartmentCoordinator (跨部门协调)                                              │
│  TechBrain → GitLabTool / JiraTool / DockerTool / ClaudeCliTool                              │
│                                                                                              │
│  工具依赖：                                                                                   │
│  ToolRegistry → 70+ Tool实现 (工具注册)                                                       │
│  DefaultToolExecutor → ToolRegistry (工具查找)                                               │
│  JiraTool → JiraApiService (Jira API调用)                                                   │
│  GitLabTool → ProcessBuilder (Git CLI执行)                                                      │
│  WindowsAutomationTool → WebSocketClient (Electron通信)                                      │
│  ClaudeCliTool → ProcessBuilder (Claude CLI执行)                                           │
│                                                                                              │
│  模型依赖：                                                                                   │
│  BrainModelSelectorManager → BrainModelResolver (模型解析)                                   │
│  BrainModelSelectorManager → ModelPoolManager (模型池管理)                                   │
│  BrainModelResolver → BrainModelAssignmentRepository (分配查询)                              │
│  BrainModelAssigner → BrainModelAssignmentRepository (分配存储)                              │
│  LlmClient → Provider实现 (Provider调用)                                                      │
│  NamedPipeModelClient → model_daemon.py (命名管道通信)                                          │
│                                                                                              │
│  进化与反馈依赖：                                                                             │
│  EvolutionOrchestrator → SignalExtractor (信号提取)                                          │
│  EvolutionOrchestrator → EvolutionDecisionEngine (决策引擎)                                  │
│  EvolutionOrchestrator → EvolutionExecutor (执行器)                                          │
│  EvolutionOrchestrator → EvolutionFeedbackService (反馈服务)                                 │
│  EvolutionOrchestrator → BrainModelSelectorManager (模型选择)                                │
│  ProactiveOrchestrator → PatternPredictor (模式预测)                                         │
│  ProactiveOrchestrator → RiskPredictor (风险预测)                                            │
│  ProactiveOrchestrator → ProactiveSuggestionService (建议服务)                              │
│                                                                                              │
│  知识与记忆依赖：                                                                             │
│  SQLiteKnowledgeBase → KnowledgeEntryRepository (知识存储)                                   │
│  SQLiteKnowledgeBase → QdrantClient (向量搜索)                                               │
│  SQLiteKnowledgeBase → EmbeddingService (向量生成)                                           │
│  SQLiteMemoryBackend → MemoryRecordRepository (记忆存储)                                     │
│  ProfessionalKnowledgeSeeder → DocumentParser (文档解析)                                     │
│  ProfessionalKnowledgeSeeder → KnowledgeBase (知识注入)                                      │
│                                                                                              │
│  审批与任务依赖：                                                                             │
│  ApprovalService → ApprovalWorkflowRepository (工作流存储)                                   │
│  ApprovalService → ApprovalInstanceRepository (实例存储)                                     │
│  TaskCheckout → TaskRepository (任务存储)                                                    │
│  TaskCheckout → EmployeeRegistry (员工查询)                                                  │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 24.4 外部系统交互关系

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              外部系统交互关系                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  Python model_daemon.py:                                                                     │
│  NamedPipeModelClient(Java) ←→ model_daemon.py(Python) ←→ AI Models(Qwen3/Qwen3.5/ASR/TTS)     │
│  通信方式: 命名管道 (JSON请求/响应)                                                          │
│                                                                                              │
│  Windows自动化桥接:                                                                           │
│  WindowsAutomationTool(Java) ←→ Electron App ←→ Python service.py ←→ Windows API            │
│  通信方式: WebSocket (JSON指令/结果)                                                         │
│                                                                                              │
│  Claude CLI代理:                                                                              │
│  ClaudeCliTool(Java) ←→ Claude CLI(本地) ←→ Claude API(Anthropic)                         │
│  通信方式: subprocess (命令执行/输出解析)                                                    │
│                                                                                              │
│  Jira集成:                                                                                    │
│  JiraTool(Java) ←→ Jira Server(企业部署)                                                     │
│  通信方式: REST API (HTTP请求/JSON响应)                                                      │
│                                                                                              │
│  邮件系统:                                                                                    │
│  EmailTool(Java) ←→ Email Server(企业邮箱)                                                   │
│  通信方式: SMTP/IMAP (发送/接收邮件)                                                         │
│                                                                                              │
│  向量数据库:                                                                                  │
│  QdrantClient(Java) ←→ Qdrant(Docker容器)                                                    │
│  通信方式: REST API (向量存储/搜索)                                                          │
│                                                                                              │
│  消息队列:                                                                                    │
│  KafkaTemplate(Spring) ←→ Kafka(Docker容器)                                                 │
│  通信方式: Kafka协议 (消息发送/消费)                                                         │
│                                                                                              │
│  前端交互:                                                                                    │
│  WebSocketHandler(Java) ←→ React前端(Vite构建)                                               │
│  通信方式: WebSocket (双向通信)                                                              │
│  Controller(Java) ←→ API Service(src/services)                                               │
│  通信方式: REST API (HTTP请求/JSON响应)                                                      │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 26. 关键代码路径索引

### 26.1 核心流程关键路径

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              核心流程关键路径索引                                              │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  WebSocket入口路径:                                                                           │
│  ├─ Handler: living-agent-gateway/.../websocket/AgentWebSocketHandler.java                  │
│  ├─ Handler: living-agent-gateway/.../websocket/DepartmentWebSocketHandler.java             │
│  ├─ Service: living-agent-gateway/.../service/AgentService.java                              │
│  ├─ Service: living-agent-gateway/.../service/DepartmentChatService.java                    │
│  ├─ Coordinator: living-agent-core/.../neuron/NeuronCoordinator.java                         │
│  ├─ Neurons: living-agent-core/.../neuron/impl/Qwen3Neuron.java                              │
│  ├─ Trace: living-agent-core/.../autonomy/trace/AutonomyTraceService.java                           │
│                                                                                              │
│  权限检查路径:                                                                                │
│  ├─ Service: living-agent-gateway/.../security/PermissionService.java                        │
│  ├─ 枚举: living-agent-core/.../security/AccessLevel.java (CHAT_ONLY/LIMITED/DEPARTMENT/FULL)│
│  ├─ 校验: living-agent-core/.../security/BrainAccessControl.java                             │
│                                                                                              │
│  大脑处理路径:                                                                                │
│  ├─ Registry: living-agent-core/.../brain/BrainRegistry.java                                 │
│  ├─ AbstractBrain: living-agent-core/.../brain/impl/AbstractBrain.java                       │
│  ├─ MainBrain: living-agent-core/.../brain/impl/MainBrain.java                               │
│  ├─ TechBrain: living-agent-core/.../brain/impl/TechBrain.java                               │
│  ├─ BrainReActEngine: living-agent-core/.../brain/impl/BrainReActEngine.java                 │
│                                                                                              │
│  模型调用路径:                                                                                │
│  ├─ Selector: living-agent-core/.../model/selector/BrainModelSelectorManager.java            │
│  ├─ Resolver: living-agent-core/.../model/pool/BrainModelResolver.java                       │
│  ├─ Assigner: living-agent-core/.../model/pool/BrainModelAssigner.java                       │
│  ├─ Client: living-agent-core/.../model/client/LlmClient.java                                │
│  ├─ Provider: living-agent-core/.../model/provider/impl/NamedPipeModelClient.java               │
│  ├─ Python守护进程: scripts/python/model_daemon.py                                           │
│                                                                                              │
│  工具执行路径:                                                                                │
│  ├─ Registry: living-agent-core/.../tool/ToolRegistry.java                                   │
│  ├─ Executor: living-agent-core/.../tool/executor/DefaultToolExecutor.java                   │
│  ├─ JiraTool: living-agent-skill/.../enterprise/JiraTool.java                                │
│  ├─ GitLabTool: living-agent-skill/.../enterprise/GitLabTool.java                                  │
│  ├─ WindowsAutomationTool: living-agent-skill/.../windows/WindowsAutomationTool.java         │
│  ├─ ClaudeCliTool: living-agent-skill/.../claude/ClaudeCliTool.java                      │
│                                                                                              │
│  进化调整路径:                                                                                │
│  ├─ Orchestrator: living-agent-core/.../evolution/engine/EvolutionOrchestrator.java          │
│  ├─ FeedbackService: living-agent-core/.../evolution/executor/impl/JpaEvolutionFeedbackService.java │
│  ├─ SignalExtractor: living-agent-core/.../evolution/signal/SignalExtractor.java             │
│                                                                                              │
│  主动预判路径:                                                                                │
│  ├─ Orchestrator: living-agent-gateway/.../proactive/ProactiveOrchestrator.java              │
│  ├─ PatternPredictor: living-agent-core/.../proactive/predictor/PatternPredictor.java        │
│  ├─ RiskPredictor: living-agent-core/.../proactive/predictor/RiskPredictor.java              │
│  ├─ SuggestionService: living-agent-core/.../proactive/suggestion/ProactiveSuggestionService.java │
│                                                                                              │
│  知识与记忆路径:                                                                              │
│  ├─ KnowledgeBase: living-agent-core/.../knowledge/impl/SQLiteKnowledgeBase.java             │
│  ├─ MemoryBackend: living-agent-core/.../memory/impl/SQLiteMemoryBackend.java                │
│  ├─ KnowledgeSeeder: living-agent-core/.../knowledge/seeder/ProfessionalKnowledgeSeeder.java │
│                                                                                              │
│  审批流程路径:                                                                                │
│  ├─ Service: living-agent-core/.../approval/impl/ApprovalServiceImpl.java                    │
│  ├─ Controller: living-agent-gateway/.../controller/ApprovalController.java                  │
│  ├─ Callback: living-agent-core/.../approval/ApprovalCallback.java                           │
│                                                                                              │
│  任务管理路径:                                                                                │
│  ├─ TaskCheckout: living-agent-core/.../task/checkout/TaskCheckout.java                      │
│  ├─ TaskAssignment: living-agent-core/.../task/assignment/TaskAssignment.java                │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 26.2 数据库表路径索引

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              数据库表路径索引                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  核心业务表:                                                                                  │
│  ├─ autonomy_trace_events: init-db/01_init.sql → TraceEventEntity → TraceEventRepository    │
│  ├─ evolution_feedback: init-db/01_init.sql → EvolutionFeedbackEntity → EvolutionFeedbackRepository │
│  ├─ approval_instances: init-db/01_init.sql → ApprovalInstanceEntity → ApprovalInstanceRepository │
│  ├─ approval_workflows: init-db/01_init.sql → ApprovalWorkflowEntity → ApprovalWorkflowRepository │
│  ├─ tasks: init-db/01_init.sql → TaskEntity → TaskRepository                                   │
│                                                                                              │
│  模型管理表:                                                                                  │
│  ├─ llm_models: init-db/01_init.sql → LlmModelEntity → LlmModelRepository                    │
│  ├─ llm_providers: init-db/01_init.sql → LlmProviderEntity → LlmProviderRepository           │
│  ├─ brain_model_assignments: init-db/01_init.sql → BrainModelAssignmentEntity → Repository  │
│  ├─ brain_model_change_history: init-db/01_init.sql → BrainModelChangeHistoryEntity → Repo  │
│                                                                                              │
│  知识与记忆表:                                                                                │
│  ├─ knowledge_entries: init-db/01_init.sql → KnowledgeEntryEntity → KnowledgeEntryRepository │
│  ├─ (向量存储使用Qdrant，非PG表)                                                              │
│                                                                                              │
│  权限与员工表:                                                                                │
│  ├─ access_audit_logs: init-db/01_init.sql → AccessAuditLog → AccessAuditLogRepository        │
│  ├─ fixed_employee_definition: init-db/01_init.sql → FixedEmployeeDefinitionEntity → FixedEmployeeDefinitionRepository │
│  ├─ fixed_employee_profile: init-db/01_init.sql → FixedEmployeeProfileEntity → FixedEmployeeProfileRepository │
│  ├─ fixed_employee_persona: init-db/01_init.sql → FixedEmployeePersonaEntity → FixedEmployeePersonaRepository │
│                                                                                              │
│  数据库初始化脚本:                                                                            │
│  ├─ Docker初始化: init-db/01_init.sql                                                        │
│  ├─ 本地开发: living-agent-core/.../database/schema.sql                                      │
│  ├─ 两个脚本内容一致，维护时需同步更新                                                        │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 26.3 配置文件路径索引

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              配置文件路径索引                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  Spring Boot配置:                                                                             │
│  ├─ application.yml: living-agent-app/src/main/resources/application.yml                    │
│  ├─ application-dev.yml: living-agent-app/src/main/resources/application-dev.yml            │
│  ├─ application-prod.yml: living-agent-app/src/main/resources/application-prod.yml          │
│                                                                                              │
│  Python配置 (环境变量覆盖):                                                                   │
│  ├─ model_daemon.py: scripts/python/model_daemon.py                                          │
│  │   SHERPA_MODEL_DIR, QWEN3_MODEL_FILE, QWEN35_MODEL_FILE, MELOTTS_MODEL_DIR               │
│  │   CHAT_CONFIG: max_history_turns, max_tokens_chat, temperature_chat                      │
│  ├─ service.py (Windows自动化): scripts/python/service.py                                    │
│                                                                                              │
│  Docker配置:                                                                                  │
│  ├─ docker-compose.yml: docker-compose.yml                                                   │
│  ├─ Dockerfile: Dockerfile                                                                   │
│                                                                                              │
│  企业知识配置:                                                                                │
│  ├─ documents/: documents/ (部门架构、员工编制、职责卡、企业知识文档)                        │
│                                                                                              │
│  前端配置:                                                                                    │
│  ├─ vite.config.ts: frontend/vite.config.ts                                                  │
│  ├─ package.json: frontend/package.json                                                      │
│  ├─ tsconfig.json: frontend/tsconfig.json                                                    │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 27. 常见问题排查指南

### 27.1 WebSocket连接问题排查

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              WebSocket连接问题排查                                             │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  问题现象: WebSocket连接失败、无法发送消息、无法接收响应                                      │
│                                                                                              │
│  排查步骤:                                                                                    │
│  1. 检查服务是否启动: curl http://localhost:8382/actuator/health                           │
│  2. 检查WebSocket端点是否注册: 查看启动日志中的端点映射                                      │
│  3. 检查权限配置: 查看AccessLevel枚举值及AccessGateService逻辑                              │
│  4. 测试WebSocket连接: wscat -c ws://localhost:8382/ws/agent?userId={userId}                 │
│  5. 发送测试消息: {"type":"chat","message":"你好"}                                            │
│  6. 检查日志错误: docker logs living-agent-service | grep ERROR                               │
│                                                                                              │
│  常见错误及解决:                                                                              │
│  ├─ "Permission denied": 更新permissions表提升权限级别                                       │
│  │   SQL: UPDATE permissions SET level = 3 WHERE user_id = '{userId}';                      │
│  ├─ "WebSocket connection failed": 检查WebSocketConfig配置和端口8765                         │
│  ├─ "Session not found": 检查Redis会话缓存，重新建立连接                                      │
│  │   Redis: redis-cli GET "session:{sessionId}"                                             │
│                                                                                              │
│  关键代码位置:                                                                                │
│  ├─ AgentWebSocketHandler: living-agent-gateway/.../websocket/AgentWebSocketHandler.java    │
│  ├─ PermissionService: living-agent-gateway/.../security/PermissionService.java              │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 27.2 模型调用问题排查

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              模型调用问题排查                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  问题现象: 模型响应慢、模型调用失败、模型返回空响应                                          │
│                                                                                              │
│  排查步骤:                                                                                    │
│  1. 检查model_daemon.py是否启动: docker exec living-agent-service ps aux | grep model_daemon│
│  2. 检查命名管道是否存在: docker exec living-agent-service ls -la /tmp/*.pipe                │
│  3. 检查模型文件是否加载: docker logs living-agent-service | grep "Loading model"            │
│  4. 检查模型分配配置: SELECT * FROM brain_model_assignments;                                 │
│  5. 检查模型调用日志: docker logs living-agent-service | grep "LlmClient"                    │
│  6. 检查进化调整状态: SELECT brain_domain, AVG(score) FROM evolution_feedback GROUP BY brain_domain; │
│                                                                                              │
│  常见错误及解决:                                                                              │
│  ├─ "Model not found": 检查模型文件路径，确保文件存在                                        │
│  │   命令: ls -la /app/ai-models/Qwen3-0.6B-GGUF/                                            │
│  ├─ "NamedPipe timeout": 重启model_daemon.py，重启Docker容器                                  │
│  │   命令: docker restart living-agent-service                                               │
│  ├─ "Model response too slow": 检查GPU/CPU资源，触发模型降级                                 │
│  │   查询: evolution_feedback表评分 → 触发: runAutoAdjust()                                  │
│  ├─ "Empty response from model": 检查prompt格式，检查CHAT_CONFIG配置                         │
│                                                                                              │
│  关键代码位置:                                                                                │
│  ├─ model_daemon.py: scripts/python/model_daemon.py                                          │
│  ├─ NamedPipeModelClient: living-agent-core/.../provider/impl/NamedPipeModelClient.java            │
│  ├─ BrainModelAssigner: living-agent-core/.../model/pool/BrainModelAssigner.java             │
│  ├─ EvolutionOrchestrator: living-agent-core/.../evolution/engine/EvolutionOrchestrator.java │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 27.3 工具执行问题排查

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              工具执行问题排查                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  问题现象: 工具调用失败、工具返回错误、工具超时                                              │
│                                                                                              │
│  排查步骤:                                                                                    │
│  1. 检查工具是否注册: ToolRegistry.getAllTools()                                             │
│  2. 检查工具权限: SELECT * FROM tool_permissions WHERE tool_name = '{toolName}';             │
│  3. 检查工具参数: SELECT * FROM autonomy_trace_events WHERE tool_calls LIKE '%{toolName}%';        │
│  4. 检查外部系统连接: curl http://jira-server/rest/api/2/serverInfo                          │
│  5. 检查Windows自动化桥接: wscat -c ws://localhost:9999                                       │
│  6. 检查工具执行日志: docker logs living-agent-service | grep "ToolExecutor"                  │
│                                                                                              │
│  常见错误及解决:                                                                              │
│  ├─ "Tool not found": 检查ToolRegistry，确保工具注册                                         │
│  │   检查: @Component注解是否存在 → 检查: ToolRegistry.initialize()扫描                      │
│  ├─ "Permission denied for tool": 提升用户权限级别                                           │
│  │   SQL: UPDATE permissions SET level = 3 WHERE user_id = '{userId}';                      │
│  ├─ "Invalid tool parameters": 检查工具JSON Schema定义                                       │
│  │   检查: 各Tool实现的getSchema()方法                                                       │
│  ├─ "External system connection failed": 检查外部系统URL和网络连接                            │
│  │   检查: application.yml中的外部系统URL → 测试: curl/telnet测试连接                        │
│  ├─ "Windows automation timeout": 重启Electron App和Python service.py                        │
│                                                                                              │
│  关键代码位置:                                                                                │
│  ├─ ToolRegistry: living-agent-core/.../tool/ToolRegistry.java                               │
│  ├─ DefaultToolExecutor: living-agent-core/.../executor/DefaultToolExecutor.java             │
│  ├─ JiraTool: living-agent-skill/.../enterprise/JiraTool.java                                │
│  ├─ WindowsAutomationTool: living-agent-skill/.../windows/WindowsAutomationTool.java         │
│  ├─ Windows自动化Python: scripts/python/service.py                                           │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 27.4 数据库问题排查

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              数据库问题排查                                                    │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  问题现象: 数据库连接失败、数据写入失败、数据查询异常                                         │
│                                                                                              │
│  排查步骤:                                                                                    │
│  1. 检查PostgreSQL服务状态: docker ps | grep postgres                                         │
│  2. 检查数据库连接配置: 查看application.yml中的datasource配置                                │
│  3. 测试数据库连接: docker exec postgres psql -U postgres -d living_agent                    │
│  4. 查看表结构: docker exec postgres psql -c "\dt"                                           │
│  5. 检查数据一致性: SELECT COUNT(*) FROM autonomy_trace_events;                                     │
│  6. 查看JPA日志: docker logs living-agent-service | grep "Hibernate"                          │
│                                                                                              │
│  常见错误及解决:                                                                              │
│  ├─ "Connection refused": 检查PostgreSQL容器启动状态                                         │
│  │   命令: docker-compose up postgres                                                        │
│  ├─ "Table not found": 检查01_init.sql执行，重新初始化数据库                                 │
│  │   命令: docker exec postgres psql -f /docker-entrypoint-initdb.d/01_init.sql              │
│  ├─ "Constraint violation": 检查唯一约束，避免重复插入                                        │
│  ├─ "Transaction rollback": 检查@Transactional配置，查看异常堆栈                              │
│                                                                                              │
│  关键代码位置:                                                                                │
│  ├─ Schema定义: init-db/01_init.sql                                                          │
│  ├─ Repository: living-agent-core/.../database/repository/                                   │
│  ├─ Entity: living-agent-core/.../database/entity/                                           │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 文档维护说明

本文档完整梳理了Living Agent Service的所有功能模块流程、关联关系和闭环需求。

**文档结构:**
- 第1-9章: 核心流程（WebSocket、权限、路由、自治编排、大脑、神经元、模型、工具）
- 第10-23章: 支撑流程（知识、记忆、员工、通道、模型池、进化、预判、审批、任务、前端、数据库、Native、Python、Windows、Claude、Docker）
- 第24章: 流程关联关系总图（核心流程依赖、数据流向、组件依赖矩阵、外部系统交互）
- 第25章: 闭环需求整理（14个闭环及验证清单）
- 第26章: 关键代码路径索引（核心流程、数据库表、配置文件路径）
- 第27章: 常见问题排查指南（WebSocket、模型、工具、数据库问题排查）

**维护建议:**
1. 代码变更时同步更新对应流程章节
2. 新增功能时补充相应流程图和闭环需求
3. 定期验证闭环清单中的待验证项
4. 问题排查时参考第27章排查指南

**文档版本: v1.0**
**生成日期: 2026-06-30**
**参考文档: CODE_STRUCTURE_AND_FILE_GUIDE.md**

---
