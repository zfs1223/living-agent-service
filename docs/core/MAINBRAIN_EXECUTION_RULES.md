# 主大脑做事规则：全场景对话处理与任务分配逻辑

> 版本：2026-06-23
> 范围：`docker/living-agent-service`
> 目标：定义主大脑面对用户任何对话时的完整决策逻辑和执行规则

---

## 1. 核心问题

当前主大脑在处理用户对话时，缺乏统一的"做事规则"——先做什么、后做什么、什么条件下走哪条路径、什么条件下拒绝或澄清。导致：

1. **路由混乱**：本应单部门直达的任务被送到主脑拆解（Optional bug 已修复）
2. **LLM 分配失败**：LLM 返回 5 token 的异常短响应，回退到规则版兜底
3. **员工能力不匹配**：分派了没有对应工具的员工（T06 做前端任务）
4. **需求冻结过早**：执行被 BLOCKED 后用户无法追问
5. **澄清逻辑重复**：`orchestrate()` 和 `thenCompose` 链中重复检查

---

## 2. 主大脑做事规则：六步决策法

主大脑处理每一条用户消息，必须严格按以下六步依次执行。**每一步都有明确的进入条件、执行动作、输出结果和退出条件**。

```
用户消息
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Step 1: 意图识别（必须首先执行）                        │
│   输入：用户原始消息 + 对话上下文                        │
│   输出：DialogueDecision（kind/intent/complexity/risk） │
│   失败：降级到规则版，kind=CHAT                         │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 2: 路由决策（基于 Step 1 结果）                    │
│   规则优先级：                                        │
│   2a. 跨部门请求 → 主脑拆解                            │
│   2b. 需要澄清 → 返回澄清问题                           │
│   2c. 有协作部门 → 主脑拆解                            │
│   2d. 单部门+有大脑 → 直达部门大脑                      │
│   2e. 部门不一致 → 主脑拆解                            │
│   2f. 无法判断 → 主脑拆解（兜底）                       │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 3: 需求就绪评估（仅主脑拆解路径）                   │
│   INSUFFICIENT → 返回澄清（不进入规划）                 │
│   PARTIALLY_SUFFICIENT → 记录警告，继续规划             │
│   SUFFICIENT → 继续规划                               │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 4: 任务规划（生成 MainBrainTaskPlan）              │
│   关键约束：                                          │
│   - requiredTools 必须从意图分析中提取                  │
│   - 部门计划必须包含 suggestedRoles                    │
│   - 交付物和验收标准必须明确                            │
│   失败：降级到规则版，生成最小可行计划                    │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 5: 员工分派（基于 Step 4 结果）                   │
│   关键约束：                                          │
│   - 员工必须拥有任务所需工具（工具匹配）                 │
│   - 员工能力必须与 suggestedRoles 匹配                  │
│   - 员工负载不超过上限                                 │
│   - LLM 分配失败时规则版兜底（但规则版也必须检查工具）    │
│   失败：返回 BLOCKED + 原因说明                       │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 6: 执行与交付                                    │
│   6a. 执行 → 收集回执 → 审查闭环（如需）                │
│   6b. 聚合交付 → 质量检查 → 用户响应                    │
│   6c. 失败重试 → 换人重派（最多1次）                    │
│   6d. 最终阻塞 → 清除冻结，允许用户追问                  │
└─────────────────────────────────────────────────────┘
```

---

## 3. 各步骤详细规则

### 3.1 Step 1: 意图识别

**目标**：理解用户到底要什么

**规则**：
| # | 规则 | 说明 |
|---|------|------|
| 1.1 | LLM 意图分析优先 | 使用 `LlmBasedDialogueAnalyzer`，返回 `DialogueDecision` |
| 1.2 | 降级到规则版 | LLM 不可用时使用 `RuleBasedDialogueAnalyzer` |
| 1.3 | kind 分类必须准确 | CHAT/TASK/PROJECT/APPROVAL/CONSULTATION/KNOWLEDGE/CROSS_DEPARTMENT |
| 1.4 | riskLevel >= 4 时标记高风险 | 后续步骤需人工确认 |
| 1.5 | requiresClarification 时必须先澄清 | 不跳过直接规划 |
| 1.6 | requiredTools 从意图中提取 | 用户说"帮我操作电脑"→ requiredTools=[win_automation] |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| `LlmDecisionClient` fallback 返回 kind=CHAT | 应保留原始消息语义，至少根据关键词判断 kind |
| `mapDepartmentToBrain` 重复实现 | 统一使用 `Department.mapDepartmentToBrain()` |
| `clarificationQuestion` 是单个 String | 改为 `List<String>` 与 `MainBrainTaskPlan` 对齐 |

### 3.2 Step 2: 路由决策

**目标**：决定任务走单部门直达还是主脑拆解

**规则优先级**（按顺序短路匹配）：

| 优先级 | 条件 | 路由 | 原因 |
|--------|------|------|------|
| 2a | kind == CROSS_DEPARTMENT | 主脑拆解 | 显式跨部门请求 |
| 2b | requiresClarification == true | 返回澄清 | 信息不足无法路由 |
| 2c | supportingDepartments 非空 | 主脑拆解 | 需要多部门协作 |
| 2d | 主部门 == 用户部门 && 部门有大脑 | 单部门直达 | 本部门可独立完成 |
| 2e | 主部门 != 用户部门 | 主脑拆解 | 可能需要跨部门协调 |
| 2f | 无法判断 | 主脑拆解 | 兜底安全策略 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| ~~规则5 Optional != null 永远为 true~~ | ✅ 已修复为 `.isPresent()` |
| 规则6 过于激进（部门不一致就拆解） | 增加 complexity 判断：complexity <= 2 且无协作部门 → 单部门直达 |
| 缺少 kind=CONSULTATION 的特殊处理 | 咨询类任务 complexity <= 2 → 单部门直达 |

### 3.3 Step 3: 需求就绪评估

**目标**：判断用户需求是否足够清晰，可以开始规划

**规则**：
| # | 规则 | 说明 |
|---|------|------|
| 3.1 | INSUFFICIENT → 必须澄清 | 消息过短、目标不明、缺少关键信息 |
| 3.2 | PARTIALLY_SUFFICIENT → 警告但继续 | 有基本框架但缺少细节 |
| 3.3 | SUFFICIENT → 继续规划 | 需求清晰完整 |
| 3.4 | 澄清只在 Step 3 执行一次 | 不在后续步骤重复检查 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| 澄清逻辑在 `orchestrate()` 和 `thenCompose` 中重复 | 删除 `thenCompose` 中的重复检查，只保留 `orchestrate()` 内的 |
| `parseRequirementStatus` 默认返回 CONFIRMED | 默认应返回 `NEEDS_CLARIFICATION`，更安全 |

### 3.4 Step 4: 任务规划

**目标**：生成可执行的 `MainBrainTaskPlan`

**规则**：
| # | 规则 | 说明 |
|---|------|------|
| 4.1 | requiredTools 必须从 Step 1 提取 | 用户意图中涉及的工具需求 |
| 4.2 | 每个 DepartmentTaskPlan 必须有 suggestedRoles | 指导后续员工分派 |
| 4.3 | 交付物和验收标准必须明确 | 否则无法判断执行是否成功 |
| 4.4 | riskLevel >= 4 时需要人工确认 | 在规划阶段标记 |
| 4.5 | LLM 规划失败时降级到规则版 | 规则版生成最小可行计划 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| `businessDomain` 字段始终为 null | 在 LLM prompt 中要求返回，或删除该字段 |
| `parseRequirementStatus` 默认 CONFIRMED | 改为默认 NEEDS_CLARIFICATION |
| LLM 返回 5 token 异常短响应 | 增加 prompt 约束 + 重试机制 |

### 3.5 Step 5: 员工分派

**目标**：为每个部门计划分配合适的员工

**分派规则**（LLM 版和规则版都必须遵守）：

| 优先级 | 规则 | 说明 |
|--------|------|------|
| 5a | **工具匹配**（硬性约束） | 员工必须拥有任务所需的全部工具，否则不能分派 |
| 5b | **能力匹配** | 员工能力与 suggestedRoles 匹配度最高 |
| 5c | **负载均衡** | 优先分派当前负载最低的员工 |
| 5d | **审查关系** | 如果需要审查，分派有 downstreamReviewers 的员工 |

**LLM 分派 Prompt 必须包含的信息**：
1. 任务所需工具列表（requiredTools）
2. 每个候选员工的工具列表（tools）
3. 每个候选员工的能力列表（capabilities）
4. 每个候选员工的当前负载
5. 明确的 JSON 输出格式要求

**规则版分派（兜底）改进**：

当前规则版逻辑：`definitions.stream().limit(3)` — 直接取前3个员工，不考虑能力匹配。

改进为：
```java
// 规则版分派逻辑改进
List<FixedEmployeeDefinition> candidates = definitions.stream()
    // 1. 工具匹配（硬性约束）
    .filter(def -> requiredTools.isEmpty() || def.tools().containsAll(requiredTools))
    // 2. 能力匹配（优先级排序）
    .sorted((a, b) -> {
        long aMatch = a.capabilities().stream().filter(suggestedRoles::contains).count();
        long bMatch = b.capabilities().stream().filter(suggestedRoles::contains).count();
        return Long.compare(bMatch, aMatch); // 匹配度高的排前面
    })
    // 3. 取前3个
    .limit(3)
    .toList();
```

**分派失败处理**：
- 无合适员工 → 返回 BLOCKED + "当前部门没有具备所需工具的员工"
- LLM 分派失败 + 规则版也无合适员工 → 返回 BLOCKED + 详细原因

### 3.6 Step 6: 执行与交付

**目标**：执行任务、收集结果、交付用户

**执行规则**：
| # | 规则 | 说明 |
|---|------|------|
| 6.1 | 执行超时 90 秒 | 复杂任务可延长到 180 秒 |
| 6.2 | 审查闭环 | 有 downstreamReviewers 时必须通过审查 |
| 6.3 | 失败重试最多 1 次 | 换人重派，不无限重试 |
| 6.4 | 最终阻塞 → 清除冻结 | 允许用户追问重新规划 |
| 6.5 | 聚合交付 | 部门级聚合 → 主脑收口 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| `lastAggregationResult` 并发不安全 | ✅ 已改为 `ConcurrentHashMap<executionId, result>` |
| 跨部门协调结果直接字符串拼接 | 应通过 `mainBrainResponseComposer` 格式化 |
| 90 秒超时可能不够 | 根据 complexity 动态调整 |

---

## 4. 按消息类型的完整处理路径

### 4.1 闲聊（CHAT）

```
用户: "你好"
  → Step 1: kind=CHAT, complexity=1, riskLevel=1
  → Step 2: 规则2b → 单部门直达（非 TASK/PROJECT/APPROVAL 类型）
  → 直接调用 Qwen3Neuron 闲聊响应
  → 不进入规划/分派/执行流程
```

### 4.2 简单任务（TASK, complexity <= 2）

```
用户: "帮我打开记事本"
  → Step 1: kind=TASK, intent=win_automation, requiredTools=[win_automation], complexity=1
  → Step 2: 规则2d → 单部门直达（用户在 tech 部门，tech 有大脑）
  → Step 3: 跳过（单部门直达不评估就绪）
  → Step 4: 跳过（单部门直达不规划）
  → Step 5: 部门大脑直接分派有 win_automation 工具的员工
  → Step 6: 员工执行 → 返回结果
```

### 4.3 复杂任务（TASK, complexity >= 3）

```
用户: "帮我开发一个用户管理系统，包含登录注册和权限管理"
  → Step 1: kind=TASK, intent=software_development, complexity=4, requiredTools=[win_automation]
  → Step 2: 规则2c → 主脑拆解（有协作部门：tech + hr）
  → Step 3: SUFFICIENT → 继续规划
  → Step 4: 生成 MainBrainTaskPlan
       - tech 部门：开发登录注册和权限管理模块
       - hr 部门：提供组织架构和角色权限数据
  → Step 5: LLM 分派
       - tech: T09(前端) + T10(后端) — 都有 win_automation 工具
       - hr: H01(人事专员) — 有 knowledge_graph 工具
  → Step 6: 并行执行 → 审查 → 聚合 → 主脑收口
```

### 4.4 跨部门协调（CROSS_DEPARTMENT）

```
用户: "我需要技术部和法务部一起评估这个系统的合规性"
  → Step 1: kind=CROSS_DEPARTMENT, complexity=4, supportingDepartments=[tech, legal]
  → Step 2: 规则2a → 主脑拆解
  → Step 3: SUFFICIENT → 继续规划
  → Step 4: 生成 MainBrainTaskPlan
       - tech 部门：技术合规评估
       - legal 部门：法律合规评估
  → Step 5: 分派对应部门员工
  → Step 6: 并行执行 → 跨部门协调 → 主脑收口
```

### 4.5 审批（APPROVAL）

```
用户: "请批准我的请假申请"
  → Step 1: kind=APPROVAL, intent=leave_approval, complexity=2
  → Step 2: 规则2d → 单部门直达（hr 部门）
  → Step 3: 跳过
  → Step 4: 跳过
  → Step 5: hr 大脑直接处理
  → Step 6: 审批流程 → 返回结果
```

### 4.6 咨询（CONSULTATION）

```
用户: "公司的年假政策是什么？"
  → Step 1: kind=CONSULTATION, intent=leave_policy, complexity=1
  → Step 2: 规则2b → 单部门直达（非 TASK/PROJECT/APPROVAL 类型）
  → 直接调用部门大脑回答
  → 不进入规划/分派/执行流程
```

### 4.7 知识查询（KNOWLEDGE）

```
用户: "上次项目的技术方案是什么？"
  → Step 1: kind=KNOWLEDGE, intent=knowledge_query, complexity=1
  → Step 2: 规则2b → 单部门直达
  → 查询知识库 → 返回结果
```

### 4.8 信息不足（requiresClarification）

```
用户: "帮我弄一下那个东西"
  → Step 1: kind=TASK, requiresClarification=true, clarificationQuestion="请问您需要完成什么具体任务？"
  → Step 2: 规则2b → 返回澄清问题
  → 用户回答后 → 重新 Step 1
```

### 4.9 高风险（riskLevel >= 4）

```
用户: "删除生产环境的所有数据库"
  → Step 1: kind=TASK, riskLevel=5, intent=database_deletion
  → Step 2: 继续路由
  → Step 3: SUFFICIENT → 继续规划
  → Step 4: 规划中标记高风险
  → Step 5: 尝试人工接管（InterventionNeuron）
       - 有 InterventionNeuron → 返回 ESCALATE_TO_HUMAN
       - 无 InterventionNeuron → 继续执行但添加风险警告
```

---

## 5. 代码改进清单

### 5.1 必须修复（P0）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| P0-1 | ~~路由分类器 Optional bug~~ | `DefaultTaskRouteClassifier.java` | ✅ 已修复 `.isPresent()` |
| P0-2 | ~~聚合结果并发不安全~~ | `DepartmentChatService.java` | ✅ 已改为 ConcurrentHashMap |
| P0-3 | 澄清逻辑重复 | `DepartmentChatService.java` | 删除 thenCompose 中的重复澄清检查 |
| P0-4 | 规则版分派不考虑工具匹配 | `RegistryBackedFixedEmployeeDispatcher.java` | 增加 requiredTools 过滤 + 能力排序 |
| P0-5 | `parseRequirementStatus` 默认 CONFIRMED | `LlmBasedMainBrainTaskDirector.java` | 改为默认 NEEDS_CLARIFICATION |

### 5.2 应该改进（P1）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| P1-1 | LLM 分派 prompt 缺少工具匹配提示 | `LlmBasedFixedEmployeeDispatcher.java` | ✅ 已添加所需工具信息 |
| P1-2 | LLM 返回异常短响应无重试 | `LlmBasedFixedEmployeeDispatcher.java` | 增加1次重试机制 |
| P1-3 | `clarificationQuestion` 单个 String | `DialogueDecision.java` | 改为 `List<String>` |
| P1-4 | `mapDepartmentToBrain` 重复实现 | `LlmBasedDialogueAnalyzer.java` | 统一使用 `Department.mapDepartmentToBrain()` |
| P1-5 | 路由规则6过于激进 | `DefaultTaskRouteClassifier.java` | 增加 complexity 判断 |
| P1-6 | `businessDomain` 字段未使用 | `MainBrainTaskPlan.java` | 在 LLM prompt 中要求返回或删除 |

### 5.3 可以优化（P2）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| P2-1 | 超时时间硬编码 90 秒 | `DepartmentChatService.java` | 根据 complexity 动态调整 |
| P2-2 | 跨部门协调结果直接拼接 | `DepartmentChatService.java` | 通过 mainBrainResponseComposer 格式化 |
| P2-3 | `resumeAfterClarification` 中 .join() | `ConversationOrchestrator.java` | 改为异步链式调用 |
| P2-4 | `executionId` 始终为 null | `DepartmentWebSocketHandler.java` | 从执行结果中获取并传递 |
| P2-5 | InterventionNeuron 未配置时降级不一致 | `ConversationOrchestrator.java` | 统一降级策略 |

---

## 6. 规则版分派改进详细设计

### 6.1 当前逻辑

```java
// RegistryBackedFixedEmployeeDispatcher.java — 当前实现
if (selectedCodes.isEmpty()) {
    definitions.stream().limit(3).map(...).forEach(selectedCodes::add);
}
```

问题：直接取前3个员工，不考虑工具匹配和能力匹配。

### 6.2 改进后逻辑

```java
// 改进后的规则版分派
if (selectedCodes.isEmpty()) {
    List<FixedEmployeeDefinition> matched = definitions.stream()
        // 硬性约束：工具匹配
        .filter(def -> requiredTools.isEmpty()
            || new HashSet<>(def.tools()).containsAll(requiredTools))
        // 优先级排序：能力匹配度
        .sorted((a, b) -> {
            long aMatch = a.capabilities().stream()
                .filter(c -> suggestedRoles.stream()
                    .anyMatch(r -> c.toLowerCase().contains(r.toLowerCase())))
                .count();
            long bMatch = b.capabilities().stream()
                .filter(c -> suggestedRoles.stream()
                    .anyMatch(r -> c.toLowerCase().contains(r.toLowerCase())))
                .count();
            return Long.compare(bMatch, aMatch);
        })
        .limit(3)
        .toList();

    if (matched.isEmpty() && !requiredTools.isEmpty()) {
        // 没有工具匹配的员工，记录警告
        log.warn("No employees with required tools {} in department {}, falling back to all employees",
            requiredTools, department);
        // 降级：不考虑工具，只按能力匹配
        matched = definitions.stream()
            .sorted((a, b) -> /* 能力匹配排序 */)
            .limit(3)
            .toList();
    }

    matched.stream().map(FixedEmployeeDefinition::code).forEach(selectedCodes::add);
}
```

### 6.3 分派失败处理

```java
// 如果最终 selectedCodes 仍为空
if (selectedCodes.isEmpty()) {
    return DepartmentAssignmentResult.blocked(
        "No qualified employees available. Required tools: " + requiredTools
        + ", Required roles: " + suggestedRoles);
}
```

---

## 7. LLM 分派重试机制

### 7.1 当前问题

LLM 返回 5 token 的异常短响应，直接回退到规则版。

### 7.2 改进方案

```java
// LlmBasedFixedEmployeeDispatcher.planAssignments() 改进
String llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, userPrompt);

// 检查响应是否异常短（正常 JSON 至少 50 字符）
if (llmResponse == null || llmResponse.isBlank() || llmResponse.trim().length() < 20) {
    log.warn("LLM dispatch response too short ({} chars), retrying...",
        llmResponse != null ? llmResponse.length() : 0);

    // 重试1次
    llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, userPrompt);

    if (llmResponse == null || llmResponse.isBlank() || llmResponse.trim().length() < 20) {
        log.warn("LLM dispatch still too short after retry, using rule-based fallback");
        return fallbackWithTrace(...);
    }
}
```

---

## 8. 与第11章最优流程的映射

| 第11章组件 | 本规则对应步骤 | 状态 |
|-----------|--------------|------|
| TaskRouteClassifier | Step 2 | ✅ 已实现 |
| InternalReviewService | Step 6.2 | ✅ 已实现 |
| DepartmentTodoPool + EmployeeSelfClaimService | Step 5 | ✅ 已实现并集成到 DCS |
| DepartmentAggregationService | Step 6.5 | ✅ 已实现并集成到 DCS（含 LLM 增强版 LlmDepartmentAggregationService） |
| CrossDepartmentCoordinator | Step 6.5 | ✅ 已实现 |
| RequirementReadinessEvaluator | Step 3 | ✅ 已实现 |
| 员工分派工具匹配 | Step 5 | ✅ 已完成（P0-4：RegistryBackedFixedEmployeeDispatcher 增加 requiredTools 过滤 + 能力排序） |
| LLM 分派重试 | Step 5 | ✅ 已完成（P1-2：LlmBasedFixedEmployeeDispatcher 增加 <20字符重试1次） |
| 澄清去重 | Step 3 | ✅ 已完成（P0-3：删除 thenCompose 重复检查，澄清在 orchestrate() 中统一处理） |

---

## 9. 实施顺序

1. **P0-3**: 删除 thenCompose 中重复的澄清检查
2. **P0-4**: 规则版分派增加工具匹配 + 能力排序
3. **P0-5**: `parseRequirementStatus` 默认值改为 NEEDS_CLARIFICATION
4. **P1-2**: LLM 分派增加重试机制
5. **P1-5**: 路由规则6增加 complexity 判断
6. 其余 P1/P2 按优先级逐步实施

---

## 10. 部门大脑做事规则：五步执行法

部门大脑收到任务后（无论来自用户直达还是主脑转发），必须严格按以下五步依次执行。

```
部门大脑收到 ChannelMessage
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Step D1: 任务理解与分类                                │
│   输入：ChannelMessage（含 goal/requiredTools/          │
│         suggestedRoles/acceptanceCriteria）             │
│   输出：部门执行计划（子任务拆解 + 优先级排序）           │
│   失败：返回 BLOCKED + "任务目标不明确"                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step D2: 员工分派（基于 Step D1 结果）                  │
│   规则优先级：                                        │
│   D2a. 工具匹配（硬性约束）— 员工必须拥有所需工具        │
│   D2b. 能力匹配 — 员工能力与 suggestedRoles 匹配度最高  │
│   D2c. 负载均衡 — 优先分派当前负载最低的员工             │
│   D2d. 审查关系 — 如需审查，分派有 downstreamReviewers  │
│   失败：返回 BLOCKED + "无合适员工"                    │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step D3: 员工执行与审查闭环                             │
│   D3a. 工具优先执行（ToolBacked）                      │
│   D3b. 工具失败 → LLM 降级执行（标记 degraded）         │
│   D3c. 执行成功 + 有审查员 → 提交审查                   │
│   D3d. 审查不通过 → 重做 + 重新审查（最多 maxRounds）    │
│   D3e. 超时（120秒）→ 标记超时失败                      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step D4: 部门级聚合交付                                │
│   D4a. 收集所有员工执行回执                             │
│   D4b. 检查审查状态（全部通过 / 部分通过 / 未审查）      │
│   D4c. 质量评分（基于验收标准达成度）                    │
│   D4d. 一致性检查（多员工产出是否矛盾）                  │
│   失败：聚合失败 → 标记 needsRetry 或 BLOCKED          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step D5: 响应交付与知识沉淀                             │
│   D5a. 单部门任务 → 直接交付用户响应                    │
│   D5b. 跨部门任务 → 交付到主脑收口                      │
│   D5c. 产物归档 → 知识库沉淀                           │
│   D5d. 绩效记录 → 员工执行质量评分                     │
│   D5e. 失败重试 → 换人重派（最多1次）                   │
│   D5f. 最终阻塞 → 清除冻结，允许用户追问                 │
└─────────────────────────────────────────────────────┘
```

---

## 11. 部门大脑各步骤详细规则

### 11.1 Step D1: 任务理解与分类

**目标**：理解任务目标，拆解为可执行的子任务

**规则**：
| # | 规则 | 说明 |
|---|------|------|
| D1.1 | 提取任务元数据 | 从 ChannelMessage.metadata 中提取 goal、requiredTools、suggestedRoles、acceptanceCriteria |
| D1.2 | 判断任务类型 | 单步骤任务（直接执行）vs 多步骤任务（需拆解） |
| D1.3 | 子任务拆解 | 多步骤任务按依赖关系拆解，确定执行顺序 |
| D1.4 | 优先级排序 | 有依赖关系的子任务按拓扑排序，无依赖的可并行 |
| D1.5 | 目标不明确时 | 返回 BLOCKED + "任务目标不明确，需要更多信息" |

**消息来源差异处理**：

| 来源 | 特征 | 处理方式 |
|------|------|---------|
| 用户直达（单部门路由） | metadata 较少，可能缺少 requiredTools | 需自行分析意图，提取工具需求 |
| 主脑转发 | metadata 完整（含 goal、requiredTools、suggestedRoles） | 直接使用元数据，无需重新分析 |
| 协作控制消息（task_completed/task_failed） | 来自其他部门大脑的协作通知 | 委托 LeadOrchestrator 处理 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| TechBrain 收到员工任务后直接短路返回"已派发N个任务" | 应等待员工执行完成，聚合结果后再返回 |
| 协作消息缺少 task_id 时被静默忽略 | 应记录 warn 日志并尝试通过其他元数据关联 |
| publishFallbackResponse 不设置 lastOutputContract | 应统一通过 publishResponse/publishError 设置 Contract |

### 11.2 Step D2: 员工分派

**目标**：为每个子任务分配合适的员工

**分派规则**（与主大脑 Step 5 一致，LLM 版和规则版都必须遵守）：

| 优先级 | 规则 | 说明 |
|--------|------|------|
| D2a | **工具匹配**（硬性约束） | 员工必须拥有任务所需的全部工具，否则不能分派 |
| D2b | **能力匹配** | 员工能力与 suggestedRoles 匹配度最高 |
| D2c | **负载均衡** | 优先分派当前负载最低的员工 |
| D2d | **审查关系** | 如果需要审查，分派有 downstreamReviewers 的员工 |

**分派失败处理**：
- 无工具匹配的员工 → 记录 warn，降级到不考虑工具的能力匹配
- 完全无合适员工 → 返回 BLOCKED + 详细原因
- LLM 分派失败 → 规则版兜底（规则版也必须检查工具匹配）

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| 规则版 `definitions.stream().limit(3)` 不考虑工具和能力 | 增加工具过滤 + 能力排序（见第6章） |
| LLM 分派 prompt 缺少工具匹配提示 | ✅ 已添加所需工具信息 |

### 11.3 Step D3: 员工执行与审查闭环

**目标**：员工执行任务，必要时走审查闭环

**执行规则**：
| # | 规则 | 说明 |
|---|------|------|
| D3.1 | 工具优先执行 | 有 ToolExecutor 时使用 ToolBacked 执行，产出真实文件 |
| D3.2 | LLM 降级执行 | 工具执行失败时降级到 LLM，标记 degraded |
| D3.3 | 降级产物标记 | degraded 产物需标记 `needsHumanReview=true` |
| D3.4 | 审查闭环 | 有 downstreamReviewers 时提交到 InternalReviewService |
| D3.5 | 审查不通过重做 | REVISION_NEEDED → 重新发布任务到员工通道（含审查意见） |
| D3.6 | 审查最大轮次 | maxReviewRounds 默认 2，超过后自动通过 |
| D3.7 | 执行超时 120 秒 | 超时后标记失败，不无限等待 |
| D3.8 | 审查提交失败 | 记录 warn + 标记任务需要人工审查 |

**执行路径决策树**：
```
员工收到任务消息
├── 有 ToolExecutor?
│   ├── 执行成功 → 发送回执 → 有审查员? → 提交审查
│   └── 执行失败 → 降级到 LLM
│       ├── LLM 成功 → 发送 degraded 回执 → 有审查员? → 提交审查
│       └── LLM 也失败 → 发送 failed 回执
└── 无 ToolExecutor → LLM 执行
    ├── 成功 → 发送回执 → 有审查员? → 提交审查
    └── 失败 → 发送 failed 回执
```

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| ToolBacked 失败后 LLM 降级，callLLM 超时 310s 远大于外层 120s | LLM 降级调用应使用更短的超时（60s） |
| 审查提交异常只打 warn 日志 | 应标记任务 `needsHumanReview=true` |
| 超时后底层线程未取消 | 增加 Future.cancel(true) 调用 |

### 11.4 Step D4: 部门级聚合交付

**目标**：聚合部门内所有员工执行结果，检查质量

**聚合规则**：
| # | 规则 | 说明 |
|---|------|------|
| D4.1 | 收集所有回执 | 按部门过滤 EmployeeExecutionReceipt |
| D4.2 | 检查审查状态 | 全部通过 / 部分通过 / 未审查 |
| D4.3 | 质量评分 | 基于验收标准达成度计算 0-1 分 |
| D4.4 | 一致性检查 | 多员工产出是否矛盾（如前端和后端接口定义不一致） |
| D4.5 | 聚合状态判定 | ALL_COMPLETED / PARTIAL / FAILED |
| D4.6 | 聚合失败处理 | needsRetry=true → 触发换人重派；BLOCKED → 返回阻塞 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| TechBrain 员工任务短路不聚合 | 应等待员工执行完成，调用 DepartmentAggregationService.aggregate() |
| 聚合结果 lastAggregationResult 并发不安全 | ✅ 已改为 ConcurrentHashMap |

### 11.5 Step D5: 响应交付与知识沉淀

**目标**：将执行结果交付给用户或主脑，沉淀知识

**交付规则**：
| # | 规则 | 说明 |
|---|------|------|
| D5.1 | 单部门任务 → 直接交付 | 通过 DepartmentChatService 发送用户响应 |
| D5.2 | 跨部门任务 → 交付到主脑 | 通过 MainBrainResponseComposer 生成摘要，主脑收口 |
| D5.3 | 失败重试 | needsRetry=true 时换人重派，最多1次 |
| D5.4 | 最终阻塞 → 清除冻结 | 允许用户追问重新规划 |
| D5.5 | 产物归档 | 执行产物存入知识库 |
| D5.6 | 绩效记录 | 员工执行质量评分存入绩效系统 |

**当前问题与改进**：

| 问题 | 改进 |
|------|------|
| 跨部门协调结果直接字符串拼接 | 应通过 mainBrainResponseComposer 格式化 |
| MainBrain.forwardToDepartment() 是 fire-and-forget | 应增加响应聚合机制 |

---

## 12. 部门大脑按任务来源的处理路径

### 12.1 用户直达的简单任务

```
用户: "帮我打开记事本"（通过 /ws/dept/tech）
  → 主大脑 Step 2: 单部门直达 → TechBrain
  → TechBrain Step D1: 提取 goal="打开记事本", requiredTools=[win_automation]
  → TechBrain Step D2: 分派有 win_automation 工具的员工（如 T09/T10）
  → TechBrain Step D3: 员工执行 → 工具调用 → 成功
  → TechBrain Step D4: 聚合（单员工，直接通过）
  → TechBrain Step D5: 直接交付用户响应
```

### 12.2 用户直达的复杂任务

```
用户: "帮我开发一个前端页面"（通过 /ws/dept/tech）
  → 主大脑 Step 2: 单部门直达 → TechBrain
  → TechBrain Step D1: 拆解为 前端实现 + 后端接口
  → TechBrain Step D2: 分派 T09(前端) + T10(后端)
  → TechBrain Step D3: 并行执行 → T09↔T10 交叉审查
  → TechBrain Step D4: 聚合检查（接口一致性 + 验收标准）
  → TechBrain Step D5: 直接交付用户响应
```

### 12.3 主脑转发的跨部门子任务

```
主脑规划: tech 部门负责"技术合规评估"
  → MainBrain.forwardToDepartment("tech", message)
  → TechBrain Step D1: 提取 goal="技术合规评估", metadata 含 coordination_session_id
  → TechBrain Step D2: 分派有合规评估能力的员工
  → TechBrain Step D3: 员工执行 → 审查
  → TechBrain Step D4: 聚合
  → TechBrain Step D5: 交付到主脑收口（标记为跨部门协调结果）
```

### 12.4 协作控制消息

```
其他部门大脑发送 task_completed/task_failed 消息到 channel://tech/
  → TechBrain Step D1: 识别为协作控制消息
  → 委托 LeadOrchestrator 处理
  → 更新协作任务状态
```

### 12.5 审查闭环

```
员工 T09 完成前端代码 → 有 downstreamReviewers=[T10]
  → Step D3.4: 提交到 InternalReviewService
  → T10 审查 → REVISION_NEEDED（发现接口不一致）
  → 审查监听器 → 重新发布任务到 T09 通道（含审查意见）
  → T09 修改后重新提交 → T10 审查 → APPROVED
  → 审查通过 → 标记任务完成
```

### 12.6 执行失败与重试

```
员工 T09 执行失败
  → Step D3: 发送 failed 回执
  → Step D4: 聚合发现 needsRetry=true
  → Step D5.3: 换人重派（如 T10 接替）
  → T10 执行 → 成功 → 重新聚合 → 交付
```

---

## 13. 部门大脑代码改进清单

### 13.1 必须修复（P0）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| DP0-1 | TechBrain 员工任务短路不聚合结果 | `TechBrain.java` | 等待员工执行完成，调用 DepartmentAggregationService.aggregate() |
| DP0-2 | publishFallbackResponse 不设置 lastOutputContract | `AbstractBrain.java` | 统一通过 publishResponse 设置 Contract |
| DP0-3 | 规则版分派不考虑工具匹配 | `RegistryBackedFixedEmployeeDispatcher.java` | 增加 requiredTools 过滤 + 能力排序 |

### 13.2 应该改进（P1）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| DP1-1 | 双通道响应竞态（ChannelMessage + Contract） | `DepartmentChatService.java` | 统一为 Contract 单通道，ChannelMessage 仅用于内部事件 |
| DP1-2 | LLM 降级调用超时 310s 远大于外层 120s | `DynamicEmployeeTaskConsumerRegistry.java` | LLM 降级调用使用 60s 超时 |
| DP1-3 | 审查提交异常只打 warn | `DynamicEmployeeTaskConsumerRegistry.java` | 标记 needsHumanReview=true |
| DP1-4 | 协作消息缺少 task_id 被静默忽略 | `TechBrain.java` | 记录 warn + 尝试通过其他元数据关联 |
| DP1-5 | MainBrain.forwardToDepartment 无响应聚合 | `MainBrain.java` | 增加响应订阅和聚合机制 |

### 13.3 可以优化（P2）

| # | 问题 | 文件 | 改进 |
|---|------|------|------|
| DP2-1 | 进化策略空实现（REPAIR/OPTIMIZE/INNOVATE） | `AbstractBrain.java` | 实现具体的修复/优化/创新策略 |
| DP2-2 | 超时后底层线程未取消 | `DynamicEmployeeTaskConsumerRegistry.java` | 增加 Future.cancel(true) |
| DP2-3 | BrainOutputContract.plan 字段未使用 | `AbstractBrain.java` | 在 ReAct 循环中填充执行计划 |
| DP2-4 | BrainOutputContract.READY 语义模糊 | `BrainOutputContract.java` | 明确 READY 与 EXECUTING 的区别 |
| DP2-5 | MainBrain 部门识别硬编码关键词 | `MainBrain.java` | 使用 LLM 意图分析结果替代关键词匹配 |

---

## 14. 主大脑与部门大脑的协作规则

### 14.1 主脑 → 部门大脑

| 场景 | 主脑动作 | 部门大脑期望 |
|------|---------|-------------|
| 单部门直达 | 不介入，直接路由 | 完整处理 D1→D5，直接交付用户 |
| 跨部门拆解 | 规划 → 转发到各部门 | 处理 D1→D5，交付到主脑收口 |
| 高风险任务 | 标记风险 → 尝试人工接管 | 如无人接管，继续执行但添加风险警告 |
| 需求澄清 | 返回澄清问题给用户 | 不参与（未进入执行） |

### 14.2 部门大脑 → 主脑

| 场景 | 部门大脑动作 | 主脑期望 |
|------|-------------|---------|
| 执行成功 | 聚合结果 → 交付到主脑 | 主脑收口 → 统一回复用户 |
| 执行失败 | 标记 needsRetry 或 BLOCKED | 主脑决定是否重试或通知用户 |
| 需要跨部门协作 | 发布协作消息到其他部门通道 | 主脑协调跨部门协作 |
| 审查不通过 | 重做 → 重新审查 | 不介入（部门内闭环） |

### 14.3 协作消息格式

```json
{
  "type": "task_completed",
  "source": "channel://tech/lead",
  "task_id": "task-uuid",
  "coordination_session_id": "session-uuid",
  "result_summary": "技术合规评估完成，发现3个风险点",
  "artifacts": ["artifact-ref-1", "artifact-ref-2"]
}
```

---

## 15. 统一改进实施顺序

### 阶段1：严重 Bug 修复
1. P0-1: 路由分类器 Optional bug ✅
2. P0-2: 聚合结果并发不安全 ✅
3. DP0-2: publishFallbackResponse 不设置 Contract

### 阶段2：核心逻辑改进
4. P0-3: 删除 thenCompose 重复澄清检查
5. P0-4 / DP0-3: 规则版分派增加工具匹配 + 能力排序
6. P0-5: parseRequirementStatus 默认值改为 NEEDS_CLARIFICATION
7. DP0-1: TechBrain 员工任务短路不聚合结果

### 阶段3：增强与优化
8. P1-2: LLM 分派增加重试机制
9. P1-5: 路由规则6增加 complexity 判断
10. DP1-2: LLM 降级调用超时调整
11. DP1-3: 审查提交异常标记 needsHumanReview

### 阶段4：架构改进
12. DP1-1: 双通道响应统一为 Contract 单通道
13. DP1-5: MainBrain.forwardToDepartment 响应聚合
14. 其余 P1/P2 按优先级逐步实施

---

## 16. 工具与技能的寻找→匹配→装备闭环

### 16.1 当前现状

| 环节 | 工具 | 技能 |
|------|------|------|
| 注册 | ToolRegistry（启动时全量注册 25+ 工具） | SkillRegistry（三来源加载 SKILL.md） |
| 发现/搜索 | `getByDepartment` / `get`（精确查找） | `searchSkills`（子串匹配）+ `SkillFinderTool` |
| 匹配 | `DefaultEmployeeSelfClaimService.containsAll`（仅自领取路径） | 无 |
| 装备 | 数据库静态配置（启动时加载） | 数据库静态配置 + 4 个默认技能 |
| 执行 | `ToolBackedEmployeeTaskExecutor`（4 层权限检查） | `SkillFinderTool.install` → `reloadSkills` |

**核心问题**：没有"任务需要 X → 寻找 X → 装备 X → 执行"的动态闭环。

### 16.2 严重 Bug：tools/capabilities 混用

**文件**：`DynamicEmployeeTaskConsumerRegistry.java` 第 354、366-367 行

```java
// 当前代码（错误）
def.capabilities() != null ? def.capabilities() : List.of(),  // allowedTools
List<String> availableTools = def.capabilities() != null ? def.capabilities() : List.of();

// 应该是
def.tools() != null ? def.tools() : List.of(),  // allowedTools
List<String> availableTools = def.tools() != null ? def.tools() : List.of();
```

**影响**：
- `capabilities` 是能力描述（如"前端开发"、"后端开发"），不是工具 ID
- `tools` 是工具 ID 列表（如"win_automation"、"knowledge_graph"）
- 将 capabilities 作为 allowedTools 传入，导致工具权限检查完全失效
- 员工可能被授权使用本不该使用的工具，或被拒绝使用本该使用的工具

### 16.3 完整闭环设计

```
任务到达（含 requiredTools/requiredSkills）
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Phase 1: 需求分析                                     │
│   - 从 MainBrainTaskPlan 提取 requiredTools           │
│   - 从 MainBrainTaskPlan 提取 requiredSkills          │
│   - 从 DepartmentTaskPlan 提取 suggestedRoles          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase 2: 工具/技能寻找                                 │
│   2a. 在 ToolRegistry 中查找所需工具                    │
│       - 精确匹配：toolRegistry.get(toolId)             │
│       - 语义匹配：toolRegistry.getByDepartment(dept)   │
│   2b. 在 SkillRegistry 中查找所需技能                   │
│       - 精确匹配：skillRegistry.getSkill(skillId)      │
│       - 语义匹配：skillRegistry.searchSkills(query)    │
│   2c. 工具不存在 → 记录缺失，后续分派时 BLOCKED         │
│   2d. 技能不存在 → 调用 SkillFinderTool.search 搜索     │
│       - 找到 → 可安装                                  │
│       - 未找到 → 记录缺失                              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase 3: 员工匹配                                     │
│   3a. 工具匹配（硬性约束）                              │
│       - 员工 def.tools() 必须包含 requiredTools        │
│       - 不满足 → 排除该员工                            │
│   3b. 技能匹配（软性排序）                              │
│       - 员工 def.requiredSkills 与 requiredSkills 交集  │
│       - 交集越大，匹配度越高，排序越靠前                 │
│   3c. 能力匹配（软性排序）                              │
│       - 员工 def.capabilities 与 suggestedRoles 匹配   │
│   3d. 无匹配员工 → Phase 4（动态装备）                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase 4: 动态装备（当 Phase 3 无匹配员工时）            │
│   4a. 工具装备                                        │
│       - 查找有该工具的其他员工 → 授权当前员工临时使用     │
│       - 或：查找部门内能力最接近的员工 → 临时授权工具     │
│   4b. 技能装备                                        │
│       - 调用 SkillFinderTool.install 安装技能          │
│       - 安装后更新员工 requiredSkills 列表              │
│   4c. 装备失败 → 返回 BLOCKED + 详细原因               │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase 5: 执行与权限校验                                │
│   5a. ToolBackedEmployeeTaskExecutor.isToolAllowed()  │
│       - 检查员工工具列表（含动态装备的工具）             │
│   5b. 执行完成后清理                                   │
│       - 临时授权的工具在任务完成后自动回收                │
│       - 永久装备的技能保留在员工 requiredSkills 中       │
└─────────────────────────────────────────────────────┘
```

### 16.4 各 Phase 对应的代码改动

#### Phase 1: 需求分析（已实现）

- `LlmBasedMainBrainTaskDirector.buildPlanFromParsed()` 提取 `requiredTools` 和 `requiredSkills`
- `LlmBasedDialogueAnalyzer` 提取 `primaryDepartment` 和 `supportingDepartments`
- **无需改动**

#### Phase 2: 工具/技能寻找（部分实现，需增强）

| 改动 | 文件 | 说明 |
|------|------|------|
| 增加 `ToolRegistry.searchTools(query)` | `ToolRegistry.java` | 语义搜索工具（基于 name/description/capabilities 匹配） |
| 增加 `ToolRegistry.getToolsByCapability(cap)` | `ToolRegistry.java` | 按能力标签查找工具 |
| SkillFinderTool 推荐逻辑改为语义匹配 | `SkillFinderTool.java` | 当前是硬编码关键词匹配 |

#### Phase 3: 员工匹配（需实现）

| 改动 | 文件 | 说明 |
|------|------|------|
| **修复 tools/capabilities 混用 bug** | `DynamicEmployeeTaskConsumerRegistry.java` | `def.capabilities()` → `def.tools()` |
| RegistryBackedFixedEmployeeDispatcher 增加工具过滤 | `RegistryBackedFixedEmployeeDispatcher.java` | P0-4 已规划 |
| RegistryBackedFixedEmployeeDispatcher 增加技能排序 | `RegistryBackedFixedEmployeeDispatcher.java` | 按 requiredSkills 交集排序 |
| DefaultEmployeeSelfClaimService 增加技能匹配 | `DefaultEmployeeSelfClaimService.java` | 当前只检查 requiredTools，需增加 requiredSkills |

#### Phase 4: 动态装备（需新实现）

| 改动 | 文件 | 说明 |
|------|------|------|
| 新增 `EmployeeEquipmentService` 接口 | `core/autonomy/EmployeeEquipmentService.java` | 动态装备服务 |
| 新增 `DefaultEmployeeEquipmentService` 实现 | `core/autonomy/impl/DefaultEmployeeEquipmentService.java` | 工具临时授权 + 技能安装 |
| FixedEmployeeDefinition 增加临时工具列表 | `FixedEmployeeRegistry.java` | `temporaryTools` 字段，任务完成后回收 |
| FixedEmployeeDefinition 增加动态技能更新 | `FixedEmployeeRegistry.java` | `addSkill()` / `removeSkill()` 方法 |
| SkillFinderTool.install 后更新员工技能 | `SkillFinderTool.java` | 安装后调用 `employee.addSkill()` |

#### Phase 5: 执行与权限校验（部分实现，需修复）

| 改动 | 文件 | 说明 |
|------|------|------|
| 修复 allowedTools 传参 | `DynamicEmployeeTaskConsumerRegistry.java` | `def.capabilities()` → `def.tools()` |
| isToolAllowed 增加临时工具检查 | `ToolBackedEmployeeTaskExecutor.java` | 检查 `def.temporaryTools()` |
| 任务完成后回收临时工具 | `DynamicEmployeeTaskConsumerRegistry.java` | finally 块中调用 `equipmentService.revokeTemporaryTools()` |

### 16.5 EmployeeEquipmentService 接口设计

```java
public interface EmployeeEquipmentService {

    /**
     * 为员工临时装备所需工具
     * @param employeeCode 员工代码
     * @param toolIds 需要装备的工具 ID 列表
     * @param taskId 关联的任务 ID（用于回收）
     * @return 装备结果（成功/失败/部分成功）
     */
    EquipmentResult equipTools(String employeeCode, List<String> toolIds, String taskId);

    /**
     * 为员工安装技能
     * @param employeeCode 员工代码
     * @param skillIds 需要安装的技能 ID 列表
     * @return 装备结果
     */
    EquipmentResult equipSkills(String employeeCode, List<String> skillIds);

    /**
     * 回收临时装备的工具
     * @param taskId 任务 ID
     */
    void revokeTemporaryEquipment(String taskId);

    /**
     * 查找拥有指定工具的员工
     * @param toolIds 工具 ID 列表
     * @param department 部门（可选过滤）
     * @return 匹配的员工代码列表
     */
    List<String> findEmployeesWithTools(List<String> toolIds, String department);

    /**
     * 查找拥有指定技能的员工
     * @param skillIds 技能 ID 列表
     * @param department 部门（可选过滤）
     * @return 匹配的员工代码列表
     */
    List<String> findEmployeesWithSkills(List<String> skillIds, String department);

    record EquipmentResult(
        boolean success,
        List<String> equippedItems,
        List<String> failedItems,
        String message
    ) {}
}
```

### 16.6 与主大脑/部门大脑做事规则的映射

| 做事规则步骤 | 工具/技能闭环对应 | 当前状态 |
|------------|------------------|---------|
| 主脑 Step 1 意图识别 | Phase 1 需求分析 | ✅ requiredTools/requiredSkills 已提取 |
| 主脑 Step 5 员工分派 | Phase 3 员工匹配 | ✅ 已完成（P0-4：RegistryBackedFixedEmployeeDispatcher 增加 requiredTools 过滤 + 能力排序） |
| 主脑 Step 5 员工分派 | Phase 4 动态装备 | ✅ 已完成（NP1-1：EmployeeEquipmentService 实现） |
| 部门 Step D2 员工分派 | Phase 3 员工匹配 | ✅ 已完成（同上） |
| 部门 Step D3 员工执行 | Phase 5 执行与权限校验 | ✅ 已修复（工具P0：tools/capabilities 混用 bug） |
| 部门 Step D5 响应交付 | Phase 5 清理 | ✅ 已完成（NP1-1：EmployeeEquipmentService.recycleTemporaryEquipment） |

### 16.7 实施优先级

| 优先级 | 改动 | 说明 |
|--------|------|------|
| **P0** | 修复 tools/capabilities 混用 bug | `DynamicEmployeeTaskConsumerRegistry` 第 354、366-367 行 |
| **P0** | RegistryBackedFixedEmployeeDispatcher 增加工具过滤 | P0-4 已规划 |
| **P1** | DefaultEmployeeSelfClaimService 增加技能匹配 | 当前只检查工具，不检查技能 |
| **P1** | ToolRegistry 增加 searchTools / getToolsByCapability | 支持语义搜索 |
| **P1** | EmployeeEquipmentService 动态装备服务 | 完成工具技能闭环（从 P2 提升） |
| **P1** | SkillFinderTool 安装后更新员工技能 | 闭环缺失环节（从 P2 提升） |

---

## 17. 知识沉淀闭环

### 17.1 当前现状（已改进）

| 组件 | 状态 | 说明 |
|------|------|------|
| `KnowledgeCaptureService` 接口 | ✅ 已定义 | `captureFromExecution(executionId, department, taskType, goal, resultSummary, employeeCodes)` |
| `KnowledgeCaptureService` 实现 | ✅ 已实现（NP1-5） | `DefaultKnowledgeCaptureService` 实现，存储到 KnowledgeManager |
| `KnowledgeManager` | ✅ 已实现 | `storePrivate/storeDomain/storeShared/addExperience/recordBestPractice` |
| 知识晋升条件 | ✅ 已定义 | `canPromoteToDomain` / `canPromoteToShared`（访问次数≥3、有效性≥0.7） |
| 执行闭环集成 | ✅ 已集成（NP1-6） | `DepartmentChatService.processBrainResponse()` 调用 `knowledgeCaptureService.captureFromExecution()` |
| 知识质量评估 | ✅ 已实现（NP2-1） | `KnowledgeQualityEvaluator` + `DefaultKnowledgeQualityEvaluator` |
| 知识晋升自动化 | ✅ 已实现（NP2-2） | `KnowledgePromotionScheduler` 每10分钟检查晋升条件 |

**核心问题**：执行完成后没有自动触发知识沉淀，执行产物和经验无法沉淀为知识资产。

### 17.2 知识沉淀闭环设计

```
员工执行完成 → 发送回执（含 artifacts + resultSummary）
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Phase K1: 执行结果收集                                 │
│   - 从 EmployeeExecutionReceipt 提取 artifacts        │
│   - 从聚合结果提取 resultSummary                       │
│   - 提取 taskType / goal / employeeCodes              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase K2: 知识质量评估                                 │
│   - 评估执行质量评分（0.0-1.0）                         │
│   - 评估产物有效性（是否有真实文件）                     │
│   - 评估经验价值（是否可复用的最佳实践）                 │
│   - 质量评分 < 0.5 → 不沉淀，仅记录执行日志              │
│   - 质量评分 ≥ 0.5 → 继续沉淀                          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase K3: 知识分类与存储                               │
│   K3a. 个人经验 → L1 私有知识（storePrivate）           │
│        - key: "experience:{employeeCode}:{taskType}"  │
│        - content: 执行过程 + 产物描述                   │
│   K3b. 部门最佳实践 → L2 部门知识（storeDomain）         │
│        - 条件：质量评分 ≥ 0.8 + 部门内首次成功           │
│        - key: "best_practice:{department}:{taskType}" │
│   K3c. 企业规范 → L3 共享知识（storeShared）             │
│        - 条件：晋升条件满足（访问≥3 + 有效性≥0.7）        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase K4: 知识晋升评估                                 │
│   - 每次知识访问时更新 accessCount                      │
│   - 定期检查晋升条件（canPromoteToDomain/Shared）       │
│   - 自动晋升：L1 → L2 → L3                             │
│   - 晋升失败：记录原因，等待下次评估                     │
└─────────────────────────────────────────────────────┘
```

### 17.3 代码改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 新增 `DefaultKnowledgeCaptureService` | `core/autonomy/impl/DefaultKnowledgeCaptureService.java` | 实现 KnowledgeCaptureService 接口 |
| 集成到 `DepartmentChatService.processBrainResponse()` | `DepartmentChatService.java` | 在聚合完成后调用 `knowledgeCaptureService.captureFromExecution()` |
| 集成到 `DynamicEmployeeTaskConsumerRegistry` | `DynamicEmployeeTaskConsumerRegistry.java` | 在发送回执后调用知识沉淀（可选，部门级更合适） |
| 新增 `KnowledgeQualityEvaluator` | `core/autonomy/KnowledgeQualityEvaluator.java` | 评估执行结果是否值得沉淀为知识 |
| 新增 `KnowledgeCaptureResult` | `core/autonomy/KnowledgeCaptureResult.java` | 知识沉淀结果 record |

### 17.4 DefaultKnowledgeCaptureService 实现设计

```java
public class DefaultKnowledgeCaptureService implements KnowledgeCaptureService {

    private final KnowledgeManager knowledgeManager;
    private final KnowledgeQualityEvaluator qualityEvaluator;

    @Override
    public KnowledgeCaptureResult captureFromExecution(
            String executionId, String department, String taskType,
            String goal, String resultSummary, List<String> employeeCodes) {

        // 1. 评估知识质量
        double qualityScore = qualityEvaluator.evaluate(resultSummary, taskType);
        if (qualityScore < 0.5) {
            return KnowledgeCaptureResult.skipped("Quality score too low: " + qualityScore);
        }

        // 2. 为每个员工沉淀个人经验（L1）
        for (String employeeCode : employeeCodes) {
            String key = "experience:" + employeeCode + ":" + taskType;
            knowledgeManager.storePrivate(key, resultSummary, employeeCode, department);
        }

        // 3. 如果质量足够高，沉淀为部门最佳实践（L2）
        if (qualityScore >= 0.8) {
            String key = "best_practice:" + department + ":" + taskType;
            knowledgeManager.storeDomain(key, resultSummary, department, department);
        }

        return KnowledgeCaptureResult.success(
            "Captured " + employeeCodes.size() + " personal experiences"
            + (qualityScore >= 0.8 ? " + 1 department best practice" : ""));
    }
}
```

### 17.5 与执行闭环的集成点

| 集成点 | 触发时机 | 调用方法 |
|--------|---------|---------|
| `DepartmentChatService.processBrainResponse()` | 聚合完成后、保存消息前 | `knowledgeCaptureService.captureFromExecution(executionId, ...)` |
| `DepartmentAggregationService.aggregate()` | 聚合结果生成后 | 可选：在聚合结果中添加 `knowledgeCaptured` 字段 |
| `MainBrainResponseComposer.composeUserResponse()` | 主脑收口时 | 可选：查询相关知识辅助响应生成 |

### 17.6 实施优先级

| 优先级 | 改动 | 说明 |
|--------|------|------|
| **P1** | 实现 `DefaultKnowledgeCaptureService` | 知识沉淀闭环核心 |
| **P1** | 集成到 `DepartmentChatService.processBrainResponse()` | 执行闭环集成 |
| **P2** | 实现 `KnowledgeQualityEvaluator` | 知识质量评估（初期可用简单规则） |
| **P2** | 知识晋升自动化 | 定期检查晋升条件并自动晋升 |

---

## 18. 绩效记录闭环

### 18.1 当前现状（已改进）

| 组件 | 状态 | 说明 |
|------|------|------|
| `PerformanceCaptureService` 接口 | ✅ 已定义 | `captureFromExecution(executionId, department, taskType, goal, employeeCodes, resultStatus)` |
| `PerformanceCaptureService` 实现 | ✅ 已实现（NP1-7） | `DefaultPerformanceCaptureService` 实现，通过 LedgerService 记录绩效积分 |
| `EmployeeExecutionReceipt` | ✅ 已实现 | 包含 `qualityScore` 字段（0.0-1.0） |
| 绩效存储 | ✅ 已实现（NP1-8） | 通过 `LedgerService.IncomeRecord` 存储绩效数据 |
| 执行闭环集成 | ✅ 已集成 | `DepartmentChatService.processBrainResponse()` 调用绩效记录 |
| 绩效统计服务 | ✅ 已实现（NP2-3） | `PerformanceStatsService` + `DefaultPerformanceStatsService`，从 LedgerService 聚合绩效数据 |
| 绩效数据集成到分派 | ✅ 已实现（NP2-4） | `RegistryBackedFixedEmployeeDispatcher` 注入 PerformanceStatsService，能力匹配度相同时绩效高的优先 |

**核心问题**：员工执行质量无法沉淀为绩效数据，无法用于后续分派决策（负载均衡、能力评估）。

### 18.2 绩效记录闭环设计

```
员工执行完成 → 发送回执（含 qualityScore + executionTime）
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Phase P1: 执行绩效提取                                 │
│   - 从 EmployeeExecutionReceipt 提取 qualityScore     │
│   - 提取 executionTime（开始→完成时间差）               │
│   - 提取 taskType / goal / resultStatus               │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase P2: 绩效评分计算                                 │
│   - 基础评分 = qualityScore（0.0-1.0）                 │
│   - 效率加分 = 1.0 - min(executionTime / expectedTime, 1.0) │
│   - 审查加分 = 审查通过 ? 0.1 : 0.0                    │
│   - 最终评分 = 基础评分 * 0.7 + 效率加分 * 0.2 + 审查加分 * 0.1 │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase P3: 绩效记录存储                                 │
│   - 创建 PerformanceRecord（employeeCode, taskType, score, executionId） │
│   - 存储到数据库（performance_records 表）              │
│   - 更新员工累计绩效统计（avgScore, totalTasks）        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Phase P4: 绩效数据应用                                 │
│   - 员工分派时：优先选择 avgScore 高的员工              │
│   - 负载均衡时：考虑绩效评分，避免给低绩效员工过多任务    │
│   - 能力评估时：基于历史绩效推断擅长领域                 │
└─────────────────────────────────────────────────────┘
```

### 18.3 代码改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 新增 `PerformanceRecordEntity` | `core/database/entity/PerformanceRecordEntity.java` | 绩效记录数据库实体 |
| 新增 `PerformanceRecordRepository` | `core/database/repository/PerformanceRecordRepository.java` | 绩效记录 JPA Repository |
| 新增 `DefaultPerformanceCaptureService` | `core/autonomy/impl/DefaultPerformanceCaptureService.java` | 实现 PerformanceCaptureService 接口 |
| 新增 `PerformanceStatsService` | `core/autonomy/PerformanceStatsService.java` | 员工绩效统计服务（avgScore, totalTasks） |
| 集成到 `DepartmentChatService.processBrainResponse()` | `DepartmentChatService.java` | 在聚合完成后调用绩效记录 |
| 集成到员工分派逻辑 | `RegistryBackedFixedEmployeeDispatcher.java` | 分派时考虑绩效评分 |

### 18.4 PerformanceRecordEntity 设计

```java
@Entity
@Table(name = "performance_records")
public class PerformanceRecordEntity {
    @Id
    private String recordId;           // UUID
    private String employeeCode;       // 员工代码
    private String executionId;        // 执行ID
    private String taskType;           // 任务类型
    private String department;         // 部门
    private double qualityScore;       // 质量评分（0.0-1.0）
    private double efficiencyScore;    // 效率评分（0.0-1.0）
    private double reviewScore;        // 审查评分（0.0-0.1）
    private double finalScore;         // 最终评分（加权）
    private long executionTimeMs;      // 执行耗时（毫秒）
    private String resultStatus;       // 结果状态（COMPLETED/FAILED/DEGRADED）
    private Instant createdAt;         // 创建时间

    // 索引：employeeCode + createdAt（用于查询员工历史绩效）
}
```

### 18.5 绩效数据在员工分派中的应用

```java
// RegistryBackedFixedEmployeeDispatcher 改进
List<FixedEmployeeDefinition> matched = definitions.stream()
    // 1. 工具匹配（硬性约束）
    .filter(def -> requiredTools.isEmpty() || def.tools().containsAll(requiredTools))
    // 2. 绩效排序（新增）
    .sorted((a, b) -> {
        double aScore = performanceStatsService.getAverageScore(a.code());
        double bScore = performanceStatsService.getAverageScore(b.code());
        return Double.compare(bScore, aScore); // 高绩效优先
    })
    // 3. 能力匹配（软性排序）
    .sorted((a, b) -> { ... })
    .limit(3)
    .toList();
```

### 18.6 实施优先级

| 优先级 | 改动 | 说明 |
|--------|------|------|
| **P1** | 实现 `DefaultPerformanceCaptureService` | 绩效记录闭环核心 |
| **P1** | 新增 `PerformanceRecordEntity` + Repository | 绩效数据存储 |
| **P1** | 集成到 `DepartmentChatService.processBrainResponse()` | 执行闭环集成 |
| **P2** | 实现 `PerformanceStatsService` | 员工绩效统计 |
| **P2** | 集成到员工分派逻辑 | 绩效数据应用 |

---

## 19. 完整闭环覆盖度（更新）

### 19.1 各环节覆盖度（更新后）

| 环节 | 改进项 | 状态 | 覆盖度 |
|------|--------|------|--------|
| **意图识别** | P1-3/P1-4 | ✅ 已覆盖 | 完整 |
| **路由决策** | P0-1 ✅ / P1-5 | ✅ 已覆盖 | 完整 |
| **需求就绪评估** | P0-3 / P0-5 | ✅ 已覆盖 | 完整 |
| **任务规划** | P1-6 | ✅ 已覆盖 | 完整 |
| **员工分派** | P0-4 / P1-1 ✅ / P1-2 / 工具P0 ✅ / NP1-1 | ✅ 已覆盖 | **完整** |
| **员工执行** | DP1-2 / DP1-3 / DP2-2 | ✅ 已覆盖 | 完整 |
| **审查闭环** | ✅ 已实现 / DP1-3 | ✅ 已覆盖 | 完整 |
| **部门聚合** | DP0-1 / P0-2 ✅ | ✅ 已覆盖 | 完整 |
| **主脑收口** | NP1-3 / P2-2 | ✅ 已覆盖 | **完整** |
| **用户响应** | NP1-4 / DP0-2 / P2-4 | ✅ 已覆盖 | **完整** |
| **知识沉淀** | NP1-5 / NP1-6 / NP2-2 | ✅ 新增覆盖 | **完整** |
| **绩效记录** | NP1-7 / NP1-8 / NP2-3 / NP2-4 | ✅ 新增覆盖 | **完整** |

### 19.2 新增改进项汇总

| # | 新增改进项 | 说明 | 优先级 |
|---|-----------|------|--------|
| **NP1-1** | EmployeeEquipmentService 动态装备服务 | 从 P2 提升为 P1 | P1 |
| **NP1-2** | SkillFinderTool 安装后更新员工技能 | 从 P2 提升为 P1 | P1 |
| **NP1-3** | DP1-5 提升优先级（跨部门响应聚合） | 从阶段4提前到阶段2 | P1 |
| **NP1-4** | DP1-1 提升优先级（双通道统一） | 从阶段4提前到阶段2 | P1 |
| **NP1-5** | 实现 DefaultKnowledgeCaptureService | 知识沉淀闭环核心 | P1 |
| **NP1-6** | 集成知识沉淀到 DepartmentChatService | 执行闭环集成 | P1 |
| **NP1-7** | 实现 DefaultPerformanceCaptureService | 绩效记录闭环核心 | P1 |
| **NP1-8** | 新增 PerformanceRecordEntity + Repository | 绩效数据存储 | P1 |
| **NP2-1** | 实现 KnowledgeQualityEvaluator | 知识质量评估 | P2 |
| **NP2-2** | 知识晋升自动化 | 定期检查晋升条件 | P2 |
| **NP2-3** | 实现 PerformanceStatsService | 员工绩效统计 | P2 |
| **NP2-4** | 集成绩效数据到员工分派 | 绩效数据应用 | P2 |

---

## 20. 最终实施顺序（完整闭环）

### 阶段1：严重 Bug 修复
1. P0-1: 路由分类器 Optional bug ✅
2. P0-2: 聚合结果并发不安全 ✅
3. 工具P0: tools/capabilities 混用 ✅
4. DP0-2: publishFallbackResponse 不设置 Contract

### 阶段2：核心逻辑改进
5. P0-3: 删除 thenCompose 重复澄清检查
6. P0-4 / DP0-3: 规则版分派增加工具匹配 + 能力排序
7. P0-5: parseRequirementStatus 默认值改为 NEEDS_CLARIFICATION
8. DP0-1: TechBrain 员工任务短路不聚合结果
9. **NP1-4**: DP1-1 双通道响应统一（提前）
10. **NP1-3**: DP1-5 跨部门响应聚合（提前）

### 阶段3：增强与闭环补全
11. **NP1-1**: EmployeeEquipmentService 动态装备服务
12. **NP1-2**: SkillFinderTool 安装后更新员工技能
13. **NP1-5**: DefaultKnowledgeCaptureService 实现
14. **NP1-6**: 知识沉淀集成到 DepartmentChatService
15. **NP1-7**: DefaultPerformanceCaptureService 实现
16. **NP1-8**: PerformanceRecordEntity + Repository
17. P1-2: LLM 分派增加重试机制
18. P1-5: 路由规则6增加 complexity 判断
19. DP1-2: LLM 降级调用超时调整
20. DP1-3: 审查提交异常标记 needsHumanReview

### 阶段4：优化与扩展
21. **NP2-1**: KnowledgeQualityEvaluator
22. **NP2-2**: 知识晋升自动化
23. **NP2-3**: PerformanceStatsService
24. **NP2-4**: 绩效数据集成到员工分派
25. P2系列（超时动态调整、executionId传递等）
26. DP2系列（进化策略实现、READY语义明确等）

---

## 21. 闭环完整性最终评估

| 类别 | 状态 | 覆盖度 |
|------|------|--------|
| 主大脑六步决策法 | ✅ 完整覆盖 | 100% |
| 部门大脑五步执行法 | ✅ 完整覆盖 | 100% |
| 工具技能寻找→匹配→装备 | ✅ 完整覆盖 | 100% |
| 知识沉淀闭环 | ✅ 新增覆盖 | 100% |
| 绩效记录闭环 | ✅ 新增覆盖 | 100% |

**结论**：按本方案实施后，可实现 **100% 完整闭环**。

---

## 22. 规范强制加载链

### 22.1 规范加载顺序

大脑和员工执行前必须按以下顺序加载规范，确保不依赖模型自由发挥：

```
职责卡 / 大脑职责定义（documents/shared/company/hr-*-duty-card.md）
  │
  ▼
系统提示词（fixed-employee-system-prompts.md / brain guidelines）
  │
  ▼
Agent Prompt（fixed-employee-agent-prompt.md）
  │
  ▼
自主执行手册（fixed-employee-autonomous-runbook.md）
  │
  ▼
文档工作流（fixed-employee-document-workflow.md）
  │
  ▼
部门制度 / 治理规则（documents/shared/governance）
  │
  ▼
DynamicPromptBuilder 拼装
  │
  ▼
BrainContext / DecisionContext 注入
  │
  ▼
DepartmentChatService / ConversationOrchestrator 编排
  │
  ▼
EmployeeWorkAssignment 任务单
  │
  ▼
EmployeeTaskExecutor 执行
  │
  ▼
EmployeeExecutionReceipt 回执
  │
  ▼
ExecutionReceiptReviewer 验收
  │
  ▼
ArtifactRecord / RuntimeEventStore / KnowledgeCapture / PerformanceCapture 沉淀
```

### 22.2 强制要求

| # | 要求 | 说明 |
|---|------|------|
| 22.2.1 | **职责卡必须优先于模型自由发挥** | 模型不能临时发明规则覆盖职责卡 |
| 22.2.2 | **Prompt 只负责注入规则** | 不允许临时发明规则覆盖职责卡 |
| 22.2.3 | **runbook 必须进入任务单或执行上下文** | 员工执行时必须能看到 runbook |
| 22.2.4 | **澄清和越权必须在编排层硬判断** | 不能完全依赖 LLM 判断 |
| 22.2.5 | **回执必须能证明是否按规范执行** | 回执中应包含规范执行记录 |
| 22.2.6 | **Trace 必须记录关键规范判断** | 澄清、越权、权限、验收、人工介入 |

### 22.3 规范加载代码路径

| 组件 | 文件 | 作用 |
|------|------|------|
| 职责卡加载 | `documents/shared/company/hr-*-duty-card.md` | 各部门员工职责定义 |
| 系统提示词 | `documents/shared/company/fixed-employee-system-prompts.md` | 固定员工系统级行为约束 |
| Agent Prompt | `documents/shared/company/fixed-employee-agent-prompt.md` | 固定员工 Agent 执行提示模板 |
| 自主执行手册 | `documents/shared/company/fixed-employee-autonomous-runbook.md` | 固定员工任务执行步骤 |
| 文档工作流 | `documents/shared/company/fixed-employee-document-workflow.md` | 文档产物处理、归档、交付流程 |
| 规范强制加载链 | `core/brain/prompt/StandardLoadingChainService.java` | 职责卡→Prompt→runbook→文档工作流→自定义指令强制加载 |
| 规范合规追踪 | `core/runtime/StandardComplianceTraceService.java` | 边界检查/标准加载/澄清/升级/回执合规/权限检查追踪 |
| 动态 Prompt | `core/brain/prompt/DynamicPromptBuilder.java` | 拼装角色、人格、知识、技能、工具、guidelines |
| 指令文件加载 | `core/brain/prompt/InstructionFileLoader.java` | 加载 `.living/{employeeId}/instructions.md` 指令链 |

---

## 23. 澄清、越权和失败处理规则

### 23.1 澄清规则

**必须澄清的场景**：

| # | 场景 | 说明 |
|---|------|------|
| 23.1.1 | 用户目标不清楚 | 消息过短或意图模糊 |
| 23.1.2 | 验收标准不明确 | 无法判断执行是否成功 |
| 23.1.3 | 缺少必要输入 | 缺少关键参数或上下文 |
| 23.1.4 | 高风险操作 | 财务、法务、人事、安全、权限操作 |
| 23.1.5 | 模型无法判断用户真实意图 | LLM 返回 requiresClarification=true |
| 23.1.6 | 任务超出员工职责 | 员工发现任务不在职责范围内 |

**澄清处理流程**：

```
检测到需要澄清
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ 1. 保存 clarificationQuestions                       │
│    - 存入 MainBrainTaskPlan.clarificationQuestions    │
│    - 存入 DialogueDecision.clarificationQuestion      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 2. 更新状态                                          │
│    - DepartmentConversation.status = WAITING_USER     │
│    - BrainOutputContract.status = NEEDS_CLARIFICATION │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 3. 绑定上下文                                        │
│    - 绑定 taskKey / executionId（如果已创建）          │
│    - 存入 pendingClarifications（sessionId 为 key）   │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 4. 返回用户                                          │
│    - 通过 WebSocket/API 返回澄清问题                   │
│    - 禁止继续等待 output channel 导致超时              │
└─────────────────────────────────────────────────────┘
```

**澄清相关改进项**：

| 改进项 | 说明 | 状态 |
|--------|------|------|
| P0-3 | 删除 thenCompose 中重复澄清检查 | ✅ 已完成（澄清在 orchestrate() 中统一处理） |
| P0-5 | parseRequirementStatus 默认值改为 NEEDS_CLARIFICATION | ✅ 已完成 |
| P1-3 | clarificationQuestion 改为 List<String> | ✅ 已完成（改为 clarificationQuestions） |

### 23.2 越权拦截规则

**必须禁止的越权行为**：

| # | 越权行为 | 拦截机制 |
|---|---------|---------|
| 23.2.1 | 固定员工替主脑做跨部门战略决策 | `ExecutionBoundaryEnforcer` 检查跨部门操作 |
| 23.2.2 | 固定员工替部门脑做最终验收 | `BrainBoundaryEnforcer` 检查验收权限 |
| 23.2.3 | 部门脑擅自接管其他部门主责工作 | `Department.mapDepartmentToBrain()` 路由检查 |
| 23.2.4 | 低权限用户销毁会话、任务、artifact | `WorkItemPermissionService` 权限检查 |
| 23.2.5 | 大脑/员工绕过权限系统操作项目、任务 | `PermissionService.getAccessLevel()` 检查 |
| 23.2.6 | 员工擅自改写职责卡、系统提示词、runbook | 进化权限检查（evolution_write 禁止） |
| 23.2.7 | 固定员工直接访问代码库 | `CodebaseAccessService` 权限检查（codebase_access 禁止） |
| 23.2.8 | 固定员工写入进化空间 | `EvolutionNamespaceService` 权限检查（evolution_write 禁止） |
| 23.2.9 | 修复循环≥3次或连续失败≥5次不升级 | `BrainBoundaryEnforcer.mustEscalateScenarios` 强制升级 |

**越权拦截代码路径**：

| 组件 | 文件 | 作用 |
|------|------|------|
| 大脑边界执行器 | `core/brain/BrainBoundaryEnforcer.java` | 大脑职责边界硬判断 |
| 越权拦截执行器 | `core/security/ExecutionBoundaryEnforcer.java` | 员工越权拦截 |
| 权限服务 | `core/security/PermissionService.java` | 用户权限级别检查 |
| 工作项权限服务 | `core/security/WorkItemPermissionService.java` | 项目/任务/会话权限检查 |

### 23.3 失败处理规则

**失败不能静默，必须输出以下字段**：

| 字段 | 说明 |
|------|------|
| `failedReason` | 失败原因描述 |
| `failedStage` | 失败发生的阶段（意图识别/路由/规划/分派/执行/聚合/验收） |
| `retryable` | 是否可重试（true/false） |
| `suggestedNextStep` | 建议的下一步操作 |
| `requiresHumanReview` | 是否需要人工审查 |

**失败处理流程**：

```
执行失败
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ 1. 记录失败信息                                       │
│    - EmployeeExecutionReceipt.status = FAILED        │
│    - BrainOutputContract.status = FAILED             │
│    - 填充 failedReason / failedStage                 │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 2. 判断是否可重试                                     │
│    - retryable = true → 触发换人重派（最多1次）         │
│    - retryable = false → 标记 BLOCKED                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 3. 判断是否需要人工审查                                │
│    - requiresHumanReview = true → 推送人工介入事件     │
│    - requiresHumanReview = false → 直接返回用户       │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 4. 清除冻结状态                                       │
│    - 清除 activeSessionPlans                          │
│    - 允许用户追问重新规划                               │
└─────────────────────────────────────────────────────┘
```

---

## 24. 规范执行证据

### 24.1 证据记录位置

每次任务执行完成后，应能从以下位置看到规范执行证据：

| 证据类型 | 服务/文件 | 应记录内容 |
|---------|----------|-----------|
| **Trace** | `AutonomyTraceService` | 意图识别、路由、分派、澄清、越权、执行、验收 |
| **RuntimeEvent** | `RuntimeEventStore` | conversation/task/execution 事件流 |
| **回执** | `EmployeeExecutionReceiptService` | 员工执行结果、失败原因、产物、风险 |
| **Artifact** | `ArtifactRecordService` | 产物路径、类型、员工、executionId、taskId/projectId |
| **知识沉淀** | `KnowledgeCaptureService` | 可复用经验、问题、解决方案 |
| **绩效沉淀** | `PerformanceCaptureService` | 员工贡献、执行质量、奖励 |
| **对话历史** | `DepartmentChatService` | 用户需求、澄清、最终回复、conversationId |

### 24.2 Trace 必须记录的关键事件

| 事件类型 | 触发时机 | 应记录字段 |
|---------|---------|-----------|
| `intake_classified` | 意图识别完成 | kind/intent/complexity/riskLevel/primaryDepartment |
| `route_decided` | 路由决策完成 | routeType(SINGLE_DEPARTMENT/CROSS_DEPARTMENT)/targetBrain |
| `clarification_needed` | 需要澄清 | clarificationQuestions/sessionId |
| `boundary_violation` | 越权拦截 | violationType/employeeCode/department |
| `employee_assigned` | 员工分派完成 | employeeCodes/taskKey/executionId |
| `execution_started` | 执行开始 | executionId/employeeCode/taskType |
| `execution_completed` | 执行完成 | status/qualityScore/artifacts |
| `review_submitted` | 审查提交 | reviewState/reviewerCode |
| `aggregation_completed` | 聚合完成 | aggregationStatus/needsRetry |
| `escalation_triggered` | 人工介入 | escalationReason/riskLevel |

### 24.3 回执必须包含的规范执行证明

| 字段 | 说明 | 规范要求 |
|------|------|---------|
| `employeeCode` | 员工代码 | 必须与职责卡匹配 |
| `department` | 部门 | 必须与路由决策匹配 |
| `status` | 执行状态 | COMPLETED/FAILED/DEGRADED/BLOCKED |
| `qualityScore` | 质量评分 | 0.0-1.0，用于绩效记录 |
| `artifacts` | 产物列表 | 必须能追踪到 ArtifactRecord |
| `failedReason` | 失败原因 | 失败时必须填写 |
| `requiresHumanReview` | 需要人工审查 | 高风险时必须为 true |
| `reviewState` | 审查状态 | 有审查员时必须记录 |
| `standardCompliance` | 规范合规 | 是否按职责卡/runbook 执行 |

---

## 25. 审查清单

新增或修改大脑/固定员工时，必须检查以下项目：

### 25.1 大脑审查清单

| # | 检查项 | 说明 |
|---|--------|------|
| 25.1.1 | 是否有明确职责边界？ | 在 `BrainBoundaryEnforcer` 中定义 |
| 25.1.2 | 是否有不能做的事？ | 在 `forbiddenActions` 中定义 |
| 25.1.3 | 是否有必须澄清的条件？ | 在 `mustClarifyScenarios` 中定义 |
| 25.1.4 | 是否有必须上报主脑/人工的条件？ | 在 `mustEscalateScenarios` 中定义 |
| 25.1.5 | 是否绑定了 Prompt / guidelines？ | 在 `DynamicPromptBuilder` 中加载 |
| 25.1.6 | 是否有统一输出结构？ | 使用 `BrainOutputContract` |
| 25.1.7 | 是否写入 Trace？ | 调用 `AutonomyTraceService.recordEvent()` |
| 25.1.8 | 是否生成回执？ | 通过 `EmployeeExecutionReceiptService` |
| 25.1.9 | 是否经过验收？ | 通过 `ExecutionReceiptReviewer` |
| 25.1.10 | 是否能追踪 artifact？ | 通过 `ArtifactRecordService` |
| 25.1.11 | 是否符合权限规则？ | 通过 `PermissionService` 检查 |
| 25.1.12 | 是否能在长期 conversation 中恢复上下文？ | 通过 `DepartmentConversation` 绑定 |

### 25.2 固定员工审查清单

| # | 检查项 | 说明 |
|---|--------|------|
| 25.2.1 | 是否有职责卡？ | 在 `documents/shared/company/hr-*-duty-card.md` 中定义 |
| 25.2.2 | 是否有 system prompt？ | 在 `fixed-employee-system-prompts.md` 中定义 |
| 25.2.3 | 是否有 agent prompt？ | 在 `fixed-employee-agent-prompt.md` 中定义 |
| 25.2.4 | 是否有 runbook？ | 在 `fixed-employee-autonomous-runbook.md` 中定义 |
| 25.2.5 | 是否有文档工作流？ | 在 `fixed-employee-document-workflow.md` 中定义 |
| 25.2.6 | 是否有工具列表？ | 在 `FixedEmployeeDefinition.tools` 中定义 |
| 25.2.7 | 是否有能力列表？ | 在 `FixedEmployeeDefinition.capabilities` 中定义 |
| 25.2.8 | 是否有审查员？ | 在 `FixedEmployeeDefinition.downstreamReviewers` 中定义 |
| 25.2.9 | 是否有进化权限？ | 在 `FixedEmployeeDefinition.evolutionPermissions` 中定义 |
| 25.2.10 | 是否有统一输出结构？ | 使用 `EmployeeOutputContract` |
| 25.2.11 | 是否写入回执？ | 通过 `EmployeeExecutionReceiptService` |
| 25.2.12 | 是否接受验收？ | 通过 `ExecutionReceiptReviewer` |

---

## 26. 与其他规范文档的关系

| 文档 | 关系 | 本方案对应章节 |
|------|------|--------------|
| `BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` | 规范约束索引 | 第22-25章补充规范加载链、澄清越权、证据、审查清单 |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | 文件位置索引 | 各章节代码路径引用 |
| `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 整体优化路线图 | 第11章最优流程映射 |
| `MODULE_KNOWLEDGE_MEMORY.md` | 知识记忆模块 | 第17章知识沉淀闭环 |
| `documents/shared/company/fixed-employee-*.md` | 固定员工规范源文件 | 第22章规范加载链引用 |
| `documents/shared/governance/*` | 企业治理规则 | 第22章治理规则加载 |

---

## 27. 完整改进项总表（按优先级）

### 27.1 P0 必须修复（阶段1）

| # | 改进项 | 文件 | 说明 |
|---|--------|------|------|
| P0-1 | 路由分类器 Optional bug | `DefaultTaskRouteClassifier.java` | ✅ 已修复 `.isPresent()` |
| P0-2 | 聚合结果并发不安全 | `DepartmentChatService.java` | ✅ 已改为 ConcurrentHashMap |
| 工具P0 | tools/capabilities 混用 bug | `DynamicEmployeeTaskConsumerRegistry.java` | ✅ 已修复 `def.tools()` |
| DP0-2 | publishFallbackResponse 不设置 Contract | `TechBrain.java` | ✅ 已修复 BrainOutputContract 构建 |
| P0-3 | 删除 thenCompose 重复澄清检查 | `DepartmentChatService.java` | ✅ 已删除，澄清在 orchestrate() 中统一处理 |
| P0-4 | 规则版分派增加工具匹配 | `RegistryBackedFixedEmployeeDispatcher.java` | ✅ 已添加 requiredTools 过滤 + 能力排序 |
| P0-5 | parseRequirementStatus 默认值 | `LlmBasedMainBrainTaskDirector.java` | ✅ 已改为 NEEDS_CLARIFICATION |
| DP0-1 | TechBrain 员工任务短路不聚合 | `TechBrain.java` | ✅ 已改进：中间状态"正在执行...请稍候" + `triggerAsyncFinalResponse` 发送最终结果（前端已处理） |

### 27.2 P1 应该改进（阶段2-3）

| # | 改进项 | 文件 | 说明 |
|---|--------|------|------|
| NP1-1 | EmployeeEquipmentService | `core/autonomy/impl/` | ✅ 已实现：动态装备服务，equipTools/equipSkills/recycle/checkMissingTools |
| NP1-2 | SkillFinderTool 更新员工技能 | `SkillFinderTool.java` | ✅ 已实现：handleInstall 添加 employee_code 参数；FixedEmployeeDefinition 添加 withAdditionalSkill/withAdditionalCapability |
| NP1-3 | DP1-5 跨部门响应聚合 | `MainBrain.java` | ✅ 已实现：determineRequestType 识别部门响应、handleDepartmentResponse 收集响应、aggregateDepartmentResponses 汇总、超时检查 scheduleSessionTimeout、AbstractBrain.publishResponse 回传响应到主脑 |
| NP1-4 | DP1-1 双通道统一 | `DepartmentChatService.java` | ✅ 已实现：processBrainResponseWithContract 构建合成 ChannelMessage 委托到 processBrainResponse，消除重复的状态处理逻辑 |
| NP1-5 | DefaultKnowledgeCaptureService | `core/autonomy/impl/` | ✅ 已实现：captureFromExecution 存储到 KnowledgeManager |
| NP1-6 | 知识沉淀集成 | `DepartmentChatService.java` | ✅ 已实现：processBrainResponse 调用 knowledgeCaptureService.captureFromExecution() |
| NP1-7 | DefaultPerformanceCaptureService | `core/autonomy/impl/` | ✅ 已实现：captureFromExecution 通过 LedgerService 记录绩效积分 |
| NP1-8 | PerformanceRecordEntity | `LedgerService.java` | ✅ 已实现：通过 LedgerService.IncomeRecord 存储绩效数据 |
| P1-1 | LLM 分派 prompt 工具提示 | `LlmBasedFixedEmployeeDispatcher.java` | ✅ 已添加所需工具信息 |
| P1-2 | LLM 分派重试机制 | `LlmBasedFixedEmployeeDispatcher.java` | ✅ 已添加异常短响应重试（<20字符重试1次） |
| P1-3 | clarificationQuestion 改为 List | `DialogueDecision.java` | ✅ 已改为 clarificationQuestions (List<String>)，兼容旧格式 |
| P1-4 | mapDepartmentToBrain 统一 | `LlmBasedDialogueAnalyzer.java` | ✅ 已使用 Department.mapDepartmentToBrain() |
| P1-5 | 路由规则6增加 complexity 判断 | `DefaultTaskRouteClassifier.java` | ✅ 已添加 complexity<=2 单部门直达，>2 走主脑 |
| DP1-2 | LLM 降级超时调整 | `DynamicEmployeeTaskConsumerRegistry.java` | ✅ 已添加 fallbackTimeoutMs=60_000 |
| DP1-3 | 审查提交异常标记 | `DynamicEmployeeTaskConsumerRegistry.java` | ✅ 已添加 withNeedsHumanReview(true) 标记 |

### 27.3 P2 可以优化（阶段4）

| # | 改进项 | 文件 | 说明 |
|---|--------|------|------|
| NP2-1 | KnowledgeQualityEvaluator | `core/autonomy/` | ✅ 已实现：KnowledgeQualityEvaluator 接口 + DefaultKnowledgeQualityEvaluator 实现，assess/calculatePromotionReadiness |
| NP2-2 | 知识晋升自动化 | `KnowledgePromotionScheduler.java` | ✅ 已实现：@Scheduled 每10分钟检查晋升条件，PRIVATE→DOMAIN(readiness>=0.6)、DOMAIN→SHARED(readiness>=0.75) |
| NP2-3 | PerformanceStatsService | `core/autonomy/` | ✅ 已实现：PerformanceStatsService 接口 + DefaultPerformanceStatsService 实现，从 LedgerService 聚合绩效数据 |
| NP2-4 | 绩效数据集成到分派 | `RegistryBackedFixedEmployeeDispatcher.java` | ✅ 已实现：注入 PerformanceStatsService，能力匹配度相同时绩效高的优先 |
| P2-1 | 超时动态调整 | `DepartmentChatService.java` | ✅ 已实现：resolveTimeoutSeconds 根据 complexity 动态调整（1-2:60s, 3-4:120s, 5+:180s） |
| P2-2 | 跨部门协调格式化 | `DepartmentChatService.java` | 通过 mainBrainResponseComposer（已有 DefaultMainBrainResponseComposer + LlmBasedMainBrainResponseComposer） |
| P2-3 | resumeAfterClarification 异步 | `ConversationOrchestrator.java` | ✅ 已实现：resumeAfterClarificationAsync 使用 thenApply 异步链式调用，避免 .join() 阻塞 |
| P2-4 | executionId 传递 | `DepartmentWebSocketHandler.java` | ✅ 已实现：DepartmentChatResult 添加 executionId 字段，从 chatResult 获取 |
| P2-5 | InterventionNeuron 降级统一 | `ConversationOrchestrator.java` | ✅ 已实现：统一降级策略，InterventionNeuron 未配置时降级到大脑自行判断 |
| DP2-1 | 进化策略实现 | `AbstractBrain.java` | ✅ 已实现：REPAIR 保存修复状态到 context、OPTIMIZE 调整人格参数、INNOVATE 记录创新尝试 |
| DP2-2 | 超时线程取消 | `DynamicEmployeeTaskConsumerRegistry.java` | ✅ 已实现：future.cancel(true) 超时/中断后取消底层线程 |
| DP2-3 | BrainOutputContract.plan 填充 | `AbstractBrain.java` | ✅ 已实现：publishResponse 中填充 plan 字段（ReAct循环执行N步/直接响应） |
| DP2-4 | READY 语义明确 | `BrainOutputContract.java` | ✅ 已实现：READY=任务已规划等待启动，EXECUTING=正在执行中 |
| DP2-5 | 部门识别 LLM 化 | `MainBrain.java` | ✅ 已实现：关键词无匹配时调用 identifyDepartmentsWithLLM 语义识别 |

---

## 28. 主脑主动汇报机制（自治生命体特性）

> **核心理念**：主脑作为自治生命体，应具备主动汇报能力。当用户以特定身份登录时，主脑应主动汇报该身份应该了解的事情。

### 28.0 现有基础设施（已实现）

> **⚠️ 项目已具备完整的 `proactive` 模块**，位于 `living-agent-core/src/main/java/com/livingagent/core/proactive/`。

| 组件 | 文件 | 功能 | 状态 |
|------|------|------|------|
| **ProactiveOrchestrator** | `gateway/proactive/ProactiveOrchestrator.java` | 主动编排入口，生成建议+风险警报 | ✅ 已实现 |
| **DailyDigestGenerator** | `core/proactive/digest/DailyDigestGenerator.java` | 每日摘要生成（新闻/待办/日程/邮件/报告） | ✅ 已实现 |
| **ProactiveWeeklyReportHandler** | `core/proactive/scenario/ProactiveWeeklyReportHandler.java` | 周报处理器 | ✅ 已实现 |
| **EmployeeOnboardingHandler** | `core/proactive/scenario/EmployeeOnboardingHandler.java` | 员工入职处理器（自动生成入职清单） | ✅ 已实现 |
| **ProactiveSuggestionService** | `core/proactive/suggestion/ProactiveSuggestionService.java` | 主动建议服务 | ✅ 已实现 |
| **RiskPredictor** | `core/proactive/predictor/RiskPredictor.java` | 风险预测器 | ✅ 已实现 |
| **PatternPredictor** | `core/proactive/predictor/PatternPredictor.java` | 模式预测器 | ✅ 已实现 |
| **EventHookManager** | `core/proactive/event/EventHookManager.java` | 事件钩子管理器 | ✅ 已实现 |
| **AlertNotifier** | `core/proactive/alert/AlertNotifier.java` | 告警通知接口 | ✅ 已实现 |
| **FeishuNotifier** | `core/proactive/alert/impl/FeishuNotifier.java` | 飞书通知 | ✅ 已实现 |
| **DingTalkNotifier** | `core/proactive/alert/impl/DingTalkNotifier.java` | 钉钉通知 | ✅ 已实现 |
| **ProactiveTaskScheduler** | `core/proactive/scheduler/ProactiveTaskScheduler.java` | 主动任务调度器 | ✅ 已实现 |
| **LlmProactiveAdvisor** | `core/proactive/llm/LlmProactiveAdvisor.java` | LLM 主动建议 | ✅ 已实现 |
| **LlmRiskAssessor** | `core/proactive/llm/LlmRiskAssessor.java` | LLM 风险评估 | ✅ 已实现 |

### 28.1 设计原则

| 原则 | 说明 |
|------|------|
| **身份驱动** | 根据用户身份（董事长、部门经理、普通员工等）决定汇报内容 |
| **职责映射** | 汇报内容来源于 `documents/` 目录中的职责卡定义 |
| **登录触发** | 用户登录时主动汇报一次，使用过程中不再汇报（除非用户询问） |
| **持续更新** | 汇报内容是长期准备的，随系统运行不断更新 |
| **下次再报** | 下次登录时再次汇报（因为使用过程或自主改进都会有更新） |

### 28.2 身份与汇报内容映射

| 身份 | 汇报内容来源 | 汇报内容示例 |
|------|--------------|--------------|
| **董事长** | `hr-15-founder-chairman-system.md` | 组织战略进度、编制与资源分配状态、数字员工体系运行情况、重大风险预警、跨部门协同待决事项 |
| **部门经理** | `duty-cards/{dept}.md` | 部门员工绩效汇总、部门任务执行状态、部门知识库更新、部门风险事项 |
| **普通员工** | `fixed-employee-duty-card-template.md` | 个人任务完成情况、待处理任务、技能提升建议、协作请求 |
| **访客** | 无 | 仅提供欢迎消息和系统简介 |

### 28.3 汇报时机流程

```
用户登录
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 1: 身份识别                                      │
│   输入：用户 authProvider/accountId/role              │
│   输出：UserRole（董事长/部门经理/普通员工/访客）        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 2: 汇报内容准备                                  │
│   来源：documents/ 职责卡 + 系统运行数据               │
│   内容：                                              │
│   - 战略进度（董事长）                                 │
│   - 部门绩效（部门经理）                               │
│   - 个人任务（普通员工）                               │
│   更新：长期准备，随运行不断更新                        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 3: 主动汇报                                      │
│   时机：登录后首次对话前                               │
│   格式：结构化汇报消息                                 │
│   示例：                                              │
│   "董事长您好，以下是当前系统状态汇报：                 │
│    - 战略进度：XX                                     │
│    - 数字员工运行：XX                                 │
│    - 重大风险：XX                                     │
│    - 待决事项：XX"                                    │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 4: 标记已汇报                                    │
│   存储：sessionMetadata.proactiveReported = true     │
│   效果：本次会话不再主动汇报                           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
                   用户正常使用
```

### 28.4 汇报内容数据来源

| 汇报项 | 数据来源 | 更新频率 |
|--------|----------|----------|
| **战略进度** | `documents/shared/company/` + 任务执行统计 | 每日汇总 |
| **数字员工运行状态** | `EmployeeService.getStatus()` + `EmployeeExecutionReceipt` | 实时 |
| **部门绩效汇总** | `PerformanceRecordEntity` + `EmployeeExecutionReceipt` | 每周汇总 |
| **知识库更新** | `KnowledgeManager.search()` + `KnowledgeEntry` | 每日汇总 |
| **重大风险预警** | `BrainOutputContract.riskLevel` + `AutonomyTraceEvent` | 实时 |
| **跨部门协同待决** | `MainBrainTaskPlan.status` + `InternalReviewService` | 实时 |
| **个人任务完成情况** | `EmployeeExecutionReceipt` + `TaskCheckout` | 实时 |

### 28.5 实现要点

#### 28.5.1 身份识别

```java
// 在 AuthService 或 DepartmentChatService 中添加
public enum UserRole {
    CHAIRMAN,      // 董事长
    DEPT_MANAGER,  // 部门经理
    EMPLOYEE,      // 普通员工
    VISITOR        // 访客
}

public UserRole identifyUserRole(String userId, String authProvider) {
    // 从 documents/shared/company/hr-15-founder-chairman-system.md 获取董事长定义
    // 从 documents/shared/governance/01-employee-governance.md 获取员工定义
    // 从数据库获取用户角色配置
}
```

#### 28.5.2 汇报内容准备服务

```java
// 新增 ProactiveReportService
public interface ProactiveReportService {
    /**
     * 根据用户身份准备汇报内容
     */
    ProactiveReport prepareReport(UserRole role, String userId, String department);
    
    /**
     * 更新汇报内容（长期准备）
     */
    void updateReportContent(UserRole role);
}

public record ProactiveReport(
    UserRole role,
    String userId,
    String department,
    String summary,           // 汇报摘要
    List<ReportItem> items,   // 汇报项列表
    Instant generatedAt,
    Map<String, Object> metadata
) {}

public record ReportItem(
    String category,    // 分类（战略进度/绩效/风险等）
    String title,       // 标题
    String content,     // 内容
    String priority,    // 优先级（high/medium/low）
    String actionHint   // 行动提示（可选）
) {}
```

#### 28.5.3 登录时触发汇报

```java
// 在 DepartmentWebSocketHandler 或 AgentWebSocketHandler 中添加
public void onUserLogin(String sessionId, String userId, UserRole role) {
    if (!sessionMetadata.containsKey(sessionId)) {
        sessionMetadata.put(sessionId, new HashMap<>());
    }
    
    // 标记未汇报
    sessionMetadata.get(sessionId).put("proactiveReported", false);
    
    // 准备汇报内容
    ProactiveReport report = proactiveReportService.prepareReport(role, userId, null);
    
    // 发送汇报消息
    if (report != null && !report.items().isEmpty()) {
        pushProactiveReport(sessionId, report);
        sessionMetadata.get(sessionId).put("proactiveReported", true);
    }
}
```

### 28.6 改进项清单（整合现有 proactive 模块）

| # | 改进项 | 文件 | 说明 |
|---|--------|------|------|
| **PR-0** | 现有 proactive 模块 | `core/proactive/` | ✅ 已实现（14个组件） |
| **PR-1** | 登录时触发汇报 | `DepartmentWebSocketHandler.java` | ✅ 已实现：WebSocket 连接时调用 `ProactiveOrchestrator.runForUser()` |
| **PR-2** | 身份驱动汇报内容 | `ProactiveSuggestionService.java` | ✅ 已实现：注入 TaskCheckout/BountyHunterService/TaskClaimService/Memory/AccessLevel，generateTaskBasedSuggestions() 根据身份生成不同建议 |
| **PR-3** | 职责卡内容解析 | `DutyCardParser.java` | ✅ 已实现：解析 documents/shared/company/duty-cards/*.md；提供 getChairmanReportSummary()、getDutyCardByDepartment() |
| **PR-4** | 董事长专属汇报 | `ProactiveSuggestionService.java` | ✅ 已实现：generateTaskBasedSuggestions() 中 AccessLevel.FULL 分支生成数字员工体系概览、各部门核心使命、任务统计 |
| **PR-5** | 部门经理专属汇报 | `ProactiveSuggestionService.java` | ✅ 已实现：generateTaskBasedSuggestions() 中 AccessLevel.DEPARTMENT 分支生成部门职责提醒、部门成功标准、待认领任务 |
| **PR-6** | 普通员工专属汇报 | `ProactiveSuggestionService.java` | ✅ 已实现：generateTaskBasedSuggestions() 中 AccessLevel.CHAT_ONLY/LIMITED 分支生成收益待结算、技能提升建议、可接取任务 |
| **PR-7** | 前端汇报展示 | `DepartmentChatInline.tsx` | ✅ 已实现：处理 `proactive_report` 消息类型，显示建议和警告 |
| **PR-8** | 汇报内容缓存 | `ProactiveReportCache.java` | ✅ 已实现：全局缓存（董事长视角）+ 用户缓存；每5分钟自动刷新；forceRefresh() 强制刷新 |
| **PR-9** | 登录汇报状态标记 | `SessionMetadata.proactiveReported` | ✅ 已实现：标记本次会话已汇报，避免重复 |

### 28.7 登录触发汇报流程（整合现有组件）

```
用户登录 WebSocket 连接
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 1: 身份识别                                      │
│   输入：用户 authProvider/accountId/role              │
│   输出：UserRole（董事长/部门经理/普通员工/访客）        │
│   现有：PermissionService.getAccessLevel(userId)      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 2: 调用现有 ProactiveOrchestrator               │
│   现有：ProactiveOrchestrator.runForUser(userId)     │
│   返回：OrchestrationResult(suggestions, alerts)     │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 3: 根据身份定制汇报内容                          │
│   董事长：战略进度 + 数字员工运行 + 重大风险            │
│   部门经理：部门绩效 + 部门任务 + 部门知识库            │
│   普通员工：个人任务 + 待处理 + 技能建议               │
│   现有：ProactiveSuggestionService.generateSuggestions│
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 4: 发送汇报消息                                  │
│   现有：AlertNotifier.send(alert)                    │
│   新增：WebSocket 推送 proactive_report 消息          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ Step 5: 标记已汇报                                    │
│   存储：sessionMetadata.proactiveReported = true     │
│   效果：本次会话不再主动汇报                           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
                   用户正常使用
```

### 28.8 现有组件复用策略

| 需求 | 现有组件 | 复用方式 |
|------|----------|----------|
| **风险预警** | `RiskPredictor.getActiveAlerts()` | ✅ 已集成：ProactiveSuggestionService 直接调用 |
| **主动建议** | `ProactiveSuggestionService.generateSuggestions(userId)` | ✅ 已集成：ProactiveOrchestrator 调用 |
| **每日摘要** | `DailyDigestGenerator.generateDigest(userId)` | 登录时调用，生成当日摘要 |
| **周报生成** | `ProactiveWeeklyReportHandler` | 定时触发，生成周报 |
| **入职处理** | `EmployeeOnboardingHandler` | 新员工入职时触发 |
| **事件钩子** | `EventHookManager` | 注册登录事件处理器 |
| **消息推送** | `AlertNotifier` | 通过飞书/钉钉/Webhook推送 |

### 28.8.1 已集成的组件（PR-2 完成）

> **✅ PR-2 改进已完成：ProactiveSuggestionService 已注入 TaskCheckout/BountyHunterService/TaskClaimService/Memory/AccessLevel，generateTaskBasedSuggestions() 根据身份生成不同建议**

| 需求 | 现有组件 | 当前状态 | 说明 |
|------|----------|----------|------|
| **用户任务完成进度** | `TaskCheckout.getStatistics()` | ✅ 已集成（PR-2） | 获取用户任务统计 |
| **用户执行回执** | `EmployeeExecutionReceiptService.getReceiptsByDepartment()` | ✅ 已集成（PR-2） | 获取用户执行历史 |
| **用户个人记忆** | `MemoryService.recall(userId)` | ✅ 已集成（PR-2） | 获取用户偏好/习惯 |
| **用户身份识别** | `PermissionService.getAccessLevel(userId)` | ✅ 已集成（PR-2） | 根据身份定制汇报内容 |
| **部门绩效数据** | `PerformanceRecordEntity` | ✅ 已集成（PR-5） | 部门经理专属汇报 |
| **知识库更新** | `KnowledgeManager.search()` | ✅ 已集成（PR-5） | 部门经理专属汇报 |
| **任务认领状态** | `TaskClaimService.scanAvailable(role)` | ✅ 已集成（PR-2） | 获取可认领任务数量 |
| **赏金任务状态** | `BountyHunterService.getWorkerTasks(workerId)` | ✅ 已集成（PR-2） | 获取员工接取/完成的赏金任务 |

### 28.8.2 任务发布/接取体系（完整流程）

> **⚠️ 任务涉及发布→接取→执行→提交→审查→完成的全流程**

```
董事长/用户发布任务
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ TaskCheckout.createTask()                           │
│   创建任务 → 状态: PENDING                            │
│   存储: TaskRepository (数据库持久化)                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ BountyHunterService.findAvailableTasks()            │
│   员工浏览可接取的任务                                │
│   状态: PENDING → 可被认领                            │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ TaskClaimService.scanAndClaim()                     │
│   神经元自动扫描并认领任务                            │
│   或 BountyHunterService.acceptTask() 手动接取       │
│   状态: PENDING → CHECKED_OUT                        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ 员工执行任务                                         │
│   DynamicEmployeeTaskConsumerRegistry.executeTask() │
│   状态: CHECKED_OUT → IN_PROGRESS                    │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ TaskCheckout.submitTask()                           │
│   员工提交任务结果                                    │
│   状态: IN_PROGRESS → SUBMITTED                      │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ TaskCheckout.reviewTask()                           │
│   审查员审查任务结果                                  │
│   状态: SUBMITTED → COMPLETED 或 NEEDS_REWORK        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│ EmployeeExecutionReceiptService.recordReceipt()     │
│   记录执行回执（质量评分、总结等）                     │
│   用于后续汇报和绩效计算                              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
                   任务完成
```

### 28.8.3 各身份在任务流程中的角色

| 身份 | 任务流程角色 | 汇报内容需求 |
|------|-------------|--------------|
| **董事长** | 任务发布者 + 最终审查者 | 发布的任务状态、有多少员工接取、完成进度、收益统计 |
| **部门经理** | 任务分配者 + 部门审查者 | 部门任务分配情况、员工接取情况、部门完成率 |
| **普通员工** | 任务接取者 + 执行者 | 可接取的任务、已接取的任务、收益统计、技能提升建议 |

### 28.8.4 TaskClaimService 可提供的汇报数据

```java
// TaskClaimService 已实现的方法：
scanAvailable(role)           // 扫描可认领的任务（按角色）
hasClaimableTasks(role)       // 检查是否有可认领任务
scanAndClaim(neuronId, role)  // 自动认领任务

// 可用于汇报：
- "技术部有 5 个待认领任务"
- "您的角色可接取 3 个任务"
- "建议接取任务 T01（优先级高）"
```

### 28.8.5 BountyHunterService 可提供的汇报数据

```java
// BountyHunterService 已实现的方法：
findAvailableTasks(workerId)       // 查找可用任务
getWorkerTasks(workerId)           // 员工所有任务
getWorkerActiveTasks(workerId)     // 员工活跃任务
getWorkerCompletedTasks(workerId)  // 呂工已完成任务
getWorkerEarnings(workerId)        // 呂工收益统计

// WorkerEarnings 包含：
public record WorkerEarnings(
    String workerId,
    double totalEarned,       // 总收益
    double pendingEarnings,   // 待结算收益
    int tasksCompleted,       // 完成任务数
    int tasksRejected,        // 拒绝任务数
    double averageRating,     // 平均评分
    double successRate        // 成功率
) {}

// 可用于汇报：
- "本周您完成 12 个任务，收益 850 元"
- "成功率 92%，平均评分 4.5"
- "有 3 个待结算任务"
```

```java
// TaskCheckout.TaskStatistics 包含：
public record TaskStatistics(
    int pendingCount,      // 待处理任务数
    int checkedOutCount,   // 正在执行任务数
    int completedCount,    // 已完成任务数
    Map<String, Long> employeeActiveCounts  // 各员工活跃任务数
) {}

// 可用于汇报：
- "您有 3 个待处理任务"
- "本周已完成 12 个任务"
- "当前正在执行 2 个任务"
```

### 28.8.3 EmployeeExecutionReceiptService 可提供的汇报数据

```java
// EmployeeExecutionReceipt 包含：
public record EmployeeExecutionReceipt(
    String receiptId,
    String executionId,
    String employeeCode,
    ReceiptStatus status,       // COMPLETED/FAILED/DEGRADED
    String summary,
    double qualityScore,
    Instant completedAt,
    Map<String, Object> metadata
) {}

// 可用于汇报：
- "员工 T01 完成任务，质量评分 0.85"
- "本周部门共完成 45 个任务，平均质量 0.78"
- "有 3 个任务需要人工审查"
```

### 28.9 新增组件设计

#### 28.9.1 登录触发处理器

```java
// 新增 LoginProactiveReportHandler.java
public class LoginProactiveReportHandler implements HookHandler {
    
    private final ProactiveOrchestrator orchestrator;
    private final UserRoleService roleService;
    
    @Override
    public String[] supportedEvents() {
        return new String[]{"user.login", "websocket.connected"};
    }
    
    @Override
    public void handle(HookEvent event) {
        String userId = event.getString("userId");
        String sessionId = event.getString("sessionId");
        
        // 检查是否已汇报
        if (isAlreadyReported(sessionId)) {
            return;
        }
        
        // 获取用户身份
        UserRole role = roleService.identifyUserRole(userId);
        
        // 调用现有 ProactiveOrchestrator
        OrchestrationResult result = orchestrator.runForUser(userId);
        
        // 根据身份定制汇报内容
        ProactiveReport report = customizeReport(role, result);
        
        // 推送汇报消息
        pushProactiveReport(sessionId, report);
        
        // 标记已汇报
        markAsReported(sessionId);
    }
}
```

#### 28.9.2 身份驱动的汇报定制

```java
// 新增 UserRoleProactiveReportCustomizer.java
public class UserRoleProactiveReportCustomizer {
    
    public ProactiveReport customize(UserRole role, OrchestrationResult result) {
        return switch (role) {
            case CHAIRMAN -> customizeForChairman(result);
            case DEPT_MANAGER -> customizeForDeptManager(result);
            case EMPLOYEE -> customizeForEmployee(result);
            case VISITOR -> createWelcomeMessage();
        };
    }
    
    private ProactiveReport customizeForChairman(OrchestrationResult result) {
        // 董事长专属内容：
        // - 战略进度（从 documents/shared/company/ 获取）
        // - 数字员工运行状态（从 EmployeeExecutionReceipt 获取）
        // - 重大风险预警（从 RiskPredictor 获取）
        // - 跨部门协同待决（从 MainBrainTaskPlan 获取）
    }
    
    private ProactiveReport customizeForDeptManager(OrchestrationResult result) {
        // 部门经理专属内容：
        // - 部门绩效汇总（从 PerformanceRecordEntity 获取）
        // - 部门任务执行状态（从 EmployeeExecutionReceipt 获取）
        // - 部门知识库更新（从 KnowledgeManager 获取）
    }
    
    private ProactiveReport customizeForEmployee(OrchestrationResult result) {
        // 普通员工专属内容：
        // - 个人任务完成情况（从 EmployeeExecutionReceipt 获取）
        // - 待处理任务（从 TaskCheckout 获取）
        // - 技能提升建议（从 LlmProactiveAdvisor 获取）
    }
}
```

---

## 29. 文档版本与更新记录

| 版本 | 日期 | 更新内容 |
|------|------|---------|
| v1.0 | 2026-06-23 | 初版：主大脑六步决策法 + 部门大脑五步执行法 |
| v1.1 | 2026-06-23 | 补充：工具技能闭环（第16章） |
| v1.2 | 2026-06-23 | 补充：知识沉淀闭环（第17章）+ 绩效记录闭环（第18章） |
| v1.3 | 2026-06-23 | 补充：规范强制加载链（第22章）+ 澄清越权规则（第23章）+ 执行证据（第24章）+ 审查清单（第25章） |
| v1.4 | 2026-06-23 | P0阶段改进完成：DP0-2、P0-3、P0-4、P0-5、DP0-1 |
| v1.5 | 2026-06-23 | P1阶段改进完成：P1-2、P1-3、P1-4、P1-5、DP1-2、DP1-3；等待过程进度提示机制 |
| v1.6 | 2026-06-23 | 新增：主脑主动汇报机制（第28章）- 自治生命体特性，整合现有 proactive 模块（14个组件已实现） |
| v1.7 | 2026-06-23 | 完善：主动汇报机制整合现有基础设施，新增登录触发、前端汇报展示等改进项 |
| v1.8 | 2026-06-23 | PR系列改进：PR-1 登录触发汇报 ✅、PR-7 前端汇报展示 ✅、PR-9 会话标记 ✅ |
| v1.9 | 2026-06-23 | 补充：任务发布/接取体系完整流程图；TaskClaimService、BountyHunterService 关联说明；各身份在任务流程中的角色 |
| v2.0 | 2026-06-23 | PR-2 改进完成：ProactiveSuggestionService 注入 TaskCheckout/BountyHunterService/TaskClaimService/Memory/AccessLevel，generateTaskBasedSuggestions() 根据身份生成不同建议 |
| v2.1 | 2026-06-23 | PR-3 改进完成：DutyCardParser 解析职责卡；PR-4~PR-6 通过 generateTaskBasedSuggestions() 实现；PR-8 汇报内容缓存服务完成 |
| v2.2 | 2026-06-23 | PR 系列全部完成：PR-0~PR-9 共10项改进，实现完整的主动汇报机制 |
| v2.3 | 2026-06-23 | NP1-3 跨部门响应聚合完成：determineRequestType 识别部门响应、handleDepartmentResponse 收集、aggregateDepartmentResponses 汇总、scheduleSessionTimeout 超时检查、AbstractBrain.publishResponse 回传响应到主脑 |
| v2.4 | 2026-06-23 | NP1-4 双通道统一完成：processBrainResponseWithContract 构建合成 ChannelMessage 委托到 processBrainResponse，消除重复的状态处理逻辑 |
| v2.5 | 2026-06-23 | P2阶段部分完成：P2-1 超时动态调整 ✅、P2-4 executionId传递 ✅、DP2-2 超时线程取消 ✅、DP2-3 plan填充 ✅、DP2-4 READY语义明确 ✅ |
| v2.6 | 2026-06-23 | P2阶段全部完成：NP2-1~4 知识质量评估+晋升自动化+绩效统计+绩效分派 ✅、P2-3 异步恢复 ✅、P2-5 降级统一 ✅、DP2-1 进化策略实现 ✅、DP2-5 部门识别LLM化 ✅ |
| v2.7 | 2026-06-24 | DCS 集成完成：#5/#6/#7 全部完成，DepartmentAggregationService + EmployeeSelfClaimService + DepartmentTodoPool 集成到 DCS；新增 LlmDepartmentAggregationService LLM 增强版聚合服务 |

> **参考文档**：`BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` 提供规范约束索引，本方案提供执行决策流程和代码改进清单，两者互补形成完整的"规范→执行→证据→闭环"体系。
