# 核心定义文档索引

> 本目录包含 Living Agent 的核心类型定义文档，包括 Prompt 模板、Schema、枚举定义等。
> 版本：2026-05-20

---

## 文档列表

| 文档 | 说明 |
| --- | --- |
| [MAINBRAIN_PROMPT_TEMPLATE.md](./MAINBRAIN_PROMPT_TEMPLATE.md) | MainBrain 系统 Prompt 模板 |
| [MAINBRAIN_TASK_PLAN_SCHEMA.md](./MAINBRAIN_TASK_PLAN_SCHEMA.md) | MainBrainTaskPlan JSON Schema 定义 |
| [REQUIREMENT_STATUS_STATE_MACHINE.md](./REQUIREMENT_STATUS_STATE_MACHINE.md) | 需求状态机定义 |
| [REQUIREMENT_READINESS_EVALUATOR_RULES.md](./REQUIREMENT_READINESS_EVALUATOR_RULES.md) | 需求就绪评估器判断规则 |
| [EXECUTION_CAPABILITY_ENUM.md](./EXECUTION_CAPABILITY_ENUM.md) | 执行能力枚举 |
| [ARTIFACT_TYPE_ENUM.md](./ARTIFACT_TYPE_ENUM.md) | 产物类型枚举 |
| [EXECUTION_MODE_ENUM.md](./EXECUTION_MODE_ENUM.md) | 执行模式枚举 |

---

## 文档关系图

```
MainBrain Prompt 模板
    │
    ├──► MainBrainTaskPlan Schema
    │        │
    │        ├──► RequirementStatus (状态机)
    │        │        │
    │        │        └──► RequirementReadinessEvaluator (评估器)
    │        │
    │        ├──► ExecutionCapability (执行能力)
    │        │
    │        ├──► ArtifactType (产物类型)
    │        │
    │        └──► ExecutionMode (执行模式)
    │
    └──► 响应模板
             │
             ├──► 需求澄清模板
             └──► 需求确认模板
```

---

## 快速参考

### RequirementStatus 状态流转

```
DRAFT -> NEEDS_CLARIFICATION/REQUIREMENT_CONFIRMED
NEEDS_CLARIFICATION -> CLARIFICATION_PENDING
CLARIFICATION_PENDING -> REQUIREMENT_CONFIRMED/NEEDS_CLARIFICATION
REQUIREMENT_CONFIRMED -> PLANNING
PLANNING -> PLANNED
PLANNED -> ASSIGNED
ASSIGNED -> EXECUTING
EXECUTING -> COMPLETED/FAILED
```

### 评估阈值

```
confidence >= 0.85 -> SUFFICIENT (可进入规划)
0.65 <= confidence < 0.85 -> PARTIALLY_SUFFICIENT (可规划但建议澄清)
confidence < 0.65 -> INSUFFICIENT (必须先澄清)
```

### 执行能力枚举 (15种)

```
WEB_APP_BUILD, DOCUMENT_GENERATION, DATA_ANALYSIS, CODE_CHANGE,
CODE_REVIEW, ARCHITECTURE_DESIGN, RESEARCH_ANALYSIS, BUSINESS_PLAN,
CUSTOMER_SUPPORT, LEGAL_REVIEW, FINANCE_ANALYSIS, HR_WORKFLOW,
OPERATION_PLAN, APPROVAL_REQUIRED, HUMAN_HANDOFF
```

### 产物类型枚举 (15种)

```
INTERACTIVE_WEB_PAGE, WEB_PROJECT, DOCUMENT, DATA_REPORT, CODE_PATCH,
REVIEW_REPORT, ARCHITECTURE_SPEC, BUSINESS_PROPOSAL, SUPPORT_REPLY,
LEGAL_MEMO, FINANCE_REPORT, HR_DOCUMENT, OPERATION_RUNBOOK,
APPROVAL_REQUEST, HUMAN_HANDOFF_NOTE
```

### 执行模式枚举 (6种)

```
ARTIFACT_ONLY, DOCKER_SANDBOX, LOCAL_RESTRICTED,
HUMAN_REVIEW_REQUIRED, APPROVAL_REQUIRED, NO_EXECUTION
```
