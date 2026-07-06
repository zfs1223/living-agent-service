# 大脑与固定数字员工做事规范索引

> 目的：建立每个部门大脑、主脑、固定数字员工与其职责边界、Prompt、runbook、路由配置、执行器、回执规范之间的映射，避免大脑和员工自由发挥、越权执行、输出不一致或无法审计。
>
> 配合文档：
>
> - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`：解决“文件在哪里”。
> - `docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md`：解决“为什么要这样改、整体优化路线是什么”。
> - 本文档：解决“每个大脑/员工应该按什么规范做事、规范从哪里加载、执行证据落在哪里”。
>
> 更新时间：2026-05-19

---

## 1. 总原则

### 1.1 大脑和员工都必须受规范约束

系统中的大脑和固定数字员工不能只依赖模型自由发挥，必须遵守统一规范链：

```text
角色职责
-> 权限边界
-> Prompt 约束
-> runbook / 操作手册
-> 任务单
-> 执行器
-> 回执
-> 验收
-> 产物
-> Trace / RuntimeEvent
-> 知识与绩效沉淀
```

### 1.2 WebSocket / 对话 / 任务 / 执行必须分层

```text
WebSocketConnection：短生命周期传输连接
DepartmentConversation：长期可恢复部门对话
WorkItem / Task：可跨多轮对话推进的工作项
Execution：一次具体执行实例
EmployeeReceipt：固定员工执行回执
Artifact：可追踪产物
```

大脑和员工不能把 WebSocket session 当成业务会话，也不能把一次模型回复当成任务生命周期。

### 1.3 所有任务型输出必须可结构化

大脑和员工最终输出至少应能映射到以下字段：

```text
status
summary
currentStep
blockingIssues
clarificationQuestions
assignedWorkers
artifacts
nextSteps
traceId
conversationId
taskKey
executionId
riskLevel
requiresHumanReview
```

---

## 2. 大脑规范索引

### 2.1 大脑公共规范入口

| 类型 | 文件 | 作用 |
| --- | --- | --- |
| 大脑接口 | `living-agent-core/src/main/java/com/livingagent/core/brain/Brain.java` | 定义统一大脑处理入口 |
| 大脑上下文 | `living-agent-core/src/main/java/com/livingagent/core/brain/BrainContext.java` | 承载用户、部门、会话、权限、metadata |
| 大脑公共基类 | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/AbstractBrain.java` | 所有部门脑公共行为、工具调用、模型调用和兜底逻辑入口 |
| 大脑注册表 | `living-agent-core/src/main/java/com/livingagent/core/brain/BrainRegistry.java` | 大脑查找、路由和注册接口 |
| 大脑注册实现 | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/BrainRegistryImpl.java` | MainBrain、TechBrain、HrBrain 等注册映射 |
| 大脑输出契约 | `living-agent-core/src/main/java/com/livingagent/core/brain/BrainOutputContract.java` | 统一大脑输出结构：status/summary/plan/clarificationQuestions/blockingIssues/riskLevel/conversationId/taskKey/executionId |
| 大脑边界执行器 | `living-agent-core/src/main/java/com/livingagent/core/brain/BrainBoundaryEnforcer.java` | 大脑职责边界硬判断：allowedActions/forbiddenActions/escalationTriggers/mustEscalateScenarios |
| 越权拦截执行器 | `living-agent-core/src/main/java/com/livingagent/core/security/ExecutionBoundaryEnforcer.java` | 员工越权拦截：跨部门/超管辖/高风险任务硬判断 |
| 动态 Prompt | `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/DynamicPromptBuilder.java` | 拼装角色、人格、知识、技能、工具、guidelines |
| 指令文件加载 | `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/InstructionFileLoader.java` | 加载 `.living/{employeeId}/instructions.md` 指令链 |
| 协作分工 | `living-agent-core/src/main/java/com/livingagent/core/brain/collaboration/*` | 部门脑对团队成员进行角色化分工 |
| 上下文压缩 | `living-agent-core/src/main/java/com/livingagent/core/brain/compact/*` | 长对话与长任务上下文压缩 |
| 部门映射 | `living-agent-core/src/main/java/com/livingagent/core/security/Department.java` | department code 与 brain name 的映射 |

### 2.2 大脑职责边界索引

| Brain | 部门/层级 | 主要职责 | 不应越权做的事 | 必须上报/澄清场景 | 进化权限 | 代码文件 |
| --- | --- | --- | --- | --- | --- | --- |
| `MainBrain` | 主脑/总控 | 跨部门协调、任务识别、战略判断、最终收口 | 不直接替员工执行具体工具任务；不可绕过熔断器强制应用代码修复 | 跨部门冲突、高风险、审批、资源冲突；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java` |
| `TechBrain` | 技术部 | 技术方案、代码开发、系统架构、研发执行协调 | 不处理财务、人事、法务最终决策；不可在熔断状态下继续自修复 | 需求不清、上线风险、安全风险、跨部门依赖；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/TechBrain.java` |
| `HrBrain` | 人力资源部 | 招聘、绩效、组织、人事流程 | 不做财务支付和法务裁定；不可绕过修复循环检测 | 涉及员工隐私、奖惩、组织调整；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/HrBrain.java` |
| `FinanceBrain` | 财务部 | 报销、预算、发票、成本、财务审核 | 不做业务战略和法律结论；不可绕过修复循环检测 | 大额支出、预算不足、合规风险；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/FinanceBrain.java` |
| `SalesBrain` | 销售部 | 客户、线索、报价、销售推进 | 不承诺无法交付的技术/法务条款；不可绕过修复循环检测 | 大客户承诺、价格异常、合同风险；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/SalesBrain.java` |
| `CsBrain` | 客服部 | 客诉、工单、FAQ、用户支持 | 不擅自承诺赔偿或技术改造；不可绕过修复循环检测 | 高危客诉、赔付、法务风险；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/CsBrain.java` |
| `AdminBrain` | 行政部 | 办公行政、资产、后勤、行政流程 | 不做人事奖惩和财务最终审批；不可绕过修复循环检测 | 资产处置、采购、权限申请；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/AdminBrain.java` |
| `LegalBrain` | 法务部 | 合同、合规、风险、法律条款 | 不替业务部门做商业承诺；不可绕过修复循环检测 | 合同重大风险、监管风险、争议处理；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/LegalBrain.java` |
| `OpsBrain` | 运营部 | 数据运营、流程运营、活动运营 | 不直接替销售承诺客户结果；不可绕过修复循环检测 | 活动风险、数据异常、跨部门流程变化；修复循环≥3次；连续失败≥5次 | codebase_full_access + evolution_full_access + apply_code_fix + propose_code_fix | `living-agent-core/src/main/java/com/livingagent/core/brain/impl/OpsBrain.java` |

### 2.3 大脑执行必须遵守的流程

```text
接收用户输入
-> 加载 conversationId / userId / tenantId / departmentCode
-> 构造 BrainContext / DecisionContext
-> 判断权限与职责边界
-> 判断是否需要澄清
-> 如需澄清：保存问题、返回用户、状态 WAITING_USER
-> 如可执行：生成计划、分派员工、准备任务单
-> 监听员工回执
-> 执行验收
-> 主脑/部门脑最终收口
-> 保存消息、Trace、RuntimeEvent、Artifact、知识/绩效
```

### 2.4 大脑输出契约

大脑输出至少应包含：

```text
status: READY | NEEDS_CLARIFICATION | EXECUTING | COMPLETED | BLOCKED | FAILED
summary: 对当前判断或结果的摘要
plan: 任务计划或处理方案
clarificationQuestions: 需要用户补充的问题
blockingIssues: 阻塞项
assignedWorkers: 分派的员工
riskLevel: LOW | MEDIUM | HIGH | CRITICAL
nextSteps: 下一步
conversationId: 长期对话 ID
taskKey: 工作项 key
executionId: 执行 ID
traceId: Trace ID
```

---

## 3. 固定数字员工规范索引

### 3.1 固定员工公共规范入口

| 类型 | 文件 | 作用 |
| --- | --- | --- |
| 固定员工注册表 | `living-agent-core/src/main/java/com/livingagent/core/employee/registry/FixedEmployeeRegistry.java` | 固定员工定义、部门映射、能力注册 |
| 固定员工定义实体 | `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeeDefinitionEntity.java` | 数据库固定员工定义 |
| 固定员工画像实体 | `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeePersonaEntity.java` | 固定员工人格、风格、能力画像 |
| 固定员工档案实体 | `living-agent-core/src/main/java/com/livingagent/core/database/entity/FixedEmployeeProfileEntity.java` | 固定员工档案 |
| 系统提示词 | `documents/shared/company/fixed-employee-system-prompts.md` | 固定员工系统级行为约束 |
| Agent Prompt | `documents/shared/company/fixed-employee-agent-prompt.md` | 固定员工 Agent 执行提示模板 |
| 自主执行手册 | `documents/shared/company/fixed-employee-autonomous-runbook.md` | 固定员工任务执行步骤 |
| 文档工作流 | `documents/shared/company/fixed-employee-document-workflow.md` | 文档产物处理、归档、交付流程 |
| 职责卡模板 | `documents/shared/company/fixed-employee-duty-card-template.md` | 固定员工职责卡模板 |
| 路由配置 | `documents/shared/company/fixed-employee-routing-config.yaml` | 固定员工路由、部门、能力配置 |
| 员工任务单 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeWorkAssignment.java` | 员工任务目标、角色、指令、产物和工具上下文 |
| 员工执行器 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeTaskExecutor.java` | 员工任务执行接口 |
| 工具执行器 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` | 按任务类型调用工具/沙箱/文件系统 |
| 员工回执服务 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeExecutionReceiptService.java` | 注册执行、记录回执、监听进度 |
| 员工输出契约 | `living-agent-core/src/main/java/com/livingagent/core/autonomy/EmployeeOutputContract.java` | 统一员工输出结构：employeeCode/status/summary/completedItems/failedItems/artifacts/blockingIssues/riskLevel/retryable |
| 规范强制加载链 | `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/StandardLoadingChainService.java` | 职责卡→Prompt→runbook→文档工作流→自定义指令强制加载 |
| 规范合规追踪 | `living-agent-core/src/main/java/com/livingagent/core/runtime/StandardComplianceTraceService.java` | 边界检查/标准加载/澄清/升级/回执合规/权限检查追踪 |
| 进化空间命名空间 | `core/runtime/EvolutionNamespaceService.java` | .living/ 进化空间路径管理 |
| 架构知识播种 | `core/knowledge/professional/ArchitectureKnowledgeSeeder.java` | docs/documents → 知识库 |
| 源码索引 | `core/knowledge/professional/SourceTreeIndexer.java` | 生成 source-tree.json |
| 统一升级通知 | `core/evolution/escalation/EscalationNotificationService.java` | 所有升级的唯一出口 |
| 错误代码映射 | `core/evolution/codemapper/ErrorCodeMapper.java` | 异常→代码文件→文档 |
| 代码库访问 | `core/evolution/codebase/CodebaseAccessService.java` | 大脑自由读写代码库 |
| 补丁提案 | `core/evolution/patch/PatchProposalService.java` | 创建/保存/查询补丁 |
| 补丁应用 | `core/evolution/patch/PatchApplicationService.java` | 大脑自主决定应用/回滚 |

### 3.2 部门固定员工职责卡索引

| 部门 | 职责卡 | 用途 |
| --- | --- | --- |
| HR / 人力资源 | `documents/shared/company/hr-20-hr-fixed-employee-duty-card.md` | 人力资源固定员工职责 |
| Finance / 财务 | `documents/shared/company/hr-21-finance-fixed-employee-duty-card.md` | 财务固定员工职责 |
| Tech / 技术 | `documents/shared/company/hr-22-tech-fixed-employee-duty-card.md` | 技术固定员工职责 |
| Sales / 销售 | `documents/shared/company/hr-23-sales-fixed-employee-duty-card.md` | 销售固定员工职责 |
| Ops / 运营/行政 | `documents/shared/company/hr-24-ops-fixed-employee-duty-card.md` | 运营/行政固定员工职责 |
| CS / 客服 | `documents/shared/company/hr-25-cs-fixed-employee-duty-card.md` | 客服固定员工职责 |
| Legal / 法务 | `documents/shared/company/hr-26-legal-fixed-employee-duty-card.md` | 法务固定员工职责 |

> 注意：如果 `FixedEmployeeRegistry` 中存在 32 个固定员工，则职责卡需要进一步拆分或补充到员工编号级别，避免只有部门级职责而缺少员工级职责。

### 3.3 固定员工执行必须遵守的流程

```text
接收 EmployeeWorkAssignment
-> 校验 employeeCode / department / taskKey / executionId
-> 加载职责卡、system prompt、agent prompt、runbook、文档工作流
-> 判断任务是否在本人职责范围内
-> 判断是否需要澄清或上报
-> 按任务类型选择工具/沙箱/文件系统/人工审核
-> 执行任务
-> 生成 artifact
-> 生成 EmployeeExecutionReceipt
-> 接受 ExecutionReceiptReviewer 验收
-> 失败时返回 failedReason / retryable / suggestedNextStep
```

### 3.4 固定员工输出契约

固定员工回执至少应包含：

```text
employeeCode
employeeName
departmentCode
assignmentId
taskKey
executionId
status
summary
completedItems
failedItems
artifacts
blockingIssues
clarificationQuestions
riskLevel
requiresHumanReview
retryable
suggestedNextStep
startedAt
completedAt
```

---

## 4. 规范强制加载链

### 4.1 推荐加载链

```text
职责卡 / 大脑职责定义
-> fixed-employee-system-prompts.md / brain guidelines
-> fixed-employee-agent-prompt.md
-> fixed-employee-autonomous-runbook.md
-> fixed-employee-document-workflow.md
-> 部门制度 / 治理规则 / documents/shared/governance
-> DynamicPromptBuilder
-> BrainContext / DecisionContext
-> DepartmentChatService / ConversationOrchestrator
-> EmployeeWorkAssignment
-> EmployeeTaskExecutor
-> EmployeeExecutionReceipt
-> ExecutionReceiptReviewer
-> ArtifactRecord / RuntimeEventStore / KnowledgeCapture / PerformanceCapture
```

### 4.2 强制要求

1. **职责卡必须优先于模型自由发挥**。
2. **Prompt 只负责注入规则，不允许临时发明规则覆盖职责卡**。
3. **runbook 必须进入任务单或执行上下文**。
4. **澄清和越权必须在编排层硬判断，不能完全依赖 LLM**。
5. **回执必须能证明是否按规范执行**。
6. **Trace 必须记录关键规范判断：澄清、越权、权限、验收、人工介入**。

---

## 5. 澄清、越权和失败处理规则

### 5.1 澄清规则

以下情况必须澄清：

- 用户目标不清楚。
- 验收标准不明确。
- 缺少必要输入。
- 涉及高风险财务、法务、人事、安全、权限操作。
- 模型无法判断用户真实意图。
- 固定员工发现任务超出自身职责。

澄清时必须：

```text
保存 clarificationQuestions
更新 DepartmentConversation.status = WAITING_USER
绑定 taskKey / executionId（如果已创建）
通过 WebSocket/API 返回澄清问题
禁止继续等待 output channel 导致超时
```

### 5.2 越权规则

必须禁止：

- 固定员工替主脑做跨部门战略决策。
- 固定员工替部门脑做最终验收。
- 部门脑擅自接管其他部门主责工作。
- 低权限用户销毁会话、任务、artifact 或治理文档。
- 大脑/员工绕过 `WorkItemPermissionService` 操作项目、任务、会话。
- 员工擅自改写职责卡、系统提示词、runbook 或治理规则。
- 固定员工不可直接访问代码库（codebase_access 禁止）。
- 固定员工不可写入进化空间（evolution_write 禁止）。
- 修复循环（>=3次）和连续失败（>=5次）必须强制升级。

### 5.3 失败处理规则

失败不能静默，也不能伪装成功。必须输出：

```text
failedReason
failedStage
retryable
suggestedNextStep
requiresHumanReview
```

---

## 6. 规范执行证据

每次任务执行完成后，应能从以下位置看到规范执行证据：

| 证据类型 | 位置/服务 | 应记录内容 |
| --- | --- | --- |
| Trace | `AutonomyTraceService` | 意图识别、路由、分派、澄清、越权、执行、验收 |
| RuntimeEvent | `RuntimeEventStore` | conversation/task/execution 事件流 |
| 回执 | `EmployeeExecutionReceiptService` | 员工执行结果、失败原因、产物、风险 |
| Artifact | `ArtifactRecordService` | 产物路径、类型、员工、executionId、taskId/projectId |
| 知识沉淀 | `KnowledgeCaptureService` | 可复用经验、问题、解决方案 |
| 绩效沉淀 | `PerformanceCaptureService` | 员工贡献、执行质量、奖励 |
| 对话历史 | `DepartmentChatService` | 用户需求、澄清、最终回复、conversationId |

---

## 7. 待补齐清单

| 优先级 | 待补齐项 | 目标 | 状态 |
| --- | --- | --- | --- |
| P0 | 统一大脑/员工输出契约 | 让前端、Trace、回执、任务状态稳定消费 | ✅ 已完成 |
| P0 | 固化 `NEEDS_CLARIFICATION` 处理 | 澄清直接返回用户并持久化，不再超时 | ✅ 已完成 |
| P0 | 固化越权拦截 | 员工/部门脑不能绕过主脑和权限系统 | ✅ 已完成 |
| P0 | 建立职责卡 → Prompt → runbook 强制加载链 | 确保每个固定员工执行前都带着规范 | ✅ 已完成 |
| P1 | 员工编号级职责卡补齐 | 32 个固定员工不只依赖部门级职责 | ✅ 已完成 |
| P1 | 大脑职责边界细化 | 每个 brain 有明确边界、禁区和升级条件 | ✅ 已完成 |
| P1 | Trace/Receipt 记录规范执行结果 | 可审计是否按规范执行 | ✅ 已完成 |
| P2 | 定期检查职责卡与代码一致性 | 防止文档与代码漂移 | 待实施 |

---

## 8. 审查清单

新增或修改大脑/固定员工时，必须检查：

- [ ] 是否有明确职责边界？
- [ ] 是否有不能做的事？
- [ ] 是否有必须澄清的条件？
- [ ] 是否有必须上报主脑/人工的条件？
- [ ] 是否绑定了 Prompt / runbook / 职责卡？
- [ ] 是否有统一输出结构？
- [ ] 是否写入 Trace / RuntimeEvent？
- [ ] 是否生成回执？
- [ ] 是否经过验收？
- [ ] 是否能追踪 artifact？
- [ ] 是否符合权限规则？
- [ ] 是否能在长期 conversation 中恢复上下文？

---

## 9. 与其他文档的关系

| 文档 | 关系 |
| --- | --- |
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 文件位置和代码结构索引 |
| `docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 模型职责、执行优化和治理路线图 |
| `documents/shared/company/fixed-employee-*.md` | 固定员工 Prompt、runbook、工作流、职责卡源文件 |
| `documents/shared/company/fixed-employee-routing-config.yaml` | 固定员工路由和分派配置 |
| `documents/shared/governance/*` | 企业治理规则和制度约束 |
| `documents/department/*` | 各部门制度、流程、模板和 SOP |
