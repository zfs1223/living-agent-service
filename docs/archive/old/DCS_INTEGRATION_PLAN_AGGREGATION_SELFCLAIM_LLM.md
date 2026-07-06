# DepartmentChatService 集成方案：聚合服务 + 自行领取 + LLM 增强实现

> 本文档为 `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` 第12.7节差距分析中 #5/#6/#7 项的完整实施方案。
>
> 创建日期：2026-06-23
> 最后更新：2026-06-24

---

## 1. 背景与目标

第11章"最优流程设计：分层自治执行闭环"已落地 P0/P1/P2 全部20个核心组件，但 DepartmentChatService（DCS）作为网关层编排中枢，尚未接入以下3个已实现的 core 层服务：

| # | 差距项 | 当前状态 | 目标 |
|---|--------|----------|------|
| 5 | DCS 接入 DepartmentAggregationService | ✅ 已完成：DCS 注入聚合服务，onReceiptRecorded 调用 aggregate()，triggerAsyncFinalResponse 使用 DepartmentDeliverable | 在最终响应生成前调用聚合服务，用 DepartmentDeliverable 替代原始 Receipt 聚合 |
| 6 | DCS 接入 EmployeeSelfClaimService + DepartmentTodoPool | ✅ 已完成：DCS 注入 TodoPool + SelfClaimService，规划完成后发布待办，窗口期后兜底指派 | 部门大脑分析后发布待办到 TodoPool，员工自行领取替代中央指派 |
| 7 | LlmDepartmentAggregationService | ✅ 已完成：新增 LLM 驾动的聚合服务，使用 DefaultDepartmentAggregationService 作为 fallback，GatewayConfig 中注册为 Primary Bean | 新增 LLM 驱动的聚合分析，提升成果一致性和质量评估的语义理解能力 |

---

## 2. 设计原则

1. **渐进式改造**：新逻辑作为可选注入，通过开关或条件判断控制，现有流程不受影响
2. **Fallback 保护**：每个新集成点都有回退到现有行为的路径
3. **向后兼容**：DCS 构造函数新增参数使用 `@Nullable` 或提供默认值，不破坏现有调用方
4. **最小改动面**：优先在现有方法中增加分支逻辑，而非重写整个方法

---

## 3. #5 DCS 接入 DepartmentAggregationService

### 3.1 当前流程（Receipt 直聚）

```
onReceiptRecorded()
  → 收集所有 Receipt
  → 全部收集完毕
  → triggerAsyncFinalResponse()
    → ExecutionResultAggregator.aggregateWithCompensation()
    → MainBrainFinalSummaryService.generateSummary()
```

### 3.2 目标流程（部门级聚合 → 主脑收口）

```
onReceiptRecorded()
  → 收集所有 Receipt
  → 全部收集完毕
  → [新增] DepartmentAggregationService.aggregate()
    → 检查审查状态、待办池完成情况
    → 生成 DepartmentDeliverable
  → triggerAsyncFinalResponse()
    → [修改] 使用 DepartmentDeliverable 替代原始 Receipt 聚合
    → MainBrainFinalSummaryService.generateSummary()
```

### 3.3 修改文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `gateway/service/DepartmentChatService.java` | 修改 | 注入 DepartmentAggregationService；在 `onReceiptRecorded` 全部回执收集完毕后调用 `aggregate()`；在 `triggerAsyncFinalResponse` 中使用 DepartmentDeliverable |
| `gateway/config/GatewayConfig.java` | 修改 | DCS 构造函数新增 DepartmentAggregationService 参数 |

### 3.4 详细修改步骤

#### 步骤1：DCS 构造函数新增参数

```java
// DepartmentChatService.java 构造函数末尾新增
DepartmentAggregationService departmentAggregationService
```

#### 步骤2：onReceiptRecorded 中调用聚合

在 `onReceiptRecorded()` 方法中，全部回执收集完毕后、调用 `triggerAsyncFinalResponse()` 之前，新增聚合调用：

```java
// 现有逻辑：全部回执收集完毕
if (allReceiptsCollected) {
    // [新增] 部门级聚合
    if (departmentAggregationService != null) {
        try {
            String planId = executionId; // 使用 executionId 作为 planId
            AggregationResult aggregationResult = departmentAggregationService.aggregate(
                department, planId, goal);

            if (aggregationResult.success()) {
                log.info("Department aggregation completed: department={}, quality={}",
                    department, aggregationResult.deliverable().overallQualityScore());
            } else {
                log.warn("Department aggregation incomplete: department={}, issues={}",
                    department, aggregationResult.message());
                // Fallback: 聚合不成功时仍走原有 Receipt 聚合路径
            }

            // 缓存聚合结果供最终响应使用
            aggregationResultCache.put(executionId, aggregationResult);
        } catch (Exception e) {
            log.warn("Department aggregation failed, falling back to receipt aggregation: {}", e.getMessage());
            // Fallback: 异常时走原有路径
        }
    }

    triggerAsyncFinalResponse(executionId, sessionId);
}
```

#### 步骤3：triggerAsyncFinalResponse 中使用聚合结果

在 `triggerAsyncFinalResponse()` 中，当 `aggregationResultCache` 中有聚合结果时，使用 DepartmentDeliverable 中的数据替代原始 Receipt 聚合：

```java
// 现有逻辑：汇总回执状态
AggregationResult aggregationResult = aggregationResultCache.remove(executionId);
if (aggregationResult != null && aggregationResult.success()) {
    // 使用聚合结果中的质量分和交付项
    DepartmentDeliverable deliverable = aggregationResult.deliverable();
    // 将 deliverable 的摘要信息注入到最终响应的上下文中
    summaryContext.put("departmentDeliverable", deliverable);
}
```

#### 步骤4：GatewayConfig 更新

DCS Bean 注册时注入 `DepartmentAggregationService`。

### 3.5 Fallback 策略

| 场景 | Fallback |
|------|----------|
| departmentAggregationService 为 null | 跳过聚合，走原有 Receipt 聚合路径 |
| aggregate() 抛异常 | 捕获异常，走原有路径 |
| 聚合结果为 partial/incomplete | 仍触发最终响应，但附加聚合问题到响应上下文 |

### 3.6 验收标准

1. 部门内所有员工执行完成后，自动触发 DepartmentAggregationService.aggregate()
2. 聚合成功时，最终响应包含 DepartmentDeliverable 的质量评分和交付项摘要
3. 聚合失败或服务不可用时，回退到原有 Receipt 聚合路径，不影响用户响应
4. Trace 中出现 `department_aggregation_started` / `department_aggregation_completed` 事件

---

## 4. #6 DCS 接入 EmployeeSelfClaimService + DepartmentTodoPool

### 4.1 当前流程（中央指派）

```
ConversationOrchestrator.orchestrate()
  → MainBrainTaskDirector.plan() → 生成 MainBrainTaskPlan
  → FixedEmployeeDispatcher.dispatch() → 中央指派员工
  → AssignmentPreparationService.prepare() → 生成任务单
  → DepartmentExecutionCoordinator.coordinate() → 发布到员工通道
```

### 4.2 目标流程（自行领取 + 兜底指派）

```
ConversationOrchestrator.orchestrate()
  → MainBrainTaskDirector.plan() → 生成 MainBrainTaskPlan
  → [新增] 将部门计划发布到 DepartmentTodoPool
  → [新增] 窗口期（2s）内员工自行领取
  → [新增] 窗口期后未领取的由 EmployeeSelfClaimService.assignUnclaimed() 兜底指派
  → 领取/指派成功 → AssignmentPreparationService.prepare() → 执行
```

### 4.3 修改文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `gateway/service/DepartmentChatService.java` | 修改 | 注入 DepartmentTodoPool + EmployeeSelfClaimService；在 `processDepartmentBrainAsync` 中规划完成后发布待办到 TodoPool |
| `core/autonomy/ConversationOrchestrator.java` | 修改 | orchestrate() 返回结果中携带待办池信息 |
| `gateway/config/GatewayConfig.java` | 修改 | DCS 构造函数新增 DepartmentTodoPool + EmployeeSelfClaimService 参数 |

### 4.4 详细修改步骤

#### 步骤1：DCS 构造函数新增参数

```java
// DepartmentChatService.java 构造函数末尾新增
DepartmentTodoPool departmentTodoPool,
EmployeeSelfClaimService employeeSelfClaimService
```

#### 步骤2：规划完成后发布待办到 TodoPool

在 `processDepartmentBrainAsync()` 中，`conversationOrchestrator.orchestrate()` 返回成功结果后，将 MainBrainTaskPlan 中的部门计划转换为 DepartmentTodoItem 并发布：

```java
// 现有逻辑：orchestrate 成功后
OrchestrationResult result = orchestrateFuture.join();
if (result.success() && result.mainBrainTaskPlan() != null) {
    MainBrainTaskPlan plan = result.mainBrainTaskPlan();

    // [新增] 将部门计划发布到待办池
    if (departmentTodoPool != null && plan.departmentPlans() != null) {
        for (DepartmentTaskPlan deptPlan : plan.departmentPlans()) {
            if (deptPlan.department().equals(department)) {
                publishDepartmentTodos(deptPlan, plan, executionId);
            }
        }
    }
}
```

#### 步骤3：新增 publishDepartmentTodos 方法

```java
private void publishDepartmentTodos(DepartmentTaskPlan deptPlan,
                                     MainBrainTaskPlan mainPlan,
                                     String executionId) {
    List<DepartmentTodoItem> items = new ArrayList<>();

    for (var assignment : deptPlan.assignments()) {
        DepartmentTodoItem item = new DepartmentTodoItem(
            "todo-" + UUID.randomUUID().toString().substring(0, 8),
            deptPlan.department(),
            executionId,
            assignment.goal(),
            assignment.requiredCapabilities(),
            assignment.requiredTools(),
            assignment.requiredCapability(),
            DepartmentTodoItem.Priority.MEDIUM,
            DepartmentTodoItem.Status.PENDING
        );
        items.add(item);
    }

    departmentTodoPool.publishAll(items);
    log.info("Published {} todo items to pool for department={}, executionId={}",
        items.size(), deptPlan.department(), executionId);

    // 窗口期后兜底指派
    scheduleFallbackAssignment(deptPlan.department(), items.size());
}
```

#### 步骤4：窗口期兜底指派

```java
private void scheduleFallbackAssignment(String department, int expectedCount) {
    if (employeeSelfClaimService == null) return;

    // 2秒后检查未领取的待办，执行兜底指派
    CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
        .execute(() -> {
            try {
                List<TodoClaimResult> results =
                    employeeSelfClaimService.assignUnclaimed(department);
                if (!results.isEmpty()) {
                    log.info("Fallback assignment for department={}: {} items assigned",
                        department, results.size());
                }
            } catch (Exception e) {
                log.warn("Fallback assignment failed for department={}: {}",
                    department, e.getMessage());
            }
        });
}
```

#### 步骤5：GatewayConfig 更新

DCS Bean 注册时注入 `DepartmentTodoPool` 和 `EmployeeSelfClaimService`。

### 4.5 与现有 FixedEmployeeDispatcher 的关系

自行领取机制**不替代** `FixedEmployeeDispatcher`，而是作为**前置优化**：

| 场景 | 行为 |
|------|------|
| TodoPool 有待办且员工自行领取 | 走自行领取路径 |
| TodoPool 有待办但无人领取（2s后） | 走兜底指派路径 |
| TodoPool 为空或不可用 | 回退到现有 FixedEmployeeDispatcher 路径 |

两种路径最终都进入 `AssignmentPreparationService.prepare()` → `DepartmentExecutionCoordinator.coordinate()` 执行。

### 4.6 Fallback 策略

| 场景 | Fallback |
|------|----------|
| departmentTodoPool 为 null | 跳过待办池，走现有中央指派 |
| employeeSelfClaimService 为 null | 跳过自行领取，走现有中央指派 |
| 发布待办异常 | 捕获异常，走现有中央指派 |
| 兜底指派失败 | 部分待办可能无人认领，记录日志 |

### 4.7 验收标准

1. 部门大脑规划完成后，待办项自动发布到 DepartmentTodoPool
2. 2秒窗口期内，有能力的员工可自行领取待办
3. 窗口期后，未领取的待办由 EmployeeSelfClaimService.assignUnclaimed() 兜底指派
4. TodoPool 不可用时，回退到现有 FixedEmployeeDispatcher 路径
5. Trace 中出现 `department_todo_published` / `todo_self_claimed` / `todo_brain_assigned` 事件

---

## 5. #7 LlmDepartmentAggregationService

### 5.1 当前状态

`DefaultDepartmentAggregationService` 使用规则逻辑进行聚合：
- 检查回执状态（COMPLETED/DEGRADED）
- 检查审查状态（InternalReviewService）
- 检查待办池完成情况
- 计算质量分（平均值）
- 确定聚合状态

### 5.2 LLM 增强目标

LLM 版聚合服务在规则版基础上增加**语义理解能力**：

| 能力 | 规则版 | LLM 版 |
|------|--------|--------|
| 完整性检查 | ✅ 检查回执状态 | ✅ + 语义判断成果是否真正完成 |
| 一致性检查 | ❌ 不支持 | ✅ LLM 分析前后端接口是否对齐、文档与代码是否匹配 |
| 质量评估 | ✅ 平均质量分 | ✅ + LLM 语义评估成果质量 |
| 问题发现 | ✅ 基于状态 | ✅ + LLM 发现潜在问题和不一致 |
| 修复建议 | ❌ 不支持 | ✅ LLM 生成修复建议 |

### 5.3 修改文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `core/autonomy/impl/LlmDepartmentAggregationService.java` | 新增 | LLM 驱动的部门聚合服务实现 |
| `gateway/config/GatewayConfig.java` | 修改 | 注册 LlmDepartmentAggregationService Bean（可选，覆盖默认 Bean） |

### 5.4 LlmDepartmentAggregationService 设计

```java
public class LlmDepartmentAggregationService implements DepartmentAggregationService {

    private final DefaultDepartmentAggregationService fallback;  // 规则版作为降级
    private final BrainModelResolver brainModelResolver;
    private final EmployeeExecutionReceiptService receiptService;
    private final InternalReviewService internalReviewService;
    private final DepartmentTodoPool todoPool;
    private final String brainId;  // 用于模型池解析

    // 核心方法
    @Override
    public AggregationResult aggregate(String department, String planId, String objective) {
        // 1. 先用规则版完成基础聚合
        AggregationResult ruleResult = fallback.aggregate(department, planId, objective);
        if (!ruleResult.success()) {
            return ruleResult;  // 基础聚合未通过，无需 LLM 分析
        }

        // 2. LLM 语义分析
        try {
            DepartmentDeliverable deliverable = ruleResult.deliverable();
            String llmAnalysis = callLlmForAggregation(department, objective, deliverable);

            // 3. 解析 LLM 结果，更新聚合状态
            return parseAndUpdateResult(ruleResult, llmAnalysis);
        } catch (Exception e) {
            log.warn("LLM aggregation analysis failed, using rule-based result: {}", e.getMessage());
            return ruleResult;  // Fallback 到规则版结果
        }
    }
}
```

### 5.5 LLM Prompt 设计

```
你是一个部门级成果聚合分析器。请分析以下部门交付物，判断：

1. 完整性：所有子任务是否真正完成（不仅看状态，还要看内容）
2. 一致性：各交付物之间是否一致
   - 技术部门：前端和后端接口是否对齐
   - 文档部门：文档和代码是否匹配
   - 财务部门：财务数据和报告是否一致
3. 质量：整体质量评分（0-1.0）
4. 问题：发现的潜在问题
5. 建议：修复建议

部门：{department}
目标：{objective}
交付物列表：
{deliverableItems}

请以JSON格式返回：
{
  "consistency_check": "PASSED" | "ISSUES_FOUND",
  "consistency_issues": ["问题1", "问题2"],
  "quality_score": 0.85,
  "potential_issues": ["潜在问题1"],
  "fix_suggestions": ["建议1"],
  "overall_assessment": "总体评价"
}
```

### 5.6 Fallback 策略

| 场景 | Fallback |
|------|----------|
| LLM 调用失败 | 使用规则版 DefaultDepartmentAggregationService 的结果 |
| LLM 返回空或无法解析 | 使用规则版结果 |
| LLM 解析的 quality_score 异常 | 忽略 LLM 评分，使用规则版评分 |

### 5.7 验收标准

1. LLM 版聚合服务能正确调用模型池中的模型
2. LLM 分析结果包含一致性检查、质量评分、问题发现和修复建议
3. LLM 不可用时自动降级到规则版，不影响聚合流程
4. Trace 中出现 `department_aggregation_llm_started` / `department_aggregation_llm_completed` 事件

---

## 6. 实施顺序与依赖关系

```text
阶段1：#5 DCS 接入聚合服务（独立，无前置依赖）
  ├── DCS 构造函数新增 DepartmentAggregationService
  ├── onReceiptRecorded 中调用 aggregate()
  ├── triggerAsyncFinalResponse 中使用聚合结果
  └── GatewayConfig 更新

阶段2：#6 DCS 接入自行领取（依赖 #5，因为聚合需要待办池数据）
  ├── DCS 构造函数新增 DepartmentTodoPool + EmployeeSelfClaimService
  ├── processDepartmentBrainAsync 中发布待办
  ├── 窗口期兜底指派
  └── GatewayConfig 更新

阶段3：#7 LLM 聚合增强（依赖 #5，在规则版聚合集成后增强）
  ├── 新增 LlmDepartmentAggregationService
  ├── 实现 LLM Prompt 和结果解析
  ├── GatewayConfig 注册（可选覆盖）
  └── 集成测试
```

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DCS 构造函数参数过多（已28个） | 可维护性下降 | 考虑引入 `DcsServiceRegistry` 聚合注入（不在本次范围内，记录为技术债） |
| 自行领取窗口期（2s）过短 | 员工来不及领取 | 窗口期可配置化，默认2s，通过 ClaudeCliProperties 或 application.yml 调整 |
| LLM 聚合分析延迟 | 增加最终响应时间 | LLM 分析异步执行，不阻塞主流程；超时后使用规则版结果 |
| TodoPool 内存版重启丢失 | 待办数据丢失 | 当前为 P1 实现级别，生产环境需替换为 Redis/JPA 持久化版本 |

---

## 8. 与第11章设计的映射

| 第11章设计 | 本方案对应 | 说明 |
|------------|------------|------|
| 11.4 员工自行领取机制 | #6 | DCS 发布待办 → 窗口期领取 → 兜底指派 |
| 11.5 部门内审查闭环 | 已完成（12.3/12.8） | InternalReviewService + ReviewListener 回调 |
| 11.6 部门级聚合与交付 | #5 + #7 | DCS 接入聚合服务 + LLM 增强版 |
| 11.7 主脑跨部门协调 | 已完成（12.6） | CrossDepartmentCoordinator 接口化 |
| 11.9 渐进式迁移策略 | 全方案 | Fallback 保护 + 可选注入 + 灰度切换 |

---

## 9. 后续迭代展望

完成 #5/#6/#7 后，第11章最优流程设计将全部落地。后续可考虑：

1. **TodoPool 持久化**：InMemoryDepartmentTodoPool → JpaDepartmentTodoPool 或 Redis 实现
2. **DCS 构造函数重构**：引入 `DcsServiceRegistry` 减少参数数量
3. **审查关系配置化**：downstreamReviewers 从数据库/配置文件加载，而非硬编码
4. **灰度切换**：通过 application.yml 开关控制新流程的启用范围（先技术部试点）
5. **更多部门审查关系**：为财务、法务等部门配置审查关系

---

## 10. 实施进度记录

| 日期 | 实施项 | 状态 | 说明 |
|------|--------|------|------|
| 2026-06-24 | #5 DCS 接入聚合服务 | ✅ 完成 | DCS 构造函数新增 DepartmentAggregationService；onReceiptRecorded 调用 aggregate()；triggerAsyncFinalResponse 使用 DepartmentDeliverable |
| 2026-06-24 | #6 DCS 接入自行领取 | ✅ 完成 | DCS 构造函数新增 DepartmentTodoPool + EmployeeSelfClaimService；publishDepartmentTodos 发布待办；scheduleFallbackAssignment 窗口期兜底指派 |
| 2026-06-24 | #7 LLM 聚合增强 | ✅ 完成 | 新增 LlmDepartmentAggregationService 类；GatewayConfig 注册为 Primary Bean；使用 DefaultDepartmentAggregationService 作为 fallback |

### #5 实施详情

**修改文件**：
- `DepartmentChatService.java`：新增字段 `departmentAggregationService`、`departmentAggregationResultCache`
- `DefaultDepartmentAggregationService.java`：添加 `@Service` 注解

**关键代码位置**：
- `onReceiptRecorded()` 第280-319行：聚合服务调用
- `triggerAsyncFinalResponse()` 第2535-2557行：使用聚合结果

### #6 实施详情

**修改文件**：
- `DepartmentChatService.java`：新增字段 `departmentTodoPool`、`employeeSelfClaimService`
- `InMemoryDepartmentTodoPool.java`：添加 `@Service` 注解
- `DefaultEmployeeSelfClaimService.java`：添加 `@Service` 注解

**关键代码位置**：
- `processDepartmentBrainAsync()` 第581-584行：发布待办调用
- `publishDepartmentTodos()` 第1588-1643行：待办发布逻辑
- `scheduleFallbackAssignment()` 第1645-1671行：窗口期兜底指派

### #7 实施详情

**新增文件**：
- `LlmDepartmentAggregationService.java`：LLM 驾动的部门聚合服务实现

**修改文件**：
- `GatewayConfig.java`：修改 `departmentAggregationService` Bean，添加 `@Primary` 注解，根据 MainBrain 可用性选择 LLM 版或规则版

**关键代码位置**：
- `aggregate()` 方法：先用规则版聚合，再用 LLM 增强
- `callLlmForAggregation()` 方法：构建 Prompt 并调用 MainBrain.callLlm()
- `parseAndUpdateResult()` 方法：解析 LLM JSON 结果，更新聚合状态
- `GatewayConfig.departmentAggregationService()` 第230-255行：Bean 注册逻辑

**LLM Prompt 设计**：
- 系统提示词：定义聚合分析器的角色和输出格式（JSON）
- 用户提示词：包含部门、目标、交付物列表、整体质量分
- 输出格式：consistency_check、consistency_issues、quality_score、potential_issues、fix_suggestions、overall_assessment

**Fallback 策略**：
- LLM 调用失败 → 使用规则版结果
- JSON 解析失败 → 尝试文本解析
- MainBrain 不可用 → 直接使用规则版 DefaultDepartmentAggregationService
