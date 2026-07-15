# 闭环体系代码验证与修复计划

## Context

IMPROVEMENT_PLAN_INDEX.md 声称64个闭环全部"✅ 完整闭环"，但经代码逐文件验证，发现多处 feedback→improvement 环节断裂（仅log不驱动行为变更）。本计划按用户要求：逐个验证→修复小问题→大问题先记录文档再逐步解决。

---

## 一、已确认的核心问题（代码已验证）

### P0 级（影响全局协同体系）

| # | 问题 | 闭环 | 文件 | 断裂类型 |
|---|------|------|------|---------|
| 1 | `dispatchLogOnly()` 处理闭环25/26/29，仅log返回"success"，不调用任何实际执行器 | 31 | SelfGovernanceOrchestratorImpl.java:247-250 | F-LOG |
| 2 | `dispatchSecurity()` 仅log返回"escalated"，不触发 SandboxViolationTracker/BrainBoundaryEnforcer | 31 | SelfGovernanceOrchestratorImpl.java:237-240 | F-STUB |
| 3 | `dispatchReceipt()` 仅log返回"success"，不调用 ExecutionReceiptReviewer | 31 | SelfGovernanceOrchestratorImpl.java:242-245 | F-LOG |
| 4 | P28-B 回执反馈闭环未实施：审核结果仅调权重，不沉淀为绩效经验 | 28 | LlmExecutionReceiptReviewer.java | F-MISS |

### P1 级（单闭环 feedback→improvement 断裂）

| # | 问题 | 闭环 | 文件 | 断裂类型 |
|---|------|------|------|---------|
| 5 | `ProactiveStrategyOptimizer.optimize()` 仅log，不驱动策略参数变更 | 47 | ProactiveStrategyOptimizer.java:21-34 | F-LOG |
| 6 | WebSocket Handler 异常/错误不发布 EvolutionSignal，对话改进无自动化入口 | 1 | AgentWebSocketHandler.java / DepartmentWebSocketHandler.java | F-MISS |
| 7 | `QdrantVectorService.deleteVector()` 后无PostgreSQL侧一致性校验 | 8 | QdrantVectorService.java | F-MISS |

### P2 级（文档标记⬜待实施项，确认无代码）

| # | 项目 | 闭环 | 状态 |
|---|------|------|------|
| 8 | P24-A 进程级自愈闭环 | 24 | ⬜ 无代码 |
| 9 | P28-B 回执反馈闭环（与#4重复） | 28 | ⬜ 无代码 |
| 10 | P31-A-A 事件优先级仲裁闭环 | 31 | ⬜ 无代码 |
| 11 | P31-A-B 协同执行闭环 | 31 | ⬜ 无代码 |
| 12 | P31-A-C 协同审计闭环 | 31 | ⬜ 无代码 |

---

## 二、修复计划

### 阶段1：全面验证 + 问题文档化

**目标**：对64个闭环逐一完成5阶段验证，产出验证报告

**步骤**：
1. 对L1闭环1-14逐一验证5阶段完整性，记录到验证报告
2. 对L2闭环17-22, 3-A/B, 11-A/B验证
3. 对L3闭环24-32验证
4. 对L4闭环38-63抽样验证（抽查38/39/40/42/49/50/63）
5. 产出 `docs/LOOP_VERIFICATION_REPORT.md`

**验收**：每个闭环有5阶段评估结果+断裂类型标注

### 阶段2：P0级修复

**2.1 闭环31 dispatch方法升级**（SelfGovernanceOrchestratorImpl.java）

修改文件：`living-agent-core/.../evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java`

- `dispatchLogOnly()` → 按loopId分发到实际执行器：
  - 闭环25 → 调用 LedgerService（需注入）记录经济事件
  - 闭环26 → 调用 KnowledgeConsumptionFeedback（需注入）触发confidence调整
  - 闭环29 → 调用 PersonalityMutation/SatisfactionCollector（需注入）触发个性回滚
- `dispatchSecurity()` → 调用 SandboxViolationTracker.recordViolation() + BrainBoundaryEnforcer.checkAction()
- `dispatchReceipt()` → 调用 PerformanceStatsService.adjustWeight() + 发布 ReceiptReviewEvent

注意：添加调用深度限制（maxDepth=3）防止循环调用

**2.2 P28-B 回执反馈闭环**

新增逻辑：审核结果 → 绩效经验沉淀
- 在 LlmExecutionReceiptReviewer 审核完成后，写入 KnowledgeBase（L3_SHARED）
- 高频失败模式发布 EvolutionSignal（CAPABILITY_GAP）
- 依赖文件：LlmExecutionReceiptReviewer.java, DefaultPerformanceStatsService.java

### 阶段3：P1级修复

**3.1 ProactiveStrategyOptimizer 策略驱动**

修改文件：`living-agent-core/.../proactive/feedback/ProactiveStrategyOptimizer.java`

- 新增 ProactiveStrategyProperties 配置类（pushFrequency, contentCategories）
- optimize() 修改策略参数到 properties
- ProactiveTaskScheduler 读取 properties 动态调整调度

**3.2 WebSocket Handler EvolutionSignal**

修改文件：
- `living-agent-gateway/.../websocket/AgentWebSocketHandler.java`
- `living-agent-gateway/.../websocket/DepartmentWebSocketHandler.java`

在 handleTransportError / afterConnectionClosed 异常场景发布 EvolutionSignal

**3.3 QdrantVectorService 删除一致性**

修改文件：`living-agent-core/.../database/vector/QdrantVectorService.java`

- deleteVector() 后查询 KnowledgeEntry 关联条目
- 残留条目标记为 ORPHANED 或级联删除

### 阶段4：P2级实施（按文档规划）

按依赖顺序：P31-A-A → P31-A-B → P31-A-C → P24-A

### 阶段5：文档同步

- 更新 IMPROVEMENT_PLAN_INDEX.md 闭环状态
- 更新 IMPROVEMENT_PLAN_PENDING_ITEMS.md
- 全局编译检查

---

## 三、关键文件清单

| 文件 | 修改类型 | 阶段 |
|------|---------|------|
| SelfGovernanceOrchestratorImpl.java | P0: 3个dispatch方法升级 | 2 |
| LlmExecutionReceiptReviewer.java | P0: 新增经验沉淀逻辑 | 2 |
| ProactiveStrategyOptimizer.java | P1: 策略参数驱动 | 3 |
| AgentWebSocketHandler.java | P1: EvolutionSignal发布 | 3 |
| DepartmentWebSocketHandler.java | P1: EvolutionSignal发布 | 3 |
| QdrantVectorService.java | P1: 删除一致性校验 | 3 |
| docs/LOOP_VERIFICATION_REPORT.md | 新增：验证报告 | 1 |

---

## 四、验证方式

每个修复完成后：
1. 编译检查（`mvn compile -pl living-agent-core,living-agent-gateway`）
2. 搜索验证：确认新代码中不再有"仅log"的feedback→improvement路径
3. 调用链追踪：从事件发布到最终状态变更的完整路径可追踪
