# Living Agent 架构文档总索引

> 本文档是 Living Agent 架构文档的总索引，按照功能模块和完成状态组织。
> 版本：2026-05-20

---

## 目录结构

```
docs/
├── core/                    # 核心类型定义
│   ├── INDEX.md
│   ├── MAINBRAIN_PROMPT_TEMPLATE.md
│   ├── MAINBRAIN_TASK_PLAN_SCHEMA.md
│   ├── REQUIREMENT_STATUS_STATE_MACHINE.md
│   ├── REQUIREMENT_READINESS_EVALUATOR_RULES.md
│   ├── EXECUTION_CAPABILITY_ENUM.md
│   ├── ARTIFACT_TYPE_ENUM.md
│   └── EXECUTION_MODE_ENUM.md
│
├── implemented/             # 已完成的落地方案
│   ├── INDEX.md
│   ├── EXECUTION_CAPABILITY_RESOLVER.md
│   ├── REQUIREMENT_READINESS_EVALUATOR.md
│   └── WEBSOCKET_DIALOGUE_FIX.md
│
├── pending/                 # 待实施的方案
│   └── INDEX.md
│
├── guides/                  # 开发指南
│   ├── INDEX.md
│   ├── END_TO_END_ACCEPTANCE.md
│   └── DEVELOPMENT_ORDER.md
│
├── ARCHITECTURE_INDEX.md    # 本文档
└── MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md  # 原始完整文档（保留）
```

---

## 文档分类

### 1. 核心定义 (docs/core/)

包含可直接用于代码实现的类型定义、Prompt 模板和 Schema。

| 文档 | 说明 |
| --- | --- |
| `INDEX.md` | 核心文档索引和快速参考 |
| `MAINBRAIN_PROMPT_TEMPLATE.md` | MainBrain 系统 Prompt 模板 |
| `MAINBRAIN_TASK_PLAN_SCHEMA.md` | MainBrainTaskPlan JSON Schema 定义 |
| `REQUIREMENT_STATUS_STATE_MACHINE.md` | 需求状态机定义 |
| `REQUIREMENT_READINESS_EVALUATOR_RULES.md` | 需求就绪评估器判断规则 |
| `EXECUTION_CAPABILITY_ENUM.md` | 执行能力枚举 (15种) |
| `ARTIFACT_TYPE_ENUM.md` | 产物类型枚举 (15种) |
| `EXECUTION_MODE_ENUM.md` | 执行模式枚举 (6种) |

### 2. 已完成方案 (docs/implemented/)

包含已完成落地实施的方案文档。

| 文档 | 说明 |
| --- | --- |
| `INDEX.md` | 已完成方案索引 |
| `EXECUTION_CAPABILITY_RESOLVER.md` | 任务类型归一化方案 |
| `REQUIREMENT_READINESS_EVALUATOR.md` | 需求明确性前置判断方案 |
| `WEBSOCKET_DIALOGUE_FIX.md` | WebSocket 对话闭环修复方案 |

### 3. 待实施方案 (docs/pending/)

包含后续需要实施的方案。

| 文档 | 说明 |
| --- | --- |
| `INDEX.md` | 待实施方案索引 |

### 4. 开发指南 (docs/guides/)

包含开发指南和验收标准。

| 文档 | 说明 |
| --- | --- |
| `INDEX.md` | 开发指南索引 |
| `END_TO_END_ACCEPTANCE.md` | 端到端验收标准 |
| `DEVELOPMENT_ORDER.md` | 推荐开发顺序 |

---

## 快速导航

### 按功能模块

| 模块 | 核心文档 | 已完成文档 |
| --- | --- | --- |
| MainBrain | `MAINBRAIN_PROMPT_TEMPLATE.md` | `EXECUTION_CAPABILITY_RESOLVER.md` |
| 需求状态 | `REQUIREMENT_STATUS_STATE_MACHINE.md` | `REQUIREMENT_READINESS_EVALUATOR.md` |
| 执行能力 | `EXECUTION_CAPABILITY_ENUM.md` | - |
| 产物类型 | `ARTIFACT_TYPE_ENUM.md` | - |
| 执行模式 | `EXECUTION_MODE_ENUM.md` | - |
| WebSocket | - | `WEBSOCKET_DIALOGUE_FIX.md` |

### 按完成状态

| 状态 | 文档 |
| --- | --- |
| ✅ 已完成 | `docs/core/` 所有文档 + `docs/implemented/` 所有文档 |
| ⏳ 待实施 | `docs/pending/` 所有文档 |

---

## 相关链接

- 原始完整文档：`docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md`
- 代码落点指南：参考各模块文档内的"相关文件"章节
