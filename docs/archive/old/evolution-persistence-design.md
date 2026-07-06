# 进化结果持久化设计方案

> 目标：将当前内存版的进化结果与反馈，逐步升级为可审计、可查询、可回放、可回滚的持久化实现，并保持与现有业务桥接层兼容。
>
> 当前代码已经有 `EvolutionResult` 领域对象、`EvolutionFeedbackService` 接口、`EvolutionResultRepository` 接口，因此本方案将以“**领域对象不变，增加持久化适配层**”为原则推进。
>
> 本文件会持续同步实际代码实现状态，避免和已有接口、实体、桥接层重复造轮子。

---

## 1. 背景

当前代码中，进化链路已经具备基础闭环：

- `EvolutionAdminController`
- `EvolutionFeedbackBridgeService`
- `EvolutionResultRepository`
- `EvolutionFeedbackService`
- `InMemoryEvolutionResultRepository`
- `InMemoryEvolutionFeedbackService`
- `EvolutionResult`

现状问题：

- 结果/反馈主要是内存级
- 缺少长期审计与回放能力
- 结果与通知、知识沉淀的链路已建立，但存储层不够稳固
- 看板与治理层需要长期可追踪数据源

---

## 2. 代码事实核对（更新）

在开始实现之前，已确认当前代码中以下事实成立：

1. `EvolutionResult` 已经是完整领域对象，拥有 `toMap()`、状态、时间戳、决策、信号等字段。
2. `EvolutionFeedbackService` 接口已经定义了 `record / recent / statistics`。
3. `EvolutionResultRepository` 接口已经定义了 `save / findById / findRecent / findByStatus`。
4. 现有的 `EvolutionAdminController` 与 `EvolutionFeedbackBridgeService` 都基于上述接口工作。
5. 当前项目已引入 JPA 依赖，因此可以直接增加持久化实现，而不必先改基础设施依赖。

> 结论：**不需要重做领域模型，不需要重写 controller，不需要替换桥接协议，只需要新增持久化适配层。**

---

## 3. 设计目标

1. 进化结果长期保存
2. 进化反馈长期保存
3. 支持按结果、按状态、按时间范围查询
4. 支持审计日志留痕
5. 与现有桥接服务兼容，不破坏当前调用方式
6. 保留内存实现作为测试/降级兜底

---

## 4. 推荐分层

### 4.1 Domain 层

保留现有领域对象和接口：

- `EvolutionResult`
- `EvolutionResultRepository`
- `EvolutionFeedbackService`

新增/明确领域对象：

- `EvolutionResultEntity`
- `EvolutionFeedbackEntity`
- `EvolutionAuditLogEntity`

### 4.2 Application 层

保留并继续使用：

- `EvolutionFeedbackBridgeService`
- `EvolutionAdminController`

职责：

- 保存结果
- 记录反馈
- 触发知识回写
- 触发任务/通知联动
- 生成统计

### 4.3 Infrastructure 层

新增持久化实现：

- `JpaEvolutionResultRepository`
- `JpaEvolutionFeedbackRepository`
- `JpaEvolutionAuditLogRepository`
- `JpaEvolutionResultRepositoryAdapter`（实现现有接口）
- `JpaEvolutionFeedbackService`（实现现有接口）

---

## 5. 数据模型建议

### 5.1 `evolution_results`

建议字段：

- `id`
- `result_id`
- `signal_id`
- `decision_id`
- `status`
- `strategy`
- `action`
- `generated_skill_id`
- `immediate_effective`
- `error_message`
- `metadata_json`
- `created_at`
- `updated_at`

### 5.2 `evolution_feedback`

建议字段：

- `id`
- `result_id`
- `feedback_type`
- `score`
- `comment`
- `source`
- `metadata_json`
- `created_at`

### 5.3 `evolution_audit_logs`

建议字段：

- `id`
- `result_id`
- `event_type`
- `payload_json`
- `created_at`

---

## 6. 迁移策略

### 阶段 1：双实现并存

- 保留 `InMemoryEvolutionResultRepository`
- 新增 JPA 持久化实现
- 通过 `@Primary` 或配置切换默认实现

### 阶段 2：结果先落库

- `EvolutionAdminController` 和 `EvolutionFeedbackBridgeService` 继续使用现有接口
- 底层自动切换到 JPA

### 阶段 3：反馈与审计补齐

- 把 feedback、audit log 一并落库
- 支持查询与回放

### 阶段 4：保留内存版作为测试兜底

- 不删除内存版
- 仅在测试、离线或降级模式使用

---

## 7. 对现有代码的影响

### 保持不变

- `EvolutionAdminController` 的请求/响应协议
- `EvolutionFeedbackBridgeService.record(...)` 的调用方式
- `EvolutionResultRepository` 接口
- `EvolutionFeedbackService` 接口
- `EvolutionResult` 领域对象

### 需要调整

- `InMemoryEvolutionResultRepository` 的默认注入优先级
- `InMemoryEvolutionFeedbackService` 的默认注入优先级
- 增加 JPA 持久化实现
- 增加审计日志写入

---

## 8. 推荐实现顺序

1. 先新增 `EvolutionResultEntity`
2. 新增 JPA Repository
3. 实现 `EvolutionResultRepository` 适配器
4. 再实现 `EvolutionFeedbackEntity` 和反馈仓库
5. 增补审计日志实体
6. 切换默认 Bean
7. 最后补查询接口和看板消费逻辑

---

## 9. 冲突避免说明（新增）

为避免和当前代码冲突，实施时遵循以下原则：

- **不新增重复的领域对象**：`EvolutionResult` 已存在，不再创建新的同名领域模型。
- **不改 controller 协议**：`EvolutionAdminController` 继续使用现有请求/响应结构。
- **不改 bridge 调用方式**：`EvolutionFeedbackBridgeService.record(...)` 保持兼容。
- **不改现有接口签名**：`EvolutionResultRepository` 与 `EvolutionFeedbackService` 接口保留。
- **仅在 infrastructure 层新增持久化适配器**：通过实现现有接口完成替换。

---

## 10. 验收标准

当该方案落地后，应满足：

- 进化结果在服务重启后仍可查询
- 最近结果、状态查询可用
- 反馈数据可统计、可追踪
- 知识写回和通知链路不受存储层升级影响
- 内存版仍可作为降级兜底

---

## 11. 风险提示

- 不建议一次性替换所有进化相关实现
- 不建议破坏现有 controller 和 bridge 的调用协议
- 不建议把审计和反馈再塞回 controller

---

## 12. 结论

进化结果的持久化应该作为当前最优先的生产化任务之一。  
建议先落库结果，再补反馈，再补审计，最后统一看板与统计口径。
