# P2/P3 级闭环修复计划

> **创建日期**: 2026-07-11
> **前置状态**: P0(4项)和P1(8项)已全部修复，IMPROVEMENT_PLAN_INDEX.md 已部分更新(1.2节编号表已同步，1.3覆盖统计表和1.4-1.6摘要待更新)
> **目标**: 修复P2级11项L4闭环 + P3级3项结构性断裂 + 完成文档同步

---

## 一、当前状态分析

### 系统性问题
L4闭环的Tracker/Optimizer普遍存在"eventBus.publish()无消费者"问题——`SelfGovernanceOrchestratorImpl.dispatch()`只处理loopId 24-30(L3闭环)，不处理L4闭环事件(43-62)。

### 修复策略
采用"**内联改进**"模式：在Tracker/Optimizer内部直接执行改进动作，而非依赖dispatch消费事件。与第二阶段修复闭环44-49的模式一致(ProactiveStrategyOptimizer/NotificationStrategyOptimizer/CodeReviewQualityOptimizer等均在内部直接调整参数)。

---

## 二、修复项详细方案

### Phase 1: 文档同步 (先完成)

**Task A: 更新 IMPROVEMENT_PLAN_INDEX.md 1.3覆盖统计表**

当前1.3表仍是旧数据(64✅+0⚠️+0❌)，需更新为：
| 层级 | ✅完整闭环 | ⚠️部分闭环 | ❌未闭环 | 合计 |
|------|----------|-------------|--------------|------|
| L1 | 12 | 2 | 0 | 14 |
| L2 | 7 | 2 | 0 | 9 |
| L2业务 | 1 | 0 | 0 | 1 |
| L3 | 14 | 0 | 0 | 14 |
| L4 | 12 | 11 | 3 | 26 |
| **合计** | **46** | **15** | **3** | **64** |

同时更新1.1节架构图中的计数。

---

### Phase 2: P2级闭环修复 (11项)

#### P2-13: 闭环43 工作流编排 — WorkflowOptimizationService接入主流程

**文件**: `living-agent-core/src/main/java/com/livingagent/core/workflow/WorkflowOrchestrator.java`

**问题**: `WorkflowOptimizationService.optimizeStageTimeout()`计算了优化阈值但`WorkflowOrchestrator`不读取它。

**修复方案**:
1. 在WorkflowOrchestrator中`@Autowired(required=false)`注入WorkflowOptimizationService
2. 在`executePhase()`方法中，阶段开始时读取优化阈值并应用
3. 添加`@Scheduled`定时任务调用`optimizeStageTimeout()`

```java
// WorkflowOrchestrator 新增
@Autowired(required = false)
public void setOptimizationService(WorkflowOptimizationService optimizationService) {
    this.optimizationService = optimizationService;
}

// executePhase()中，阶段完成时调用
if (optimizationService != null && stageMonitor != null) {
    Long optimized = optimizationService.getOptimizedTimeout(phase.getCode());
    if (optimized != null) {
        log.info("[闭环43] Applied optimized timeout for {}: {}ms", phase.getCode(), optimized);
    }
}
```

#### P2-14: 闭环50 租户管理 — TenantHealthMonitor事件无消费者

**文件**: `living-agent-core/src/main/java/com/livingagent/core/tenant/feedback/TenantHealthMonitor.java`

**问题**: 配额超限时仅eventBus.publish，无实际动作。

**修复方案**: 添加内联改进——配额超限时自动调整配额警戒线或发布通知
1. 添加`@Component`注解（当前为手动@Bean）
2. 新增`double quotaWarningMultiplier = 1.0`字段
3. 新增`@Scheduled(fixedRate = 30*60*1000)`定时检查方法
4. 配额超限时：调整quotaWarningMultiplier（如从1.0提升到0.9即更早预警）
5. 发布CrossLoopEvent携带multiplier变更

注意：TenantHealthMonitor中的log格式`{:.0%}`是Python风格，需修正为Java兼容格式。

#### P2-15: 闭环51 接待/访客 — VisitorConversionTracker事件无消费者

**文件**: `living-agent-core/src/main/java/com/livingagent/core/visitor/feedback/VisitorConversionTracker.java`

**问题**: 转化率低时仅eventBus.publish，无策略调整。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`double conversionWarningThreshold = 0.10`字段
3. 转化率低于阈值时动态降低阈值（更敏感预警）+ CrossLoopEvent携带变更
4. 新增`@Scheduled`定时分析

注意：log格式`{:.1%}`和`{:.0%}%`需修正。

#### P2-16: 闭环52 预算管理 — MonthlyBudgetManager孤岛

**文件**: `living-agent-core/src/main/java/com/livingagent/core/finance/budget/MonthlyBudgetManager.java`

**问题**: MonthlyBudgetManager有@Component注解但无调用者（无Controller/Service调用其方法）。

**修复方案**: 
1. 查找是否有BudgetController或FinanceController
2. 如无，在现有Controller中添加预算管理端点
3. 确保recordExpense/recordIncome有调用入口

需进一步搜索确认调用链路。

#### P2-17: 闭环53 绩效考核 — PerformanceEvaluationCycle事件无消费者

**文件**: `living-agent-core/src/main/java/com/livingagent/core/operation/performance/feedback/PerformanceEvaluationCycle.java`

**问题**: 低绩效时仅eventBus.publish，无培训触发。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`double lowPerformanceThreshold = 0.4`字段
3. 低绩效时：降低阈值（更严格筛选）+ 统计培训效果
4. 新增`@Scheduled`定时分析
5. 发布CrossLoopEvent携带阈值变更

注意：log格式`{:.1f}`需修正。

#### P2-18: 闭环54 积分/薪酬 — CreditEconomyMonitor事件无消费者

**文件**: `living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/feedback/CreditEconomyMonitor.java`

**问题**: 通胀/通缩时仅eventBus.publish，无兑换比率调整。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`double exchangeRateMultiplier = 1.0`字段
3. 通胀时提高兑换消耗(>1.0)，通缩时降低(<1.0)
4. 新增`@Scheduled`定时分析
5. 发布CrossLoopEvent携带调整

注意：log格式`{:.2f}`需修正。

#### P2-19: 闭环57 系统设置 — 仅发布事件未自动改变

**文件**: `living-agent-core/src/main/java/com/livingagent/core/settings/feedback/SettingsChangeImpactTracker.java`

**问题**: 高回滚率时仅eventBus.publish，不自动锁定设置。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`Set<String> lockedSettings`字段
3. 回滚率超阈值时：将高频回滚设置加入lockedSettings
4. 提供`isSettingLocked(String key)`方法供设置修改时检查
5. 新增`@Scheduled`定时分析

注意：log格式`{:.0%}`需修正。

#### P2-20: 闭环58 分布式部署 — 仅建议重平衡未自动触发

**文件**: `living-agent-core/src/main/java/com/livingagent/core/cluster/feedback/ClusterHealthMonitor.java`

**问题**: 节点故障时仅eventBus.publish，不触发重平衡。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`rebalanceShards()`方法
3. 节点不健康时：在recordNodeHealth中自动调用rebalanceShards()
4. 发布CrossLoopEvent携带重平衡结果

#### P2-21: 闭环59 异常检测 — 仅建议调参未自动调整

**文件**: `living-agent-core/src/main/java/com/livingagent/core/anomaly/feedback/AnomalyDetectionFeedbackLoop.java`

**问题**: 误报率高时仅eventBus.publish，不自动调整阈值。

**修复方案**: 添加内联改进
1. 添加`@Component`注解
2. 新增`double sensitivityMultiplier = 1.0`字段
3. 误报率高时：sensitivityMultiplier上调（降低敏感度）
4. 新增`@Scheduled`定时分析
5. 发布CrossLoopEvent携带调整

注意：log格式`{:.0%}`需修正。

#### P2-22: 闭环60 服务管理 — 仅发布事件无实际修复

**文件**: `living-agent-core/src/main/java/com/livingagent/core/diagnosis/feedback/ServiceBootstrapHealthTracker.java`

**问题**: 服务启动失败时仅eventBus.publish，无自动重试。

**修复方案**: 添加内联改进
1. 已有@Component注解
2. 新增`Map<String, Integer> retryCountMap`字段
3. 启动失败时：自动增加重试计数，超过阈值标记为需要人工干预
4. 新增`@Scheduled`定时检查挂起的服务重试
5. 发布CrossLoopEvent携带重试结果

#### P2-23: 闭环61 客户端设备 — 伪计数器未真正解绑

**文件**: `living-agent-core/src/main/java/com/livingagent/core/security/client/feedback/ClientDeviceHealthMonitor.java`

**问题**: `autoUnbound.increment()`只是递增伪计数器，不调用实际的markOffline()解绑。

**修复方案**: 添加内联改进
1. 已有@Component注解
2. 新增`markDeviceCompromised(String deviceId)`方法
3. 异常操作超阈值时：调用markDeviceCompromised()而非仅递增计数器
4. markDeviceCompromised()中：将设备从deviceHealthMap移到compromisedDevices集合
5. 新增`isDeviceCompromised(String deviceId)`查询方法
6. 发布CrossLoopEvent携带解绑信息

---

### Phase 3: P3级结构性断裂修复 (3项)

#### P3-24: 闭环55 广场/社交 — 无持久化+Improvement缺失

**文件**: `living-agent-core/src/main/java/com/livingagent/core/social/feedback/PlazaEngagementTracker.java`

**问题**: 所有数据仅在内存ConcurrentHashMap中，无JPA持久化；improvement仅发布事件。

**修复方案**: 最小化修复——添加内联改进
1. 添加`@Component`注解
2. 新增`double engagementThreshold = 0.15`字段
3. 活跃度低时：动态调整推荐权重/阈值
4. 新增`@Scheduled`定时分析
5. 发布CrossLoopEvent携带调整
6. **暂不实现JPA持久化**（需要新建Entity+Repository+schema变更，工作量过大，记录为后续TODO）

#### P3-25: 闭环56 虚拟办公室 — Feedback恒为healthy

**文件**: `living-agent-core/src/main/java/com/livingagent/core/office/feedback/OfficeStateSyncMonitor.java`

**问题**: `recordSnapshot()`需要被调用者传入真实的consistent和syncDelayMs参数，但当前调用者可能传入固定健康值。

**修复方案**: 
1. 添加`@Component`注解
2. 搜索所有调用`recordSnapshot`的地方，确认参数来源
3. 如果调用者确实传固定值，修复调用者传入真实数据
4. 添加内联改进：同步延迟超阈值时自动调整检查频率
5. 新增`@Scheduled`定时分析

#### P3-26: 闭环62 数据迁移 — rollbackMigration()返回false

**文件**: `living-agent-core/src/main/java/com/livingagent/core/migration/impl/DataMigrationServiceImpl.java`

**问题**: `rollbackMigration()`硬编码返回false。

**修复方案**: 实现最小回滚逻辑
1. 查询migrationRecordRepository获取迁移记录
2. 如果是SQLite→Postgres：删除Postgres中已插入的记录（按迁移ID范围）
3. 更新MigrationRecordEntity状态为ROLLED_BACK
4. 返回true
5. 添加try-catch兜底（失败仍返回false但不抛异常）

---

## 三、执行顺序

1. **Phase 1**: 文档同步（IMPROVEMENT_PLAN_INDEX.md 1.3统计表 + 1.1架构图）
2. **Phase 2**: P2级11项修复（按闭环编号顺序43→50→51→52→53→54→57→58→59→60→61）
3. **Phase 3**: P3级3项修复（55→56→62）
4. **最终**: 编译检查 + 更新LOOP_VERIFICATION_REPORT.md + IMPROVEMENT_PLAN_INDEX.md

## 四、通用修改模式

P2级修复统一遵循以下模式（与第二阶段44-49修复一致）：

1. **注册方式**: 从GatewayConfig手动@Bean改为@Component自动注册
2. **内联改进**: 在Tracker/Optimizer内部直接执行改进动作（调整阈值/参数/状态）
3. **定时触发**: 添加`@Scheduled(fixedRate = 30*60*1000)`定时分析
4. **事件发布**: 保留eventBus.publish()作为辅助通知，不再依赖消费端
5. **GatewayConfig清理**: 移除对应的@Bean方法

## 五、日志格式修复

以下类中存在Python风格的SLF4J格式说明符，编译会报错，需一并修正：

| 文件 | 错误格式 | 修正方式 |
|------|---------|---------|
| TenantHealthMonitor | `{:.0%}` | `String.format("%.0f%%", val * 100)` |
| VisitorConversionTracker | `{:.1%}`, `{:.0%}%` | `String.format("%.1f%%", val * 100)` |
| PerformanceEvaluationCycle | `{:.1f}` | `String.format("%.1f", val)` |
| CreditEconomyMonitor | `{:.2f}`, `{:.1f}` | `String.format("%.2f", val)` |
| SettingsChangeImpactTracker | `{:.0%}` | `String.format("%.0f%%", val * 100)` |
| AnomalyDetectionFeedbackLoop | `{:.0%}` | `String.format("%.0f%%", val * 100)` |
| PlazaEngagementTracker | `{:.2f}` | `String.format("%.2f", val)` |

## 六、验证步骤

1. 每个闭环修复后立即编译检查: `mvn compile -pl living-agent-core -q` / `mvn compile -pl living-agent-gateway -q`
2. 全量编译: `mvn compile -q`
3. 搜索确认无残留的Python风格格式说明符: `grep -r "{:.*%}" --include="*.java"`
4. 更新LOOP_VERIFICATION_REPORT.md中对应闭环的状态从P/B升级为C
5. 更新IMPROVEMENT_PLAN_INDEX.md中对应闭环的状态
