## Living Agent 项目 WebSocket 架构审查报告

基于"方案三：WebSocket 持久连接"标准架构的对照审查，涵盖后端（living-agent-gateway）与桌面客户端（living-agent-desktop）。

---

### 一、整体架构判断

项目确实采用了方案三所述的 WebSocket 持久连接架构，但实现并不完整。后端基于 Spring Boot WebSocket（原生 JSON 协议，非 STOMP）构建了一套相当完善的连接管理和消息路由体系；桌面客户端基于 Electron + React + TypeScript，内嵌了 WebSocket 客户端。**然而存在一个关键缺陷：客户端的 WebSocket 连接从未被实际调用**，导致所有实时通信功能处于瘫痪状态，系统实质上退化为纯 HTTP 轮询模式。

---

### 二、后端实现评估

后端 WebSocket 层主要在 `living-agent-gateway` 模块中实现，整体设计比较扎实。

**端点拓扑：** 在 `WebSocketConfig.java` 中注册了四类端点——`/ws/agent`（Agent 直聊）、`/ws/dept/*`（部门频道）、`/ws/enterprise`（企业级频道）、`/ws/public`（公共访客频道），分别由 `AgentWebSocketHandler` 和 `DepartmentWebSocketHandler` 处理。

**连接管理：** 使用多层 `ConcurrentHashMap` 做内存中的会话追踪。`AgentWebSocketHandler` 维护了 5 个 Map（sessionId→Session、sessionToAgent、sessionToUser、sessionAccessLevel、sessionDepartment）。`DepartmentWebSocketHandler` 更复杂，增加了部门分组、连接时间、最后活跃时间、发送锁等维度，并设置了全局 500 / 每部门 50 的连接上限。此外还有 `ConnectionRegistry` / `InMemoryConnectionRegistry` 提供统一的注册表，支持按 taskKey、executionId、projectKey、conversationId 做反向查找。

**消息协议：** 纯 JSON 文本帧，无 STOMP。客户端→服务器的消息类型包括 `text`、`audio`、`audio_full`、`control`、`ping`、`abort`（Agent 频道）以及 `CHAT`、`TYPING`、`PING`（部门频道）。服务器→客户端的消息类型更丰富，包括 `connected`、`reconnected`、`done`、`thinking`、`chunk`、`progress`、`artifact`、`error`、`pong`/`PONG`、`execution_progress`、`execution_event`、`employee_task_update` 等。

**心跳机制：** 服务端有专门的守护线程 `ws-heartbeat`，每 30 秒扫描一次 `sessionLastActive`，超过 60 秒无活动的连接会被强制关闭。客户端每 30 秒发送一次 `ping`。两者配合，能有效清理僵尸连接。

**认证与授权：** Spring Security 对 `/ws/**` 路径做了 `permitAll()` 放行，认证在 WebSocket 处理器内部完成。Token 提取支持三级回退：`Sec-WebSocket-Protocol` 头 → `Authorization` 头 → URL 查询参数。认证后通过 `UnifiedAuthService.validateSession()` 获取 `AuthContext`（含 employeeId、accessLevel、department、tenantId）。授权层面，Agent 频道检查 `AccessGateService.canRoute()`，部门频道检查 `DepartmentAccessService.hasDepartmentAccess()`。

**重连机制：** Agent 频道实现了会话挂起/恢复——断连后会话进入 `suspendedSessions`（TTL 5 分钟），客户端重连时传入 `?sessionId=旧ID` 即可恢复上下文和历史记录。部门频道则通过 `EventQueueService` 实现持久化事件缓冲，断连期间的事件会入库保存，重连后回放。`SessionPersistenceService` 还会将 `ConnectionContext` 持久化到数据库，支持跨重启恢复。

**并发安全：** 全部使用 `ConcurrentHashMap`，发送操作使用 per-session 的 `ReentrantLock`（`sendTextSafely()` 方法），异步处理使用 `CompletableFuture`。

**任务分发与结果推送：** 消息到达后经由 `ConversationOrchestrator` → Brain → Employee 分发管线处理，执行过程中通过 `pushProgress()` 和 `pushExecutionEvent()` 实时推送进度，最终结果通过 `done` 消息返回。

---

### 三、桌面客户端实现评估

客户端是一个 Electron 42 + Vite + React 19 + TypeScript 的桌面应用，打包为 Windows NSIS 安装包。

**WebSocket 客户端：** `ws-client.ts` 使用 `ws` Node.js 库实现了一个单例 `WSClient` 类，具备完整的连接建立、消息收发、事件分发、心跳和重连逻辑。消息格式为 `{ type, data, timestamp }`，支持按事件类型注册监听器，也支持通配符监听。心跳每 30 秒发送 `ping`，断连后 5 秒固定间隔重连。

**关键问题——连接从未建立：** 代码中 `wsClient.connect()` 从未在任何模块中被调用。`index.ts` 的启动序列包括加载后端 URL、生成 clientId、注册 IPC、创建窗口、启动连接监控（HTTP 健康检查）、初始化任务通知等步骤，但缺少 `wsClient.connect()` 这一关键调用。三个模块（`task-board-tray.ts`、`task-notification.ts`、`local-save-sync.ts`）虽然注册了 WebSocket 事件监听器，但由于连接从未建立，这些监听器永远不会触发。

**客户端能力：** 桌面客户端的定位是任务管理和制品同步客户端，**不具备本地系统命令执行或 COM 对象操作能力**。它能做的事情包括：通过 REST API 浏览/领取任务、接收 WebSocket 实时事件（当前不工作）、将制品下载到本地目录、系统托盘通知、全局快捷键等。实际的 Windows 自动化（pywinauto）运行在独立的节点上，后端根据 `clientId` 路由任务到对应的 pywinauto 节点。

**认证：** 手机号 + 短信验证码登录，Token 通过 Electron 的 `safeStorage` API（Windows DPAPI）加密存储在 `{userData}/token.enc`。如果加密不可用，会降级为明文存储。

**ClientId：** 首次启动时生成 UUID v4，持久化在 `{userData}/client-id.json`，所有 HTTP 和 WebSocket 请求都携带此 ID，用于审计追踪和任务路由。

---

### 四、与方案三标准的差距分析

以下对照方案三（WebSocket 持久连接）的核心要素逐项评估。

**客户端注册机制：** 方案三要求客户端连接后发送 `register` 消息声明身份。项目中后端在 WebSocket 握手阶段通过 Token 和查询参数（agentId、clientId）完成身份识别，不需要额外的 register 消息。这是一种合理的替代方案，但意味着客户端无法在连接后动态切换身份。

**双向实时通信：** 方案三的核心价值。后端实现完善——支持服务器主动推送进度、事件、通知到客户端。但客户端因 `connect()` 未调用，实际无法接收任何推送消息，双向通信断裂。

**心跳保活：** 方案三要求定期心跳防止连接被中间件断开。后端（30s 僵尸检测 + 60s 超时）和客户端（30s ping）代码层面都实现了，但由于客户端未连接，心跳实际未运行。

**自动重连：** 方案三要求断连后自动重连。后端有会话挂起/恢复和事件回放机制。客户端代码有 5 秒固定间隔重连逻辑，但同样因为未连接而失效。

**任务分发与结果回传：** 后端通过 `ConversationOrchestrator` 管线分发任务，通过 WebSocket 实时推送执行进度和结果。由于客户端未连接，这些推送只会发送到 Web 前端（如果有的话），桌面客户端无法接收。

**多客户端管理：** 后端有连接数限制（全局 500 / 部门 50）、在线状态广播（`ONLINE_USERS`、`USER_JOINED`、`USER_LEFT`），基本符合方案三的要求。

---

### 五、发现的问题清单

#### 严重问题（CRITICAL）

**1. 桌面客户端 WebSocket 从未连接**

`ws-client.ts` 中的 `wsClient.connect()` 在整个代码库中从未被外部调用。`index.ts` 启动序列缺少这一步。所有依赖 WebSocket 的功能（实时任务通知、执行事件推送、制品就绪通知）全部不工作。系统退化为 HTTP 轮询模式（任务列表每 5 分钟刷新一次，健康检查每 30 秒一次）。

修复方式：在 `index.ts` 中用户登录成功（或从存储中加载到有效 Token）后调用 `wsClient.connect('/ws/agent', { clientId })`，并在 Token 变更时重新连接。

**2. Agent 频道重连特性实际未启用**

后端实现了会话挂起/恢复机制（5 分钟 TTL），客户端重连时传入 `?sessionId=旧ID` 即可恢复上下文。但前端 `Chat.tsx` 重连时并未传递 `sessionId` 参数，只传了 `token` 和 `agentId`。这意味着后端的会话挂起/恢复功能形同虚设。

#### 中等问题（MODERATE）

**3. Token 通过 WebSocket URL 查询参数传递**

桌面客户端将 auth token 放在 URL 查询参数中（`?token=...`），这是服务端代码明确标记为 "deprecated" 的方式。Token 会出现在服务器访问日志、代理日志和浏览器历史中。服务端支持更安全的 `Sec-WebSocket-Protocol` 头方式，但客户端未使用。

**4. Agent 频道缺少僵尸连接检测**

`AgentWebSocketHandler` 不像 `DepartmentWebSocketHandler` 那样追踪 `sessionLastActive` 和实现僵尸连接清理。如果 Agent 频道的 WebSocket 连接静默断开（例如网络中断但 TCP 连接未正式关闭），服务端可能长时间保留无效会话。

**5. 无 WebSocket 连接超时**

`ws-client.ts` 创建 WebSocket 时未设置 `handshakeTimeout`。如果服务器无响应，连接尝试可能无限期挂起。

**6. 固定重连间隔，无指数退避**

桌面客户端使用固定 5 秒间隔重连，前端 `Chat.tsx` 使用固定 2 秒间隔。在服务器长时间不可用时，会产生大量无效连接请求。相比之下，`DepartmentChatInline.tsx` 实现了指数退避（2s→4s→8s→...→30s 上限，最多 10 次），更合理。

**7. Pong 响应类型不一致**

`AgentWebSocketHandler` 返回 `{"type": "pong"}`（小写），`DepartmentWebSocketHandler` 返回 `{"type": "PONG"}`（大写）。虽然前端做了兼容处理，但对第三方客户端不友好。

**8. 无 HandshakeInterceptor**

WebSocket 端点未配置 `HandshakeInterceptor`，认证在连接建立之后才进行。无效连接会短暂消耗服务器资源。可通过 HandshakeInterceptor 在握手阶段拒绝未认证的连接。

**9. Token 明文降级存储**

`auth.ts` 中，当 `safeStorage.isEncryptionAvailable()` 返回 false 时，Token 会以明文写入磁盘。虽然 Windows 上 DPAPI 几乎总是可用，但降级策略存在安全风险。

**10. 无 Token 刷新机制**

登录响应包含 `refreshToken` 字段，但客户端从未使用。AccessToken 过期后用户必须重新用手机号 + 短信验证码登录，体验很差。

**11. 无消息频率限制**

两个 WebSocket Handler 都没有消息频率限制。恶意客户端可以大量发送消息，可能导致后端异步处理管线（特别是昂贵的 LLM 调用）过载。

#### 轻微问题（MINOR）

**12. 优雅关闭不完整**

`DepartmentWebSocketHandler` 的 `@PreDestroy` 方法关闭了心跳调度器，但未显式关闭所有活跃的 WebSocket 会话。`AgentWebSocketHandler` 完全没有 `@PreDestroy`。应用重启时客户端可能经历不干净的断连。

**13. Agent 频道会话历史过小**

`AgentService.SessionContext.addHistory()` 将对话历史限制为 10 条，对于复杂对话场景偏小。部门频道使用数据库存储历史，无此限制。

**14. `DEFAULT_BACKEND_URL` 为空字符串**

`src/shared/types.ts` 中 `DEFAULT_BACKEND_URL = ''`，`connection.ts` 的健康检查回退为 `fetch('/api/health')`，会请求 Electron 应用本身。

**15. `sandbox: false` 配置**

窗口配置中 `sandbox: false`，虽然 `contextIsolation: true` 和 `nodeIntegration: false` 提供了基本隔离，但关闭沙箱意味着渲染进程可通过 preload 脚本间接访问 Node.js API。

**16. `computeStats()` 未实现**

`local-save-config.ts` 中的 `computeStats()` 函数始终返回零值，标记了 TODO 但未完成。

---

### 六、修复优先级建议

最高优先级是修复桌面客户端的 WebSocket 连接问题（问题 1）。这是所有实时功能的基础，修复后系统的实时任务通知、执行事件推送、制品同步等能力将立即恢复。建议在 `index.ts` 的启动流程中，在 Token 可用后立即调用 `wsClient.connect()`，同时在 Token 刷新后重新建立连接。

第二优先级是修复 Agent 频道的重连参数传递（问题 2）和 Agent 频道的僵尸连接检测（问题 4），让会话恢复机制真正生效。

第三优先级是安全相关的改进：迁移 Token 传递方式到 `Sec-WebSocket-Protocol` 头（问题 3），实现 Token 刷新（问题 10），添加 HandshakeInterceptor（问题 8）。

第四优先级是稳定性改进：添加连接超时（问题 5），改用指数退避重连（问题 6），添加消息频率限制（问题 11）。

---

### 七、架构总结

```
┌───────────────────────────────────────────┐
│  Spring Boot 后端 (living-agent-gateway)   │
│                                           │
│  WebSocket Endpoints:                     │
│    /ws/agent      → AgentWebSocketHandler │
│    /ws/dept/*     → DeptWebSocketHandler  │
│    /ws/enterprise → DeptWebSocketHandler  │
│    /ws/public     → DeptWebSocketHandler  │
│                                           │
│  连接管理: ConcurrentHashMap + 连接上限     │
│  心跳: 30s扫描 + 60s超时 (仅部门频道)       │
│  认证: Token (3级回退提取)                  │
│  重连: 会话挂起(5min) + 事件队列回放        │
│  并发: per-session ReentrantLock           │
│  持久化: JPA SessionContext + PendingEvent │
└──────────────────┬────────────────────────┘
                   │ WebSocket (JSON)
                   │ ⚠ 桌面客户端从未连接
                   │
┌──────────────────┴────────────────────────┐
│  Electron 桌面客户端 (living-agent-desktop)│
│                                           │
│  ws-client.ts: WSClient 单例 (ws库)       │
│    ⚠ connect() 从未被调用                  │
│    心跳: 30s ping (未运行)                 │
│    重连: 5s固定间隔 (未运行)                │
│                                           │
│  api-client.ts: REST API (httpx)          │
│    ✅ Bearer Token + X-Client-Id          │
│    ✅ 手机号 + 短信验证码登录               │
│                                           │
│  connection.ts: 健康检查 (30s HTTP轮询)    │
│  task-board-tray.ts: 任务轮询 (5min HTTP) │
│  local-save-sync.ts: 制品下载同步          │
│  auth.ts: Token加密存储 (DPAPI)            │
│  client-id.ts: 持久化UUID                 │
│                                           │
│  ⚠ 无系统命令执行能力                      │
│  ⚠ 无COM对象操作能力                       │
└───────────────────────────────────────────┘
```

项目整体设计方向正确，后端的 WebSocket 基础设施相当成熟，连接管理、消息路由、会话持久化、事件回放等核心能力都已实现。最大的短板在客户端侧——WebSocket 连接从未建立，使得后端的实时推送能力无法触达桌面用户。修复这个单一问题后，系统的实时性和用户体验将有质的提升。
