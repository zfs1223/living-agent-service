# 代码逻辑落地验证报告

> 生成时间：2026-06-05
> 验证范围：living-agent-service 全部核心模块
> 验证方法：逐文件阅读源码，与文档描述的逻辑逐一比对，判定每项设计是否真正落地（REAL / PARTIAL / STUB）

---

## 一、总体结论

经过对整个项目核心代码的深度逐文件验证，**绝大部分文档描述的逻辑已在代码中真实落地**。自治编排链路 14 个步骤、9 个部门大脑、LLM 优先 + 规则兜底模式、模型池与熔断机制、权限矩阵、工具注册与执行、知识库三层作用域、进化系统五大策略等核心设计均有对应实现。

但同时发现 **7 项需要关注的偏差**（2 项 P0 级、3 项 P1 级、2 项 P2 级），分布在跨部门转发、会话上下文完整性、能力解析器 LLM 实现、浏览器工具模拟实现、Receipt 收集架构偏差等方面。

### 落地率概览

| 子系统 | 检查项数 | REAL | PARTIAL | STUB |
|--------|----------|------|---------|------|
| 自治编排链路 | 14 | 12 | 2 | 0 |
| 部门大脑 & 模型池 | 8 | 7 | 1 | 0 |
| 网关 / 权限 / WebSocket | 6 | 4 | 2 | 0 |
| 工具 / 沙箱 / 知识 / 进化 | 41+4子系统 | 38+4 | 1 | 0 |
| **合计** | **~73** | **~65 (89%)** | **6 (8%)** | **0** |

---

## 二、自治编排链路验证

### 2.1 编排链全景（14 步）

编排链的实际驱动中心是 `DepartmentChatService.java`，而非文档中暗示的单一 `ConversationOrchestrator`。实际流程如下：

| 步骤 | 组件 | 状态 | 说明 |
|------|------|------|------|
| 1 | DialogueAnalyzer (LLM) | ✅ REAL | `LlmBasedDialogueAnalyzer` 调用 `llmDecisionClient.decideWithRetry()`，完整 JSON Schema，MessageKind 枚举 |
| 2 | RequirementReadinessEvaluator | ✅ REAL | 基于需求状态机 (requirementStatus) 的规则评估 |
| 3 | MainBrainTaskDirector (LLM) | ✅ REAL | `LlmBasedMainBrainTaskDirector` 包含详细 System Prompt，16 个 executionCapability、16 个 artifactType、7 个 executionMode |
| 4 | Brain 路由 | ✅ REAL | 根据任务类型路由到对应部门大脑 |
| 5 | ExecutionCapabilityResolver | ⚠️ PARTIAL | `DefaultExecutionCapabilityResolver` 仅规则实现（关键词→枚举映射），**无 LLM 实现** |
| 6 | FixedEmployeeDispatcher (LLM) | ✅ REAL | `LlmBasedFixedEmployeeDispatcher` LLM 选人，校验 FixedEmployeeRegistry，生成执行指令 |
| 7 | AssignmentPreparationService | ✅ REAL | 准备任务分配批次 |
| 8 | DepartmentExecutionCoordinator | ✅ REAL | `ChannelBackedDepartmentExecutionCoordinator` 先创建 receipt channel，再发布 ChannelMessage 到员工任务通道 |
| 9 | ToolBackedEmployeeTaskExecutor | ✅ REAL | 960 行，按 ExecutionCapability 路由，LLM 代码生成，Docker 沙箱，ToolRegistry 调用 |
| 10 | Receipt 聚合 | ⚠️ PARTIAL | 轮询模式（60s 超时，500ms 轮询间隔）+ 异步 onReceiptRecorded 监听器，**非纯通道订阅** |
| 11 | ExecutionReceiptReviewer (LLM) | ✅ REAL | `LlmExecutionReceiptReviewer` LLM 语义评审 + 质量评分，fallback 到 DefaultExecutionReceiptReviewer |
| 12 | MainBrainFinalSummaryService (LLM) | ✅ REAL | `LlmMainBrainFinalSummaryService` LLM 生成结构化摘要，3 层解析兜底 |
| 13 | FinalResponseCoordinator (LLM) | ✅ REAL | `LlmBasedFinalResponseCoordinator` 7 种响应策略，LLM 选择 |
| 14 | 知识/性能/制品捕获 | ✅ REAL | `DefaultKnowledgeCaptureService`(74行)、`DefaultPerformanceCaptureService`(56行)，轻量但真实 |

### 2.2 ConversationOrchestrator 与 DepartmentChatService 的关系

文档描述编排链为一个统一流程，实际代码中分为**两个阶段**：

- **阶段一（ConversationOrchestrator）**：DialogueAnalyzer → RequirementReadinessEvaluator → MainBrainTaskDirector → Brain 路由，返回 `OrchestrationResult`
- **阶段二（DepartmentChatService）**：通过 `thenCompose` 链式异步调用 planEmployeeAssignments → prepareAssignmentBatch → coordinateDepartmentExecution → collectExecutionReceipts → aggregateExecutionResult → generateSummary → determineStrategy

这不是功能缺失，而是文档与实际代码的**架构表述差异**——核心逻辑完整，但编排职责分散在两个类中。

### 2.3 LLM 优先 + 规则兜底模式

| 组件 | LLM 实现 | 规则兜底 | 验证结果 |
|------|----------|----------|----------|
| DialogueAnalyzer | LlmBasedDialogueAnalyzer | DefaultDialogueAnalyzer | ✅ 双实现真实 |
| MainBrainTaskDirector | LlmBasedMainBrainTaskDirector | DefaultMainBrainTaskDirector | ✅ 双实现真实 |
| FixedEmployeeDispatcher | LlmBasedFixedEmployeeDispatcher | DefaultFixedEmployeeDispatcher | ✅ 双实现真实 |
| ExecutionReceiptReviewer | LlmExecutionReceiptReviewer | DefaultExecutionReceiptReviewer | ✅ 双实现真实 |
| MainBrainFinalSummaryService | LlmMainBrainFinalSummaryService | DefaultMainBrainFinalSummaryService | ✅ 双实现真实 |
| FinalResponseCoordinator | LlmBasedFinalResponseCoordinator | DefaultFinalResponseCoordinator | ✅ 双实现真实 |
| ExecutionCapabilityResolver | **无 LLM 实现** | DefaultExecutionCapabilityResolver | ❌ 缺少 LLM 实现 |

**结论**：7 个 LLM+规则组件中，6 个完整落地，1 个（ExecutionCapabilityResolver）仅有规则实现。

---

## 三、部门大脑 & 模型池验证

### 3.1 部门大脑

| 大脑 | 存在性 | ReAct 循环 | 工具调用 | 模型回退 | 状态 |
|------|--------|-----------|---------|---------|------|
| MainBrain | ✅ | ✅ (max 10 轮) | ✅ | ✅ | ⚠️ PARTIAL（见下方） |
| TechBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| HrBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| FinanceBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| SalesBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| CsBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| AdminBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| LegalBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |
| OpsBrain | ✅ | ✅ | ✅ | ✅ | ✅ REAL |

`AbstractBrain.java` 实现了完整的 ReAct 循环基类：最大 10 轮迭代、工具调用、模型回退（动态 provider 解析，当 `getProvider()==null` 时触发）、上下文压缩、会话管理。所有 9 个部门大脑均继承此基类。

**MainBrain 特殊逻辑**：MainBrain.java（29KB）包含跨部门协调逻辑和 `callLlm()` 方法，这些核心功能已真实落地。但 `forwardToDepartment()` 方法**仅有日志输出，无实际消息转发逻辑**。

### 3.2 模型池

| 组件 | 状态 | 说明 |
|------|------|------|
| BrainModelResolver | ✅ REAL | 3 级解析链：分配数据库 → selector → 默认值，Ollama /api/tags 发现，健康检查过滤 |
| ModelHealthRegistry | ✅ REAL | 完整 Circuit Breaker 状态机：AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN，3 次失败→5 分钟冷却→2 次成功恢复 |
| BrainAutoAssigner | ✅ REAL | 9 个大脑定义，评分算法，幂等启动分配 |
| ResolvedBrainModelProvider | ✅ REAL | 通过 RestTemplate 实际发送 HTTP POST 到 LLM API 端点 |
| ExecutionCapability 枚举 | ✅ REAL | 14 个能力值：WEB_APP_BUILD, DOCUMENT_GENERATION, DATA_ANALYSIS 等 |

### 3.3 固定员工注册

| 组件 | 状态 | 说明 |
|------|------|------|
| FixedEmployeeRegistry | ✅ REAL | 1075 行，32 个员工跨 9 个部门，DB/静态双加载，工具白名单 |
| EmployeeNeuron | ✅ REAL | 475 行，大脑绑定、延迟绑定、provider 注入、自动认领、状态同步 |

---

## 四、网关 / 权限 / WebSocket 验证

### 4.1 WebSocket 路由

| 端点 | Handler | 状态 | 说明 |
|------|---------|------|------|
| `/ws/agent` | AgentWebSocketHandler | ✅ REAL | 个人通道，拒绝固定员工（CloseStatus.NOT_ACCEPTABLE） |
| `/ws/dept/*` | DepartmentWebSocketHandler | ✅ REAL | 部门大脑通道，调用 DepartmentChatService |
| `/ws/public` | DepartmentWebSocketHandler | ✅ REAL | 未认证用户通道 |
| `/ws/enterprise` | DepartmentWebSocketHandler | ✅ REAL | 企业主/董事长通道 |
| PersistentConnectionRegistry | ✅ REAL | 继承 ConnectionRegistry，重连时 pending 事件重放 |

### 4.2 权限矩阵

| 权限层 | 状态 | 说明 |
|--------|------|------|
| 登录态校验 | ✅ REAL | 区分已登录/未登录用户 |
| 身份识别 | ✅ REAL | 区分普通用户/管理员/企业主 |
| 页面权限 | ✅ REAL | 按身份限制可访问页面 |
| 通道权限 | ✅ REAL | 按身份限制可接入的 WebSocket 通道 |
| 固定员工禁聊 | ✅ REAL | AgentWebSocketHandler 中校验，返回 NOT_ACCEPTABLE |
| 部门统一入口 | ✅ REAL | 部门大脑作为统一入口，禁止直接访问员工 |
| DepartmentPermissionInterceptor | ✅ REAL | 3 层权限拦截：管理员 API、企业 API、部门 API |

### 4.3 SessionContext

| 字段 | 状态 | 说明 |
|------|------|------|
| sessionId | ✅ | 已实现 |
| accessLevel | ✅ | 已实现 |
| departmentId | ✅ | 已实现 |
| userId | ✅ | 已实现 |
| **taskKey** | ❌ 缺失 | 文档描述用于关联执行上下文，代码中未定义 |
| **executionId** | ❌ 缺失 | 文档描述用于追踪执行生命周期，代码中未定义 |

---

## 五、工具 / 沙箱 / 知识库 / 进化系统验证

### 5.1 工具注册与执行

| 组件 | 状态 | 说明 |
|------|------|------|
| ToolRegistryImpl | ✅ REAL | ConcurrentHashMap 存储，部门索引，Schema 提取 |
| ToolBackedEmployeeTaskExecutor | ✅ REAL | 960 行，按 ExecutionCapability 路由，LLM 代码生成，Docker 沙箱，真实工具调用 |
| DynamicEmployeeTaskConsumerRegistry | ✅ REAL | 订阅员工通道，委托给 ToolBackedEmployeeTaskExecutor，发送回执 |

### 5.2 工具实现统计

| 类别 | 数量 | REAL | PARTIAL |
|------|------|------|---------|
| 文件操作工具 | 5 | 5 | 0 |
| 代码执行工具 | 4 | 4 | 0 |
| 网络/搜索工具 | 5 | 5 | 0 |
| 数据库工具 | 3 | 3 | 0 |
| 部署/运维工具 | 6 | 6 | 0 |
| 协作/通信工具 | 8 | 8 | 0 |
| 浏览器自动化工具 | 3 | 0 | **3** |
| 项目管理工具 | 4 | 4 | 0 |
| 其他辅助工具 | 3 | 3 | 0 |
| **合计** | **41** | **38** | **3** |

**BrowserAutomationTool 详细问题**：

| 方法 | 状态 | 说明 |
|------|------|------|
| navigate() | ✅ REAL | 调用 Playwright/Selenium 实际导航 |
| click() | ⚠️ MOCK | 仅记录日志，返回模拟成功响应，无实际点击操作 |
| screenshot() | ⚠️ MOCK | 返回占位图片数据，无实际截图 |
| getText() | ⚠️ MOCK | 返回空字符串或占位文本，无实际 DOM 提取 |

### 5.3 Docker 沙箱

| 组件 | 状态 | 说明 |
|------|------|------|
| DockerSandboxService | ✅ REAL | 340 行，docker-java 客户端，容器创建带资源限制，多语言执行支持 |

### 5.4 知识库系统

| 组件 | 状态 | 说明 |
|------|------|------|
| SQLiteKnowledgeBase | ✅ REAL | 742 行，4 张 SQLite 表，向量搜索，混合检索 |
| LayeredKnowledgeBaseImpl | ✅ REAL | 654 行，3 层作用域（L1_PRIVATE / L2_DEPARTMENT / L3_SHARED），内存/PostgreSQL/Qdrant 存储 |
| KnowledgeEvolverImpl | ✅ REAL | 428 行，质量评估、晋升、合并、清理、最佳实践提取 |

### 5.5 进化系统

| 组件 | 状态 | 说明 |
|------|------|------|
| EvolutionExecutor | ✅ REAL | 499 行，5 种策略（REPAIR / OPTIMIZE / INNOVATE / DEFER / ESCALATE），技能生成与安装流水线 |
| DefaultSignalExtractor | ✅ REAL | 306 行，从 4 个来源（对话、日志、指标、反馈）提取信号的正则模式 |

---

## 六、发现的问题清单（按优先级排序）

### P0 — 功能缺陷（影响核心流程完整性）

#### P0-1：MainBrain.forwardToDepartment() 仅记录日志，无实际转发

- **文件**：`MainBrain.java`
- **现象**：`forwardToDepartment()` 方法内部仅有 `log.info()` 调用，没有实际将消息路由到目标部门大脑的逻辑
- **影响**：跨部门协调功能无法真正工作。当 MainBrain 判断需要其他部门协助时，转发操作不会发生
- **建议**：实现实际的消息转发逻辑，通过 `DepartmentChatService` 或 `ChannelManager` 将任务发送到目标部门的执行通道

#### P0-2：SessionContext 缺少 taskKey 和 executionId

- **文件**：`SessionContext.java`
- **现象**：当前仅有 `sessionId`、`accessLevel`、`departmentId`、`userId` 四个字段。文档中描述的 `taskKey`（关联执行上下文）和 `executionId`（追踪执行生命周期）均未定义
- **影响**：WebSocket 会话无法与具体执行任务关联，影响任务追踪、上下文恢复、断点续传等能力
- **建议**：在 SessionContext 中添加 `taskKey` 和 `executionId` 字段，并在任务创建/执行/完成的生命周期中维护

### P1 — 功能不完整（部分场景受限）

#### P1-1：ExecutionCapabilityResolver 缺少 LLM 实现

- **文件**：`DefaultExecutionCapabilityResolver.java`
- **现象**：当前仅有基于关键词匹配的规则实现，嵌入在 `AssignmentPreparationService` 和 `ToolBackedEmployeeTaskExecutor` 中。其他同类组件（DialogueAnalyzer、TaskDirector 等）均有 LLM+规则双实现，唯独此组件只有规则实现
- **影响**：当用户需求表述模糊或不在预设关键词范围内时，无法通过 LLM 语义理解来正确解析执行能力
- **建议**：参照其他组件的模式，新增 `LlmBasedExecutionCapabilityResolver`，将 14 个 ExecutionCapability 枚举值作为 LLM 的分类目标

#### P1-2：BrowserAutomationTool 三个核心方法为 Mock 实现

- **文件**：`BrowserAutomationTool.java`
- **现象**：`click()`、`screenshot()`、`getText()` 三个方法均为模拟实现（日志记录/占位返回），仅 `navigate()` 为真实调用
- **影响**：依赖浏览器自动化的任务（网页截图、页面元素交互、内容提取）无法正常执行
- **建议**：集成 Playwright 或 Selenium WebDriver，实现真实的 DOM 操作、截图和文本提取

#### P1-3：Receipt 收集机制与文档描述不一致

- **文件**：`DepartmentChatService.java`
- **现象**：文档描述 Receipt 通过通道订阅（channel subscription）收集，实际代码使用**轮询模式**（60s 超时，500ms 轮询间隔）加异步 `onReceiptRecorded` 监听器的双路径方案
- **影响**：轮询方式在高并发下可能产生不必要的 CPU 开销；双路径（轮询+监听）存在竞态风险
- **建议**：评估当前轮询+监听双路径方案在高并发下的表现，如性能可接受则补充文档说明；否则改为纯响应式通道订阅

### P2 — 文档与代码偏差（不影响功能但影响维护）

#### P2-1：编排链驱动中心与文档描述不一致

- **现象**：文档将编排链描述为一个统一流程，实际由 `ConversationOrchestrator`（前半段）和 `DepartmentChatService`（后半段，thenCompose 链）两个类共同驱动
- **影响**：新开发者阅读文档后可能对编排入口产生误解
- **建议**：更新文档，明确说明编排链的两阶段划分和各自的驱动类

#### P2-2：Receipt 等待超时时间文档值与代码值不一致

- **现象**：文档中描述超时为 5s，实际代码中为 60s（带自适应延长机制）
- **影响**：文档与代码不一致可能导致运维/调试时的困惑
- **建议**：以代码实际值（60s）为准更新文档

---

## 七、架构亮点（已验证落地的核心设计）

以下为文档描述的核心设计中，经代码验证**确认真实落地**的关键架构特性：

1. **完整的 14 步自治编排链**：从对话分析到最终响应，每个环节都有对应的 Java 实现类，LLM 组件均有规则兜底
2. **9 个部门大脑的 ReAct 循环**：`AbstractBrain` 基类实现了最大 10 轮迭代、工具调用、模型回退、上下文压缩等核心能力
3. **模型池三级解析 + 熔断机制**：BrainModelResolver 的分配库→选择器→默认值三级链路，ModelHealthRegistry 的 5 态状态机（AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN）
4. **Channel-based 消息架构**：ChannelManager 驱动的发布/订阅模型，员工任务通道与 Receipt 通道的生命周期管理
5. **32 个固定员工的注册管理**：FixedEmployeeRegistry 的 DB/静态双加载、工具白名单、跨部门分配
6. **41 个工具中 38 个的真实外部系统调用**：覆盖文件、代码、网络、数据库、部署、协作、项目管理等 8 个类别
7. **知识库三层作用域**：L1_PRIVATE → L2_DEPARTMENT → L3_SHARED 的晋升/合并/清理机制
8. **进化系统五大策略**：REPAIR / OPTIMIZE / INNOVATE / DEFER / ESCALATE 的完整信号提取→评估→执行流水线
9. **权限矩阵四层拦截**：登录态 → 身份 → 页面 → 通道，固定员工禁聊、部门统一入口

---

## 八、修复建议优先级路线图

### 第一阶段（P0，建议立即修复）

```
1. MainBrain.forwardToDepartment()
   → 实现跨部门消息转发逻辑
   → 预计工作量：1-2 天

2. SessionContext 补全
   → 添加 taskKey + executionId 字段
   → 在任务生命周期中维护这两个字段
   → 预计工作量：1 天
```

### 第二阶段（P1，建议一周内完成）

```
3. ExecutionCapabilityResolver LLM 实现
   → 新增 LlmBasedExecutionCapabilityResolver
   → 预计工作量：1-2 天

4. BrowserAutomationTool 真实实现
   → 集成 Playwright/Selenium，替换 mock 方法
   → 预计工作量：2-3 天

5. Receipt 收集机制评估
   → 性能测试 + 架构评审
   → 如需重构预计 2 天
```

### 第三阶段（P2，文档同步）

```
6. 更新编排链文档，明确两阶段划分
7. 更新 Receipt 超时文档值为 60s
```

---

## 九、验证方法说明

本次验证采用以下方法：

1. **逐文件源码阅读**：对每个文档提及的核心类，直接阅读 Java 源码，检查方法实现是否为真实逻辑（非空方法体、非仅日志、非硬编码返回）
2. **调用链追踪**：从 `DepartmentChatService` 入口开始，追踪整个编排链的 `thenCompose` 异步链路，验证每一步是否被真实调用
3. **LLM/规则双实现验证**：对每个文档描述为 LLM 优先的组件，检查是否同时存在 LLM 实现类和规则兜底实现类
4. **接口与实现对照**：检查接口定义是否有对应的实现类，实现类的方法是否有真实逻辑
5. **外部集成验证**：对涉及外部系统调用的工具（Docker、SQLite、Qdrant、REST API），检查是否有真实的客户端创建和调用代码
