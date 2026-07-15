# Living Agent Service - 闭环改进方案汇总索引

> **生成日期**: 2026-07-02
> **最近更新**: 2026-07-11（新增闭环64：固定员工行动规范闭环，借鉴CLI-Anything设计模式，7阶段SOP + 7个子流程闭环）
> **目的**: 汇总64个闭环的覆盖状态、改进方案索引、实施优先级与验收清单，方便进度跟踪
> **配套文档**:
> - [未完成项汇总](IMPROVEMENT_PLAN_PENDING_ITEMS.md) — DESKTOP+MODEL+IMPROVEMENT_PLAN 三份文档的未完成项统一跟踪
> - [L1改进方案](IMPROVEMENT_PLAN_L1_CORE_LOOPS.md) — 流程正确性闭环(1-14)
> - [L2改进方案](IMPROVEMENT_PLAN_L2_COVERAGE_LOOPS.md) — 覆盖完整性闭环(17-22, 3-A/B, 11-A/B)
> - [L3改进方案](IMPROVEMENT_PLAN_L3_AUTONOMY_LOOPS.md) — 生命体自洽闭环(24-32)
> - [L4改进方案](IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md) — 用户业务闭环(38-63)
> - [Claude Code集成方案](CLAUDE_CODE_INTEGRATION_IMPROVEMENT_PLAN.md) — MCP Server + 插件 + 技能(闭环33)
> - [fuck-u-code Docker集成方案](FUCK_U_CODE_DOCKER_INTEGRATION_PLAN.md) — 代码审查工具部署+模型守护进程HTTP端点扩展(闭环49 P49-C)
> - [前端/桌面端闭环断点](IMPROVEMENT_PLAN_FRONTEND_DESKTOP_LOOP_GAPS.md) — 前端+桌面端闭环断点分析(P0×4 + P1×6 + P2×5 = 15个)
> - [固定员工行动规范SOP](FIXED_EMPLOYEE_ACTION_SOP_IMPROVEMENT_PLAN.md) — 借鉴CLI-Anything设计模式的7阶段行动规范(闭环64)

---

## 一、闭环体系总览

### 1.1 四层闭环架构

```
┌──────────────────────────────────────────────────────────────┐
│                    闭环体系四层架构                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  L1 流程正确性（14个闭环，14✅ + 0⚠️）                           │
│  ├─ 保证核心业务流程的输入→处理→输出→反馈→改进 链路正确          │
│  ├─ 编号: 1-14                                                │
│  └─ 详细改进方案: IMPROVEMENT_PLAN_L1_CORE_LOOPS.md           │
│                                                              │
│  L2 覆盖完整性（10个闭环，10✅ + 0⚠️） — 深度验证修复后          │
│  ├─ 补全被遗漏的支撑流程闭环                                    │
│  ├─ 编号: 17, 18-表级, 19, 20, 22, 3-A, 3-B, 11-A, 11-B     │
│  └─ 详细改进方案: IMPROVEMENT_PLAN_L2_COVERAGE_LOOPS.md       │
│                                                              │
│  L3 生命体自洽（14个闭环，14✅ + 0⚠️）                         │
│  ├─ 实现无需人工 + 兼容人工的自洽生命智能体                      │
│  ├─ 编号: 24-32（原有9个）+ 33（L2业务）+ 34-37（新增4个）     │
│  ├─ 内部分层: 感知层(32) + 协调层(31,34,36) + 调度层(37) + 创建层(35) + 执行层(24-30) │
│  ├─ 2026-07-03 第五轮断裂修复完成：所有闭环升级为完整闭环        │
│  ├─ 2026-07-03 闭环34-37补充：数字员工协作/动态创建/会话协调/智能调度 │
│  ├─ 详细改进方案: IMPROVEMENT_PLAN_L3_AUTONOMY_LOOPS.md       │
│  └─ 补充文档: IMPROVEMENT_PLAN_INDEX_SUPPLEMENT_LOOPS_34_37.md │
│                                                              │
│  L4 用户业务闭环（26个闭环，26✅ + 0⚠️）                  │
│  ├─ 用户可感知的业务功能生命周期闭环                              │
│  ├─ 编号: 38-63                                               │
│  ├─ 内部分层: P0核心入口(38-41) + P1运营支撑(42-49) + P2特定领域(50-63) │
│  ├─ 2026-07-08 新增：补齐系统基础设施闭环未覆盖的业务功能         │
│  └─ 详细改进方案: IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 闭环编号与覆盖状态

| 编号 | 闭环名称 | 层级 | 覆盖状态 | 改进方案编号 | 所属文件 |
|------|---------|------|---------|-------------|---------|
| 1 | WebSocket对话闭环 | L1 | ✅ 完整闭环(深度验证修复:dispatchWebSocketRecovery接入闭环31) | — | L1 |
| 2 | 审批流程闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 3 | 任务分配闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 4 | 进化调整闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 5 | 知识注入闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 6 | Windows自动化闭环 | L1 | ✅ 完整闭环(深度验证修复:WindowsAutomationImprovementTracker接入) | — | L1 |
| 7 | PostgreSQL数据一致性闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 8 | Qdrant向量一致性闭环 | L1 | ✅ 完整闭环(修复后) | — | L1 |
| 9 | 多级缓存一致性闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 10 | 跨系统数据同步闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 11 | 模型健康监控闭环 | L1 | ✅ 完整闭环 | — | L1 |
| 12 | 服务启动健康闭环 | L1 | ✅ 完整闭环 | P12-A✅/B✅/C✅/D✅ | L1 |
| 13 | 主动预判健康闭环 | L1 | ✅ 完整闭环(深度验证修复:ProactiveStrategyOptimizer添加@Scheduled) | — | L1 |
| 14 | 权限管理闭环 | L1 | ✅ 完整闭环 | P14-A✅/B✅/C✅/D✅ | L1 |
| 17 | 前端与后端交互闭环 | L2 | ✅ 完整闭环(深度验证修复:EvolutionSignal添加消费端) | P17-A✅/B✅/C✅/D✅ | L2 |
| 18-表级 | 数据库持久化闭环 | L2 | ✅ 完整闭环 | P18-A✅/B✅/C✅/D✅ | L2 |
| 19 | Native Rust与Java交互闭环 | L2 | ✅ 完整闭环 | P19-A✅/B✅/C✅ | L2 |
| 20 | Python模型守护进程闭环 | L2 | ✅ 完整闭环 | P20-A✅/B✅/C✅/D✅ | L2 |
| 22 | Claude CLI代理闭环 | L2 | ✅ 完整闭环 | P22-A✅/B✅/C✅ | L2 |
| 11-A | WebSocket连接管理闭环 | L2 | ✅ 完整闭环 | P3-A-A✅/B✅/C✅ | L2 |
| 3-B | 消息Trace闭环 | L2 | ✅ 完整闭环 | P3-B-A✅/B✅/C✅ | L2 |
| 11-A | 员工状态管理闭环 | L2 | ✅ 完整闭环(深度验证修复:EmployeeStatusRecoveryMonitor接入) | P11-A-A✅/B✅/C✅ | L2 |
| 11-B | 通道监控闭环 | L2 | ✅ 完整闭环(深度验证修复:ChannelSelfHealingMonitor接入) | P11-B-A✅/B✅/C✅ | L2 |
| 24 | 自愈闭环 | L3 | ✅ 完整闭环 | P24-A✅/B✅/C✅ | L3 |
| 25 | 经济自治闭环 | L3 | ✅ 完整闭环 | P25-A✅ | L3 |
| 26 | 知识自进化闭环 | L3 | ✅ 完整闭环 | P26-A✅ | L3 |
| 27 | 降级链路闭环 | L3 | ✅ 完整闭环 | P27-A✅/B✅ | L3 |
| 28 | 执行回执闭环 | L3 | ✅ 完整闭环(修复后:经验沉淀+CrossLoopEvent) | P28-A✅/B✅(修复后) | L3 |
| 29 | 大脑个性进化闭环 | L3 | ✅ 完整闭环 | P29-A✅/B✅ | L3 |
| 30 | 沙箱安全闭环 | L3 | ✅ 完整闭环 | P30-A✅ | L3 |
| 31 | 跨闭环协同编排闭环 | L3 | ✅ 完整闭环(修复后:5个dispatch方法全部实际执行) | P31-A✅ | L3 |
| 32 | 生命体征仪表盘闭环 | L3 | ✅ 完整闭环 | P32-A✅ | L3 |
| 33 | 数字员工使用Claude CLI工具闭环 | L2业务 | ✅ 完整闭环(深度验证修复:provider降级+恢复+动态阈值) | P33-A✅/B✅/C✅/D✅ | CLAUDE_CODE |
| 34 | 数字员工协作闭环 | L3 | ✅ 完整闭环 | 34-A✅/B✅/C✅/D✅ | SUPPLEMENT_34_37 |
| 35 | 动态员工创建闭环 | L3 | ✅ 完整闭环(深度验证修复:EmployeeCreationImprovementTracker+绩效反馈) | 35-A✅/B✅/C✅/D✅ | SUPPLEMENT_34_37 |
| 36 | 神经元会话协调闭环 | L3 | ✅ 完整闭环(深度验证修复:evaluateSessionQuality+参数动态调整) | 36-A✅/B✅/C✅/D✅/E✅ | SUPPLEMENT_34_37 |
| 37 | 员工智能调度闭环 | L3 | ✅ 完整闭环 | 37-A✅/B✅/C✅/D✅ | SUPPLEMENT_34_37 |
| 38 | 认证全生命周期闭环 | L4 | ✅ 完整闭环(深度验证修复:FeedbackActionHandler注入+CrossLoopEventBus) | P38-A✅ | L4 |
| 39 | 智能体(Agent)生命周期闭环 | L4 | ✅ 完整闭环(深度验证修复:RecoveryActionHandler注入+CrossLoopEventBus) | P39-A✅ | L4 |
| 40 | 项目管理闭环 | L4 | ✅ 完整闭环(深度验证修复:ProjectImprovementExecutor+动态阈值) | P40-A✅ | L4 |
| 41 | 人工干预决策闭环 | L4 | ✅ 完整闭环 | P41-A✅/B✅/C✅ | L4 |
| 42 | 技能管理闭环 | L4 | ✅ 完整闭环(深度验证修复:SkillImprovementExecutor+动态阈值) | P42-A✅ | L4 |
| 43 | 工作流编排闭环 | L4 | ✅ 完整闭环(修复后:OptimizationService接入+@Scheduled) | P43-A✅/B✅(修复后) | L4 |
| 44 | 消息通知闭环 | L4 | ✅ 完整闭环(修复后:策略参数驱动) | P44-A✅/B✅(修复后) | L4 |
| 45 | 合规管理闭环 | L4 | ✅ 完整闭环(修复后:Tracker+AutoUpdater接入) | P45-A✅/B✅(修复后) | L4 |
| 46 | 对话管理闭环 | L4 | ✅ 完整闭环(深度验证修复:ConversationQualityOptimizer+动态阈值) | P46-A✅ | L4 |
| 47 | 主动服务闭环 | L4 | ✅ 完整闭环(修复后:策略参数驱动) | P47-A✅/B✅(修复后) | L4 |
| 48 | 记忆管理闭环 | L4 | ✅ 完整闭环(修复后:提取阈值动态调整) | P48-A✅/B✅(修复后) | L4 |
| 49 | 代码审查工作流闭环 | L4 | ✅ 完整闭环(修复后:qualityThreshold动态调整) | P49-A✅/B✅/C✅ | L4 |
| 50 | 租户管理闭环 | L4 | ✅ 完整闭环(修复后:quotaWarningMultiplier动态调整+@Scheduled) | P50-A✅(修复后) | L4 |
| 51 | 接待/访客闭环 | L4 | ✅ 完整闭环(修复后:conversionWarningThreshold动态调整+@Scheduled) | P51-A✅(修复后) | L4 |
| 52 | 预算管理闭环 | L4 | ✅ 完整闭环(修复后:autoAlertThreshold动态调整+@Scheduled) | P52-A✅(修复后) | L4 |
| 53 | 绩效考核闭环 | L4 | ✅ 完整闭环(修复后:lowPerformanceThreshold动态调整+@Scheduled) | P53-A✅(修复后) | L4 |
| 54 | 积分/薪酬闭环 | L4 | ✅ 完整闭环(修复后:exchangeRateMultiplier动态调整+@Scheduled) | P54-A✅(修复后) | L4 |
| 55 | 广场/社交闭环 | L4 | ✅ 完整闭环(修复后:recommendationBoost动态调整+@Scheduled) | P55-A✅(修复后) | L4 |
| 56 | 虚拟办公室闭环 | L4 | ✅ 完整闭环(修复后:OfficeController传入真实值+syncCheckIntervalMs调整) | P56-A✅(修复后) | L4 |
| 57 | 系统设置闭环 | L4 | ✅ 完整闭环(修复后:lockedSettings自动锁定+@Scheduled) | P57-A✅(修复后) | L4 |
| 58 | 分布式部署闭环 | L4 | ✅ 完整闭环(深度验证修复:rebalanceShards真正迁移shard) | P58-A✅(修复后) | L4 |
| 59 | 异常检测闭环 | L4 | ✅ 完整闭环(修复后:sensitivityMultiplier动态调整+@Scheduled) | P59-A✅(修复后) | L4 |
| 60 | 服务管理闭环 | L4 | ✅ 完整闭环(深度验证修复:自动重试+动态调整重试间隔和并发) | P60-A✅(修复后) | L4 |
| 61 | 客户端设备闭环 | L4 | ✅ 完整闭环(深度验证:markDeviceCompromised自动解绑+isDeviceCompromised查询) | P61-A✅ | L4 |
| 62 | 数据迁移闭环 | L4 | ✅ 完整闭环(深度验证修复:evaluateMigrationStrategy动态调整批次和并发) | P62-A✅(修复后) | L4 |
| 63 | Claude Proxy闭环 | L4 | ✅ 完整闭环 | P63-A✅ | L4 |
| 64 | 固定员工行动规范闭环 | L3 | ✅ 完整闭环 | 64-A✅/B✅/C✅/D✅/E✅/F✅/G✅ | FIXED_EMPLOYEE_ACTION_SOP |

### 1.3 覆盖统计

| 层级 | ✅完整闭环 | ⚠️部分闭环 | ❌未闭环 | 合计 |
|------|----------|-------------|--------------|------|
| L1 | 14 | 0 | 0 | 14 |
| L2 | 10 | 0 | 0 | 10 |
| L2业务 | 1 | 0 | 0 | 1 |
| L3 | 14 | 0 | 0 | 14 |
| L4 | 26 | 0 | 0 | 26 |
| **合计** | **65** | **0** | **0** | **65** |

### 1.4 已落地验证摘要（2026-07-02 代码核验）

> 以下闭环经 DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 和 MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 的改进项已落地到代码，核验结果如下：

| 闭环 | 已落地改进 | 核验结论 |
|------|-----------|---------|
| 2 审批流程 | ApprovalServiceImpl→ApprovalInstanceRepository(JPA)+@Transactional | ✅ 完全落地 |
| 3 任务分配 | TaskRouteClassifier+Self-Claiming+InternalReview+DepartmentAggregation | ✅ 完全落地 |
| 4 进化调整 | ModelHealthRegistry+BrainModelFallback（COOLDOWN替代CircuitBreaker） | ✅ 完全落地 |
| 5 知识注入 | LayeredKnowledgeBase+KnowledgePersistenceService+QdrantVectorService | ✅ 完全落地 |
| 6 Windows自动化 | 双通道(HTTP+WebSocket)+IPC注册/清理 | ✅ 完全落地 |
| 7 PostgreSQL一致性 | RuntimeEventStore→DB优先+文件降级；ExecutionResult→JPA持久化 | ✅ 完全落地 |
| 9 多级缓存 | DistributedCacheService(Redis+内存降级)+SystemConfigService(内存+DB双写) | ✅ 完全落地（Redis用于分布式缓存+内存作为降级，SystemConfig热路径内存优先+DB持久化） |
| 10 跨系统同步 | ExecutionReceiptTaskProjectBridge真实更新task+project统计 | ✅ 完全落地 |
| 11 模型健康监控 | ModelHealthRegistry(243行)+BrainModelFallback(298行) | ✅ 完全落地 |
| 28 执行回执 | LlmExecutionReceiptReviewer+DefaultExecutionReceiptReviewer双链+P28-A审核→权重联动 | ✅ 完整覆盖 |
| B1-B12断点 | B1/B2/B3✅/B4/B5/B6/B12已修复，B11声纹stub部分修复 | 见详细报告 |

### 1.5 2026-07-14 深度验证与修复记录

> 本轮验证聚焦：智能前台闲聊闭环、降级链路完整性、Docker日志问题修复

**代码修复（4项）**：

| 修复项 | 文件 | 修复内容 |
|--------|------|---------|
| Qdrant集合初始化 | `QdrantVectorService.java` | `initializeCollections()` 添加 `@PostConstruct`，启动时自动创建knowledge/employee/experience三个集合 |
| 模型路径健康检查 | `ModelLoadHealthCheck.java` | 硬编码MODEL_DIRS改为动态构建：优先读取`AI_MODELS_PATH`环境变量，补充`/app/ai-models`容器路径 |
| Agency-agents目录 | `MemoryConfig.java` | 知识播种前自动创建`./data/agency-agents`目录，避免路径不存在警告 |
| 固定员工日志改进 | `FixedEmployeeRegistry.java` | 创建摘要从"X succeeded, Y failed"改为"X created, Y already existed, Z failed (total: N)"，消除误导性"0 succeeded" |

**闭环验证结论**：

| 验证领域 | 结果 | 说明 |
|---------|------|------|
| L1闭环1-7 | ✅ 完整 | WebSocket对话→审批→任务分配→进化调整→知识注入→Win自动化→PG一致性 |
| L1闭环8-14 | ✅ 完整 | Qdrant(已加@PostConstruct)→Redis→跨系统→模型健康→启动依赖(已修路径)→主动预判→权限 |
| L2闭环17-22 | ✅ 完整 | 前端交互→DB持久化→Rust JNI→Python守护进程→Claude CLI→连接管理→Trace→员工状态→通道监控 |
| L3闭环24-37 | ✅ 完整 | 自愈→经济→知识进化→降级链路(3条全部真正fallback)→执行回执→大脑个性→沙箱→跨闭环协同→生命体征→CLI工具→员工协作/创建/会话/调度 |
| L4闭环38-64 | ✅ 完整 | 认证→Agent→项目→干预→技能→工作流→通知→合规→对话→主动→记忆→代码审查→租户→...→Proxy→固定员工SOP |
| 智能前台闲聊闭环 | ✅ 完整 | 未登录→/ws/public→chatPublic()→llm_chat→generate_chat_response()→响应 |
| 降级链路 | ✅ 完整 | 5条LLM-first/Rule-fallback链路全部真正执行fallback |

**分支记录**：

- 闲聊闭环分支：未登录走`chatPublic()`→`modelManager.chatAsync()`；已登录走部门大脑→绕过ChatNeuronRouter
- 语音闭环分支：未登录走`chatPublicAudio()`→临时session→`processAudioFullChain()`；已登录走Agent WebSocket
- 降级链路分支：每条链路有3-4个降级触发点（MainBrain不可用/LLM空/解析失败/响应异常短）
- FixedEmployee创建分支：数据库有定义→从DB加载；数据库空→静态定义兜底；已存在→确保neuron启动

### 1.6 第二轮改进落地（2026-07-02 Top 5 实施）

| 闭环/改进项 | 落地内容 | 核验结论 |
|------------|---------|---------|
| B3 回执事务 | listener回调在事务内同步执行，review保留afterCommit | ✅ 完全落地 |
| M-NC 澄清闭环 | findPendingClarifications+processWithClarificationContext，最大3轮 | ✅ 完全落地 |
| M-IR 审查持久化 | InternalReviewEntity+JpaInternalReviewService替代内存版 | ✅ 完全落地 |
| P28-A 审核权重联动 | PerformanceStatsService.adjustWeight+LLM Dispatcher prompt注入绩效 | ✅ 完全落地 |
| P12-A 启动依赖检查 | StartupDependencyChecker+ModelDaemonHealthCheck+ModelLoadHealthCheck | ✅ 完全落地 |

### 1.6 第三轮闭环链路验证（2026-07-03 代码逐环节核验）

> **判定标准**：闭环 = input→processing→output→feedback→improvement 全链路代码打通；缺 feedback→improvement = 半闭环；缺核心执行环节 = 未闭环

#### ✅ 完整闭环（37个）— 全部闭环已完整打通

| 编号 | 闭环名称 | 链路验证 |
|------|---------|---------|
| 1-11,13 | WebSocket/审批/任务/进化/知识/Win自动化/PG/Qdrant/Redis/跨系统/模型健康/预判 | 前两轮已验证 ✅ |
| 12 | 服务启动健康闭环 | 依赖检查→降级模式→定时重试→canary探测→shouldPromote阈值→clearDegraded ✅ |
| 14 | 权限管理闭环 | 声纹注册→权限校验→PermissionChangeEvent→WebSocket断连→审计日志 ✅ |
| 17-22, 3-A/B, 11-A/B | L2覆盖闭环 | 前两轮已验证 ✅ |
| 19 | Native交互闭环 | NativeServiceWrapper→NativeCallMetrics→NativePerformanceMonitor→NativeLibraryHealthCheck ✅ |
| 20 | Python模型守护进程闭环 | 进程监控→熔断器→推理验证→HealthIssue.type推断→SelfHealingOrchestrator + **LLM HTTP端点(8392)OpenAI兼容** ✅ |
| 24 | 自愈闭环 | 异常检测→根因分析→补丁→执行→验证→**经验沉淀写入KnowledgeBase** ✅ 第五轮修复 |
| 25 | 经济自治闭环 | ROI计算→CircuitBreaker触发→**ROI<-50%自动回滚** ✅ 第五轮修复 |
| 26 | 知识自进化闭环 | KnowledgeConsumptionFeedback→confidence升降→晋升/降级 ✅ |
| 27 | 降级链路闭环 | Canary启动→小流量探测→**定量指标判断(响应时间≤5s/成功率≥80%)**→全量切换 ✅ 第五轮修复 |
| 28 | 执行回执闭环 | 回执审核→adjustWeight→Dispatcher prompt注入 ✅ |
| 29 | 大脑个性进化闭环 | 个性变异→**满意度对比→回滚机制**→边界联动 ✅ 第五轮修复 |
| 30 | 沙箱安全闭环 | checkAction→违规记录→3次拉黑→边界收紧→TTL恢复 ✅ |
| 31 | 跨闭环协同编排闭环 | 优先级仲裁→事件分发→**真正执行→审计持久化** ✅ 第五轮修复 |
| 32 | 生命体征仪表盘闭环 | 采集→评分→展示→**预警主动推送→驱动改进闭环** ✅ 第五轮修复 |
| 33 | Claude CLI工具闭环 | MCP配置→插件安装→技能对接→API调用→回执提交 ✅ |
| 34 | 数字员工协作闭环 | 协作需求识别→createSession→任务分配→startSession→执行→绩效评估→策略优化 ✅ |
| 35 | 动态员工创建闭环 | 工作负载评估→MainBrain推理→能力缺口识别→员工设计→创建→验证→策略改进 ✅ |
| 36 | 神经元会话协调闭环 | 会话创建→四通道创建→神经元绑定→输入分发→响应聚合→资源清理 ✅ |
| 37 | 员工智能调度闭环 | 任务分析→MainBrain推理→能力匹配→负载均衡→调度决策→执行监控→绩效反馈 ✅ |

#### ⚠️ 半闭环/部分闭环（0个）— 全部已修复

> 2026-07-03 第五轮断裂修复+闭环34-37补充后，所有37个闭环均为完整闭环，无半闭环或部分闭环。

---

## 二、实施优先级（全量合并）

### 2.1 P0 — 立即实施（解锁其他闭环的前提）

| 编号 | 改进方案 | 所属文件 | 依赖关系 | 状态 |
|------|---------|---------|---------|------|
| P12-A | 启动依赖检查增强 | L1 | 无 | ✅ 已完成 |
| P12-B | 健康检查端点增强 | L1 | P12-A | ✅ 已完成 |
| P12-C | 启动顺序强制控制 | L1 | 无 | ✅ 已完成 |
| P14-B | 权限变更强制实时生效 | L1 | 无 | ✅ 已完成 |
| P20-A | 进程存活监控增强 | L2 | 无 | ✅ 已完成 |
| P20-B | 模型加载验证增强 | L2 | P20-A | ✅ 已完成 |
| P20-C | NamedPipe健康检查增强 | L2 | P20-A | ✅ 已完成 |
| P20-D | 推理结果验证增强 | L2 | P20-A | ✅ 已完成 |
| P19-A | JNI调用结果验证增强 | L2 | 无 | ✅ 已完成 |
| P19-B | Native执行性能监控增强 | L2 | P19-A | ✅ 已完成 |
| P19-C | Native模块健康检查增强 | L2 | P19-A | ✅ 已完成 |
| P24-A | SelfHealingOrchestrator | L3 | 无 | ✅ 已完成 |
| P31-A | SelfGovernanceOrchestrator | L3 | P24-A | ✅ 已完成 |
| P28-A | 审核结果→分派权重联动 | L3 | 无 | ✅ 已完成 |
| B11 | 声纹embedding真实实现 | DESKTOP | 无 | ✅ 已完成 |
| M-IR | InternalReviewService持久化 | MODEL | 无 | ✅ 已完成 |
| M-DA | DepartmentAggregationService持久化 | MODEL | 无 | ✅ 已完成 |
| M-NC | NEEDS_CLARIFICATION澄清闭环 | MODEL | 无 | ✅ 已完成 |
| 16.3 | InterventionDecisionEngine精简 | DESKTOP | 无 | ✅ 已完成 |
| 16.4 | BrainBoundaryEnforcer补强 | DESKTOP | 无 | ✅ 已完成 |
| P33-A | Claude Code MCP Server配置 | CLAUDE_CODE | 无 | ✅ 已完成 |

**P0完成进度**: 21/21 (100%) ✅ 全部完成

### 2.2 P1 — 近期实施（提升自洽完整性）

| 编号 | 改进方案 | 所属文件 | 依赖关系 | 状态 |
|------|---------|---------|---------|------|
| P12-D | 启动失败自动恢复 | L1 | P12-A/B/C | ✅ 已完成 |
| P14-A | 声纹验证实现 | L1 | 无 | ✅ 已完成（B11: Python端/extract + Java端远程调用） |
| P14-C | 跨端权限实时同步 | L1 | P14-B | ✅ 已完成 |
| P14-D | 权限审计日志记录 | L1 | P14-B | ✅ 已完成 |
| P17-A | WebSocket连接状态监控增强 | L2 | 无 | ✅ 已完成（connectionStore.ts） |
| P17-B | API响应验证增强 | L2 | 无 | ✅ 已完成（apiBase.ts 重试机制） |
| P17-C | 前端状态同步机制增强 | L2 | P17-A | ✅ 已完成（ErrorReportFeedbackService→EvolutionSignal） |
| P17-D | 前端错误上报机制增强 | L2 | P17-A | ✅ 已完成（errorReporter.ts + ErrorReportController） |
| P22-A | CLI可用性检查增强 | L2 | 无 | ✅ 已完成（ClaudeCliHealthChecker + StartupDependencyChecker注册） |
| P22-B | 调用结果验证增强 | L2 | P22-A | ✅ 已完成（enrichResult 超时/空输出/返回码检测） |
| P22-C | 输出解析验证增强 | L2 | P22-A | ✅ 已完成（enrichResult stream-json解析验证+格式降级） |
| P27-A | 降级小流量回归+效果对比 | L3 | P24-A | ✅ 已完成 |
| P30-A | 违规→黑名单→边界收紧联动 | L3 | P31-A | ✅ 已完成 |
| P26-A | 知识消费效果反馈 | L3 | 无 | ✅ 已完成 |
| P25-A | 硬件升级ROI验证 | L3 | 无 | ✅ 已完成 |
| P33-B | CodeGraph+MemPalace MCP增强 | CLAUDE_CODE | P33-A | ✅ 已完成（插件安装+CLAUDE_PLUGINS_DIR；CodeGraph待Rust预编译；MemPalace已有@PreDestroy） |
| P33-C | Claude Code插件+技能对接 | CLAUDE_CODE | P33-A | ✅ 已完成（动态技能目录+服务发现提示词） |

### 2.3 P2 — 后续实施（细化验证/增强生命感）

> **注**: 子流程闭环编号已与 COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md v3.2 统一命名。

| 编号 | 改进方案 | 所属文件 | 依赖关系 | 状态 |
|------|---------|---------|---------|------|
| P18-A | autonomy_trace_events表写入验证 | L2 | 无 | ✅ 已完成（saveAndVerify + traceId唯一索引） |
| P18-B | evolution_feedback表写入验证 | L2 | 无 | ✅ 已完成（saveAndVerify） |
| P18-C | 其他关键表写入验证 | L2 | P18-A/B | ✅ 已完成（ApprovalInstance saveAndVerify + 状态流转验证） |
| P18-D | @Transactional回滚保护 | L2 | P18-A/B/C | ✅ 已完成（5个JPA服务添加@Transactional） |
| P3-A-A/B/C | WebSocket连接管理子闭环 | L2 | P17-A | ✅ 已完成（AgentWS连接数限制200+僵尸清理完整映射） |
| P3-B-A/B/C | 消息Trace子闭环 | L2 | P17-A | ✅ 已完成（AutonomyTraceService.saveAndVerify写入验证） |
| P11-A-A/B/C | 员工状态管理子闭环 | L2 | 无 | ✅ 已完成（saveAndVerify + 状态验证 + 变更通知） |
| P11-B-A/B/C | 通道监控子闭环 | L2 | 无 | ✅ 已完成（getHealthSummary + checkChannels增强） |
| P29-A | 满意度采集+行为验证 | L3 | 无 | ✅ 已完成 |
| P29-B | riskTolerance→边界联动 | L3 | P29-A | ✅ 已完成（BrainBoundaryEnforcer动态调整升级/降级策略） |
| P24-A | 进程级自愈闭环 | L3 | P24-A(SelfHealingOrchestrator) | ✅ 已完成（SelfHealingOrchestratorImpl处理PROCESS级问题+进程重启+经验沉淀） |
| P24-B | 连接级自愈闭环 | L3 | P24-A | ✅ 已完成（ConnectionHealthCheck+NOTIFY_CLIENT自愈） |
| P24-C | 业务级自愈闭环 | L3 | P24-A | ✅ 已完成（SelfHealingOrchestrator新增NOTIFY_CLIENT动作） |
| P28-A | 回执审核闭环 | L3 | 无 | ✅ 已完成（审核结果→分派权重联动） |
| P28-B | 回执反馈闭环 | L3 | P28-A | ✅ 已完成（SelfGovernanceOrchestrator.dispatchReceipt→PerformanceStatsService.adjustWeight+经验沉淀） |
| P31-A-A | 事件优先级仲裁闭环 | L3 | P31-A(SelfGovernanceOrchestrator) | ✅ 已完成（ArbitrationService优先级排序+事件分发） |
| P31-A-B | 协同执行闭环 | L3 | P31-A-A | ✅ 已完成（7个dispatch方法全部实际执行：自愈/WS恢复/降级/安全/回执/经济/知识/个性） |
| P31-A-C | 协同审计闭环 | L3 | P31-A-B | ✅ 已完成（治理审计记录持久化到autonomy_trace_events） |
| P32-A | 生命体征仪表盘 | L3 | P31-A | ✅ 已完成（VitalSignsService+VitalSignsController+预警推送） |
| P33-D | 服务对接增强（GitLab/Jenkins/OpenProject） | CLAUDE_CODE | P33-C | ✅ 已完成（GITLAB_URL/JENKINS_URL/OPENPROJECT_URL环境变量注入） |

**子流程闭环改进方案说明**：
- P24-A/B/C：自愈闭环的三个子流程闭环（进程级/连接级/业务级）
- P28-A/B：执行回执闭环的两个子流程闭环（审核/反馈）
- P31-A-A/B/C：跨闭环协同编排闭环的三个子流程闭环（仲裁/执行/审计）

---

## 三、实施路线图

### 3.1 Sprint 规划

```
Sprint 1: P0级基础设施
├─ Week 1: P12-A + P12-B + P12-C（服务启动闭环）+ P20-A/B/C（Python进程闭环）
├─ Week 2: P20-D + P19-A（推理验证 + JNI验证）+ P14-B（权限强制生效）
└─ Week 3: P19-B + P19-C（Native性能 + 模块健康）+ P24-A（自愈编排器）

Sprint 2: P0级协调器 + P1级启动
├─ Week 1: P31-A（协同编排器，L3协调层基础）
├─ Week 2: P28-A（回执反馈联动，快速见效）+ P12-D（启动自动恢复）
└─ Week 3: P17-A/B（前端WebSocket + API验证）

Sprint 3: P1级业务支撑
├─ Week 1: P17-C/D（前端状态同步 + 错误上报）
├─ Week 2: P22-A/B/C（Claude CLI闭环）+ P14-A/C/D（权限完整闭环）
└─ Week 3: P27-A + P30-A（降级+安全联动）

Sprint 4: P1级验证闭环
├─ Week 1: P26-A + P25-A（知识+经济验证）
├─ Week 2: P18-A/B/C（数据库表级闭环）
└─ Week 3: P3-A-A/B/C + P3-B-A/B/C（WebSocket子闭环）

Sprint 5: P2级生命感增强
├─ Week 1: P11-A-A/B/C + P11-B-A/B/C（员工/通道子闭环）
├─ Week 2: P29-A/B（满意度+边界联动）+ P24-B/C（进程/连接级自愈）
└─ Week 3: P32-A（生命体征仪表盘）
```

---

## 四、闭环验收清单（全量）

### 4.1 L1 闭环验收

| 序号 | 闭环名称 | 状态 | 验收方法 |
|------|----------|------|---------|
| 1 | WebSocket对话闭环 | ✅ | 发送WebSocket消息→检查响应→查询autonomy_trace_events表 |
| 2 | 审批流程闭环 | ✅ | POST /api/approvals创建→GET查询状态→POST approve执行 |
| 3 | 任务分配闭环 | ✅ | 创建任务→checkout领取→complete完成→查询tasks表 |
| 4 | 进化调整闭环 | ✅ | 触发模型降级→检查变更记录→验证模型替换效果 |
| 5 | 知识注入闭环 | ✅ | 添加文档→触发注入→查询knowledge_entries表→查询Qdrant |
| 6 | Windows自动化闭环 | ✅ | 发送"打开浏览器"→检查桌面→查询windows_automation_nodes表 |
| 7 | PostgreSQL数据一致性闭环 | ✅ | JPA CRUD操作→@Transactional事务验证 |
| 8 | Qdrant向量一致性闭环 | ✅ | 注入知识→查询Qdrant确认→删除知识→确认向量删除 |
| 9 | 多级缓存一致性闭环 | ✅ | 写入缓存→查询确认→更新数据→确认缓存更新（实际：DistributedCacheService Redis+内存降级 + SystemConfigService 内存+DB双写） |
| 10 | 跨系统数据同步闭环 | ✅ | 创建任务→查询Jira确认→更新本地→确认Jira同步 |
| 11 | 模型健康监控闭环 | ✅ | 模型调用→检查评分→阈值触发→模型降级→验证恢复 |
| 12 | 服务启动健康闭环 | ✅ | 启动→日志确认依赖检查→GET /api/health→降级模式验证→canary探测→小流量回归→全量恢复 ✅ |
| 13 | 主动预判健康闭环 | ✅ | 定时预判→风险报警→建议执行→效果记录 |
| 14 | 权限管理闭环 | ✅ | 声纹注册/验证→权限变更→PermissionChangeEvent→WebSocket断连→审计日志 ✅ |

### 4.2 L2 闭环验收

| 序号 | 闭环名称 | 状态 | 验收方法 |
|------|----------|------|---------|
| 17 | 前端与后端交互闭环 | ✅ | 连接WebSocket→查询状态→心跳→权限同步→错误上报→ErrorReportFeedbackService→EvolutionSignal ✅ |
| 18-表级 | 数据库持久化闭环 | ✅ | 创建Trace→查询确认→重复ID→事务回滚（@Transactional+saveAndVerify已添加） ✅ |
| 19 | Native Rust与Java交互闭环 | ✅ | JNI调用→结果验证→性能监控→模块健康检查 ✅ 全链路打通 |
| 20 | Python模型守护进程闭环 | ✅ | 进程存活→模型加载→NamedPipe→推理结果→ModelDaemonHealthCheck→SelfHealingOrchestrator ✅ |
| 22 | Claude CLI代理闭环 | ✅ | CLI可用性→调用验证→输出解析→enrichResult→ClaudeCliHealthChecker ✅ |
| 3-A | WebSocket连接管理闭环 | ✅ | 连接数限制→心跳→僵尸清理→ConnectionHealthCheck→NOTIFY_CLIENT自愈 ✅ |
| 3-B | 消息Trace闭环 | ✅ | Trace完整→查询验证→AutonomyTraceService→saveAndVerify→改进决策 ✅ |
| 33 | 数字员工使用Claude CLI工具闭环 | ✅ | MCP Server连接→插件加载→技能目录发现→GitLabTool/JenkinsTool/OpenProjectTool API调用→ToolResult返回 ✅ 全链路打通 |
| 11-A | 员工状态管理闭环 | ✅ | 状态变更→查询确认→EmployeeStatusRecoveryMonitor自动恢复 ✅ |
| 11-B | 通道监控闭环 | ✅ | 通道状态→健康检查→ChannelSelfHealingMonitor自动愈合 ✅ |

### 4.3 L3 闭环验收

| 序号 | 闭环名称 | 状态 | 验收方法 |
|------|----------|------|---------|
| 24 | 自愈闭环 | ✅ | 模拟崩溃→自动修复→**经验沉淀写入KnowledgeBase**→查询验证 ✅ |
| 25 | 经济自治闭环 | ✅ | 模拟升级→7天对比ROI→亏损时**自动回滚**→验证恢复 ✅ |
| 26 | 知识自进化闭环 | ✅ | 知识消费→效果反馈→confidence调整→晋升/降级 ✅ |
| 27 | 降级链路闭环 | ✅ | LLM恢复→PROBING→10%回归→**定量指标判断**→全量切换 ✅ |
| 28 | 执行回执闭环 | ✅ | 回执failed→权重降→dispatcher不再分派→经验沉淀 ✅ |
| 29 | 大脑个性进化闭环 | ✅ | 个性变异→**满意度对比→回滚机制**→边界联动 ✅ |
| 30 | 沙箱安全闭环 | ✅ | 违规3次→黑名单+边界收紧→TTL到期恢复 ✅ |
| 31 | 跨闭环协同编排闭环 | ✅ | 多事件并发→优先级仲裁→**真正执行→审计持久化** ✅ |
| 32 | 生命体征仪表盘闭环 | ✅ | GET /api/vitals→评分→**预警主动推送→驱动改进闭环** ✅ |
| 34 | 数字员工协作闭环 | ✅ | 多员工协作→createSession→任务分配→执行→绩效评估→策略优化 ✅ |
| 35 | 动态员工创建闭环 | ✅ | 工作负载评估→能力缺口识别→MainBrain推理设计→创建→验证→策略改进 ✅ |
| 36 | 神经元会话协调闭环 | ✅ | 会话创建→四通道创建→神经元绑定→输入分发→响应聚合→资源清理 ✅ |
| 37 | 员工智能调度闭环 | ✅ | 任务分析→LLM推理→能力匹配→负载均衡→调度决策→执行监控→绩效反馈 ✅ |

**L3 整体验收**：✅ 所有13个L3闭环已完成改进，系统已能在无人工干预下完成 `自感知→自决策→自执行→自验证→自进化` 全链路，同时兼容人工介入点。

### 4.4 闭环34-37子流程闭环拆分

> 来源：[IMPROVEMENT_PLAN_INDEX_SUPPLEMENT_LOOPS_34_37.md](IMPROVEMENT_PLAN_INDEX_SUPPLEMENT_LOOPS_34_37.md)

#### 闭环34-数字员工协作闭环 子流程拆分

| 编号 | 子流程闭环名称 | 核心价值 | 自洽层级 | 完整性 |
|------|--------------|---------|---------|--------|
| 34-A | TASK_CHAIN协作闭环 | 任务链式协作→依赖解析→串行执行→链式绩效 | 自感知→自协调 | ✅ 完整 |
| 34-B | PARALLEL协作闭环 | 并行任务分片→并发调度→结果聚合→并行效率 | 自感知→自协调 | ✅ 完整 |
| 34-C | PEER_REVIEW协作闭环 | 开发→提交→审查→反馈→通过的完整流程 | 自感知→自协调 | ✅ 完整 |
| 34-D | 协作推荐闭环 | 任务分析→能力匹配→协作推荐→推荐验证 | 自分析→自推荐 | ✅ 完整 |

**34-C PEER_REVIEW状态机**:
```
DEVELOPER_WRITING → CODE_SUBMITTED → REVIEWER_REVIEWING →
  ├─ REVIEW_APPROVED → REVIEW_COMPLETED
  └─ REVIEW_CHANGES_REQUESTED → DEVELOPER_FIXING → CODE_RESUBMITTED → REVIEWER_REVIEWING (循环)
```

#### 闭环35-动态员工创建闭环 子流程拆分

| 编号 | 子流程闭环名称 | 核心价值 | 自洽层级 | 完整性 |
|------|--------------|---------|---------|--------|
| 35-A | 能力缺口识别闭环 | 工作负载分析→能力缺口识别→创建需求判断 | 自感知→自分析 | ✅ 完整 |
| 35-B | 员工设计闭环 | LLM推理→能力设计→技能设计→工具设计 | 自决策→自设计 | ✅ 完整 |
| 35-C | 员工创建验证闭环 | ID生成→员工创建→神经元绑定→能力验证 | 自执行→自验证 | ✅ 完整 |
| 35-D | 去重检测闭环 | 提案分析→现有员工比对→能力重叠检测→去重决策 | 自分析→自决策 | ✅ 完整 |

#### 闭环36-神经元会话协调闭环 子流程拆分

| 编号 | 子流程闭环名称 | 核心价值 | 自洽层级 | 完整性 |
|------|--------------|---------|---------|--------|
| 36-A | 会话创建闭环 | 会话请求→四种通道创建→状态初始化 | 自感知→自组织 | ✅ 完整 |
| 36-B | 神经元绑定闭环 | 绑定请求→订阅感知→发布响应→绑定确认 | 自感知→自绑定 | ✅ 完整 |
| 36-C | 输入分发闭环 | 用户输入→感知通道广播→神经元接收 | 自感知→自分发 | ✅ 完整 |
| 36-D | 响应聚合闭环 | 神经元响应→响应通道订阅→消息聚合→上下文更新 | 自感知→自聚合 | ✅ 完整 |
| 36-E | 资源清理闭环 | 会话结束→通道销毁→状态清理→内存释放 | 自感知→自清理 | ✅ 完整 |

#### 闭环37-员工智能调度闭环 子流程拆分

| 编号 | 子流程闭环名称 | 核心价值 | 自洽层级 | 完整性 |
|------|--------------|---------|---------|--------|
| 37-A | LLM调度决策闭环 | 任务分析→LLM推理→调度决策→assignments生成 | 自分析→自决策 | ✅ 完整 |
| 37-B | Fallback降级闭环 | LLM失败→规则降级→fallback调度→结果验证 | 自适应→自恢复 | ✅ 完整 |
| 37-C | 能力匹配闭环 | 任务能力需求→员工能力比对→匹配分数计算→最优选择 | 自分析→自匹配 | ✅ 完整 |
| 37-D | 负载均衡闭环 | 员工负载查询→负载对比→负载均衡决策→分配调整 | 自感知→自均衡 | ✅ 完整 |

**子流程闭环统计**：闭环34-37共包含17个子流程闭环（4+4+5+4），全部为完整闭环。

### 4.5 闭环34-37五阶段验证

#### 闭环34五阶段验证

| 阶段 | 入口条件 | 核心处理 | 出口条件 | 闭环检查 |
|------|---------|---------|---------|---------|
| **输入** | 多员工协作需求识别 | 协作类型选择+参与者识别 | CollaborationRequest构建 | 需求已明确 |
| **处理** | CollaborationRequest提交 | createSession→任务分配→startSession | CollaborationSession创建 | 会话已建立 |
| **输出** | 会话启动 | 任务执行→进度追踪→completeTask | CollaborationResult生成 | 结果已产出 |
| **反馈** | 结果提交 | 绩效评估→贡献度计算→participantScores | 绩效数据记录 | 绩效已评估 |
| **改进** | 绩效分析 | 协作策略优化→推荐算法调整→经验沉淀 | 推荐算法更新 | 策略已改进 |

#### 闭环35五阶段验证

| 阶段 | 入口条件 | 核心处理 | 出口条件 | 闭环检查 |
|------|---------|---------|---------|---------|
| **输入** | 工作负载评估触发 | department+taskDescription+workloadContext | 评估请求已构建 | 触发条件已明确 |
| **处理** | evaluateCreationNeed调用 | MainBrain推理→能力缺口识别→员工设计 | EmployeeCreationProposal生成 | 提案已设计 |
| **输出** | createFromProposal调用 | ID生成→员工创建→神经元绑定→通道订阅 | Employee实体创建 | 员工已创建 |
| **反馈** | 员工投入使用 | 任务分配→执行验证→绩效评估→能力验证 | 绩效数据记录 | 能力已验证 |
| **改进** | 绩效分析 | 员工能力调整→技能补充→工具扩展→创建策略优化 | 创建策略更新 | 策略已改进 |

#### 闭环36五阶段验证

| 阶段 | 入口条件 | 核心处理 | 出口条件 | 闭环检查 |
|------|---------|---------|---------|---------|
| **输入** | 会话创建请求 | createSession→四种通道创建 | SessionState初始化 | 会话已创建 |
| **处理** | 神经元绑定请求 | bindNeuronToSession→订阅感知+发布响应 | 神经元已绑定 | 绑定已完成 |
| **输出** | 用户输入提交 | publishUserInput→感知通道广播→神经元接收 | 神经元响应生成 | 响应已产出 |
| **反馈** | 响应聚合 | 响应通道订阅→消息收集→上下文更新 | 上下文已更新 | 聚合已完成 |
| **改进** | 资源清理 | destroySession→通道销毁→状态清理→内存释放 | 资源已清理 | 清理已完成 |

#### 闭环37五阶段验证

| 阶段 | 入口条件 | 核心处理 | 出口条件 | 闭环检查 |
|------|---------|---------|---------|---------|
| **输入** | 任务计划提交 | mainBrainTaskPlan+departmentTaskPlan | 调度请求已构建 | 任务已明确 |
| **处理** | planAssignments调用 | MainBrain推理→能力匹配→负载均衡 | assignments列表生成 | 调度已决策 |
| **输出** | 员工分配执行 | 任务派发→执行监控→进度追踪 | ExecutionReceipt生成 | 执行已完成 |
| **反馈** | 执行结果提交 | 绩效评估→负载更新→能力验证 | 绩效数据记录 | 绩效已评估 |
| **改进** | 绩效分析 | 调度策略优化→匹配算法调整→fallback策略更新 | 调度策略更新 | 策略已改进 |

### 4.6 闭环34-37与现有闭环关系

| 新闭环 | 关系类型 | 关联闭环 | 关系说明 |
|--------|---------|---------|---------|
| 闭环37 | 子流程 | 闭环1(WebSocket对话闭环) | 闭环37是闭环1的内部子流程：MainBrain→LLM调度→员工分配 |
| 闭环34 | 扩展 | 闭环11-A(员工任务处理闭环) | 闭环11-A处理单员工任务，闭环34扩展到多员工协作场景 |
| 闭环35 | 姊妹闭环 | 闭环26(知识自进化闭环) | 闭环26=知识能力进化，闭环35=员工能力扩展（姊妹关系） |

### 4.7 闭环34-37核心实现文件索引

| 闭环 | 文件路径 | 核心功能 |
|------|---------|---------|
| 34 | CollaborationServiceImpl.java | 协作服务实现（7种协作类型，PEER_REVIEW自动推进） |
| 34 | CollaborationSession.java | 协作会话模型 |
| 34 | CollaborationService.java | 协作服务接口定义 |
| 35 | LLMEmployeeCreationServiceImpl.java | LLM驱动的员工设计实现 |
| 35 | LLMEmployeeCreationService.java | 员工创建服务接口 |
| 36 | NeuronCoordinator.java | 神经元会话协调（四通道架构） |
| 37 | LlmBasedFixedEmployeeDispatcher.java | LLM调度+Fallback降级 |

## 五、三大系统性缺口（全部已填补）

| 缺口 | 描述 | 影响闭环 | 关键改进方案 | 当前状态 |
|------|------|---------|-------------|---------|
| 缺口一：闭环编排器缺失 | 各闭环独立运行，无统一串联 | 24(缺SelfHealingOrchestrator), 31(缺SelfGovernanceOrchestrator) | P24-A, P31-A | ✅ 已填补：P24-A✅/P31-A✅ + 经验沉淀/审计持久化完成 |
| 缺口二：反馈回路断裂 | 执行结果未回流到上游决策 | 17/22/29 feedback→improvement断裂 | P17-C, P22-D, P29-A | ✅ 已填补：P17-C✅/P22-C✅/P29-A✅全部完成 |
| 缺口三：效果验证环节空白 | 升级/降级/变异后无效果对比 | 25/27/29 缺ROI对比/效果对比/满意度对比 | P25-A, P27-A, P29-A | ✅ 已填补：P25-A✅(自动回滚)/P27-A✅(定量指标)/P29-A✅(满意度+回滚) |

> **2026-07-03 第五轮断裂修复+闭环34-37补充结论**：三大系统性缺口已全部填补，37个闭环全部升级为完整闭环（含17个子流程闭环）。

---

## 六、进度跟踪

> 以下表格用于跟踪各改进方案的实施进度，实施时请更新状态列。

### 6.1 状态标记说明

| 标记 | 含义 |
|------|------|
| ⬜ 待实施 | 尚未开始 |
| 🔄 进行中 | 正在实施 |
| ✅ 已完成 | 实施完成并验证 |
| ⏸️ 暂停 | 因依赖或优先级调整暂停 |
| ❌ 取消 | 不再实施 |

### 6.2 进度总览

> 注：下表统计 IMPROVEMENT_PLAN 体系内新增改进方案 + DESKTOP/MODEL文档提取的P0项进度 + v1.3新增子流程闭环。

| Sprint | 改进方案数 | ⬜待实施 | 🔄部分实施 | ✅已完成 | 完成率 |
|--------|----------|--------|----------|--------|-------|
| Sprint 1 | 10 | 0 | 0 | 10 | 100% |
| Sprint 2 | 6 | 0 | 0 | 6 | 100% |
| Sprint 3 | 7 | 0 | 0 | 7 | 100% |
| Sprint 4 | 5 | 0 | 0 | 5 | 100% |
| Sprint 5 | 11 | 0 | 0 | 11 | 100% |
| **合计** | **39** | **0** | **0** | **39** | **100%** |

**整体落地情况**（含DESKTOP/MODEL文档改进项 + 子流程闭环）：
- DESKTOP文档断点B1/B2/B3/B4/B5/B6/B11/B12：8/8已修复 ✅
- MODEL文档阶段1-7+迭代1-6+第11章：全部已落地
- IMPROVEMENT_PLAN体系新增方案：39/39已完成，0/39待实施
- 子流程闭环改进方案：6项（P24-A/B/C + P28-B + P31-A-A/B/C），全部已完成 ✅
- P0项完成进度：21/21 (100%) ✅ 全部完成

**已完成改进项清单**（2026-07-03 更新）：
| 编号 | 改进方案 | 完成日期 | 代码位置 |
|------|---------|---------|---------|
| P12-A | 启动依赖检查增强 | 2026-07-02 | StartupDependencyChecker.java |
| P12-B | 健康检查端点增强 | 2026-07-02 | HealthController.java |
| P12-C | 启动顺序强制控制 | 2026-07-02 | AppModeUtil.java + HealthMonitorImpl.java |
| P14-B | 权限变更强制实时生效 | 2026-07-02 | PermissionChangeEvent.java + DepartmentWebSocketHandler.java |
| P28-A | 审核结果→分派权重联动 | 2026-07-02 | PerformanceStatsService.java |
| M-IR | InternalReviewService持久化 | 2026-07-02 | JpaInternalReviewService.java |
| M-DA | DepartmentAggregationService持久化 | 2026-07-02 | JpaDepartmentAggregationService.java |
| M-NC | NEEDS_CLARIFICATION澄清闭环 | 2026-07-02 | DepartmentChatService.java |
| 16.3 | InterventionDecisionEngine精简 | 2026-07-02 | InterventionDecisionEngineImpl.java + InterventionProperties.java |
| 16.4 | BrainBoundaryEnforcer补强 | 2026-07-02 | BrainBoundaryEnforcer.java + BrainBoundaryProperties.java |
| B11 | 声纹embedding真实实现 | 2026-07-02 | VoicePrintServiceImpl.java + SpeakerVerificationService.java |
| P19-A | JNI调用结果验证增强 | 2026-07-02 | NativeServiceWrapper.java + NativeException.java |
| P20-A | 进程存活监控增强 | 2026-07-02 | ModelDaemonProcessMonitor.java + ModelDaemonRecoveryService.java |
| P24-A | SelfHealingOrchestrator | 2026-07-02 | SelfHealingOrchestratorImpl.java |
| P31-A | SelfGovernanceOrchestrator | 2026-07-02 | SelfGovernanceOrchestratorImpl.java |
| P19-B | Native执行性能监控增强 | 2026-07-03 | NativeCallMetrics.java + NativePerformanceMonitor.java |
| P19-C | Native模块健康检查增强 | 2026-07-03 | NativeLibraryHealthCheck.java |
| P20-B | 模型加载验证增强 | 2026-07-03 | ModelLoadHealthCheck.java |
| P20-C | NamedPipe健康检查增强 | 2026-07-03 | NamedPipeModelClient.java（熔断器） |
| P20-D | 推理结果验证增强 | 2026-07-03 | InferenceResultValidator.java + QwenProvider/BitNetProvider |
| P12-D | 启动失败自动恢复 | 2026-07-03 | StartupRecoveryService.java |
| P14-C | 跨端权限实时同步 | 2026-07-03 | AgentWebSocketHandler.java |
| P14-D | 权限审计日志记录 | 2026-07-03 | PermissionServiceImpl.java |
| P25-A | 硬件升级ROI验证 | 2026-07-03 | HardwareUpgradeRoiValidator.java |
| P26-A | 知识消费效果反馈 | 2026-07-03 | KnowledgeConsumptionFeedback.java |
| P27-A | 降级小流量回归 | 2026-07-03 | DegradedTrafficCanary.java + StartupRecoveryService.java |
| P30-A | 违规→黑名单→边界收紧联动 | 2026-07-03 | SandboxViolationTracker.java + BrainBoundaryEnforcer.java |
| P33-A | Claude Code MCP Server配置 | 2026-07-03 | mcp.json + ClaudeCliProperties + ClaudeExecutionGateway |
| P33-B | CodeGraph+MemPalace MCP增强 | 2026-07-03 | 4个插件 + CLAUDE_PLUGINS_DIR |
| P33-C | Claude Code插件+技能对接 | 2026-07-03 | ClaudeExecutionGateway(动态技能目录+服务发现) |
| P33-D | 服务对接增强 | 2026-07-03 | ClaudeExecutionGateway(GITLAB_URL/JENKINS_URL/OPENPROJECT_URL) |
| P17-A | WebSocket连接状态监控增强 | 2026-07-03 | connectionStore.ts |
| P17-B | API响应验证增强 | 2026-07-03 | apiBase.ts(重试机制) |
| P17-D | 前端错误上报机制增强 | 2026-07-03 | errorReporter.ts + ErrorReportController.java |
| P22-A | CLI可用性检查增强 | 2026-07-03 | ClaudeCliHealthChecker.java + StartupDependencyChecker |
| P22-B | 调用结果验证增强 | 2026-07-03 | ClaudeExecutionGateway.enrichResult(超时/空输出) |
| P22-C | 输出解析验证增强 | 2026-07-03 | ClaudeExecutionGateway.enrichResult(解析验证) |
| P18-A | autonomy_trace_events表写入验证 | 2026-07-03 | TraceEventRepository.saveAndVerify + traceId唯一索引 |
| P18-B | evolution_feedback表写入验证 | 2026-07-03 | EvolutionFeedbackRepository.saveAndVerify |
| P18-C | 其他关键表写入验证 | 2026-07-03 | ApprovalInstanceRepository.saveAndVerify + 状态验证 |
| P3-A | WebSocket连接管理闭环 | 2026-07-03 | AgentWebSocketHandler(连接数限制+僵尸清理) |
| P3-B | 消息Trace闭环 | 2026-07-03 | AutonomyTraceService.saveAndVerify |
| P11-A | 员工状态管理闭环 | 2026-07-03 | EnterpriseEmployeeRepository.saveAndVerify + 状态验证 |
| P11-B | 通道监控闭环 | 2026-07-03 | ChannelManager.getHealthSummary + HealthMonitorImpl.checkChannels |
| 闭环12修复 | StartupRecoveryService canary修复 | 2026-07-03 | StartupRecoveryService(canary探测+recordProbeSuccess/Failure) |
| 闭环20修复 | HealthIssue.type推断+null安全 | 2026-07-03 | HealthMonitorImpl.inferIssueType + SelfHealingOrchestratorImpl null安全 |
| P33-CodeGraph | CodeGraph MCP Server集成 | 2026-07-03 | Dockerfile.local+ClaudeCliProperties+application.yml+mcp.json+ClaudeExecutionGateway |
| P33-API | 服务API调用闭环完成 | 2026-07-03 | GitLabTool+JenkinsTool+OpenProjectTool(实际API调用)+ClaudeCliProperties.getProxy修复 |
| P29-B | riskTolerance→边界联动 | 2026-07-03 | BrainBoundaryEnforcer.java(BrainRegistry注入+riskTolerance动态调整) |
| P24-C+P3-A | 连接级自愈+WebSocket健康检查 | 2026-07-03 | ConnectionHealthCheck.java+SelfHealingOrchestratorImpl(NOTIFY_CLIENT) |
| P32-A | 生命体征仪表盘 | 2026-07-03 | VitalSignsService.java+VitalSignsController.java+GatewayConfig Bean注册 |
| P18-D | @Transactional回滚保护 | 2026-07-03 | 5个JPA服务添加@Transactional注解 |
| B-2-2 | TaskRepository.findByExecutionId返回类型 | 2026-07-03 | Optional→List，8处调用方更新 |
| B-2-6 | PerformanceDashboardService解耦 | 2026-07-03 | 接口新增getCompanyTopPerformers/getCompanyBottomPerformers，移除instanceof |
| P-CC-1 | FileEditTool行级编辑+正则搜索 | 2026-07-06 | edit_file操作(old_string→new_string)+searchCode正则支持 |
| P-CC-2 | PatchApplicationService diff真正应用 | 2026-07-06 | applyPatch()真正应用JSON/unified diff+rollbackPatch()恢复原始内容 |
| P-CC-3 | BrainReActEngine编译-修复自闭环 | 2026-07-06 | executeCompileFixLoop()+TechBrain自动启用(当ClaudeCliTool不可用时) |

---

## 七、文档维护说明

**配套文档关系**：
- COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md — 37个闭环需求定义 + 8+17个子流程闭环（权威源）
- COMPLETE_FLOW_DOCUMENTATION.md — 流程文档与闭环定义（第25章）
- CODE_STRUCTURE_AND_FILE_GUIDE.md — 实际代码结构（权威源）
- MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md — 模型与执行优化
- DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md — 桌面端对接审计
- CLAUDE_CODE_INTEGRATION_IMPROVEMENT_PLAN.md — Claude Code 完整集成改进方案（闭环33）

**使用建议**：
1. 实施改进前，先查阅 CODE_STRUCTURE_AND_FILE_GUIDE.md 确认复用组件的精确路径
2. 新增文件遵循项目规则：autonomy vs autonomous 区分、ID命名规范、API响应格式统一
3. 数据库变更直接修改 schema.sql + init-db/01_init.sql，不再创建 Flyway V 版本迁移文件
4. 实施完成后同步更新 CODE_STRUCTURE_AND_FILE_GUIDE.md 对应小节
5. 进度更新只需修改本文件的"进度跟踪"部分
6. 子流程闭环改进方案（P24-A/B/C + P28-A/B + P31-A-A/B/C）已与 COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md v3.2 统一命名
7. Claude Code 集成方案（P33-A/B/C/D）详见 CLAUDE_CODE_INTEGRATION_IMPROVEMENT_PLAN.md

**版本信息**：
- 文档版本: v2.0
- 生成日期: 2026-07-02
- 最近更新: 2026-07-13（第三阶段闭环验证修复：43/50-59升级✅，60⚠️，61-62❌(部分实现)，L4从12✅+11⚠️+3❌→23✅+1⚠️+2❌）
- 借鉴来源: [Living Agent vs Claude Code 架构对比分析](Living_Agent_vs_Claude_Code_Architecture_Analysis.md)
- 总闭环数: 64个（14个L1 + 9个L2 + 1个L2业务流程 + 14个L3 + 26个L4）+ 25个子流程闭环（8原有+17新增）
- 闭环覆盖: 57完整闭环 + 5部分闭环 + 2断裂闭环（89%完整率）
- L1-L3改进方案: 43个（P0×21✅ + P1×22✅ + P2×3✅）
- L4改进方案: 43个（P0×12✅ + P1×16✅ + P2×14✅）
- 子流程闭环改进方案: P24-A/B/C + P28-B + P31-A-A/B/C（共6项）+ 闭环34-37（17个子流程闭环）
- Claude Code集成方案: P33-A/B/C/D（共4项，全部完成）
- P0完成进度(L1-L3): 21/21 (100%) | P0完成进度(L4): 12/12 (100%)
- P1完成进度(L1-L3): 22/22 (100%) | P1完成进度(L4): 16/16 (100%)
- P2完成进度(L1-L3): 3/21 (14%) | P2完成进度(L4): 14/14 (100%)
