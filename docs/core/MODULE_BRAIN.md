# 大脑模块

> 版本：2026-05-18 | 路径：living-agent-core/brain/

## 核心接口

```java
public interface Brain {
    String getId();
    String getName();
    String getDepartment();
    BrainState getState();

    void start(BrainContext context);
    void stop();
    void process(ChannelMessage message);

    List<Tool> getTools();
    List<String> getSubscribedChannels();
    List<String> getPublishChannels();

    enum BrainState { INITIALIZING, RUNNING, PAUSED, STOPPED, ERROR }
}
```

> **注意**：`BrainState` 是 `Brain` 接口的内部枚举，不存在独立 `BrainState.java` 文件。

## 大脑列表

| ID | 类 | 部门 | 技能数 | 订阅通道 |
|----|-----|------|--------|----------|
| `neuron://core/main-brain/001` | `MainBrain` | core | - | `cross_department` |
| `neuron://tech/tech-brain/001` | `TechBrain` | tech | 25 | `tech` |
| `neuron://hr/hr-brain/001` | `HrBrain` | hr | 3 | `hr` |
| `neuron://finance/finance-brain/001` | `FinanceBrain` | finance | 4 | `finance` |
| `neuron://sales/sales-brain/001` | `SalesBrain` | sales | 4 | `sales` |
| `neuron://cs/cs-brain/001` | `CsBrain` | cs | 3 | `cs` |
| `neuron://admin/admin-brain/001` | `AdminBrain` | admin | 15 | `admin` |
| `neuron://legal/legal-brain/001` | `LegalBrain` | legal | 3 | `legal` |
| `neuron://ops/ops-brain/001` | `OpsBrain` | ops | 9 | `ops` |

## AbstractBrain 核心逻辑

```java
process(ChannelMessage message) {
    1. doProcess(message)           // 子类实现
}

// AbstractBrain 内部流程：
1. validateState()                  // 状态检查
2. contextCompactor.compact()       // 上下文压缩（超长对话截断）
3. buildPrompt()                    // 构建提示词
4. toolHookManager.beforeExecute()  // 工具钩子（前置）
5. brainModelSelector.select()      // 模型选择
6. executeReActLoop()               // ReAct 执行循环
7. toolHookManager.afterExecute()   // 工具钩子（后置）
8. publishResponse()                // 发布响应

executeReActLoop() {
    for (i = 0; i < maxIterations; i++) {
        1. callLlm(thought)         // LLM 思考
        2. if (hasToolCall) {
            executeTool()            // 执行工具
        } else {
            return result            // 直接返回
        }
    }
    return maxIterations_exceeded
}
```

## MainBrain 特殊逻辑

```java
handleCrossDepartmentRequest() {
    1. 解析消息中的涉及部门
    2. 创建 CoordinationSession
    3. 转发到各相关部门
    4. 收集部门响应
    5. 整合结果返回
}
```

## BrainContext 依赖注入

```java
// BrainContext.java — class + Builder 模式（非 record）
public class BrainContext {
    String brainId;                       // 大脑 ID
    String department;                    // 所属部门
    String sessionId;                     // 会话 ID
    Provider provider;                    // 模型提供商
    Memory memory;                        // 记忆
    ToolRegistry toolRegistry;            // 工具注册
    KnowledgeBase knowledgeBase;          // 知识库
    EvolutionDecisionEngine evolutionEngine; // 进化引擎
    String personality;                   // 人格设定
    ChannelManager channelManager;        // 通道管理
    SkillRegistry skillRegistry;          // 技能注册
    InstructionFileLoader instructionFileLoader; // 指令文件加载器
    String employeeId;                    // 员工 ID
    Map<String, Object> state;            // 运行时状态
    List<ChatMessage> history;            // 对话历史

    // Builder 模式构建
    public static Builder builder() { ... }
}
```

## 代码路径

```
brain/
├── Brain.java                      # 核心接口（含 BrainState 内部枚举）
├── BrainContext.java               # 上下文（class + Builder）
├── BrainRegistry.java              # 注册表接口
└── impl/
    ├── AbstractBrain.java          # 抽象基类（ReAct 循环 + 上下文压缩 + 工具钩子）
    ├── BrainRegistryImpl.java      # 注册表实现
    ├── MainBrain.java              # 主脑（跨部门协调）
    ├── TechBrain.java              # 技术大脑
    ├── FinanceBrain.java           # 财务大脑
    ├── HrBrain.java                # 人力资源大脑
    ├── SalesBrain.java             # 销售大脑
    ├── CsBrain.java                # 客服大脑
    ├── AdminBrain.java             # 行政大脑
    ├── LegalBrain.java             # 法务大脑
    └── OpsBrain.java               # 运营大脑
```

## 快速定位

| 需求 | 文件 |
|------|------|
| 修改 ReAct 执行逻辑 | `AbstractBrain.java` |
| 新增部门大脑 | `impl/TechBrain.java` → 复制修改 |
| 修改大脑注册逻辑 | `BrainRegistryImpl.java` |
| 修改主脑协调逻辑 | `MainBrain.java` |
| 修改提示词模板 | 各 `*Brain.java` |
| 添加工具到大脑 | 各 `*Brain.java` 构造函数 |
| 修改上下文构建 | `BrainContext.Builder` |
| 修改上下文压缩 | `ContextCompactor.java` |

## 代码审查状态机

### TechLeadOrchestrator 审查循环

`TechLeadOrchestrator` 实现了 `LeadOrchestrator` 接口的审查循环方法，驱动代码从编写到审查通过的完整闭环：

```
PLAN_CREATED → ASSIGN_DEVELOPER → DEVELOPER_WRITING → CODE_SUBMITTED
→ ASSIGN_REVIEWER → REVIEWING → REVIEW_CHANGES_REQUESTED → DEVELOPER_REVISING
→ CODE_RESUBMITTED → ASSIGN_REVIEWER（循环）
→ REVIEW_APPROVED → FINAL_SUMMARY → USER_ACCEPTED / USER_REJECTED
```

任意阶段可升级到 `ESCALATED`（人工介入）。

### 审查循环方法

| 方法 | 阶段转换 | 说明 |
|------|---------|------|
| `submitForReview(taskId, reviewerNeuronId)` | CODE_SUBMITTED → REVIEWING | 提交审查，通过 Channel 通知审查员工 |
| `requestChanges(taskId, findings)` | REVIEWING → REVIEW_CHANGES_REQUESTED | 审查不通过，通知开发员工修改 |
| `resubmitCode(taskId)` | DEVELOPER_REVISING → ASSIGN_REVIEWER | 修改后重新提交 |
| `approveCode(taskId)` | REVIEW_APPROVED → FINAL_SUMMARY | 审查通过，更新任务状态为 COMPLETED |
| `escalateReview(taskId, reason)` | 任意 → ESCALATED | 升级人工，广播团队 |
| `getReviewState(taskId)` | 查询 | 获取当前审查状态 |

### 状态转换校验

- `CodeReviewWorkflowService.canTransition(from, to)` 静态方法校验转换合法性
- `MAX_REVIEW_ROUNDS = 3`：超过3轮自动审查后必须升级人工
- `advanceStage()` 在非法转换时抛出 `IllegalStateException`

### 持久化

审查状态通过 `JpaCodeReviewWorkflowService` 持久化到 PostgreSQL `code_review_states` 表，重启不丢失。

### 层次边界

- **brain/collaboration**（`LeadOrchestrator`）：大脑 → 员工，单向指挥，基于 Channel 通道和 TaskDagService
- **worker/collaboration**（`CollaborationService`）：员工 ↔ 员工，多向协作，基于内存会话和7种协作类型
- PEER_REVIEW 类型协作会话任务完成后自动对接 `CodeReviewWorkflowService`
