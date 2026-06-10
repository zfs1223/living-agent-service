# 任务模块问题清单与修复优先级

> 日期：2026-05-18  
> 范围：任务模块、任务领取/提交/审核、WebSocket 任务续接、用户任务记忆隔离、`data` 分类沉淀  
> 相关代码：
> - `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java`
> - `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java`
> - `living-agent-core/src/main/java/com/livingagent/core/ops/scheduler/TaskCheckout.java`
> - `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskWorkflowService.java`
> - `living-agent-gateway/src/main/java/com/livingagent/gateway/service/TaskPerformanceBridgeService.java`
> - `frontend/src/components/PublicTaskBoard.tsx`
> - `frontend/src/pages/MyTasks.tsx`
> - `frontend/src/services/api.ts`

---

## 1. 当前总体结论

当前任务模块可以完成一个基础的“公共任务领取 → 我的任务 → 提交结果 → 审核 → 积分/绩效联动”流程，但它还不能完成企业级任务系统所需的完整闭环。

当前可用能力：

```text
任务创建
-> pendingTasks
-> 公共任务栏展示
-> 用户领取任务
-> checkedOutTasks
-> 我的任务展示
-> 用户提交结果
-> completeTask 标记完成
-> 审核接口触发积分/绩效/通知
```

但以下关键能力尚未完整落地：

```text
同一用户同一任务统一关联
WebSocket 断线重连续接
userId + taskKey + executionId 三元绑定
任务与 autonomy execution/receipt/artifact 统一
任务运行数据按 data 分类沉淀
任务状态机真实表达 submit/review/rework
任务持久化和历史恢复
```

---

## 2. 已检查到的主要代码路径

### 2.1 后端主任务接口

主入口：

```text
living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java
```

当前提供接口：

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{taskId}
POST   /api/tasks/{taskId}/checkout
POST   /api/tasks/{taskId}/complete
POST   /api/tasks/{taskId}/release
POST   /api/tasks/{taskId}/reassign
GET    /api/tasks/statistics
GET    /api/tasks/pending
GET    /api/tasks/employee/{employeeId}
GET    /api/tasks/public
POST   /api/tasks/{taskId}/claim
POST   /api/tasks/{taskId}/submit
POST   /api/tasks/{taskId}/review
```

### 2.2 核心任务调度类

```text
living-agent-core/src/main/java/com/livingagent/core/ops/scheduler/TaskCheckout.java
```

当前使用内存 Map 管理任务：

```text
pendingTasks
checkedOutTasks
completedTasks
checkoutRecords
employeeTaskCounts
```

### 2.3 Agent 维度任务接口

```text
living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java
```

该接口目前更像 mock/stub：

- `listTasks()` 返回固定示例任务 `task_001`
- `createTask()` 只构造返回对象，不持久化
- `getTask()` 返回“任务详情”
- `getTaskLogs()` 返回固定“任务创建”日志
- `triggerTask()` 只返回 `triggered`

它目前不是正式任务闭环主路径。

### 2.4 前端任务页面

```text
frontend/src/components/PublicTaskBoard.tsx
frontend/src/pages/MyTasks.tsx
frontend/src/services/api.ts
```

前端当前主要调用 `/api/tasks` 这套接口：

```text
taskApi.getPublicTasks()
taskApi.claimTask()
taskApi.submitTask()
taskApi.getEmployeeTasks()
```

---

## 3. 当前能完成的基础闭环

当前任务模块可以跑通以下人工任务闭环：

```text
1. 创建任务
2. 任务进入 pendingTasks
3. 公共任务栏通过 /api/tasks/public 展示
4. 用户通过 /api/tasks/{taskId}/claim 领取任务
5. 任务从 pendingTasks 移入 checkedOutTasks
6. 我的任务页面通过 /api/tasks/employee/{employeeId} 展示进行中任务
7. 用户通过 /api/tasks/{taskId}/submit 提交结果
8. TaskCheckout.completeTask() 将任务标记为 COMPLETED
9. 审核方通过 /api/tasks/{taskId}/review 审核
10. TaskPerformanceBridgeService 记录积分、风险指标、部门通知
```

这说明“任务广场/人工领取/人工提交/审核奖励”的基础能力已经存在。

---

## 4. 当前无法完成的完整闭环

目标完整闭环应为：

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

当前这条链路还没有完全打通。

---

## 5. P0 问题清单

### P0-1：任务没有 `userId/taskKey/executionId` 统一关联

当前 `TaskCheckout.Task` 字段为：

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

风险：

- 同一用户同一任务无法统一续接。
- 不同用户相同任务描述可能混写。
- WebSocket 重连后无法稳定恢复原任务。
- 任务与 receipt/artifact/final summary 无法天然关联。

处理建议：

- 引入正式任务实体字段，不要只塞到 `context`。
- 至少保证 `tenantId + userId + taskKey + executionId` 成为任务主关联键。

---

### P0-2：任务状态只存在内存 Map 中

`TaskCheckout` 当前使用：

```text
ConcurrentHashMap pendingTasks
ConcurrentHashMap checkedOutTasks
ConcurrentHashMap completedTasks
ConcurrentHashMap checkoutRecords
```

风险：

- 服务重启任务丢失。
- 无法跨实例部署。
- 无法长期记忆。
- 无法和 `data/` 分类沉淀对齐。
- WebSocket 断线后无法稳定恢复任务状态。

处理建议：

- 新增 `TaskEntity`、`TaskRepository`、`TaskService`。
- 将内存 Map 降级为缓存或开发模式实现。
- 任务事件、提交、审核也应持久化。

---

### P0-3：任务模块与 WebSocket/execution 体系未统一

当前存在两条相对独立的链路：

```text
任务市场链路：TaskController -> TaskCheckout
自治执行链路：DepartmentChatService -> autonomy execution/receipt/artifact
```

风险：

- 任务中心看不到对话任务执行状态。
- WebSocket 任务续接不一定反映到 `/api/tasks`。
- receipt/artifact 不会自然进入“我的任务”。
- 任务审核和员工执行验收割裂。

处理建议：

建立统一桥接：

```text
Task.taskId
Task.taskKey
Task.executionId
Execution.executionId
Receipt.executionId
Artifact.executionId
```

让任务中心可以展示 execution progress、receipt、artifact 和 final summary。

---

### P0-4：`GET /api/tasks/employee/{employeeId}` 只返回 checked out 任务

当前 `TaskController.getEmployeeTasks()` 调用：

```text
taskCheckout.getCheckedOutTasks(employeeId)
```

问题：

- 已完成任务不会返回。
- 前端 `MyTasks.tsx` 的“已完成”tab 没有可靠数据来源。
- 提交后任务从 checkedOutTasks 移到 completedTasks，前端再次查询时可能看不到。

处理建议：

- 接口增加 `status=active|submitted|completed|reviewed`。
- 支持 `includeHistory=true`。
- 返回 checked out、submitted、pending review、completed、reviewed 全生命周期任务。

---

### P0-5：`submitTask()` 直接 complete，缺少 PENDING_REVIEW 状态

当前提交逻辑：

```text
POST /api/tasks/{taskId}/submit
-> TaskResult.success(...)
-> taskCheckout.completeTask(...)
-> TaskStatus.COMPLETED
```

但前端提示为“等待审核”。

风险：

- 用户看到的是等待审核，系统状态却已经 completed。
- 审核动作不再决定任务是否完成。
- 拒绝审核后没有返工状态。
- 绩效和知识沉淀可能提前发生。

处理建议：

任务状态机至少补充：

```text
SUBMITTED
PENDING_REVIEW
REVIEWED
REJECTED
NEEDS_REWORK
```

并明确：

```text
submit != complete
review approved == completed
review rejected == needs_rework 或 rejected
```

---

### P0-6：`AgentTaskController` 是 stub，容易误导使用方

当前 `AgentTaskController` 返回示例任务和假日志，不接入 `TaskCheckout` 或统一任务服务。

风险：

- API 使用方以为它是正式任务接口。
- 前端或第三方接入后会拿到假数据。
- 同一系统存在两套语义不一致的任务 API。

处理建议：

三选一：

1. 接入统一任务服务。
2. 标记为 legacy/mock/internal。
3. 从正式 API 文档中移除或隐藏。

---

## 6. P1 问题清单

### P1-1：任务创建不会自动进入自治执行

`TaskController.createTask()` 只是创建 pending task，不会触发：

```text
主脑规划
部门路由
固定员工派发
execution 创建
receipt channel 创建
artifact 生成
WebSocket progress 推送
```

处理建议：

- 增加 `sourceType`：`MANUAL_MARKET` / `AUTONOMY_EXECUTION` / `CONVERSATION_TASK`。
- 对 `CONVERSATION_TASK` 自动接入 autonomy execution。
- 对 `MANUAL_MARKET` 保持人工领取模式。

---

### P1-2：任务完成缺少 artifact/result 结构化落盘

当前 `submitTask()` 只接收文本 `result`。

缺少：

```text
artifact 文件
receipt
execution trace
final summary
review conclusion
data/conversations 路径
```

处理建议：

- `SubmitTaskRequest` 支持 artifactIds、attachments、structuredResult。
- 提交后写入 `data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/`。
- 与 ArtifactRecordService 关联。

---

### P1-3：权限模型较粗

多数接口只通过：

```text
X-Employee-Id -> accessGateService.canRoute(employeeId, "brain", "OpsBrain")
```

风险：

- 未严格校验当前 token 用户是否等于 employeeId。
- 未区分任务 owner、领取人、审核人、部门负责人。
- 可能出现代领取、代提交、跨租户查看任务。

处理建议：

- 所有任务接口统一从认证上下文解析 userId。
- `employeeId` 只能作为业务目标，不能作为身份凭证。
- 增加 owner/assignee/reviewer/department/tenant 权限校验。

---

### P1-4：缺少事件流和重连补发机制

当前任务流转没有统一事件日志，例如：

```text
task_created
task_claimed
task_submitted
task_reviewed
execution_progress
artifact_recorded
main_brain_finalized
```

风险：

- WebSocket 重连后无法补发中间事件。
- `lastEventId` 没有可靠数据源。
- 只能查当前状态，不能回放过程。

处理建议：

- 新增 TaskEventEntity 或 JSONL 事件流。
- 每次状态变化追加事件。
- WebSocket 重连根据 `lastEventId` 补发。

---

## 7. 推荐修复优先级

### 第一优先级：统一任务身份字段

补齐：

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

### 第二优先级：持久化 Task

新增：

```text
TaskEntity
TaskRepository
TaskService
TaskEventEntity
TaskSubmissionEntity
TaskReviewEntity
```

### 第三优先级：合并 TaskCheckout 与 autonomy execution

建立统一关系：

```text
Task.taskId -> Task.executionId -> Receipt.executionId -> Artifact.executionId
```

### 第四优先级：修正任务状态机

建议状态：

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

### 第五优先级：修复“我的任务”接口

`GET /api/tasks/employee/{employeeId}` 增加：

```text
status=active|submitted|completed|reviewed
includeHistory=true
```

### 第六优先级：落地 `data` 分类沉淀

建议路径：

```text
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/events.jsonl
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/session.json
data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/summary.json
data/memory/{tenantId}/{userId}/{taskKey}/knowledge.json
data/indexes/by-user/{userId}.json
data/indexes/by-task/{taskKey}.json
data/indexes/by-execution/{executionId}.json
```

### 第七优先级：明确 `AgentTaskController` 去留

如果保留，则接入统一任务服务。  
如果不保留，则标记为 mock/legacy，避免误用。

---

## 8. 最终判断

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

也就是说，当前系统能支撑“任务广场/我的任务/提交审核”的初级功能，但还不能支撑“同一用户同一任务持续推进、记忆不乱、WebSocket 可恢复、执行产物可追踪”的完整企业任务系统。
