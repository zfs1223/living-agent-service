# Memory 系统说明（按当前代码实现更新）

> 本文档描述的是 Living Agent Service 中 **底层记忆能力** 的现状与边界，重点区分：
>
> - **Memory**：底层记忆存储/检索基础设施
> - **Knowledge**：企业知识治理层
> - **Evolution / Task / Performance**：业务闭环与治理层
>
> 当前代码已经不再把“长期记忆”和“知识治理”混为一层，因此本文档也同步按实际实现进行收敛。

---

## 1. 记忆系统定位

### 1.1 当前职责

Memory 系统负责：

- 存储短期与长期记忆
- 提供快速检索与回收
- 支持多后端路由与降级
- 作为业务闭环的底层数据基础设施

### 1.2 不再承担的职责

Memory 系统**不直接承担**以下职责：

- 知识晋升与知识治理
- 任务审核与绩效核算
- 进化反馈与业务通知
- 员工编制与生命周期治理

这些职责由各自的业务服务层负责。

---

## 2. 当前代码中的记忆相关实现

### 2.1 已存在的核心接口/模型

当前代码中，记忆能力主要由以下接口/实现构成：

- `com.livingagent.core.memory.Memory`
- `com.livingagent.core.memory.MemoryBackend`
- `com.livingagent.core.memory.MemoryCategory`
- `com.livingagent.core.memory.MemoryEntry`
- `com.livingagent.core.memory.impl.MemoryServiceImpl`
- `com.livingagent.core.memory.impl.SQLiteMemoryBackend`
- `com.livingagent.core.memory.impl.MemosMemoryBackend`
- `com.livingagent.core.memory.impl.MemPalaceBackend`

### 2.2 当前支持的能力

#### Memory 接口能力

- `store(...)`
- `recall(...)`
- `get(...)`
- `list(...)`
- `forget(...)`
- `count(...)`
- `healthCheck(...)`

#### MemoryBackend 能力

- `initialize()`
- `store(...)`
- `recall(...)`
- `get(...)`
- `list(...)`
- `forget(...)`
- `count(...)`
- `healthCheck()`
- `close()`

---

## 3. 当前架构边界

### 3.1 底层记忆基础设施

Memory 层主要关注：

- 读写存储
- 快速检索
- 后端适配
- 降级与健康检查
- 会话级/类别级索引

### 3.2 企业知识治理层

知识治理由以下模块承担：

- `KnowledgeManager`
- `KnowledgeGovernanceService`
- `KnowledgePromotionAuditService`
- `KnowledgeController`

它们负责：

- 知识写入与检索
- 作用域治理
- 知识晋升与审计
- 回滚与清理
- 质量评估

### 3.3 业务闭环层

业务结果与治理闭环由以下模块承担：

- `TaskController`
- `TaskEventBridgeService`
- `TaskPerformanceBridgeService`
- `PerformanceController`
- `PerformanceDashboardService`
- `EvolutionAdminController`
- `EvolutionFeedbackBridgeService`
- `DashboardDataService`

这些模块会把业务事件回写到：

- 告警
- 风险
- 绩效
- 补偿
- 知识
- 仪表盘

---

## 4. 当前 Memory 体系与代码实现的关系

### 4.1 记忆后端的定位

当前 `memory.md` 所描述的多后端架构，可以理解为：

- **SQLite**：本地兜底
- **MemOS**：长期记忆/外部记忆服务
- **MemPalace**：快速记忆/会话记忆服务

### 4.2 与知识系统的关系

需要明确：

- `Memory` 负责“底层记忆存储和检索”
- `Knowledge` 负责“结构化治理后的知识资产”

两者可以同步、桥接、互相导入，但**不应混为同一层**。

### 4.3 与企业语义层的关系

业务层的任务、绩效、进化、员工生命周期等模块，可能会读取或写入记忆，但它们自身不是 Memory 模块的一部分。

---

## 5. 当前建议的分类方式

### 5.1 底层记忆分类

建议 Memory 层只保留以下语义：

- `CORE`：核心持久记忆
- `DAILY`：日常快速记忆
- `CONVERSATION`：会话记忆
- `CUSTOM`：自定义兜底

### 5.2 企业知识分类

知识系统建议使用：

- `KnowledgeType`
- `KnowledgeScope`
- `Importance`
- `Validity`
- `brainDomain`
- `neuronId`

### 5.3 业务事件分类

任务/绩效/进化建议使用各自领域模型，不复用 Memory 分类作为业务语义。

---

## 6. 当前实现状态总结

### 6.1 已完成

- 底层记忆接口已存在
- 多后端能力已具备
- 记忆与外部服务的路由设计已明确
- 主业务闭环已从记忆层中剥离出来

### 6.2 仍需注意

- `memory.md` 中的“长期记忆 = 企业知识”说法需要避免
- `memory` 的路由分类与 `knowledge` 的治理分类不要混用
- 若后续继续扩展记忆系统，应优先保持“基础设施层”的边界清晰

---

## 7. 与当前代码一致的推荐配置口径

建议继续保持下列方向：

- `Memory` 负责底层读写和检索
- `Knowledge` 负责知识资产治理
- `Evolution / Task / Performance` 负责业务结果与反馈
- `Dashboard` 负责聚合展示

---

## 8. 结论

`memory.md` 现在应被视为 **底层记忆基础设施说明**，而不是企业知识系统说明。

如果后续继续优化，重点不是增加更多“记忆概念”，而是：

1. 保持 Memory 层边界清晰
2. 避免和 Knowledge 层重复定义
3. 保持多后端路由、降级和健康检查的稳定性
4. 将业务闭环沉淀到各自领域服务中
