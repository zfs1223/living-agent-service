# 任务模块代码路径问题与修复优先级

> 日期：2026-05-18  
> 范围：`TaskController`、`TaskCheckout`、`AgentTaskController`、前端公共任务栏、我的任务页  
> 结论：当前任务模块可完成“公共任务创建 → 领取 → 提交 → 审核 → 积分/绩效联动”的基础闭环，但还不能完成“同一用户同一任务统一关联、WebSocket 可续接、data 分类沉淀、execution/receipt/artifact 全链路闭环”的完整企业任务系统。

---

## 1. 已检查的主要代码路径

### 1.1 后端真实任务 API

- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java`
- `living-agent-core/src/main/java/com/livingagent/core/ops/scheduler/TaskCheckout.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskWorkflowService.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskPerformanceBridgeService.java`

`TaskController` 当前提供：

```text
GET  /api/tasks
POST /api/tasks
GET  /api/tasks/{taskId}
POST /api/tasks/{taskId}/checkout
POST /api/tasks/{taskId}/complete
POST /api/tasks/{taskId}/release
POST /api/tasks/{taskId}/reassign
GET  /api/tasks/statistics
GET  /api/tasks/pending
GET  /api/tasks/employee/{employeeId}
GET  /api/tasks/public
POST /api/tasks/{taskId}/claim
POST /api/tasks/{taskId}/submit
POST /api/tasks/{taskId}/review
```

### 1.2 Agent 任务 API

- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java`

该 Controller 当前更像 mock/stub：

- `listTasks()` 返回固定 `task_001`
- `createTask()` 只构造返回对象，不持久化
- `getTask()` 返回固定“任务详情”
- `getTaskLogs()` 返回固定“任务创建”
- `triggerTask()` 只返回 `triggered`

### 1.3 前端任务页面

- `frontend/src/components/PublicTaskBoard.tsx`
- `frontend/src/pages/MyTasks.tsx`
- `frontend/src/services/api.ts`

前端当前调用 `/api/tasks` 这一套真实任务接口，而不是 `/api/agents/{agentId}/tasks` 这一套 stub 接口。

---

## 2. 当前可以完成的基础闭环

当前任务模块可以跑通以下人工任务闭环：

```text
管理员/系统创建任务
-> 任务进入 pendingTasks
-> 公共任务栏展示 /api/tasks/public
-> 用户领取任务 /api/tasks/{taskId}/claim
-> 任务从 pendingTasks 移到 checkedOutTasks
-> 我的任务页面展示 /api/tasks/employee/{employeeId}
-> 用户提交结果 /api/tasks/{taskId}/submit
-> TaskCheckout.completeTask() 标记 COMPLETED
-> 审核接口 /api/tasks/{taskId}/review
-> TaskPerformanceBridgeService 记录积分、风险指标、部门通知
```

因此，“任务市场 / 任务领取 / 人工提交 / 审核奖励”这个小闭环基本存在。

---

## 3. 当前无法完成的完整闭环

按企业自治任务系统的要求，完整闭环应该是：

```text
用户对话任务
-> 生成 userId + taskKey
-> WebSocket 绑定 session/user/task/execution
-> 创建或复用 execution
-> 派发员工执行
-> receipt/artifact/progress 统一挂到 execution
-> WebSocket 断线可重连补发
-> data 按 documents 同构分类沉淀
-> 知识/绩效/记忆按用户和任务归档
```

当前任务模块尚未完全打通这条链路。

---

## 4. 主要问题清单

### P0-1：任务没有 `userId/taskKey/executionId` 统一关联

`TaskCheckout.Task` 当前字段为：

```text
taskId
taskType
description
priority
requiredCapability
context
status
createdAt
checkedOutAt
assignedTo
completedAt
```

缺少一等字段：

```text
userId
tenantId
taskKey
executionId
conversationId
departmentCode
sourceSessionId
```

虽然 `context` 可以临时塞这些值，但这不是可靠建模，会影响同一用户同一任务的统一关联与后续恢复。

### P0-2：任务状态只在内存 Map 中

`TaskCheckout` 使用：

```text
pendingTasks
checkedOutTasks
completedTasks
checkoutRecords
employeeTaskCounts
```

这些都是 `ConcurrentHashMap`，存在以下风险：

- 服务重启任务丢失
- 无法跨实例部署
- 无法长期记忆
- 无法和 `data/` 分类沉淀对齐
- 无法稳定恢复 WebSocket 任务状态

### P0-3：任务模块和 WebSocket/execution 体系未统一

当前存在两条并行链路：

```text
任务市场链路：TaskController -> TaskCheckout
自治执行链路：DepartmentChatService -> autonomy execution/receipt/artifact
```

风险：

- 任务中心看不到对话任务的执行状态
- WebSocket 任务续接不一定反映到 `/api/tasks`
- receipt/artifact 不会自然进入“我的任务”
- 任务审核和员工执行验收割裂

### P0-4：前端“我的任务”只能看到 checkedOutTasks

`MyTasks.tsx` 调用：

```text
taskApi.getEmployeeTasks(employeeId)
```

后端对应：

```text
GET /api/tasks/employee/{employeeId}
```

但该接口只返回：

```text
taskCheckout.getCheckedOutTasks(employeeId)
```

导致已完成任务不会被返回，前端“已完成”tab 可能为空或不完整。

### P0-5：`submitTask()` 直接 complete，缺少 PENDING_REVIEW 状态

前端提交后显示“等待审核”，但后端 `submitTask()` 实际调用：

```text
TaskResult.success(...)
taskCheckout.completeTask(...)
```

任务状态立即变为 `COMPLETED`。

当前 `TaskStatus` 缺少：

```text
SUBMITTED
PENDING_REVIEW
REVIEWED
REJECTED
NEEDS_REWORK
```

因此 submit、review、complete 的语义混在一起。

### P0-6：`AgentTaskController` 是 stub，容易误导 API 使用方

`AgentTaskController` 路径看起来像正式 API，但目前没有真实持久化或执行联动。

建议：

- 标记 deprecated/mock；或
- 接入统一任务服务；或
- 从 API 文档中明确为 legacy/demo 接口。

### P1-1：任务创建没有自动进入自治执行

`TaskController.createTask()` 只创建 pending task，不会：

- 触发主脑规划
- 触发部门路由
- 自动派发固定员工
- 创建 execution
- 创建 receipt channel
- 写 artifact
- 通过 WebSocket 推送进度

因此它是“人工领取型任务”，不是“数字员工自治执行型任务”。

### P1-2：任务完成缺少 artifact/result 结构化落盘

`submitTask()` 只有 `result` 文本，最终进入 `TaskResult.output()`。

缺少关联：

- artifact 文件
- receipt
- execution trace
- final summary
- review conclusion
- `data/conversations` 路径

### P1-3：权限模型较粗

多数接口只通过：

```text
X-Employee-Id -> accessGateService.canRoute(employeeId, "brain", "OpsBrain")
```

缺少以下校验：

- 当前用户是否是任务 owner
- 当前用户是否是任务领取人
- 是否同租户
- 是否同部门
- 是否有审核权限
- token 解析用户是否与请求中的 `employeeId` 一致

---

## 5. 修复优先级建议

### 第一优先级：统一任务身份字段

任务模型至少应包含：

```text
tenantId
userId
taskKey
executionId
departmentCode
sourceType
sourceSessionId
conversationId
```

建议不要只放在 `context` 中，而是升级为正式字段或引入新的 `TaskEntity`。

### 第二优先级：持久化任务

建议新增：

```text
TaskEntity
TaskRepository
TaskService
TaskEventEntity
TaskSubmissionEntity
TaskReviewEntity
```

让 pending、checked_out、submitted、completed、reviewed 都可重启恢复。

### 第三优先级：合并 TaskCheckout 与 autonomy execution

建立统一桥接关系：

```text
Task.taskId
Task.taskKey
Task.executionId
Execution.executionId
Receipt.executionId
Artifact.executionId
```

使对话任务能进入任务中心，任务中心能展示 execution progress，receipt/artifact 能反查任务。

### 第四优先级：修正任务状态机

建议状态至少包括：

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

并明确：

```text
submit != complete
review approved == completed
review rejected == needs_rework 或 rejected
```

### 第五优先级：修复“我的任务”接口

`GET /api/tasks/employee/{employeeId}` 应支持：

```text
status=active|submitted|completed|reviewed
includeHistory=true
```

并返回 active、submitted、pending review、completed、reviewed 等不同阶段任务。

### 第六优先级：落地 data 分类沉淀

任务每次流转都应写入：

```text
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/events.jsonl
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/session.json
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/summary.json
data/memory/{tenantId}/{userId}/{taskKey}/knowledge.json
data/indexes/by-user/{userId}.json
data/indexes/by-task/{taskKey}.json
data/indexes/by-execution/{executionId}.json
```

### 第七优先级：明确 AgentTaskController 去留

如果保留，应接入统一任务服务。  
如果不保留，应标记为 legacy/mock，避免误用。

---

## 6. 最终判断

当前任务模块状态：

```text
人工公共任务领取闭环：基本可用
数字员工自治执行闭环：未完全统一
WebSocket 续接任务闭环：未落地
用户级任务记忆隔离：未落地
data 分类沉淀：未落地
任务状态机：需要重构
任务持久化：需要补齐
```
