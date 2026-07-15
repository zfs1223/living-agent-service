# Living Agent Service 代码结构与文件功能说明

> 目的：把 `docker/living-agent-service` 的代码结构、关键文件职责和修改入口整理成一份索引，避免后续改代码时重复实现、重复落点、重复造服务。
>
> 更新时间：2026-07-09
>
> 适用范围：后端 Java/Spring Boot、前端 React/Vite、Rust Native、Python model daemon、Windows 自动化（pywinauto）、Docker 编排、数据库脚本、项目文档。

***

## 1. 项目总体结构

```text
docker/living-agent-service/
├── pom.xml                         # Maven 父工程，聚合 Java 多模块
├── docker-compose.yml              # 本地 Docker 编排，包含后端、前端、PostgreSQL、Redis、Kafka、Qdrant、Memos、OpenProject、fuck-u-code 等
├── living-agent-app/               # Spring Boot 启动模块，负责组装 core/gateway/skill/perception
├── living-agent-core/              # 核心领域层：大脑、神经元、员工、模型、工具、知识、进化、权限、工作流等
├── living-agent-gateway/           # API/WebSocket 网关层：Controller、WebSocket、前端服务适配
├── living-agent-skill/             # 技能加载、技能注册和内置技能资源
├── living-agent-perception/        # 感知相关模块，主要预留视觉/音频/输入感知能力
├── living-agent-native/            # Rust native/JNI 高性能模块
├── frontend/                       # React + Vite 前端工程
├── scripts/python/                 # Python 模型守护进程、LLM/ASR/TTS 辅助脚本
├── scripts/windows_automation/     # Windows 桌面应用自动化服务（pywinauto + FastAPI），部署到客户端电脑
├── init-db/                        # 数据库初始化脚本
├── documents/                      # 企业制度、部门文档、数字员工职责卡、治理文档
├── docs/                           # 架构、计划、问题排查和设计文档
├── image/                          # 镜像构建辅助、系统依赖、离线镜像资源
└── data/                           # 本地运行数据挂载目录
```

***

## 2. 模块职责总览

| 模块                         | 职责                       | 修改时优先找这里                                              |
| -------------------------- | ------------------------ | ----------------------------------------------------- |
| `living-agent-app`         | 应用启动、配置加载、Docker 镜像入口    | 启动失败、配置不生效、Bean 扫描问题                                  |
| `living-agent-core`        | 核心业务能力和领域模型              | 大脑、员工、任务、工具、知识、模型、权限、进化、自治逻辑                          |
| `living-agent-gateway`     | HTTP API、WebSocket、前后端交互 | 接口路径、登录、聊天、部门页、Dashboard、WebSocket                    |
| `living-agent-skill`       | 技能系统和技能资源                | 新增/修改技能、技能热加载、技能绑定                                    |
| `living-agent-perception`  | 感知层预留模块                  | 视觉、音频、传感器等感知能力扩展                                      |
| `living-agent-native`      | Rust JNI 高性能能力           | AudioNative、MemoryNative、SecurityNative、CompactNative |
| `frontend`                 | 用户界面                     | 页面、路由、接口调用、WebSocket、前端状态                             |
| `scripts/python`           | 本地模型守护进程                 | NamedPipe 模型调用、ASR/TTS/LLM Python 进程                  |
| `scripts/windows_automation` | Windows 桌面应用自动化客户端       | pywinauto HTTP 服务、金蝶 KIS 等桌面应用远程控制                    |
| `init-db` / `db/schema.sql` | 初始化和 schema               | 表结构（schema.sql 权威源 + 01_init.sql Docker 初始化），不再使用 Flyway 迁移                      |
| `documents`                | 企业知识和制度源文件               | 数字员工职责卡、部门制度、治理规则                                     |
| `docs`                     | 设计、排查和计划                 | 方案补充、问题记录、架构说明                                        |

***

## 3. Java 后端模块结构

### 3.1 父工程 `pom.xml`

| 文件        | 功能说明                                                         | 修改建议                                   |
| --------- | ------------------------------------------------------------ | -------------------------------------- |
| `pom.xml` | Maven 父工程，声明 Java 21、Spring Boot 3.4、Spring Cloud、公共依赖版本和子模块 | 添加跨模块公共依赖时改这里；单模块私有依赖优先改对应模块 `pom.xml` |

子模块：

```text
living-agent-core
living-agent-perception
living-agent-skill
living-agent-gateway
living-agent-app
```

***

## 4. `living-agent-app` 启动模块

```text
living-agent-app/
├── pom.xml
├── Dockerfile
├── Dockerfile.local
├── entrypoint.sh
└── src/main/
    ├── java/com/livingagent/LivingAgentApplication.java
    └── resources/
        ├── application.yml
        └── claude/
            ├── mcp.json                    # Claude Code MCP Server 配置（filesystem/memory/sequential-thinking/fuck-u-code）
            └── plugins/                    # Claude Code 插件（commit-commands/code-review/feature-dev/security-guidance）
```

| 文件                            | 功能说明                                                      | 常见修改场景                                    |
| ----------------------------- | --------------------------------------------------------- | ----------------------------------------- |
| `LivingAgentApplication.java` | Spring Boot 主启动类，通常负责 `@SpringBootApplication`、调度、异步等全局开关 | 启动类扫描范围、启动开关、定时任务启用问题                     |
| `application.yml`             | 后端主配置，包含端口、数据库、Redis、Kafka、模型、Native、认证、日志等配置             | 容器环境变量、模型服务地址、数据库地址、日志级别                  |
| `Dockerfile`                  | 生产/容器构建后端镜像                                               | 打包 jar、复制 native 库、安装运行依赖                 |
| `Dockerfile.local`            | 本地开发镜像构建；安装 Claude Code CLI + MCP Server npm 包；COPY 插件到 /home/livingagent/.claude/plugins | 本地调试、挂载源码或快速构建、MCP/插件安装         |
| `entrypoint.sh`               | 容器启动脚本，常用于等待依赖、启动 Java、启动 Python daemon                   | 容器启动顺序、环境变量注入、native path、model daemon 启动 |

避免重复建议：

- 不要在 `gateway` 或 `core` 再写新的启动类。
- 全局配置统一放 `application.yml` 或环境变量，不要硬编码在业务类。
- Native 加载路径优先通过 `JAVA_OPTS` / `-Djava.library.path` / Dockerfile 处理。

***

## 5. `living-agent-gateway` 网关模块

网关模块负责把前端请求转换为 core 能力调用。

```text
living-agent-gateway/src/main/java/com/livingagent/gateway/
├── audio/              # 音频处理辅助
├── config/             # Web、WebSocket、CORS、安全、Tomcat、外部平台配置
├── controller/         # REST API 控制器
├── dto/                # API 请求/响应 DTO
├── event/              # 事件定义
├── exception/          # 全局异常处理
├── executor/           # 工具执行
├── interceptor/        # 拦截器，如部门权限拦截
├── parallel/           # 并行模型调用
├── proactive/          # 网关层主动提醒/编排适配
├── prompt/             # 提示词构建
├── security/           # 网关层权限辅助
├── service/            # 面向 Controller/WebSocket 的应用服务
└── websocket/          # WebSocket Handler
```

### 5.1 配置文件

| 文件                            | 功能说明                     | 修改建议                                             |
| ----------------------------- | ------------------------ | ------------------------------------------------ |
| `config/GatewayConfig.java`   | 网关基础配置                   | 通用 Bean、跨域/网关默认行为；自治编排 Bean、固定员工分派 Bean 注册也优先在这里 |
| `config/WebSocketConfig.java` | 注册 WebSocket 路由，如聊天、部门聊天 | 新增 WebSocket 路径时改这里                              |
| `config/WebMvcConfig.java`    | 注册 MVC 拦截器、静态资源、路径规则     | 部门权限拦截、API 路径拦截                                  |
| `config/CorsConfig.java`      | CORS 配置                  | 前端跨域问题                                           |
| `config/SecurityConfig.java`  | Spring Security 配置       | 登录鉴权、放行接口、认证过滤                                   |
| `config/FounderConfig.java`   | 创始人/管理员初始化配置             | 创始人配置、初始化状态                                      |
| `config/FeishuConfig.java`    | 飞书配置                     | 飞书 OAuth / API 集成                                |
| `config/TomcatConfig.java`    | Tomcat 容器配置              | Header 限制、连接数、WebSocket 容器参数                     |
| `config/JacksonConfig.java`   | Jackson JSON 配置           | 序列化/反序列化规则、日期格式、空值处理                             |

### 5.2 WebSocket 文件

| 文件                                          | 功能说明                                                                                             | 重要注意                                                 |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `websocket/AgentWebSocketHandler.java`      | 通用 Agent/语音/神经元会话 WebSocket，通常会进入 `AgentService`、模型会话、Qwen3/BitNet 等链路；Token 认证三级优先级：Sec-WebSocket-Protocol 头 > Authorization 头 > URL 查询参数（兼容降级）                           | 不要把部门文本聊天直接塞进这里                                      |
| `websocket/DepartmentWebSocketHandler.java` | 部门聊天 WebSocket，处理 `/ws/dept/{dept}` 的部门消息、权限、在线连接、推送；已新增 `pushExecutionProgress()` 方法支持长任务异步进度推送 | 部门文本聊天应走部门大脑/自治编排，不应无条件启动 `AgentService/Qwen3Neuron` |
| `websocket/PersistentConnectionRegistry.java` | WebSocket 持久连接注册表 | 管理长连接生命周期、断线重连、连接状态追踪 |
| `websocket/ConnectionRegistry.java`         | **【新增】** WebSocket 连接注册接口，定义 bindSession/unbindSession/getSession/bindConversation/unbindConversation/getSessionIdByConversationId 方法 | WebSocket 连接管理抽象 |
| `websocket/InMemoryConnectionRegistry.java` | **【新增】** 内存版 WebSocket 连接注册实现，维护 userId → WebSocketSession 映射，支持 conversationId/taskKey/executionId/projectKey 绑定 | WebSocket 连接查询和绑定 |
| `websocket/AuthHandshakeInterceptor.java`   | **【新增】** WebSocket 认证握手拦截器，从 HTTP 请求中提取 token 并验证，将用户信息注入 WebSocket session | WebSocket 认证逻辑修改 |
| `websocket/WebSocketRateLimiter.java`       | **【新增】** WebSocket 速率限制器，限制单用户消息频率，防止滥用 | WebSocket 流控调整 |
| `websocket/WindowsAutomationClientGatewayImpl.java` | **【新增】** Windows 自动化 WebSocket 客户端网关实现，维护 clientId → WebSocketSession 映射，转发 WIN_AUTOMATION_CALL/WIN_AUTOMATION_RESPONSE 消息 | Windows 自动化 WebSocket 通信 |

### 5.3 关键 Service

| 文件                                            | 功能说明                                                                  | 修改建议                                                 |
| --------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------- |
| `service/AgentService.java`                   | Agent 会话服务，创建模型 session、绑定神经元、处理语音/通用 Agent 链路                        | 语音、Qwen3Neuron、NamedPipe 模型会话相关改这里；部门文本不要复用它         |
| `service/WorkItemContextService.java`         | **【新增】** 工作项上下文服务                        | 维护工作项上下文，支持 ExecutionSnapshotRecorder                              |
| `service/PublicTaskEventPublisher.java`       | **【新增】** 公开任务事件发布器                        | 向前端 WebSocket 推送任务事件                              |
| `service/ExecutionProgressBroadcaster.java`   | **【新增】** 执行进度广播器                        | 向 WebSocket 客户端广播执行进度                              |
| `service/DepartmentChatService.java`          | 部门聊天服务，保存部门消息、调用部门大脑、承接自治编排结果、透传部门计划/员工任务单/准备批次到部门大脑，并汇总执行派发与回执 Trace；已添加 triggeredFinalResponses 原子集合防止轮询/监听双路径重复触发；executionResultCache 缓存解决监听路径 executionResult=null；activeSessionPlans 追踪活跃计划实现需求冻结/防漂移；NP1-4: processBrainResponseWithContract 构建合成 ChannelMessage 委托到 processBrainResponse，消除双通道重复逻辑；findByExecutionId 调用已改为 .isEmpty()/.stream().findFirst() | 部门聊天落库、部门大脑调用、自治编排入口、固定员工分派建议、任务单准备、执行回执 Trace 优先改这里 |
| `service/TaskWorkflowService.java`            | 网关任务流转服务                                                              | 任务状态、任务事件、部门任务流转                                     |
| `service/TaskEventBridgeService.java`         | 将任务事件桥接到其他模块                                                          | 任务与绩效/消息/通知联动                                        |
| `service/TaskPerformanceBridgeService.java`   | 任务绩效桥接                                                                | 任务完成后记绩效                                             |
| `service/EvolutionFeedbackBridgeService.java` | 进化反馈桥接                                                                | 用户反馈、任务结果进入进化系统                                      |
| `service/DashboardDataService.java`           | Dashboard 数据聚合                                                        | 首页/管理看板数据来源                                          |
| `service/PerformanceDashboardService.java`    | 绩效看板服务，已移除 instanceof 耦合，直接调用接口方法                                                                | 绩效统计、趋势图                                             |
| `service/OrganizationQueryService.java`       | 组织/员工查询服务                                                             | 部门、员工、组织结构查询                                         |
| `service/DepartmentNotificationService.java`  | 部门通知服务                                                                | 部门内消息、提醒、广播                                          |
| `service/KnowledgeGovernanceService.java`     | 知识治理应用服务                                                              | 知识晋升、审核、治理流程                                         |
| `service/KnowledgePromotionAuditService.java` | 知识晋升审计                                                                | 知识库审批和日志                                             |
| `service/BackupRecoveryService.java`          | 备份恢复                                                                  | 系统备份、恢复、导出                                           |
| `service/MonitoringService.java`              | 监控服务                                                                  | 系统状态、健康检查聚合                                          |
| `service/SystemConfigService.java`            | 系统设置服务                                                                | 管理后台配置读写                                             |
| `service/SessionContext.java`                 | 会话上下文；已新增 taskKey/executionId 字段                                                                 | 当前用户、租户、权限、任务上下文                                       |
| `service/DialogueService.java`                | 对话会话服务                                                                | WebSocket 对话会话管理                                     |
| `service/OrganizationQueryServiceImpl.java`   | 组织查询服务实现                                                              | 部门、员工、组织结构查询，支持我的部门、部门成员                             |
| `dialogue/DialogueSession.java`               | 对话会话模型                                                                | WebSocket 会话状态、消息历史、部门上下文                            |
| `dialogue/DialogueMessage.java`               | 对话消息模型                                                                | 用户消息、系统消息、脑回复、执行进度等类型                                |
| `dialogue/DialogueSessionManager.java`        | 对话会话管理器                                                               | 会话创建、查找、销毁、超时管理                                      |

### 5.4 Controller 分类索引

| Controller                                                          | 功能说明                  | 常见前端页面                                                                                             |
| ------------------------------------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------- |
| `AuthController.java`                                               | 认证相关接口（含 `GET /api/auth/check` 权限检查端点）                | 登录、用户信息、权限检查                                                                                            |
| `PhoneAuthController.java`                                          | 手机验证码登录               | `Login.tsx`                                                                                        |
| `EnterpriseController.java` / `EnterpriseApiController.java`        | 企业设置、企业信息             | 企业设置页                                                                                              |
| `TenantController.java`                                             | 租户管理                  | 多租户/企业初始化                                                                                          |
| `SystemController.java` / `SystemSettingsController.java`           | 系统状态和系统设置             | 设置、状态页                                                                                             |
| `HealthController.java`                                             | **P12-B** K8s 健康检查端点（`/api/health`, `/api/health/live`, `/api/health/ready`） | Kubernetes liveness/readiness 探针、桌面端状态检查                                                          |
| `DashboardController.java`                                          | Dashboard API         | `Dashboard.tsx`、`PlatformDashboard.tsx`                                                            |
| `AgentController.java` / `AgentApiController.java`                  | Agent 列表、详情、创建、操作     | Agent 管理页                                                                                          |
| `AgentChannelController.java`                                       | Agent 通道管理接口          | Agent 通道配置、通道状态                                                                                   |
| `AgentScheduleController.java`                                      | Agent 调度接口            | Agent 定时调度、触发规则                                                                                   |
| `AgentTriggerController.java`                                       | Agent 触发器接口           | Agent 手动/事件触发                                                                                     |
| `DepartmentController.java` / `DepartmentApiController.java`        | 部门信息、部门详情、部门大脑        | 部门页                                                                                                |
| `EmployeeController.java`                                           | 员工管理                  | 员工/用户管理                                                                                            |
| `FixedEmployeeController.java`                                      | 固定数字员工定义、画像、档案        | 部门数字员工设置                                                                                           |
| `KnowledgeController.java`                                          | 知识库接口                 | 知识管理                                                                                               |
| `MessageController.java`                                            | 消息接口                  | 消息中心                                                                                               |
| `TaskController.java` / `AgentTaskController.java`                  | 任务接口                  | 我的任务、任务看板。**注意**：`AgentTaskController` 已标记 `@Deprecated`，所有任务操作统一使用 `TaskController` 的 `/tasks` 路由 |
| `ProjectController.java`                                            | 项目接口                  | 项目管理。项目任务子资源 `/projects/{projectId}/tasks` 已接入真实 `TaskRepository`，不再是 stub |
| `DepartmentConversationController.java`                              | 部门对话 REST API         | 对话列表、详情、创建、更新、归档、恢复、软删除。实现 P2-1 部门对话 REST API |
| `ConversationController.java`                                       | 通用对话 REST API          | 对话列表、详情、消息历史、对话管理                                                                                   |
| `InterventionController.java`                                       | 人工干预接口                | 干预操作、风险评估、影响分析                                                                                     |
| `AgentFileController.java`                                          | 文件浏览/产物接口             | 文件浏览组件                                                                                             |
| `WindowsAutomationController.java`                                   | Windows 自动化节点管理        | 节点注册、心跳、启用/禁用、删除；供 server.py 和前端调用                                                                                |
| `ApprovalController.java`                                           | 审批接口                  | 审批页；findByExecutionId 调用已改为 .stream().findFirst()                                                                                                |
| `PerformanceController.java`                                        | 绩效接口                  | 绩效看板；已移除 instanceof 耦合，直接调用接口方法                                                                                               |
| `CreditController.java`                                             | 积分/收益接口               | 激励/积分页                                                                                             |
| `AutonomousController.java`                                         | 经济自治接口               | 赏金猎取/收款管理/账本/进化追踪/总览；对接 `core/autonomous` 模块                                                              |
| `ModelPoolController.java`                                          | 模型池 Provider/Model 管理 | 模型池页面                                                                                              |
| `BrainModelResolver.java`                                           | 大脑模型解析器               | 按 brainId 解析模型分配，已集成 `ModelHealthRegistry` 支持熔断过滤                                                  |
| `ModelHealthRegistry.java`                                          | 模型健康注册表               | 记录模型成功/失败/超时/熔断状态，支持 AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN 五种状态，冷却到期自动恢复                 |
| `ModelPoolManager.java`                                             | 模型池管理器                | 模型和 Provider 统一管理                                                                                  |
| `LlmProviderRegistry.java`                                          | LLM Provider 注册清单（原 `ProviderRegistry`）         | Provider 注册清单查询                                                                                    |
| `BrainModelAssigner.java`                                           | 大脑模型分配器               | 分配/清除模型到大脑                                                                                         |
| `BrainModelChangeHistory.java`                                      | 大脑模型变更历史实体            | 记录模型分配变更历史                                                                                         |
| `BrainModelChangeHistoryRepository.java`                            | 变更历史 Repository       | 查询模型变更历史                                                                                           |
| `BrainModelResolver.java`                                           | 大脑模型解析器               | 解析大脑模型分配，支持熔断过滤                                                                                    |
| `BuiltinModelCatalog.java`                                          | 内置模型目录                | 预置模型列表                                                                                             |
| `LlmClient.java`                                                    | LLM 客户端接口             | 统一 LLM 调用                                                                                          |
| `LlmClientFactory.java`                                             | LLM 客户端工厂             | 按 Provider 类型创建客户端                                                                                 |
| `ResolvedBrainModel.java`                                           | 已解析大脑模型               | 解析后的模型配置                                                                                           |
| `ProviderTestResult.java`                                           | Provider 测试结果         | 连接测试记录                                                                                             |
| `client/AnthropicClient.java`                                       | Anthropic LLM 客户端     | Claude 系列模型调用                                                                                      |
| `client/OpenAiCompatibleClient.java`                                | OpenAI 兼容客户端          | 兼容 OpenAI API 的模型调用                                                                                |
| `BrainModelConfigController.java`                                   | 大脑模型分配配置              | 大脑模型配置页                                                                                            |
| `MonitoringController.java`                                         | 监控接口                  | 监控页                                                                                                |
| `ErrorReportController.java`                                        | 前端错误上报接收端点            | `/api/error-reports` 接收前端批量错误上报并记录日志                                                               |
| `VitalSignsController.java`                                         | P32-A: 生命体征仪表盘           | GET `/api/vitals` 当前快照 + GET `/api/vitals/history` 历史趋势                                                      |
| `SatisfactionController.java`                                       | P29-A: 满意度采集             | POST `/api/satisfaction` + GET `/api/satisfaction/{brainDomain}`                                                |
| `NeuronController.java`                                             | 神经元接口                 | 神经元调试/状态                                                                                           |
| `SkillsController.java`                                             | 技能接口                  | 技能管理                                                                                               |
| `VoicePrintController.java`                                         | 声纹接口                  | 声纹认证/语音身份                                                                                          |
| `AgentFileController.java`                                          | 文件浏览/产物接口             | 文件浏览组件                                                                                             |
| `OfficeController.java`                                             | 虚拟办公室/在线状态            | 部门办公室 UI                                                                                           |
| `OrgController.java`                                                | 组织结构接口                | 组织管理                                                                                               |
| `ReceptionController.java`                                          | 接待/入口能力               | 前台/接待场景                                                                                            |
| `PlazaController.java`                                              | 广场/公开任务               | Plaza 页面                                                                                           |
| `ProactiveController.java` / `ProactiveOrchestratorController.java` | 主动预判/主动任务             | 主动服务页面                                                                                             |
| `RecoveryController.java`                                           | 恢复相关接口                | 运维恢复                                                                                               |
| `ExecutionStatusController.java`                                    | 执行状态查询接口              | 执行状态监控                                                                                             |
| `ArtifactController.java`                                           | 任务产物专用 REST API       | 产物列表/详情/下载/预览/统计/重新索引                                                                              |
| `MiscController.java`                                               | 零散接口                  | 应尽量减少新增，避免垃圾桶化                                                                                     |
| `common/ApiResponse.java`                                           | 统一 API 响应结构（`gateway/controller/common/`）；提供 `ok()/err()` 主方法和 `success()/error()` 别名方法；推荐统一使用 `ok()/err()`           | 新接口优先使用统一响应                                                                                        |
| `ClaudeProxyController.java`                                        | Claude CLI 代理控制器      | 暴露 Anthropic API 端点，代理 Claude CLI 请求到模型池                                                           |

### 5.4a 新增 API 端点汇总

| 端点                                               | 对应 Controller           | 功能说明                   |
| ------------------------------------------------- | ---------------------- | ---------------------- |
| `GET /api/auth/check`                             | `AuthController`        | 权限检查，返回当前用户访问级别和可访问大脑 |
| `GET /api/model-pool/visible`                     | `ModelPoolController`   | 查询当前用户可见的模型列表（按权限过滤）  |
| `POST /api/approval/{instanceId}/callback/approved` | `ApprovalController`  | 审批通过回调，触发后续执行流程       |
| `POST /api/approval/{instanceId}/callback/rejected` | `ApprovalController`  | 审批拒绝回调，记录拒绝原因并通知申请人   |

### 5.5 Claude 代理（Claude Proxy）

Claude CLI 代理模块允许 Claude CLI 工具通过模型池中的本地模型运行，无需外部 Anthropic API Key。

```text
living-agent-gateway/src/main/java/com/livingagent/gateway/
├── controller/ClaudeProxyController.java     # 代理控制器
└── dto/anthropic/                            # Anthropic API DTO
    ├── AnthropicMessagesRequest.java         # /v1/messages 请求 DTO
    ├── AnthropicMessagesResponse.java        # /v1/messages 响应 DTO
    ├── AnthropicTokenCountRequest.java       # 计 token 请求 DTO
    ├── AnthropicTokenCountResponse.java      # 计 token 响应 DTO
    └── AnthropicTool.java                    # 工具定义 DTO
```

| 文件                           | 功能说明                                       | 修改建议            |
| ---------------------------- | ------------------------------------------ | --------------- |
| `ClaudeProxyController.java` | 代理 Anthropic API 请求，路由到 ClaudeProxyService | 新增端点或修改路由规则时改这里 |

代理端点：

- `POST /api/v1/proxy/anthropic/v1/messages` — 消息生成（SSE 流式）
- `POST /api/v1/proxy/anthropic/v1/messages/count_tokens` — Token 计数
- `GET /api/v1/proxy/anthropic/v1/models` — 模型列表
- `GET /api/v1/proxy/anthropic/health` — 健康检查

避免重复建议：

- Claude CLI 代理复用模型池 `BrainModelSelectorManager` 进行路由，不要新增平行的路由逻辑
- 新增 REST API 前先查是否已有对应 Controller。
- 部门聊天优先改 `DepartmentChatService` + `DepartmentWebSocketHandler`，不要新增平行聊天服务。
- 通用 Agent/语音能力改 `AgentService`，不要和部门文本链路混用。
- 新接口统一返回 `ApiResponse` 或现有 DTO 风格。

### 5.6 其他子包文件

#### audio/ 音频处理辅助

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `audio/AudioBuffer.java` | 音频缓冲区 | 音频数据缓存、流式音频缓冲 |
| `audio/AudioConfig.java` | 音频配置 | 采样率、编码格式、缓冲区大小等配置 |
| `audio/AudioUtils.java` | 音频工具类 | 音频格式转换、音量计算、静音检测 |

#### executor/ 工具执行

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `executor/ToolExecutor.java` | 工具执行器接口 | 工具执行抽象，定义执行契约 |
| `executor/ToolExecutorService.java` | 工具执行服务 | 工具调用编排、超时控制、结果收集 |

#### parallel/ 并行模型调用

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `parallel/ParallelModelService.java` | 并行模型服务 | 多模型并行调用、结果聚合、超时控制 |

#### prompt/ 提示词构建

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `prompt/PromptBuilder.java` | Prompt 构建器 | 动态构建系统提示词、上下文注入 |
| `prompt/PromptConfig.java` | Prompt 配置 | 提示词模板、变量占位符配置 |
| `prompt/RoleConfig.java` | 角色配置 | 神经元/大脑角色定义、角色行为配置 |

#### security/ 网关权限辅助

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `security/RequireAccess.java` | 统一权限注解 | 标注在 Controller 方法上，声明所需访问级别；替代零散的 `accessGateService.canRoute()` 调用 |
| `security/RequireAccessAspect.java` | 权限 AOP 切面 | 拦截 `@RequireAccess` 注解方法，执行权限校验；校验失败抛出 403 |
| `security/WorkItemPermissionService.java` | 工作项权限服务 | 统一判断项目/任务访问权限，接受 `AuthContext` 参数 |

#### event/ 事件定义

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `event/ToolResultEvent.java` | 工具执行结果事件 | 工具执行完成后的异步事件通知 |

#### exception/ 全局异常处理

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `exception/GlobalExceptionHandler.java` | 全局异常处理器 | 统一异常捕获、错误响应格式化 |

#### interceptor/ 拦截器

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `interceptor/DepartmentPermissionInterceptor.java` | 部门权限拦截器 | 部门 API 访问权限校验 |
| `interceptor/SystemInitInterceptor.java` | 系统初始化拦截器 | 系统首次启动检测、初始化引导 |

#### proactive/ 网关层主动适配

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `proactive/ConsoleAlertNotifier.java` | 控制台告警通知器 | 主动提醒的控制台输出适配 |

#### dto/ 遗漏 DTO

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `dto/AssignModelRequest.java` | 模型分配请求 DTO | 大脑模型分配请求参数 |
| `dto/BrainModelRequest.java` | 大脑模型请求 DTO | 大脑模型配置请求参数 |
| `dto/BrainModelResponse.java` | 大脑模型响应 DTO | 大脑模型配置响应数据 |
| `dto/ModelRequest.java` | 模型通用请求 DTO | 模型调用请求参数 |
| `dto/ProviderRequest.java` | Provider 请求 DTO | Provider 注册/更新请求参数 |
| `dto/ProviderTestRequest.java` | Provider 测试请求 DTO | Provider 连接测试请求参数 |

***

## 5a. `living-agent-perception` 感知模块

感知模块负责语音识别（ASR）、语音合成（TTS）、多模态感知和文本处理。

```text
living-agent-perception/src/main/java/com/livingagent/perception/
├── config/
│   └── PerceptionConfig.java     # 感知模块配置
├── ear/
│   └── EarNeuron.java            # 语音识别神经元
├── mouth/
│   └── MouthNeuron.java          # 语音合成神经元
├── sensor/
│   └── PerceptionSensorNeuron.java  # 传感器神经元（原 SensorNeuron，已重命名避免与 neuron/impl/SensorNeuron 混淆）
└── text/
    └── TextNeuron.java           # 文本处理神经元
```

| 文件 | 功能 | 说明 |
| --- | --- | --- |
| `PerceptionConfig.java` | 感知模块配置 | ASR/TTS/Sensor 神经元配置 |
| `EarNeuron.java` | 语音识别神经元 | 调用 Sherpa-NCNN 进行 ASR |
| `MouthNeuron.java` | 语音合成神经元 | 调用 MeloTTS 进行 TTS |
| `PerceptionSensorNeuron.java` | 传感器神经元（原 `SensorNeuron`，已重命名避免与 neuron/impl/SensorNeuron 混淆） | 多模态感知接入 |
| `TextNeuron.java` | 文本处理神经元 | 文本预处理和后处理 |

***

## 6. `living-agent-core` 核心模块

核心模块是项目业务能力的主体。按包职责整理如下。

```text
com.livingagent.core/
├── brain/              # 大脑接口、部门大脑、主脑、上下文、提示词、协作、压缩
├── autonomy/           # 对话自治编排、意图分析、自治 Trace
├── neuron/             # 神经元接口、注册、执行、协调、视觉/路由/聊天神经元
├── channel/            # 通道通信，广播/单播/轮询/优先级
├── employee/           # 统一员工模型、人类员工、数字员工、生命周期、注册表、薪酬
├── worker/             # 数字工人抽象、工厂、生命周期、协作
├── model/              # 模型请求、响应、会话、模型管理、模型池、模型选择
├── provider/           # LLM/ASR/TTS Provider 抽象和实现
├── tool/               # 工具接口、注册、执行、企业工具、浏览器/爬虫/Office/外部平台
├── skill/              # 技能接口和技能上下文
├── knowledge/          # 知识库、分层知识、知识持久化、专业知识种子
├── memory/             # 记忆接口、SQLite/Memos/MemPalace 后端
├── evolution/          # 自主进化、信号、决策、执行、熔断、反馈、知识进化
├── autonomous/         # 赚钱驱动、赏金、激励、支付、自主运营
├── approval/           # 审批流、审批实例、计划审批
├── project/            # 项目管理
├── workflow/           # 工作流编排、阶段 Handler、监控
├── planner/            # 任务规划、DAG 任务
├── sandbox/            # 沙箱执行、Docker/Hybrid/Claude/Trae 执行网关
├── proxy/              # Claude CLI 代理，Anthropic API 到模型池的协议转换
├── security/           # 权限、认证、员工身份、声纹、会话、安全策略、命令安全
├── database/           # JPA 实体、Repository、数据库配置、向量服务、租户服务
├── distributed/        # Redis、Kafka、分布式缓存和消息
├── operation/          # Dashboard、绩效、指标
├── proactive/          # 主动预判、定时、事件、提醒、场景处理
├── diagnosis/          # 健康检查、健康问题、健康状态
├── compliance/         # 合规规则、合规报告
├── intervention/       # 人工干预、风险评估、影响分析
├── budget/             # 预算服务、预算实体
├── heartbeat/          # 心跳和定时唤醒
├── nativelib/          # Java native 声明和 JNI 包装
├── embedding/          # 嵌入服务和向量索引优化
├── deployment/         # 分布式部署服务
├── anomaly/            # 异常检测
├── scenario/           # 场景处理器
├── ops/                # 运行队列、任务结账等运营支撑
├── feedback/           # 反馈事件、反馈事件总线
├── service/            # 本地模型、ASR/TTS/声纹等服务接口
└── util/               # 工具类
```

### 6.1 `brain` 大脑包

| 文件/目录                                          | 功能说明                                                                                                            | 修改建议                                             |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `Brain.java`                                   | 大脑统一接口                                                                                                          | 新增大脑能力先看接口方法是否够用                                 |
| `BrainContext.java`                            | 大脑处理上下文，承载用户、部门、会话、权限、元数据                                                                                       | 新增上下文参数优先扩展这里，避免散落 Map                           |
| `BrainRegistry.java`                           | 大脑注册表接口                                                                                                         | 路由到部门大脑时走这里                                      |
| `impl/AbstractBrain.java`                      | 部门大脑公共基类，封装模型调用、工具/上下文/日志等共性；计数器已改 AtomicInteger/AtomicLong；会话历史已拆分到 BrainSessionManager（ConcurrentHashMap 手动 TTL 30分钟 + 最大 500 会话驱逐），ReAct 循环已拆分到 BrainReActEngine，模型降级已拆分到 BrainModelFallback；NP1-3: `publishResponse()` 检查 requires_response 元数据，自动回传部门响应到主脑；新增 getReActEngine() getter                                                                                    | 修改所有部门大脑共同逻辑时改这里                                 |
| `impl/BrainSessionManager.java`                | 大脑会话历史管理器（从 AbstractBrain 拆分）                                                                                   | 会话缓存策略、TTL、驱逐策略                                  |
| `impl/BrainReActEngine.java`                   | 大脑 ReAct 循环引擎（从 AbstractBrain 拆分）                                                                               | ReAct 循环逻辑、工具调用循环、executeCompileFixLoop()编译-修复自闭环 |
| `impl/BrainModelFallback.java`                 | 大脑模型降级管理器（从 AbstractBrain 拆分）                                                                                   | 模型降级策略、备选 Provider 选择                            |
| `impl/BrainRegistryImpl.java`                  | 大脑注册实现，维护 MainBrain、TechBrain、HrBrain 等映射                                                                       | 新增部门大脑或改路由映射时改这里                                 |
| `impl/MainBrain.java`                          | 主脑/总控大脑，负责战略、跨部门、复杂协调；`forwardToDepartment()` 已实现通过 ChannelManager 实际转发消息到目标部门大脑输入通道；NP1-3: `handleDepartmentResponse()` 收集部门响应、`aggregateDepartmentResponses()` 汇总结果、`scheduleSessionTimeout()` 超时检查                                                                                           | 跨部门协调、企业级判断入口                                    |
| `impl/TechBrain.java`                          | 技术部大脑                                                                                                           | 技术任务、代码、开发流程、shouldUseCompileFixLoop()判断BUG_FIX/TEST_GENERATE/CODE_CHANGE任务是否走编译-修复闭环(ClaudeCliTool不可用时自动启用) |
| `impl/HrBrain.java`                            | 人力资源大脑                                                                                                          | 招聘、绩效、组织、人事流程                                    |
| `impl/FinanceBrain.java`                       | 财务大脑                                                                                                            | 报销、预算、发票、成本                                      |
| `impl/SalesBrain.java`                         | 销售大脑                                                                                                            | 销售、客户、市场线索                                       |
| `impl/CsBrain.java`                            | 客服大脑                                                                                                            | 客诉、工单、FAQ                                        |
| `impl/AdminBrain.java`                         | 行政大脑                                                                                                            | 行政文档、办公事务                                        |
| `impl/LegalBrain.java`                         | 法务大脑                                                                                                            | 合同、合规、风险                                         |
| `impl/OpsBrain.java`                           | 运营大脑                                                                                                            | 数据运营、流程运营                                        |
| `prompt/DynamicPromptBuilder.java`             | 动态 Prompt 构造                                                                                                    | 部门职责、员工职责、知识上下文拼 Prompt                          |
| `prompt/InstructionFileLoader.java`            | 加载外部指令/制度文件                                                                                                     | 文档驱动 Prompt 时改这里                                 |
| `prompt/StandardLoadingChainService.java`      | 规范强制加载链：职责卡→Prompt→runbook→文档工作流→自定义指令                                                                          | 确保员工/大脑执行前带着规范时改这里                               |
| `BrainOutputContract.java`                     | 统一大脑输出契约：status/summary/plan/clarificationQuestions/blockingIssues/riskLevel/conversationId/taskKey/executionId | 前端/Trace/回执消费大脑输出时参考这里                           |
| `BrainBoundaryEnforcer.java`                   | 大脑职责边界硬判断：allowedActions/forbiddenActions/escalationTriggers/mustEscalateScenarios                              | 新增大脑或修改边界时改这里                                    |
| `collaboration/LeadOrchestrator.java`          | 协作编排接口                                                                                                          | 部门大脑给数字员工分工可复用                                   |
| `collaboration/impl/TechLeadOrchestrator.java` | 技术负责人式任务分工实现                                                                                                    | 技术任务分工、代码任务拆解                                    |
| `collaboration/TeammateRole.java`              | 团队成员角色定义                                                                                                        | 协作分工中的角色类型枚举                                     |
| `collaboration/TeammateAssignment.java`        | 团队成员任务分配结构                                                                                                      | 记录成员在协作中的任务分工                                    |
| `compact/ContextCompactor.java`                | 上下文压缩接口                                                                                                         | 长对话压缩、上下文裁剪                                      |
| `compact/CompactionResult.java`                | 上下文压缩结果结构                                                                                                       | 压缩后的摘要、保留 token 数                                |
| `compact/impl/RuleBasedContextCompactor.java`  | 规则式上下文压缩                                                                                                        | token 控制、摘要策略；作为 `HybridContextCompactor` 的程序提取层 |
| `compact/impl/HybridContextCompactor.java`     | 混合上下文压缩                                                                                                         | 程序提取 + LLM 语义压缩；规则层先做结构化裁剪，LLM 层再做语义摘要           |
| `impl/TechClaudeCliPromptTemplates.java`       | Tech Claude CLI 提示词模板                                                                                          | 代码审查/开发专用提示词                                       |

避免重复建议：

- 不要为每个部门再单独写一套模型调用逻辑，公共逻辑放 `AbstractBrain`。
- 部门路由统一走 `BrainRegistry` / `BrainRegistryImpl`。
- “部门大脑让员工分工”优先复用 `brain/collaboration`，不要散落在 Controller。

### 6.2 `autonomy` 自治编排包

**闭环归属**: 闭环35（动态员工创建闭环）+ 闭环37（员工智能调度闭环）

| 文件                 | 功能说明                    | 修改建议                     |

| `ConversationOrchestrator.java`                         | 对话自治总入口，负责把用户输入转为入口分类、需求就绪评估、主脑规划、部门路由、部门计划和 Trace；OrchestrationResult 新增 needsClarification/clarificationMessage 字段；P2-3: resumeAfterClarificationAsync 异步链式调用；P2-5: InterventionNeuron 统一降级策略                                                                                           | 部门对话要形成“生命力”时优先改这里                                                                                                                                            |
| `DialogueAnalyzer.java`                                 | 对话意图分析接口                                                                                                                              | 判断闲聊/任务/跨部门/审批等                                                                                                                                               |
| `impl/RuleBasedDialogueAnalyzer.java`                   | 规则式意图分析实现                                                                                                                             | 先用关键词/规则兜底，现作为 `LlmBasedDialogueAnalyzer` 的降级兜底                                                                                                               |
| `impl/LlmBasedDialogueAnalyzer.java`                    | LLM 驱动的入口消息分析器                                                                                                                        | 通过 `MainBrain.callLlm()` 获得结构化 JSON 分析结果；LLM 不可用时自动降级到 `RuleBasedDialogueAnalyzer`                                                                            |
| `IntakeClassification.java`                             | 入口分类结果                                                                                                                                | 区分是否需要主脑规划、是否可能跨部门                                                                                                                                            |
| `MainBrainTaskDirector.java`                            | 主脑任务规划接口                                                                                                                              | 后续接真实 MainBrain 模型规划时改这里的实现                                                                                                                                   |
| `impl/RuleBasedMainBrainTaskDirector.java`              | 规则版主脑规划器                                                                                                                              | 第一阶段兜底实现，输出 `MainBrainTaskPlan`；现作为 `LlmBasedMainBrainTaskDirector` 的降级兜底                                                                                     |
| `impl/LlmBasedMainBrainTaskDirector.java`               | LLM 驱动的主脑规划器                                                                                                                          | 通过 `MainBrain.callLlm()` 调用真实 LLM 获得结构化 JSON 任务计划；LLM 不可用时自动降级到 `RuleBasedMainBrainTaskDirector`                                                              |
| `MainBrainTaskPlan.java`                                | 主脑结构化任务计划                                                                                                                             | 任务类型、目标、主责部门、交付物、验收标准、部门计划、executionCapability/artifactType/executionMode 归一字段；`isRequirementFrozen()` 需求冻结判定；`withRequirementStatus()` 带状态转换校验的不可变更新；`withIncrementedVersion()` 递增需求版本 |
| `DepartmentTaskPlan.java`                               | 单部门任务计划                                                                                                                               | 部门目标、建议角色、建议员工、交付物和验收标准、executionCapability/artifactType/executionMode 归一字段                                                                                     |
| `BrainRoutingDecision.java`                             | 大脑/部门路由决策                                                                                                                             | 记录主责部门、支持部门、是否重路由                                                                                                                                             |
| `FixedEmployeeDispatcher.java`                          | 固定员工分派接口                                                                                                                              | 将部门计划转为固定员工任务单                                                                                                                                                |
| `EmployeeWorkAssignment.java`                           | 固定员工任务单                                                                                                                               | 员工编码、神经元、角色、指令、产物和工具上下文、worktreePath、diffPath（代码任务专用字段）                                                                                                  |
| `impl/RegistryBackedFixedEmployeeDispatcher.java`       | 基于 `FixedEmployeeRegistry` 的规则式员工分派实现                                                                                                 | 作为 `LlmBasedFixedEmployeeDispatcher` 的降级兜底                                                                                                                    |
| `impl/LlmBasedFixedEmployeeDispatcher.java`             | **【闭环37-A】** LLM 驱动的智能员工分派实现                                                                                                                       | 通过 `MainBrain.callLlm()` 根据员工能力、负载、历史绩效动态选人；LLM 不可用时自动降级到 `RegistryBackedFixedEmployeeDispatcher`；**包含Fallback降级机制**                                                             |
| `PreparedAssignmentBatch.java`                          | 部门级任务单准备批次                                                                                                                            | 聚合一次部门任务的员工任务单、目标、批次 ID 和准备状态                                                                                                                                 |
| `AssignmentPreparationService.java`                     | 任务单准备服务接口                                                                                                                             | 将主脑计划、部门计划和员工任务单整理为可交给部门大脑/后续执行器的准备批次                                                                                                                         |
| `impl/DefaultAssignmentPreparationService.java`         | 默认任务单准备实现                                                                                                                             | P0-5 新增 ExecutionCapabilityResolver 集成，将 executionCapability/artifactType/executionMode 写入每个 assignment 的 context                                                                          |
| `AssignmentReadinessEvaluator.java`                     | 任务分派准备度评估接口                                                                                                                           | 评估 READY/BLOCKED/NEEDS\_CLARIFICATION/PARTIALLY\_READY                                                                                                        |
| `impl/LlmAssignmentReadinessEvaluator.java`             | LLM 任务分派准备度评估                                                                                                                         | LLM 评估任务目标清晰度、员工合适度、交付物明确度                                                                                                                                    |
| `DepartmentExecutionCoordinator.java`                   | 部门执行协调接口                                                                                                                              | 将准备批次推进到员工执行通道                                                                                                                                                |
| `DepartmentExecutionResult.java`                        | 部门执行派发结果                                                                                                                              | 记录 executionId、batchId、派发状态和员工派发列表                                                                                                                            |
| `CrossDepartmentCoordinator.java`                       | 跨部门任务协调器接口                                                                                                                              | 聚合多部门执行结果，判断跨部门任务整体完成状态；`needsCrossDepartmentCoordination()` 判断是否需要跨部门协调；`coordinate()` 接收 requestId、MainBrainTaskPlan、Map<部门, 执行结果>；P2 重构为接口 |
| `impl/DefaultCrossDepartmentCoordinator.java`           | 默认跨部门协调器实现                                                                                                                             | 从原 CrossDepartmentCoordinator 提取的逻辑；收集各部门结果、标记失败部门、聚合整体状态                                                                                              |
| `EmployeeExecutionDispatch.java`                        | 单个员工执行派发记录                                                                                                                            | 记录 dispatchId、assignmentId、目标 channel、派发状态                                                                                                                    |
| `impl/ChannelBackedDepartmentExecutionCoordinator.java` | 基于 `ChannelManager` 的第一版执行协调器                                                                                                         | 发布到 `channel://employee/{neuron-or-code}/tasks`，并写入回执 channel 元数据                                                                                             |
| `EmployeeExecutionReceipt.java`                         | 员工执行回执结构                                                                                                                              | 记录执行完成/失败状态、摘要和回执元数据、worktreePath、diffPath（代码任务专用字段）；**64-E-1: 扩展 ActionStep/ToolCallRecord/ValidationRecord/KnowledgeCaptureCandidate**                                        |
| `EmployeeExecutionReceiptService.java`                  | 员工执行回执服务接口                                                                                                                            | 注册执行、记录回执、查询回执和判断执行是否完成；内置 `ReceiptListener` 支持 receipt 到达后通知 WebSocket 推送进度；**64-G-2: 新增 getReceiptsByEmployee(employeeCode, limit) 默认方法**                                                                                  |
| `impl/InMemoryEmployeeExecutionReceiptService.java`     | 内存版员工执行回执服务                                                                                                                           | 第一版闭环缓存，保留作为测试/回退使用；生产使用 `FileBasedEmployeeExecutionReceiptService`                                                                                           |
| `impl/FileBasedEmployeeExecutionReceiptService.java`    | JSON 文件持久化回执服务                                                                                                                        | 回执写入 `data/receipts/` 目录，启动时自动加载历史数据，支持 getStats/clearAll                                                                                                     |
| `DialogueDecision.java`                                 | 意图分析和路由决策结果                                                                                                                           | 新增决策字段时改这里                                                                                                                                                    |
| `AutonomyTraceService.java`                             | 自治流程追踪日志服务，recordEvent() 已添加 @Transactional                                                                                          | 日志要看到分析、路由、分工、执行阶段时改这里                                                                                                                                        |
| `AutonomyTraceEvent.java`                               | 自治 Trace 事件数据结构                                                                                                                       | Trace 字段扩展                                                                                                                                                    |
| `ArtifactRecord.java`                                   | 任务产物记录结构                                                                                                                              | 记录执行ID、部门、员工、产物类型/路径/名称/大小/sha256，新增 `taskId`/`projectId` 关联字段                                                                                                |
| `ArtifactRecordService.java`                            | 产物记录服务接口                                                                                                                              | 按执行ID/部门/员工/类型查询产物，支持分页和目录扫描索引；新增 `getByTaskId`/`getByProjectId`/`associateTaskAndProject`                                                                    |
| `impl/InMemoryArtifactRecordService.java`               | 内存版产物记录服务                                                                                                                             | 第一版闭环缓存，保留作为测试/回退使用；生产使用 `JpaArtifactRecordService`                                                                                                           |
| `impl/JpaArtifactRecordService.java`                    | JPA 数据库产物记录服务，recordArtifact()/associateTaskAndProject()/scanAndIndexDirectory() 已添加 @Transactional                              | 生产持久化实现，支持 artifact\_records 表的 CRUD、分页查询、统计、目录扫描索引                                                                                                           |
| `MainBrainFinalSummaryService.java`                     | 主脑最终总结服务接口                                                                                                                            | 执行类任务最终回复由主脑基于完整上下文进行组织级收口，定义 `FinalSummaryResult` 结构                                                                                                         |
| `impl/LlmMainBrainFinalSummaryService.java`             | LLM 驱动的主脑最终总结                                                                                                                         | 调用 MainBrain LLM 生成结构化总结，LLM 不可用时自动降级到 `DefaultMainBrainFinalSummaryService`                                                                                  |
| `impl/DefaultMainBrainFinalSummaryService.java`         | 默认主脑最终总结实现                                                                                                                            | LLM 不可用时使用模板方式生成总结，支持状态判定、产物列表、风险和建议                                                                                                                          |
| `ExecutionCapability.java`                              | 执行能力枚举（18 种），将 LLM 开放意图归一到有限执行能力                                                                                                       | 新增 FILE_SYSTEM_QUERY、PROJECT_MANAGEMENT（项目管理/Issue 追踪/进度同步）、ISSUE_TRACKING（Bug 管理/任务状态流转/工单处理）；任务意图可以开放，执行能力必须收敛；执行器只消费 ExecutionCapability                                                                                                                    |
| `ArtifactType.java`                                     | 产物类型枚举（16 种），执行器按此生成、前端按此展示                                                                                                            | 新增 TOOL_RESULT（工具调用结果）；新增产物类型时扩展此枚举                                                                                                                                                  |
| `ExecutionMode.java`                                    | 执行模式枚举（7 种），决定沙箱/工具/人工审核等执行方式                                                                                                         | 新增 TOOL_EXECUTION（调用工具直接返回结果，不生成 artifact）；新增执行模式时扩展此枚举                                                                                                                  |
| `ExecutionCapabilityRequest.java`                       | 执行能力解析请求 record                                                                                                                     | 输入：用户消息 + 任务类型 + 意图 + 交付物 + 技能需求 + 部门 + 建议员工                                                                                                                 |
| `ExecutionCapabilityResolution.java`                    | 执行能力解析结果 record                                                                                                                     | 输出：executionCapability + artifactType + executionMode + confidence + reason；提供 resolved/needsClarification/needsHumanReview 工厂方法                          |
| `ExecutionCapabilityResolver.java`                      | 执行能力解析器接口                                                                                                                             | 将 LLM 开放输出归一到有限枚举；无法归一时进入澄清或人工介入                                                                                                                              |
| `impl/DefaultExecutionCapabilityResolver.java`          | 默认执行能力解析器实现                                                                                                                           | 规则兜底优先 → 枚举校验 → 置信度检查 → 无法归一则 NEEDS\_CLARIFICATION / HUMAN\_HANDOFF；包含中英文 taskType 映射和关键词评分                                                              |
| `impl/LlmBasedExecutionCapabilityResolver.java`         | LLM 驱动的执行能力解析器                                                                                                                       | 规则置信度不足时调用 LLM 语义判断；支持 LlmDecisionClient 和 MainBrain.callLlm 双通道；LLM 不可用时自动降级到 DefaultExecutionCapabilityResolver                                                      |
| `RequirementReadinessEvaluator.java`                    | 需求就绪评估器接口                                                                                                                             | 在主脑规划和员工分派之前判断需求是否明确；SUFFICIENT/PARTIALLY\_SUFFICIENT/INSUFFICIENT                                                                                          |
| `impl/DefaultRequirementReadinessEvaluator.java`        | 默认需求就绪评估器实现（规则版）                                                                                                                       | 7条确定性规则：空消息→INSUFFICIENT、过短→INSUFFICIENT、动作词→SUFFICIENT、疑问词→SUFFICIENT、请求词→SUFFICIENT、≥10字符→SUFFICIENT、短消息无关键词→PARTIALLY\_SUFFICIENT；不依赖LLM，作为降级方案 |
| `MainBrainRequirementClarifier.java`                    | 主脑需求澄清器接口                                                                                                                             | 需求不明确时由主脑统一生成澄清消息返回用户，不直接派给员工                                                                                                                                |
| `impl/DefaultMainBrainRequirementClarifier.java`        | 默认主脑需求澄清器实现                                                                                                                           | 基于规则生成结构化澄清消息，列出缺失信息和引导问题                                                                                                                                    |
| `EmployeeTaskExecutor.java`                             | 员工任务执行器接口                                                                                                                             | 定义 `ExecutionResult` 和 `ArtifactFile` 结构，支持按任务类型分发执行（web/document/analysis/review等）                                                                           |
| `impl/ToolBackedEmployeeTaskExecutor.java`              | 工具驱动的员工任务执行器                                                                                                                          | 新增 FILE\_SYSTEM\_QUERY → executeToolTask 工具调用分支，通过 ToolRegistry 查找并调用真实 Tool 实现（如 FileEditTool.list\_dir）；保留 ExecutionCapabilityResolver 集成和 normalizeTaskType 兼容兜底；新增 resolveToolInvocation 意图→工具解析、formatToolResult 结果格式化；**64-C-1: 集成 ActionReadinessChecker + ActionOutputValidator**                                                          |
| **`impl/ActionReadinessChecker.java`**                 | **【64-B-2 新增】** 行动准备度检查器                                                                                                                       | 执行前检查：工具健康(BackendRegistry)+前置条件+输入完整性，返回 READY/BLOCKED/DEGRADED                                                             |
| **`impl/ActionOutputValidator.java`**                  | **【64-D-1 新增】** 4层输出验证器                                                                                                                       | L1结构验证(html/markdown/json)+L2内容验证(非空/拒绝语/占位符)+L3交付物验证(对照assignment)+L4工具结果验证                             |
| **`impl/ActionDiscoveryServiceImpl.java`**            | **【64-A-2 新增】** 行动发现服务实现                                                                                                                    | 基于FixedEmployeeRegistry+ToolRegistry+BackendRegistry，动态生成员工能力清单，支持工具健康检查和最佳工具匹配                                                   |
| **`impl/ActionEffectivenessTracker.java`**            | **【64-F-1 新增】** 行动效果追踪器                                                                                                                    | 追踪工具成功率/验证通过率/行动平均耗时，提供效果上下文给LLM调度器，含员工级/工具级/验证级三级指标                                                              |
| **`ActionDiscoveryService.java`**                     | **【64-A-2 新增】** 行动发现服务接口                                                                                                                    | discoverCapabilities(员工能力清单)+checkToolHealth(健康检查)+findBestTool(最佳工具匹配)，含ToolHealthStatus/ToolMatch record   |
| `KnowledgeCaptureResult.java`                           | 知识沉淀结果                                                                                                                                | 记录沉淀是否成功、知识键、层级、领域                                                                                                                                            |
| `KnowledgeCaptureService.java`                          | 知识沉淀服务接口                                                                                                                              | 从执行结果中提取经验写入知识库                                                                                                                                               |
| `impl/DefaultKnowledgeCaptureService.java`              | 基于 KnowledgeManager 的知识沉淀实现                                                                                                           | 将执行经验以 EXPERIENCE 类型写入部门知识库                                                                                                                                   |
| `PerformanceCaptureResult.java`                         | 绩效记录结果                                                                                                                                | 记录员工编码、执行ID、贡献类型                                                                                                                                              |
| `PerformanceCaptureService.java`                        | 绩效记录服务接口                                                                                                                              | 从执行结果中记录员工贡献                                                                                                                                                  |
| `impl/DefaultPerformanceCaptureService.java`            | 基于 LedgerService 的绩效记录实现                                                                                                              | 完成任务后自动发放积分奖励                                                                                                                                                 |
| `KnowledgeQualityEvaluator.java`                        | NP2-1: 知识质量评估接口，assess/calculatePromotionReadiness                                                                                       | 评估知识条目质量和晋升就绪度                                                                                                                                             |
| `impl/DefaultKnowledgeQualityEvaluator.java`            | NP2-1: 默认知识质量评估实现，基于 confidence/accessCount/verified/relevanceScore 计算质量评分                                                         | 知识晋升前的质量评估                                                                                                                                                    |
| `PerformanceStatsService.java`                           | NP2-3: 员工绩效统计接口，getStats/getStatsBatch/getDepartmentRanking                                                                               | 聚合 LedgerService 数据生成结构化绩效指标                                                                                                                                   |
| `impl/DefaultPerformanceStatsService.java`               | NP2-3: 默认绩效统计实现，从 LedgerService 聚合经济数据                                                                                              | 员工分派时的绩效参考                                                                                                                                                    |
| `impl/DynamicEmployeeTaskConsumerRegistry.java`         | 基于 FixedEmployeeRegistry 的动态员工消费者注册                                                                                                   | 启动时自动注册所有真实员工消费者，替代模拟回执；已接入 `EmployeeTaskExecutor` 支持真实工具执行                                                                                                   |
| `LLMEmployeeCreationService.java`                       | **【闭环35】** LLM 驱动的动态员工创建接口（evaluateCreationNeed、createFromProposal）                                                                                                                       | 评估是否需要新员工、从提案创建员工；**人员不足时自动创建员工**                                                                                                                                             |
| `impl/LLMEmployeeCreationServiceImpl.java`              | **【闭环35-B】** LLM 动态员工创建实现                                                                                                                          | 通过 `MainBrain.callLlm()` 自主判断是否需要新员工，确保新员工有专属名字/编号/能力/职责；**新员工命名以"真"字开头（如"真测"），自动设计能力/技能/工具列表**                                                                                                      |
| `MainBrainResponseComposer.java`                        | 主脑响应编排器接口                                                                                                                             | 组合大脑响应+执行团队+执行状态为用户可见回复                                                                                                                                       |
| `impl/DefaultMainBrainResponseComposer.java`            | 默认主脑响应编排器实现                                                                                                                           | 作为 `LlmBasedMainBrainResponseComposer` 的降级兜底                                                                                                                  |
| `impl/LlmBasedMainBrainResponseComposer.java`           | LLM 驱动的主脑响应编排器                                                                                                                        | 通过 `MainBrain.callLlm()` 根据执行结果动态生成自然语言回复；LLM 不可用时降级到模板拼接                                                                                                     |
| `ExecutionResultAggregator.java`                        | 执行结果聚合器接口                                                                                                                             | 汇总所有员工 receipt 为执行摘要                                                                                                                                          |
| `impl/DefaultExecutionResultAggregator.java`            | 默认执行结果聚合器实现                                                                                                                           | 作为 `LlmBasedExecutionResultAggregator` 的降级兜底                                                                                                                  |
| `impl/LlmBasedExecutionResultAggregator.java`           | LLM 执行结果聚合器                                                                                                                           | LLM 汇总执行结果 + `ExecutionReceiptReviewer` 审核回执质量                                                                                                                |
| `ExecutionReceiptReviewer.java`                         | 执行回执审核接口                                                                                                                              | 审核回执是否满足验收标准                                                                                                                                                  |
| `impl/DefaultExecutionReceiptReviewer.java`             | 默认程序规则验收实现                                                                                                                            | 降级验收严格化：摘要为空→rejected、验收标准未满足→rejected、期望产物但无文件→rejected+needsRetry；检查 worktreePath/diffPath/metadata.artifactPaths                                                                 |
| `impl/LlmExecutionReceiptReviewer.java`                 | LLM 执行回执审核                                                                                                                            | LLM 对比回执与验收标准，输出质量分和未满足项                                                                                                                                      |
| `ExecutionReviewResult.java`                            | 执行评审结果结构                                                                                                                              | 记录评审状态 passed/needsRework/failed、问题列表、返工建议、是否建议二次派发                                                                                                           |
| `FinalResponseCoordinator.java`                         | 最终出口策略协调器接口                                                                                                                           | 决定回复策略：DIRECT\_ANSWER / ASK\_CLARIFICATION / MAIN\_BRAIN\_COMPOSE / WAIT\_FOR\_RECEIPTS / DEPARTMENT\_BRAIN\_DIRECT / ESCALATE\_TO\_HUMAN / REQUEST\_APPROVAL |
| `impl/DefaultFinalResponseCoordinator.java`             | 默认出口策略协调器实现                                                                                                                           | 作为 `LlmBasedFinalResponseCoordinator` 的降级兜底                                                                                                                   |
| `impl/LlmBasedFinalResponseCoordinator.java`            | LLM 驱动的出口策略协调器                                                                                                                        | 通过 `MainBrain.callLlm()` 根据对话上下文、执行状态、风险等级动态选择回复策略；LLM 不可用时降级到规则判断                                                                                            |
| `EmployeeTaskExecutionOutcome.java`                     | 员工任务执行结果结构                                                                                                                            | 记录执行状态（COMPLETED/DEGRADED/FAILED/NEEDS\_RETRY）、模型信息、产物、置信度、是否需要重试                                                                                             |
| `EmployeeOutputContract.java`                           | 统一员工输出契约：employeeCode/status/summary/completedItems/failedItems/artifacts/blockingIssues/riskLevel/retryable/failedReason/failedStage | 前端/Trace/回执消费员工输出时参考这里                                                                                                                                        |
| `ToolCallRecord.java`                                   | 工具调用记录                                                                                                                                | 记录工具名称、参数、结果、成功状态、耗时                                                                                                                                          |
| `CodeReviewWorkflowService.java`                        | 代码审查工作流接口                                                                                                                            | 定义13阶段审查状态机（PLAN\_CREATED→ESCALATED）、requestChanges/approve/escalate操作、4种代码产物注册（worktree/diff/review\_report/final\_summary）                                      |
| `impl/InMemoryCodeReviewWorkflowService.java`           | 内存版审查工作流实现                                                                                                                           | 审查状态CRUD、阶段推进、变更请求、审批、升级；依赖 `ArtifactRecordService` 注册产物（已弃用，生产使用 JpaCodeReviewWorkflowService）                                                          |
| `impl/JpaCodeReviewWorkflowService.java`               | JPA 持久化审查工作流实现，全部写方法已添加 @Transactional                                                                                                 | 审查状态持久化到 PostgreSQL，重启不丢失；含 canTransition 校验 + MAX\_REVIEW\_ROUNDS 限制                                                                                         |
| `impl/CodeArtifactMetadataBinder.java`                  | 代码产物元数据绑定器                                                                                                                          | 注册worktree/diff/review\_report/final\_summary四种代码产物，合并元数据并关联 `CodeReviewWorkflowService`                                                                      |
| `RequirementStatus.java`                                | 需求状态枚举与状态机                                                                                                                          | DRAFT→COMPLETED/FAILED 9阶段流转，canTransition/allowsAssignment/allowsExecution/needsClarification 状态判定方法                                                              |
| `TaskMetadataKeys.java`                                  | 元数据键常量                                                                                                                              | 统一 taskId/worktreePath/diffPath 等 Map key 常量                                                                                                                           |
| `impl/MinimalEmployeeTaskExecutor.java`                  | 最小任务执行器                                                                                                                             | 简单的任务执行实现，已被 `DynamicEmployeeTaskConsumerRegistry` 替代，类文件保留用作参考                                                                                                        |
| `ReceiptStatus.java`                                      | 回执状态枚举                                                                                                                              | PENDING/COMPLETED/FAILED/TIMEOUT/RETRYING，回执生命周期状态；**64-D-2: 新增 NEEDS_REWORK（输出验证失败时使用）**                                                                                                  |
| `impl/JpaEmployeeExecutionReceiptService.java`            | JPA 持久化回执服务                                                                                                                         | 生产级回执持久化实现，替代 `FileBasedEmployeeExecutionReceiptService`；基于 `EmployeeExecutionReceiptEntity` 存储，支持按 executionId/employeeCode/department 查询和统计                                  |
| `TaskRouteClassifier.java`                                | 轻量路由分类器接口（P0）                                                                                                                         | 在意图分析后、主脑规划前判断：单部门直达 / 跨部门主脑拆解 / 需要澄清                                                                                                               |
| `TaskRouteResult.java`                                    | 路由结果 record（P0）                                                                                                                      | SINGLE\_DEPARTMENT / CROSS\_DEPARTMENT / CLARIFICATION\_NEEDED                                                                                               |
| `impl/DefaultTaskRouteClassifier.java`                   | 默认路由分类实现（P0）                                                                                                                         | 7条路由规则：CROSS\_DEPARTMENT kind→主脑、非TASK/PROJECT→单部门、requiresClarification→澄清、有supportingDepts→主脑、部门一致且无协作→单部门、部门不一致→主脑、兜底→主脑                              |
| `DepartmentTodoItem.java`                                 | 部门待办项数据模型（P1）                                                                                                                        | 含 Status/Priority 枚举、AtomicInteger claimVersion 乐观锁、claim()/assign() 方法                                                                                   |
| `TodoClaimResult.java`                                    | 待办领取结果 record（P1）                                                                                                                   | ClaimFailureReason 枚举：SUCCESS/ALREADY\_CLAIMED/NOT\_QUALIFIED/NOT\_FOUND/NOT\_PENDING                                                                       |
| `DepartmentTodoPool.java`                                 | 部门待办池接口（P1）                                                                                                                         | publish/claim/assign/get/getPendingByDepartment/getAllByDepartment/getClaimedByEmployee                                                                      |
| `impl/InMemoryDepartmentTodoPool.java`                   | 内存版部门待办池实现（P1）                                                                                                                      | ConcurrentHashMap 实现，后续可替换 Redis                                                                                                                          |
| `impl/JpaDepartmentAggregationService.java`             | **【新增】** JPA 持久化版部门聚合服务                                                                                                            | 部门交付物持久化实现，替代内存版为生产默认实现                              |
| `impl/JpaInternalReviewService.java`                    | **【新增】** JPA 持久化版部门内审查服务                                                                                                            | 部门内审查持久化实现                              |
| `EmployeeSelfClaimService.java`                           | 员工自行领取服务接口（P1）                                                                                                                      | tryClaim/tryClaimBestMatch/assignUnclaimed/isQualified/getCurrentLoad/getMaxLoad                                                                             |
| `impl/DefaultEmployeeSelfClaimService.java`              | 默认员工自行领取实现（P1）                                                                                                                     | 校验资格（部门归属+职责匹配+工具白名单+负载检查）+ 乐观锁领取 + 兜底指派                                                                                                              |
| `EmployeeEquipmentService.java`                           | **【新增】** 员工设备服务接口                                                                                                                | 员工工具/技能/权限配置                              |
| `impl/DefaultEmployeeEquipmentService.java`              | **【新增】** 默认员工设备服务实现                                                                                                            | 员工设备配置和状态管理                              |
| `DepartmentDeliverable.java`                              | 部门交付物 record（P1）                                                                                                                    | 含 AggregationStatus 枚举和 DeliverableItem 内部 record                                                                                                        |
| `AggregationResult.java`                                  | 聚合结果 record（P1）                                                                                                                     | success/partial/qualityIssues 静态工厂方法                                                                                                                     |
| `DepartmentAggregationService.java`                       | 部门级聚合服务接口（P1）                                                                                                                      | aggregate/getDeliverable/getDeliverablesByDepartment/getDeliverablesByPlan                                                                                    |
| `impl/DefaultDepartmentAggregationService.java`          | 默认部门级聚合实现（P1）                                                                                                                     | 收集回执→检查审查状态→构建交付项→计算质量分→确定聚合状态                                                                                                                  |
| `impl/LlmDepartmentAggregationService.java`              | LLM 增强版部门级聚合实现（#7）                                                                                                              | 规则版聚合→LLM 语义分析→一致性检查→质量评估→问题发现→修复建议；LLM 失败时自动降级到规则版                                                                                              |

#### 6.2.1 `autonomy/context` 决策上下文包

| 文件                                        | 功能说明         | 修改建议                                                                   |
| ----------------------------------------- | ------------ | ---------------------------------------------------------------------- |
| `DecisionContext.java`                    | 统一决策上下文结构    | 聚合请求、用户、大脑、员工、工具、知识、项目、审批、约束上下文；提供 `toPromptContext()` 方法生成 LLM Prompt |
| `DecisionContextBuilder.java`             | 决策上下文构建器接口   | 定义 `build()` 和 `buildFull()` 方法，支持不同构建选项                               |
| `impl/DefaultDecisionContextBuilder.java` | 默认决策上下文构建器实现 | 从各注册表/服务聚合上下文，支持按部门/能力筛选员工、搜索知识、构建审批约束                                 |

#### 6.2.2 `autonomy/llm` LLM 决策客户端包

| 文件                                   | 功能说明           | 修改建议                                                                           |
| ------------------------------------ | -------------- | ------------------------------------------------------------------------------ |
| `LlmDecisionClient.java`             | 统一 LLM 决策客户端接口 | 定义 `decide()` 和 `decideWithRetry()` 方法，支持 JSON Schema 校验、修复重试、降级兜底             |
| `impl/DefaultLlmDecisionClient.java` | 默认 LLM 决策客户端实现 | 通过 `MainBrain.callLlm()` 调用 LLM，支持 Prompt 版本管理、JSON 提取、Schema 校验、修复重试、Trace 记录 |

#### 6.2.3 `autonomy/review` 部门内审查闭环包（P0）

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `ReviewState.java` | 审查状态枚举 | SUBMITTED\_FOR\_REVIEW/UNDER\_REVIEW/REVISION\_NEEDED/COMPLETED/REJECTED/ESCALATED |
| `ReviewDecision.java` | 审查决定枚举 | APPROVED/REVISION\_NEEDED/REJECTED/ESCALATE\_TO\_BRAIN |
| `ReviewResult.java` | 审查结果 record | reviewerCode/decision/qualityScore/issues/suggestions/completionTag/reviewRound |
| `ReviewHistory.java` | 审查历史 record | reviewId/todoItemId/authorCode/reviewerCode/reviewRound/state/result/revisionNotes |
| `InternalReviewService.java` | 部门内审查服务接口 | submitForReview/review/getReview/getReviewHistoryByTodoItem/getReviewState/isCompleted/getCurrentRound |
| `impl/DefaultInternalReviewService.java` | 默认审查服务实现 | 内存版，管理审查状态机、轮次计数、超轮次自动 escalate |

避免重复建议：

- 不要在 `DepartmentWebSocketHandler` 里直接写复杂意图判断。
- 部门聊天要先进入 `ConversationOrchestrator`，再由它调用 `BrainRegistry`、规划、分工。

### 6.3 `neuron` 神经元包

**闭环归属**: 闭环36（神经元会话协调闭环）

| 文件/目录                                                  | 功能说明                                | 修改建议                                                                       |
| ------------------------------------------------------ | ----------------------------------- | -------------------------------------------------------------------------- |
| `Neuron.java`                                          | 神经元接口                               | 所有神经元统一生命周期和消息处理                                                           |
| `NeuronState.java`                                     | 神经元状态枚举                             | 状态新增或状态机修改                                                                 |
| `NeuronContext.java`                                   | 神经元上下文                              | 神经元处理所需上下文                                                                 |
| `NeuronRegistry.java` / `impl/NeuronRegistryImpl.java` | 神经元注册表                              | 新增神经元注册/查询                                                                 |
| `NeuronExecutor.java`                                  | 神经元执行器                              | 并发执行、调度策略                                                                  |
| `impl/AbstractNeuron.java`                             | 神经元公共基类                             | 公共状态、订阅、发布逻辑                                                               |
| `impl/NeuronCoordinator.java`                          | **【闭环36】** 神经元会话协调器，创建 session、绑定通道、协调感知/派发/响应；**四通道架构**（感知通道BROADCAST/分发通道ROUND_ROBIN/工具意图通道UNICAST/响应通道BROADCAST） | 通用 Agent 链路核心，不要滥用于部门文本                                                                 |
| `impl/Qwen3Neuron.java`                                | 前台轻量聊天神经元                           | 闲聊、轻量对话、兜底，不等同部门大脑                                                         |
| `impl/BitNetNeuron.java`                               | BitNet 工具/低资源神经元                    | 低资源模型、工具判断                                                                 |
| `impl/RouterNeuron.java`                               | 路由神经元                               | 消息路由、意图转发                                                                  |
| `impl/EyeNeuronImpl.java`                              | 视觉神经元                               | 图像识别、视觉问答                                                                  |
| `impl/SensorNeuron.java`                               | 传感器神经元                              | 感知输入扩展                                                                     |
| `impl/ToolNeuron.java`                                 | **【新增】** 工具神经元                        | 工具检测和执行专用神经元，固定模型，负责工具意图识别和兜底处理                              |
| `impl/ProjectDevelopmentNeuron.java`                   | 项目开发神经元                             | 项目开发型任务执行                                                                  |
| `chat/ChatNeuronRouter.java`                           | 聊天神经元路由                             | 普通聊天路由                                                                     |
| `chat/ChatIntentClassifier.java`                       | 聊天意图分类                              | 支持 LLM-first / Rule-fallback：优先使用 `DialogueAnalyzer` 语义分类，LLM 不可用时降级到关键词规则 |
| `fallback/FallbackHandler.java`                        | 神经元失败兜底                             | 模型失败、工具失败兜底                                                                |
| `EyeNeuron.java`                                       | 视觉神经元                              | 图像/人脸/文档分析                                                                 |
| `ComparisonResult.java`                                | 对比结果                               | 代码/文本对比                                                                   |
| `DetectedObject.java`                                  | 检测对象                               | 图像中检测到的对象                                                                  |
| `DocumentAnalysisResult.java`                          | 文档分析结果                             | OCR/文档结构分析                                                                 |
| `FaceAnalysisResult.java`                              | 人脸分析结果                             | 人脸特征和匹配                                                                   |
| `ImageAnalysisResult.java`                             | 图像分析结果                             | 图像描述和分类                                                                   |
| `VisualQAResult.java`                                  | 视觉问答结果                             | 图像问答                                                                      |
| `chat/ChatNeuronConfig.java`                           | 闲聊神经元配置                            | Qwen3 模型配置                                                                |
| `evolution/EvolutionSignalTrigger.java`                | 进化信号触发器                            | 神经元层进化信号                                                                  |

避免重复建议：

- `Qwen3Neuron` 是轻量聊天神经元，不是部门大脑。
- 部门业务应走 `Brain`，不是直接走 `Qwen3Neuron`。
- 通道会话协调走 `NeuronCoordinator`，不要各处手写通道绑定。

### 6.4 `channel` 通道包

| 文件                                                         | 功能说明               | 修改建议                     |
| ---------------------------------------------------------- | ------------------ | ------------------------ |
| `Channel.java`                                             | 通道接口               | 新通道类型先扩接口                |
| `ChannelManager.java` / `impl/ChannelManagerImpl.java`     | 通道创建、订阅、发布、外部订阅者管理；`getHealthSummary()` 通道级健康摘要 | WebSocket/神经元消息推送问题优先查这里 |
| `ChannelMessage.java`                                      | 通道消息对象             | 消息字段扩展                   |
| `ChannelMessageQueue.java`                                 | 消息队列抽象             | 队列策略                     |
| `ChannelPublisher.java` / `impl/ChannelPublisherImpl.java` | 发布器接口和实现           | 业务层统一发布消息                |
| `ChannelSubscriber.java`                                   | 订阅者接口              | 神经元或外部订阅者实现              |
| `impl/BroadcastChannel.java`                               | 广播通道               | 一对多推送                    |
| `impl/UnicastChannel.java`                                 | 单播通道               | 点对点消息                    |
| `impl/RoundRobinChannel.java`                              | 轮询通道               | 多执行者分发                   |
| `impl/PriorityChannel.java`                                | 优先级通道              | 高优先级任务/告警                |

### 6.5 `employee` 与 `worker` 员工/数字工人包

| 文件/目录                                                                               | 功能说明        | 修改建议                |
| ----------------------------------------------------------------------------------- | ----------- | ------------------- |
| `employee/Employee.java`                                                            | 统一员工领域对象    | 人类员工和数字员工共性字段       |
| `employee/EmployeeOrigin.java`                                                     | 员工来源枚举      | HUMAN/FIXED/EVOLVED/PERSONAL，区分员工创建方式 |
| `employee/AccessType.java`                                                         | 访问类型枚举      | PUBLIC/AUTHENTICATED/DEPARTMENT，控制资源可见性 |
| `employee/EmployeePersonality.java`                                                | 员工个性模型      | rigor/creativity/riskTolerance/obedience 四维个性，支持部门默认值和 BrainPersonality 转换 |
| **`employee/EmployeeCapabilityProfile.java`**                                      | **【64-A-2 新增】** 员工能力清单 | record含availableTools/department/capabilities/knowledgeDomains/load，供行动发现/LLM调度使用，替代关键词匹配 |
| `employee/EmployeeStatus.java`                                                     | 员工状态枚举      | 11种状态（ONLINE/OFFLINE/BUSY/ACTIVE/DORMANT/LEARNING/EVOLVING等），含状态转换规则和分类判定 |
| `employee/impl/HumanEmployee.java`                                                  | 人类员工实现      | 人类员工行为或状态           |
| `employee/impl/DigitalEmployee.java`                                                | 数字员工实现      | 数字员工执行、技能、状态        |
| `employee/EmployeeService.java`                                                     | 员工服务接口      | 员工查询、创建、状态更新；EmployeeCreationRequest 含29字段（含primaryModelId/fallbackModelId/templateId/permissionScopeType/permissionAccessLevel/maxTokensPerDay/maxTokensPerMonth）        |
| `employee/impl/EmployeeServiceImpl.java`                                            | 早期/内存员工服务实现 | 注意和 JPA 实现区分，避免重复   |
| `employee/impl/JpaEmployeeServiceImpl.java`                                         | JPA 员工服务实现  | 持久化员工数据优先改这里        |
| `employee/entity/EmployeeEntity.java`                                               | 员工 JPA 基类实体 | SINGLE\_TABLE 继承策略，employees 表，id/name/department/status 等公共字段 |
| `employee/entity/DigitalEmployeeEntity.java`                                        | 数字员工 JPA 实体 | 继承 EmployeeEntity，model/brainDomain/skills/origin 等数字员工字段 |
| `employee/entity/HumanEmployeeEntity.java`                                          | 人类员工 JPA 实体 | 继承 EmployeeEntity，email/phone/role 等人类员工字段 |
| `employee/repository/EmployeeRepository.java`                                       | 员工 JPA Repository | 按部门/状态查询、数字/人类员工分类查询、计数统计 |
| `employee/EmployeeRegistry.java` / `impl/InMemoryEmployeeRegistry.java`             | 员工注册表       | 数字员工查找和调度           |
| `employee/registry/FixedEmployeeRegistry.java`                                      | 固定数字员工注册表   | 32 个固定数字员工定义、部门员工映射 |
| `employee/EmployeeLifecycleService.java` / `impl/EmployeeLifecycleServiceImpl.java` | 员工生命周期管理    | 入职、离职、状态流转          |
| `employee/EmployeeCompensationService.java`                                         | 员工薪酬/激励服务接口 | 数字员工奖励、补偿           |
| `employee/lifecycle/AgentLifecycleMonitor.java`                                     | P39-A: Agent生命周期监控（闭环39） | 心跳超时60s/错误率30%，异常触发CrossLoopEventBus |
| `employee/lifecycle/AgentHealthMetrics.java`                                        | P39-B: Agent指标采集（闭环39） | uptime/errorCount/avgResponseTime采集 |
| `employee/lifecycle/AgentAutoRecovery.java`                                         | P39-C: Agent自动恢复（闭环39） | 最大重启3次/冷却30s/降级配置/永久故障沉淀经验 |
| `employee/impl/JpaEmployeeCompensationService.java`                                 | 薪酬 JPA 实现，record()/definePlan()/assignPlan() 已添加 @Transactional   | 持久化薪酬记录             |
| `employee/impl/InMemoryEmployeeCompensationService.java`                            | 内存薪酬实现      | 本地或测试               |
| `employee/neuron/EmployeeNeuron.java`                                               | 员工和神经元适配    | 让数字员工以神经元形式执行       |
| `employee/claim/TaskClaimService.java`                                              | 任务认领服务      | 公开任务/抢单任务           |
| `employee/sync/EmployeeStateSynchronizer.java`                                     | 员工-神经元状态同步器 | 双向同步 EmployeeStatus↔NeuronState，30秒定时同步，forceSync/getSyncStatus |
| `employee/EmployeeEntityMigrationService.java`                                     | 员工数据迁移服务   | 员工实体数据迁移，统一 EmployeeEntity/EnterpriseEmployeeEntity 数据同步           |
| `employee/ResponsibilityCardService.java`                                           | 职责卡服务      | 数字员工职责卡管理，支持从 documents 加载和数据库持久化职责卡                               |
| `worker/DigitalWorker.java`                                                         | 数字工人抽象      | 更偏执行工人的抽象           |
| `worker/WorkerMetrics.java`                                                         | 工人指标度量      | 任务完成/成功/失败计数、响应时间、成功率、token消耗、能力评分、快照 |
| `worker/factory/DigitalWorkerFactory.java` / `impl/DigitalWorkerFactoryImpl.java`   | 数字工人工厂      | 创建数字员工/工人           |
| `worker/lifecycle/LifecycleManager.java` / `impl/LifecycleManagerImpl.java`         | 工人生命周期管理    | 工人健康检查、启动/停止        |
| `worker/collaboration/CollaborationService.java`                                    | **【闭环34】** 工人协作服务接口      | 多员工协作会话管理（createSession/startSession/completeSession）、任务分配、协作推荐、绩效评估 |
| `worker/collaboration/CollaborationSession.java`                                   | **【闭环34】** 协作会话接口      | 定义7种协作类型（TASK\_CHAIN/PARALLEL/ROUND\_ROBIN/DEBATE/CONSENSUS/HIERARCHICAL/PEER\_REVIEW）、会话状态、任务结构和结果；**PEER\_REVIEW自动状态推进**（开发→提交→审查→反馈→通过） |
| `worker/collaboration/impl/CollaborationServiceImpl.java`                          | **【闭环34-C】** 协作服务内存实现    | 协作会话创建/查询/加入/离开、任务分配与完成、依赖推进、等待完成、协作推荐；**支持TaskChain依赖解析和PEER_REVIEW状态机** |
| `worker/template/WorkerTemplate.java`                                               | 工人模板        | 新员工模板化创建            |
| `worker/template/impl/BaseWorkerTemplate.java`                                      | 基础工人模板实现    | Builder模式，含WorkerType/部门/角色/能力/技能/通道/个性/经验等级等配置 |

避免重复建议：

- 统一员工模型优先使用 `core.employee.Employee`，不要和 `core.security.SecurityIdentity`（原 `Employee`）混用。
- 持久化员工优先走 `JpaEmployeeServiceImpl` + `EnterpriseEmployeeEntity` / `EmployeeEntity`。
- 部门固定数字员工优先查 `FixedEmployeeRegistry` 和 `FixedEmployeeDefinitionEntity`。

### 6.6 `model` 与 `provider` 模型包

| 文件/目录                                                                  | 功能说明                                   | 修改建议                                                                               |
| ---------------------------------------------------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------- |
| `model/ModelManager.java` / `impl/ModelManagerImpl.java`               | 模型管理入口                                 | 模型状态、会话、调用管理                                                                       |
| `model/ModelClient.java`                                               | 模型客户端接口                                | 新模型客户端实现接口                                                                         |
| `model/impl/NamedPipeModelClient.java`                                 | 与 Python model daemon 通过 named pipe 通讯 | 本地模型会话、超时、管道协议问题                                                                   |
| `model/ModelRequest.java` / `ModelResponse.java` / `ModelSession.java` | 模型请求/响应/会话对象                           | 模型协议字段扩展                                                                           |
| `provider/Provider.java`                                               | 模型 Provider 接口                         | LLM/ASR/TTS Provider 统一抽象                                                          |
| `provider/ProviderRegistry.java` / `impl/ProviderRegistryImpl.java`    | Provider 注册                            | 新 Provider 注册                                                                      |
| `provider/impl/QwenProvider.java`                                      | Qwen 模型 Provider                       | Qwen 调用问题                                                                          |
| `provider/impl/OllamaProvider.java`                                    | Ollama Provider                        | Ollama 本地模型                                                                        |
| `provider/impl/BitNetProvider.java`                                    | BitNet Provider                        | 低资源模型                                                                              |
| `provider/impl/AnthropicProvider.java`                                | Anthropic 协议 Provider                 | 支持 Anthropic 原生协议的模型调用，与 `model/pool/client/AnthropicClient` 互补                |
| `provider/impl/AsrProvider.java` / `TtsProvider.java`                  | ASR/TTS Provider                       | 语音输入输出                                                                             |
| `provider/impl/ProviderFactory.java`                                   | Provider 工厂                            | 根据配置创建 Provider                                                                    |
| `provider/impl/ResolvedBrainModelProvider.java`                        | 根据模型池解析后的大脑 Provider                   | 大脑模型选择后的调用链路                                                                       |
| `provider/impl/InferenceResultValidator.java`                         | P20-D: 推理结果校验器，null/空/过短检查+10种错误模式匹配+CUDA OOM/timeout等检测+sanitize清洗 | 推理结果质量保障 |
| `model/pool/Protocol.java`                                             | 模型协议枚举                                 | HTTP/HTTPS/NamedPipe 等协议类型                                                         |
| `model/pool/LlmModel.java`                                             | LLM 模型实体                               | 模型名称、Provider、类型、版本、启用状态                                                           |
| `model/pool/LlmModelRepository.java`                                   | LLM 模型 Repository                      | 按 Provider/启用状态查询模型                                                                |
| `model/pool/ProviderConfig.java`                                       | Provider 配置实体                          | Provider ID、类型、BaseURL、API Key 配置、启用状态                                             |
| `model/pool/ProviderConfigRepository.java`                             | Provider 配置 Repository                 | 按 ID/类型/启用状态查询 Provider                                                            |
| `model/pool/BrainModelAssignment.java`                                 | 大脑模型分配实体                               | brainId 到 modelId 的映射关系                                                            |
| `model/pool/BrainModelAssignmentRepository.java`                       | 大脑模型分配 Repository                      | 按 brainId 查询模型分配                                                                   |
| `model/pool/BrainModelResolver.java`                                   | 大脑模型解析器                                | 按 brainId 解析模型分配，已集成 `ModelHealthRegistry` 支持熔断过滤                                  |
| `model/pool/ModelHealthRegistry.java`                                  | 模型健康注册表                                | 记录模型成功/失败/超时/熔断状态，支持 AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN 五种状态，冷却到期自动恢复 |
| `model/pool/ModelPoolManager.java`                                     | 模型池管理器                                 | 模型和 Provider 统一管理，支持测试连接                                                           |
| `model/pool/LlmProviderRegistry.java`                                     | LLM Provider 注册清单（原 `ProviderRegistry`，已重命名明确职责）                          | Provider 注册清单查询                                                                    |
| `model/pool/BrainModelAssigner.java`                                   | 大脑模型分配器                                | 分配/清除模型到大脑                                                                         |
| `model/pool/BrainModelChangeHistory.java`                              | 大脑模型变更历史实体                             | 记录模型分配变更历史                                                                         |
| `model/pool/BrainModelChangeHistoryRepository.java`                    | 变更历史 Repository                        | 查询模型变更历史                                                                           |
| `model/pool/BuiltinModelCatalog.java`                                  | 内置模型目录                                 | 预置模型列表                                                                             |
| `model/pool/LlmClient.java`                                            | LLM 客户端接口                              | 统一 LLM 调用                                                                          |
| `model/pool/LlmClientFactory.java`                                     | LLM 客户端工厂                              | 按 Provider 类型创建客户端                                                                 |
| `model/pool/ResolvedBrainModel.java`                                   | 已解析大脑模型                                | 解析后的模型配置                                                                           |
| `model/pool/ProviderTestResult.java`                                   | Provider 测试结果                          | 连接测试记录                                                                             |
| `model/pool/client/AnthropicClient.java`                               | Anthropic LLM 客户端                      | Claude 系列模型调用                                                                      |
| `model/pool/client/OpenAiCompatibleClient.java`                        | OpenAI 兼容客户端                           | 兼容 OpenAI API 的模型调用                                                                |
| `model/selector/ModelSelector.java`                                    | 模型选择器接口                                | 定义模型选择策略                                                                           |
| `model/selector/AbstractModelSelector.java`                            | 模型选择器抽象基类                              | 通用选择逻辑                                                                             |
| `model/selector/BrainModelSelector.java`                               | 大脑模型选择器接口                              | 大脑模型选择注册接口                                                                         |
| `model/selector/BrainModelSelectorManager.java`                        | 模型选择器管理器                               | 统一管理所有选择器                                                                          |
| `model/selector/BrainModelSelectorRegistrar.java`                      | 模型选择器注册器                               | 选择器注册管理                                                                            |
| `model/selector/AdminBrainModelSelector.java`                          | 管理员大脑模型选择器                             | admin 大脑模型选择                                                                       |
| `model/selector/MainBrainModelSelector.java`                           | 主脑模型选择器                                | core/main 大脑模型选择                                                                   |
| `model/selector/TechBrainModelSelector.java`                           | 技术部大脑模型选择器                             | tech 大脑模型选择                                                                        |
| `model/selector/HrBrainModelSelector.java`                             | 人力资源部大脑模型选择器                           | hr 大脑模型选择                                                                          |
| `model/selector/FinanceBrainModelSelector.java`                        | 财务部大脑模型选择器                             | finance 大脑模型选择                                                                     |
| `model/selector/SalesBrainModelSelector.java`                          | 销售部大脑模型选择器                             | sales 大脑模型选择                                                                       |
| `model/selector/CsBrainModelSelector.java`                             | 客服部大脑模型选择器                             | cs 大脑模型选择                                                                          |
| `model/selector/LegalBrainModelSelector.java`                          | 法务部大脑模型选择器                             | legal 大脑模型选择                                                                       |
| `model/selector/OpsBrainModelSelector.java`                            | 运营部大脑模型选择器                             | ops 大脑模型选择                                                                         |
| `model/selector/ToolNeuronModelSelector.java`                          | 工具神经元模型选择器                             | 工具调用专用模型选择                                                                         |
| `model/selector/HardwareResourceMonitor.java`                          | 硬件资源监控器                                | GPU/内存/CPU 监控，用于模型选择时考虑硬件负载                                                        |
| `model/UsageTracker.java` / `TokenUsage.java`                          | token 和用量统计                            | 成本控制、计费、日志                                                                         |
| `model/local/AiModelsConfig.java`                                       | AI 模型配置                                | 本地模型路径和参数                                                                          |
| `model/pool/ModelCapabilityAssessor.java`                               | 模型能力评估接口                               | 评估模型适合的任务类型                                                                        |
| `model/pool/impl/ModelCapabilityAssessorImpl.java`                      | 模型能力评估实现                               | 基于模型规格的能力评估                                                                        |
| `model/pool/ModelPerformanceAssessor.java`                              | 模型性能评估接口                               | 评估模型响应速度和质量                                                                        |
| `model/pool/impl/ModelPerformanceAssessorImpl.java`                     | 模型性能评估实现                               | 基于历史数据的性能评估                                                                        |
| `model/ModelStatus.java`                                                | 模型状态枚举                                 | AVAILABLE/LOADING/OVERLOADED/ERROR                                                   |

避免重复建议：

- 大脑使用什么模型，优先走 `model/pool` + `model/selector`，不要在 Brain 里硬编码模型名。
- 本地 named pipe 协议改 `NamedPipeModelClient` 和 `scripts/python/model_daemon.py`，两边要同步。

### 6.7 `tool` 工具包

| 文件/目录                                                                                                             | 功能说明            | 修改建议             |
| ----------------------------------------------------------------------------------------------------------------- | --------------- | ---------------- |
| `Tool.java`                                                                                                       | 工具接口            | 新工具实现此接口         |
| `ToolCall.java`                                                                                                   | 工具调用请求          | 工具参数协议           |
| `ToolResult.java`                                                                                                 | 工具调用结果          | 统一返回结构           |
| `ToolContext.java`                                                                                                | 工具上下文           | 用户、权限、会话、部门上下文   |
| `ToolSchema.java`                                                                                                 | 工具 schema       | 暴露给模型/大脑的工具定义；**64-A-1: 扩展 capabilities/outputSchema/healthCheckHint/installHint**    |
| **`ToolInvocationProtocol.java`**                                                                                  | **【64-C-2 新增】** 工具调用协议 | 统一工具调用：resolve(意图解析)→invoke(执行)→formatResult(格式化)，含InvocationPlan/InvocationResult record |
| **`impl/LlmToolInvocationResolver.java`**                                                                          | **【64-C-2 新增】** LLM工具意图解析器 | LLM语义解析任务描述到工具调用计划，LLM不可用时自动降级到关键词匹配（17种关键词映射） |
| `ToolRegistry.java` / `impl/ToolRegistryImpl.java`                                                                | 工具注册表           | 新工具注册入口          |
| `ToolExecutor.java` / `impl/DefaultToolExecutor.java`                                                             | 工具执行器           | 权限、Hook、调用分发     |
| `hook/ToolHookManager.java`                                                                                       | 工具调用前后 Hook     | 审计、审批、安全检查       |
| `impl/DockerTool.java`                                                                                            | Docker 操作工具     | 容器操作             |
| `impl/ClaudeCliTool.java`                                                                                         | Claude CLI 工具   | 外部 CLI 执行        |
| `impl/FileEditTool.java`                                                                                         | 文件编辑工具 v1.2.0   | 支持5种操作(read_file/write_file/edit_file/list_dir/search_code)，edit_file为行级精确编辑(old_string→new_string)，search_code支持正则(regex参数) |
| `impl/TraeTool.java`                                                                                              | Trae 工具         | Trae 执行集成        |
| `impl/WindowsAppTool.java`                                                                                        | Windows 应用自动化工具（pywinauto 桥接） | 通过 HTTP API 控制局域网客户端电脑的 Win32 桌面应用；需配合 `scripts/windows_automation/server.py` 使用 |
| `impl/WindowsAutomationTool.java`                                                                                 | 通用 Windows 系统控制工具（WebSocket 桥接） | 通过 WebSocket 转发到桌面端内嵌 Python 服务，支持 UIA/PowerShell/注册表/文件系统/进程管理等通用操作；需配合 `living-agent-desktop/resources/win-automation/service.py` 使用；高风险操作需审批 |
| `impl/GitHubTool.java`                                                                                            | GitHub 工具       | GitHub API       |
| `impl/BrowserAutomationTool.java` / `PlaywrightCrawlerTool.java`                                                  | 浏览器/爬虫工具；BrowserAutomationTool 已改为真实 Playwright API 实现（navigate/click/type/screenshot/getText/wait），懒初始化 Browser 实例，会话隔离 BrowserContext，优雅降级        | 页面自动化和采集         |
| `impl/PdfTool.java` / `OfficeTool.java`                                                                           | 文档处理工具          | PDF/Office 文档    |
| `impl/WebCrawlerTool.java` / `TavilySearchTool.java` / `SearXNGTool.java`                                         | 搜索/爬虫工具         | 外部信息检索           |
| `impl/enterprise/FeishuTool.java` / `EnterpriseFeishuTool.java` / `EmployeeFeishuTool.java` / `HrFeishuTool.java` | 飞书企业工具；EnterpriseFeishuTool 已 @Deprecated 并拆分为四个子工具（27 个 action），公共逻辑提取到 AbstractFeishuTool 基类 | 企业通讯录、通知、HR 场景   |
| `impl/enterprise/AbstractFeishuTool.java`                            | **【新增】** 飞书工具基类，提取公共逻辑（getAccessToken/buildClient/handleError） | 飞书工具公共逻辑修改时改这里 |
| `impl/enterprise/FeishuMessageTool.java`                             | **【新增】** 飞书消息工具（sendMessage/sendCard/updateCard/deleteMessage/replyMessage 等） | 飞书消息相关操作 |
| `impl/enterprise/FeishuContactTool.java`                             | **【新增】** 飞书通讯录工具（getUser/getDepartment/getDepartmentUsers/searchUser 等） | 飞书通讯录相关操作 |
| `impl/enterprise/FeishuApprovalTool.java`                            | **【新增】** 飞书审批工具（createApproval/getApproval/listApprovals/cancelApproval 等） | 飞书审批流程相关操作 |
| `impl/enterprise/FeishuCalendarTool.java`                            | **【新增】** 飞书日历工具（createEvent/getEvent/listEvents/deleteEvent 等） | 飞书日历相关操作 |
| `impl/enterprise/DingTalkTool.java`                                                                               | 钉钉企业工具          | 钉钉 API 集成        |
| `impl/enterprise/GitLabTool.java`                                                                                 | GitLab API操作工具，8个操作(list_projects/get_project/list_mrs/get_mr/create_mr_comment/list_commits/get_file/search)，支持员工级token隔离(resolveAccessToken)；配置：tool.gitlab.base-url/tool.gitlab.access-token/tool.gitlab.employee-accounts | 代码仓库             |
| `impl/enterprise/JenkinsTool.java`                                                                                | Jenkins CI/CD工具，6个操作(list_jobs/get_job/build/build_status/console_output/cancel_build)，Basic认证；配置：tool.jenkins.base-url/tool.jenkins.username/tool.jenkins.api-token | CI/CD            |
| `impl/enterprise/JiraTool.java`                                                                                   | Jira 工具         | 任务/缺陷管理          |
| `impl/enterprise/OpenProjectTool.java`                                                                            | OpenProject项目管理工具，6个操作(search_issue/get_issue/create_issue/update_issue/add_comment/search_user)，映射为jira工具名；配置：tool.openproject.base-url/tool.openproject.api-token | 项目管理             |
| `impl/BudgetManagementTool.java`                                                                                  | 预算管理工具          | 部门预算分配和跟踪        |
| `impl/HttpTool.java`                                                                                              | HTTP 请求工具       | 通用 HTTP/REST 调用  |
| `impl/HuggingFaceTool.java`                                                                                       | HuggingFace 工具  | HuggingFace 模型调用 |
| `impl/InvoiceProcessingTool.java`                                                                                 | 发票处理工具          | 发票识别和处理          |
| `impl/KnowledgeGraphTool.java`                                                                                    | 知识图谱工具          | 知识图谱查询和构建        |
| `impl/NotionTool.java`                                                                                            | Notion 工具       | Notion API 集成    |
| `impl/ProactiveAgentTool.java`                                                                                    | 主动服务工具          | 主动建议和服务触发        |
| `impl/RobotsChecker.java`                                                                                         | Robots 检查工具     | 网站 robots.txt 检查 |
| `impl/RssReaderTool.java`                                                                                         | RSS 阅读工具        | RSS 订阅读取         |
| `impl/SelfImprovingTool.java`                                                                                     | 自我改进工具          | 工具自动优化和进化        |
| `impl/SkillFinderTool.java`                                                                                       | 技能查找工具          | ClawHub 技能搜索     |
| `impl/SkillInstaller.java`                                                                                        | 技能安装器           | ClawHub 技能安装     |
| `impl/SlackTool.java`                                                                                             | Slack 工具        | Slack API 集成     |
| `impl/SummarizeTool.java`                                                                                         | 摘要工具            | 文本摘要生成           |
| `impl/Crawl4aiClient.java`                                                                                        | Crawl4AI 客户端    | AI 爬虫客户端         |
| `worktree/WorktreeManager.java` / `impl/GitWorktreeManager.java`                                                  | Git worktree 管理 | 代码任务隔离工作区        |
| `worktree/WorktreeEntry.java`                                                                                     | Worktree 数据结构   | 记录 name/path/branch/taskId/status/createdAt，提供 create 工厂方法和 withStatus 状态转换 |
| `weather/WeatherInfo.java`                                                                                        | 天气信息数据结构    | location/temperature/humidity/windSpeed/condition/pressure/feelsLike/forecast/alert |
| `weather/WeatherProvider.java`                                                                                    | 天气 Provider 接口 | getName/getPriority/isEnabled/fetchWeather/fetchForecast |
| `weather/impl/WttrProvider.java`                                                                                  | wttr.in 天气实现  | 基于 wttr.in 的天气查询  |
| `weather/impl/OpenWeatherMapProvider.java`                                                                         | OpenWeatherMap 实现 | 基于 OpenWeatherMap API 的天气查询 |
| `weather/impl/QWeatherProvider.java`                                                                               | 和风天气实现      | 基于和风天气 API 的天气查询  |
| `ToolStats.java`                                                                                                   | 工具统计        | 调用次数/成功率/耗时        |
| `hook/ToolHookResult.java`                                                                                         | 工具钩子结果      | 钩子执行结果             |
| **`backend/ExternalToolBackend.java`**                                                                             | **【64-B-1 新增】** 外部工具后端接口 | discover/healthCheck/installHint 统一抽象 |
| **`backend/BackendRegistry.java`**                                                                                 | **【64-B-1 新增】** 外部工具后端注册表 | 带30秒缓存的统一健康检查 |
| **`backend/ClaudeCliBackend.java`**                                                                                | **【64-B-1 新增】** Claude CLI 后端 | PATH 发现 + claude --version 健康检查 |
| **`backend/GitLabBackend.java`**                                                                                   | **【64-B-1 新增】** GitLab 后端 | HTTP /api/v4/version 健康检查 |
| **`backend/JenkinsBackend.java`**                                                                                  | **【64-B-1 新增】** Jenkins 后端 | HTTP /api/json 健康检查 |
| **`backend/OpenProjectBackend.java`**                                                                              | **【64-B-1 新增】** OpenProject 后端 | HTTP /api/v3/configuration 健康检查 |
| **`backend/FileSystemBackend.java`**                                                                               | **【64-B-1 新增】** 文件系统后端 | Java NIO 读写测试 |
| **`backend/DockerBackend.java`**                                                                                   | **【64-B-1 新增】** Docker 后端 | docker info 健康检查 |

避免重复建议：

- 新增外部系统能力优先做成 `Tool`，不要直接在 Brain/Controller 里写 HTTP 调用。
- 工具安全、审批、审计优先接 `ToolHookManager` 和 `security` 包。

### 6.7b `skill` 技能包

| 文件/目录                                                           | 功能说明              | 修改建议                   |
| ----------------------------------------------------------------- | ----------------- | -------------------- |
| `skill/Skill.java`                                                | 技能接口              | 新增 scope/ownerId/departmentId 字段（P3-1b） |
| `skill/SkillRegistry.java`                                        | 技能注册表接口           | 新增 getVisibleSkills() 按 AccessLevel 过滤 |
| `skill/SkillContext.java`                                         | 技能执行上下文           | 上下文信息扩展              |
| `skill/GeneratedSkill.java`                                       | 进化生成的技能           | 实现 scope/ownerId/departmentId |
| `skill/bounty/impl/BountyHunterSkillAdapter.java`                 | 赏金猎人技能适配器（原 `BountyHunterSkill`，已重命名避免与 Skill 接口混淆）          | 自主运营场景               |
| `skill/bounty/BountyHunterService.java`                           | 赏金猎手服务            | 赏金任务执行               |
| `skill/bounty/BountyTask.java`                                    | 赏金任务              | 赏金任务定义               |
| `skill/SkillResult.java`                                          | 技能执行结果            | 技能输出和状态              |
| `skill/feedback/SkillEffectivenessTracker.java`                   | P42-A: 技能效果追踪（闭环42） | 调用成功率/耗时追踪，<80%标记低效 |
| `skill/feedback/SkillRecommendationEngine.java`                   | P42-B: 技能推荐（闭环42） | 低效技能建议替换，耗时>5s建议优化 |
| **`skill/feedback/SkillCoverageEvaluator.java`**                  | **【64-G-1 新增】** 技能覆盖度评估器 | 评估员工技能覆盖度，识别高价值缺失技能，生成增量优化建议(CoverageReport/SkillSuggestion) |
| **`skill/feedback/SkillRefineService.java`**                      | **【64-G-2 新增】** 技能增量优化服务 | 借鉴CLI-Anything /refine机制：分析回执→识别低效模式→对照技能覆盖→生成改进建议(RefineResult/PerformanceIssue/SkillImprovement) |

**living-agent-skill 模块：**

| 文件/目录                                                           | 功能说明              | 修改建议                   |
| ----------------------------------------------------------------- | ----------------- | -------------------- |
| `model/SkillImpl.java`                                            | 技能实现类             | 实现 scope/ownerId/departmentId 字段 |
| `registry/SkillRegistryImpl.java`                                 | 技能注册表实现           | 实现 getVisibleSkills() 按 AccessLevel 过滤可见性 |
| `resources/skills/`                                               | 技能定义文件（SKILL.md）  | 新增技能在此目录添加           |

**技能作用域（P3-1b）：**

| scope      | 可见性         | 来源                     |
| ---------- | ----------- | ---------------------- |
| `global`   | 全租户可见       | `skills/` 目录下的 SKILL.md |
| `evolved`  | 所属部门可见      | `GeneratedSkill`（进化系统） |
| `personal` | 仅自己可见       | ClawHub 安装 / 用户创建      |

避免重复建议：

- 技能可见性由 `SkillRegistry.getVisibleSkills(userId, accessLevel, departmentId)` 统一过滤，前端只展示后端返回的列表。
- 新增技能作用域字段后，现有技能默认为 `global`。

### 6.8 `knowledge` 与 `memory` 包

| 文件/目录                                                                        | 功能说明           | 修改建议                 |
| ---------------------------------------------------------------------------- | -------------- | -------------------- |
| `knowledge/KnowledgeEntry.java`                                              | 知识条目领域对象       | 知识字段扩展               |
| `knowledge/KnowledgeManager.java` / `impl/KnowledgeManagerImpl.java`         | 知识管理接口和实现      | 知识增删改查、检索            |
| `knowledge/impl/KnowledgePromotionScheduler.java`                            | NP2-2: 知识晋升自动化调度器，@Scheduled 每10分钟检查晋升条件 | PRIVATE→DOMAIN/SHARED 自动晋升 |
| `knowledge/KnowledgeBase.java`                                               | 知识库接口          | 新知识库后端实现             |
| `knowledge/LayeredKnowledgeBase.java` / `impl/LayeredKnowledgeBaseImpl.java` | 分层知识库 L1/L2/L3；3 个 ConcurrentHashMap 手动缓存（最大 1000 条 + TTL 30分钟 + 定期清理） | 神经元/部门/企业知识晋升        |
| `knowledge/impl/KnowledgePersistenceService.java`                            | 知识持久化服务        | PostgreSQL/Qdrant 存储 |
| `knowledge/impl/SQLiteKnowledgeBase.java`                                    | SQLite 知识库；连接缓存复用（volatile Connection + JDK 动态代理包装防误关闭）；⚠️ 缺少 @PreDestroy，shutdown() 需手动调用     | 本地轻量知识               |
| `knowledge/impl/NativeKnowledgeBase.java`                                    | Native 知识库     | Rust native 知识能力     |
| `knowledge/professional/ProfessionalKnowledgeSeeder.java`                    | 专业知识初始化        | 预置知识导入               |
| `knowledge/professional/ArchitectureKnowledgeSeeder.java`                   | 架构文档知识播种器      | docs/documents → 知识库，让大脑"看到"代码结构 |
| `knowledge/professional/SourceTreeIndexer.java`                             | 源码结构索引生成器      | 生成 source-tree.json  |
| `knowledge/KnowledgeScope.java`                                              | 知识范围枚举         | L1_PRIVATE/L2_DEPARTMENT/L3_SHARED |
| `knowledge/KnowledgeType.java`                                               | 知识类型枚举         | RULE/BEST_PRACTICE/EXPERIENCE/PROCEDURE |
| `knowledge/KnowledgeStatus.java`                                             | 知识状态枚举         | DRAFT/ACTIVE/DEPRECATED/ARCHIVED |
| `knowledge/KnowledgeMetadata.java`                                           | 知识元数据          | 使用次数/有效性/来源          |
| `knowledge/Importance.java`                                                  | 重要性枚举          | LOW/MEDIUM/HIGH/CRITICAL |
| `knowledge/Validity.java`                                                    | 有效性枚举          | UNVERIFIED/VERIFIED/OUTDATED/INVALID |
| `knowledge/BestPractice.java`                                                | 最佳实践           | 可复用最佳实践              |
| `knowledge/Experience.java`                                                  | 经验记录           | 个人/团队经验              |
| `knowledge/nativestore/NativeKnowledge.java`                                     | 原生知识接口（原 `knowledge/native_/`，已重命名避免 Java 关键字冲突）         | Rust 实现的知识存储         |
| `knowledge/MemoryToKnowledgeExtractor.java`                                      | 记忆转知识提取器                                                    | 从对话记忆中自动提取可复用知识，支持 L1→L2→L3 晋升评估    |
| `knowledge/testing/KnowledgeBaseTestFramework.java`                          | 知识库测试框架        | 知识查询测试               |
| `memory/Memory.java` / `MemoryEntry.java`                                    | 记忆接口/条目        | 记忆字段和接口              |
| `memory/MemoryBackend.java`                                                  | 记忆后端接口         | 新后端实现                |
| `memory/impl/MemoryServiceImpl.java`                                         | 记忆服务实现         | 对话/任务记忆读写            |
| `memory/impl/SQLiteMemoryBackend.java`                                       | SQLite 记忆后端    | 本地记忆                 |
| `memory/impl/MemosMemoryBackend.java`                                        | Memos 记忆后端     | 外部 Memos 记忆服务        |
| `memory/impl/MemPalaceBackend.java`                                          | MemPalace 记忆后端；已有 @jakarta.annotation.PreDestroy + destroy() 优雅关闭 MCP 子进程 | MemPalace 集成         |
| `memory/MemoryCategory.java`                                                 | 记忆分类枚举         | EPISODIC/SEMANTIC/PROCEDURAL |
| `knowledge/KnowledgeConsumptionFeedback.java`                                | P26-A: 知识消费反馈        | recordFeedback(key,helpful,context,consumerId)，helpfulRate≥80%提升confidence(+0.15)，≤30%降低(-0.2) |
| `memory/feedback/MemoryConversionTracker.java`                               | P48-A: 记忆转化追踪（闭环48） | 记忆→知识转化率/引用率/归档率追踪 |
| `memory/feedback/MemoryConsolidationService.java`                            | P48-B: 记忆整合（闭环48） | 转化率<10%建议降低阈值，自动整合相似记忆 |

避免重复建议：

- 短期上下文不等于长期记忆；长期记忆优先走 `MemoryServiceImpl`。
- 企业制度/最佳实践优先走 `KnowledgeManager` / `LayeredKnowledgeBase`。

### 6.9 `evolution` 自主进化包

| 文件/目录                                                                                   | 功能说明             | 修改建议          |
| --------------------------------------------------------------------------------------- | ---------------- | ------------- |
| `evolution/signal/EvolutionSignal.java`                                                 | 进化信号对象           | 新信号类型字段       |
| `evolution/signal/SignalExtractor.java` / `DefaultSignalExtractor.java`                 | 从错误、反馈、性能中提取进化信号 | 进化触发条件        |
| `evolution/engine/EvolutionDecisionEngine.java` / `DefaultEvolutionDecisionEngine.java` | 进化决策引擎           | 修复/优化/创新/上报判断 |
| `evolution/engine/EvolutionOrchestrator.java`                                           | 进化总编排            | 定时进化、自动调整     |
| `evolution/executor/EvolutionExecutor.java`                                             | 进化执行器接口          | 执行修复或优化       |
| `evolution/executor/EvolutionResult.java`                                               | 进化结果对象           | 结果字段          |
| `evolution/executor/EvolutionFeedbackService.java`                                      | 进化反馈服务，JPA实现 record() 已添加 @Transactional     | 用户/系统反馈入库     |
| `evolution/scheduler/EvolutionScheduler.java` / `EvolutionSchedulerImpl.java`           | 进化调度             | 定时进化和失败重试     |
| `evolution/circuitbreaker/*`                                                            | 进化熔断             | 防止错误进化或高风险操作  |
| `evolution/memory/*`                                                                    | 进化记忆图            | 进化历史和经验       |
| `evolution/impl/KnowledgeEvolverImpl.java`                                              | 知识进化实现           | 知识质量提升        |
| `evolution/impl/SkillGeneratorImpl.java`                                                | 技能生成实现           | 自动生成技能        |
| `evolution/CapabilityEvaluator.java`                                                    | 能力评估器            | 评估数字员工能力      |
| `evolution/EvaluationResult.java`                                                       | 评估结果             | 能力评分和改进建议     |
| `evolution/ImprovementSuggestion.java`                                                  | 改进建议             | 进化方向建议        |
| `evolution/SkillGenerator.java`                                                         | 技能生成器            | 自动生成新技能       |
| `evolution/KnowledgeEvolution.java`                                                     | 知识进化             | 知识晋升和衰退       |
| `evolution/KnowledgeEvolver.java`                                                       | 知识进化器            | 知识质量提升        |
| `evolution/KnowledgeIssue.java`                                                         | 知识问题             | 知识缺陷记录        |
| `evolution/KnowledgeMergeResult.java`                                                   | 知识合并结果           | 知识合并产出        |
| `evolution/KnowledgePropagationResult.java`                                             | 知识传播结果           | 跨部门知识传播       |
| `evolution/KnowledgeQualityReport.java`                                                 | 知识质量报告           | 知识健康度评估       |
| `evolution/personality/BrainPersonality.java`                                           | 大脑个性             | rigor/creativity/riskTolerance/obedience |
| `evolution/personality/PersonalityMutation.java`                                        | 个性变异             | 个性参数调整        |
| `evolution/personality/PersonalityStats.java`                                           | 个性统计             | 个性参数统计        |
| `evolution/engine/AutoAdjustStrategy.java`                                              | 自动调整策略           | 基于绩效的自动调整     |
| `evolution/scheduler/EvolutionJobConfig.java`                                           | 进化任务配置           | 定时进化任务配置      |
| `evolution/executor/EvolutionResultRepository.java`                                     | 进化结果仓库           | 进化结果持久化       |
| `evolution/EvolutionManager.java`                                                       | 进化管理器（从 `autonomous/evolution` 迁移至 `core/evolution`） | 进化流程统一管理      |
| `evolution/HardwareUpgradeService.java`                                                 | 硬件升级服务（从 `autonomous/evolution` 迁移至 `core/evolution`） | 硬件升级决策和执行     |
| `evolution/escalation/EscalationLevel.java`                                             | 升级级别枚举（WARNING/CRITICAL/EMERGENCY） | 统一升级通知级别      |
| `evolution/escalation/EscalationRecord.java`                                            | 升级记录实体         | 升级通知完整信息       |
| `evolution/escalation/EscalationNotificationService.java`                               | 统一升级通知服务       | 所有升级的唯一出口      |
| `evolution/codemapper/CodeLocation.java`                                                | 代码位置注解         | 在关键类上标注代码位置    |
| `evolution/codemapper/CodeContext.java`                                                 | 代码上下文实体        | 异常→代码→文档的映射结果  |
| `evolution/codemapper/ErrorCodeMapper.java`                                             | 错误到代码映射器       | 异常类/错误码→代码文件→文档 |
| `evolution/codebase/CodebaseAccessConfig.java`                                          | 代码库访问配置        | 挂载点/敏感文件过滤/速率限制 |
| `evolution/codebase/CodebaseAccessService.java`                                         | 代码库受控访问服务      | 大脑自由读写代码库镜像    |
| `evolution/patch/PatchProposal.java`                                                    | 补丁提案实体         | 含 confidence/rollbackAvailable |
| `evolution/patch/PatchProposalService.java`                                             | 补丁提案服务         | 创建/保存/查询补丁提案   |
| `evolution/patch/PatchApplicationService.java`                                          | 补丁应用服务         | applyPatch()真正应用diff(JSON格式+unified diff格式)+rollbackPatch()恢复原始内容+saveRollbackBaseline()保存.orig副本 |
| `evolution/orchestrator/impl/SelfHealingOrchestratorImpl.java`                          | 自愈编排器实现        | P24-A: 异常→根因→决策→执行→验证；`determineAction()` null安全处理 |
| `evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java`                       | 自治编排器实现，已修复 createIssue()/setMode() 方法不存在错误        | P31-A: 优先级仲裁→事件分发→协同执行 |

**evolution 子包结构：**

| 子包 | 说明 |
|------|------|
| `evolution/orchestrator/` | 自愈与治理编排器 |
| `evolution/orchestrator/impl/` | 编排器实现 |
| `evolution/circuitbreaker/` | 进化熔断器 |
| `evolution/personality/` | 大脑个性与满意度 |
| `evolution/codemapper/` | 错误码映射 |

**evolution 新增类：**

| 文件/目录                                                                                   | 功能说明             | 修改建议          |
| --------------------------------------------------------------------------------------- | ---------------- | ------------- |
| `evolution/HardwareUpgradeRoiValidator.java`                                            | P25-A: 硬件升级ROI验证        | 7天跟踪，PerformanceBaseline vs PerformanceSnapshot，ROI<-20%触发CircuitBreaker，<-50%标记ROLLBACK_RECOMMENDED |
| `evolution/orchestrator/impl/SelfHealingOrchestratorImpl.java`                          | P24-A: 自愈编排器实现        | 六步闭环(异常检测→根因分析→补丁→执行→验证→经验沉淀)，RESTART_PROCESS/CLEAR_DEGRADED/RECONNECT_PIPE/ESCALATE四种动作 |
| `evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java`                       | P31-A: 跨闭环协同编排器，已修复 createIssue()/setMode() 方法不存在错误        | 7级优先级(安全>自愈>降级>回执>经济>知识>个性)+CrossLoopEventBus+冷却期去重 |
| `evolution/personality/SatisfactionCollector.java`                                      | P29-A: 满意度采集        | recordSatisfaction+平均分+低满意度降riskTolerance+高满意度增riskTolerance |

### 6.10 `security` 权限认证安全包

| 文件/目录                                                        | 功能说明           | 修改建议                        |
| ------------------------------------------------------------ | -------------- | --------------------------- |
| `AuthContext.java` / `AuthContextService.java`               | 当前认证上下文        | 用户身份、租户、权限传递                |
| `AccessLevel.java` / `AutonomyLevel.java`                    | 访问级别/自主级别      | 权限等级定义                      |
| `PermissionService.java` / `impl/PermissionServiceImpl.java` | 权限判断和访问记录      | 部门/资源权限                     |
| `AccessGateService.java` / `impl/AccessGateServiceImpl.java` | 访问网关           | 统一权限入口                      |
| `RequireAccess.java` / `RequireAccessAspect.java`           | 统一权限注解 + AOP 切面（已移至 `gateway/security/`） | 替代 Controller 中零散的 `accessGateService.canRoute()` 调用 |
| `DepartmentAccessValidator.java`                             | 部门访问校验         | 部门页面/部门大脑权限                 |
| `BrainAccessControl.java`                                    | 大脑访问控制         | 用户能否访问某部门大脑                 |
| `auth/UnifiedAuthService.java`                               | 统一认证服务         | session 创建、登录后身份            |
| `auth/PhoneVerificationService.java`                         | 手机验证码          | 测试环境注意不要打印真实验证码             |
| `auth/FounderService.java`                                   | 创始人状态和初始化      | 创始人登录、初始化流程                 |
| `auth/OAuthService.java` + impl                              | 钉钉/飞书/企微 OAuth | 第三方登录                       |
| `service/EnterpriseEmployeeService.java`                     | 企业员工服务         | 企业通讯录/员工信息                  |
| `sync/*`                                                     | 钉钉/飞书/HR 同步适配  | 外部组织同步                      |
| `session/*`                                                  | 会话实体、仓库、管理器    | 登录 session 持久化              |
| `voiceprint/*` / `speaker/*`                                 | 声纹/说话人识别       | 语音身份认证                      |
| `bash/BashSecurityValidator.java`                            | Bash 命令安全校验    | 命令执行安全                      |
| `impl/SandboxExecutorImpl.java`                              | 沙箱执行           | 命令/脚本隔离执行                   |
| `impl/ContentValidatorImpl.java`                             | 内容安全校验         | 内容审核、提示词安全                  |
| `SkillVetter.java` / `impl/SkillVetterImpl.java`             | 技能安全审核         | 自动生成技能上线前检查                 |
| `ExecutionBoundaryEnforcer.java`                             | 越权拦截执行器        | 跨部门/超管辖/高风险任务硬判断，8 个部门管辖权映射 |
| `SecurityIdentity.java`                                      | 安全上下文员工        | 认证用员工信息（原 `Employee.java`，已重命名避免与 `employee.Employee` 混淆）   |
| `AuthEmployeeService.java`                                   | 安全员工服务         | 认证/声纹/OAuth 查找（原 `EmployeeService.java`，与 `employee.EmployeeService` 不同） |
| `EmployeeAuthService.java`                                   | 员工认证服务         | 登录/注册/OAuth 绑定                     |
| `UserIdentity.java`                                          | 用户身份枚举         | FOUNDER/EMPLOYEE/TRAINEE/RESIGNED/VISITOR/CUSTOMER/PARTNER |
| `Department.java`                                            | 部门枚举           | tech/hr/finance/sales/cs/admin/legal/ops |
| `SecurityPolicy.java`                                        | 安全策略接口         | 权限检查策略                              |
| `impl/SecurityPolicyImpl.java`                               | 安全策略实现         | 基于访问级别的权限控制                         |
| `impl/AuthEmployeeServiceImpl.java`                          | 安全员工服务实现      | 内存 Map 存储（认证用，原 `EmployeeServiceImpl.java`）                     |
| `impl/EmployeeChangeDetectorImpl.java`                        | 员工变更检测实现      | 检测员工信息变更                           |
| `impl/SandboxWorker.java`                                    | 沙箱工人           | 沙箱执行辅助                              |
| `EmployeeChangeDetector.java`                                | 员工变更检测接口      | 变更检测和通知                            |
| `DetectedChange.java`                                        | 检测到的变更        | 变更类型和内容                             |
| `ChangeStatus.java`                                          | 变更状态           | PENDING/APPROVED/REJECTED            |
| `AccessAuditLog.java`                                        | 访问审计日志         | 操作审计记录                              |
| `ApprovalManager.java`                                       | ~~已删除~~ 审批管理器          | ~~安全相关审批~~ 已移除，工具审批由 BrainBoundaryEnforcer 四重校验等价替代                              |
| `SandboxExecutor.java`                                       | 沙箱执行器接口       | 沙箱执行抽象                              |
| `bash/BashValidationResult.java`                             | Bash 验证结果       | 命令安全性检查结果                           |
| `importer/EmployeeImporter.java`                             | 员工导入器          | 批量导入员工数据                            |
| `profile/UserProfileEntity.java`                             | 用户画像实体         | JPA 实体                              |
| `profile/UserProfileRepository.java`                         | 用户画像 Repository | JPA 查询                              |
| `profile/UserProfileService.java`                            | 用户画像服务         | 画像数据管理                              |
| `DepartmentAccessService.java`                               | 部门访问服务         | 部门权限控制                              |
| `SandboxViolationTracker.java`                              | P30-A: 沙箱违规追踪        | 3次违规/1h→自动黑名单，BlacklistEntry含expiresAt(TTL 1h)，isBlacklisted()检查TTL自动恢复 |
| `auth/AuthMetricsService.java`                             | P38-A: 认证指标收集（闭环38）   | 按method/source/success/failure/reason聚合，失败率>30%触发告警 |
| `auth/AuthFeedbackService.java`                            | P38-B/C: 认证反馈+声纹质量（闭环38） | 失败率超阈值自动调整策略，声纹质量闭环 |
| `client/feedback/ClientDeviceHealthMonitor.java`           | P61-A: 客户端设备健康（闭环61） | 设备异常操作>=5次建议自动解绑，通过CrossLoopEventBus发布告警 |

避免重复建议：

- 权限判断统一走 `PermissionService` / `AccessGateService`。
- 不要在 Controller 里手写大量权限 if；应放拦截器或 core security 服务。

### 6.11 `database` 数据库包

| 文件/目录                                                                 | 功能说明                   | 修改建议                                                                                                                                                                            |
| --------------------------------------------------------------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `database/config/PostgreSQLConfig.java`                               | PostgreSQL 配置          | 数据源、JPA 配置                                                                                                                                                                      |
| `database/config/QdrantConfig.java`                                   | Qdrant 配置              | 向量库地址和客户端                                                                                                                                                                       |
| `database/entity/*Entity.java`                                        | JPA 实体                 | 表字段变更时改实体 + migration                                                                                                                                                           |
| `database/entity/ArtifactRecordEntity.java`                           | Artifact 记录实体          | artifact 产物数据库表结构，支持 artifactId/executionId/department/employee/type/path/size/sha256/metadata，新增 `task_id`/`project_id` 列                                                      |
| `database/entity/CodeReviewStateEntity.java`                          | 代码审查状态实体              | code\_review\_states 表，taskId/projectId/executionId/stage/reviewRound/developer/reviewer/worktreePath/diffPath/findings/metadata                                                          |
| `database/entity/DepartmentEntity.java`                               | 部门实体                   | 部门基本信息、编码、层级关系                                                                                                                                                                  |
| `database/entity/TenantEntity.java`                                   | 租户实体                   | 多租户数据隔离                                                                                                                                                                         |
| `database/entity/EnterpriseEmployeeEntity.java`                       | 企业员工实体                 | 员工档案、部门归属、职位                                                                                                                                                                    |
| `database/entity/DepartmentChatMessageEntity.java`                    | 部门聊天消息实体               | 部门聊天会话记录，新增 conversationId/taskKey/executionId/messageType/tenantId/deletedAt 字段                                                                                                |
| `database/entity/DepartmentConversationEntity.java`                   | 部门对话实体                 | 长期可恢复部门对话，包含 conversationId/conversationKey/tenantId/ownerUserId/departmentCode/title/status/lastMessageAt/activeTaskKey/activeExecutionId/retentionPolicy/archivedAt/deletedAt |
| `database/entity/KnowledgeEntryEntity.java`                           | 知识条目实体                 | 知识库内容持久化                                                                                                                                                                        |
| `database/entity/TaskEntity.java`                                     | 任务实体                   | 任务持久化主表，包含 taskType/description/priority/status/userId/tenantId/taskKey/executionId/departmentCode/sourceType/projectId/submissionResult/reviewerId 等统一身份字段                     |
| `database/entity/ProjectEntity.java`                                  | 项目实体                   | 项目持久化主表，包含 tenantId/creatorUserId/projectKey/sourceTaskKey/sourceConversationId/dataNamespace/managerId/ownerDepartment 等统一身份字段                                                 |
| `database/entity/FixedEmployeeDefinitionEntity.java`                  | 固定员工定义实体               | 固定员工代码、名称、标题、部门、neuronId、能力、工具                                                                                                                                                  |
| `database/entity/FixedEmployeeProfileEntity.java`                     | 固定员工画像实体               | 员工长期画像、绩效、经验                                                                                                                                                                    |
| `database/entity/FixedEmployeePersonaEntity.java`                     | 固定员工外观实体               | 员工个性化配置、头像、语气                                                                                                                                                                   |
| `database/entity/PerformanceAssessmentEntity.java`                    | 绩效评估实体                 | 员工绩效考核记录                                                                                                                                                                        |
| `database/entity/PerformanceIndicatorEntity.java`                     | 绩效指标实体                 | 绩效指标定义                                                                                                                                                                          |
| `database/entity/PerformanceTrendSnapshotEntity.java`                 | 绩效趋势快照实体               | 绩效趋势快照数据                                                                                                                                                                        |
| `database/entity/CompensationAccountEntity.java`                      | 薪酬账户实体                 | 员工薪酬账户                                                                                                                                                                          |
| `database/entity/CompensationPlanEntity.java`                         | 薪酬计划实体                 | 薪酬计划定义                                                                                                                                                                          |
| `database/entity/CompensationRecordEntity.java`                       | 薪酬记录实体                 | 薪酬发放记录                                                                                                                                                                          |
| `database/entity/EvolutionFeedbackEntity.java`                        | 进化反馈实体                 | 进化反馈数据                                                                                                                                                                          |
| `database/entity/EvolutionAuditLogEntity.java`                        | 进化审计日志实体               | 进化操作审计                                                                                                                                                                          |
| `database/entity/EvolutionResultEntity.java`                          | 进化结果实体                 | 进化结果记录                                          |
| `database/entity/PendingEventEntity.java`                             | 待处理事件实体                | JPA 实体                                                                                                          |
| `database/entity/SessionContextEntity.java`                            | 会话上下文实体                | JPA 实体                                                                                                          |
| `database/entity/TraceEventEntity.java`                                | Trace 事件实体               | 自治编排 Trace 事件持久化，记录 traceId/stage/eventType/timestamp/metadata 等字段                                    |
| `database/entity/EmployeeExecutionReceiptEntity.java`                  | 员工执行回执实体              | 员工执行回执持久化，记录 executionId/employeeCode/status/summary/artifacts/worktreePath/diffPath 等字段                      |
| `database/entity/ApprovalInstanceEntity.java`                          | **【新增】** 审批实例实体              | 审批流程实例持久化                              |
| `database/entity/ApprovalWorkflowEntity.java`                          | **【新增】** 审批工作流实体            | 审批流程定义持久化                              |
| `database/entity/BrainBoundaryAuditEntity.java`                        | **【新增】** 大脑边界审计实体          | 大脑边界决策审计记录                              |
| `database/entity/ClientDeviceEntity.java`                              | **【新增】** 客户端设备实体            | 客户端设备注册信息                              |
| `database/entity/ClientOperationAuditLogEntity.java`                   | **【新增】** 客户端操作审计日志实体      | 客户端操作审计记录                              |
| `database/entity/ClientUserBindingEntity.java`                         | **【新增】** 客户端用户绑定实体        | 客户端设备与用户绑定关系（复合主键 ClientUserBindingId）                              |
| `database/entity/ComplianceViolationEntity.java`                       | **【新增】** 合规违规实体            | 合规违规记录                              |
| `database/entity/DagTaskEntity.java`                                   | **【新增】** DAG 任务实体          | DAG 任务依赖关系                              |
| `database/entity/DepartmentDeliverableEntity.java`                     | **【新增】** 部门交付物实体          | 部门级交付物记录                              |
| `database/entity/DepartmentExecutionResultEntity.java`                 | **【新增】** 部门执行结果实体        | 部门执行派发结果                              |
| `database/entity/EmployeeExternalAccountEntity.java`                   | **【新增】** 员工外部账号实体        | 员工外部平台账号映射（GitLab/Jira/OpenProject 等）                              |
| `database/entity/InternalReviewEntity.java`                            | **【新增】** 部门内审查实体          | 部门内审查记录                              |
| `database/entity/InterventionDecisionEntity.java`                      | **【新增】** 人工干预决策实体        | 人工干预决策记录                              |
| `database/entity/LedgerTransactionEntity.java`                         | **【新增】** 账本交易实体          | 经济自治账本交易记录                              |
| `database/entity/MessageEntity.java`                                   | **【新增】** 消息实体              | 消息持久化                              |
| `database/entity/NotificationEntity.java`                              | **【新增】** 通知实体              | 通知消息持久化                              |
| `database/entity/PlanApprovalRequestEntity.java`                       | **【新增】** 计划审批请求实体        | 计划审批请求                              |
| `database/entity/RuntimeEventEntity.java`                              | **【新增】** 运行时事件实体          | 运行时事件存储                              |
| `database/entity/ServiceAdminBootstrapStateEntity.java`                | **【新增】** 服务管理启动状态实体    | 服务初始化状态幂等记录                              |
| `database/entity/VisitorEntity.java`                                   | **【新增】** 访客实体              | 访客信息                              |
| `database/entity/WindowsAutomationNodeEntity.java`                     | **【新增】** Windows 自动化节点实体  | Windows 自动化节点注册、心跳                              |
| `database/repository/*Repository.java`                                | Spring Data Repository | 数据访问                                                                                                                                                                            |
| `database/repository/ArtifactRecordRepository.java`                   | Artifact 记录 Repository | 支持按 executionId/department/employee/type 查询，分页和统计；新增 `findByTaskId`/`findByProjectId`                                                                                           |
| `database/repository/CodeReviewStateRepository.java`                 | 代码审查状态 Repository     | 按 taskId/executionId/stage/developer/reviewer 查询审查状态                                                                                                                               |
| `database/repository/DepartmentRepository.java`                       | 部门 Repository          | 按代码/激活状态查询部门                                                                                                                                                                    |
| `database/repository/TenantRepository.java`                           | 租户 Repository          | 租户数据访问                                                                                                                                                                          |
| `database/repository/EnterpriseEmployeeRepository.java`               | 企业员工 Repository        | 员工档案查询                                                                                                                                                                          |
| `database/repository/DepartmentChatMessageRepository.java`            | 部门聊天消息 Repository      | 聊天会话记录查询，新增按 conversationId 查询方法                                                                                                                                                |
| `database/repository/DepartmentConversationRepository.java`           | 部门对话 Repository        | 按 conversationId/ownerUserId+departmentCode+status/tenantId+status 查询长期会话                                                                                                       |
| `database/repository/KnowledgeEntryRepository.java`                   | 知识条目 Repository        | 知识库内容查询                                                                                                                                                                         |
| `database/repository/TaskRepository.java`                             | 任务 Repository          | 支持 findByAssignedToAndStatus/findByUserId/findByTaskKey/findByExecutionId/findByProjectId 等多维度查询；findByExecutionId 返回类型已从 Optional 改为 List                                                                                |
| `database/repository/ProjectRepository.java`                          | 项目 Repository          | 支持 findByTenantId/findByCreatorUserId/findByProjectKey/findByOwnerDepartment 等多维度查询                                                                                             |
| `database/repository/FixedEmployeeDefinitionRepository.java`          | 固定员工定义 Repository      | 按部门/激活状态查询固定员工                                                                                                                                                                  |
| `database/repository/FixedEmployeeProfileRepository.java`             | 固定员工画像 Repository      | 员工画像查询                                                                                                                                                                          |
| `database/repository/FixedEmployeePersonaRepository.java`             | 固定员工外观 Repository      | 员工外观配置查询                                                                                                                                                                        |
| `database/repository/PerformanceAssessmentRepository.java`            | 绩效评估 Repository        | 绩效考核查询                                                                                                                                                                          |
| `database/repository/PerformanceIndicatorRepository.java`             | 绩效指标 Repository        | 绩效指标查询                                                                                                                                                                          |
| `database/repository/PerformanceTrendRepository.java`                 | 绩效趋势 Repository        | 绩效趋势快照查询                                                                                                                                                                        |
| `database/repository/CompensationAccountRepository.java`              | 薪酬账户 Repository        | 薪酬账户查询                                                                                                                                                                          |
| `database/repository/CompensationPlanRepository.java`                 | 薪酬计划 Repository        | 薪酬计划查询                                                                                                                                                                          |
| `database/repository/CompensationRecordRepository.java`               | 薪酬记录 Repository        | 薪酬发放记录查询                                                                                                                                                                        |
| `database/repository/EvolutionFeedbackRepository.java`                | 进化反馈 Repository        | 进化反馈查询                                                                                                                                                                          |
| `database/repository/EvolutionAuditLogRepository.java`                | 进化审计日志 Repository      | 审计日志查询                                                                                                                                                                          |
| `database/repository/EvolutionResultRepository.java`                  | 进化结果 Repository        | 进化结果查询                                                                                                                                                                          |
| `database/repository/WindowsAutomationNodeRepository.java`            | Windows 自动化节点 Repository | 节点注册、心跳、可见性查询                                                                                                                                                                |
| `database/repository/PendingEventRepository.java`                     | 待处理事件 Repository       | JPA 查询                                                                                                                                                                         |
| `database/repository/SessionContextRepository.java`                    | 会话上下文 Repository       | JPA 查询                                                                                                                                                                         |
| `database/repository/TraceEventRepository.java`                       | Trace 事件 Repository      | 按 traceId/stage/eventType/timestamp 查询 Trace 事件；支持按 executionId 关联查询                                                                                                            |
| `database/repository/EmployeeExecutionReceiptRepository.java`          | 员工执行回执 Repository       | 按 executionId/employeeCode/status 查询回执记录；支持统计和分页                                                                                                                               |
| `database/repository/ApprovalInstanceRepository.java`                  | **【新增】** 审批实例 Repository       | 审批实例查询                              |
| `database/repository/ApprovalWorkflowRepository.java`                  | **【新增】** 审批工作流 Repository     | 审批工作流定义查询                              |
| `database/repository/BrainBoundaryAuditRepository.java`                | **【新增】** 大脑边界审计 Repository    | 大脑边界审计查询                              |
| `database/repository/ClientDeviceRepository.java`                      | **【新增】** 客户端设备 Repository      | 客户端设备查询                              |
| `database/repository/ClientOperationAuditLogRepository.java`           | **【新增】** 客户端操作审计 Repository  | 客户端操作审计查询                              |
| `database/repository/ClientUserBindingRepository.java`                 | **【新增】** 客户端用户绑定 Repository  | 客户端用户绑定查询                              |
| `database/repository/ComplianceViolationRepository.java`               | **【新增】** 合规违规 Repository      | 合规违规查询                              |
| `database/repository/DagTaskRepository.java`                           | **【新增】** DAG 任务 Repository    | DAG 任务依赖查询                              |
| `database/repository/DepartmentDeliverableRepository.java`             | **【新增】** 部门交付物 Repository    | 部门交付物查询                              |
| `database/repository/DepartmentExecutionResultRepository.java`         | **【新增】** 部门执行结果 Repository  | 部门执行结果查询                              |
| `database/repository/EmployeeExternalAccountRepository.java`           | **【新增】** 员工外部账号 Repository  | 员工外部账号查询                              |
| `database/repository/InternalReviewRepository.java`                    | **【新增】** 部门内审查 Repository    | 部门内审查查询                              |
| `database/repository/InterventionDecisionRepository.java`              | **【新增】** 人工干预决策 Repository  | 人工干预决策查询                              |
| `database/repository/LedgerTransactionRepository.java`                 | **【新增】** 账本交易 Repository    | 经济自治账本交易查询                              |
| `database/repository/MessageRepository.java`                           | **【新增】** 消息 Repository        | 消息查询                              |
| `database/repository/NotificationRepository.java`                      | **【新增】** 通知 Repository      | 通知消息查询                              |
| `database/repository/PlanApprovalRequestRepository.java`               | **【新增】** 计划审批请求 Repository  | 计划审批请求查询                              |
| `database/repository/RuntimeEventRepository.java`                      | **【新增】** 运行时事件 Repository    | 运行时事件查询                              |
| `database/repository/ServiceAdminBootstrapStateRepository.java`        | **【新增】** 服务管理启动状态 Repository | 服务初始化状态查询                              |
| `database/repository/VisitorRepository.java`                          | **【新增】** 访客 Repository      | 访客信息查询                              |
| `database/repository/SpeakerProfileRepository.java`                    | **【新增】** 声纹档案 Repository    | 声纹档案查询                              |
| `database/service/TenantService.java`                                 | 租户服务                   | 多租户数据                                                                                                                                                                           |
| `database/vector/QdrantVectorService.java` / `QdrantVectorStore.java` | Qdrant 向量服务            | 知识检索、向量写入                                                                                                                                                                       |
| `src/main/resources/db/schema.sql`                                    | 核心 schema（权威源）              | 核心模块表结构定义，所有表结构变更直接修改此文件                                                                                                                          |
| `init-db/01_init.sql`                                                 | Docker 初始化 schema              | Docker 容器启动时初始化数据库，与 schema.sql 保持同步                                                                                                                      |

主要实体分类：

| 分类      | 代表实体                                                                                                       |
| ------- | ---------------------------------------------------------------------------------------------------------- |
| 组织/租户   | `TenantEntity`、`DepartmentEntity`、`EnterpriseEmployeeEntity`                                               |
| 员工/固定员工 | `FixedEmployeeDefinitionEntity`、`FixedEmployeePersonaEntity`、`FixedEmployeeProfileEntity`、`EmployeeEntity` |
| 聊天      | `DepartmentChatMessageEntity`                                                                              |
| 知识      | `KnowledgeEntryEntity`                                                                                     |
| 任务      | `TaskEntity`                                                                                               |
| 项目      | `ProjectEntity`                                                                                            |
| 进化      | `EvolutionResultEntity`、`EvolutionFeedbackEntity`、`EvolutionAuditLogEntity`                                |
| Windows自动化 | `WindowsAutomationNodeEntity`                                                                                   |
| 自治Trace | `TraceEventEntity`                                                                                          |
| 执行回执    | `EmployeeExecutionReceiptEntity`                                                                             |
| 绩效      | `PerformanceAssessmentEntity`、`PerformanceIndicatorEntity`、`PerformanceTrendSnapshotEntity`                |
| 薪酬      | `CompensationAccountEntity`、`CompensationPlanEntity`、`CompensationRecordEntity`                            |
| 配置      | `ConfigVersionEntity`                                                                                      |
| 预算      | `BudgetAllocationEntity`、`BudgetTransactionEntity`                                                         |
| 会话/声纹   | `SessionEntity`、`SpeakerProfile`、`UserProfileEntity`                                                       |

避免重复建议：

- 新增表结构必须新增 `db/migration/V*.sql`，不要只改 `schema.sql`。
- Repository 只做数据访问，业务逻辑放 service。

### 6.12 `workflow` / `planner` / `project` / `approval`

| 包/文件                                                              | 功能说明                          | 修改建议                                |
| ----------------------------------------------------------------- | ----------------------------- | ----------------------------------- |
| `planner/TaskPlanner.java` / `impl/TaskPlannerImpl.java`          | 普通任务规划                        | 简单任务拆解                              |
| `planner/dag/*`                                                   | DAG 任务服务、任务节点、状态              | 复杂任务依赖关系                            |
| `planner/TaskPlan.java`                                           | 任务计划                          | 任务分解和依赖                             |
| `planner/TaskStep.java`                                           | 任务步骤                          | 单步执行定义                              |
| `workflow/WorkflowOrchestrator.java`                              | 工作流总编排                        | 阶段式项目/任务流程                          |
| `workflow/WorkflowExecution.java` / `WorkflowContext.java`        | 工作流执行和上下文                     | 状态追踪                                |
| `workflow/handlers/*Handler.java`                                 | 市场、需求、设计、开发、测试、部署、运维、售后各阶段处理器 | 增加阶段逻辑                              |
| `workflow/handlers/MarketAnalysisHandler.java`                     | **【新增】** 市场分析阶段处理器            | 市场调研、竞品分析                              |
| `workflow/handlers/RequirementAnalysisHandler.java`                | **【新增】** 需求分析阶段处理器          | 需求梳理、可行性评估                              |
| `workflow/handlers/DesignHandler.java`                             | **【新增】** 设计阶段处理器              | 架构设计、UI设计                              |
| `workflow/handlers/DevelopmentHandler.java`                        | **【新增】** 开发阶段处理器            | 代码开发、单元测试                              |
| `workflow/handlers/TestingHandler.java`                            | **【新增】** 测试阶段处理器              | 集成测试、验收测试                              |
| `workflow/handlers/DeploymentHandler.java`                         | **【新增】** 部署阶段处理器            | 环境部署、配置发布                              |
| `workflow/handlers/OperationHandler.java`                          | **【新增】** 运维阶段处理器            | 监控运维、故障处理                              |
| `workflow/handlers/AfterSalesHandler.java`                         | **【新增】** 售后阶段处理器            | 客户支持、问题反馈                              |
| `workflow/WorkflowMonitor.java`                                   | 工作流监控和超时检查                    | 超时、失败、健康检查                          |
| `workflow/monitor/WorkflowStageMonitor.java`                      | P43-A: 工作流阶段监控（闭环43） | 阶段耗时/卡点/超时检测，阈值30min |
| `workflow/monitor/WorkflowOptimizationService.java`               | P43-B: 工作流优化（闭环43） | 基于历史数据动态优化阶段超时阈值 |
| `workflow/HeartbeatProvider.java`                                 | 心跳提供者                          | 工作流心跳                              |
| `workflow/PhaseHandler.java`                                      | 阶段处理器                          | 工作流阶段处理                            |
| `project/ProjectService.java` / `impl/ProjectServiceImpl.java`    | 项目服务                          | 长周期项目管理。已注入 ProjectRepository 同步持久化 |
| `project/Project.java` / `ProjectPhaseRecord.java`                | 项目领域对象                        | 项目字段和阶段                             |
| `project/ProjectPhase.java`                                       | 项目阶段                          | INITIATION/PLANNING/EXECUTION/CLOSING |
| `project/ProjectStatus.java`                                      | 项目状态                          | ACTIVE/ON_HOLD/COMPLETED/CANCELLED   |
| `project/ProjectStatistics.java`                                  | 项目统计                          | 进度/成本/风险统计                          |
| `approval/ApprovalService.java` / `impl/ApprovalServiceImpl.java` | 审批服务                          | 审批流执行                               |
| `approval/plan/*`                                                 | 计划审批服务                        | 高风险计划执行前审批                          |
| `approval/ApprovalInstance.java`                                  | 审批实例                          | 审批流程实例                              |
| `approval/ApprovalRecord.java`                                    | 审批记录                          | 审批操作记录                              |
| `approval/ApprovalStep.java`                                      | 审批步骤                          | 审批流程步骤                              |
| `approval/ApprovalWorkflow.java`                                  | 审批工作流                         | 审批流程定义                              |
| `project/monitor/ProjectHealthMonitor.java`                       | P40-A: 项目健康监控（闭环40） | 偏差阈值20%，HEALTHY/CAUTION/WARNING/CRITICAL |
| `project/monitor/ProjectDeviationDetector.java`                   | P40-B: 项目偏差识别（闭环40） | SEVERE_DEVIATION/INCREASING/STABLE/MINOR模式 |
| `project/retro/ProjectRetroService.java`                          | P40-C: 项目复盘（闭环40） | 自动生成复盘报告+EvolutionSignal沉淀经验 |

避免重复建议：

- “一次对话变成任务/项目”时，不要只保存聊天消息，应接 `planner` / `workflow` / `project`。
- 高风险工具执行前接 `approval`。

### 6.13 `sandbox` 沙箱和执行网关

| 文件                                                     | 功能说明           | 修改建议                                                |
| ------------------------------------------------------ | -------------- | --------------------------------------------------- |
| `SandboxService.java`                                  | 沙箱服务接口         | 统一沙箱能力                                              |
| `impl/DockerSandboxService.java`                       | Docker 沙箱实现    | 容器隔离执行                                              |
| `impl/HybridSandboxService.java`                       | 混合沙箱实现         | 本地/容器/远程混合                                          |
| `SandboxSession.java` / `impl/SandboxSessionImpl.java` | 沙箱会话           | 会话生命周期，支持带 env 参数的 executeCommand 重载                |
| `ExecutionResult.java`                                 | 执行结果           | stdout/stderr/exitCode                              |
| `ClaudeExecutionGateway.java`                          | Claude 执行网关    | 外部 Coding Agent 集成；支持 `executeWithProxy()` 注入代理环境变量；`--mcp-config` MCP Server 注入；动态技能目录注入；服务发现系统提示词；GITLAB_URL/JENKINS_URL/OPENPROJECT_URL 环境变量 |
| `ClaudeCliProperties.java`                             | Claude CLI 配置类 | 配置代理地址、超时、审计开关、MCP 配置路径/开关(mcpConfigPath/mcpEnabled)等                 |
| `ClaudeCliHealthChecker.java`                          | Claude CLI 健康检查 | 检查 CLI 二进制是否安装/可执行/版本兼容；注册到 StartupDependencyChecker |
| `TraeExecutionGateway.java`                            | Trae 执行网关      | Trae 集成                                             |

### 6.13a `proxy/anthropic` Claude CLI 代理

Claude CLI 代理模块将 Anthropic API 格式的请求转换为模型池的 OpenAI-compatible 格式，使 Claude CLI 能使用本地模型运行。

```text
com.livingagent.core.proxy.anthropic/
├── AnthropicMessagesRequest.java       # Anthropic /v1/messages 请求 DTO
├── ClaudeProxyRequestContext.java      # 请求上下文（employeeId/brainId/dept 等）
├── ClaudeProxyModelRouter.java         # 模型路由器，复用 BrainModelSelectorManager
├── ClaudeProxyService.java             # 代理服务主流程（SSE 流式代理）
├── ClaudeProxyAuditService.java        # 审计日志服务
├── converter/
│   └── AnthropicToOpenAiConverter.java # Anthropic → OpenAI 格式转换器
└── sse/
    ├── OpenAiStreamChunkParser.java    # OpenAI SSE 流式解析器
    └── AnthropicSseBuilder.java        # Anthropic 格式 SSE 事件构建器
```

| 文件                                | 功能说明                                                                    | 修改建议                   |
| --------------------------------- | ----------------------------------------------------------------------- | ---------------------- |
| `ClaudeProxyModelRouter.java`     | 按 employeeId → brainId → department → default 路由到模型池 Provider           | 新增路由策略时改这里             |
| `AnthropicToOpenAiConverter.java` | Anthropic messages → OpenAI Chat Completions                            | 新增 Anthropic 内容块类型时改这里 |
| `OpenAiStreamChunkParser.java`    | 解析 OpenAI SSE chunk（content/tool\_calls/finish\_reason）                 | 修改 chunk 解析逻辑时改这里      |
| `AnthropicSseBuilder.java`        | 构建 Anthropic SSE 事件（message\_start/content\_block\_delta/message\_stop） | 修改 SSE 事件序列时改这里        |
| `ClaudeProxyService.java`         | 编排完整代理流程：路由 → 转换 → 流式调用 → SSE 转发                                        | 修改主流程或错误处理时改这里         |
| `ClaudeProxyAuditService.java`    | 审计日志（请求/模型解析/流式事件/完成/失败）                                                | 新增审计字段或持久化时改这里         |

代理流程：

1. **接收** Claude CLI/SDK 的 Anthropic `/v1/messages` 请求
2. **路由** 通过 `ClaudeProxyModelRouter` 解析到模型池 Provider
3. **转换** `AnthropicToOpenAiConverter` 将请求转为 OpenAI 格式
4. **流式** `ClaudeProxyService` 调用 Provider 的 SSE 端点
5. **解析** `OpenAiStreamChunkParser` 解析 OpenAI chunk
6. **构建** `AnthropicSseBuilder` 生成 Anthropic SSE 事件
7. **推送** SSE 事件流返回给 Claude CLI/SDK
8. **审计** `ClaudeProxyAuditService` 记录全流程日志

避免重复建议：

- Claude 代理复用模型池 `BrainModelSelectorManager`，不要新增平行的路由逻辑
- 新增 Anthropic 内容块类型时同步更新 `AnthropicToOpenAiConverter` 和 `AnthropicSseBuilder`
- SSE 事件序列参考 Anthropic 官方文档，保持与 Claude CLI 兼容

### 6.14 `proactive` 主动预判包

| 包/文件                                                                                              | 功能说明                                                         |
| ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `predictor/TimePredictor.java`、`EventPredictor.java`、`PatternPredictor.java`、`RiskPredictor.java` | 时间、事件、模式、风险预判（统计规则层，作为 LLM 层的特征提取）                           |
| `llm/LlmProactiveAdvisor.java`                                                                    | LLM 主动建议接口，基于行为模式和业务上下文生成智能建议                                |
| `llm/LlmRiskAssessor.java`                                                                        | LLM 风险评估接口，结合业务上下文动态评估风险                                     |
| `llm/impl/LlmProactiveAdvisorImpl.java`                                                           | LLM 主动建议实现，通过 `MainBrain.callLlm()` 生成结构化建议；统计规则作为特征输入       |
| `llm/impl/LlmRiskAssessorImpl.java`                                                               | LLM 风险评估实现，通过 `MainBrain.callLlm()` 评估风险等级和影响范围；数值阈值告警作为基础输入 |
| `suggestion/ProactiveSuggestionService.java`                                                      | 主动建议服务                                                       |
| `scheduler/ProactiveTaskScheduler.java`                                                           | 主动任务调度                                                       |
| `cron/CronService.java` / `impl/CronServiceImpl.java`                                             | Cron 定时任务                                                    |
| `event/EventHookManager.java`                                                                     | 事件 Hook 管理                                                   |
| `alert/*`                                                                                         | 告警通知器                                                        |
| `scenario/*`                                                                                      | 周报、入职、会议等主动场景                                                |
| `digest/DailyDigestGenerator.java`                                                                | 日报/摘要生成                                                      |
| `cron/CronJob.java`                                                                               | 定时任务定义                                                       | Cron 表达式和任务配置                          |
| `event/HookEvent.java`                                                                            | 钩子事件                                                         | 事件触发定义                                 |
| `event/HookHandler.java`                                                                          | 钩子处理器                                                        | 事件响应处理                                 |
| `alert/impl/DingTalkNotifier.java`                                                                | 钉钉通知器                                                        | 钉钉机器人告警                                |
| `alert/impl/FeishuNotifier.java`                                                                  | 飞书通知器                                                        | 飞书机器人告警                                |
| `alert/impl/WebhookAlertNotifier.java`                                                            | Webhook 通知器                                                   | 通用 Webhook 告警                          |
| `habit/HabitTrackerCoach.java`                                                                    | 习惯追踪教练                                                       | 用户习惯分析和提醒                              |
| `habit/UserHabitAnalyzer.java`                                                                    | 用户习惯分析器                                                      | 行为模式识别                                 |
| `feedback/ProactiveEffectivenessTracker.java`                                                     | P47-A: 主动服务效果追踪（闭环47） | 采纳率/行为改变率，采纳率<20%触发优化 |
| `feedback/ProactiveStrategyOptimizer.java`                                                         | P47-B: 主动策略优化（闭环47） | 基于采纳率优化建议频率和内容 |
| `meeting/MeetingNotesHandler.java`                                                                | 会议纪要处理器                                                      | 自动会议纪要                                |
| `scheduler/ProactiveTask.java`                                                                    | 主动任务                                                         | 主动任务定义                                 |
| `scenario/EmployeeOnboardingHandler.java`                                                         | 入职场景处理器                                                      | 新员工入职引导                               |
| `scenario/ProactiveWeeklyReportHandler.java`                                                    | 周报场景处理器（原 `WeeklyReportScenarioHandler`，已重命名明确主动预判归属）                                                      | 自动周报生成                                |

### 6.15 `operation` / `diagnosis` / `compliance` / `intervention`

#### `diagnosis` 健康诊断包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `diagnosis/HealthMonitor.java`                  | 健康监控接口         | 系统健康检查              |
| `diagnosis/impl/HealthMonitorImpl.java`         | 健康监控实现         | 定时检查各组件状态；`createIssue()` 根据 componentName 推断 IssueType + `fillSuggestedAction()`；`checkChannels()` 增强为通道级健康检查           |
| `diagnosis/HealthCheck.java`                    | 健康检查项          | 单项检查定义              |
| `diagnosis/HealthStatus.java`                   | 健康状态           | UP/DOWN/DEGRADED     |
| `diagnosis/HealthIssue.java`                    | 健康问题           | 问题描述和修复建议           |
| `diagnosis/HealthAlert.java`                    | 健康告警           | 告警通知                |
| `diagnosis/DegradedTrafficCanary.java`          | 降级小流量回归        | P27-A: 10%探测→成功5次提升/失败3次回滚/300s超时回滚 |
| `diagnosis/impl/StartupRecoveryService.java`    | 启动失败自动恢复       | P12-D: canary探测→recordProbeSuccess→shouldPromote阈值→条件全量恢复 |
| `diagnosis/StartupRecoveryService.java`         | P12-D: 启动恢复服务       | 降级模式下@Scheduled(fixedRate=60000)重试HealthMonitor.checkHealth()，集成DegradedTrafficCanary |
| `diagnosis/DegradedTrafficCanary.java`          | P27-A: 降级小流量回归      | 10%流量探测+5次成功promoteToFull+3次失败rollback+5min超时 |
| `diagnosis/VitalSignsService.java`              | P32-A: 生命体征服务       | 聚合HealthMonitor+连接数+降级模式+JVM内存，支持历史趋势快照 |
| `diagnosis/feedback/ServiceBootstrapHealthTracker.java` | P60-A: 服务初始化追踪（闭环60） | 初始化成功/失败率追踪，失败时触发CrossLoopEventBus |

#### `compliance` 合规管理包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `compliance/ComplianceManager.java`             | 合规管理器          | 合规规则执行              |
| `compliance/ComplianceRule.java`                | 合规规则           | 规则定义和匹配             |
| `compliance/ComplianceReport.java`              | 合规报告           | 审计报告生成              |
| `compliance/ComplianceViolation.java`           | 合规违规           | 违规记录                |
| `compliance/feedback/ComplianceViolationTracker.java` | P45-A: 违规追踪（闭环45） | 违规频率/整改率/重复违规率追踪 |
| `compliance/feedback/ComplianceRuleAutoUpdater.java` | P45-B: 规则自动更新（闭环45） | 重复违规率>30%建议加强拦截 |

#### `intervention` 人工干预包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `intervention/InterventionNeuron.java`          | 人工干预神经元        | 干预决策入口              |
| `intervention/InterventionDecision.java`        | 干预决策           | 决策类型和参数             |
| `intervention/InterventionDecisionEngine.java`  | 干预决策引擎接口       | 决策逻辑抽象              |
| `intervention/impl/InterventionDecisionEngineImpl.java` | 决策引擎实现         | 规则+LLM 混合决策         |
| `intervention/InterventionRule.java`            | 干预规则           | 触发条件定义              |
| `intervention/RiskAssessment.java`              | 风险评估结果         | 风险等级和建议             |
| `intervention/RiskFactor.java`                  | 风险因子           | 单项风险因素              |
| `intervention/RiskAssessmentService.java`       | 风险评估接口         | 风险分析服务              |
| `intervention/impl/RiskAssessmentServiceImpl.java` | 风险评估实现         | 多维度风险计算             |
| `intervention/ImpactAnalyzer.java`              | 影响分析接口         | 操作影响评估              |
| `intervention/impl/ImpactAnalyzerImpl.java`     | 影响分析实现         | 依赖链影响分析             |
| `intervention/feedback/InterventionEffectivenessTracker.java` | P41-A: 干预效果追踪（闭环41） | SUCCESS/FALSE_POSITIVE/MISSED_DETECTION，按ruleId聚合 |
| `intervention/feedback/InterventionRuleOptimizer.java` | P41-B/C: 干预规则优化（闭环41） | 误报率>40%→INCREASE_THRESHOLD，成功率<50%→DECREASE_PRIORITY |

#### `operation` 运营包

| 包/文件                                                    | 功能说明                     | 修改建议                |
| ------------------------------------------------------- | ------------------------ | ------------------- |
| `operation/dashboard`                                   | CEO/企业 Dashboard 数据和 DTO |                     |
| `operation/dashboard/CEODashboard.java`                 | CEO 仪表盘                  | 企业全局概览              |
| `operation/dashboard/CEODashboardService.java`          | CEO 仪表盘服务                | 数据聚合和展示             |
| `operation/dashboard/DashboardDTOs.java`                | 仪表盘 DTO                  | 数据传输对象              |
| `operation/performance`                                 | 绩效评估、指标、趋势、JPA/内存实现       |                     |
| `operation/performance/PerformanceAssessmentServiceImpl.java` | 绩效评估实现                   | 绩效计算和排名；接口已新增 getCompanyTopPerformers/getCompanyBottomPerformers 方法             |
| `operation/performance/PerformanceIndicator.java`        | 绩效指标                     | KPI 定义和计算           |
| `operation/performance/feedback/PerformanceEvaluationCycle.java` | P53-A: 绩效评估闭环（闭环53） | 评估→培训→再评估，低绩效预警+培训效果追踪 |
| `operation/metrics`                                     | 运行指标采集                   |                     |

### 6.16a `ops` 运营支撑包

| 文件                                | 功能说明               | 修改建议                                                                                                                                        |
| --------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `ops/scheduler/TaskCheckout.java` | 任务创建、领取、完成、释放、重派服务 | 已扩展 TaskStatus 枚举（+SUBMITTED/PENDING\_REVIEW/REVIEWED/REJECTED/NEEDS\_REWORK），注入 TaskRepository 同步持久化；新增 `submitTask()` 和 `reviewTask()` 方法 |
| `ops/scheduler/TaskCheckoutSyncService.java` | 任务同步服务 | TaskCheckout 与 TaskRepository 之间的数据同步，确保内存状态与数据库一致 |
| `ops/scheduler/TaskStatus.java`   | 任务状态枚举             | PENDING → CLAIMED → IN\_PROGRESS → SUBMITTED → PENDING\_REVIEW → REVIEWED → COMPLETED → REJECTED → NEEDS\_REWORK                            |
| `ops/queue/RunQueue.java`         | 运行队列               | 任务排队和调度                              |

### 6.16 `autonomous` 赚钱驱动和自主运营

| 包/文件                                               | 功能说明                                 |
| -------------------------------------------------- | ------------------------------------ |
| `autonomous/config/AutonomousOperationConfig.java` | 自主运营配置                               |
| `autonomous/bounty/*`                              | 赏金任务扫描、执行、Ledger、成本估算                |
| `autonomous/bounty/impl/*`                         | GitHub/Freelance/BugBounty 扫描器、复合执行器 |
| `autonomous/bounty/feedback/CreditEconomyMonitor.java` | P54-A: 积分经济监控（闭环54） | 通胀(velocity>2.0)/通缩(velocity<0.3)检测 |
| `autonomous/incentive/*`                           | 激励、积分账户、进化追踪                         |
| `autonomous/payout/*`                              | 支付账户、支付记录、支付服务                       |
| `autonomous/evolution/*`                           | 硬件升级、自主进化管理（注意：`EvolutionManager` 和 `HardwareUpgradeService` 已迁移至 `core/evolution` 包）                          |
| `autonomous/platform/*`                            | 外部平台集成                               |

### 6.17 包命名区分说明：`autonomy` vs `autonomous`

> ⚠️ **重要提示**：这两个包名只差一个 's'，但职责完全不同，请勿混淆

#### 快速对照表

| 维度          | `core/autonomy` (对话自治)                                        | `core/autonomous` (经济自治)                          |
| ----------- | ------------------------------------------------------------- | ------------------------------------------------- |
| **🎯 中文名**  | 对话自治系统                                                        | 经济自治系统                                            |
| **💡 核心关注** | 💬 对话入口的智能处理                                                  | 💰 数字员工的自主生存                                      |
| **👥 服务对象** | 用户对话 / WebSocket 消息                                           | DigitalEmployee 数字员工                              |
| **⚙️ 触发时机** | 每次用户发消息时**实时运行**                                              | 后台定时任务 / 任务完成时                                    |
| **🔧 关键组件** | DialogueAnalyzerConversationOrchestratorAutonomyTraceService  | BountyHunterSkillEvolutionManagerIncentiveManager |
| **📋 典型场景** | 判断"帮我做个网页"是 TASK 类型→ 路由到 TechBrain→ 记录 Trace 日志               | 扫描 GitHub 赎金任务→ 计算成本和利润→ 决定是否升级硬件                 |
| **📊 输出结果** | DialogueDecision（消息类型判断）路由决策（去哪个大脑）Trace 日志（\[AutonomyTrace]） | 收益报告（赚了多少钱）进化计划（该升级什么）支付记录（资金流水）                  |
| **🔗 依赖关系** | 依赖 BrainRegistry、ChannelManager                               | 依赖 GitHub/Jira 等外部平台                              |
| **📦 文件数量** | 6 个文件                                                         | 25 个文件                                            |

#### 为什么分成两个包？

1. **职责完全不同**：
   - `autonomy` 处理的是 **"这条消息该怎么处理"**（交通指挥官）
   - `autonomous` 处理的是 **"怎么让数字员工活下去并进化"**（财务总监）
2. **服务对象隔离**：
   - `autonomy` 面向 **用户交互层**（WebSocket → 大脑）
   - `autonomous` 面向 **数字员工内部**（后台调度）
3. **性能要求不同**：
   - `autonomy` 需要 **低延迟实时响应**（用户等待回复）
   - `autonomous` 可以 **异步批量处理**（后台定时运行）
4. **避免耦合**：
   - 对话分析不应依赖赚钱逻辑（否则聊天会变慢）
   - 经济决策不应阻塞用户交互（否则体验差）

#### 未来可能的协作点（互补而非冲突）

虽然目前两个包完全独立，但在以下场景可以 **协作集成**：

| 场景             | autonomy 角色         | autonomous 角色 | 协作方式                            |
| -------------- | ------------------- | ------------- | ------------------------------- |
| 用户说："帮我做个网站赚钱" | 识别为 TASK+PROJECT 类型 | 评估技术可行性和预估成本  | autonomy 路由 → autonomous 提供评估数据 |
| 任务完成后          | 记录任务执行 Trace        | 结算收益和积分       | autonomy 触发 → autonomous 记账     |
| 员工进化决策         | 分析任务复杂度趋势           | 根据收益决定是否升级硬件  | autonomous 决策 → autonomy 提供历史数据 |

#### 开发时的快速判断方法

当你不确定代码应该放在哪个包时，问自己：

```
❓ 这个功能是处理"用户说了什么"吗？
   ✅ 是 → 放 core/autonomy（对话分析、路由、编排）

❓ 这个功能是处理"员工怎么赚钱/进化"吗？
   ✅ 是 → 放 core/autonomous（赏金、激励、支付、进化）

❓ 这个功能和聊天无关也和赚钱无关？
   ❌ 可能放错了位置，重新考虑职责归属
```

### 6.18 `conversation` / `session` / `runtime` / `migration` / `task` / `work` / `finance/budget` / `tech/config` 其他核心包

#### `conversation` 对话服务包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `conversation/ConversationService.java`         | 对话服务接口         | 对话会话管理              |
| `conversation/ConversationServiceImpl.java`     | 对话服务实现         | 对话会话创建/查询/权限        |
| `conversation/ConversationPermissionService.java` | 对话权限服务         | 对话访问权限检查            |
| `conversation/ConversationStatus.java`          | 对话状态枚举         | ACTIVE/ARCHIVED/DELETED |
| `conversation/feedback/ConversationQualityService.java` | P46-A: 对话质量评估（闭环46） | 解决率/澄清率/满意度评估 |
| `conversation/feedback/ConversationArchiveService.java` | P46-B: 对话归档（闭环46） | 归档时自动沉淀经验到知识库 |

#### `session` 会话管理包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `session/ConnectionContext.java`                | 连接上下文          | WebSocket 连接信息      |
| `session/EventQueueService.java`                | 事件队列服务接口       | 事件排队和消费             |
| `session/SessionPersistenceService.java`        | 会话持久化接口        | 会话状态持久化             |
| `session/impl/EventQueueServiceImpl.java`       | 事件队列实现         | 内存事件队列              |
| `session/impl/SessionPersistenceServiceImpl.java` | 会话持久化实现        | JPA 会话持久化           |

#### `runtime` 运行时包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `runtime/DataNamespaceService.java`             | 数据命名空间服务       | 多租户数据隔离             |
| `runtime/EvolutionNamespaceService.java`        | 进化空间命名空间服务     | .living/ 进化空间路径管理，大脑自由权限 |
| `runtime/RuntimeEventStore.java`                | 运行时事件存储        | 事件溯源                |
| `runtime/StandardComplianceTraceService.java`   | 合规追踪服务         | 操作审计追踪              |

#### `migration` 数据迁移包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `migration/DataMigrationService.java`           | 数据迁移接口         | 版本升级数据迁移            |
| `migration/impl/DataMigrationServiceImpl.java`  | 数据迁移实现         | schema.sql 变更后数据修复      |
| `migration/feedback/MigrationVerificationService.java` | P62-A: 迁移验证（闭环62） | 迁移→验证→回滚→审计闭环 |

#### `task` 任务状态包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `task/TaskStatus.java`                          | 任务状态枚举         | PENDING/RUNNING/COMPLETED/FAILED/CANCELLED |

#### `work` 工作项包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `work/WorkItemContext.java`                     | 工作项上下文         | 工作项执行环境信息           |
| `work/WorkItemKeyGenerator.java`                | 工作项键生成器        | 工作项唯一标识生成           |

#### `finance/budget` 月度预算子包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `finance/budget/MonthlyBudgetManager.java`      | 月度预算管理器        | 月度预算分配和追踪           |

#### `budget` 预算服务包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `budget/BudgetService.java`                     | 预算服务接口         | 预算分配和追踪             |
| `budget/impl/BudgetServiceImpl.java`            | 预算服务实现         | 预算 CRUD             |
| `budget/BudgetAllocationEntity.java`            | 预算分配实体         | JPA 实体              |
| `budget/BudgetAllocationRepository.java`        | 预算分配 Repository | JPA 查询              |
| `budget/BudgetTransactionEntity.java`           | 预算交易实体         | JPA 实体              |
| `budget/BudgetTransactionRepository.java`       | 预算交易 Repository | JPA 查询              |
| `budget/feedback/BudgetHealthMonitor.java`      | P52-A: 预算健康监控（闭环52） | 使用率>90%预警，OVERSPENT/WARNING/HEALTHY |

#### `tech/config` 技术部门配置子包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `tech/config/ConfigVersionManager.java`         | 配置版本管理器        | 技术部门配置版本控制          |

#### `anomaly` 异常检测包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `anomaly/AnomalyDetector.java`                  | 异常检测器          | 异常模式识别              |
| `anomaly/AnomalyContext.java`                   | 异常上下文          | 异常发生时的环境信息          |
| `anomaly/AnomalyResult.java`                    | 异常检测结果         | 异常类型/严重度/建议         |
| `anomaly/feedback/AnomalyDetectionFeedbackLoop.java` | P59-A: 异常检测反馈（闭环59） | 误报率>40%触发模型优化 |

#### `config` 核心配置包

> **2026-06-23 拆分说明**：原 `LivingAgentCoreConfig` 上帝类（54个Bean）已按功能域拆分为5个子配置类。

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `config/LivingAgentCoreConfig.java`             | 核心配置类（已拆分），仅保留 Employee/Security/Autonomy 等 10 个 Bean | Spring Bean 注册和配置   |
| `config/BrainConfig.java`                       | **【新增】** 大脑配置类，注册 BrainRegistry/BrainSessionManager/BrainReActEngine/BrainModelFallback/BrainBoundaryEnforcer 等 Brain 相关 Bean | 大脑相关 Bean 新增或修改时改这里 |
| `config/ToolConfig.java`                        | **【新增】** 工具配置类，注册 ToolRegistry/ToolExecutor/ToolHookManager 及所有 Tool 实现类（含 AdminTool） | 工具注册、新增 Tool 时改这里 |
| `config/ProviderConfig.java`                    | **【新增】** Provider 配置类，注册 ProviderRegistry/ModelManager/NamedPipeModelClient 等模型相关 Bean | 模型/Provider 新增时改这里 |
| `config/MemoryConfig.java`                      | **【新增】** 记忆配置类，注册 MemoryService/MemoryBackend/MemosMemoryBackend/MemPalaceBackend 等 | 记忆后端新增或切换时改这里 |
| `config/ChannelConfig.java`                     | **【新增】** 通道配置类，注册 ChannelManager/ChannelPublisher/BroadcastChannel/UnicastChannel 等 | 通道类型新增或策略调整时改这里 |
| `config/AdminConfig.java`                       | **【新增】** 服务管理配置，通过 `service-admin.enabled=true` 启用，注册 ServiceAdminBootstrap | GitLab/OpenProject/Jenkins 管理类操作启用时改这里 |
| `config/ConfigVersionControl.java`              | 配置版本控制接口       | 配置变更追踪              |
| `config/impl/ConfigVersionControlImpl.java`     | 配置版本控制实现       | Git 风格配置版本管理        |
| `config/ConfigVersionEntity.java`               | 配置版本实体         | JPA 实体              |
| `config/ConfigVersionRepository.java`           | 配置版本 Repository | JPA 查询              |

#### `deployment` 分布式部署包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `deployment/DistributedDeploymentService.java`  | 分布式部署接口        | 节点管理和任务分发           |
| `deployment/impl/DistributedDeploymentServiceImpl.java` | 分布式部署实现        | 基于 Redis 的节点协调      |
| `cluster/feedback/ClusterHealthMonitor.java`    | P58-A: 集群健康监控（闭环58） | 节点注册/健康检查/负载因子/自动重平衡 |

#### `distributed` 分布式基础设施包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `distributed/config/RedisConfig.java`           | Redis 配置       | Lettuce 连接池配置       |
| `distributed/config/KafkaConfig.java`           | Kafka 配置       | Producer/Consumer 配置 |
| `distributed/cache/DistributedCacheService.java` | 分布式缓存服务        | Redis 缓存封装          |
| `distributed/messaging/KafkaMessageService.java` | Kafka 消息服务     | 异步消息收发              |

#### `embedding` 嵌入服务包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `embedding/EmbeddingService.java`               | 嵌入服务接口         | 文本向量化               |
| `embedding/impl/LocalEmbeddingService.java`     | 本地嵌入实现         | 基于 ONNX 的本地推理       |
| `embedding/optimization/VectorIndexOptimizer.java` | 向量索引优化器        | Qdrant 索引优化         |

#### `heartbeat` 心跳服务包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `heartbeat/HeartbeatService.java`               | 心跳服务接口         | 节点存活检测              |
| `heartbeat/impl/HeartbeatServiceImpl.java`      | 心跳服务实现         | 定时心跳和超时检测           |
| `heartbeat/HeartbeatRun.java`                   | 心跳运行记录         | 单次心跳记录              |
| `heartbeat/HeartbeatRunRepository.java`         | 心跳 Repository  | JPA 持久化             |
| `heartbeat/ScheduledWakeup.java`                | 定时唤醒           | Cron 触发的心跳唤醒        |

#### `nativelib` 原生库声明包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `nativelib/NativeLibrary.java`                  | 原生库加载器         | JNI 库路径解析和加载        |
| `nativelib/AudioNative.java`                    | 音频 JNI 声明      | Opus/VAD/Resampler  |
| `nativelib/ChannelNative.java`                  | 通道 JNI 声明      | MPSC/Broadcast      |
| `nativelib/CompactNative.java`                  | 压缩 JNI 声明      | 上下文压缩               |
| `nativelib/MemoryNative.java`                   | 记忆 JNI 声明      | 记忆后端                |
| `nativelib/SecurityNative.java`                 | 安全 JNI 声明      | Bash 验证             |
| `nativelib/NativeCallMetrics.java`             | P19-B: Native调用原子计数器（LongAdder），记录totalCalls/successCalls/failureCalls/totalDurationMs，提供getSuccessRate/getAvgDurationMs | Native调用性能度量 |
| `nativelib/NativePerformanceMonitor.java`       | P19-B: Native性能监控，慢调用告警(>500ms)、高失败率告警(>30%)，getSlowOperations/getUnhealthyOperations/getOverallSuccessRate | Native性能告警 |
| `nativelib/NativeLibraryHealthCheck.java`       | P19-C: Native库健康检查，检查NativeLibrary.isAvailable()+各操作成功率+慢调用，注册到HealthMonitor周期检查 | Native健康监控 |

#### `scenario` 场景处理包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `scenario/ScenarioHandler.java`                 | 场景处理器接口        | 场景匹配和执行             |
| `scenario/ScenarioResult.java`                  | 场景处理结果         | 执行结果和建议             |
| `scenario/impl/EmployeeOnboardingScenarioHandler.java` | 入职场景处理器        | 新员工入职引导             |
| `scenario/impl/WeeklyReportScenarioHandler.java` | 周报场景处理器（旧名，已迁移为 `proactive/scenario/ProactiveWeeklyReportHandler`） | 自动周报生成              |

#### `feedback` 反馈事件包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `feedback/FeedbackEvent.java`                  | 反馈事件接口         | 定义反馈事件类型和元数据        |
| `feedback/FeedbackEventBus.java`               | 反馈事件总线         | 反馈事件发布/订阅/分发，支持异步处理 |
| `feedback/SimpleFeedbackEvent.java`            | 简单反馈事件实现       | 基础反馈事件结构，含类型/来源/内容  |

#### `service` 本地服务包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `service/local/AsrService.java`                 | ASR 服务         | 语音识别封装              |
| `service/local/TtsService.java`                 | TTS 服务         | 语音合成封装              |
| `service/local/LocalModelService.java`          | 本地模型服务         | Qwen3 本地推理          |
| `service/voice/SpeakerVerificationService.java` | 声纹验证服务         | 说话人识别               |
| `service/voice/SpeakerVerificationResult.java`  | 声纹验证结果         | 验证分数和判定             |

#### `util` 工具类包

| 文件/目录                                          | 功能说明           | 修改建议                |
| ---------------------------------------------- | -------------- | ------------------- |
| `util/IdUtils.java`                             | ID 工具类         | 生成 employee:// / neuron:// / channel:// 格式 ID |

### 6.19 `admin` 服务管理包（2026-06-25 新增）

> **关联文档**：`docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md`、`docs/core/MAINBRAIN_SERVICE_MANAGEMENT.md`
>
> **核心职责**：主脑（MainBrain）以管理员身份完成外部服务（GitLab/OpenProject/Jenkins）的一次性初始配置。
> **架构简化**（2026-06-25）：管理员凭据从环境变量读取，不存储在数据库中；管理类操作作为独立的管理工具（GitLabAdminTool/OpenProjectAdminTool/JenkinsAdminTool），实现 Tool 接口，注册到 ToolRegistry。

| 文件/目录                                          | 功能说明                                                                                                            | 修改建议                                             |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `admin/ServiceAdminBootstrap.java`              | 服务初始化入口接口，定义 `bootstrapAll()`/`bootstrapService(serviceType)`/`isServiceInitialized(serviceType)`；返回 `BootstrapResult` | 新增服务初始化入口时改这里                                    |
| `admin/EmployeeExternalAccount.java`            | 员工外部账号映射值对象（employeeCode/serviceType/externalUserId/externalUsername/externalToken）                              | 员工外部账号信息传递                                       |
| `admin/AdminOperationResult.java`               | 管理操作结果值对象（success/operation/entityId/message/detail）                                                             | AdminService 统一返回格式                              |
| `admin/impl/DefaultServiceAdminBootstrap.java`  | 默认服务初始化实现，编排 GitLab/OpenProject/Jenkins 的初始化步骤；幂等设计，通过 `service_admin_bootstrap_state` 表记录状态                          | 新增初始化步骤或调整顺序时改这里                                 |
| `admin/impl/AdminJsonUtils.java`                | JSON 工具类（基于 Jackson），供 AdminService 使用                                                                            | Admin 包内 JSON 处理                                 |
| `config/AdminConfig.java`                       | 服务管理 Spring 配置，通过 `service-admin.enabled=true` 启用；默认不启用；管理员凭据从环境变量读取                                        | 启用/禁用服务管理                                        |
| `tool/impl/admin/GitLabAdminTool.java`          | GitLab 管理工具：createGroup/createProject/createUser/createToken/addGroupMember；实现 Tool 接口，部门 "admin_management"        | GitLab 管理类操作                                     |
| `tool/impl/admin/OpenProjectAdminTool.java`     | OpenProject 管理工具：createRole/createProject/createUser/addMember；实现 Tool 接口，部门 "admin_management"                  | OpenProject 管理类操作                                |
| `tool/impl/admin/JenkinsAdminTool.java`         | Jenkins 管理工具：createJob/createCredential/installPlugin；实现 Tool 接口，部门 "admin_management"                           | Jenkins 管理类操作                                    |


**管理员凭据配置**（环境变量）：

```yaml
# .env 文件
GITLAB_ACCESS_TOKEN=your-gitlab-root-token-here
OPENPROJECT_API_TOKEN=your-openproject-admin-api-key-here
JENKINS_API_TOKEN=your-jenkins-api-token-here

# application.yml
tool:
  gitlab:
    access-token: ${GITLAB_ACCESS_TOKEN:}
  openproject:
    api-token: ${OPENPROJECT_API_TOKEN:}
  jenkins:
    api-token: ${JENKINS_API_TOKEN:}
```

**启用方式**：
```yaml
# application.yml
service-admin:
  enabled: true  # 默认 false，需要时手动启用
```

### 6.20 L4 用户业务闭环反馈服务汇总（2026-07-08 新增）

> **关联文档**：`docs/IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md`、`docs/IMPROVEMENT_PLAN_INDEX.md`
>
> **架构定位**：L4层闭环=用户可感知的业务功能生命周期闭环，通过feedback子包与CrossLoopEventBus实现 feedback→improvement 链路。
> **Bean注册**：所有L4反馈服务在 `gateway/config/GatewayConfig.java` 中注册（共42个Bean：P0×10 + P1×16 + P2×14 + P1通知2个）。

#### P0 核心入口闭环（闭环38-41）

| 闭环 | 包路径 | 服务类 | 功能 |
|------|--------|--------|------|
| 38 | `security/auth/` | AuthMetricsService | 认证指标按method/source聚合，失败率>30%告警 |
| 38 | `security/auth/` | AuthFeedbackService | 认证失败策略自动调整+声纹质量闭环 |
| 39 | `employee/lifecycle/` | AgentLifecycleMonitor | Agent心跳/错误率监控，异常触发事件 |
| 39 | `employee/lifecycle/` | AgentHealthMetrics | Agent级指标采集(uptime/errorCount/avgResponseTime) |
| 39 | `employee/lifecycle/` | AgentAutoRecovery | 自动重启/配置降级/经验沉淀 |
| 40 | `project/monitor/` | ProjectHealthMonitor | 项目进度偏差>20%预警 |
| 40 | `project/monitor/` | ProjectDeviationDetector | 偏差模式识别(SEVERE/INCREASING/STABLE/MINOR) |
| 40 | `project/retro/` | ProjectRetroService | 项目完成自动复盘+经验沉淀 |
| 41 | `intervention/feedback/` | InterventionEffectivenessTracker | 干预效果按ruleId聚合(成功/误报/漏报) |
| 41 | `intervention/feedback/` | InterventionRuleOptimizer | 误报率>40%提高阈值，成功率<50%降低优先级 |

#### P1 运营支撑闭环（闭环42-49）

| 闭环 | 包路径 | 服务类 | 功能 |
|------|--------|--------|------|
| 42 | `skill/feedback/` | SkillEffectivenessTracker | 技能调用成功率/耗时，<80%标记低效 |
| 42 | `skill/feedback/` | SkillRecommendationEngine | 低效技能建议替换，耗时>5s建议优化 |
| 43 | `workflow/monitor/` | WorkflowStageMonitor | 阶段耗时/卡点/超时，阈值30min |
| 43 | `workflow/monitor/` | WorkflowOptimizationService | 阶段超时阈值动态优化 |
| 44 | `notification/feedback/` | NotificationMetricsService | 消息触达率/已读率追踪 |
| 44 | `notification/feedback/` | NotificationStrategyOptimizer | 触达率<50%告警 |
| 45 | `compliance/feedback/` | ComplianceViolationTracker | 违规频率/整改率/重复违规追踪 |
| 45 | `compliance/feedback/` | ComplianceRuleAutoUpdater | 重复违规率>30%建议加强拦截 |
| 46 | `conversation/feedback/` | ConversationQualityService | 对话解决率/澄清率/满意度评估 |
| 46 | `conversation/feedback/` | ConversationArchiveService | 对话归档+知识沉淀 |
| 47 | `proactive/feedback/` | ProactiveEffectivenessTracker | 主动建议采纳率/行为改变率 |
| 47 | `proactive/feedback/` | ProactiveStrategyOptimizer | 采纳率<20%优化策略 |
| 48 | `memory/feedback/` | MemoryConversionTracker | 记忆→知识转化率/引用率追踪 |
| 48 | `memory/feedback/` | MemoryConsolidationService | 转化率<10%建议降低阈值 |
| 49 | `codereview/feedback/` | CodeReviewMetricsService | 审查通过率/返工次数追踪 + 基线评分记录(P49-C) |
| 49 | `codereview/feedback/` | CodeReviewQualityOptimizer | 平均返工>2次建议优化 + 阈值动态调整(P49-C) |
| 49 | `codereview/client/` | FuckUCodeClient | docker exec调用fuck-u-code analyze/ai-review（P49-C） |

#### P2 特定领域闭环（闭环50-63）

| 闭环 | 包路径 | 服务类 | 功能 |
|------|--------|--------|------|
| 50 | `tenant/feedback/` | TenantHealthMonitor | 配额使用率>80%预警+活跃度监控 |
| 51 | `visitor/feedback/` | VisitorConversionTracker | 访客转化率<10%建议优化接待话术 |
| 52 | `budget/feedback/` | BudgetHealthMonitor | 预算使用率>90%超支预警 |
| 53 | `operation/performance/feedback/` | PerformanceEvaluationCycle | 低绩效预警+培训效果追踪 |
| 54 | `autonomous/bounty/feedback/` | CreditEconomyMonitor | 积分通胀/通缩检测 |
| 55 | `social/feedback/` | PlazaEngagementTracker | 广场活跃度<15%建议优化推荐 |
| 56 | `office/feedback/` | OfficeStateSyncMonitor | 办公室同步延迟>30s告警 |
| 57 | `settings/feedback/` | SettingsChangeImpactTracker | 设置回滚率>30%建议加审批 |
| 58 | `cluster/feedback/` | ClusterHealthMonitor | 节点健康/负载/自动重平衡 |
| 59 | `anomaly/feedback/` | AnomalyDetectionFeedbackLoop | 误报率>40%触发模型优化 |
| 60 | `diagnosis/feedback/` | ServiceBootstrapHealthTracker | 服务初始化失败率追踪 |
| 61 | `security/client/feedback/` | ClientDeviceHealthMonitor | 设备异常操作>=5次自动解绑 |
| 62 | `migration/feedback/` | MigrationVerificationService | 迁移→验证→回滚→审计 |
| 63 | `model/proxy/feedback/` | ClaudeProxyMetricsService | 代理成功率/延迟/路由优化 |

#### 尚未在现有包中注册的全新包

| 包路径 | 闭环 | 说明 |
|--------|------|------|
| `tenant/feedback/` | 50 | 租户管理闭环，新包 |
| `visitor/feedback/` | 51 | 接待/访客闭环，新包 |
| `social/feedback/` | 55 | 广场/社交闭环，新包 |
| `office/feedback/` | 56 | 虚拟办公室闭环，新包 |
| `settings/feedback/` | 57 | 系统设置闭环，新包 |
| `cluster/feedback/` | 58 | 分布式部署闭环，新包 |
| `codereview/feedback/` | 49 | 代码审查闭环，新包 |
| `notification/feedback/` | 44 | 消息通知闭环，新包 |

***

## 7. `living-agent-skill` 技能模块

```text
living-agent-skill/
├── src/main/java/com/livingagent/skill/
│   ├── hotreload/    # 技能热重载
│   ├── loader/       # 技能加载
│   ├── model/        # 技能实现模型
│   ├── registry/     # 技能注册表
│   └── service/      # 技能管理服务
└── src/main/resources/skills/       # 内置技能资源
```

| 文件/目录                                                       | 功能说明        | 修改建议          |
| ----------------------------------------------------------- | ----------- | ------------- |
| `loader/SkillLoader.java`                                   | 从资源目录加载技能定义 | 技能加载失败、目录扫描问题 |
| `loader/SkillLoadResult.java`                               | 技能加载结果      | 加载状态和错误信息     |
| `model/SkillImpl.java`                                      | 技能实现        | 技能元数据和执行适配    |
| `model/Skill.java`                                          | 技能模型类       | 技能定义数据模型，与 core 模块的 Skill.java 不同 |
| `registry/SkillRegistryImpl.java`                           | 技能注册表实现     | 技能查找、注册、绑定    |
| `hotreload/SkillHotReloader.java`                           | 技能热重载器      | 运行时技能定义热更新、无需重启 |
| `service/SkillBindingService.java`                          | 技能绑定服务      | 技能与大脑/员工绑定关系管理 |
| `service/SkillService.java`                                 | 技能管理服务      | 技能 CRUD、技能生命周期管理 |
| `src/main/resources/skills/tech/code-review/SKILL.md`       | 技术代码审查技能    | 技术技能内容        |
| `src/main/resources/skills/tech/cicd-pipeline/SKILL.md`     | CI/CD 技能    | DevOps 技能     |
| `src/main/resources/skills/finance/finance-api-gateway/SKILL.md` | 财务 API 网关技能 | ERP（金蝶/用友/SAP）、银行接口、税务系统对接；与 `WindowsAppTool` 配合实现金蝶桌面端自动化 |
| `src/main/resources/skills/sales/sales-automation/SKILL.md` | 销售自动化技能     | 销售场景          |
| `src/main/resources/skills/legal/*/SKILL.md`                | 法务合同/合规技能   | 法务场景          |
| `src/main/resources/skills/cs/customer-portal/SKILL.md`     | 客服门户技能      | 客服场景          |

避免重复建议：

- 业务能力可复用且需要给大脑/员工调用时，优先做成 Skill 或 Tool。
- Skill 文本规则放资源目录，执行能力放 Java Tool/Skill 实现。

***

## 8. `living-agent-native` Rust Native 模块

```text
living-agent-native/src/
├── lib.rs                 # Rust crate 入口，导出 JNI/native 能力
├── audio/                 # Opus、VAD、音频处理、重采样
├── channel/               # Rust 高性能通道（广播/MPSC/消息类型）
├── compact/               # 上下文压缩 native 能力
├── jni/                   # JNI 导出函数
├── knowledge/             # SQLite/向量/知识缓存/相似度
├── memory/                # 记忆后端/条目/查询
└── security/              # 安全校验、bash validator、策略、沙箱
```

| 文件/目录                            | 功能说明                  | Java 对应                                    |
| -------------------------------- | --------------------- | ------------------------------------------ |
| `src/lib.rs`                     | Rust native 库入口       | `core/nativelib/NativeLibrary.java`        |
| `src/jni/audio_jni.rs`           | 音频 JNI 导出             | `AudioNative.java`                         |
| `src/jni/memory_jni.rs`          | 记忆 JNI 导出             | `MemoryNative.java`                        |
| `src/jni/security_jni.rs`        | 安全 JNI 导出             | `SecurityNative.java`                      |
| `src/jni/compact_jni.rs`         | 上下文压缩 JNI 导出          | `CompactNative.java`                       |
| `src/jni/channel_jni.rs`         | 通道 JNI 导出             | 通道 Native 接口                               |
| `src/jni/knowledge_jni.rs`       | 知识 JNI 导出             | 知识 Native 接口                               |
| `src/audio/processor.rs`         | 音频处理                  | 语音链路                                       |
| `src/audio/opus_codec.rs`        | Opus 编解码              | 语音传输                                       |
| `src/audio/vad.rs`               | VAD 语音活动检测            | ASR 前处理                                    |
| `src/audio/resampler.rs`         | 音频重采样                 | 采样率转换、音频格式适配                               |
| `src/channel/broadcast_channel.rs` | 广播通道                 | 高性能广播消息分发                                  |
| `src/channel/message.rs`         | 通道消息类型                | 通道消息定义和序列化                                 |
| `src/channel/mpsc_channel.rs`    | MPSC 通道               | 多生产者单消费者通道                                 |
| `src/knowledge/cache.rs`         | 知识缓存                  | 知识缓存管理                                     |
| `src/knowledge/similarity.rs`    | 相似度计算                 | 向量相似度计算                                    |
| `src/knowledge/sqlite_backend.rs` | SQLite 后端            | 知识持久化存储                                    |
| `src/knowledge/types.rs`         | 知识类型定义                | 知识数据结构                                     |
| `src/knowledge/vector_store.rs`  | 向量存储                  | 向量索引和检索                                    |
| `src/memory/backend.rs`          | 记忆后端                  | 记忆存储后端抽象                                   |
| `src/memory/entry.rs`            | 记忆条目                  | 记忆数据结构                                     |
| `src/memory/query.rs`            | 记忆查询                  | 记忆检索和过滤                                    |
| `src/security/bash_validator.rs` | Bash 命令安全校验           | `BashSecurityValidator` / `SecurityNative` |
| `src/security/policy.rs`         | 安全策略                  | 安全策略定义和评估                                  |
| `src/security/sandbox.rs`        | 沙箱                    | 沙箱隔离执行                                     |
| `src/security/validator.rs`      | 安全验证器                 | 通用安全验证                                     |

避免重复建议：

- Java native 方法名和 Rust JNI 导出签名必须同步。
- Native 加载失败优先查 Dockerfile 是否复制 `.so`、`java.library.path`、依赖库。

***

## 9. `frontend` 前端模块

```text
frontend/
├── package.json
├── vite.config.ts
├── eslint.config.js          # ESLint 9 flat config
├── nginx.conf
├── Dockerfile
├── src/
│   ├── App.tsx
│   ├── pages/             # 页面
│   ├── components/        # 通用组件（含 Toast.tsx/Toast.css）
│   ├── services/          # API 客户端（统一 fetchJson 入口在 api.ts）
│   ├── stores/            # Zustand 状态（含 toastStore.ts）
│   ├── hooks/             # React hooks（含 usePolling.ts）
│   ├── i18n/              # 国际化
│   ├── types/             # TS 类型
│   └── utils/             # 工具函数
└── dist/                  # 构建产物，不建议手改
```

### 9.1 前端入口和基础文件

| 文件                             | 功能说明   | 修改建议            |
| ------------------------------ | ------ | --------------- |
| `src/App.tsx`                  | 前端路由入口 | 新增页面路由时改这里      |
| `src/index.css`                | 全局样式   | 全局主题、布局基础样式     |
| `src/stores/index.ts`          | 全局状态   | 当前用户、认证状态、UI 状态；新增 `getToken()` 辅助函数统一 token 访问 |
| `src/stores/toastStore.ts`     | Toast 状态 | Toast 消息队列管理，`showToast(message, type)` |
| `src/stores/connectionStore.ts` | 连接状态   | WebSocket 连接状态管理（isConnected/quality/heartbeat/reconnectAttempts） |
| `src/hooks/useIdleTimeout.ts`  | 空闲超时   | 自动登出、会话超时       |
| `src/hooks/usePolling.ts`      | 轮询 Hook | 页面可见时启动 setInterval，不可见时暂停；替代固定 30s 轮询 |
| `src/i18n/zh.json` / `en.json` | 国际化文案  | 文案统一维护          |
| `src/types/index.ts`           | 通用类型   | 全局 DTO 类型       |
| `src/utils/theme.ts`           | 主题工具   | 明暗主题、颜色变量       |

### 9.2 前端 API 客户端

| 文件                                  | 功能说明                      | 对应后端                         |
| ----------------------------------- | ------------------------- | ---------------------------- |
| `src/services/apiBase.ts`           | API 基础封装，baseUrl、认证头、错误处理；自动重试（5xx/429/网络错误最多2次） | 所有后端 API                     |
| `src/services/api.ts`               | 通用业务 API，提供统一 `fetchJson` 入口（`apiBase.request` 别名）                  | 多个 Controller                |
| `src/services/errorReporter.ts`     | 前端错误上报，批量发送到 `/api/error-reports`；全局 unhandled error/rejection 捕获 | `ErrorReportController`       |
| `src/services/fixedEmployeeApi.ts`  | 固定员工 API                  | `FixedEmployeeController`    |
| `src/services/dashboardApi.ts`      | Dashboard API             | `DashboardController`        |
| `src/services/modelPoolApi.ts`      | 模型池 API                   | `ModelPoolController`        |
| `src/services/brainModelApi.ts`     | 大脑模型配置 API                | `BrainModelConfigController` |
| `src/services/officeExtendedApi.ts` | 虚拟办公室扩展 API               | `OfficeController`           |
| `src/services/autonomousApi.ts`     | 经济自治 API                  | `AutonomousController`       |

避免重复建议：

- 新页面不要直接 `fetch`，优先在 `services` 增加 API 方法。
- DTO 类型放 `types` 或对应 service 附近，避免页面内重复定义。

### 9.3 页面文件索引

| 页面文件                                             | 功能说明                   | 后端关联                                                                |
| ------------------------------------------------ | ---------------------- | ------------------------------------------------------------------- |
| `pages/Login.tsx`                                | 登录页，手机号验证码、登录跳转        | `PhoneAuthController`、`AuthController`                              |
| `pages/CompanySetup.tsx`                         | 企业初始化                  | `EnterpriseController`、`TenantController`                           |
| `pages/Layout.tsx`                               | 主布局（侧边栏：我的大脑快捷入口+企业频道入口+部门权限过滤）                    | 用户信息、导航、通知                                                          |
| `pages/Dashboard.tsx`                            | 首页 Dashboard           | `DashboardController`                                               |
| `pages/PlatformDashboard.tsx`                    | 平台看板                   | Dashboard/Monitoring                                                |
| `pages/Dashboard/EnterpriseDashboard.tsx`        | 企业 Dashboard 子页面（含系统健康VitalSignsDashboard区块）       | `DashboardController`、`VitalSignsController`                                               |
| `pages/Chat.tsx`                                 | 聊天页面（嵌入 ChannelIndicator 通道状态指示）   | `DepartmentWebSocketHandler`、`AgentWebSocketHandler`                |
| `pages/DepartmentDetail/DepartmentDetail.tsx`    | 部门详情主页面                | 部门、固定员工、Office、聊天                                                   |
| `pages/DepartmentDetail/*`                       | 部门办公室、员工工位、活动流、脑图/状态组件 | `DepartmentController`、`FixedEmployeeController`、`OfficeController` |
| `pages/DepartmentDetail2.tsx`                    | 旧/备用部门详情页              | 如无引用应逐步清理                                                           |
| `pages/AgentCreate.tsx`                          | 创建 Agent               | `AgentApiController`                                                |
| `pages/AgentDetail.tsx`                          | Agent 详情               | `AgentApiController`                                                |
| `pages/MyTasks.tsx`                              | 我的任务                   | `TaskController`、`AgentTaskController`                              |
| `pages/Projects.tsx`                             | 项目管理                   | `ProjectController`                                                 |
| `pages/Approvals.tsx`                            | 审批                     | `ApprovalController`                                                |
| `pages/CodeReview.tsx`                           | 代码审查（3Tab+列表+详情+操作，前端骨架，后端待就绪） | `CollaborationController`                                           |
| `pages/MemoryBrowser.tsx`                        | 记忆管理（统计+搜索+筛选+列表+详情+删除，前端骨架，后端待就绪） | `MemoryController`                                                  |
| `pages/Plaza.tsx`                                | 广场/公开任务                | `PlazaController`                                                   |
| `pages/UserManagement.tsx`                       | 用户/员工管理（详情弹窗+权限级别+激活/停用） | `EmployeeController`、`OrgController`                                |
| `pages/EnterpriseSettings.tsx`                   | 公司设置页面（12 Tab: info/llm/brain/knowledge/tools/skills/users/org/invites/quotas/approvals/audit），含CompanyLogoUploader(Logo上传+本地预览fallback)、KnowledgeTab(知识CRUD+搜索+分类+统计+收藏+文件+治理+状态流转+晋升审核+有效性标记+搜索高亮)、ApprovalsTab(分Tab+筛选+详情+步骤+评论+取消)、AuditLogTab(搜索+分页+导出+详情+时间范围)、SkillsTab(文件+绑定+统计+热更新+自动生成)、CreditOverview(积分+排行榜)、DeptTree(部门CRUD)、OrgTab、工具搜索+部门筛选 | `EnterpriseController`、`SystemSettingsController`、`KnowledgeController`、`ApprovalController`、`DepartmentController` |
| `pages/OpenClawSettings.tsx`                     | OpenClaw 设置            | 系统设置/模型设置                                                           |
| `pages/BrainConfig.tsx`                          | 大脑模型配置                 | `BrainModelConfigController`                                        |
| `pages/ModelPoolProviders.tsx`                   | 模型供应商管理                | `ModelPoolController`                                               |
| `pages/AdminCompanies.tsx`                       | 管理企业                   | 租户/企业接口                                                             |
| `pages/SSOEntry.tsx`                             | SSO 入口                 | OAuth/Auth                                                          |
| `pages/ForgotPassword.tsx` / `ResetPassword.tsx` | 找回/重置密码                | Auth                                                                |

### 9.4 通用组件

| 文件                                       | 功能说明              |
| ---------------------------------------- | ----------------- |
| `components/FileBrowser.tsx`             | 文件浏览器，查看生成产物/项目文件 |
| `components/ChannelConfig.tsx`           | 通道配置组件            |
| `components/DigitalEmployeeSettings.tsx` | 数字员工设置            |
| `components/FixedEmployeeSettings.tsx`   | 固定员工设置            |
| `components/HumanEmployeeSettings.tsx`   | 人类员工设置            |
| `components/EvolvedEmployeeSettings.tsx` | 进化员工设置            |
| `components/PublicTaskBoard.tsx`         | 公开任务板             |
| `components/Toast.tsx` / `Toast.css`    | Toast 提示组件（替代 alert） |
| `components/ChannelIndicator.tsx`       | 通道状态指示组件，支持4种通道类型（dept/enterprise/public/agent），显示通道名称+连接状态 |
| `components/VitalSignsDashboard.tsx`    | 生命体征仪表盘组件，5个卡片（健康分数/内存/连接/组件/运行模式），15秒自动刷新 |

避免重复建议：

- 部门详情相关 UI 优先放 `pages/DepartmentDetail/` 下拆分组件。
- 可跨页面复用的组件放 `components/`。
- `dist/` 是构建产物，不要手改。
- `node_modules/` 不要纳入业务修改。

***

## 10. Python 脚本和模型守护进程

### 10a. 智能前台架构

> **核心概念**：`model_daemon.py` 是独立的"智能前台"服务，所有模型能力都在守护进程内部加载，不依赖外部认证。

**架构分层**：

| 层级 | 模型/组件 | 用途 | 权限 |
|------|----------|------|------|
| **Layer 2** | Qwen3-0.6B | 闲聊神经元，日常对话与快速响应 | 所有用户（含未登录） |
| **Layer 3** | Qwen3.5-2B | 工具神经元，公共工具路由 | 所有用户（含未登录） |
| **ASR** | Sherpa-ONNX | 语音识别 | 所有用户（含未登录） |
| **TTS** | MeloTTS | 语音合成 | 所有用户（含未登录） |
| **声纹** | CAM++ | 声纹识别（可选） | 所有用户（含未登录） |

**权限边界**：

```
┌──────────────────────────────────────────────────────┐
│           智能前台 (model_daemon.py)                  │
│  ├── Qwen3-0.6B（闲聊）                               │
│  ├── Qwen3.5-2B（公共工具路由）                        │
│  ├── Sherpa-ONNX（ASR）                               │
│  ├── MeloTTS（TTS）                                   │
│  └── CAM++（声纹，可选）                              │
│         所有用户（含未登录）无需认证                   │
└──────────────────────────────────────────────────────┘
                        ↓ 需要登录
┌──────────────────────────────────────────────────────┐
│           公司内部 (Java ToolNeuron等)                │
│  员工管理、部门管理、预算管理等内部管理工具            │
│         已登录用户（按权限）                          │
└──────────────────────────────────────────────────────┘
```

**关键实现**：
- 未登录用户 → `/ws/public` → `processPublicChannel` → `agentService.chatPublic()` → `model_daemon.py`
- 已登录用户 → `/ws/dept/{code}` → 部门大脑LLM（绕过ChatNeuronRouter）
- Java `ChatNeuronRouter` 仅用于未登录公共闲聊，注释明确"登录后部门通道应绕过"

---

```text
scripts/
├── python/
│   ├── model_daemon.py             # 模型守护进程，处理 named pipe 控制和会话请求 + OpenAI兼容HTTP(8392)
│   ├── llm/run_qwen35.py           # Qwen3.5 LLM 调用脚本
│   ├── llm/run_qwen3.py            # Qwen3 本地推理服务
│   ├── asr/run_sherpa_ncnn.py      # Sherpa-NCNN ASR 推理服务
│   ├── tts/run_melotts.py          # MeloTTS 语音合成服务
│   ├── speaker/speaker_verifier.py # 声纹验证服务
│   ├── crawl4ai_client.py          # Crawl4AI 网页抓取客户端
│   ├── crawl4ai_usage_guide.py     # Crawl4AI 使用指南
│   ├── install_skills.py           # 技能安装脚本
│   └── models.md                   # 模型说明
├── install-skills.sh               # 技能安装 Shell 脚本
└── windows_automation/             # Windows 桌面应用自动化服务
```

| 文件                          | 功能说明                                                     | 修改建议                            |
| --------------------------- | -------------------------------------------------------- | ------------------------------- |
| `python/model_daemon.py`    | **智能前台核心服务**：包含Qwen3/Qwen35(Sherpa/MeloTTS/CAM++)所有模型；处理named pipe请求和OpenAI兼容HTTP(8392)；未登录用户直接使用，已登录用户通过Java ModelManager调用 | 管道协议、超时、session生命周期必须和Java同步；公共工具路由与公司内部管理分离；LLM HTTP端点与fuck-u-code集成(闭环49) |
| `python/llm/run_qwen35.py`  | Qwen3.5 推理入口                                             | 本地 Qwen 模型路径和推理参数               |
| `python/llm/run_qwen3.py`   | Qwen3 本地推理服务                                             | Qwen3 模型路径和推理参数                |
| `python/asr/run_sherpa_ncnn.py` | Sherpa-NCNN ASR 推理服务                                  | ASR 模型路径、推理参数、语言配置             |
| `python/tts/run_melotts.py` | MeloTTS 语音合成服务                                           | TTS 模型路径、语音参数                   |
| `python/speaker/speaker_verifier.py` | 声纹验证服务                                            | 声纹模型、阈值、向量维度                   |
| `python/crawl4ai_client.py` | Crawl4AI 网页抓取客户端                                         | 抓取目标、解析规则、输出格式                 |
| `python/crawl4ai_usage_guide.py` | Crawl4AI 使用指南                                       | 使用说明和示例                        |
| `python/install_skills.py`  | 技能安装脚本                                                   | 技能源、安装路径                       |
| `python/models.md`          | 本地模型说明                                                   | 模型部署说明                          |
| `install-skills.sh`         | 技能安装 Shell 脚本                                            | Linux/Mac 环境技能安装               |

避免重复建议：

- 模型调用协议改动要同时检查 `NamedPipeModelClient.java` 和 `model_daemon.py`。
- Java 侧不要再另起一套 Python 调用协议。
- **智能前台权限边界**：model_daemon.py只处理公共工具（天气、时间等），公司内部管理（员工、部门、财务）由Java端ToolNeuron处理。
- **ChatNeuronRouter范围**：仅用于未登录公共闲聊，已登录用户的部门通道绕过此路由。

### 10b. Windows 自动化桥接服务 (`scripts/windows_automation/`)

基于 pywinauto + FastAPI 的 Windows 桌面应用自动化服务，支持局域网内多节点集中控制。

```text
living-agent-core/src/main/resources/scripts/windows_automation/
├── server.py                    # FastAPI + pywinauto HTTP 服务端，运行在客户端电脑上
├── config.json                  # 服务端配置（应用路径、后端、安全策略）
├── config.client.example.json   # 客户端配置示例
├── requirements.txt             # Python 依赖（fastapi, pywinauto, Pillow 等）
├── README.md                    # 单机/多节点部署说明和 API 文档
└── MULTI_NODE_DEPLOY.md         # 多节点部署方案（架构图、安全、监控）
```

| 文件                         | 功能说明                                                    | 修改建议                                         |
| -------------------------- | ------------------------------------------------------- | -------------------------------------------- |
| `server.py`                | FastAPI HTTP 服务，提供 `/api/windows/*` 端点供 Java 端调用        | 新增操作类型、修改控件查找逻辑、调整超时                         |
| `config.json`              | 应用配置：`applications` 定义可自动化的应用（如金蝶 KIS）及 exe 路径和 backend | 新增应用、修改路径、切换 win32/uia 后端                     |
| `config.client.example.json` | 客户端部署配置模板                                              | 复制为 config.json 后修改                           |
| `requirements.txt`         | Python 依赖：fastapi, uvicorn, pywinauto, pydantic, Pillow | 版本升级、新增依赖                                    |
| `README.md`                | 部署说明和完整 API 文档                                          | API 变更时同步更新                                  |
| `MULTI_NODE_DEPLOY.md`     | 多节点架构图、部署步骤、安全配置、故障排查                                    | 新增节点、调整安全策略时更新                               |

**架构流程**：

```
AI 大脑 → ToolNeuron → WindowsAppTool (Java, 服务器端)
                              ↓ HTTP API (局域网)
                    server.py (Python, 客户端电脑)
                              ↓ pywinauto
                    Windows 桌面应用 (金蝶 KIS 等)
```

**WindowsAutomationTool（通用系统控制，WebSocket 桥接）**：

```
AI 大脑 → ToolNeuron → WindowsAutomationTool (Java, 服务器端)
                              ↓ WebSocket (WIN_AUTOMATION_CALL)
                    living-agent-desktop (Electron, 客户端电脑)
                              ↓ stdin/stdout (JSON 行协议)
                    service.py (Python, 内嵌子进程)
                              ↓ UIA/PowerShell/psutil/win32api
                    Windows 系统 (控件/进程/注册表/文件系统)
                              ↑ WIN_AUTOMATION_RESPONSE (原路返回)
```

- 与 `WindowsAppTool` 区别：`WindowsAppTool` 走 HTTP 调用远程 pywinauto 服务（业务化封装）；`WindowsAutomationTool` 走 WebSocket 调用桌面端内嵌 Python 服务（通用系统控制）
- 核心接口：`core/websocket/WindowsAutomationClientGateway`（core 定义接口，gateway 实现）
- gateway 实现：`gateway/websocket/WindowsAutomationClientGatewayImpl`（维护 clientId → WebSocketSession 映射）
- 桌面端服务：`living-agent-desktop/src/main/win-automation-service.ts`（管理 Python 子进程）
- Python 服务：`living-agent-desktop/resources/win-automation/service.py`（UIA/PowerShell/注册表/文件系统等）
- 权限分级：CHAT_ONLY/LIMITED/FULL，高风险操作（shell/process_kill/registry_set/delete/filesystem_write/delete）需 BrainBoundaryEnforcer 边界检查（ApprovalManager 已移除）
- 详细设计：`docs/WINDOWS_MCP_INTEGRATION_PLAN.md`

**部署步骤**：

1. **客户端电脑**：复制 `windows_automation/` 目录 → 修改 `config.json` 中的应用路径 → `pip install -r requirements.txt` → `python server.py` → 开放防火墙端口 8765
2. **服务器端**：修改 `WindowsAppTool.java` 中 `initializeDefaultNodes()` 的节点 IP 地址
3. **WindowsAutomationTool**：桌面端安装 `living-agent-desktop` 后自动启动内嵌 Python 服务，无需额外部署（需安装 Python 依赖：`pip install -r resources/win-automation/requirements.txt`）

**与 `docker/pywinauto/` 项目的关系**：

- `docker/pywinauto/` 是独立的 pywinauto 开发/测试项目，包含金蝶自动化脚本（`kingdee_automation/`）
- `scripts/windows_automation/` 是将 pywinauto 能力集成为服务化架构的版本，通过 FastAPI 提供 HTTP API
- 集成版已完全覆盖独立版功能，且增加了多节点远程控制能力
- 两者配置格式不同：独立版用 `accounts` 数组，集成版用 `applications` 对象

避免重复建议：

- 不要在 `docker/pywinauto/kingdee_automation/` 和 `scripts/windows_automation/` 中维护两套代码，统一使用 `scripts/windows_automation/`。
- `WindowsAppTool.java` 的 API 路径和 `server.py` 的端点必须同步修改。
- 新增可自动化的 Windows 应用时，在 `config.json` 的 `applications` 中添加配置即可，无需改 Java 代码。

### 10.3 桌面端模块更新（P0-7 多通道WebSocket + P1-1/P1-2 Agent/干预管理 + P1-3/P1-4/P1-5 技能/主动/广场 + P1-7 固定员工防护 + P1-8 部门权限校验）

| 文件 | 功能说明 | 修改建议 |
| --- | --- | --- |
| `src/main/ws-client.ts` | WebSocket客户端，支持4种通道（AGENT/DEPT/ENTERPRISE/PUBLIC），switchChannel方法，固定员工(origin=fixed)直连防护 | 新增通道或修改重连逻辑时改这里 |
| `src/main/ipc.ts` | IPC处理器，包含ws + agent + intervention + skill + proactive + plaza 全部通道 | 新增IPC通道时改这里 |
| `src/main/api-client.ts` | REST API客户端，含Agent管理、干预、技能、主动服务、广场API | 新增API时改这里 |
| `src/shared/api-types.ts` | LivingAgentAPI类型契约，包含ws/agent/intervention/skill/proactive/plaza命名空间 + EmployeeOrigin类型 | 新增API类型时改这里 |
| `src/preload/index.ts` | Preload脚本，暴露ws/agent/intervention/skill/proactive/plaza IPC通道 | 新增暴露时改这里 |
| `src/renderer/App.tsx` | 桌面端React根组件，含闲聊+企业频道+智能体+干预+技能+主动服务+广场导航 | 新增View时改这里 |
| `src/renderer/pages/OfficeChat/OfficeChatPage.tsx` | 办公室聊天页，支持forceChannel prop + 部门选择器权限过滤 | 修改聊天行为时改这里 |

***

## 11. Docker 与部署文件

| 文件/目录                          | 功能说明                                                           | 修改建议                           |
| ------------------------------ | -------------------------------------------------------------- | ------------------------------ |
| `docker-compose.yml`           | 本地整体编排：后端、前端、PostgreSQL、Redis、Kafka、Qdrant、Memos、OpenProject、fuck-u-code 等；已拆分为 frontend/backend 双网络隔离；基础设施服务已添加内存限制；Kafka 已禁止自动创建 Topic；数据库密码强制环境变量注入；fuck-u-code 使用模型守护进程8392端口Qwen3.5-2B | 服务端口、依赖、healthcheck、环境变量       |
| `.dockerignore`                | Docker 构建忽略                                                    | 排除 target/node\_modules/logs 等 |
| `.gitignore`                   | Git 忽略                                                         | 避免提交构建产物和敏感信息                  |
| `Dockerfile.rust-native`       | Rust native 构建镜像                                               | 构建 `.so`                       |
| `image/Dockerfile.system-deps` | 系统依赖镜像                                                         | 离线/基础依赖                        |
| `image/download_images.py`     | 镜像下载辅助（含 fuck-u-code 本地源码构建）                                      | 离线镜像准备                         |
| `image/load_images.ps1`        | Windows 加载镜像脚本                                                 | 本地导入镜像                         |
| `image/codegraph/codegraph-linux-x64.tar.gz` | CodeGraph v1.2.0 Linux x64 预编译二进制；Dockerfile.local 中 COPY 并解压到 /opt/codegraph，链接到 /usr/local/bin/codegraph | 离线部署 CodeGraph 代码索引工具         |
| `init-db/01_init.sql`          | 初始化数据库                                                         | 初次启动基础表/数据                     |
| `init-db/02_openproject.sh`    | OpenProject 初始化                                                | OpenProject 数据库初始化             |

避免重复建议：

- 容器健康检查问题先改 `docker-compose.yml` 的 `healthcheck`。
- 环境变量优先放 compose，不要写死到代码。
- 敏感信息不要写入文档或 Git。

***

## 12. 文档和制度目录

### 12.1 `docs/` 技术文档

| 目录/文件                                         | 功能说明               |
| --------------------------------------------- | ------------------ |
| `docs/README.md`                              | 文档入口               |
| `docs/DOCKER_SERVICE_LOG_ISSUES_AND_FIXES.md` | Docker 服务日志问题和解决方案 |
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`       | 本文档，代码结构和文件功能索引    |
| `docs/planning/`                              | 规划和改进方案            |
| `docs/analysis/`                              | 分析文档               |
| `docs/adr/`                                   | 架构决策记录             |
| `docs/old/`                                   | 旧版文档归档             |
| `docs/guides/`                                | 操作指南               |
| `docs/references/`                            | API 参考             |
| `docs/对话入口逻辑梳理.md`                            | 对话入口和链路梳理          |
| `docs/权限与入口矩阵.md`                             | 权限和入口矩阵            |

### 12.2 `documents/` 企业知识源

| 目录                              | 功能说明                 |
| ------------------------------- | -------------------- |
| `documents/_meta/`              | 命名规范、分类、访问规则         |
| `documents/shared/company/`     | 公司级制度、数字员工职责卡、需求路由规则 |
| `documents/shared/governance/`  | 治理制度                 |
| `documents/department/tech/`    | 技术部制度、流程、模板、runbook  |
| `documents/department/hr/`      | 人力资源制度、流程、记录、模板      |
| `documents/department/finance/` | 财务制度、报表、流程、模板        |
| `documents/department/sales/`   | 销售制度、提案、流程、模板        |
| `documents/department/cs/`      | 客服制度、话术、流程、模板        |
| `documents/department/legal/`   | 法务制度、合同、流程、模板        |
| `documents/department/ops/`     | 运营制度、清单、流程、模板        |

避免重复建议：

- 部门大脑 Prompt 或知识应优先引用 `documents`，不要把制度写死在 Java 代码里。
- 数字员工职责优先维护 `documents/shared/company/*fixed-employee-duty-card*` 或数据库固定员工定义。

***

## 13. 常见修改场景定位表

| 想改什么             | 首选文件/目录                                                                                                       | 不建议位置                        |
| ---------------- | ------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| 部门聊天消息流程         | `DepartmentWebSocketHandler.java`、`DepartmentChatService.java`、`core/autonomy`                                | `AgentService`、`Qwen3Neuron` |
| 部门大脑回复逻辑         | `core/brain/impl/*Brain.java`、`AbstractBrain.java`、`DynamicPromptBuilder.java`                                | Controller                   |
| 对话是否任务/路由到哪个部门   | `core/autonomy/DialogueAnalyzer.java`、`ConversationOrchestrator.java`、`BrainRegistryImpl.java`                | 前端或 WebSocket Handler        |
| 部门大脑分派数字员工       | `core/brain/collaboration`、`core/employee`、`core/worker`、`core/planner/dag`                                   | Controller                   |
| 普通 Agent/语音会话    | `AgentService.java`、`AgentWebSocketHandler.java`、`NeuronCoordinator.java`、`NamedPipeModelClient.java`         | `DepartmentChatService`      |
| 模型池/Provider     | `core/model/pool`、`core/provider`、`ModelPoolController.java`                                                  | Brain 内硬编码                   |
| 大脑模型选择           | `core/model/selector`、`BrainModelConfigController.java`                                                       | `application.yml` 硬编码每个大脑    |
| 工具调用             | `core/tool`、`ToolRegistryImpl.java`、`DefaultToolExecutor.java`                                                | Brain 里直接 HTTP 调用            |
| 外部企业平台           | `core/tool/impl/enterprise`、对应 Controller/Service                                                             | 新建散乱 util                    |
| 权限问题             | `core/security`、`gateway/interceptor`、`SecurityConfig.java`                                                   | 页面里只隐藏按钮                     |
| 员工/组织            | `core/employee`、`database/entity`、`OrganizationQueryService`                                                  | 前端假数据                        |
| 固定数字员工           | `FixedEmployeeRegistry.java`、`FixedEmployeeController.java`、`FixedEmployee*Entity`、`documents/shared/company` | 各部门 Brain 内硬编码               |
| 任务/项目            | `core/planner`、`core/workflow`、`core/project`、`TaskController`、`ProjectController`                            | 只保存聊天记录                      |
| 审批               | `core/approval`、`ApprovalController.java`                                                                     | 工具里直接跳过                      |
| 绩效/贡献            | `core/operation/performance`、`TaskPerformanceBridgeService.java`                                              | Controller 里临时统计             |
| 知识库              | `core/knowledge`、`KnowledgeController.java`、`documents`                                                       | prompt 字符串写死                 |
| 长期记忆             | `core/memory`                                                                                                 | 聊天表临时拼接                      |
| 进化系统             | `core/evolution`、`EvolutionAdminController.java`                                                              | 定时任务里临时逻辑                    |
| Docker unhealthy | `docker-compose.yml`、对应服务 Dockerfile                                                                          | Java 业务代码                    |
| Native 加载失败      | `living-agent-native`、`core/nativelib`、`Dockerfile`、`entrypoint.sh`                                           | 业务 Service 捕获后忽略             |
| 前端页面             | `frontend/src/pages`                                                                                          | `dist`                       |
| 前端 API 调用        | `frontend/src/services`                                                                                       | 页面组件内散落 fetch                |
| 前端类型             | `frontend/src/types`                                                                                          | 多页面重复定义                      |

***

## 14. 重点链路说明

### 14.1 部门文本聊天推荐链路

```text
frontend/src/pages/Chat.tsx
  -> WebSocket /ws/dept/{dept}
  -> DepartmentWebSocketHandler
  -> DepartmentChatService
  -> ConversationOrchestrator
  -> DialogueAnalyzer
  -> MainBrainTaskDirector / MainBrainTaskPlan（任务类需求）
  -> BrainRegistry / BrainRouter / BrainRoutingDecision
  -> FixedEmployeeDispatcher / EmployeeWorkAssignment（计划级固定员工分派建议）
  -> AssignmentPreparationService / PreparedAssignmentBatch（任务单准备层）
  -> DepartmentExecutionCoordinator / EmployeeExecutionDispatch（员工任务通道派发）
  -> EmployeeExecutionReceiptService / EmployeeExecutionReceipt（第一版执行回执与完成态）
  -> Department Brain
  -> DepartmentWorkCoordinator / EmployeeDispatcher / TaskPlanner（生产级真实执行阶段，后续继续接入）
  -> ResultAggregator
  -> DepartmentChatService 保存 user + assistant
  -> WebSocket 返回前端
```

注意：

- 文本部门聊天不应无条件进入 `AgentService.startSession()`。
- 文本部门聊天不应无条件绑定 `Qwen3Neuron`。
- 用户消息落库应统一由 `DepartmentChatService` 负责，避免重复保存。

### 14.2 通用 Agent/语音链路

```text
frontend Chat/Voice
  -> /ws/agent 或对应 Agent WebSocket
  -> AgentWebSocketHandler
  -> AgentService.startSession
  -> NeuronCoordinator.createSession
  -> Qwen3Neuron / BitNetNeuron / ASR / TTS / NamedPipeModelClient
  -> Python model_daemon.py
  -> 返回文本/音频
```

注意：

- 这是通用 Agent/语音链路，不等于部门业务大脑链路。

### 14.3 模型调用链路

```text
Brain / Neuron
  -> Provider / ResolvedBrainModelProvider
  -> ModelManager / NamedPipeModelClient 或 OpenAI/Ollama compatible client
  -> scripts/python/model_daemon.py 或外部 API
  -> ModelResponse
```

### 14.4 任务变项目链路

```text
ConversationOrchestrator 判断 TASK/PROJECT
  -> TaskPlanner / TaskDagService
  -> DepartmentWorkCoordinator 分工
  -> TaskWorkflowService / WorkflowOrchestrator
  -> ProjectService 创建长期项目
  -> ApprovalService 处理高风险审批
  -> PerformanceAssessmentService 记录贡献
  -> KnowledgeManager / MemoryService 沉淀经验
```

### 14.5 智能前台闲聊闭环（2026-07-14 验证）

```text
未登录用户闲聊闭环：
  frontend Chat.tsx（无参数）
  -> /ws/public
  -> DepartmentWebSocketHandler.processPublicChannel()
  -> agentService.chatPublic(content, userId)
  -> modelManager.chatAsync("qwen3-0.6b", message)   [service="llm_chat"]
  -> NamedPipe → model_daemon.py llm_chat
  -> generate_chat_response()（含意图识别+快速响应）
  -> llama-server HTTP(8393/8394) → 响应文本
  -> 返回 WebSocket → 前端

未登录用户语音闭环：
  frontend 语音录制
  -> /ws/public "audio_full"
  -> DepartmentWebSocketHandler.processPublicAudioChannel()
  -> agentService.chatPublicAudio(audioData, userId)
  -> modelManager.createSession() + processAudioFullChain()
  -> ASR(Sherpa-ONNX) → LLM(Qwen3) → TTS(MeloTTS)
  -> 返回音频 + 文本 → 前端

已登录用户部门闭环：
  frontend Chat.tsx（带brain参数）
  -> /ws/dept/{brainId}
  -> DepartmentWebSocketHandler.processWithBrain()
  -> 直接调用部门大脑LLM（绕过ChatNeuronRouter）
  -> 返回 WebSocket → 前端
```

验证结论：闭环完整 ✅
- `chatPublic()` 使用 `service="llm_chat"` 走 `generate_chat_response()` 含意图识别
- `ChatNeuronRouter` 仅用于未登录公共闲聊，已登录用户绕过
- 降级：`chatPublic()` 有 `exceptionally()` 兜底返回"抱歉，我暂时无法回复"

### 14.6 LLM-first/Rule-fallback 降级链路验证（2026-07-14 验证）

| 降级链路 | LLM优先 | 规则降级 | 验证结果 |
|---------|---------|---------|---------|
| 对话分析 | `LlmBasedDialogueAnalyzer` | `RuleBasedDialogueAnalyzer` | ✅ MainBrain不可用/LLM失败/解析失败均调用 `fallbackAnalyzer.analyze()` |
| 员工分派 | `LlmBasedFixedEmployeeDispatcher` | `RegistryBackedFixedEmployeeDispatcher` | ✅ MainBrain不可用/LLM失败/响应过短均调用 `fallbackDispatcher.planAssignments()` |
| 响应协调 | `LlmBasedFinalResponseCoordinator` | `DefaultFinalResponseCoordinator` | ✅ MainBrain不可用/LLM空/解析失败/未知策略均调用 `fallbackCoordinator.determineStrategy()` |
| 主脑任务 | `LlmBasedMainBrainTaskDirector` | `RuleBasedMainBrainTaskDirector` | ✅ LLM不可用时降级到规则版 |
| 执行能力 | `LlmBasedExecutionCapabilityResolver` | `DefaultExecutionCapabilityResolver` | ✅ LLM不可用时降级到规则版 |

验证结论：所有降级链路完整，fallback 均为真正执行（非空实现） ✅

***

## 15. 避免重复实现的开发规则

1. **Controller 只做入口和参数转换**：业务逻辑放 `gateway/service` 或 `core`。
2. **部门业务逻辑放 Department Brain，不放 WebSocket Handler**。
3. **对话分析、路由、分工统一放** **`core/autonomy`** **和** **`core/brain/collaboration`**。
4. **模型选择不要硬编码，优先走** **`model/pool`** **和** **`model/selector`**。
5. **外部系统调用统一做成 Tool，不要散落 HTTP 客户端**。
6. **权限统一走** **`security`** **包，不要只在前端隐藏入口**。
7. **员工模型优先使用** **`core.employee`，不要和** **`core.security.SecurityIdentity`（原 Employee）** **重复扩展**。
8. **新增表必须修改 schema.sql（核心模块）+ 01_init.sql（Docker 初始化），不再创建 Flyway V 版本迁移文件**。
9. **前端请求统一放** **`src/services`，不要页面里到处写** **`fetch`**。
10. **`dist/`、`target/`、`node_modules/`、Rust** **`target/`** **都是构建产物，不手改**。
11. **文档型知识放** **`documents/`，技术方案放** **`docs/`，不要混在代码里**。
12. **Native 能力改动要 Java** **`nativelib`** **和 Rust** **`jni`** **同步修改**。
13. **部门文本链路和语音/通用 Agent 链路保持分离**。
14. **一次对话如果是任务，应进入 planner/workflow/project，而不是只保存一条 assistant 回复**。

***

## 16. 建议清理和统一点

以下是为了后续避免重复和歧义，建议逐步统一的点：

| 问题                                                                                        | 建议                                                                                                                                                                  |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core.employee.Employee` 与 `core.security.SecurityIdentity`（原 `Employee`）命名已区分                | 安全身份已重命名为 `SecurityIdentity`，避免与领域员工混淆；DTO 命名不再重复                                                                                                                        |
| `AuthEmployeeServiceImpl`（原 `EmployeeServiceImpl`）与 `JpaEmployeeServiceImpl` 并存                | 明确 profile/条件装配，文档说明当前使用哪个                                                                                                                                          |
| `DepartmentDetail2.tsx` 与 `DepartmentDetail/DepartmentDetail.tsx` 并存                      | 确认旧页面是否废弃，避免两套部门 UI 同时维护                                                                                                                                            |
| Controller 较多且部分职责重叠                                                                      | 新接口优先复用现有 Controller，必要时按领域合并                                                                                                                                       |
| 文档中有 old/current/planning 多版本                                                             | 设计定稿后迁移到 `docs/README.md` 索引，旧版归档                                                                                                                                   |
| 部门聊天链路和 Agent 链路曾混用                                                                       | 固化链路边界：部门文本走自治/部门大脑，语音/通用 Agent 走 AgentService                                                                                                                      |
| 固定员工定义既有代码又有文档又有数据库                                                                       | 明确数据库为运行态真源，documents 为制度源，Registry 负责启动兜底                                                                                                                          |
| `core/autonomy` 与 `core/autonomous` 包名相似                                                  | 已在 **6.17 节** 详细说明区分：autonomy=对话自治，autonomous=经济自治                                                                                                                  |
| `MinimalEmployeeTaskExecutor` 已被移除                                                        | 已由 `DynamicEmployeeTaskConsumerRegistry` 替代，`GatewayConfig` 中已移除注册，类文件保留用作参考                                                                                        |
| `InMemoryEmployeeExecutionReceiptService` 已升级为 `FileBasedEmployeeExecutionReceiptService` | `FileBasedEmployeeExecutionReceiptService` 为生产默认实现，支持 JSON 文件持久化                                                                                                    |
| `RuleBasedDialogueAnalyzer` 已升级为 `LlmBasedDialogueAnalyzer`                               | `LlmBasedDialogueAnalyzer` 为生产默认实现，LLM 驱动分析含降级兜底                                                                                                                    |
| `RuleBasedMainBrainTaskDirector` 已补齐全部 8 个部门                                              | `createDepartmentPlan()` 覆盖 tech/finance/legal/hr/sales/cs/ops/admin                                                                                                |
| `RuleBasedMainBrainTaskDirector` 交付物/验收标准/关键词全部覆盖                                         | `detectDeliverables()`/`detectAcceptanceCriteria()` 覆盖 10+ 任务类型；`detectTaskType()` 含各部门关键词                                                                          |
| `DynamicEmployeeTaskConsumerRegistry` 已支持派发的全部任务类型                                        | `executeTask()` switch 从 4 种扩展到 14 种任务类型                                                                                                                            |
| `DefaultMainBrainResponseComposer` 增强输出                                                   | 用户响应附带任务目标、类型、员工代码、交付物清单、验收标准、状态标识                                                                                                                                  |
| `LlmBasedFixedEmployeeDispatcher` 替代 `RegistryBackedFixedEmployeeDispatcher` 为默认实现        | LLM 根据员工能力/负载/绩效动态选人，规则版降级为 fallback                                                                                                                                |
| `LlmBasedFinalResponseCoordinator` 替代 `DefaultFinalResponseCoordinator` 为默认实现             | LLM 动态选择回复策略（7种），规则版降级为 fallback                                                                                                                                    |
| `LlmBasedMainBrainResponseComposer` 替代 `DefaultMainBrainResponseComposer` 为默认实现           | LLM 根据执行结果动态生成自然语言回复，模板版降级为 fallback                                                                                                                                |
| `ChatIntentClassifier` 支持 LLM-first / Rule-fallback                                       | 优先使用 `DialogueAnalyzer` 语义分类，LLM 不可用时降级到关键词规则                                                                                                                       |
| `FinalResponseStrategy` 枚举扩展                                                              | 从 2 种扩展到 7 种：DIRECT\_ANSWER / ASK\_CLARIFICATION / MAIN\_BRAIN\_COMPOSE / WAIT\_FOR\_RECEIPTS / DEPARTMENT\_BRAIN\_DIRECT / ESCALATE\_TO\_HUMAN / REQUEST\_APPROVAL |
| `LlmProactiveAdvisor` / `LlmRiskAssessor` LLM 主动建议和风险评估                                   | 统计规则作为特征提取层，LLM 层生成语义化建议和风险评估                                                                                                                                       |
| `LLMEmployeeCreationService` LLM 驱动的动态员工创建                                                | 新员工创建由 LLM 自主判断，确保专属名字/编号/能力/职责                                                                                                                                     |
| `DigitalEmployeeEntity` 新增 `origin` 字段                                                    | 区分 FIXED（32固定员工）和 EVOLVED（LLM自主创建的动态员工）                                                                                                                             |
| 固定员工 ID 生成修复                                                                              | `FixedEmployeeRegistry.createFixedEmployee` 使用 `IdUtils.neuronToEmployeeId()` 生成确定性 ID，避免重启重复创建                                                                     |
| Dashboard 统计公式修复                                                                          | `digital`/`human` 按 `isDigital()`/`isHuman()` 属性分别计数，不再用 `total - digital`                                                                                          |
| `LlmBasedDialogueAnalyzer` Prompt 动态化                                                     | 从 `FixedEmployeeRegistry` 动态注入部门/员工信息，移除硬编码默认值；增加 `requiresClarification`/`riskReasons` 字段                                                                          |
| `LlmBasedMainBrainTaskDirector` Prompt 动态化                                                | 从 `FixedEmployeeRegistry` 动态注入员工画像；移除 `tech`/`T02/T09` 默认值；`departmentPlans` 为空时使用当前部门而非硬编码 `tech`                                                                  |
| `LlmBasedExecutionResultAggregator` + `ExecutionReceiptReviewer`                          | LLM 汇总执行结果 + 审核回执质量，输出质量分和未满足项                                                                                                                                      |
| `HybridContextCompactor`                                                                  | 程序提取 + LLM 语义压缩；规则层先做结构化裁剪，LLM 层再做语义摘要                                                                                                                              |
| `AssignmentReadinessEvaluator` + `LlmAssignmentReadinessEvaluator`                        | LLM 评估任务分派准备度（READY/BLOCKED/NEEDS\_CLARIFICATION/PARTIALLY\_READY）                                                                                                  |
| `TaskEntity` / `ProjectEntity` 持久化实体                                                      | 任务和项目统一身份字段（userId/tenantId/taskKey/projectKey/executionId），替换内存 Map 存储                                                                                             |
| `TaskRepository` / `ProjectRepository`                                                    | 多维度查询支持（findByUserId/findByTaskKey/findByProjectId 等）                                                                                                               |
| `TaskStatus` 枚举扩展                                                                         | 从 5 种扩展到 10 种：PENDING/CLAIMED/IN\_PROGRESS/SUBMITTED/PENDING\_REVIEW/REVIEWED/COMPLETED/REJECTED/NEEDS\_REWORK/FAILED                                               |
| `TaskCheckout` 持久化同步                                                                      | 注入 TaskRepository，关键操作同步持久化；新增 `submitTask()` 和 `reviewTask()` 方法                                                                                                   |
| `ProjectServiceImpl` 持久化同步                                                                | 注入 ProjectRepository，关键操作同步持久化                                                                                                                                      |
| `AgentTaskController` 已废弃                                                                 | 标记 `@Deprecated`，所有任务操作统一使用 `TaskController` 的 `/tasks` 路由                                                                                                          |
| 前端 `taskApi` 统一                                                                           | list/create/update/getLogs/trigger 改为调用 `/tasks` 路由                                                                                                                 |
| 项目任务子资源真实接入                                                                               | `/projects/{projectId}/tasks` 接入真实 TaskRepository，不再是 stub                                                                                                          |
| `WorkItemContext` / `WorkItemKeyGenerator`                                                | 统一 project/task/execution 上下文和 key 生成（`core/work/`）                                                                                                                 |
| `DataNamespaceService` / `RuntimeEventStore`                                              | 标准 data 路径生成和运行时事件存储（`core/runtime/`），注册为 Spring Bean                                                                                                               |
| `StandardComplianceTraceService`                                                          | 规范合规追踪服务（`core/runtime/`），记录边界检查/标准加载/澄清/升级/回执合规/权限检查事件                                                                                                             |
| `ConnectionRegistry` / `InMemoryConnectionRegistry`                                       | WebSocket 连接注册，映射 userId/taskKey/executionId/projectKey（`gateway/websocket/`）                                                                                       |
| `ConnectionHealthCheck`                                                                    | P24-C+P3-A: WebSocket连接健康检查，检测5分钟DEGRADED/30分钟UNHEALTHY，发布HealthIssue触发自愈（`gateway/websocket/`）                                                                 |
| `WorkItemPermissionService`                                                               | 统一判断项目/任务访问权限，接受 `AuthContext` 参数（`gateway/security/`）                                                                                                              |
| `RequireAccess` / `RequireAccessAspect`                                                  | 统一权限检查注解 + AOP 切面（`gateway/security/`），替代 Controller 中零散的 `accessGateService.canRoute()` 调用                                                                                     |
| `WorkItemContextService`                                                                  | 从 AuthContext/WebSocket session 构造 WorkItemContext（`gateway/service/`）                                                                                              |
| `ExecutionReceiptTaskProjectBridge`                                                       | receipt 到达时反写 Task/Project 状态（`gateway/service/`）；findByExecutionId 调用已改为 .stream().findFirst()                                                                                                                   |
| `ArtifactRecord` 新增 taskId/projectId                                                      | artifact 记录关联任务和项目，支持 `withTaskId`/`withProjectId`/`associateTaskAndProject`                                                                                        |
| `ArtifactRecordEntity` 新增 task\_id/project\_id 列                                          | 数据库持久化 artifact 与任务/项目的关联                                                                                                                                           |
| 身份认证强化                                                                                    | `claim`/`submit`/`/tasks/my` 从 token 提取身份，不再信任请求体中的 employeeId                                                                                                      |
| `BrainOutputContract` / `EmployeeOutputContract`                                          | 统一大脑/员工输出契约，前端/Trace/回执稳定消费                                                                                                                                         |
| `BrainBoundaryEnforcer`                                                                   | 大脑职责边界硬判断：9 个大脑的 allowedActions/forbiddenActions/escalationTriggers/mustEscalateScenarios                                                                           |
| `ExecutionBoundaryEnforcer`                                                               | 员工越权拦截：跨部门/超管辖/高风险任务硬判断                                                                                                                                             |
| `StandardLoadingChainService`                                                             | 规范强制加载链：职责卡→Prompt→runbook→文档工作流→自定义指令                                                                                                                              |
| `StandardComplianceTraceService`                                                          | 规范合规追踪：边界检查/标准加载/澄清/升级/回执合规/权限检查                                                                                                                                    |
| `DepartmentConversationEntity` / `DepartmentConversationRepository`                       | 长期可恢复部门对话持久化                                                                                                                                                        |
| `DepartmentChatMessageEntity` 新增字段                                                        | conversationId/taskKey/executionId/messageType/tenantId/deletedAt                                                                                                   |
| `ConnectionRegistry` 新增 conversationId 绑定                                                 | bindConversation/unbindConversation/getSessionIdByConversationId                                                                                                    |
| `DepartmentChatService` 以 conversationId 为核心                                              | findOrCreateConversation/saveMessage 带 conversationId/全链路传递 conversationId                                                                                          |
| `DepartmentWebSocketHandler` 断线重连                                                         | 支持 `?conversationId=xxx` 查询参数重连，发送 reconnected 消息                                                                                                                   |
| `DepartmentApiController` 新增 conversation 端点                                              | listConversations/getConversationHistory/deleteConversation                                                                                                         |
| `TaskStatus` 新增澄清状态                                                                       | NEEDS\_CLARIFICATION/CLARIFICATION\_PENDING                                                                                                                         |
| `TaskEntity` 新增澄清字段                                                                       | readinessStatus/clarificationQuestions/clarificationAnswer/clarificationRequestedAt/blockingIssues                                                                  |
| `TaskCheckout` 新增澄清方法                                                                     | requestClarification/resolveClarification                                                                                                                           |
| `TechBrain` 兜底输出                                                                          | publishFallbackResponse：空消息/空内容/异常时发布兜底响应                                                                                                                           |
| `DepartmentChatService` 澄清直接返回                                                            | NEEDS\_CLARIFICATION/BLOCKED 时直接返回澄清消息，不再进入 brain.process()                                                                                                         |
| `DepartmentWebSocketHandler` 结构化事件推送                                                      | pushExecutionEvent：intake\_classified/main\_brain\_planned/readiness\_evaluated/clarification\_requested/execution\_started/receipt\_received/finalized             |
| `DepartmentChatService` taskKey 归并                                                        | resolveTaskKeyForConversation：从 conversation 的 activeTaskKey 恢复可续接任务                                                                                                |
| `security.Employee` → `SecurityIdentity` 重命名                                              | 安全上下文员工类已重命名，避免与 `employee.Employee` 混淆；DTO 命名不再重复                                                                                                                  |
| `security.EmployeeService` → `AuthEmployeeService` 重命名                                    | 安全员工服务已重命名，与 `employee.EmployeeService` 明确区分                                                                                                                          |
| `security.EmployeeServiceImpl` → `AuthEmployeeServiceImpl` 重命名                            | 安全员工服务实现已重命名，明确为认证用途                                                                                                                                               |
| `BountyHunterSkill` → `BountyHunterSkillAdapter` 重命名                                     | 赏金猎人技能已重命名，避免与 Skill 接口混淆                                                                                                                                           |
| `ProviderRegistry` → `LlmProviderRegistry` 重命名                                           | Provider 注册清单已重命名，明确为 LLM Provider 专用注册                                                                                                                             |
| `SensorNeuron` → `PerceptionSensorNeuron` 重命名                                            | 感知传感器神经元已重命名，避免与 `neuron/impl/SensorNeuron` 混淆                                                                                                                       |
| `WeeklyReportScenarioHandler` → `ProactiveWeeklyReportHandler` 重命名                        | 周报场景处理器已重命名，明确主动预判归属                                                                                                                                               |
| `knowledge/native_` → `knowledge/nativestore` 包名变更                                       | 原生知识包已重命名，避免 Java 关键字冲突                                                                                                                                             |
| `autonomous.evolution` → `core.evolution` 迁移                                              | `EvolutionManager` 和 `HardwareUpgradeService` 从经济自治包迁移至核心进化包，统一进化能力管理                                                                                             |
| `ReceiptStatus` 枚举新增                                                                     | 回执生命周期状态：PENDING/COMPLETED/FAILED/TIMEOUT/RETRYING                                                                                                                  |
| `JpaEmployeeExecutionReceiptService` 新增                                                   | JPA 持久化回执服务，替代 `FileBasedEmployeeExecutionReceiptService` 为生产默认实现                                                                                                   |
| `TraceEventEntity` / `TraceEventRepository` 新增                                            | 自治编排 Trace 事件持久化，支持按 traceId/stage/eventType/executionId 查询                                                                                                          |
| `EmployeeExecutionReceiptEntity` / `EmployeeExecutionReceiptRepository` 新增                 | 员工执行回执持久化，支持按 executionId/employeeCode/status 查询和统计                                                                                                                   |
| `AnthropicProvider` 新增                                                                    | 支持 Anthropic 原生协议的 Provider，与 `AnthropicClient` 互补                                                                                                                  |
| `EmployeeEntityMigrationService` 新增                                                       | 员工实体数据迁移，统一 EmployeeEntity/EnterpriseEmployeeEntity 数据同步                                                                                                             |
| `ResponsibilityCardService` 新增                                                            | 数字员工职责卡管理，支持从 documents 加载和数据库持久化                                                                                                                                     |
| `MemoryToKnowledgeExtractor` 新增                                                           | 从对话记忆中自动提取可复用知识，支持 L1→L2→L3 晋升评估                                                                                                                                    |
| `TaskCheckoutSyncService` 新增                                                              | TaskCheckout 与 TaskRepository 数据同步，确保内存状态与数据库一致                                                                                                                     |
| `FeedbackEvent` / `FeedbackEventBus` / `SimpleFeedbackEvent` 新增                           | 反馈事件接口/总线/实现，支持异步反馈事件发布/订阅/分发                                                                                                                                        |
| `gateway/security/RequireAccess` + `RequireAccessAspect` 新增                               | 统一权限注解 + AOP 切面，替代 Controller 中零散的权限检查调用                                                                                                                             |
| `GET /api/auth/check` 端点新增                                                              | 权限检查，返回当前用户访问级别和可访问大脑                                                                                                                                               |
| `GET /api/model-pool/visible` 端点新增                                                      | 查询当前用户可见的模型列表（按权限过滤）                                                                                                                                                |
| `POST /api/approval/{instanceId}/callback/approved` 端点新增                                 | 审批通过回调，触发后续执行流程                                                                                                                                                     |
| `POST /api/approval/{instanceId}/callback/rejected` 端点新增                                 | 审批拒绝回调，记录拒绝原因并通知申请人                                                                                                                                                 |
| Flyway V18-V23 迁移新增                                                                      | V18: trace\_events 表；V19: 9 个缺失实体表；V20: session\_context 字段长度修复；V21: 统一员工表和回执表；V22: trace 关联键和索引；V23: 职责卡表和外键约束                                           |
| `LlmBasedExecutionCapabilityResolver` 新增                                                | LLM 驱动的执行能力解析器，规则置信度不足时调用 LLM 语义判断；支持 LlmDecisionClient 和 MainBrain.callLlm 双通道；降级到 DefaultExecutionCapabilityResolver                                           |
| `BrowserAutomationTool` 改为真实 Playwright 实现                                              | 所有方法（navigate/click/type/screenshot/getText/wait）替换为 Playwright API 调用；懒初始化 Browser 实例；会话隔离 BrowserContext；优雅降级；AutoCloseable + @PreDestroy 资源管理                          |
| `MainBrain.forwardToDepartment()` 实现实际转发                                               | 通过 ChannelManager 将消息发布到目标部门大脑的输入通道，携带协调元数据（coordination_session_id/forwarded_by/original_user_id）                                                                     |
| `SessionContext` 新增 taskKey/executionId 字段                                              | 与 ConnectionContext/SessionContextEntity 保持一致，gateway 层可直接感知任务上下文                                                                                                    |
| `DepartmentChatService` Receipt 双路径去重                                                  | 新增 triggeredFinalResponses 原子集合，防止轮询路径和监听路径重复触发最终响应                                                                                                               |
| `DepartmentChatService` executionResultCache                                              | 监听路径 `onReceiptRecorded` 中 executionResult=null，添加 executionResultCache 缓存回查                                                                                           |
| `DepartmentChatService` 需求冻结/防漂移                                                      | 新增 activeSessionPlans 映射追踪活跃计划，执行中拒绝重新规划并推送 requirement_frozen 事件；30 分钟超时自动清理                                                                              |
| `CrossDepartmentCoordinator` 跨部门协调                                                     | 新增跨部门任务协调器，聚合多部门执行结果；needsCrossDepartmentCoordination() 判断是否需要跨部门协调                                                                                         |
| `MainBrainTaskPlan` 需求冻结与版本控制                                                       | 新增 isRequirementFrozen() 冻结判定、withRequirementStatus() 带状态转换校验的不可变更新、withIncrementedVersion() 递增需求版本                                                                   |
| `DefaultExecutionReceiptReviewer` 降级验收严格化                                              | 摘要为空→rejected、验收标准未满足→rejected、期望产物但无文件→rejected+needsRetry；检查 worktreePath/diffPath/metadata.artifactPaths                                                              |
| `MainBrain` 工具迭代限制调整                                                                | maxToolIterations 从 5 增加到 20；达到限制时强制让 LLM 生成最终响应（不再返回错误消息）                                                                                                  |
| `SelfImprovingTool` NPE 修复                                                               | Map.of() 不允许 null 值，改用 LinkedHashMap                                                                                                                                   |
| `FixedEmployeeRegistry` 部门匹配校验                                                        | 启动时校验员工部门与绑定大脑部门是否匹配，日志输出 mismatch 详情                                                                                                                         |
| 前端需求状态展示                                                                             | DepartmentChatInline 添加 requirementStatus state 和 UI 展示标签，处理 execution_event 中的 requirementStatus 字段                                                                                   |
| 前端 WebSocket 30 秒心跳                                                                   | DepartmentChatInline 添加 ping 心跳，防止僵尸会话循环                                                                                                                            |
| `ExecutionCapability` 新增 PROJECT_MANAGEMENT/ISSUE_TRACKING                               | 新增项目管理执行能力枚举，支持任务创建/查询/更新和 Issue 追踪/状态流转                                                                                                                      |
| `ToolBackedEmployeeTaskExecutor` 项目管理路由                                               | 新增 executeProjectManagementTask + resolveProjectManagementInvocation；routeByCapability 增加 PROJECT_MANAGEMENT/ISSUE_TRACKING 路由；normalizeTaskType 增加 project_management 归一 |
| `FixedEmployeeRegistry` T03/T04/T09/T10 增加 jira 工具                                     | DevOps/运维/前端/后端员工增加 jira 工具和 Jira项目管理 capabilities                                                                                                                  |
| 5份员工规范文档补充项目管理内容                                                               | 职责卡/系统提示词/Agent Prompt/自主执行手册/文档工作流均补充 Jira/OpenProject 使用指引                                                                                                        |
| `BrainConfig` 工具部门隔离 P2-1                                                             | 新增 BRAIN_TOOL_DEPARTMENT_MAPPING 静态映射 + filterToolsByBrainDepartment 方法；8 个业务大脑按部门过滤工具，MainBrain 保留全部                                                                 |
| `AbstractBrain` 动态工具列表 P2-2                                                           | 新增 buildDynamicToolList() 方法，根据实际注入工具动态生成描述；TechBrain SYSTEM_PROMPT 改为 SYSTEM_PROMPT_TEMPLATE 动态替换                                                                   |
| `AgentWebSocketHandler` Token 认证改进                                                      | 优先从 Sec-WebSocket-Protocol 头获取 token，URL 查询参数降级为兼容模式                                                                                                              |
| 前端 Toast 组件替代 alert()                                                                   | 新增 toastStore.ts + Toast.tsx + Toast.css，43 处 alert() 已替换为 showToast()                                                                                             |
| 前端 token 访问统一                                                                          | 新增 getToken() 辅助函数，51 处 localStorage.getItem('token') 已替换                                                                                                        |
| 前端 fetchJson 统一                                                                         | 5 个独立 fetchJson 实现已合并为从 api.ts 统一导入                                                                                                                              |
| 前端 usePolling Hook                                                                       | 页面可见时启动 setInterval，不可见时暂停；Dashboard.tsx 和 Chat.tsx 已使用                                                                                                           |
| 前端 ESLint 配置                                                                            | 新增 eslint.config.js（ESLint 9 flat config），配置 no-explicit-any:warn、no-console:warn、react-hooks 规则                                                                    |
| Docker 网络分离                                                                              | docker-compose.yml 拆分为 frontend/backend 双网络隔离                                                                                                                      |
| Docker 基础设施内存限制                                                                         | postgres(2G/512M)、redis(1G/256M)、qdrant(2G/512M)、zookeeper(1G/256M)                                                                                                  |
| Docker Kafka 禁止自动创建 Topic                                                               | KAFKA\_AUTO\_CREATE\_TOPICS\_ENABLE 改为 false                                                                                                                         |
| Docker 数据库密码强制环境变量                                                                       | POSTGRES\_PASSWORD 和 DATABASE\_PASSWORD 改为强制环境变量注入                                                                                                                  |
| WebSocket 消息体大小限制                                                                       | maxTextMessageBufferSize=128KB, maxBinaryMessageBufferSize=256KB                                                                                                      |
| OAuth state 参数验证                                                                         | AuthController 添加 state 存储、5 分钟过期、一次性消费验证                                                                                                                          |
| Token 刷新令牌轮换                                                                             | refreshToken 时旧 session 立即失效，颁发新 session                                                                                                                           |
| ExecutorService @PreDestroy                                                                | 10 个类添加 @PreDestroy 优雅关闭：SandboxExecutorImpl、HealthMonitorImpl、HardwareResourceMonitor、EvolutionExecutor、EventHookManager、RunQueue、ParallelModelService、AgentService、DepartmentWebSocketHandler、AutonomyTraceService |
| MemPalaceBackend @PreDestroy（已补充）                                                        | ✅ 已有 @jakarta.annotation.PreDestroy + destroy()（调用 close().join()），内含 mcpProcess.destroyForcibly() + 守护线程 interrupt                                                                                      |
| ApiResponse 统一                                                                           | AuthController/TaskController 内部 ApiResponse 已删除，统一使用 common/ApiResponse.java；公共类新增 success()/error() 别名方法                                                                 |
| 国际化统一（渐进式）                                                                               | 部分 isChinese ? '中文' : 'English' 替换为 t('key')；zh.json/en.json 新增 layout/userMgmt/agentDetail/openclaw/phoneLogin/approvals/projects 等翻译 key                          |
| `LivingAgentCoreConfig` 上帝类拆分                                                            | 原 54 个 @Bean 已按功能域拆分到 BrainConfig/ToolConfig/ProviderConfig/MemoryConfig/ChannelConfig 五个子配置类，本类仅保留 10 个 Bean |
| `EnterpriseFeishuTool` 上帝类拆分                                                            | 原 1571 行 27 个 action 已拆分为 FeishuMessageTool/FeishuContactTool/FeishuApprovalTool/FeishuCalendarTool 四个子工具，公共逻辑提取到 AbstractFeishuTool 基类；原类标记 @Deprecated |
| `AbstractBrain` 上帝类拆分                                                                   | 会话历史管理拆分到 BrainSessionManager，ReAct 循环拆分到 BrainReActEngine，模型降级拆分到 BrainModelFallback；AbstractBrain 注入这些组件委托调用 |
| `MemPalaceBackend` @PreDestroy                                                            | ✅ 已有 @jakarta.annotation.PreDestroy + destroy()，MCP 子进程可优雅关闭                                                                                 |
| `SQLiteKnowledgeBase` 缺少 @PreDestroy                                                     | ⚠️ 连接缓存 shutdown() 需手动调用，建议补充 @PreDestroy 自动关闭                                                                   |
| P0 轻量路由层（TaskRouteClassifier）                                                          | 新增 TaskRouteClassifier 接口 + DefaultTaskRouteClassifier 实现（7条路由规则），集成到 ConversationOrchestrator，在意图分析后判断单部门直达/跨部门主脑拆解/需要澄清                          |
| P0 部门内审查闭环（Internal Review Loop）                                                      | 新增 autonomy/review 子包（ReviewState/ReviewDecision/ReviewResult/ReviewHistory/InternalReviewService/DefaultInternalReviewService）；DynamicEmployeeTaskConsumerRegistry 集成审查流程；FixedEmployeeDefinition 增加 downstreamReviewers 字段；EmployeeWorkAssignment 增加 reviewRequired/reviewerCode/maxReviewRounds 字段 |
| P1 员工自行领取机制（Self-Claiming）                                                           | 新增 DepartmentTodoPool/DepartmentTodoItem/TodoClaimResult/InMemoryDepartmentTodoPool/EmployeeSelfClaimService/DefaultEmployeeSelfClaimService；乐观锁领取 + 资格校验 + 兜底指派                          |
| P1 部门级聚合交付（Department Aggregation）                                                    | 新增 DepartmentAggregationService/DepartmentDeliverable/AggregationResult/DefaultDepartmentAggregationService；EmployeeExecutionReceiptService 新增 getReceiptsByDepartment()；Entity/Repository 增加 department 字段；Flyway V26 迁移 |
| P2 CrossDepartmentCoordinator 接口化                                                        | 将 CrossDepartmentCoordinator 从具体类重构为接口，新增 DefaultCrossDepartmentCoordinator 实现；GatewayConfig Bean 更新                                                                              |
| P2 DefaultRequirementReadinessEvaluator                                                    | 新增规则版需求就绪评估器，7条确定性规则替代 LLM 调用，作为降级方案                                                                                                                  |
| 审查闭环增强（ReviewListener）                                                              | InternalReviewService 新增 ReviewListener 回调机制；DefaultInternalReviewService 在 review() 末尾通知监听器；DynamicEmployeeTaskConsumerRegistry 注册监听器，REVISION_NEEDED 时重新发布任务消息触发重试 |
| 审查关系数据填充                                                                            | FixedEmployeeDefinition 新增 withDownstreamReviewers() 方法；技术部 T09↔T10 交叉审查关系已配置                                                                                      |
| **2026-06-23 MAINBRAIN_EXECUTION_RULES.md P0阶段改进** | |
| DP0-2: TechBrain publishFallbackResponse 设置 Contract | TechBrain.publishFallbackResponse() 添加 BrainOutputContract 构建，确保 fallback 响应也有 Contract |
| P0-3: 删除 thenCompose 重复澄清检查 | DepartmentChatService 删除 thenCompose 中重复的澄清检查，澄清已在 ConversationOrchestrator.orchestrate() 中统一处理 |
| P0-4: 规则版分派增加工具匹配 + 能力排序 | RegistryBackedFixedEmployeeDispatcher.planAssignments() 增加 requiredTools 过滤（硬性约束）+ suggestedRoles 能力匹配排序；无工具匹配时降级到能力匹配 |
| P0-5: parseRequirementStatus 默认值改为 NEEDS_CLARIFICATION | LlmBasedMainBrainTaskDirector.parseRequirementStatus() 默认值从 REQUIREMENT_CONFIRMED 改为 NEEDS_CLARIFICATION（更安全） |
| DP0-1: TechBrain 员工任务响应改进 | TechBrain.doProcess() 将"已派发并执行 N 个员工任务"改为"正在执行 N 个员工任务，请稍候..."（中间状态）；triggerAsyncFinalResponse 发送最终结果；前端 DepartmentChatInline.tsx 已处理 async_final_response 事件将 summary 添加为聊天消息；**桌面端 OfficeChatPage.tsx 同步添加处理逻辑** |
| 等待过程进度提示 | DepartmentChatService.collectExecutionReceipts() 每 15 秒推送 waiting_progress 事件（"⏳ 任务执行中... 已完成 X/Y，正在执行 Z 个"）；前端替换上一条等待消息避免堆积 |
| **2026-06-23 MAINBRAIN_EXECUTION_RULES.md P1阶段改进** | |
| P1-2: LLM 分派重试机制 | LlmBasedFixedEmployeeDispatcher.planAssignments() 添加 MIN_VALID_RESPONSE_LENGTH=20；异常短响应（<20字符）自动重试1次 |
| P1-3: clarificationQuestion 改为 List | DialogueDecision.clarificationQuestion 改为 clarificationQuestions (List<String>)；LlmBasedDialogueAnalyzer 兼容旧格式 |
| P1-4: mapDepartmentToBrain 统一 | LlmBasedDialogueAnalyzer.mapDepartmentToBrain() 使用 Department.mapDepartmentToBrain() 统一映射逻辑 |
| P1-5: 路由规则6增加 complexity 判断 | DefaultTaskRouteClassifier.classify() 规则6：complexity<=2 单部门直达，complexity>2 走主脑拆解 |
| DP1-2: LLM 降级超时调整 | DynamicEmployeeTaskConsumerRegistry 添加 fallbackTimeoutMs=60_000；LLM 降级调用使用 60s 超时 |
| DP1-3: 审查提交异常标记 | EmployeeTaskExecutionOutcome 添加 withNeedsHumanReview() 方法；审查提交失败时标记 needsHumanReview=true |
| **2026-06-23 MAINBRAIN_EXECUTION_RULES.md PR系列改进（主动汇报机制）** | |
| PR-1: 登录时触发汇报 | DepartmentWebSocketHandler.afterConnectionEstablished() 调用 ProactiveOrchestrator.runForUser(userId)；发送 proactive_report 消息 |
| PR-7: 前端汇报展示 | DepartmentChatInline.tsx 处理 proactive_report 消息类型；显示建议和警告列表 |
| PR-9: 登录汇报状态标记 | DepartmentWebSocketHandler 添加 sessionProactiveReported Map；标记本次会话已汇报避免重复 |
| PR-2: 身份驱动汇报内容 | ProactiveSuggestionService 注入 TaskCheckout/BountyHunterService/TaskClaimService/Memory/AccessLevel；generateTaskBasedSuggestions() 根据身份（董事长/部门经理/普通员工）生成不同建议 |
| PR-3: 职责卡内容解析 | DutyCardParser 解析 documents/shared/company/duty-cards/*.md；提供 getChairmanReportSummary()、getDutyCardByDepartment()；ProactiveSuggestionService 使用职责卡生成专属汇报 |
| PR-4~PR-6: 专属汇报 | ProactiveSuggestionService.generateTaskBasedSuggestions() 根据身份（董事长/部门经理/普通员工）生成专属汇报内容；董事长：数字员工体系概览；部门经理：部门职责提醒；员工：收益待结算 |
| PR-8: 汇报内容缓存 | ProactiveReportCache 全局缓存（董事长视角）+ 用户缓存；每5分钟自动刷新；forceRefresh() 强制刷新；CachedReport.formatForUser() 格式化输出 |
| **2026-06-23 MAINBRAIN_EXECUTION_RULES.md NP1系列改进（知识沉淀/绩效记录闭环）** | |
| NP1-2: SkillFinderTool 更新员工技能 | SkillFinderTool.handleInstall 添加 employee_code 参数；FixedEmployeeDefinition 添加 withAdditionalSkill()/withAdditionalCapability() 方法 |
| NP1-5: 知识沉淀闭环 | DefaultKnowledgeCaptureService.captureFromExecution() 存储到 KnowledgeManager；已集成到 DepartmentChatService.processBrainResponse() |
| NP1-6: 知识沉淀集成 | DepartmentChatService 已注入 KnowledgeCaptureService；processBrainResponse 调用 captureFromExecution() |
| NP1-7: 绩效记录闭环 | DefaultPerformanceCaptureService.captureFromExecution() 通过 LedgerService 记录绩效积分；已集成到 DepartmentChatService |
| NP1-8: 绩效数据存储 | 通过 LedgerService.IncomeRecord 存储绩效数据（替代独立 PerformanceRecordEntity） |

| **2026-06-25 MAINBRAIN_ADMIN_BRIDGE_PLAN 服务管理衔接方案** | |
| 目的 | 解决 EXECUTION_RULES 与 SERVICE_MANAGEMENT 单独使用时的冲突，管理类操作独立成服务 |
| 新增 admin 包 | `core/admin/` 包独立于 `core/tool/`，AdminService 不实现 Tool 接口 |
| ServiceAdminBootstrap | 服务初始化入口接口，编排 GitLab/OpenProject/Jenkins 初始化 |
| GitLabAdminService | GitLab 管理操作：createGroup/createProject/createUser/createToken/addGroupMember |
| OpenProjectAdminService | OpenProject 管理操作：createRole/createProject/createUser/addMember |
| JenkinsAdminService | Jenkins 管理操作：createJob/createCredential/installPlugin |
| AdminConfig | Spring 配置，通过 `service-admin.enabled=true` 启用，默认不启用 |
| V27 迁移脚本 | 新增 employee_external_account/service_admin_bootstrap_state 两张表（service_admin_credential 已删除） |
| 权限隔离 | AdminService 不注册到 ToolRegistry，不走 ReAct 循环，与员工工具完全隔离 |
| 幂等设计 | 通过 service_admin_bootstrap_state 表记录每步状态，已成功的跳过，失败的重试 |

### 2026-07-03 第三轮闭环改进
- 新增 NativeCallMetrics/NativePerformanceMonitor/NativeLibraryHealthCheck（P19-B/C）
- 新增 InferenceResultValidator（P20-D）
- 新增 StartupRecoveryService/DegradedTrafficCanary（P12-D/P27-A）
- 新增 SandboxViolationTracker（P30-A）
- 新增 KnowledgeConsumptionFeedback（P26-A）
- 新增 HardwareUpgradeRoiValidator/SelfHealingOrchestratorImpl/SelfGovernanceOrchestratorImpl（P24-A/P25-A/P31-A）
- 新增 SatisfactionCollector（P29-A）
- 新增 ErrorReportFeedbackService（P17-C）
- 修复 Canary空转：NamedPipeModelClient集成DegradedTrafficCanary路由判断
- 修复 ModelDaemonRecoveryService逻辑顺序：clearDegraded改为canary.startProbing
- 修复 HealthIssue.type未设置：3处new HealthIssue改为createIssue工厂方法
- ClaudeExecutionGateway新增失败/超时EvolutionSignal发布
- SelfHealingOrchestratorImpl新增RECONNECT_PIPE动作分支
- HealthMonitorImpl.fillSuggestedAction新增管道丢失场景→RECONNECT_PIPE

### 2026-07-03 第五轮闭环改进
- 新增 VitalSignsService（P32-A: 生命体征服务，聚合健康+连接+降级+内存）
- 新增 VitalSignsController（P32-A: GET /api/vitals + /api/vitals/history）
- 新增 ConnectionHealthCheck（P24-C+P3-A: WebSocket连接健康检查）
- 新增 SatisfactionController（P29-A: 满意度采集API）
- BrainRegistry接口新增 getPersonality/updatePersonality 方法
- BrainBoundaryEnforcer新增 riskTolerance→边界联动（P29-B）
- SelfHealingOrchestratorImpl新增 NOTIFY_CLIENT 动作分支（P24-C）
- ConnectionRegistry接口新增 getAllSessionIds 方法

***

## 17. 新增功能前检查清单

新增任何功能前，先检查：

- [ ] 是否已有 Controller 可以挂接口？
- [ ] 是否已有 Service 可以复用？
- [ ] 是否应该放在 `core` 而不是 `gateway`？
- [ ] 是否是 Tool/Skill，而不是直接写在 Brain？
- [ ] 是否需要权限校验？
- [ ] 是否需要审计日志？
- [ ] 是否需要数据库表和 migration？
- [ ] 是否需要前端 service 封装？
- [ ] 是否需要更新 DTO/type？
- [ ] 是否需要接入自治 Trace？
- [ ] 是否会和部门文本链路/通用 Agent 链路混淆？
- [ ] 是否需要沉淀到 `documents` 或 `docs`？

***

## 18. 快速定位命名关键词

| 关键词                    | 可能位置                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------- |
| 登录、验证码、Founder         | `gateway/controller/AuthController`、`PhoneAuthController`、`core/security/auth`                  |
| 部门聊天                   | `DepartmentWebSocketHandler`、`DepartmentChatService`、`core/brain`、`core/autonomy`               |
| Qwen3、BitNet、NamedPipe | `core/neuron/impl`、`core/model/impl/NamedPipeModelClient`、`scripts/python/model_daemon.py`      |
| 模型池                    | `core/model/pool`、`ModelPoolController`、`frontend/src/services/modelPoolApi.ts`                 |
| 大脑模型分配                 | `core/model/selector`、`BrainModelConfigController`、`brainModelApi.ts`                           |
| 固定员工                   | `FixedEmployeeRegistry`、`FixedEmployeeController`、`FixedEmployee*Entity`、`fixedEmployeeApi.ts`  |
| 工具                     | `core/tool`、`ToolRegistryImpl`、`DefaultToolExecutor`                                            |
| 知识库                    | `core/knowledge`、`KnowledgeController`、`documents`                                              |
| 记忆                     | `core/memory`、Memos/MemPalace/SQLite backend                                                    |
| 任务                     | `TaskController`、`TaskWorkflowService`、`core/planner`、`core/workflow`                           |
| 项目                     | `ProjectController`、`core/project`                                                              |
| 审批                     | `ApprovalController`、`core/approval`                                                            |
| 绩效                     | `PerformanceController`、`core/operation/performance`                                            |
| 主动预判                   | `ProactiveController`、`core/proactive`                                                          |
| 进化                     | `EvolutionAdminController`、`core/evolution`                                                     |
| 赚钱、赏金、激励、支付            | `core/autonomous/bounty`、`core/autonomous/incentive`、`core/autonomous/payout`                   |
| 对话分析、意图识别、自治Trace      | `core/autonomy`（详见 **6.17 节** 区分说明）                                                             |
| LLM 入口分析、消息意图自动识别      | `core/autonomy/impl/LlmBasedDialogueAnalyzer`（LLM 优先 + 规则降级兜底）                                  |
| 产物记录、Artifact          | `core/autonomy/ArtifactRecord`、`core/autonomy/ArtifactRecordService`                            |
| 知识沉淀、经验写入              | `core/autonomy/KnowledgeCaptureService`、`core/autonomy/impl/DefaultKnowledgeCaptureService`     |
| 绩效记录、积分发放              | `core/autonomy/PerformanceCaptureService`、`core/autonomy/impl/DefaultPerformanceCaptureService` |
| 动态消费者注册、员工任务消费         | `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry`                                        |
| 响应编排、主脑回复组合            | `core/autonomy/MainBrainResponseComposer`、`core/autonomy/impl/DefaultMainBrainResponseComposer` |
| 执行结果聚合、receipt 汇总      | `core/autonomy/ExecutionResultAggregator`、`core/autonomy/impl/DefaultExecutionResultAggregator` |
| 出口策略、最终回复选择            | `core/autonomy/FinalResponseCoordinator`、`core/autonomy/impl/DefaultFinalResponseCoordinator`   |
| 持久化回执、JSON receipt     | `core/autonomy/impl/FileBasedEmployeeExecutionReceiptService`、`data/receipts/`                  |
| LLM 任务规划、主脑 JSON 解析    | `core/autonomy/impl/LlmBasedMainBrainTaskDirector`、`MainBrain.callLlm()`                        |
| Native                 | `core/nativelib`、`living-agent-native/src/jni`                                                  |
| Windows 自动化           | `core/tool/impl/WindowsAppTool`（HTTP+pywinauto 业务化封装）、`core/tool/impl/WindowsAutomationTool`（WebSocket 通用系统控制）、`core/websocket/WindowsAutomationClientGateway`、`gateway/websocket/WindowsAutomationClientGatewayImpl`、`living-agent-desktop/src/main/win-automation-service.ts`、`living-agent-desktop/resources/win-automation/service.py`、`scripts/windows_automation/server.py`、`scripts/windows_automation/config.json` |
| Docker                 | `docker-compose.yml`、Dockerfile、`entrypoint.sh`                                                 |
| 前端 Toast、alert 替换      | `frontend/src/components/Toast.tsx`、`frontend/src/stores/toastStore.ts`                          |
| 前端 token 获取           | `frontend/src/stores/index.ts` → `getToken()`                                                    |
| 前端轮询、页面可见性            | `frontend/src/hooks/usePolling.ts`                                                               |
| 前端国际化、i18n             | `frontend/src/i18n/zh.json`、`frontend/src/i18n/en.json`、`useTranslation()` hook                |
| 飞书工具拆分                | `core/tool/impl/enterprise/AbstractFeishuTool`、`FeishuMessageTool`、`FeishuContactTool`、`FeishuApprovalTool`、`FeishuCalendarTool` |
| 大脑会话管理                | `core/brain/impl/BrainSessionManager`（从 AbstractBrain 拆分）                                       |
| 大脑 ReAct 引擎           | `core/brain/impl/BrainReActEngine`（从 AbstractBrain 拆分）                                         |
| 大脑模型降级                | `core/brain/impl/BrainModelFallback`（从 AbstractBrain 拆分）                                       |
| 执行能力解析                | `core/autonomy/impl/LlmBasedExecutionCapabilityResolver`（LLM 优先 + Default 降级）                  |
| 核心配置拆分                | `core/config/BrainConfig`、`ToolConfig`、`ProviderConfig`、`MemoryConfig`、`ChannelConfig`          |
| canary/金丝雀            | `diagnosis/DegradedTrafficCanary.java`                                                          |
| 熔断/断路器                | `evolution/circuitbreaker/EvolutionCircuitBreaker.java`、`model/impl/NamedPipeModelClient.java`  |
| 自愈                     | `evolution/orchestrator/impl/SelfHealingOrchestratorImpl.java`                                  |
| 协同编排                  | `evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java`                               |
| ROI                    | `evolution/HardwareUpgradeRoiValidator.java`                                                    |
| 满意度                   | `evolution/personality/SatisfactionCollector.java`                                              |
| 错误反馈                  | `diagnosis/ErrorReportFeedbackService.java`                                                     |
| 违规追踪                  | `security/SandboxViolationTracker.java`                                                         |
| 知识反馈                  | `knowledge/KnowledgeConsumptionFeedback.java`                                                   |
| 推理验证                  | `provider/InferenceResultValidator.java`                                                        |
| riskTolerance联动        | `brain/BrainBoundaryEnforcer.java`（P29-B: 低风险升级mustEscalate，高风险降级needsEscalation）            |
| 连接自愈                 | `gateway/websocket/ConnectionHealthCheck.java`（P24-C: 僵死连接检测+NOTIFY_CLIENT）                    |
| 生命体征                 | `diagnosis/VitalSignsService.java`（P32-A: 健康快照+历史趋势）                                      |
| @Transactional         | `core/evolution/executor/impl/JpaEvolutionFeedbackService`、`core/employee/impl/JpaEmployeeCompensationService`、`core/autonomy/AutonomyTraceService`、`core/autonomy/impl/JpaCodeReviewWorkflowService`、`core/autonomy/impl/JpaArtifactRecordService` |
| findByExecutionId      | `TaskRepository`（返回类型 Optional→List）；`ApprovalController`、`ExecutionReceiptTaskProjectBridge`、`DepartmentChatService`（调用改为 .stream().findFirst()/.isEmpty()） |
| instanceof移除          | `PerformanceDashboardService`、`PerformanceController`（改用接口方法） |
| 绩效Top/Bottom         | `PerformanceAssessmentService`（接口新增 getCompanyTopPerformers/getCompanyBottomPerformers）、`InMemoryPerformanceAssessmentService`、`JpaPerformanceAssessmentService`（实现+@Override） |
| createIssue/setMode修复 | `SelfGovernanceOrchestratorImpl`（修复方法不存在错误） |

***

## 19. 变更日志

### 2026-07-03

**修改文件：**

| 文件 | 变更内容 |
|------|----------|
| `living-agent-core/.../evolution/executor/impl/JpaEvolutionFeedbackService.java` | record() 添加 @Transactional |
| `living-agent-core/.../employee/impl/JpaEmployeeCompensationService.java` | record()/definePlan()/assignPlan() 添加 @Transactional |
| `living-agent-core/.../autonomy/AutonomyTraceService.java` | recordEvent() 添加 @Transactional |
| `living-agent-core/.../autonomy/impl/JpaCodeReviewWorkflowService.java` | 全部写方法添加 @Transactional |
| `living-agent-core/.../autonomy/impl/JpaArtifactRecordService.java` | recordArtifact()/associateTaskAndProject()/scanAndIndexDirectory() 添加 @Transactional |
| `living-agent-core/.../database/repository/TaskRepository.java` | findByExecutionId 返回类型从 Optional 改为 List |
| `living-agent-core/.../operation/performance/PerformanceAssessmentService.java` | 接口新增 getCompanyTopPerformers/getCompanyBottomPerformers |
| `living-agent-core/.../operation/performance/InMemoryPerformanceAssessmentService.java` | 实现新增接口方法 + @Override |
| `living-agent-core/.../operation/performance/JpaPerformanceAssessmentService.java` | 实现新增接口方法 + @Override |
| `living-agent-gateway/.../service/PerformanceDashboardService.java` | 移除 instanceof 耦合，直接调用接口方法 |
| `living-agent-gateway/.../controller/PerformanceController.java` | 移除 instanceof 耦合，直接调用接口方法 |
| `living-agent-gateway/.../controller/ApprovalController.java` | findByExecutionId 调用改为 .stream().findFirst() |
| `living-agent-gateway/.../service/ExecutionReceiptTaskProjectBridge.java` | findByExecutionId 调用改为 .stream().findFirst() |
| `living-agent-gateway/.../service/DepartmentChatService.java` | findByExecutionId 调用改为 .isEmpty()/.stream().findFirst() |
| `living-agent-core/.../evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java` | 修复 createIssue() 不存在和 setMode() 不存在错误 |

**新增文件：** 无（本轮修改的是已有文件）

**变更主题：** 事务注解补全 + findByExecutionId 返回类型适配 + instanceof 耦合消除 + 绩效接口扩展 + 编排器方法修复

