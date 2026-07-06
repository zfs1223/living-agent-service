# 研发部门数字员工协作优化计划

> 基于 claude-code-collaboration-analysis.md v3.0 分析结论，对 living-agent-service 进行系统性优化
>
> **核心原则：不脱离现有框架，不产生冲突和重复，增量式优化**

---

## 一、现有代码盘点（避免冲突）

### 1.1 已有功能（不可重复实现）

| 功能 | 已有实现 | 包路径 | 状态 |
|------|----------|--------|------|
| 权限检查 | PermissionService + BrainAccessControl | core/security/ | ✅ 完善 |
| 沙箱执行 | SandboxExecutor + DockerSandboxService | core/security/ + core/sandbox/ | ✅ 两套需统一 |
| 审批工作流 | ApprovalService + ApprovalWorkflow | core/approval/ | ✅ 完善 |
| 任务规划 | TaskPlanner + TaskPlan + TaskStep | core/planner/ | ⚠️ 无 DAG 依赖 |
| 协作服务 | CollaborationService + CollaborationSession | core/worker/collaboration/ | ⚠️ 无 Lead-Teammate |
| 事件钩子 | EventHookManager + HookEvent | core/proactive/event/ | ⚠️ 无 PreToolUse/PostToolUse |
| ReAct 循环 | AbstractBrain.executeReActLoop() | core/brain/impl/ | ✅ 已提取 |
| 安全策略 | SecurityPolicy + Rust SecurityValidator | core/security/ + native/security/ | ✅ 完善 |
| 通道系统 | ChannelManager + 4种通道 | core/channel/ | ✅ 完善 |

### 1.2 需解决的冲突点

| 冲突 | 说明 | 处理方式 |
|------|------|---------|
| Sandbox 三套系统 | core/sandbox/ + core/security/ + native/security/ | 统一入口：SandboxExecutor 为主，DockerSandboxService 为容器级补充 |
| ApprovalManager vs ApprovalService | security/ApprovalManager 与 approval/ApprovalService | ApprovalService 为主，ApprovalManager 降级为安全审批专用 |
| Scenario Handler 重复 | proactive/scenario/ vs scenario/ | 合并到 scenario/，proactive/ 通过引用调用 |
| BountyHunterSkill 重复 | autonomous/bounty/ vs skill/bounty/ | 合并到 skill/bounty/，autonomous/ 引用 |
| EmployeeService 重复 | employee/ vs security/ | employee/ 为主，security/ 引用 |
| 固定数字员工专业知识 | 25个数字员工无专门知识储存进化机制 | 新增 Phase 5 |

---

## 二、优化阶段规划

### 阶段总览

```
Phase 1 (P0): 核心能力补齐 ─── 上下文压缩 + 任务DAG + ReAct提取 ✅
Phase 2 (P0): 协作模式引入 ─── Lead-Teammate + 计划审批 + 工作树隔离 ✅
Phase 3 (P1): 安全与效率 ─── 自动认领 + Bash安全 + MCP帧编码 + 沙箱统一 ✅
Phase 4 (P2): 体验优化 ─── Hook系统 + 指令文件链 + Usage追踪 ✅
Phase 5 (P0): 专业知识增强 ─── 知识注入 + 动态提示词 + 专业知识导入 + 进化执行 ✅
```

---

## 三、Phase 1：核心能力补齐

### 3.1 上下文压缩（参考 Claw compact.rs）

**问题**：TechBrain 的 ReAct 循环无上下文压缩，长对话必然溢出 token 限制

**方案**：当前以 Java 侧压缩链路为主实现（`ContextCompactor`），Rust+JNI 压缩作为后续增强项。

**当前已落地文件**：
- `living-agent-core/.../brain/compact/ContextCompactor.java` - 压缩接口
- `living-agent-core/.../brain/compact/CompactionResult.java` - 压缩结果
- `living-agent-core/.../brain/compact/impl/RuleBasedContextCompactor.java` - 规则压缩实现

**后续增强（可选）**：
- `living-agent-native/src/compact/mod.rs`
- `living-agent-native/src/compact/summarizer.rs`
- `living-agent-native/src/compact/merger.rs`
- `living-agent-native/src/jni/compact_jni.rs`

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java` - 在 ReAct 循环中集成压缩
- `living-agent-core/.../brain/impl/TechBrain.java` - 使用 AbstractBrain 的压缩能力
- `living-agent-core/.../config/LivingAgentCoreConfig.java` - 添加压缩配置
- `living-agent-app/.../resources/application.yml` - 添加 compact 配置段

**压缩策略**：
```
L1 大输出持久化 - 工具输出 > 30000 字符时持久化到磁盘
L2 微压缩 - 每轮保留最近 N 个 tool_result，旧结果替换为占位符
L3a 规则压缩 - 纯 Rust 实现，零成本，提取6项关键信息 + timeline
L3b LLM 压缩 - 累积多次规则压缩后，用 LLM 深度整合（可选）
```

**不冲突**：现有无任何压缩代码，纯新增

### 3.2 任务依赖图（参考 LCC s12）

**问题**：现有 TaskPlanner 只有 TaskStep 列表，无 DAG 依赖关系

**方案**：扩展现有 TaskPlanner，新增 TaskDagService

**新增文件**：
- `living-agent-core/.../planner/dag/TaskDagService.java` - DAG 依赖管理接口
- `living-agent-core/.../planner/dag/impl/InMemoryTaskDagService.java` - 内存实现
- `living-agent-core/.../planner/dag/DagTask.java` - DAG 任务节点（含 blockedBy/blocks）
- `living-agent-core/.../planner/dag/DagTaskStatus.java` - PENDING/IN_PROGRESS/COMPLETED/FAILED

**修改文件**：
- `living-agent-core/.../planner/TaskPlanner.java` - 添加 createDagPlan() 方法
- `living-agent-core/.../planner/TaskPlan.java` - 添加 dagTasks 字段
- `living-agent-core/.../brain/impl/TechBrain.java` - 添加 task_create/task_update/task_list 工具
- `living-agent-core/.../config/LivingAgentCoreConfig.java` - 添加 TaskDagService bean

**DAG 任务模型**：
```java
public record DagTask(
    String id,
    String subject,
    String description,
    DagTaskStatus status,
    List<String> blockedBy,
    List<String> blocks,
    String assignee,
    String worktree,
    Instant createdAt,
    Instant updatedAt
) {}
```

**不冲突**：TaskPlanner 保持不变，DagService 是独立的新服务

### 3.3 ReAct 循环提取到 AbstractBrain

**问题**：TechBrain 有 ReAct 循环，MainBrain 没有，其他 Brain 需要重复实现

**方案**：将 ReAct 循环提取到 AbstractBrain

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java` - 添加 executeReActLoop() 模板方法
- `living-agent-core/.../brain/impl/TechBrain.java` - 改用 AbstractBrain.executeReActLoop()
- `living-agent-core/.../brain/impl/HrBrain.java` - 改用 AbstractBrain.executeReActLoop()

**不冲突**：TechBrain 的现有逻辑不变，只是移动到父类

---

## 四、Phase 2：协作模式引入

### 4.1 Lead-Teammate 协作模式（参考 LCC s15/s16）

**问题**：TechBrain 单体处理所有技术任务，无法并行

**方案**：TechBrain 作为 Lead，数字员工作为 Teammate，通过现有 Channel 系统通信

**新增文件**：
- `living-agent-core/.../brain/collaboration/LeadOrchestrator.java` - Lead 编排器接口
- `living-agent-core/.../brain/collaboration/TechLeadOrchestrator.java` - TechBrain 专用 Lead
- `living-agent-core/.../brain/collaboration/TeammateRole.java` - Teammate 角色定义
- `living-agent-core/.../brain/collaboration/TeammateAssignment.java` - 任务分配记录

**修改文件**：
- `living-agent-core/.../brain/impl/TechBrain.java` - 集成 TechLeadOrchestrator
- `living-agent-core/.../employee/neuron/EmployeeNeuron.java` - 添加 Teammate 模式支持
- `living-agent-core/.../channel/impl/ChannelManagerImpl.java` - 支持动态创建协作通道

**协作流程**：
```
TechBrain (Lead)
  ├── T01 代码审查员 (Teammate) ──── channel://tech/code-review
  ├── T02 架构师 (Teammate)       ──── channel://tech/architecture
  ├── T09 前端工程师 (Teammate)   ──── channel://tech/frontend
  └── T10 后端工程师 (Teammate)   ──── channel://tech/backend
```

**关键设计**：复用现有 Channel 系统（BroadcastChannel/UnicastChannel），不引入新的 MessageBus

**不冲突**：Lead-Teammate 是 TechBrain 的新能力，不改变现有通道系统

### 4.2 计划审批协议（参考 LCC s16）

**问题**：数字员工执行重大操作前无审批机制

**方案**：扩展现有 ApprovalService，添加 plan_approval 协议

**新增文件**：
- `living-agent-core/.../approval/plan/PlanApprovalService.java` - 计划审批接口
- `living-agent-core/.../approval/plan/PlanApprovalRequest.java` - 审批请求
- `living-agent-core/.../approval/plan/PlanApprovalResponse.java` - 审批响应
- `living-agent-core/.../approval/plan/impl/InMemoryPlanApprovalService.java` - 内存实现

**修改文件**：
- `living-agent-core/.../brain/collaboration/TechLeadOrchestrator.java` - 集成计划审批
- `living-agent-core/.../brain/impl/TechBrain.java` - 添加 plan_submit/plan_approve 工具

**审批流程**：
```
Teammate → plan_submit(plan) → Lead
Lead → plan_approve(requestId, approved, feedback) → Teammate
超时 → 自动通过（可配置）
```

**不冲突**：PlanApprovalService 是独立服务，与现有 ApprovalService 并行，不修改 ApprovalService

### 4.3 工作树隔离（参考 LCC s18）

**问题**：多个数字员工同时修改代码时可能冲突

**方案**：新增 WorktreeManager，管理 Git Worktree 隔离

**新增文件**：
- `living-agent-core/.../tool/worktree/WorktreeManager.java` - 工作树管理接口
- `living-agent-core/.../tool/worktree/WorktreeEntry.java` - 工作树记录
- `living-agent-core/.../tool/worktree/impl/GitWorktreeManager.java` - Git 实现

**修改文件**：
- `living-agent-core/.../brain/impl/TechBrain.java` - 添加 worktree_create/worktree_closeout 工具
- `living-agent-core/.../planner/dag/DagTask.java` - 添加 worktree 字段绑定
- `living-agent-core/.../config/LivingAgentCoreConfig.java` - 添加 WorktreeManager bean

**不冲突**：WorktreeManager 是独立的工具服务，不修改现有 Git 工具

---

## 五、Phase 3：安全与效率

### 5.1 自动认领机制（参考 LCC s17）

**问题**：数字员工空闲时无法自动认领待处理任务

**方案**：EmployeeNeuron 增加 idle → scan 循环

**新增文件**：
- `living-agent-core/.../employee/claim/TaskClaimService.java` - 自动认领服务

**修改文件**：
- `living-agent-core/.../employee/neuron/EmployeeNeuron.java` - 添加 idle 状态和 scan 逻辑
- `living-agent-core/.../planner/dag/TaskDagService.java` - 添加 getUnclaimedTasks() 方法

**不冲突**：idle → scan 是 EmployeeNeuron 的新行为，不影响现有心跳机制

### 5.2 Bash 安全验证（参考 LCC s07）

**问题**：数字员工执行 Bash 命令时无安全验证

**方案**：在 Rust native 模块实现 Bash 安全验证器

**新增文件**：
- `living-agent-native/src/security/bash_validator.rs` - Bash 安全验证（5个检查器）
- `living-agent-core/.../security/bash/BashSecurityValidator.java` - Java 包装
- `living-agent-core/.../security/bash/BashValidationResult.java` - 验证结果

**修改文件**：
- `living-agent-core/.../security/impl/SandboxExecutorImpl.java` - 在统一执行入口集成 Bash 安全验证（`executeScript/executeCommand`）

**5 个验证器**：
1. shell_metachar - 检测 `;` `&` `|` `` ` `` `$`
2. sudo - 检测提权命令
3. rm_rf - 检测递归删除
4. cmd_substitution - 检测 `$(...)`
5. ifs_injection - 检测 IFS 操纵

**不冲突**：在现有 SecurityPolicy/Rust SecurityValidator 基础上扩展，不替换

### 5.3 MCP 帧编码升级（参考 Claw mcp_stdio.rs）

**问题**：MemPalaceBackend 使用换行分隔 JSON-RPC，不够可靠

**方案**：升级为 Content-Length 帧编码

**修改文件**：
- `living-agent-core/.../memory/impl/MemPalaceBackend.java` - 升级帧编码

**不冲突**：仅修改 MemPalaceBackend 内部通信协议

### 5.4 沙箱系统统一

**问题**：三套 Sandbox 系统并存

**方案**：统一入口

**修改文件**：
- `living-agent-core/.../security/SandboxExecutor.java` - 添加 Docker 沙箱委托
- `living-agent-core/.../sandbox/SandboxService.java` - 标记为 @Deprecated，委托给 SandboxExecutor
- `living-agent-core/.../config/LivingAgentCoreConfig.java` - 统一 SandboxExecutor bean

**不冲突**：SandboxService 保留但标记废弃，逐步迁移

---

## 六、Phase 4：体验优化

### 6.1 Hook 系统（参考 Claw hooks.rs）

**问题**：工具执行无拦截机制，无法在执行前后进行安全检查或日志记录

**方案**：新增 ToolHookManager，支持 PreToolUse/PostToolUse/ErrorHook/TimeoutHook 四种钩子

**新增文件**：
- `living-agent-core/.../tool/hook/ToolHookManager.java` - 钩子管理器（支持 PreToolHook/PostToolHook/ToolErrorHook/ToolTimeoutHook）
- `living-agent-core/.../tool/hook/ToolHookResult.java` - 钩子结果（ALLOW/DENY/WARN + exitCode）

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java` - executeToolCalls 中集成 Hook

**退出码语义**（参考 Claw）：
- 0 = allow
- 2 = deny
- 其他 = warn（继续执行）

**钩子类型**：
```
PreToolHook   - 工具执行前调用，可拦截/警告
PostToolHook  - 工具执行后调用，可检查结果
ToolErrorHook - 工具执行异常时调用
ToolTimeoutHook - 工具超时时调用
```

### 6.2 指令文件链（参考 Claw prompt.rs）

**问题**：无法通过文件系统为特定员工提供自定义指令

**方案**：新增 InstructionFileLoader，支持从 `.living/{employee-id}/instructions.md` 加载指令，支持继承链

**新增文件**：
- `living-agent-core/.../brain/prompt/InstructionFileLoader.java` - 指令文件发现、加载、继承链合并

**修改文件**：
- `living-agent-core/.../brain/BrainContext.java` - 添加 instructionFileLoader 和 employeeId
- `living-agent-core/.../brain/impl/AbstractBrain.java` - getSystemPrompt 中集成指令链

**发现链**：
```
.global/instructions.md
.tech/instructions.md
.tech/code-reviewer/instructions.md
```

### 6.3 Usage 追踪（参考 Claw usage.rs）

**问题**：无法追踪 Token 消耗和计算成本

**方案**：新增 UsageTracker，记录每次 LLM 调用的 Token 消耗

**新增文件**：
- `living-agent-core/.../model/UsageTracker.java` - Token 消耗追踪（按 sessionId/brainId 统计）
- `living-agent-core/.../model/TokenUsage.java` - 单次使用量记录

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java` - ReAct 循环中集成 Usage 记录

**功能**：
- 按 brainId/sessionId 统计 Token 消耗
- 支持成本报告（COST_PER_1K_PROMPT/COMST_PER_1K_COMPLETION）
- 按 model/operationType 分类统计

---

## 七、Phase 5：专业知识增强

> **背景**：living-agent-service 现有 25 个固定数字员工（9个部门），每个员工有 BrainPersonality、capabilities、tools、channels，但缺乏专门的**专业领域知识储存和进化机制**。
>
> **目标**：让固定数字员工能够动态获取和进化专业知识，参考 agency-agents-main 的 144 个 agent 专业经验。

**治理原则（新增）**：
- 固定数字员工在规则框架内可持续成长（知识、策略、经验不设硬上限）。
- 成长与授权分离：能力可进化，但工具调用必须遵循“员工清单 + 部门白名单 + 共享工具”边界。
- 严禁以“成长”为名突破部门与岗位工具权限。

### 7.1 知识注入到神经元（P0）

**问题**：EmployeeNeuron 的 BrainContext 不包含 KnowledgeBase 和 EvolutionEngine，无法进行 RAG 增强

**方案**：修改 EmployeeNeuron，在创建 BrainContext 时注入 KnowledgeBase 和 EvolutionEngine

**修改文件**：
- `living-agent-core/.../employee/neuron/EmployeeNeuron.java` - 添加 setKnowledgeBase() 和 setEvolutionEngine()

```java
private BrainContext createBrainContext(NeuronContext neuronContext) {
    BrainContext.Builder builder = BrainContext.builder()
        .brainId(delegateBrain != null ? delegateBrain.getId() : id)
        .department(delegateBrain != null ? delegateBrain.getDepartment() : employee.getDepartmentId())
        .sessionId(neuronContext.getSessionId())
        .channelManager(neuronContext.getChannelManager())
        .skillRegistry(neuronContext.getSkillRegistry());

    if (knowledgeBase != null) {
        builder.knowledgeBase(knowledgeBase);
    }
    if (evolutionEngine != null) {
        builder.evolutionEngine(evolutionEngine);
    }
    return builder.build();
}
```

### 7.2 工具加载与授权治理（P0）

**问题**：仅“能加载工具”不足以保证安全协作。若缺少部门与岗位边界，固定数字员工会出现跨部门越权调用。

**方案**：在 `FixedEmployeeRegistry` 落地“三层授权 + 启动校验 + 严格模式”。

**修改文件**：
- `living-agent-core/.../employee/registry/FixedEmployeeRegistry.java`

**治理规则（当前实现）**：
1. 员工清单约束：只加载该员工 `tools` 明确声明的工具。
2. 部门白名单：工具必须在 `DEPARTMENT_TOOL_ALLOWLIST` 允许范围。
3. 共享工具集合：公共工具由 `SHARED_TOOL_NAMES` 管理，支持跨部门复用。
4. 别名兼容：`TOOL_ALIAS` 支持历史 `xxx_tool` 到真实工具名的迁移。
5. 启动一致性校验：`validateConfiguredTools()` 检查未注册工具与越权配置。
6. 严格模式：`living-agent.fixed-employee.tools.strict=true` 时，违规直接 fail-fast。

```java
private List<Tool> loadToolsForEmployee(DigitalEmployee employee) {
    if (toolRegistry == null) {
        return List.of();
    }

    String department = employee.getDepartmentId() == null || employee.getDepartmentId().isBlank()
        ? employee.getDepartment()
        : employee.getDepartmentId();

    Set<String> departmentAllowed = DEPARTMENT_TOOL_ALLOWLIST.getOrDefault(department, Set.of());
    Set<String> resolvedNames = new LinkedHashSet<>();

    for (String configured : employee.getTools()) {
        String normalized = TOOL_ALIAS.getOrDefault(configured, configured);
        boolean allowed = SHARED_TOOL_NAMES.contains(normalized) || departmentAllowed.contains(normalized);
        if (allowed) {
            resolvedNames.add(normalized);
        }
    }

    List<Tool> loaded = new ArrayList<>();
    for (String toolName : resolvedNames) {
        toolRegistry.get(toolName).ifPresent(loaded::add);
    }
    return loaded;
}
```

**关键约束**：`claude_cli` 属于技术部受控工具，仅可配置给技术部指定数字员工（当前示例为 `T03`）。

### 7.3 动态系统提示词（P1）

**问题**：大脑系统提示词是静态的，无法注入角色人格、技能、知识等动态信息

**方案**：新增 DynamicPromptBuilder，动态构建包含 personality、skills、knowledge 的系统提示词

**新增文件**：
- `living-agent-core/.../brain/prompt/DynamicPromptBuilder.java` - 动态提示词构建器

**修改文件**：
- `living-agent-core/.../brain/impl/AbstractBrain.java` - getSystemPrompt() 改为动态构建

```java
protected String getSystemPrompt() {
    String base = doGetSystemPrompt();
    DynamicPromptBuilder builder = new DynamicPromptBuilder()
        .basePrompt(base)
        .personality(personality);

    if (context != null) {
        builder.skills(context.getSkillRegistry(), name);
        if (context.getKnowledgeBase() != null) {
            builder.knowledge(context.getKnowledgeBase(), department, 5);
        }
    }
    return builder.build();
}
```

### 7.4 专业知识导入（P1）

**问题**：agency-agents-main 有大量专业经验（144个agent），但无法被 LAS 使用

**方案**：新增 ProfessionalKnowledgeSeeder，从 agency-agents markdown 文件导入专业知识

**新增文件**：
- `living-agent-core/.../knowledge/professional/ProfessionalKnowledgeSeeder.java` - 专业知识导入器

**功能**：
- 解析 YAML frontmatter（department, tags, version）
- 映射 agency departments 到 LAS departments
- 存储为 L2_DEPARTMENT/HIGH importance
- 支持增量更新

### 7.5 进化决策执行（P1）

**问题**：EvolutionExecutor 有执行逻辑，但 repair/optimize/innovate 未实际从知识库获取专业知识

**方案**：扩展 EvolutionExecutor，增强 repair/optimize/innovate 从知识库获取专业知识和最佳实践

**修改文件**：
- `living-agent-core/.../evolution/executor/EvolutionExecutor.java` - 添加知识库集成

**执行策略**：
```
SKIP    - 跳过，不处理
REPAIR  - 修复知识缺口，从 L3_SHARED/L2_DEPARTMENT 获取专业知识，增强技能内容
OPTIMIZE- 优化现有知识，获取最佳实践，调整权重
INNOVATE- 生成新技能，注入专业知识，触发 SkillGenerator
DEFER   - 延迟处理，加入待处理队列
ESCALATE- 上报，触发 MainBrain 介入
```

**增强功能**：
- `fetchProfessionalKnowledge()` - 从 L3_SHARED 和 L2_DEPARTMENT 获取相关专业知识
- `fetchBestPractices()` - 从知识库获取最佳实践
- `enhanceSkillWithKnowledge()` - 将专业知识注入技能内容
- `storeEvolutionKnowledge()` - 将进化结果存储回知识库

---

## 八、实施顺序与依赖关系

```
Phase 1.1 ReAct提取 ──────┐
Phase 1.2 上下文压缩 ─────┤── 无依赖，可并行
Phase 1.3 任务DAG ────────┘
         │
         ▼
Phase 2.1 Lead-Teammate ── 依赖: 任务DAG + ReAct提取
Phase 2.2 计划审批 ──────── 依赖: Lead-Teammate
Phase 2.3 工作树隔离 ────── 依赖: 任务DAG
         │
         ▼
Phase 3.1 自动认领 ──────── 依赖: 任务DAG + Lead-Teammate
Phase 3.2 Bash安全 ──────── 无依赖
Phase 3.3 MCP帧编码 ────── 无依赖
Phase 3.4 沙箱统一 ──────── 无依赖
         │
         ▼
Phase 4.1 Hook系统 ──────── 依赖: ReAct提取
Phase 4.2 指令文件链 ────── 无依赖
Phase 4.3 Usage追踪 ─────── 依赖: ReAct提取
         │
         ▼
Phase 5.1 知识注入 ─────── 依赖: KnowledgeBase + EvolutionEngine
Phase 5.2 工具加载 ──────── 依赖: ToolRegistry
Phase 5.3 动态提示词 ────── 依赖: 知识注入
Phase 5.4 专业知识导入 ──── 无依赖
Phase 5.5 进化执行 ──────── 依赖: 知识注入 + 动态提示词
```

---

## 九、风险控制

| 风险 | 应对 |
|------|------|
| ReAct 提取影响 TechBrain | 先写测试，确保 TechBrain 行为不变 |
| DAG 服务与 TaskPlanner 冲突 | DagService 独立接口，TaskPlanner 保持不变 |
| Lead-Teammate 增加资源消耗 | 限制最大并发 Teammate 数量 |
| 工作树 Git 操作出错 | 充分测试，添加回滚机制 |
| 沙箱统一影响现有功能 | 废弃标记 + 逐步迁移，不立即删除 |
| 压缩丢失关键上下文 | 保留最近 N 条消息不压缩，压缩结果可审查 |
| 知识注入影响大脑行为 | 通过 DynamicPromptBuilder 动态注入，不改变大脑核心逻辑 |
| agency-agents 知识格式不兼容 | 通过 YAML frontmatter 标准化，映射到 L2_DEPARTMENT |

---

## 十、知识流架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     agency-agents-main                          │
│  (144 agents: review-checklist, tech-templates, decision-frames) │
└─────────────────────┬───────────────────────────────────────────┘
                      │ ProfessionalKnowledgeSeeder
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                KnowledgeBase (L2_DEPARTMENT)                    │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │  Tech   │  │   HR    │  │Finance  │  │  Sales  │   ...      │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘            │
└───────┼────────────┼────────────┼────────────┼─────────────────┘
        │            │            │            │
        ▼            ▼            ▼            ▼
┌─────────────────────────────────────────────────────────────────┐
│              DynamicPromptBuilder                               │
│  - personality: BrainPersonality                                │
│  - skills: SkillRegistry.lookup()                               │
│  - knowledge: KnowledgeBase.search()                            │
│  - tools: ToolRegistry.getAll()                                │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│              AbstractBrain (ReAct Loop)                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Thought → Action → Observation → ... (压缩)            │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│              LLM (Qwen3.5-27B / Qwen3.5-2B)                     │
└─────────────────────────────────────────────────────────────────┘
```

---

**文档版本**: v1.5
**创建日期**: 2026-04-09
**最近校对**: 2026-04-14
**基于**: claude-code-collaboration-analysis.md v3.0
**状态**: 已实施（持续收敛中）。实现以代码为准，文档作为治理与核对基线；Rust+JNI 压缩链路当前为 backlog。

---

## 十一、实施进度（按代码核对）

> 说明：本节由代码核对后更新，状态分为：✅ 已完成 / 🟡 部分完成 / ❌ 未完成。

### Phase 1：核心能力补齐（核对结果）

| 优化项 | 核对结果 | 说明 |
|--------|---------|------|
| ReAct 循环提取 | ✅ | `AbstractBrain.executeReActLoop()` 已存在，`TechBrain/HrBrain` 已调用 |
| 上下文压缩 | 🟡 | Java 压缩链路已实现；Rust+JNI 压缩基线已落地（`living-agent-native/src/compact/mod.rs` + `jni/compact_jni.rs` + `CompactNative`），当前为可开关接入，默认关闭 |
| 任务依赖图 | ✅ | `TaskDagService/DagTask/DagTaskStatus/InMemoryTaskDagService` 已存在 |

### Phase 2：协作模式引入（核对结果）

| 优化项 | 核对结果 | 说明 |
|--------|---------|------|
| Lead-Teammate | ✅ | `TechBrain` 已显式接入 `LeadOrchestrator`，协作控制消息可回传完成/失败 |
| 计划审批 | ✅ | `approval/plan/*` 服务类已存在 |
| 工作树隔离 | ✅ | `WorktreeManager/WorktreeEntry/GitWorktreeManager` 已存在 |

### Phase 3：安全与效率（核对结果）

| 优化项 | 核对结果 | 说明 |
|--------|---------|------|
| Bash 安全验证 | ✅ | `SandboxExecutorImpl` 已在 `executeScript/executeCommand` 前接入 `BashSecurityValidator`，对高危命令进行阻断 |
| 自动认领 | ✅ | `TaskClaimService` 已接入 `EmployeeNeuron` 的 idle→scan 周期调度 |
| MCP 帧编码升级 | ✅ | `MemPalaceBackend` 已支持 `Content-Length` 帧编码，并保留兼容模式 |
| 沙箱系统统一 | 🟡 | 已形成 `SandboxExecutor + HybridSandboxService` 统一入口与兼容层；仍有历史调用待迁移 |

### Phase 4：体验优化（核对结果）

| 优化项 | 核对结果 | 说明 |
|--------|---------|------|
| Hook 系统 | ✅ | `ToolHookManager/ToolHookResult` 已存在，已接入 `AbstractBrain.executeToolCalls` |
| 指令文件链 | ✅ | `InstructionFileLoader` 已实现并接入系统提示词构建 |
| Usage 追踪 | ✅ | `UsageTracker/TokenUsage` 已在 ReAct 调用后记录 |

### Phase 5：专业知识增强（核对结果）

| 优化项 | 核对结果 | 说明 |
|--------|---------|------|
| 知识注入 | ✅ | `EmployeeNeuron` 已支持注入 `KnowledgeBase/EvolutionEngine` |
| 工具加载 | ✅ | `FixedEmployeeRegistry.loadToolsForEmployee()` 已实现 |
| 动态提示词 | ✅ | `DynamicPromptBuilder` 已接入 `AbstractBrain.getSystemPrompt()` |
| 专业知识导入 | ✅ | `ProfessionalKnowledgeSeeder` 已存在 |
| 进化决策执行 | ✅ | `EvolutionExecutor` 已增强专业知识与最佳实践获取 |

---

## 十二、待补清单（与原计划对齐）

### P0（优先）

1. **MCP Content-Length 帧编码升级**（替换 newline 分隔）✅ 已完成
   - 目标文件：`memory/impl/MemPalaceBackend.java`
   - 完成内容：读写双向支持 `Content-Length` 帧；保留旧行模式兼容
2. **Lead-Teammate 运行链路打通**（在 `TechBrain` 显式接入 orchestrator）✅ 已完成
   - 完成内容：`TechBrain` 显式注入 `LeadOrchestrator`，并处理协作控制消息（完成/失败回传）
3. **自动认领闭环补齐**（`EmployeeNeuron` 增加 idle→scan 调度逻辑）✅ 已完成
   - 完成内容：新增后台扫描器，按 idle 阈值 + 冷却窗口执行 `scanAndClaim`

### P1（次优先）

4. **确认 BashTool 执行前验证链路** ✅ 已完成（通过统一入口替代）
   - 完成内容：未发现稳定 `BashTool` 调用面，已在 `SandboxExecutorImpl` 统一执行入口补齐验证，覆盖 `executeScript/executeCommand`
5. **沙箱统一迁移路线图** 🟡 进行中
   - 当前状态：`SandboxService` 已标记 `@Deprecated(since="1.4")`，并由 `HybridSandboxService` 作为兼容层
   - 待完成：按调用点逐步将依赖从 `SandboxService` 迁移至 `SandboxExecutor`

### P2（增强）

6. **Rust+JNI 压缩链路生产化收敛**（已完成基线实现，待压测与默认策略评估）
   - 当前状态：`compact/mod.rs` + `jni/compact_jni.rs` + Java `CompactNative` 已接入；`living-agent.brain.compact.native-enabled` 默认 `false`
   - 待完成：压测、稳定性观察、灰度放量与默认值评估

---

## 十三、符合核心架构检查（核对版）

| 检查项 | 核对结论 | 备注 |
|--------|---------|------|
| ID 命名规范 | ✅ | 主体符合 `employee://` / `neuron://` / `channel://` |
| 三层 LLM 架构 | ✅ | 文档与配置层基本一致 |
| 权限隔离规则 | ✅ | 核心权限模型存在 |
| WebSocket 路径 | ✅ | 网关路径设计完整 |
| API 响应格式 | 🟡 | 需按控制器层统一性进一步抽查 |
| Java vs Rust 分层 | 🟡 | 分层存在，但“Rust压缩”未按计划落地 |
| 知识库分层 | ✅ | L1/L2/L3 结构与实现存在 |
| 技能系统 | ✅ | SkillRegistry + 进化增强链路存在 |

---

## 十四、说明

本文件是“计划 + 落地核对”的联合文档。后续如代码有新增，请优先更新本文件第十一至十三节，确保状态与实现保持一致。

### 14.1 固定数字员工工具授权矩阵（治理基线）

> 说明：下表为当前治理基线，实际以 `FixedEmployeeRegistry` 中的员工配置 + 授权校验为准。

**共享工具（跨部门可复用）**：
- `playwright_crawler`
- `rss_reader`
- `proactive_agent`
- `find_skills`
- `searxng`
- `weather`
- `summarize`
- `pdf`
- `office`
- `notion`
- `slack`

**部门授权基线（示意）**：
- tech: `browser_automation`, `docker`, `github`, `gitlab`, `jenkins`, `huggingface`, `trae`, `claude_cli`, `knowledge_graph`, `self_improving`, `jira`
- finance: `budget_management`, `invoice_processing`, `summarize`, `browser_automation`
- ops: `summarize`, `proactive_agent`, `notion`
- sales: `github`, `browser_automation`, `notion`, `slack`, `summarize`
- hr: `notion`, `slack`, `summarize`
- cs: `notion`, `slack`, `jira`
- admin: `notion`, `office`, `slack`, `summarize`
- legal: `office`, `summarize`
- main: `slack`, `proactive_agent`, `summarize`

**关键约束**：
- `claude_cli` 为技术部受控工具，仅允许配置给技术部指定数字员工（当前示例：`T03`）。
- 固定数字员工可持续成长（知识/策略/经验），但工具调用必须始终遵循授权边界。

### 14.2 严格模式配置建议

- 配置项：`living-agent.fixed-employee.tools.strict`
- 建议值：
  - 开发环境：`false`（告警为主，便于迁移）
  - 生产环境：`true`（违规即 fail-fast）

### 14.3 本地部署与 Native 构建说明（新增）

> 本项目为本地部署形态，镜像与 so 产物位于：`docker/living-agent-service/image`。

- Rust+JNI 压缩相关开关：`living-agent.brain.compact.native-enabled`
  - 开发默认：`false`
  - 灰度/生产评估后可开启：`true`
- Native so 重建方式：`docker/living-agent-service/image/build_rust_native.bat`
- 建议流程：
  1. 先以 `native-enabled=false` 回归 Java 路径（功能正确性）
  2. 打开 `native-enabled=true` 进行本地灰度验证（稳定性/性能）
  3. 通过后再执行 `build_rust_native.bat` 固化 so 版本并更新镜像

---

## 十五、SandboxService → SandboxExecutor 迁移清单（2026-04-13）

> 目标：保留兼容层、逐步迁移调用，最终让 `SandboxExecutor` 成为唯一统一执行入口。

### 15.1 当前状态（已完成）

1. `SandboxService` 已标记为 `@Deprecated(since="1.4", forRemoval=false)`，作为兼容接口保留。
2. `LivingAgentCoreConfig` 中由 `HybridSandboxService` 作为兼容实现，内部组合本地 `SandboxExecutor` 与 Docker 后端。
3. `SandboxExecutorImpl` 已接入 Bash 安全验证（脚本/命令双入口）。

### 15.2 现存调用面（需迁移）

1. `TraeTool`（`core/tool/impl/TraeTool.java`）
   - 当前直接依赖 `SandboxService` 与 `SandboxSession`。
   - 风险：新增功能若继续沿用该路径，会绕开统一执行抽象演进。

### 15.3 迁移步骤（建议顺序）

1. **新增适配层（P1）**
   - 新建 `TraeExecutionGateway`（或同类命名）
   - 对外暴露 Trae 所需动作接口，内部优先走 `SandboxExecutor`，会话类需求按需透传 Docker 能力。

2. **工具层改造（P1）** ✅ 已完成
   - `TraeTool` 构造参数已从 `SandboxService` 切换为 `TraeExecutionGateway`。
   - 已删除工具层对 `SandboxSession` 细节的直接感知。

3. **配置层收口（P1）** ✅ 已完成
   - `LivingAgentCoreConfig` 已显式装配 `TraeExecutionGateway`。
   - `TraeTool` 已通过网关注册到 `ToolRegistry`；`SandboxService` 继续保留为兼容层。

4. **兼容观察期（P2）**
   - 增加日志埋点：统计 `SandboxService` 调用来源与频次。
   - 连续两个版本无新增依赖后，评估 `forRemoval=true` 时间点。

### 15.4 验收标准

1. 业务代码中不再新增 `SandboxService` 注入点。
2. Bash 类执行路径全部经由 `SandboxExecutorImpl` 校验。
3. `TraeTool` 迁移后功能行为与现网一致（init/generate/review/test/refactor/debug）。

### 15.5 Claude CLI 工具接入（2026-04-13）

1. **新增工具**：`ClaudeCliTool`（`core/tool/impl/ClaudeCliTool.java`）
   - 参数语义参考 `free-claude-code-main`：`prompt/resume_session_id/fork_session/output_format/verbose`。
   - 默认对齐其常见调用参数：`--output-format stream-json` + `--dangerously-skip-permissions`。

2. **新增执行网关**：`ClaudeExecutionGateway`（`core/sandbox/ClaudeExecutionGateway.java`）
   - 独立管理 Claude 会话与参数映射，不再复用 `TraeExecutionGateway`。
   - 当前通过 `SandboxSession.executeCommand("claude", args)` 执行命令。

3. **配置接线**：
   - `LivingAgentCoreConfig` 新增 `ClaudeExecutionGateway` Bean。
   - `toolRegistry(...)` 注入 `tool.claude-cli.enabled` 开关，true 时注册 `claude_cli`。

4. **后续演进建议**：
   - 下一阶段将 Claude CLI 执行接入 `SandboxExecutor` 统一审计/超时/白名单策略。
   - 评估引入“长驻进程 + 流式事件桥接”模型，进一步贴近 free-claude-code-main 的实时会话体验。

5. **V2（当前已落地）准流式能力**：
   - `ClaudeExecutionGateway` 对 `stream-json` 输出做逐行事件提取（`stream_events` / `stream_event_count`）。
   - 自动从事件中抽取 `session_id/sessionId`，并写入会话状态缓存。
   - `ClaudeCliTool` 返回结构新增 `events/event_count/parsed_session_id/raw_metrics`。
   - `action=status` 支持返回当前会话快照（最近动作、退出码、事件数、解析出的 Claude 会话 ID）。

6. **V3（当前已落地）异步任务模型**：
   - `ClaudeExecutionGateway` 新增异步任务池：`startAsyncJob/pollAsyncJob/cancelAsyncJob`。
   - `ClaudeCliTool` 新增动作：
     - `start`：提交后台执行任务，返回 `job_id`
     - `poll`：查询任务状态与结果（running/completed/failed）
     - `cancel`：取消后台任务
   - 现阶段实现为“网关后台 Future + 轮询式回收”，便于后续平滑升级到长驻进程与实时推送。
