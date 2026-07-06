# 员工补偿持久化设计方案

> 目标：将当前内存版员工补偿/奖励/处罚体系升级为可审计、可统计、可追踪、可回放的持久化实现，并保持与现有 `EmployeeCompensationService` 接口兼容。
>
> 当前代码已经存在 `EmployeeCompensationService` 接口，以及 `InMemoryEmployeeCompensationService` 内存实现，因此本方案以“**接口不变，新增持久化适配层**”为原则推进。
>
> 本文件会持续同步实际代码实现状态，避免与现有计划、实体、桥接层重复造轮子。

---

## 1. 背景

当前员工补偿体系已经在业务上接通：

- `TaskPerformanceBridgeService` 会根据任务审核结果发放奖励或处罚
- `EmployeeController` 能查询补偿汇总
- `InMemoryEmployeeCompensationService` 已具备计划、绑定、记账、历史、汇总能力

现状问题：

- 数据仍以内存为主
- 服务重启后记录丢失
- 缺少正式审计与明细账
- 不利于绩效、任务、部门经营看板的长期统计

---

## 2. 代码事实核对（更新）

在开始实现之前，已确认当前代码中以下事实成立：

1. `EmployeeCompensationService` 接口已经定义了 `definePlan / assignPlan / recordReward / recordPenalty / getBalance / getHistory / summarizeDepartment`。
2. `InMemoryEmployeeCompensationService` 已经是可工作的临时实现，支持计划、账户、历史和汇总。
3. `TaskPerformanceBridgeService` 已经依赖该接口执行奖励/处罚。
4. `EmployeeController` 已经依赖该接口展示汇总。
5. 当前项目已引入 JPA 依赖，因此可以直接增加持久化实现，而不必先改基础设施依赖。

> 结论：**不需要重做接口，不需要重写 controller，只需要新增持久化适配层。**

---

## 3. 设计目标

1. 补偿方案可持久化
2. 员工账户可持久化
3. 奖励/处罚明细可持久化
4. 支持余额、历史、部门汇总查询
5. 与现有接口兼容，不破坏当前任务桥接逻辑
6. 保留内存实现作为测试/降级兜底

---

## 4. 推荐分层

### 4.1 Domain 层

保留现有接口：

- `EmployeeCompensationService`
- `CompensationPlan`
- `CompensationRecord`

新增领域对象建议：

- `CompensationPlanEntity`
- `CompensationAccountEntity`
- `CompensationRecordEntity`

### 4.2 Application 层

继续使用：

- `TaskPerformanceBridgeService`
- `EmployeeController`

职责：

- 任务结果触发奖励/处罚
- 汇总补偿数据
- 对外提供查询入口

### 4.3 Infrastructure 层

新增持久化实现：

- `JpaEmployeeCompensationService`
- `CompensationPlanRepository`
- `CompensationAccountRepository`
- `CompensationRecordRepository`
- `CompensationAuditLogRepository`（可选）

---

## 5. 数据模型建议

### 5.1 `compensation_plans`

建议字段：

- `plan_id`
- `department_id`
- `employee_type`
- `rules_json`
- `created_at`
- `updated_at`

### 5.2 `compensation_accounts`

建议字段：

- `employee_id`
- `plan_id`
- `balance`
- `status`
- `last_updated_at`

### 5.3 `compensation_records`

建议字段：

- `record_id`
- `employee_id`
- `type` (`REWARD` / `PENALTY`)
- `points`
- `reason`
- `source_task_id`
- `source_review_id`
- `created_at`

---

## 6. 迁移策略

### 阶段 1：双实现并存

- 保留 `InMemoryEmployeeCompensationService`
- 新增 JPA 持久化实现
- 通过 `@Primary` 或配置切换默认实现

### 阶段 2：记账先落库

- 先保证奖励/处罚明细落库
- 再保证余额可计算/可回放

### 阶段 3：补部门汇总

- 用持久化明细表生成部门汇总
- 为绩效、任务、仪表盘提供稳定数据源

### 阶段 4：保留内存版作为兜底

- 测试环境、离线环境、降级模式仍可使用

---

## 7. 对现有代码的影响

### 保持不变

- `EmployeeCompensationService` 接口
- `TaskPerformanceBridgeService` 的调用方式
- `EmployeeController` 的查询接口

### 需要调整

- 将 `InMemoryEmployeeCompensationService` 降为非默认实现
- 新增 JPA 持久化实体与仓库
- 汇总逻辑改为基于持久化明细计算

---

## 8. 推荐实现顺序

1. 先新增 `CompensationPlanEntity`
2. 再新增 `CompensationAccountEntity`
3. 再新增 `CompensationRecordEntity`
4. 新建 JPA 仓库
5. 实现 `JpaEmployeeCompensationService`
6. 切换默认 Bean
7. 最后接入看板和统计查询

---

## 9. 冲突避免说明（新增）

为避免和当前代码冲突，实施时遵循以下原则：

- **不新增重复的接口**：`EmployeeCompensationService` 已存在，不再创建新的同名接口。
- **不改 controller 协议**：`EmployeeController` 继续使用现有请求/响应结构。
- **不改 bridge 调用方式**：`TaskPerformanceBridgeService` 继续通过 `EmployeeCompensationService` 记账。
- **仅在 infrastructure 层新增持久化适配器**：通过实现现有接口完成替换。
- **不把汇总逻辑塞回 controller**：部门汇总应由 service 层生成。

---

## 10. 验收标准

当该方案落地后，应满足：

- 员工奖励/处罚记录不会因重启丢失
- 余额可回放、可追踪
- 部门汇总能稳定查询
- 任务审核触发的补偿可以被审计
- 内存版仍可作为测试或降级实现

---

## 11. 风险提示

- 不建议一次性删掉内存实现
- 不建议把补偿逻辑再塞回 controller
- 不建议把任务桥接和补偿存储强耦合到同一个类里

---

## 12. 结论

补偿体系是任务审核、绩效管理、部门经营看板的关键数据源之一，建议尽快做成持久化实现。  
优先顺序建议是：**明细先落库 → 账户可回放 → 部门汇总稳定化**。
