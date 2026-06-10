# 任务类型归一化 - ExecutionCapabilityResolver 体系

> 本文档记录了任务类型归一化方案，将 LLM 开放意图归一到有限执行能力枚举。
> 对应原文章节：2.3.3.1 P0-5
> 状态：✅ 已完成

---

## 设计原则

```text
任务意图可以开放，执行能力必须收敛。
```

## 新增组件

### 枚举定义

**ExecutionCapability (15种)**
```java
WEB_APP_BUILD, DOCUMENT_GENERATION, DATA_ANALYSIS, CODE_CHANGE,
CODE_REVIEW, ARCHITECTURE_DESIGN, RESEARCH_ANALYSIS, BUSINESS_PLAN,
CUSTOMER_SUPPORT, LEGAL_REVIEW, FINANCE_ANALYSIS, HR_WORKFLOW,
OPERATION_PLAN, APPROVAL_REQUIRED, HUMAN_HANDOFF
```

**ArtifactType (15种)**
```java
INTERACTIVE_WEB_PAGE, WEB_PROJECT, DOCUMENT, DATA_REPORT, CODE_PATCH,
REVIEW_REPORT, ARCHITECTURE_SPEC, BUSINESS_PROPOSAL, SUPPORT_REPLY,
LEGAL_MEMO, FINANCE_REPORT, HR_DOCUMENT, OPERATION_RUNBOOK,
APPROVAL_REQUEST, HUMAN_HANDOFF_NOTE
```

**ExecutionMode (6种)**
```java
ARTIFACT_ONLY, DOCKER_SANDBOX, LOCAL_RESTRICTED,
HUMAN_REVIEW_REQUIRED, APPROVAL_REQUIRED, NO_EXECUTION
```

---

## 执行流程改造

```text
DialogueAnalyzer
-> MainBrainTaskDirector 输出 intent/taskType/deliverables/acceptanceCriteria
-> ExecutionCapabilityResolver 归一化 executionCapability/artifactType/executionMode
-> FixedEmployeeDispatcher 根据 capability + skills 选人
-> AssignmentPreparationService 将 capability 写入 EmployeeWorkAssignment
-> ToolBackedEmployeeTaskExecutor 按 executionCapability 执行
-> ExecutionReceiptReviewer 按 artifactType 和 acceptanceCriteria 验收
```

---

## 示例映射

| 用户任务 | executionCapability | artifactType | executionMode |
| --- | --- | --- | --- |
| 做一个星空飞机射击网页游戏 | WEB_APP_BUILD | INTERACTIVE_WEB_PAGE | ARTIFACT_ONLY |
| 写一份销售方案 | DOCUMENT_GENERATION | BUSINESS_PROPOSAL | ARTIFACT_ONLY |
| 分析 Excel 财务数据 | DATA_ANALYSIS | DATA_REPORT | LOCAL_RESTRICTED |
| 评审合同风险 | LEGAL_REVIEW | LEGAL_MEMO | HUMAN_REVIEW_REQUIRED |

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapability.java` | 执行能力枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ArtifactType.java` | 产物类型枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionMode.java` | 执行模式枚举 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionCapabilityResolver.java` | 归一化解析器接口 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultExecutionCapabilityResolver.java` | 归一化解析器实现 |
