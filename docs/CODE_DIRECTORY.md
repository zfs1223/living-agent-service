# Living Agent Service 完整代码结构目录

> 基于 `CODE_STRUCTURE_AND_FILE_GUIDE.md` 整理的完整代码目录树，包含所有文件的功能说明
> 
> 生成时间：2026-05-08
> 
> 适用范围：后端 Java/Spring Boot、前端 React/Vite、Rust Native、Python model daemon、Docker 编排、数据库脚本

---

## 项目根目录

```
docker/living-agent-service/
├── pom.xml                              # Maven 父工程，声明 Java 21、Spring Boot 3.4、Spring Cloud 及子模块依赖版本
├── docker-compose.yml                   # 本地 Docker 编排，包含后端、前端、PostgreSQL、Redis、Kafka、Qdrant、Memos、OpenProject 等服务
├── .dockerignore                        # Docker 构建忽略规则，排除 target/node_modules/logs 等构建产物
├── .gitignore                           # Git 忽略规则，避免提交构建产物、日志和敏感信息
├── Dockerfile.rust-native               # Rust native 模块构建镜像，用于编译 .so 动态链接库
├── living-agent-app/                    # Spring Boot 启动模块，聚合 core/gateway/skill/perception
├── living-agent-core/                   # 核心领域层，包含大脑、神经元、员工、模型、工具、知识、进化等
├── living-agent-gateway/                # API/WebSocket 网关层，Controller、WebSocket Handler、前端服务适配
├── living-agent-skill/                  # 技能加载、技能注册和内置技能资源
├── living-agent-perception/             # 感知层预留模块，视觉/音频/输入感知能力扩展
├── living-agent-native/                 # Rust native/JNI 高性能模块
├── frontend/                            # React + Vite 前端工程
├── scripts/                             # Python 脚本和模型守护进程
├── init-db/                             # 数据库初始化脚本
├── documents/                           # 企业制度、部门文档、数字员工职责卡、治理文档
├── docs/                                # 架构设计、计划、问题排查文档
├── image/                               # 镜像构建辅助、系统依赖、离线镜像资源
└── data/                                # 本地运行数据挂载目录
```

---

## living-agent-app 启动模块

```
living-agent-app/
├── pom.xml                              # 启动模块依赖，引用 core/gateway/skill/perception 子模块
├── Dockerfile                           # 生产环境 Docker 镜像构建，打包 jar、复制 native 库、安装运行依赖
├── Dockerfile.local                     # 本地开发镜像构建，用于挂载源码和快速构建调试
├── entrypoint.sh                        # 容器启动脚本，等待依赖服务、启动 Java、启动 Python model daemon
└── src/main/
    ├── java/com/livingagent/
    │   └── LivingAgentApplication.java  # Spring Boot 主启动类，@SpringBootApplication，全局开关、异步、定时任务
    └── resources/
        └── application.yml              # 后端主配置，端口、数据库、Redis、Kafka、模型服务、Native、认证、日志
```

---

## living-agent-gateway 网关模块

```
living-agent-gateway/
├── pom.xml                              # 网关模块依赖，引用 core 模块，Web/安全/消息中间件依赖
└── src/main/java/com/livingagent/gateway/
    ├── config/                          # Web、WebSocket、CORS、安全、Tomcat、外部平台配置
    │   ├── GatewayConfig.java           # 网关基础配置，通用 Bean、跨域、自治编排 Bean 注册
    │   ├── WebSocketConfig.java         # WebSocket 路由注册，聊天、部门聊天等 WebSocket 路径
    │   ├── WebMvcConfig.java            # MVC 拦截器、静态资源、路径规则、部门权限拦截
    │   ├── CorsConfig.java              # CORS 跨域配置
    │   ├── SecurityConfig.java          # Spring Security 配置，登录鉴权、放行接口、认证过滤
    │   ├── FounderConfig.java           # 创始人/管理员初始化配置
    │   ├── FeishuConfig.java            # 飞书配置，OAuth/API 集成
    │   └── TomcatConfig.java            # Tomcat 容器配置，Header 限制、连接数、WebSocket 参数
    │
    ├── controller/                      # REST API 控制器
    │   ├── AuthController.java          # 认证接口，OAuth 回调、用户信息、令牌刷新、登出
    │   ├── PhoneAuthController.java     # 手机验证码登录，发送验证码、登录、绑定手机号
    │   ├── VoicePrintController.java    # 声纹接口，声纹列表、注册、登录、验证、状态
    │   ├── SystemController.java        # 系统状态、注册创始人、系统配置、健康检查
    │   ├── SystemSettingsController.java # 系统设置，获取/更新设置、批量更新、变更历史、版本回滚
    │   ├── TenantController.java        # 租户管理，注册配置、自建、加入、解析、更新
    │   ├── AgentController.java         # Agent 通用接口，健康检查、会话状态
    │   ├── AgentApiController.java      # Agent 管理接口，列表、详情、启动/停止、技能、指标、会话、模板
    │   ├── AgentTaskController.java     # Agent 任务接口，列表、创建、详情、更新、日志、触发
    │   ├── AgentScheduleController.java # Agent 定时任务接口，列表、创建、更新、删除、运行、历史
    │   ├── AgentTriggerController.java  # Agent 触发器接口，列表、更新、删除
    │   ├── AgentChannelController.java  # Agent 频道接口，获取、创建、更新、删除、Webhook URL
    │   ├── AgentFileController.java     # Agent 文件接口，列表、读取、写入、删除、上传、下载
    │   ├── DepartmentController.java    # 部门接口，列表、代码查询、大脑、员工、成员
    │   ├── DepartmentApiController.java # 部门 API，聊天、信息、成员、大脑列表、我的部门
    │   ├── EmployeeController.java      # 员工接口，汇总、来源查询、空闲刷新、导入、预览
    │   ├── FixedEmployeeController.java # 固定员工接口，汇总、定义、按部门分组
    │   ├── OrgController.java           # 组织接口，用户列表、部门列表
    │   ├── EnterpriseController.java    # 企业接口，LLM 模型、技能、工具、测试、文档
    │   ├── EnterpriseApiController.java # 企业治理接口，仪表盘、员工、权限、部门、身份提供商、邀请码
    │   ├── TaskController.java          # 任务接口，列表、创建、详情、检出、完成、释放、审核
    │   ├── ProjectController.java       # 项目接口，列表、创建、详情、更新、删除、阶段、进度、任务
    │   ├── ApprovalController.java      # 审批接口，待审批、创建、批准、拒绝、退回、取消、工作流
    │   ├── PerformanceController.java   # 绩效接口，个人绩效、排行榜、趋势、部门统计
    │   ├── CreditController.java        # 积分接口，余额、历史、排行榜、统计
    │   ├── ModelPoolController.java     # 模型池接口，供应商、模型 CRUD、测试、发现
    │   ├── BrainModelController.java    # 大脑模型分配接口，列表、分配、清除、可用模型
    │   ├── BrainModelConfigController.java # 大脑模型配置接口
    │   ├── SkillsController.java        # 技能接口，列表、创建、更新、删除、浏览、ClawHub、导入
    │   ├── NeuronController.java        # 神经元接口，列表、详情、状态、指标
    │   ├── KnowledgeController.java     # 知识接口，概览、清理、晋升、历史
    │   ├── MessageController.java       # 消息接口，收件箱、未读数、标记已读
    │   ├── InterventionController.java  # 干预接口，评估、待处理决策、响应、升级、规则
    │   ├── ProactiveController.java     # 主动服务接口，摘要、习惯、通知、会议、分析、建议、预测
    │   ├── ProactiveOrchestratorController.java # 主动编排器接口
    │   ├── ReceptionController.java     # 接待接口，状态、聊天、流式聊天、访客、登记
    │   ├── OfficeController.java        # 办公室接口，列表、创建、状态、智能体、区域、部门状态、备忘
    │   ├── PlazaController.java         # 广场接口，帖子列表、统计、创建、点赞
    │   ├── EvolutionAdminController.java # 进化管理接口，反馈、自动调整、回滚、历史
    │   ├── MonitoringController.java    # 监控接口，健康、组件、问题、告警
    │   ├── RecoveryController.java      # 备份恢复接口，快照、恢复、验证
    │   ├── DashboardController.java     # 仪表盘接口，概览
    │   ├── MiscController.java          # 零散接口，版本信息、未读通知
    │   └── common/
    │       └── ApiResponse.java         # 统一 API 响应结构，success/data/error/errorDescription
    │
    ├── dto/                             # API 请求/响应 DTO
    ├── exception/                       # 全局异常处理
    ├── interceptor/                     # 拦截器，部门权限拦截等
    ├── proactive/                       # 网关层主动提醒/编排适配
    ├── security/                        # 网关层权限辅助
    │
    ├── service/                         # 面向 Controller/WebSocket 的应用服务
    │   ├── AgentService.java            # Agent 会话服务，创建模型 session、绑定神经元、语音/通用 Agent 链路
    │   ├── DepartmentChatService.java   # 部门聊天服务，消息保存、大脑调用、自治编排、任务单准备、执行回执 Trace
    │   ├── TaskWorkflowService.java     # 网关任务流转服务，任务状态、事件
    │   ├── TaskEventBridgeService.java  # 任务事件桥接，联动绩效/消息/通知
    │   ├── TaskPerformanceBridgeService.java # 任务绩效桥接，任务完成后记绩效
    │   ├── EvolutionFeedbackBridgeService.java # 进化反馈桥接，用户反馈进入进化系统
    │   ├── DashboardDataService.java    # Dashboard 数据聚合，首页/管理看板数据来源
    │   ├── PerformanceDashboardService.java # 绩效看板服务，统计、趋势
    │   ├── OrganizationQueryService.java # 组织/员工查询，部门、员工、组织结构
    │   ├── DepartmentNotificationService.java # 部门通知服务，消息、提醒、广播
    │   ├── KnowledgeGovernanceService.java # 知识治理应用服务，晋升、审核、治理
    │   ├── KnowledgePromotionAuditService.java # 知识晋升审计，审批和日志
    │   ├── BackupRecoveryService.java   # 备份恢复服务，系统备份、恢复、导出
    │   ├── MonitoringService.java       # 监控服务，状态、健康检查聚合
    │   ├── SystemConfigService.java     # 系统设置服务，管理后台配置读写
    │   └── SessionContext.java          # 会话上下文，当前用户、租户、权限
    │
    └── websocket/                       # WebSocket Handler
        ├── AgentWebSocketHandler.java   # 通用 Agent/语音/神经元会话 WebSocket，进入 AgentService 链路
        └── DepartmentWebSocketHandler.java # 部门聊天 WebSocket，处理 /ws/dept/{dept} 消息、权限、推送
```

---

## living-agent-core 核心模块

```
living-agent-core/
├── pom.xml                              # 核心模块依赖，JPA、Redis、Kafka、向量库、本地模型等
└── src/main/java/com/livingagent/core/
    │
    ├── brain/                           # 大脑接口、部门大脑、主脑、上下文、提示词、协作、压缩
    │   ├── Brain.java                   # 大脑统一接口，定义大脑能力方法
    │   ├── BrainContext.java            # 大脑处理上下文，承载用户、部门、会话、权限、元数据
    │   ├── BrainRegistry.java           # 大脑注册表接口，路由到部门大脑
    │   ├── prompt/
    │   │   ├── DynamicPromptBuilder.java # 动态 Prompt 构造，部门/员工职责、知识上下文
    │   │   └── InstructionFileLoader.java # 加载外部指令/制度文件，文档驱动 Prompt
    │   ├── collaboration/
    │   │   ├── LeadOrchestrator.java    # 协作编排接口，部门大脑给数字员工分工
    │   │   └── impl/
    │   │       └── TechLeadOrchestrator.java # 技术负责人式任务分工实现
    │   ├── compact/
    │   │   ├── ContextCompactor.java    # 上下文压缩接口，长对话压缩、上下文裁剪
    │   │   └── impl/
    │   │       └── RuleBasedContextCompactor.java # 规则式上下文压缩，token 控制、摘要策略
    │   └── impl/
    │       ├── AbstractBrain.java       # 部门大脑公共基类，封装模型调用、工具/上下文/日志
    │       ├── BrainRegistryImpl.java   # 大脑注册实现，维护 MainBrain、TechBrain 等映射
    │       ├── MainBrain.java           # 主脑/总控大脑，战略、跨部门、复杂协调
    │       ├── TechBrain.java           # 技术部大脑，技术任务、代码、开发流程
    │       ├── HrBrain.java             # 人力资源大脑，招聘、绩效、组织、人事
    │       ├── FinanceBrain.java        # 财务大脑，报销、预算、发票、成本
    │       ├── SalesBrain.java          # 销售大脑，销售、客户、市场线索
    │       ├── CsBrain.java             # 客服大脑，客诉、工单、FAQ
    │       ├── AdminBrain.java          # 行政大脑，行政文档、办公事务
    │       ├── LegalBrain.java          # 法务大脑，合同、合规、风险
    │       └── OpsBrain.java            # 运营大脑，数据运营、流程运营
    │
    ├── autonomy/                        # 对话自治编排、意图分析、自治 Trace
    │   ├── ConversationOrchestrator.java # 对话自治总入口，用户输入转入口分类、主脑规划、部门路由、Trace
    │   ├── DialogueAnalyzer.java        # 对话意图分析接口，判断闲聊/任务/跨部门/审批
    │   ├── impl/
    │   │   └── RuleBasedDialogueAnalyzer.java # 规则式意图分析实现，关键词/规则兜底
    │   ├── IntakeClassification.java    # 入口分类结果，区分是否需要主脑规划、是否跨部门
    │   ├── MainBrainTaskDirector.java   # 主脑任务规划接口，后续接真实 MainBrain 模型规划
    │   ├── impl/
    │   │   └── RuleBasedMainBrainTaskDirector.java # 规则版主脑规划器，输出 MainBrainTaskPlan
    │   ├── MainBrainTaskPlan.java       # 主脑结构化任务计划，任务类型、目标、主责部门、交付物
    │   ├── DepartmentTaskPlan.java      # 单部门任务计划，部门目标、建议角色、员工、交付物
    │   ├── BrainRoutingDecision.java    # 大脑/部门路由决策，记录主责部门、支持部门、是否重路由
    │   ├── DialogueDecision.java        # 意图分析和路由决策结果
    │   ├── FixedEmployeeDispatcher.java # 固定员工分派接口，将部门计划转为固定员工任务单
    │   ├── EmployeeWorkAssignment.java  # 固定员工任务单，员工编码、神经元、角色、指令、产物
    │   ├── impl/
    │   │   └── RegistryBackedFixedEmployeeDispatcher.java # 基于 FixedEmployeeRegistry 的分派实现
    │   ├── PreparedAssignmentBatch.java # 部门级任务单准备批次，聚合员工任务单、目标、批次 ID
    │   ├── AssignmentPreparationService.java # 任务单准备服务接口，整理为可交给部门大脑的准备批次
    │   ├── impl/
    │   │   └── DefaultAssignmentPreparationService.java # 默认任务单准备实现
    │   ├── DepartmentExecutionCoordinator.java # 部门执行协调接口，推进到员工执行通道
    │   ├── DepartmentExecutionResult.java # 部门执行派发结果，executionId、batchId、派发状态
    │   ├── EmployeeExecutionDispatch.java # 单个员工执行派发记录，dispatchId、目标 channel
    │   ├── impl/
    │   │   ├── ChannelBackedDepartmentExecutionCoordinator.java # 基于 ChannelManager 的执行协调器
    │   │   └── MinimalEmployeeTaskExecutor.java # 员工任务通道最小真实消费者
    │   ├── EmployeeExecutionReceipt.java # 员工执行回执结构，完成/失败状态、摘要
    │   ├── EmployeeExecutionReceiptService.java # 员工执行回执服务接口
    │   ├── impl/
    │   │   └── InMemoryEmployeeExecutionReceiptService.java # 内存版员工执行回执服务
    │   ├── AutonomyTraceService.java    # 自治流程追踪日志服务，分析、路由、分工、执行阶段
    │   └── AutonomyTraceEvent.java      # 自治 Trace 事件数据结构
    │
    ├── neuron/                          # 神经元接口、注册、执行、协调、视觉/路由/聊天神经元
    │   ├── Neuron.java                  # 神经元接口，统一生命周期和消息处理
    │   ├── NeuronState.java             # 神经元状态枚举
    │   ├── NeuronContext.java           # 神经元上下文，处理所需上下文
    │   ├── NeuronRegistry.java          # 神经元注册表接口
    │   ├── NeuronExecutor.java          # 神经元执行器，并发执行、调度策略
    │   ├── impl/
    │   │   ├── AbstractNeuron.java      # 神经元公共基类，公共状态、订阅、发布逻辑
    │   │   ├── NeuronCoordinator.java   # 神经元会话协调器，创建 session、绑定通道、感知/派发/响应
    │   │   ├── NeuronRegistryImpl.java  # 神经元注册实现
    │   │   ├── Qwen3Neuron.java         # 前台轻量聊天神经元，闲聊、轻量对话、兜底
    │   │   ├── BitNetNeuron.java        # BitNet 工具/低资源神经元
    │   │   ├── RouterNeuron.java        # 路由神经元，消息路由、意图转发
    │   │   ├── EyeNeuronImpl.java       # 视觉神经元，图像识别、视觉问答
    │   │   ├── SensorNeuron.java        # 传感器神经元，感知输入扩展
    │   │   └── ProjectDevelopmentNeuron.java # 项目开发神经元，项目开发型任务执行
    │   ├── chat/
    │   │   ├── ChatNeuronRouter.java    # 聊天神经元路由，普通聊天路由
    │   │   └── ChatIntentClassifier.java # 聊天意图分类，闲聊/业务/兜底分类
    │   └── fallback/
    │       └── FallbackHandler.java     # 神经元失败兜底，模型失败、工具失败兜底
    │
    ├── channel/                         # 通道通信，广播/单播/轮询/优先级
    │   ├── Channel.java                 # 通道接口
    │   ├── ChannelManager.java          # 通道管理接口，创建、订阅、发布、外部订阅者管理
    │   ├── impl/
    │   │   ├── ChannelManagerImpl.java  # 通道管理实现
    │   │   ├── BroadcastChannel.java    # 广播通道，一对多推送
    │   │   ├── UnicastChannel.java      # 单播通道，点对点消息
    │   │   ├── RoundRobinChannel.java   # 轮询通道，多执行者分发
    │   │   └── PriorityChannel.java     # 优先级通道，高优先级任务/告警
    │   ├── ChannelMessage.java          # 通道消息对象
    │   ├── ChannelMessageQueue.java     # 消息队列抽象，队列策略
    │   ├── ChannelPublisher.java        # 发布器接口
    │   ├── impl/
    │   │   └── ChannelPublisherImpl.java # 发布器实现
    │   └── ChannelSubscriber.java       # 订阅者接口，神经元或外部订阅者实现
    │
    ├── employee/                        # 统一员工模型、人类员工、数字员工、生命周期、注册表、薪酬
    │   ├── Employee.java                # 统一员工领域对象，人类员工和数字员工共性字段
    │   ├── EmployeeService.java         # 员工服务接口，查询、创建、状态更新
    │   ├── EmployeeRegistry.java        # 员工注册表接口
    │   ├── EmployeeLifecycleService.java # 员工生命周期管理接口
    │   ├── EmployeeCompensationService.java # 员工薪酬/激励服务接口
    │   ├── impl/
    │   │   ├── HumanEmployee.java       # 人类员工实现
    │   │   ├── DigitalEmployee.java     # 数字员工实现，执行、技能、状态
    │   │   ├── EmployeeServiceImpl.java # 早期/内存员工服务实现
    │   │   ├── JpaEmployeeServiceImpl.java # JPA 员工服务实现，持久化员工数据
    │   │   ├── InMemoryEmployeeRegistry.java # 内存员工注册表
    │   │   ├── EmployeeLifecycleServiceImpl.java # 员工生命周期管理实现
    │   │   ├── JpaEmployeeCompensationService.java # 薪酬 JPA 实现
    │   │   └── InMemoryEmployeeCompensationService.java # 内存薪酬实现
    │   ├── registry/
    │   │   └── FixedEmployeeRegistry.java # 固定数字员工注册表，32 个固定数字员工定义
    │   ├── neuron/
    │   │   └── EmployeeNeuron.java      # 员工和神经元适配，数字员工以神经元形式执行
    │   └── claim/
    │       └── TaskClaimService.java    # 任务认领服务，公开任务/抢单任务
    │
    ├── worker/                          # 数字工人抽象、工厂、生命周期、协作
    │   ├── DigitalWorker.java           # 数字工人抽象，偏执行工人
    │   ├── factory/
    │   │   ├── DigitalWorkerFactory.java # 数字工人工厂接口
    │   │   └── impl/
    │   │       └── DigitalWorkerFactoryImpl.java # 数字工人工厂实现
    │   ├── lifecycle/
    │   │   ├── LifecycleManager.java    # 工人生命周期管理接口
    │   │   └── impl/
    │   │       └── LifecycleManagerImpl.java # 工人生命周期实现，健康检查、启动/停止
    │   ├── collaboration/
    │   │   └── CollaborationService.java # 工人协作服务，多员工协作
    │   └── template/
    │       └── WorkerTemplate.java      # 工人模板，新员工模板化创建
    │
    ├── model/                           # 模型请求、响应、会话、模型管理、模型池、模型选择
    │   ├── ModelManager.java            # 模型管理接口，模型状态、会话、调用管理
    │   ├── impl/
    │   │   └── ModelManagerImpl.java    # 模型管理实现
    │   ├── ModelClient.java             # 模型客户端接口
    │   ├── impl/
    │   │   └── NamedPipeModelClient.java # 与 Python model daemon 通过 named pipe 通讯
    │   ├── ModelRequest.java            # 模型请求对象
    │   ├── ModelResponse.java           # 模型响应对象
    │   ├── ModelSession.java            # 模型会话对象
    │   ├── UsageTracker.java            # token 和用量统计，成本控制、计费、日志
    │   ├── TokenUsage.java              # Token 使用记录
    │   ├── pool/                        # 模型池：ProviderConfig、LlmModel、BrainModelAssignment、Resolver、Manager、Assigner、Repository、Client
    │   └── selector/                    # 大脑模型选择器，每个部门一个 Selector
    │
    ├── provider/                        # LLM/ASR/TTS Provider 抽象和实现
    │   ├── Provider.java                # 模型 Provider 接口，LLM/ASR/TTS 统一抽象
    │   ├── ProviderRegistry.java        # Provider 注册接口
    │   ├── impl/
    │   │   ├── ProviderRegistryImpl.java # Provider 注册实现
    │   │   ├── QwenProvider.java        # Qwen 模型 Provider
    │   │   ├── OllamaProvider.java      # Ollama Provider，本地模型
    │   │   ├── BitNetProvider.java      # BitNet Provider，低资源模型
    │   │   ├── AsrProvider.java         # ASR Provider，语音输入
    │   │   ├── TtsProvider.java         # TTS Provider，语音输出
    │   │   ├── ProviderFactory.java     # Provider 工厂，根据配置创建
    │   │   └── ResolvedBrainModelProvider.java # 根据模型池解析后的大脑 Provider
    │
    ├── tool/                            # 工具接口、注册、执行、企业工具、浏览器/爬虫/Office/外部平台
    │   ├── Tool.java                    # 工具接口，新工具实现此接口
    │   ├── ToolCall.java                # 工具调用请求
    │   ├── ToolResult.java              # 工具调用结果
    │   ├── ToolContext.java             # 工具上下文，用户、权限、会话、部门
    │   ├── ToolSchema.java              # 工具 schema，暴露给模型/大脑的工具定义
    │   ├── ToolRegistry.java            # 工具注册表接口
    │   ├── impl/
    │   │   ├── ToolRegistryImpl.java    # 工具注册实现
    │   │   ├── DefaultToolExecutor.java # 工具执行器，权限、Hook、调用分发
    │   │   ├── DockerTool.java          # Docker 操作工具，容器操作
    │   │   ├── ClaudeCliTool.java       # Claude CLI 工具，外部 CLI 执行
    │   │   ├── TraeTool.java            # Trae 工具，Trae 执行集成
    │   │   ├── WindowsAppTool.java      # Windows 应用自动化工具，Win32/桌面自动化
    │   │   ├── GitHubTool.java          # GitHub 工具，GitHub API
    │   │   ├── BrowserAutomationTool.java # 浏览器自动化工具
    │   │   ├── PlaywrightCrawlerTool.java # Playwright 爬虫工具
    │   │   ├── PdfTool.java             # PDF 处理工具
    │   │   ├── OfficeTool.java          # Office 文档处理工具
    │   │   ├── WebCrawlerTool.java      # Web 爬虫工具
    │   │   ├── TavilySearchTool.java    # Tavily 搜索工具
    │   │   └── SearXNGTool.java         # SearXNG 搜索工具
    │   ├── hook/
    │   │   └── ToolHookManager.java     # 工具调用前后 Hook，审计、审批、安全检查
    │   ├── enterprise/
    │   │   ├── FeishuTool.java          # 飞书工具，企业通讯录、通知
    │   │   ├── EnterpriseFeishuTool.java # 企业飞书工具
    │   │   ├── EmployeeFeishuTool.java  # 员工飞书工具
    │   │   ├── HrFeishuTool.java        # HR 飞书工具
    │   │   ├── GitLabTool.java          # GitLab 工具，代码仓库
    │   │   ├── JenkinsTool.java         # Jenkins 工具，CI/CD
    │   │   ├── JiraTool.java            # Jira 工具，任务/缺陷管理
    │   │   └── OpenProjectTool.java     # OpenProject 工具，项目管理
    │   └── worktree/
    │       ├── WorktreeManager.java     # Git worktree 管理接口
    │       └── impl/
    │           └── GitWorktreeManager.java # Git worktree 管理实现，代码任务隔离工作区
    │
    ├── skill/                           # 技能接口和技能上下文
    │   └── (技能接口定义，具体实现在 living-agent-skill 模块)
    │
    ├── knowledge/                       # 知识库、分层知识、知识持久化、专业知识种子
    │   ├── KnowledgeEntry.java          # 知识条目领域对象
    │   ├── KnowledgeManager.java        # 知识管理接口
    │   ├── impl/
    │   │   └── KnowledgeManagerImpl.java # 知识管理实现，增删改查、检索
    │   ├── KnowledgeBase.java           # 知识库接口
    │   ├── LayeredKnowledgeBase.java    # 分层知识库接口，L1/L2/L3
    │   ├── impl/
    │   │   └── LayeredKnowledgeBaseImpl.java # 分层知识库实现，神经元/部门/企业知识晋升
    │   ├── KnowledgePersistenceService.java # 知识持久化服务，PostgreSQL/Qdrant 存储
    │   ├── impl/
    │   │   ├── SQLiteKnowledgeBase.java # SQLite 知识库，本地轻量知识
    │   │   └── NativeKnowledgeBase.java # Native 知识库，Rust native 知识能力
    │   └── professional/
    │       └── ProfessionalKnowledgeSeeder.java # 专业知识初始化，预置知识导入
    │
    ├── memory/                          # 记忆接口、SQLite/Memos/MemPalace 后端
    │   ├── Memory.java                  # 记忆接口
    │   ├── MemoryEntry.java             # 记忆条目
    │   ├── MemoryBackend.java           # 记忆后端接口
    │   └── impl/
    │       ├── MemoryServiceImpl.java   # 记忆服务实现，对话/任务记忆读写
    │       ├── SQLiteMemoryBackend.java # SQLite 记忆后端，本地记忆
    │       ├── MemosMemoryBackend.java  # Memos 记忆后端，外部 Memos 服务
    │       └── MemPalaceBackend.java    # MemPalace 记忆后端，MemPalace 集成
    │
    ├── evolution/                       # 自主进化、信号、决策、执行、熔断、反馈、知识进化
    │   ├── signal/
    │   │   ├── EvolutionSignal.java     # 进化信号对象
    │   │   ├── SignalExtractor.java     # 信号提取接口，从错误/反馈/性能中提取
    │   │   └── DefaultSignalExtractor.java # 默认信号提取实现
    │   ├── engine/
    │   │   ├── EvolutionDecisionEngine.java # 进化决策接口，修复/优化/创新/上报判断
    │   │   ├── DefaultEvolutionDecisionEngine.java # 默认进化决策实现
    │   │   └── EvolutionOrchestrator.java # 进化总编排，定时进化、自动调整
    │   ├── executor/
    │   │   ├── EvolutionExecutor.java   # 进化执行器接口，执行修复或优化
    │   │   ├── EvolutionResult.java     # 进化结果对象
    │   │   └── EvolutionFeedbackService.java # 进化反馈服务，用户/系统反馈入库
    │   ├── scheduler/
    │   │   ├── EvolutionScheduler.java  # 进化调度接口，定时进化和失败重试
    │   │   └── EvolutionSchedulerImpl.java # 进化调度实现
    │   ├── circuitbreaker/              # 进化熔断，防止错误进化或高风险操作
    │   ├── memory/                      # 进化记忆图，进化历史和经验
    │   └── impl/
    │       ├── KnowledgeEvolverImpl.java # 知识进化实现，知识质量提升
    │       └── SkillGeneratorImpl.java  # 技能生成实现，自动生成技能
    │
    ├── autonomous/                      # 赚钱驱动、赏金、激励、支付、自主运营
    │   ├── config/
    │   │   └── AutonomousOperationConfig.java # 自主运营配置
    │   ├── bounty/                      # 赏金任务扫描、执行、Ledger、成本估算
    │   ├── bounty/impl/                 # GitHub/Freelance/BugBounty 扫描器、复合执行器
    │   ├── incentive/                   # 激励、积分账户、进化追踪
    │   ├── payout/                      # 支付账户、支付记录、支付服务
    │   ├── evolution/                   # 硬件升级、自主进化管理
    │   └── platform/                    # 外部平台集成
    │
    ├── approval/                        # 审批流、审批实例、计划审批
    │   ├── ApprovalService.java         # 审批服务接口
    │   ├── impl/
    │   │   └── ApprovalServiceImpl.java # 审批服务实现
    │   └── plan/                        # 计划审批服务，高风险计划执行前审批
    │
    ├── project/                         # 项目管理
    │   ├── ProjectService.java          # 项目服务接口
    │   ├── impl/
    │   │   └── ProjectServiceImpl.java  # 项目服务实现，长周期项目管理
    │   ├── Project.java                 # 项目领域对象
    │   └── ProjectPhaseRecord.java      # 项目阶段记录
    │
    ├── workflow/                        # 工作流编排、阶段 Handler、监控
    │   ├── WorkflowOrchestrator.java    # 工作流总编排，阶段式项目/任务流程
    │   ├── WorkflowExecution.java       # 工作流执行对象
    │   ├── WorkflowContext.java         # 工作流上下文，状态追踪
    │   ├── WorkflowMonitor.java         # 工作流监控和超时检查
    │   └── handlers/                    # 市场、需求、设计、开发、测试、部署、运维、售后阶段处理器
    │
    ├── planner/                         # 任务规划、DAG 任务
    │   ├── TaskPlanner.java             # 普通任务规划接口
    │   ├── impl/
    │   │   └── TaskPlannerImpl.java     # 任务规划实现，简单任务拆解
    │   └── dag/                         # DAG 任务服务、任务节点、状态
    │
    ├── sandbox/                         # 沙箱执行、Docker/Hybrid/Claude/Trae 执行网关
    │   ├── SandboxService.java          # 沙箱服务接口
    │   ├── impl/
    │   │   ├── DockerSandboxService.java # Docker 沙箱实现，容器隔离执行
    │   │   └── HybridSandboxService.java # 混合沙箱实现，本地/容器/远程
    │   ├── SandboxSession.java          # 沙箱会话接口
    │   ├── impl/
    │   │   └── SandboxSessionImpl.java  # 沙箱会话实现，生命周期
    │   ├── ExecutionResult.java         # 执行结果，stdout/stderr/exitCode
    │   ├── ClaudeExecutionGateway.java  # Claude 执行网关，外部 Coding Agent 集成
    │   └── TraeExecutionGateway.java    # Trae 执行网关，Trae 集成
    │
    ├── security/                        # 权限、认证、员工身份、声纹、会话、安全策略、命令安全
    │   ├── AuthContext.java             # 当前认证上下文
    │   ├── AuthContextService.java      # 认证上下文服务，用户身份、租户、权限传递
    │   ├── AccessLevel.java             # 访问级别定义
    │   ├── AutonomyLevel.java           # 自主级别定义
    │   ├── PermissionService.java       # 权限判断和访问记录接口
    │   ├── impl/
    │   │   └── PermissionServiceImpl.java # 权限服务实现，部门/资源权限
    │   ├── AccessGateService.java       # 访问网关接口，统一权限入口
    │   ├── impl/
    │   │   └── AccessGateServiceImpl.java # 访问网关实现
    │   ├── DepartmentAccessValidator.java # 部门访问校验
    │   ├── BrainAccessControl.java      # 大脑访问控制，用户能否访问某部门大脑
    │   ├── auth/
    │   │   ├── UnifiedAuthService.java  # 统一认证服务，session 创建、登录后身份
    │   │   ├── PhoneVerificationService.java # 手机验证码服务
    │   │   ├── FounderService.java      # 创始人状态和初始化
    │   │   ├── OAuthService.java        # OAuth 服务，钉钉/飞书/企微
    │   │   └── impl/                    # OAuth 实现
    │   ├── service/
    │   │   └── EnterpriseEmployeeService.java # 企业员工服务，企业通讯录/员工信息
    │   ├── sync/                        # 钉钉/飞书/HR 同步适配
    │   ├── session/                     # 会话实体、仓库、管理器，登录 session 持久化
    │   ├── voiceprint/                  # 声纹认证
    │   ├── speaker/                     # 说话人识别
    │   ├── bash/
    │   │   └── BashSecurityValidator.java # Bash 命令安全校验
    │   ├── impl/
    │   │   ├── SandboxExecutorImpl.java # 沙箱执行，命令/脚本隔离执行
    │   │   └── ContentValidatorImpl.java # 内容安全校验，内容审核、提示词安全
    │   └── SkillVetter.java             # 技能安全审核接口
    │   └── impl/
    │       └── SkillVetterImpl.java     # 技能安全审核实现，自动生成技能上线前检查
    │
    ├── database/                        # JPA 实体、Repository、数据库配置、向量服务、租户服务
    │   ├── config/
    │   │   ├── PostgreSQLConfig.java    # PostgreSQL 配置，数据源、JPA 配置
    │   │   └── QdrantConfig.java        # Qdrant 配置，向量库地址和客户端
    │   ├── entity/                      # JPA 实体
    │   │   ├── TenantEntity.java        # 租户实体
    │   │   ├── DepartmentEntity.java    # 部门实体
    │   │   ├── EnterpriseEmployeeEntity.java # 企业员工实体
    │   │   ├── FixedEmployeeDefinitionEntity.java # 固定员工定义实体
    │   │   ├── FixedEmployeePersonaEntity.java # 固定员工画像实体
    │   │   ├── FixedEmployeeProfileEntity.java # 固定员工档案实体
    │   │   ├── EmployeeEntity.java      # 员工实体
    │   │   ├── DepartmentChatMessageEntity.java # 部门聊天消息实体
    │   │   ├── KnowledgeEntryEntity.java # 知识条目实体
    │   │   ├── EvolutionResultEntity.java # 进化结果实体
    │   │   ├── EvolutionFeedbackEntity.java # 进化反馈实体
    │   │   ├── EvolutionAuditLogEntity.java # 进化审计日志实体
    │   │   ├── PerformanceAssessmentEntity.java # 绩效评估实体
    │   │   ├── PerformanceIndicatorEntity.java # 绩效指标实体
    │   │   ├── PerformanceTrendSnapshotEntity.java # 绩效趋势快照实体
    │   │   ├── CompensationAccountEntity.java # 薪酬账户实体
    │   │   ├── CompensationPlanEntity.java # 薪酬计划实体
    │   │   ├── CompensationRecordEntity.java # 薪酬记录实体
    │   │   ├── ConfigVersionEntity.java # 配置版本实体
    │   │   ├── BudgetAllocationEntity.java # 预算分配实体
    │   │   ├── BudgetTransactionEntity.java # 预算交易实体
    │   │   ├── SessionEntity.java       # 会话实体
    │   │   ├── SpeakerProfile.java      # 说话人档案
    │   │   └── UserProfileEntity.java   # 用户档案实体
    │   ├── repository/                  # Spring Data Repository，数据访问
    │   ├── service/
    │   │   └── TenantService.java       # 租户服务，多租户数据
    │   └── vector/
    │       ├── QdrantVectorService.java # Qdrant 向量服务，知识检索、向量写入
    │       └── QdrantVectorStore.java   # Qdrant 向量存储
    │
    ├── distributed/                     # Redis、Kafka、分布式缓存和消息
    ├── operation/                       # Dashboard、绩效、指标
    │   ├── dashboard/                   # CEO/企业 Dashboard 数据和 DTO
    │   ├── performance/                 # 绩效评估、指标、趋势、JPA/内存实现
    │   └── metrics/                     # 运行指标采集
    ├── proactive/                       # 主动预判、定时、事件、提醒、场景处理
    │   ├── predictor/
    │   │   ├── TimePredictor.java       # 时间预测器
    │   │   ├── EventPredictor.java      # 事件预测器
    │   │   ├── PatternPredictor.java    # 模式预测器
    │   │   └── RiskPredictor.java       # 风险预测器
    │   ├── suggestion/
    │   │   └── ProactiveSuggestionService.java # 主动建议服务
    │   ├── scheduler/
    │   │   └── ProactiveTaskScheduler.java # 主动任务调度
    │   ├── cron/
    │   │   ├── CronService.java         # Cron 定时任务接口
    │   │   └── impl/
    │   │       └── CronServiceImpl.java # Cron 定时任务实现
    │   ├── event/
    │   │   └── EventHookManager.java    # 事件 Hook 管理
    │   ├── alert/                       # 告警通知器
    │   ├── scenario/                    # 周报、入职、会议等主动场景
    │   └── digest/
    │       └── DailyDigestGenerator.java # 日报/摘要生成
    │
    ├── diagnosis/                       # 健康检查、健康问题、健康状态
    ├── compliance/                      # 合规规则、合规报告
    ├── intervention/                    # 人工干预、风险评估、影响分析
    ├── budget/                          # 预算服务、预算实体
    ├── heartbeat/                       # 心跳和定时唤醒
    ├── nativelib/                       # Java native 声明和 JNI 包装
    ├── embedding/                       # 嵌入服务和向量索引优化
    ├── deployment/                      # 分布式部署服务
    ├── anomaly/                         # 异常检测
    ├── scenario/                        # 场景处理器
    ├── ops/                             # 运行队列、任务结账等运营支撑
    ├── service/                         # 本地模型、ASR/TTS/声纹等服务接口
    └── util/                            # 工具类
```

---

## living-agent-skill 技能模块

```
living-agent-skill/
├── pom.xml                              # 技能模块依赖
└── src/
    ├── main/
    │   ├── java/com/livingagent/skill/
    │   │   ├── loader/
    │   │   │   ├── SkillLoader.java     # 从资源目录加载技能定义
    │   │   │   └── SkillLoadResult.java # 技能加载结果，加载状态和错误信息
    │   │   ├── model/
    │   │   │   └── SkillImpl.java       # 技能实现，技能元数据和执行适配
    │   │   └── registry/
    │   │       └── SkillRegistryImpl.java # 技能注册表实现，技能查找、注册、绑定
    │   └── resources/skills/            # 内置技能资源
    │       ├── tech/
    │       │   ├── code-review/
    │       │   │   └── SKILL.md         # 技术代码审查技能
    │       │   └── cicd-pipeline/
    │       │       └── SKILL.md         # CI/CD 技能，DevOps
    │       ├── sales/
    │       │   └── sales-automation/
    │       │       └── SKILL.md         # 销售自动化技能
    │       ├── legal/                   # 法务合同/合规技能
    │       └── cs/
    │           └── customer-portal/
    │               └── SKILL.md         # 客服门户技能
    └── test/
```

---

## living-agent-perception 感知模块

```
living-agent-perception/
├── pom.xml                              # 感知模块依赖
└── src/
    └── main/java/com/livingagent/perception/
        └── (视觉、音频、传感器等感知能力扩展，当前主要为预留模块)
```

---

## living-agent-native Rust Native 模块

```
living-agent-native/
├── Cargo.toml                           # Rust 项目配置
└── src/
    ├── lib.rs                           # Rust crate 入口，导出 JNI/native 能力
    ├── audio/                           # Opus、VAD、音频处理
    │   ├── processor.rs                 # 音频处理，语音链路
    │   ├── opus_codec.rs                # Opus 编解码，语音传输
    │   └── vad.rs                       # VAD 语音活动检测，ASR 前处理
    ├── channel/                         # Rust 高性能通道
    ├── compact/                         # 上下文压缩 native 能力
    ├── jni/                             # JNI 导出函数
    │   ├── audio_jni.rs                 # 音频 JNI 导出，对应 AudioNative.java
    │   ├── memory_jni.rs                # 记忆 JNI 导出，对应 MemoryNative.java
    │   ├── security_jni.rs              # 安全 JNI 导出，对应 SecurityNative.java
    │   └── compact_jni.rs               # 上下文压缩 JNI 导出，对应 CompactNative.java
    ├── knowledge/                       # SQLite/向量/知识缓存
    ├── memory/                          # 记忆后端
    └── security/
        └── bash_validator.rs            # Bash 命令安全校验，对应 BashSecurityValidator
```

---

## frontend 前端模块

```
frontend/
├── package.json                         # 前端依赖配置，React、Vite、UI 库等
├── vite.config.ts                       # Vite 构建配置
├── nginx.conf                           # Nginx 配置，生产环境静态服务和 API 代理
├── Dockerfile                           # 前端 Docker 镜像构建
├── tsconfig.json                        # TypeScript 配置
├── index.html                           # HTML 入口
└── src/
    ├── App.tsx                          # 前端路由入口，新增页面路由时修改
    ├── main.tsx                         # React 应用挂载入口
    ├── index.css                        # 全局样式，主题、布局基础
    │
    ├── pages/                           # 页面组件
    │   ├── Login.tsx                    # 登录页，手机号验证码、登录跳转
    │   ├── CompanySetup.tsx             # 企业初始化页
    │   ├── Layout.tsx                   # 主布局，用户信息、导航、通知
    │   ├── Dashboard.tsx                # 首页 Dashboard
    │   ├── PlatformDashboard.tsx        # 平台看板
    │   ├── Dashboard/
    │   │   └── EnterpriseDashboard.tsx  # 企业 Dashboard 子页面
    │   ├── Chat.tsx                     # 聊天页面，部门聊天/Agent 聊天入口
    │   ├── DepartmentDetail/            # 部门详情页组件集合
    │   │   ├── DepartmentDetail.tsx     # 部门详情主页面
    │   │   └── (办公室、员工工位、活动流、脑图/状态组件)
    │   ├── DepartmentDetail2.tsx        # 旧/备用部门详情页，如无引用应逐步清理
    │   ├── AgentCreate.tsx              # 创建 Agent 页面
    │   ├── AgentDetail.tsx              # Agent 详情页面
    │   ├── MyTasks.tsx                  # 我的任务页面
    │   ├── Projects.tsx                 # 项目管理页面
    │   ├── Approvals.tsx                # 审批页面
    │   ├── Plaza.tsx                    # 广场/公开任务页面
    │   ├── UserManagement.tsx           # 用户/员工管理页面
    │   ├── EnterpriseSettings.tsx       # 企业设置页面
    │   ├── OpenClawSettings.tsx         # OpenClaw 设置页面
    │   ├── BrainConfig.tsx              # 大脑模型配置页面
    │   ├── ModelPoolProviders.tsx       # 模型供应商管理页面
    │   ├── AdminCompanies.tsx           # 管理企业页面
    │   ├── SSOEntry.tsx                 # SSO 入口页面
    │   ├── ForgotPassword.tsx           # 找回密码页面
    │   └── ResetPassword.tsx            # 重置密码页面
    │
    ├── components/                      # 通用组件
    │   ├── FileBrowser.tsx              # 文件浏览器，查看生成产物/项目文件
    │   ├── ChannelConfig.tsx            # 通道配置组件
    │   ├── DigitalEmployeeSettings.tsx  # 数字员工设置组件
    │   ├── FixedEmployeeSettings.tsx    # 固定员工设置组件
    │   ├── HumanEmployeeSettings.tsx    # 人类员工设置组件
    │   ├── EvolvedEmployeeSettings.tsx  # 进化员工设置组件
    │   └── PublicTaskBoard.tsx          # 公开任务板组件
    │
    ├── services/                        # API 客户端
    │   ├── apiBase.ts                   # API 基础封装，baseUrl、认证头、错误处理
    │   ├── api.ts                       # 通用业务 API
    │   ├── fixedEmployeeApi.ts          # 固定员工 API，对应 FixedEmployeeController
    │   ├── dashboardApi.ts              # Dashboard API，对应 DashboardController
    │   ├── modelPoolApi.ts              # 模型池 API，对应 ModelPoolController
    │   ├── brainModelApi.ts             # 大脑模型配置 API，对应 BrainModelConfigController
    │   └── officeExtendedApi.ts         # 虚拟办公室扩展 API，对应 OfficeController
    │
    ├── stores/                          # Zustand 状态管理
    │   └── index.ts                     # 全局状态，当前用户、认证状态、UI 状态
    │
    ├── hooks/                           # React hooks
    │   └── useIdleTimeout.ts            # 空闲超时 Hook，自动登出、会话超时
    │
    ├── i18n/                            # 国际化
    │   ├── zh.json                      # 中文文案
    │   └── en.json                      # 英文文案
    │
    ├── types/                           # TypeScript 类型定义
    │   └── index.ts                     # 通用类型，全局 DTO 类型
    │
    └── utils/                           # 工具函数
        └── theme.ts                     # 主题工具，明暗主题、颜色变量
```

---

## scripts/python Python 脚本和模型守护进程

```
scripts/python/
├── model_daemon.py                     # 模型守护进程，处理 named pipe 控制和会话请求，与 Java NamedPipeModelClient 配合
├── llm/
│   └── run_qwen35.py                   # Qwen 推理入口，本地 Qwen 模型路径和推理参数
├── speaker/                            # 声纹/说话人相关脚本，语音身份识别辅助
└── models.md                           # 本地模型说明文档
```

---

## init-db 数据库初始化

```
init-db/
├── 01_init.sql                         # 初始化数据库，初次启动基础表/数据
└── 02_openproject.sh                   # OpenProject 初始化脚本
```

---

## documents 企业知识源

```
documents/
├── _meta/                              # 命名规范、分类、访问规则
├── shared/
│   └── company/                        # 公司级制度、数字员工职责卡、需求路由规则
├── governance/                         # 治理制度
└── department/                         # 各部门制度文档
    ├── tech/                           # 技术部制度、流程、模板、runbook
    ├── hr/                             # 人力资源制度、流程、记录、模板
    ├── finance/                        # 财务制度、报表、流程、模板
    ├── sales/                          # 销售制度、提案、流程、模板
    ├── cs/                             # 客服制度、话术、流程、模板
    ├── legal/                          # 法务制度、合同、流程、模板
    └── ops/                            # 运营制度、清单、流程、模板
```

---

## docs 技术文档

```
docs/
├── README.md                           # 文档入口
├── CODE_STRUCTURE_AND_FILE_GUIDE.md    # 代码结构和文件功能索引（本文档的参考源）
├── DOCKER_SERVICE_LOG_ISSUES_AND_FIXES.md # Docker 服务日志问题和解决方案
├── 对话入口逻辑梳理.md                  # 对话入口和链路梳理
├── 权限与入口矩阵.md                    # 权限和入口矩阵
├── planning/                           # 规划和改进方案
├── analysis/                           # 分析文档
├── adr/                                # 架构决策记录
├── old/                                # 旧版文档归档
├── guides/                             # 操作指南
└── references/                         # API 参考
```

---

## Docker 与部署文件

```
docker/living-agent-service/
├── docker-compose.yml                  # 本地整体编排：后端、前端、PostgreSQL、Redis、Kafka、Qdrant、Memos、OpenProject 等
├── .dockerignore                       # Docker 构建忽略规则
├── .gitignore                          # Git 忽略规则
├── Dockerfile.rust-native              # Rust native 构建镜像
├── image/
│   ├── Dockerfile.system-deps          # 系统依赖镜像
│   ├── download_images.py              # 镜像下载辅助，离线镜像准备
│   └── load_images.ps1                 # Windows 加载镜像脚本，本地导入镜像
├── init-db/
│   ├── 01_init.sql                     # 初始化数据库
│   └── 02_openproject.sh               # OpenProject 初始化
└── data/                               # 本地运行数据挂载目录
```

---

## 各模块间依赖关系图

```
┌─────────────────────────────────────────────────────────────┐
│                        frontend (React)                      │
│                  用户界面、页面、组件、状态                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP / WebSocket
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  living-agent-gateway                        │
│           Controller / WebSocket / Service                   │
│         接收前端请求，转换为 core 能力调用                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ living-agent-  │  │ living-agent- │  │ living-agent- │
│     core       │  │    skill      │  │  perception   │
│  核心业务领域   │  │  技能系统     │  │   感知预留     │
└───────┬───────┘  └───────────────┘  └───────────────┘
        │
        ▼
┌───────────────┐     ┌───────────────┐
│ living-agent- │     │  scripts/     │
│    native     │◄───►│   python/     │
│  Rust/JNI     │     │ model_daemon  │
└───────────────┘     └───────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│              PostgreSQL / Redis / Kafka / Qdrant             │
│                    数据存储与消息中间件                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 关键数据流向

| 流向 | 说明 | 关键文件 |
|------|------|----------|
| 用户发送部门聊天消息 | 前端 → WebSocket → 部门大脑 → 返回 | `Chat.tsx` → `DepartmentWebSocketHandler` → `DepartmentChatService` → `ConversationOrchestrator` → `Brain` |
| 用户发送 Agent 消息 | 前端 → WebSocket → AgentService → 神经元 → 模型 | `Chat.tsx` → `AgentWebSocketHandler` → `AgentService` → `NeuronCoordinator` → `NamedPipeModelClient` |
| 任务创建与流转 | 对话识别 → 任务规划 → 工作流 → 项目 | `ConversationOrchestrator` → `TaskPlanner` → `WorkflowOrchestrator` → `ProjectService` |
| 模型调用 | Brain/Neuron → Provider → 模型服务 | `AbstractBrain` → `Provider` → `model_daemon.py` 或外部 API |
| 工具调用 | Brain 请求工具 → ToolExecutor → 外部系统 | `Brain` → `ToolRegistry` → `DefaultToolExecutor` → `enterprise/*` |
| 进化反馈 | 用户反馈 → 进化系统 → 自动调整 | `EvolutionFeedbackBridgeService` → `EvolutionOrchestrator` → `EvolutionExecutor` |

---

## 模块修改优先级速查

| 想改什么 | 首选模块/目录 | 不建议位置 |
|----------|--------------|-----------|
| 部门聊天消息流程 | `gateway/websocket/DepartmentWebSocketHandler`、`gateway/service/DepartmentChatService`、`core/autonomy` | `AgentService`、`Qwen3Neuron` |
| 部门大脑回复逻辑 | `core/brain/impl/*Brain.java`、`AbstractBrain.java`、`DynamicPromptBuilder.java` | Controller |
| 对话是否任务/路由到哪个部门 | `core/autonomy/DialogueAnalyzer.java`、`ConversationOrchestrator.java`、`BrainRegistryImpl.java` | 前端或 WebSocket Handler |
| 部门大脑分派数字员工 | `core/brain/collaboration`、`core/employee`、`core/worker`、`core/planner/dag` | Controller |
| 普通 Agent/语音会话 | `gateway/service/AgentService.java`、`AgentWebSocketHandler.java`、`NeuronCoordinator.java`、`NamedPipeModelClient.java` | `DepartmentChatService` |
| 模型池/Provider | `core/model/pool`、`core/provider`、`gateway/controller/ModelPoolController.java` | Brain 内硬编码 |
| 大脑模型选择 | `core/model/selector`、`gateway/controller/BrainModelConfigController.java` | `application.yml` 硬编码每个大脑 |
| 工具调用 | `core/tool`、`ToolRegistryImpl.java`、`DefaultToolExecutor.java` | Brain 里直接 HTTP 调用 |
| 外部企业平台 | `core/tool/impl/enterprise`、对应 Controller/Service | 新建散乱 util |
| 权限问题 | `core/security`、`gateway/interceptor`、`gateway/config/SecurityConfig.java` | 页面里只隐藏按钮 |
| 员工/组织 | `core/employee`、`database/entity`、`gateway/service/OrganizationQueryService` | 前端假数据 |
| 固定数字员工 | `core/employee/registry/FixedEmployeeRegistry.java`、`FixedEmployeeController.java`、`FixedEmployee*Entity`、`documents/shared/company` | 各部门 Brain 内硬编码 |
| 任务/项目 | `core/planner`、`core/workflow`、`core/project`、`TaskController`、`ProjectController` | 只保存聊天记录 |
| 审批 | `core/approval`、`ApprovalController.java` | 工具里直接跳过 |
| 绩效/贡献 | `core/operation/performance`、`TaskPerformanceBridgeService.java` | Controller 里临时统计 |
| 知识库 | `core/knowledge`、`KnowledgeController.java`、`documents` | prompt 字符串写死 |
| 长期记忆 | `core/memory` | 聊天表临时拼接 |
| 进化系统 | `core/evolution`、`EvolutionAdminController.java` | 定时任务里临时逻辑 |
| Docker unhealthy | `docker-compose.yml`、对应服务 Dockerfile | Java 业务代码 |
| Native 加载失败 | `living-agent-native`、`core/nativelib`、`Dockerfile`、`entrypoint.sh` | 业务 Service 捕获后忽略 |
| 前端页面 | `frontend/src/pages` | `dist` |
| 前端 API 调用 | `frontend/src/services` | 页面组件内散落 fetch |
| 前端类型 | `frontend/src/types` | 多页面重复定义 |
