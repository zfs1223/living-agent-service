# Living Agent Service - L3 生命体自洽闭环改进方案

> **生成日期**: 2026-07-02
> **层级**: L3 生命体自洽（闭环24-32）
> **索引**: [IMPROVEMENT_PLAN_INDEX.md](IMPROVEMENT_PLAN_INDEX.md)
> **配套需求**: COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md 第八章
> **L3内部分层**: 感知层(32) + 协调层(31) + 执行层(24-30)

---

## 一、覆盖情况总览

### 1.1 L3自洽闭环清单

| 闭环编号 | 闭环名称 | 自洽层级 | L3内部分层 | 覆盖状态 | 改进方案 | 断裂环节 |
|---------|---------|---------|-----------|---------|---------|---------|
| 24 | 自愈闭环 | 自感知→自修复 | 执行层 | ✅ 完整闭环 | P24-A✅/B✅/C✅ | — |
| 25 | 经济自治闭环 | 自决策→自运营 | 执行层 | ✅ 完整闭环 | P25-A✅ | — |
| 26 | 知识自进化闭环 | 自学习→自成长 | 执行层 | ✅ 完整闭环 | P26-A✅ | — |
| 27 | 降级链路闭环 | 自适应→自恢复 | 执行层 | ✅ 完整闭环 | P27-A✅/B✅ | 定量指标（响应时间≤5s/成功率≥80%）已补充 |
| 28 | 执行回执闭环 | 自验证→自优化 | 执行层 | ✅ 完整闭环 | P28-A✅ | — |
| 29 | 大脑个性进化闭环 | 自进化→自成长 | 执行层 | ✅ 完整闭环 | P29-A✅/B✅ | 变异前后对比+回滚机制已补充 |
| 30 | 沙箱安全闭环 | 自保护→自约束 | 执行层 | ✅ 完整闭环 | P30-A✅ | — |
| 31 | 跨闭环协同编排闭环 | 自感知→自协调 | 协调层 | ✅ 完整闭环 | P31-A✅ | 真正执行+审计持久化已补充 |
| 32 | 生命体征仪表盘闭环 | 自感知→自暴露 | 感知层 | ✅ 完整闭环 | P32-A✅ | 预警主动推送+驱动改进闭环已补充 |

### 1.2 覆盖情况统计

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                    L3 自洽闭环覆盖情况统计（2026-07-03 第五轮断裂修复完成）                           │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  ✅ 完整闭环（全链路打通）: 9个执行层闭环                                                          │
│  ├─ 闭环24 自愈：异常检测→修复→验证→经验沉淀写入KnowledgeBase ✅                                    │
│  ├─ 闭环25 经济自治：ROI亏损<-50%→自动回滚 ✅                                                    │
│  ├─ 闭环26 知识自进化：recordFeedback→confidence升降→晋升/降级 ✅                               │
│  ├─ 闭环27 降级链路：Canary探测→定量指标判断（响应时间≤5s/成功率≥80%）→全量提升 ✅               │
│  ├─ 闭环28 执行回执：审核→adjustWeight→Dispatcher prompt注入 ✅                                 │
│  ├─ 闭环29 大脑个性：满意度采集→变异前后对比→回滚机制 ✅                                         │
│  ├─ 闭环30 沙箱安全：checkAction→违规记录→黑名单→边界收紧→TTL恢复 ✅                             │
│  ├─ 闭环31 跨闭环协同：分发真正执行→审计持久化 ✅                                                 │
│  └─ 闭环32 生命体征：预警主动推送→驱动改进闭环 ✅                                                 │
│                                                                                              │
│  ⚠️ 部分闭环: 0个                                                                                │
│                                                                                              │
│  ❌ 未闭环: 0个                                                                                │
│                                                                                              │
│  代码落地度: 100%（所有 L3 闭环断裂环节已修复）        │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 L3自洽闭环实现缺口对照表（2026-07-03 第五轮断裂修复完成）

| 闭环 | 已实现要素 | 闭环验证结论 | 断裂根因 |
|------|-----------|-------------|---------|
| 24 自愈 | SelfHealingOrchestratorImpl ✅ + HealthMonitorImpl + captureHealingExperience ✅ | ✅ 完整闭环 | — |
| 25 经济 | HardwareUpgradeRoiValidator ✅ + executeRollback ✅ + LedgerService + EvolutionCircuitBreaker | ✅ 完整闭环 | — |
| 26 知识 | KnowledgeConsumptionFeedback ✅ + KnowledgePromotionScheduler + LayeredKnowledgeBase | ✅ 完整闭环 | — |
| 27 降级 | DegradedTrafficCanary ✅ + 定量指标阈值（MAX_RESPONSE_TIME_MS/MIN_SUCCESS_RATE）✅ + recordProbeWithMetrics ✅ | ✅ 完整闭环 | — |
| 28 回执 | PerformanceStatsService.adjustWeight ✅ + LlmExecutionReceiptReviewer + LlmBasedFixedEmployeeDispatcher | ✅ 完整闭环 | — |
| 29 个性 | SatisfactionCollector ✅ + startMutationEvaluation/evaluateMutation ✅ + rollbackMutation ✅ | ✅ 完整闭环 | — |
| 30 沙箱 | SandboxViolationTracker ✅ + BrainBoundaryEnforcer联动 ✅ + BashSecurityValidator | ✅ 完整闭环 | — |
| 31 协同 | SelfGovernanceOrchestratorImpl ✅ + dispatchSelfHealing/dispatchDegradation真正执行 ✅ + persistAudit ✅ | ✅ 完整闭环 | — |
| 32 体征 | VitalSignsService ✅ + checkAndPushAlerts周期预警 ✅ + pushAlertAndTriggerImprovement驱动改进 ✅ | ✅ 完整闭环 | — |

---

## 二、P0 改进方案（立即实施，解锁其他闭环的前提）— 已全部完成

> **2026-07-03 状态更新**：P0三项已全部完成并代码验证。但闭环24和31仍为部分闭环，原因见1.3缺口对照表。

### 2.1 P24-A：自愈编排器（SelfHealingOrchestrator）✅ 已完成

> **代码核验**：SelfHealingOrchestratorImpl.java 已创建，实现了六步编排。但经验沉淀环节仅重置计数器，未真正写入KnowledgeBase。

**问题分析**：闭环24 的六步（异常检测→根因分析→补丁生成→应用/回滚→效果验证→经验沉淀）由 HealthMonitorImpl、ErrorCodeMapper、PatchProposalService、PatchApplicationService 等独立服务承担，但缺少编排器串联，目前只能人工驱动。所有 L3 闭环都依赖自愈能力作为异常恢复基础。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P24-A：自愈编排器 SelfHealingOrchestrator                                                          │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  新增文件：                                                                                   │
│  ├─ core/evolution/orchestrator/SelfHealingOrchestrator.java                                 │
│  │    接口：orchestrate(HealthIssue issue) → SelfHealingResult                                │
│  │    职责：串联六步，按 confidence 阈值决定自动应用或人工审批                                  │
│  ├─ core/evolution/orchestrator/SelfHealingResult.java                                       │
│  │    字段：success/rootCause/patchApplied/verified/experienceCaptured/needsHumanApproval    │
│  └─ core/evolution/orchestrator/impl/SelfHealingOrchestratorImpl.java                        │
│                                                                                              │
│  实现要点：                                                                                   │
│  1. 订阅 HealthMonitorImpl 的 HealthIssue 事件                                                │
│  2. 调用 ErrorCodeMapper 进行根因分析 → 输出 CodeContext                                       │
│  3. 调用 PatchProposalService 创建补丁提案（含 confidence/rollbackAvailable）                  │
│  4. 判断 confidence：                                                                          │
│     ├─ ≥ 阈值（如 0.8）：调用 PatchApplicationService 应用补丁                                │
│     └─ < 阈值：触发 InterventionNeuron 请求人工审批                                            │
│  5. 效果验证：重新调用 HealthMonitorImpl 检查 + EvolutionCircuitBreaker 确认恢复               │
│  6. 经验沉淀：调用 KnowledgeCaptureService 将修复过程写入 KnowledgeBase（L1→L2→L3 晋升）       │
│  7. 失败回滚：PatchApplicationService.rollback() + 记录失败原因                                │
│                                                                                              │
│  复用已有组件：                                                                               │
│  ├─ HealthMonitorImpl（异常检测）                                                             │
│  ├─ ErrorCodeMapper（根因分析）                                                               │
│  ├─ PatchProposalService（补丁生成）                                                          │
│  ├─ PatchApplicationService（应用/回滚）                                                       │
│  ├─ EvolutionCircuitBreaker（熔断/冷却）                                                       │
│  ├─ InterventionNeuron（人工审批）                                                             │
│  └─ KnowledgeCaptureService（经验沉淀）                                                       │
│                                                                                              │
│  配置项（application.yml）：                                                                  │
│  evolution:                                                                                   │
│    self-healing:                                                                              │
│      enabled: true                                                                            │
│      confidence-threshold: 0.8                                                                 │
│      cooldown-seconds: 300                                                                    │
│      max-retry: 3                                                                              │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 模拟 model_daemon.py 崩溃 → HealthMonitor 检测 → SelfHealingOrchestrator 自动重启 → 验证恢复
2. 模拟低 confidence 补丁 → 确认触发 InterventionNeuron 而非自动应用
3. 自愈完成后查询 KnowledgeBase → 确认修复经验已沉淀

---

### 2.2 P31-A：跨闭环协同编排器（SelfGovernanceOrchestrator）✅ 已完成

> **代码核验**：SelfGovernanceOrchestratorImpl.java 已创建，实现了7级优先级仲裁+冷却期去重+CrossLoopEventBus。但分发后多数handler仅log未真正执行，审计无持久化。

**问题分析**：闭环24-30 各自独立运行，无统一协调入口。并发触发时可能震荡（自愈修复→引发降级→触发个性变异→...）。闭环间无优先级仲裁，安全/经济/进化混在一起。闭环31 是 L3 协调层，缺失会导致整个 L3 层无法协同。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P31-A：跨闭环协同编排器 SelfGovernanceOrchestrator                                                │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  新增文件：                                                                                   │
│  ├─ core/evolution/orchestrator/SelfGovernanceOrchestrator.java                              │
│  │    接口：submitEvent(CrossLoopEvent) / orchestrate() / getStatus()                         │
│  ├─ core/evolution/orchestrator/CrossLoopEventBus.java                                       │
│  │    职责：发布/订阅 L3 闭环事件，支持去重和冷却期                                            │
│  ├─ core/evolution/orchestrator/LoopPriorityArbiter.java                                     │
│  │    职责：优先级仲裁（安全30>自愈24>降级27>回执28>经济25>知识26>个性29）                       │
│  ├─ core/evolution/orchestrator/CrossLoopAggregator.java                                     │
│  │    职责：聚合一次协同周期内所有闭环执行结果                                                  │
│  ├─ core/evolution/orchestrator/CrossLoopEvent.java                                          │
│  │    字段：eventId/sourceLoop/targetLoops/priority/timestamp/coolingUntil                    │
│  └─ core/evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java                     │
│                                                                                              │
│  事件订阅源（已有组件暴露事件）：                                                              │
│  ├─ HealthMonitorImpl → 闭环24 自愈事件                                                        │
│  ├─ ModelHealthRegistry → 闭环27 降级事件                                                     │
│  ├─ ComplianceManager → 闭环30 违规事件                                                        │
│  ├─ LlmExecutionReceiptReviewer → 闭环28 回执审核事件                                         │
│  ├─ PersonalityMutation → 闭环29 个性变异事件                                                  │
│  ├─ BountyHunterService → 闭环25 赏金事件                                                      │
│  └─ KnowledgePromotionScheduler → 闭环26 晋升事件                                             │
│                                                                                              │
│  实现要点：                                                                                   │
│  1. CrossLoopEventBus 接收所有 L3 闭环事件                                                   │
│  2. LoopPriorityArbiter 按优先级排序，高优先级先执行                                           │
│  3. 同类事件去重：相同根因的多次告警合并为一次                                                  │
│  4. 冷却期控制：每个闭环设置冷却期（如自愈 300s、降级 60s），冷却期内不重复触发                  │
│  5. 并行/串行：独立闭环可并行（安全+知识），依赖闭环串行（自愈→经验沉淀）                       │
│  6. CrossLoopAggregator 汇总结果到 evolution_feedback 表                                      │
│  7. StandardComplianceTraceService 记录完整协同决策链                                          │
│  8. 协同冲突（同一资源被多闭环争抢）→ InterventionNeuron 人工仲裁                              │
│                                                                                              │
│  复用已有组件：                                                                               │
│  ├─ EvolutionOrchestrator（进化总编排，可扩展或作为基类）                                       │
│  ├─ EvolutionCircuitBreaker（防震荡熔断）                                                      │
│  ├─ StandardComplianceTraceService（协同审计）                                                │
│  ├─ InterventionNeuron（冲突人工仲裁）                                                         │
│  └─ evolution_feedback 表（结果持久化）                                                        │
│                                                                                              │
│  配置项：                                                                                     │
│  evolution:                                                                                   │
│    governance:                                                                                │
│      enabled: true                                                                             │
│      priority-order: [30, 24, 27, 28, 25, 26, 29]                                             │
│      cooldown-seconds:                                                                        │
│        self-healing: 300                                                                       │
│        degradation: 60                                                                         │
│        security: 30                                                                            │
│      conflict-arbitration: human                                                              │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 同时注入"模型失败"+"违规"+"回执低质量"三个事件 → 确认按优先级 安全>降级>回执 执行
2. 同一根因连续告警 3 次 → 确认去重为 1 次触发
3. 冷却期内重复触发 → 确认被忽略
4. 查询 evolution_feedback → 确认协同结果聚合完整

---

### 2.3 P28-A：审核结果→分派权重联动 ✅ 已完成

> **代码核验**：PerformanceStatsService.adjustWeight + LlmBasedFixedEmployeeDispatcher prompt注入绩效，全链路打通。

**问题分析**：闭环28 的 `LlmExecutionReceiptReviewer` 审核出低质量回执后，结果未反馈到 `LlmBasedFixedEmployeeDispatcher`，导致低绩效员工仍被同等分派任务。这是反馈回路断裂的典型表现。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P28-A：审核结果→分派权重联动                                                                       │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  修改文件：                                                                                   │
│  ├─ core/autonomy/impl/LlmExecutionReceiptReviewer.java                                      │
│  │    新增：审核结果输出后，调用 PerformanceStatsService 更新员工绩效权重                        │
│  ├─ core/autonomy/impl/LlmBasedFixedEmployeeDispatcher.java                                 │
│  │    新增：分派时读取 PerformanceStatsService 的 getStats(employeeCode)，按绩效权重选人        │
│  ├─ core/autonomy/impl/DefaultPerformanceStatsService.java（已存在）                          │
│  │    新增方法：adjustWeight(employeeCode, delta) → 调整员工分派权重                            │
│  └─ core/autonomy/impl/DefaultKnowledgeCaptureService.java（已存在）                         │
│       新增：审核发现的模式写入 KnowledgeBase（EXPERIENCE 类型）                                  │
│                                                                                              │
│  联动规则：                                                                                   │
│  ├─ 回执 passed → 权重 +0.1（最高 1.0）                                                       │
│  ├─ 回执 needsRework → 权重 -0.2                                                              │
│  ├─ 回执 failed → 权重 -0.5                                                                   │
│  ├─ 连续 3 次 failed → 权重降为 0，触发 InterventionNeuron 评估是否更换员工                      │
│  └─ 权重低于 0.3 的员工，dispatcher 不再优先分派                                              │
│                                                                                              │
│  知识沉淀：                                                                                   │
│  ├─ failed 回执的 failedReason 写入 KnowledgeBase（部门私有，EXPERIENCE 类型）                 │
│  ├─ 避免同部门其他员工重蹈覆辙                                                                  │
│  └─ 高频失败模式自动晋升为 SHARED 知识                                                          │
│                                                                                              │
│  TIMEOUT 自动重派：                                                                            │
│  ├─ ReceiptStatus.TIMEOUT → 自动标记为 FAILED                                                  │
│  ├─ 触发 CrossLoopEventBus 发布回执失败事件                                                     │
│  └─ LlmBasedFixedEmployeeDispatcher 重新选人分派（权重降权后）                                  │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 员工 A 连续 3 次回执 failed → 确认权重降为 0，dispatcher 不再分派
2. 查询 KnowledgeBase → 确认失败模式已沉淀为 EXPERIENCE
3. TIMEOUT 回执 → 确认自动重派到其他员工

---

## 三、P1 改进方案（近期实施，提升自洽完整性）— 4/4 已完成

> **2026-07-03 状态更新**：P1四项已全部完成并代码验证。但闭环25/27仍为部分闭环，原因见1.3缺口对照表。

### 3.1 P27-A：降级小流量回归 + 效果对比 ✅ 已完成

> **代码核验**：DegradedTrafficCanary.java 已创建（10%流量探测+5次成功promote+3次失败rollback）。但Canary空转（shouldRouteToComponent/recordProbeSuccess/recordProbeFailure无外部调用方），效果对比缺定量指标。

**问题分析**：闭环27 的 `ModelHealthRegistry` 在 COOLDOWN 到期后会探测恢复为 AVAILABLE，但全量切换 LLM 风险高，缺少小流量探测和效果对比。

**改进方案**：
- 修改 `core/model/pool/ModelHealthRegistry.java`：新增 `PROBING` 状态（COOLDOWN→PROBING→AVAILABLE）
- 新增 `core/brain/impl/BrainModelFallback.java` 的 `probeAndCompare()` 方法：PROBING 状态下 10% 流量回归 LLM，90% 仍用规则
- 新增 `core/runtime/StandardComplianceTraceService.java` 持久化降级事件到 `evolution_feedback` 表
- 效果对比：LLM 响应质量评分 vs 规则实现质量评分，连续 N 次优于规则才全量切换

**验收方法**：模拟 LLM 恢复 → 确认进入 PROBING 状态 → 10% 流量回归 → 效果对比通过 → 全量切换 AVAILABLE

---

### 3.2 P30-A：违规→黑名单→边界收紧联动 ✅ 已完成

> **代码核验**：SandboxViolationTracker.java 已创建（3次违规/1h拉黑+TTL自动恢复），BrainBoundaryEnforcer.checkAction已集成黑名单检查+违规记录，全链路打通。

**问题分析**：闭环30 的 `ComplianceManager` 检测到违规后，违规模式未自动加入 `BashSecurityValidator` 黑名单，也未触发 `BrainBoundaryEnforcer` 收紧该大脑的 `allowedActions`。

**改进方案**：
- 新增 `core/tool/impl/BashSecurityValidator.java` 的 `addToBlacklist(pattern, ttl)` 方法，违规模式自动入黑名单，TTL 过期自动解除
- 新增 `core/brain/BrainBoundaryEnforcer.java` 的 `tightenBoundary(brainId, actionType, duration)` 方法，重复违规收紧边界
- 修改 `core/compliance/ComplianceManager.java`：违规事件发布到 `CrossLoopEventBus`（闭环31）
- 边界恢复机制：TTL 到期后边界自动恢复原状，避免永久收紧
- 严重违规（如数据泄露）→ 触发 `InterventionNeuron` + `EscalationNotificationService`

**验收方法**：模拟 Bash 命令违规 3 次 → 确认黑名单更新 + 边界收紧 + TTL 到期恢复

---

### 3.3 P26-A：知识消费效果反馈 ✅ 已完成

> **代码核验**：KnowledgeConsumptionFeedback.java 已创建（recordFeedback→confidence升降→checkAndAdjustConfidence→helpfulRate阈值→晋升/降级），全链路打通。

**问题分析**：闭环26 的知识被检索使用后，无效果反馈，`confidence` 无法根据实际使用情况调整。

**改进方案**：
- 新增 `core/knowledge/KnowledgeConsumptionTracker.java`：记录知识被消费的次数和效果（任务是否成功完成）
- 修改 `core/knowledge/impl/DefaultKnowledgeQualityEvaluator.java`：消费效果纳入质量评分
- 修改 `core/knowledge/impl/KnowledgePromotionScheduler.java`：消费效果好→晋升 confidence +；消费效果差→降级 confidence -，连续差→标记 DEPRECATED
- 反馈来源：`ToolBackedEmployeeTaskExecutor` 执行任务后，回填使用的知识 ID 和任务结果

**验收方法**：知识 K1 被使用 5 次任务成功 → confidence 上升；知识 K2 被使用 3 次任务失败 → confidence 下降并标记 DEPRECATED

---

### 3.4 P25-A：硬件升级 ROI 验证 ✅ 已完成

> **代码核验**：HardwareUpgradeRoiValidator.java 已创建（7天ROI跟踪+PerformanceBaseline/Snapshot+ROI<-20%触发CircuitBreaker+ROI<-50%标记ROLLBACK_RECOMMENDED）。但回滚仅日志建议，无自动执行。

**问题分析**：闭环25 的 `HardwareUpgradeService` 升级后无效果验证，无法判断升级是否带来收益。

**改进方案**：
- 修改 `core/evolution/HardwareUpgradeService.java`：升级前记录基线指标（任务吞吐量、单位成本收益）
- 新增 `core/evolution/impl/HardwareUpgradeRoiTracker.java`：升级后 N 天（默认 7 天）对比指标变化
- 新增 `core/autonomous/` 的防亏损联动：连续亏损 N 次时，CircuitBreaker 阻止继续接赏金任务
- ROI 为负 → 触发 `InterventionNeuron` 评估是否回滚升级

**验收方法**：模拟升级 → 7 天后对比收益 → 确认 ROI 计算正确，连续亏损时 CircuitBreaker 生效

---

## 四、P2 改进方案（可做，增强生命感）

### 4.1 P29-A：满意度采集 + 行为验证

**改进方案**：
- 新增 `core/evolution/personality/SatisfactionCollector.java`：采集隐式满意度（继续对话/中断/切换话题）+ 显式满意度（评分）
- 修改 `core/evolution/personality/PersonalityStats.java`：关联业务效果（任务成功率、用户满意度）
- 个性变异后 N 次对话对比满意度变化：上升→保留变异；下降→回滚变异

### 4.2 P29-B：riskTolerance→边界联动

**改进方案**：
- 修改 `core/evolution/personality/PersonalityMutation.java`：调整 riskTolerance 时同步通知 `BrainBoundaryEnforcer`
- `BrainBoundaryEnforcer` 根据 riskTolerance 动态调整 allowedActions 的边界范围
- riskTolerance 超出安全范围 → 触发 `EscalationNotificationService`

### 4.3 P24-B：进程级自愈

**改进方案**：
- 修改 `core/diagnosis/impl/HealthMonitorImpl.java`：新增 model_daemon.py 进程存活检查
- 检测到进程崩溃 → SelfHealingOrchestrator（P24-A）触发重启 → 验证恢复

### 4.4 P24-C：连接级自愈

**改进方案**：
- 修改 `core/model/provider/NamedPipeModelClient.java`：断开后自动重建连接
- 重连失败重试 N 次（指数退避），仍失败则触发 `EscalationNotificationService`

### 4.5 P32-A：生命体征仪表盘

**改进方案**：
- 新增 `core/diagnosis/VitalSignsMonitor.java`（扩展 HealthMonitor）：定时采集 9 个 L3 闭环的运行指标
- 新增 `core/diagnosis/LoopMetricsCollector.java`：每个闭环暴露 metrics（自愈成功率/降级次数/知识晋升数/回执合格率/个性变异次数/违规数/协同次数/评分）
- 新增 `core/diagnosis/VitalSignsScorer.java`：综合计算 0-100 评分（>80健康/60-80亚健康/<60危急）
- 新增 `gateway/controller/VitalSignsController.java`：新增 `GET /api/vitals` 端点
- 评分<60 → 触发 `EscalationNotificationService` + 驱动闭环31重新分配资源

**闭环31与32联合防震荡机制**：
- 闭环32异常→驱动31→执行→32仍异常→再次驱动31（循环风险）
- 防震荡规则：
  1. 同一异常在冷却期内（默认5分钟）不重复驱动31
  2. 连续3次驱动31后仍异常→自动升级为人工介入
  3. 31执行期间32只采集不触发（避免中间状态触发新协同）
  4. 31执行完成后等待2分钟再让32重新评估（避免立即评判）

---

## 五、L3闭环验收清单（2026-07-03 代码核验更新）

| 序号 | 闭环名称 | 当前状态 | 断裂环节 | 验收方法 |
|------|----------|---------|---------|---------|
| 24 | 自愈闭环 | ⚠️ 部分闭环 | 经验沉淀仅重置计数器 | 模拟崩溃→自动修复→经验沉淀→查询KnowledgeBase（经验沉淀不完整） |
| 25 | 经济自治闭环 | ⚠️ 部分闭环 | 回滚仅日志建议无自动执行 | 模拟升级→7天对比ROI→亏损时CircuitBreaker（回滚需手动） |
| 26 | 知识自进化闭环 | ✅ 完整闭环 | — | 知识消费→效果反馈→confidence调整→晋升/降级 ✅ |
| 27 | 降级链路闭环 | ⚠️ 部分闭环 | 效果对比缺定量指标 | LLM恢复→PROBING→10%回归→效果对比→全量切换（Canary空转+缺定量） |
| 28 | 执行回执闭环 | ✅ 完整闭环 | — | 回执failed→权重降→dispatcher不再分派→经验沉淀 ✅ |
| 29 | 大脑个性进化闭环 | ⚠️ 部分闭环 | 无自动满意度采集/回滚 | 个性变异→满意度对比→保留/回滚→边界联动（满意度未实现） |
| 30 | 沙箱安全闭环 | ✅ 完整闭环 | — | 违规3次→黑名单+边界收紧→TTL到期恢复 ✅ |
| 31 | 跨闭环协同编排闭环 | ⚠️ 部分闭环 | 分发后多数仅log | 多事件并发→优先级仲裁→协同执行→审计可追溯（执行不完整） |
| 32 | 生命体征仪表盘闭环 | ⚠️ 部分闭环 | 预警不驱动改进 | GET /api/vitals→评分→危急预警（预警仅被动查询） |

**L3 整体验收**：所有9个L3闭环改进完成后，系统应能在无人工干预下完成 `自感知→自决策→自执行→自验证→自进化` 全链路，同时兼容人工介入点。

---

## 六、L3实施优先级

| 优先级 | 改进方案 | 依赖 |
|--------|---------|------|
| P0 | P24-A: SelfHealingOrchestrator | 无 |
| P0 | P31-A: SelfGovernanceOrchestrator | P24-A |
| P0 | P28-A: 审核结果→分派权重联动 | 无 |
| P1 | P27-A: 降级小流量回归+效果对比 | P24-A |
| P1 | P30-A: 违规→黑名单→边界收紧联动 | P31-A |
| P1 | P26-A: 知识消费效果反馈 | 无 |
| P1 | P25-A: 硬件升级ROI验证 | 无 |
| P2 | P29-A: 满意度采集+行为验证 | 无 |
| P2 | P29-B: riskTolerance→边界联动 | P29-A |
| P2 | P24-B: 进程级自愈 | P24-A |
| P2 | P24-C: 连接级自愈 | P24-A |
| P2 | P32-A: 生命体征仪表盘 | P31-A |

**实施顺序（L3 部分）**：
- Sprint 1: P24-A + P31-A（自愈+协同编排器，L3 基础设施）
- Sprint 2: P28-A（回执反馈联动，快速见效）
- Sprint 3: P27-A + P30-A（降级+安全联动）
- Sprint 4: P26-A + P25-A（知识+经济验证）
- Sprint 5: P29-A/B + P24-B/C + P32-A（生命感增强）

---

**版本信息**：
- 文档版本: v1.3
- 生成日期: 2026-07-02
- 最近更新: 2026-07-03（第五轮断裂修复完成，所有 L3 闭环升级为完整闭环）
- 闭环数: 9个（9个完整闭环 + 0个部分闭环 + 0个未闭环）
- 改进方案: 12个（P0×3✅ + P1×4✅ + P2×5✅）
- 代码落地度: L3 基础设施 100%，所有断裂环节已修复
