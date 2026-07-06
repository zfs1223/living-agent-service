# 绩效评估持久化设计方案

> 目标：将当前内存版绩效评估、排行、趋势与指标定义，升级为可持久化、可追踪、可回放的实现，并保持现有 `PerformanceAssessmentService` 接口兼容。
>
> 当前代码已经存在 `PerformanceAssessmentService` 接口，以及两个实现：
> - `InMemoryPerformanceAssessmentService`
> - `PerformanceAssessmentServiceImpl`
>
> 其中 `InMemoryPerformanceAssessmentService` 提供了较完整的临时业务逻辑，而 `PerformanceAssessmentServiceImpl` 仍偏简化。因此本方案以“**接口不变，新增持久化适配层，保留内存实现做兜底**”为原则推进。
>
> 本文件会持续同步实际代码实现状态，避免与已有计划、实体、桥接层重复造轮子。

---

## 1. 背景

当前绩效体系已经接入业务闭环：

- `TaskController` 审核任务后会触发绩效/补偿/风险联动
- `PerformanceController` 和 `PerformanceDashboardService` 已经能提供评估、排行、趋势、看板
- `InMemoryPerformanceAssessmentService` 已能提供基础评分、排行与趋势

现状问题：

- 绩效评估结果主要以内存方式保存
- 排行榜与趋势依赖即时计算，缺少长期历史
- 指标定义未持久化
- 绩效结果不利于审计、对比和周期回放

---

## 2. 代码事实核对（更新）

在开始实现之前，已确认当前代码中以下事实成立：

1. `PerformanceAssessmentService` 接口已经定义了 `assessEmployee / getAssessment / getEmployeeAssessments / getDepartmentAssessments / getTopPerformers / getBottomPerformers / getPerformanceTrend / defineIndicator / setIndicatorWeight / getDefinedIndicators`。
2. 当前存在两个实现：
   - `InMemoryPerformanceAssessmentService`：包含较完整的计算、排行和趋势逻辑。
   - `PerformanceAssessmentServiceImpl`：简化实现，返回默认值。
3. 当前已新增并开始使用持久化结构：
   - `PerformanceAssessmentEntity`
   - `PerformanceAssessmentRepository`
   - `PerformanceIndicatorEntity`
   - `PerformanceIndicatorRepository`
   - `PerformanceTrendSnapshotEntity`
   - `PerformanceTrendRepository`
4. `JpaPerformanceAssessmentService` 已实现并作为持久化路径的主要候选实现。
5. `TaskPerformanceBridgeService`、`PerformanceController`、`PerformanceDashboardService` 都依赖该服务接口。
6. 当前项目已引入 JPA 依赖，因此可以直接增加持久化实现，而不必先改基础设施依赖。

> 结论：**不需要重做接口，不需要重写 controller，只需要新增/完善持久化适配层，并保留现有实现作为兜底。**

---

## 3. 设计目标

1. 绩效评估结果长期保存
2. 指标定义长期保存
3. 支持员工/部门/公司多维查询
4. 支持趋势与排行基于历史数据生成
5. 与现有 controller / dashboard 兼容
6. 保留内存实现作为测试/降级兜底

---

## 4. 推荐分层

### 4.1 Domain 层

保留现有接口：

- `PerformanceAssessmentService`
- `PerformanceAssessment`
- `PerformanceIndicator`

新增领域对象建议：

- `PerformanceAssessmentEntity`
- `PerformanceIndicatorEntity`
- `PerformanceTrendSnapshotEntity`

### 4.2 Application 层

继续使用：

- `PerformanceController`
- `PerformanceDashboardService`
- `TaskPerformanceBridgeService`

职责：

- 触发评估
- 提供排行/趋势/看板数据
- 接收任务审核回写的绩效影响

### 4.3 Infrastructure 层

新增持久化实现：

- `JpaPerformanceAssessmentService`
- `PerformanceAssessmentRepository`
- `PerformanceIndicatorRepository`
- `PerformanceTrendRepository`
- `PerformanceRankingService`（可选）

---

## 5. 数据模型建议

### 5.1 `performance_assessments`

建议字段：

- `assessment_id`
- `employee_id`
- `employee_name`
- `period_type`
- `overall_score`
- `grade`
- `dimension_scores_json`
- `comment`
- `assessed_at`
- `created_at`
- `updated_at`

### 5.2 `performance_indicators`

建议字段：

- `indicator_id`
- `name`
- `description`
- `category`
- `weight`
- `target_value`
- `calculation_method`
- `enabled`
- `created_at`
- `updated_at`

### 5.3 `performance_trend_snapshots`

建议字段：

- `id`
- `employee_id`
- `date`
- `score`
- `grade`
- `period`
- `created_at`

---

## 6. 迁移策略

### 阶段 1：双实现并存

- 保留 `InMemoryPerformanceAssessmentService`
- 新增 JPA 持久化实现
- 通过 `@Primary` 或配置切换默认实现

### 阶段 2：评估结果落库

- 先确保 `assessEmployee(...)` 的结果持久化
- 再补查询接口从历史数据读取

### 阶段 3：排行与趋势基于历史数据

- 从历史评估记录生成排行
- 从历史评估记录生成趋势快照

### 阶段 4：保留内存版作为兜底

- 测试环境、离线模式、降级模式可继续使用

---

## 7. 对现有代码的影响

### 保持不变

- `PerformanceAssessmentService` 接口
- `PerformanceController` 的 API 协议
- `PerformanceDashboardService` 的调用方式

### 需要调整

- 将 `InMemoryPerformanceAssessmentService` 降为非默认实现
- 新增 JPA 持久化实体和仓库
- 排行和趋势逻辑改为读取持久化历史数据

---

## 8. 当前实现状态（新增）

目前已经落地的持久化组件：

- `PerformanceAssessmentEntity`
- `PerformanceAssessmentRepository`
- `PerformanceIndicatorEntity`
- `PerformanceIndicatorRepository`
- `PerformanceTrendSnapshotEntity`
- `PerformanceTrendRepository`
- `JpaPerformanceAssessmentService`

这意味着绩效生产化已进入“**可切换实现**”阶段，不再只是设计。

---

## 9. 当前兼容分支说明（新增）

`PerformanceDashboardService` 和 `PerformanceController` 仍然保留对 `InMemoryPerformanceAssessmentService` 的兼容分支，用于公司级 top/bottom 排行展示。

这属于**展示层兼容**，不是边界混层：

- 当前实现先保证展示稳定
- 后续若要完全统一，可把公司级排行能力下沉到 `PerformanceAssessmentService` 或单独的排名服务
- 在此之前，兼容分支可以保留

---

## 10. 推荐实现顺序

1. 先补充/校对趋势快照与排行的查询细节
2. 再统一 `JpaPerformanceAssessmentService` 的查询与写入规则
3. 最后决定是否把 `InMemoryPerformanceAssessmentService` 继续保留为默认兜底

---

## 11. 冲突避免说明（新增）

为避免和当前代码冲突，实施时遵循以下原则：

- **不新增重复的接口**：`PerformanceAssessmentService` 已存在，不再创建新的同名接口。
- **不改 controller 协议**：`PerformanceController` 继续使用现有请求/响应结构。
- **不改 bridge 调用方式**：`TaskPerformanceBridgeService` 继续通过 `PerformanceAssessmentService` 获取结果。
- **仅在 infrastructure 层新增持久化适配器**：通过实现现有接口完成替换。
- **不把排行逻辑塞回 controller**：排行/趋势应由 service 层生成。
- **与现有实体对齐**：`PerformanceAssessmentEntity` 已存在，后续实现应复用，而不是重复创建同名实体。
- **保留展示层兼容分支**：公司级排行在完全统一之前，可继续使用内存实现兼容方法，不视为边界冲突。

---

## 12. 验收标准

当该方案落地后，应满足：

- 评估结果不会因重启丢失
- 指标定义可持久化维护
- 排行榜可按历史数据计算
- 趋势可回放
- 看板数据稳定可追踪

---

## 13. 风险提示

- 不建议一次性替换所有绩效相关实现
- 不建议把排行逻辑塞回 controller
- 不建议把绩效和补偿存储强耦合成一个类

---

## 14. 结论

绩效评估是任务审核、补偿、员工管理、公司排行的重要基础数据，建议尽快生产化。  
优先顺序建议是：**评估结果先落库 → 指标定义落库 → 趋势与排行改为基于历史数据生成**。
