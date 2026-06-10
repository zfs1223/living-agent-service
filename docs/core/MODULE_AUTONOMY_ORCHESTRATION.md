# 自治编排模块

> 版本：2026-05-18 | 路径：living-agent-core/autonomy/

## 核心流程

```
用户消息
  ↓
DialogueAnalyzer.analyze()         [1. 意图分析]
  ↓
MainBrainTaskDirector.plan()       [2. 任务规划]
  ↓
FixedEmployeeDispatcher.planAssignments() [3. 员工分派]
  ↓
ToolBackedEmployeeTaskExecutor.executeTask() [4. 任务执行]
  ↓
ExecutionResult
```

## 核心接口

### 1. DialogueAnalyzer

```java
DialogueDecision analyze(String message, String userId, String department, String sessionId);

public record DialogueDecision(
    String requestId,
    String sessionId,
    String userId,
    String originalMessage,
    MessageKind kind,
    String intent,
    String primaryDepartment,
    String primaryBrainId,
    List<String> supportingDepartments,
    boolean requiresTaskExecution,
    boolean requiresClarification,
    String clarificationQuestion,
    int complexity,
    int riskLevel,
    Map<String, Object> metadata
) {}

public enum MessageKind {
    CHAT, TASK, PROJECT, APPROVAL, CONSULTATION, KNOWLEDGE, CROSS_DEPARTMENT
}
```

### 2. MainBrainTaskDirector

```java
CompletableFuture<MainBrainTaskPlan> plan(
    IntakeClassification intake,
    DialogueDecision decision,
    String userMessage,
    String userId,
    String sessionId,
    String currentDepartment
);
```

### 3. FixedEmployeeDispatcher

```java
List<EmployeeWorkAssignment> planAssignments(
    MainBrainTaskPlan mainBrainTaskPlan,
    DepartmentTaskPlan departmentTaskPlan,
    String sessionId,
    String userId
);
```

### 4. EmployeeTaskExecutor

```java
ExecutionResult executeTask(
    String employeeCode,
    String taskType,
    String taskDescription,
    EmployeeWorkAssignment assignmentTask,
    List<String> availableTools,
    String executionEnvironment
);
```

## 实现类

| 接口 | LLM实现 | 规则兜底 |
|-----|--------|--------|
| DialogueAnalyzer | `LlmBasedDialogueAnalyzer` | `RuleBasedDialogueAnalyzer` |
| MainBrainTaskDirector | `LlmBasedMainBrainTaskDirector` | `RuleBasedMainBrainTaskDirector` |
| FixedEmployeeDispatcher | `LlmBasedFixedEmployeeDispatcher` | `RegistryBackedFixedEmployeeDispatcher` |
| ExecutionReceiptReviewer | `LlmExecutionReceiptReviewer` | — |
| ExecutionResultAggregator | `LlmBasedExecutionResultAggregator` | `DefaultExecutionResultAggregator` |
| MainBrainResponseComposer | `LlmBasedMainBrainResponseComposer` | `DefaultMainBrainResponseComposer` |
| FinalResponseCoordinator | `LlmBasedFinalResponseCoordinator` | `DefaultFinalResponseCoordinator` |
| MainBrainFinalSummaryService | `LlmMainBrainFinalSummaryService` | `DefaultMainBrainFinalSummaryService` |
| AssignmentReadinessEvaluator | `LlmAssignmentReadinessEvaluator` | — |
| KnowledgeCaptureService | `DefaultKnowledgeCaptureService` | — |
| PerformanceCaptureService | `DefaultPerformanceCaptureService` | — |

## LlmBasedDialogueAnalyzer 逻辑

```
1. 构建 Prompt（包含可用部门列表）
2. 如果 llmDecisionClient != null：
   a. 通过 DecisionContextBuilder 构建 DecisionContext
   b. 创建 LlmDecisionRequest（含 DecisionContext）
   c. 调用 llmDecisionClient.decide()
3. 否则：调用 mainBrain.callLlm() 直接路径
4. 解析 JSON 为 DialogueDecision
5. 映射部门到大脑名称
6. 兜底：解析失败返回 CHAT 类型
```

**消息类型枚举**：
- `CHAT` - 闲聊
- `TASK` - 可执行任务
- `PROJECT` - 项目计划
- `APPROVAL` - 审批请求
- `KNOWLEDGE` - 知识查询
- `CROSS_DEPARTMENT` - 跨部门

## ToolBackedEmployeeTaskExecutor 逻辑

```
1. 标准化任务类型
2. 根据类型执行：
   - web_prototype/web_development → 生成 HTML/CSS/JS
   - generic → 返回 FAILED（拒绝硬编码或通用兜底伪造完成）
3. 保存产物到 data/artifacts/{department}/{executionId}/
4. 返回 ExecutionResult
```

## LlmExecutionReceiptReviewer 逻辑

```
1. 尝试 LLM 语义审查回执
2. LLM 可用时：返回语义审查结果
3. LLM 不可用时（defaultAccept 降级）：
   - status=COMPLETED → accepted=true, qualityScore=0.5（低置信度）
   - status=FAILED → accepted=false, 建议重试
```

## 代码路径

```
autonomy/
├── ConversationOrchestrator.java     # 编排入口
├── DialogueAnalyzer.java             # 意图分析接口
├── DialogueDecision.java             # 意图分析结果（含 MessageKind 枚举）
├── MainBrainTaskDirector.java        # 任务规划接口
├── FixedEmployeeDispatcher.java      # 员工分派接口
├── EmployeeTaskExecutor.java         # 任务执行接口
├── EmployeeWorkAssignment.java       # 任务单记录
├── IntakeClassification.java         # 入口分类
├── DepartmentTaskPlan.java          # 部门计划
├── MainBrainTaskPlan.java           # 主脑计划
├── AutonomyTraceService.java        # 链路追踪
├── AutonomyTraceEvent.java          # 追踪事件
├── ExecutionReceiptReviewer.java    # 回执审查接口
├── ExecutionResultAggregator.java   # 结果聚合接口
├── MainBrainResponseComposer.java   # 响应组合接口
├── FinalResponseCoordinator.java    # 最终响应协调接口
├── MainBrainFinalSummaryService.java # 主脑二次总结接口
├── AssignmentReadinessEvaluator.java # 分配就绪评估接口
├── KnowledgeCaptureService.java     # 知识捕获接口
├── PerformanceCaptureService.java   # 绩效捕获接口
├── KnowledgeCaptureResult.java      # 知识捕获结果 record
├── PerformanceCaptureResult.java    # 绩效捕获结果 record
├── context/
│   ├── DecisionContext.java          # 九大上下文 record
│   ├── DecisionContextBuilder.java   # 上下文构建器接口
│   └── impl/
│       └── DefaultDecisionContextBuilder.java
└── impl/
    ├── LlmBasedDialogueAnalyzer.java
    ├── RuleBasedDialogueAnalyzer.java
    ├── LlmBasedMainBrainTaskDirector.java
    ├── RuleBasedMainBrainTaskDirector.java
    ├── LlmBasedFixedEmployeeDispatcher.java
    ├── RegistryBackedFixedEmployeeDispatcher.java
    ├── ToolBackedEmployeeTaskExecutor.java
    ├── MinimalEmployeeTaskExecutor.java
    ├── ChannelBackedDepartmentExecutionCoordinator.java
    ├── LlmExecutionReceiptReviewer.java
    ├── LlmBasedExecutionResultAggregator.java
    ├── DefaultExecutionResultAggregator.java
    ├── LlmBasedMainBrainResponseComposer.java
    ├── DefaultMainBrainResponseComposer.java
    ├── LlmBasedFinalResponseCoordinator.java
    ├── DefaultFinalResponseCoordinator.java
    ├── LlmMainBrainFinalSummaryService.java
    ├── DefaultMainBrainFinalSummaryService.java
    ├── LlmAssignmentReadinessEvaluator.java
    ├── DefaultAssignmentPreparationService.java
    ├── DefaultKnowledgeCaptureService.java
    ├── DefaultPerformanceCaptureService.java
    ├── DynamicEmployeeTaskConsumerRegistry.java
    ├── InMemoryArtifactRecordService.java
    ├── JpaArtifactRecordService.java
    ├── InMemoryEmployeeExecutionReceiptService.java
    ├── FileBasedEmployeeExecutionReceiptService.java
    └── llm/
        ├── LlmDecisionClient.java
        └── impl/
            └── DefaultLlmDecisionClient.java
```

## 元数据统一约定

> 所有代码派发、执行回执、审查状态机、artifact 记录都应尽量统一使用 `taskId` 作为主链路标识。`assignmentId`、`dispatchId` 仅用于兼容或追溯，不作为主关联键。

### 核心字段

| 字段 | 说明 | 示例 |
|------|------|------|
| `taskId` | 统一任务主键 | 所有链路优先使用 |
| `taskType` | 任务类型 | `code_review`、`bug_fix`、`test_generate`、`release_prep` |
| `taskScope` | 任务范围 | `ADHOC`、`SCHEDULED`、`PROJECT`、`PIPELINE` |
| `workflowType` | 流程类型 | `SINGLE_PASS`、`REVIEW_LOOP`、`RECURRING`、`PARALLEL` |
| `projectId` | 项目标识 | 绑定代码仓库 |
| `scheduleId` | 定时任务标识 | 定时触发场景 |
| `parentTaskId` | 父任务标识 | 子任务/拆分任务追踪 |

### 产物字段

| 字段 | 说明 | 所属 record |
|------|------|-------------|
| `worktreePath` | 代码工作区路径 | `EmployeeWorkAssignment`、`EmployeeExecutionReceipt` |
| `diffPath` | 代码差异路径 | `EmployeeWorkAssignment`、`EmployeeExecutionReceipt` |
| `branchName` | Git 分支名 | `ArtifactRecord.metadata` |
| `reviewReportPath` | 审查报告路径 | `CodeReviewWorkflowService.ReviewState` |
| `finalSummaryPath` | 最终摘要路径 | `CodeReviewWorkflowService.ReviewState` |
| `reviewStage` | 审查阶段 | `CodeReviewWorkflowService.ReviewState` |
| `reviewRound` | 审查轮次 | `CodeReviewWorkflowService.ReviewState` |

### 适配原则

1. **主链路统一**：所有链路优先用 `taskId`。
2. **差异进入元数据**：临时任务、定时任务、项目任务的差异不体现在主键，而体现在 `taskScope`、`workflowType`、`scheduleId`、`projectId`。
3. **兼容读取**：旧字段 `assignmentId`、`dispatchId` 继续保留兼容，但调用点逐步迁移到 `taskId`。
4. **产物绑定**：worktree / diff / review report / final summary 必须通过 artifact metadata 与任务关联。

## 快速定位

| 需求 | 文件 |
|------|------|
| 修改意图分类规则 | `LlmBasedDialogueAnalyzer.java` |
| 修改任务规划逻辑 | `LlmBasedMainBrainTaskDirector.java` |
| 修改员工分派逻辑 | `LlmBasedFixedEmployeeDispatcher.java` |
| 修改任务执行逻辑 | `ToolBackedEmployeeTaskExecutor.java` |
| 修改产物保存路径 | `ToolBackedEmployeeTaskExecutor.java` (ARTIFACTS_DIR) |
| 新增任务类型 | `ToolBackedEmployeeTaskExecutor.java` (switch) |
| 修改回执审查 | `LlmExecutionReceiptReviewer.java` |
| 修改结果聚合 | `LlmBasedExecutionResultAggregator.java` |
| 修改知识捕获 | `DefaultKnowledgeCaptureService.java` |
| 修改绩效捕获 | `DefaultPerformanceCaptureService.java` |
