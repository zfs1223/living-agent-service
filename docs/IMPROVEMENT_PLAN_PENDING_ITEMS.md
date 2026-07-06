# Living Agent Service - 未完成闭环改进方案汇总

> **生成日期**: 2026-07-02
> **来源文档**:
> - [DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md](DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md) — 桌面端对接审计+后端闭环深度审计
> - [MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md](MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md) — 模型责任与执行优化
> - [IMPROVEMENT_PLAN_INDEX.md](IMPROVEMENT_PLAN_INDEX.md) — 闭环改进方案汇总索引
> **说明**: 两份来源文档体积过大（171KB/251KB），本文件专门提取两份文档中**尚未完成**的改进项，便于跟踪实施。

---

## 一、DESKTOP_BACKEND 文档未完成项

### 1.1 断点 B3：回执链路无事务（✅ 已修复 2026-07-02）

**当前状态**：listener 回调（含 updateTaskFromReceipt）已改为事务内同步执行，review workflow 保留 afterCommit。回执+任务更新在同一事务边界内。

**已修复内容**：
1. `JpaEmployeeExecutionReceiptService.recordReceipt()` 中 `notifyListeners()` 在事务内同步调用
2. `routeReviewAndNotify()` 保留在 afterCommit（非关键路径）
3. `ExecutionReceiptTaskProjectBridge.updateTaskFromReceipt` 在调用者事务内执行

**代码位置**：`JpaEmployeeExecutionReceiptService.java:108-157`

---

### 1.2 断点 B11：声纹 embedding 真实实现（✅ 已完成 2026-07-02）

**当前状态**：
- ✅ OAuth token 校验已接入真实 `OAuthService`（飞书/钉钉/企业微信）
- ✅ Python 端 `speaker_service.py` 新增 `/extract` 端点，调用 CAM++ 模型提取 192 维 embedding
- ✅ Java 端 `SpeakerVerificationService` 新增 `extractEmbeddingRemote()` 远程调用方法
- ✅ `VoicePrintServiceImpl.extractEmbedding()` 改为远程调用 + 降级模式 fallback
- ✅ 声纹注册/验证管线已搭建（Qdrant向量库 + 余弦相似度比对逻辑已实现）

**已完成内容**：
1. Python 端新增 `POST /extract` 端点，返回 `{"success": true, "data": {"embedding": [...], "dimension": 192}}`
2. `SpeakerVerificationService.extractEmbeddingRemote()` 解析远程响应中的 embedding 数组
3. `VoicePrintServiceImpl.extractEmbedding()` 优先远程调用，失败后 fallback 到伪实现 + 日志告警
4. 降级模式下直接走 fallback，远程调用连续失败后缓存失败状态

**代码位置**：`speaker_service.py:395-427`、`SpeakerVerificationService.java:403-464`、`VoicePrintServiceImpl.java:238-282`

---

### 1.3 P2 后端：代码质量与性能（8项，已完成1项）

| 编号 | 任务 | 修复方案 | 状态 |
|------|------|---------|------|
| B-2-1 | updateProjectFromExecution N+1 查询 | 一次查询后内存聚合 | ⬜ 待实施 |
| B-2-2 | TaskRepository.findByExecutionId 无 UNIQUE | 加 UNIQUE 约束或改返回 List | ✅ 已完成 2026-07-03（返回类型改List，8处调用方更新） |
| B-2-3 | ReceiptStatus 终态定义不一致 | DEGRADED 加入 isTerminal() | ✅ 已完成 2026-07-03 |
| B-2-4 | 统一 schema.sql 与 01_init.sql | 两文件内容对齐，不再使用 Flyway V 版本迁移文件 | ⬜ 待实施 |
| B-2-5 | DagTaskRepository JPQL JSONB | 改为 nativeQuery = true | ⬜ 待实施 |
| B-2-6 | PerformanceDashboardService JPA 实现 | getCompanyTop/Bottom 改用 PerformanceAssessmentRepository | ✅ 已完成 2026-07-03（接口新增方法，移除instanceof耦合） |
| B-2-7 | 异步任务失败重试机制 | CompletableFuture 增加 .exceptionally() 重试 | ⬜ 待实施 |
| B-2-8 | schema.sql 维护说明 | 文件头注释说明此文件为核心模块表结构定义源 | ⬜ 待实施 |

---

### 1.4 内存态模块仍需持久化升级（3项）

| 模块 | 当前状态 | 残留风险 | 建议 |
|------|---------|---------|------|
| UnifiedAuthService 会话 | 内存ConcurrentHashMap主 + DB双写 | 热路径写内存，极端情况内存与DB不一致 | 可接受：热路径性能需要，DB作为兜底恢复 |
| SystemConfigService 配置 | 内存ConcurrentHashMap缓存 + DB双写 | getProviderConfigs先查DB刷新缓存，但其他配置项可能不一致 | 可接受：启动时从DB加载，写入双写 |
| InMemoryConnectionRegistry | 内存ConcurrentHashMap主 + DB回退 | 连接信息重启后需从DB重建 | 可接受：WebSocket连接本身重启后需重连 |

> **结论**：3项均为"内存主+DB从"架构，在当前单节点部署下可接受。如需多节点部署，需升级为 Redis/DB 主索引。

---

### 1.5 审批机制精简待完成项

| 编号 | 任务 | 优先级 | 状态 |
|------|------|--------|------|
| 16.3 | InterventionDecisionEngine 精简 | P1 | ✅ 已完成 2026-07-02 |
| 16.4 | BrainBoundaryEnforcer 补强 | P1 | ✅ 已完成 2026-07-02 |
| 16.5 | 业务审批与计划审批独立改进 | P1 | ✅ 已完成 2026-07-03（审批体系已完整：ApprovalService+JPA+Controller+回调） |
| 16.6 | 审计与监控 | P2 | ⬜ 待实施 |

**16.3 已修复内容**：
1. `evaluate()` 从 `InterventionRequest` 传入 department/sessionId/conversationId/aiDecision 字段
2. 新建 `InterventionProperties` 配置类，升级/降级阈值（95%/10, 80%/5）配置化
3. 新建 `InterventionDecisionEntity` + `InterventionDecisionRepository` 持久化干预决策
4. 决策创建/学习/升级后自动写入 DB，getPendingDecisions 内存为空时从 DB 加载
5. 超时值改为配置化（realtime 300s, async 86400s）
6. schema.sql + 01_init.sql 新增 `intervention_decisions` 表

**代码位置**：`InterventionDecisionEngineImpl.java`、`InterventionProperties.java`、`InterventionDecisionEntity.java`

**16.4 已修复内容**：
1. `BrainBoundaryProperties` 新增 `BoundaryDefinition` 内嵌类和 `boundaries` 列表，支持 YAML 外部化配置
2. `registerBoundaries()` 优先加载配置，为空则使用默认边界
3. `findBoundary()` 改用精确后缀匹配（equals 或 endsWith）和 department 提取匹配，避免 contains 误匹配
4. `checkAction()` 检测 mustEscalateScenarios 时自动调用 `InterventionDecisionEngine.evaluate()`
5. 新增 `autoEscalateToMainBrain` 配置开关

**代码位置**：`BrainBoundaryEnforcer.java`、`BrainBoundaryProperties.java`

---

## 二、MODEL_RESPONSIBILITY 文档未完成项

### 2.1 DefaultInternalReviewService 内存版（✅ 已修复 2026-07-02）

**当前状态**：已新建 JPA 持久化实现 `JpaInternalReviewService`，审查数据存储在 PostgreSQL `internal_reviews` 表，重启不丢失。

**已修复内容**：
1. 新增 `InternalReviewEntity` + `InternalReviewRepository`（JPA）
2. 新建 `JpaInternalReviewService` 实现类，所有写操作 @Transactional
3. 保留 `DefaultInternalReviewService`（测试/降级）
4. 保留 ReviewListener 回调机制
5. GatewayConfig Bean 注册已切换为 JpaInternalReviewService

**代码位置**：`JpaInternalReviewService.java`、`InternalReviewEntity.java`、`InternalReviewRepository.java`

---

### 2.2 DefaultDepartmentAggregationService deliverables 内存版（✅ 已修复 2026-07-03）

**当前状态**：已完整实现持久化版本。`JpaDepartmentAggregationService` + `DepartmentDeliverableEntity` + `DepartmentDeliverableRepository`，Bean注册默认使用 LLM增强版(fallback为JPA版)。

**已完成内容**：
1. `DepartmentDeliverableEntity` + `DepartmentDeliverableRepository`（JPA）已存在
2. `JpaDepartmentAggregationService` 持久化实现已存在
3. `LlmDepartmentAggregationService` LLM增强版(fallback为JPA)已存在
4. `GatewayConfig` Bean注册已切换到JPA版作为fallback

**代码位置**：`JpaDepartmentAggregationService.java`、`DepartmentDeliverableEntity.java`、`DepartmentDeliverableRepository.java`

---

### 2.3 DepartmentExecutionResult.sessionId 非强类型（✅ 已修复 2026-07-03）

**当前状态**：sessionId 已提升为 `DepartmentExecutionResult` record 的一等字段，并新增 `resolveSessionId()` 兼容方法。

**已完成内容**：
1. `DepartmentExecutionResult` 新增 `sessionId` 一等字段
2. 新增 `resolveSessionId()` 兼容方法（优先使用一等字段，fallback到metadata）
3. 8处调用方已修复（DepartmentChatService 6处 + ChannelBackedDepartmentExecutionCoordinator 2处）

**代码位置**：`DepartmentExecutionResult.java`、`DepartmentChatService.java`、`ChannelBackedDepartmentExecutionCoordinator.java`

---

### 2.4 NEEDS_CLARIFICATION 澄清分支未闭环（✅ 已修复 2026-07-02）

**当前状态**：下游澄清闭环已实现。用户回复澄清问题后，系统自动检测 NEEDS_CLARIFICATION TaskEntity，注入澄清上下文到增强消息，重新走编排→分派→readiness评估流程。最大3轮后建议人工介入。

**已修复内容**：
1. DepartmentChatService.processDepartmentBrainAsync 入口新增澄清响应检测
2. 新增 findPendingClarifications / buildClarificationContextMessage / processWithClarificationContext 方法
3. TaskEntity 新增 clarificationRound 字段，TaskRepository 新增按会话+状态查询
4. 澄清上下文以 `[澄清对话上下文]` 前缀注入 LLM 消息
5. 最大3轮后强制 BLOCKED 并建议人工介入

**代码位置**：`DepartmentChatService.java:474-506,805-893`

---

### 2.5 3.4.18 待修复情况（R1-R6，P2 级）

| 编号 | 问题 | 风险 | 处理建议 |
|------|------|------|---------|
| R1 | SessionContext 目前可能仍只在 WebSocket 层短生命周期存在 | 断线重连后任务上下文可能丢失 | 将 SessionContext 的关键字段持久化到 ConnectionRegistry 或执行存储中 |
| R2 | ConnectionRegistry 可能尚未形成统一接口 | 连接、任务、执行之间的映射容易分散 | 先抽象接口，再由内存/Redis/数据库实现 |
| R3 | taskKey 生成逻辑若仍由前端拼接，容易重复或串任务 | 同名任务跨用户混写 | 改为服务端统一生成并回传 |
| R4 | data/ 目录如果仍是扁平 memory 结构 | 运行时数据会继续乱序增长 | 按 conversations/memory/indexes/archive 重新整理 |
| R5 | 已有历史数据未分类迁移 | 旧数据回放和检索仍不稳定 | 增加迁移脚本和重建索引流程 |
| R6 | 重连补发若只看当前状态不看事件游标 | 会丢失中间进度与 receipt | 引入 lastEventId 游标和事件重放机制 |

---

### 2.6 第11章最优流程待优化项

| 编号 | 待优化项 | 优先级 | 说明 |
|------|---------|--------|------|
| O1 | CrossDepartmentCoordinator 接口化 | P2 | 当前 DefaultCrossDepartmentCoordinator 为简单实现，跨部门协调能力有限 |
| O2 | DefaultRequirementReadinessEvaluator 增强 | P2 | readiness 评估规则可优化，减少 NEEDS_CLARIFICATION 误判 |
| O3 | 员工自行领取性能优化 | P2 | 大量待办时领取窗口期可动态调整 |

---

## 三、IMPROVEMENT_PLAN 体系未完成项

> 以下为 IMPROVEMENT_PLAN_L1/L2/L3 中定义的新增改进方案，尚未开始实施。详见各层级文件。

### 3.0 闭环断裂修复（2026-07-03 完成）

| 编号 | 断裂点 | 修复内容 | 状态 |
|------|--------|---------|------|
| 断裂1 | Canary空转 | NamedPipeModelClient新增canary路由判断+recordProbeSuccess/Failure | ✅ 已完成 |
| 断裂2 | ModelDaemonRecoveryService逻辑顺序 | clearDegraded()改为canary.startProbing() | ✅ 已完成 |
| 断裂3 | HealthIssue.type未设置 | 3处new HealthIssue()改为createIssue()工厂方法 | ✅ 已完成 |

### 3.1 L1 未完成项（8项）

| 编号 | 改进方案 | 优先级 | 依赖 | 状态 |
|------|---------|--------|------|------|
| P12-A | 启动依赖检查增强 | P0 | 无 | ✅ 已完成 |
| P12-B | 健康检查端点增强 | P0 | P12-A | ✅ 已完成 |
| P12-C | 启动顺序强制控制 | P0 | 无 | ✅ 已完成 |
| P12-D | 启动失败自动恢复 | P1 | P12-A/B/C | ✅ 已完成 |
| P14-A | 声纹验证 embedding 实现 | P1 | 无（管线已搭建） | ✅ 已完成（B11） |
| P14-B | 权限变更强制实时生效 | P0 | 无 | ✅ 已完成 |
| P14-C | 跨端权限实时同步 | P1 | P14-B | ✅ 已完成 |
| P14-D | 权限审计日志记录 | P1 | P14-B | ✅ 已完成 |

### 3.2 L2 未完成项（28项）

| 编号 | 改进方案 | 优先级 | 状态 |
|------|---------|--------|------|
| P17-A/B/C/D | 前端与后端交互闭环（4项） | P1 | P17-A✅/B✅/C✅/D✅ |
| P18-A/B/C/D | 数据库持久化闭环（4项） | P2 | P18-A✅/B✅/C✅/D✅（@Transactional回滚保护已添加） |
| P19-A | JNI调用结果验证增强 | P0 | ✅ 已完成 |
| P19-B | Native执行性能监控增强 | P0 | ✅ 已完成 |
| P19-C | Native模块健康检查增强 | P0 | ✅ 已完成 |
| P20-A | 进程存活监控增强 | P0 | ✅ 已完成 |
| P20-B | 模型加载验证增强 | P0 | ✅ 已完成 |
| P20-C | NamedPipe健康检查增强 | P0 | ✅ 已完成 |
| P20-D | 推理结果验证增强 | P0 | ✅ 已完成 |
| P22-A/B/C | Claude CLI代理闭环（3项） | P1 | P22-A✅/B✅/C✅ |
| P3-A-A/B/C | WebSocket连接管理子闭环（3项） | P2 | P3-A-A✅/B✅/C✅（连接管理+心跳+清理已有实现） |
| P3-B-A/B/C | 消息Trace子闭环（3项） | P2 | P3-B-A✅/B✅/C✅（AutonomyTraceService等价实现，DB持久化闭环完成） |
| P11-A-A/B/C | 员工状态管理子闭环（3项） | P2 | P11-A-A✅/B✅/C✅（EmployeeStateSynchronizer+EmployeeLifecycleService组合覆盖） |
| P11-B-A/B/C | 通道监控子闭环（3项） | P2 | P11-B-A✅/B✅/C✅（ChannelManager.getHealthSummary+HealthMonitorImpl.checkChannels） |

### 3.3 L3 未完成项（12项，全部完成 ✅）

| 编号 | 改进方案 | 优先级 | 状态 |
|------|---------|--------|------|
| P24-A | SelfHealingOrchestrator | P0 | ✅ 已完成 |
| P24-B | 进程级自愈 | P2 | ✅ 已完成（RECONNECT_PIPE） |
| P24-C | 连接级自愈 | P2 | ✅ 已完成（ConnectionHealthCheck+NOTIFY_CLIENT） |
| P25-A | 硬件升级ROI验证 | P1 | ✅ 已完成（自动回滚已补充） |
| P26-A | 知识消费效果反馈 | P1 | ✅ 已完成 |
| P27-A | 降级小流量回归+效果对比 | P1 | ✅ 已完成（定量指标已补充） |
| P28-A | 审核结果→分派权重联动 | P0 | ✅ 已完成 |
| P29-A | 满意度采集+行为验证 | P2 | ✅ 已完成（变异前后对比+回滚机制已补充） |
| P29-B | riskTolerance→边界联动 | P2 | ✅ 已完成（BrainBoundaryEnforcer注入BrainRegistry） |
| P30-A | 违规→黑名单→边界收紧联动 | P1 | ✅ 已完成 |
| P31-A | SelfGovernanceOrchestrator | P0 | ✅ 已完成（分发真正执行+审计持久化已补充） |
| P32-A | 生命体征仪表盘 | P2 | ✅ 已完成（预警主动推送+驱动改进闭环已补充） |

> **2026-07-03 第五轮断裂修复完成**：所有 L3 闭环断裂环节已修复，9个闭环全部升级为完整闭环。

---

## 四、统一实施优先级（合并去重）

### 4.1 🔴 P0 — 立即实施（21项，全部完成 ✅）

| 来源 | 编号 | 改进方案 | 依赖 | 状态 |
|------|------|---------|------|------|
| DESKTOP | B3 | 回执链路事务完整性 | 无 | ✅ 已完成 |
| DESKTOP | B11 | 声纹embedding真实实现 | 无 | ✅ 已完成 2026-07-02 |
| IMPROVE-L1 | P12-A | 启动依赖检查增强 | 无 | ✅ 已完成 |
| IMPROVE-L1 | P12-B | 健康检查端点增强 | P12-A | ✅ 已完成 |
| IMPROVE-L1 | P12-C | 启动顺序强制控制 | 无 | ✅ 已完成 |
| IMPROVE-L1 | P14-B | 权限变更强制实时生效 | 无 | ✅ 已完成 |
| IMPROVE-L2 | P19-A | JNI调用结果验证增强 | 无 | ✅ 已完成 2026-07-02 |
| IMPROVE-L2 | P19-B | Native执行性能监控增强 | P19-A | ✅ 已完成 2026-07-03 |
| IMPROVE-L2 | P19-C | Native模块健康检查增强 | P19-A | ✅ 已完成 2026-07-03 |
| IMPROVE-L2 | P20-A | 进程存活监控增强 | 无 | ✅ 已完成 2026-07-02 |
| IMPROVE-L2 | P20-B | 模型加载验证增强 | P20-A | ✅ 已完成 2026-07-03 |
| IMPROVE-L2 | P20-C | NamedPipe健康检查增强 | P20-A | ✅ 已完成 2026-07-03 |
| IMPROVE-L2 | P20-D | 推理结果验证增强 | P20-A | ✅ 已完成 2026-07-03 |
| IMPROVE-L3 | P24-A | SelfHealingOrchestrator | 无 | ✅ 已完成 2026-07-02 |
| IMPROVE-L3 | P31-A | SelfGovernanceOrchestrator | P24-A | ✅ 已完成 2026-07-02 |
| IMPROVE-L3 | P28-A | 审核结果→分派权重联动 | 无 | ✅ 已完成 |
| MODEL | M-IR | InternalReviewService 持久化 | 无 | ✅ 已完成 |
| MODEL | M-DA | DepartmentAggregationService 持久化 | 无 | ✅ 已完成 |
| MODEL | M-NC | NEEDS_CLARIFICATION 澄清闭环 | 无 | ✅ 已完成 |
| DESKTOP | 16.3 | InterventionDecisionEngine 精简 | 无 | ✅ 已完成 |
| DESKTOP | 16.4 | BrainBoundaryEnforcer 补强 | 无 | ✅ 已完成 |

> **P19-A 已完成内容**：
> 1. 新增 `NativeException` — 区分可恢复/不可恢复错误，包含 operationName
> 2. 新增 `NativeExceptionHandler` — UnsatisfiedLinkError→不可恢复，NullPointerException→可恢复
> 3. 新增 `NativeServiceWrapper` — `callNative()`(null检查)、`callNativeBoolean()`(区分业务false和调用出错)、`callNativeOptional()`(允许合法null)
> 4. 增强 `NativeLibrary` — 加载失败后 `loaded=false` + `log.error`，新增 `isAvailable()` 和 `getLoadError()`

> **P20-A 已完成内容**：
> 1. 新增 `ModelDaemonProcessMonitor` — 通过控制管道 `status` 命令探测进程存活（替代 `ps aux`）
> 2. 修改 `HealthMonitorImpl.checkHealth()` — 纳入 `registeredChecks` 到周期检查
> 3. 新增 `ModelDaemonRecoveryService` — 自动重启 model_daemon.py + 降级模式自动清除
> 4. 修改 `StartupDependencyChecker` — 优先使用 `ModelDaemonProcessMonitor`，ModelClient 不可用时 fallback
> 5. 增强 `ModelDaemonHealthCheck` — Windows 下改用 PowerShell 检测 Named Pipe

> **P24-A 已完成内容**：
> 1. 新增 `SelfHealingOrchestrator` 接口 — `orchestrate(HealthIssue)` + `getRecentResults()` + `isEnabled()`
> 2. 新增 `SelfHealingResult` record — success/failure/escalated 三种工厂方法
> 3. 新增 `SelfHealingProperties` — enabled/confidenceThreshold/cooldownSeconds/maxRetry 配置化
> 4. 新增 `SelfHealingOrchestratorImpl` — 六步闭环 + 4种内置修复动作 + 防震荡
> 5. 修改 `HealthMonitorImpl` — 发布 CRITICAL/HIGH 级别 HealthIssue 事件 + 填充 suggestedAction

> **P31-A 已完成内容**：
> 1. 新增 `CrossLoopEvent` — sourceLoop/eventType/EventPriority/payload/coolingUntil
> 2. 新增 `CrossLoopEventBus` — 发布/订阅 L3 事件，支持去重和冷却期
> 3. 新增 `LoopPriorityArbiter` — 优先级仲裁（安全>自愈>降级>回执>经济>知识>个性）
> 4. 新增 `SelfGovernanceOrchestrator` 接口 — `submitEvent()` + `orchestrate()` + `getStatus()`
> 5. 新增 `SelfGovernanceProperties` — priorityOrder/cooldownSeconds/conflictArbitration 配置化
> 6. 新增 `SelfGovernanceOrchestratorImpl` — 事件订阅+优先级排序+分发到对应闭环处理器
> 7. 新增 `GovernanceReport` + `GovernanceStatus` — 编排结果和状态记录

> **P19-B 已完成内容**：
> 1. 新增 `NativeCallMetrics` — LongAdder 原子计数，记录 totalCalls/successCalls/failureCalls/totalDurationMs/min/max/lastError
> 2. 新增 `NativePerformanceMonitor` — 慢调用告警(>500ms)、高失败率告警(>30%)、getSlowOperations/getUnhealthyOperations
> 3. 修改 `NativeServiceWrapper` — 注入 PerformanceMonitor，每个 callNative/callNativeBoolean/callNativeOptional 自动记录成功/失败/耗时

> **P19-C 已完成内容**：
> 1. 新增 `NativeLibraryHealthCheck` — 检查 NativeLibrary.isAvailable() + 各操作成功率 + 慢调用检测
> 2. 注册到 HealthMonitor 周期检查，通过 GatewayConfig 注册 Bean

> **P20-B 已完成内容**：
> 1. 增强 `ModelLoadHealthCheck` — 新增 ModelClient 依赖注入（可选），有 ModelClient 时通过 `status` 命令验证推理可用性
> 2. 检查 model_status 中模型加载状态，无模型加载返回 DEGRADED
> 3. 修改 `StartupDependencyChecker` — 传入 ModelClient 给 ModelLoadHealthCheck

> **P20-C 已完成内容**：
> 1. NamedPipeModelClient 新增熔断器（CLOSED→OPEN→HALF_OPEN 三态）
> 2. 5次连续失败触发熔断（OPEN），30s 后半开探测（HALF_OPEN），探测成功恢复（CLOSED）
> 3. `sendRequest` 和 `sendControlRequest` 均受熔断保护

> **P20-D 已完成内容**：
> 1. 新增 `InferenceResultValidator` — null/空/过短检查 + 10种错误模式匹配（CUDA OOM、timeout 等）+ sanitize 清洗
> 2. 集成到 `QwenProvider.simpleChat/chat` 和 `BitNetProvider.simpleChat/chat`

> **P12-D 已完成内容**：
> 1. 新增 `StartupRecoveryService` — 降级模式下每60s重新执行 HealthMonitor.checkHealth()，依赖恢复后自动 clearDegraded()
> 2. 最多20次连续重试，避免无限循环

> **P14-C 已完成内容**：
> 1. `AgentWebSocketHandler` 新增 `@EventListener onPermissionChange(PermissionChangeEvent)` — 权限降级时发送通知+断连

> **P14-D 已完成内容**：
> 1. `PermissionServiceImpl.recordAccess` 新增5参数重载（detail 参数），`updateAccessLevel` 调用时记录 "Access level changed: OLD -> NEW"

> **P25-A 已完成内容**：
> 1. 新增 `HardwareUpgradeRoiValidator` — 升级后7天ROI跟踪，PerformanceBaseline vs PerformanceSnapshot对比
> 2. ROI计算：响应时间改善(40%) + 吞吐提升(40%) + 成功率提升(20%) - 成本摊销
> 3. ROI<-20%触发CircuitBreaker.recordFailure，ROI<-50%标记ROLLBACK_RECOMMENDED

> **P26-A 已完成内容**：
> 1. 新增 `KnowledgeConsumptionFeedback` — 记录消费反馈(helpful/unhelpful)，自动调整relevance(±0.05/±0.1)
> 2. 3次以上反馈后按helpfulRate调整confidence（≥80%晋升+0.15，≤30%降级-0.2）

> **P27-A 已完成内容**：
> 1. 新增 `DegradedTrafficCanary` — 10%流量探测，5次成功→全量提升，3次失败→回滚，5min超时
> 2. 集成到 `StartupRecoveryService`，降级恢复后先启动canary探测

> **P30-A 已完成内容**：
> 1. 新增 `SandboxViolationTracker` — 3次违规/1h→自动拉黑，TTL 1h后自动恢复
> 2. 集成到 `BrainBoundaryEnforcer.checkAction()` — 黑名单brain只允许chat_response，违规自动记录

> **闭环断裂修复已完成内容**：
> 1. 断裂1：NamedPipeModelClient新增canary字段+setter，sendRequest/sendControlRequest插入shouldRouteToComponent()路由判断和recordProbeSuccess/Failure记录；ProviderConfig注入DegradedTrafficCanary
> 2. 断裂2：ModelDaemonRecoveryService注入DegradedTrafficCanary，clearDegraded()改为canary.startProbing()
> 3. 断裂3：HealthMonitorImpl 3处直接new HealthIssue()改为createIssue()工厂方法，确保type字段正确填充

> **P17-C 已完成内容**：
> 1. 新增 `ErrorReportFeedbackService` — 高频错误(同type+url出现>=3次)自动发布EvolutionSignal
> 2. 修改 `ErrorReportController` — 注入ErrorReportFeedbackService，reportErrors末尾调用processErrorReports

> **P22 Claude CLI反馈闭环已完成内容**：
> 1. 修改 `ClaudeExecutionGateway` — 新增eventPublisher setter，enrichResult中失败/超时时发布EvolutionSignal(ERROR,REPAIR)
> 2. 修改 `ToolConfig` — claudeExecutionGateway Bean注入ApplicationEventPublisher

> **P24-B 已完成内容**：
> 1. SelfHealingOrchestratorImpl新增ACTION_RECONNECT_PIPE常量和reconnectPipe()方法
> 2. HealthMonitorImpl.fillSuggestedAction()管道丢失场景→RECONNECT_PIPE
> 3. executeAction() switch新增RECONNECT_PIPE分支

> **P29-A 已完成内容**：
> 1. 新增 `SatisfactionCollector` — 满意度记录+平均分+联动riskTolerance(低满意度降风险，高满意度增风险)
> 2. 新增 `SatisfactionController` — POST/GET /api/satisfaction 满意度API
> 3. PersonalityMutation新增increaseRisk()工厂方法

### 4.2 🟠 P1 — 近期实施（22项，已完成12项）

| 来源 | 编号 | 改进方案 |
|------|------|---------|
| IMPROVE-L1 | P12-D | 启动失败自动恢复 |
| IMPROVE-L1 | P14-A | 声纹embedding实现（管线已搭建） |
| IMPROVE-L1 | P14-C | 跨端权限实时同步 |
| IMPROVE-L1 | P14-D | 权限审计日志记录 |
| IMPROVE-L2 | P17-A/B/C/D | 前端与后端交互闭环 |
| IMPROVE-L2 | P19-B/C | Native性能+健康检查 |
| IMPROVE-L2 | P20-B/C/D | 模型加载+NamedPipe+推理验证 |
| IMPROVE-L2 | P22-A/B/C | Claude CLI代理闭环 |
| IMPROVE-L3 | P25-A | 硬件升级ROI验证 |
| IMPROVE-L3 | P26-A | 知识消费效果反馈 |
| IMPROVE-L3 | P27-A | 降级小流量回归 |
| IMPROVE-L3 | P30-A | 违规→黑名单联动 |
| MODEL | M-SID | DepartmentExecutionResult.sessionId 强类型化 |
| DESKTOP | 16.5 | 业务审批与计划审批独立改进 |

### 4.3 🟡 P2 — 后续实施（21项）

| 来源 | 编号 | 改进方案 |
|------|------|---------|
| DESKTOP | B-2-1~B-2-8 | 代码质量与性能（8项） |
| DESKTOP | 16.6 | 审计与监控 |
| IMPROVE-L2 | P18-A/B/C | 数据库表级闭环 |
| IMPROVE-L2 | P3-A/B, P11-A/B | 子闭环（12项） |
| IMPROVE-L3 | P24-B/C | 进程/连接级自愈 |
| IMPROVE-L3 | P29-A/B | 满意度+边界联动 |
| IMPROVE-L3 | P32-A | 生命体征仪表盘 |
| MODEL | R1-R6 | 3.4.18 待修复情况 |
| MODEL | O1-O3 | 第11章待优化项 |

---

## 五、验收清单（未完成项专用）

### 5.1 事务一致性验收

- [ ] 回执保存、任务状态更新在同一事务内
- [ ] 客户端收到"COMPLETED"推送后查询 task 状态已为 COMPLETED
- [ ] 重启后进行中的审查闭环仍可继续
- [ ] 重启后部门交付物记录仍存在

### 5.2 安全验收

- [ ] 伪造 voiceVector 无法通过声纹认证
- [ ] 权限变更后 WebSocket 连接强制断开
- [ ] 权限审计日志可追溯变更历史

### 5.3 健康与自愈验收

- [ ] model_daemon.py 崩溃后自动重启
- [ ] NamedPipe 断开后自动重建
- [ ] 启动依赖检查阻止不完整启动
- [ ] NEEDS_CLARIFICATION 返回澄清问题而非超时

---

**版本信息**：
- 文档版本: v1.5
- 更新日期: 2026-07-03
- P0项: 21/21 (100%) ✅ 全部完成
- P1项: 22/22 (100%) ✅ 全部完成（含 L3 闭环断裂修复）
- P2项: 3/21 已完成（P24-B、P29-A、B-2-3）
- L3闭环: 9/9 (100%) ✅ 全部完整闭环
- 闭环断裂修复: 3/3 ✅ 全部完成
- 未完成项总数: 18项（P2×18，主要是代码质量与性能优化）
