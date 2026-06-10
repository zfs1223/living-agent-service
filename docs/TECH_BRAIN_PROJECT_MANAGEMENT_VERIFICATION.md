# 技术部门大脑项目管理功能落地验证报告

> 生成时间：2026-06-09
> 验证范围：TechBrain 使用项目管理工具对部门内员工进行管理的功能是否真正落地
> 验证方法：逐文件阅读源码与规范文档，追踪从大脑 Prompt → 工具注册 → 员工职责卡 → 执行链路的完整传递路径

---

## 一、总体结论

技术部门大脑使用项目管理工具管理部门内员工的功能，**基础设施层已基本就绪，但规范传递链路和执行链路存在多处断裂，导致该功能在实际运行中无法有效落地。**

具体而言，工具实现已到位（OpenProjectTool/JiraTool 提供完整的 CRUD API），TechBrain 的系统提示词已声明可使用 Jira 工具，但以下三个关键环节存在断裂：

1. **员工规范层完全缺失**：5 份核心规范文档（职责卡、系统提示词、Agent Prompt、自主执行手册、文档工作流）中没有任何一份提到项目管理工具的使用。
2. **员工工具授权过于集中**：10 个技术员工中仅 T02（架构师）配置了 `jira` 工具和 "Jira项目管理" 能力，其余 9 人无权使用。
3. **执行链路未打通**：`ToolBackedEmployeeTaskExecutor` 没有项目管理专属路由，`DepartmentChatService` 不使用 `TechLeadOrchestrator`，内部任务系统与外部项目管理工具之间没有交互。

### 落地状态概览

| 层次 | 检查项 | 状态 |
|------|--------|------|
| 工具实现 | OpenProjectTool / JiraTool | ✅ REAL |
| 工具注册 | ToolConfig 条件注册（OpenProject 优先） | ✅ REAL |
| TechBrain 提示词 | 列出 jira_* 作为可用工具 | ✅ REAL |
| TechBrain 协作编排 | TechLeadOrchestrator 4 角色分配 | ✅ REAL |
| 内部任务系统 | TaskCheckout + TaskController + ProjectController | ✅ REAL |
| 员工工具授权 | T02/T03/T04/T09/T10 配置 jira（5/10） | 🟢 已修复 |
| 员工职责卡 | 已补充项目管理工具使用 | 🟢 已修复 |
| 员工系统提示词 | 已补充项目管理工具使用指引 | 🟢 已修复 |
| 员工 Agent Prompt | 已补充项目管理步骤 | 🟢 已修复 |
| 员工自主执行手册 | 已补充项目管理自动化边界 | 🟢 已修复 |
| 员工文档工作流 | 已补充项目管理路由 | 🟢 已修复 |
| 执行器路由 | ToolBackedEmployeeTaskExecutor 已增加项目管理路由 | 🟢 已修复 |
| ExecutionCapability | 已新增 PROJECT_MANAGEMENT / ISSUE_TRACKING | 🟢 已修复 |
| 部门聊天链路 | DepartmentChatService 不调用 LeadOrchestrator | 🟡 待评估 — 代码审查循环建议接入 |
| 内外系统桥接 | 内部 TaskCheckout 与外部 JiraTool 无交互 | 🟡 待评估 — 建议单向推送 |

---

## 二、基础设施层验证

### 2.1 项目管理工具实现

#### OpenProjectTool（优先注册）

- **文件**：`living-agent-core/src/main/java/com/livingagent/core/tool/impl/enterprise/OpenProjectTool.java`
- **注册名**：`"jira"`（与 JiraTool 互斥共用注册名）
- **描述**：`"项目管理工具 (OpenProject)，用于查询和管理任务、缺陷和项目"`
- **状态**：✅ REAL
- **支持的操作**：`search_issue`、`get_issue`、`create_issue`、`update_issue`、`add_comment`、`search_user`
- **API**：调用 OpenProject v3 REST API（`/api/v3/work_packages`），完整的 Basic Auth 认证

#### JiraTool（备选注册）

- **文件**：`living-agent-core/src/main/java/com/livingagent/core/tool/impl/enterprise/JiraTool.java`
- **注册名**：`"jira"`
- **状态**：✅ REAL
- **功能与 OpenProjectTool 对等**，同样支持 6 个 action

#### 注册互斥逻辑

来自 `ToolConfig.java`：

```text
if (openprojectBaseUrl 已配置)  → 注册 OpenProjectTool（名为 "jira"）
else if (jiraBaseUrl 已配置)   → 注册 JiraTool（名为 "jira"）
else                           → 不注册，日志记录 "missing base-url"
```

docker-compose.yml 中 `OPENPROJECT_BASE_URL=http://openproject:8080` 已配置，因此 **OpenProjectTool 会被实际注册**。

### 2.2 TechBrain 访问能力

TechBrain 通过 `toolRegistry.getAll()` 获取注册表中的**全部工具**，不做任何过滤。这意味着：

- TechBrain **可以**访问已注册的 OpenProjectTool（名为 "jira"）
- TechBrain **也可以**访问所有其他工具（包括财务部的 `budget_management`、`invoice_processing`），不符合最小权限原则
- `BrainBoundaryEnforcer` 虽然在设计上存在，但 `BrainConfig` 中并未调用 `setBrainBoundaryEnforcer()`，因此边界检查**未激活**

### 2.3 TechBrain 系统提示词

TechBrain 的 System Prompt 硬编码部分明确列出了：

```text
你可以使用以下工具：
- claude_cli : Claude CLI 代码生成、审查、测试、调试、会话恢复
- gitlab_* : GitLab 相关操作
- jira_* : Jira 任务管理操作
- jenkins_* : Jenkins 构建操作
```

`TechClaudeCliPromptTemplates.SHARED_POLICY` 中有一条间接提及：

```text
多仓库协作：如果需要同时查看 GitLab / Jenkins / OpenProject，只读取必要元数据，不导出敏感内容。
```

### 2.4 TechLeadOrchestrator 协作编排

`core/brain/collaboration/impl/TechLeadOrchestrator.java` 预设 4 个团队角色：

| 角色 | 通道 |
|------|------|
| `code-reviewer`（代码审查员） | `channel://tech/code-review` |
| `architect`（架构师） | `channel://tech/architecture` |
| `frontend-dev`（前端工程师） | `channel://tech/frontend` |
| `backend-dev`（后端工程师） | `channel://tech/backend` |

支持的操作：`assignTask()`、`completeTask()`、`failTask()`、`sendToTeammate()`、`broadcastToTeam()`，以及完整的代码审查循环（`submitForReview()` → `requestChanges()` → `resubmitCode()` → `approveCode()` → `escalateReview()`）。

**但此协作编排器与 `DepartmentChatService` 主链路不连通**（详见第四节）。

### 2.5 内部任务/项目管理系统

系统内部存在一套完整的项目管理基础设施：

| 模块 | 文件 | 状态 | 说明 |
|------|------|------|------|
| TaskCheckout | `core/ops/scheduler/TaskCheckout.java` | ✅ REAL | 内部任务生命周期管理（创建/领取/完成/审查） |
| TaskController | `gateway/controller/TaskController.java` | ✅ REAL | 15 个 REST API 端点 |
| ProjectController | `gateway/controller/ProjectController.java` | ✅ REAL | 14 个 REST API 端点 |
| Project 领域模型 | `core/project/Project.java` 等 | ✅ REAL | 项目实体、状态、阶段、统计 |

**关键问题**：这套内部系统与外部项目管理工具（OpenProject/Jira）完全独立运行，没有交互。

---

## 三、员工规范层验证

### 3.1 职责卡（hr-22-tech-fixed-employee-duty-card.md）

**结论：❌ 未提及项目管理。**

职责卡将技术员工的角色定位为"文档分流与知识管理"岗位，六项主要职责全部围绕文档处理展开：识别技术类文档、分类架构/规范/runbook、路由文档、维护版本秩序、发出风险预警。

整份职责卡没有出现 "Jira"、"项目管理"、"任务管理"、"OpenProject" 等任何关键词。

### 3.2 系统提示词（fixed-employee-system-prompts.md）

**结论：❌ 未提及项目管理。**

技术固定数字员工系统提示词段落的内容为：

```text
角色定位：你是技术固定数字员工，负责架构、部署、运行、故障排查、开发规范等文档。
工作重点：技术标准、运行手册、故障处理、版本控制、变更风险
升级条件：生产变更、回滚决策、数据迁移、高风险架构改动
```

通用提示词结构定义了 7 个标准模块（角色定位、职责范围、工作流程、知识库规则、安全与权限、协作协议、输出格式），但其中没有"项目管理"或"工具使用"模块。

### 3.3 Agent Prompt（fixed-employee-agent-prompt.md）

**结论：❌ 未提及项目管理。**

Agent Prompt 是全局通用骨架，定义了 7 步标准处理流程（识别输入 → 判断类型 → 判断风险 → 决定是否自动处理 → 决定是否路由 → 决定是否入库 → 生成输出）。输出格式以文件路径、部门、文档类型、风险等级等字段为主，没有任何项目管理相关的字段或要求。

### 3.4 自主执行手册（fixed-employee-autonomous-runbook.md）

**结论：❌ 无项目管理工具使用流程。**

自主执行手册定义的启动步骤为：读取职责卡模板 → 加载部门职责卡 → 加载路由配置 → 扫描 documents/ 目录 → 分类 → 路由 → 输出报告 → 触发审批。整个流程中没有任何步骤涉及项目管理工具的调用。

### 3.5 文档工作流（fixed-employee-document-workflow.md）

**结论：❌ 未涉及项目管理。**

工作流定义了 8 类文档路由规则，技术类的规则为"路由给技术固定员工；典型内容：架构、部署、运行手册、故障排查、研发规范"，没有提到将任何内容路由到 Jira 或从 Jira 获取任务信息。

### 3.6 路由配置（fixed-employee-routing-config.yaml）

路由配置定义了任务分派规则，但没有涉及项目管理工具的调用或同步。

---

## 四、员工工具授权验证

### 4.1 部门级白名单

`FixedEmployeeRegistry.java` 中 `DEPARTMENT_TOOL_ALLOWLIST` 为技术部门授权了以下工具：

```text
"tech", Set.of("browser_automation", "docker", "github", "gitlab", "jenkins",
    "huggingface", "trae", "claude_cli", "knowledge_graph", "self_improving",
    "jira", "file_edit")
```

`jira` 在部门白名单中 ✅。

### 4.2 各技术员工的实际工具配置

| 员工代码 | 姓名 | 岗位 | 配置的工具 | 含 jira？ | 含项目管理能力？ |
|---------|------|------|-----------|----------|---------------|
| T01 | 真砺 | 代码审查员 | `gitlab`, `github` | ❌ 否 | ❌ 否 |
| **T02** | **真构** | **架构师** | **`gitlab`, `jira`** | **✅ 是** | **✅ 是**（capabilities 含 "Jira项目管理"） |
| **T03** | **真捷** | **DevOps工程师** | **`jenkins`, `docker`, `gitlab`, `claude_cli`, `browser_automation`, `jira`** | **✅ 是** | **✅ 是**（capabilities 含 "Jira项目管理"） |
| **T04** | **真稳** | **运维工程师** | **`proactive_agent`, `docker`, `claude_cli`, `jira`** | **✅ 是** | **✅ 是**（capabilities 含 "Jira项目管理"） |
| T05 | 真模 | AI模型管理员 | `huggingface`, `claude_cli` | ❌ 否 | ❌ 否 |
| T06 | 真续 | 状态管理员 | `knowledge_graph` | ❌ 否 | ❌ 否 |
| T07 | 真盾 | 安全工程师 | `self_improving` | ❌ 否 | ❌ 否 |
| T08 | 真策 | 配置管理员 | `notion` | ❌ 否 | ❌ 否 |
| **T09** | **真绘** | **前端工程师** | **`gitlab`, `browser_automation`, `jira`** | **✅ 是** | **✅ 是**（capabilities 含 "Jira项目管理"） |
| **T10** | **真栈** | **后端工程师** | **`gitlab`, `knowledge_graph`, `jira`** | **✅ 是** | **✅ 是**（capabilities 含 "Jira项目管理"） |

**10 个技术员工中已有 5 人（T02/T03/T04/T09/T10）配置了 `jira` 工具和 "Jira项目管理" 能力。** 其余 5 人（T01 代码审查员、T05 AI模型管理员、T06 状态管理员、T07 安全工程师、T08 配置管理员）因岗位特性暂不需要项目管理工具。

---

## 五、执行链路验证

### 5.1 ToolBackedEmployeeTaskExecutor 路由

该执行器是员工任务执行的核心入口，其任务类型路由如下：

```text
web_prototype / web_development → executeWebTask()
document_generation             → executeDocumentTask()
data_analysis                   → executeDataAnalysisTask()
legal_review / finance_workflow → executeReviewTask()
file_system_query               → executeToolTask()
project_management / issue_tracking → executeProjectManagementTask()  ← 🟢 已新增
default (generic)               → 拒绝执行，返回 FAILED
```

**已新增 `project_management` / `issue_tracking` 项目管理专属路由。** `executeProjectManagementTask()` 方法会检查员工 jira 工具权限，解析操作意图（create/update/search/comment/get），调用 jira 工具执行，并保存产物文件。

### 5.2 ExecutionCapability 枚举

当前 18 个执行能力枚举值，已新增：

- **`PROJECT_MANAGEMENT`** — 项目管理、任务创建/查询/更新、Issue 追踪、进度同步
- **`ISSUE_TRACKING`** — Issue 追踪、Bug 管理、任务状态流转、工单处理

### 5.3 DepartmentChatService 主链路

`DepartmentChatService` 的执行链路为：

```text
用户消息 → ConversationOrchestrator.orchestrate()
  → MainBrain 规划 → 员工任务分配
  → DepartmentExecutionCoordinator 协调
  → FixedEmployeeDispatcher 分发到员工 Channel
  → DynamicEmployeeTaskConsumerRegistry 消费
  → ToolBackedEmployeeTaskExecutor 执行
```

**关键断裂**：
- `DepartmentChatService` **不使用** `TechLeadOrchestrator`
- `DepartmentChatService` **不调用**项目管理工具
- 只有当 LLM 作为大脑直接处理消息时（非 DepartmentChatService 主链路），才可能通过 function calling 调用 jira 工具

### 5.4 内外系统桥接

内部任务系统与外部项目管理工具之间的关系：

| 层面 | 内部系统 | 外部工具 | 打通状态 |
|------|----------|----------|----------|
| 项目模型 | `core/project/Project.java` | `OpenProjectTool` | ❌ 未打通 |
| 任务调度 | `TaskCheckout` | `JiraTool` | ❌ 未打通 |
| REST API | `TaskController` + `ProjectController` | 无直接关联 | ❌ 独立运行 |
| 协作层 | `TechLeadOrchestrator` | 不涉及外部工具 | ❌ 独立运行 |

---

## 六、规范传递链路完整性

```text
治理文档 (01-employee-governance.md)
    │  tech 部门授权工具含 jira .................. ✅ PASS
    ▼
FixedEmployeeRegistry.java
    │  DEPARTMENT_TOOL_ALLOWLIST 含 jira ......... ✅ PASS
    │  T02/T03/T04/T09/T10 配置 jira + "Jira项目管理" ✅ PASS（5/10）
    │  T01/T05/T06/T07/T08 暂不需要 ............... ⚪ N/A
    ▼
TechBrain 系统提示词
    │  列出 jira_* 作为可用工具 .................. ✅ PASS
    ▼
TechClaudeCliPromptTemplates
    │  仅在多仓库协作时间接提及 OpenProject ....... ⚠️ WEAK
    ▼
职责卡 (hr-22-tech-fixed-employee-duty-card.md)
    │  已补充项目管理工具使用 ...................... 🟢 FIXED
    ▼
系统提示词 (fixed-employee-system-prompts.md)
    │  已补充项目管理工具使用指引 ................. 🟢 FIXED
    ▼
Agent Prompt (fixed-employee-agent-prompt.md)
    │  已补充项目管理步骤 ......................... 🟢 FIXED
    ▼
自主执行手册 (fixed-employee-autonomous-runbook.md)
    │  已补充项目管理自动化边界 ................... 🟢 FIXED
    ▼
文档工作流 (fixed-employee-document-workflow.md)
    │  已补充项目管理路由 ......................... 🟢 FIXED
    ▼
ToolBackedEmployeeTaskExecutor
    │  已新增项目管理执行路由 ..................... 🟢 FIXED
    ▼
ExecutionCapability 枚举
    │  已新增 PROJECT_MANAGEMENT / ISSUE_TRACKING . 🟢 FIXED
```

---

## 七、发现的问题清单（按优先级排序）

> 🟢 = 已修复 | 🟡 = 待评估 | ❌ = 未修复

### P0 — 规范与执行链路断裂

#### P0-1：5 份核心员工规范文档完全没有提及项目管理 🟢 已修复

- **涉及文件**：`hr-22-tech-fixed-employee-duty-card.md`、`fixed-employee-system-prompts.md`、`fixed-employee-agent-prompt.md`、`fixed-employee-autonomous-runbook.md`、`fixed-employee-document-workflow.md`
- **修复内容**：
  - 职责卡：新增"使用 Jira/OpenProject 项目管理工具创建、查询和更新任务"和"将 TechBrain 分配的任务同步到项目管理工具，跟踪执行进度"
  - 系统提示词：新增"项目管理工具使用指引"章节
  - Agent Prompt：新增 Step 6"决定是否需要创建/更新项目管理任务"
  - 自主执行手册：新增允许自动执行（创建/更新/查询 Issue）和必须人工审批（删除/批量/跨部门）
  - 文档工作流：技术类路由新增"项目管理相关：任务创建、Issue 更新、进度查询、代码审查关联"

#### P0-2：仅 T02（架构师）配置了项目管理工具 🟢 已修复

- **涉及文件**：`FixedEmployeeRegistry.java`
- **修复内容**：为 T03（DevOps）、T04（运维）、T09（前端）、T10（后端）增加 `jira` 工具和 "Jira项目管理" capabilities
- **当前状态**：5/10 技术员工配置了 jira，其余 5 人因岗位特性暂不需要

#### P0-3：ToolBackedEmployeeTaskExecutor 没有项目管理路由 🟢 已修复

- **涉及文件**：`ToolBackedEmployeeTaskExecutor.java`
- **修复内容**：
  - 新增 `executeProjectManagementTask()` 方法：检查 jira 工具权限 → 解析操作意图 → 调用 jira 工具 → 保存产物
  - 新增 `resolveProjectManagementInvocation()` 方法：基于关键词匹配操作类型（create/update/search/comment/get）
  - `routeByCapability()` 新增 PROJECT_MANAGEMENT / ISSUE_TRACKING 路由
  - `normalizeTaskType()` 新增 project_management 关键词归一

### P1 — 链路打通与系统桥接

#### P1-1：DepartmentChatService 不使用 TechLeadOrchestrator

- **涉及文件**：`DepartmentChatService.java`
- **现象**：主链路走 `ConversationOrchestrator` → `FixedEmployeeDispatcher`，不经过 `TechLeadOrchestrator`
- **影响**：TechBrain 的协作编排能力（角色分配、代码审查循环等）无法在部门对话中生效
- **建议**：评估是否将 `TechLeadOrchestrator` 接入部门执行链路，或将其能力整合到 `ConversationOrchestrator`

#### P1-2：内部任务系统与外部项目管理工具未桥接

- **涉及文件**：`TaskCheckout.java`、`TaskController.java`、`ProjectController.java`
- **现象**：内部的 `TaskCheckout` 管理任务生命周期，外部的 `OpenProjectTool`/`JiraTool` 提供 API 集成，但二者没有交互
- **影响**：内部创建的任务不会同步到 Jira/OpenProject，外部项目管理工具中的任务也不会反映到内部系统
- **建议**：设计桥接机制，在内部任务创建/更新时同步到外部项目管理工具

#### P1-3：ExecutionCapability 缺少 PROJECT_MANAGEMENT 🟢 已修复

- **涉及文件**：`ExecutionCapability.java`、`LlmBasedMainBrainTaskDirector.java`
- **修复内容**：新增 `PROJECT_MANAGEMENT`（项目管理、任务创建/查询/更新、Issue 追踪、进度同步）和 `ISSUE_TRACKING`（Issue 追踪、Bug 管理、任务状态流转、工单处理）枚举值

### P2 — 工具权限与安全

#### P2-1：TechBrain 通过 getAll() 获取全部工具，无部门隔离 🟢 已修复

- **涉及文件**：`BrainConfig.java`、`ToolRegistry.java`
- **修复内容**：
  - 新增 `BRAIN_TOOL_DEPARTMENT_MAPPING` 静态映射，定义每个大脑可访问的工具部门集合
  - 新增 `filterToolsByBrainDepartment()` 方法，按部门过滤工具
  - 8 个业务大脑（Tech/Hr/Finance/Sales/Cs/Admin/Legal/Ops）已替换为按部门过滤
  - MainBrain 保留 `getAll()` 获取全部工具（跨部门协调需要）
  - 无部门标识的工具视为共享工具，允许所有大脑访问

#### P2-2：TechBrain 提示词中工具列表为硬编码 🟢 已修复

- **涉及文件**：`AbstractBrain.java`、`TechBrain.java`
- **修复内容**：
  - `AbstractBrain` 新增 `buildDynamicToolList()` 方法，根据实际注入的工具动态生成工具列表描述
  - `TechBrain` 的 `SYSTEM_PROMPT` 改为 `SYSTEM_PROMPT_TEMPLATE`，使用 `{TOOL_LIST_PLACEHOLDER}` 占位符
  - `doGetSystemPrompt()` 中动态替换占位符为实际工具列表
  - 提示词与实际工具集始终保持一致，新增工具无需手动同步

---

## 八、修复建议

### 8.1 规范文档补充（P0-1）

#### 职责卡补充

在 `hr-22-tech-fixed-employee-duty-card.md` 中增加：

```text
项目管理工具使用：
- 接收 TechBrain 分配的任务后，在 Jira/OpenProject 中创建对应的 Issue
- 任务进度变化时更新 Issue 状态（IN_PROGRESS / IN_REVIEW / COMPLETED）
- 每日汇报时将 Jira 任务状态同步到部门大脑
- 代码审查流程与 Jira Issue 关联
```

#### 系统提示词补充

在 `fixed-employee-system-prompts.md` 技术员工段落中增加：

```text
项目管理工具使用指引：
- 使用 jira 工具创建/查询/更新任务
- 任务创建时必须关联部门大脑的 executionId
- 任务状态变更必须同步回执给部门大脑
- 代码审查通过时必须更新对应 Issue 状态
```

#### Agent Prompt 补充

在标准处理流程中增加步骤：

```text
Step 4.5：判断是否需要创建/更新项目管理任务
  - 如果接收到的任务来自 TechBrain 分配 → 在 Jira 创建 Issue
  - 如果任务状态发生变化 → 更新 Issue 状态
  - 如果任务完成 → 将 Jira Issue 标记为 Done
```

#### 自主执行手册补充

增加项目管理操作的自动化边界定义：

```text
允许自动执行：
- 创建 Jira Issue（来源于 TechBrain 分配的任务）
- 更新 Issue 状态（基于员工执行进度）
- 查询 Issue 列表（用于每日汇报）

需要审批：
- 删除 Issue
- 批量修改 Issue
- 跨部门 Issue 操作
```

### 8.2 员工工具授权扩展（P0-2）

建议在 `FixedEmployeeRegistry.java` 中为以下员工增加 `jira` 工具：

| 员工 | 理由 |
|------|------|
| T03（DevOps工程师） | 需要将 CI/CD 流水线状态同步到项目管理 |
| T09（前端工程师） | 需要查看和更新前端开发任务状态 |
| T10（后端工程师） | 需要查看和更新后端开发任务状态 |
| T04（运维工程师） | 需要将运维事件关联到项目管理 |

### 8.3 执行器路由补充（P0-3）

在 `ToolBackedEmployeeTaskExecutor.java` 中增加项目管理路由：

```text
project_management / issue_tracking → executeProjectManagementTask()
  → 解析 action（create/update/search/comment）
  → 调用 ToolRegistry 中的 "jira" 工具
  → 返回操作结果
```

在 `ExecutionCapability.java` 中新增：

```text
PROJECT_MANAGEMENT  // 创建/查询/更新项目管理任务
ISSUE_TRACKING      // Issue 追踪和状态管理
```

### 8.4 链路打通建议（P1）

#### 方案 A：将 TechLeadOrchestrator 接入部门执行链路

在 `DepartmentChatService` 中增加 `TechLeadOrchestrator` 的使用：

```text
ConversationOrchestrator.orchestrate() 得到任务计划
  → 如果 primaryDept == tech:
    → TechLeadOrchestrator.assignTask() 分配到团队角色
    → TechLeadOrchestrator 通过 Channel 通信驱动员工
    → 员工执行结果通过 TechLeadOrchestrator 聚合
```

#### 方案 B：将外部项目管理与内部系统桥接

新增 `ProjectManagementBridge` 服务：

```text
内部 TaskCheckout.createTask()
  → ProjectManagementBridge.syncToJira()
  → OpenProjectTool.create_issue()

外部 OpenProjectTool.update_issue()
  → ProjectManagementBridge.syncFromJira()
  → TaskCheckout.updateTaskStatus()
```

---

## 九、验收标准

项目管理功能完整落地后，以下场景必须通过：

### 场景 1：TechBrain 通过 Jira 分配任务给员工

```text
用户在技术部门说："帮我做一个用户管理模块"
预期：
  1. TechBrain 接收任务
  2. TechBrain 调用 jira 工具创建 Issue（"用户管理模块开发"）
  3. TechBrain 通过 LeadOrchestrator 分配给 T10（后端）和 T09（前端）
  4. T09/T10 能查看自己在 Jira 中的任务
  5. 执行完成后更新 Jira Issue 状态
```

### 场景 2：员工主动查询项目管理任务

```text
T02（架构师）被 TechBrain 要求审查本周任务进度
预期：
  1. T02 调用 jira 工具的 search_issue 操作
  2. 获取技术部门本周所有 Issue
  3. 汇总状态并汇报给 TechBrain
```

### 场景 3：内部任务与外部 Jira 同步

```text
用户通过 REST API 创建一个任务
预期：
  1. TaskCheckout.createTask() 创建内部任务
  2. ProjectManagementBridge 同步到 Jira 创建 Issue
  3. 两边状态保持一致
```

---

## 十、修复记录

### 2026-06-09 修复

| 编号 | 问题 | 修复内容 | 修改文件 |
|------|------|----------|----------|
| P0-1 | 5份员工规范文档未提及项目管理 | 补充项目管理工具使用指引、操作流程、自动化边界 | 职责卡/系统提示词/Agent Prompt/自主执行手册/文档工作流 |
| P0-2 | 仅T02配置jira工具 | T03/T04/T09/T10 增加 jira 工具和 Jira项目管理 capabilities | `FixedEmployeeRegistry.java` |
| P0-3 | 执行器无项目管理路由 | 新增 executeProjectManagementTask + resolveProjectManagementInvocation | `ToolBackedEmployeeTaskExecutor.java` |
| P1-3 | ExecutionCapability 缺少项目管理枚举 | 新增 PROJECT_MANAGEMENT / ISSUE_TRACKING | `ExecutionCapability.java` |
| P2-1 | 大脑工具无部门隔离 | BRAIN_TOOL_DEPARTMENT_MAPPING + filterToolsByBrainDepartment | `BrainConfig.java` |
| P2-2 | 大脑工具列表硬编码 | buildDynamicToolList() + SYSTEM_PROMPT_TEMPLATE 动态替换 | `AbstractBrain.java` + `TechBrain.java` |

### 待评估项

| 编号 | 问题 | 评估结论 | 建议 |
|------|------|----------|------|
| P1-1 | DepartmentChatService 不使用 TechLeadOrchestrator | 代码审查循环是独有能力，建议接入 | 采用"能力下沉"策略，仅接入代码审查循环，不替换现有分派链路 |
| P1-2 | 内部任务系统与外部 Jira 未桥接 | 模型差异大，双向同步不推荐 | 建议单向推送（内部完成→Jira），TaskCheckout 完成时可选推送 |
