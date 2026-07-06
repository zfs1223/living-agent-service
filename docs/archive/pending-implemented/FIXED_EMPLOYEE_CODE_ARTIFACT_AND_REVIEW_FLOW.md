# 固定数字员工代码产物、协作审查与用户获取流程设计

## 元数据统一约定

所有代码派发、执行回执、审查状态机、artifact 记录都应尽量统一使用 `taskId` 作为主链路标识。`assignmentId`、`dispatchId` 仅用于兼容或追溯，不作为主关联键。

### 核心字段

- `taskId`：统一任务主键
- `taskType`：任务类型，如 `code_review`、`bug_fix`、`test_generate`、`release_prep`
- `taskScope`：任务范围，如 `ADHOC`、`SCHEDULED`、`PROJECT`、`PIPELINE`
- `workflowType`：流程类型，如 `SINGLE_PASS`、`REVIEW_LOOP`、`RECURRING`、`PARALLEL`
- `projectId`：项目标识
- `scheduleId`：定时任务标识
- `parentTaskId`：父任务标识，用于子任务/拆分任务追踪

### 产物字段

- `worktreePath`
- `branchName`
- `diffPath`
- `reviewReportPath`
- `finalSummaryPath`
- `reviewStage`
- `reviewRound`

### 适配原则

1. **主链路统一**：所有链路优先用 `taskId`。
2. **差异进入元数据**：临时任务、定时任务、项目任务的差异不体现在主键，而体现在 `taskScope`、`workflowType`、`scheduleId`、`projectId`。
3. **兼容读取**：旧字段 `assignmentId`、`dispatchId` 继续保留兼容，但调用点逐步迁移到 `taskId`。
4. **产物绑定**：worktree / diff / review report / final summary 必须通过 artifact metadata 与任务关联。

---

## 1. 背景与问题

当前系统已经具备以下基础能力：

- 固定数字员工注册与部门绑定：`FixedEmployeeRegistry`
- Tech 部门固定员工岗位：前端、后端、DevOps、架构师、代码审查员等
- Claude CLI 工具：`ClaudeCliTool`、`ClaudeExecutionGateway`
- 任务派发与执行回执：`EmployeeWorkAssignment`、`EmployeeExecutionReceipt`
- 产物记录模型：`ArtifactRecord`、`ArtifactRecordService`
- 技术团队编排雏形：`TechLeadOrchestrator`
- 协作会话模型：`CollaborationSession`
- Worktree 管理雏形：`GitWorktreeManager`

但目前代码链路仍存在关键不完整点：

1. 固定数字员工写出的代码最终保存在哪里，尚未形成统一协议。
2. 产物记录虽然存在，但与 Claude CLI 写文件、worktree、任务、审查结果之间的绑定还不完整。
3. 用户如何查看、下载、合并、验收代码产物，入口不够明确。
4. 写代码员工与代码审查员工之间缺少强制性的多轮审查状态机。
5. 技术部门内部的“开发 -> 审查 -> 修复 -> 复审 -> 通过/驳回”闭环尚未固化。

因此需要补充一套明确的代码产物协议与协作审查流程。
