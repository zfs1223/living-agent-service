# Docker 服务日志问题排查与解决方案

> 排查时间：2026-05-07  
> 范围：`docker/living-agent-service` 相关前后端与依赖容器  
> 重点容器：`living-agent-service`、`living-agent-frontend`、`living-agent-qdrant`、`living-agent-memos`、`living-agent-openproject`

## 1. 总体结论

当前主前后端服务基本可用：

| 容器 | 当前观察 | 结论 |
|---|---|---|
| `living-agent-service` | Spring Boot 后端运行，健康检查为 healthy | 主服务可用，但有部门聊天链路与 Native/Audio 问题 |
| `living-agent-frontend` | Nginx 正常启动，静态资源与 API 代理可用 | 前端服务正常 |
| `living-agent-qdrant` | Qdrant 日志显示 HTTP/gRPC 已监听，但容器 unhealthy | 主要是 healthcheck 命令错误 |
| `living-agent-memos` | Uvicorn 子进程启动失败，8000 端口不可用 | 真实启动失败，需要修配置 |
| `living-agent-openproject` | Rails/Puma 已启动，但 healthcheck 返回 400 | 服务可能可用，healthcheck 配置不正确 |

最优先需要处理的问题：

1. 部门 WebSocket 建连仍启动 `AgentService/Qwen3Neuron`。
2. 部门 WebSocket 聊天用户消息重复保存。
3. `living-agent-memos` 启动失败。
4. Qdrant/OpenProject healthcheck 配置不准确。
5. Native/AudioNative 加载失败。
6. 日志中缺少“对话入口 -> 大脑协调分析 -> 部门路由判断 -> 部门大脑分工 -> 数字员工执行 -> 结果汇总反馈”的完整自治流程。当前更像是一次普通 LLM 回复，而不是具备组织协同能力的数字企业生命体。

---

## 2. 后端主服务：`living-agent-service`

### 2.1 部门 WebSocket 建连仍启动 `AgentService/Qwen3Neuron`

#### 日志现象

```text
Session created: session-a52d6389
Qwen3Neuron neuron://chat/qwen3/001 subscribed to channel: channel://perception/session-a52d6389
Qwen3Neuron neuron://chat/qwen3/001 will publish to channel: channel://response/session-a52d6389
Neuron neuron://chat/qwen3/001 bound to session session-a52d6389
AgentService : Bound chat neuron to coordinator session
DepartmentWebSocketHandler : WebSocket connected: user=..., dept=tech
AgentService : Session started: ..., accessLevel=FULL, departmentId=tech
```

#### 问题说明

部门文本消息处理已经修成：

```text
/ws/dept/{dept}
  -> DepartmentWebSocketHandler
  -> DepartmentChatService
  -> BrainRegistry.getByDepartment(dept)
  -> Department Brain.process(...)
```

但是部门 WebSocket 建立连接时，仍然调用了 `agentService.startSession(...)`，导致只要打开部门聊天页面，就会创建 `AgentService` 会话并绑定 `Qwen3Neuron`。

这与文档口径不一致：部门文字对话不应进入 `Qwen3Neuron / chat` 闲聊神经元链路；非语音时也不涉及 ASR/TTS。

#### 影响

- 无意义创建 `AgentService` 会话。
- 无意义绑定 `Qwen3Neuron`。
- 触发音频相关初始化与 `AudioNative not available` 警告。
- 增加资源消耗和日志噪声。
- 后续排查部门大脑链路时容易误判。

#### 建议解决方案

修改 `DepartmentWebSocketHandler`：

1. 在 `afterConnectionEstablished()` 中不要对部门文本 WebSocket 调用 `agentService.startSession(...)`。
2. 在 `afterConnectionClosed()` 中不要无条件调用 `agentService.endSession(sessionId)`。
3. 如果未来需要部门语音入口，单独通过语音消息类型或独立 WebSocket 路由启动 Agent/ASR/TTS 会话。

建议逻辑：

```text
/ws/dept/{dept} 文本连接：
  - 只记录 session -> department/user/authContext
  - 不启动 AgentService
  - 文本消息直接进入部门大脑

语音入口：
  - 明确检测 AUDIO/VOICE 类型
  - 再启动 ASR/TTS/AgentService 相关链路
```

---

### 2.2 部门聊天用户消息重复保存

#### 日志现象

```text
DepartmentChatService : Saved chat message: dept=tech, user=founder..., role=user, content=帮我做一个跳动的小球的网页
...
DepartmentChatService : Saved chat message: dept=tech, user=founder..., role=user, content=帮我做一个跳动的小球的网页
DepartmentChatService : Saved chat message: dept=tech, user=brain_tech, role=assistant, content=...
```

#### 问题说明

同一条用户消息被保存了两次。

原因是：

1. `DepartmentWebSocketHandler.handleChatMessage()` 先调用了一次 `departmentChatService.saveMessage(..., "user")`。
2. `DepartmentChatService.processBrainResponse()` 又保存了一次用户消息和助手回复。

#### 影响

- 部门聊天历史中用户消息重复。
- 数据库 `department_chat_messages` 中会出现重复记录。
- 前端历史展示可能出现重复气泡。
- 后续统计、审计、上下文构造会受影响。

#### 建议解决方案

推荐由 `DepartmentChatService` 统一保存消息。

修改方式：

1. 删除或禁用 `DepartmentWebSocketHandler.handleChatMessage()` 中的用户消息落库。
2. 保留 `DepartmentChatService.processBrainResponse()` 中的统一保存逻辑。
3. 如果 WebSocket 需要立即广播用户发言，可以广播临时消息，但不要落库。

目标职责划分：

| 模块 | 职责 |
|---|---|
| `DepartmentWebSocketHandler` | WebSocket 会话管理、权限校验、消息广播、响应发送 |
| `DepartmentChatService` | 部门大脑调用、结果解析、用户/助手消息统一落库 |

---

### 2.3 缺少大脑协调、部门路由和数字员工分工的自治流程

#### 日志现象

当前部门对话日志大致是：

```text
DepartmentWebSocketHandler : WebSocket message: dept=tech, message={...}
DepartmentWebSocketHandler : processWithBrain: dept=tech, userId=..., sessionId=..., contentLength=...
DepartmentChatService      : External subscriber ... subscribed to channel channel://output/text
TechBrain                  : TechBrain processing message: ...
DepartmentChatService      : Saved chat message: dept=tech, user=brain_tech, role=assistant, content=...
```

日志中没有出现以下关键过程：

```text
对话入口接收
  -> 大脑协调层分析：这是闲聊、咨询、任务、项目还是跨部门需求？
  -> 判断应由哪个部门大脑主责，是否需要跨部门协同？
  -> 部门大脑拆解目标、制定计划
  -> 部门大脑选择部门数字员工并分工
  -> 数字员工执行子任务、调用工具、产生产物
  -> 部门大脑汇总结果、验收质量、返回用户
  -> 经验沉淀到记忆/知识/绩效系统
```

这说明当前链路虽然已经避免误入 `Qwen3Neuron/chat`，但仍然是“用户消息直接给某个 Department Brain 生成一次回复”。它缺少企业大脑系统应有的自主生命力：

- 缺少统一入口处的意图分析和任务识别。
- 缺少主脑/协调脑对任务归属的判断。
- 缺少部门大脑对部门数字员工的组织、调度和分工。
- 缺少任务执行过程、工具调用、产物沉淀和验收闭环。
- 缺少跨部门协同机制，例如技术 + 运营 + 财务联合完成任务。
- 缺少把一次对话升级为任务、项目、审批或知识沉淀的机制。

#### 问题说明

当前 `DepartmentWebSocketHandler -> DepartmentChatService -> BrainRegistry.getByDepartment(department) -> Brain.process(...)` 是一个“固定部门直达”路径。它适合明确的部门聊天，但不适合承载完整企业自治流程。

例如用户在技术部说：

```text
帮我做一个跳动的小球的网页
```

理想流程不应该只是 `TechBrain` 回答“我可以指导你怎么做”，而应该是：

```text
1. 对话入口识别：这是一个可执行开发任务，不是普通咨询。
2. 大脑协调层判断：主责部门为 tech，任务类型为 web_prototype，复杂度 low/medium。
3. TechBrain 接收任务：拆解为需求确认、代码生成、预览验证、交付说明。
4. TechBrain 分派数字员工：
   - 前端工程员工：生成 HTML/CSS/JS
   - 测试/验收员工：检查是否可运行、是否满足“跳动小球”
   - 文档/交付员工：整理运行方法和产物路径
5. 执行层调用工具：创建文件、运行静态预览或测试。
6. TechBrain 汇总：返回可访问链接、代码位置、说明。
7. 记忆/绩效：记录任务完成情况和员工贡献。
```

#### 目标架构

建议增加一层“企业大脑协调/任务路由/员工调度”流程，而不是让 WebSocket 直接把所有消息交给某个 Department Brain。

目标链路：

```text
用户输入
  -> ConversationOrchestrator / BrainCoordinator
  -> DialogueAnalyzer
      - 判断 messageType: chat / task / project / approval / knowledge / cross_department
      - 判断 intent、domain、complexity、risk、requiredDepartments
  -> BrainRouter
      - 选择 primaryBrain
      - 选择 supportingBrains
      - 判断是否需要 MainBrain / CrossDeptBrain 协调
  -> TaskPlanner
      - 生成任务目标、验收标准、子任务 DAG
  -> DepartmentBrain
      - 结合部门上下文、工具、员工画像制定执行方案
  -> EmployeeDispatcher
      - 从 EmployeeRegistry / FixedEmployeeRegistry 中选择合适数字员工
      - 分派子任务
  -> DigitalEmployee / Neuron / Tool 执行
      - 生成代码、调用 GitLab/Jira/OpenProject/文件工具等
  -> ResultAggregator
      - 汇总子任务结果
      - 质量检查
      - 判断是否需要追问用户
  -> ResponseComposer
      - 返回最终答复
  -> Memory / Knowledge / Performance / Ledger
      - 记录经验、知识、绩效、积分或赏金
```

#### 建议新增核心组件

| 组件 | 所属模块 | 职责 |
|---|---|---|
| `ConversationOrchestrator` | gateway 或 core | 对话总入口，统一编排分析、路由、执行和回复 |
| `DialogueAnalyzer` | core/brain 或 core/planner | 判断用户输入是闲聊、任务、项目、审批还是知识请求 |
| `BrainRouter` | core/brain | 根据意图和部门映射选择主责大脑和协同大脑 |
| `TaskPlanningService` | core/planner | 将任务拆解成子任务、依赖关系和验收标准 |
| `DepartmentWorkCoordinator` | core/brain/collaboration | 部门内工作协调，负责把任务交给部门数字员工 |
| `EmployeeDispatcher` | core/employee | 根据员工能力、部门、状态、负载选择执行者 |
| `TaskExecutionOrchestrator` | core/workflow 或 core/project | 跟踪子任务执行、超时、失败重试和汇总 |
| `ResultAggregator` | core/brain/collaboration | 汇总执行结果并做质量检查 |
| `AutonomyTraceService` | gateway/core | 记录每次自治流程的 trace，方便日志和前端可视化 |

#### 建议数据结构

建议在 core 中引入统一的对话决策对象：

```java
public record DialogueDecision(
    String requestId,
    String sessionId,
    String userId,
    String originalMessage,
    MessageKind kind,
    String intent,
    String primaryDepartment,
    String primaryBrainId,
    List<String> supportingDepartments,
    boolean requiresTaskExecution,
    boolean requiresClarification,
    String clarificationQuestion,
    int complexity,
    int riskLevel,
    Map<String, Object> metadata
) {}
```

任务规划对象：

```java
public record WorkPlan(
    String planId,
    String requestId,
    String objective,
    List<String> acceptanceCriteria,
    List<WorkStep> steps,
    List<String> requiredTools,
    String primaryDepartment,
    List<String> supportingDepartments
) {}
```

员工分工对象：

```java
public record WorkAssignment(
    String assignmentId,
    String stepId,
    String employeeId,
    String employeeName,
    String department,
    String role,
    String instruction,
    List<String> toolsAllowed,
    String status
) {}
```

自治追踪对象：

```java
public record AutonomyTraceEvent(
    String traceId,
    String requestId,
    String stage,
    String actor,
    String summary,
    Map<String, Object> data,
    Instant timestamp
) {}
```

#### 建议日志格式

为方便后续从日志中判断系统是否具备“生命力”，建议每个阶段都输出结构化日志：

```text
[AutonomyTrace] requestId=... stage=dialogue_analyzed kind=TASK intent=web_prototype primary=tech supporting=[] complexity=2 risk=1
[AutonomyTrace] requestId=... stage=brain_routed primaryBrain=TechBrain supportingBrains=[] routeReason="web development request"
[AutonomyTrace] requestId=... stage=work_plan_created planId=... steps=3 acceptance="page renders bouncing ball"
[AutonomyTrace] requestId=... stage=employee_assigned step=frontend_code employee=neuron://tech/真捷/023 reason="frontend implementation"
[AutonomyTrace] requestId=... stage=employee_assigned step=qa_check employee=neuron://tech/真稳/027 reason="quality verification"
[AutonomyTrace] requestId=... stage=work_step_completed step=frontend_code status=SUCCESS artifact=...
[AutonomyTrace] requestId=... stage=result_aggregated status=SUCCESS deliverables=...
[AutonomyTrace] requestId=... stage=knowledge_recorded entries=1 performanceEvents=2
```

#### 改造后的部门对话决策逻辑

建议将部门文本入口分为两类：

1. **普通部门问答**：例如“技术部有哪些成员？”、“解释一下 CI/CD”。
2. **可执行任务**：例如“帮我做一个网页”、“生成一份预算表”、“整理合同风险”。

伪流程：

```text
DepartmentChatService.processDepartmentText(...)
  -> ConversationOrchestrator.handle(input)
      -> DialogueAnalyzer.analyze(input)
      -> if decision.requiresClarification:
             return clarification question
      -> if decision.kind == CHAT:
             return DepartmentBrain.answer(decision)
      -> if decision.kind == TASK:
             plan = TaskPlanningService.createPlan(decision)
             assignments = DepartmentWorkCoordinator.assign(plan)
             execution = TaskExecutionOrchestrator.execute(assignments)
             result = ResultAggregator.aggregate(execution)
             Memory/Knowledge/Performance.record(result)
             return ResponseComposer.compose(result)
      -> if decision.kind == CROSS_DEPARTMENT:
             return CrossDepartmentCoordinator.coordinate(decision)
```

#### 部门大脑如何让数字员工分工

部门大脑不应该只调用 LLM 生成回答，而应该具备“部门经理/主管”职责：

1. 理解任务目标和验收标准。
2. 查询本部门数字员工能力：
   - 员工部门
   - 技能标签
   - 工具权限
   - 当前状态/负载
   - 历史绩效
3. 将任务拆成可执行子任务。
4. 为每个子任务选择合适员工。
5. 给员工明确输入、边界、产出格式和工具权限。
6. 等待或监听员工执行结果。
7. 对结果做一致性检查和质量验收。
8. 汇总为用户可读的最终回复。

示例分工：

```text
目标：做一个跳动的小球网页

TechBrain 分工：
- 真捷 / Frontend Engineer：创建 HTML/CSS/JS，实现动画。
- 真稳 / QA Engineer：检查页面是否可打开、动画是否符合需求。
- 真构 / Architect：确认文件结构和交付说明。

输出：
- 文件路径或预览 URL
- 实现说明
- 测试结果
- 后续可扩展建议
```

#### 与现有代码的落地关系

当前代码中已经存在一些可复用基础：

| 现有能力 | 可复用方式 |
|---|---|
| `BrainRegistry` | 查询部门大脑、主脑、跨部门协调脑 |
| `FixedEmployeeRegistry` / `EmployeeRegistry` | 查询固定员工和数字员工 |
| `DigitalEmployee` / `EmployeeService` | 作为执行者抽象 |
| `ToolRegistry` | 给员工或大脑调用工具 |
| `Workflow` / `TaskWorkflowService` | 承载执行流程、状态流转 |
| `Project` / `TaskController` | 将长期任务转成项目/任务管理对象 |
| `ChannelManager` | 大脑与员工之间的异步消息通道 |
| `KnowledgeManager` / `Memory` | 沉淀执行经验和知识 |
| `PerformanceAssessmentService` / `LedgerService` | 记录贡献、绩效、积分或赏金 |

#### 分阶段实施方案

##### 阶段 1：补齐自治 Trace 和任务识别，不改变执行能力

目标：先让日志中能看见完整思考链路。

- 新增 `DialogueAnalyzer`，基于规则 + LLM 判断 `CHAT/TASK/CROSS_DEPARTMENT`。
- 新增 `AutonomyTraceService`，记录并输出每个阶段。
- `DepartmentChatService` 不再直接调用 `brain.process(...)`，而是先调用 `ConversationOrchestrator`。
- 对 `TASK` 类型先不真正执行员工分工，只生成 `WorkPlan` 和“建议分工”，然后由部门大脑回复。

验证日志应出现：

```text
stage=dialogue_analyzed
stage=brain_routed
stage=work_plan_created
stage=assignment_planned
stage=response_composed
```

##### 阶段 2：接入部门内员工分工和异步执行

目标：让部门大脑真正把任务交给数字员工。

- 实现 `DepartmentWorkCoordinator`。
- 从员工注册表中按部门和技能筛选员工。
- 为每个 `WorkStep` 创建 `WorkAssignment`。
- 使用 `ChannelManager` 或 `TaskWorkflowService` 投递给员工。
- 员工执行结果进入 `ResultAggregator`。

验证日志应出现：

```text
stage=employee_assigned
stage=work_step_started
stage=work_step_completed
stage=result_aggregated
```

##### 阶段 3：接入工具执行、项目任务和产物管理

目标：让任务不只是“回复”，而能创建实际产物。

- 给技术类员工开放文件、GitLab、Jenkins、OpenProject 等工具。
- 可执行任务创建 `Task` 或 `Project` 记录。
- 生成代码/文档/配置时保存 artifact。
- 返回用户可追踪的任务 ID、产物路径、预览地址。

验证：

```text
stage=artifact_created
stage=task_record_created
stage=quality_checked
```

##### 阶段 4：跨部门协调和自主进化

目标：形成企业级自主生命力。

- 实现 `CrossDepartmentCoordinator`。
- 主脑根据任务需要协调多个部门大脑。
- 任务结果进入知识库和最佳实践。
- 员工贡献进入绩效/积分/赏金系统。
- 失败任务进入复盘和进化队列。

验证：

```text
stage=cross_department_routed
stage=department_result_collected
stage=knowledge_recorded
stage=performance_recorded
stage=evolution_signal_emitted
```

#### 优先修复建议

该问题应提升为新的 P0/P1 架构问题：

| 优先级 | 子问题 | 说明 |
|---|---|---|
| P0 | 部门文本入口缺少协调层 | 当前直接进入部门大脑，缺少任务识别和路由判断 |
| P0 | 部门大脑缺少员工分工闭环 | 无法体现数字企业的自主执行能力 |
| P1 | 缺少自治 Trace | 日志无法证明系统经历了分析、路由、分工、执行、汇总 |
| P1 | 缺少任务/产物沉淀 | 对话无法升级为任务、项目或可追踪产物 |
| P2 | 跨部门协同缺失 | 复杂任务无法由多个部门共同完成 |

---

### 2.4 Native Library 加载失败

#### 日志现象

```text
Failed to load native library: 'void com.livingagent.core.nativelib.NativeLibrary.initialize()'
```

#### 问题说明

后端尝试加载 Native/JNI 库失败。可能原因包括：

- Rust native 库没有构建。
- `.so` 文件没有打进镜像。
- Java `java.library.path` 没包含 native 库目录。
- JNI 方法签名与 Java 声明不一致。
- Linux 容器运行时缺少 native 库依赖。

#### 影响

- Native 能力不可用。
- 音频、安全沙箱、compact/memory 等依赖 native 的能力可能降级或失败。
- 当前文本部门大脑链路不一定受影响。

#### 建议解决方案

1. 检查 native 模块是否构建成功：

```bash
cd docker/living-agent-service/living-agent-native
cargo build --release
```

2. 检查产物是否被复制进后端镜像，例如：

```text
libliving_agent_native.so
```

3. 检查 Dockerfile 是否设置：

```bash
-Djava.library.path=/app/native
```

4. 检查 Java native 方法声明和 Rust JNI 导出方法签名是否一致。

5. 如果短期不需要 native 能力，可将该异常降级为清晰的 warning，并避免反复初始化。

---

### 2.5 AudioNative 不可用

#### 日志现象

```text
AudioNative not available, audio processing disabled for session
```

#### 问题说明

音频 Native 能力不可用。当前这条日志主要由部门 WebSocket 建连时错误启动 `AgentService` 触发。

#### 影响

- ASR/TTS/音频处理不可用。
- 文本对话本身不受直接影响。
- 会制造误导性日志，似乎部门聊天需要音频能力。

#### 建议解决方案

1. 优先修复 2.1：部门文本 WebSocket 不启动 `AgentService`。
2. 如果需要语音能力，再修复 native library 加载问题。
3. 将语音链路与文本链路显式分离。

---

### 2.6 Kafka consumer 周期性断开

#### 日志现象

```text
Kafka NetworkClient : Node -1 disconnected.
```

#### 问题说明

Kafka consumer 与 broker 之间出现周期性断开。当前只看到 INFO 级别断开日志，没有看到业务消费失败或应用崩溃。

#### 可能原因

- Kafka advertised listeners 配置不适合容器网络。
- broker 地址解析偶发失败。
- consumer 空闲连接被断开。
- consumer group rebalance。
- Docker 网络短暂抖动。

#### 影响

目前看不是致命问题，但如果伴随消息消费延迟、丢失或重平衡频繁，会影响分布式消息功能。

#### 建议解决方案

1. 检查 `KAFKA_ADVERTISED_LISTENERS` 是否面向容器网络使用服务名，例如：

```text
PLAINTEXT://living-agent-kafka:9092
```

2. 检查 Spring Kafka 配置：

```text
spring.kafka.bootstrap-servers=living-agent-kafka:9092
```

3. 如果只是空闲断开，可先观察。
4. 如出现消费问题，再增加 Kafka client DEBUG 日志排查 consumer rebalance。

---

### 2.7 FounderService 日志过于频繁

#### 日志现象

```text
Founder status refreshed from database: true
Founder status refreshed from database: true
```

该日志在大量请求中重复出现。

#### 问题说明

每个接口或多个拦截环节都可能触发 Founder 状态刷新，并以 INFO 级别输出。

#### 影响

- 日志噪声大。
- 掩盖真正错误。
- 可能增加数据库访问频率。

#### 建议解决方案

1. 给 Founder 状态增加短 TTL 缓存，例如 5～30 秒。
2. 将正常刷新日志从 `INFO` 降为 `DEBUG`。
3. 只在状态变化时输出 `INFO`。

---

## 3. 前端服务：`living-agent-frontend`

### 3.1 前端服务整体正常

#### 日志现象

```text
Configuration complete; ready for start up
GET / HTTP/1.1 200
GET /assets/index-*.js HTTP/1.1 200
```

说明 Nginx 正常启动，静态资源正常返回。

---

### 3.2 未登录访问 `/api/auth/me` 返回 401

#### 日志现象

```text
GET /api/auth/me HTTP/1.1 401
GET /login HTTP/1.1 200
```

#### 问题说明

这是未登录访问首页时的正常行为。前端检测到未登录后跳转 `/login`。

#### 结论

不是错误，无需处理。

---

### 3.3 登录后接口正常

#### 日志现象

```text
POST /api/auth/sms/send HTTP/1.1 200
POST /api/auth/phone/login HTTP/1.1 200
GET /api/agents?tenant_id=tenant_default HTTP/1.1 200
GET /api/dashboard/enterprise/summary HTTP/1.1 200
```

#### 结论

前端代理到后端 API 基本正常。

---

### 3.4 周期性请求较频繁

#### 日志现象

```text
GET /api/notifications/unread-count
GET /api/agents?tenant_id=tenant_default
```

这些请求约每 30 秒出现一次。

#### 影响

- 增加后端请求量。
- 间接触发大量 FounderService 刷新日志。

#### 建议解决方案

1. 降低轮询频率，例如 60～120 秒。
2. 页面不可见时暂停轮询。
3. 合并通知、agent 列表、健康状态等周期请求。
4. 对低频变化数据使用 React Query staleTime/cacheTime。

---

## 4. Qdrant：`living-agent-qdrant`

### 4.1 容器 unhealthy，但服务本身可能正常

#### 日志现象

Qdrant 正常启动：

```text
Qdrant HTTP listening on 6333
Qdrant gRPC listening on 6334
```

但 healthcheck 失败：

```text
OCI runtime exec failed: exec failed: unable to start container process: exec: "curl": executable file not found in $PATH
```

#### 问题说明

Qdrant 镜像中没有 `curl`，但 docker-compose 的 healthcheck 使用了 `curl`，所以健康检查失败。

#### 影响

- Docker 显示容器 unhealthy。
- 依赖 `depends_on.condition: service_healthy` 的服务可能无法正确启动或重启。
- 运维判断容易误判 Qdrant 宕机。

#### 建议解决方案

方案 A：改 healthcheck，不依赖 curl。

如果镜像内有 `wget`：

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q --spider http://localhost:6333/healthz || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 5
```

方案 B：使用自定义 Qdrant 镜像安装 curl。

```dockerfile
FROM qdrant/qdrant:latest
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
```

方案 C：如果不需要 Docker healthcheck，去掉 healthcheck，让后端健康检查负责探测 Qdrant。

---

## 5. Memos：`living-agent-memos`

### 5.1 Memos API 启动失败

#### 日志现象

```text
ValidationError: 2 validation errors for InternetRetrieverConfigFactory
reader.llm.model_name_or_path
  Input should be a valid string, input_value=None
reader.llm.api_base
  Input should be a valid string, input_value=None
```

healthcheck 失败：

```text
curl: (7) Failed to connect to localhost port 8000
```

#### 问题说明

Memos 的 Uvicorn 子进程启动失败，原因是 Internet Retriever 的 LLM 配置缺失：

- `reader.llm.model_name_or_path` 为 `None`
- `reader.llm.api_base` 为 `None`

因此 Memos API 没有成功监听 8000 端口。

#### 影响

- Memos 记忆服务不可用。
- 如果后端依赖 Memos 做长期记忆/检索，会出现降级或失败。
- Docker healthcheck 持续 unhealthy。

#### 其他警告

```text
Failed to create text splitter: Missing required module - 'langchain_text_splitters'
pip install langchain_text_splitters==1.0.0
```

这是文本切分器缺包警告，当前会 fallback 到 simple splitter，不是启动失败主因。

#### 建议解决方案

方案 A：补齐 Internet Retriever LLM 配置。

需要在 Memos 配置或环境变量中提供类似字段：

```yaml
reader:
  llm:
    model_name_or_path: "qwen3.5-27b"
    api_base: "http://living-agent-service:8382/v1"
```

实际字段名需以 Memos 项目的配置文件为准。

方案 B：如果暂时不用 Internet Retriever，则关闭该模块。

例如：

```yaml
internet_retriever:
  enabled: false
```

或通过环境变量关闭。实际变量名需查看 Memos 配置说明。

方案 C：补安装缺失依赖。

```bash
pip install langchain_text_splitters==1.0.0
```

如果使用镜像，建议写入 Dockerfile 或 requirements。

#### 修复优先级

如果当前系统强依赖记忆能力，优先级为 P1；如果当前只做部门文本对话，可暂时降为 P2。

---

## 6. OpenProject：`living-agent-openproject`

### 6.1 服务启动，但 healthcheck 返回 400

#### 日志现象

OpenProject 正常启动：

```text
Puma starting in cluster mode...
Listening on http://0.0.0.0:8080
```

healthcheck 失败：

```text
curl: (22) The requested URL returned error: 400
```

#### 问题说明

OpenProject 服务本身可能已经启动，但 healthcheck 访问的 URL 或 Host header 不符合 OpenProject 要求，导致 HTTP 400。

常见原因：

- healthcheck 访问 `/`，但 OpenProject 对 Host header 有校验。
- 缺少 `OPENPROJECT_HOST__NAME` 或配置与访问 Host 不一致。
- healthcheck 路径不适合作健康检查。

#### 影响

- Docker 显示 unhealthy。
- 依赖 OpenProject healthy 状态的服务可能受影响。
- 运维判断容易误判服务不可用。

#### 建议解决方案

方案 A：修改 healthcheck，加入 Host header。

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -fsS -H 'Host: localhost:8080' http://localhost:8080/ || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 10
```

方案 B：使用 OpenProject 更合适的健康路径。

需要确认当前版本是否提供 health endpoint，例如：

```text
/health_checks/default
/health_checks/all
```

如果可用，建议改为：

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -fsS http://localhost:8080/health_checks/default || exit 1"]
```

方案 C：检查 OpenProject Host 配置。

确保环境变量类似：

```yaml
OPENPROJECT_HOST__NAME: "localhost:8080"
OPENPROJECT_HTTPS: "false"
```

#### 其他警告

```text
OpenProject versions higher than 16.0 will require at least PostgreSQL 17.
```

这是未来版本兼容性警告，当前不阻塞启动。后续升级 OpenProject 前应规划 PostgreSQL 17 迁移。

---

## 7. 建议修复优先级

| 优先级 | 问题 | 原因 |
|---|---|---|
| P0 | 部门 WebSocket 建连启动 `AgentService/Qwen3Neuron` | 与部门文本对话架构冲突，直接影响当前主流程 |
| P0 | 缺少大脑协调、任务识别、部门路由和数字员工分工闭环 | 系统无法体现数字企业自主生命力，任务只变成一次 LLM 回复 |
| P1 | 部门聊天用户消息重复保存 | 影响历史记录、审计和上下文质量 |
| P1 | 缺少自治 Trace 与任务/产物沉淀 | 日志无法证明经历了分析、路由、分工、执行、汇总过程 |
| P1/P2 | Memos 启动失败 | 若依赖长期记忆则为 P1，否则 P2 |
| P2 | Qdrant healthcheck 使用不存在的 curl | 服务可能正常，但健康状态误报 |
| P2 | OpenProject healthcheck 返回 400 | 服务可能正常，但健康状态误报 |
| P3 | Native/AudioNative 不可用 | 文本链路可暂不处理，语音链路必须处理 |
| P3 | FounderService 日志过多 | 非功能错误，但影响排查效率 |
| P3 | Kafka Node -1 disconnected | 暂未看到业务失败，先观察 |

---

## 8. 推荐下一步执行清单

### 8.1 后端代码修复

- [ ] 修改 `DepartmentWebSocketHandler.afterConnectionEstablished()`：部门文本连接不调用 `agentService.startSession(...)`。
- [ ] 修改 `DepartmentWebSocketHandler.afterConnectionClosed()`：部门文本连接不调用 `agentService.endSession(...)`。
- [ ] 删除 `DepartmentWebSocketHandler.handleChatMessage()` 中重复的用户消息保存。
- [ ] 保留 `DepartmentChatService` 作为消息落库统一入口。
- [ ] 新增 `ConversationOrchestrator` 作为部门文本对话总入口。
- [ ] 新增 `DialogueAnalyzer`，判断输入是闲聊、咨询、任务、项目、审批还是跨部门需求。
- [ ] 新增 `BrainRouter`，选择主责部门大脑和协同部门大脑。
- [ ] 新增 `TaskPlanningService`，将可执行任务拆解为带验收标准的 `WorkPlan`。
- [ ] 新增 `DepartmentWorkCoordinator` / `EmployeeDispatcher`，让部门大脑把子任务分派给部门数字员工。
- [ ] 新增 `ResultAggregator`，汇总员工执行结果并做质量检查。
- [ ] 新增 `AutonomyTraceService`，输出 `dialogue_analyzed`、`brain_routed`、`work_plan_created`、`employee_assigned`、`result_aggregated` 等结构化日志。
- [ ] 将执行结果沉淀到 Memory / Knowledge / Performance / Task / Project 等系统。

### 8.2 Compose / 容器配置修复

- [ ] 修正 Qdrant healthcheck，避免使用镜像中不存在的 `curl`。
- [ ] 修正 OpenProject healthcheck 路径或 Host header。
- [ ] 修正 Memos Internet Retriever LLM 配置，或关闭 Internet Retriever。
- [ ] Memos 镜像补装 `langchain_text_splitters==1.0.0`。

### 8.3 日志与观测优化

- [ ] `FounderService` 正常刷新日志降级为 DEBUG。
- [ ] 为 Founder 状态增加 TTL 缓存。
- [ ] 降低前端轮询频率或页面不可见时暂停轮询。
- [ ] 观察 Kafka 断开是否伴随消费失败。

---

## 9. 验证方法

### 9.1 验证部门 WebSocket 不再启动 Qwen3Neuron

打开部门聊天页面后，查看后端日志，应不再出现：

```text
Qwen3Neuron neuron://chat/qwen3/001 subscribed
AgentService : Bound chat neuron to coordinator session
AgentService : Session started
```

发送部门文本消息后，应看到：

```text
DepartmentWebSocketHandler : processWithBrain: dept=tech
DepartmentChatService : External subscriber ... subscribed to channel channel://output/text
TechBrain : TechBrain processing message
```

### 9.2 验证消息不重复保存

发送一条部门聊天消息后，日志中应只有一次用户消息保存：

```text
Saved chat message: dept=tech, user=<user>, role=user
Saved chat message: dept=tech, user=brain_tech, role=assistant
```

不应出现两条相同 `role=user`。

### 9.3 验证自治流程日志

发送一条可执行部门任务，例如：

```text
帮我做一个跳动的小球的网页
```

期望后端日志不仅看到 `TechBrain processing message`，还应看到完整自治阶段：

```text
[AutonomyTrace] stage=dialogue_analyzed kind=TASK intent=web_prototype primary=tech
[AutonomyTrace] stage=brain_routed primaryBrain=TechBrain
[AutonomyTrace] stage=work_plan_created steps=...
[AutonomyTrace] stage=employee_assigned employee=...
[AutonomyTrace] stage=work_step_completed status=SUCCESS
[AutonomyTrace] stage=result_aggregated status=SUCCESS
[AutonomyTrace] stage=knowledge_recorded
```

如果只是看到：

```text
DepartmentWebSocketHandler : processWithBrain
TechBrain : TechBrain processing message
Saved chat message: role=assistant
```

说明仍然只是“部门大脑单次回复”，尚未形成大脑协调、部门分工和数字员工执行闭环。

### 9.4 验证 Qdrant healthcheck

```bash
docker inspect --format "{{json .State.Health}}" living-agent-qdrant
```

期望：

```text
"Status":"healthy"
```

### 9.5 验证 Memos

```bash
docker logs --tail 100 living-agent-memos
```

不应再出现：

```text
ValidationError: InternetRetrieverConfigFactory
reader.llm.model_name_or_path
reader.llm.api_base
```

并且 healthcheck 不应再显示：

```text
Failed to connect to localhost port 8000
```

### 9.6 验证 OpenProject

```bash
docker inspect --format "{{json .State.Health}}" living-agent-openproject
```

期望 `healthy`。如果仍是 unhealthy，检查 healthcheck URL 是否返回 400。
