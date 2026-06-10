# `memory.md` / `knowledge` 边界修复建议

> 目的：在不破坏当前底层记忆能力的前提下，进一步收敛 `docs/memory.md`、`Knowledge`、`Evolution`、`Task`、`Performance` 之间的边界，并为后续代码修复提供可执行顺序。
>
> 本文档是**修复路线图**，不是单纯的概念说明。

---

## 1. 当前问题概述

经过与当前代码对照后，可以确认：

- `Memory` 已经更明确地承担底层记忆存储/检索职责
- `Knowledge` 已经独立为企业知识治理层
- `Evolution` / `Task` / `Performance` 已经成为业务闭环层
- 但文档与代码之间仍需要进一步保持一致，避免概念再次混层

当前最容易出问题的地方是：

1. 把长期记忆直接等同于企业知识
2. 把 Memory 分类当作业务语义分类
3. 把知识治理职责塞回底层记忆层
4. 把记忆、知识、业务闭环的数据源在看板里混成一类

---

## 2. 与当前代码的边界对照

### 2.1 Memory 层

当前代码中的 Memory 层包括：

- `com.livingagent.core.memory.Memory`
- `com.livingagent.core.memory.MemoryBackend`
- `com.livingagent.core.memory.MemoryCategory`
- `com.livingagent.core.memory.MemoryEntry`
- `com.livingagent.core.memory.impl.MemoryServiceImpl`
- `com.livingagent.core.memory.impl.SQLiteMemoryBackend`
- `com.livingagent.core.memory.impl.MemosMemoryBackend`
- `com.livingagent.core.memory.impl.MemPalaceBackend`

#### 责任

- 底层存储
- 检索
- 路由
- 降级
- 同步
- 健康检查

#### 不应承担

- 知识晋升
- 知识审计
- 任务绩效
- 业务通知
- 员工治理

---

### 2.2 Knowledge 层

当前代码中的知识层包括：

- `KnowledgeEntry`
- `KnowledgeManager`
- `KnowledgeGovernanceService`
- `KnowledgePromotionAuditService`
- `KnowledgeController`

#### 责任

- 知识资产管理
- 作用域治理
- 晋升/回滚/历史
- 清理与质量评估

---

### 2.3 业务闭环层

当前代码中的业务闭环层包括：

- `TaskController`
- `TaskEventBridgeService`
- `TaskPerformanceBridgeService`
- `PerformanceController`
- `PerformanceDashboardService`
- `EvolutionAdminController`
- `EvolutionFeedbackBridgeService`
- `DashboardDataService`
- `EmployeeController`
- `EmployeeLifecycleService`
- `EmployeeLifecycleServiceImpl`

#### 责任

- 任务执行与审核
- 奖励与惩罚
- 风险更新
- 通知联动
- 绩效评估与排行
- 进化结果回写
- 仪表盘聚合
- 员工生命周期与编制视图

---

## 3. 需要立即收紧的边界

### 3.1 不要再把“长期记忆”写成“企业知识”

#### 问题
旧写法容易把 MemOS 和 KnowledgeManager 混成一层。

#### 修复目标
统一成：

- MemOS = 长期记忆基础设施
- Knowledge = 企业知识治理层

#### 涉及文件

- `docs/memory.md`
- `docs/old/04-knowledge-system.md` 的对照说明
- 任何提到 MemOS / Knowledge 的说明文档

---

### 3.2 `MemoryCategory` 只能做底层路由

#### 问题
`MemoryCategory` 不应被用作任务、绩效、知识的业务分类。

#### 修复目标
明确：

- `MemoryCategory` 只表示底层记忆用途
- 业务分类由各自领域模型表达

#### 涉及文件

- `docs/memory.md`
- `MemoryServiceImpl`
- `MemPalaceBackend`
- `MemosMemoryBackend`

---

### 3.3 知识治理职责不要回流到 Memory 层

#### 问题
记忆层和知识层的职责容易在桥接代码里重叠。

#### 修复目标
任何“知识晋升 / 审计 / 回滚 / 清理”只能进入：

- `KnowledgeManager`
- `KnowledgeGovernanceService`
- `KnowledgePromotionAuditService`

#### 涉及文件

- `EvolutionFeedbackBridgeService`
- `DashboardDataService`
- `KnowledgeGovernanceService`
- `KnowledgePromotionAuditService`

---

### 3.4 看板不要把 Memory 和 Knowledge 混成同一指标

#### 问题
仪表盘容易把底层记忆和知识资产并列为一类数据源。

#### 修复目标
看板中应区分：

- Memory 指标：底层存储、检索、路由、健康、同步
- Knowledge 指标：条目数、晋升数、作用域、质量、回滚
- Business 指标：任务、绩效、进化、补偿、通知、员工

#### 涉及文件

- `DashboardDataService`
- `DashboardController`
- `PerformanceDashboardService`
- `KnowledgeGovernanceService`

---

## 4. 推荐修复顺序

### Step 1：先稳住文档边界

- 保持 `docs/memory.md` 为底层记忆说明
- 避免再次把 Memory 写成知识层
- 统一“长期记忆基础设施”表述

### Step 2：检查桥接层是否越界

重点检查：

- `EvolutionFeedbackBridgeService`
- `TaskEventBridgeService`
- `TaskPerformanceBridgeService`
- `DashboardDataService`

要求：

- 业务结果只回写到对应业务层
- 不要把记忆层逻辑混进治理层

### Step 3：检查知识层是否被记忆层污染

重点检查：

- 是否有记忆对象被直接当作知识对象持久化
- 是否所有知识晋升都经过治理层
- 是否知识回滚和历史记录完备

### Step 4：再考虑底层记忆生产化

- 保留 SQLite 兜底
- 明确 MemOS / MemPalace 的定位
- 补同步、降级、健康检查测试

---

## 5. 文件级修复建议

### 5.1 `docs/memory.md`

建议继续保持为：

- 底层记忆基础设施说明
- 多后端路由说明
- 与 Knowledge 的边界说明

### 5.2 `docs/old/04-knowledge-system.md`

建议作为历史基线保留，但需要补充：

- 这是旧设计快照
- 当前代码已更细化边界
- 推荐对照当前知识治理实现阅读

### 5.3 `docs/04-knowledge-system.current.md`

建议作为当前代码对照版继续维护，确保：

- L1 / L2 / L3 与 PRIVATE / DOMAIN / SHARED 对齐
- 晋升与复制的区别明确
- 知识、记忆、文档边界清晰
- 与当前 `KnowledgeEntry` 字段一致

### 5.4 `DashboardDataService`

建议后续进一步拆出更明确的指标分组：

- memoryMetrics
- knowledgeMetrics
- businessMetrics
- operationMetrics

---

## 6. 验收标准

当本建议对应的修复完成后，应满足：

1. `Memory` 文档只描述底层记忆
2. `Knowledge` 文档只描述知识治理
3. 业务桥接层不会把记忆和知识混用
4. 看板指标能明确区分三类数据源
5. 代码中不会再出现“长期记忆 = 企业知识”的描述

---

## 7. 风险提示

- 不建议一次性重构全部记忆/知识代码
- 不建议直接删除旧文档
- 不建议把所有底层记忆能力都塞回知识系统
- 不建议将业务闭环重新写回 Memory 层

---

## 8. 结论

当前最重要的不是增加更多记忆能力，而是：

- **先把 Memory 与 Knowledge 的边界彻底收紧**
- **再把桥接层语义统一**
- **最后再推进底层记忆生产化**

这样后续无论修代码还是修文档，都能保持一致，不再出现概念混层。
