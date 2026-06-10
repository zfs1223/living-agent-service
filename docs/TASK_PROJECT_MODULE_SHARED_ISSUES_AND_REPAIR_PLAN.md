# 任务与项目管理模块共性问题及统一修复计划

> 日期：2026-05-18  
> 范围：任务管理、项目管理、项目任务子资源、前端任务/项目页面、WebSocket 续接、data 分类沉淀  
> 目标：把任务模块与项目管理模块中相同或相近的问题合并治理，避免分别修补导致两套生命周期、两套身份模型、两套数据沉淀规则继续分裂。

---

## 1. 总体结论

任务模块和项目管理模块都存在相同类型的问题：

```text
内存存储
缺少用户/租户/任务键/项目键绑定
缺少 execution/receipt/artifact 关联
和 WebSocket 续接链路未统一
和 data/documents 分类沉淀未统一
状态机不完整
权限模型较粗
前后端字段/状态不一致
子任务/项目任务没有真实接入统一任务服务
```

因此建议不要分别修复，而是建立统一的“工作项 WorkItem”或“任务/项目统一执行上下文”治理层。

任务和项目不是两个完全独立系统：

```text
Project 是上层容器
Task 是可执行工作单元
Execution 是一次执行实例
Receipt 是员工执行回执
Artifact 是执行产物
Memory/Data 是运行时沉淀
Document 是规范知识和业务文档
```

推荐统一关系：

```text
Project.projectId
  -> Task.projectId
      -> Task.taskKey
          -> Execution.executionId
              -> Receipt.executionId
              -> Artifact.executionId
              -> data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}
```

---

## 2. 已确认相关代码文件

### 2.1 任务模块

| 文件 | 当前作用 | 问题 |
| --- | --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java` | 任务 REST API 主入口 | 接入 `TaskCheckout`，状态机和身份字段不足 |
| `living-agent-core/src/main/java/com/livingagent/core/ops/scheduler/TaskCheckout.java` | 任务创建、领取、完成、释放、重派 | 使用内存 Map，缺少持久化、taskKey、executionId、tenant/user 绑定 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java` | Agent 任务 API | 当前为 mock/stub，容易误导使用方 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskWorkflowService.java` | 审核摘要 | 仅返回 Map，没有形成真实工作流状态流转 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskPerformanceBridgeService.java` | 审核后积分/风险/通知联动 | 有绩效联动，但前置任务状态机不完整 |
| `frontend/src/components/PublicTaskBoard.tsx` | 公共任务栏 | 可领取任务，但只覆盖人工领取任务 |
| `frontend/src/pages/MyTasks.tsx` | 我的任务 | 后端只返回 checkedOut，已完成/审核状态不完整 |
| `frontend/src/services/api.ts` | `taskApi` | 同时存在 agent task API 和 `/tasks` API，语义容易分裂 |

### 2.2 项目管理模块

| 文件 | 当前作用 | 问题 |
| --- | --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ProjectController.java` | 项目 REST API 主入口 | 项目任务子资源为 stub，权限不完整，字段与前端不一致 |
| `living-agent-core/src/main/java/com/livingagent/core/project/ProjectService.java` | 项目服务接口 | 缺少持久化语义、项目任务关系、项目事件流 |
| `living-agent-core/src/main/java/com/livingagent/core/project/impl/ProjectServiceImpl.java` | 项目服务实现 | 使用 `ConcurrentHashMap` 内存存储 |
| `living-agent-core/src/main/java/com/livingagent/core/project/Project.java` | 项目领域对象 | 缺少 tenantId、creatorUserId、projectKey、sourceTaskKey、executionId、dataNamespace |
| `living-agent-core/src/main/java/com/livingagent/core/project/ProjectPhaseRecord.java` | 项目阶段记录 | 阶段固定，缺少验收、交付物、审批关系 |
| `frontend/src/pages/Projects.tsx` | 项目页面 | 字段使用 `department_id/start_date/end_date/budget`，与后端 `ownerDepartment/managerId` 不一致 |
| `frontend/src/services/api.ts` | `projectApi` | `department_id` 查询参数与后端 `department` 不一致 |

### 2.3 自治执行与数据沉淀相关文件

| 文件 | 当前作用 | 需要统一接入点 |
| --- | --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java` | 对话任务入口和自治执行编排 | 创建/复用 taskKey、executionId 后应能回写任务/项目 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | 部门 WebSocket | 需要按 userId/taskKey/projectKey/executionId 做订阅和重连恢复 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeExecutionReceiptService.java` | 员工回执服务 | receipt 应反查 Task/Project |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ArtifactRecordService.java` | 产物记录服务 | artifact 应反查 Task/Project |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/AutonomyTraceService.java` | 自治 Trace | Trace 应落到统一 data namespace |

---

## 3. 共性问题对照

| 问题类型 | 任务模块表现 | 项目模块表现 | 是否同类修复 |
| --- | --- | --- | --- |
| 内存存储 | `TaskCheckout` 使用 Map | `ProjectServiceImpl` 使用 Map | 是，统一持久化 |
| 缺少租户/用户绑定 | Task 无 tenantId/userId | Project 无 tenantId/creatorUserId | 是，统一身份字段 |
| 缺少稳定归并键 | Task 无 taskKey | Project 无 projectKey | 是，统一 key 生成规则 |
| 缺少 execution 关联 | Task 无 executionId | Project 无 executionId/sourceTaskKey | 是，统一 Execution 关联 |
| 子任务不真实 | Task 与项目无关系 | `/projects/{id}/tasks` 为 stub | 是，项目任务接入 TaskService |
| WebSocket 不统一 | 任务无法续接 | 项目无实时进度/补发 | 是，统一 ConnectionRegistry |
| data 分类未落地 | Task 无标准 data 路径 | Project 无标准 data 路径 | 是，统一 data namespace |
| 状态机不完整 | submit 直接 complete | project complete 无验收 | 是，统一生命周期与验收 |
| 权限模型粗 | 只看 X-Employee-Id/brain | 部分接口无细粒度鉴权 | 是，统一 Access Policy |
| 前后端字段不一致 | 部分状态/历史查询不完整 | department_id/status 明显不一致 | 需要各自修复但统一规范 |

---

## 4. 统一修复目标模型

### 4.1 建议引入统一 WorkItem 概念

可选方案：

1. 保留 `Project` 和 `Task` 两个实体，但抽象出统一 `WorkItemContext`。
2. 新增 `WorkItemEntity` 作为项目/任务/执行的统一父级。
3. 先做轻量治理，不新建 WorkItem 表，只统一字段、事件、namespace 和关联关系。

建议先采用第 1 种：**保留项目/任务实体，新增统一上下文模型**。

```text
WorkItemContext
  tenantId
  ownerUserId
  departmentCode
  projectId
  projectKey
  taskId
  taskKey
  executionId
  sourceConversationId
  sourceSessionId
  dataNamespace
  documentNamespace
```

### 4.2 统一身份字段

任务和项目都应具备：

```text
tenantId
creatorUserId
ownerUserId
departmentCode
dataNamespace
createdAt
updatedAt
```

任务额外需要：

```text
taskKey
executionId
projectId
sourceConversationId
sourceSessionId
assignedEmployeeId
reviewerId
```

项目额外需要：

```text
projectKey
managerId
sourceTaskKey
sourceConversationId
workspaceId
```

---

## 5. 统一落盘与 data/documents 映射

### 5.1 data 运行时路径

```text
data/projects/{tenantId}/{projectId}/project.json
data/projects/{tenantId}/{projectId}/events.jsonl
data/projects/{tenantId}/{projectId}/phases/{phaseId}.json
data/projects/{tenantId}/{projectId}/tasks/{taskId}.json
data/projects/{tenantId}/{projectId}/artifacts/
data/projects/{tenantId}/{projectId}/summary.json

data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/session.json
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/events.jsonl
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/receipts/
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/artifacts/
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/summary.json

data/indexes/by-user/{userId}.json
data/indexes/by-project/{projectId}.json
data/indexes/by-task/{taskKey}.json
data/indexes/by-execution/{executionId}.json
```

### 5.2 documents 规范文档路径

运行时 `data` 不应替代 `documents`，二者职责不同：

```text
documents/department/{department}/projects/
documents/department/{department}/procedures/
documents/department/{department}/templates/
documents/shared/company/
documents/shared/governance/
```

关系：

```text
documents = 规范、模板、制度、沉淀后的正式知识
data      = 执行过程、事件、回执、临时产物、运行时状态
```

---

## 6. 统一生命周期建议

### 6.1 任务状态机

```text
PENDING
CLAIMED
IN_PROGRESS
SUBMITTED
PENDING_REVIEW
REVIEWED
COMPLETED
REJECTED
NEEDS_REWORK
FAILED
CANCELLED
```

### 6.2 项目状态机

```text
DRAFT
PLANNING
PENDING_APPROVAL
APPROVED
IN_PROGRESS
PHASE_REVIEW
NEEDS_REWORK
ON_HOLD
COMPLETED
ARCHIVED
CANCELLED
```

### 6.3 项目与任务关系

```text
Project COMPLETED 不能只靠手动 complete。
Project Phase COMPLETED 应依赖：
  - 阶段任务完成
  - 阶段交付物存在
  - 阶段验收通过
  - 必要审批通过

Task COMPLETED 不能只靠 submit。
Task submit 后应进入 PENDING_REVIEW。
review approved 后进入 COMPLETED。
review rejected 后进入 NEEDS_REWORK 或 REJECTED。
```

---

## 7. 统一修复文件计划

### 阶段 A：修复字段与前后端一致性 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| `frontend/src/services/api.ts` | `projectApi.list()` 参数从 `department_id` 统一为后端支持的 `department`，或后端同时兼容 `department_id` | 待实施 |
| `frontend/src/pages/Projects.tsx` | 项目创建字段对齐 `ownerDepartment/managerId` 或统一改成 `departmentId` | 待实施 |
| `ProjectController.java` | 返回字段兼容前端：`departmentId`、`status` 统一格式 | 待实施 |
| `TaskController.java` | `GET /employee/{employeeId}` 支持 status/includeHistory，返回 completed/reviewed | ✅ 已完成 |

### 阶段 B：抽象统一上下文 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `core/work/WorkItemContext.java` | 统一 project/task/execution 上下文 | ✅ 已完成 |
| 新增 `core/work/WorkItemKeyGenerator.java` | 统一生成 projectKey/taskKey | ✅ 已完成 |
| 新增 `gateway/service/WorkItemContextService.java` | 从 token、请求、WebSocket session 构造上下文 | ✅ 已完成 |
| 修改 `TaskController.java` | 创建/领取/提交任务时写入 WorkItemContext | ✅ 已完成 |
| 修改 `ProjectController.java` | 创建/更新项目时写入 WorkItemContext | ✅ 已完成 |
| 修改 `DepartmentChatService.java` | 对话任务创建 taskKey/executionId 后回写 WorkItemContext | ✅ 已完成 |

### 阶段 C：持久化任务和项目 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `core/database/entity/TaskEntity.java` | 持久化任务主表 | ✅ 已完成 |
| 新增 `core/database/entity/TaskEventEntity.java` | 任务事件流 | 待实施 |
| 新增 `core/database/entity/TaskSubmissionEntity.java` | 任务提交记录 | 待实施 |
| 新增 `core/database/entity/TaskReviewEntity.java` | 任务审核记录 | 待实施 |
| 新增 `core/database/entity/ProjectEntity.java` | 持久化项目主表 | ✅ 已完成 |
| 新增 `core/database/entity/ProjectPhaseEntity.java` | 持久化项目阶段 | 待实施 |
| 新增 `core/database/entity/ProjectMemberEntity.java` | 项目成员/权限 | 待实施 |
| 新增对应 Repository | 替换内存 Map 查询 | ✅ 已完成 |
| 修改 `TaskCheckout.java` | 降级为调度/领取服务，不再作为主存储 | ✅ 已完成（增加持久化同步） |
| 修改 `ProjectServiceImpl.java` | 从 `ConcurrentHashMap` 改为 Repository | ✅ 已完成（增加持久化同步） |

### 阶段 D：项目任务接入真实任务服务 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| `ProjectController.java` | `/projects/{projectId}/tasks` 改为查询真实 TaskService | ✅ 已完成 |
| `TaskController.java` | 创建任务支持 `projectId/projectPhaseId` | 待实施 |
| 新增 `ProjectTaskBridgeService.java` | 负责 Project 与 Task 的关联、状态汇总 | 待实施 |
| `ProjectServiceImpl.java` | 项目 progress 从真实任务/阶段验收计算，不再手动刷 | 待实施 |

### 阶段 E：WebSocket 与 execution 联动 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `gateway/websocket/ConnectionRegistry.java` | 统一连接/任务/项目/execution 映射 | ✅ 已完成 |
| 新增 `gateway/websocket/InMemoryConnectionRegistry.java` | 单实例实现，后续可替换 Redis | ✅ 已完成 |
| 修改 `DepartmentWebSocketHandler.java` | WebSocket 绑定 userId/taskKey/projectKey/executionId | ✅ 已完成 |
| 修改 `DepartmentChatService.java` | 创建 execution 时写入 Task/Project 关系 | ✅ 已完成 |
| 新增 `gateway/service/ExecutionReceiptTaskProjectBridge.java` | receipt 到达时反写 Task/Project 状态 | ✅ 已完成 |
| 修改 `ArtifactRecordService` 实现 | artifact 记录时关联 taskId/projectId | ✅ 已完成 |

### 阶段 F：data 分类沉淀 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `core/runtime/DataNamespaceService.java` | 生成标准 data 路径 | ✅ 已完成 |
| 新增 `core/runtime/RuntimeEventStore.java` | 写 `events.jsonl/session.json/summary.json` | ✅ 已完成 |
| 修改 `LivingAgentCoreConfig.java` | 注册 DataNamespaceService/RuntimeEventStore 为 Spring Bean | ✅ 已完成 |
| 修改 `TaskController.java` | 任务生命周期事件写入 RuntimeEventStore | ✅ 已完成 |
| 修改 `ProjectController.java` | 项目生命周期事件写入 RuntimeEventStore | ✅ 已完成 |
| 修改 `DepartmentChatService.java` | 对话执行事件写入 RuntimeEventStore | ✅ 已完成 |
| 修改 `ArtifactRecordService` | artifact 路径对齐 namespace，关联 taskId/projectId | ✅ 已完成 |

### 阶段 G：权限收敛 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `gateway/security/WorkItemPermissionService.java` | 统一判断项目/任务访问权限 | ✅ 已完成 |
| 修改 `TaskController.java` | 校验 owner、assignee、reviewer、tenant、department | ✅ 已完成 |
| 修改 `ProjectController.java` | 校验 owner、manager、member、tenant、department | ✅ 已完成 |
| 修改前端 API 调用 | 不再信任用户可手填 employeeId，后端以 token 为准 | ✅ 已完成 |

### 阶段 H：统一上下文 ✅ 已完成

| 文件 | 修复内容 | 状态 |
| --- | --- | --- |
| 新增 `core/work/WorkItemContext.java` | 统一 project/task/execution 上下文 | ✅ 已完成 |
| 新增 `core/work/WorkItemKeyGenerator.java` | 统一生成 projectKey/taskKey | ✅ 已完成 |
| 新增 `gateway/service/WorkItemContextService.java` | 从 token、请求、WebSocket session 构造上下文 | ✅ 已完成 |
| 修改 `TaskController.java` | 创建/领取/提交任务时写入 WorkItemContext | ✅ 已完成 |
| 修改 `ProjectController.java` | 创建/更新项目时写入 WorkItemContext | ✅ 已完成 |
| 修改 `DepartmentChatService.java` | 对话任务创建 taskKey/executionId 后回写 WorkItemContext | ✅ 已完成 |

---

## 8. 是否使用相同代码文件

### 8.1 当前没有使用相同主存储

当前任务和项目没有使用相同的主存储文件：

```text
Task 主存储：TaskCheckout.java 内存 Map
Project 主存储：ProjectServiceImpl.java 内存 Map
```

两者是并行实现，没有统一 Repository、统一 Entity、统一生命周期事件。

### 8.2 当前有相同的问题模式

虽然不是同一个代码文件，但问题模式相同：

```text
内存 Map
缺少租户/用户归属
缺少统一 key
缺少 execution 关联
缺少 data namespace
状态机粗糙
权限只做粗粒度路由
```

### 8.3 应该使用相同的基础设施

后续建议两者共享这些新文件/服务：

```text
WorkItemContext
WorkItemKeyGenerator
DataNamespaceService
RuntimeEventStore
ConnectionRegistry
WorkItemPermissionService
ProjectTaskBridgeService
```

这样任务和项目不必完全合并成一个类，但能共享身份、权限、事件、数据沉淀、WebSocket 续接、execution 关联。

---

## 9. 推荐实施顺序

1. **先修前后端字段和状态枚举不一致**：低风险、立刻提升可用性。
2. **新增 WorkItemContext / KeyGenerator / DataNamespaceService**：建立统一语义。
3. **新增 TaskEntity / ProjectEntity 持久化**：替换内存 Map。
4. **把 Project tasks 接入真实 TaskService**：消除 stub。
5. **把 DepartmentChatService 生成的 execution 回写 Task/Project**：统一自治执行和任务中心。
6. **引入 ConnectionRegistry 和 WebSocket 重连补发**：解决继续任务问题。
7. **补权限服务 WorkItemPermissionService**：解决跨用户/跨租户串数据问题。

---

## 10. 最终判断

任务和项目管理模块不应该继续分开打补丁。它们应该共享统一的工作项上下文、统一持久化关联、统一 WebSocket/execution/data 沉淀规则。

当前状态可以概括为：

```text
任务模块：人工任务领取闭环基本可用，但未接入完整自治执行和记忆沉淀。
项目模块：项目 CRUD/阶段演示可用，但项目任务、持久化、生命周期、权限、数据沉淀都未完整落地。
共性根因：缺少统一 WorkItemContext、持久化实体、事件流、data namespace、细粒度权限和 execution 关联。
```
