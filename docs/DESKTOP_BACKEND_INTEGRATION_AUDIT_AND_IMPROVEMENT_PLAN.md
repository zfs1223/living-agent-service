# 桌面端 ↔ 后端对接闭环审计与改进计划

> **目的**：以"桌面端 ↔ 后端对接方式"为主线，逐一梳理 `living-agent-desktop` 与 `living-agent-gateway`/`living-agent-core` 之间每个功能的逻辑闭环，绘制闭环关系网，并集中暴露闭环中尚未实现或不一致的环节，给出可执行的修复方案。
>
> **生成时间**：2026-06-29
> **关联文档**：
> - 代码结构总览：`docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`
> - 项目规则：`.trae/rules/project_rules.md`（统一 ApiResponse、API 路径不带末尾斜杠、不硬编码模型等）
> - 已知问题（后端）：`docs/CODE_LOGIC_AND_FLOW_ISSUES.md`、`docs/TASK_MODULE_CODE_PATH_ISSUES.md`
> - WebSocket 重连方案：`docs/pending/WEBSOCKET_RECONNECT_SOLUTION.md`、`docs/pending/WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md`
>
> **⚠️ 未完成项提取**：本文档中尚未完成的改进项（断点B3/B11、P2代码质量8项、审批精简4项）已提取至 [IMPROVEMENT_PLAN_PENDING_ITEMS.md](IMPROVEMENT_PLAN_PENDING_ITEMS.md) 统一跟踪实施进度。本文档保留完整历史记录，新改进实施请参阅提取文件。

---

## 目录

### 第一部分：桌面端 ↔ 后端对接闭环审计
1. [对接方式总览](#1-对接方式总览)
2. [功能闭环清单](#2-功能闭环清单)
3. [闭环关系网](#3-闭环关系网)
4. [问题清单（按严重度）](#4-问题清单按严重度)
5. [修复任务与优先级](#5-修复任务与优先级)
6. [验收标准](#6-验收标准)

### 第二部分：后端功能闭环深度审计
7. [后端模块矩阵](#7-后端模块矩阵)
8. [闭环断点详解](#8-闭环断点详解)
9. [后端闭环关系网](#9-后端闭环关系网)
10. [后端问题清单](#10-后端问题清单)
11. [后端修复任务与优先级](#11-后端修复任务与优先级)
12. [后端验收标准](#12-后端验收标准)

### 第三部分：审批机制精简方案——移除工具执行审批
13. [现状分析：工具审批闸门名存实亡](#13-现状分析工具审批闸门名存实亡)
14. [工具执行审批移除设计](#14-工具执行审批移除设计)
15. [大脑工具分配校验链路（替代工具审批）](#15-大脑工具分配校验链路替代工具审批)
16. [工具审批移除任务清单](#16-工具审批移除任务清单)
17. [工具审批移除验收标准](#17-工具审批移除验收标准)

### 第四部分：前端 ↔ 后端对接闭环审计
18. [前端架构与对接总览](#18-前端架构与对接总览)
19. [前端功能闭环清单](#19-前端功能闭环清单)
20. [前端问题清单](#20-前端问题清单)
21. [前端修复任务与优先级](#21-前端修复任务与优先级)
22. [前端验收标准](#22-前端验收标准)

### 第五部分：所有改进项冲突分析与实施路线图
23. [改进项全景图](#23-改进项全景图)
24. [跨部分依赖与冲突分析](#24-跨部分依赖与冲突分析)
25. [整体实施路线图](#25-整体实施路线图)

---

## 1. 对接方式总览

桌面端与后端存在 **三类通信通道**，桌面端代码同时使用了三条通道，且在多个文件中存在重复或矛盾的实现。

| 通道 | 桌面端入口 | 后端入口 | 用途 |
|------|-----------|---------|------|
| **REST API** | [api-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts) （统一 `apiRequest<T>`）、渲染层 `App.tsx`/`OfficeChatPage.tsx` 直接 `fetch` | 各 `*Controller` | 登录、任务/项目/审批/消息/产物等资源类操作 |
| **主进程 WebSocket** | [ws-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts) 单例 `wsClient` | `/ws/agent` → `AgentWebSocketHandler` | 任务推送、Windows 自动化双向调用、心跳 |
| **渲染进程 WebSocket** | [OfficeChatPage.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx) 独立 `new WebSocket(...)` | `/ws/dept/{dept}` → `DepartmentWebSocketHandler` | 部门聊天对话 |

### 1.1 鉴权链路

| 资源 | 鉴权方式 | 桌面端实现 | 后端实现 |
|------|---------|-----------|---------|
| REST API | `Authorization: Bearer {token}` + `X-Client-Id` 头 | [api-client.ts:97-100](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L97) 自动注入 | `SessionAuthenticationFilter` + `ClientIdFilter` |
| 主进程 WS | `Sec-WebSocket-Protocol: bearer.{token}` | [ws-client.ts:77](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L77) | `AuthHandshakeInterceptor` 三级优先级解析 |
| 渲染层 WS | query string `token=...`（兼容降级） | OfficeChatPage URL 拼接 | 同上（URL 参数为最低优先级） |

### 1.2 Token 存储

- 桌面端：Electron `safeStorage` 加密落盘 `{userData}/token.enc`（[auth.ts:21](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/auth.ts#L21)）
- 后端：`SessionEntity` 持久化到 PostgreSQL，刷新时旧 token 立即失效（令牌轮换）

---

## 2. 功能闭环清单

按"桌面端 → REST/WS → 后端 → 推送 → 桌面端 UI"梳理 12 个核心闭环。

### 闭环 1：登录认证

```
[桌面端] App.tsx 输入手机号
  → IPC auth:sms-send
  → [主进程] api-client.ts POST /api/auth/sms/send
  → [后端] PhoneAuthController.smsSend 生成验证码
  ← 返回 {message, expiresIn, code}        ⚠ 验证码明文返回
[桌面端] 用户输入验证码
  → IPC auth:phone-login
  → [主进程] POST /api/auth/phone/login
  → [后端] PhoneAuthController.phoneLogin → UnifiedAuthService.createSession
  ← 返回 {token, employee}
[桌面端] auth.ts saveToken (safeStorage 加密)
  → 触发 auth:changed 事件
  → wsClient.reconnectWebSocket()
  → IPC auth:me → GET /api/auth/me
  → 渲染层获取用户信息并渲染
```

**关联文件**
- 桌面端：[auth.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/auth.ts)、[api-client.ts:237-260](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L237)
- 后端：[PhoneAuthController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/PhoneAuthController.java)、[AuthController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java)

**已知问题**
- 🔴 [后端] [PhoneAuthController.java:104](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/PhoneAuthController.java#L104) `SendSmsResponse.code` 把验证码明文返回客户端，任意调用 `/api/auth/sms/send` 即可绕过短信验证
- 🟠 [后端] [AuthController.java:225-267](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java#L225) `PATCH /api/auth/me` 未调用任何 service 持久化
- 🟠 [桌面端] [api-client.ts:166](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L166) `refreshToken` 未携带 `X-Client-Id` 头
- 🟡 [后端] [AuthController.java:383](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java#L383) 权限检查硬编码字符串比较，未与 `DepartmentPermissionInterceptor` 对齐

---

### 闭环 2：WebSocket 主连接

```
[桌面端] index.ts 启动
  → loadBackendUrl() 从 {userData}/backend-config.json 读取
  → auth.loadToken() 加密 token
  → wsClient.connect('/ws/agent')
     query: clientId, hostname, macAddress, platform, osUser, applications
     subprotocol: bearer.{token}
  → [后端] AuthHandshakeInterceptor 验证 token
  → AgentWebSocketHandler.afterConnectionEstablished
     → ClientDeviceRegistryService.registerOrUpdate
     → WindowsAutomationClientGatewayImpl.registerSession(clientId, session)
     → proactiveOrchestrator.runForUser (首次连接推送主动汇报)
[桌面端] 30s 心跳 {type:'ping'}
  → [后端] sendPong
[桌面端] 监听 win_automation_call / public_task_* / employee_task_update / execution_event
```

**关联文件**
- 桌面端：[ws-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts)、[app-scanner.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/app-scanner.ts)、[client-id.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/client-id.ts)
- 后端：[AgentWebSocketHandler.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java)、[AuthHandshakeInterceptor.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AuthHandshakeInterceptor.java)、[InMemoryConnectionRegistry.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/InMemoryConnectionRegistry.java)

**已知问题**
- 🟠 ~~[桌面端] [ws-client.ts:184-189](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L184) 发送 ping 但未检测 pong 超时~~ ✅ 已修复（阶段2）：新增 pong 超时检测，超过 2*pingInterval 未收到 pong 触发重连
- 🟠 ~~[桌面端] [ws-client.ts:39,54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L39) `cachedApps` 只在首次连接时扫描~~ ✅ 已修复（阶段2）：新增 `invalidateAppsCache()` 方法
- 🟡 ~~[桌面端] [connection.ts:41](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/connection.ts#L41) 心跳间隔硬编码 30_000~~ ✅ 已修复（阶段2）：改为使用 `SHARED_CONSTANTS.HEARTBEAT_INTERVAL_MS`
- ✅ [后端] [InMemoryConnectionRegistry.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/InMemoryConnectionRegistry.java) 反向索引内存 + DB 回退查询 + 启动时从 DB 恢复活跃 session（B-0-2 已完成，阶段4）
- 🟡 [后端] [DepartmentWebSocketHandler.java:88-89](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L88) 移除了 zombie 检测，崩溃连接可能永久占用

---

### 闭环 3：部门聊天（核心业务）

```
[桌面端] OfficeChatPage 渲染层
  → fetch GET /api/dept/{dept}/conversations           ← 加载对话列表
  → fetch GET /api/dept/{dept}/conversations/history    ← 加载历史
  → new WebSocket('/ws/dept/{dept}?conversationId=&clientId=')
  → 用户输入消息
  → ws.send({type:'CHAT', content, conversationId})
  → [后端] DepartmentWebSocketHandler.handleChatMessage
  → DepartmentChatService.processWithBrain
  → ConversationOrchestrator.orchestrate
     → DialogueAnalyzer.analyze         (LLM 意图分析)
     → TaskRouteClassifier.classify     (单部门/跨部门/需澄清)
     → MainBrainTaskDirector.plan       (LLM 主脑规划)
     → FixedEmployeeDispatcher.dispatch (LLM 员工分派)
     → AssignmentPreparationService.prepare
     → DepartmentExecutionCoordinator.coordinate
  → EmployeeExecutionReceiptService.record
  → ExecutionReceiptTaskProjectBridge.onReceiptRecorded
     → TaskRepository 更新任务状态
     → ProjectRepository 更新项目统计
     → RuntimeEventStore 记录 Trace
  → DepartmentChatService.saveMessage (user + assistant)
  → WS 推送 done/execution_progress/employee_task_update/execution_event/proactive_report
[桌面端] OfficeChatPage 接收并渲染
```

**关联文件**
- 桌面端：[OfficeChatPage.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx)、[App.tsx:918-924](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L918)
- 后端：[DepartmentWebSocketHandler.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java)、[DepartmentChatService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java)、[ConversationController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ConversationController.java)
- Core：`core/autonomy/ConversationOrchestrator`、`core/brain/impl/*Brain`、`core/autonomy/impl/LlmBased*`

**已知问题**
- 🔴 [桌面端] [App.tsx:733,792,854,919,924](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L733) 和 [OfficeChatPage.tsx:126,214,234](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L126) `localStorage.getItem('desktop_token')` 永远返回 null（token 实际在 safeStorage），属于死代码；每次都要异步 `await window.livingAgentAPI.auth.getToken()` 拼接
- 🟠 [桌面端] [OfficeChatPage.tsx:278-313](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L278) 渲染层 WS 也处理 `win_automation_call`，与主进程 WSClient 重复（详见闭环 5）
- 🟠 [桌面端] [OfficeChatPage.tsx:478-488](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L478) 重连最多 5 次，主进程 WSClient 无限重连，两套策略不一致
- 🟠 [桌面端] [OfficeChatPage.tsx:506](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L506) 重连 useEffect 依赖 `conversationId`，切换对话会重建 WS
- 🟠 [后端] [DepartmentWebSocketHandler.java:366-377](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L366) `default` 分支把未知消息类型当作 CHAT 处理，恶意 payload 直入 LLM
- 🟡 [后端] [ConversationController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ConversationController.java) 与桌面端调用的 `/api/dept/{dept}/conversations` 路径不一致，需核对（CODE_STRUCTURE 中标注 `DepartmentConversationController` 实际不存在）

---

### 闭环 4：任务看板（公开任务）

```
[桌面端] task-board-tray.ts 启动
  → 拉取一次 GET /api/tasks/public
  → 启动 5min 定时轮询兜底 (constants.POLL_INTERVAL_MS)
  → 订阅 WS 事件:
      public_task_published
      public_task_updated
      public_task_claimed
  → task-board-cache.cacheVisibleTasks() 按部门分组写入 {userData}/cache/task-board/{year}/{month}/{dept}.json
[渲染层] PublicTaskBoard.tsx
  → IPC taskboard:list → 主进程 GET /api/tasks/public
  → IPC taskboard:claim → POST /api/tasks/{taskId}/claim
[后端] TaskController.getPublicTasks (无登录)
  → TaskController.claimPublicTask (需登录)
  → PublicTaskEventPublisher.publish → WS 广播 public_task_claimed
[桌面端] task-board-tray 接收事件
  → setBadgeVisible(count > 0) 更新托盘红点
  → IPC 推送 taskboard:count-changed 到渲染层
```

**关联文件**
- 桌面端：[task-board-tray.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-tray.ts)、[task-board-cache.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-cache.ts)、[ipc.ts:149-165](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L149)、[PublicTaskBoard.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/components/PublicTaskBoard.tsx)
- 后端：[TaskController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java)、`PublicTaskEventPublisher`

**已知问题**
- 🔴 [桌面端] [task-board-cache.ts:77-78](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-cache.ts#L77) 过滤逻辑 bug：`if (!dept && f === 'all.json') continue;` 应为 `!==`，导致无 dept 参数时 `all.json` 缓存被跳过，离线缓存实际不可用
- 🟡 [桌面端] [ipc.ts:149-165](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L149) `taskboard:cache` 和 `taskboard:list` 都调用 `getPublicTasks()`，渲染层并发调用会触发重复请求
- 🟡 [桌面端] [task-board-cache.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-cache.ts) 每部门最多 100 条，TTL 24 小时，但账号切换时仅 `clearAllCache()` 不重新拉取

---

### 闭环 5：Windows 自动化（双通道：HTTP + WebSocket）

#### 5.1 通道 A：HTTP+pywinauto（WindowsAppTool，业务化）

```
[后端] AI 大脑 → ToolNeuron → WindowsAppTool.execute()
  → WindowsAppTool 通过 HTTP 调用 scripts/windows_automation/server.py
  → server.py 通过 pywinauto 控制金蝶 KIS 等桌面应用
[客户端电脑] server.py 启动
  → POST /api/windows-automation/nodes/register  ← ⚠ 需要 FULL 权限
  → 每 60s POST /api/windows-automation/nodes/{nodeId}/heartbeat
[后端] WindowsAutomationController 同步节点到 WindowsAppTool.addNode()
```

#### 5.2 通道 B：WebSocket 桥接（WindowsAutomationTool，通用）

```
[后端] AI 大脑 → WindowsAutomationTool.execute()
  → WindowsAutomationClientGatewayImpl.sendCall(clientId, op, args)
  → 通过 WS 推送 {type:'win_automation_call', data:{id, operation, args}}
[桌面端主进程] ws-client.ts 收到 win_automation_call
  → winAutomationService.execute(operation, args)
  → 通过 stdin 发送 JSON 到 service.py 子进程
  → service.py 调用 UIA/PowerShell/注册表/文件系统
  → 通过 stdout 返回 JSON 结果
  → wsClient.send('win_automation_response', {id, success, result|error})
[后端] WindowsAutomationClientGatewayImpl 等待对应 id 的响应
  → 超时 30s
```

**关联文件**
- 桌面端：[win-automation-service.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/win-automation-service.ts)、[ws-client.ts:165-182](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L165)、`resources/win-automation/service.py`
- 后端：`core/tool/impl/WindowsAppTool.java`、`core/tool/impl/WindowsAutomationTool.java`、`core/websocket/WindowsAutomationClientGateway`、[WindowsAutomationClientGatewayImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/WindowsAutomationClientGatewayImpl.java)、[WindowsAutomationController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/WindowsAutomationController.java)

**已知问题**
- 🔴 [后端] [WebMvcConfig.java:55](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/WebMvcConfig.java#L55) + [DepartmentPermissionInterceptor.java:86-106](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/interceptor/DepartmentPermissionInterceptor.java#L86) `/api/windows-automation/**` 强制要求 `AccessLevel.FULL`，桌面端 `server.py` 节点注册/心跳会被 403 拒绝
- 🔴 [桌面端] [ws-client.ts:90-93](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L90) 和 [OfficeChatPage.tsx:341-343](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L341) 主进程和渲染层都监听 `win_automation_call`，若后端向 `/ws/agent` 和 `/ws/dept/*` 都推送，会重复执行
- 🟠 [后端] [WindowsAutomationClientGatewayImpl.java:190-195](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/WindowsAutomationClientGatewayImpl.java#L190) `pendingRequests` 以 `requestId` 为 key，无 `clientId→requestIds` 反向映射，客户端断开后挂起请求必须等 30s 超时
- 🟠 [后端] [AgentWebSocketHandler.java:174](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java#L174) 和 [DepartmentWebSocketHandler.java:233](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L233) 同一桌面端同时连接两条 WS 时 `clientSessions.put(clientId, wsSession)` 后者覆盖前者
- 🟠 [桌面端] [win-automation-service.ts:102-111](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/win-automation-service.ts#L102) Python 子进程崩溃后未自动重启，所有自动化操作失效直到下次应用启动
- 🟠 [后端] [WindowsAutomationController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/WindowsAutomationController.java) 返回 `ResponseEntity<Map<String,Object>>`，未使用 `ApiResponse.ok/err`，违反项目规则
- 🟡 [后端] `server.py` 心跳超过 90s 判离线，但桌面端 `win-automation-service.ts` 内嵌 Python 子进程不发心跳，仅通过 WS 保活

---

### 闭环 6：本地保存同步

```
[渲染层] Settings/LocalSave.tsx 用户配置本地保存路径
  → IPC localsave:set-config
  → [主进程] local-save-config.ts 持久化到 {userData}/local-save/config.json
  → [主进程] PUT /api/v1/system/workspace/config   ⚠ 路径前缀不一致
     body: {containerPath: '/app/user-workspace'}   ⚠ 硬编码
[主进程] local-save-sync.ts 启动后全量同步
  → 订阅 WS 事件:
      employee_task_update (status=COMPLETED)
      execution_event (type=artifact_ready)
  → GET /api/artifacts/my-visible?page=0&size=200
  → GET /api/artifacts/{artifactId}/download
  → SHA-256 校验
  → 写入 {basePath}/artifacts/{year}/{month}/{execId}/{file}
  → 推送 localsave:saved 事件到渲染层
[主进程] 每天 cleanupExpired() 清理过期文件
[主进程] cleanupUnauthorized() 清理无权限副本  ⚠ inventory 未持久化
```

**关联文件**
- 桌面端：[local-save-config.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-config.ts)、[local-save-sync.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-sync.ts)、[ipc.ts:90-113](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L90)
- 后端：[ArtifactController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ArtifactController.java)、`SystemController`（路径 `/api/v1/system/workspace/config` 待核对）

**已知问题**
- 🟠 [桌面端] [ipc.ts:98](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L98) `containerPath = '/app/user-workspace'` 硬编码，多租户/多用户场景会冲突
- 🟠 [桌面端] [ipc.ts:99](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L99) 调用 `/api/v1/system/workspace/config` 使用 `/api/v1/` 前缀，与项目其他 `/api/` 前缀不一致；需核对后端是否真的存在 v1 路由
- 🟠 [桌面端] [local-save-sync.ts:26](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-sync.ts#L26) `inventory` Map 内存态，应用重启后丢失，`cleanupUnauthorized` 不知道之前的本地副本，可能残留无权限文件
- 🟡 [桌面端] [local-save-sync.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-sync.ts) 全量同步防抖 1000ms，但 `cleanupExpired` 和 `cleanupUnauthorized` 之间无锁，可能并发清理正在下载的文件

---

### 闭环 7：连接状态检测

```
[桌面端] connection.ts 启动时检测一次
  → 每 30s GET /api/health    ⚠ 端点不存在
  → 5s 超时
  → 状态变化推送 backend:status-changed 到渲染层
[渲染层] Settings.tsx:44 也独立 fetch /api/health 测试连接
[主进程] ipc.ts:29 IPC backend:check 也调用 /api/health
```

**关联文件**
- 桌面端：[connection.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/connection.ts)、[ipc.ts:29](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L29)、[Settings.tsx:44](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/Settings/Settings.tsx#L44)
- 后端：`MonitoringController`（`/api/monitoring/health`，公开）、`SystemController`（`/api/system/health`）

**已知问题**
- 🔴 [后端] [SecurityConfig.java:64](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/SecurityConfig.java#L64) 注释里写 `/api/health` 放行，但代码中实际不存在该端点；桌面端三处调用都会得到 404
- 🟡 [桌面端] [connection.ts:41](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/connection.ts#L41) 心跳间隔硬编码 30s，未使用 `constants.HEARTBEAT_INTERVAL_MS`（60s），且与主进程 WS 30s ping 重复

---

### 闭环 8：审批

```
[渲染层] App.tsx:791 fetch GET /api/approvals
[渲染层] 用户点击批准/拒绝
  → fetch POST /api/approvals/{instanceId}/approve|reject
[后端] ApprovalController.approve/reject
  → ⚠ getCurrentApproverId() 硬编码返回 "current_user"
  → ⚠ 完全没有权限检查
  → 审批通过 → ApprovalController.callback/approved → 任务推进到 IN_PROGRESS
  → 审批拒绝 → ApprovalController.callback/rejected → 任务置为 FAILED
[后端] ApprovalController.getSteps
  → ⚠ 返回硬编码 step_1 + user1
```

**关联文件**
- 桌面端：[App.tsx:791](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L791)
- 后端：[ApprovalController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java)

**已知问题**
- 🔴 [后端] [ApprovalController.java:166-203](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L166) `approve/reject` 无权限检查，任意认证用户可审批任意审批单
- 🔴 [后端] [ApprovalController.java:365-367](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L365) `getCurrentApproverId()` 硬编码返回 `"current_user"`
- 🔴 [后端] [ApprovalController.java:257-280](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L257) `getSteps` 返回硬编码 stub
- 🟠 [后端] [ApprovalController.java:539-570](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L539) 审批回调端点 `/callback/approved` 和 `/callback/rejected` 无鉴权
- 🟠 [后端] [ApprovalController.java:435-448](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L435) 自定义 `ApiResponse` record，未使用 `common/ApiResponse`
- 🟡 [桌面端] [App.tsx:791](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L791) fetch 无错误处理，失败时 setApprovals([]) 用户无法感知

---

### 闭环 9：项目

```
[渲染层] App.tsx:732 fetch GET /api/projects
[渲染层] 用户操作
  → fetch POST /api/projects/{id}/start|complete|hold
[后端] ProjectController.startProject/completeProject/holdProject
  → ProjectServiceImpl 持久化
  → RuntimeEventStore 记录 Trace
```

**关联文件**
- 桌面端：[App.tsx:732](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L732)
- 后端：[ProjectController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ProjectController.java)

**已知问题**
- 🟠 [后端] [ProjectController.java:428-441](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ProjectController.java#L428) 自定义 `ApiResponse` record，未使用 `common/ApiResponse`
- 🟡 [桌面端] [App.tsx:732](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L732) fetch 无错误处理，失败时 setProjects([])

---

### 闭环 10：消息中心

```
[渲染层] App.tsx:853 fetch GET /api/messages
[后端] MessageController.getInbox → ⚠ 返回空 ArrayList
[后端] MessageController.getUnreadCount → ⚠ 返回 0
[渲染层] App.tsx 显示消息列表（永远为空）
```

**关联文件**
- 桌面端：[App.tsx:853](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L853)
- 后端：[MessageController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MessageController.java)

**已知问题**
- 🔴 [后端] [MessageController.java:23-64](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MessageController.java#L23) 全部为 stub：`getInbox` 返回空、`getUnreadCount` 返回 0、`markAsRead`/`markAllAsRead` 只记日志不持久化
- 🟠 [后端] [MessageController.java:66-79](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MessageController.java#L66) 自定义 `ApiResponse` record
- 🟡 [桌面端] [App.tsx:853](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L853) fetch 无错误处理

---

### 闭环 11：绩效积分

```
[渲染层] 渲染层调用 IPC credits:get-balance
  → [主进程] GET /api/credits/balance
  → [后端] CreditController（待核对是否存在）
  ← 返回 {balance, ...}
```

**关联文件**
- 桌面端：[ipc.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts)、[api-client.ts:280](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L280)
- 后端：`CreditController`（CODE_STRUCTURE 中列出，需核对实际实现）

**已知问题**
- 🟡 [后端] `CreditController` 在 CODE_STRUCTURE_AND_FILE_GUIDE.md 中列出但未在 Explore 中确认实际实现，需进一步核对
- 🟡 [桌面端] 未与 `PerformanceController` 对接（`/api/performance/my-assessment` 等绩效查询接口桌面端未使用）

---

### 闭环 12：托盘与通知

```
[桌面端] task-board-tray.ts 订阅 WS 事件
  → public_task_published → 调用 claimTopPriorityTask 候选
  → task-notification.ts handleTaskPublished
     → 检查 minPriority=3 和部门过滤
     → notifications.ts showNotification
  → tray.ts setBadgeVisible(count > 0)
[渲染层] PublicTaskBoard.tsx 监听 taskboard:count-changed
[主进程] shortcuts.ts Ctrl+Shift+C → claimTopPriorityTask
```

**关联文件**
- 桌面端：[task-board-tray.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-tray.ts)、[task-notification.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-notification.ts)、[notifications.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/notifications.ts)、[tray.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/tray.ts)、[shortcuts.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/shortcuts.ts)

**已知问题**
- 🟠 [桌面端] [floating-task-board.ts:60-62](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts#L60) 加载 `floating.html`，但该文件不存在（src/renderer 下只有 `index.html`），悬浮窗功能实际不可用
- 🟠 [桌面端] [floating-task-board.ts:23-25](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts#L23) `initFloatingTaskBoard()` 为空实现
- 🟠 [桌面端] [floating-task-board.ts:54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts#L54) 悬浮窗 `sandbox: false`，与主窗口 `sandbox: true` 安全策略不一致
- 🟡 [桌面端] [notifications.ts:9](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/notifications.ts#L9) `notifHistory` 写入但从未读取做去重，是死代码
- 🟡 [桌面端] [tray.ts:27-28](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/tray.ts#L27) 图标加载失败时 `nativeImage.createEmpty()` 显示空白，未提示用户

---

### 闭环 13：IPC 注册与清理

```
[桌面端] index.ts 启动
  → registerIpcHandlers() 注册 30+ invoke 通道
  → unregisterIpcHandlers() 在退出时调用  ⚠ 实现错误
```

**已知问题**
- 🔴 [桌面端] [ipc.ts:287-289](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L287) `ipcMain.removeAllListeners()` 会移除所有非 invoke 监听器（包括其他模块的），应改为遍历具体 channel 调用 `ipcMain.removeHandler(channel)`

---

## 3. 闭环关系网

```text
                    ┌─────────────────────────────────────────────────────────────┐
                    │                  后端 living-agent-gateway                    │
                    │                                                              │
                    │  ┌──────────────────┐    ┌──────────────────────────────┐    │
                    │  │ AuthController    │    │ AgentWebSocketHandler        │    │
                    │  │ PhoneAuth-       │    │  (/ws/agent)                 │    │
                    │  │ Controller       │    │   ↓ 注册 clientId            │    │
                    │  │  ↑ token          │    │  WindowsAutomationClient-   │    │
                    │  └────────┬─────────┘    │  GatewayImpl                 │    │
                    │           │              │   ↓ win_automation_call      │    │
                    │           │              │  PublicTaskEventPublisher    │    │
                    │           │              │   ↓ public_task_*           │    │
                    │  ┌────────▼─────────┐    │  DepartmentWebSocketHandler  │    │
                    │  │ SessionAuth-     │    │   (/ws/dept/*)               │    │
                    │  │ Filter           │    │   ↓ execution_progress       │    │
                    │  │ ClientIdFilter    │    │   ↓ employee_task_update    │    │
                    │  │ Department-      │    │   ↓ proactive_report        │    │
                    │  │ Permission-     │    └──────────────┬───────────────┘    │
                    │  │ Interceptor      │                   │                    │
                    │  └────────┬─────────┘                   │                    │
                    │           │                              │                    │
                    │  ┌────────▼──────────────────────────────▼───────────────┐  │
                    │  │ TaskController │ ProjectController │ ApprovalController│  │
                    │  │  MessageController(stub) │ ArtifactController        │  │
                    │  │  WindowsAutomationController(403)                     │  │
                    │  └────────┬─────────────────────────────────────────────┘  │
                    └───────────┼─────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────────┐
        │   REST API (Authorization: Bearer + X-Client-Id)   │
        │                       │                             │
┌───────▼──────────┐    ┌──────▼──────────┐         ┌────────▼─────────┐
│ 桌面端主进程      │    │  桌面端渲染层     │         │  桌面端渲染层     │
│ (api-client.ts)  │    │  (App.tsx)      │         │ (OfficeChatPage)  │
│                  │    │                 │         │                   │
│ • auth.ts        │    │ • fetch /api/   │         │ • fetch /api/     │
│   safeStorage    │    │   projects      │         │   dept/conversa-  │
│ • ws-client.ts   │    │ • fetch /api/   │         │   tions           │
│   /ws/agent      │    │   approvals     │         │ • fetch /api/     │
│   win_automation │    │ • fetch /api/   │         │   fixed-employees │
│   public_task_* │    │   messages      │         │ • new WebSocket   │
│                  │    │ • fetch /api/   │         │   /ws/dept/{dept} │
│ • local-save-    │    │   enterprise/*  │         │                   │
│   sync.ts        │    │                 │         │ ⚠ 同时监听        │
│ • win-automation │    │ ⚠ fetch 时用    │         │   win_automation_ │
│   -service.ts    │    │   localStorage  │         │   call (重复)     │
│   Python 子进程  │    │   (永远 null)   │         │                   │
└────────┬─────────┘    └─────────────────┘         └───────────────────┘
         │
         │ stdin/stdout JSON
         ▼
┌────────────────────┐
│ service.py (Python)│
│  UIA/PowerShell/   │
│  注册表/文件系统   │
└────────────────────┘
```

**关系网要点**
1. **认证链贯穿所有闭环**：token 由 `auth.ts` 加密存储 → `api-client.ts` 注入 Bearer → `ws-client.ts` 注入 subprotocol → 渲染层 fetch 自己拼装（有 bug）
2. **WS 双连接并行**：主进程 `/ws/agent` 负责任务推送和 Windows 自动化；渲染层 `/ws/dept/{dept}` 负责部门聊天；两者**无统一管理**
3. **Windows 自动化三通道并存**：HTTP（WindowsAppTool，业务化）+ WebSocket 主进程（WindowsAutomationTool，通用）+ 渲染层 WS 重复处理
4. **任务状态多源同步**：`TaskController` REST + `EmployeeExecutionReceiptService` 回执 + `ExecutionReceiptTaskProjectBridge` 桥接 + WS 推送，需保证一致性
5. **stub 集中在 REST API 层**：`MessageController`、`ApprovalController.getSteps`、`ReceptionController`、`AuthController.updateMe` 等，桌面端调用后无法获得真实数据

---

## 4. 问题清单（按严重度）

### 4.1 🔴 严重（影响功能正确性或安全）

| # | 位置 | 问题描述 | 影响 |
|---|------|---------|------|
| S1 | [PhoneAuthController.java:104](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/PhoneAuthController.java#L104) | `SendSmsResponse.code` 把验证码明文返回客户端 | 任意调用 `/api/auth/sms/send` 即可绕过短信验证，登录形同虚设 |
| S2 | [ApprovalController.java:166-203](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L166) | `approve/reject` 无权限检查 | 任意认证用户可审批任意审批单 |
| S3 | [ApprovalController.java:365-367](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L365) | `getCurrentApproverId()` 硬编码 `"current_user"` | 审批人记录永远错误 |
| S4 | [ApprovalController.java:257-280](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L257) | `getSteps` 返回硬编码 stub | 桌面端审批步骤永远显示固定数据 |
| S5 | [MessageController.java:23-64](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MessageController.java#L23) | 全部方法 stub | 桌面端消息中心永远空白 |
| S6 | [WebMvcConfig.java:55](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/WebMvcConfig.java#L55) + [DepartmentPermissionInterceptor.java:86-106](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/interceptor/DepartmentPermissionInterceptor.java#L86) | `/api/windows-automation/**` 强制 FULL 权限 | 桌面端 `server.py` 节点注册/心跳被 403 拒绝 |
| S7 | [SecurityConfig.java:64](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/SecurityConfig.java#L64) | `/api/health` 端点不存在 | 桌面端三处连接检测都返回 404 |
| S8 | [task-board-cache.ts:77-78](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-cache.ts#L77) | `if (!dept && f === 'all.json') continue;` 应为 `!==` | 无 dept 参数时 `all.json` 缓存被跳过，离线缓存不可用 |
| S9 | [ipc.ts:287-289](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L287) | `ipcMain.removeAllListeners()` 误删所有监听器 | 退出时影响其他模块的 ipcMain 监听器 |
| S10 | [floating-task-board.ts:60-62](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts#L60) | 加载不存在的 `floating.html` | 悬浮任务板功能完全不可用 |
| S11 | [ws-client.ts:90-93](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L90) + [OfficeChatPage.tsx:341-343](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L341) | 主进程和渲染层都处理 `win_automation_call` | 同一消息可能被处理两次 |
| S12 | [App.tsx:733,792,854,919,924](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx#L733) + [OfficeChatPage.tsx:126,214,234](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L126) | `localStorage.getItem('desktop_token')` 永远 null | 死代码，每次都要异步 getToken，且代码冗余 |
| S13 | [DepartmentWebSocketHandler.java:366-377](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L366) | `default` 分支把未知消息类型当 CHAT 处理 | 恶意 payload 直入 LLM 推理 |

### 4.2 🟠 中度（影响稳定性或一致性）

| # | 位置 | 问题描述 |
|---|------|---------|
| M1 | [ws-client.ts:184-189](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L184) | 发送 ping 未检测 pong 超时，连接僵死时不会主动重连 |
| M2 | [ws-client.ts:39,54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L39) | `cachedApps` 只在首次连接时扫描，应用列表过期 |
| M3 | [OfficeChatPage.tsx:478-488](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L478) | 渲染层 WS 重连最多 5 次，与主进程无限重连策略不一致 |
| M4 | [OfficeChatPage.tsx:506](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L506) | 重连 useEffect 依赖 `conversationId`，切换对话重建 WS |
| M5 | [WindowsAutomationClientGatewayImpl.java:190-195](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/WindowsAutomationClientGatewayImpl.java#L190) | 无 `clientId→requestIds` 反向映射，断开后挂起请求等 30s 超时 |
| M6 | [AgentWebSocketHandler.java:174](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java#L174) + [DepartmentWebSocketHandler.java:233](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L233) | 同一 clientId 在两个 Handler 都注册，后者覆盖前者 |
| M7 | [win-automation-service.ts:102-111](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/win-automation-service.ts#L102) | Python 子进程崩溃后未自动重启 |
| M8 | [WindowsAutomationController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/WindowsAutomationController.java) | 返回 `Map<String,Object>`，未用 `ApiResponse.ok/err` |
| M9 | [AuthController.java:225-267](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java#L225) | `PATCH /api/auth/me` 未调用任何 service 持久化 |
| M10 | [api-client.ts:166](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L166) | `refreshToken` 未携带 `X-Client-Id` 头 |
| M11 | [ipc.ts:98-99](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L98) | `containerPath = '/app/user-workspace'` 硬编码 + `/api/v1/` 前缀不一致 |
| M12 | [local-save-sync.ts:26](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-sync.ts#L26) | `inventory` Map 内存态，重启后丢失，cleanupUnauthorized 失效 |
| M13 | [ApprovalController.java:539-570](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L539) | 审批回调端点无鉴权 |
| M14 | 多个 Controller（ProjectController/ApprovalController/MessageController/PerformanceController/PhoneAuthController/ReceptionController/ExecutionStatusController） | 自定义 `ApiResponse` record，未使用 `common/ApiResponse`，违反项目规则 |
| M15 | [AuthController.java:383](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java#L383) | 权限检查硬编码字符串比较 |
| M16 | [TaskController.java:631-646](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java#L631) | `getDepartmentExecutions` 用 `summary.contains(department)` 过滤 |
| M17 | [TaskController.java:664-681](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java#L664) | `extractMetadataField` 手工字符串查找解析 JSON |
| M18 | [floating-task-board.ts:54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts#L54) | 悬浮窗 `sandbox: false`，与主窗口安全策略不一致 |

### 4.3 🟡 轻度（代码质量或可维护性）

| # | 位置 | 问题描述 |
|---|------|---------|
| L1 | [connection.ts:41](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/connection.ts#L41) | 心跳间隔硬编码 30s，未用 `constants.HEARTBEAT_INTERVAL_MS` |
| L2 | [ipc.ts:149-165](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L149) | `taskboard:cache` 和 `taskboard:list` 都调用 `getPublicTasks()`，可能重复请求 |
| L3 | [notifications.ts:9](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/notifications.ts#L9) | `notifHistory` 写入但从未读取，死代码 |
| L4 | [tray.ts:27-28](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/tray.ts#L27) | 图标加载失败时 `nativeImage.createEmpty()` 显示空白，未提示 |
| L5 | [InMemoryConnectionRegistry.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/InMemoryConnectionRegistry.java) | 内存 + DB 回退查询 + 启动时从 DB 恢复（B-0-2 已完成） |
| L6 | [DepartmentWebSocketHandler.java:88-89](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L88) | 移除 zombie 检测，崩溃连接可能永久占用 |
| L7 | [ReceptionController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ReceptionController.java) | 全部硬编码 stub |
| L8 | [App.tsx fetch 多处](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx) | fetch 无统一错误处理，失败时返回空列表 |
| L9 | 桌面端渲染层多处直接 fetch | 应统一走 IPC + api-client，避免散落 fetch |

---

## 5. 修复任务与优先级

### P0：安全与功能阻断（立即修复）

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~0-1~~ ⏸ 移除短信验证码明文返回 | [PhoneAuthController.java:104](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/PhoneAuthController.java#L104) | 当前阶段未挂接短信服务，明文返回 `code` 用于开发/测试，暂不移除；生产上线前需删除该字段 |
| 0-2 审批接口加权限校验 | [ApprovalController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java) | approve/reject 无权限校验，`getCurrentApproverId()` 硬编码返回 `"current_user"`；需注入 `ApprovalService`，从 `AuthContext` 获取真实 `approverId` |
| ~~0-3~~ ✅ 修复 `/api/windows-automation/**` 权限 | [DepartmentPermissionInterceptor.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/interceptor/DepartmentPermissionInterceptor.java) | 已纳入 `ADMIN_API_PATTERN`，要求 FULL 权限或创始人身份 |
| ~~0-4~~ ✅ 创建 `/api/health` 端点 | [AgentController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentController.java) | `GET /api/health` 已实现，SecurityConfig 已加白名单 |
| 0-5 修复 task-board-cache 过滤 bug | [task-board-cache.ts:78](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/task-board-cache.ts#L78) | `!dept` 时仅加载 `all.json`，部门缓存文件被跳过；应加载所有 `.json` 文件 |
| ~~0-6~~ ✅ 修复 IPC 清理误删 | [ipc.ts:302-305](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L302) | 已改为遍历 `IPC_CHANNELS` 数组调用 `ipcMain.removeHandler(channel)` |
| ~~0-7~~ ✅ 修复渲染层 token 获取 | [App.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx) | 已统一 `window.livingAgentAPI.auth.getToken()`，无 `localStorage.getItem('desktop_token')` 残留 |
| 0-8 消除 win_automation_call 重复处理 | [OfficeChatPage.tsx:340-343](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L340) + [ws-client.ts:90-92](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L90) | 主进程和渲染层双重处理，同一操作可能执行两次；需统一到一条链路 |
| 0-9 修复 DepartmentWebSocketHandler 默认分支 | [DepartmentWebSocketHandler.java:377](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L377) | `default` 仍 fallback 到 `handleChatMessage`；应改为 `sendError` |
| ~~0-10~~ ✅ 实现悬浮任务板 | [floating-task-board.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/floating-task-board.ts) | 已实现完整功能（show/hide/toggle/destroy） |

### P1：稳定性与一致性（本周内修复）

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~1-1~~ ✅ 实现 MessageController 真实逻辑 | [MessageController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MessageController.java) | 已创建 `MessageEntity` + `MessageRepository`；`getInbox` 查询当前用户消息；`markAsRead` 更新 `read_at`；`getUnreadCount` 聚合统计；使用 `common.ApiResponse` |
| ~~1-2~~ ✅ 实现 ApprovalController.getSteps | [ApprovalController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java) | 从 workflow 获取真实审批步骤；同时修复 getCurrentApproverId() 使用 SecurityContext、统一 common.ApiResponse |
| 1-3 统一 WS 重连策略 | [OfficeChatPage.tsx:478-488](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx#L478) + [ws-client.ts:109-119](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L109) | 抽取共享 `ReconnectStrategy`（指数退避，无限重连，最大 30s 封顶），主进程和渲染层共用 |
| 1-4 WS 心跳超时检测 | [ws-client.ts:184-189](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L184) | 发送 ping 后启动 10s 定时器，未收到 pong 则 `ws.terminate()` 触发重连 |
| 1-5 WindowsAutomationClientGateway 反向映射 | [WindowsAutomationClientGatewayImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/WindowsAutomationClientGatewayImpl.java) | 新增 `Map<String, Set<Long>> clientToRequestIds`，`registerSession` 时迁移旧 session 的 pending；`failPendingRequests` 按 clientId 主动 fail |
| 1-6 clientId 注册去重 | [AgentWebSocketHandler.java:174](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java#L174) + [DepartmentWebSocketHandler.java:233](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L233) | `WindowsAutomationClientGatewayImpl.registerSession` 改为 `Map<String, List<WsSession>>`，按 session 复用而非覆盖；或限制每 clientId 只允许一条 WS 注册自动化 |
| 1-7 Python 子进程自动重启 | [win-automation-service.ts:102-111](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/win-automation-service.ts#L102) | `close` 事件中若 `!isQuitting && restartCount < MAX_RESTARTS(3)`，延迟 2s 重新 spawn |
| 1-8 app-scanner 定期刷新 | [ws-client.ts:39,54-61](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L39) | 每 30min 重新扫描一次，连接时携带最新结果 |
| 1-9 refreshToken 携带 X-Client-Id | [api-client.ts:166-172](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts#L166) | 在 refresh 请求头加入 `X-Client-Id: ${getClientId()}` |
| 1-10 local-save-sync inventory 持久化 | [local-save-sync.ts:26](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-sync.ts#L26) | 启动时从 `{userData}/local-save/inventory.json` 加载；每次更新后异步落盘 |
| 1-11 修复 `/api/v1/system/workspace/config` 路径 | [ipc.ts:99](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts#L99) | 核对后端 SystemController，统一为 `/api/system/workspace/config`；containerPath 改为从用户配置派生 |
| ~~1-12~~ ✅ 统一 ApiResponse | ProjectController/ApprovalController/MessageController/PerformanceController/PhoneAuthController/ReceptionController/ExecutionStatusController 等20个 Controller | 删除各自内部 record，改用 `common/ApiResponse.ok/err`；全部20个 Controller 已完成，编译通过 |
| ~~1-13~~ ✅ 实现 AuthController.updateMe | [AuthController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java) | 注入 `EnterpriseEmployeeService`，调用 `updateAuthContext()` 持久化 name/email/avatar |
| ~~1-14~~ ✅ 修复 ArtifactController.resolveUser() | [ArtifactController.java:309](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ArtifactController.java#L309) | 注入 `UnifiedAuthService`，调用 `validateSession(token)` 正确解析 Bearer token，不再返回 null |
| ~~1-15~~ ✅ 修复 apiBase.ts 401 自动跳转 | [apiBase.ts:19-23](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/apiBase.ts#L19) | 将 `/auth/me` 添加到 isAuthEndpoint 列表，避免认证恢复时 401 自动跳转打断 App.tsx 的 catch 处理 |
| ~~1-16~~ ✅ 修复 Layout.tsx agentApi.list() 401 跳转 | [Layout.tsx:234-238](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Layout.tsx#L234) | 添加 `enabled: !!user` 条件，确保只在用户认证后才调用 agentApi.list()，避免未认证时 401 自动跳转到登录页 |
| ~~1-17~~ ✅ 修复 EnterpriseDashboard.tsx API 无条件调用 | [EnterpriseDashboard.tsx:367-371](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Dashboard/EnterpriseDashboard.tsx#L367) | 添加 `enabled: !!user` 条件，确保只在用户认证后才调用 dashboardApi.getEnterpriseSummary()，避免未认证时 401 跳转 |
| ~~1-18~~ ✅ 修复 DashboardController 使用 SecurityContext | [DashboardController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DashboardController.java) | 添加 `getAuthenticatedEmployeeId()` 方法，优先从 `SecurityContextHolder` 获取 employeeId（由 SessionAuthenticationFilter 设置），降级使用 X-Employee-Id header，解决 HttpServletRequestWrapper getHeader 未被 Spring MVC 正确调用的问题 |

### P2：代码质量优化（下个迭代）

| 任务 | 修复方案 |
|------|---------|
| 2-1 统一渲染层 fetch 错误处理 | 抽取 `fetchJson` helper，失败时 `showToast(message, 'error')` 而非静默 setX([]) |
| 2-2 渲染层 fetch 改走 IPC | 删除 App.tsx 中的直接 fetch，全部通过 preload IPC 转发到主进程 api-client |
| 2-3 修复 connection.ts 心跳常量 | 使用 `constants.HEARTBEAT_INTERVAL_MS`，或与主进程 WS ping 合并（30s） |
| 2-4 taskboard:cache 与 list 合并 | `taskboard:list` 内部调用 cache，避免重复请求 |
| 2-5 删除 notifHistory 死代码 | 或实现真正的去重逻辑 |
| 2-6 TaskController.getDepartmentExecutions 改用 Repository | 新增 `receiptRepository.findByDepartment(department)` |
| 2-7 TaskController.extractMetadataField 改用 ObjectMapper | 注入 `ObjectMapper`，解析 JSON metadata |
| 2-8 AuthController 权限检查改用 AccessLevel 枚举 | 替换字符串比较 |
| 2-9 DepartmentWebSocketHandler zombie 检测 | 加回 5min idle 检测，发送 close 4408 通知客户端 |
| ~~2-10~~ ✅ ReceptionController 实现真实逻辑 | 已创建 `VisitorEntity` + `VisitorRepository`；getVisitors 查询 DB；checkIn 持久化；processReceptionChat 基于关键词匹配；使用 `common.ApiResponse` |

---

## 6. 验收标准

每个修复任务完成后，需通过以下验收：

### 6.1 安全验收
- [ ] `POST /api/auth/sms/send` 响应 body 不再包含 `code` 字段
- [ ] `POST /api/approvals/{id}/approve` 非审批人调用返回 403
- [ ] `/api/windows-automation/nodes/register` 携带 node_token 可成功注册（不需要用户 FULL 权限）
- [ ] `GET /api/health` 返回 200 + `{status:"UP"}`

### 6.2 桌面端功能验收
- [ ] 桌面端登录后 5 分钟内无 token 刷新失败
- [ ] 主进程 WSClient 断网后 30s 内重连成功
- [ ] 渲染层 OfficeChatPage WS 断网后 30s 内重连成功
- [ ] 任务看板在断网状态下能从缓存加载 `all.json`
- [ ] 悬浮任务板快捷键 Ctrl+Shift+F 能打开窗口（或快捷键被移除）
- [ ] Windows 自动化操作不会同时被主进程和渲染层处理两次

### 6.3 数据一致性验收
- [ ] 审批 `approverId` 等于当前登录用户 ID
- [ ] 审批步骤 `getSteps` 返回真实工作流数据
- [ ] `GET /api/messages/unread-count` 返回真实未读数
- [ ] `PATCH /api/auth/me` 修改后重新登录信息保留

### 6.4 代码质量验收
- [ ] 所有 Controller 返回 `common/ApiResponse.ok/err`
- [ ] 渲染层不再出现 `localStorage.getItem('desktop_token')`
- [ ] `ipcMain.removeAllListeners()` 不再出现
- [ ] 渲染层不再监听 `win_automation_call`
- [ ] `DepartmentWebSocketHandler` `default` 分支返回 error 而非走 CHAT

---

## 附录 A：闭环 ↔ 文件矩阵

| 闭环 | 桌面端主进程文件 | 桌面端渲染层文件 | 后端 Controller | 后端 WebSocket Handler | Core 服务 |
|------|----------------|----------------|----------------|----------------------|----------|
| 1 登录 | auth.ts、api-client.ts | App.tsx | AuthController、PhoneAuthController | AuthHandshakeInterceptor | UnifiedAuthService、FounderService |
| 2 主 WS | ws-client.ts、app-scanner.ts、client-id.ts | - | - | AgentWebSocketHandler | ClientDeviceRegistryService、ProactiveOrchestrator |
| 3 部门聊天 | - | OfficeChatPage.tsx | ConversationController | DepartmentWebSocketHandler | ConversationOrchestrator、DepartmentChatService、BrainRegistry |
| 4 任务看板 | task-board-tray.ts、task-board-cache.ts、ipc.ts | PublicTaskBoard.tsx | TaskController | DepartmentWebSocketHandler | PublicTaskEventPublisher、TaskCheckout |
| 5 Win 自动化 | win-automation-service.ts、ws-client.ts | OfficeChatPage.tsx | WindowsAutomationController | AgentWebSocketHandler、WindowsAutomationClientGatewayImpl | WindowsAppTool、WindowsAutomationTool |
| 6 本地保存 | local-save-config.ts、local-save-sync.ts、ipc.ts | LocalSave.tsx | ArtifactController、SystemController | - | ArtifactRecordService |
| 7 连接检测 | connection.ts、ipc.ts | Settings.tsx | (待创建 /api/health) | - | - |
| 8 审批 | - | App.tsx | ApprovalController | - | ApprovalService、ApprovalManager |
| 9 项目 | - | App.tsx | ProjectController | - | ProjectService、ProjectRepository |
| 10 消息 | - | App.tsx | MessageController | - | (待实现) |
| 11 积分 | ipc.ts | - | CreditController | - | LedgerService |
| 12 托盘通知 | tray.ts、notifications.ts、task-notification.ts、shortcuts.ts | PublicTaskBoard.tsx | - | DepartmentWebSocketHandler | - |
| 13 IPC | ipc.ts | - | - | - | - |

---

# 第二部分：后端功能闭环深度审计

> 本部分深入分析 `living-agent-gateway` 和 `living-agent-core` 后端代码的功能闭环完整性，逐个 Service / Core 模块判断"是否能独立完成业务功能"，找出 stub、断链、内存态、事务缺失等问题。

## 7. 后端功能闭环矩阵

按"是否能闭环使用"分四类：

| 状态 | 含义 | 模块数 |
|------|------|--------|
| ✅ 完整闭环 | 入口→Service→Repository→DB 全链路真实落地 | 18 |
| ⚠️ 半闭环 | 业务逻辑真实但存储为内存态，重启丢失 | 9 |
| ❌ stub 闭环 | 方法体为空/硬编码/返回固定值，闭环失效 | 7 |
| 🟡 部分闭环 | 同一 Service 内部分方法真实、部分 stub | 5 |

### 7.1 ✅ 完整闭环模块（18 个）

| 模块 | Service | Repository | 闭环验证 |
|------|---------|-----------|---------|
| 部门对话编排 | DepartmentChatService | DepartmentChatMessageRepository、TaskRepository、DepartmentConversationRepository | 真实调用 ConversationOrchestrator → FixedEmployeeDispatcher → DepartmentExecutionCoordinator → 回执 → 聚合 → WS 推送 |
| 对话持久化 | ConversationServiceImpl | DepartmentConversationRepository | `@Transactional` 全部 `repository.save/findByConversationId` |
| 任务接领 | TaskCheckout（@Component） | TaskRepository | `createTask` 调用 `persistTask` 写 DB |
| 任务同步 | TaskCheckoutSyncService | TaskRepository | 启动时 `loadPendingTasks` 从 DB 重建内存 |
| 公共任务事件广播 | PublicTaskEventPublisher | - | 调用 `webSocketHandler.broadcastRawJson` 真实推送 WS |
| 回执桥接 | ExecutionReceiptTaskProjectBridge | TaskRepository、ProjectRepository | `onReceiptRecorded` 真实更新 task 状态 |
| 任务事件桥接 | TaskEventBridgeService | - | 调用 RiskPredictor、ProactiveSuggestionService、DepartmentNotificationService |
| 任务绩效桥接 | TaskPerformanceBridgeService | - | 调用 EmployeeCompensationService.recordReward/Penalty |
| 进化反馈桥接 | EvolutionFeedbackBridgeService | EvolutionAuditLogRepository | 调用 knowledgeManager.storeShared + auditLogRepository.save |
| 通用 Agent 服务 | AgentService | - | 真实分流到 processWithOrchestration 或 processWithNeuron |
| 工作项上下文 | WorkItemContextService | DepartmentConversationRepository | 真实调用 repository |
| 设备注册 | ClientDeviceRegistryService | ClientDeviceRepository | `@Transactional` 真实 registerOrUpdate |
| 创始人服务 | FounderService | FounderCheckStrategy | 10s 缓存 + DB 委托 |
| 企业员工 | EnterpriseEmployeeService | EnterpriseEmployeeRepository | `@Transactional` 真实 CRUD |
| 账本服务 | JpaLedgerService | LedgerTransactionRepository | `@Transactional` 真实记录赏金 |
| 产物归档 | JpaArtifactRecordService | ArtifactRecordRepository | `recordArtifact` 真实写 DB + 文件索引 |
| 知识库 | LayeredKnowledgeBaseImpl | KnowledgePersistenceService + QdrantVectorService | 向量检索 + 混合搜索真实落地 |
| 记忆系统 | MemoryServiceImpl + SQLiteMemoryBackend | - | SQLite PreparedStatement 真实存储 |

### 7.2 ⚠️ 半闭环模块（9 个，内存态丢失风险）

| 模块 | Service | 真实部分 | 内存态部分 | 风险 |
|------|---------|---------|-----------|------|
| 审批工作流 | ApprovalServiceImpl | 多步审批推进、回调注册 | `approvalStore`/`workflowStore` ConcurrentHashMap | 重启丢失所有审批中实例 |
| 项目管理 | ProjectServiceImpl | `persistProject` 同步写 DB | `getProject`/`listProjects` 只读内存 Map | 重启后查不到项目，即使 DB 有数据 |
| 员工管理 | EmployeeServiceImpl | `getDepartmentMembersByCode` 读 DB | `createEmployee` 只写内存、`getEmployee` 只读内存 | 写入与读取走不同存储，数据不一致 |
| 会话认证 | UnifiedAuthService | 令牌轮换、OAuth 流程 | `activeSessions` ConcurrentHashMap | 重启所有登录会话失效 |
| 部门通知 | DepartmentNotificationService | 通知发送 + listener 触发 | `notifications`/`readStatus` 内存 Deque | 重启丢失通知队列 |
| 系统配置 | SystemConfigService | `createTenantWithCompany` 持久化 | `providerConfigs` 内存 + `initDefaultProviders` 硬编码 4 个 | 重启丢失所有 Provider apiKey |
| 备份恢复 | BackupRecoveryService | `createSnapshot` SHA-256 | `snapshots` 内存 Map，`restoreSnapshot` 假还原 | 备份可用但还原失效 |
| 知识晋升审计 | KnowledgePromotionAuditService | `promote` 调用 knowledgeManager | `auditHistory` 内存 Map | 重启丢失审计历史 |
| 监控指标 | MonitoringService | 健康检查委托 HealthMonitor | `metrics`/`metricHistory` 内存 | 重启丢失历史指标 |

### 7.3 ❌ stub 闭环模块（7 个 → 5 个已修复）

| 模块 | 文件 | stub 方法 | 影响 | 状态 |
|------|------|----------|------|------|
| ~~消息中心~~ | ~~MessageController~~ | ~~`getInbox`/`getUnreadCount`/`markAsRead`/`markAllAsRead` 全部~~ | ~~桌面端消息列表永远空白~~ | ✅ 已修复 |
| ~~任务工作流~~ | ~~TaskWorkflowService~~ | ~~`summarizeReview` 仅打包数据无业务逻辑~~ | ~~TaskController.reviewTask 调用后无任何持久化与传播~~ | ✅ 已修复：注入TaskRepository + @Transactional + 持久化审查结果 |
| ~~部门审计日志~~ | ~~OrganizationQueryServiceImpl~~ | ~~`getRecentAuditLogs` 返回 emptyList~~ | ~~部门审计日志查询永远为空~~ | ✅ 已修复（阶段2，B-1-14） |
| ~~工具审批闸门~~ | ~~SimpleApprovalManager~~ | ~~`requestApproval` 硬编码 `return ApprovalResponse.YES`~~ | ~~所有工具调用自动批准~~ | ✅ 实际使用 JpaPlanApprovalService（@Primary），已有真实DB审批逻辑 |
| ~~换人重试（默认）~~ | ~~FixedEmployeeDispatcher 接口~~ | ~~`reassign` 默认 `return List.of()`~~ | ~~若实现未重写，换人重试链断~~ | ✅ RegistryBackedFixedEmployeeDispatcher 已实现真实 reassign 逻辑 |
| ~~兜底工具执行~~ | ~~DefaultToolExecutor~~ | ~~`execute` 返回硬编码 success~~ | ~~兜底无操作，仅返回假成功~~ | ✅ 已修复：改为 `ToolResult.failure()` |
| ~~前台接待~~ | ~~ReceptionController~~ | ~~`processReceptionChat`/`getVisitors` 全部硬编码~~ | ~~前台对话永远返回固定字符串~~ | ✅ 已修复：VisitorEntity + VisitorRepository + 关键词匹配 |

### 7.4 🟡 部分闭环模块（5 个）

| 模块 | 真实部分 | stub 部分 |
|------|---------|-----------|
| 公司排行榜 | PerformanceDashboardService.getCompanySummary/buildSummary | `getCompanyTop/Bottom` 仅对 InMemory 实现有效，JPA 实现返回 List.of() |
| 权限校验 | PermissionServiceImpl.canAccessBrain/canUseModel/canExecuteTool 真实 | ~~`validateDingTalkToken`/`validateFeishuToken`/`validateWeChatToken` 仅格式检查~~ ✅ OAuth token 校验已接入 OAuthService（阶段1）；`validateVoiceVector` 声纹比对待实施 |
| Trace 服务 | AutonomyTraceService DB 优先查询 + 内存兜底双写 | ~~`getTraceByRequestId/TaskKey/ExecutionId` 内存优先，重启后历史断裂~~ ✅ 已改为 DB 优先查询（阶段1） |
| RuntimeEventStore | 文件系统真实写入 | tenantId 硬编码 `_system`、多节点不可共享 |
| ConversationOrchestrator | 全链路 LLM 调用 | `interventionNeuron = null` 导致高风险任务人工接管降级 |

---

## 8. 后端闭环断点详解

### 8.1 断点 B1：ExecutionResult 内存缓存丢失（🔴 P0）

**位置**：
- [JpaEmployeeExecutionReceiptService.java:40](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/JpaEmployeeExecutionReceiptService.java#L40) `executionResultsById ConcurrentHashMap`
- [DepartmentChatService.java:107](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java#L107) `executionResultCache ConcurrentHashMap`

**链路**：异步执行任务 → receipt 在重启后到达 → `onReceiptRecorded(executionId, ...)` → `executionResultsById.get(executionId)` 返回 null → [DepartmentChatService.java:207-213](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java#L207) 直接 return

**影响**：重启后所有进行中的执行链路断裂，回执不再触发任务状态更新、不再推送 WebSocket、不再触发最终响应生成。任务永远停留在 IN_PROGRESS。

**修复方案**：将 `DepartmentExecutionResult` 序列化到 PostgreSQL（新建 `department_execution_results` 表或写入 `runtime_events` 文件），重启时从持久化加载。

---

### 8.2 断点 B2：项目统计未落库（~~🔴 P0~~ ✅ 已修复）

> ✅ 已修复（阶段1）：在 `updateProjectFromExecution` 末尾增加了 `projectRepository.save(project)` 更新进度字段。

**位置**：[ExecutionReceiptTaskProjectBridge.java:96-125](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/ExecutionReceiptTaskProjectBridge.java#L96)

~~**问题**：`updateProjectFromExecution` 只调用 `runtimeEventStore.appendProjectEvent` 写文件，**未调用 `projectRepository.save()` 更新 ProjectEntity**。~~

~~**影响**：
- `ProjectEntity.progress` 字段永远是初始值 0.0
- `ProjectEntity.completedTaskCount`/`totalTaskCount` 永远不更新
- 任何通过 DB 查询项目状态的接口（`/api/projects/{id}/progress`、`/api/projects/statistics`）都返回错误数据
- 项目完成率、总任务数等统计只存在于本地文件，不入库、不可查询~~

~~**修复方案**：在 `updateProjectFromExecution` 末尾增加 `projectRepository.save(project)` 更新进度字段。~~

---

### 8.3 断点 B3：回执链路无事务（🔴 P0）

**位置**：[JpaEmployeeExecutionReceiptService.java:108-137](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/JpaEmployeeExecutionReceiptService.java#L108)

**问题**：`recordReceipt` 方法未加 `@Transactional`，整个链路跨 5+ Service：
1. `receiptRepository.save(entity)` — 独立事务（JpaRepository 默认）
2. `listener.onReceiptRecorded(...)` → `ExecutionReceiptTaskProjectBridge.updateTaskFromReceipt` → `taskRepository.save(task)` — 又一个独立事务
3. `runtimeEventStore.appendTaskEvent` — 文件系统写入，无事务
4. `AutonomyTraceService.recordEvent` → `traceEventRepository.save` — 又一个独立事务
5. `routeToReviewWorkflow` — 又一个独立事务

**影响**：任一中间步骤失败，前面已落库的数据无法回滚，导致：
- 回执已保存但任务未更新
- 任务已更新但 review 状态未推进
- Trace 事件丢失但 task 状态已变更

**修复方案**：
- `recordReceipt` + `updateTaskFromReceipt` 包在同一个 `@Transactional` 方法中
- `runtimeEventStore` 文件写入和 `AutonomyTraceService.recordEvent` 改用 `@TransactionalEventListener(phase = AFTER_COMMIT)`，确保数据落库后再写
- WebSocket 推送改为 `AFTER_COMMIT`，避免客户端收到 "COMPLETED" 后查询仍是 IN_PROGRESS

---

### 8.4 断点 B4：RuntimeEventStore 基于文件 + tenantId 硬编码（🔴 P0）

**位置**：
- [RuntimeEventStore.java:113-129](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/runtime/RuntimeEventStore.java#L113) `Files.writeString` 写文件
- [ExecutionReceiptTaskProjectBridge.java:67](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/ExecutionReceiptTaskProjectBridge.java#L67) `appendTaskEvent("_system", ...)` 硬编码

**影响**：
- 多节点部署时无法共享事件（每节点写本地磁盘）
- 无事务保护，磁盘故障即丢失
- 多租户场景下数据隔离失效（所有租户的回执事件都写入 `_system` 命名空间）
- 错误处理用 `System.err.println`（第 127、137、149 行）非 logger

**修复方案**：
- 改为基于 DB 的 EventStore（新建 `runtime_events` 表，使用 `RuntimeEventEntity` + `RuntimeEventRepository`）
- tenantId 从 `WorkItemContext` 或 `AuthContext` 获取，不硬编码

---

### 8.5 断点 B5：~~AutonomyTraceService 内存优先导致历史断裂~~ ✅ 已修复（阶段1）

> ✅ 已修复（阶段1）：AutonomyTraceService 已改为 DB 优先查询，内存仅作为兜底；recordEvent 双写 DB + 内存。

**位置**：[AutonomyTraceService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/AutonomyTraceService.java)

~~**问题**：`getTraceByRequestId`、`getTraceByTaskKey`、`getTraceByExecutionId` 三个方法都是"内存有结果就直接返回，不查 DB"~~

~~**影响**：重启后内存只有新事件，对于跨越重启的执行（部分 trace 在重启前已落 DB，部分在重启后还在内存），**只能查到重启后的部分**，无法获取完整链路。~~

~~**修复方案**：内存作为缓存，DB 作为权威源；查询时合并内存 + DB 结果，按时间戳排序去重。~~

---

### 8.6 断点 B6：triggerAsyncFinalResponse 名不副实（🟠 P1）

**位置**：[DepartmentChatService.java:2625](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java#L2625)

**问题**：方法名带 "Async"，但方法体内无 `CompletableFuture.supplyAsync`、无 `executor.submit`、无 `@Async`。该方法被 `onReceiptRecorded` 同步调用，**阻塞 receipt listener 线程**。

**影响**：所有 receipt listener 串行执行最终响应生成（含 LLM 聚合调用），单条执行链路长则阻塞后续回执处理。

**修复方案**：
- 真正改为异步：`CompletableFuture.supplyAsync(() -> ..., asyncExecutor)`
- 或重命名为 `finalizeResponse`，明确同步语义

---

### 8.7 断点 B7：TraceEventEntity.execution_id 长度截断（🟠 P1）

**位置**：
- [TraceEventEntity.java:51](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/entity/TraceEventEntity.java#L51) `execution_id VARCHAR(100)`
- [TaskEntity.java:61](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/entity/TaskEntity.java#L61) `execution_id VARCHAR(500)`

**问题**：来自 task 的 executionId（最长 500 字符）写入 trace 表（100 字符）会被截断。

**影响**：后续按 executionId 查询 trace 时找不到记录，Trace 链路断裂。

**修复方案**：修改 `schema.sql` 将 `autonomy_trace_events.execution_id` 长度改为 500，与 TaskEntity 对齐。

---

### 8.8 断点 B8：@EnableAsync 死配置（🟠 P1）

**位置**：[LivingAgentCoreConfig.java:44](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/config/LivingAgentCoreConfig.java#L44)

**问题**：`@EnableAsync` 已开启，但全代码库无 `@Async` 注解（搜索返回 0 结果）。异步通过 `CompletableFuture.supplyAsync()`（默认 ForkJoinPool.commonPool）实现，集中在 AgentService 4 处、DialogueService 4 处、DepartmentChatService 1 处。

**影响**：
- ForkJoinPool.commonPool 与业务线程池不隔离，LLM 调用阻塞时影响其他并行任务
- `@EnableAsync` 是死配置，易误导维护者以为使用了 Spring 异步机制

**修复方案**：
- 定义专用 `TaskExecutor` bean（核心 10、最大 50、队列 100）
- `triggerAsyncFinalResponse` 等长任务改用 `@Async("llmTaskExecutor")`
- 移除 `@EnableAsync` 或真正使用它

---

### 8.9 断点 B9：TaskRepository.findByExecutionId 无 UNIQUE 约束（🟡 P2）

**位置**：
- [TaskRepository.java:31](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/repository/TaskRepository.java#L31) `Optional<TaskEntity> findByExecutionId(String executionId);`
- schema.sql `tasks` 表 `execution_id` 列无 UNIQUE 约束

**问题**：若多个 task 共享同一 executionId（部门并行任务场景），调用 `findByExecutionId` 会抛 `IncorrectResultSizeException`。

**修复方案**：要么给 `execution_id` 加 UNIQUE 约束（业务上保证一对一），要么改用 `List<TaskEntity> findByExecutionId(String executionId)` 返回列表。

---

### 8.10 断点 B10：ReceiptStatus 终态定义不一致（🟡 P2）

**位置**：
- [ReceiptStatus.java:46-48](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/ReceiptStatus.java#L46) `isTerminal()` 只把 COMPLETED/FAILED 视为终态
- [JpaEmployeeExecutionReceiptService.java:177-180](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/JpaEmployeeExecutionReceiptService.java#L177) `isExecutionComplete` 把 COMPLETED/DEGRADED/FAILED 都视为终态

**问题**：DEGRADED 在"执行完成"判定时算终态，但在 ReceiptStatus.isTerminal() 不算终态。

**影响**：`triggerAsyncFinalResponse` 触发条件不一致，DEGRADED 状态可能触发也可能不触发最终响应。

**修复方案**：统一终态定义，把 DEGRADED 加入 `ReceiptStatus.isTerminal()` 或从 `isExecutionComplete` 移除。

---

### 8.11 断点 B11：声纹/OAuth token 校验 stub（🟠 P1 安全，部分修复）

**位置**：[PermissionServiceImpl.java:403-444](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/impl/PermissionServiceImpl.java#L403)

**当前状态**：
- ✅ OAuth token 校验已接入 `List<OAuthService>` 真实调用（阶段1），钉钉/飞书/企业微信 token 通过对应 OAuthService 实现验证
- ⚠️ `validateVoiceVector` 仅检查 `voiceVector != null && voiceVector.length > 0`，未实现余弦相似度比对

**剩余风险**：声纹校验仍为 stub，伪造 voiceVector 可绕过认证。

**修复方案**：
- 声纹：实现余弦相似度比对，阈值 0.85

---

### 8.12 断点 B12：ToolNeuron 缺失（🟠 P1 架构偏离）

**位置**：`living-agent-core/src/main/java/com/livingagent/core/neuron/impl/` 目录无 `ToolNeuron.java`

**问题**：项目规则要求"Layer 3: ToolNeuron — 固定模型，工具检测/兜底处理"，但代码中只有 `BitNetNeuron.java`（`@Deprecated` 已停用），且 [BitNetNeuron.java:31-43](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/neuron/impl/BitNetNeuron.java#L31) 注释说明 TOOL_CALL 意图已临时路由到 Qwen3Neuron。

**影响**：第三层 LLM 架构与设计文档不一致，工具检测/兜底处理实际由 Qwen3Neuron 承担。

**修复方案**：
- 选项 A：在 `model_daemon.py` 中实现 `tool_intent` service 并恢复 BitNetNeuron
- 选项 B：更新项目规则文档，移除 ToolNeuron 描述，明确 TOOL_CALL 走 Qwen3Neuron

---

### 8.13 断点 B13：updateProjectFromExecution N+1 查询（🟡 P2）

**位置**：[ExecutionReceiptTaskProjectBridge.java:99-125](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/ExecutionReceiptTaskProjectBridge.java#L99)

**问题**：同一 `findByProjectIdOrderByCreatedAtAsc(projectId)` List 被拉取 3 次：
- 第 104 行 `.size()` 第 1 次
- 第 105-107 行 `.stream().filter(...)` 第 2 次
- 第 108-110 行 `.stream().filter(...)` 第 3 次

**修复方案**：一次查询后内存聚合：
```java
List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
int total = tasks.size();
long completed = tasks.stream().filter(...).count();
long failed = tasks.stream().filter(...).count();
```

---

### 8.14 断点 B14：schema.sql 与 init-db/01_init.sql 重复（🟡 P2）

**位置**：
- `living-agent-core/src/main/resources/db/schema.sql`（2300+ 行）
- `init-db/01_init.sql`（前 60 行与 schema.sql 完全相同）

**问题**：两份 2300+ 行文件内容重复，维护风险高。

**修复方案**：统一使用 `schema.sql`（核心模块）+ `01_init.sql`（Docker 初始化）维护表结构，不再创建 V 版本迁移文件。

---

### 8.15 断点 B15：DagTaskRepository JPQL 含 JSONB（🟡 P2）

**位置**：[DagTaskRepository.java:28](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/repository/DagTaskRepository.java#L28)

**问题**：`@Query` 含 JSONB 数组判断，JPQL 无法正确解析，需改为原生 SQL（`nativeQuery = true`）。

---

## 9. 后端 Service 完整性速查表

| Service | 注入 | 是否真实 | 是否持久化 | 闭环状态 |
|---------|------|---------|-----------|---------|
| DepartmentChatService | 35 个依赖 | ✅ 全真实 | ✅ DB | ✅ 完整 |
| ConversationServiceImpl | DepartmentConversationRepository | ✅ | ✅ DB + @Transactional | ✅ 完整 |
| TaskCheckout | TaskRepository | ✅ | ✅ DB 双写 | ✅ 完整 |
| TaskCheckoutSyncService | TaskRepository | ✅ | ✅ DB | ✅ 完整 |
| PublicTaskEventPublisher | DepartmentWebSocketHandler | ✅ | - | ✅ 完整 |
| ExecutionReceiptTaskProjectBridge | TaskRepository、ProjectRepository | ✅ | ✅ task + project 均写 DB | ✅ 已修复（阶段1） |
| ApprovalServiceImpl | ApprovalInstanceEntity/ApprovalWorkflowEntity + Repository | ✅ 业务逻辑 | ✅ DB 持久化 | ✅ 已修复（阶段1） |
| ProjectServiceImpl | ProjectRepository | ✅ persistProject 写 DB | ✅ DB 优先查询 + 内存回退 | ✅ 已修复（阶段2，B-1-7） |
| EmployeeServiceImpl | EnterpriseEmployeeRepository | ✅ JPA 持久化 | ✅ DB 加载缓存 | ✅ 已修复（阶段1，B-1-8） |
| UnifiedAuthService | OAuthService 等 | ✅ | ✅ DB 双写 + 启动加载 | ✅ 已修复（阶段2，B-1-10） |
| DepartmentNotificationService | DepartmentChatService | ✅ | ✅ DB 双写 | ✅ 已修复（阶段3，B-1-11） |
| SystemConfigService | FounderService、TenantService | ✅ tenant 持久化 | ✅ DB 双写 + 启动加载 | ✅ 已修复（阶段2，B-1-9） |
| BackupRecoveryService | 无 | 🟡 createSnapshot 真实 | ❌ restore 假还原 | ❌ 还原失效 |
| OrganizationQueryServiceImpl | DepartmentRepository 等 | ✅ 大部分 | ✅ DB | ✅ 已修复（阶段2，B-1-14） |
| PerformanceDashboardService | 2 个 Service | 🟡 buildSummary 真实 | - | 🟡 Top/Bottom 仅 InMemory 可用 |
| TaskWorkflowService | TaskRepository | ✅ 审查持久化 + @Transactional | ✅ | ✅ 已修复：注入TaskRepository，summarizeReview 持久化审查结果 |
| MessageController | MessageRepository | ✅ 全部真实实现 | ✅ | ✅ 已修复：MessageEntity + MessageRepository + common.ApiResponse |
| SimpleApprovalManager | ~~已删除~~ | ~~❌ return YES~~ | ~~❌~~ | ❌ 已移除（工具审批闸门由 BrainBoundaryEnforcer 替代） |
| ReceptionController | VisitorRepository | ✅ 全部真实实现 | ✅ | ✅ 已修复：VisitorEntity + VisitorRepository + 关键词匹配 + common.ApiResponse |
| DefaultToolExecutor | 无 | ✅ 返回 failure | - | ✅ 已修复：不再返回假成功 |
| FounderService | FounderCheckStrategy | ✅ | ✅ DB 委托 | ✅ 完整 |
| EnterpriseEmployeeService | EnterpriseEmployeeRepository | ✅ | ✅ DB + @Transactional | ✅ 完整 |
| JpaLedgerService | LedgerTransactionRepository | ✅ | ✅ DB + @Transactional | ✅ 完整 |
| JpaArtifactRecordService | ArtifactRecordRepository | ✅ | ✅ DB | ✅ 完整 |
| ClientDeviceRegistryService | ClientDeviceRepository | ✅ | ✅ DB + @Transactional | ✅ 完整 |
| ConversationOrchestrator | 9 个依赖 | ✅ | 🟡 澄清上下文写文件 | 🟡 interventionNeuron=null |
| AutonomyTraceService | TraceEventRepository | ✅ | ✅ DB 优先查询 + 内存兜底 | ✅ 已修复（阶段1） |
| RuntimeEventStore | DataNamespaceService | ✅ | ✅ DB 优先 + 文件降级 | ✅ 已修复（阶段2，B-0-4） |

---

## 10. 后端 Core 模块闭环验证

### 10.1 ✅ 真实落地能力

| 能力 | 实现位置 | 验证 |
|------|---------|------|
| LLM 调用 | [MainBrain.java:613](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java#L613) `provider.chat(request).join()` | 真实调用，含工具调用迭代（最多 20 次）、降级模型 fallback |
| Brain ReAct 循环 | BrainReActEngine | 真实 ReAct 循环，调用 `provider.chat(request).join()` |
| 意图分析 | LlmBasedDialogueAnalyzer | 调用 `mainBrain.callLlmWithUser()`，JSON Schema 约束 |
| 主脑规划 | LlmBasedMainBrainTaskDirector | 调用 `mainBrain.callLlm()` |
| 结果聚合 | LlmBasedExecutionResultAggregator | 调用 `mainBrain.callLlm(AGGREGATE_SYSTEM_PROMPT, ...)` |
| 员工分派 | LlmBasedFixedEmployeeDispatcher | 调用 `mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, ...)`，覆盖了 reassign 默认 stub |
| 模型池动态选择 | BrainModelResolver.resolve | 能力评分 → DB 分配 → Selector → 默认值，含健康度冷却 |
| 向量检索 | LayeredKnowledgeBaseImpl + QdrantVectorService | 真实 Qdrant 向量检索 + 混合搜索 |
| 工具执行 | FileEditTool / GitHubTool / WindowsAppTool 等 | 真实文件 IO / gh CLI / HTTP 调用 |
| 权限隔离 | AccessLevel 4 级 + MainBrain.checkPermission | 真实落地，与规则文档一致 |
| 技能加载 | SkillRegistryImpl @PostConstruct | 从磁盘加载 25 个 SKILL.md，支持热重载 |
| 通道消息投递 | ChannelManagerImpl → NeuronRegistry | 真实消息投递闭环 |
| 固定员工注册 | FixedEmployeeRegistry @PostConstruct | 从 FixedEmployeeDefinitionRepository 加载 28 个员工 |

### 10.2 ❌ 未实现/偏离设计能力

| 能力 | 设计要求 | 实际状态 | 影响 |
|------|---------|---------|------|
| ToolNeuron | Layer 3 工具检测/兜底 | BitNetNeuron @Deprecated，TOOL_CALL 路由到 Qwen3Neuron | 三层 LLM 架构缺一层 |
| 声纹登录 | 余弦相似度比对 | 仅检查 length > 0 | 可伪造声纹绕过 |
| 钉钉/飞书/企业微信 OAuth | 调用三方 getuserinfo API | 仅格式检查 | 可伪造 token 绕过 |
| 工具调用审批 | 人工二次确认 | 硬编码 return YES | 所有工具自动批准 |
| 高风险任务人工接管 | InterventionNeuron 处理 | GatewayConfig 注入 null | 降级到大脑自行判断 |
| 消息中心 | 持久化 + WebSocket 推送 | MessageController 全 stub | 桌面端消息列表永远空白 |
| 项目进度更新 | ProjectEntity.progress 落库 | 只写文件 | DB 查询永远返回 0.0 |
| 审批实例持久化 | ApprovalInstance 落库 | ConcurrentHashMap | 重启丢失所有审批 |
| 备份还原 | restoreSnapshot 真实还原 | 假还原 | 备份可用但还原失效 |
| 部门审计日志 | AuditLogRepository 查询 | 返回 emptyList | 审计日志查询永远为空 |

---

## 11. 后端修复任务清单（追加到原 P0/P1/P2 之后）

### 11.1 P0 后端：闭环阻断（立即修复）

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~B-0-1 ExecutionResult 持久化~~ ✅ 已完成（阶段2） | [DepartmentExecutionResultEntity.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/entity/DepartmentExecutionResultEntity.java)、[DepartmentChatService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java) | 新建 DepartmentExecutionResultEntity/Repository + department_execution_results 表；executionResultCache 改为 DB 双写，内存未命中时 DB 回退查询 |
| ~~B-0-2 项目统计落库~~ ✅ 已完成（阶段1） | [ExecutionReceiptTaskProjectBridge.java:96-125](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/ExecutionReceiptTaskProjectBridge.java#L96) | 末尾增加 `projectRepository.save(project)` 更新 progress；已完成 2026-06-29 |
| ~~B-0-3 回执链路加事务~~ ✅ 已完成（阶段2） | [JpaEmployeeExecutionReceiptService.java:108](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/JpaEmployeeExecutionReceiptService.java#L108) | `recordReceipt` 加 `@Transactional`；listener 通知和 review workflow 通过 `TransactionSynchronization.afterCommit` 延迟到事务提交后执行 |
| ~~B-0-4 RuntimeEventStore 改 DB~~ ✅ 已完成（阶段2） | [RuntimeEventStore.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/runtime/RuntimeEventStore.java) | DB 优先持久化，文件系统降级回退；新建 RuntimeEventEntity/Repository + runtime_events 表（参见 MODEL 文档 2.3.2/7 节） |
| ~~B-0-5 修复 tenantId 硬编码~~ ✅ 已完成（阶段1） | [ExecutionReceiptTaskProjectBridge.java:67](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/ExecutionReceiptTaskProjectBridge.java#L67) | 新增 `resolveTenantId`：executionResult.metadata.tenantId > receipt.metadata.tenantId > task.tenantId > `_system` 兜底；已完成 2026-06-29 |
| ~~B-0-6 实现 SimpleApprovalManager 真实审批~~ ❌ 已废弃（被第三部分替代） | ~~SimpleApprovalManager.java:33-36~~ | **已删除**。第三部分"移除工具执行审批"方案已实施（ApprovalManager 接口和 SimpleApprovalManager 实现已删除）；工具执行由 BrainBoundaryEnforcer 四重校验等价保障；业务审批保留 ApprovalService/PlanApprovalService |
| ~~B-0-7~~ ✅ 实现 InterventionNeuron | [InterventionNeuron.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/intervention/InterventionNeuron.java) | 已完整实现（`@Service` + `AbstractNeuron`，含 `handleInterventionRequest`/`handleInterventionResponse`/`handleLearningSignal` 三分支） |

### 11.2 P1 后端：稳定性与一致性

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~B-1-1 AutonomyTraceService 查询合并~~ ✅ 已完成（阶段1） | [AutonomyTraceService.java:117-246](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/AutonomyTraceService.java#L117) | 4 个查询方法改为 DB 优先，内存兜底；已完成 2026-06-29 |
| ~~B-1-2 修复 triggerAsyncFinalResponse~~ ✅ 已完成（阶段4） | [DepartmentChatService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java) | 重命名为 triggerFinalResponse，消除"Async"误导 |
| ~~B-1-3 修复 TraceEventEntity.execution_id 长度~~ ✅ 已完成（阶段4） | [TraceEventEntity.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/entity/TraceEventEntity.java) | execution_id 从 VARCHAR(100) 改为 VARCHAR(500)，schema.sql 同步 |
| ~~B-1-4 实现 @Async 或移除 @EnableAsync~~ ✅ 已完成（阶段4） | [LivingAgentCoreConfig.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/config/LivingAgentCoreConfig.java) | 新增 llmTaskExecutor Bean（ThreadPoolTaskExecutor: core=4, max=10, queue=50） |
| ~~B-1-5 实现声纹/OAuth token 真实校验~~ ✅ 已完成（阶段4） | [PermissionServiceImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/impl/PermissionServiceImpl.java) | OAuth token 校验已接入 `List<OAuthService>`；声纹余弦相似度比对已委托 VoicePrintService.verify()（B-1-5）；已完成 2026-06-30 |
| ~~B-1-6 ApprovalServiceImpl 持久化~~ ✅ 已完成（阶段1） | [ApprovalServiceImpl.java:13-14](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/approval/impl/ApprovalServiceImpl.java#L13) | 新建 `ApprovalInstanceEntity` + `ApprovalWorkflowEntity` + 2 Repository；`approvalStore`/`workflowStore` 改为 DB；表结构变更写入 `schema.sql` + `01_init.sql`；已完成 2026-06-29 |
| ~~B-1-7 ProjectServiceImpl 读取走 DB~~ ✅ 已完成（阶段2） | [ProjectServiceImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/project/impl/ProjectServiceImpl.java) | getProject 改为 DB 优先查询，内存作为缓存回退 |
| ~~B-1-8~~ ✅ EmployeeServiceImpl 写入走 DB | [JpaEmployeeServiceImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/employee/impl/JpaEmployeeServiceImpl.java) | 已改为 JPA 持久化（save/deleteById），启动时从 DB 加载缓存，旧 EmployeeServiceImpl 未注册为 bean |
| ~~B-1-9 SystemConfigService Provider 持久化~~ ✅ 已完成（阶段2） | [SystemConfigService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/SystemConfigService.java) | 注入 `ProviderConfigRepository`；启动时从 DB 加载；写操作 DB 双写 |
| ~~B-1-10 UnifiedAuthService 会话持久化~~ ✅ 已完成（阶段2） | [UnifiedAuthService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/auth/UnifiedAuthService.java) | 注入 `SessionContextRepository`；启动时从 DB 加载；写操作 DB 双写；内存未命中时 DB 回退查询 |
| ~~B-1-11 DepartmentNotificationService 持久化~~ ✅ 已完成（阶段3） | [DepartmentNotificationService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentNotificationService.java) | 创建 `NotificationEntity`/`NotificationRepository`；`schema.sql`+`01_init.sql` 新增 `notifications` 表；实现 DB 双写 + 启动从 DB 加载 |
| ~~B-1-12 ToolNeuron 独立实现~~ ✅ 已完成（阶段3） | [ToolNeuron.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/neuron/impl/ToolNeuron.java) | 创建 `ToolNeuron`（neuron://tool/qwen35/001）；`ChatNeuronRouter` TOOL_CALL 路由修正；`ChannelConfig` 注册切换 |
| ~~B-1-13 BackupRecoveryService 真实还原~~ ✅ 已标记 @Deprecated（阶段2） | [BackupRecoveryService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/BackupRecoveryService.java) | 标记 `@Deprecated`，真实还原逻辑未实现 |
| ~~B-1-14 OrganizationQueryServiceImpl 实现 getRecentAuditLogs~~ ✅ 已完成（阶段2） | [OrganizationQueryServiceImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/impl/OrganizationQueryServiceImpl.java) | 注入 `AccessAuditLogRepository`，`getRecentAuditLogs` 改为调用 DB 查询 |

### 11.3 P2 后端：代码质量与性能

| 任务 | 修复方案 |
|------|---------|
| B-2-1 修复 updateProjectFromExecution N+1 查询 | 一次查询后内存聚合 |
| B-2-2 修复 TaskRepository.findByExecutionId 无 UNIQUE | 加 UNIQUE 约束或改返回 List |
| B-2-3 统一 ReceiptStatus 终态定义 | DEGRADED 加入 isTerminal() 或从 isExecutionComplete 移除 |
| B-2-4 统一 schema.sql 与 01_init.sql | 两文件内容对齐，不再使用 Flyway V 版本迁移文件 |
| B-2-5 修复 DagTaskRepository JPQL JSONB | 改为 nativeQuery = true |
| B-2-6 PerformanceDashboardService 支持 JPA 实现 | getCompanyTop/Bottom 改用 PerformanceAssessmentRepository |
| B-2-7 异步任务失败重试机制 | CompletableFuture 增加 .exceptionally() 重试 |
| B-2-8 schema.sql 维护说明 | 文件头注释说明此文件为核心模块表结构定义源，Docker 初始化用 01_init.sql |

---

## 12. 后端闭环验收标准（追加）

### 12.1 持久化验收
- [ ] 重启服务后，审批中实例仍可查询到
- [ ] 重启服务后，登录会话仍有效（token 仍可使用）
- [ ] 重启服务后，项目进度数据正确（非 0.0）
- [ ] 重启服务后，Provider 配置（含 apiKey）仍存在
- [ ] 重启服务后，通知未读数正确
- [ ] 重启服务后，部门通知历史可查
- [ ] 重启服务后，进行中的执行链路可继续处理回执

### 12.2 数据一致性验收
- [ ] 回执保存、任务状态更新、Trace 写入在同一事务内
- [ ] 客户端收到 "COMPLETED" 推送后查询 task，状态已为 COMPLETED
- [ ] `ProjectEntity.progress` 与文件系统中的项目事件一致
- [ ] `EmployeeServiceImpl.createEmployee` 写入的数据 `getEmployee` 能读到
- [ ] `TraceEventEntity.execution_id` 长度与 `TaskEntity.execution_id` 一致

### 12.3 安全验收
- [ ] 伪造的钉钉 token（符合 `dt_` 前缀 + 长度 ≥ 20）无法通过校验
- [ ] 工具调用审批可配置为需要人工确认
- [ ] 高风险任务能触发 InterventionNeuron 人工接管
- [ ] 多租户场景下，tenant A 的回执事件不会写入 tenant B 的命名空间

### 12.4 性能验收
- [ ] `updateProjectFromExecution` 单次查询完成统计
- [ ] `triggerAsyncFinalResponse` 不阻塞 receipt listener 线程
- [ ] 异步任务使用专用线程池，不占用 ForkJoinPool.commonPool
- [ ] `AutonomyTraceService.getTraceByExecutionId` 重启后仍能返回完整链路

### 12.5 架构一致性验收
- [ ] ToolNeuron 真实存在，或项目规则文档已更新移除该层
- [ ] `@EnableAsync` 真正使用，或已移除
- [ ] `SimpleApprovalManager.requestApproval` 不再硬编码 YES
- [x] `MessageController` 4 个端点均真实实现
- [x] `TaskWorkflowService.summarizeReview` 有真实业务逻辑或已删除

---

# 第三部分：审批机制精简方案——移除工具执行审批

> **设计目标**：移除"工具执行审批"这一层。数字员工的工具使用已通过 `EmployeeWorkAssignment.allowedTools` 白名单、`ToolRegistryImpl.getByDepartment` 部门隔离、`AbstractBrain.tools` 子集注入、`BrainBoundaryEnforcer.checkAction` 边界检查四重机制做了等价于"审批"的校验；大脑 LLM 在评定、安装、分配工具时已天然完成安全判定，无需再走人工审批或自动审批闸门。
>
> **保留范围**：仅保留 `ApprovalService`（业务审批，针对任务/回执）与 `PlanApprovalService`（计划审批，针对 CODE_CHANGE/DEPLOYMENT_PLAN），用于业务流和计划流的合规把关，与工具执行解耦。
>
> **修订时间**：2026-06-29（根据代码事实重写）
>
> **交叉引用**：MODEL 文档第 7 节结论已同步标注 APPROVAL_REQUIRED 枚举语义修正（工具审批闸门已移除，现对应业务审批 ApprovalService/PlanApprovalService）

---

## 13. 现状分析：工具审批闸门名存实亡

### 13.1 工具审批三层全部失效

| 层 | 期望行为 | 实际行为 | 文件 |
|---|---------|---------|------|
| `ApprovalManager.requestApproval` | 返回 YES/NO/ALWAYS 决策 | 硬编码返回 `ApprovalResponse.YES` | [SimpleApprovalManager.java:33-36](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/impl/SimpleApprovalManager.java#L33) |
| `ToolExecutorService.requiresApproval` | 调用闸门拦截工具 | 仅 `log.info("pending...")` 后继续执行 | [ToolExecutorService.java:52-54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/executor/ToolExecutorService.java#L52) |
| `MainBrain.executeToolCall` | 走 ToolExecutorService 闸门 | 完全不引用 ApprovalManager，直接 `tool.execute` | [MainBrain.java:768-838](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java#L768) |

→ 工具审批闸门当前**不拦截任何工具调用**，移除它不会改变现有运行行为。

### 13.2 大脑已具备等价审批能力

| 校验点 | 实现位置 | 作用 |
|--------|---------|------|
| 工具白名单 | [BrainReActEngine.java:294-311](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/BrainReActEngine.java#L294) 调用 `BrainBoundaryEnforcer.checkAction` | 每个 Brain 配置 `allowedActions` / `forbiddenActions` / `mustEscalateScenarios`，违规直接拒绝 |
| 工具子集 | [AbstractBrain.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/AbstractBrain.java) `tools` 字段 | 每个 Brain 构造时仅注入其授权工具子集，未注入工具不可调用 |
| 部门隔离 | [ToolRegistryImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/registry/ToolRegistryImpl.java) `getByDepartment` | 工具按部门注册，跨部门调用受隔离 |
| 数字员工授权 | [EmployeeWorkAssignment.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/employee/EmployeeWorkAssignment.java) `allowedTools` 字段 | 数字员工层面再限定可调用工具白名单 |
| 边界升级 | `BrainBoundaryEnforcer.BrainBoundary` `mustEscalateScenarios` | 高风险场景强制升级到 MainBrain |

→ 大脑分配工具的过程已经完成"是否允许该工具执行"的判定，再叠加一层工具审批是冗余。

### 13.3 业务审批与计划审批独立于工具审批

- `ApprovalService` 走 `core/approval/`，触发点：[DepartmentChatService.java:352](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java#L352) 员工回执状态为 `NEEDS_APPROVAL` 时自动建审批实例；[ApprovalController.java:145](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L145) HTTP API
- `PlanApprovalService` 走 `core/approval/plan/`，针对 CODE_CHANGE / DEPLOYMENT_PLAN
- 两者均不通过 `ApprovalManager`，移除工具审批对它们无影响

### 13.4 三套机制关系澄清

```
┌─────────────────────────────────┐
│ 业务审批 ApprovalService       │ 保留（业务流合规）
│ - 任务/回执审批                │
│ - 6 态状态机                   │
│ - ApprovalController HTTP API   │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ 计划审批 PlanApprovalService   │ 保留（计划流合规）
│ - CODE_CHANGE                  │
│ - DEPLOYMENT_PLAN              │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ 工具审批 ApprovalManager       │ 移除（已被大脑分配链路替代）
│ - SimpleApprovalManager        │
│ - ToolExecutorService 闸门     │
│ - InterventionDecisionEngine   │
│   ASYNC_APPROVAL（死代码）      │
└─────────────────────────────────┘
```

### 13.5 高风险工具内部审批引用清单

下列三个工具在执行前调用 `ApprovalManager.requestApproval`（实际返回 YES，等同于日志摆设），移除审批闸门后需迁移：

| 工具 | 调用位置 | 替代方案 |
|------|---------|---------|
| `WindowsAutomationTool` | [WindowsAutomationTool.java:262](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAutomationTool.java#L262) | 依赖 BrainBoundaryEnforcer 在 BrainReActEngine 调用前校验 |
| `WindowsAppTool` | [WindowsAppTool.java:162](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAppTool.java#L162) | 同上 |
| `SandboxExecutorImpl` | [SandboxExecutorImpl.java:441](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/sandbox/SandboxExecutorImpl.java#L441) | 同上；破坏性子操作（如 sandbox 销毁）改为调用 `PlanApprovalService.submitPlan` |

---

## 14. 工具执行审批移除设计

### 14.1 设计原则

1. **工具执行不再走审批**：大脑分配工具时已通过四重校验等价于审批
2. **业务审批与计划审批保留**：用于业务流和计划流的合规把关，与工具执行解耦
3. **高风险工具内部审批逻辑外迁**：三个工具的 `ApprovalManager` 引用迁移到 `BrainBoundaryEnforcer` 或 `PlanApprovalService`
4. **删除死代码**：`InterventionDecisionEngine.ASYNC_APPROVAL` 决策类型与相关代码清除
5. **配置简化**：不再需要 `approval.mode` / `approval.tools.*` 工具级开关，仅保留业务审批配置

### 14.2 移除清单

| 序号 | 移除对象 | 文件 | 处理方式 |
|------|---------|------|---------|
| R-1 | `ApprovalManager` 接口 | `core/security/ApprovalManager.java` | 删除整个接口（8 个方法） |
| R-2 | `SimpleApprovalManager` 实现 | [SimpleApprovalManager.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/impl/SimpleApprovalManager.java) | 删除整个类 |
| R-3 | `ApprovalResponse` 枚举 | `core/security/ApprovalResponse.java` | 删除（仅 ApprovalManager 使用） |
| R-4 | `ToolExecutor.requiresApproval()` 默认方法 | [ToolExecutor.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/ToolExecutor.java) | 删除默认方法，子类同步清理 |
| R-5 | `ToolExecutorService` 闸门日志 | [ToolExecutorService.java:52-54](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/executor/ToolExecutorService.java#L52) | 删除 `if (requiresApproval()) log.info(...)` 三行 |
| R-6 | `MainBrain.executeToolCall` 中的 ApprovalManager 引用 | [MainBrain.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java) | 当前已无引用，确认清理 |
| R-7 | `WindowsAutomationTool` 内部审批调用 | [WindowsAutomationTool.java:262](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAutomationTool.java#L262) | 删除调用，依赖 BrainBoundaryEnforcer 校验 |
| R-8 | `WindowsAppTool` 内部审批调用 | [WindowsAppTool.java:162](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAppTool.java#L162) | 同上 |
| R-9 | `SandboxExecutorImpl` 内部审批调用 | [SandboxExecutorImpl.java:441](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/sandbox/SandboxExecutorImpl.java#L441) | 同上；破坏性子操作改走 PlanApprovalService |
| R-10 | `InterventionDecisionEngine.ASYNC_APPROVAL` 决策类型 | [InterventionDecisionEngineImpl.java:230-232](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/intervention/impl/InterventionDecisionEngineImpl.java#L230) | 删除枚举值与相关分支、`pendingDecisions` Map |
| R-11 | `BrainConfig` 中 ApprovalManager bean 注册 | `BrainConfig.java` | 清理 `@Bean` 注册 |
| R-12 | `autoApproveTools` / `sessionAllowlist` 硬编码白名单 | [SimpleApprovalManager.java:22-25](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/impl/SimpleApprovalManager.java#L22) | 随 R-2 一并删除 |

### 14.3 高风险工具内部审批逻辑迁移

**方案 A：依赖 BrainBoundaryEnforcer（推荐，默认）**

工具内部不再做审批，由 `BrainReActEngine.executeToolCalls` 在调用工具前统一做 `BrainBoundaryEnforcer.checkAction` 校验：

```java
// WindowsAutomationTool.execute 内部移除：
// ApprovalResponse resp = approvalManager.requestApproval(getName(), call);
// if (resp != ApprovalResponse.YES) return error("审批未通过");

// 工具直接执行，前置校验由 BrainReActEngine 完成
public ToolResult execute(Map<String, Object> params, ExecutionContext context) {
    return doExecute(params, context);
}
```

**方案 B：破坏性子操作走 PlanApprovalService（仅识别出的破坏性操作）**

对于明确破坏性的子操作（如 `docker rm`、`file_delete`、`shell_execute`、`sandbox destroy`），可在工具内部识别后转为 `PlanApprovalService.submitPlan`，由人工或大脑审批"计划"而非"工具调用"：

```java
public ToolResult execute(Map<String, Object> params, ExecutionContext context) {
    if (isDestructiveOperation(params)) {
        PlanApprovalService.PlanRequest plan = new PlanApprovalService.PlanRequest(
            PlanType.DEPLOYMENT_PLAN,
            buildDestructiveOpDescription(params),
            context.getUserId()
        );
        planApprovalService.submitPlan(plan);
        return ToolResult.pending("破坏性操作已提交计划审批，等待审批结果");
    }
    return doExecute(params, context);
}
```

→ 默认采用方案 A；方案 B 仅用于已识别的破坏性子操作（需工具内部维护破坏性操作清单）。

### 14.4 BrainBoundaryEnforcer 配置补强

为弥补工具审批移除后的安全网，对 `BrainBoundaryEnforcer` 配置做以下补强：

```yaml
# application.yml
brain:
  boundary:
    enabled: true
    enforce-on:
      - main-brain
      - tech-brain
      - finance-brain
      - hr-brain
      - legal-brain
      - sales-brain
      - cs-brain
      - admin-brain
      - ops-brain
    # 各 brain 的 forbiddenActions / mustEscalateScenarios 已在 BrainBoundaryEnforcer.java:20-137 配置
    # 此处仅控制总开关与审计
    audit-log: true                  # 边界检查命中记录到审计日志
    audit-log-file: logs/brain-boundary-audit.log
```

### 14.5 InterventionDecisionEngine 决策类型精简

`InterventionDecisionEngineImpl` 现有决策类型与处理：

| 决策类型 | 原用途 | 处理 |
|---------|------|------|
| `REALTIME_CONFIRM` | 实时确认 | 保留，改为推送业务审批（`ApprovalService.createApproval`） |
| `ASYNC_APPROVAL` | 异步审批（死代码） | **删除**，工具审批已移除 |
| `MANDATORY_HUMAN` | 强制人工 | 保留，转为业务审批触发 |
| `AUTO_EXECUTE` | 自动执行 | 保留，工具调用由大脑边界检查通过后自动执行 |

```java
// InterventionDecisionEngineImpl 修改后
public enum InterventionDecisionType {
    REALTIME_CONFIRM,    // 业务审批触发
    MANDATORY_HUMAN,     // 强制业务审批
    AUTO_EXECUTE         // 大脑边界检查通过后自动执行
    // ASYNC_APPROVAL 已删除
}
```

### 14.6 业务审批与计划审批保持不变

- `ApprovalService` 接口、`ApprovalServiceImpl`、`ApprovalController` 保持现有行为
- `PlanApprovalService` 接口、`InMemoryPlanApprovalService`/`JpaPlanApprovalService` 保持现有行为
- 仅做一项独立改进：`ApprovalServiceImpl` 的 `approvalStore`/`workflowStore` 从 `ConcurrentHashMap` 改为 JPA Repository（参见第二部分 B-1-1 任务）

### 14.7 与原"统一开关方案"对比

| 维度 | 原方案（已废弃） | 新方案（本节） |
|------|----------------|---------------|
| 工具调用审批入口 | ApprovalDispatcher 统一入口 | 无需审批入口，由 BrainBoundaryEnforcer 在工具执行前校验 |
| 配置开关 | `approval.mode` = AUTO/MANUAL/HYBRID | `brain.boundary.enabled` + 各 Brain 的 forbiddenActions |
| 风险评估 | RiskAssessmentService 计算风险分数 | BrainBoundaryEnforcer 静态规则 + mustEscalateScenarios |
| 人工介入 | HumanApprovalService 推送 IM | 仅破坏性操作走 PlanApprovalService |
| 持久化 | ApprovalDecisionEntity 审计日志 | brain-boundary-audit.log 文件日志 |
| 改造范围 | 新增 ApprovalDispatcher + BrainApprovalService + HumanApprovalService + 6 个新文件 | 删除 ApprovalManager + SimpleApprovalManager + ApprovalResponse；改造 3 个工具内部引用 |
| 与现状契合度 | 需新建大量基础设施 | 删除空壳代码 + 复用已有 BrainBoundaryEnforcer |

→ 新方案改造范围小、维护成本低、与现有大脑架构契合。

---

## 15. 大脑工具分配校验链路（替代工具审批）

### 15.1 四重校验链路

```
[工具调用请求]
  ↓
[1] ToolRegistryImpl.getByDepartment(department)
    └─ 工具按部门注册，跨部门调用受隔离
  ↓
[2] AbstractBrain.tools 子集注入
    └─ 每个 Brain 构造时仅注入授权工具
    └─ MainBrain.executeToolCall 仅能从 this.tools 查找
  ↓
[3] EmployeeWorkAssignment.allowedTools
    └─ 数字员工层面再限定可调用工具白名单
  ↓
[4] BrainBoundaryEnforcer.checkAction(brainId, toolName)
    └─ allowedActions 白名单
    └─ forbiddenActions 黑名单
    └─ mustEscalateScenarios 高风险升级
    └─ mustEscalateToMainBrain 强制升级主脑
  ↓
[工具执行] tool.execute(params, context)
```

### 15.2 各 Brain 边界配置（已存在，无需新增）

| Brain | forbiddenActions（示例） | mustEscalateScenarios |
|-------|--------------------------|----------------------|
| main-brain | financial_payment, code_development | - |
| finance-brain | business_strategy, legal_conclusion | high_amount_payment |
| tech-brain | financial_payment, contract_signing | production_db_drop |
| fixed-employee | codebase_access, apply_code_fix, evolution_write | - |
| 其他 | 详见 BrainBoundaryEnforcer.java:20-137 | - |

### 15.3 数字员工职责卡（duty-cards）

每个数字员工的职责边界已在职责卡中定义：

- [documents/shared/company/duty-cards/tech.md](file:///f:/SoarCloudAI/docker/living-agent-service/documents/shared/company/duty-cards/tech.md) 技术部员工操作边界
- [documents/shared/company/duty-cards/finance.md](file:///f:/SoarCloudAI/docker/living-agent-service/documents/shared/company/duty-cards/finance.md) 财务部员工操作边界
- 其他部门职责卡同目录

→ 职责卡 + BrainBoundaryEnforcer 配置 + AbstractBrain.tools 子集 = 完整的工具调用安全边界，等价于"自动审批"。

### 15.4 数字员工职责已具备工具分配能力

| 部门 | 大脑 | 职责 | 可调用的工具 |
|------|------|------|------------|
| 技术 | TechBrain | 25 个技能（github、docker、cicd、code-review 等） | FileEditTool、GitHubTool、DockerTool、WindowsAppTool、WindowsAutomationTool |
| 财务 | FinanceBrain | 财务报表、报销、预算 | InvoiceProcessTool、BudgetManageTool、ErpTool |
| 人力 | HrBrain | 员工生命周期、考勤绩效 | HrSystemTool |
| 法务 | LegalBrain | 合同审查、合规 | 合同相关工具 |
| 销售 | SalesBrain | 销售流程、客户管理 | CrmTool |
| 客服 | CsBrain | 客户服务 | 客服相关工具 |
| 行政 | AdminBrain | 行政事务（15 个技能） | 行政相关工具 |
| 运营 | OpsBrain | 运营管理（9 个技能） | 运营相关工具 |
| 跨部门 | MainBrain | 复杂推理、跨部门协调 | 高风险/跨部门工具调用 |

→ 大脑在调用工具前已通过 LLM 推理"是否需要该工具"+ 边界校验"是否允许该工具"，这一过程本身即是审批。

### 15.5 大脑 LLM 推理能力已验证

- [MainBrain.callLlmWithTools](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java#L613) 已支持工具调用迭代（最多 20 次）
- [BrainReActEngine](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/BrainReActEngine.java) 已支持 ReAct 循环（含 `BrainBoundaryEnforcer.checkAction` 调用）
- 模型池动态选择已落地（BrainModelResolver），各部门大脑可指定专用模型

---

## 16. 工具审批移除任务清单

### 16.1 P0：移除工具审批闸门

| 任务 | 文件 | 处理 |
|------|------|------|
| ~~T-0-1~~ ✅ 删除 `ApprovalManager` 接口 | `core/security/ApprovalManager.java` | 已删除（阶段1） |
| ~~T-0-2~~ ✅ 删除 `SimpleApprovalManager` 实现 | ~~SimpleApprovalManager.java~~ | 已删除（阶段1） |
| ~~T-0-3~~ ✅ 删除 `ApprovalResponse` 枚举 | `core/security/ApprovalResponse.java` | 已删除（阶段1） |
| ~~T-0-4~~ ✅ 清理 `ToolExecutor.requiresApproval` | [ToolExecutor.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/ToolExecutor.java) | 已清理（阶段1） |
| ~~T-0-5~~ ✅ 清理 `ToolExecutorService` 闸门日志 | [ToolExecutorService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/executor/ToolExecutorService.java) | 已清理（阶段1） |
| ~~T-0-6~~ ✅ 确认 `MainBrain` 无 ApprovalManager 引用 | [MainBrain.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/impl/MainBrain.java) | 已确认无残留（阶段1） |
| ~~T-0-7~~ ✅ 清理 `BrainConfig` 中的 bean 注册 | `BrainConfig.java` | 已清理（阶段1） |

### 16.2 P0：高风险工具内部引用迁移

| 任务 | 文件 | 处理 |
|------|------|------|
| T-0-8 迁移 `WindowsAutomationTool` 内部审批 | [WindowsAutomationTool.java:262](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAutomationTool.java#L262) | 删除 `approvalManager.requestApproval` 调用；依赖 BrainBoundaryEnforcer 在 BrainReActEngine 调用前校验 |
| T-0-9 迁移 `WindowsAppTool` 内部审批 | [WindowsAppTool.java:162](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/builtin/WindowsAppTool.java#L162) | 同上 |
| T-0-10 迁移 `SandboxExecutorImpl` 内部审批 | [SandboxExecutorImpl.java:441](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/sandbox/SandboxExecutorImpl.java#L441) | 同上；破坏性子操作（如 sandbox 销毁）改为调用 `PlanApprovalService.submitPlan` |
| T-0-11 工具类构造函数移除 ApprovalManager 依赖 | 三个工具类的构造函数 | 删除 `ApprovalManager approvalManager` 字段与构造参数 |

### 16.3 P1：InterventionDecisionEngine 精简

| 任务 | 文件 | 处理 |
|------|------|------|
| T-1-1 删除 `ASYNC_APPROVAL` 决策类型 | [InterventionDecisionEngineImpl.java:230-232](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/intervention/impl/InterventionDecisionEngineImpl.java#L230) | 从枚举中移除；删除相关分支与 `pendingDecisions` Map |
| T-1-2 `REALTIME_CONFIRM` 决策接入 ApprovalService | [InterventionDecisionEngineImpl.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/intervention/impl/InterventionDecisionEngineImpl.java) | 改为调用 `approvalService.createApproval`，触发业务审批 |
| T-1-3 `MANDATORY_HUMAN` 决策接入 ApprovalService | 同上 | 同上 |
| T-1-4 `InterventionController` 文档更新 | [InterventionController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/InterventionController.java) | API 文档移除 `ASYNC_APPROVAL` 描述 |

### 16.4 P1：BrainBoundaryEnforcer 补强

| 任务 | 文件 | 处理 |
|------|------|------|
| ~~T-1-5 增加审计日志输出~~ ✅ 已完成（阶段3） | [BrainBoundaryEnforcer.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/BrainBoundaryEnforcer.java) | 创建 `BrainBoundaryAuditEntity`/`Repository`；`schema.sql`+`01_init.sql` 新增 `brain_boundary_audit` 表；`checkAction` 审计落盘 |
| ~~T-1-6 配置项接入~~ ✅ 已完成（阶段2） | [application.yml](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-app/src/main/resources/application.yml) + [BrainBoundaryProperties.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/BrainBoundaryProperties.java) | `@ConfigurationProperties(prefix="brain.boundary")`；enabled 开关 + consecutiveFailuresThreshold + auditLog 配置；checkAction 加 enabled 检查 |
| ~~T-1-7 边界配置可视化接口~~ ✅ 已完成（阶段3） | [BrainBoundaryController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/BrainBoundaryController.java) | 新增 `GET /api/brain-boundary/config` + `/config/{brainId}`；`BrainBoundaryEnforcer` 新增 `getBoundaries()`/`getBoundary()`/`getProperties()` 公开方法 |

### 16.5 P1：业务审批与计划审批独立改进

| 任务 | 文件 | 处理 |
|------|------|------|
| T-1-8 `ApprovalServiceImpl` 持久化改造 | [ApprovalServiceImpl.java:13-14](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/approval/impl/ApprovalServiceImpl.java#L13) | 新建 `ApprovalInstanceEntity` + Repository；内存 Map 改 DB |
| T-1-9 审批回调从 Controller 移到 Service | [ApprovalController.java:36-46](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java#L36) | 新建 `ApprovalCallbackService` 注册回调 |
| T-1-10 `PlanApprovalService` 真实接入 | `core/approval/plan/PlanApprovalService.java` | 落实 `submitPlan` 调用方（CODE_CHANGE 触发点） |

### 16.6 P2：审计与监控

| 任务 | 处理 |
|------|------|
| T-2-1 工具调用审计日志 | 新建 `ToolInvocationAuditEntity`，记录每次工具调用：toolName、brainId、employeeId、parameters、result、boundaryCheckResult |
| T-2-2 审计日志查询接口 | `GET /api/audit/tool-invocations?toolName=&from=&to=` |
| T-2-3 `BrainBoundaryEnforcer` 命中统计 | `GET /api/audit/brain-boundary-hits` 统计违规拒绝次数、升级次数 |
| T-2-4 工具调用 Prometheus 指标 | 暴露 `tool_invocation_total{toolName,brainId,result}` counter |

---

## 17. 工具审批移除验收标准

### 17.1 闸门移除验收
- [ ] 全项目搜索 `ApprovalManager` 无生产代码引用（仅历史文档保留）
- [ ] 全项目搜索 `ApprovalResponse` 无生产代码引用
- [ ] 全项目搜索 `requestApproval` 无生产代码引用
- [ ] `ToolExecutor.requiresApproval` 默认方法已删除
- [ ] `ToolExecutorService` 不再调用 `requiresApproval`
- [ ] `MainBrain.executeToolCall` 无审批相关代码

### 17.2 高风险工具迁移验收
- [ ] `WindowsAutomationTool.execute` 不再调用 `approvalManager.requestApproval`
- [ ] `WindowsAppTool.execute` 不再调用 `approvalManager.requestApproval`
- [ ] `SandboxExecutorImpl.execute` 不再调用 `approvalManager.requestApproval`
- [ ] 三个工具构造函数不再依赖 `ApprovalManager`
- [ ] 破坏性子操作通过 `PlanApprovalService.submitPlan` 触发计划审批

### 17.3 大脑边界校验验收
- [ ] `BrainReActEngine.executeToolCalls` 调用工具前执行 `BrainBoundaryEnforcer.checkAction`
- [ ] `forbiddenActions` 列表中的工具调用被拒绝并返回错误
- [ ] `mustEscalateScenarios` 触发时升级到 MainBrain
- [ ] 边界检查命中记录到 `logs/brain-boundary-audit.log`
- [ ] `GET /api/brain-boundary/config` 返回各 Brain 配置

### 17.4 InterventionDecisionEngine 精简验收
- [ ] `InterventionDecisionType` 枚举不再包含 `ASYNC_APPROVAL`
- [ ] `REALTIME_CONFIRM` 决策触发 `approvalService.createApproval`
- [ ] `MANDATORY_HUMAN` 决策触发 `approvalService.createApproval`
- [ ] `AUTO_EXECUTE` 决策由大脑边界检查通过后自动执行

### 17.5 业务审批与计划审批保留验收
- [ ] `ApprovalService.createApproval` 行为不变
- [ ] `ApprovalController` HTTP API 不变
- [ ] `PlanApprovalService.submitPlan` 接口签名不变
- [ ] 员工回执 `NEEDS_APPROVAL` 状态仍触发业务审批实例创建
- [ ] CODE_CHANGE / DEPLOYMENT_PLAN 仍走 `PlanApprovalService`

### 17.6 配置与持久化验收
- [ ] `application.yml` 包含 `brain.boundary.*` 配置块
- [ ] `application.yml` 不再包含 `approval.mode` / `approval.tools.*` 工具级开关
- [ ] `ApprovalServiceImpl` 的审批实例持久化到 DB（参见 T-1-8）
- [ ] 重启服务后审批中实例仍可查询到
- [ ] `BrainBoundaryEnforcer` 配置修改后无需重启即可生效

### 17.7 现有断链修复验收
- [ ] `SimpleApprovalManager.requestApproval` 不再硬编码返回 YES（已删除）
- [ ] `ToolExecutorService.requiresApproval` 不再仅记录日志（已删除）
- [ ] `InterventionDecisionEngine.ASYNC_APPROVAL` 死代码已清除
- [ ] 三个高风险工具内部审批引用已迁移
- [ ] 审批回调从 Controller 移到 Service（参见 T-1-9）

---

# 第四部分：前端 ↔ 后端对接闭环审计

> **范围**：`f:\SoarCloudAI\docker\living-agent-service\frontend`（React + Vite + TypeScript + Zustand）
>
> **审计目标**：对照后端功能实现，梳理前端每个页面的 API 闭环，识别路径违规、绕过统一封装、重复实现、缺失功能等问题。
>
> **生成时间**：2026-06-29

---

## 18. 前端架构与对接总览

### 18.1 三层基础设施

| 层 | 文件 | 职责 |
|---|------|------|
| API 基础封装 | [apiBase.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/apiBase.ts) | `request<T>(url, options)` 统一封装：token 注入、`credentials: 'include'`、401 跳登录、`{success, data}` 拆包、FastAPI 错误中文化 |
| API 模块层 | [api.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts) | 50+ API 模块（auth/agent/task/approval/...），约 350+ 方法 |
| WebSocket URL 构造器 | `wsApi`（api.ts:671-710） | 7 个端点：chatUrl/neuronUrl/brainUrl/agentUrl/deptUrl/chairmanUrl/publicUrl |

### 18.2 状态管理

| Store | 文件 | 状态 |
|------|------|------|
| `useAuthStore` | [stores/index.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/stores/index.ts) | user/token/lastActivity/currentDepartment；token 双写 `localStorage` + 内存 |
| `useAppStore` | 同上 | sidebarCollapsed/selectedAgentId/currentDepartmentCode |
| `useToastStore` | [toastStore.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/stores/toastStore.ts) | 全局通知，3.5 秒自动消失 |

### 18.3 鉴权链路

```
[浏览器] localStorage.token
  → useAuthStore.setAuth(token)
  → apiBase.request 自动注入 Authorization: Bearer ${token}
  → credentials: 'include' 携带 HttpOnly Cookie
  → 401 时清空 localStorage 跳 /login（auth 端点除外）
```

### 18.4 API 模块清单（按业务域分组）

| 业务域 | API 模块 | 关键方法 |
|-------|---------|---------|
| 认证 | authApi | sendSmsCode/phoneLogin/bindPhone/me/updateMe |
| 系统 | systemApi、systemExtendedApi | status/register/config/providers、health/startSession |
| 租户 | tenantApi、tenantExtendedApi | selfCreate/join/registrationConfig |
| 管理 | adminApi | listCompanies/createCompany/toggleCompany |
| Agent | agentApi | list/get/create/update/delete/start/stop/metrics/collaborators |
| 任务 | taskApi（部分 @deprecated）、globalTaskApi | list/create/getMyTasks/getMyExecutions、list/checkout/complete/release |
| 文件 | fileApi | list/read/write/delete/upload/importSkill |
| 频道 | channelApi | get/create/update/delete/webhookUrl |
| 企业 | enterpriseApi、enterpriseSettingsApi | llmModels/templates/kbFiles、getByCategory/updateSetting |
| 数字员工 | fixedEmployeeApi | getSummary/getAllDefinitions/getDefinitionsByDepartment |
| 活动 | activityApi | list |
| 消息 | messageApi | inbox/unreadCount/markRead |
| 调度 | scheduleApi | list/create/update/delete/trigger |
| 技能 | skillApi | list/get/create/update/delete/browse/clawhub |
| 部门 | departmentApi | list/get/getByCode/getBrain/listConversations |
| 项目 | projectApi、projectActionApi | list/create/delete/getTasks、start/complete/hold/getProgress |
| 审批 | approvalApi | list/getMyPending/getMyApprovals/create/approve/reject |
| 神经元 | neuronApi | list/get/getStatus/getMetrics |
| 知识 | knowledgeApi、knowledgeExtendedApi | list/get/create、search/getCategories/getStats |
| 工具 | toolApi | list/getByDepartment |
| 干预 | interventionApi、interventionExtendedApi | list/get/create、respond/escalate/listRules |
| 主动 | proactiveApi、proactiveExtendedApi | getPredictions/trigger、getDigest/listHabits |
| 声纹 | voicePrintApi、voicePrintExtendedApi | list/register/verify、login |
| 办公 | officeApi、officeExtendedApi | list/create、getStatus/listAgents |
| 接待 | receptionApi、receptionExtendedApi | getVisitors/checkIn、chat/chatStream |
| 演化 | evolutionApi、evolutionExtendedApi | getStatus/trigger、getResults/extractSignals/listSkills |
| 董事长 | chairmanApi | getDashboard/getEmployees/getDepartments |
| LLM 提供商 | llmProviderApi | list/listModels/createModel/testModel |
| 企业技能 | enterpriseSkillApi | list/getByBrain/getCounts |
| 广场 | plazaApi | listPosts/createPost/getStats/likePost |
| 积分 | creditApi | getBalance/getEmployeeBalance/getHistory/getLeaderboard |
| 模型池 | modelPoolApi（独立文件） | providers.list/manifest、models.list、assignments.assign |
| 大脑模型 | brainModelApi（独立文件） | list/assign/clear/available |
| 仪表盘 | dashboardApi（独立文件） | getEnterpriseSummary/getDepartmentHealth/getRiskAlerts |

---

## 19. 前端功能闭环清单

按"前端页面 → API → 后端 → WS 推送 → 前端 UI"梳理 15 个核心闭环。

### 闭环 F1：登录认证

```
[Login.tsx] 手机号 + 验证码
  → systemApi.status（检查系统状态）
  → authApi.sendSmsCode → POST /api/auth/sms/send
  → authApi.phoneLogin → POST /api/auth/phone/login
  ← 返回 {token, employee}
  → useAuthStore.setAuth(token)
  → 跳转 /dashboard
[Layout.tsx] → authApi.me → GET /api/auth/me
```

**关联文件**：[Login.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Login.tsx)、[ForgotPassword.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/ForgotPassword.tsx)、[ResetPassword.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/ResetPassword.tsx)、[SSOEntry.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/SSOEntry.tsx)

**已知问题**
- 🟠 [SSOEntry.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/SSOEntry.tsx) 使用 `fetchJson('/sso/session/{sid}/scan')` 绕过 `apiBase`，token 注入与 401 处理不一致
- 🟡 后端 `PhoneAuthController.smsSend` 把验证码明文返回（见第一部分闭环 1），前端未做客户端校验

### 闭环 F2：仪表盘

```
[Dashboard.tsx] → agentApi.list + taskApi.list + activityApi.list
[EnterpriseDashboard.tsx] → dashboardApi.getEnterpriseSummary
[PlatformDashboard.tsx] → 直接 fetch /api/admin/metrics/timeseries、/api/admin/metrics/leaderboards
```

**已知问题**
- 🔴 [PlatformDashboard.tsx:32,49](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/PlatformDashboard.tsx#L32) 直接 `fetch` 绕过 `apiBase`，无 token 注入与 401 处理
- 🟠 [Dashboard.tsx:464](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Dashboard.tsx#L464) 仍调用 `taskApi.list`（已标记 @deprecated），应迁移到 `globalTaskApi.list`
- 🟡 三套仪表盘（Dashboard / EnterpriseDashboard / PlatformDashboard）使用不同的数据获取方式，未统一

### 闭环 F3：Agent 管理

```
[AgentCreate.tsx] → modelPoolApi.models.list + enterpriseApi.templates + skillApi.list + agentApi.create + channelApi.create
[AgentDetail.tsx] → agentApi.* + activityApi.list + skillApi.* + fileApi.*
[AgentDetail/AgentChat.tsx] → 直接 fetch sessions/messages + WebSocket /ws/agent
[AgentDetail/AgentApprovals.tsx] → fetchAuth + 直接 fetch /api/agents/{id}/approvals/{aid}/resolve
[AgentDetail/AgentTools.tsx] → 直接 fetch + localStorage.getItem('token') 手动拼 header
[AgentDetail/AgentTriggers.tsx] → 直接 fetch
```

**已知问题**
- 🔴 [AgentDetail/AgentTools.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/AgentDetail/AgentTools.tsx) 9 处直接 `fetch` + 手动 `localStorage.getItem('token')` 拼 header，绕过 `apiBase`
- 🔴 [AgentDetail/AgentApprovals.tsx:25](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/AgentDetail/AgentApprovals.tsx#L25) `fetchAuth` + 直接 `fetch` 重复实现
- 🟠 [AgentDetail/AgentChat.tsx:125,142,187,203,213](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/AgentDetail/AgentChat.tsx#L125) 5 处直接 `fetch` 绕过 `apiBase`
- 🟠 [AgentDetail.tsx:94](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/AgentDetail.tsx#L94) 直接 `fetch` DELETE

### 闭环 F4：聊天（Chat）

```
[Chat.tsx]
  → authApi.me + agentApi.get + enterpriseApi.llmModels
  → 直接 fetch /api/chat/{id}/history、/api/chat/upload
  → WebSocket /ws/agent、/ws/public、/ws/dept、/ws/enterprise
```

**已知问题**
- 🔴 [Chat.tsx:171,387](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Chat.tsx#L171) 直接 `fetch` 绕过 `apiBase`
- 🟠 [Chat.tsx:194-219](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Chat.tsx#L194) WebSocket URL 手动拼接，未使用 `wsApi.agentUrl` 等
- 🟡 Chat.tsx 同时使用 4 个 WebSocket 端点，连接管理复杂，无统一心跳/重连

### 闭环 F5：部门详情与聊天

```
[DepartmentDetail/DepartmentDetail.tsx] → fixedEmployeeApi + agentApi.list
[DepartmentChatInline.tsx]
  → departmentApi.listConversations/getConversationHistory/deleteConversation
  → WebSocket /ws/dept/{code}（手动拼接）
[DepartmentOfficeData.ts] → departmentApi.getByCode/getBrain/getAgents/getMembers + officeExtendedApi.getDepartmentStatus
```

**已知问题**
- 🟠 [DepartmentChatInline.tsx:198](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx#L198) WebSocket URL 手动拼接，未使用 `wsApi.deptUrl`
- 🟡 [STATUS_REFACTOR_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/STATUS_REFACTOR_PLAN.md) 标识状态机有重构计划但未完成

### 闭环 F6：项目管理

```
[Projects.tsx] → projectApi.list/create/delete/getTasks + departmentApi.list
```

**已知问题**
- 🔴 [api.ts:593](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts#L593) `projectApi.create` 路径 `/projects/` 带末尾斜杠，违反项目规则
- 🟠 `projectActionApi.getStatistics` 已定义但无页面调用，项目统计闭环未形成
- 🟡 `projectApi`（CRUD）与 `projectActionApi`（动作）分散在两个对象，调用方需切换

### 闭环 F7：审批工作流

```
[Approvals.tsx] → approvalApi.list/getMyPending/create/approve/reject/getSteps
[EnterpriseSettings.tsx 审批段] → 嵌入式审批配置
[AgentDetail/AgentApprovals.tsx] → fetchAuth + 直接 fetch /api/agents/{id}/approvals/{aid}/resolve
```

**已知问题**
- 🔴 [api.ts:634](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts#L634) `approvalApi.create` 路径 `/approvals/` 带末尾斜杠
- 🔴 [AgentDetail/AgentApprovals.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/AgentDetail/AgentApprovals.tsx) `fetchAuth` 重复实现 + 直接 `fetch` 调用 resolve 端点
- 🟠 与第三部分工具审批移除方案有联动：`AgentDetail/AgentApprovals.tsx` 显示的是 Agent 维度审批，需确认是否仍需要

### 闭环 F8：消息中心

```
[Messages.tsx] → messageApi.inbox/markRead/markAllRead
[Layout.tsx] → fetchJson('/messages/unread-count') + 直接 fetch /api/auth/me、/api/version、/api/messages/read-all
```

**已知问题**
- 🟠 [Layout.tsx:93,113,162,204,210](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/Layout.tsx#L93) 5 处直接 `fetch` 绕过 `apiBase`
- 🟡 `fetchJson` 在多个文件中重复定义（Layout / InvitationCodes / UserManagement），应统一到 apiBase

### 闭环 F9：我的任务

```
[MyTasks.tsx] → taskApi.getMyTasks/submitTask + creditApi.getBalance
```

**已知问题**
- 🟠 `taskApi.getMyTasks` 已标记 @deprecated，应迁移到 `globalTaskApi.getMyTasks`
- 🟡 积分（credit）显示嵌入 MyTasks，无独立页面

### 闭环 F10：大脑配置

```
[BrainConfig.tsx] → brainModelApi.list/available/assign/clear + modelPoolApi.providers.list
```

**已知问题**
- 🟡 与第二部分 B-1-x 大脑配置改进联动：后端 `BrainConfig.java` 清理 ApprovalManager bean 后，前端无需变更（接口签名不变）

### 闭环 F11：模型池

```
[ModelPoolProviders.tsx] → modelPoolApi.providers/models.*
```

**已知问题**
- 🔴 [ModelPoolProviders.tsx:8-21](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx#L8) 14 个 provider baseUrl 硬编码（anthropic/openai/deepseek/qwen 等），与后端 `modelPoolApi.providers.manifest()` 重复
- 🟠 manifest API 已定义但页面未使用，导致新增 provider 需改前端代码

### 闭环 F12：知识库

```
[EnterpriseSettings.tsx 知识库段] → knowledgeApi + knowledgeExtendedApi
```

**已知问题**
- 🟠 知识库无独立页面，嵌入 EnterpriseSettings.tsx:972-1201，与其他配置混杂
- 🟡 `knowledgeExtendedApi.search/getCategories` 已定义但使用率低

### 闭环 F13：演化模块

```
[EnterpriseSettings.tsx 演化段] → evolutionExtendedApi（部分方法）
```

**已知问题**
- 🟠 演化模块无独立监控页面，嵌入 EnterpriseSettings
- 🟡 `evolutionExtendedApi.extractSignals/generateSkill/installSkill` 等关键方法未使用，演化闭环不完整

### 闭环 F14：经济自治（缺失）

```
[后端] core/autonomous（赏金/激励/支付）
[前端] creditApi.getBalance/getHistory/getLeaderboard（仅查询）
```

**已知问题**
- 🔴 经济自治管理 UI 完全缺失：无赏金发布、激励规则配置、支付审批页面
- 🔴 `creditApi` 仅查询，无创建/更新/审批接口
- 🟠 与第一部分闭环 11（绩效积分）联动：桌面端有积分显示，但前端无管理入口

### 闭环 F15：管理后台

```
[UserManagement.tsx] → chairmanApi.updateEmployeeAccess + fetchJson('/enterprise/employees') + /users/{id}/quota + /org/users + /enterprise/employees/{id}/activate
[AdminCompanies.tsx] → adminApi.* + 直接 fetch /api/enterprise/system-settings/notification_bar
[InvitationCodes.tsx] → 直接 fetch /api/enterprise/invitation-codes*
[EnterpriseSettings.tsx] → 极多 API（creditApi/knowledgeApi/approvalApi/enterpriseApi.kb*/evolutionExtendedApi/skillApi/enterpriseSkillApi）+ fetchJson 多处
[OpenClawSettings.tsx] → 自定义 fetchAuth /agents/{id}/api-key、/agents/{id}/permissions
```

**已知问题**
- 🔴 [EnterpriseSettings.tsx:2709,2907,3132,3140,3153,3799,3917](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/EnterpriseSettings.tsx#L2709) 7 处直接 `fetch` 绕过 `apiBase`
- 🔴 [InvitationCodes.tsx:31,48,60,67](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/InvitationCodes.tsx#L31) 4 处直接 `fetch`
- 🔴 [OpenClawSettings.tsx:9](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/OpenClawSettings.tsx#L9) `fetchAuth` 重复实现
- 🟠 EnterpriseSettings.tsx 单文件包含 8+ 业务域配置（积分/知识/审批/演化/技能/Windows 自动化/通知/Logo），违反单一职责

---

## 20. 前端问题清单

### 20.1 P0：阻断性问题

| 编号 | 问题 | 影响范围 | 文件 |
|------|------|---------|------|
| F-P0-1 | API 路径带末尾斜杠（3 处） | 违反项目规则，可能被后端拦截 | [api.ts:593](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts#L593) `/projects/`、[api.ts:634](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts#L634) `/approvals/`、[api.ts:658](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts#L658) `/neurons/` |
| F-P0-2 | 48 处直接 `fetch` 绕过 `apiBase` | token/401/拆包逻辑失效 | 见第 19 节各闭环 |
| F-P0-3 | 6 处 `fetchAuth` 重复实现 | 维护成本高，行为不一致 | `components/ChannelConfig.tsx:11`、`pages/AgentDetail/utils.ts:16`、`pages/AgentDetail/AgentSettings.tsx:18`、`pages/AgentDetail/AgentRelations.tsx:6`、`pages/OpenClawSettings.tsx:9`、`pages/AgentDetail/AgentApprovals.tsx:5` |
| F-P0-4 | ModelPoolProviders 硬编码 14 个 baseUrl | 新增 provider 需改前端代码 | [ModelPoolProviders.tsx:8-21](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx#L8) |
| F-P0-5 | 经济自治管理 UI 完全缺失 | 后端 `core/autonomous` 无前端闭环 | 无对应页面 |

### 20.2 P1：闭环不完整

| 编号 | 问题 | 影响范围 |
|------|------|---------|
| F-P1-1 | ~~`taskApi.list` 等 @deprecated 方法仍在用~~ ✅ 已修复（阶段2） | 已迁移 Dashboard.tsx 调用方到 globalTaskApi，删除 5 个 @deprecated 方法 |
| ~~F-P1-2~~ ✅ `projectActionApi.getStatistics` 已定义未使用 | 项目统计已接入 Projects.tsx 横幅（活跃数+已完成数+总数） |
| ~~F-P1-3~~ ✅ `neuronApi.*` 全部已定义未使用 | Neuron 管理页面已创建（`pages/Neurons.tsx`，路由 `/neurons`） |
| ~~F-P1-4~~ ✅ `interventionApi` / `interventionExtendedApi` 未使用 | 干预系统页面已创建（`pages/Interventions.tsx`，路由 `/interventions`） |
| ~~F-P1-5~~ ✅ `proactiveApi` / `proactiveExtendedApi` 未使用 | 主动服务页面已创建（`pages/Proactive.tsx`，路由 `/proactive`） |
| ~~F-P1-6~~ ✅ `receptionApi` / `receptionExtendedApi` 未使用 | 接待/前台页面已创建（`pages/Reception.tsx`，路由 `/reception`） |
| ~~F-P1-7~~ ✅ `voicePrintExtendedApi.login` 未使用 | 声纹登录页面已创建（`pages/VoicePrintLogin.tsx`，路由 `/voiceprint`） |
| ~~F-P1-8~~ ✅ `officeApi.list/create` 未使用 | 办公室管理页面已创建（`pages/Office.tsx`，路由 `/office`） |
| ~~F-P1-9 WebSocket URL 手动拼接（3 处）~~ ✅ 已修复（阶段4） | Chat.tsx/AgentChat.tsx/DepartmentChatInline.tsx 已改用 wsApi 工具函数 |
| F-P1-10 | ~~`fetchJson` 在 Layout / InvitationCodes / UserManagement 重复定义~~ ✅ 已修复（阶段1） | 均已改为从 `api.ts` 导入共享 `fetchJson` |
| ~~F-P1-11~~ | ~~EnterpriseSettings.tsx 单文件 8+ 业务域~~ ✅ 已修复（迭代6） | 已拆分为 `pages/EnterpriseSettings/` 子目录（7个文件）：OrgTab/KnowledgeTab/AuditLogTab/ApprovalsTab/SkillsTab/InfoTabComponents/index |
| F-P1-12 | ~~`dashboardApi.ts` 独立 request 实现~~ ✅ 已修复（阶段2） | 已改为从 apiBase.ts 导入共享 request 函数 |

### 20.3 P2：优化项

| 编号 | 问题 | 处理 |
|------|------|------|
| F-P2-1 | `projectApi` 与 `projectActionApi` 分散 | 合并为单一 projectApi 对象 |
| F-P2-2 | `taskApi`（@deprecated）与 `globalTaskApi` 并存 | 删除 @deprecated 方法，统一到 globalTaskApi |
| F-P2-3 | 知识库无独立页面 | 抽取到 `pages/Knowledge.tsx` |
| F-P2-4 | 演化模块无独立监控页面 | 抽取到 `pages/Evolution.tsx` |
| F-P2-5 | 积分无独立页面 | 抽取到 `pages/Credits.tsx` |
| F-P2-6 | Dashboard / EnterpriseDashboard / PlatformDashboard 数据获取不统一 | 统一使用 dashboardApi |
| F-P2-7 | 类型定义散乱 | 部分类型在 `types/index.ts`，部分在页面内联，应集中 |

---

## 21. 前端修复任务与优先级

### 21.1 P0：基础设施修复

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~F-0-1~~ ✅ 修复 API 路径末尾斜杠 | [api.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/api.ts) | 已修复（阶段1） |
| ~~F-0-2~~ ✅ 统一 fetch 到 apiBase | 48 处直接 `fetch` | 已统一（阶段1） |
| ~~F-0-3~~ ✅ 合并 6 处 fetchAuth | `ChannelConfig.tsx` 等 6 处 | 已合并（阶段1） |
| ~~F-0-4~~ ✅ ModelPoolProviders 改用 manifest | [ModelPoolProviders.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/ModelPoolProviders.tsx) | 已改用 manifest（阶段1） |
| ~~F-0-5~~ ✅ 新建经济自治管理页面 | `pages/Autonomous.tsx` + `services/autonomousApi.ts` + `AutonomousController.java` | 后端暴露 11 个 API（bounty/payout/ledger/evolution/overview）；前端 5 个 Tab（总览/赏金猎取/收款管理/账本/进化追踪）；路由 `/autonomous`；侧边栏导航已添加 |

### 21.2 P1：闭环补全

| 任务 | 文件 | 修复方案 |
|------|------|---------|
| ~~F-1-1~~ ✅ 迁移 @deprecated taskApi 调用 | `Dashboard.tsx` | 已清理未使用的 taskApi 导入，统一使用 `globalTaskApi` |
| ~~F-1-2~~ ✅ 接入项目统计 | `Projects.tsx` | 已接入 `projectActionApi.getStatistics`，横幅显示活跃数+已完成数+总数 |
| ~~F-1-3~~ ✅ 新建 Neuron 管理页面 | `pages/Neurons.tsx` | 已创建，调用 `neuronApi.list/getStatus/getMetrics`，路由 `/neurons` |
| ~~F-1-4~~ ✅ 新建干预系统页面 | `pages/Interventions.tsx` | 已创建，调用 `interventionApi`、`interventionExtendedApi`，路由 `/interventions` |
| ~~F-1-5~~ ✅ 新建主动服务页面 | `pages/Proactive.tsx` | 已创建，调用 `proactiveApi`、`proactiveExtendedApi`，路由 `/proactive` |
| ~~F-1-6~~ ✅ 新建接待/前台页面 | `pages/Reception.tsx` | 已创建，调用 `receptionApi`、`receptionExtendedApi`，路由 `/reception` |
| ~~F-1-7~~ ✅ 新建声纹登录页面 | `pages/VoicePrintLogin.tsx` | 已创建，调用 `voicePrintExtendedApi.login`，路由 `/voiceprint` |
| ~~F-1-8~~ ✅ WebSocket URL 统一使用 wsApi | `Chat.tsx` 等 | 已改用 `wsApi.agentUrl()`、`wsApi.deptUrl(code)` 等 |
| ~~F-1-9~~ ✅ 统一 fetchJson 到 apiBase | `Layout.tsx`、`InvitationCodes.tsx`、`UserManagement.tsx` | 已从 apiBase 导入共享 `fetchJson` |
| ~~F-1-10~~ ✅ 拆分 EnterpriseSettings.tsx | 拆分为 `pages/EnterpriseSettings/` 子目录（OrgTab/KnowledgeTab/AuditLogTab/ApprovalsTab/SkillsTab/InfoTabComponents/index.tsx） | 已完成 |
| ~~F-1-11~~ ✅ 合并 dashboardApi 到 apiBase | [dashboardApi.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/services/dashboardApi.ts) | 已使用 apiBase 的 `request`，无独立实现 |
| ~~F-1-12~~ ✅ 新建办公室管理页面 | `pages/Office.tsx` | 已创建，调用 `officeApi.list/create`、`officeExtendedApi.getStatus/listAgents`，路由 `/office` |

### 21.3 P2：优化与重构

| 任务 | 修复方案 |
|------|---------|
| F-2-1 合并 projectApi 与 projectActionApi | 统一为 `projectApi.list/create/.../start/complete/getStatistics` |
| F-2-2 删除 @deprecated taskApi 方法 | 迁移完成后删除 `taskApi.list/create/update/getLogs/trigger` 等已弃用方法 |
| F-2-3 抽取知识库独立页面 | 新建 `pages/Knowledge.tsx`，从 EnterpriseSettings 迁移 |
| F-2-4 抽取演化监控独立页面 | 新建 `pages/Evolution.tsx`，调用 `evolutionExtendedApi.extractSignals/generateSkill/installSkill` 等 |
| F-2-5 抽取积分独立页面 | 新建 `pages/Credits.tsx`，整合 `creditApi` 全部方法 |
| F-2-6 统一三套仪表盘数据获取 | Dashboard / EnterpriseDashboard / PlatformDashboard 统一使用 `dashboardApi` |
| F-2-7 集中类型定义 | 所有内联类型迁移到 `types/index.ts` 或按模块拆分 `types/{module}.ts` |

---

## 22. 前端验收标准

### 22.1 路径规范验收
- [ ] 全项目搜索 `/projects/`、`/approvals/`、`/neurons/` 等末尾斜杠路径无残留
- [ ] 新增 API 路径全部不带末尾斜杠

### 22.2 API 统一封装验收
- [ ] 全项目搜索 `fetch(` 仅出现在 `apiBase.ts` 内部
- [ ] 全项目搜索 `fetchAuth` 无重复定义
- [ ] 全项目搜索 `fetchJson` 仅在 apiBase 导出
- [ ] 401 自动跳登录、token 自动注入、`{success, data}` 拆包在所有请求生效

### 22.3 WebSocket URL 验收
- [ ] `Chat.tsx` 使用 `wsApi.agentUrl` / `wsApi.publicUrl` / `wsApi.deptUrl` / `wsApi.enterpriseUrl`
- [ ] `AgentChat.tsx` 使用 `wsApi.agentUrl`
- [ ] `DepartmentChatInline.tsx` 使用 `wsApi.deptUrl`

### 22.4 缺失功能补全验收
- [ ] `pages/Autonomous.tsx` 经济自治管理页面就绪
- [ ] `pages/Neurons.tsx` 神经元管理页面就绪
- [ ] `pages/Interventions.tsx` 干预系统页面就绪
- [ ] `pages/Proactive.tsx` 主动服务页面就绪
- [ ] `pages/Reception.tsx` 接待/前台页面就绪
- [ ] `pages/VoicePrintLogin.tsx` 声纹登录页面就绪
- [ ] `pages/Office.tsx` 办公室管理页面就绪
- [ ] 项目统计图表在 `Projects.tsx` 渲染

### 22.5 重构验收
- [ ] `EnterpriseSettings.tsx` 拆分为 7+ 子页面
- [ ] `taskApi` @deprecated 方法已删除
- [ ] `projectApi` 与 `projectActionApi` 已合并
- [ ] `dashboardApi.ts` 独立 request 已合并到 apiBase
- [ ] `ModelPoolProviders.tsx` 启动时调用 manifest 动态加载

### 22.6 与后端闭环验收
- [ ] 后端 `core/autonomous` 端点被前端 `Autonomous.tsx` 调用
- [ ] 后端 `core/intervention` 端点被前端 `Interventions.tsx` 调用
- [ ] 后端 `core/proactive` 端点被前端 `Proactive.tsx` 调用
- [ ] 后端 `core/evolution` 端点被前端 `Evolution.tsx` 调用全部方法
- [ ] 后端 `core/office` 端点被前端 `Office.tsx` 调用

---

# 第五部分：所有改进项冲突分析与实施路线图

> **范围**：对第一部分（桌面端，41 项）、第二部分（后端，29 项）、第三部分（审批机制，22 项）、第四部分（前端，24 项）共 116 项改进任务进行依赖与冲突分析，给出整体实施路线图。
>
> **生成时间**：2026-06-29

---

## 23. 改进项全景图

### 23.1 改进项数量统计

> **2026-06-30 完成进度**：已完成 49 项，暂停 1 项，已废弃 1 项。
> - ✅ 已完成：阶段1(22) + 阶段2(16) + 阶段3(4) + 阶段4(7) = 49
>   - 阶段1：T-0-1~T-0-7(7) + F-0-1~F-0-4(4) + B-0-2/B-0-5/B-0-7(3) + B-1-1/B-1-6/B-1-8(3) + 桌面端 0-3/0-4/0-6/0-7/0-10(5) = 22
>   - 阶段2：B-0-1/B-0-3/B-0-4(3) + B-1-7/B-1-9/B-1-10/B-1-13/B-1-14(5) + T-1-6(1) + F-P1-1/F-P1-10/F-P1-12(3) + 桌面端 0-2/0-3/0-4(3) + F-P1-2/F-P1-3(1) = 16
>   - 阶段3：B-1-11(通知持久化) + B-1-12(ToolNeuron独立实现) + T-1-5(审计日志落盘) + T-1-7(边界配置可视化接口) = 4
>   - 阶段4：B-0-2(启动恢复session) + B-1-2(重命名triggerAsyncFinalResponse) + B-1-3(execution_id长度) + B-1-4(llmTaskExecutor) + B-1-5(声纹余弦比对) + T-1-5~T-1-7已在前阶段完成 + F-P1-9(wsApi统一) = 7
> - ⏸ 暂停：0-1(短信验证码，当前未挂接短信服务)
> - ❌ 已废弃：B-0-6(SimpleApprovalManager，被第三部分替代)

| 部分 | P0 | P1 | P2 | 合计 |
|------|----|----|----|------|
| 第一部分 桌面端 ↔ 后端 | 8 | 18 | 15 | 41 |
| 第二部分 后端功能闭环 | 7 | 14 | 8 | 29 |
| 第三部分 审批机制精简 | 11 | 10 | 4 | 25（重写后调整为 11+10+4） |
| 第四部分 前端 ↔ 后端 | 5 | 12 | 7 | 24 |
| **合计** | **31** | **54** | **34** | **119** |

### 23.2 改进项类别分布

| 类别 | 改进项数 | 代表任务 |
|------|---------|---------|
| 路径/规范修复 | 6 | F-P0-1（前端末尾斜杠）、第一部分闭环 1（验证码明文） |
| 持久化改造 | 9 | B-0-1~B-0-7（后端内存 Map 改 DB）、T-1-8（审批持久化）、F-P1-11（dashboardApi 合并） |
| 死代码/空壳清理 | 7 | T-0-1~T-0-7（ApprovalManager 移除）、第一部分 App.tsx 死代码 |
| 缺失功能补全 | 18 | F-0-5（经济自治 UI）、F-1-3~F-1-7（Neuron/Intervention/Proactive/Reception/VoicePrint 页面） |
| 重复实现合并 | 8 | F-0-3（fetchAuth 合并）、F-1-9（fetchJson 合并）、第一部分 ipc.ts 重复监听器 |
| 风险评估打通 | 4 | T-1-2~T-1-4（InterventionDecisionEngine 接入）、B-1-x（RiskAssessmentService） |
| WebSocket 优化 | 5 | 第一部分 ws-client.ts 心跳、F-P1-9（前端 wsApi 统一） |
| 工具审批移除 | 11 | T-0-1~T-0-11（删除 + 迁移） |
| 单一职责重构 | 3 | F-1-10（EnterpriseSettings 拆分）、F-P2-1~F-P2-2（API 对象合并） |

---

## 24. 跨部分依赖与冲突分析

### 24.1 依赖关系图

```
┌──────────────────────────────────────────────────────────────┐
│  阶段 1：基础设施修复（无依赖，可并行）                       │
│                                                              │
│  第一部分 1-1~1-4（桌面端 token/路径/IPC）                   │
│  第二部分 B-0-1~B-0-7（后端持久化）                          │
│  第三部分 T-0-1~T-0-7（工具审批闸门删除）                   │
│  第四部分 F-0-1~F-0-4（前端路径/fetch 统一）                │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  阶段 2：高风险工具迁移与 Intervention 精简                  │
│  （依赖阶段 1 的 T-0-1~T-0-7 完成）                          │
│                                                              │
│  第三部分 T-0-8~T-0-11（三个工具内部审批迁移）              │
│  第三部分 T-1-1~T-1-4（InterventionDecisionEngine 精简）     │
│  第二部分 B-1-x（RiskAssessmentService 接入）                │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  阶段 3：缺失功能补全（依赖阶段 1、2 的接口稳定性）         │
│                                                              │
│  第四部分 F-0-5（经济自治 UI）                               │
│  第四部分 F-1-3~F-1-7（Neuron/Intervention/Proactive 等）   │
│  第四部分 F-1-10（EnterpriseSettings 拆分）                 │
│  第四部分 F-1-11（dashboardApi 合并）                        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  阶段 4：优化与重构（无依赖，可最后做）                      │
│                                                              │
│  第四部分 F-2-1~F-2-7（API 合并、页面抽取）                  │
│  第三部分 T-2-1~T-2-4（审计日志、Prometheus 指标）           │
│  第二部分 B-2-x（优化扩展）                                  │
│  第一部分 5-x（P2 优化）                                     │
└──────────────────────────────────────────────────────────────┘
```

### 24.2 冲突识别与解决

> **2026-06-29 更新**：冲突 1-6 已按第三部分方案实施完成，原第二部分矛盾任务均已标记"已废弃/已被替代"。冲突 7-8 无实质冲突。
>
> **2026-06-30 交叉引用补充**：B-0-1/B-0-4/T-1-5 已补充 MODEL 文档交叉引用；B-1-12 已完成独立实现；第三部分设计目标已补充 MODEL 文档 APPROVAL_REQUIRED 枚举语义修正交叉引用；CODE_STRUCTURE 中 ApprovalManager 引用和 Flyway migration 引用已更新。

#### 冲突 1：`InterventionDecisionEngine.ASYNC_APPROVAL` 处理方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第二部分 | B-1-1 | 接入 `ApprovalDispatcher.dispatch`（原方案） |
| 第三部分 | T-1-1 | **删除** `ASYNC_APPROVAL` 决策类型 |

**冲突说明**：第二部分写作时基于原"统一开关方案"，要求 ASYNC_APPROVAL 接入 ApprovalDispatcher。第三部分重写后改为移除工具审批，ASYNC_APPROVAL 是死代码应直接删除。

**解决方案**：以第三部分为准，T-1-1 优先执行。第二部分 B-1-1 标记为"已被 T-1-1 替代"，不重复实施。

#### 冲突 2：`SimpleApprovalManager` 处理方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第二部分 | B-1-2 | 修复空壳，委托 ApprovalDispatcher |
| 第三部分 | T-0-2 | **删除** SimpleApprovalManager 整个类 |

**解决方案**：以第三部分为准，T-0-2 优先执行。第二部分 B-1-2 标记为"已被 T-0-2 替代"。

#### 冲突 3：`ToolExecutorService.requiresApproval` 处理方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第二部分 | B-1-3 | 改为真正调用 ApprovalDispatcher |
| 第三部分 | T-0-5 | **删除** 闸门日志 |

**解决方案**：以第三部分为准，T-0-5 优先执行。第二部分 B-1-3 标记为"已被 T-0-5 替代"。

#### 冲突 4：`MainBrain.executeToolCall` 处理方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第二部分 | B-1-4 | 接入 ApprovalDispatcher |
| 第三部分 | T-0-6 | 确认无 ApprovalManager 引用（保持不变） |

**解决方案**：以第三部分为准。MainBrain 当前已不引用 ApprovalManager，无需变更。第二部分 B-1-4 标记为"已被 T-0-6 替代"。

#### 冲突 5：审批模式开关方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第三部分原方案（已废弃） | A-0-1~A-0-8 | 新建 ApprovalMode/Dispatcher/BrainApproval/HumanApproval |
| 第三部分新方案 | T-0-1~T-0-7 | **删除** ApprovalManager 体系，由 BrainBoundaryEnforcer 替代 |

**解决方案**：以第三部分新方案为准，原 A-0-x 系列任务全部废弃。

#### 冲突 6：`application.yml` 配置项方向 ✅ 已解决

| 部分 | 任务 | 方向 |
|------|------|------|
| 第三部分原方案 | A-1-9 | 增加 `approval.mode` / `approval.tools.*` |
| 第三部分新方案 | T-1-6 | 增加 `brain.boundary.*`，**不增加** `approval.mode` |

**解决方案**：以第三部分新方案为准，仅增加 `brain.boundary.*` 配置块。

#### 冲突 7：前端 `AgentDetail/AgentApprovals.tsx` 处理方向

| 部分 | 任务 | 方向 |
|------|------|------|
| 第四部分 | F-0-3 | 合并 fetchAuth，统一到 apiBase |
| 第三部分 | T-0-x | 工具审批移除（仅影响后端） |

**说明**：无实质冲突。前端 `AgentApprovals.tsx` 显示的是 Agent 维度业务审批，与工具审批移除无关。F-0-3 仅做 fetchAuth 合并，保留页面。

#### 冲突 8：`ApprovalService` 持久化重复

| 部分 | 任务 | 方向 |
|------|------|------|
| 第二部分 | B-0-1 | ApprovalServiceImpl 内存 Map 改 DB |
| 第三部分 | T-1-8 | ApprovalServiceImpl 持久化改造（同上） |

**解决方案**：两者实际是同一任务，合并为单一任务 `B-0-1 / T-1-8`，由第二部分主导，第三部分引用。

### 24.3 依赖关系矩阵

| 后续任务 | 依赖前置任务 | 说明 |
|---------|------------|------|
| T-0-8~T-0-11（工具内部审批迁移） | T-0-1~T-0-7（ApprovalManager 删除） | 工具类构造函数移除 ApprovalManager 依赖前，需先确认接口已删除 |
| T-1-1~T-1-4（Intervention 精简） | T-0-1~T-0-7 | ASYNC_APPROVAL 删除前需确认 ApprovalManager 已无引用 |
| F-0-5（经济自治 UI） | 后端 `core/autonomous` 端点稳定 | 需后端确认接口签名 |
| F-1-3~F-1-7（缺失页面） | 后端对应端点稳定 | Neuron/Intervention/Proactive/Reception/VoicePrint API 已定义，需确认后端实现完整 |
| F-1-10（EnterpriseSettings 拆分） | F-0-2（fetch 统一） | 拆分前应先统一 fetch 调用，避免拆分后重复修改 |
| F-1-11（dashboardApi 合并） | F-0-2 | 同上 |
| F-2-1（projectApi 合并） | F-P1-2（项目统计接入） | 先接入统计，再合并对象 |
| F-2-2（taskApi @deprecated 删除） | F-P1-1（迁移 @deprecated 调用） | 先迁移调用方，再删除方法 |

---

## 25. 整体实施路线图

### 25.1 阶段 1：基础设施修复（P0，预计 2 周）— ✅ 大部分已完成

> **2026-06-29 进度**：T-0-1~T-0-7 ✅、F-0-1~F-0-4 ✅、B-0-2 ✅、B-0-5 ✅、B-0-7 ✅、桌面端 0-3/0-4/0-6/0-7/0-10 ✅。剩余：B-0-1 部分完成、B-0-3/B-0-4 未完成、0-2/0-5/0-8/0-9 未完成。

**目标**：消除阻断性问题，统一基础设施。

| 周次 | 任务 | 负责 | 状态 |
|------|------|------|------|
| W1 | 第一部分 桌面端 0-3/0-4/0-6/0-7/0-10 | 桌面端 | ✅ 已完成 |
| W1 | 第二部分 B-0-1~B-0-4（后端持久化） | 后端 | B-0-1 部分完成，B-0-3/B-0-4 未完成 |
| W1 | 第三部分 T-0-1~T-0-7（删除 ApprovalManager 体系） | 后端 | ✅ 已完成 |
| W1 | 第四部分 F-0-1~F-0-4（前端路径/fetch/manifest） | 前端 | ✅ 已完成 |
| W2 | 第二部分 B-0-5~B-0-7 | 后端 | B-0-5 ✅、B-0-7 ✅、B-0-6 已废弃 |

**阶段验收**：
- 全项目无 `ApprovalManager` 生产代码引用
- 前端全项目无直接 `fetch`（除 apiBase 内部）
- API 路径全部不带末尾斜杠
- 后端 9 个内存 Map 主存储全部改为 DB

### 25.2 阶段 2：工具迁移与 Intervention 精简（P0+P1，预计 1 周）

**目标**：完成工具审批移除的收尾工作，打通风险评估。

| 周次 | 任务 | 负责 |
|------|------|------|
| W3 | 第三部分 T-0-8~T-0-11（WindowsAutomationTool/WindowsAppTool/SandboxExecutorImpl 内部审批迁移） | 后端 |
| W3 | 第三部分 T-1-1~T-1-4（InterventionDecisionEngine 精简，删除 ASYNC_APPROVAL） | 后端 |
| W3 | 第二部分 B-1-x 接入 RiskAssessmentService 到 BrainBoundaryEnforcer（替代原 B-1-1~B-1-3 已被替代的任务） | 后端 |
| W3 | 第三部分 T-1-5~T-1-7（BrainBoundaryEnforcer 审计日志 + 配置项 + 可视化接口） | 后端 |

**阶段验收**：
- 三个高风险工具不再依赖 ApprovalManager
- `InterventionDecisionType` 枚举不含 ASYNC_APPROVAL
- `application.yml` 包含 `brain.boundary.*` 配置块
- 边界检查命中记录到审计日志（✅ 已落盘至 `brain_boundary_audit` 表）
- 边界配置可视化接口已实现（✅ `GET /api/brain-boundary/config`）
- ToolNeuron Layer 3 独立实现已完成（✅ `neuron://tool/qwen35/001`）
- 通知持久化已完成（✅ DB 双写 + 启动加载）

### 25.3 阶段 3：缺失功能补全（P0+P1，预计 3 周）

**目标**：补齐前端缺失页面，打通与后端的全闭环。

| 周次 | 任务 | 负责 |
|------|------|------|
| W4-W5 | 第四部分 F-0-5（经济自治管理页面） | 前端 + 后端协同 |
| W4-W5 | 第四部分 F-1-3~F-1-7（Neuron/Intervention/Proactive/Reception/VoicePrint 页面） | 前端 |
| W6 | 第四部分 F-1-8~F-1-9（WebSocket URL 统一 / fetchJson 统一） | 前端 |
| W6 | 第四部分 F-1-10（EnterpriseSettings 拆分为 7+ 子页面） | 前端 |
| W6 | 第四部分 F-1-11~F-1-12（dashboardApi 合并 / Office 页面） | 前端 |
| W6 | 第四部分 F-1-1~F-1-2（@deprecated 迁移 / 项目统计接入） | 前端 |
| W6 | 第二部分 B-1-x 业务审批/计划审批独立改进（T-1-8~T-1-10） | 后端 |

**阶段验收**：
- 7 个缺失页面就绪（Autonomous/Neurons/Interventions/Proactive/Reception/VoicePrintLogin/Office）
- EnterpriseSettings 拆分为 7+ 子页面
- 后端 `core/autonomous`、`core/intervention`、`core/proactive` 端点被前端调用
- 项目统计图表在 Projects.tsx 渲染

### 25.4 阶段 4：优化与重构（P2，预计 2 周）

**目标**：清理冗余，统一对象，集中类型。

| 周次 | 任务 | 负责 |
|------|------|------|
| W7 | 第四部分 F-2-1~F-2-2（projectApi 合并 / taskApi @deprecated 删除） | 前端 |
| W7 | 第四部分 F-2-3~F-2-5（知识库/演化/积分独立页面抽取） | 前端 |
| W7 | 第四部分 F-2-6~F-2-7（三套仪表盘统一 / 类型集中） | 前端 |
| W8 | 第三部分 T-2-1~T-2-4（工具调用审计日志 / 查询接口 / 命中统计 / Prometheus 指标） | 后端 |
| W8 | 第二部分 B-2-x（后端优化扩展） | 后端 |
| W8 | 第一部分 P2 优化项（桌面端） | 桌面端 |

**阶段验收**：
- `taskApi` @deprecated 方法全部删除
- `projectApi` 与 `projectActionApi` 合并完成
- 工具调用审计日志可查询
- Prometheus 指标暴露

### 25.5 关键里程碑

| 里程碑 | 时间 | 验收标准 |
|--------|------|---------|
| M1 基础设施修复完成 | W2 末 | 阶段 1 验收通过 |
| M2 工具审批移除完成 | W3 末 | 阶段 2 验收通过 |
| M3 缺失功能补全完成 | W6 末 | 阶段 3 验收通过 |
| M4 全部改进项完成 | W8 末 | 阶段 4 验收通过，全项目 119 项任务关闭 |

### 25.6 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 删除 ApprovalManager 后工具行为变化 | 工具调用可能绕过原有（已失效的）审批 | 实施前全项目 grep 确认引用；阶段 2 增加回归测试 |
| 经济自治 UI 缺失依赖后端接口稳定 | 阶段 3 阻塞 | W3 同步推进后端 `core/autonomous` 接口确认 |
| EnterpriseSettings 拆分影响在线配置 | 现有配置入口失效 | 拆分时保留路由兼容，渐进迁移 |
| 持久化改造期间数据丢失 | 内存 Map 数据无法迁移到 DB | 改造前导出关键数据；改造后双写一段时间 |
| 前端 fetch 统一可能引入回归 | 48 处修改风险高 | 分批改造，每批配套测试 |

---

## 附录 B：参考文档

- [CODE_STRUCTURE_AND_FILE_GUIDE.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/CODE_STRUCTURE_AND_FILE_GUIDE.md)：代码结构与文件功能总览
- [项目规则 `.trae/rules/project_rules.md`](file:///f:/SoarCloudAI/.trae/rules/project_rules.md)：核心约束清单
- [FLOW_IMPROVEMENT_REPORT.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/FLOW_IMPROVEMENT_REPORT.md)：流程改进报告
- [WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/pending/WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md)：WebSocket 后端改进计划
- [WEBSOCKET_RECONNECT_SOLUTION.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/pending/WEBSOCKET_RECONNECT_SOLUTION.md)：WebSocket 重连方案
- [WINDOWS_MCP_INTEGRATION_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/WINDOWS_MCP_INTEGRATION_PLAN.md)：Windows MCP 集成计划
- [MAINBRAIN_EXECUTION_RULES.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/core/MAINBRAIN_EXECUTION_RULES.md)：主脑执行规则
- [对话入口逻辑梳理.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/对话入口逻辑梳理.md)：对话入口链路
- [权限与入口矩阵.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)：权限矩阵
