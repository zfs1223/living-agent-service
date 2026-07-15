# 闭环验证修复计划 — 第二阶段

## 当前状态总结

### 已完成（上次会话）
- P0级4项修复：SelfGovernanceOrchestratorImpl dispatch方法升级 + P28-B经验沉淀 ✅
- P1级4/5项修复：ProactiveStrategyOptimizer / NotificationStrategyOptimizer / CodeReviewQualityOptimizer / AgentWebSocketHandler ✅
- 验证39个闭环（L1×14 + L2×9 + L3×9 + L4抽样×7）
- 创建 LOOP_VERIFICATION_REPORT.md

### 待完成
1. **P1-8**: QdrantVectorService.deleteVector后PostgreSQL一致性校验（闭环8）
2. **L4闭环完整验证**: 剩余16个L4闭环（43/45/46/48/50-62）未详细验证
3. **L4闭环修复**: 多数L4闭环存在相同的"log-only improvement"断裂模式
4. **文档同步**: IMPROVEMENT_PLAN_INDEX.md状态需要更新为真实值
5. **P2待实施项**: P24-A进程级自愈 / P31-A-A/B/C协同闭环

### 核心发现
- IMPROVEMENT_PLAN_INDEX.md声称64闭环全部✅，实际验证发现大量improvement阶段仅log不驱动行为变更
- 这是一种系统性模式：Tracker/Monitor → log或publish event → 无实际策略调整
- L4闭环尤其严重，多数Tracker/Optimizer的optimize()方法仅log

---

## 执行计划

### 阶段1：完成P1-8修复 — QdrantVectorService一致性校验

**问题**: KnowledgePersistenceService.delete()先删Qdrant向量再删PostgreSQL记录，若PostgreSQL删除失败（事务回滚），Qdrant向量已删无法恢复。且无定期一致性校验。

**修复方案**:
1. 在QdrantVectorService中新增`reconcileCollection(String collectionName)`方法，对比Qdrant中的向量ID与PostgreSQL中的vectorId，清理孤立向量
2. 在KnowledgePersistenceService中新增`reconcileVectorConsistency()`定时任务，每小时执行一次一致性校验
3. 修改delete()方法：先删PostgreSQL再删Qdrant（反转顺序，利用@Transactional保障PostgreSQL删除成功后再删向量）

**涉及文件**:
- `living-agent-core/src/main/java/com/livingagent/core/database/vector/QdrantVectorService.java` — 新增reconcileCollection()
- `living-agent-core/src/main/java/com/livingagent/core/knowledge/impl/KnowledgePersistenceService.java` — 新增reconcileVectorConsistency() + 修改delete()顺序

**验证**: mvn compile -pl living-agent-core

### 阶段2：L4闭环逐个验证（43/45/46/48/50-62）

逐一读取每个闭环的Tracker/Service实现，检查5阶段完整性，重点检查improvement阶段。

| 闭环 | 核心Service | 预估状态 |
|------|------------|---------|
| 43 工作流编排 | WorkflowOrchestrator | P (improvement可能仅log) |
| 45 合规管理 | JpaComplianceManager | P (improvement仅log) |
| 46 对话管理 | ConversationServiceImpl | P (无improvement) |
| 48 记忆管理 | MemoryServiceImpl | P (无improvement) |
| 50 租户管理 | TenantHealthMonitor | P (improvement仅log) |
| 51 接待/访客 | VisitorConversionTracker | P (improvement仅发布事件) |
| 52 预算管理 | MonthlyBudgetManager | P (improvement仅告警) |
| 53 绩效考核 | — | 待确认 |
| 54 积分/薪酬 | — | 待确认 |
| 55 广场/社交 | PlazaEngagementTracker | P (improvement仅发布事件) |
| 56 虚拟办公室 | — | 待确认 |
| 57 系统设置 | — | 待确认 |
| 58 分布式部署 | — | 待确认 |
| 59 异常检测 | InterventionDecisionEngine | P (部分闭环) |
| 60 服务管理 | — | 待确认 |
| 61 客户端设备 | ClientDeviceRegistryService | C (设备注册/更新/清理完整) |
| 62 数据迁移 | — | 待确认 |

### 阶段3：L4闭环修复

根据阶段2的验证结果，对断裂的闭环进行修复。由于多数L4闭环共享同一模式（Tracker→log），采用统一修复策略：

**统一修复模式**:
每个Tracker/Optimizer新增：
1. 策略参数字段（如频率阈值、质量阈值等）
2. optimize()/adjustStrategy()方法：根据metrics调整策略参数
3. CrossLoopEventBus发布：参数变更时发布事件通知下游

**修复优先级**:
- P1: 直接影响用户体验的闭环（43工作流、45合规、46对话、48记忆）
- P2: 间接影响的闭环（50-62）

**注意**: L4闭环数量多（16个待验证），如果问题较多，先记录到文档，按文档逐步修复。

### 阶段4：文档同步

1. 更新 LOOP_VERIFICATION_REPORT.md — 补充L4完整验证结果
2. 更新 IMPROVEMENT_PLAN_INDEX.md — 将虚假✅更新为实际P/B状态
3. 创建/更新 IMPROVEMENT_PLAN_PENDING_ITEMS.md — 汇总所有待修复项

---

## 修复的具体文件和代码变更

### 阶段1文件变更

#### 1. QdrantVectorService.java
- 新增 `reconcileCollection(String collectionName, Set<String> validVectorIds)` 方法
  - 获取Qdrant中所有向量ID
  - 对比validVectorIds，删除不在validVectorIds中的孤立向量
  - 返回清理的孤立向量数量

#### 2. KnowledgePersistenceService.java
- 修改 `delete(String key)` 方法：调换删除顺序
  ```java
  // 旧：先删向量再删PG
  vectorService.deleteVector(COLLECTION_NAME, entity.getVectorId());
  repository.delete(entity);
  
  // 新：先删PG再删向量（利用@Transactional保障PG删除成功）
  repository.delete(entity);
  if (vectorSearchEnabled && entity.getVectorId() != null) {
      try {
          vectorService.deleteVector(COLLECTION_NAME, entity.getVectorId());
      } catch (Exception e) {
          log.warn("Vector deletion failed after PG delete, will be reconciled: {}", e.getMessage());
      }
  }
  ```
- 新增 `@Scheduled reconcileVectorConsistency()` 方法
  - 获取所有知识条目的vectorId
  - 调用QdrantVectorService.reconcileCollection()进行一致性校验
  - 记录校验结果

### 阶段2-3 L4修复模式（示例）

对每个断裂的L4闭环，参考ProactiveStrategyOptimizer的修复模式：

```java
// 修复前：optimize()仅log
public void optimize() {
    log.info("Optimizing strategy based on metrics...");
}

// 修复后：optimize()驱动策略参数变更
private double strategyParam = 1.0; // 策略参数

public void optimize() {
    double newValue = calculateAdjustedValue();
    if (Math.abs(newValue - strategyParam) > 0.01) {
        strategyParam = newValue;
        log.info("Strategy param adjusted to {}", strategyParam);
        if (crossLoopEventBus != null) {
            crossLoopEventBus.publish(loopId, "strategy_adjusted",
                CrossLoopEvent.EventPriority.NORMAL,
                Map.of("param", strategyParam), 300);
        }
    }
}
```

---

## 验证步骤

1. 编译检查：`mvn compile -pl living-agent-core`
2. 代码审查：确认每个修复点的improvement阶段确实改变了系统状态
3. 文档一致性：IMPROVEMENT_PLAN_INDEX.md状态与代码实际一致

---

## 风险与注意事项

1. **Qdrant事务性**: Qdrant不是事务性的，deleteVector后无法回滚。调换删除顺序后，如果向量删除失败，PostgreSQL记录已删，向量残留需要通过定期一致性校验清理。
2. **L4闭环数量多**: 16个闭环逐个验证+修复工作量大，先完成验证记录，再分批修复。
3. **构造函数注入**: 添加CrossLoopEventBus等依赖时，需注意Spring构造函数注入的兼容性。
4. **不改IMPROVEMENT_PLAN_INDEX.md的总体结论**: 只更新具体闭环的状态标记，不改变文档结构。
