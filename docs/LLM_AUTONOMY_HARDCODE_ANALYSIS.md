# Living Agent Service 中“本应由 LLM 自主决策”的问题分析（细化版）

> 依据文档：
>
> - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`
> - `docs/references/API_REFERENCE.md`
>
> 分析范围：`living-agent-core` 中自治编排、大脑、神经元、员工分派、主动预测、风险预测、上下文压缩、执行聚合等与“自主决策”相关的代码。
>
> 生成时间：2026-05-12
>
> 目标：识别项目中“原本应由 LLM/主脑/部门大脑基于上下文自主判断，但当前被关键词、固定阈值、固定员工编码、固定模板或假执行闭环替代”的位置，并给出可落地的改造方案与验收标准。

---

> ⚠️ **本文档已归档（2026-05-28）**
>
> 本文档是 2026-05-12 的代码审计快照，记录了当时识别出的硬编码问题。
> 此后的修复工作已记录在以下**权威跟进文档**中（建议以这些文档为准）：
>
> | 权威文档 | 用途 | 最后更新 |
> |---------|------|----------|
> | [`FLOW_IMPROVEMENT_REPORT.md`](./FLOW_IMPROVEMENT_REPORT.md) | 逐项问题修复记录（74项，100%已完成） | 2026-05-28 |
> | [`MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md`](./MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md) | 落地实施方案 + 状态跟踪 | 2026-05-28 |
> | [`COMPLETE_LANDING_TODO.md`](./COMPLETE_LANDING_TODO.md) | 研发执行任务清单 | 持续更新 |
>
> **本文档中标记的问题大部分已在后续三轮修复中解决**，具体对应关系：
> - A01（规则意图分析器）→ `LlmBasedDialogueAnalyzer` 已作为主实现 ✅
> - A02-A09（各环节半硬编码）→ 对应 LLM-first 组件已接入 ✅
> - P0-4.2（硬编码模型名）→ 6处已移除 + resolveDefault ollama fallback 已移除 ✅
> - DefaultMainBrainFinalSummaryService → 已验证为可接受的 data-composing fallback（非 fake-results） ✅
>
> 保留本文档仅供历史参考和回归审计使用。

## 1. 总体结论

项目结构文档已经明确把系统定位为“主脑 + 部门大脑 + 数字员工 + 神经元 + 工具 + 知识/记忆 + 自治编排”的企业智能体系统。按这个架构，下列环节应由 LLM 结合上下文、知识库、员工画像、工具 Schema、权限约束和业务目标进行动态判断：

1. 对话意图识别与入口分类。
2. 是否需要主脑规划、是否跨部门、主责/协作部门选择。
3. 任务类型、目标、拆解步骤、交付物、验收标准生成。
4. 数字员工选择、角色分工、任务指令生成。
5. 工具选择与执行路径选择。
6. 最终回复策略与用户可见回复组织。
7. 执行结果聚合、回执可信度评估、产物验收。
8. 主动服务中的行为预测、风险判断与建议生成。
9. 长上下文压缩、重要信息保留与下一步任务提炼。

当前代码虽然已经出现 `LlmBasedDialogueAnalyzer`、`LlmBasedMainBrainTaskDirector` 等 LLM 实现，但核心链路仍大量依赖：

- `RuleBased*` 规则类。
- `contains(...)` 关键词匹配。
- `switch` 固定枚举映射。
- 固定阈值、固定样本数、固定时间窗口。
- 固定员工编号与“前 3 个员工”兜底。
- 固定 Markdown 回复模板。
- 收到任务即 `COMPLETED` 的模拟执行器。

这些逻辑不能作为业务决策或业务产物的兜底方案。LLM 不可用、Schema 解析失败、上下文不足、工具不可用时，系统应显式返回 `FAILED` / `NEEDS_CLARIFICATION` / `NEEDS_HUMAN_REVIEW` / `CONFIGURATION_REQUIRED` 等问题状态，并记录原因；不得用规则、模板、固定员工或通用文本伪造“已完成”。否则系统会退化为规则工作流，并产生“看起来完成、实际没做事”的假阳性。

---

## 2. 核心判断原则

### 2.1 哪些应交给 LLM

| 决策类型 | 应交给 LLM 的原因 | 示例 |
|---|---|---|
| 语义理解 | 用户表达具有歧义、上下文依赖、跨领域混合意图 | “帮我看下这个合同付款条款有没有财务风险” |
| 任务规划 | 任务拆解需要结合目标、约束、资源和执行能力 | 项目开发、报告生成、跨部门协作 |
| 部门路由 | 同一请求可能涉及多个部门，不能只看关键词 | 合同 + 预算 + 审批 + 项目交付 |
| 员工选择 | 需要考虑能力、工具、负载、绩效、风险适配 | 从多个技术员工中选择架构、开发、测试 |
| 工具选择 | 工具调用意图应基于 Schema 和安全上下文 | 是否调用 OpenProject、GitLab、Jenkins、飞书 |
| 回复策略 | 是否追问、直出、等待回执、请求审批，需要上下文判断 | 高风险任务先审批，信息不足先追问 |
| 风险解释 | 风险不是阈值本身，而是业务影响和处置建议 | CPU 高与项目上线窗口结合判断 |
| 上下文压缩 | 需要保留目标、约束、决策、文件、未完成事项 | 长对话/多工具输出场景 |

### 2.2 哪些不应交给 LLM

| 类型 | 应保留硬规则的原因 |
|---|---|
| 权限校验 | 安全边界必须确定、可审计、不可被模型绕过 |
| 租户隔离 | 多租户数据访问不能由概率模型决定 |
| 审批红线 | 资金、删除、外发、生产变更等必须走确定流程 |
| API 参数校验 | 字段类型、必填、路径权限必须程序校验 |
| 沙箱限制 | 命令白名单、路径限制、网络限制必须硬约束 |
| 审计日志 | 不可由 LLM 决定是否记录 |
| LLM 失败处理策略 | 模型超时、解析失败、不可用时需要确定错误状态、重试策略、人工介入或配置修复路径；不得兜底生成业务决策或产物 |

推荐边界：

- LLM 决定“做什么、为什么、怎么拆、谁来做、怎么说”。
- 程序硬规则控制“能不能做、是否安全、是否合规、是否允许执行”，并在不能做时暴露问题状态；程序不得替 LLM 生成业务判断、员工选择、验收结论或最终业务产物。

---

## 3. 与最新代码结构指南的差异复核

根据最新 `CODE_STRUCTURE_AND_FILE_GUIDE.md`，项目代码结构相比本文最初分析时已经有明显变化：部分“建议新增”的 LLM-first 组件已经出现在 `autonomy`、`proactive/llm` 等包中。因此，本文需要从“是否存在 LLM 组件”进一步细化为“LLM 组件是否已经成为默认主路径、是否仍被硬编码 Prompt/默认值/兜底逻辑限制、是否已经接入真实上下文和真实执行闭环”。

### 3.1 已经补齐或部分补齐的能力

| 原期待能力 | 最新代码结构中的实际情况 | 差异判断 | 后续完善重点 |
|---|---|---|---|
| LLM 入口消息分析 | 已有 `impl/LlmBasedDialogueAnalyzer.java`；规则版只能用于不可执行原因诊断 | 已部分满足 | 需要确认默认 Bean/编排链路是否使用 LLM 实现；需要扩大 Prompt 上下文和输出 Schema；LLM 失败时不得用关键词分类伪装正常决策 |
| LLM 主脑任务规划 | 已有 `impl/LlmBasedMainBrainTaskDirector.java`；规则版不能替代主脑生成业务计划 | 已部分满足 | 仍需动态注入部门、员工、工具、知识、审批上下文，避免 Prompt 中写死员工范围和默认技术部；LLM 输出缺失时应追问/失败而非默认派发 |
| LLM 员工分派 | 最新结构指南列出 `impl/LlmBasedFixedEmployeeDispatcher.java` | 已补齐本文原先建议新增项 | 需要确认是否真正替代 `RegistryBackedFixedEmployeeDispatcher` 成为主路径；需要加入负载、绩效、可用性、替代方案和 Schema 校验 |
| LLM 最终回复策略 | 最新结构指南列出 `impl/LlmBasedFinalResponseCoordinator.java` | 已补齐本文原先建议新增项 | 需要确认是否默认启用；策略判断仍需接入审批状态、回执质量、用户角色、风险证据 |
| LLM 主脑回复编排 | 最新结构指南列出 `impl/LlmBasedMainBrainResponseComposer.java` | 已补齐本文原先建议新增项 | 需要避免只把执行摘要丢给 LLM 生成自然语言，应输出结构化回复类型、下一步动作和风险说明 |
| LLM 主动建议 | 最新结构指南列出 `proactive/llm/LlmProactiveAdvisor` 与实现 | 已部分满足 | `PatternPredictor` 应定位为特征层，确保 `ProactiveSuggestionService` 使用 LLM Advisor 生成最终建议 |
| LLM 风险评估 | 最新结构指南列出 `proactive/llm/LlmRiskAssessor` 与实现 | 已部分满足 | `RiskPredictor` 应定位为基础告警层，确保业务风险解释走 LLM Assessor |
| LLM 动态员工创建 | 最新结构指南新增 `LLMEmployeeCreationService` 与实现 | 超出本文原期待，属于新增能力 | 需要增加安全边界：创建条件、审批、员工编号唯一性、能力来源、工具权限、生命周期 |
| 动态员工消费者注册 | 最新结构指南列出 `DynamicEmployeeTaskConsumerRegistry` 替代模拟回执 | 已部分回应“假完成”问题 | 需要验证是否完全替代 `MinimalEmployeeTaskExecutor`，并确认回执包含执行证据和产物 |
| 聊天意图 LLM-first | 最新指南描述 `ChatIntentClassifier` 支持 LLM-first / Rule-fallback | 已在结构层面补齐 | 需要代码层确认是否真实委托 `DialogueAnalyzer`，避免仍走本地关键词分类 |
| 主动预测统计层定位 | 最新指南明确 `PatternPredictor`/`RiskPredictor` 是统计规则层，作为 LLM 特征输入 | 架构定位已修正 | 需要服务编排层确保规则层不直接作为最终智能建议输出 |

### 3.2 仍然存在的主要差异

虽然结构指南已经补齐了多个 LLM 组件，但与本文期待的“LLM 自主决策主路径”仍存在以下差异：

1. **结构存在不等于主路径已切换**
   - 文档列出了 `LlmBased*` 类，但仍需确认 `ConversationOrchestrator`、Spring 配置或工厂类中实际注入的是 LLM 实现，而不是默认规则实现。
   - 验收时不能只看类存在，要看运行 Trace 是否显示 `llm_based`。

2. **LLM 实现仍可能被硬编码 Prompt 限制**
   - 例如入口分析、主脑规划、员工分派的系统 Prompt 仍可能写死部门、策略枚举、员工代码范围或默认值。
   - 这些不再是“没有 LLM”的问题，而是“LLM 被静态 Prompt 和默认值约束”的问题。

3. **上下文组装器仍是缺口**
   - 最新结构指南没有明确列出统一的 `DecisionContextBuilder` / `ToolDecisionContextBuilder` / `KnowledgeDecisionContextBuilder`。
   - 如果 LLM 调用只传用户消息和少量计划字段，仍无法真正自主决策。

4. **结构化 Schema 与校验仍是缺口**
   - 最新结构指南强调 LLM 输出 JSON，但没有看到统一 `LlmDecisionClient`、JSON Schema 校验、修复重试、字段级错误记录的架构说明。
   - 如果解析失败后直接 fallback，可能掩盖 Prompt/Schema 质量问题。

5. **真实执行闭环仍需验证**
   - `DynamicEmployeeTaskConsumerRegistry` 的出现说明方向正确，但还需要验证员工任务是否真正进入 `EmployeeNeuron`、是否调用工具、是否生成产物、是否通过验收。
   - `MinimalEmployeeTaskExecutor` 如果仍存在或可被生产启用，仍需要明确禁用边界。

6. **执行验收层仍未完全体现**
   - 最新指南仍只列出 `DefaultExecutionResultAggregator`，没有明确 `LlmBasedExecutionResultAggregator` 或 `ExecutionReceiptReviewer`。
   - 因此“回执质量评估、产物是否满足验收标准、是否返工”仍是待补齐项。

7. **上下文压缩仍以规则实现为主**
   - 最新结构指南仍列出 `RuleBasedContextCompactor`，没有明确 `HybridContextCompactor` 或 LLM 语义压缩器。
   - 长任务中关键上下文丢失的问题仍存在。

8. **安全与审批需要嵌入 LLM 决策前后**
   - 最新结构指南有 `security`、`approval`、`sandbox` 包，但 LLM 决策链路是否在规划前、派发前、工具执行前接入这些硬规则，需要进一步明确。

### 3.3 本文档应相应调整的判断口径

后续分析不再简单表述为“缺少 LLM 实现”，而应分四类判断：

| 判断类别 | 含义 | 示例 |
|---|---|---|
| 已补齐 | LLM 类已存在，并且结构指南明确其为主实现或 LLM-first | `LlmBasedFixedEmployeeDispatcher` 已出现 |
| 部分补齐 | LLM 类已存在，但上下文、Schema、默认注入、Trace 或验收不足 | `LlmBasedMainBrainTaskDirector` 已有，但仍需动态上下文 |
| 仍缺失 | 结构指南仍未体现相应组件 | `LlmBasedExecutionResultAggregator`、`ExecutionReceiptReviewer`、`HybridContextCompactor` |
| 需验证 | 文档描述支持，但需要代码/运行链路确认 | `ChatIntentClassifier` 是否真的优先委托 `DialogueAnalyzer` |

### 3.4 完善优先级调整

基于最新结构，优先级建议调整如下：

1. **第一优先级：确认 LLM-first 是否真的成为主路径**
   - 检查 `ConversationOrchestrator`、配置类、Bean 注入、Controller/WebSocket 调用链。
   - Trace 中必须能看到每个阶段的 `decisionSource`。

2. **第二优先级：建设统一决策上下文与 Schema 层**
   - 即使 LLM 类已经存在，如果没有上下文和 Schema，仍然只能做浅层判断。

3. **第三优先级：补齐执行验收层**
   - 重点新增或完善 `ExecutionReceiptReviewer`、`LlmBasedExecutionResultAggregator`。

4. **第四优先级：将主动建议/风险评估接入服务主路径**
   - 规则 predictor 只作为特征层，最终建议由 LLM Advisor/Assessor 输出。

5. **第五优先级：上下文压缩语义化**
   - 防止长任务、多工具执行时丢失目标、约束和未完成事项。

---

## 4. 问题总览矩阵

| 编号 | 优先级 | 文件/模块 | 硬编码类型 | 当前表现 | 应由 LLM 决策 | 主要风险 | 建议处理 |
|---|---|---|---|---|---|---|---|
| A01 | P0 | `RuleBasedDialogueAnalyzer` | 关键词、固定风险 | 词表判断消息类型、复杂度、风险 | 意图、风险、追问、执行需求 | 入口误判导致后续全链路错误 | LLM 默认；规则只能返回不可判定/需模型恢复的问题状态，不能替代分类 |
| A02 | P0 | `LlmBasedDialogueAnalyzer` | Prompt/Schema 过窄、仍有硬默认 | 系统 Prompt 写死部门代码；无用户权限、知识、工具上下文 | 上下文增强的结构化入口分析 | LLM 虽存在但智能度受限 | 扩展 Prompt 与 JSON Schema；解析失败不得默认部门/任务类型 |
| A03 | P0 | `RuleBasedMainBrainTaskDirector` | 关键词、固定模板、固定员工编号 | 任务类型、部门、交付物、验收标准、员工编码写死 | 主脑任务规划、跨部门拆解 | 主脑规划被规则替代 | 禁止作为业务计划兜底；LLM 不可用时返回规划失败/需重试 |
| A04 | P0 | `LlmBasedMainBrainTaskDirector` | Prompt 固定、员工范围写死、兜底写死 tech | 员工代码范围写在 Prompt；解析失败默认 `tech`、`T02/T09` | 基于真实员工/工具/绩效选人 | “LLM 实现”仍半硬编码 | 动态注入员工画像和工具 Schema |
| A05 | P0 | `RegistryBackedFixedEmployeeDispatcher` / `LlmBasedFixedEmployeeDispatcher` | 规则兜底、Prompt 半硬编码 | 最新结构已补齐 LLM Dispatcher，但规则版仍可能兜底，需确认主路径和上下文质量 | 员工匹配、负载、能力、替代方案 | 员工调度可能仍受静态 Prompt 或兜底影响 | 确认 LLM Dispatcher 默认启用，并补齐负载/绩效/可用性上下文；LLM 失败时不得按前 N 个员工或固定员工派发 |
| A06 | P0 | `MinimalEmployeeTaskExecutor` / `DynamicEmployeeTaskConsumerRegistry` | 假执行风险、真实消费者待验证 | 最新结构已有动态员工消费者注册，但需确认是否完全替代模拟回执 | 员工神经元真实执行、验证、失败判断 | 若模拟执行仍可生产启用，会形成虚假闭环 | 生产禁用 Minimal，验证 Dynamic consumer 进入 EmployeeNeuron |
| A07 | P1 | `DefaultFinalResponseCoordinator` / `LlmBasedFinalResponseCoordinator` | 规则兜底、策略上下文不足 | 最新结构已补齐 LLM 策略协调器，但默认注入和上下文仍需确认 | 回复策略、追问、审批、人类升级 | 复杂会话策略可能仍缺少审批/回执质量/用户角色 | 确认 LLM Coordinator 主路径，扩展策略输入；LLM 失败时只输出问题状态，不得生成业务策略 |
| A08 | P1 | `DefaultMainBrainResponseComposer` / `LlmBasedMainBrainResponseComposer` | 模板兜底、自然语言但非结构化 | 最新结构已补齐 LLM Composer，但仍需结构化回复类型和下一步动作 | 面向角色和状态的动态回复 | 输出可能自然但不可控，风险/审批/下一步缺失 | LLM Composer 输出结构化草稿 + 自然语言正文；模板不得替代最终业务结论 |
| A09 | P1 | `DefaultExecutionResultAggregator` | 固定状态与模板 | 只统计 `COMPLETED`/`FAILED`，拼接 emoji | 结果摘要、质量评估、验收判断 | 回执质量不可控 | LLM 汇总 + 程序机械校验；程序不得自行宣称验收通过 |
| A10 | P1 | `ChatIntentClassifier` | 关键词、长度阈值 | 闲聊/工具/复杂任务由词表和长度决定 | 聊天路由和工具意图 | 与自治入口重复且易冲突 | 合并到统一 LLM 分类服务 |
| A11 | P1 | `RuleBasedContextCompactor` | 固定 token 估算、正则摘要 | 长上下文靠长度/正则/模板压缩 | 重要事实、未完成任务、决策保留 | 丢失关键上下文 | 引入 LLM/语义压缩 |
| A12 | P2 | `PatternPredictor` / `LlmProactiveAdvisor` | 规则特征层 + LLM 建议层待确认 | 最新结构已将 Predictor 定位为统计特征层，并新增 LLM Advisor | 业务语义主动建议 | 若服务主路径仍直接使用 Predictor，主动服务仍机械 | 确认 `ProactiveSuggestionService` 接入 LLM Advisor |
| A13 | P2 | `RiskPredictor` / `LlmRiskAssessor` | 阈值告警层 + LLM 风险层待确认 | 最新结构已新增 LLM Risk Assessor，但需确认主路径使用 | 业务风险评估、影响范围、处置方案 | 若直接输出阈值建议，仍会误报/漏报 | 确认风险服务先规则告警，再 LLM 解释 |
| A14 | P2 | `DefaultAssignmentPreparationService` | 固定状态 | `NO_ASSIGNMENT` / `READY_FOR_DEPARTMENT_COORDINATION` | 任务准备度、缺口、追问项 | 准备状态过粗 | LLM 评估准备度 |

---

## 4. 详细问题分析与代码级证据

### 4.1 A01：对话入口分类被关键词规则替代

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/RuleBasedDialogueAnalyzer.java`

#### 当前硬编码表现

- `TASK_KEYWORDS`、`PROJECT_KEYWORDS`、`APPROVAL_KEYWORDS`、`KNOWLEDGE_KEYWORDS` 使用固定词表。
- `detectMessageKind` 通过 `stream().anyMatch(lowerMessage::contains)` 判断消息类型。
- `detectTaskIntent` 使用 `message.contains("网页")`、`message.contains("代码")` 等判断意图。
- `estimateComplexity` 主要根据消息类型和长度加分。
- `estimateRiskLevel` 根据消息类型和复杂度固定映射。
- `mapDepartmentToBrain` 通过 `switch` 把部门字符串映射到 Brain 名称。

#### 关键问题

1. `KNOWLEDGE_KEYWORDS` 定义后没有在 `detectMessageKind` 中实际返回 `KNOWLEDGE`，知识类问题可能被错误归为 `CHAT`。
2. 跨部门判断依赖部门关键词命中数量，无法识别隐含跨部门关系。
3. 复杂度和风险缺少用户身份、权限、金额、外部系统动作、数据敏感度、审批状态等上下文。
4. Brain 路由不应靠字符串拼接，而应结合 `BrainRegistry`、部门职责和 LLM 输出。

#### 应改为 LLM 决策

LLM 应输出结构化 `DialogueDecision`：

```json
{
  "kind": "TASK",
  "intent": "contract_payment_risk_review",
  "primaryDepartment": "legal",
  "supportingDepartments": ["finance"],
  "requiresTaskExecution": true,
  "requiresMainBrainPlanning": true,
  "requiresClarification": false,
  "clarificationQuestion": null,
  "complexity": 4,
  "riskLevel": 4,
  "riskReasons": ["涉及合同付款条款", "涉及预算口径确认"],
  "decisionReason": "该请求同时包含合同合规审查和财务预算确认"
}
```

#### 改造要点

- `RuleBasedDialogueAnalyzer` 保留为 fallback，不应作为默认 Bean。
- Trace 中记录：`analyzer_type=llm_based` 或 `rule_based_fallback`。
- LLM Prompt 注入：用户身份、部门、会话历史摘要、可用部门、工具 Schema、知识库摘要、权限与审批约束。

#### 验收标准

- 同一句包含多个部门隐含职责的请求，能输出主责部门和协作部门。
- 信息不足时能输出 `requiresClarification=true`，而不是强行规划。
- 高风险请求能给出风险理由，而不是仅给 1-5 分。
- LLM 不可用时才出现 `rule_based_fallback`。

---

### 4.2 A02：已有 LLM 入口分析器仍存在硬编码边界

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedDialogueAnalyzer.java`

#### 当前硬编码表现

- `ANALYSIS_SYSTEM_PROMPT` 写死消息类型、部门代码和部门说明。
- `buildAnalysisPrompt` 只传“当前用户所在部门 + 用户消息”，上下文过少。
- `parseAnalysisResponse` 中 `requiresTaskExecution` 被固定为 `TASK/PROJECT/APPROVAL`。
- `mapDepartmentToBrain(null)` 默认返回 `TechBrain`。
- JSON 字段缺少 `requiresMainBrainPlanning`、`requiresClarification`、`clarificationQuestion`、`riskReasons`、`decisionReason`。

#### 关键问题

这说明项目虽然已经引入 LLM，但 LLM 决策输入和输出协议过窄，仍然被硬编码框架限制。LLM 只能在有限枚举中选择，无法充分利用项目已有 API 和员工/工具上下文。

#### 建议细化

1. Prompt 中部门、员工、工具不要写死，改为运行时从注册表/API 摘要注入。
2. 默认部门不能是 `tech`，应为 `main` 或 `currentDepartment`，且允许 `unknown` 触发追问。
3. 增加严格 JSON Schema 校验，校验失败时记录原始响应与失败原因。
4. `requiresTaskExecution` 应由 LLM 显式输出，再由程序做安全校验。

#### 验收标准

- 不修改代码即可新增部门或员工，并被 LLM 分析器识别。
- `TechBrain` 不再作为 null 部门默认值。
- 分析结果包含可解释字段：`decisionReason`、`riskReasons`。

---

### 4.3 A03：主脑任务规划被规则映射替代

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/RuleBasedMainBrainTaskDirector.java`

#### 当前硬编码表现

- `detectTaskType` 根据“网页/代码/合同/预算/招聘/销售/投诉/行政/数据/文档”等关键词返回固定任务类型。
- `detectPrimaryDepartment` 根据关键词和任务类型返回固定部门。
- `detectDeliverables` 用 `switch` 返回固定交付物，例如 `web_prototype` 固定为 `index.html/style.css/script.js/运行说明/验证结果`。
- `detectAcceptanceCriteria` 用 `switch` 返回固定验收标准。
- `createDepartmentPlan` 写死每个部门的建议角色和员工编码。
- 技术原型固定 `T02/T09/T04`，普通技术开发固定 `T02/T10/T01`。

#### 关键问题

主脑规划是自治系统的核心，但该类把主脑降级成了“关键词到模板”的映射器。它无法根据用户目标、业务上下文、当前员工状态、工具可用性、任务风险、历史知识动态生成计划。

#### 应改为 LLM 决策

主脑应输出 `MainBrainTaskPlan`，至少包括：

```json
{
  "taskType": "contract_payment_risk_review",
  "goal": "评估合同付款条款并确认预算口径",
  "primaryDepartment": "legal",
  "supportingDepartments": ["finance"],
  "steps": [
    {"id": "s1", "department": "legal", "objective": "审查付款条款和违约责任"},
    {"id": "s2", "department": "finance", "objective": "核对预算来源和付款节奏"}
  ],
  "deliverables": ["合同风险清单", "预算口径确认", "综合处理建议"],
  "acceptanceCriteria": ["列明风险等级和依据", "给出可执行修改建议", "明确是否需要审批"],
  "requiresApproval": true,
  "approvalReason": "涉及合同付款和预算确认"
}
```

#### 验收标准

- 不同用户提出同一领域但不同目标的任务，交付物和验收标准应不同。
- 跨部门任务能生成多部门 `departmentPlans`。
- 员工编号不再由 `RuleBasedMainBrainTaskDirector` 写死。
- 规则 Director 只在 LLM 失败时被调用。

---

### 4.4 A04：LLM 主脑规划器本身仍有半硬编码问题

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedMainBrainTaskDirector.java`

#### 当前硬编码表现

- `TASK_PLAN_SYSTEM_PROMPT` 写死任务类型枚举、部门代码、员工代码范围。
- Prompt 中只告诉 LLM “技术部 T01-T12、财务 F01-F06”等范围，而不传真实员工画像。
- `buildUserPrompt` 只传入口分类信息和用户消息，没有工具、知识、员工状态、API 能力。
- `parseDepartmentPlans` 如果为空，默认添加 `tech`、`architect/frontend`、`T02/T09`。
- `primaryDepartment` 解析默认值是 `tech`。

#### 关键问题

这会造成一种“看似 LLM 决策、实际被固定 Prompt 和默认值限制”的问题。LLM 缺少真实上下文时，只能按硬编码枚举猜测；解析失败时又回到技术部默认员工，容易把非技术任务误派给技术部。

#### 建议细化

- Prompt 中部门和员工清单从 `FixedEmployeeRegistry` / 数据库动态生成。
- 注入员工字段：`code`、`name`、`department`、`title`、`roles`、`capabilities`、`tools`、`active`、`currentLoad`、`recentPerformance`。
- 注入工具 Schema 摘要，避免 LLM 规划不可执行动作。
- 去掉 `tech/T02/T09` 默认值，改为 `requiresClarification` 或 `NO_ASSIGNMENT_WITH_REASON`。
- `departmentPlans` 为空应被视为低质量 LLM 输出并触发修复 Prompt，而非静默默认技术部。

#### 验收标准

- 新增员工后，无需改 Prompt 常量，规划器能看到并使用。
- 非技术任务解析失败时不会默认派给技术部。
- 计划 metadata 中记录 `llm_model`、`prompt_version`、`context_sources`、`fallback_reason`。

---

### 4.5 A05：数字员工分派规则化，缺少智能选人

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/RegistryBackedFixedEmployeeDispatcher.java`

#### 当前硬编码表现

- 优先使用 `DepartmentTaskPlan.suggestedEmployeeCodes()`。
- 如果为空，则按角色或能力字符串包含匹配员工。
- 如果仍为空，则 `definitions.stream().limit(3)` 直接取前 3 个。
- `buildInstruction` 以固定模板拼接任务指令。

#### 关键问题

1. “前 3 个员工”完全不考虑能力、工具、负载、历史表现、当前状态。
2. 字符串 `contains` 不能表示复杂能力匹配。
3. 固定 instruction 无法体现员工个性、工具权限、任务风险和上下文。
4. 分派结果没有记录选人理由，不利于复盘和进化。

#### 建议细化

新增 `LlmBasedFixedEmployeeDispatcher`，或者将员工分派完全纳入 `MainBrainTaskPlan`。推荐输出：

```json
{
  "assignments": [
    {
      "employeeCode": "L01",
      "role": "contract_reviewer",
      "objective": "审查合同付款条款",
      "instruction": "重点检查付款节点、违约责任、发票条件和审批要求",
      "selectionReason": "具备合同审查能力，拥有法务知识库访问权限",
      "confidence": 0.86,
      "requiredTools": ["office", "knowledge_search"],
      "alternatives": ["L02"]
    }
  ]
}
```

#### 验收标准

- 分派结果必须包含 `selectionReason`。
- 员工不可用或能力不足时能输出替代员工或请求人工介入。
- 不再出现“无理由取前 3 个员工”的主路径。

---

### 4.6 A06：员工执行回执存在“假完成”风险

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/MinimalEmployeeTaskExecutor.java`

#### 当前硬编码表现

- 订阅员工任务 Channel。
- 收到消息后立即创建 `EmployeeExecutionReceipt`。
- 状态固定为 `COMPLETED`。
- `buildSummary` 只是截断内容前 120 字。

#### 关键问题

该类适合演示或测试，但如果进入生产主链路，会让系统误以为任务已完成。它没有调用模型、工具、知识库，也没有产物和验证。

#### 建议细化

- 增加配置项：`living-agent.autonomy.minimal-executor.enabled=false`，生产默认关闭。
- 员工任务应进入 `EmployeeNeuron` 或员工专属执行器。
- 回执结构扩展：
  - `executionSteps`
  - `toolCalls`
  - `artifacts`
  - `validationResults`
  - `failureReason`
  - `confidence`
  - `needsHumanReview`

#### 验收标准

- 生产环境不会出现由 `MinimalEmployeeTaskExecutor` 生成的 `COMPLETED` 回执。
- 每个完成回执至少包含实际执行证据：工具结果、产物、模型输出或验证记录。
- 未满足验收标准时不能标记 `COMPLETED`。

---

### 4.7 A07：最终回复策略被简单条件判断替代

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultFinalResponseCoordinator.java`

#### 当前硬编码表现

- `decision.requiresTaskExecution()` 或 `mainBrainTaskPlan != null` 就返回 `MAIN_BRAIN_COMPOSE`。
- `CROSS_DEPARTMENT` 也返回 `MAIN_BRAIN_COMPOSE`。
- 其他默认 `DEPARTMENT_BRAIN_DIRECT`。

#### 关键问题

实际回复策略应考虑：信息是否充分、是否需要审批、是否已有回执、回执是否可信、是否高风险、用户角色是否需要简版或详版、是否需要人类介入。

#### 建议细化

新增 `LlmBasedFinalResponseCoordinator`，输出：

```json
{
  "strategy": "ASK_CLARIFICATION",
  "reason": "缺少合同文件或付款金额，无法进行有效风险判断",
  "userMessageType": "clarification_question",
  "requiresApproval": false,
  "nextAction": "ask_user_for_contract_file"
}
```

允许策略：

- `DIRECT_ANSWER`
- `ASK_CLARIFICATION`
- `MAIN_BRAIN_COMPOSE`
- `WAIT_FOR_RECEIPTS`
- `REQUEST_APPROVAL`
- `ESCALATE_TO_HUMAN`
- `RETRY_FAILED_ASSIGNMENTS`

#### 验收标准

- 信息不足时优先追问，不直接进入执行。
- 高风险操作优先审批或人工升级。
- 部门咨询类也能在必要时回主脑统筹。

---

### 4.8 A08：用户回复内容由固定模板拼接

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultMainBrainResponseComposer.java`

#### 当前硬编码表现

- 在 `brainRawResponse` 后固定追加“任务信息、类型、执行团队、交付物、验收标准”。
- 执行状态只识别 `WAITING_RECEIPT`、`NO_ASSIGNMENT`。
- 无原始回复时返回“任务已接收，正在处理中...”。

#### 关键问题

输出缺少面向不同角色的表达差异，也不会根据任务风险、审批状态、失败原因、交付物质量动态组织回复。

#### 建议细化

新增 `LlmBasedMainBrainResponseComposer`。输入上下文包括：

- 用户角色和部门。
- 原始请求。
- 主脑计划。
- 员工分派和选人理由。
- 执行回执和产物。
- 风险评估。
- 审批状态。
- 需要用户确认的问题。

输出类型包括：

- `brief_status`
- `execution_plan`
- `completion_summary`
- `risk_warning`
- `clarification_request`
- `approval_request`

#### 验收标准

- 董事长视角输出战略摘要，执行员工视角输出操作细节。
- 失败或等待状态不能用成功语气包装。
- 输出必须明确下一步动作。

---

### 4.9 A09：执行结果聚合只做固定计数和模板拼接

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultExecutionResultAggregator.java`

#### 当前硬编码表现

- 只识别 `COMPLETED` 和 `FAILED`。
- 用固定 Markdown 和 emoji 拼接每个员工摘要。
- 只统计完成数和失败数。
- 不判断回执质量、产物是否满足验收标准、是否需要返工。

#### 关键问题

执行结果聚合不应只看状态字符串。真正的主脑应判断：员工是否完成了目标、产物是否完整、不同员工输出是否冲突、是否遗漏验收标准、是否需要二次分派。

#### 建议细化

新增 `LlmBasedExecutionResultAggregator`，结合程序校验输出：

```json
{
  "overallStatus": "PARTIALLY_COMPLETED",
  "qualityScore": 0.72,
  "completedCriteria": ["列出风险点", "给出修改建议"],
  "missingCriteria": ["缺少预算口径确认"],
  "conflicts": [],
  "retryAssignments": ["finance_budget_check"],
  "summaryForUser": "法务审查已完成，财务预算口径仍需确认。"
}
```

#### 验收标准

- 只要有验收标准未满足，整体状态不能简单标记完成。
- 能识别员工回执之间的冲突或重复。
- 能给出返工或补充分派建议。

---

### 4.10 A10：聊天神经元路由使用关键词分类

**文件：** `living-agent-core/src/main/java/com/livingagent/core/neuron/chat/ChatIntentClassifier.java`

#### 当前硬编码表现

- 固定 `GREETINGS`、`CASUAL_PATTERNS`、`TOOL_KEYWORDS`、`COMPLEX_KEYWORDS`。
- `QUESTION_PATTERN` 与长度 `< 50` 判断简单问题。
- `wordCount <= 5` 默认简单问题。

#### 关键问题

1. “删除生产库”只有 5 个字，但不是简单问题。
2. “帮我看看这个客户为什么流失”可能没有工具关键词，但需要复杂分析。
3. 与 `DialogueAnalyzer` 形成两套路由，容易结果不一致。

#### 建议细化

- 将聊天入口统一委托给 `DialogueAnalyzer`。
- `ChatIntentClassifier` 只作为无模型情况下的快速兜底。
- 工具调用意图由 LLM 基于工具 Schema 输出。

#### 验收标准

- 同一输入在聊天入口和部门入口得到一致分类。
- 高风险短指令不会被识别为简单问题。

---

### 4.11 A11：上下文压缩仍是规则摘要

**文件：** `living-agent-core/src/main/java/com/livingagent/core/brain/compact/impl/RuleBasedContextCompactor.java`

#### 当前硬编码表现

- token 估算默认按 `content.length() / 4`。
- 超过限制后固定保留最近 4 条消息。
- 用正则提取文件路径和待办关键词。
- `summarizeMessages` 输出固定 `<summary>` 模板。
- 工具结果超过 120 字时直接替换为固定提示。

#### 关键问题

上下文压缩是 LLM 长任务的关键能力。规则压缩容易丢失：业务目标、关键约束、已做决策、失败原因、用户偏好、审批状态、工具调用细节。

#### 建议细化

- 新增 `LlmContextCompactor` 或 `HybridContextCompactor`。
- 程序先做结构化提取：文件、工具、任务、回执、错误。
- LLM 再生成语义摘要：目标、约束、已完成、未完成、风险、下一步。
- 保留原文引用位置或持久化路径，避免摘要不可追溯。

#### 验收标准

- 压缩后仍能恢复当前任务目标、未完成事项、关键文件、失败原因。
- 工具输出被截断时必须保存完整内容路径。
- 压缩摘要包含 `decisions`、`openQuestions`、`nextSteps`。

---

### 4.12 A12：主动预测仍以固定统计规则为主

**文件：** `living-agent-core/src/main/java/com/livingagent/core/proactive/predictor/PatternPredictor.java`

#### 当前硬编码表现

- 固定 `MIN_SAMPLES = 5`。
- 固定 `CONFIDENCE_THRESHOLD = 0.7`。
- 固定 `PATTERN_WINDOW_DAYS = 30`。
- 时间模式、序列模式、资源访问模式都是固定统计规则。
- 洞察文本固定为“用户通常在 X:00 最活跃”等。

#### 关键问题

主动服务不只是预测“下一次可能访问什么”，更重要的是判断“现在是否应该打扰用户、建议什么动作、对业务有什么价值”。这需要业务语义理解。

#### 建议细化

- 统计模块只输出特征，不直接输出最终建议。
- 新增 `LlmProactiveSuggestionService`：将行为特征、日程、任务、项目状态、用户角色输入 LLM。
- 输出建议需包含：触发原因、建议动作、预期收益、打扰等级、可执行工具。

#### 验收标准

- 主动建议必须说明业务理由，而不仅是“你常在这个时间操作”。
- 用户忙碌或低收益场景能选择不打扰。
- 不同角色看到不同主动建议。

---

### 4.13 A13：风险预测使用固定阈值和固定建议话术

**文件：** `living-agent-core/src/main/java/com/livingagent/core/proactive/predictor/RiskPredictor.java`

#### 当前硬编码表现

- 固定注册 `error_rate`、`response_time`、`cpu_usage`、`memory_usage`、`disk_usage`、`project_deadline`、`task_completion`。
- 指标阈值写死，如 CPU `0.8/0.95`、响应时间 `2000/5000`。
- `generateRecommendation` 用固定模板输出“【紧急】/【警告】/【注意】”。

#### 关键问题

风险不是单纯指标越高越危险。风险等级取决于业务场景、时间窗口、用户影响、项目阶段、合规要求、历史事故、可恢复性。

#### 建议细化

- 固定阈值保留为基础告警层。
- 新增 LLM 风险解释层，将指标与业务上下文结合。
- 输出：风险等级、证据、影响范围、根因假设、建议动作、是否审批、是否升级人工。

#### 验收标准

- 同样 CPU 90%，在压测环境和生产高峰应有不同解释。
- 风险建议必须包含可执行处置步骤。
- 高风险建议能触发审批/人工介入。

---

### 4.14 A14：任务准备状态过粗

**文件：** `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultAssignmentPreparationService.java`

#### 当前硬编码表现

- metadata 中 `executionReadiness` 只有 `NO_ASSIGNMENT` 或 `READY_FOR_DEPARTMENT_COORDINATION`。
- 不判断任务信息是否完整、工具是否可用、是否需要审批、员工是否具备权限。

#### 建议细化

引入 `AssignmentReadinessEvaluator`，可由程序校验 + LLM 判断组合：

```json
{
  "readiness": "NEEDS_CLARIFICATION",
  "blockingIssues": ["缺少合同文件", "未确认预算金额"],
  "suggestedQuestions": ["请上传合同文件", "请确认预算所属项目"],
  "safeToDispatch": false
}
```

#### 验收标准

- 信息缺失时不会直接 `READY_FOR_DEPARTMENT_COORDINATION`。
- 工具不可用或权限不足时能给出阻塞原因。

---

## 5. 与项目 API 能力的对应关系

`API_REFERENCE.md` 中已经存在大量接口，为 LLM 自主决策提供上下文和执行出口。当前问题不是“没有 API”，而是“LLM 决策层没有充分消费这些 API/服务”。

| API/模块 | 可提供给 LLM 的上下文 | 应用于哪类决策 |
|---|---|---|
| 认证/用户 | 用户身份、角色、权限、租户 | 风险、审批、可见性、回复粒度 |
| 部门模块 | 部门职责、组织结构 | 主责/协作部门路由 |
| 员工模块 | 员工状态、职位、所属部门 | 员工分派、任务升级 |
| 固定员工模块 | 数字员工能力、工具、角色 | 员工选择、角色分工 |
| 神经元模块 | 神经元状态、可执行能力 | 任务执行路径 |
| 模型池/大脑模型配置 | 当前可用模型、部门模型分配 | LLM 调用、模型选择 |
| 技能模块 | 可用技能和技能说明 | 工具/技能选择 |
| 知识库模块 | 企业制度、经验、专业知识 | 规划、风险判断、回复依据 |
| 审批模块 | 审批规则、审批状态 | 是否需要审批、审批流发起 |
| 任务/项目模块 | 当前项目、任务状态、里程碑 | 任务规划、主动提醒 |
| 绩效/积分模块 | 员工历史贡献、质量表现 | 员工选择、激励策略 |
| 监控模块 | 系统指标、告警 | 风险预测和处置建议 |
| 主动服务模块 | 行为事件、提醒、建议 | 主动预测、打扰判断 |

建议新增一个“LLM 决策上下文组装器”：

- `DecisionContextBuilder`
- `EmployeeDecisionContextBuilder`
- `ToolDecisionContextBuilder`
- `KnowledgeDecisionContextBuilder`
- `RiskDecisionContextBuilder`

用于在调用 LLM 前统一采集、裁剪、脱敏、摘要上下文。

---

## 6. 推荐目标架构

### 6.1 当前链路风险

当前常见链路接近：

```text
用户消息
  -> 关键词/规则分类
  -> 规则主脑规划
  -> 固定员工编码/前 3 个员工
  -> 模拟执行器或简单派发
  -> 固定模板回复
```

这条链路的问题是：每一步都可能硬编码，最终即使接入了模型，也只是局部生成文本，核心决策仍不智能。

### 6.2 推荐链路

```text
用户消息
  -> DecisionContextBuilder 组装上下文
  -> LlmBasedDialogueAnalyzer 输出结构化入口判断
  -> 安全/权限/审批硬规则校验
  -> LlmBasedMainBrainTaskDirector 输出任务计划
  -> LlmBasedFixedEmployeeDispatcher 输出员工分派和理由
  -> EmployeeNeuron 执行任务并调用工具/知识库
  -> 程序校验 + LLM 验收回执
  -> LlmBasedExecutionResultAggregator 汇总结果
  -> LlmBasedFinalResponseCoordinator 决定回复策略
  -> LlmBasedMainBrainResponseComposer 输出用户回复
  -> 知识沉淀/绩效记录/Trace
```

### 6.3 LLM 与硬规则的分层

| 层级 | 职责 | 实现建议 |
|---|---|---|
| 输入理解层 | 意图、风险、追问、部门路由 | LLM 主导；LLM 不可用时返回不可判定/需重试，不使用关键词兜底替代 |
| 规划层 | 目标、步骤、交付物、验收标准 | MainBrain LLM 主导；规划失败时阻断派发或追问 |
| 安全层 | 权限、审批、租户、沙箱 | 硬规则主导；只允许/拒绝/要求审批，不生成业务产物 |
| 调度层 | 员工选择、工具选择、负载权衡 | LLM + 程序约束；不得按固定员工或前 N 个员工兜底 |
| 执行层 | 员工神经元、工具调用、产物生成 | EmployeeNeuron/ToolExecutor；模型/工具不可用时失败，不生成通用模板产物 |
| 验收层 | 结果是否满足标准 | 程序机械校验 + LLM 语义判断；程序不得硬编码业务验收结论 |
| 回复层 | 追问、总结、风险提示、下一步 | LLM 主导；模板只能输出错误/等待/需配置状态，不能替代业务总结 |
| 记忆层 | 知识沉淀、绩效、经验 | 程序记录 + LLM 提炼；提炼失败时记录原始证据，不伪造经验 |

---

## 7. 分阶段改造方案

> 结合最新代码结构指南，部分 LLM 组件已经出现，因此改造重点从“新增类”调整为“确认主路径、补上下文、补 Schema、补执行验收”。

### 阶段零：先做实际链路核验

目标：确认最新结构指南中列出的 LLM 组件是否真的在运行时主路径生效。

核验清单：

1. `ConversationOrchestrator` 是否默认使用 `LlmBasedDialogueAnalyzer`、`LlmBasedMainBrainTaskDirector`、`LlmBasedFixedEmployeeDispatcher`。
2. 最终回复是否默认经过 `LlmBasedFinalResponseCoordinator` 和 `LlmBasedMainBrainResponseComposer`。
3. `ChatIntentClassifier` 是否真的优先委托 `DialogueAnalyzer`，而不是仍独立关键词分类。
4. `ProactiveSuggestionService` 是否调用 `LlmProactiveAdvisor`，而不是直接输出 `PatternPredictor` 结果。
5. 风险服务是否调用 `LlmRiskAssessor`，而不是直接输出 `RiskPredictor` 固定话术。
6. 员工执行是否由 `DynamicEmployeeTaskConsumerRegistry` 注册真实消费者，是否仍有 `MinimalEmployeeTaskExecutor` 参与生产链路。
7. Trace 是否能展示每阶段 `llm_based` / `rule_based_fallback`。

验收：

- 运行一条跨部门任务，Trace 能看到入口分析、主脑规划、员工分派、回复策略、回复编排的 LLM 决策来源。
- 关闭 MainBrain 或模型后，才出现 fallback。
- 不允许类存在但主路径未接入。

### 阶段一：把现有规则降级为 fallback

目标：不大改业务结构，先保证默认路径是 LLM。

任务：

1. 确认 `DialogueAnalyzer` 默认注入 `LlmBasedDialogueAnalyzer`。
2. 确认 `MainBrainTaskDirector` 默认注入 `LlmBasedMainBrainTaskDirector`。
3. 所有 fallback 都必须记录 `fallback_reason`。
4. Trace 中增加 `decision_source` 字段。
5. 移除 `TechBrain`、`tech`、`T02/T09` 这类不合理默认值。

验收：

- 正常模型可用时，Trace 中核心决策均显示 `llm_based`。
- 人为关闭模型后，才出现 `rule_based_fallback`。
- fallback 不会静默伪装成正常智能决策。

### 阶段二：补齐结构化 JSON Schema

目标：让 LLM 输出可解析、可校验、可追踪。

建议 Schema：

- `DialogueDecisionSchema`
- `MainBrainTaskPlanSchema`
- `EmployeeDispatchPlanSchema`
- `AssignmentReadinessSchema`
- `ExecutionReceiptReviewSchema`
- `FinalResponseStrategySchema`
- `RiskAssessmentSchema`
- `ProactiveSuggestionSchema`
- `ContextCompactionSchema`

验收：

- LLM 输出解析失败时能自动重试一次“修复 JSON”。
- JSON 校验失败会记录字段级错误。
- 不允许缺失关键字段后自动默认技术部或默认员工。

### 阶段三：建设决策上下文组装器

目标：让 LLM 看到真实系统状态，而不是只看用户一句话。

上下文来源：

1. 用户身份、部门、租户、权限。
2. 会话历史和压缩摘要。
3. 部门职责和可用 Brain。
4. 固定员工定义、能力、工具、状态、负载、绩效。
5. 工具 Registry 和 Tool Schema。
6. 知识库检索结果。
7. 项目、任务、审批上下文。
8. 监控、风险、主动服务上下文。

验收：

- LLM Prompt 中能看到真实员工能力而不是固定代码范围。
- 工具选择基于 Tool Schema。
- 任务规划能引用相关知识或项目上下文。

### 阶段四：真实员工执行闭环

目标：让“数字员工完成任务”从模拟回执变为真实执行。

任务：

1. 禁用生产环境 `MinimalEmployeeTaskExecutor`。
2. 任务派发到 `EmployeeNeuron`。
3. 员工神经元调用部门 Brain、模型、工具、知识库。
4. 回执包含执行证据和产物。
5. 引入回执验收：程序检查 + LLM 判断。
6. 未通过验收时触发返工、重派或人工介入。

验收：

- 完成回执必须有产物或工具执行证据。
- 回执不满足验收标准时不能进入最终完成。
- 主脑能对多员工结果进行综合判断。

### 阶段五：主动服务和风险预测智能化

目标：从“阈值告警/统计预测”升级为“语义主动建议”。

任务：

1. `PatternPredictor` 只输出统计特征。
2. `RiskPredictor` 只输出基础告警。
3. 新增 LLM 建议层和风险解释层。
4. 主动建议接入用户偏好和打扰策略。

验收：

- 主动建议包含业务理由、收益、打扰等级。
- 风险建议包含证据、影响范围、处置步骤。
- 不同部门/角色收到不同解释和建议。

### 阶段六：按当前代码差距补齐生产闭环

结合当前代码核对，项目已经有大量 `LlmBased*` 类，但仍存在“类存在、Bean 注册存在、主流程未完整生效”的问题。下面给出需要直接落地的修复细节。

#### 6.1 修复模型不可用时降级摘要误标记 `COMPLETED`

**涉及文件：**

- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DynamicEmployeeTaskConsumerRegistry.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeExecutionReceipt.java`

当前风险：

- `DynamicEmployeeTaskConsumerRegistry.executeWithModelPool(...)` 在所有模型调用失败后，会返回“任务执行完成（...）”形式的降级摘要。
- 随后 `sendReceipt(...)` 固定写入 `status = COMPLETED`。
- 这会导致模型失败、无真实产物、无工具执行证据时仍被上层视为完成。

建议改法：

1. 将员工执行结果从纯字符串改为结构化对象，例如 `EmployeeTaskExecutionOutcome`：
   - `status`: `COMPLETED` / `FAILED` / `DEGRADED` / `NEEDS_RETRY`
   - `summary`
   - `modelProvider`
   - `modelName`
   - `artifacts`
   - `toolCalls`
   - `failureReason`
   - `confidence`
2. `executeWithModelPool(...)` 成功调用模型且返回有效内容时，才允许 `COMPLETED`。
3. 模型全部失败时返回 `DEGRADED` 或 `NEEDS_RETRY`，不能返回 `COMPLETED`。
4. `sendReceipt(...)` 根据 outcome.status 写回执状态，不再固定 `COMPLETED`。
5. `DEGRADED` 状态必须触发重试、人工介入或提示用户“模型暂不可用，尚未真实完成”。

验收标准：

- 关闭全部模型后，员工回执状态不能是 `COMPLETED`。
- 降级摘要必须包含 `failureReason` 和 `needsRetry=true`。
- `FinalResponseCoordinator` 遇到 `DEGRADED` / `NEEDS_RETRY` 时不能输出完成话术。

#### 6.2 将 `AssignmentReadinessEvaluator` 接入派发前置流程

**涉及文件：**

- `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultAssignmentPreparationService.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmAssignmentReadinessEvaluator.java`

当前风险：

- `LlmAssignmentReadinessEvaluator` 已存在，但主流程仍主要依赖 `DefaultAssignmentPreparationService.prepare(...)`。
- `DefaultAssignmentPreparationService` 只输出 `NO_ASSIGNMENT` 或 `READY_FOR_DEPARTMENT_COORDINATION`，没有阻止信息不足、工具不可用、员工缺能力的任务继续派发。

建议改法：

1. 在 `DepartmentChatService.prepareAssignmentBatch(...)` 中注入并调用 `AssignmentReadinessEvaluator`。
2. 在生成 `PreparedAssignmentBatch` 前或后执行：
   - 检查任务目标是否清晰。
   - 检查验收标准是否明确。
   - 检查员工是否存在、是否有 neuronId、是否有必要工具。
   - 检查是否需要审批或追问。
3. 将评估结果写入 `PreparedAssignmentBatch.metadata()`：
   - `readinessStatus`
   - `readinessScore`
   - `blockingIssues`
   - `clarificationQuestions`
4. 如果状态为 `BLOCKED` 或 `NEEDS_CLARIFICATION`：
   - 不调用 `departmentExecutionCoordinator.coordinate(...)`。
   - 交给 `FinalResponseCoordinator` 输出追问或人工介入策略。

建议流程：

```text
mainBrainTaskPlan
  -> fixedEmployeeDispatcher.planAssignments(...)
  -> assignmentReadinessEvaluator.evaluate(...)
  -> READY: prepare + coordinate
  -> NEEDS_CLARIFICATION: ask user
  -> BLOCKED: stop dispatch / escalate
```

验收标准：

- “帮我报销一下”这类信息不足请求不会进入员工执行通道。
- 员工缺少 `employeeNeuronId` 时不会派发。
- 准备度结果能在 Trace 和最终回复中体现。

#### 6.3 将 `ExecutionResultAggregator` 真正接入最终回复链路

**涉及文件：**

- `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedExecutionResultAggregator.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmExecutionReceiptReviewer.java`

当前风险：

- `ExecutionResultAggregator` 已注入 `DepartmentChatService`，但当前流程中只看到字段保存，没有形成有效聚合调用。
- `result_aggregated` Trace 更多是“收到部门大脑响应”，不是“员工回执已验收并聚合”。

建议改法：

1. 在 `DepartmentChatService.processBrainResponse(...)` 中，在调用 `mainBrainResponseComposer.composeUserResponse(...)` 前先收集执行结果：
   - `executionResult.executionId()`
   - `executionResult.receipts()`
   - `mainBrainTaskPlan.acceptanceCriteria()`
   - `responseText`
2. 调用：
   - `executionResultAggregator.aggregate(executionId, department, mainBrainTaskPlan, receipts, responseText)`
3. 将聚合摘要作为 `brainRawResponse` 或新增字段传给 `MainBrainResponseComposer`。
4. 如果聚合结果显示 `PARTIALLY_COMPLETED`、`FAILED`、`NEEDS_FOLLOW_UP`：
   - `FinalResponseCoordinator` 应优先选择 `WAIT_FOR_RECEIPTS`、`RETRY_FAILED_ASSIGNMENTS`、`ASK_CLARIFICATION` 或 `ESCALATE_TO_HUMAN`。
5. `result_aggregated` Trace 应记录：
   - `overallStatus`
   - `qualityScore`
   - `unmetCriteria`
   - `needsFollowUp`

验收标准：

- 员工回执不满足验收标准时，最终回复不能说“已完成”。
- 多员工回执存在冲突时，聚合器能指出冲突。
- `ExecutionReceiptReviewer` 的审核结果会影响最终状态。

#### 6.4 让 `ExecutionReceiptReviewer` 成为完成态门禁

**涉及文件：**

- `living-agent-core/src/main/java/com/livingagent/core/autonomy/ExecutionReceiptReviewer.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmExecutionReceiptReviewer.java`
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/LlmBasedExecutionResultAggregator.java`

当前风险：

- 审核器虽然存在，但如果只在聚合器内部生成文字评价，而不影响执行状态，仍无法阻止“假完成”。

建议改法：

1. 给 `ReceiptReviewResult` 增加或确保包含：
   - `accepted`
   - `qualityScore`
   - `unmetCriteria`
   - `missingArtifacts`
   - `needsRetry`
   - `reviewComment`
2. 聚合器必须以审核结果计算整体状态：
   - 任一关键验收标准未满足：整体不得为 `COMPLETED`。
   - 所有回执 `accepted=false`：整体为 `FAILED`。
   - 部分通过：整体为 `PARTIALLY_COMPLETED`。
3. 将审核失败的员工任务重新进入：
   - 重试队列。
   - 重新分派。
   - 人工介入。

验收标准：

- 回执只有“已完成”三个字时，审核不能通过。
- 无产物、无步骤、无证据时，质量分应低于通过阈值。
- 审核未通过时，绩效和知识沉淀不能按完成任务记录。

#### 6.5 接入主动建议 LLM 层

**涉及文件：**

- `living-agent-core/src/main/java/com/livingagent/core/proactive/predictor/PatternPredictor.java`
- `living-agent-core/src/main/java/com/livingagent/core/proactive/llm/LlmProactiveAdvisor.java`
- `living-agent-core/src/main/java/com/livingagent/core/proactive/llm/impl/LlmProactiveAdvisorImpl.java`
- `living-agent-core/src/main/java/com/livingagent/core/proactive/suggestion/ProactiveSuggestionService.java`
- `living-agent-gateway/src/main/java/com/livingagent/gateway/config/GatewayConfig.java`

当前风险：

- `LlmProactiveAdvisor` 已存在并可能已注册，但 `ProactiveSuggestionService` 如果仍以空依赖或规则结果为主，就不会产生语义化主动建议。

建议改法：

1. 修改 `ProactiveSuggestionService` 构造函数，显式依赖：
   - `PatternPredictor`
   - `RiskPredictor`
   - `LlmProactiveAdvisor`
   - `LlmRiskAssessor`
2. `PatternPredictor` 只输出行为模式和统计特征。
3. `LlmProactiveAdvisor` 负责生成最终建议：
   - `triggerReason`
   - `expectedBenefit`
   - `riskNote`
   - `recommendedActions`
   - `requiresUserConfirmation`
   - `confidence`
4. 用户忙碌、低收益或无充分依据时，应返回“不打扰”。

验收标准：

- 主动建议不再只是“你经常在 9 点活跃”。
- 建议必须包含业务原因和推荐动作。
- 能根据用户角色、当前任务、项目状态生成不同建议。

#### 6.6 接入 LLM 风险评估层

**涉及文件：**

- `living-agent-core/src/main/java/com/livingagent/core/proactive/predictor/RiskPredictor.java`
- `living-agent-core/src/main/java/com/livingagent/core/proactive/llm/LlmRiskAssessor.java`
- `living-agent-core/src/main/java/com/livingagent/core/proactive/llm/impl/LlmRiskAssessorImpl.java`

当前风险：

- `RiskPredictor` 的固定阈值只能作为基础告警，不能解释业务风险。
- 如果服务层直接输出固定话术，仍会误报或漏报。

建议改法：

1. 将 `RiskPredictor` 输出作为基础事件：
   - `metricName`
   - `value`
   - `threshold`
   - `severity`
   - `trend`
2. 将业务上下文传给 `LlmRiskAssessor`：
   - 环境：生产/测试/压测。
   - 项目阶段：上线前/日常运行/事故处理中。
   - 影响范围：用户数、部门、系统。
   - 历史事故和处置记录。
3. LLM 输出：
   - `riskLevel`
   - `evidence`
   - `impactScope`
   - `rootCauseHypothesis`
   - `recommendedActions`
   - `requiresApproval`
   - `requiresHumanIntervention`

验收标准：

- 同样 CPU 90%，压测环境和生产高峰输出不同风险解释。
- 高风险结果能触发审批或人工介入。
- 风险建议必须包含处置步骤，而不是固定话术。

#### 6.7 建设统一 `DecisionContextBuilder`

**涉及范围：**

- `autonomy`
- `brain`
- `tool`
- `knowledge`
- `security`
- `project`
- `approval`
- `proactive`

当前风险：

- 各个 LLM 类自行拼 Prompt，导致上下文不一致、字段遗漏、脱敏不统一、Trace 不统一。

建议新增：

- `DecisionContextBuilder`
- `EmployeeDecisionContextBuilder`
- `ToolDecisionContextBuilder`
- `KnowledgeDecisionContextBuilder`
- `RiskDecisionContextBuilder`

上下文结构建议：

```json
{
  "request": {"message": "...", "sessionId": "...", "requestId": "..."},
  "user": {"userId": "...", "department": "...", "roles": [], "accessLevel": "..."},
  "brains": [{"department": "tech", "brainId": "TechBrain", "state": "RUNNING"}],
  "employees": [{"code": "T02", "department": "tech", "capabilities": [], "tools": [], "load": 0.3}],
  "tools": [{"name": "office", "schema": {}, "riskLevel": "LOW"}],
  "knowledge": [{"title": "...", "summary": "...", "source": "..."}],
  "project": {"activeTasks": [], "milestones": []},
  "approval": {"required": false, "rules": []},
  "constraints": {"tenantId": "...", "dataSensitivity": "..."}
}
```

验收标准：

- 入口分析、主脑规划、员工分派、最终回复使用同一上下文来源。
- 敏感字段统一脱敏。
- Trace 能记录本次使用了哪些上下文来源。

#### 6.8 建设统一 `LlmDecisionClient` 和 Schema 校验

当前风险：

- 每个 `LlmBased*` 类自行 `extractJson`、`ObjectMapper.readValue`、fallback，重复且不一致。
- 解析失败后直接 fallback，无法区分模型输出质量问题和系统不可用问题。

建议新增 `LlmDecisionClient`，统一负责：

1. Prompt version 管理。
2. JSON Schema 校验。
3. JSON 修复重试。
4. 字段级错误记录。
5. 模型、token、耗时、fallback reason Trace。
6. 安全脱敏日志。

建议调用形态：

```text
llmDecisionClient.decide(
  schema = EmployeeDispatchPlanSchema,
  promptVersion = "employee-dispatch-v2",
  context = decisionContext,
  fallback = registryBackedDispatcher
)
```

验收标准：

- 所有 LLM 决策类不再重复实现 `extractJson`。
- Schema 不通过时先修复重试，失败后才 fallback。
- Trace 能看到 `schemaValidationErrors`。

#### 6.9 增加 `HybridContextCompactor`

**涉及文件：**

- `living-agent-core/src/main/java/com/livingagent/core/brain/compact/ContextCompactor.java`
- `living-agent-core/src/main/java/com/livingagent/core/brain/compact/impl/RuleBasedContextCompactor.java`

当前风险：

- 当前 `RuleBasedContextCompactor` 主要靠长度估算、正则和固定摘要，容易丢失任务目标、决策、失败原因、审批状态、未完成事项。

建议改法：

1. 保留规则层用于：
   - token 粗估。
   - 文件路径提取。
   - 工具输出持久化。
2. 新增 LLM 语义层生成结构化摘要：
   - `goals`
   - `decisions`
   - `constraints`
   - `artifacts`
   - `failures`
   - `openQuestions`
   - `nextSteps`
3. 大输出必须先持久化再摘要，摘要中保留原文路径。

验收标准：

- 长对话压缩后仍能恢复任务目标、关键文件、未完成事项。
- 工具失败原因不会被摘要丢失。
- 压缩结果可被后续 LLM 决策直接消费。

---

## 8. 建议新增/调整的类

结合最新代码结构指南，以下表格区分“已经出现但需完善”和“仍建议新增”。

| 类名 | 类型 | 当前差异 | 职责/完善方向 |
|---|---|---|---|
| `LlmBasedFixedEmployeeDispatcher` | 已出现，需完善 | 最新结构已列出 | 确认默认启用；补充负载、绩效、可用性、替代员工、选择置信度；避免只依赖静态候选列表 |
| `LlmBasedFinalResponseCoordinator` | 已出现，需完善 | 最新结构已列出 | 确认默认启用；策略输入增加审批状态、回执质量、用户角色、风险证据 |
| `LlmBasedMainBrainResponseComposer` | 已出现，需完善 | 最新结构已列出 | 输出结构化回复类型、风险说明、下一步动作，再生成自然语言正文 |
| `LlmProactiveAdvisor` / `LlmProactiveAdvisorImpl` | 已出现，需接入 | 最新结构已列出 | 确认主动建议服务主路径调用它，`PatternPredictor` 只做特征输入 |
| `LlmRiskAssessor` / `LlmRiskAssessorImpl` | 已出现，需接入 | 最新结构已列出 | 确认风险服务主路径调用它，`RiskPredictor` 只做基础告警输入 |
| `LLMEmployeeCreationService` | 已出现，需治理 | 最新结构新增 | 增加创建审批、编号唯一性、能力来源校验、工具权限、安全边界和生命周期管理 |
| `DecisionContextBuilder` | 仍建议新增 | 结构指南未明确 | 聚合用户、部门、会话、权限、工具、知识、项目、审批上下文 |
| `EmployeeDecisionContextBuilder` | 仍建议新增 | 结构指南未明确 | 生成员工画像、负载、绩效、工具权限摘要 |
| `ToolDecisionContextBuilder` | 仍建议新增 | 结构指南未明确 | 将 ToolRegistry/ToolSchema 转成 LLM 可用上下文 |
| `KnowledgeDecisionContextBuilder` | 仍建议新增 | 结构指南未明确 | 检索并摘要企业制度、经验、专业知识 |
| `LlmDecisionClient` | 仍建议新增 | 结构指南未明确 | 统一 LLM JSON 调用、修复重试、解析、Schema 校验、Trace |
| `LlmBasedExecutionResultAggregator` | 仍建议新增 | 结构指南仍以默认聚合器为主 | 聚合回执、判断质量、发现冲突、提出返工建议 |
| `AssignmentReadinessEvaluator` | 仍建议新增 | 结构指南未明确 | 判断任务是否具备派发条件，输出阻塞项和追问项 |
| `ExecutionReceiptReviewer` | 仍建议新增 | 结构指南未明确 | 判断员工回执是否满足验收标准，避免“已完成”但无产物 |
| `HybridContextCompactor` | 仍建议新增 | 结构指南仍是 `RuleBasedContextCompactor` | 程序提取 + LLM 语义压缩，保留目标、决策、未完成事项 |
| `RuleBased*` | 调整 | 已定位为 fallback | 统一记录 fallback reason，不参与正常主路径 |

---

## 9. 统一决策 Trace 建议

每次自主决策都应记录：

```json
{
  "requestId": "...",
  "stage": "dialogue_analysis",
  "decisionSource": "llm_based",
  "model": "...",
  "promptVersion": "dialogue-v2",
  "contextSources": ["user", "department", "tools", "knowledge"],
  "inputTokens": 1234,
  "outputTokens": 456,
  "latencyMs": 820,
  "fallbackReason": null,
  "confidence": 0.84,
  "decisionReason": "用户请求涉及合同付款和预算确认，需要法务主责、财务协作"
}
```

这样才能区分：

- 真正 LLM 自主决策。
- 规则兜底。
- 解析失败后默认值。
- 安全规则拦截。
- 人工介入。

---

## 10. 测试用例建议

| 场景 | 输入 | 期望 |
|---|---|---|
| 隐含跨部门 | “帮我审一下合同付款条款，看看预算有没有问题” | 法务主责、财务协作，风险较高 |
| 信息不足 | “帮我报销一下” | 追问金额、发票、项目，不直接执行 |
| 高风险短指令 | “删除生产库” | 高风险，拒绝或审批/人工介入，不识别为简单问题 |
| 非技术任务 | “制定招聘面试评估表” | HR 主责，不默认技术部 |
| 技术任务 | “实现登录接口并写测试” | 技术主责，拆分开发/测试/评审 |
| 主动服务 | 用户每天 9 点查看项目延期 | 建议查看项目风险，而不是只说活跃时间 |
| 风险预测 | CPU 90%，环境=压测 | 解释为压测负载，不直接紧急告警 |
| 回执验收 | 员工返回“已完成”但无产物 | 不通过验收，要求补充产物或重派 |

---

## 11. 最新代码核对结果（2026-05-12）

基于当前代码再次核对后，文档中的问题**仍未全部完成**。相比上一轮，部分问题已经有明显推进：`DecisionContextBuilder`、`LlmDecisionClient`、`EmployeeTaskExecutionOutcome`、`AssignmentReadinessEvaluator` 等关键类已经出现，`DynamicEmployeeTaskConsumerRegistry` 也已避免“模型全部失败仍固定 COMPLETED”的老问题。但仍存在“能力已存在但未接入所有主流程”“注册了 Bean 但业务服务未使用”“实现了类但配置仍用旧实现”等问题。

### 11.1 当前完成度总览

| 问题域 | 当前代码状态 | 完成度 | 仍需处理 |
|---|---|---:|---|
| LLM 入口分析 | `GatewayConfig` 默认注入 `LlmBasedDialogueAnalyzer` | 80% | 需要接入统一 `DecisionContextBuilder` / `LlmDecisionClient`，减少类内自拼 Prompt |
| LLM 主脑规划 | 默认注入 `LlmBasedMainBrainTaskDirector` | 80% | 同上，仍需统一上下文和 Schema 治理 |
| LLM 员工分派 | 默认注入 `LlmBasedFixedEmployeeDispatcher` | 80% | 员工负载、绩效、可用性仍需真实数据接入 |
| 派发准备度 | `AssignmentReadinessEvaluator` 已注入并在 `DepartmentChatService.prepareAssignmentBatch(...)` 调用 | 75% | `NEEDS_CLARIFICATION` 后的用户追问/状态恢复仍需闭环 |
| 降级执行状态 | 已新增 `EmployeeTaskExecutionOutcome`，模型全失败返回 `DEGRADED`，回执不再固定 `COMPLETED` | 85% | `DEGRADED` 后的重试队列/人工介入仍需落地 |
| 动态员工消费者 | `DynamicEmployeeTaskConsumerRegistry` 已注册真实消费者并调用模型池 | 75% | 仍是直接 LLM 执行摘要，未真正进入完整 `EmployeeNeuron` + ToolExecutor 执行链 |
| 执行结果聚合 | `LlmBasedExecutionResultAggregator` 已注册，`DepartmentChatService.processBrainResponse(...)` 已调用 `executionResultAggregator.aggregate(...)` | 65% | 已进入最终回复前置链路，但当前聚合返回仍是字符串摘要，缺少结构化质量分、未满足验收项和冲突列表的强类型传递 |
| 回执验收 | `LlmExecutionReceiptReviewer` 已存在并被聚合器依赖，`DepartmentChatService` 已增加 `completionGate` 门禁 | 60% | 当前门禁仍有本地启发式判断，尚未直接消费 reviewer 的结构化审核明细；重试/人工介入队列未落地 |
| 最终回复策略 | 默认注入 `LlmBasedFinalResponseCoordinator`，并已接收聚合后的 `DepartmentExecutionResult` | 80% | 策略输入已包含聚合状态和门禁 metadata，但仍需支持结构化 `unmetCriteria`、`needsRetry`、`humanInterventionReason` |
| 主脑回复编排 | 默认注入 `LlmBasedMainBrainResponseComposer`，已接收执行聚合摘要 | 80% | 已能基于聚合摘要生成最终回复，但聚合摘要仍非强类型，无法稳定表达验收缺口和返工建议 |
| 主动建议 LLM 化 | `GatewayConfig` 已用 `PatternPredictor`、`RiskPredictor`、`LlmProactiveAdvisor`、`LlmRiskAssessor` 构造 `ProactiveSuggestionService`；服务内已优先调用 LLM Advisor | 65% | LLM 建议已接入主路径，但仍缺用户忙碌状态、项目/任务上下文、打扰策略和“不打扰”强语义输出 |
| 风险评估 LLM 化 | `ProactiveSuggestionService` 已在高风险告警建议中调用 `LlmRiskAssessor`，失败时回退规则话术 | 60% | 风险解释已接入主动建议链路，但独立风险服务仍需全面改为规则告警 + LLM 解释；业务环境、影响范围、历史事故上下文仍不足 |
| 决策上下文 | `DecisionContextBuilder` / `DefaultDecisionContextBuilder` 已出现并注册 | 45% | 主要 `LlmBased*` 类仍未统一使用它 |
| LLM 决策客户端 | `LlmDecisionClient` / `DefaultLlmDecisionClient` 已出现并注册 | 45% | 主要 `LlmBased*` 类仍保留各自 `extractJson` / `ObjectMapper.readValue` |
| 上下文压缩 | `HybridContextCompactor` 已出现 | 25% | `LivingAgentCoreConfig.contextCompactor()` 仍返回 `RuleBasedContextCompactor` |

### 11.2 已经完成或基本完成的问题

1. **LLM-first 主链路已基本建立**
   - `DialogueAnalyzer` 默认是 `LlmBasedDialogueAnalyzer`。
   - `MainBrainTaskDirector` 默认是 `LlmBasedMainBrainTaskDirector`。
   - `FixedEmployeeDispatcher` 默认是 `LlmBasedFixedEmployeeDispatcher`。
   - `MainBrainResponseComposer` 默认是 `LlmBasedMainBrainResponseComposer`。
   - `FinalResponseCoordinator` 默认是 `LlmBasedFinalResponseCoordinator`。

2. **模拟执行器主路径基本移除**
   - `MinimalEmployeeTaskExecutor` 文件仍存在，但当前没有看到它被注册为生产 Bean。
   - 当前主路径使用 `DynamicEmployeeTaskConsumerRegistry` 注册员工任务消费者。

3. **降级完成态问题已有修正**
   - 当前 `DynamicEmployeeTaskConsumerRegistry` 调用模型失败后会返回 `EmployeeTaskExecutionOutcome.degraded(...)`。
   - `EmployeeTaskExecutionOutcome.toReceipt(...)` 会将 `DEGRADED` 映射为回执状态 `DEGRADED`，不再固定 `COMPLETED`。

4. **派发准备度已开始接入主流程**
   - `DepartmentChatService` 已注入 `AssignmentReadinessEvaluator`。
   - `prepareAssignmentBatch(...)` 中已调用 `assignmentReadinessEvaluator.evaluate(...)`。
   - `BLOCKED` / `NEEDS_CLARIFICATION` 会被写入 `PreparedAssignmentBatch.metadata()`，并在 `coordinateDepartmentExecution(...)` 中跳过执行派发。

5. **上下文和 LLM 决策治理类已出现**
   - 已新增并注册 `DecisionContextBuilder` / `DefaultDecisionContextBuilder`。
   - 已新增并注册 `LlmDecisionClient` / `DefaultLlmDecisionClient`。
   - `DefaultLlmDecisionClient` 已具备 Prompt context 拼接、JSON 提取、Schema 校验、修复重试、fallback 结果包装等基础能力。

### 11.3 仍未完成的关键问题

#### P0：执行结果聚合已经接入最终回复链路，但仍缺结构化聚合结果

当前 `DepartmentChatService.processBrainResponse(...)` 已经完成以下接入：

```text
collectExecutionReceipts(executionResult)
  -> executionResultAggregator.aggregate(...)
  -> enrichExecutionResultWithAggregation(...)
  -> mainBrainResponseComposer.composeUserResponse(..., aggregatedResponse, responseExecutionResult)
  -> finalResponseCoordinator.determineStrategy(..., responseExecutionResult)
```

这说明原先“聚合器只注册但未调用”的问题已经缓解，`result_aggregated` Trace 也已从“收到部门大脑响应”调整为“员工回执审核和聚合后再生成最终回复”。同时，`DepartmentChatService` 已在 `responseExecutionResult.metadata()` 中写入：

- `receiptCount`
- `acceptedReceiptCount`
- `failedReceiptCount`
- `allDispatchesReported`
- `acceptedCompletion`
- `completionGate`
- `aggregationSource`

但当前仍不足：

1. `ExecutionResultAggregator.aggregate(...)` 接口仍返回 `String`，聚合质量、未满足验收项、冲突、重试建议没有强类型返回。
2. `ExecutionResultAggregator.AggregationResult` record 虽然存在，但主链路未使用它。
3. `FinalResponseCoordinator` 和 `MainBrainResponseComposer` 只能通过字符串摘要和 metadata 推断状态，不能稳定消费 `qualityScore`、`unmetCriteria`、`needsRetry`、`conflicts`。
4. 聚合失败时仍会 fallback 到部门大脑原始响应，需要更明确的 `fallbackReason` 和“不宣称完成”策略。

需要继续补齐：

1. 将 `ExecutionResultAggregator.aggregate(...)` 升级为返回 `AggregationResult` 或新增 `aggregateStructured(...)`。
2. `AggregationResult` 增加 `qualityScore`、`accepted`、`unmetCriteria`、`conflicts`、`retryAssignments`、`needsHumanIntervention`、`summaryForUser`。
3. `DepartmentChatService` 使用结构化聚合结果计算 `DepartmentExecutionResult.status()`，而不是依赖字符串和本地启发式。
4. `FinalResponseCoordinator` 直接消费结构化聚合结果，遇到 `FAILED` / `PARTIALLY_COMPLETED` / `DEGRADED` / `NEEDS_RETRY` 时禁止完成话术。

#### P0：回执验收已成为初步完成态门禁，但审核明细仍未贯穿

当前代码已经不再无条件记录知识、产物和绩效。`DepartmentChatService` 通过 `isAcceptedCompletion(responseExecutionResult)` 控制：

- 只有 `completionGate=PASSED` 才调用 `captureArtifacts(...)`。
- 只有 `completionGate=PASSED` 才调用 `captureKnowledge(...)`。
- 只有 `completionGate=PASSED` 才调用 `capturePerformance(...)`。
- 未通过时记录 `completion_gate_blocked` Trace。

这已经解决了“只要有回执就按完成沉淀”的一部分问题。

但当前门禁仍不足：

1. `isAcceptedReceipt(...)` 主要基于 `status == COMPLETED` 和摘要长度判断，仍是启发式，不等同于 `LlmExecutionReceiptReviewer` 的结构化审核结论。
2. 无产物、无工具证据、缺验收标准、摘要空泛等问题没有被强类型表达为 `missingArtifacts` / `unmetCriteria`。
3. 审核失败后只阻止沉淀，尚未进入重试、重新分派或人工介入队列。
4. `ExecutionReceiptReviewer` 的审核结果还没有作为独立审计对象写入 Trace 或持久化。

需要继续补齐：

1. 扩展 `ExecutionReceiptReviewer.ReceiptReviewResult`，确保包含 `accepted`、`qualityScore`、`unmetCriteria`、`missingArtifacts`、`needsRetry`、`reviewComment`。
2. 聚合器必须以 reviewer 的审核结果，而不是摘要长度，计算 `completionGate`。
3. 审核失败时写入 `receipt_review_failed` Trace，并记录具体失败原因。
4. 审核失败后进入重试队列、重新分派或人工介入，而不只是跳过知识/绩效沉淀。

#### P0：`DEGRADED` 后续处理仍未闭环

当前已经避免了 `DEGRADED` 被误标记为 `COMPLETED`，但后续处理仍不足。

需要补齐：

1. `DEGRADED` 回执进入重试队列。
2. 达到重试上限后触发人工介入。
3. 最终回复明确告知用户“尚未真实完成”。
4. Trace 记录 `needs_retry`、`failure_reason`、`retry_count`。

#### P1：`DecisionContextBuilder` 和 `LlmDecisionClient` 尚未被核心 LLM 类统一使用

当前它们已注册为 Bean，但主要 `LlmBasedDialogueAnalyzer`、`LlmBasedMainBrainTaskDirector`、`LlmBasedFixedEmployeeDispatcher`、`LlmBasedFinalResponseCoordinator` 等仍保留各自 Prompt 拼接、`extractJson`、`ObjectMapper.readValue` 和 fallback 逻辑。

需要补齐：

1. 将入口分析、主脑规划、员工分派、最终策略、回复编排迁移到 `LlmDecisionClient.decide(...)`。
2. 所有 Prompt 通过 `DecisionContextBuilder` 生成统一上下文。
3. Trace 统一记录 `promptVersion`、`contextSources`、`schemaValidationErrors`、`fallbackReason`。

#### P1：主动建议和风险评估已初步接入 LLM 层，但上下文和“不打扰”策略仍不足

当前代码已经完成以下推进：

1. `GatewayConfig.proactiveSuggestionService(...)` 不再使用 `new ProactiveSuggestionService(null, null, List.of())`，而是注入：
   - `PatternPredictor`
   - `RiskPredictor`
   - `LlmProactiveAdvisor`
   - `LlmRiskAssessor`
   - `AlertNotifier` 列表
2. `ProactiveSuggestionService.generateSuggestions(...)` 会先构造上下文并调用 `LlmProactiveAdvisor.generateSuggestions(...)`。
3. LLM 建议为空或失败时，才回退到时间规则、行为模式和风险规则建议。
4. 风险建议中，`generateRiskBasedSuggestions(...)` 会对高风险 alert 调用 `LlmRiskAssessor.assessRisk(...)`，并使用 LLM 输出的 `evidence`、`impactScope`、`recommendedAction`、`requiresApproval`、`requiresHumanIntervention` 组织建议。
5. LLM 风险评估失败时，才回退到 `RiskPredictor` 原始固定话术，并在 metadata 中标记 `source=rule_based_fallback`。

这说明“主动建议/风险评估完全未接入 LLM 层”的问题已缓解。

但当前仍不足：

1. `buildSuggestionContext(...)` 目前主要包含行为预测、行为洞察和风险告警，缺少用户忙碌状态、日程、当前任务、项目里程碑、用户角色、部门上下文。
2. LLM Advisor 虽然可返回空建议，但服务层没有强类型表达 `DO_NOT_DISTURB` / `NO_ACTION`，无法审计“不打扰”的原因。
3. 时间规则建议仍会在 LLM 无结果时直接出现，例如固定早晨、周五、下午建议；这仍是 fallback，但需要更明确的 fallback trace。
4. 风险 LLM 解释目前只接入 `ProactiveSuggestionService` 的建议链路，独立风险 API/服务如果直接使用 `RiskPredictor`，仍可能输出固定阈值话术。
5. `LlmRiskAssessor` 的上下文仍偏薄，缺少生产/测试/压测环境、影响用户数、业务窗口、历史事故和处置记录。

需要继续补齐：

1. 将用户日程、任务、项目、角色、部门、忙碌状态纳入 `buildSuggestionContext(...)`。
2. 为主动建议增加结构化决策类型：`SUGGEST_ACTION`、`REMIND`、`WARN`、`DO_NOT_DISTURB`、`NEED_MORE_CONTEXT`。
3. LLM 返回“不打扰”时记录原因和证据，而不是简单返回空列表。
4. 所有风险输出入口都统一走“`RiskPredictor` 基础告警 + `LlmRiskAssessor` 业务解释”的两层链路。
5. 为规则 fallback 记录 `fallbackReason`、`contextSources` 和 `decisionSource=rule_based_fallback`。

#### P1：`HybridContextCompactor` 已存在但未启用

当前 `LivingAgentCoreConfig.contextCompactor()` 仍返回 `RuleBasedContextCompactor`。虽然 `HybridContextCompactor` 已出现，但没有成为默认或可配置实现。

需要补齐：

1. 增加配置项，例如 `living-agent.compact.mode=rule|hybrid`。
2. `hybrid` 模式下返回 `HybridContextCompactor`。
3. 让部门大脑在长上下文场景使用 LLM 语义摘要。

#### P1：动态员工消费者仍未完整进入工具执行闭环

`DynamicEmployeeTaskConsumerRegistry` 当前主要是根据员工定义和模型池直接调用 LLM，尚未体现完整 `EmployeeNeuron` + `ToolExecutor` + `ToolHookManager` + `Approval` 的执行链。

需要补齐：

1. 员工任务应转交 `EmployeeNeuron` 或统一执行网关。
2. 工具调用必须经过 `ToolExecutor` / `ToolHookManager`。
3. 高风险工具调用必须进入审批。
4. 回执必须包含工具调用证据和产物记录。

### 11.4 更新后的结论

当前项目已经具备 LLM 自主决策系统的模块基础：主脑、部门大脑、模型池、员工、神经元、工具、知识库、任务、审批和主动服务接口都已存在。结合最新代码核对，项目已经从“缺少 LLM 实现”推进到了“LLM 实现大多存在并部分接入主链路”的阶段：入口分析、主脑规划、员工分派、最终回复策略、主脑回复编排等环节已经具备 LLM-first 实现，并且规则实现多已退到 fallback 位置。

但这并不代表本文列出的问题已经全部完成。现在剩下的核心问题已经从“有没有 LLM 类”转变为：

1. LLM 决策上下文不统一，缺少统一的 `DecisionContextBuilder` / `ToolDecisionContextBuilder` / `KnowledgeDecisionContextBuilder` 等上下文组装层。
2. Schema 治理不足，缺少统一的 `LlmDecisionClient`、JSON Schema 校验、修复重试、字段级错误 Trace。
3. 回执验收和执行聚合已经开始进入主流程：`DepartmentChatService` 已调用聚合器、生成聚合摘要、更新执行状态，并用 `completionGate` 阻止未验收通过的任务进行知识/绩效/产物沉淀；但聚合结果仍非强类型，reviewer 的结构化审核明细尚未贯穿。
4. 主动建议和风险评估已经开始接入 LLM 层：`ProactiveSuggestionService` 已优先调用 `LlmProactiveAdvisor`，高风险告警已调用 `LlmRiskAssessor`；但上下文仍偏薄，“不打扰”策略、独立风险服务统一解释链路仍需补齐。
5. 员工执行虽然已从简单模拟执行器推进到动态消费者/模型池调用，但 `DEGRADED` 后的重试、重新分派、人工介入仍未形成闭环。
6. 上下文压缩仍以规则实现为主，长任务中的目标、约束、决策、风险、未完成事项仍有丢失风险。
7. `DecisionContextBuilder` 和 `LlmDecisionClient` 虽已出现并注册，但核心 LLM 类仍大量保留自拼 Prompt、手动 JSON 提取和各自 fallback，统一 Schema 治理尚未完成。

因此，后续工作的重点不再是简单新增 `LlmBased*` 类，而是让这些 LLM 能力真正成为可审计、可校验、可验收、可闭环的生产主路径。

最关键的改造方向是：

1. 把 `RuleBased*` 从主路径降级为 fallback。
2. 让 MainBrain/DepartmentBrain 成为任务规划、路由、分派、回复的真实决策者。
3. 将员工画像、工具、知识、API、任务状态注入 LLM 上下文。
4. 用结构化 JSON Schema 约束 LLM 输出，并记录决策 Trace。
5. 员工执行从模拟回执升级为真实 `EmployeeNeuron` 执行闭环。
6. 安全、权限、审批、审计继续由硬规则兜底。

这样才能让项目从“硬编码规则驱动的自动化系统”升级为“LLM 自主决策驱动的数字员工系统”。
