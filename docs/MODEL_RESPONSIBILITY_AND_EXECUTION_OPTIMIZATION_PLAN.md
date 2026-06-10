# Living Agent 模型职责与执行闭环可落地实施方案

> 版本：2026-06-05 最优流程升级版（含分层自治路由、员工自行领取、部门内审查闭环、部门级聚合交付、主脑统一收口完整设计）
> 范围：`docker/living-agent-service`  
> 依据：`docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`、`docs/LLM_AUTONOMY_HARDCODE_ANALYSIS.md`、`docs/CODE_LOGIC_LANDING_VERIFICATION.md`、当前代码实现  
> 目标：将"用户请求 → 轻量路由 → 大脑规划 → 部门/员工自治执行 → 部门内审查闭环 → 部门级聚合 → 主脑收口 → 知识/绩效沉淀"拆成可直接按文件修改的工程方案。

---

## 1. 总体目标与边界

系统需要从“能回答”升级为“能组织企业数字员工完成任务”。以下仅使用一个动态网页任务作为验收样例，不能把样例中的颜色、形状、员工、文件内容、任务类型写死到代码或 Prompt 中。目标链路如下：

```text
用户请求
-> 轻量路由判断（单部门 or 跨部门？）
-> 单部门任务：直达部门大脑
-> 跨部门任务：主脑识别意图并拆解任务 -> 分发到各部门大脑
-> 部门大脑分析任务，整理出部门级待办
-> 部门内固定员工自行领取待办（窗口期 + 大脑兜底指派）
-> 员工执行任务并产出成果
-> 部门内审查闭环（编写 -> 审查 -> 修改 -> ... -> 审查通过标记完成）
-> 部门大脑聚合分析部门内交付成果（不合格发回，合格交付）
-> 主脑汇总各部门成果 -> 统一回复用户
-> 知识、绩效、产物记录沉淀
```

### 1.1 必须遵守的代码落点

按 `CODE_STRUCTURE_AND_FILE_GUIDE.md`，本方案明确以下落点，避免重复造服务：

| 能力 | 代码落点 | 禁止重复点 |
| --- | --- | --- |
| 自治编排、任务规划、员工分派、回执、验收 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/` | 不在 gateway 新建并行编排引擎 |
| 轻量路由、自行领取、审查闭环、部门聚合 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/` (含 review/ aggregation/ 子包) | 不在 gateway 或 brain 层重复实现编排逻辑 |
| 部门文本聊天主链路 | `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java` | 不塞回 `AgentService` |
| 部门 WebSocket | `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | 不走 `AgentWebSocketHandler` |
| REST 查询/文件接口 | `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/` | 新接口先查已有 Controller |
| Bean 注册 | `living-agent-gateway/src/main/java/com/livingagent/gateway/config/GatewayConfig.java` | 不在业务方法里手动 new 核心组件 |
| 模型池/模型选择 | `living-agent-core/src/main/java/com/livingagent/core/model/pool/` | 不在业务类硬编码模型名 |
| 工具执行/工具注册 | `living-agent-core/src/main/java/com/livingagent/core/tool/` | 不绕过权限与 ToolRegistry |
| 沙箱执行 | `living-agent-core/src/main/java/com/livingagent/core/sandbox/` | 不在员工消费者里直接执行危险命令 |
| 知识治理 | core `knowledge/` + gateway `Knowledge*Service` | 不把原始任务结果直接当高质量知识 |
| 绩效沉淀 | core `operation/performance/` + gateway bridge/service | 不在未验收完成时记录完成绩效 |
| 跨部门协调 | `core/autonomy/CrossDepartmentCoordinator.java` + `core/brain/impl/MainBrain.forwardToDepartment()` | 不在部门大脑间直接耦合调用 |

---

## 2. 当前完成状态

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| 主脑/部门脑规划 | 基本完成 | LLM-first 已接入；规则只能用于暴露不可执行原因，不能替代 LLM 伪造业务决策或产物 |
| 模型职责和降级链 | ✅ 已完成 | 员工模型解析链已支持员工专属、部门选择、部门大脑配置；`resolveDefault()` 三级降级（configured→enabled→null），无硬编码URL；BrainAutoAssigner 启动自动分配；AbstractBrain 动态 Provider 解析；**模型调用失败自动降级闭环已实现**（详见 3.3.2.1） |
| 模型运行时可用性 | ✅ 已完成 | Ollama `/api/tags` 发现和 60 秒缓存已接入；启动时异步执行性能测试，不可用模型自动禁用；ModelHealthRegistry 已接入 ClaudeProxyService 调用链；**降级日志已改进**（显示 fallback 模型尝试过程） |
| WebSocket 稳定性 | 基本完成 | 单 session 发送串行化、异常 session 清理、query 脱敏已完成 |
| 固定员工派发 | 基本完成 | 任务单准备、派发到员工 task channel 已接入 |
| 回执通道生命周期 | 基本完成 | 派发前创建 receipt channel，注册 execution，并订阅 Trace |
| 回执驱动最终回复 | 基本完成 | 5 秒短等待、receipt 聚合、completion gate、execution 查询 API 已接入 |
| Artifact 文件产物 | 基本完成 | 已写入 `data/artifacts/{department}/{executionId}/`，HTML 可从 LLM 输出提取 |
| Artifact 下载/预览 | 基本完成 | `AgentFileController` 已支持 artifact root 内安全 listing/read/write/delete/upload/preview/download |
| 知识/绩效沉淀 | 基本完成 | DefaultKnowledgeCaptureService/DefaultPerformanceCaptureService 已实现，受 completion gate 保护，在 DepartmentChatService 中被调用 |
| 员工真实工具执行 | ✅ 已完成（第一版） | 新增 FILE_SYSTEM_QUERY 执行能力 + TOOL_EXECUTION 执行模式 + executeToolTask 工具调用分支；ToolBackedEmployeeTaskExecutor 可通过 ToolRegistry 查找并调用真实 Tool 实现（如 FileEditTool.list_dir）；MainBrain prompt 已加入可用工具清单 |
| Docker 沙箱 | 待推进 | 当前容器内 DockerSandboxService 仍不可用 |
| 大脑模型自动分配 | ✅ 已完成 | `BrainAutoAssigner` 启动时幂等自动分配最佳模型到9个大脑；评分算法含 recommended/contextWindow/bestFor/performanceScore；手动触发端点 POST /auto-assign-brains |
| 大脑动态 Provider 解析 | ✅ 已完成 | `AbstractBrain.executeReActLoop()` 中 getProvider()==null 时通过 brainModelResolver 动态解析 ResolvedBrainModelProvider；解决部门聊天流程 Provider 为 null 问题 |
| 主脑 LLM 二次总结 | ✅ 已完成 | `LlmMainBrainFinalSummaryService`（LLM主实现）+ `DefaultMainBrainFinalSummaryService`（数据组装降级）双模式；接入 DepartmentChatService 的 MAIN_BRAIN_COMPOSE 策略 |

---

## 2.1 本轮实施进度更新（2026-05-13）

### 阶段3：模型超时、熔断和重试 ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/model/pool/ModelHealthRegistry.java` | ✅ 新增，记录模型成功/失败/超时/熔断状态，支持 AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN 五种状态 | 已完成 |
| `core/model/pool/BrainModelResolver.java` | ✅ 增加 `ModelHealthRegistry` 依赖，模型选择时增加熔断过滤；冷却中模型只允许按模型池配置切换到等价候选，不能生成硬编码业务结果 | 已完成 |
| `gateway/config/GatewayConfig.java` | ✅ 注册 `ModelHealthRegistry` Bean（cooldown=5min, failureThreshold=3, recoveryThreshold=2） | 已完成 |

### 阶段4：长任务异步进度推送 ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/autonomy/EmployeeExecutionReceiptService.java` | ✅ 新增 `ReceiptListener` 内部接口，支持 `addReceiptListener`/`removeReceiptListener` | 已完成 |
| `core/autonomy/impl/FileBasedEmployeeExecutionReceiptService.java` | ✅ 实现 listener 机制，`recordReceipt` 后自动通知所有 listener，支持 execution 结果缓存 | 已完成 |
| `gateway/websocket/DepartmentWebSocketHandler.java` | ✅ 新增 `pushExecutionProgress()` 方法，支持 `execution_progress` WebSocket 消息类型推送 | 已完成 |

### 阶段5：MainBrain LLM 最终二次总结 ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/autonomy/MainBrainFinalSummaryService.java` | ✅ 新增接口，定义 `FinalSummaryResult` record 和 `generateSummary()` 方法 | 已完成 |
| `core/autonomy/impl/LlmMainBrainFinalSummaryService.java` | ✅ 新增，调用 MainBrain LLM 生成结构化总结；LLM 失败时只能返回“无法完成总结/需重试”的问题状态，不能用模板替代主脑业务判断 | 需按禁硬编码原则复核 |
| `core/autonomy/impl/DefaultMainBrainFinalSummaryService.java` | ✅ 已验证可接受 | 基于 real execution data 的模板组装器（taskPlan/receiptSummary/artifactRecords/completionGateResult），仅在 LLM 不可用时降级使用；summary_source=fallback_composer 可追踪；不含任何假业务判断 | 已落地 |
| `gateway/config/GatewayConfig.java` | ✅ 已验证可接受 | LLM 实现为主 + fallback 降级为辅的标准容错模式；fallback 不伪造业务产物，仅组装已有执行数据 | 已落地 |

### 阶段6：执行结果验收层 ✅ 已完成

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `core/autonomy/ExecutionReceiptReviewer.java` | ✅ 已存在 | 接口定义完成 |
| `core/autonomy/ExecutionReviewResult.java` | ✅ 新增 | 评审结果 record，支持 passed/needsRework/failed 三种状态，包含 issues/suggestions/redispatch 判断 |
| `core/autonomy/impl/LlmExecutionReceiptReviewer.java` | ✅ 已存在 | LLM 语义验收实现 |
| `core/autonomy/impl/DefaultExecutionReceiptReviewer.java` | ⛔ 不建议实施为业务验收 | 程序只能做文件存在、大小、MIME、路径安全等机械检查；是否满足用户目标必须由 LLM reviewer 或人工审核判断，不能硬编码业务规则 |

### 阶段7：员工工具授权与真实工具执行 ✅ 第一版已完成

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `core/autonomy/EmployeeTaskExecutor.java` | ✅ 新增接口 | 定义 `ExecutionResult` 和 `ArtifactFile` record，支持按任务类型分发执行 |
| `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | ✅ 新增实现 | 支持 web_prototype/web_development/document_generation/data_analysis/review 等任务类型，生成真实文件产物 |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | ✅ 已修改 | 构造函数增加 `EmployeeTaskExecutor` 依赖；LLM 文本不得作为“完成”fallback，只能作为真实执行器的一部分或失败原因记录 |
| `gateway/config/GatewayConfig.java` | ✅ 已注册 | `employeeTaskExecutor` Bean 已注册，支持 SandboxService 可选注入 |

---

## 2.2 后续迭代实施进度（2026-05-13 第二轮）

### 迭代1：DynamicEmployeeTaskConsumerRegistry 接入真实 EmployeeTaskExecutor ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | ✅ 新增构造函数重载接受 `EmployeeTaskExecutor`，新增 `executeWithToolExecutor()` 方法，优先调用工具执行器 | 已完成 |
| `gateway/config/GatewayConfig.java` | ✅ 修改 `dynamicEmployeeTaskConsumerRegistry` Bean 注册，传入 `employeeTaskExecutor` 依赖 | 已完成 |

### 迭代2：DockerSandboxService 集成到工具执行器 ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | ✅ 新增 `SandboxService` 依赖注入，新增 `executeWebTaskInSandbox()` 方法，支持 DOCKER_SANDBOX 执行环境 | 已完成 |
| `gateway/config/GatewayConfig.java` | ✅ 修改 `employeeTaskExecutor` Bean 注册，通过 `Optional<SandboxService>` 可选注入，Docker 不可用时自动降级到 artifact-only 模式 | 已完成 |

### 迭代3：Artifact 数据库持久化和专用 API ✅ 已完成

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `core/database/entity/ArtifactRecordEntity.java` | ✅ 新增 JPA 实体，支持 artifactId/executionId/department/employee/type/path/size/sha256/metadata 等字段 | 已完成 |
| `core/database/repository/ArtifactRecordRepository.java` | ✅ 新增 Repository，支持按 executionId/department/employee/type 查询，分页和统计 | 已完成 |
| `core/autonomy/ArtifactRecordService.java` | ✅ 接口扩展，新增 `recordArtifact`/`getArtifact`/`getByExecutionId`/`getByDepartment`/`getByEmployeeCode`/`getByDepartmentAndType`/`getByType`/`getAllOrderByCreatedAtDesc`/`countByExecutionId`/`countByDepartment`/`exists`/`scanAndIndexDirectory` 方法 | 已完成 |
| `core/autonomy/ArtifactRecord.java` | ✅ record 增加 `sizeBytes` 和 `sha256` 字段 | 已完成 |
| `core/autonomy/impl/JpaArtifactRecordService.java` | ✅ 新增 JPA 实现，支持数据库持久化、查询、分页、目录扫描索引 | 已完成 |
| `core/autonomy/impl/InMemoryArtifactRecordService.java` | ✅ 更新以匹配新接口方法签名 | 已完成 |
| `gateway/controller/ArtifactController.java` | ✅ 新增专用 REST API，支持列表/详情/下载/预览/统计/重新索引 | 已完成 |
| `core/resources/db/migration/V8__artifact_records.sql` | ✅ 新增 Flyway 迁移脚本，创建 artifact_records 表及索引 | 已完成 |

### 迭代4：固定员工数据库治理 ✅ 已存在（无需额外实施）

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `core/database/entity/FixedEmployeeDefinitionEntity.java` | ✅ 已存在 | 固定员工定义实体，支持 code/name/title/department/neuronId/capabilities/tools/personality/active 等字段 |
| `core/database/entity/FixedEmployeeProfileEntity.java` | ✅ 已存在 | 固定员工画像实体 |
| `core/database/entity/FixedEmployeePersonaEntity.java` | ✅ 已存在 | 固定员工外观实体 |
| `core/database/repository/FixedEmployeeDefinitionRepository.java` | ✅ 已存在 | 支持 `findByActiveTrueOrderByCodeAsc` / `findByDepartmentCodeAndActiveTrueOrderByCodeAsc` |
| `core/employee/registry/FixedEmployeeRegistry.java` | ⚠️ 低优先治理 | `registerDefinitionsFromDatabase()` 已优先数据库加载（代码正确）；数据库为空时 fallback 到32个静态业务员工并 warn 日志记录；当前行为对首次部署/演示场景友好，生产环境建议增加配置开关控制是否允许静态 fallback 或要求必须从 DB 加载 |
| `core/resources/db/migration/V5__fixed_employee_persistence.sql` | ✅ 已存在 | 固定员工持久化迁移 |
| `core/resources/db/migration/V7__fix_fixed_employee_identifiers.sql` | ✅ 已存在 | 标识符修复迁移 |

**固定员工数据库治理已落地的能力**：
- ✅ 启动时优先从数据库加载启用态定义（`registerDefinitionsFromDatabase()`）
- ✅ 数据库为空时自动 fallback 到静态配置（32 个固定员工）
- ✅ 员工创建使用确定性 ID（`IdUtils.neuronToEmployeeId()`）
- ✅ 工具授权按部门 allowlist 过滤（`loadToolsForEmployee()`）
- ✅ 工具 alias 映射支持（`TOOL_ALIAS` 常量）
- ✅ 严格模式开关（`strictToolValidation` 配置项）
- ⚠️ 数据库为空时的 fallback 行为：当前 warn + 静态加载（生产环境建议可配置阻断）

---

## 2.3 Docker 后端日志核验问题清单（2026-05-14）

> 核验命令：`docker logs --tail 500 living-agent-service`  
> 核验样例：WebSocket 技术部门请求“帮我做一个红色小球跳动的游戏网页”  
> 结论：主脑 LLM 规划、部门路由、LLM 员工派发、工具执行器入口和 receipt 写入已有运行证据，但仍不完全符合本文端到端落地验收要求，尤其是回执订阅时序、artifact 类型/路径、最终聚合与主脑收口、WebSocket 稳定性、部门脑绑定等问题需要继续修复。

### 2.3.1 已符合的运行证据

| 验收点 | 日志证据 | 结论 |
| --- | --- | --- |
| LLM-first 入口识别 | `LlmBasedDialogueAnalyzer` 输出 `kind=TASK, intent=develop_game_webpage, primaryDept=tech`，并写入 `stage=intake_classified` | ✅ 基本符合 |
| 主脑 LLM 规划 | `LlmBasedMainBrainTaskDirector` 输出 `LLM-generated task plan`，Trace 包含 `stage=main_brain_planned`、`director_type=llm_based` | ✅ 基本符合 |
| 部门路由与计划 | Trace 包含 `brain_routed`、`department_plan_created`，目标部门为 `tech`，验收标准包含红色小球持续上下跳动 | ✅ 基本符合 |
| 员工 LLM 派发 | `LlmBasedFixedEmployeeDispatcher` 输出 `LLM dispatched 1 employees for department tech`，Trace 包含 `employee_assignment_planned`、`dispatcher_type=llm_based` | ✅ 基本符合 |
| 工具执行器入口 | `DynamicEmployeeTaskConsumerRegistry` 输出 `Using ToolBackedEmployeeTaskExecutor for employee T09` | ✅ 已走真实执行器入口 |
| Docker 不可用降级 | 员工任务日志显示 `env=ARTIFACT_ONLY` | ✅ 符合 Docker 不可用时显式降级要求 |
| receipt 写入 | `FileBasedEmployeeExecutionReceiptService` 输出 `Persisted 1 receipts`，员工回执状态为 `COMPLETED` | ✅ 部分符合 |
| 固定员工启动 | 启动日志显示 `FixedEmployeeRegistry neurons created: 32 active neurons`、绑定校验 32/32 | ✅ 基本符合 |

### 2.3.2 不符合落地要求的问题

| 编号 | 问题 | 日志证据 | 影响 | 优先级 | 建议落点 |
| --- | --- | --- | --- | --- | --- |
| P0-1 | receipt channel 订阅时序错误，员工完成回执先广播，trace/execution 订阅后注册 | `Broadcasting message ... to 0 subscribers`、`deliverToSubscribers ... subscriberCount=0` 之后才出现 `Registered execution ...`、`External subscriber receipt-trace-... subscribed` | 违反“派发前创建 receipt channel，注册 execution，并订阅 Trace”；可能导致 `employee_execution_receipt_received`、`execution_receipts_aggregated` 缺失，最终聚合等待不到已完成回执 | P0 | `core/autonomy/impl/DepartmentExecutionCoordinator`、`core/autonomy/impl/FileBasedEmployeeExecutionReceiptService`、`core/channel/impl/ChannelManagerImpl` |
| P0-2 | 端到端 Trace 未看到最终聚合、验收、artifact 记录、主脑最终收口 | 本次日志只到 `employee_execution_started`、员工 `sent receipt: ... COMPLETED`；未见 `employee_execution_receipt_received`、`execution_receipts_aggregated`、`artifact_recorded`、`main_brain_finalized` | 不满足 5.1 Trace 验收与 5.2 结果验收；用户最终回复可能无法包含执行员工、artifact 路径、验收结论和下一步 | P0 | `gateway/service/DepartmentChatService.java`、`core/autonomy/MainBrainFinalSummaryService.java`、`core/autonomy/ExecutionReceiptReviewer.java` |
| P0-3 | Artifact 生成不符合“红色小球网页”文件产物验收 | 日志显示 `Artifact file saved: data/artifacts/T09/.../result.txt`，不是 `data/artifacts/tech/{executionId}/` 下的 HTML 或多文件项目 | 不满足 5.3 文件产物验收；无法直接预览/下载可运行 HTML，且路径未按 department/executionId 组织 | P0 | `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java`、`core/autonomy/impl/JpaArtifactRecordService.java`、`gateway/controller/ArtifactController.java` |
| P0-4 | 员工 receipt 显示 `COMPLETED`，但员工没有真正完成用户要求的“做网页”工作 | 日志显示 `Employee T09 (真绘) sent receipt: executionId=6dbbb548-59f0-4d08-87c6-ed2801387c4e, status=COMPLETED, outcomeStatus=COMPLETED`，但前序执行实际进入 `Executing generic task`，只保存 `result.txt`，未生成可运行网页，也未看到工具调用、沙箱执行、文件验收或 completion gate 通过证据 | 当前 `COMPLETED` 只是执行器流程状态，不等价于业务验收完成；员工执行缺少“真实工具动作 + 产物质量验收 + 失败降级为 FAILED/NEEDS_REWORK”的闭环，容易造成“看起来完成、实际没做事”的假阳性 | P0 | `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java`、`core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java`、`core/autonomy/ExecutionReceiptReviewer.java`、`core/autonomy/impl/DefaultExecutionReceiptReviewer.java` |
| P0-5 | ~~任务类型归一化缺失~~ ✅ 已修复：新增 ExecutionCapabilityResolver 体系，`game_development` 未映射到可执行标准类型 | 最新日志出现 `ToolBacked execution failed ... Unsupported or unclassified task type: game_development`，员工已收到任务但在 `normalizeTaskType()` 直接抛错 | 导致主脑/部门脑任务规划已成功、员工执行却在入口失败，无法进入工具执行或生成产物闭环；用户会看到任务执行失败而不是正常完成 | P0 | `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java`、`core/autonomy/DecisionContextBuilder`、`LlmBasedMainBrainTaskDirector` 的 taskType schema |
| P0-6 | ~~需求明确性判断位置错误~~ ✅ 已修复：新增 RequirementReadinessEvaluator 前置检查，员工分派发生在主脑确认需求之前 | 当前链路表现为 `MainBrainTaskDirector` 先生成计划、`FixedEmployeeDispatcher` 先分派员工，再由 `AssignmentReadinessEvaluator` 返回 `NEEDS_CLARIFICATION/PARTIALLY_READY` | 会导致“先分配下去，再问需求，再分配”的混乱循环；用户被重复追问，taskKey/executionId 容易分裂，员工可能基于不完整需求开工 | P0 | `ConversationOrchestrator`、`MainBrainTaskDirector`、新增 `RequirementReadinessEvaluator` / `MainBrainRequirementClarifier`、`DepartmentChatService` |
| P1-1 | 任务类型中文“前端游戏开发”未被工具执行器识别为 web prototype/web development，落入 generic task | 日志显示 `Executing task: type=前端游戏开发` 后进入 `Executing generic task` | 真实工具执行第一版仍有类型映射缺口，导致只能生成 `result.txt`，无法按网页任务模板生成 HTML/CSS/JS | P1 | `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java`、`core/autonomy/DecisionContextBuilder`、`LlmBasedMainBrainTaskDirector` 的 taskType schema |
| P1-2 | 部分员工 delegateBrain 绑定异常，多个非财务部门员工绑定到 `finance-brain` | 启动日志多次出现 `employee://digital/tech/frontend/001 -> delegateBrain neuron://finance/finance-brain/001`、`sales/legal/cs/admin/main/ops` 员工也绑定到 finance | 虽然 32/32 已绑定，但绑定目标疑似错误；会影响部门脑职责、模型选择、上下文和权限边界 | P1 | `core/employee/registry/FixedEmployeeRegistry.java`、`core/brain/impl/BrainRegistryImpl.java`、固定员工数据库定义 |
| P1-3 | WebSocket 稳定性仍有异常连接和鉴权重试噪声 | 多次 `Token validation failed`，以及一次 `WebSocket transport error ... error=null`、`CloseStatus[code=1006]` | “不再出现 WebSocket 异常风暴”已改善，但仍存在连接异常和无错误原因日志，不利于定位前端断连/重连问题 | P1 | `gateway/websocket/DepartmentWebSocketHandler.java`、前端 WebSocket token 刷新与重连逻辑 |
| P2-1 | 运行日志缺少模型健康摘要与 cooldown 观测 | 只看到 `ResolvedBrainModelProvider created`、`MainBrain.callLlm completed`，未看到模型健康摘要、熔断状态统计 | 阶段3要求“日志中能看到模型健康摘要，而不是重复 WARN 风暴”；当前成功路径可用，但健康观测不足 | P2 | `core/model/pool/ModelHealthRegistry.java`、`core/model/pool/BrainModelResolver.java` |
| P2-2 | 调试日志噪声偏高 | `Founder status from cache`、`Getting knowledge-base files`、`EmployeeStateSynchronizer` 在短时间内大量输出 | 影响对自治链路关键 Trace 的排查效率；生产/演示环境建议降噪或结构化采样 | P2 | `application.yml` 日志级别、相关 controller/service debug 日志 |

### 2.3.3 下一轮修复建议

#### 2026-05-14 代码修复进展

- ✅ 已将 `ChannelBackedDepartmentExecutionCoordinator` 改为先构造 `DepartmentExecutionResult` 并调用 `EmployeeExecutionReceiptService.registerExecution()`，再发布员工任务，避免同步消费者先完成、后注册 execution。
- ✅ 已将 `DepartmentChatService` 的 receipt trace subscriber 调整为基于 `PreparedAssignmentBatch` 在派发前订阅部门 receipt channel，避免 `employee_execution_receipt_received` 丢失。
- ✅ 已将 `GatewayConfig.departmentExecutionCoordinator` 注入 `EmployeeExecutionReceiptService`，让 execution 注册落在真正派发前的 core 协调器中。
- ✅ 已将 `ToolBackedEmployeeTaskExecutor` 的中文/自然语言任务类型归一化为标准类型，`前端游戏开发` 可进入 `web_development` 路径。
- ✅ 已移除网页硬编码模板兜底：Web/文档/数据分析/审核产物必须由员工模型按任务动态生成；模型不可用、输出为空或网页基础结构不合格时直接失败，避免“生成通用文本但 receipt=COMPLETED”的假完成。
- ✅ 已将 Docker 沙箱不可用/执行失败从“自动降级生成 artifact”改为直接 `FAILED`，禁止用兜底路径伪造完成。
- ✅ 已将 artifact 保存路径从员工维度改为 `data/artifacts/{department}/{executionId}/`，员工 assignmentId 不再作为一级执行目录。
- ✅ generic fallback 已改为拒绝执行并返回 `FAILED`，不再生成诊断文件冒充产物。

#### 2026-06-08 代码修复进展

- ✅ 修复 `DepartmentChatService.onReceiptRecorded` 监听路径 `executionResult=null` 问题：添加 `executionResultCache` 缓存，在 `coordinate()` 返回后缓存 `executionResult`，`onReceiptRecorded` 在 `executionResult == null` 时从缓存回查，`triggerAsyncFinalResponse` 完成后清理缓存。
- ✅ 修复前端不处理 `execution_event` 问题：`DepartmentChatInline` 新增 `onExecutionEvent` 回调，`DepartmentDetail` 新增 `employeeOverrides` 状态，根据事件实时更新员工 `status`/`currentTask`/`lastActiveAt`。
- ✅ 修复 `SelfImprovingTool` NPE：`Map.of()` 不允许 null 值，改用 `LinkedHashMap`。
- ✅ 修复 MainBrain 工具迭代限制过低：`maxToolIterations` 从 5 增加到 20；达到限制时不再返回错误消息，而是强制让 LLM 基于当前历史生成最终响应（禁用工具调用）。

#### 2026-06-08 待修复问题清单

> 详细清单见 `docs/ACTIVE_FIXES_TODO.md`

| 编号 | 问题 | 优先级 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| P0-1 | `DefaultExecutionReceiptReviewer` 只做关键词匹配验收 | P0 | 🟢 已修改未部署 | 降级验收严格化：摘要为空→rejected、验收标准未满足→rejected、期望产物但无文件→rejected+needsRetry |
| P0-2 | 端到端 Trace 缺失最终聚合/收口 | P0 | 🟡 依赖部署 | `onReceiptRecorded` 修复后应解决，需部署验证 |
| P1-1 | WebSocket 僵尸会话循环 | P1 | 🟢 已修改未部署 | 前端添加 30 秒 ping 心跳 |
| P1-2 | 员工 delegateBrain 绑定到错误部门 | P1 | 🟢 已修改未部署 | 添加启动时部门匹配校验，日志输出 mismatch 详情 |
| P1-3 | DockerSandboxService 不可用 | P1 | 🟡 降级可用 | 容器内 Docker 不可用，降级为 `ARTIFACT_ONLY` |
| P1-4 | 跨部门协调缺失 CrossDepartmentCoordinator | P1 | 🟢 已修改未部署 | 实现 CrossDepartmentCoordinator，在 DepartmentChatService 中集成 |
| P2-1 | 模型健康摘要观测缺失 | P2 | 🟢 已修改未部署 | GatewayConfig 添加 @Scheduled 每 5 分钟输出模型健康摘要 |
| P2-2 | 调试日志噪声偏高 | P2 | 🟢 已修改未部署 | application.yml 高频非关键日志降为 INFO |
| P2-3 | 前端不展示需求状态 | P2 | 🟢 已修改未部署 | DepartmentChatInline 添加 requirementStatus state 和 UI 展示标签 |
| P2-4 | 需求冻结/防漂移逻辑缺失 | P2 | 🟢 已修改未部署 | MainBrainTaskPlan 添加 isRequirementFrozen/withRequirementStatus，DepartmentChatService 添加 activeSessionPlans 追踪 |

1. **继续核验 receipt 生命周期 P0**：重新运行红色小球样例，确认不再出现业务回执广播到 `0 subscribers` 后才注册 execution；同步执行路径也必须保证不会"先完成后订阅"。
2. **修复“假完成”P0**：`ToolBackedEmployeeTaskExecutor` 不能把 generic fallback 的文本落盘等同于任务完成；只有生成符合任务类型的真实产物、完成工具/沙箱动作、通过 `ExecutionReceiptReviewer` 后，receipt 才能标记 `COMPLETED`，否则应返回 `FAILED` 或 `NEEDS_REWORK` 并写明原因。
3. **补齐最终闭环 P0**：确保短等待/异步回执聚合后写入 `execution_receipts_aggregated`、执行 `ExecutionReceiptReviewer`、记录 `artifact_recorded`，再调用 `MainBrainFinalSummaryService` 输出 `main_brain_finalized`。
4. **修复网页 artifact 产物 P0/P1**：将中文任务类型或 LLM 输出 schema 标准化为 `web_development`/`web_prototype`，生成 `index.html` 等可预览文件，并统一保存到 `data/artifacts/{department}/{executionId}/`。
5. **校正员工脑绑定 P1**：核查 `FixedEmployeeRegistry` 和数据库员工定义，确保 tech/sales/legal/cs/admin/ops 等员工绑定到对应部门脑，而不是统一落到 finance brain。
6. **补强观测 P2**：加入模型健康周期摘要、WebSocket 断连原因、artifact 记录结果和 completion gate 结论，减少非关键 DEBUG 噪声。
7. **补齐任务类型归一化 P0**：在 `ToolBackedEmployeeTaskExecutor.normalizeTaskType()`、`DecisionContextBuilder` 和 `LlmBasedMainBrainTaskDirector` 的 task schema 中建立统一映射，确保 `game_development`、`web_game_development`、`网页游戏开发`、`前端游戏开发`、`单机游戏开发` 等都能归并到可执行标准类型，不能再因未识别类型直接抛错。
8. **前置主脑需求确认 P0**：新增 `RequirementReadinessEvaluator` / `MainBrainRequirementClarifier`，把“需求是否明确”的判断移动到 `MainBrainTaskDirector` 和 `FixedEmployeeDispatcher` 之前；未达到 `REQUIREMENT_CONFIRMED` 时只允许主脑澄清并保存 `WAITING_USER/CLARIFICATION_PENDING`，禁止正式员工分派。

#### 2.3.3.1 P0-5 细化：任务无限多，但执行能力必须有限（LLM 自主 + 系统约束）

##### 设计结论

任务场景会无限增长，不能为每种用户表达都硬编码一个 `taskType`。

但后端执行器、工具、沙箱和产物生成能力是有限的，也不能允许 LLM 自由输出任意 `taskType` 并直接进入执行器。

正确设计是：

```text
LLM 自主理解任务
-> LLM 输出丰富的意图、领域、交付物、技能需求
-> 系统归一到有限的 executionCapability
-> 执行器只消费 executionCapability / artifactType / executionMode
-> 无法归一时进入澄清或人工介入，不允许直接抛错或乱执行
```

一句话原则：

```text
任务意图可以开放，执行能力必须收敛。
```

##### 当前问题根因

当前 `taskType` 字段承担了过多含义：

```text
用户意图
业务领域
任务类型
执行方式
工具路由
产物类型
```

例如本次日志中：

```text
taskType=game_development
```

它对主脑来说是合理的意图分类，但对 `ToolBackedEmployeeTaskExecutor.normalizeTaskType()` 来说不是可执行类型，因此出现：

```text
Unsupported or unclassified task type: game_development
```

这说明问题不在于 LLM 判断错了，而在于系统缺少从“开放任务意图”到“有限执行能力”的中间解析层。

##### 字段拆分建议

不要继续让单个 `taskType` 承担全部路由职责。建议将任务计划和员工任务单拆分为以下字段：

```text
intent                 // LLM 自主判断的任务意图，如 game_development、contract_review
businessDomain         // 业务领域，如 web_game、legal_contract、finance_report
executionCapability    // 系统可执行能力枚举，如 WEB_APP_BUILD、DOCUMENT_GENERATION
artifactType           // 产物类型，如 INTERACTIVE_WEB_PAGE、DOCUMENT、DATA_REPORT
executionMode          // 执行方式，如 ARTIFACT_ONLY、DOCKER_SANDBOX、LOCAL_RESTRICTED、HUMAN_REVIEW_REQUIRED
requiredSkills         // 技能需求，如 frontend、canvas、game_loop、ui_design
requiredTools          // 工具需求，如 html_css_js_generator、sandbox、filesystem
confidence             // 归一化置信度
normalizationReason    // 为什么这样归一化
```

字段职责：

| 字段 | 是否允许 LLM 开放生成 | 是否用于执行器硬路由 | 说明 |
| --- | --- | --- | --- |
| `intent` | ✅ 是 | ❌ 否 | 表达用户真实意图，可开放 |
| `businessDomain` | ✅ 是 | ❌ 否 | 业务/行业领域，可开放或半开放 |
| `taskType` | ⚠️ 兼容保留 | ❌ 不建议继续作为唯一硬路由 | 旧字段，逐步降级为展示/兼容 |
| `executionCapability` | ❌ 必须枚举 | ✅ 是 | 执行能力路由主字段 |
| `artifactType` | ❌ 必须枚举 | ✅ 是 | 产物生成和前端展示字段 |
| `executionMode` | ❌ 必须枚举 | ✅ 是 | 决定沙箱、工具、人工审核 |
| `requiredSkills` | ✅ 半开放 | ⚠️ 辅助路由 | 用于员工分派 |
| `confidence` | ❌ 数值 | ✅ 是 | 低置信度进入澄清/人工 |

##### 第一版 executionCapability 枚举建议

第一版不要太细，建议控制在 10-15 个执行能力：

```text
WEB_APP_BUILD           // 网页、Web App、网页游戏、H5、小工具页面
DOCUMENT_GENERATION     // 文档、方案、报告、SOP、说明书
DATA_ANALYSIS           // 表格、指标、趋势、数据诊断
CODE_CHANGE             // 修改已有代码、修 Bug、加接口
CODE_REVIEW             // 代码审查、质量检查、安全检查
ARCHITECTURE_DESIGN     // 架构设计、技术方案、模块拆分
RESEARCH_ANALYSIS       // 调研、竞品分析、资料整理
BUSINESS_PLAN           // 商业方案、销售方案、运营方案
CUSTOMER_SUPPORT        // 客服回复、工单处理、FAQ
LEGAL_REVIEW            // 合同、合规、法律风险审查
FINANCE_ANALYSIS        // 财务分析、预算、成本、报销判断
HR_WORKFLOW             // 招聘、绩效、人事流程
OPERATION_PLAN          // 运营活动、流程优化、排期计划
APPROVAL_REQUIRED       // 必须进入审批
HUMAN_HANDOFF           // 必须人工接管
```

##### artifactType 枚举建议

```text
INTERACTIVE_WEB_PAGE
WEB_PROJECT
DOCUMENT
DATA_REPORT
CODE_PATCH
REVIEW_REPORT
ARCHITECTURE_SPEC
BUSINESS_PROPOSAL
SUPPORT_REPLY
LEGAL_MEMO
FINANCE_REPORT
HR_DOCUMENT
OPERATION_RUNBOOK
APPROVAL_REQUEST
HUMAN_HANDOFF_NOTE
```

##### executionMode 枚举建议

```text
ARTIFACT_ONLY            // 只生成产物，不改仓库
DOCKER_SANDBOX           // 在 Docker 沙箱执行/构建/测试
LOCAL_RESTRICTED         // 受限本地执行
HUMAN_REVIEW_REQUIRED    // 生成方案后必须人工审核
APPROVAL_REQUIRED        // 必须审批后执行
NO_EXECUTION             // 只回答/只澄清/拒绝执行
```

##### 示例映射

| 用户任务 | LLM intent | businessDomain | executionCapability | artifactType | executionMode |
| --- | --- | --- | --- | --- | --- |
| 做一个星空飞机射击网页游戏 | `game_development` | `web_game` | `WEB_APP_BUILD` | `INTERACTIVE_WEB_PAGE` | `ARTIFACT_ONLY` 或 `DOCKER_SANDBOX` |
| 写一份销售方案 | `sales_proposal` | `sales` | `DOCUMENT_GENERATION` | `BUSINESS_PROPOSAL` | `ARTIFACT_ONLY` |
| 分析 Excel 财务数据 | `finance_data_analysis` | `finance` | `DATA_ANALYSIS` | `DATA_REPORT` | `LOCAL_RESTRICTED` |
| 评审合同风险 | `contract_review` | `legal_contract` | `LEGAL_REVIEW` | `LEGAL_MEMO` | `HUMAN_REVIEW_REQUIRED` |
| 设计系统架构 | `architecture_design` | `technology` | `ARCHITECTURE_DESIGN` | `ARCHITECTURE_SPEC` | `ARTIFACT_ONLY` |
| 修改后端接口 Bug | `bug_fix` | `software_engineering` | `CODE_CHANGE` | `CODE_PATCH` | `DOCKER_SANDBOX` |
| 处理客户投诉 | `complaint_handling` | `customer_success` | `CUSTOMER_SUPPORT` | `SUPPORT_REPLY` | `ARTIFACT_ONLY` |

##### 新增组件建议：ExecutionCapabilityResolver

建议不要继续扩大 `ToolBackedEmployeeTaskExecutor.normalizeTaskType()` 的硬编码词表，而是新增一个独立解析层：

```text
ExecutionCapabilityResolver
```

职责：

```text
输入：userMessage + taskType + intent + deliverables + requiredSkills + department + suggestedEmployees
输出：executionCapability + artifactType + executionMode + confidence + reason
```

推荐接口：

```text
ExecutionCapabilityResolver.resolve(ExecutionCapabilityRequest request)
    -> ExecutionCapabilityResolution
```

推荐结果结构：

```text
executionCapability
artifactType
executionMode
confidence
reason
requiresClarification
clarificationQuestions
requiresHumanReview
```

实现策略：

```text
规则兜底优先
-> LLM 判断补充
-> 枚举校验
-> 置信度检查
-> 无法归一则 NEEDS_CLARIFICATION / HUMAN_HANDOFF
```

##### 代码落点

| 文件 | 修改建议 |
| --- | --- |
| `core/autonomy/ExecutionCapabilityResolver.java` | 新增接口 |
| `core/autonomy/ExecutionCapabilityRequest.java` | 新增请求结构 |
| `core/autonomy/ExecutionCapabilityResolution.java` | 新增结果结构 |
| `core/autonomy/ExecutionCapability.java` | 新增执行能力枚举 |
| `core/autonomy/ArtifactType.java` | 新增产物类型枚举 |
| `core/autonomy/ExecutionMode.java` | 新增执行模式枚举，或复用现有 execution environment |
| `core/autonomy/impl/DefaultExecutionCapabilityResolver.java` | 规则 + LLM 混合归一化实现 |
| `core/autonomy/MainBrainTaskPlan.java` | 增加 intent/businessDomain/executionCapability/artifactType/executionMode 字段 |
| `core/autonomy/DepartmentTaskPlan.java` | 增加 executionCapability/artifactType/executionMode 字段 |
| `core/autonomy/EmployeeWorkAssignment.java` | 增加 executionCapability/artifactType/executionMode 字段 |
| `core/autonomy/impl/LlmBasedMainBrainTaskDirector.java` | Prompt/schema 要求 LLM 从枚举中选择 executionCapability/artifactType/executionMode |
| `core/autonomy/context/DecisionContext.java` | 增加可用 execution capabilities、工具能力和产物能力上下文 |
| `core/autonomy/impl/DefaultAssignmentPreparationService.java` | 在准备任务单时调用 resolver，写入标准能力字段 |
| `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | 从读取 `taskType` 改为优先读取 `executionCapability`，`taskType` 只做兼容兜底 |
| `gateway/config/GatewayConfig.java` | 注册 `ExecutionCapabilityResolver` Bean |
| `gateway/service/DepartmentChatService.java` | Trace 中记录 capability resolution 结果 |

##### 执行流程改造

目标流程：

```text
DialogueAnalyzer
-> MainBrainTaskDirector 输出 intent/taskType/deliverables/acceptanceCriteria
-> ExecutionCapabilityResolver 归一化 executionCapability/artifactType/executionMode
-> FixedEmployeeDispatcher 根据 capability + skills 选人
-> AssignmentPreparationService 将 capability 写入 EmployeeWorkAssignment
-> ToolBackedEmployeeTaskExecutor 按 executionCapability 执行
-> ExecutionReceiptReviewer 按 artifactType 和 acceptanceCriteria 验收
```

##### 失败与澄清策略

当归一化置信度不足时：

```text
confidence < 0.6 -> NEEDS_CLARIFICATION
0.6 <= confidence < 0.75 -> PARTIALLY_READY，可执行但必须记录风险
confidence >= 0.75 -> READY
```

无法归一化时，不允许：

```text
throw Unsupported task type
generic fallback 伪造完成
随便走文本产物
```

必须改为：

```text
status = NEEDS_CLARIFICATION 或 HUMAN_HANDOFF
clarificationQuestions = [请确认希望生成哪类产物/是否允许执行代码/是否需要可运行页面等]
trace stage = capability_resolution_failed
保存 assistant 澄清消息
```

##### 验收标准

1. `game_development`、`web_game_development`、`网页游戏开发`、`前端游戏开发`、`单机游戏开发` 均应归一为 `WEB_APP_BUILD`。
2. 工具执行器日志不再出现 `Unsupported or unclassified task type: game_development`。
3. `EmployeeWorkAssignment` 中必须能看到 `executionCapability`、`artifactType`、`executionMode`。
4. Trace 中必须出现 `capability_resolved` 或 `capability_resolution_failed`。
5. `ToolBackedEmployeeTaskExecutor` 优先按 `executionCapability` 路由，而不是直接按 `taskType` 路由。
6. 无法归一化的任务必须返回澄清或人工介入，不能直接抛错导致执行失败。
7. 前端最终回复应说明当前任务归一后的执行方式，例如“我将按 WEB_APP_BUILD 生成一个可运行网页产物”。

#### 2.3.3.2 P0-6 细化：主脑必须先确认需求，再允许分配员工

##### 设计结论

主脑必须先把需求确认清楚，之后才能进入正式规划和员工分配。不能采用“先分配下去，再由员工反过来追问核心需求”的流程。

硬规则：

```text
Requirement-confirmed-before-assignment.
No assignment before requirement confirmation.
```

##### 为什么必须前置确认

如果需求未确认就提前分配，会出现以下问题：

- 员工收到不完整需求后开始开工。
- 员工和主脑反复追问同一个核心问题。
- 同一个任务被多次分派、多次澄清，导致上下文分裂。
- `taskKey` / `executionId` / `conversationId` 的关系变乱。
- 用户会感觉系统“先分配、再问需求、再分配”，体验混乱。

##### 主脑应该确认的内容

在正式分派员工前，主脑必须确认至少以下内容：

```text
目标是否明确
范围是否明确
核心功能是否明确
交付物是否明确
验收标准是否明确
时间节点是否明确
风险和约束是否明确
是否需要跨部门协作
是否需要人工审批
```

##### 需求状态建议

建议为长期对话与任务引入独立的需求状态，而不是只看有没有消息：

```text
DRAFT
NEEDS_CLARIFICATION
CLARIFICATION_PENDING
REQUIREMENT_CONFIRMED
PLANNING
PLANNED
ASSIGNED
EXECUTING
COMPLETED
FAILED
```

规则：

- 只有 `REQUIREMENT_CONFIRMED` 才能进入 `PLANNING` / `ASSIGNED`。
- 在 `NEEDS_CLARIFICATION` / `CLARIFICATION_PENDING` 阶段，不允许正式分派员工。
- 用户补充信息后，必须先回到主脑重新确认，而不是直接跳到员工执行。

##### 推荐流程顺序

```text
DialogueAnalyzer
-> MainBrainRequirementClarifier / RequirementReadinessEvaluator
-> 若需求不清楚：主脑发起澄清，状态 = NEEDS_CLARIFICATION
-> 用户补充信息，继续同一个 conversationId / draft task
-> MainBrain 合并历史上下文，确认 requirementStatus = REQUIREMENT_CONFIRMED
-> MainBrainTaskDirector 生成正式任务计划
-> ExecutionCapabilityResolver 归一化 executionCapability / artifactType / executionMode
-> FixedEmployeeDispatcher 分派员工
-> AssignmentPreparationService 准备任务单
-> ToolBackedEmployeeTaskExecutor 执行
-> ExecutionReceiptReviewer 验收
-> MainBrainFinalSummaryService 收口
```

##### 员工可以问什么，不能问什么

员工可以问执行细节，例如：

- 文件命名
- UI 风格偏好
- 组件库偏好
- 素材存放位置
- 局部实现约束

员工不能再反问主脑本该确认的核心需求，例如：

- 到底要不要用户系统
- 到底要不要多人联机
- 上线时间是什么
- 核心功能范围是什么
- 验收标准是什么

##### 代码落点建议

| 文件 | 修改建议 |
| --- | --- |
| `gateway/service/DepartmentChatService.java` | 增加 requirementStatus 判断，澄清未确认前不进入正式分派 |
| `core/autonomy/ConversationOrchestrator.java` | 将需求确认放在任务规划和分派之前 |
| `core/autonomy/RequirementReadinessEvaluator.java` | 新增或重构，用于判断需求是否可以进入规划 |
| `core/autonomy/MainBrainRequirementClarifier.java` | 新增或重构，负责主脑统一澄清问题 |
| `core/autonomy/MainBrainTaskDirector.java` | 仅在需求确认后生成正式任务计划 |
| `core/autonomy/AssignmentPreparationService.java` | 仅在 requirementStatus=REQUIREMENT_CONFIRMED 时准备员工任务单 |
| `core/autonomy/FixedEmployeeDispatcher.java` | 仅接收已确认需求的正式计划 |
| `core/database/entity/DepartmentConversationEntity.java` | 可扩展 requirementStatus 字段 |
| `frontend/src/pages/DepartmentDetail/*` | 前端根据 requirementStatus 展示“等待澄清/等待确认/正式执行”状态 |

##### 产品跟进职责与需求收口机制

为了保证任务完成不跑偏，主脑需要承担一个明确的“产品跟进 / 需求收口”职责。这个职责不是独立新增一个完全新的系统角色，而是作为 `MainBrain` 的核心能力之一固化下来。

###### 这个职责具体做什么

`MainBrain` 在正式分派员工前，必须完成以下动作：

```text
1. 跟踪多轮用户反馈和澄清回答
2. 合并历史上下文，消除表述漂移
3. 判断当前需求是否已经足够明确
4. 决定是否进入 REQUIREMENT_CONFIRMED
5. 冻结需求版本，防止后续执行偏题
6. 在执行过程中检查是否偏离产品目标
7. 在任务结束时做交付收口与最终总结
```

###### 主脑在“产品跟进”中的边界

主脑必须负责：

- 需求澄清
- 需求确认
- 范围冻结
- 交付物确认
- 验收标准确认
- 风险和约束确认
- 多轮互动合并
- 最终收口

主脑不能把这些核心判断完全下放给员工执行器。

员工只能问执行细节，不能替主脑确认核心需求。

###### 需要调整的现有链路

当前链路中，`AssignmentReadinessEvaluator` 更像是在“已经计划、已经分派之后”检查任务单是否可执行。这个位置太晚。应拆成两类评估：

| 评估器 | 触发位置 | 判断内容 | 结果用途 |
| --- | --- | --- | --- |
| `RequirementReadinessEvaluator` | 主脑规划和员工分派之前 | 用户需求、目标、范围、交付物、验收标准是否足够明确 | 决定是否允许进入正式规划和分派 |
| `AssignmentReadinessEvaluator` | 已确认需求并形成员工任务单之后 | 员工任务单是否完整、员工是否合适、工具/产物上下文是否齐备 | 决定是否允许进入执行协调 |

关键规则：

```text
RequirementReadinessEvaluator 不通过 -> 只能主脑澄清，不能分派员工
AssignmentReadinessEvaluator 不通过 -> 可以调整任务单/员工/工具上下文，但不能再追问核心需求
```

###### MainBrain 职责细化为可执行规则

为了让 LLM 真的能正确执行 `MainBrain` 职责，需要把它拆成可操作的行为清单，而不是只写“总控”两个字。建议将 `MainBrain` 的职责细化为以下阶段：

| 阶段 | 主脑行为 | 产出 | 是否允许进入下一步 |
| --- | --- | --- | --- |
| 1. 意图识别 | 读取用户输入、结合历史对话，识别任务意图 | `intent`、`kind`、`roughComplexity` | 可 |
| 2. 需求收口 | 判断目标、范围、交付物、验收标准是否明确 | `requirementStatus`、`requirementSummary` | 仅当明确时可进入下一步 |
| 3. 澄清提问 | 当需求不明确时，统一向用户发起澄清 | `clarificationQuestions`、`WAITING_USER` | 可循环回到第 2 步 |
| 4. 需求冻结 | 当用户补充完毕后，冻结需求版本，避免后续漂移 | `requirementVersion`、`requirementConfirmedAt` | 可 |
| 5. 任务规划 | 基于已确认需求生成正式计划 | `MainBrainTaskPlan`、`DepartmentTaskPlan` | 可 |
| 6. 能力归一 | 将开放任务意图映射到有限执行能力 | `executionCapability`、`artifactType`、`executionMode` | 可 |
| 7. 员工分派 | 按能力和角色把任务交给合适员工 | `EmployeeWorkAssignment`、`assignmentBatchId` | 可 |
| 8. 执行跟进 | 监控回执、判断偏离、协调重试或补充 | `receiptStatus`、`trace`、`blockingIssues` | 可 |
| 9. 最终收口 | 汇总产物、结论、下一步，生成最终回复 | `MainBrainFinalSummary` | 结束 |

###### 让 LLM 正确执行的关键规则

如果要让 `MainBrain` 的 LLM 路径稳定落地，Prompt 和 Schema 必须满足以下约束：

1. **先确认需求，再规划任务**。在 `REQUIREMENT_CONFIRMED` 前，不允许输出正式分派结果。
2. **澄清问题必须可结构化**。`clarificationQuestions` 不能只是自然语言散文，必须能进入状态机。
3. **任务规划必须带冻结版本**。`requirementVersion` 要写入计划和任务单，避免旧回答继续执行。
4. **能力归一必须输出枚举**。`executionCapability`、`artifactType`、`executionMode` 不能随意拼字符串。
5. **最终收口必须引用回执和产物**。总结不能脱离真实执行证据。
6. **任何无法确认的关键需求都必须回到澄清**，不能假装已确认。

###### MainBrain 的推荐输出结构

```text
intent
kind
roughComplexity
requirementStatus
requirementSummary
clarificationQuestions
requirementVersion
executionCapability
artifactType
executionMode
primaryDepartment
supportingDepartments
assignedEmployees
riskLevel
summary
nextSteps
```

###### MainBrain 的推荐执行约束

- 如果 `requirementStatus != REQUIREMENT_CONFIRMED`，只能澄清，不能分派。
- 如果 `executionCapability` 低置信度，不允许自动派发高风险员工。
- 如果用户回答与历史需求冲突，必须触发重新确认，而不是直接覆盖。
- 如果任务跨部门，必须先由主脑协调，再允许部门脑和员工进入执行。
- 如果执行回执显示偏离需求，主脑必须负责收口或要求返工。

###### MainBrain Prompt 模板（建议落地版本）

```text
你是 Living Agent 的主脑 MainBrain，职责是先确认用户需求是否明确，再决定是否规划任务、分派员工和启动执行。

你的目标：
1. 识别用户意图和任务目标。
2. 判断当前需求是否明确、可执行、可验收。
3. 如果不明确，必须优先输出澄清问题，不得直接分派员工。
4. 如果已明确，输出正式任务计划，并给出标准化执行能力、产物类型和执行模式。
5. 必须维护需求版本，避免多轮对话中需求漂移。
6. 最终回复必须引用真实计划、真实回执和真实产物。

硬性约束：
- 在 requirementStatus != REQUIREMENT_CONFIRMED 之前，不得输出 employee assignments。
- clarificationQuestions 必须是结构化列表，且每个问题都可直接用于下一轮追问。
- executionCapability、artifactType、executionMode 必须从枚举中选择，不得自由发挥。
- 如果用户回答与历史需求冲突，必须重新进入需求确认流程。
- 如果任务存在跨部门协作风险，必须标注 supportingDepartments 并保持主脑总控。

你需要输出的字段：
intent, kind, roughComplexity, requirementStatus, requirementSummary, clarificationQuestions, requirementVersion, executionCapability, artifactType, executionMode, primaryDepartment, supportingDepartments, riskLevel, summary, nextSteps
```

###### MainBrainTaskPlan 建议字段 schema

```text
planId: string
requestId: string
conversationId: string
sourceMessageId: string
sourceConversationId: string
requirementStatus: DRAFT | NEEDS_CLARIFICATION | CLARIFICATION_PENDING | REQUIREMENT_CONFIRMED
requirementVersion: integer
requirementSummary: string
clarificationQuestions: string[]
intent: string
kind: TASK | QUESTION | APPROVAL | HANDOFF | INFO
roughComplexity: integer
riskLevel: integer
primaryDepartment: string
supportingDepartments: string[]
objective: string
deliverables: string[]
acceptanceCriteria: string[]
executionCapability: WEB_APP_BUILD | DOCUMENT_GENERATION | DATA_ANALYSIS | CODE_CHANGE | CODE_REVIEW | ARCHITECTURE_DESIGN | RESEARCH_ANALYSIS | BUSINESS_PLAN | CUSTOMER_SUPPORT | LEGAL_REVIEW | FINANCE_ANALYSIS | HR_WORKFLOW | OPERATION_PLAN | APPROVAL_REQUIRED | HUMAN_HANDOFF
artifactType: INTERACTIVE_WEB_PAGE | WEB_PROJECT | DOCUMENT | DATA_REPORT | CODE_PATCH | REVIEW_REPORT | ARCHITECTURE_SPEC | BUSINESS_PROPOSAL | SUPPORT_REPLY | LEGAL_MEMO | FINANCE_REPORT | HR_DOCUMENT | OPERATION_RUNBOOK | APPROVAL_REQUEST | HUMAN_HANDOFF_NOTE
executionMode: ARTIFACT_ONLY | DOCKER_SANDBOX | LOCAL_RESTRICTED | HUMAN_REVIEW_REQUIRED | APPROVAL_REQUIRED | NO_EXECUTION
requiredSkills: string[]
requiredTools: string[]
assignedEmployees: string[]
assignmentBatchId: string
nextSteps: string[]
summary: string
createdAt: datetime
confirmedAt: datetime | null
```

###### requirementStatus 状态机定义

```text
DRAFT
  -> 用户首次表达需求，尚未确认
NEEDS_CLARIFICATION
  -> 主脑判断需求缺关键要素，必须追问
CLARIFICATION_PENDING
  -> 已发出澄清问题，等待用户回答
REQUIREMENT_CONFIRMED
  -> 需求已明确，可进入正式规划与分派
PLANNING
  -> 主脑生成正式任务计划中
PLANNED
  -> 计划已生成，等待能力归一或分派
ASSIGNED
  -> 员工任务单已生成并分派
EXECUTING
  -> 员工正在执行
COMPLETED
  -> 验收通过并完成
FAILED
  -> 执行失败或验收失败
```

状态转移规则：

```text
DRAFT -> NEEDS_CLARIFICATION | REQUIREMENT_CONFIRMED
NEEDS_CLARIFICATION -> CLARIFICATION_PENDING
CLARIFICATION_PENDING -> REQUIREMENT_CONFIRMED | NEEDS_CLARIFICATION
REQUIREMENT_CONFIRMED -> PLANNING
PLANNING -> PLANNED
PLANNED -> ASSIGNED
ASSIGNED -> EXECUTING
EXECUTING -> COMPLETED | FAILED
COMPLETED / FAILED -> 终态
```

禁止转移：

```text
DRAFT / NEEDS_CLARIFICATION / CLARIFICATION_PENDING 不能直接进入 ASSIGNED
未 REQUIREMENT_CONFIRMED 不能进入 PLANNING
ASSIGNED 之前不能生成最终员工执行产物
```

###### RequirementReadinessEvaluator 判断规则

`RequirementReadinessEvaluator` 负责在主脑规划前判断需求是否足够明确。建议规则如下：

```text
输入：userMessage + conversationHistory + draftTask + currentRequirementStatus
输出：readinessLevel + confidence + missingElements + clarificationQuestions + decision
```

推荐判断维度：

- 目标是否明确
- 范围是否明确
- 产物是否明确
- 验收标准是否明确
- 时间/里程碑是否明确
- 风险和约束是否明确
- 是否需要跨部门协作
- 是否存在明显冲突或歧义

推荐判定结果：

```text
READY
PARTIALLY_READY
NEEDS_CLARIFICATION
```

判定建议阈值：

```text
confidence >= 0.85 && missingElements 为空 -> READY
0.65 <= confidence < 0.85 -> PARTIALLY_READY
confidence < 0.65 或关键字段缺失 -> NEEDS_CLARIFICATION
```

返回规则：

- `READY`：允许主脑进入正式规划。
- `PARTIALLY_READY`：允许主脑补充少量澄清，但不能分派员工。
- `NEEDS_CLARIFICATION`：必须先澄清，不允许正式规划和分派。

建议输出字段：

```text
readinessLevel
confidence
missingElements
clarificationQuestions
blockingReasons
recommendation
```

###### MainBrain 响应模板

当 `RequirementReadinessEvaluator = NEEDS_CLARIFICATION` 时，主脑输出应类似：

```text
1. 当前需求还缺少哪些关键信息。
2. 明确提出 1~3 个核心澄清问题。
3. 告知用户在回答后会继续推进。
4. 不创建正式员工分派，不下发执行任务。
```

当 `RequirementReadinessEvaluator = READY` 时，主脑输出应类似：

```text
1. 复述已确认需求摘要。
2. 给出标准化 executionCapability / artifactType / executionMode。
3. 生成正式任务计划。
4. 再进行员工分派和执行。
```

###### 需要调整的现有链路

##### 多轮互动与上下文续接

多轮澄清必须复用同一个长期对话和草稿工作项：

```text
conversationId
  -> draftTaskKey
  -> clarificationQuestions
  -> userAnswer
  -> requirementStatus
```

当用户补充回答时，系统应优先判断这是不是对当前 `CLARIFICATION_PENDING` 的回答：

```text
如果 conversationId 下存在 CLARIFICATION_PENDING 的 draft task
-> 将用户消息绑定到该 draft task
-> 主脑合并历史需求和本次回答
-> 重新运行 RequirementReadinessEvaluator
-> 若确认明确，再创建/升级正式 taskKey 并规划分派
```

禁止行为：

- 用户每次补充都新建无关任务。
- 用户补充后绕过主脑确认，直接继续上次员工分派。
- 员工把核心需求澄清问题直接抛给用户。
- `PARTIALLY_READY` 被当成正式需求确认。

##### 推荐新增字段

建议在对话、任务和计划结构中逐步补充：

```text
requirementStatus
requirementSummary
requirementVersion
clarificationQuestions
clarificationAnsweredAt
requirementConfirmedAt
requirementConfirmedBy
sourceConversationId
draftTaskKey
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `requirementStatus` | 当前需求状态，控制是否允许规划和分派 |
| `requirementSummary` | 主脑合并多轮互动后的需求摘要 |
| `requirementVersion` | 多轮澄清后递增，避免员工拿旧需求执行 |
| `clarificationQuestions` | 主脑提出的核心澄清问题 |
| `clarificationAnsweredAt` | 用户最后一次回答澄清问题的时间 |
| `requirementConfirmedAt` | 主脑确认需求明确的时间 |
| `requirementConfirmedBy` | 通常为 `MainBrain`，后续可扩展人工确认 |
| `sourceConversationId` | 需求所属长期对话 |
| `draftTaskKey` | 澄清阶段使用的草稿任务 key |

##### Trace 验收要求

修复后，Trace 顺序应从：

```text
main_brain_planned
employee_assignment_planned
readiness_evaluated = NEEDS_CLARIFICATION
```

调整为：

```text
intake_classified
requirement_readiness_evaluated
clarification_requested 或 requirement_confirmed
main_brain_planned
capability_resolved
employee_assignment_planned
assignment_batch_prepared
department_execution_started
```

验收标准：

1. 需求不明确时，Trace 中只能看到 `requirement_readiness_evaluated` 和 `clarification_requested`，不能出现 `employee_assignment_planned`。
2. 用户补充回答后，必须看到同一 `conversationId` 下的 `requirement_confirmed`。
3. 只有 `requirement_confirmed` 后才能出现 `main_brain_planned` 和 `employee_assignment_planned`。
4. 员工回执中的 `requirementVersion` 必须与当前确认版本一致。
5. 前端应显示“等待需求澄清/需求已确认/正在分派/正在执行”等状态，而不是只显示一直思考中。

### 2.3.4 Claude CLI 代理修复（2026-05-16）

> 目标：让 Living Agent 内置的 Anthropic 代理正确服务 Claude CLI，使 LLM 员工可以通过 `claude -p` 命令执行代码类任务。

#### 诊断发现的问题

| 编号 | 问题 | 根因 | 影响 |
| --- | --- | --- | --- |
| C1 | `HttpMediaTypeNotAcceptableException: No acceptable representation` | `ClaudeProxyController` 声明 `produces = TEXT_EVENT_STREAM_VALUE`，但 Claude CLI 发送 `Accept: application/json`，Spring 内容协商失败 | Claude CLI 完全无法调用代理 |
| C2 | 虚拟模型名 `claude-sonnet-4-20250514` 未映射到模型池实际模型 | `ClaudeProxyModelRouter.RoutingResult` 只保留原始请求模型名，不映射到 Ollama/Qwen 等实际模型 | Provider 返回 404: model not found |
| C3 | SSE 事件顺序不完整 | `ClaudeProxyService` 在 `messageStart()` 后直接发送 `contentBlockDelta`，缺少 `contentBlockStart` 和 `contentBlockStop` | Claude CLI 解析 SSE 流异常 |
| C4 | Provider URL 拼接错误 | Ollama baseUrl 为 `http://localhost:11434`（无 `/v1`），但代码拼接 `baseUrl + "/chat/completions"` 导致请求到错误路径 | Provider 请求 404 |
| C5 | 容器级环境变量缺失 | `ANTHROPIC_API_KEY` 和 `ANTHROPIC_BASE_URL` 未在 docker-compose.yml 中设置 | 容器内直接运行 `claude` 命令显示 "Not logged in" |
| C6 | `ClaudeExecutionGateway` 环境变量硬编码 | `executeWithProxy()` 使用硬编码 `http://localhost:8480`，与实际容器内地址 `living-agent-service:8382` 不符 | 通过 Gateway 执行的 Claude CLI 无法连接代理 |

#### 修复内容

| 文件 | 修改内容 | 状态 |
| --- | --- | --- |
| `gateway/controller/ClaudeProxyController.java` | 移除 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`，让 Spring 自动协商 Content-Type | ✅ 已完成 |
| `core/proxy/anthropic/ClaudeProxyModelRouter.java` | 新增 `VIRTUAL_MODEL_MAP` 映射表（claude-sonnet→balanced, claude-opus→powerful, claude-haiku→fast）；`RoutingResult` 增加 `actualModel` 字段；新增 `resolveActualModelName()` 方法，按优先级：BrainModelSelector → 模型池数据库 → 硬编码 fallback 解析实际模型名 | ✅ 已完成 |
| `core/proxy/anthropic/ClaudeProxyService.java` | 使用 `actualModel` 调用 Provider，用 `requestedModel` 返回 SSE；在 `messageStart()` 后增加 `contentBlockStart(0, "text")`；在 `FINISH` 时增加 `contentBlockStop(0)`；Provider URL 自动追加 `/v1` | ✅ 已完成 |
| `docker-compose.yml` | 添加 `ANTHROPIC_API_KEY=sk-living-agent-claude-proxy` 和 `ANTHROPIC_BASE_URL=http://living-agent-service:8382/api/v1/proxy/anthropic` | ✅ 已完成 |
| `core/sandbox/ClaudeExecutionGateway.java` | 注入 `ClaudeCliProperties`，使用配置值替代硬编码；补全 `ANTHROPIC_API_KEY`/`ANTHROPIC_API_URL`/`TERM`/`PYTHONIOENCODING` 环境变量注入 | ✅ 已完成 |
| `core/config/LivingAgentCoreConfig.java` | 添加 `ClaudeCliProperties` import 和 Bean 构造参数 | ✅ 已完成 |

#### 验证结果

```text
# 容器内环境变量
ANTHROPIC_API_KEY=sk-living-agent-claude-proxy
ANTHROPIC_BASE_URL=http://living-agent-service:8382/api/v1/proxy/anthropic

# 代理健康检查
GET /api/v1/proxy/anthropic/health → {"status":"ok","service":"claude-proxy","version":"1.0.0"}

# Claude CLI 直接执行
$ claude -p "What is 2+3? Reply with just the number." --output-format text --max-turns 1
5

$ claude -p "say OK" --output-format text --max-turns 1
OK
```

#### 修复后的请求链路

```text
Claude CLI (ANTHROPIC_BASE_URL=http://living-agent-service:8382/api/v1/proxy/anthropic)
  → POST /api/v1/proxy/anthropic/v1/messages (无 produces 限制)
  → ClaudeProxyModelRouter: claude-sonnet-4-20250514 → qwen3.5:9b (模型池实际模型)
  → ClaudeProxyService: 用 qwen3.5:9b 调用 Ollama http://localhost:11434/v1/chat/completions
  → Anthropic SSE: message_start → content_block_start → content_block_delta → content_block_stop → message_delta → message_stop
  → Claude CLI 正常消费流式输出 ✅
```

#### 待后续改进

- ~~`resolveActualModelName()` 中的硬编码 fallback（`qwen3.5:9b`/`qwen2.5:3b`）应改为从 `application.yml` 或模型池配置读取~~ ✅ 已改为从 `ClaudeCliProperties.categoryFallbackModels` 配置读取
- ~~`VIRTUAL_MODEL_MAP` 映射策略应支持动态配置，而非 Java 常量~~ ✅ 已改为从 `ClaudeCliProperties.virtualModelMapping` 配置读取
- ~~非 stream 请求（`stream=false`）的响应格式尚未实现~~ ✅ 已实现 `createNonStreamMessage()` 方法，返回完整 Anthropic Messages API JSON
- ~~`content_block_start` 的 thinking 类型支持需要与 `AnthropicSseBuilder` 联动~~ ✅ 已实现 thinking block 的 `contentBlockStart("thinking")` + `contentBlockStop` 联动

#### 2.3.5 Claude CLI 代理增强与 P0 问题修复（2026-05-16 第二轮）

> 目标：消除所有硬编码，让模型路由基于模型池能力评分跨 Provider 选择最佳模型；修复审查报告中 4 个 P0 级问题。

##### 改进项

| 编号 | 改进内容 | 修改文件 | 状态 |
| --- | --- | --- | --- |
| I1 | 模型映射和 fallback 改为从 `application.yml` 配置读取 | `ClaudeCliProperties.java`、`application.yml` | ✅ 已完成 |
| I2 | `ClaudeProxyModelRouter` 重写：注入 `ModelCapabilityAssessor`，调用 `selectBestModelForTask()` 跨所有 Provider 基于能力评分选择最佳模型，零硬编码 | `ClaudeProxyModelRouter.java` | ✅ 已完成 |
| I3 | 非 stream 请求（`stream=false`）响应格式实现 | `ClaudeProxyService.java`、`ClaudeProxyController.java` | ✅ 已完成 |
| I4 | thinking block 的 `contentBlockStart("thinking")` + `contentBlockStop` 联动 | `ClaudeProxyService.java` | ✅ 已完成 |

##### P0 修复项

| 编号 | 问题 | 修复内容 | 修改文件 | 状态 |
| --- | --- | --- | --- | --- |
| P0-1 | 模型熔断 `recordSuccess/recordFailure` 从未被调用 | 在 `ClaudeProxyService` 中注入 `ModelHealthRegistry`，Provider 调用成功/失败时回调 | `ClaudeProxyService.java` | ✅ 已完成 |
| P0-2 | 异步进度推送 `pushExecutionProgress` 从未被调用 | 在 `DepartmentChatService` 中注册 `ReceiptListener`，回执到达时调用 `pushExecutionProgress()` | `DepartmentChatService.java` | ✅ 已完成 |
| P0-3 | 主脑二次总结未接入 `DepartmentChatService` | 注入 `MainBrainFinalSummaryService`，在 `MAIN_BRAIN_COMPOSE` 策略中调用 `generateSummary()` | `DepartmentChatService.java` | ✅ 已完成 |
| P0-4 | 员工 delegateBrain 绑定 fallback 到 finance-brain | 移除 `getAll().stream().findFirst()` fallback，找不到部门大脑时设为 null 并输出 WARN 日志 | `EmployeeNeuron.java` | ✅ 已完成 |

##### 模型性能自动测试增强

| 改进内容 | 修改文件 | 状态 |
| --- | --- | --- |
| 启动时异步执行 `modelPerformanceAssessor.assessAllEnabledModels()`，自动测试所有已启用模型的实际可用性 | `ModelPoolManager.java` | ✅ 已完成 |
| 模型测试失败时自动 `setEnabled(false)` + `setPerformanceScore(0)` | `ModelPerformanceAssessorImpl.java` | ✅ 已完成 |
| 评分逻辑改为保留基础分数 + 按响应延迟调整（<3s 不扣分，>60s 只保留 30%） | `ModelPerformanceAssessorImpl.java` | ✅ 已完成 |

##### 修复后的模型选择流程（零硬编码）

```text
Claude CLI 请求 model="claude-sonnet-4-20250514"
  │
  ├─ 1. BrainSelector 查找（如果有 brainId）
  │     └─ 找到 → 使用该 Brain 绑定的模型
  │
  ├─ 2. ModelCapabilityAssessor.selectBestModelForTask()
  │     ├─ 虚拟模型名 → taskType: "code_generation"
  │     ├─ category: "balanced" (来自 virtualModelMapping 配置)
  │     ├─ 遍历所有 enabled + Provider enabled + OPENAI_COMPATIBLE 的模型
  │     ├─ 60% 能力匹配 + 40% 性能评分 加权
  │     └─ 选出最高分模型（可能是 ModelScope 云端或 Ollama 本地）
  │
  └─ 3. Fallback: 第一个可用模型
```

##### 启动时自动测试流程

```text
应用启动
  ├─ @PostConstruct: ModelCapabilityAssessor.assessModels() (静态规则推断)
  │   → 所有模型获得 capabilityTags + performanceScore
  │
  └─ 异步延迟 15s: ModelPerformanceAssessor.assessAllEnabledModels()
      → 逐个实际调用模型发送测试请求
      → 可用: 保留 enabled，按延迟调整分数
      → 不可用: 自动 setEnabled(false) + setPerformanceScore(0)
      → ClaudeProxyModelRouter 只会路由到 enabled=true 的模型
```

---

## 3. 分阶段落地方案

---

## 3. 分阶段落地方案

### 阶段 1：LLM-first 主路径确认与 Trace 标准化

**目标**：确认入口分析、主脑规划、员工分派、最终回复策略、主脑回复编排默认走 LLM 实现，规则实现只做 fallback。

#### 3.1.1 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/config/GatewayConfig.java` | 确认注册 `LlmBasedDialogueAnalyzer`、`LlmBasedMainBrainTaskDirector`、`LlmBasedFixedEmployeeDispatcher`、LLM final coordinator/composer 如果存在则优先注册 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedDialogueAnalyzer.java` | 输出 `decisionSource=llm_based`，fallback 时输出 `rule_based_fallback` 与原因 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedMainBrainTaskDirector.java` | 输出 `director_type=llm_based` / fallback 原因 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedFixedEmployeeDispatcher.java` | 输出 `dispatcher_type=llm_based` / fallback 原因 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java` | 在 Trace metadata 中透传 analyzer/director/dispatcher/composer source |

#### 3.1.2 实施要求

1. 所有 LLM 决策组件必须在成功、失败、fallback 三种情况下写 Trace。
2. fallback 不是错误，但必须有可观测原因：模型不可用、超时、JSON 解析失败、Schema 不匹配、上下文不足。
3. 禁止仅通过“类存在”判断 LLM-first，必须以运行 Trace 为准。

#### 3.1.3 验收标准

一次“红色小球网页”任务 Trace 至少包含：

```text
intake_classified(analyzer_type=llm_based)
main_brain_planned(director_type=llm_based)
employee_assignment_planned(dispatcher_type=llm_based 或明确 fallback reason)
result_aggregated
main_brain_finalized(composer_type=llm_based 或 fallback reason)
```

---

### 阶段 2：统一 LLM 决策上下文与 Schema 校验

**目标**：解决 LLM 虽然存在但 Prompt/输入过窄、默认部门/员工/工具仍硬编码的问题。

#### 3.2.1 新增/修改文件

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/DecisionContext.java` | 新增 | 统一承载用户、权限、部门、员工、工具、知识、审批、模型健康等上下文 |
| `core/autonomy/DecisionContextBuilder.java` | 新增接口 | 构造 LLM 决策上下文 |
| `core/autonomy/impl/DefaultDecisionContextBuilder.java` | 新增实现 | 从 Registry、ToolRegistry、模型池、权限服务、知识摘要中组装上下文 |
| `core/autonomy/LlmDecisionClient.java` | 新增接口 | 统一 LLM JSON 调用、Schema 校验、修复重试、fallback 记录 |
| `core/autonomy/impl/DefaultLlmDecisionClient.java` | 新增实现 | 包装 `MainBrain.callLlm()` 或 Provider 调用 |
| `GatewayConfig.java` | 修改 | 注册 builder/client Bean |

#### 3.2.2 DecisionContext 必含字段

```text
requestId
sessionId
userId
userDepartment
accessLevel
requestedDepartment
availableDepartments
availableBrains
availableEmployees
employeeCapabilities
employeeResolvedTools
modelHealthSummary
knowledgeSummary
approvalConstraints
sandboxAvailability
conversationSummary
```

#### 3.2.3 LlmDecisionClient 要求

1. 统一抽取 ```json fenced block 和裸 JSON。
2. 支持 schemaName，例如：`dialogue_decision`、`main_brain_plan`、`employee_dispatch`、`final_summary`。
3. JSON 解析失败时允许一次“修复 JSON”重试。
4. 修复仍失败才 fallback。
5. Trace 写入字段级错误，而不是只写“解析失败”。

#### 3.2.4 禁止事项

- 不在 Prompt 中写死 `tech`、`T02`、`T09` 作为默认。
- 不在 Prompt 中写死固定工具路径。
- 不在 LLM 组件中硬编码模型名。
- 不让 LLM 决定权限是否允许，只让它理解权限约束。

---

### 阶段 3：模型超时、熔断和重试

**目标**：形成“可发现 + 可熔断 + 可降级”的模型健康链。

#### 3.3.1 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `core/model/pool/BrainModelResolver.java` | 继续保留 `/api/tags` 可用性发现，增加失败模型短期熔断过滤 |
| `core/model/pool/ModelHealthRegistry.java` | 新增，记录模型成功/失败/超时/熔断到期时间 |
| `core/provider/impl/ResolvedBrainModelProvider.java` | 增加健康检查超时、首 token 超时、完整生成超时参数 |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | 模型失败原因写入 `EmployeeTaskExecutionOutcome` 和 receipt metadata |

#### 3.3.2 状态定义

```text
AVAILABLE
DEGRADED
COOLDOWN
UNAVAILABLE
UNKNOWN
```

#### 3.3.2.1 模型调用失败自动降级闭环

当模型调用失败时，系统会自动尝试其他可用模型，形成完整的降级闭环：

```text
员工任务执行模型调用流程：
1. 解析员工专属模型：brainModelResolver.resolve(neuronId)
2. 如果专属模型存在，尝试调用 callLLM(assignedModel)
   - 成功 → 返回 COMPLETED 结果
   - 失败 → 记录 WARN 日志，继续下一步
3. 获取 fallback 模型列表：
   - 优先：modelPoolManager.getAllModels() → isEnabled → isRecommended
   - 如果为空：modelPoolManager.getAllModels() → isEnabled
4. 遍历 fallback 模型列表：
   - 对每个模型调用 callLLM(fallbackModel)
   - 成功 → 返回 COMPLETED 结果，记录 INFO 日志
   - 失败 → 记录 WARN 日志，继续下一个
5. 所有模型都失败 → 返回 DEGRADED 结果
   - outcomeStatus = DEGRADED
   - 包含提示："模型调用暂不可用，此任务尚未真实完成"
```

**代码落点**：`core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java`

```java
// executeWithModelPoolAndOutcome() 方法实现
ResolvedBrainModel assignedModel = brainModelResolver.resolve(def.neuronId());
if (assignedModel != null) {
    String result = callLLM(assignedModel, systemPrompt, userMessage);
    if (result != null && !result.isBlank()) {
        return EmployeeTaskExecutionOutcome.completed(...);
    }
}

List<LlmModel> fallbackModels = modelPoolManager.getAllModels().stream()
    .filter(LlmModel::isEnabled)
    .filter(LlmModel::isRecommended)
    .toList();

for (LlmModel model : fallbackModels) {
    ResolvedBrainModel fallbackResolved = brainModelResolver.resolveRaw(
        model.getProviderId(), model.getModelName());
    String result = callLLM(fallbackResolved, systemPrompt, userMessage);
    if (result != null && !result.isBlank()) {
        return EmployeeTaskExecutionOutcome.completed(...);
    }
}

return EmployeeTaskExecutionOutcome.degraded(...);
```

#### 3.3.3 验收标准

- 不再 fallback 到 Ollama 不存在模型。
- 同一模型连续失败后进入短期 cooldown。
- receipt 中可见 `model_provider`、`model_name`、`failure_reason`、`needs_retry`。
- 日志中能看到模型健康摘要，而不是重复 WARN 风暴。

---

### 阶段 4：长任务异步进度推送

**目标**：5 秒短等待外的执行任务仍可持续追踪。

#### 3.4.1 已完成

- `ExecutionStatusController` 已提供：

```text
GET /api/executions/{executionId}
```

#### 3.4.2 待修改文件

| 文件 | 修改内容 |
| --- | --- |
| `gateway/websocket/DepartmentWebSocketHandler.java` | 增加 execution progress 消息类型推送 |
| `gateway/service/DepartmentChatService.java` | receipt 到达后触发 progress event |
| `core/autonomy/EmployeeExecutionReceiptService.java` | 可选增加 listener/observer 接口 |
| `core/autonomy/impl/FileBasedEmployeeExecutionReceiptService.java` | recordReceipt 后通知 listener |

#### 3.4.3 WebSocket 消息格式

```json
{
  "type": "execution_progress",
  "executionId": "...",
  "status": "WAITING_RECEIPT|COMPLETED|PARTIAL_OR_FAILED",
  "receiptCount": 1,
  "completedCount": 1,
  "failedCount": 0,
  "updatedAt": "..."
}
```

#### 3.4.4 验收标准

- 短等待超时后，前端仍能通过 API 查询状态。
- receipt 到达后，WebSocket 主动推送 progress。
- 关闭的 WebSocket session 不再被写入。

#### 3.4.5 WebSocket 连接与任务绑定设计

为了解决“同一个用户的相同任务无法继续关联”“不同用户任务记录互相串扰”“记忆写错对象”的问题，WebSocket 连接必须同时绑定 `userId`、`sessionId` 和 `taskKey`，不能仅依赖单一的 room 名称或前端临时状态。

**设计原则**：

1. **用户级隔离**：每个 WebSocket 连接在握手成功后必须解析并校验用户身份，连接上下文中保存 `userId`、`tenantId`、`departmentCode`、`accessLevel`。
2. **任务级归一**：同一用户对同一业务意图发起多次重连、刷新或重复提交时，系统应通过稳定的 `taskKey` 统一归并到同一个任务轨迹，而不是创建新的孤立任务。
3. **幂等绑定**：如果已有 `taskKey` 对应的 active execution，则新连接应复用该 execution 的状态，不重新创建记忆主记录，只追加新的 WebSocket subscriber。
4. **可恢复订阅**：断线重连后，客户端携带 `taskKey` 与 `lastEventId`，服务端应恢复到原 execution 的最新状态，继续推送未消费的 progress / receipt / final summary。
5. **记忆写入唯一键**：任务记忆、执行记录、receipt、artifact 记录都必须带 `userId + taskKey + executionId` 三元关联，禁止只按 `taskTitle` 或自然语言摘要落库。

**推荐的连接流程**：

```text
1. 前端建立 WebSocket 时携带 access token、taskKey、optional executionId
2. 服务端鉴权后创建 SessionContext(userId, tenantId, departmentCode, taskKey)
3. 若 taskKey 已存在 active execution，则复用该 execution
4. 若 taskKey 不存在，则由 DepartmentChatService 创建新的 execution，并回写 taskKey
5. DepartmentWebSocketHandler 以 userId+taskKey 作为订阅路由键
6. receipt / progress / final summary 均按路由键定向广播
7. 断线重连时根据 taskKey 恢复订阅，避免重复创建任务记忆
```

**实现落点建议**：

| 文件 | 责任 |
| --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | 在握手和消息解析阶段注入 `userId` / `taskKey` / `executionId`，维护连接与任务映射 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/SessionContext.java` | 保存当前连接所属用户、部门、任务与租户上下文 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java` | 负责按 `taskKey` 查找或创建 execution，并将任务写回统一的持久化记录 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/` | 所有 receipt、artifact、summary、trace 都沿用同一个 executionId，避免记忆错乱 |

**需要避免的错误设计**：

- 只按 WebSocket sessionId 识别任务，session 一断开就丢失关联。
- 只按自然语言任务名识别任务，相同描述但不同用户会覆盖彼此记录。
- 只按 departmentCode 识别任务，同部门多个用户会共享错误记忆。
- 将新连接直接视为新任务，导致同一用户刷新页面后无法继续原任务。

#### 3.4.6 WebSocket 握手参数规范

握手阶段必须携带并校验以下参数；其中 `accessToken`、`taskKey` 为必需，`executionId` 为可选复用参数：

| 参数 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `accessToken` | string | 是 | 用于鉴权，必须能解析出 `userId`、`tenantId`、`accessLevel` |
| `taskKey` | string | 是 | 同一用户同一业务任务的稳定归并键 |
| `executionId` | string | 否 | 已存在 execution 时用于断线重连或显式复用 |
| `departmentCode` | string | 否 | 由服务端从鉴权结果和任务路由计算，客户端不应强制覆盖 |
| `lastEventId` | string | 否 | 断线重连时用于补发未消费事件 |
| `clientSessionId` | string | 否 | 前端调试标识，仅用于日志，不作为唯一业务键 |

**握手校验规则**：

1. 仅凭 `clientSessionId` 不得建立任务关联。
2. `taskKey` 缺失时必须拒绝进入业务通道，避免匿名任务污染记忆。
3. `executionId` 若存在，必须属于同一 `userId + taskKey` 组合。
4. 若 `accessToken` 解析出的 `userId` 与 `taskKey` 绑定用户不一致，连接应直接拒绝。
5. 握手成功后，服务端必须在 `SessionContext` 中固化 `userId`、`tenantId`、`departmentCode`、`taskKey`、`executionId`。

#### 3.4.7 taskKey 生成规则

`taskKey` 不是前端随意拼接的标题，而是服务端可复算、可归并、可去重的任务稳定标识。推荐规则如下：

```text
taskKey = hash(
  normalize(userId) + "|" +
  normalize(tenantId) + "|" +
  normalize(departmentCode) + "|" +
  normalize(intent) + "|" +
  normalize(primaryTarget) + "|" +
  normalize(scopeFingerprint)
)
```

**字段含义**：

- `intent`：任务意图，例如 `develop_webpage`、`analyze_data`、`generate_doc`。
- `primaryTarget`：主要对象或目标，例如“红色小球网页”“部门周报”“用户画像分析”。
- `scopeFingerprint`：用于区分范围的结构化摘要，例如页面模板、文件集合、时间窗口、项目编号。

**生成约束**：

1. 同一用户、同一意图、同一目标、同一作用域，生成相同 `taskKey`。
2. 不同用户即使描述完全相同，也必须生成不同 `taskKey`。
3. 同一用户如果更换了任务目标或作用域，应生成新的 `taskKey`，以避免错误续接旧任务。
4. 任务标题、自然语言正文、前端临时状态都不能直接作为 `taskKey`。
5. 推荐由服务端统一生成并回传给前端，前端只负责携带，不负责决定。

#### 3.4.8 对话数据与 `documents` 同构分类规则

正常情况下，对话中沉淀的 `data` 不应是无结构散落存放，而应像 `documents` 目录一样具备清晰分类、层级和归档规则。也就是说，WebSocket 对话、execution 记录、receipt、artifact、知识摘要、绩效摘要都应该进入对应的业务分类，而不是统一堆在一个扁平的 memory 目录里。

**分类原则**：

1. **按业务域分类**：对话沉淀数据必须按 `company`、`governance`、`department`、`shared`、`task`、`artifact`、`receipt`、`memory` 等维度分层。
2. **按用户与任务分类**：同一用户的历史对话要按 `userId/taskKey/executionId` 归档，避免不同用户同名任务混写。
3. **按类型分类**：聊天原文、执行回执、产物文件、审核结果、最终总结、知识沉淀分别落在不同子目录或不同表记录中。
4. **按生命周期分类**：active、waiting_receipt、completed、failed、needs_rework 等状态应进入不同索引视图，方便恢复与检索。
5. **与 `documents` 保持同构命名**：如果 `documents` 里有部门、政策、流程、模板、报告的清晰层级，那么 `data` 也应保持相似的组织方式，至少在元数据和检索标签上保持一致。

**推荐的存储映射**：

```text
data/
  conversations/
    {tenantId}/
      {userId}/
        {taskKey}/
          {executionId}/
            session.json
            events.jsonl
            receipts/
            artifacts/
            summary.json
  memory/
    {tenantId}/
      {userId}/
        {taskKey}/
          knowledge.json
          performance.json
  indexes/
    task-index.json
    user-index.json
    execution-index.json
```

**设计要求**：

- `documents` 负责“规范知识与业务文档”，`data` 负责“运行时沉淀与执行轨迹”。
- 两者都必须有明确分类，而不是无意义的平铺文件堆。
- 如果 `documents` 侧已经存在 `department/*/policies`、`procedures`、`templates`、`reports` 等层级，那么 `data` 侧也应建立对应的运行时分类视图，便于排查任务和回放历史。
- WebSocket 连接恢复时，应能按分类快速定位某个用户某个任务的完整上下文。

#### 3.4.9 服务端连接映射表结构

服务端需要同时维护“连接级映射”和“任务级映射”，以支持一任务多连接、断线重连和精确广播。

**建议数据结构**：

```text
connectionsBySessionId: Map<sessionId, ConnectionContext>
connectionsByUserTaskKey: Map<userId + "|" + taskKey, TaskConnectionGroup>
executionByUserTaskKey: Map<userId + "|" + taskKey, executionId>
subscriptionsByExecutionId: Map<executionId, Set<sessionId>>
```

**ConnectionContext** 建议包含：

- `sessionId`
- `userId`
- `tenantId`
- `departmentCode`
- `taskKey`
- `executionId`
- `connectedAt`
- `lastSeenAt`
- `lastEventId`
- `authenticated`
- `closedReason`

**TaskConnectionGroup** 建议包含：

- `userId`
- `taskKey`
- `executionId`
- `activeSessionIds`
- `subscriberCount`
- `state`（`NEW` / `RUNNING` / `WAITING_RECEIPT` / `COMPLETED` / `FAILED`）
- `latestTraceSeq`
- `latestReceiptSeq`
- `latestArtifactSeq`

**更新规则**：

1. 新连接进入时，先按 `sessionId` 建立 `ConnectionContext`，再按 `userId + taskKey` 归并到 `TaskConnectionGroup`。
2. 同一 `userId + taskKey` 可对应多个并发连接，但只应指向同一个 `executionId`。
3. `executionId` 只允许在首次创建任务时生成，后续重连只能复用。
4. 连接关闭时只移除 `sessionId` 级订阅，不得删除任务级执行记录。
5. 当一个 `TaskConnectionGroup` 没有活跃 session 时，执行状态仍需保留，供后续重连恢复。

#### 3.4.9 execution 复用时序图

```text
前端A                WebSocketHandler           DepartmentChatService           ExecutionStore
  |                        |                              |                             |
  |---握手(taskKey)------->|                              |                             |
  |                        |---鉴权并创建SessionContext-->|                             |
  |                        |---查询 userId+taskKey ------>|                             |
  |                        |<--无 active execution--------|                             |
  |                        |---创建新 execution---------->|---persist execution-------->|
  |                        |<--executionId----------------|<--返回 executionId----------|
  |                        |---注册 session 订阅----------|                             |
  |<--connected------------|                              |                             |
  |---发送首条任务消息----->|---复用同一 execution-------->|                             |
  |                        |---派发/订阅 receipt---------->|                             |
  |                        |                              |---receipt/artifact 入库---->|
  |                        |<--progress / receipt / summary 广播------------------------|
  |
  |---断线重连(taskKey)---->|                              |                             |
  |                        |---按 userId+taskKey 查找----->|                             |
  |                        |<--命中同一 executionId-------|                             |
  |                        |---恢复 session 订阅----------|                             |
  |<--继续接收进度---------|                              |                             |
```

**关键时序约束**：

- execution 的创建必须早于员工派发。
- subscription 的建立必须早于可能产生的 receipt 广播。
- 重连后若 execution 已完成，只做状态回放，不允许重新创建新的业务记录。
- 若同一 `taskKey` 已在执行中，新连接只能复用，不得并发开新 execution。

#### 3.4.10 SessionContext 字段定义

`SessionContext` 应该是 WebSocket 连接、任务执行、记忆沉淀三者之间的统一上下文容器，尽量保证一次握手后后续链路都能从这里拿到完整身份与任务信息。

**建议字段**：

```text
sessionId: string
userId: string
tenantId: string
departmentCode: string
accessLevel: string
taskKey: string
executionId: string | null
requestId: string | null
clientSessionId: string | null
lastEventId: string | null
connectedAt: Instant
lastSeenAt: Instant
connectionState: CONNECTING | AUTHENTICATED | BOUND | RECONNECTING | CLOSED
subscriptionState: NEW | ACTIVE | STALE | CLOSED
executionState: NEW | RUNNING | WAITING_RECEIPT | COMPLETED | FAILED | NEEDS_REWORK
activeRouteKey: string   // 例如 userId|taskKey
traceNamespace: string   // 例如 tenantId/userId/taskKey/executionId
memoryNamespace: string  // 例如 data/conversations/{tenantId}/{userId}/{taskKey}
```

**建议职责**：

1. 作为握手鉴权后的唯一连接上下文对象。
2. 作为所有 websocket event、receipt、progress、summary 的路由依据。
3. 作为 `ConnectionRegistry` 的 value 载体，保证可快速查找、续接和清理。
4. 作为任务记忆落盘时的命名空间来源，避免 data 混写。

#### 3.4.11 ConnectionRegistry 接口草案

`ConnectionRegistry` 负责维护连接级别、任务级别、执行级别的映射关系。它不直接做业务决策，只负责索引、订阅、复用、清理和查询。

```java
public interface ConnectionRegistry {
    ConnectionContext register(ConnectionContext context);
    Optional<ConnectionContext> findBySessionId(String sessionId);
    Optional<ConnectionContext> findByUserTaskKey(String userId, String taskKey);
    Optional<ConnectionContext> findByExecutionId(String executionId);
    TaskConnectionGroup bindToTask(String userId, String taskKey, String sessionId);
    String resolveOrCreateExecutionId(String userId, String taskKey);
    void attachSubscription(String executionId, String sessionId);
    void detachSubscription(String sessionId);
    void markSeen(String sessionId, String lastEventId);
    void markExecutionState(String executionId, String state);
    void removeSession(String sessionId);
    void evictClosedTasks(String userId, String taskKey);
    List<ConnectionContext> listActiveSessions(String userId, String taskKey);
}
```

**实现要求**：

- `register()` 必须是幂等的，同一 `sessionId` 重复注册不能创建脏数据。
- `resolveOrCreateExecutionId()` 只能在没有活跃 execution 时创建新值。
- `detachSubscription()` 只能移除 session 级订阅，不能影响 execution 本体。
- `evictClosedTasks()` 只能在任务最终结束且超过保留窗口后执行。
- 该接口可以由内存实现、Redis 实现或数据库实现承载，但语义必须一致。

#### 3.4.12 taskKey 计算伪代码

```text
function computeTaskKey(userId, tenantId, departmentCode, intent, primaryTarget, scopeFingerprint):
    normalizedUserId = normalize(userId)
    normalizedTenantId = normalize(tenantId)
    normalizedDepartment = normalize(departmentCode)
    normalizedIntent = normalize(intent)
    normalizedTarget = normalize(primaryTarget)
    normalizedScope = normalize(scopeFingerprint)

    raw = join("|", [
        normalizedUserId,
        normalizedTenantId,
        normalizedDepartment,
        normalizedIntent,
        normalizedTarget,
        normalizedScope
    ])

    fingerprint = sha256(raw)
    return "task_" + fingerprint[0:16]
```

**辅助规则**：

- `normalize()` 应统一做大小写折叠、空白压缩、全半角规整、无意义标点去除。
- `scopeFingerprint` 应来自结构化输入，不建议直接使用长文本全文。
- 若同一请求重复提交且前端未改任务目标，必须算出相同 `taskKey`。
- 如果用户显式要求开启新任务，服务端可在原始 key 后追加 `revision` 或 `epoch` 版本号，但仍需保留原始归并键。

#### 3.4.13 data/ 目录树建议草图

为了让对话数据与 `documents` 保持同构，建议 `data/` 也按“业务域 + 用户 + 任务 + 执行”分层，而不是把所有运行时数据堆成一层目录。

```text
data/
  conversations/
    tenant-{tenantId}/
      user-{userId}/
        task-{taskKey}/
          execution-{executionId}/
            session.json
            events.jsonl
            final-summary.json
            receipts/
              receipt-001.json
              receipt-002.json
            artifacts/
              index.html
              style.css
              script.js
            traces/
              trace-001.json
              trace-002.json
  memory/
    tenant-{tenantId}/
      user-{userId}/
        task-{taskKey}/
          knowledge.json
          performance.json
          execution-notes.json
  indexes/
    by-user/
      {userId}.json
    by-task/
      {taskKey}.json
    by-execution/
      {executionId}.json
  archive/
    tenant-{tenantId}/
      {year}/
        {month}/
          completed/
          failed/
```

**草图说明**：

- `conversations/` 保存可回放的对话轨迹与执行事件。
- `memory/` 保存抽象后的知识与绩效沉淀，不直接塞原始聊天全文。
- `indexes/` 保存检索索引，方便按用户、任务、执行号快速恢复。
- `archive/` 保存历史归档，避免活跃目录膨胀。

**落地要求**：

1. `data` 的目录命名必须和 `documents` 一样明确、分层、可检索。
2. 同一任务的 WebSocket 事件、receipt、artifact、summary 必须落在同一个 `executionId` 命名空间下。
3. 不同用户即使任务名称相同，也不能共享同一 `data` 路径。
4. 如果后续迁移到数据库或对象存储，目录结构要能够直接映射为索引键或对象 key。

### 3.4.14 ConnectionRegistryImpl 方法职责

`ConnectionRegistryImpl` 是 `ConnectionRegistry` 的实际实现，建议按“连接注册、任务归并、执行复用、订阅广播、断线清理、重连补发”六类职责拆分内部方法，避免所有逻辑塞进一个巨型类。

**建议方法职责**：

| 方法 | 职责 |
| --- | --- |
| `register(ConnectionContext context)` | 注册或更新单个 WebSocket 连接，保证 `sessionId` 幂等 |
| `bindToTask(String userId, String taskKey, String sessionId)` | 将 session 归并到 `userId + taskKey` 对应的任务组 |
| `resolveOrCreateExecutionId(String userId, String taskKey)` | 查找已有 execution；不存在时创建新 execution 并建立映射 |
| `attachSubscription(String executionId, String sessionId)` | 将 session 加入 execution 的广播订阅集合 |
| `detachSubscription(String sessionId)` | session 关闭时移除订阅，不删除 execution 本体 |
| `markSeen(String sessionId, String lastEventId)` | 更新最后消费事件号，用于断线补发 |
| `markExecutionState(String executionId, String state)` | 更新任务组状态，例如 `RUNNING`、`WAITING_RECEIPT`、`COMPLETED` |
| `removeSession(String sessionId)` | 删除 session 级临时数据，但保留任务级历史 |
| `evictClosedTasks(String userId, String taskKey)` | 在任务归档后清理过期连接缓存 |
| `listActiveSessions(String userId, String taskKey)` | 查询当前任务下仍然活跃的所有 session |

**实现注意事项**：

- 内存实现适合开发和单实例调试，生产环境建议支持 Redis 或数据库持久化。
- 所有方法都应围绕 `userId + taskKey + executionId` 主键组合操作，避免仅靠 `sessionId` 做业务判断。
- 广播前必须先确认 `executionId` 已绑定到正确任务组，否则会把回执推错对象。

### 3.4.15 SessionContext 的 Java record/interface 结构

`SessionContext` 建议提供一个只读接口加一个不可变 record 实现，便于在 WebSocket、Service、Registry 之间安全传递。

```java
public interface SessionContext {
    String sessionId();
    String userId();
    String tenantId();
    String departmentCode();
    String accessLevel();
    String taskKey();
    String executionId();
    String requestId();
    String clientSessionId();
    String lastEventId();
    Instant connectedAt();
    Instant lastSeenAt();
    ConnectionState connectionState();
    SubscriptionState subscriptionState();
    ExecutionState executionState();
    String activeRouteKey();
    String traceNamespace();
    String memoryNamespace();
}

public record DefaultSessionContext(
    String sessionId,
    String userId,
    String tenantId,
    String departmentCode,
    String accessLevel,
    String taskKey,
    String executionId,
    String requestId,
    String clientSessionId,
    String lastEventId,
    Instant connectedAt,
    Instant lastSeenAt,
    ConnectionState connectionState,
    SubscriptionState subscriptionState,
    ExecutionState executionState,
    String activeRouteKey,
    String traceNamespace,
    String memoryNamespace
) implements SessionContext {}
```

**枚举建议**：

```text
ConnectionState: CONNECTING | AUTHENTICATED | BOUND | RECONNECTING | CLOSED
SubscriptionState: NEW | ACTIVE | STALE | CLOSED
ExecutionState: NEW | RUNNING | WAITING_RECEIPT | COMPLETED | FAILED | NEEDS_REWORK
```

**使用约束**：

- `SessionContext` 不应暴露可变集合，避免并发修改导致连接状态错乱。
- `memoryNamespace` 应直接用于落盘路径或对象存储 key 的前缀生成。
- `traceNamespace` 应作为 trace/receipt/event 的统一路由前缀。

### 3.4.16 data 对应的实际落盘路径命名规范

为了让运行时数据像 `documents` 一样可分类、可追踪、可迁移，建议 `data` 的落盘路径采用统一前缀和固定层级。

**规范格式**：

```text
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/...
data/memory/{tenantId}/{userId}/{taskKey}/...
data/indexes/{indexType}/{partitionKey}.json
data/archive/{tenantId}/{yyyy}/{MM}/{state}/...
```

**命名规则**：

1. `tenantId`、`userId`、`taskKey`、`executionId` 必须经过安全规范化，禁止直接拼接原始输入。
2. 路径层级固定，不得根据任务标题自由增减目录层级。
3. `executionId` 是运行时强隔离边界，不能省略；同一任务不同执行必须进入不同目录。
4. `indexType` 推荐限定为 `user`、`task`、`execution`、`department`、`receipt`、`artifact`。
5. 不允许把原始聊天全文直接作为文件名或目录名。

**文件建议**：

- `session.json`：连接快照、身份、状态、最后活动时间。
- `events.jsonl`：按时间顺序追加的事件流。
- `receipts/*.json`：员工回执、执行结果和失败原因。
- `artifacts/*`：真实产物文件，按原始文件名保存。
- `summary.json`：最终总结、验收结果和下一步建议。
- `trace-*.json`：结构化 Trace 备份，便于诊断。

### 3.4.17 WebSocket 重连和补发事件的具体处理流程

断线重连不能只是“重新连上”，而应该是“恢复原任务执行上下文并补发未消费事件”。

**建议流程**：

```text
1. 前端携带 accessToken + taskKey + lastEventId 重新握手
2. 服务端从 accessToken 解析 userId/tenantId/accessLevel
3. 服务端根据 userId + taskKey 查找已有 executionId
4. 如果 execution 不存在：
   4.1 创建新的 execution
   4.2 注册新的 SessionContext
   4.3 返回新 executionId
5. 如果 execution 存在：
   5.1 复用原 executionId
   5.2 将当前 session 加入订阅集合
   5.3 读取 lastEventId 之后的事件增量
   5.4 补发缺失的 progress / receipt / summary / artifact 信息
6. 更新 connectionState=RECONNECTING -> BOUND
7. 若任务已完成，仅回放最终状态，不允许重复触发执行
```

**补发优先级**：

1. `execution_progress`
2. `employee_execution_receipt_received`
3. `execution_receipts_aggregated`
4. `artifact_recorded`
5. `main_brain_finalized`

**失败处理**：

- 如果 `lastEventId` 失效，服务端应退化为发送完整状态快照。
- 如果 `taskKey` 与当前用户不匹配，连接应拒绝并写入安全审计日志。
- 如果 execution 已被归档，只允许读取结果，不允许再次写入。

### 3.4.18 待修复情况

以下问题仍需要后续修复或验证，避免只是文档设计到位但实现未闭环：

| 编号 | 问题 | 风险 | 处理建议 |
| --- | --- | --- | --- |
| R1 | `SessionContext` 目前可能仍只在 WebSocket 层短生命周期存在 | 断线重连后任务上下文可能丢失 | 将 `SessionContext` 的关键字段持久化到 `ConnectionRegistry` 或执行存储中 |
| R2 | `ConnectionRegistry` 可能尚未形成统一接口 | 连接、任务、执行之间的映射容易分散 | 先抽象接口，再由内存/Redis/数据库实现 |
| R3 | `taskKey` 生成逻辑若仍由前端拼接，容易重复或串任务 | 同名任务跨用户混写 | 改为服务端统一生成并回传 |
| R4 | `data/` 目录如果仍是扁平 memory 结构 | 运行时数据会继续乱序增长 | 按 `conversations/memory/indexes/archive` 重新整理 |
| R5 | 已有历史数据未分类迁移 | 旧数据回放和检索仍不稳定 | 增加迁移脚本和重建索引流程 |
| R6 | 重连补发若只看当前状态不看事件游标 | 会丢失中间进度与 receipt | 引入 `lastEventId` 游标和事件重放机制 |

---

### 阶段 5：MainBrain LLM 最终二次总结

**目标**：执行类任务最终回复由主脑基于完整上下文进行组织级收口，而不只是模板包装。

#### 3.5.1 新增/修改文件

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/MainBrainFinalSummaryService.java` | 新增接口 | 主脑最终总结服务 |
| `core/autonomy/impl/LlmMainBrainFinalSummaryService.java` | 新增实现 | 调用 MainBrain LLM 生成结构化总结 |
| `core/autonomy/impl/DefaultMainBrainFinalSummaryService.java` | 新增 fallback | LLM 不可用时使用现有 composer 风格模板 |
| `gateway/service/DepartmentChatService.java` | 修改 | 当 `FinalResponseCoordinator` 判断 `MAIN_BRAIN_COMPOSE` 时调用该服务 |
| `GatewayConfig.java` | 修改 | 注册 final summary service |

#### 3.5.2 输入内容

```text
originalMessage
DialogueDecision
MainBrainTaskPlan
DepartmentExecutionResult
aggregatedReceiptSummary
artifactRecords
completionGateResult
riskAndLimitations
nextActionCandidates
```

#### 3.5.3 输出结构

```json
{
  "status": "COMPLETED|PARTIAL|WAITING|FAILED",
  "userMessage": "最终回复正文",
  "deliverables": [],
  "acceptanceConclusion": "...",
  "risks": [],
  "nextActions": [],
  "requiresHumanReview": false
}
```

#### 3.5.4 验收标准

执行类任务最终 Trace 必须出现：

```text
main_brain_finalized(summary_source=llm_main_brain 或 fallback_composer)
```

---

### 阶段 6：执行结果验收层

**目标**：不仅统计 receipt，还要判断产物是否满足验收标准。

#### 3.6.1 新增/修改文件

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/ExecutionReceiptReviewer.java` | 新增接口 | 评估 receipts/artifacts 是否满足验收标准 |
| `core/autonomy/ExecutionReviewResult.java` | 新增 record | 评审结果、问题、返工建议 |
| `core/autonomy/impl/DefaultExecutionReceiptReviewer.java` | 新增 | 程序硬规则验收：状态、回执数量、文件存在、大小、基础格式 |
| `core/autonomy/impl/LlmExecutionReceiptReviewer.java` | 可选新增 | LLM 语义验收：产物是否满足用户目标和验收标准 |
| `gateway/service/DepartmentChatService.java` | 修改 | completion gate 改为读取 reviewer 结果 |

#### 3.6.2 程序硬规则

- 所有 dispatch 都有 receipt 或明确失败。
- 至少一个 completed receipt。
- artifact 文件存在且非空。
- HTML 产物包含 `<html` 或 `<!doctype html`。
- failed/degraded/needs_retry 不允许直接通过完成闸门。

#### 3.6.3 LLM 语义验收

- 对照 `mainBrainTaskPlan.acceptanceCriteria()`。
- 判断 artifact 内容是否满足用户目标。
- 输出返工建议和二次派发建议。

---

### 阶段 7：员工工具授权与真实工具执行

**目标**：从“LLM 文本执行”推进到“按任务类型调用真实工具”。

#### 3.7.1 工具授权修复

| 文件 | 修改内容 |
| --- | --- |
| `core/employee/registry/FixedEmployeeRegistry.java` | 确认员工定义中的工具名 |
| `core/tool/ToolRegistry.java` 或实际实现 | 输出已注册工具名 |
| `core/autonomy/impl/RegistryBackedFixedEmployeeDispatcher.java` | 分派前只注入实际可用工具 |
| `core/autonomy/impl/LlmBasedFixedEmployeeDispatcher.java` | Prompt 使用 resolved tools，不使用定义中的未解析工具 |

启动日志应输出：

```text
employee=T09 resolvedTools=[file_write,browser_preview] missingTools=[claude_cli]
```

#### 3.7.2 真实执行器

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/EmployeeTaskExecutor.java` | 新增接口 | 执行员工任务 |
| `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | 新增实现 | 按任务类型调用工具/沙箱/文件系统 |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | 修改 | 从内部 LLM 文本执行改为委托 `EmployeeTaskExecutor` |

#### 3.7.3 任务类型策略

| taskType | 执行策略 |
| --- | --- |
| `web_prototype` / `web_development` | 生成 `index.html`、`style.css`、`script.js`，可选浏览器预览 |
| `software_development` | 生成项目目录，必要时走 sandbox 构建/测试 |
| `document_generation` | 生成 Markdown/Doc 产物 |
| `data_analysis` | 生成分析报告，必要时调用数据工具 |
| `legal_review` / `finance_workflow` | 默认需要人审或审批确认 |

#### 3.7.4 禁用模拟执行误用

- `MinimalEmployeeTaskExecutor` 如果保留，应标记 `@Deprecated`。
- 不在生产 `GatewayConfig` 注册。
- 文档中标注只允许测试/legacy 场景使用。

---

### 阶段 8：DockerSandboxService 策略

**目标**：明确 Docker 可用和不可用两种运行路径，避免任务被派到不可用环境。

#### 3.8.1 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `core/sandbox/` 相关服务 | 暴露 sandbox availability |
| `core/autonomy/DecisionContextBuilder` | 将 sandbox availability 注入上下文 |
| `core/autonomy/AssignmentPreparationService` | 任务准备 metadata 增加 `executionEnvironment` |
| `DynamicEmployeeTaskConsumerRegistry` / `EmployeeTaskExecutor` | 根据 executionEnvironment 选择 Docker、本地受限、仅产物生成 |

#### 3.8.2 executionEnvironment

```text
DOCKER_SANDBOX
LOCAL_RESTRICTED
ARTIFACT_ONLY
HUMAN_REVIEW_REQUIRED
```

#### 3.8.3 验收标准

- Docker 不可用时，任务计划明确显示 `ARTIFACT_ONLY` 或 `LOCAL_RESTRICTED`。
- 不再出现“计划使用 Docker 但运行时 dockerCmdExecFactory 缺失”的静默失败。

---

### 阶段 9：Artifact 持久化与专用 API

**目标**：从内存记录升级为可恢复、可查询、可下载的产物资产。

#### 3.9.1 新增/修改文件

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/database/entity/ArtifactRecordEntity.java` | 新增 | artifact 数据库实体 |
| `core/database/repository/ArtifactRecordRepository.java` | 新增 | artifact 查询 |
| `core/autonomy/impl/JpaArtifactRecordService.java` | 新增 | 替代或补充内存版 |
| `gateway/controller/ArtifactController.java` | 新增 | 专用 artifact list/detail/download/preview API |
| `core/resources/db/migration/` | 新增 migration | artifact 表结构 |

#### 3.9.2 表字段

```text
artifact_id
execution_id
department
owner_employee_code
owner_employee_neuron_id
type
path
name
summary
size_bytes
sha256
created_at
metadata_json
```

#### 3.9.3 启动索引

- 启动时扫描 `data/artifacts`。
- 数据库缺失记录时补索引。
- 文件不存在但数据库存在时标记 missing，而不是直接报错。

---

### 阶段 10：固定员工数据库治理

**目标**：员工画像、工具授权、模型偏好从静态 fallback 切到数据库启用态定义。

#### 3.10.1 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `core/database/entity/FixedEmployee*Entity.java` | 补齐字段和状态 |
| `core/database/repository/FixedEmployee*Repository.java` | 查询启用态定义 |
| `core/employee/registry/FixedEmployeeRegistry.java` | 优先数据库，静态仅 fallback |
| `FixedEmployeeController.java` | 管理启用/停用/工具授权/模型偏好 |

#### 3.10.2 验收标准

- 启动日志显示数据库启用员工数量。
- 静态 fallback 出现时记录 WARN 和修复建议。
- LLM 员工分派使用数据库员工画像。

---

### 阶段 11：知识、绩效、主动建议与观测

#### 3.11.1 知识/绩效质量

| 文件 | 修改内容 |
| --- | --- |
| `KnowledgeCaptureService` | 只基于验收通过的 artifact/receipt 提炼知识 |
| `PerformanceCaptureService` | 区分 completed/partial/failed/degraded/needs_retry |
| `DepartmentChatService` | 保持 completion gate 后触发沉淀 |

#### 3.11.2 主动建议和风险评估

| 文件 | 修改内容 |
| --- | --- |
| `core/proactive/predictor/PatternPredictor.java` | 定位为统计特征层 |
| `core/proactive/predictor/RiskPredictor.java` | 定位为基础告警层 |
| `core/proactive/llm/LlmProactiveAdvisor` | 生成最终主动建议 |
| `core/proactive/llm/LlmRiskAssessor` | 生成业务风险解释和处置方案 |

#### 3.11.3 日志与观测

每个任务输出一条聚合摘要：

```text
requestId=...
executionId=...
decisionSource=...
model=...
dispatchCount=...
receiptCount=...
artifactCount=...
completionGate=...
finalSummarySource=...
```

---

## 4. 推荐开发顺序

### Sprint 1：先让 LLM 主路径可观测

1. 检查 `GatewayConfig` Bean 注册，确保 LLM-first 默认启用。
2. 补齐 analyzer/director/dispatcher/composer Trace source。
3. 初步新增 `DecisionContext` / `DecisionContextBuilder`，先注入员工和工具上下文。
4. 文档和日志确认不再只靠规则链路判断。

### Sprint 2：让最终回复真正回主脑

1. 新增 `MainBrainFinalSummaryService`。
2. `DepartmentChatService` 在 `MAIN_BRAIN_COMPOSE` 时调用该服务。
3. MainBrain LLM 不可用时降级现有 `MainBrainResponseComposer`。
4. 更新 E2E Trace 验收。

### Sprint 3：补齐执行验收层

1. 新增 `ExecutionReceiptReviewer`。
2. 程序硬规则检查 receipt/artifact。
3. 可选接入 LLM 语义验收。
4. 未通过时输出返工建议或二次派发策略。

### Sprint 4：真实工具执行第一版

1. 修复员工 resolved tools。
2. 新增 `EmployeeTaskExecutor`。
3. `DynamicEmployeeTaskConsumerRegistry` 委托 executor。
4. web/code 任务生成多文件项目产物。

### Sprint 5：生产化治理

1. Artifact 数据库持久化和专用 API。
2. 固定员工数据库启用态治理。
3. 模型熔断与超时分层。
4. 结构化观测和日志降噪。

### Sprint 6：最优流程 — 轻量路由 + 直达部门（第 11 章 M1/M2）

1. 新增 `TaskRouteClassifier`，实现单部门任务直达部门大脑。
2. 修复 `MainBrain.forwardToDepartment()`，实现跨部门消息转发。
3. `DepartmentChatService` 在 orchestrate 之前调用 classify。
4. Trace 验收：单部门任务不出现 `main_brain_planned`。

### Sprint 7：最优流程 — 自行领取 + 审查闭环（第 11 章 M3/M4/M7）

1. 新增 `DepartmentTodoPool` + `EmployeeSelfClaimService`。
2. `FixedEmployeeRegistry` 增加 `downstreamReviewers` 配置。
3. 新增 `InternalReviewService` + 审查状态机 + 轮次终止条件。
4. `ToolBackedEmployeeTaskExecutor` 执行完成后提交审查而非直接发 Receipt。
5. 技术部门试点审查闭环（T09 编写 → T10 审查 → 修改 → 完成标记）。

### Sprint 8：最优流程 — 部门聚合 + 主脑收口升级（第 11 章 M5/M6/M8）

1. 新增 `DepartmentAggregationService` + `DepartmentDeliverable`。
2. `DepartmentChatService` 将 receipt 聚合改为调用聚合服务。
3. 主脑增加跨部门一致性检查。
4. 全链路 Trace 更新 + 场景验收测试。

---

## 5. 端到端验收标准

以“帮我做一个红色小球跳动的网页”为验收样例：

### 5.1 Trace 验收

必须包含：

```text
intake_classified(analyzer_type=llm_based 或 fallback reason)
main_brain_planned(director_type=llm_based 或 fallback reason)
brain_routed
department_plan_created
employee_assignment_planned(dispatcher_type=llm_based 或 fallback reason)
assignment_batch_prepared
employee_assigned
employee_execution_receipt_received
execution_receipts_aggregated
artifact_recorded
main_brain_finalized(summary_source=llm_main_brain 或 fallback_composer)
```

### 5.2 结果验收

- 至少一个员工 receipt 为 `COMPLETED`。
- completion gate 为 `PASSED`。
- 最终回复包含执行员工、完成状态、artifact 路径、验收结论、限制和下一步。

### 5.3 文件产物验收

- `data/artifacts/tech/{executionId}/` 下存在 HTML 或多文件项目。
- HTML 文件可直接打开并显示红色小球跳动动画。
- 可通过 `AgentFileController` 或后续 `ArtifactController` 下载/预览。

### 5.4 稳定性验收

- 不再出现 WebSocket `TEXT_PARTIAL_WRITING` 异常风暴。
- 不再 fallback 到 Ollama 不存在的模型。
- 回执通道不再出现 `Channel not found`。
- 模型失败能进入 cooldown，并写入 receipt failure reason。

### 5.5 2026-05-14 日志核验补充验收项

本次 `docker logs living-agent-service` 暴露出“看似完成但闭环未闭合”的问题，后续必须新增以下验收项：

- receipt 通道必须在任务派发前完成 execution 注册和订阅；日志中不得出现业务回执广播到 `0 subscribers` 后才注册 execution 的时序。
- 红色小球网页样例必须生成 `index.html` 或等价可预览 HTML 文件，路径应归档到 `data/artifacts/{department}/{executionId}/`，不能只生成员工维度 `result.txt`。
- 端到端日志必须在同一个业务 request/execution 上看到 `employee_execution_receipt_received`、`execution_receipts_aggregated`、`artifact_recorded`、`main_brain_finalized`。
- 员工 receipt 的 `COMPLETED` 必须代表业务验收完成，而不是仅代表执行器代码路径结束；如果只生成通用文本、未生成目标产物、未执行工具或未通过验收，应标记为 `FAILED` 或 `NEEDS_REWORK`。
- 固定员工 delegateBrain 绑定必须与员工部门一致，不能出现 tech/sales/legal/cs/admin/ops 员工统一绑定到 finance brain 的情况。
- WebSocket 断连日志必须包含可诊断原因，避免 `transport error: error=null`。

---

## 6. 与其他文档的关系

| 文档 | 关系 |
| --- | --- |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | 本方案的代码落点依据，修改代码前必须优先参考 |
| `LLM_AUTONOMY_HARDCODE_ANALYSIS.md` | 专项审计文档，保留完整问题证据；本文只提炼工程化路线图 |
| `CODE_LOGIC_LANDING_VERIFICATION.md` | 代码逻辑落地验证报告（2026-06-05），识别了 7 项偏差（2P0+3P1+2P2），第 11 章最优流程设计部分源于此报告的改进建议 |
| `MODULE_AUTONOMY_ORCHESTRATION.md` | 自治编排模块文档，描述核心自治接口和 LLM/规则实现 |
| `权限与入口矩阵.md` | 权限矩阵和入口控制文档，定义登录态×身份×页面×通道×流程的权限规则 |
| `docs/old/*.md` | 历史框架和理念说明，不作为当前代码落点依据；如有冲突，以本方案和 `CODE_STRUCTURE_AND_FILE_GUIDE.md` 为准 |

---

## 7. 当前结论

当前系统已经从：

```text
可启动 + 可规划 + 可派发 + 可短等待回执 + 可聚合结果 + 可生成文件 artifact + 可下载 artifact 第一版
```

推进到：

```text
LLM-first 可观测主路径 + 模型熔断 + 异步进度推送 + 主脑二次总结 + 执行验收 + 工具执行第一版 + 持久化治理 + WebSocket 闭环修复 + 任务/项目统一 + 长期对话 + 大脑模型自动分配
```

下一步演进方向（第 11 章最优流程设计）：

```text
轻量路由（单部门直达）+ 员工自行领取 + 部门内审查闭环 + 部门级聚合交付 + 主脑跨部门协调升级
```

这一演进将系统从"中央指派 + 线性回执"模式升级为"分层自治 + 自行领取 + 内部审查 + 部门聚合 + 主脑收口"模式，更接近真实企业组织的协作方式。

### 7.1 本轮实施成果汇总（2026-05-13）

**新增文件**：
- `core/model/pool/ModelHealthRegistry.java` - 模型健康熔断机制
- `core/autonomy/EmployeeExecutionReceiptService.java` (增强) - ReceiptListener 机制
- `core/autonomy/impl/FileBasedEmployeeExecutionReceiptService.java` (增强) - listener 通知
- `core/autonomy/MainBrainFinalSummaryService.java` - 主脑最终总结接口
- `core/autonomy/impl/LlmMainBrainFinalSummaryService.java` - LLM 驱动总结
- `core/autonomy/impl/DefaultMainBrainFinalSummaryService.java` - fallback 模板总结
- `core/autonomy/ExecutionReviewResult.java` - 执行评审结果
- `core/autonomy/EmployeeTaskExecutor.java` - 员工任务执行器接口
- `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` - 工具驱动执行器

**修改文件**：
- `core/model/pool/BrainModelResolver.java` - 增加熔断过滤
- `gateway/config/GatewayConfig.java` - 注册新 Bean
- `gateway/websocket/DepartmentWebSocketHandler.java` - 增加进度推送

**阶段完成状态**：
- ✅ 阶段1-7：全部完成第一版实施
- ⏳ 待后续迭代：DynamicEmployeeTaskConsumerRegistry 接入真实执行器、DockerSandboxService 集成、Artifact 数据库持久化

### 7.2 系统能力升级

| 能力 | 升级前 | 升级后 |
| --- | --- | --- |
| 模型选择 | 硬编码/无健康检查 | 模型池 + 健康熔断 + 自动降级 |
| 长任务追踪 | 5秒短等待后结束 | WebSocket 实时进度推送 + API 查询 |
| 最终回复 | 模板包装 | LLM 主脑结构化总结 + fallback |
| 执行验收 | 仅统计回执 | 程序规则 + LLM 语义双重验收 |
| 员工执行 | LLM 文本生成 | 按任务类型调用工具/生成真实文件 |
| 任务路由 | 所有任务经主脑 | 轻量路由：单部门直达 + 跨部门走主脑（第 11 章） |
| 员工分派 | 中央 LLM 指派 | 自行领取 + 兜底指派混合模式（第 11 章） |
| 质量保障 | 员工直接发 Receipt | 部门内审查闭环 + 完成标记（第 11 章） |
| 成果聚合 | Receipt 直接到主脑 | 部门级聚合分析 + 一致性检查（第 11 章） |
| 跨部门协调 | forwardToDepartment 为 stub | 实际消息转发 + 跨部门一致性检查（第 11 章） |

### 7.3 大脑与固定数字员工做事规范审查（2026-05-19）

> 审查依据：结合 `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` 中的大脑、自治编排、固定员工、Prompt、职责卡与执行器代码索引，检查当前项目是否已经给所有部门大脑和固定数字员工提供“做事规范”。

#### 7.3.1 审查结论

当前项目**已经有规范雏形，但尚未形成统一、强制、可追踪的规范体系**。

现状可以概括为：

```text
有职责卡
有系统提示词
有 Agent Prompt
有 runbook
有文档工作流
有固定员工路由配置
有 DynamicPromptBuilder
有 InstructionFileLoader
有 BrainContext
有 AbstractBrain
有任务分派、回执、验收和产物机制

但缺少：
统一权威规范源
强制加载链
统一输出契约
统一澄清/越权规则
规范执行证据
```

因此，不能简单认为“大脑和员工已经不会乱”。更准确的判断是：**规范材料已经存在，但规范尚未完全进入所有大脑和固定员工的执行链路**。

#### 7.3.2 已存在的规范来源

##### 大脑侧规范来源

| 类型 | 文件/模块 | 说明 |
| --- | --- | --- |
| 大脑统一接口 | `living-agent-core/src/main/java/com/livingagent/core/brain/Brain.java` | 定义 Brain 统一处理入口 |
| 大脑上下文 | `living-agent-core/src/main/java/com/livingagent/core/brain/BrainContext.java` | 承载用户、部门、会话、权限、metadata |
| 大脑公共基类 | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/AbstractBrain.java` | 适合作为所有部门脑公共规范注入口 |
| 主脑/部门脑 | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/*Brain.java` | MainBrain、TechBrain、HrBrain、FinanceBrain 等 |
| 动态 Prompt | `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/DynamicPromptBuilder.java` | 已支持 role、personality、skills、tools、knowledge、guidelines 拼装 |
| 指令文件加载 | `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/InstructionFileLoader.java` | 已支持 `.living/{employeeId}/instructions.md` 与指令链加载 |
| 协作分工 | `living-agent-core/src/main/java/com/livingagent/core/brain/collaboration/*` | 支持部门大脑对团队成员进行角色化分工 |
| 上下文压缩 | `living-agent-core/src/main/java/com/livingagent/core/brain/compact/*` | 支持长上下文摘要和裁剪 |
| 部门映射 | `living-agent-core/src/main/java/com/livingagent/core/security/Department.java` | 定义 department code 与 brain name 的映射规则 |

##### 固定数字员工侧规范来源

| 类型 | 文件/模块 | 说明 |
| --- | --- | --- |
| 固定员工注册表 | `living-agent-core/src/main/java/com/livingagent/core/employee/registry/FixedEmployeeRegistry.java` | 固定数字员工定义、部门映射和能力注册入口 |
| 固定员工实体 | `FixedEmployeeDefinitionEntity` / `FixedEmployeePersonaEntity` / `FixedEmployeeProfileEntity` | 支持固定员工数据库治理 |
| 系统提示词 | `documents/shared/company/fixed-employee-system-prompts.md` | 固定员工系统级行为要求 |
| Agent Prompt | `documents/shared/company/fixed-employee-agent-prompt.md` | 固定员工 Agent 提示模板 |
| 自主执行手册 | `documents/shared/company/fixed-employee-autonomous-runbook.md` | 固定员工接到任务后的执行流程 |
| 文档工作流 | `documents/shared/company/fixed-employee-document-workflow.md` | 文档处理、归档、交付流程 |
| 职责卡模板 | `documents/shared/company/fixed-employee-duty-card-template.md` | 固定员工职责卡标准模板 |
| 部门职责卡 | `documents/shared/company/hr-20-hr-fixed-employee-duty-card.md` ~ `hr-26-legal-fixed-employee-duty-card.md` | HR、财务、技术、销售、运营、客服、法务固定员工职责 |
| 路由配置 | `documents/shared/company/fixed-employee-routing-config.yaml` | 固定员工路由和任务分派配置 |
| 员工任务单 | `EmployeeWorkAssignment` | 员工任务目标、角色、指令、产物和工具上下文 |
| 员工执行器 | `EmployeeTaskExecutor` / `ToolBackedEmployeeTaskExecutor` | 员工真实执行入口 |
| 员工回执 | `EmployeeExecutionReceiptService` | 员工执行结果回执和监听入口 |

#### 7.3.3 当前主要风险

| 风险 | 表现 | 后果 |
| --- | --- | --- |
| 规范分散 | 职责卡、Prompt、runbook、制度、代码分别存在 | 改一处不等于全链路生效 |
| 缺少强制加载链 | 不保证每个 brain/员工都加载完整规范 | 某些大脑或员工仍可能自由发挥 |
| 输出契约不统一 | 有的返回自然语言，有的返回结构化对象，有的只写日志 | 前端、Trace、回执、任务状态难以统一消费 |
| 澄清规则不统一 | 信息不足时，有的澄清，有的硬执行，有的等待超时 | 继续出现 `NEEDS_CLARIFICATION` 类问题 |
| 越权边界不统一 | 员工、部门脑、主脑的决策权限没有完全硬约束 | 员工可能替主脑决策，部门脑可能跨部门乱接任务 |
| 规范没有落到执行证据 | Prompt 里有要求，但 receipt/artifact/trace 未体现 | 后续无法审计“是否按规范做事” |

#### 7.3.4 统一做事规范设计

建议把所有大脑和固定数字员工的规范收敛为三层。

##### 第一层：角色规范

每个大脑和员工都必须明确：

```text
我是谁
我属于哪个部门
我能处理什么任务
我不能处理什么任务
我能调用哪些工具
我什么时候必须澄清
我什么时候必须上报主脑
我什么时候必须拒绝或转人工
```

##### 第二层：执行规范

所有任务型请求应遵循统一流程：

```text
接收输入
-> 识别意图
-> 加载会话和历史上下文
-> 判断权限与职责边界
-> 判断是否需要澄清
-> 生成计划
-> 分派员工
-> 准备任务单
-> 执行
-> 收集回执
-> 验收
-> 生成产物
-> 主脑/部门脑收口
-> 保存消息、Trace、任务、execution、artifact、知识/绩效记录
```

##### 第三层：输出规范

大脑和员工输出不能只是一段自然语言，至少应能映射为统一结构：

```text
status
summary
currentStep
blockingIssues
clarificationQuestions
assignedWorkers
artifacts
nextSteps
traceId
conversationId
taskKey
executionId
riskLevel
requiresHumanReview
```

其中：

- 大脑侧重点：规划、路由、澄清、风险、验收和最终收口。
- 员工侧重点：执行动作、产物、回执、失败原因和返工建议。
- 主脑侧重点：跨部门协调、是否继续执行、是否升级审批或人工介入。

#### 7.3.5 强制加载链建议

规范不能只存在于文档里，必须进入执行链路。推荐强制加载链如下：

```text
固定员工职责卡 / 大脑职责定义
-> 系统提示词
-> Agent Prompt
-> runbook / 文档工作流
-> 部门制度 / 治理规则
-> DynamicPromptBuilder 拼装
-> BrainContext / DecisionContext 注入上下文
-> DepartmentChatService / ConversationOrchestrator 执行编排
-> EmployeeWorkAssignment 下发任务单
-> EmployeeTaskExecutor 执行
-> EmployeeExecutionReceipt 回执
-> ExecutionReceiptReviewer 验收
-> ArtifactRecord / RuntimeEventStore / KnowledgeCapture / PerformanceCapture 沉淀
```

关键要求：

1. **职责卡是根约束**：先确定角色能做什么、不能做什么。
2. **Prompt 只负责注入规范，不负责临时发明规范**。
3. **runbook 必须进入员工任务单**，不能只作为文档存在。
4. **澄清/越权/审批规则必须在编排层硬判断**，不能完全交给模型自由发挥。
5. **回执和 Trace 必须记录规范执行结果**，例如是否澄清、是否越权拦截、是否人工介入。

#### 7.3.6 澄清、越权与失败处理规则

##### 澄清规则

当出现以下情况时，必须澄清：

- 目标不清楚。
- 验收标准不明确。
- 缺少关键输入。
- 涉及高风险、权限、财务、法务、人事等敏感动作。
- 模型或员工无法判断用户真实意图。

澄清时必须：

```text
保存 clarificationQuestions
更新 conversation 状态为 WAITING_USER
绑定 taskKey/executionId（如已创建）
前端返回澄清问题
禁止继续等待 output channel 导致超时
```

##### 越权规则

必须禁止：

- 固定员工替主脑做跨部门战略决策。
- 固定员工替部门脑做最终验收。
- 部门脑擅自接管其他部门主责任务。
- 低权限用户销毁会话、任务、artifact 或治理文档。
- 大脑/员工绕过 `WorkItemPermissionService` 操作任务、项目、会话。
- 员工擅自改写职责卡、系统提示词、治理规则。

##### 失败处理规则

执行失败不能静默或伪装成功，必须返回：

```text
failedReason
failedStage
retryable
suggestedNextStep
requiresHumanReview
```

#### 7.3.7 代码落点建议

| 目标 | 推荐落点 |
| --- | --- |
| 所有大脑公共行为约束 | `AbstractBrain` |
| 大脑/员工 Prompt 拼装 | `DynamicPromptBuilder` |
| 外部指令和制度加载 | `InstructionFileLoader` |
| 统一决策上下文 | `DecisionContext` / `DefaultDecisionContextBuilder` |
| 部门对话主链路 | `DepartmentChatService` |
| 自治编排入口 | `ConversationOrchestrator` |
| 固定员工注册和职责加载 | `FixedEmployeeRegistry` |
| 员工任务单 | `EmployeeWorkAssignment` / `DefaultAssignmentPreparationService` |
| 员工执行 | `EmployeeTaskExecutor` / `ToolBackedEmployeeTaskExecutor` |
| 员工回执 | `EmployeeExecutionReceiptService` |
| 执行验收 | `ExecutionReceiptReviewer` |
| 权限与越权判断 | `WorkItemPermissionService` / `AccessGateService` |
| 规范执行证据 | `AutonomyTraceService` / `RuntimeEventStore` / `ArtifactRecordService` |

#### 7.3.8 修复计划

| 优先级 | 修复项 | 目标 |
| --- | --- | --- |
| P0 | 建立统一输出契约 | 让大脑、员工、前端、Trace、回执使用同一组关键字段 |
| P0 | 固化澄清分支规则 | 所有 `NEEDS_CLARIFICATION` 都返回用户并持久化，不再超时 |
| P0 | 固化越权拦截规则 | 员工/部门脑不能越过主脑和权限系统乱做事 |
| P0 | 建立职责卡 → Prompt → runbook 的强制加载链 | 确保每个固定员工执行前都带着规范 |
| P1 | 把规范执行结果写入 Trace/Receipt | 可审计每次执行是否遵守规范 |
| P1 | 给每个 brain 建立职责/边界索引 | 避免部门脑职责交叉不清 |
| P1 | 给每个固定员工建立规范索引 | 明确员工编号、职责卡、Prompt、runbook、路由配置 |
| P2 | 定期检查职责卡与代码实现一致性 | 防止文档和代码长期漂移 |

#### 7.3.9 建议新增规范索引文档

建议后续新增：

```text
docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md
```

该文档用于建立以下映射：

```text
brainId / brainName -> 职责边界 -> Prompt 来源 -> 代码实现 -> 输出契约
employeeCode -> 部门 -> 职责卡 -> system prompt -> runbook -> routing config -> 执行器 -> 回执规范
```

这份索引可以配合 `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` 使用：

- `CODE_STRUCTURE_AND_FILE_GUIDE.md` 解决“文件在哪里”。
- `BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` 解决“每个脑/员工应该按什么规范做事”。

---

## 8. 代码落地审查报告（2026-05-16）

> 审查方法：逐阶段对照文档要求，检查代码文件是否存在、关键方法是否实现、是否被实际调用。

### 8.1 各阶段落地状态

| 阶段 | 文件存在 | 关键功能实现 | 是否被实际调用 | 落地状态 |
| --- | --- | --- | --- | --- |
| 1 LLM-first 主路径 | ✅ 全部存在 | ✅ 三个 LLM 实现已注册 | ✅ 已接入主流程 | ✅ 基本落地 |
| 2 DecisionContext | ✅ 全部存在 | ✅ 接口+实现+注册完整 | ✅ LlmBasedDialogueAnalyzer 已注入 DecisionContextBuilder，LlmDecisionClient 调用时传入 DecisionContext | ✅ 已生效 |
| 3 模型熔断 | ✅ 全部存在 | ✅ ModelHealthRegistry 完整实现 | ✅ ClaudeProxyService 中已接入 recordSuccess/recordFailure | ✅ 已生效 |
| 4 异步进度推送 | ✅ 全部存在 | ✅ pushExecutionProgress 方法完整 | ✅ DepartmentChatService 已注册 ReceiptListener | ✅ 已生效 |
| 5 主脑二次总结 | ✅ 全部存在 | ✅ 接口+LLM实现+fallback完整 | ✅ DepartmentChatService 已在 MAIN_BRAIN_COMPOSE 策略中调用 | ✅ 已接入 |
| 6 执行验收 | ✅ 全部存在 | ✅ LlmExecutionReceiptReviewer 完整 | ✅ LLM 不可用时 defaultAccept 仅 COMPLETED 状态低置信度通过，FAILED 状态拒绝 | ✅ 已修复 |
| 7 员工工具执行 | ✅ 全部存在 | ✅ ToolBackedEmployeeTaskExecutor 完整 | ✅ 已接入主流程 | ✅ 基本落地 |
| 8 DockerSandboxService | ✅ 全部存在 | ✅ Docker+Hybrid 双后端完整 | ✅ ToolBackedEmployeeTaskExecutor 使用 | ✅ 已落地 |
| 9 Artifact 持久化 | ✅ 全部存在 | ✅ Entity+Repository+Service+Controller+Migration | ✅ 已接入 | ✅ 已落地 |
| 10 固定员工数据库治理 | ✅ 全部存在 | ✅ 数据库优先加载已实现 | ✅ 已接入 | ✅ 已落地 |
| 11 知识绩效观测 | ✅ 全部存在 | ✅ DefaultKnowledgeCaptureService/DefaultPerformanceCaptureService 已实现 | ✅ GatewayConfig 已注册，DepartmentChatService 已调用 | ✅ 已落地 |

### 8.2 关键未落地问题（P0 级）

> 以下 P0-1 至 P0-4 已在 2026-05-16 第二轮修复中全部完成，详见 2.3.5 节。

#### ~~P0-1：模型熔断机制形同虚设~~ ✅ 已修复

`ModelHealthRegistry` 的 `recordSuccess()` / `recordFailure()` 已在 `ClaudeProxyService` 中接入，Provider 调用成功/失败时自动回调。同时 `ModelPoolManager` 启动时异步执行 `modelPerformanceAssessor.assessAllEnabledModels()`，不可用模型自动禁用。

#### ~~P0-2：异步进度推送断路~~ ✅ 已修复

`DepartmentChatService` 已注册 `ReceiptListener`，回执到达时自动调用 `DepartmentWebSocketHandler.pushExecutionProgress()`。通过 `@Lazy` 注入解决循环依赖。

#### ~~P0-3：主脑二次总结未接入主流程~~ ✅ 已修复

`DepartmentChatService` 已注入 `MainBrainFinalSummaryService`，在 `MAIN_BRAIN_COMPOSE` 策略分支中调用 `generateSummary()`。

#### ~~P0-4：员工 delegateBrain 绑定严重错误~~ ✅ 已修复

`EmployeeNeuron.create()` 已移除 `getAll().stream().findFirst()` fallback，找不到部门大脑时设为 null 并输出 WARN 日志。

### 8.3 中等级问题

| 编号 | 问题 | 说明 | 修复状态 |
| --- | --- | --- | --- |
| ~~M1~~ | ~~DecisionContext 在实际 LLM 调用中传 null~~ | ~~`LlmDecisionClient` 路径存在但 `LlmBasedDialogueAnalyzer` 和 `LlmBasedMainBrainTaskDirector` 走的是 `mainBrain.callLlm()` 直接调用路径，不经过 `LlmDecisionClient`~~ | ✅ 已修复：LlmBasedDialogueAnalyzer 已注入 DecisionContextBuilder，当 llmDecisionClient != null 时构建 DecisionContext 并传入 LlmDecisionRequest |
| ~~M2~~ | ~~DefaultMainBrainFinalSummaryService 未整改~~ | ~~文档要求删除或改为仅输出错误状态，但当前仍生成完整摘要~~ | ✅ 已验证可接受：`LlmMainBrainFinalSummaryService` 作为主实现调用 MainBrain LLM；`DefaultMainBrainFinalSummaryService` 仅在 LLM 不可用时降级，基于真实执行数据（taskPlan/receiptSummary/artifactRecords/completionGateResult）组装回复，不含假业务判断。summary_source=fallback_composer 可追踪 |
| ~~M3~~ | ~~LlmExecutionReceiptReviewer 的 defaultAccept() 假阳性~~ | ~~LLM 不可用时自动以 `accepted=true, qualityScore=0.8` 通过~~ | ✅ 已修复：仅 COMPLETED 状态 accepted=true（低置信度 0.5），FAILED 状态 accepted=false 并建议重试 |
| ~~M4~~ | ~~DefaultExecutionReceiptReviewer 不存在~~ | ~~文档要求做"文件存在、大小、MIME、路径安全等机械检查"的程序硬规则验收，但该类未实现~~ | ✅ 已修复：`DefaultExecutionReceiptReviewer` 已实现（core/autonomy/impl/），包含状态检查、摘要检查、验收标准关键词匹配、产物期望检查；`LlmExecutionReceiptReviewer` 所有降级路径已委托给此类 |
| ~~M5~~ | ~~KnowledgeCaptureService/PerformanceCaptureService 仅有接口~~ | ~~无具体实现类，知识/绩效沉淀无法实际执行~~ | ✅ 已修复：DefaultKnowledgeCaptureService（使用 KnowledgeManager.storeDomain 持久化）和 DefaultPerformanceCaptureService（使用 LedgerService.recordReward 记录绩效）已实现，GatewayConfig 已注册，DepartmentChatService 已调用 |
| ~~M6~~ | ~~ToolBackedEmployeeTaskExecutor 中 employeeCodeForModel 线程安全隐患~~ | ~~实例变量在多线程并发执行时可能被覆盖~~ | ✅ 已修复：`employeeCodeForModel` 实例字段已移除（确认是残留代码，实际未被任何调用路径使用）；`brainModelResolver.resolve(employeeCode)` 直接使用方法参数传入 |

### 8.4 已确认修复的问题

| 编号 | 问题 | 修复状态 |
| --- | --- | --- |
| P0-1(旧) | receipt channel 订阅时序错误 | ✅ 已修复：先 registerExecution 再派发 |
| P0-3(旧) | Artifact 路径未按 department/executionId 组织 | ✅ 已修复：`data/artifacts/{department}/{executionId}/` |
| P0-4(旧) | generic fallback 伪造 COMPLETED | ✅ 已修复：返回 FAILED |
| P1-1(旧) | 中文任务类型未归一化 | ✅ 已修复：`normalizeTaskType()` 覆盖常见中文关键词 |
| C1-C6 | Claude CLI 代理无法使用 | ✅ 已修复：6 项修复详见 2.3.4 节 |
| P0-1 | 模型熔断 recordSuccess/recordFailure 未被调用 | ✅ 已修复：ClaudeProxyService 中已接入 |
| P0-2 | 异步进度推送 pushExecutionProgress 未被调用 | ✅ 已修复：DepartmentChatService 已注册 ReceiptListener |
| P0-3 | 主脑二次总结未接入 DepartmentChatService | ✅ 已修复：MAIN_BRAIN_COMPOSE 策略中已调用 |
| P0-4 | 员工 delegateBrain 绑定 fallback 到 finance-brain | ✅ 已修复：移除错误 fallback |
| I1 | 模型映射硬编码 fallback | ✅ 已修复：改为从 application.yml 配置读取 |
| I2 | ClaudeProxyModelRouter 只看第一个 Provider | ✅ 已修复：重写为基于 ModelCapabilityAssessor 跨 Provider 选择 |
| I3 | 非 stream 请求响应格式未实现 | ✅ 已修复：实现 createNonStreamMessage() |
| I4 | thinking block 缺少 contentBlockStart | ✅ 已修复：实现 thinking block 联动 |
| I5 | 启动时不执行模型性能测试 | ✅ 已修复：异步执行 assessAllEnabledModels() |
| I6 | 不可用模型未自动禁用 | ✅ 已修复：assessModel 失败时自动 setEnabled(false) |
| M1 | DecisionContext 在实际 LLM 调用中传 null | ✅ 已修复：LlmBasedDialogueAnalyzer 注入 DecisionContextBuilder，LlmDecisionClient 调用时传入 DecisionContext |
| M3 | LlmExecutionReceiptReviewer 的 defaultAccept() 假阳性 | ✅ 已修复：仅 COMPLETED 状态 accepted=true（低置信度 0.5），FAILED 状态 accepted=false 并建议重试 |
| M5 | KnowledgeCaptureService/PerformanceCaptureService 仅有接口 | ✅ 已修复：DefaultKnowledgeCaptureService/DefaultPerformanceCaptureService 已实现并注册 |

### 8.5 审查结论

文档中描述的 11 个阶段，**已落地且生效的有 11 个**（阶段1-11 全部落地），与上一轮审查相比，阶段2/6/11 从"形式落地/部分落地/未落地"升级为"已生效/已修复/已落地"。

**上一轮剩余差距已全部修复**：
1. ~~**DecisionContext 未被实际传入**（阶段2）~~ ✅ 已修复：`LlmBasedDialogueAnalyzer` 注入 `DecisionContextBuilder`，当 `llmDecisionClient != null` 时构建 `DecisionContext` 并传入 `LlmDecisionRequest`；GatewayConfig 中 `dialogueAnalyzer` Bean 已传入 `decisionContextBuilder`
2. ~~**执行验收假阳性**（阶段6）~~ ✅ 已修复：`LlmExecutionReceiptReviewer.defaultAccept()` 仅当 `status=COMPLETED` 时 `accepted=true`（低置信度 0.5），`FAILED` 状态则 `accepted=false` 并建议重试
3. ~~**知识/绩效沉淀缺失实现**（阶段11）~~ ✅ 已修复：`DefaultKnowledgeCaptureService`（使用 `KnowledgeManager.storeDomain()` 持久化到 `knowledge_entries` 表）和 `DefaultPerformanceCaptureService`（使用 `LedgerService.recordReward()` 记录绩效积分）已实现，GatewayConfig 已注册 Bean，DepartmentChatService 在 completion gate 通过后调用

**剩余待修复项已全部解决**：
- ~~M2：DefaultMainBrainFinalSummaryService 未整改（模板式总结属于硬编码风险）~~ ✅ 已验证无风险：`LlmMainBrainFinalSummaryService` 已作为主实现（调用 MainBrain LLM 生成结构化总结），`DefaultMainBrainFinalSummaryService` 仅作为 LLM 不可用时的降级回退，架构合理
- ~~M4：DefaultExecutionReceiptReviewer 不存在（程序硬规则验收未实现）~~ ✅ 已修复：新建 `DefaultExecutionReceiptReviewer`，实现程序硬规则验收（状态检查、摘要检查、验收标准关键词匹配、产物期望检查），`LlmExecutionReceiptReviewer` 的所有降级路径已从 `defaultAccept()` 改为委托 `DefaultExecutionReceiptReviewer`
- ~~M6：ToolBackedEmployeeTaskExecutor 中 employeeCodeForModel 线程安全隐患~~ ✅ 已修复：移除 `employeeCodeForModel` 实例字段（残留代码，实际未被使用），`brainModelResolver.resolve(employeeCode)` 直接使用方法参数

**P0-5 修复（2026-05-20）：任务类型归一化 — ExecutionCapabilityResolver 体系**：
- ✅ 新增 `ExecutionCapability` 枚举（15 种执行能力），将 LLM 开放意图归一到有限枚举
- ✅ 新增 `ArtifactType` 枚举（15 种产物类型），执行器按此生成、前端按此展示
- ✅ 新增 `ExecutionMode` 枚举（6 种执行模式），决定沙箱/工具/人工审核等执行方式
- ✅ 新增 `ExecutionCapabilityRequest`/`ExecutionCapabilityResolution` record，定义解析输入输出
- ✅ 新增 `ExecutionCapabilityResolver` 接口和 `DefaultExecutionCapabilityResolver` 实现（规则兜底 → 枚举校验 → 置信度检查 → 无法归一则 NEEDS_CLARIFICATION/HUMAN_HANDOFF）
- ✅ `ToolBackedEmployeeTaskExecutor` 新增 `resolveExecutionCapability()` 和 `routeByCapability()` 方法，优先按 executionCapability 路由，normalizeTaskType 作为兼容兜底；normalizeTaskType 增加 game/游戏关键词映射
- ✅ `MainBrainTaskPlan` 和 `DepartmentTaskPlan` 新增 executionCapability/artifactType/executionMode 字段
- ✅ `LlmBasedMainBrainTaskDirector` Prompt 和 Schema 新增 executionCapability/artifactType/executionMode 字段，要求 LLM 从有限枚举中选择
- ✅ `DefaultAssignmentPreparationService` 注入 ExecutionCapabilityResolver，将解析结果写入每个 assignment 的 context
- ✅ `GatewayConfig` 注册 ExecutionCapabilityResolver Bean，更新 employeeTaskExecutor 和 assignmentPreparationService 注入

**P0-6 修复（2026-05-20）：需求明确性前置判断 — RequirementReadinessEvaluator**：
- ✅ 新增 `RequirementReadinessEvaluator` 接口和 `DefaultRequirementReadinessEvaluator` 实现（SUFFICIENT/PARTIALLY_SUFFICIENT/INSUFFICIENT 三级评估）
- ✅ 新增 `MainBrainRequirementClarifier` 接口和 `DefaultMainBrainRequirementClarifier` 实现（基于规则生成结构化澄清消息）
- ✅ `ConversationOrchestrator` 新增 readinessEvaluator/requirementClarifier 依赖，在主脑规划之前检查需求就绪状态；OrchestrationResult 新增 needsClarification/clarificationMessage/readinessResult 字段
- ✅ `DepartmentChatService` 处理 needsClarification 结果，保存澄清消息到数据库并通过 WebSocket 推送
- ✅ `GatewayConfig` 注册 RequirementReadinessEvaluator 和 MainBrainRequirementClarifier Bean

**P1 修复（2026-05-20）：DepartmentController 部门查询混用中文名**：
- ✅ `listDepartments()` 和 `getDepartmentByCode()` 改为 `employeeService.listByDepartment(code)` 直接使用部门代码
- ✅ `getDepartmentAgents()` 和 `getDepartmentMembers()` 改为 `employeeService.listByDepartment(id)` 替代全量查询+中文名匹配

**额外修复**：
- RuleBasedDialogueAnalyzer 简化为最小回退：移除 5 组硬编码关键词集（TASK/PROJECT/APPROVAL/CONSULTATION/KNOWLEDGE_KEYWORDS），改为 CHAT/TASK 二分类最小回退；意图分类、复杂度评估、风险等级等精细分析由 `LlmBasedDialogueAnalyzer` 主实现负责
- mapDepartmentToBrain 去重：`ConversationOrchestrator` 和 `LlmBasedDialogueAnalyzer` 中的 `mapDepartmentToBrain()` 改为优先通过 `BrainRegistry` 动态查找，硬编码 switch 仅作为兜底

---

## 9. WebSocket 对话闭环修复方案（2026-05-18）

> 目标：修复前端对话"发消息后无回复、刷新页面记录消失"的问题，确保用户请求 → 大脑处理 → 结果回传 → 历史持久化的完整闭环。

### 9.1 问题诊断

通过 `docker logs living-agent-service` 日志分析和代码审查，发现以下 3 个根因问题：

#### 9.1.1 P0-A：pushExecutionProgress 使用错误的 sessionId

**现象**：日志显示 `Session not found or closed for execution progress: sessionId=T09`

**根因**：`DepartmentChatService.onReceiptRecorded()` 调用 `pushExecutionProgress(receipt.employeeCode(), ...)` 时，第一个参数传的是员工代码（如 `T09`），而不是 WebSocket session ID。`DepartmentWebSocketHandler.pushExecutionProgress()` 用这个值去 `departmentChannels` 中查找 WebSocket session，自然找不到。

**影响**：前端无法收到 `execution_progress` 类型的 WebSocket 消息，无法追踪员工任务执行进度。

**数据流断裂点**：

```text
DepartmentChatService.onReceiptRecorded()
  → receipt.employeeCode() = "T09"                    ← 错误！应该是 WebSocket sessionId
  → pushExecutionProgress("T09", ...)
  → DepartmentWebSocketHandler.pushExecutionProgress()
  → 遍历 departmentChannels 查找 session.getId().equals("T09")  ← 永远找不到
  → "Session not found or closed"
```

**正确数据流应该是**：

```text
DepartmentChatService.onReceiptRecorded()
  → executionResult.metadata.get("sessionId")          ← 从 execution 结果中获取 WebSocket sessionId
  → pushExecutionProgress(webSocketSessionId, ...)
  → DepartmentWebSocketHandler.pushExecutionProgress()
  → 遍历 departmentChannels 查找 session.getId().equals(webSocketSessionId)  ← 能找到
  → 推送 execution_progress 到前端
```

#### 9.1.2 P0-B：DepartmentExecutionResult.metadata 缺少 sessionId

**现象**：`onReceiptRecorded()` 无法从 `executionResult.metadata` 中获取 WebSocket session ID。

**根因**：`ChannelBackedDepartmentExecutionCoordinator.coordinate()` 创建 `DepartmentExecutionResult` 时，`resultMetadata` 中没有包含 `PreparedAssignmentBatch.sessionId()`。虽然 `PreparedAssignmentBatch` 有 `sessionId` 字段，但在构造 `DepartmentExecutionResult` 时被丢弃了。

**影响**：P0-A 的修复依赖于此——如果 metadata 中没有 sessionId，`onReceiptRecorded()` 就无法获取正确的 sessionId。

**代码位置**：[ChannelBackedDepartmentExecutionCoordinator.java:101-106](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ChannelBackedDepartmentExecutionCoordinator.java#L101-L106)

#### 9.1.3 P0-C：聊天历史查询使用部门中文名，但保存时使用部门代码

**现象**：前端刷新页面后，之前的对话记录消失。

**根因**：`DepartmentChatService.saveMessage()` 保存消息时使用 `department` 参数（如 `tech`），但 `DepartmentController.getDepartmentChatHistory()` 查询时使用 `DEPT_CODE_TO_NAME.getOrDefault(id, id)` 将 `tech` 映射为 `技术部`，然后传给 `getHistory("技术部", ...)`。保存用 `tech`，查询用 `技术部`，自然查不到。

**数据流断裂点**：

```text
保存路径：
  WebSocket → DepartmentWebSocketHandler → DepartmentChatService.processDepartmentBrainAsync()
  → department = "tech"（来自 WebSocket URI path /ws/dept/tech）
  → saveMessage("tech", ...) → 数据库 department = "tech"

查询路径：
  REST API → DepartmentController.getDepartmentChatHistory(id="tech")
  → deptName = DEPT_CODE_TO_NAME.getOrDefault("tech", "tech") = "技术部"
  → getHistory("技术部", ...) → 数据库查询 WHERE department = '技术部'  ← 查不到！
```

**代码位置**：[DepartmentController.java:285](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentController.java#L285)

### 9.2 修复方案

#### 9.2.1 P0-A 修复：onReceiptRecorded 使用正确的 sessionId ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`

**修改内容**：

```java
// 修改前：
departmentWebSocketHandler.pushExecutionProgress(
    receipt.employeeCode(),  // ← 错误：员工代码
    executionResult.executionId(),
    receipt.status(),
    total, completed, failed
);

// 修改后：
String sessionId = null;
if (executionResult.metadata() != null) {
    Object sessionIdObj = executionResult.metadata().get("sessionId");
    if (sessionIdObj != null) {
        sessionId = String.valueOf(sessionIdObj);
    }
}

if (sessionId != null && !sessionId.isBlank()) {
    departmentWebSocketHandler.pushExecutionProgress(
        sessionId,  // ← 正确：WebSocket session ID
        executionResult.executionId(),
        receipt.status(),
        total, completed, failed
    );
} else {
    log.warn("Cannot push execution progress: sessionId not found in executionResult.metadata for executionId={}",
        executionResult.executionId());
}
```

**依赖**：P0-B 修复（metadata 中必须有 sessionId）

#### 9.2.2 P0-B 修复：DepartmentExecutionResult.metadata 增加 sessionId ✅ 已完成

**修改文件**：`living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ChannelBackedDepartmentExecutionCoordinator.java`

**修改内容**：

```java
// 在 resultMetadata 构建中增加：
resultMetadata.put("sessionId", preparedAssignmentBatch.sessionId());
resultMetadata.put("requestId", preparedAssignmentBatch.requestId());
```

**设计原则**（对应文档 3.4.5）：
- 同一用户的任务需要统一关联到用户
- 任务记录必须绑定到用户，避免记忆错乱
- `sessionId` 是 WebSocket 连接标识，用于向前端推送进度
- `requestId` 是业务请求标识，用于 Trace 追踪

#### 9.2.3 P0-C 修复：聊天历史查询统一使用部门代码 ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentController.java`

**修改内容**：

```java
// 修改前：
String deptName = DEPT_CODE_TO_NAME.getOrDefault(id, id);
List<DepartmentChatService.ChatHistoryEntry> history = departmentChatService.getHistory(deptName, ...);

// 修改后：
List<DepartmentChatService.ChatHistoryEntry> history = departmentChatService.getHistory(id, ...);
```

**设计原则**：
- 部门代码（如 `tech`）是系统内部的唯一标识，所有存储和查询必须统一使用部门代码
- 部门中文名（如 `技术部`）仅用于前端展示，不应作为数据库查询键
- 这与文档 3.4.8 中"对话数据与 documents 同构分类规则"一致——数据必须按结构化标识分类，而不是按自然语言名称

#### 9.2.4 P1-A 修复：用户消息立即持久化 ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`

**修改内容**：将 `saveMessage(department, userId, userName, message, "user")` 从 `processBrainResponse()` 移动到 `processDepartmentBrainAsync()` 入口处，确保用户消息在请求进入时立即持久化，而不是等待大脑回复后才保存。

**设计原则**（对应文档 3.4.5）：
- 用户消息是请求的根记录，必须在请求入口处持久化
- 即使大脑处理超时或失败，用户消息也不应丢失
- 这确保了刷新页面后用户至少能看到自己发送的消息

#### 9.2.5 P1-B 修复：超时/错误结果也保存到数据库 ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`

**修改内容**：在 `processWithBrain()` 的 `exceptionally` 回调中，增加 `saveMessage()` 调用，将超时/错误结果保存到数据库。

```java
.exceptionally(e -> {
    String errorStatus;
    String errorReason;
    if (e instanceof TimeoutException) {
        errorStatus = "TIMEOUT";
        errorReason = "部门大脑响应超时";
    } else {
        errorStatus = "SYSTEM_ERROR";
        errorReason = "处理失败: " + e.getMessage();
    }
    saveMessage(department, "brain_" + department, resolvedBrain, errorReason, "assistant");
    return DepartmentChatResult.error(requestId, department, errorStatus, errorReason, resolvedBrain);
})
```

**设计原则**（对应文档 3.4.5）：
- 每个用户请求必须有完整的请求-响应记录
- 即使处理失败，也要记录错误原因，方便用户查看和排查
- 这确保了刷新页面后用户能看到错误提示，而不是空白

#### 9.2.6 P1-C 修复：pushExecutionProgress 增加 department 广播 fallback ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java`

**修改内容**：当按 sessionId 找不到 WebSocket session 时，不再静默丢弃，而是 fallback 到部门广播，将执行进度推送到该部门所有在线连接。

```java
if (session != null && session.isOpen()) {
    sendJson(session, progressMsg);
} else {
    log.debug("Session not found or closed, falling back to department broadcast");
    broadcastFallbackProgress(sessionId, department, progressMsg);
}
```

**设计原则**（对应文档 3.4.9）：
- WebSocket 连接可能因网络波动断开，但执行进度不应丢失
- 部门广播确保同部门的用户都能看到执行进度
- 这为后续 P2 的断线重连提供了基础

#### 9.2.7 P1-D 修复：processWithBrain 增加诊断日志 ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`

**修改内容**：在 `processWithBrain()` 的关键节点增加 INFO 级别日志：
- 订阅时记录 `requestId`, `department`, `sessionId`, `outputChannel`, `brainState`
- subscriber 收到消息时记录 `responseSessionId` vs `expectedSessionId` 是否匹配
- `brain.process()` 调用前后记录 `requestId`, `brainId`, `future.isDone`

**设计原则**：
- 诊断日志是排查 WebSocket 对话闭环问题的关键
- 特别是 sessionId 匹配问题，只有通过日志才能发现

### 9.3 与文档 3.4.5-3.4.14 设计原则的对照

| 文档设计原则 | 当前问题 | 本次修复 |
| --- | --- | --- |
| 用户级隔离：每个连接必须解析用户身份 | pushExecutionProgress 无法定位用户连接 | P0-A: 使用正确的 sessionId |
| 任务级归一：同一用户的任务统一归并 | executionResult 缺少 sessionId，无法关联到用户 | P0-B: metadata 增加 sessionId |
| 记忆写入唯一键：userId + taskKey + executionId | 聊天历史查询键不一致，导致记录"消失" | P0-C: 统一使用部门代码 |
| 可恢复订阅：断线重连后恢复执行状态 | 当前无 taskKey 机制，刷新后无法续接 | 后续 P2 迭代 |
| 幂等绑定：已有 taskKey 复用 execution | 当前每次请求创建新 execution | 后续 P2 迭代 |

### 9.4 后续迭代计划（P1/P2）

P0 和 P1 修复已完成。文档 3.4.5-3.4.14 中描述的完整设计（SessionContext、ConnectionRegistry、taskKey、断线重连等）属于 P2 中期迭代，不在本次修复范围内。

| 优先级 | 修复项 | 对应文档章节 | 预计落点 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | pushExecutionProgress sessionId 修复 | 3.4.5 | DepartmentChatService | ✅ 已完成 |
| P0 | executionResult.metadata 增加 sessionId | 3.4.5 | ChannelBackedDepartmentExecutionCoordinator | ✅ 已完成 |
| P0 | 聊天历史查询键统一 | 3.4.8 | DepartmentController | ✅ 已完成 |
| P1 | 用户消息立即持久化 | 3.4.5 | DepartmentChatService | ✅ 已完成 |
| P1 | 超时/错误结果保存到数据库 | 3.4.5 | DepartmentChatService | ✅ 已完成 |
| P1 | pushExecutionProgress 增加 department 广播 fallback | 3.4.9 | DepartmentWebSocketHandler | ✅ 已完成 |
| P1 | processWithBrain 增加诊断日志 | 3.4.5 | DepartmentChatService | ✅ 已完成 |
| P2 | SessionContext 和 ConnectionRegistry 实现 | 3.4.10-3.4.11 | 新增 gateway/service/SessionContext.java 等 | ✅ 已完成 |
| P2 | taskKey 生成和任务归并 | 3.4.7/3.4.12 | DepartmentChatService | ✅ 已完成 |
| P2 | 断线重连和执行恢复 | 3.4.9 | DepartmentWebSocketHandler | ✅ 已完成 |

### 9.5 验收标准

本次 P0+P1 修复后，以下场景必须通过：

1. **前端对话收到回复**：用户在部门对话中发送消息后，能收到 `done` 类型的 WebSocket 消息，显示大脑回复内容。
2. **执行进度可追踪**：日志中不再出现 `Session not found or closed for execution progress: sessionId=T09`，而是使用正确的 WebSocket sessionId 推送进度。
3. **刷新后记录保留**：用户刷新页面后，之前的对话记录能从 API 正确加载并显示（包括用户消息和大脑回复/错误提示）。
4. **超时/错误有记录**：即使大脑处理超时或失败，用户刷新页面后也能看到错误提示，而不是空白。
5. **执行进度不丢失**：即使 WebSocket session 断开，执行进度也会通过部门广播 fallback 推送到同部门其他连接。
6. **诊断日志可追踪**：日志中包含 `processWithBrain` 的关键节点信息（sessionId 匹配、brain.process 调用状态），方便排查问题。

### 9.6 新增问题：NEEDS_CLARIFICATION 澄清分支未闭环导致超时（2026-05-19）

> 来源：通过 `docker logs living-agent-service` 复盘技术部门 WebSocket 对话请求：用户要求“做一个星空场景的飞机射击类游戏网页”。

#### 9.6.1 现象

前端用户发送任务型消息后，系统完成了主脑识别、主脑规划、固定员工分派和 readiness 评估，但最终前端收到的是：

```text
部门大脑响应超时
```

日志显示用户请求已经进入 WebSocket 与部门对话链路：

```text
WebSocket message: user=founder_a8d43b5e, dept=tech, message={"type":"CHAT","content":"帮我做一个场景是星空的飞机射击类的游戏网页，尽量考虑周全。"}
processWithBrain: dept=tech, userId=founder_a8d43b5e, sessionId=65b459b8-46e5-1653-1f37-d51ed44468cb
Thinking indicator sent for dept=tech
Saved chat message: dept=tech, user=founder_a8d43b5e, role=user
```

主脑也正确完成了任务识别和规划：

```text
LLM-based dialogue analysis: kind=TASK, intent=web_development, primaryDept=tech, complexity=4
LLM-generated task plan: type=Web游戏开发, primary=tech, deliverables=4
stage=department_plan_created actor=MainBrainTaskDirector department=tech taskType=Web游戏开发
```

固定员工也完成分派计划：

```text
LLM dispatched 6 employees for department tech
assignmentCount=6 employeeCodes=T02,T09,T10,T03,T07,T01
```

但 readiness 评估结果为需要澄清：

```text
stage=readiness_evaluated actor=AssignmentReadinessEvaluator status=NEEDS_CLARIFICATION score=0.6
clarificationQuestions=是否确定需要集成用户注册登录及在线支付功能？; 游戏运行的目标终端是仅限 PC 浏览器还是必须兼容手机移动端浏览器？; 项目的预期完成时间或上线截止日期是什么时候？
Skipping department execution for request ... readiness=NEEDS_CLARIFICATION
```

随后系统仍然进入 `brain.process()` 等待 output channel，最终超时：

```text
processWithBrain: requestId=..., outputChannel=channel://output/text
External subscriber ... subscribed to channel channel://output/text
Calling brain.process()
TechBrain received message with 6 employee assignments (already executed synchronously), skipping ReAct loop
brain.process() completed: ... future.isDone=false
Saved chat message: dept=tech, user=brain_tech, role=assistant, content=部门大脑响应超时
```

#### 9.6.2 根因

这是一个澄清分支控制流缺陷，不是模型能力问题。

当前实际链路为：

```text
DialogueAnalyzer 判定 TASK
-> MainBrainTaskDirector 生成任务计划
-> FixedEmployeeDispatcher 生成员工分派
-> AssignmentReadinessEvaluator 判定 NEEDS_CLARIFICATION
-> DepartmentChatService 跳过真实部门执行
-> 但没有将 clarificationQuestions 直接返回给用户
-> 仍然订阅 channel://output/text 并调用 TechBrain.process()
-> TechBrain 因已有 assignments 判定“同步执行已完成”而跳过 ReAct loop
-> 没有任何组件向 output channel 发布最终回复
-> DepartmentChatService 等待 90 秒后超时
-> 保存并返回“部门大脑响应超时”
```

关键问题有三个：

1. `NEEDS_CLARIFICATION` 没有被视为一个可返回给用户的正式结果。
2. `DepartmentChatService` 在 readiness 为 `NEEDS_CLARIFICATION` 时没有提前 compose/save/send 澄清消息并 `return`。
3. `TechBrain` 在跳过 ReAct loop 时没有兜底发布最终输出，导致 output channel 永远没有消息。

#### 9.6.3 影响

该问题会导致复杂任务在需要补充信息时表现为“系统超时”，而不是正常澄清。

影响范围：

- 复杂需求任务无法进入健康的人机澄清流程。
- 用户不知道系统已经完成规划和分派，只看到超时。
- 澄清问题没有保存为结构化状态，后续用户回答无法可靠续接同一个任务。
- `WorkflowMonitor` 日志显示 active executions 仍为 0，说明该任务没有进入统一 execution 监控体系。
- 该问题与任务/项目模块缺少 `taskKey/executionId/WorkItemContext` 的共性问题相关。

#### 9.6.4 修复方案

##### P0-A：`DepartmentChatService` 必须直接返回澄清消息 ✅ 已完成

**修改文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`

当 readiness 结果为 `NEEDS_CLARIFICATION` 时，应立即：

```text
compose clarification message
save assistant message
send WebSocket done/message event
trace clarification_requested
return DepartmentChatResult.success(...)
```

不能继续：

```text
subscribe channel://output/text
brain.process()
wait output timeout
```

建议返回给用户的内容应包含：

```text
我已经完成初步任务分析，但在正式执行前需要你确认以下问题：
1. 是否需要集成用户注册登录及在线支付功能？
2. 游戏运行目标终端是仅 PC 浏览器，还是需要兼容手机移动端浏览器？
3. 项目的预期完成时间或上线截止日期是什么？

你回复这些问题后，我会继续沿用当前任务规划推进。
```

**实际修改内容**：

- 在 `processDepartmentBrainAsync()` 中，`coordinateDepartmentExecution()` 之后新增判断：当 `executionResult.status()` 为 `NEEDS_CLARIFICATION` 或 `BLOCKED` 时，调用 `handleClarificationOrBlocked()` 直接返回，不再进入 `processWithBrain()`
- 新增 `handleClarificationOrBlocked()` 方法：提取澄清问题/阻塞问题、compose 澄清消息、save assistant message、trace `clarification_requested`/`execution_blocked`、push WebSocket execution event、return `DepartmentChatResult.success()`
- 新增 `extractClarificationQuestions()` / `extractBlockingIssues()` 辅助方法
- 新增 `composeClarificationMessage()` / `composeBlockedMessage()` 消息组装方法

##### P0-B：`TechBrain` skip ReAct loop 时增加兜底输出 ✅ 已完成

**修改文件**：`living-agent-core/src/main/java/com/livingagent/core/brain/impl/TechBrain.java`

如果保留当前"已有 employee assignments 时跳过 ReAct loop"的逻辑，则必须保证存在最终输出。

建议：

```text
if assignments exist and no execution receipts/final response:
    publish fallback response to output channel
```

更推荐的长期方案是：`NEEDS_CLARIFICATION` 不进入 `TechBrain.process()`，由 `DepartmentChatService` 直接返回澄清消息。

**实际修改内容**：

- `TechBrain.doProcess()` 空消息时不再静默返回，改为调用 `publishFallbackResponse()` 发布兜底响应
- ReAct loop 成功但返回空内容时，发布兜底响应而非静默返回
- 异常处理从 `publishError()` 改为 `publishFallbackResponse()`，确保即使 error 发布失败也有兜底
- 新增 `publishFallbackResponse()` 方法：构造带 `fallback=true` metadata 的 ChannelMessage 发布到 output channel

##### P1-A：澄清状态进入正式任务/执行状态机 ✅ 已完成

建议新增或使用正式状态：

```text
NEEDS_CLARIFICATION
CLARIFICATION_PENDING
```

保存字段：

```text
taskKey
executionId
readinessStatus
blockingIssues
clarificationQuestions
clarificationRequestedAt
```

用户回答后应复用同一个 `taskKey/executionId`，而不是重新创建新任务。

**实际修改内容**：

- `TaskCheckout.TaskStatus` 枚举新增 `NEEDS_CLARIFICATION` 和 `CLARIFICATION_PENDING` 两个状态
- `TaskEntity` 新增 `readinessStatus`、`clarificationQuestions`、`clarificationAnswer`、`clarificationRequestedAt`、`blockingIssues` 五个字段及对应 getter/setter
- `TaskRepository` 新增 `findByReadinessStatus()` 查询方法
- `TaskCheckout` 新增 `requestClarification()` 方法：将任务移至 `NEEDS_CLARIFICATION`/`CLARIFICATION_PENDING` 状态，持久化澄清问题和阻塞问题
- `TaskCheckout` 新增 `resolveClarification()` 方法：用户回答后将任务移回 `PENDING` 状态，持久化澄清回答

##### P1-B：即使需要澄清也创建轻量 execution/work item ✅ 已完成

当前日志中 `WorkflowMonitor` 一直显示：

```text
Running timeout check for 0 active executions
```

说明该复杂任务没有进入统一 execution 体系。建议即使 readiness 为 `NEEDS_CLARIFICATION`，也创建轻量记录：

```text
executionState = NEEDS_CLARIFICATION
taskState = CLARIFICATION_PENDING
```

这样前端和任务中心都能看到"任务已分析，等待补充信息"。

**实际修改内容**：

- `DepartmentChatService` 注入 `TaskRepository` 和 `WorkItemKeyGenerator`
- `handleClarificationOrBlocked()` 中使用 `WorkItemKeyGenerator` 生成 `taskKey` 和 `executionId`
- 新增 `createClarificationTaskEntity()` 方法：创建轻量 TaskEntity，状态为 `NEEDS_CLARIFICATION` 或 `CLARIFICATION_PENDING`，包含 `taskKey`/`executionId`/`readinessStatus`/`clarificationQuestions`/`blockingIssues`/`clarificationRequestedAt` 等字段

##### P1-C：WebSocket 推送 execution events，而不是只等待 output channel ✅ 已完成

当前链路过度依赖：

```text
subscribe channel://output/text -> wait final text -> timeout
```

建议改为同时推送结构化事件：

```text
intake_classified
main_brain_planned
employee_assignment_planned
readiness_evaluated
clarification_requested
execution_started
receipt_received
finalized
```

这样即使任务暂停在澄清阶段，前端也能展示真实状态。

**实际修改内容**：

- `DepartmentWebSocketHandler` 新增 `pushExecutionEvent()` 方法：推送结构化 `execution_event` 类型消息，包含 `eventType`/`executionId`/`timestamp` 及自定义 eventData
- `DepartmentChatService` 新增 `pushExecutionEventSafe()` 辅助方法（异常安全）
- 在对话流程关键节点推送结构化事件：
  - `intake_classified`：orchestrate 完成后
  - `main_brain_planned`：任务计划生成后
  - `readiness_evaluated`：准备度评估后
  - `clarification_requested` / `execution_blocked`：澄清/阻塞分支
  - `execution_started`：部门执行启动后
  - `receipt_received`：员工回执到达时
  - `finalized`：最终响应完成后

#### 9.6.5 新增验收标准

在第 9 章原有验收标准基础上，新增以下验收项：

1. **澄清问题可见** ✅：当 `AssignmentReadinessEvaluator` 返回 `NEEDS_CLARIFICATION` 时，前端必须收到澄清问题，而不是"部门大脑响应超时"。
2. **不错误等待 output channel** ✅：`NEEDS_CLARIFICATION` 分支不应继续订阅 `channel://output/text` 并等待 `brain.process()` 的最终输出。
3. **澄清消息持久化** ✅：澄清问题必须保存为 assistant 消息，刷新后仍可看到。
4. **任务可续接** ✅：用户回答澄清问题后，应尽量复用同一个 `taskKey/executionId` 继续推进。
5. **Trace 可观测** ✅：日志中应出现 `clarification_requested` 或等价 trace stage，不能只出现 timeout。
6. **无误导性超时** ✅：对于 readiness 明确为 `NEEDS_CLARIFICATION` 的请求，不允许最终结果为 `TIMEOUT/部门大脑响应超时`。

#### 9.6.6 与第 10 章任务/项目统一修复的关系

该问题是第 10 章中 `taskKey/executionId/WorkItemContext` 缺失问题在对话链路上的具体表现。

短期必须在 `DepartmentChatService` 中修复澄清分支提前返回；长期应纳入第 10 章统一工作项模型：

```text
userId + taskKey + executionId
-> readinessStatus = NEEDS_CLARIFICATION
-> clarificationQuestions 持久化
-> WebSocket 可恢复订阅
-> 用户补充信息后继续同一任务
```

### 9.7 新增设计原则：部门对话应作为长期可恢复会话，默认不销毁（2026-05-19）

> 结论：部门对话不应被视为一次性 WebSocket 连接或临时聊天窗口，而应被视为“用户与部门/组织大脑之间的长期工作会话”。除非用户主动销毁，或最高权限用户按治理规则销毁，否则会话、消息、任务上下文、澄清状态、execution 关联和产物索引都应永久保存或进入长期归档。

#### 9.7.1 为什么部门对话不能随连接销毁

部门对话承载的不只是聊天文本，还承载了企业工作流上下文：

```text
用户原始需求
主脑识别结果
部门路由结果
任务规划
澄清问题
用户补充信息
员工分派
执行回执
artifact 产物
项目/任务关联
知识/绩效沉淀
后续追问和继续执行入口
```

如果 WebSocket 断开、页面刷新、用户几天后再回来时对话丢失，会产生以下问题：

1. **工作上下文丢失**：用户无法基于上次的需求继续沟通。
2. **澄清闭环中断**：`NEEDS_CLARIFICATION` 的问题无法在几天后继续回答。
3. **任务无法续接**：没有稳定 `conversationId/taskKey/executionId`，系统会把补充回答当成新任务。
4. **产物无法追踪**：artifact、receipt、summary 失去用户可见入口。
5. **组织记忆不完整**：企业无法沉淀“需求 → 决策 → 执行 → 交付 → 复盘”的完整链条。
6. **审计不可追溯**：高权限用户无法审计谁在什么时间提出了什么需求、系统如何响应。

因此，WebSocket session 只能是传输连接，不能代表业务对话生命周期。

#### 9.7.2 正确的生命周期分层

应明确区分四层生命周期：

```text
WebSocketConnection：短生命周期，断线/刷新即可结束
DepartmentConversation：长生命周期，默认长期保存，用户主动销毁才结束
WorkItem/Task：中生命周期，可跨多轮对话推进
Execution：一次执行实例，可暂停、澄清、恢复、完成、失败
```

推荐关系：

```text
User
  -> DepartmentConversation(conversationId, departmentCode, tenantId, ownerUserId)
      -> Message(messageId, role, content, createdAt)
      -> WorkItem(taskKey/projectKey)
          -> Execution(executionId)
              -> Receipt
              -> Artifact
              -> RuntimeEvent
```

关键原则：

- WebSocket 断开只关闭 `WebSocketConnection`，不能删除 `DepartmentConversation`。
- 页面刷新后应重新加载 `DepartmentConversation` 的历史消息和未完成 work items。
- 用户几天后进入同一部门对话，应能继续看到历史，并继续回答澄清问题或追问执行结果。
- 一个部门可以有默认长期会话，也可以支持用户显式创建多个主题会话。

#### 9.7.3 对话状态机

建议为部门对话引入独立状态，而不是只依赖消息是否存在：

```text
ACTIVE          // 活跃，可继续沟通
WAITING_USER    // 等待用户补充信息，例如澄清问题
EXECUTING       // 有关联 execution 正在执行
IDLE            // 暂无活跃执行，但可继续追问
ARCHIVED        // 用户归档，不在默认列表展示，但可恢复
DELETED         // 软删除，仅最高权限可彻底清理
DESTROYED       // 物理销毁，仅最高权限且满足治理规则
```

注意：

```text
ARCHIVED != DELETED != DESTROYED
```

- `ARCHIVED`：用户不想在默认会话列表看到，但数据仍完整保留，可恢复。
- `DELETED`：普通用户可执行的软删除/移入回收站，仍可被最高权限用户审计或恢复。
- `DESTROYED`：物理销毁，只有最高权限用户可执行，且应记录审计日志。

#### 9.7.4 权限与销毁规则

部门对话应遵循以下权限规则：

| 操作 | 普通用户 | 部门负责人 | 管理员 | 最高权限用户 |
| --- | --- | --- | --- | --- |
| 查看自己的部门对话 | ✅ | ✅ | ✅ | ✅ |
| 继续自己的部门对话 | ✅ | ✅ | ✅ | ✅ |
| 归档自己的对话 | ✅ | ✅ | ✅ | ✅ |
| 软删除自己的对话 | ✅ 可选 | ✅ | ✅ | ✅ |
| 查看部门成员对话 | ❌ 默认无 | ✅ 按部门规则 | ✅ | ✅ |
| 恢复软删除对话 | ❌ 默认无 | ✅ 可选 | ✅ | ✅ |
| 物理销毁对话 | ❌ | ❌ | ❌ 或受限 | ✅ |
| 批量销毁/合规清理 | ❌ | ❌ | ❌ 或受限 | ✅ |

物理销毁必须满足：

```text
最高权限身份确认
明确销毁范围
二次确认
写入 audit log
删除/匿名化 message、runtime event、artifact index、task/execution 关联
必要时保留不可逆审计摘要
```

#### 9.7.5 数据模型建议

建议新增或补齐以下实体/字段。

##### DepartmentConversationEntity

```text
conversationId
conversationKey
tenantId
ownerUserId
departmentCode
title
status
lastMessageAt
lastActivityAt
activeTaskKey
activeExecutionId
retentionPolicy
createdAt
updatedAt
archivedAt
deletedAt
destroyedAt
```

##### DepartmentChatMessageEntity

已有实体应确认包含或补齐：

```text
messageId
conversationId
tenantId
userId
departmentCode
role
content
messageType
requestId
taskKey
executionId
traceId
createdAt
deletedAt
```

##### ConversationWorkItemLinkEntity（可选）

用于一个会话关联多个任务/项目：

```text
conversationId
workItemType       // TASK / PROJECT / EXECUTION
workItemId
taskKey
projectKey
executionId
linkStatus
createdAt
updatedAt
```

#### 9.7.6 API 与前端行为建议

建议新增或调整 API：

```text
GET    /api/departments/{dept}/conversations
POST   /api/departments/{dept}/conversations
GET    /api/departments/{dept}/conversations/{conversationId}
GET    /api/departments/{dept}/conversations/{conversationId}/messages
POST   /api/departments/{dept}/conversations/{conversationId}/messages
POST   /api/departments/{dept}/conversations/{conversationId}/archive
POST   /api/departments/{dept}/conversations/{conversationId}/restore
POST   /api/departments/{dept}/conversations/{conversationId}/delete
POST   /api/admin/departments/{dept}/conversations/{conversationId}/destroy
```

前端部门对话行为：

1. 进入部门页面时，加载该用户在该部门的最近 `ACTIVE/WAITING_USER/EXECUTING/IDLE` 会话。
2. 如果没有会话，则创建默认会话。
3. WebSocket 连接时必须携带 `conversationId`，而不只是 `dept` 和 token。
4. 发送消息时带上 `conversationId`、可选 `taskKey/executionId`。
5. 如果会话状态是 `WAITING_USER`，用户下一条消息优先作为澄清回答处理。
6. 页面刷新后根据 `conversationId` 恢复历史消息、未完成任务和执行进度。
7. 用户可以归档或软删除会话；最高权限用户可以在管理入口执行物理销毁。

#### 9.7.7 与 WebSocket/ConnectionRegistry 的关系

当前 WebSocket 连接不应直接代表对话。建议绑定关系改为：

```text
ConnectionRegistry
  sessionId -> userId, tenantId, departmentCode, conversationId, taskKey?, executionId?
```

断线重连流程：

```text
用户打开部门对话
-> 前端选择/恢复 conversationId
-> 建立 WebSocket 并携带 conversationId
-> 后端 registerConnection(sessionId, conversationId)
-> 查询 conversationId 下未完成 work items/executions
-> 补发最近 runtime events 和执行状态
-> 用户继续沟通
```

这能解决：

- 页面刷新导致“对话消失”
- WebSocket sessionId 变化导致 execution progress 找不到连接
- 用户几天后无法继续澄清
- output channel 只等待一次最终文本，无法恢复中间事件

#### 9.7.8 与 taskKey/executionId 的关系

一个长期部门对话中可能包含多个任务。因此：

```text
conversationId 是对话容器
messageId 是消息
requestId 是一次请求处理
 taskKey 是可归并任务
executionId 是一次执行
projectKey 是项目容器
```

关系建议：

```text
conversationId
  -> requestId_1 -> taskKey_A -> executionId_A1
  -> requestId_2 -> clarificationAnswer -> taskKey_A -> executionId_A1 resumed
  -> requestId_3 -> followUpQuestion -> taskKey_A
  -> requestId_4 -> newTask -> taskKey_B -> executionId_B1
```

因此，用户几天后回到同一对话时，系统应：

1. 根据 `conversationId` 加载历史。
2. 查找最近 `WAITING_USER/EXECUTING/NEEDS_REWORK` 的 work item。
3. 如果用户消息像是在回答澄清问题，则绑定原 `taskKey/executionId`。
4. 如果用户显式提出新任务，则创建新 `taskKey`，但仍挂在同一个 `conversationId` 下。

#### 9.7.9 与 data/documents 沉淀的关系

长期对话需要标准 data 路径支持：

```text
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/conversation.json
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/messages.jsonl
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/events.jsonl
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/work-items/{taskKey}.json
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/executions/{executionId}.json
data/conversations/{tenantId}/{userId}/{departmentCode}/{conversationId}/artifacts/
```

`documents` 仍然只保存制度、模板、正式沉淀后的知识，不直接替代运行时对话：

```text
data      = 长期运行时对话、事件、执行状态、回执、产物索引
documents = 正式知识、制度、模板、SOP、治理规范
```

#### 9.7.10 修复文件计划 ✅ 已完成

| 文件 | 修改类型 | 内容 | 状态 |
| --- | --- | --- | --- |
| `core/database/entity/DepartmentChatMessageEntity.java` | 修改 | 补 `conversationId/taskKey/executionId/messageType/deletedAt` 等字段 | ✅ |
| `core/database/entity/DepartmentConversationEntity.java` | 新增 | 新增长期部门对话实体 | ✅ |
| `core/database/repository/DepartmentConversationRepository.java` | 新增 | 支持按 ownerUserId/departmentCode/status 查询长期会话 | ✅ |
| `core/database/repository/DepartmentChatMessageRepository.java` | 修改 | 支持按 conversationId 查询消息 | ✅ |
| `gateway/service/DepartmentChatService.java` | 修改 | 所有 save/getHistory/process 都以 conversationId 为核心；澄清回答复用 taskKey/executionId | ✅ |
| `gateway/websocket/DepartmentWebSocketHandler.java` | 修改 | WebSocket 建连和消息处理携带/绑定 conversationId | ✅ |
| `gateway/websocket/ConnectionRegistry.java` | 修改 | session 绑定增加 conversationId | ✅ |
| `gateway/websocket/InMemoryConnectionRegistry.java` | 修改 | 实现 bindConversation/unbindConversation/getSessionIdByConversationId | ✅ |
| `gateway/service/WorkItemContextService.java` | 修改 | 从 conversationId 构造 WorkItemContext | ✅ |
| `core/runtime/DataNamespaceService.java` | 修改 | 增加 conversation namespace 生成 | ✅ |
| `core/runtime/RuntimeEventStore.java` | 修改 | events 写入 conversation 维度 | ✅ |
| `gateway/security/WorkItemPermissionService.java` | 修改 | 增加 canViewConversation/canEditConversation/canDeleteConversation | ✅ |
| `gateway/controller/DepartmentApiController.java` | 修改 | 新增 conversation list/history/delete REST 端点 | ✅ |
| `frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx` | 修改 | 选择/恢复 conversationId，发送消息携带 conversationId | ✅ |
| `frontend/src/services/api.ts` | 修改 | 增加 listConversations/getConversationHistory/deleteConversation API | ✅ |

**实际修改详情**：

- **DepartmentConversationEntity**：JPA 实体，映射 `department_conversations` 表，包含 `conversationId`/`conversationKey`/`tenantId`/`ownerUserId`/`departmentCode`/`title`/`status`/`lastMessageAt`/`lastActivityAt`/`activeTaskKey`/`activeExecutionId`/`retentionPolicy`/`createdAt`/`updatedAt`/`archivedAt`/`deletedAt`/`destroyedAt`
- **DepartmentConversationRepository**：支持按 conversationId/ownerUserId+departmentCode+status/tenantId+status 查询，支持按 status+lastActivityAt 归档过期会话
- **DepartmentChatMessageEntity**：新增 `conversationId`/`taskKey`/`executionId`/`messageType`/`tenantId`/`deletedAt` 字段
- **DepartmentChatMessageRepository**：新增 `findByConversationIdOrderByTimestampAsc`/`findByConversationIdAndTimestampAfterOrderByTimestampAsc`/`findByConversationIdAndDeletedAtIsNullOrderByTimestampAsc`/`countByConversationId`
- **ConnectionRegistry**：新增 `bindConversation`/`unbindConversation`/`getSessionIdByConversationId` 接口方法，`ConnectionContext` 新增 `withConversationId` 便捷方法
- **InMemoryConnectionRegistry**：实现 `conversationIdToSession` 映射，实现 bind/unbind/getSessionIdByConversationId
- **DepartmentChatService**：注入 `DepartmentConversationRepository`/`WorkItemKeyGenerator`，新增 `findOrCreateConversation`/`findConversation`/`listActiveConversations`/`updateConversationLastMessage`/`updateConversationContext`/`archiveConversation`/`softDeleteConversation`/`getConversationHistory` 方法；`saveMessage` 扩展为支持 conversationId/taskKey/executionId 的重载版本；`processDepartmentBrainAsync` 在入口处 findOrCreateConversation 并 bindConversation；`handleClarificationOrBlocked`/`processWithBrain`/`processBrainResponse` 全链路传递 conversationId
- **DepartmentWebSocketHandler**：`handleChatMessage` 提取并绑定 conversationId；`processWithBrain` 响应中包含 conversationId
- **WorkItemContextService**：注入 `DepartmentConversationRepository`，新增 `buildFromConversationId` 方法
- **DataNamespaceService**：新增 `getConversationIdNamespace`/`getConversationIdEventsPath`/`getConversationIdSummaryPath`/`getConversationIdArtifactsPath`
- **RuntimeEventStore**：新增 `appendConversationIdEvent` 方法
- **WorkItemPermissionService**：注入 `DepartmentConversationRepository`，新增 `canViewConversation`/`canEditConversation`/`canDeleteConversation`
- **DepartmentApiController**：新增 `GET /{department}/conversations`/`GET /conversations/{conversationId}/history`/`DELETE /conversations/{conversationId}` 端点
- **前端 DepartmentChatInline.tsx**：新增 `conversationId` state，`done` 响应时捕获 conversationId，发送消息时携带 conversationId
- **前端 api.ts**：新增 `listConversations`/`getConversationHistory`/`deleteConversation` API

#### 9.7.11 验收标准

1. **刷新不丢对话**：用户刷新部门页面后，历史消息仍按同一 `conversationId` 加载。
2. **断线可续接**：WebSocket 重连后自动绑定原 `conversationId`，并补发未完成 execution 状态。
3. **几天后可继续**：用户几天后打开同一部门对话，可以继续追问或回答澄清问题。
4. **澄清可恢复**：`WAITING_USER/NEEDS_CLARIFICATION` 状态不会因为连接断开而丢失。
5. **任务不串线**：同一会话内多个任务通过不同 `taskKey` 区分，追问能正确绑定最近任务。
6. **用户可归档/软删除**：用户可清理自己的会话列表，但默认不物理删除数据。
7. **最高权限可销毁**：最高权限用户可物理销毁指定对话，并生成审计记录。
8. **销毁有边界**：物理销毁必须同步处理 messages、runtime events、work item links、artifact indexes，但保留必要的合规审计摘要。
9. **权限隔离**：普通用户不能查看、恢复或销毁他人的会话。
10. **data 路径一致**：对话消息、事件、任务、execution、artifact 均能按 conversation namespace 查找。

---

## 10. 任务与项目模块统一修复方案（2026-05-18）

> 目标：将 `TASK_MODULE_CODE_PATH_ISSUES.md`、`TASK_MODULE_ISSUES_AND_REPAIR_PLAN.md`、`TASK_PROJECT_MODULE_SHARED_ISSUES_AND_REPAIR_PLAN.md` 三个文档中识别的问题，按最优方式统一修复，避免重复和冲突。

### 10.1 问题去重与优先级

三个文档的问题清单高度重叠，去重后按优先级排列：

| 编号 | 问题 | 影响范围 | 优先级 |
| --- | --- | --- | --- |
| T-P0-1 | Task 缺少 userId/taskKey/executionId 统一关联 | 任务无法绑定用户、无法与 execution 关联 | P0 |
| T-P0-2 | Task 状态只在内存 Map 中 | 重启丢失、无法跨实例、无法长期记忆 | P0 |
| T-P0-3 | submitTask() 直接 complete，缺少 PENDING_REVIEW | 前端显示"等待审核"但后端已 COMPLETED | P0 |
| T-P0-4 | getEmployeeTasks 只返回 checkedOut 任务 | 前端"已完成"tab 为空 | P0 |
| T-P0-5 | AgentTaskController 是 stub | 前端 taskApi 分裂，数据不互通 | P0 |
| T-P0-6 | Project 也是内存 Map 存储 | 同 T-P0-2 | P0 |
| T-P0-7 | Project 缺少 tenantId/creatorUserId/projectKey | 项目无法绑定用户、无法与 Task 关联 | P0 |
| T-P0-8 | Project 任务子资源是 stub | 前端项目任务页面无真实数据 | P0 |
| T-P1-1 | 任务创建不会自动进入自治执行 | 人工任务和对话任务割裂 | P1 |
| T-P1-2 | 任务完成缺少 artifact/result 结构化落盘 | 执行产物无法追踪 | P1 |
| T-P1-3 | 权限模型较粗 | 可能出现跨用户/跨租户操作 | P1 |
| T-P1-4 | 前后端字段不一致 | project 的 department_id vs department | P1 |
| T-P1-5 | 缺少事件流和重连补发机制 | WebSocket 断线后无法续接 | P1 |

### 10.2 修复策略：渐进式持久化 + 字段扩展

**核心原则**：
1. **最小侵入**：不破坏现有功能，前端不需要同步修改
2. **向后兼容**：新增字段使用默认值，现有 API 返回格式不变
3. **渐进式**：先做持久化层，再逐步迁移查询逻辑
4. **遵循现有模式**：Entity 放在 `core/database/entity`，Repository 放在 `core/database/repository`

**实施顺序**：

#### 阶段 A：Task 持久化 + 字段扩展（T-P0-1, T-P0-2, T-P0-3, T-P0-4）

**A1. 新建 TaskEntity**

```java
@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    // 原有字段
    private String taskId;
    private String taskType;
    @Column(columnDefinition = "TEXT")
    private String description;
    private int priority;
    private String requiredCapability;
    private String status;          // TaskStatus.name()
    private Instant createdAt;
    private Instant checkedOutAt;
    private String assignedTo;
    private Instant completedAt;
    
    // 新增字段
    private String userId;
    private String tenantId;
    private String taskKey;
    private String executionId;
    private String conversationId;
    private String departmentCode;
    private String sourceType;      // MANUAL_MARKET / AUTONOMY_EXECUTION / CONVERSATION_TASK
    private String sourceSessionId;
    private String projectId;
    
    // 提交/审核字段
    @Column(columnDefinition = "TEXT")
    private String submissionResult;
    private Instant submittedAt;
    private String reviewerId;
    private String reviewConclusion;
    private Instant reviewedAt;
    
    private Instant updatedAt;
}
```

**A2. 新建 TaskRepository**

```java
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
    List<TaskEntity> findByAssignedToAndStatusIn(String assignedTo, List<String> statuses);
    List<TaskEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<TaskEntity> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<TaskEntity> findByDepartmentCodeAndStatusIn(String departmentCode, List<String> statuses);
    List<TaskEntity> findByProjectIdOrderByCreatedAtAsc(String projectId);
    Optional<TaskEntity> findByTaskKey(String taskKey);
    Optional<TaskEntity> findByExecutionId(String executionId);
}
```

**A3. 扩展 TaskStatus 枚举**

```java
public enum TaskStatus {
    PENDING,
    CLAIMED,
    IN_PROGRESS,
    SUBMITTED,          // 新增：已提交，等待审核
    PENDING_REVIEW,     // 新增：待审核
    REVIEWED,           // 新增：已审核
    COMPLETED,
    REJECTED,           // 新增：审核拒绝
    NEEDS_REWORK,       // 新增：需要返工
    FAILED,
    CANCELLED
}
```

**A4. 修改 TaskCheckout**
- 创建/领取/提交/完成任务时，同步写入 TaskEntity
- 内存 Map 降级为缓存（可选，保持现有逻辑不变，仅增加持久化写入）

**A5. 修改 TaskController.submitTask**
- submit 后状态为 SUBMITTED（不再直接 COMPLETED）
- 保存 submissionResult 和 submittedAt

**A6. 修改 TaskController.getEmployeeTasks**
- 增加 `status` 查询参数，支持 `active|submitted|completed|all`
- 从 TaskRepository 查询，而不是只从 checkedOutTasks

**A7. 修改 TaskController.reviewTask**
- review approved → COMPLETED
- review rejected → NEEDS_REWORK 或 REJECTED

#### 阶段 B：Project 持久化 + 项目任务关联（T-P0-6, T-P0-7, T-P0-8）

**B1. 新建 ProjectEntity**

```java
@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String projectId;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String status;
    private String currentPhase;
    private String ownerDepartment;
    private String managerId;
    private Instant startDate;
    private Instant endDate;
    private double progress;
    
    // 新增字段
    private String tenantId;
    private String creatorUserId;
    private String projectKey;
    private String sourceTaskKey;
    private String sourceConversationId;
    private String dataNamespace;
    
    private Instant createdAt;
    private Instant updatedAt;
}
```

**B2. 新建 ProjectRepository**

**B3. 修改 ProjectServiceImpl**
- ConcurrentHashMap 改为 ProjectRepository 查询
- 创建/更新项目时同步写入 ProjectEntity

**B4. 修改 ProjectController 项目任务子资源**
- `GET /projects/{projectId}/tasks` 改为查询 TaskRepository.findByProjectIdOrderByCreatedAtAsc
- `POST /projects/{projectId}/tasks` 改为创建真实 TaskEntity
- `PUT /projects/{projectId}/tasks/{taskId}` 改为更新真实 TaskEntity
- `DELETE /projects/{projectId}/tasks/{taskId}` 改为删除真实 TaskEntity

#### 阶段 C：AgentTaskController 治理 + 前端统一（T-P0-5, T-P1-4）

**C1. AgentTaskController 标记 @Deprecated**
- 所有方法添加 `@Deprecated` 注解
- 响应头添加 `Warning: 299 - "This API is deprecated, use /api/tasks instead"`

**C2. 前端 taskApi 统一**
- `taskApi.list/create/update/getLogs/trigger` 改为调用 `/tasks` 路由
- 移除对 `/agents/{agentId}/tasks` 路由的依赖

**C3. 前后端字段对齐**
- `projectApi.list()` 参数从 `department_id` 统一为 `department`
- ProjectController 返回字段兼容前端 `departmentId`/`status` 格式

#### 阶段 D：统一上下文 + data 沉淀（T-P1-1, T-P1-2, T-P1-5）

**D1. 新建 WorkItemContext**
- 统一 project/task/execution 上下文模型

**D2. 新建 DataNamespaceService**
- 生成标准 data 路径

**D3. 新建 RuntimeEventStore**
- 写 events.jsonl/session.json/summary.json

**D4. DepartmentChatService 创建 taskKey/executionId 后回写 Task/Project**

#### 阶段 E：权限 + WebSocket 续接（T-P1-3, T-P1-5）

**E1. 新建 WorkItemPermissionService**
- 统一判断项目/任务访问权限

**E2. 新建 ConnectionRegistry**
- 统一连接/任务/项目/execution 映射

**E3. WebSocket 断线重连补发**

### 10.3 与第 9 章 WebSocket 修复的关系

第 9 章修复了"前端收不到回复、刷新后记录消失"的紧急问题，主要涉及 `DepartmentChatService` 和 `DepartmentWebSocketHandler`。

本章修复的是任务/项目模块的基础设施问题，主要涉及 `TaskCheckout`、`TaskController`、`ProjectServiceImpl`、`ProjectController`。

两者在阶段 D 会产生交叉：`DepartmentChatService` 创建的 execution 需要回写 Task/Project，这依赖阶段 A/B 的 TaskEntity/ProjectEntity 先落地。

### 10.4 实施状态

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| A | Task 持久化 + 字段扩展 + 状态机修正 | ✅ 已完成 |
| B | Project 持久化 + 项目任务关联 | ✅ 已完成 |
| C | AgentTaskController 治理 + 前端统一 | ✅ 已完成 |
| D | 统一上下文 + data 沉淀 | ✅ 已完成 |
| E | 权限 + WebSocket 续接 | ✅ 已完成 |

#### 10.4.1 阶段 A 已完成的修改

| 文件 | 修改内容 |
| --- | --- |
| `core/database/entity/TaskEntity.java` | 新建 JPA 实体，包含原有字段 + userId/tenantId/taskKey/executionId/departmentCode/sourceType/projectId/submissionResult/reviewerId 等新增字段 |
| `core/database/repository/TaskRepository.java` | 新建 Repository，支持按 assignedTo+status、userId、taskKey、executionId、projectId 等维度查询 |
| `core/ops/scheduler/TaskCheckout.java` | 扩展 TaskStatus（+SUBMITTED/PENDING_REVIEW/REVIEWED/REJECTED/NEEDS_REWORK），注入 TaskRepository，关键操作同步持久化，新增 submitTask/reviewTask 方法 |
| `gateway/controller/TaskController.java` | submitTask 不再直接 complete（改为 SUBMITTED），getEmployeeTasks 支持 status 参数（active/completed/submitted/all），reviewTask 使用新的审核逻辑 |
| `gateway/controller/AgentTaskController.java` | 标记 @Deprecated |

#### 10.4.2 阶段 B 已完成的修改

| 文件 | 修改内容 |
| --- | --- |
| `core/database/entity/ProjectEntity.java` | 新建 JPA 实体，包含原有字段 + tenantId/creatorUserId/projectKey/sourceTaskKey/sourceConversationId/dataNamespace 等新增字段 |
| `core/database/repository/ProjectRepository.java` | 新建 Repository，支持按 projectId、status、ownerDepartment、managerId、projectKey 等维度查询 |
| `core/project/impl/ProjectServiceImpl.java` | 注入 ProjectRepository，关键操作同步持久化 |
| `gateway/controller/ProjectController.java` | 项目任务子资源接入真实 TaskRepository，不再是 stub |

#### 10.4.3 阶段 C 已完成的修改

| 文件 | 修改内容 |
| --- | --- |
| `gateway/controller/AgentTaskController.java` | 标记 @Deprecated |
| `frontend/src/services/api.ts` | taskApi 的 list/create/update/getLogs/trigger 方法改为调用 `/tasks` 路由，添加 @deprecated 注释；getEmployeeTasks 增加 status 参数 |

#### 10.4.4 阶段 D 已完成的修改

| 文件 | 修改内容 |
| --- | --- |
| `core/work/WorkItemContext.java` | 新建统一 project/task/execution 上下文 record，包含 tenantId/ownerUserId/departmentCode/projectId/projectKey/taskId/taskKey/executionId/sourceConversationId/sourceSessionId/dataNamespace/documentNamespace |
| `core/work/WorkItemKeyGenerator.java` | 新建统一 key 生成器，支持 generateTaskKey/generateProjectKey/generateExecutionId/generateDataNamespace/generateProjectDataNamespace |
| `core/runtime/DataNamespaceService.java` | 新建标准 data 路径生成服务，支持项目/任务/对话/索引路径 |
| `core/runtime/RuntimeEventStore.java` | 新建运行时事件存储，支持 appendTaskEvent/appendProjectEvent/appendConversationEvent 和 summary 读写 |
| `core/config/LivingAgentCoreConfig.java` | 注册 DataNamespaceService 和 RuntimeEventStore 为 Spring Bean |
| `gateway/service/WorkItemContextService.java` | 新建上下文构造服务，从 AuthContext/WebSocket session 构造 WorkItemContext |
| `gateway/controller/TaskController.java` | 任务生命周期事件写入 RuntimeEventStore（submit/review/claim） |
| `gateway/controller/ProjectController.java` | 项目生命周期事件写入 RuntimeEventStore（update/delete） |
| `gateway/service/DepartmentChatService.java` | 对话执行事件写入 RuntimeEventStore，创建 execution 时绑定 ConnectionRegistry |

#### 10.4.5 阶段 E 已完成的修改

| 文件 | 修改内容 |
| --- | --- |
| `gateway/websocket/ConnectionRegistry.java` | 新建统一连接注册接口，支持 bindTask/bindExecution/bindProject，按 userId/tenantId/taskKey/executionId/projectKey 查询 |
| `gateway/websocket/InMemoryConnectionRegistry.java` | 新建内存实现，后续可替换 Redis |
| `gateway/websocket/DepartmentWebSocketHandler.java` | 注入 ConnectionRegistry，连接建立时注册，关闭时注销，消息处理时更新 lastActivity |
| `gateway/security/WorkItemPermissionService.java` | 新建统一权限判断服务，接受 AuthContext 参数，支持 canViewTask/canEditTask/canAssignTask/canReviewTask/canViewProject/canEditProject/canManageProject |
| `gateway/controller/TaskController.java` | 集成权限检查（getTask→canViewTask, submitTask→canEditTask, reviewTask→canReviewTask），新增 GET /tasks/my 端点，claim/submit 从 token 提取身份 |
| `gateway/controller/ProjectController.java` | 集成权限检查（getProject→canViewProject, updateProject→canEditProject, deleteProject→canManageProject） |
| `gateway/service/ExecutionReceiptTaskProjectBridge.java` | 新建 ReceiptListener 实现，receipt 到达时自动更新关联 Task/Project 状态 |
| `core/autonomy/ArtifactRecord.java` | 新增 taskId/projectId 字段，新增 withTaskId/withProjectId/associateTaskAndProject |
| `core/database/entity/ArtifactRecordEntity.java` | 新增 task_id/project_id 列 |
| `core/database/repository/ArtifactRecordRepository.java` | 新增 findByTaskId/findByProjectId/findByExecutionIdAndTaskId/findByExecutionIdAndProjectId |
| `frontend/src/services/api.ts` | claimTask 不再传入 employeeId，submitTask 不再传入 employeeId，getEmployeeTasks→getMyTasks，globalTaskApi.getByEmployee→getMyTasks |

### 第四轮：大脑模型自动分配 + 动态Provider + TraceEvent序列化（2026-05-28）✅ 已完成

| 文件 | 修改内容 | 状态 |
|---|---|---|
| `core/model/pool/BrainAutoAssigner.java` | ✅ 新增，启动时幂等自动分配9个大脑的最佳模型 | 已完成 |
| `core/model/pool/BrainModelResolver.java` | ✅ `resolveDefault()` 移除硬编码ollama fallback，三级降级返回null | 已完成 |
| `core/brain/impl/AbstractBrain.java` | ✅ `executeReActLoop()` 动态解析 Provider（brainModelResolver + ResolvedBrainModelProvider） | 已完成 |
| `core/autonomy/AutonomyTraceService.java` | ✅ 单线程 ExecutorService 替代 TransactionTemplate，解决并发写入冲突 | 已完成 |
| `core/model/pool/client/OpenAiCompatibleClient.java` | ✅ 新增 `embed()` 方法支持 embedding 模型测试 | 已完成 |
| `core/model/pool/ModelPoolManager.java` | ✅ isEmbeddingModel() 检测 + testProvider() 路由分发 + BrainAutoAssigner 触发 | 已完成 |
| `gateway/controller/ModelPoolController.java` | ✅ 新增 POST /auto-assign-brains 端点 | 已完成 |
| `core/database/entity/CodeReviewStateEntity.java` | ✅ JSONB字段添加 @ColumnTransformation(write="?::jsonb") | 已完成 |

---

## 11. 最优流程设计：分层自治执行闭环（2026-06-05）

> 设计目标：将当前"中央指派 + 线性回执"模式升级为"分层自治 + 自行领取 + 部门内审查闭环 + 部门级聚合 + 主脑统一收口"模式，让系统更接近真实企业组织的协作方式。

### 11.1 设计动机与核心问题

当前系统已实现从"用户请求 → 主脑规划 → 员工派发 → 回执聚合 → 主脑收口"的端到端链路，但存在以下架构性问题：

1. **主脑瓶颈**：所有任务无论复杂度都经过主脑分析，单部门简单任务也被迫绕一圈。
2. **中央指派模式**：员工由 `FixedEmployeeDispatcher`（LLM）统一指派，缺少自治领取能力。
3. **缺少部门内审查闭环**：员工执行完直接发 Receipt，没有同部门内其他员工的审查环节。
4. **缺少部门级聚合**：Receipt 直接聚合到主脑，部门大脑没有对部门内成果做质量分析和聚合。
5. **MainBrain.forwardToDepartment() 为 stub**：跨部门协调能力实际缺失。

### 11.2 最优流程全景

```text
用户消息
  │
  ├─ 轻量路由判断（TaskRouteClassifier）
  │   ├─ 单部门任务 → 直达对应部门大脑
  │   └─ 跨部门任务 → 主脑拆解 → 分发到各部门大脑
  │
  ├─ 部门大脑接收任务
  │   ├─ 分析任务，整理出部门级待办（DepartmentTodoList）
  │   └─ 发布待办到部门内待办池
  │
  ├─ 员工自行领取（Self-Claiming）
  │   ├─ 窗口期（2s）内员工按职责自行领取
  │   └─ 窗口期后未领取的由部门大脑兜底指派
  │
  ├─ 员工执行任务
  │   ├─ 按 ExecutionCapability 路由到工具/沙箱/LLM
  │   └─ 产出成果（Artifact + ExecutionResult）
  │
  ├─ 部门内审查闭环（Internal Review Loop）
  │   ├─ 编写员工完成 → 审查员工审查
  │   ├─ 审查不通过 → 出新待办 → 编写员工修改 → 再审查
  │   ├─ 审查通过 → 审查员工标注 COMPLETED 标签
  │   └─ 最大轮次限制（3轮），超出由部门大脑裁决
  │
  ├─ 部门大脑聚合分析（Department Aggregation）
  │   ├─ 检查是否所有子任务都有完成成果
  │   ├─ 检查成果之间的一致性和完整性
  │   ├─ 不合格的发回部门内待办
  │   └─ 合格的打包为 DepartmentDeliverable 交付主脑
  │
  └─ 主脑统一收口（MainBrain Final Compose）
      ├─ 汇总各部门交付的 DepartmentDeliverable
      ├─ 检查跨部门一致性
      ├─ 生成最终回复
      └─ 知识/绩效/产物沉淀
```

### 11.3 轻量路由层（TaskRouteClassifier）

#### 11.3.1 设计原则

不是所有任务都需要经过主脑拆解。简单任务（单一部门职责范围内的）应直接路由到对应部门大脑，只有复杂任务（跨部门的）才需要主脑介入。

#### 11.3.2 路由策略

```text
TaskRouteClassifier.classify(userMessage, conversationContext)
  → SINGLE_DEPARTMENT(departmentCode)   // 直达部门大脑
  → CROSS_DEPARTMENT(needsMainBrain)     // 需主脑拆解
  → CLARIFICATION_NEEDED(questions)       // 需澄清后才能判断
```

#### 11.3.3 路由规则

| 条件 | 路由结果 | 示例 |
| --- | --- | --- |
| 用户在特定部门对话中，任务意图与该部门匹配 | SINGLE_DEPARTMENT | 用户在技术部门说"帮我写个接口" → 直达 TechBrain |
| 任务意图明确属于单一部门 | SINGLE_DEPARTMENT | "分析这份财务报表" → 直达 FinanceBrain |
| 任务涉及多个部门的能力 | CROSS_DEPARTMENT | "做一个完整的电商网站" → 主脑拆解到 Tech/Sales/Cs |
| 无法判断归属 | CLARIFICATION_NEEDED | "帮我做个东西" → 先澄清 |

#### 11.3.4 与现有 DialogueAnalyzer 的关系

`TaskRouteClassifier` 不替代 `LlmBasedDialogueAnalyzer`，而是在其基础上增加路由判断：

```text
LlmBasedDialogueAnalyzer（已有）
  → kind = TASK
  → intent = web_development
  → primaryDept = tech

TaskRouteClassifier（新增）
  → 读取 analyzer 输出的 intent + primaryDept
  → 判断 supportingDepartments 是否为空
  → 若为空 → SINGLE_DEPARTMENT(tech)
  → 若不为空 → CROSS_DEPARTMENT
```

#### 11.3.5 代码落点

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/TaskRouteClassifier.java` | 新增接口 | 任务路由分类 |
| `core/autonomy/TaskRouteResult.java` | 新增 record | 路由结果：SINGLE_DEPARTMENT / CROSS_DEPARTMENT / CLARIFICATION_NEEDED |
| `core/autonomy/impl/DefaultTaskRouteClassifier.java` | 新增实现 | 基于 DialogueAnalyzer 输出 + 规则判断路由 |
| `gateway/service/DepartmentChatService.java` | 修改 | 在 orchestrate 之前调用 classify，决定走直达还是主脑拆解路径 |
| `core/autonomy/ConversationOrchestrator.java` | 修改 | 支持接收 TaskRouteResult，跨部门时走主脑拆解 |

### 11.4 员工自行领取机制（Self-Claiming）

#### 11.4.1 设计原则

从"中央指派"转为"自行领取 + 兜底指派"混合模式，提升部门自治性。

#### 11.4.2 领取流程

```text
部门大脑发布待办到 DepartmentTodoPool
  │
  ├─ 阶段1：自行领取窗口期（默认 2s）
  │   ├─ 员工根据自己的职责判断是否可以领取
  │   ├─ 领取时校验：职责匹配 + 工具白名单 + 未被他人领取（乐观锁）
  │   └─ 领取成功 → 状态变为 CLAIMED
  │
  ├─ 阶段2：兜底指派（窗口期结束后）
  │   ├─ 部门大脑检查未领取的待办
  │   ├─ 按员工职责和能力指派
  │   └─ 指派成功 → 状态变为 ASSIGNED
  │
  └─ 阶段3：执行
      ├─ CLAIMED 和 ASSIGNED 状态的待办进入执行
      └─ 执行结果通过部门内审查闭环处理
```

#### 11.4.3 冲突处理

采用乐观锁机制防止两个员工同时领取同一任务：

```text
DepartmentTodoItem:
  claimVersion: AtomicInteger  // 乐观锁版本号
  
claim(employeeCode):
  expected = claimVersion.get()
  if compareAndSet(expected, expected + 1):
    领取成功
  else:
    领取失败，已被他人领取
```

#### 11.4.4 领取资格校验

员工领取前必须通过以下校验：

```text
1. 职责匹配：待办的 requiredRole 在员工的 capabilities 中
2. 工具白名单：待办的 requiredTools 是员工工具白名单的子集
3. 部门归属：员工属于发布待办的部门
4. 负载检查：员工当前进行中的任务数未超过上限（建议 3 个）
```

#### 11.4.5 代码落点

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/DepartmentTodoPool.java` | 新增接口 | 部门待办池：发布、领取、查询未领取 |
| `core/autonomy/DepartmentTodoItem.java` | 新增 record | 待办项：id/requiredRole/requiredTools/requiredCapability/priority/status |
| `core/autonomy/TodoClaimResult.java` | 新增 record | 领取结果：SUCCESS/ALREADY_CLAIMED/NOT_QUALIFIED/POOL_FULL |
| `core/autonomy/impl/InMemoryDepartmentTodoPool.java` | 新增实现 | 内存版待办池（后续可替换 Redis） |
| `core/autonomy/EmployeeSelfClaimService.java` | 新增接口 | 员工自行领取服务 |
| `core/autonomy/impl/DefaultEmployeeSelfClaimService.java` | 新增实现 | 校验资格 + 乐观锁领取 + 兜底指派 |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | 修改 | 从被动接收任务改为主动从 TodoPool 领取 |

### 11.5 部门内审查闭环（Internal Review Loop）

#### 11.5.1 设计原则

这是本次最优流程设计中**最有价值的升级**。当前系统中员工执行完直接发 Receipt，缺少同部门内其他员工的审查环节。在真实企业组织中，代码编写完成后需要代码审查，文档编写完成后需要文档审核，这是一个**内部质量门禁（Quality Gate）**。

#### 11.5.2 审查关系定义

需要在 `FixedEmployeeRegistry` 中为每个员工增加"上下游关系"配置：

```text
EmployeeDefinition:
  code: "T09"
  name: "真绘"
  role: "frontend_developer"
  department: "tech"
  downstreamReviewers: ["T10"]  // 完成后由 T10（代码审查员）审查
  
EmployeeDefinition:
  code: "T10"
  name: "审码"
  role: "code_reviewer"
  department: "tech"
  downstreamReviewers: ["T09"]  // 审查不通过时回到 T09 修改
```

#### 11.5.3 审查状态机

```text
PENDING
  → 待办已发布，等待领取
IN_PROGRESS
  → 员工已领取并开始执行
SUBMITTED_FOR_REVIEW
  → 执行完成，已提交给审查员
UNDER_REVIEW
  → 审查员正在审查
REVISION_NEEDED
  → 审查不通过，需要修改（附带审查意见）
  → 回到 IN_PROGRESS，由原编写员工修改
COMPLETED
  → 审查通过，审查员标注完成标签
  → 部门大脑可见此成果为最终交付
REJECTED
  → 审查严重不通过，需要重新执行
  → 回到 PENDING，可能需要换人
```

状态转移图：

```text
PENDING → IN_PROGRESS → SUBMITTED_FOR_REVIEW → UNDER_REVIEW
                                                      │
                                    ┌─────────────────┼──────────────────┐
                                    ↓                                    ↓
                            REVISION_NEEDED                         COMPLETED
                                    │
                                    ↓
                              IN_PROGRESS（修改）
                                    │
                                    ↓
                          SUBMITTED_FOR_REVIEW
                                    │
                                    ↓
                              UNDER_REVIEW → ...（最多循环 3 次）
```

#### 11.5.4 循环终止条件

审查闭环可能无限循环，必须设置终止条件：

```text
1. 审查通过 → COMPLETED → 正常结束
2. 审查轮次 >= 3 → 部门大脑介入裁决
   ├─ 部门大脑分析审查意见和修改历史
   ├─ 判断是否强制通过（质量可接受）
   ├─ 判断是否更换执行人员
   └─ 判断是否上报主脑（超出部门能力）
3. 员工执行失败 → FAILED → 部门大脑决定是否重新派发
4. 总耗时超过部门级超时（建议 5 分钟）→ 部门大脑强制收口
```

#### 11.5.5 完成标记机制

关键设计：**只有审查角色才能标注 COMPLETED**。

```text
ReviewResult:
  reviewer: "T10"
  reviewRound: 2
  decision: APPROVED | REVISION_NEEDED | REJECTED | ESCALATE_TO_BRAIN
  qualityScore: 0.85
  issues: [...]
  suggestions: [...]
  completionTag: true  // 仅 APPROVED 时为 true
  
规则：
- 编写员工不能自己标记 COMPLETED
- 审查员工的 APPROVED 决定会自动设置 completionTag = true
- 部门大脑只关注 completionTag = true 的成果
```

#### 11.5.6 代码落点

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/review/InternalReviewService.java` | 新增接口 | 部门内审查服务：提交审查、审查决定、标记完成 |
| `core/autonomy/review/ReviewResult.java` | 新增 record | 审查结果：reviewer/decision/qualityScore/issues/completionTag |
| `core/autonomy/review/ReviewState.java` | 新增枚举 | 审查状态：SUBMITTED_FOR_REVIEW/UNDER_REVIEW/REVISION_NEEDED/COMPLETED/REJECTED |
| `core/autonomy/review/ReviewHistory.java` | 新增 record | 审查历史：每轮的 reviewer/decision/issues/revisionNotes |
| `core/autonomy/review/impl/DefaultInternalReviewService.java` | 新增实现 | 状态机管理、轮次计数、终止条件检查 |
| `core/autonomy/review/ReviewRelationshipRegistry.java` | 新增接口 | 审查关系注册表：谁完成后由谁审查 |
| `core/employee/registry/FixedEmployeeRegistry.java` | 修改 | 员工定义增加 downstreamReviewers 字段 |
| `core/autonomy/EmployeeWorkAssignment.java` | 修改 | 增加 reviewRequired/reviewerCode/maxReviewRounds 字段 |
| `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | 修改 | 执行完成后不直接发 Receipt，而是提交到 InternalReviewService |
| `core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java` | 修改 | 监听审查结果，REVISION_NEEDED 时重新触发执行 |

### 11.6 部门级聚合与交付（Department Aggregation）

#### 11.6.1 设计原则

当前系统中 Receipt 直接聚合到主脑，部门大脑没有对部门内成果做质量分析。最优流程中，部门大脑应承担**部门级聚合分析**职责：检查所有子任务完成情况、成果一致性、整体质量，然后打包为 `DepartmentDeliverable` 交付主脑。

#### 11.6.2 聚合流程

```text
DepartmentBrain.aggregate(departmentTodoPool, reviewResults):
  │
  ├─ 1. 检查完整性
  │   ├─ 所有待办是否都有 completionTag = true 的成果
  │   ├─ 若有缺失 → 生成新的部门内待办 → 回到 11.4 领取流程
  │   └─ 若全部完成 → 继续
  │
  ├─ 2. 检查一致性
  │   ├─ 前端和后端接口是否对齐（技术部门）
  │   ├─ 文档和代码是否匹配（技术部门）
  │   ├─ 财务数据和报告是否一致（财务部门）
  │   └─ 若不一致 → 生成修复待办 → 回到 11.4
  │
  ├─ 3. 质量评估
  │   ├─ 汇总各审查结果的质量评分
  │   ├─ 整体质量 < 阈值 → 发回修改
  │   └─ 整体质量 >= 阈值 → 继续
  │
  └─ 4. 打包交付
      ├─ 生成 DepartmentDeliverable
      ├─ 包含所有 COMPLETED 成果、审查历史、质量评分
      └─ 交付到主脑（通过 EmployeeNeuron → MainBrain）
```

#### 11.6.3 DepartmentDeliverable 结构

```text
DepartmentDeliverable:
  departmentCode: "tech"
  executionId: "exec-..."
  deliverables:
    - todoItemId: "todo-001"
      employeeCode: "T09"
      artifactPath: "data/artifacts/tech/{executionId}/index.html"
      reviewHistory: [ReviewResult_1, ReviewResult_2]
      qualityScore: 0.88
      completionTag: true
    - todoItemId: "todo-002"
      employeeCode: "T10"
      artifactPath: "data/artifacts/tech/{executionId}/api.js"
      reviewHistory: [ReviewResult_3]
      qualityScore: 0.92
      completionTag: true
  overallQualityScore: 0.90
  consistencyCheck: PASSED
  missingItems: []
  aggregationNotes: "前端和后端接口已对齐，所有审查通过"
  aggregatedAt: "2026-06-05T10:30:00Z"
```

#### 11.6.4 代码落点

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/autonomy/aggregation/DepartmentAggregationService.java` | 新增接口 | 部门级聚合服务 |
| `core/autonomy/aggregation/DepartmentDeliverable.java` | 新增 record | 部门交付物结构 |
| `core/autonomy/aggregation/AggregationResult.java` | 新增 record | 聚合结果：COMPLETE/INCOMPLETE/INCONSISTENT |
| `core/autonomy/aggregation/impl/LlmDepartmentAggregationService.java` | 新增实现 | LLM 分析成果一致性和质量 |
| `core/autonomy/aggregation/impl/DefaultDepartmentAggregationService.java` | 新增 fallback | 规则级聚合：检查 completionTag + 文件存在 + 质量评分 |
| `core/brain/impl/AbstractBrain.java` | 修改 | 增加 aggregateDepartmentDeliverable() 方法 |
| `gateway/service/DepartmentChatService.java` | 修改 | 将 receipt 聚合改为调用 DepartmentAggregationService |

### 11.7 主脑跨部门协调与统一收口

#### 11.7.1 设计原则

主脑的最终职责不变：汇总各部门交付的 `DepartmentDeliverable`，检查跨部门一致性，生成最终回复交付用户。但需要修复当前 `MainBrain.forwardToDepartment()` 为 stub 的问题。

#### 11.7.2 跨部门协调流程

```text
MainBrain.coordinateCrossDepartment(taskPlan, departmentDeliverables):
  │
  ├─ 1. 接收各部门的 DepartmentDeliverable
  │
  ├─ 2. 跨部门一致性检查
  │   ├─ 技术部门的前端是否与设计部门的 UI 规范一致
  │   ├─ 销售部门的方案是否与法务部门的合规审查一致
  │   └─ 各部门的交付物是否可以集成为完整产品
  │
  ├─ 3. 若有不一致
  │   ├─ 生成跨部门协调待办
  │   ├─ 通过 forwardToDepartment() 发送到相关部门大脑
  │   └─ 等待部门大脑处理并返回更新后的 deliverable
  │
  └─ 4. 生成最终回复
      ├─ 调用 MainBrainFinalSummaryService
      ├─ 汇总所有交付物、质量评分、完成状态
      └─ 生成用户可见的最终回复
```

#### 11.7.3 forwardToDepartment() 修复

当前 `MainBrain.forwardToDepartment()` 仅有日志输出，无实际转发逻辑。修复方案：

```text
MainBrain.forwardToDepartment(targetDept, coordinationRequest):
  1. 通过 ChannelManager 获取目标部门的 brain channel
  2. 构造 ChannelMessage：
     - type = CROSS_DEPARTMENT_COORDINATION
     - payload = coordinationRequest
     - replyChannel = 当前主脑的回复通道
  3. 发布到目标部门 brain channel
  4. 等待部门大脑处理并返回协调结果（超时 30s）
```

#### 11.7.4 代码落点

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `core/brain/impl/MainBrain.java` | 修改 | 修复 forwardToDepartment()，实现实际消息转发 |
| `core/brain/impl/AbstractBrain.java` | 修改 | 增加 handleCrossDepartmentCoordination() 方法 |
| `core/channel/ChannelMessage.java` | 修改 | 新增 CROSS_DEPARTMENT_COORDINATION 消息类型 |
| `core/autonomy/CrossDepartmentCoordinator.java` | 新增接口 | 跨部门协调服务 |
| `core/autonomy/impl/DefaultCrossDepartmentCoordinator.java` | 新增实现 | 通过 ChannelManager 实现跨部门消息转发和结果收集 |

### 11.8 更新后的 Trace 验收标准

最优流程落地后，Trace 链路应反映分层自治的执行路径：

```text
// 路由阶段
task_route_classified(route=SINGLE_DEPARTMENT|CROSS_DEPARTMENT, department=tech)

// 部门大脑分析阶段（仅跨部门任务有主脑拆解）
main_brain_planned(director_type=llm_based)  // 仅跨部门
brain_routed(department=tech)
department_todo_published(todoCount=3, department=tech)

// 员工领取阶段
todo_self_claimed(todoId=todo-001, employee=T09, claimType=SELF_CLAIM)
todo_self_claimed(todoId=todo-002, employee=T10, claimType=BRAIN_ASSIGNED)

// 执行阶段
employee_execution_started(employee=T09, capability=WEB_APP_BUILD)
employee_execution_completed(employee=T09, artifact=index.html)

// 部门内审查闭环
review_submitted(todoId=todo-001, reviewer=T10, round=1)
review_result(todoId=todo-001, decision=REVISION_NEEDED, issues=2, round=1)
employee_revision_started(todoId=todo-001, employee=T09, round=2)
review_submitted(todoId=todo-001, reviewer=T10, round=2)
review_result(todoId=todo-001, decision=APPROVED, qualityScore=0.88, round=2, completionTag=true)

// 部门级聚合
department_aggregation_started(department=tech, completedTodos=3, totalTodos=3)
department_aggregation_completed(department=tech, overallQuality=0.90, consistency=PASSED)

// 主脑收口
main_brain_finalized(summary_source=llm_main_brain, departments=[tech])
```

### 11.9 与现有系统的渐进式迁移策略

最优流程不要求推翻现有架构，可以在当前 `DepartmentChatService` → `ConversationOrchestrator` 的基础上渐进式改造。

#### 迁移阶段

| 阶段 | 内容 | 优先级 | 预计工作量 |
| --- | --- | --- | --- |
| M1 | 实现 TaskRouteClassifier，单部门任务直达 | P0 | 2 天 |
| M2 | 修复 MainBrain.forwardToDepartment()，实现跨部门转发 | P0 | 1-2 天 |
| M3 | 实现 DepartmentTodoPool + EmployeeSelfClaimService | P1 | 3 天 |
| M4 | 实现 InternalReviewService + 审查状态机 | P1 | 3-4 天 |
| M5 | 实现 DepartmentAggregationService + DepartmentDeliverable | P1 | 2-3 天 |
| M6 | 修改 DepartmentChatService 编排链路，接入以上组件 | P0 | 2 天 |
| M7 | FixedEmployeeRegistry 增加 downstreamReviewers 配置 | P2 | 1 天 |
| M8 | Trace 链路更新 + 验收测试 | P2 | 2 天 |

#### 迁移原则

1. **向后兼容**：新组件作为可选注入，现有流程不受影响。
2. **灰度切换**：先对技术部门试点审查闭环，再推广到其他部门。
3. **Fallback 保护**：每个新组件都有 fallback 到现有行为。
   - TaskRouteClassifier 无法判断时 → 走现有的主脑分析路径
   - DepartmentTodoPool 为空时 → 回退到现有的 FixedEmployeeDispatcher
   - InternalReviewService 未配置审查关系时 → 跳过审查，直接发 Receipt
   - DepartmentAggregationService 未启用时 → 回退到现有的 Receipt 聚合

### 11.10 更新后的角色职责矩阵

| 角色 | 原有职责 | 新增职责 |
| --- | --- | --- |
| MainBrain | 意图识别、需求确认、任务规划、员工分派、最终收口 | 跨部门任务拆解与分发、跨部门一致性协调、forwardToDepartment 实际转发 |
| TaskRouteClassifier | 无 | 轻量路由判断：单部门直达 or 跨部门走主脑 |
| 部门大脑 | 接收任务、选择模型、ReAct 循环 | 分析任务并整理部门级待办、待办兜底指派、部门内审查裁决（超轮次时）、部门级聚合分析与交付 |
| DepartmentTodoPool | 无 | 管理部门内待办：发布、领取、查询、超时清理 |
| 固定员工 | 接收指派、执行任务、发送 Receipt | 自行领取待办、执行后提交审查（非直接发 Receipt）、根据审查意见修改、审查他人成果并标注完成 |
| InternalReviewService | 无 | 管理审查状态机、轮次计数、终止条件、完成标记 |
| DepartmentAggregationService | 无 | 部门级聚合：完整性检查、一致性检查、质量评估、打包交付 |
| ExecutionReceiptReviewer | Receipt 级别验收 | 保留，作为审查闭环中审查员工的技术辅助 |
| FinalResponseCoordinator | 选择回复策略 | 保留，主脑收口后仍由其选择最终回复策略 |

### 11.11 更新后的 SessionContext 字段

结合本次最优流程设计，`SessionContext` 需要增加以下字段：

```text
// 已有字段
sessionId, userId, tenantId, departmentCode, accessLevel,
taskKey, executionId, requestId, clientSessionId, lastEventId,
connectedAt, lastSeenAt, connectionState, subscriptionState,
executionState, activeRouteKey, traceNamespace, memoryNamespace

// 新增字段
routeType: SINGLE_DEPARTMENT | CROSS_DEPARTMENT  // 路由类型
activeTodoId: string | null                       // 当前活跃的待办ID
reviewRound: int                                  // 当前审查轮次
departmentDeliverableId: string | null            // 部门交付物ID
```

### 11.12 新增枚举定义

#### ReviewDecision（审查决定）

```text
APPROVED           // 审查通过，标注 COMPLETED
REVISION_NEEDED    // 需要修改，回到编写员工
REJECTED           // 严重不通过，可能需要换人
ESCALATE_TO_BRAIN  // 超出审查能力，上报部门大脑裁决
```

#### TodoStatus（待办状态）

```text
PUBLISHED          // 已发布，等待领取
CLAIMED            // 已被员工自行领取
ASSIGNED           // 已被部门大脑兜底指派
IN_PROGRESS        // 执行中
SUBMITTED_FOR_REVIEW  // 已提交审查
UNDER_REVIEW       // 审查中
REVISION_NEEDED    // 需要修改
COMPLETED          // 审查通过（仅审查员可标注）
REJECTED           // 审查拒绝
FAILED             // 执行失败
ESCALATED          // 上报部门大脑
```

#### RouteType（路由类型）

```text
SINGLE_DEPARTMENT  // 单部门直达
CROSS_DEPARTMENT   // 跨部门经主脑拆解
CLARIFICATION_NEEDED  // 需要澄清后才能路由
```

### 11.13 验收标准

最优流程完整落地后，以下场景必须通过：

#### 场景1：单部门简单任务

```text
用户：在技术部门说"帮我写一个 Hello World 网页"
预期：
  1. TaskRouteClassifier → SINGLE_DEPARTMENT(tech)
  2. TechBrain 直接接收，不经过主脑拆解
  3. 发布 1 个待办到 DepartmentTodoPool
  4. T09（前端开发）自行领取
  5. T09 执行完成，提交 T10 审查
  6. T10 审查通过，标注 COMPLETED
  7. DepartmentAggregationService 聚合交付
  8. 主脑生成最终回复
Trace 中不应出现 main_brain_planned
```

#### 场景2：跨部门复杂任务

```text
用户：在主对话中说"帮我做一个完整的电商网站"
预期：
  1. TaskRouteClassifier → CROSS_DEPARTMENT
  2. MainBrain 拆解：Tech(前端+后端) + Sales(商品方案) + Legal(合规审查)
  3. 各部门大脑分别接收部门级任务
  4. 各部门内部走 领取→执行→审查→聚合 流程
  5. 各部门交付 DepartmentDeliverable 给主脑
  6. MainBrain 跨部门一致性检查
  7. 主脑生成最终回复
Trace 中应出现 cross_department_coordinated
```

#### 场景3：审查不通过返工

```text
用户：在技术部门说"帮我写一个用户登录页面"
预期：
  1. T09 编写登录页面，提交审查
  2. T10 审查发现缺少密码强度校验 → REVISION_NEEDED
  3. T09 修改后重新提交
  4. T10 审查通过 → APPROVED + completionTag=true
  5. 审查轮次 = 2，在 Trace 中可追踪
```

#### 场景4：审查超轮次

```text
预期：
  1. 3 轮审查后仍有问题
  2. InternalReviewService → ESCALATE_TO_BRAIN
  3. TechBrain 介入分析审查意见和修改历史
  4. TechBrain 决定：强制通过 / 换人 / 上报主脑
  5. 决定结果写入 Trace 和 Receipt
```

