# 自动进化落地设计

> 按文件分组的开发清单：用于直接修改代码。
>
> 说明：自动进化主干已经存在，当前要做的不是重写，而是把统计、决策、候选模型、调度和回滚审计补稳。

---

## 1. `living-agent-core/src/main/java/com/livingagent/core/evolution/executor/impl/JpaEvolutionFeedbackService.java`

### 1.1 `record(EvolutionResult result)`

#### 修改目标
把反馈记录时的 brain 维度、失败计数、统计元数据补齐，避免后续 `statistics()` 只能看到不稳定维度。

#### 需要做的事
- 统一从 `result.getSignal()` 读取 `brainDomain`
- 若 `result.getSignal()` 为空，再从 `result.getMetadata().get("brainId")` 兜底
- 记录 feedback 时保留：
  - `brainId`
  - `brainType`
  - `department`
  - `action`
  - `status`
  - `executionTimeMs`
  - `errorMessage`
- 更新连续失败计数时，不能落到 `unknown` 桶里就结束，要尽量归一到同一 brain

#### 依赖关系
- `EvolutionResult`
- `EvolutionSignal`
- `EvolutionResultEntity`
- `EvolutionFeedbackEntity`

#### 验收标准
- 同一 brain 连续失败能正确累计，成功后能正确清零
- 同一条结果在 domain / entity / API 输出中都能拿到一致的 brain 标识

### 1.2 `recent(int limit)`

#### 修改目标
保证 recent 返回的数据里，包含可直接用于按 brain 统计和调试的必要字段。

#### 需要做的事
- 保持返回顺序稳定
- 确保 `EvolutionResultEntity.toDomain()` 不丢失 brain 维度
- 若 entity 侧缺字段，优先补 entity 映射，不要在 service 层临时拼接

#### 依赖关系
- `EvolutionResultRepository`
- `EvolutionResultEntity.toDomain()`

#### 验收标准
- recent 结果能直接喂给自动调整逻辑，不需要额外猜 brain 来源

### 1.3 `statistics()`

#### 修改目标
把 `by_brain` 的聚合维度校准为稳定 brain 维度，而不是不稳定来源。

#### 需要做的事
- `by_brain` 优先按 `brainId` / `brainDomain` 分组
- 每个 brain 统计至少保留：
  - `sampleSize`
  - `success_rate`
  - `failureRate`
  - `avgScore`
  - `avgResponseTimeMs`
  - `consecutiveFailures`
  - `brainType`
  - `department`
- 不要让 `signalId` 继续充当 brain 分组键
- `avgScore`、`avgResponseTimeMs` 的命名要在文档里固定

#### 依赖关系
- `EvolutionResultEntity`
- `EvolutionFeedbackEntity`
- `EvolutionResult`

#### 验收标准
- `stats.by_brain` 能稳定按单个 brain 聚合，不会串到别的 brain

---

## 2. `living-agent-core/src/main/java/com/livingagent/core/evolution/engine/EvolutionOrchestrator.java`

### 2.1 `runAutoAdjust(String brainId)`

#### 修改目标
把自动调整收敛成“按单 brain 决策 + 候选不足则跳过”，避免强切。

#### 需要做的事
- `brainId != null` 时只处理单个目标 brain
- `brainId == null` 时再批量处理所有 brain
- 先读取目标 brain 当前绑定模型作为基线
- 低分时先做候选筛选，再决定是否替换
- 候选不足时返回 `skipped`，不要强切
- 返回结果中统一包含：
  - `status`
  - `score`
  - `reason`
  - `oldModelId`
  - `newModelId`
  - `strategy`

#### 依赖关系
- `JpaEvolutionFeedbackService.statistics()`
- `BrainModelAssigner`
- `BrainModelSelectorManager`
- `ModelPoolManager`

#### 验收标准
- 手工触发自动调整后，只会处理目标 brain
- 没有合格候选模型时，不会强制替换当前绑定

### 2.2 `selectStrategy(EvolutionSignal signal)`

#### 修改目标
策略选择不再偏全局平均值，而是优先读取目标 brain 的统计快照。

#### 需要做的事
- 优先获取当前 brain 的 `avgScore`、`avgResponseTimeMs`、`consecutiveFailures`
- 根据单 brain 统计决定：
  - `REPLACE_MODEL`
  - `DOWNGRADE_MODEL`
  - `UPGRADE_MODEL`
  - `ESCALATE_TO_ADMIN`
  - `DEFER`
- 保留现有阈值，但阈值命中逻辑应建立在单 brain 数据上

#### 依赖关系
- `JpaEvolutionFeedbackService.statistics()`
- `EvolutionSignal`

#### 验收标准
- 不同 brain 在相似全局环境下可以得到不同策略
- 主大脑不会被别的 brain 的异常统计带偏

### 2.3 `rollbackBrain(String brainId)`

#### 修改目标
只回滚到最近一次手工配置基线，历史来源必须明确区分。

#### 需要做的事
- 从 `BrainModelChangeHistoryRepository` 取最近历史
- 只认 `source = manual` 的记录作为回滚基线
- 如果没有 manual 基线，返回明确失败原因
- 回滚成功后要再记录一条 `source = rollback` 的历史

#### 依赖关系
- `BrainModelChangeHistoryRepository`
- `BrainModelAssigner`
- `BrainModelChangeHistory`

#### 验收标准
- `rollbackBrain(...)` 可恢复到最近一次手工配置
- 不会回滚到另一条自动配置结果

### 2.4 `score(EvolutionResult result)`

#### 修改目标
保持评分逻辑可解释，并与反馈统计的字段口径一致。

#### 需要做的事
- 保持 `userRating`、`responseTime`、`success` 的加权逻辑
- 统一默认值口径，避免和 `statistics()` 的统计命名冲突
- 若后续要调阈值，先在文档中固定字段意义

#### 依赖关系
- `EvolutionResult`

#### 验收标准
- 评分逻辑可被自动调整结果稳定使用，并与统计字段口径一致

---

## 3. `living-agent-core/src/main/java/com/livingagent/core/model/selector/BrainModelSelectorManager.java`

### 3.1 候选选择入口

#### 修改目标
提供统一候选模型入口，避免 orchestrator 自己硬编码过滤规则。

#### 需要做的事
- 提供按 `brainId` / `department` / `brainType` 获取 selector 的统一入口
- 封装候选模型过滤流程
- 如果已有 `selectBestCandidateModel(...)`，则把规则集中到 manager / selector 内部

#### 依赖关系
- `BrainModelSelector`
- 各具体 selector 实现
- 模型池元数据

#### 验收标准
- orchestrator 不需要写一堆硬编码分支去判断候选模型

### 3.2 选择器注册

#### 修改目标
让 selector 注册和查找更加稳定。

#### 需要做的事
- 确保新增 selector 后能自动被 manager 识别
- 若有缓存，提供刷新或重建能力

#### 依赖关系
- Spring 容器
- `BrainModelSelectorRegistrar`（如存在）

#### 验收标准
- 新增 selector 后无需在 orchestrator 中额外补分支即可生效

---

## 4. `living-agent-core/src/main/java/com/livingagent/core/model/selector/BrainModelSelector.java`

### 4.1 选择器契约扩展

#### 修改目标
让每个 brain selector 能表达自己的候选过滤和打分规则。

#### 需要做的事
- 为 selector 契约补齐：
  - 是否支持自动调整
  - 候选打分
  - 候选过滤
  - 是否支持某个 brainType / department
- 不要只停留在“给当前模型打分”这一层

#### 依赖关系
- 各具体 selector

#### 验收标准
- 每个 brain 的模型优先级可以通过 selector 自身表达，而不是 orchestrator 硬编码

---

## 5. 各 `*BrainModelSelector.java`

### 5.1 各 brain 选择器的具体规则

#### 修改目标
把主大脑 / 技术大脑 / 财务大脑等场景的模型优先级与适配规则落实到 selector 里。

#### 需要做的事
- 补充各 brain 的能力标签偏好
- 区分推荐模型、可用模型、自动调整可用模型
- 按 brainType 做差异化过滤与评分

#### 依赖关系
- `BrainModelSelector` 契约
- 模型池 metadata

#### 验收标准
- 不同 brain 自动调整时，能选出符合职责的候选模型

---

## 6. `living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelAssigner.java`

### 6.1 `assignModel(...)`

#### 修改目标
把模型写回的来源、基线、fallback 规则收紧。

#### 需要做的事
- 明确自动调整与手工调整的写入来源
- 写回时保留必要的来源信息
- 若当前模型为空，fallback 规则要可解释

#### 依赖关系
- 变更历史表
- 模型池查询

#### 验收标准
- 自动切换后绑定结果能正确落库
- 清空绑定后 fallback 行为可预测

### 6.2 `getModelForBrain(...)`

#### 修改目标
让 orchestrator 能稳定拿到当前 brain 的基线模型。

#### 需要做的事
- 保证返回当前绑定模型的语义稳定
- 若无绑定，返回可解释的 fallback

#### 依赖关系
- assignment 数据

#### 验收标准
- `runAutoAdjust(...)` 可以用该方法拿到当前基线模型

---

## 7. `living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelChangeHistory.java`

### 7.1 历史字段语义统一

#### 修改目标
把历史表里的来源、操作者、原因统一规范，支持审计和回滚。

#### 需要做的事
- 确保 `source` 只使用约定值：`manual` / `auto` / `rollback`
- `reason` 要足够描述为何切换
- `changedBy` 要能区分系统和人工

#### 依赖关系
- orchestrator
- assigner
- history repository

#### 验收标准
- 历史记录能清晰看出每次变更的来源与原因

---

## 8. `living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelChangeHistoryRepository.java`

### 8.1 最近 manual 基线查询

#### 修改目标
补一个专用查询，避免回滚只能“拿最近一条”，无法精准回到最近 manual 配置。

#### 需要做的事
- 增加按 `brainId + source=manual` 的查询方法
- 需要时支持按时间倒序查最近一条 manual 基线

#### 依赖关系
- `BrainModelChangeHistory`

#### 验收标准
- `rollbackBrain(...)` 能稳定拿到最近一次手工配置基线

---

## 9. `living-agent-core/src/main/java/com/livingagent/core/evolution/scheduler/EvolutionSchedulerImpl.java`

### 9.1 `runHourlyAdjustment()`

#### 修改目标
让定时自动调整可观测、可追踪、可区分成功/失败/跳过。

#### 需要做的事
- 输出处理了哪些 brain
- 输出跳过原因
- 输出失败分类
- 汇总本轮调整结果

#### 依赖关系
- `EvolutionOrchestrator.runAutoAdjust(...)`

#### 验收标准
- 日志里能看出每轮调度处理了什么、跳过什么、失败什么

### 9.2 `retryFailedTasks()`

#### 修改目标
限制失败重试次数，避免无限重试。

#### 需要做的事
- 保持重试上限
- 失败任务要区分真失败与可重试失败
- 更新状态时保留重试次数

#### 依赖关系
- `EvolutionResultRepository`

#### 验收标准
- 不会无限循环重试同一条失败任务

### 9.3 `cleanupExpiredFeedback()`

#### 修改目标
清理过期反馈时不要误删回滚和审计所需数据。

#### 需要做的事
- 明确清理范围和保留策略
- 如有必要，区分 feedback 与 audit/history 的保留策略

#### 依赖关系
- `EvolutionResultRepository`

#### 验收标准
- 清理后，近期自动调整和回滚所需历史仍可查

---

## 10. `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EvolutionAdminController.java`

### 10.1 `triggerAutoAdjust(...)`

#### 修改目标
返回统一、可直接消费的结果结构。

#### 需要做的事
- 返回每个 brain 的 adjusted / skipped / error 结果
- 结果里带上 reason、oldModelId、newModelId、strategy 等字段

#### 依赖关系
- `EvolutionOrchestrator.runAutoAdjust(...)`

#### 验收标准
- 后台页面或调试脚本不需要额外猜字段就能使用返回值

### 10.2 `rollbackBrain(...)`

#### 修改目标
回滚失败时返回明确原因，不要只有模糊错误。

#### 需要做的事
- 明确返回 no_history_found / no_manual_baseline / assign_failed 等状态
- 回滚成功时返回 restoredModelId / restoredModelName

#### 依赖关系
- `EvolutionOrchestrator.rollbackBrain(...)`

#### 验收标准
- 回滚失败时能准确知道失败原因

### 10.3 `getEvolutionHistory(...)`

#### 修改目标
历史查询结果要统一，方便前端展示和审计导出。

#### 需要做的事
- 返回 items + stats + total
- 与 recent / statistics 的字段口径对齐

#### 依赖关系
- `EvolutionFeedbackService`

#### 验收标准
- 前端或接口调用方可以直接拿来做列表和统计展示

---

## 11. 推荐修改顺序

1. 先改 `JpaEvolutionFeedbackService.java`，把统计输入做准；
2. 再改 `EvolutionOrchestrator.java`，把自动调整与回滚逻辑收紧；
3. 再补 `BrainModelSelectorManager.java`、`BrainModelSelector.java` 和各 selector；
4. 再收紧 `BrainModelAssigner.java` 与 `BrainModelChangeHistoryRepository.java`；
5. 最后补 `EvolutionSchedulerImpl.java` 与 `EvolutionAdminController.java` 的状态输出。

---

## 12. 当前状态一句话总结

自动进化已经不是“待设计”，而是“待按文件逐项修复”。

**先修统计，再修决策，再修候选，再修回滚与调度。**
