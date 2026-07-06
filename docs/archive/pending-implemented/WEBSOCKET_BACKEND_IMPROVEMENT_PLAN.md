# Living Agent 后端 WebSocket 改进方案

> 来源：[websocket-architecture-review.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/websocket-architecture-review.md) 核查确认
> 核查日期：2026-06-15
> 涉及模块：`living-agent-gateway`（WebSocket Handler、Config）、`living-agent-core`（AgentService）

---

## 1. 核查结论总览

| 严重级别 | 数量 | 已修复 | 待修复 |
|----------|------|--------|--------|
| CRITICAL | 1 | 1 | 0 |
| MODERATE | 5 | 5 | 0 |
| MINOR | 3 | 3 | 0 |
| **自动化闭环** | **6** | **6** | **0** |
| **合计** | **15** | **15** | **0** |

> 注：前端/桌面端的 8 个问题已更新到 [LIVING_AGENT_DESKTOP_PLAN.md §14](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/LIVING_AGENT_DESKTOP_PLAN.md)。本文档仅覆盖后端问题。

---

## 2. CRITICAL（必须修复）

### #4 AgentWebSocketHandler 缺少僵尸连接检测

**问题**：`AgentWebSocketHandler` 没有 `sessionLastActive` 追踪，没有心跳超时检测，没有僵尸连接清理机制。如果客户端异常断开（网络中断但 TCP 连接未正式关闭），服务端可能长时间保留无效会话。

**对比**：`DepartmentWebSocketHandler` 有完整的僵尸连接检测：
- `sessionLastActive` ConcurrentHashMap 追踪每个会话最后活跃时间
- `HEARTBEAT_INTERVAL_MS = 30_000`（30秒扫描）
- `HEARTBEAT_TIMEOUT_MS = 60_000`（60秒超时关闭）
- `startHeartbeat()` 定时任务清理僵尸连接

**代码位置**：[AgentWebSocketHandler.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java)

**修复方案**：

```java
// 1. 新增字段
private final Map<String, Instant> sessionLastActive = new ConcurrentHashMap<>();
private static final long HEARTBEAT_TIMEOUT_MS = 60_000;
private static final long HEARTBEAT_INTERVAL_MS = 30_000;
private ScheduledExecutorService heartbeatScheduler;

// 2. @PostConstruct 中启动心跳检测
@PostConstruct
public void init() {
    heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-agent-heartbeat");
        t.setDaemon(true);
        return t;
    });
    heartbeatScheduler.scheduleAtFixedRate(this::checkZombieConnections,
        HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
}

// 3. 僵尸连接检测方法
private void checkZombieConnections() {
    Instant now = Instant.now();
    sessionLastActive.forEach((sessionId, lastActive) -> {
        if (now.toEpochMilli() - lastActive.toEpochMilli() > HEARTBEAT_TIMEOUT_MS) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                log.warn("[AgentWS] Zombie connection detected, closing: sessionId={}", sessionId);
                try { session.close(CloseStatus.SERVER_ERROR); } catch (Exception e) { /* ignore */ }
            }
            sessionLastActive.remove(sessionId);
        }
    });
}

// 4. handleTextMessage 中更新 lastActive
sessionLastActive.put(session.getId(), Instant.now());

// 5. afterConnectionEstablished 中初始化
sessionLastActive.put(session.getId(), Instant.now());

// 6. afterConnectionClosed 中清理
sessionLastActive.remove(session.getId());
```

---

## 3. MODERATE（应该修复）

### #7 两个 Handler 的 pong 响应大小写不一致

**问题**：
- `AgentWebSocketHandler.sendPong()` 返回 `{"type": "pong"}`（小写）
- `DepartmentWebSocketHandler.sendPong()` 返回 `{"type": "PONG"}`（大写）

前端 `ws-client.ts` 的事件类型定义只有 `'pong'`（小写），Department 通道的 PONG 响应不会被正确匹配。同样，ping 消息的匹配也不一致（Agent 匹配 `"ping"`，Department 匹配 `"PING"` 因为 `type.toUpperCase()`）。

**代码位置**：
- [AgentWebSocketHandler.java:246-248](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java#L246)
- [DepartmentWebSocketHandler.java:594-599](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L594)

**修复方案**：统一为小写 `"pong"`，与前端 `WsEventType` 定义对齐。

```java
// DepartmentWebSocketHandler.java:596
Map<String, Object> pong = Map.of(
    "type", "pong",  // PONG → pong
    "timestamp", Instant.now().toString()
);
```

同时统一 ping 消息的匹配方式：两个 Handler 都使用 `type.equalsIgnoreCase("ping")` 而非 `type.toUpperCase().equals("PING")`。

### #8 WebSocket 端点未配置 HandshakeInterceptor

**问题**：四个 WebSocket 端点均未配置 `HandshakeInterceptor`，认证在连接建立之后才进行。无效连接会短暂消耗服务器资源。

**代码位置**：[WebSocketConfig.java:30-47](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/WebSocketConfig.java#L30)

**修复方案**：

```java
// 1. 新增 AuthHandshakeInterceptor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 Sec-WebSocket-Protocol / Authorization 头 / URL 参数提取 token
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            log.warn("[WS] Handshake rejected: no token provided");
            return false; // 握手阶段直接拒绝
        }
        // 预验证 token
        try {
            AuthContext auth = unifiedAuthService.validateSession(token);
            attributes.put("authContext", auth);
            attributes.put("token", token);
            return true;
        } catch (Exception e) {
            log.warn("[WS] Handshake rejected: invalid token");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}

// 2. WebSocketConfig 中注册
registry.addHandler(agentWebSocketHandler, "/ws/agent")
    .addInterceptor(authHandshakeInterceptor)
    .setAllowedOrigins(origins);
```

### #11 两个 Handler 均无消息频率限制

**问题**：恶意客户端可以高频发送消息，可能导致 LLM 调用过载。

**代码位置**：
- [AgentWebSocketHandler.java:175-198](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java#L175)
- [DepartmentWebSocketHandler.java:257-288](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L257)

**修复方案**：

```java
// 1. 新增 WebSocketRateLimiter
@Component
public class WebSocketRateLimiter {
    // 每会话每秒最多 5 条消息，每分钟最多 30 条
    private final Map<String, RateLimiter> perSessionLimiters = new ConcurrentHashMap<>();
    private static final double PERMITS_PER_SECOND = 5.0;
    private static final int MAX_BURST_PER_MINUTE = 30;

    // 使用 Guava RateLimiter 或自实现滑动窗口
    public boolean tryAcquire(String sessionId) {
        RateLimiter limiter = perSessionLimiters.computeIfAbsent(sessionId,
            k -> RateLimiter.create(PERMITS_PER_SECOND));
        return limiter.tryAcquire();
    }

    public void removeSession(String sessionId) {
        perSessionLimiters.remove(sessionId);
    }
}

// 2. Handler 中使用
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    if (!rateLimiter.tryAcquire(session.getId())) {
        sendMessage(session, Map.of("type", "error", "code", "RATE_LIMITED",
            "message", "消息发送过于频繁，请稍后再试"));
        return;
    }
    // ... 原有逻辑
}
```

### #12 @PreDestroy 未优雅关闭所有活跃会话

**问题**：
- `DepartmentWebSocketHandler.destroy()` 只关闭了心跳调度器，没有主动关闭活跃的 WebSocket 会话
- `AgentWebSocketHandler` 完全没有 `@PreDestroy` 方法

**代码位置**：
- [DepartmentWebSocketHandler.java:970-984](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java#L970)
- AgentWebSocketHandler.java 无 @PreDestroy

**修复方案**：

```java
// AgentWebSocketHandler 新增
@jakarta.annotation.PreDestroy
public void destroy() {
    log.info("[AgentWS] Shutting down, closing {} active sessions", sessions.size());
    // 关闭心跳调度器
    if (heartbeatScheduler != null) {
        heartbeatScheduler.shutdown();
    }
    // 优雅关闭所有活跃会话
    sessions.values().forEach(session -> {
        try {
            if (session.isOpen()) {
                session.close(new CloseStatus(1001, "Server shutting down"));
            }
        } catch (Exception e) {
            log.debug("[AgentWS] Error closing session: {}", e.getMessage());
        }
    });
    sessions.clear();
    sessionLastActive.clear();
    log.info("[AgentWS] Shutdown complete");
}

// DepartmentWebSocketHandler.destroy() 补充关闭会话逻辑
@jakarta.annotation.PreDestroy
public void destroy() {
    // ... 现有关闭心跳调度器逻辑 ...

    // 新增：关闭所有活跃会话
    sessionIndex.values().forEach(session -> {
        try {
            if (session.isOpen()) {
                session.close(new CloseStatus(1001, "Server shutting down"));
            }
        } catch (Exception e) {
            log.debug("[DeptWS] Error closing session: {}", e.getMessage());
        }
    });
    sessionIndex.clear();
    departmentChannels.clear();
    sessionLastActive.clear();
    log.info("[DeptWS] Shutdown complete with sessions closed");
}
```

### #13 SessionContext.addHistory() 无数量限制（或限制过小）

**问题**：核查发现两处 `addHistory()` 实现：
- `AgentService.java` 内部类版本：限制为 10 条（5 轮对话），对复杂场景太少
- `SessionContext.java` 版本：**无任何数量限制**，历史记录会无限增长，可能导致内存溢出

**代码位置**：
- [AgentService.java:1018-1027](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/AgentService.java#L1018)
- [SessionContext.java:106-111](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/SessionContext.java#L106)

**修复方案**：

```java
// 统一为可配置的上限，默认 50 条（25 轮对话）
private static final int MAX_HISTORY_SIZE = 50; // 可通过 application.yml 配置

public void addHistory(String role, String content) {
    if (role == null || content == null) return;
    history.add(Map.of("role", role, "content", content));
    // 超出上限时淘汰最旧的记录
    while (history.size() > MAX_HISTORY_SIZE) {
        history.remove(0);
    }
}
```

---

## 4. MINOR（可以改进）

### #8b CORS 默认为 "*"（安全风险）

**问题**：`WebSocketConfig.java` 中 `WS_ALLOWED_ORIGINS` 环境变量未设置时默认为 `"*"`，允许任何来源的 WebSocket 连接。

**代码位置**：[WebSocketConfig.java:33-35](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/config/WebSocketConfig.java#L33)

**修复方案**：生产环境必须配置 `WS_ALLOWED_ORIGINS`，默认值改为空（拒绝所有未配置的来源）。

```java
String[] origins = (allowedOrigins != null && !allowedOrigins.isBlank())
    ? allowedOrigins.split(",")
    : new String[]{ "http://localhost:8382" }; // 不再默认 "*"
```

### #7b ping/pong 消息类型匹配方式不统一

**问题**：
- `AgentWebSocketHandler` 匹配 `"ping"`（小写精确匹配）
- `DepartmentWebSocketHandler` 匹配 `"PING"`（先 `type.toUpperCase()` 再匹配）

**修复方案**：统一使用 `type.equalsIgnoreCase("ping")` 进行匹配。

---

## 5. 修复优先级路线图

```
Phase 0 (P0): #A1-A6 Windows 自动化闭环（clientId 全链路贯通）
  → 这是系统能"控制客户端电脑"的前提
  → 7 个 Step：ClientIdFilter → WebSocket clientId → BrainContext → ToolContext → WindowsAppTool 自动路由 → DB client_id 列 → pywinauto 注册

Phase 1 (P0): #4 AgentWebSocketHandler 僵尸连接检测
  → 防止无效会话长期占用资源

Phase 2 (P1): #7 pong 响应统一 + #7b ping 匹配统一
  → 前端不再需要做兼容处理

Phase 2 (P1): #8 HandshakeInterceptor
  → 握手阶段拒绝未认证连接，节省资源

Phase 2 (P1): #11 消息频率限制
  → 防止恶意客户端过载

Phase 3 (P2): #12 @PreDestroy 优雅关闭
  → 应用重启时客户端收到干净断连

Phase 3 (P2): #13 addHistory() 数量限制统一
  → 防止内存溢出

Phase 4 (P3): #8b CORS 默认值收紧
  → 生产环境安全加固
```

---

## 7. Windows 自动化闭环断裂点分析（CRITICAL）

> 核心问题：LLM 通过 `WindowsAppTool` 调用 pywinauto 控制客户端电脑的完整链路**未闭环**。
> 桌面端发送的 `clientId` 在后端入口处就丢失了，无法路由到正确的 pywinauto 节点。
>
> **典型场景**：用户在 PC-A 上发送"帮我打开记事本"，系统无法确保在 PC-A 上打开，可能路由到 PC-B 或直接失败。

### 7.0 典型场景追踪：用户说"帮我打开记事本"

```
桌面端 PC-A (clientId=abc-123)
  │
  │ WebSocket ?token=xxx&clientId=abc-123
  ▼
DepartmentWebSocketHandler
  │ ✅ 接收消息
  │ ❌ 未解析 clientId（query string 中有，但代码没读取）
  │ ❌ ConnectionContext 无 clientId 字段
  ▼
DepartmentChatService.processDepartmentBrainAsync()
  │ ✅ 传递 sessionId, userId, department
  │ ❌ 未传递 clientId
  ▼
ConversationOrchestrator.orchestrate()
  │ ✅ 意图分析 → 判定需要 Windows 自动化
  │ ❌ 无 clientId，无法告知大脑"用户来自哪台 PC"
  ▼
TechBrain → 发现需要 WindowsAppTool → 属于 admin 部门工具
  │ ❌ TechBrain 无法直接调用 admin 部门工具
  │ → 升级到 MainBrain
  ▼
MainBrain → ReAct 循环 → LLM 决定调用 windows_app_automation
  │ ❌ BrainContext 无 clientId
  │ ❌ LLM 不知道用户来自哪台 PC
  │ ❌ LLM 必须猜测 node 参数（如 "pc-finance-01"）
  ▼
BrainReActEngine.executeToolCalls()
  │ ❌ ToolContext 无 clientId
  │ ❌ node 参数完全依赖 LLM 猜测
  ▼
WindowsAppTool.execute(params={node: "???", action: "launch", app_name: "notepad"})
  │ ❌ node 为空或猜测错误 → 抛异常 "节点不存在"
  │ ❌ 即使 node 正确，也是碰巧，不是自动路由
  ▼
HTTP POST → http://???:5001/api/windows/launch
  │ ❌ 可能路由到 PC-B 而非 PC-A
  ▼
server.py → pywinauto.Application().start("notepad.exe")
  │ ✅ 如果路由正确，可以打开记事本
  │ ❌ 但当前无法保证路由正确
  ▼
结果回传 → LLM → WebSocket → 桌面端
  ✅ 回传链路正常（如果工具执行成功的话）
```

**结论：10 个环节中有 6 个断裂，当前无法闭环。**

### 7.0.1 clientId 与权限安全分析

> **核心原则：clientId 是物理机器标识，userId 是登录用户标识。两者是临时绑定关系，不是固定映射。**

#### 身份与权限的三层关系

```
┌─────────────────────────────────────────────────────────┐
│  clientId（物理机器）                                     │
│  ┌─────────────────────────────────────────────────────┐│
│  │  userId（当前登录用户）← 临时绑定，可切换              ││
│  │  ┌─────────────────────────────────────────────────┐││
│  │  │  accessLevel（用户权限）                          │││
│  │  │  CHAT_ONLY=0 / LIMITED=1 / DEPARTMENT=2 / FULL=3│││
│  │  └─────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

#### 路由错误的权限风险

| 场景 | 风险 | 严重程度 |
|------|------|----------|
| PC-A 用户（FULL权限）发"打开金蝶"，路由到 PC-B（CHAT_ONLY用户登录） | **越权操作**：FULL 权限用户的指令在 CHAT_ONLY 用户的机器上执行，可能访问该用户无权访问的财务数据 | 严重 |
| PC-A 用户发"关闭所有应用"，路由到 PC-B | **误操作**：关闭了另一台机器上的应用，可能丢失未保存数据 | 严重 |
| PC-A 用户发"截图"，路由到 PC-B | **隐私泄露**：截取了另一台机器的屏幕，可能包含敏感信息 | 严重 |
| 同一台 PC，用户 A 登出、用户 B 登录，但 clientId→userId 绑定未更新 | **权限残留**：用户 B 继承了用户 A 的操作权限 | 中等 |

#### 安全约束规则

```
规则1: clientId + userId 必须同时验证
  → WindowsAppTool 执行前必须确认：目标节点的 clientId 与发起请求的 clientId 一致
  → 如果不一致，必须检查发起用户的 accessLevel >= FULL 才允许跨机操作

规则2: clientId→userId 绑定必须实时更新
  → 用户登录时：更新 clientId→userId 绑定
  → 用户登出时：清除 clientId→userId 绑定
  → 用户切换时：替换 clientId→userId 绑定

规则3: 跨机操作必须显式授权
  → 默认只能操作当前 clientId 对应的机器
  → 跨机操作需要：发起用户 accessLevel >= FULL + 目标机器用户同意（或目标机器无活跃用户）

规则4: 操作审计必须记录 clientId + userId
  → 每次自动化操作的 Trace 必须包含：谁（userId）在哪个客户端（clientId）对哪台机器（targetClientId/nodeId）执行了什么操作
```

#### clientId→userId 绑定数据模型

```sql
-- 新增：客户端与用户的临时绑定表
CREATE TABLE client_user_binding (
    client_id       VARCHAR(100) NOT NULL,  -- 桌面端 clientId
    user_id         VARCHAR(100) NOT NULL,  -- 当前登录用户 ID
    access_level    INT         NOT NULL,   -- 用户权限级别
    department_code VARCHAR(50),            -- 用户所属部门
    tenant_id       VARCHAR(100),           -- 租户 ID
    bound_at        TIMESTAMP   NOT NULL,   -- 绑定时间
    last_active_at  TIMESTAMP   NOT NULL,   -- 最后活跃时间
    PRIMARY KEY (client_id, user_id)
);

-- 索引：按 clientId 查当前绑定
CREATE INDEX idx_binding_client ON client_user_binding(client_id);

-- 索引：按 userId 查所有绑定的客户端
CREATE INDEX idx_binding_user ON client_user_binding(user_id);
```

#### 客户端设备注册表（确保唯一性）

> **核心原则：clientId 必须与设备指纹绑定，防止伪造或复制。**

```sql
-- 新增：客户端设备注册表（持久化设备信息）
CREATE TABLE client_device_registry (
    client_id       VARCHAR(100) PRIMARY KEY,  -- 桌面端 clientId（UUID）
    hostname        VARCHAR(100) NOT NULL,     -- 机器名
    platform        VARCHAR(20)  NOT NULL,     -- 操作系统（win32/linux/darwin）
    os_user         VARCHAR(100),              -- 操作系统用户名
    mac_address     VARCHAR(50),               -- 主网卡 MAC 地址（硬件指纹）
    ip_address      VARCHAR(50),               -- 最后连接 IP
    app_version     VARCHAR(20),               -- 桌面端版本
    first_seen_at   TIMESTAMP   NOT NULL,      -- 首次注册时间
    last_seen_at    TIMESTAMP   NOT NULL,      -- 最后活跃时间
    status          VARCHAR(20)  DEFAULT 'active', -- active/suspended/deleted
    node_id         VARCHAR(100),              -- 关联的 pywinauto 节点 ID
    tenant_id       VARCHAR(100),              -- 所属租户
    UNIQUE (hostname, mac_address)             -- 同一台机器只能有一个 clientId
);

-- 索引：按 MAC 地址查（防止同一机器注册多个 clientId）
CREATE INDEX idx_device_mac ON client_device_registry(mac_address);

-- 索引：按 hostname 查
CREATE INDEX idx_device_hostname ON client_device_registry(hostname);

-- 索引：按 node_id 查（clientId → pywinauto 节点映射）
CREATE INDEX idx_device_node ON client_device_registry(node_id);
```

#### 设备指纹验证机制

```java
// ClientDeviceRegistryService.java
@Service
public class ClientDeviceRegistryService {

    /**
     * 注册或更新客户端设备信息
     * 验证：同一台机器（hostname + mac_address）只能有一个 clientId
     */
    public ClientDeviceEntity registerOrUpdate(ClientDeviceInfo info) {
        // 1. 查找是否已存在相同 hostname + mac_address 的设备
        ClientDeviceEntity existing = repository.findByHostnameAndMacAddress(
            info.hostname, info.macAddress);

        if (existing != null) {
            // 2. 如果存在，验证 clientId 是否匹配
            if (!existing.getClientId().equals(info.clientId)) {
                // 同一台机器尝试用不同的 clientId 注册 → 拒绝
                log.warn("[DeviceRegistry] 同一台机器尝试注册不同 clientId: " +
                    "existing={}, new={}, hostname={}, mac={}",
                    existing.getClientId(), info.clientId, info.hostname, info.macAddress);
                throw new DeviceConflictException(
                    "该设备已注册为 clientId=" + existing.getClientId() +
                    "，请使用原有 clientId 或联系管理员重置");
            }
            // 3. 更新活跃时间
            existing.setLastSeenAt(Instant.now());
            existing.setIpAddress(info.ipAddress);
            return repository.save(existing);
        }

        // 4. 新设备注册
        ClientDeviceEntity entity = new ClientDeviceEntity();
        entity.setClientId(info.clientId);
        entity.setHostname(info.hostname);
        entity.setPlatform(info.platform);
        entity.setOsUser(info.osUser);
        entity.setMacAddress(info.macAddress);
        entity.setIpAddress(info.ipAddress);
        entity.setAppVersion(info.appVersion);
        entity.setFirstSeenAt(Instant.now());
        entity.setLastSeenAt(Instant.now());
        entity.setStatus("active");
        return repository.save(entity);
    }

    /**
     * 验证 clientId 是否有效且与设备信息匹配
     */
    public boolean validate(String clientId, ClientDeviceInfo info) {
        ClientDeviceEntity entity = repository.findById(clientId).orElse(null);
        if (entity == null || entity.getStatus() != "active") {
            return false;
        }
        // 验证 hostname 和 mac_address 是否匹配
        return entity.getHostname().equals(info.hostname) &&
               (entity.getMacAddress() == null ||
                entity.getMacAddress().equals(info.macAddress));
    }
}
```

#### 桌面端设备指纹采集（增强）

```typescript
// client-id.ts 增强：采集 MAC 地址作为硬件指纹
import { networkInterfaces } from 'os';

export interface ClientInfo {
  clientId: string;
  hostname: string;
  platform: NodeJS.Platform;
  osUser: string;
  macAddress: string;  // 新增：主网卡 MAC 地址（硬件指纹）
  appVersion: string;
  createdAt: string;
}

// 获取主网卡 MAC 地址
function getPrimaryMacAddress(): string {
  const nets = networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      // 选择第一个非内部、非虚拟的以太网接口
      if (!net.internal && net.family === 'IPv4' && !name.toLowerCase().includes('virtual')) {
        return net.mac || '';
      }
    }
  }
  return '';
}

// 生成 ClientInfo 时包含 MAC 地址
const info: ClientInfo = {
  clientId: randomUUID(),
  hostname: hostname(),
  platform: platform(),
  osUser: userInfo().username,
  macAddress: getPrimaryMacAddress(),  // 硬件指纹
  appVersion: app.getVersion(),
  createdAt: new Date().toISOString()
};
```

#### 设备注册流程

```
桌面端启动
  → 生成/读取 clientId + 设备指纹（hostname + macAddress）
  → WebSocket 连接 ?clientId=xxx&hostname=xxx&macAddress=xxx
  → ClientIdFilter 提取 clientId + 设备信息
  → ClientDeviceRegistryService.registerOrUpdate()
    → 验证 hostname + macAddress 唯一性
    → 同一台机器只能有一个 clientId
    → 存入 client_device_registry 表
  → 建立 clientId → node_id 映射（如果 pywinauto 已启动）
```

#### 重新安装后找回原 clientId（关键设计）

> **问题**：客户端重新安装后，本地 `userData/client-id.json` 会丢失，导致生成新的 clientId。
> **解决**：服务器根据设备指纹找回原来的 clientId，返回给客户端。

```
桌面端重新安装后首次启动
  → 本地无 client-id.json → 生成临时 clientId（或空）
  → WebSocket 连接 ?hostname=xxx&macAddress=xxx
  → ClientDeviceRegistryService.registerOrUpdate()
    → 查找 hostname + macAddress 对应的已注册设备
    → 找到：返回原 clientId（如 "abc-123"）
    → 未找到：注册新设备，生成新 clientId
  → 服务器返回 clientId 给桌面端
  → 桌面端保存 clientId 到 userData/client-id.json
  → 后续启动使用此 clientId
```

#### 服务器返回 clientId 的 API

```java
// ClientDeviceRegistryService.java
public ClientDeviceEntity registerOrUpdate(ClientDeviceInfo info) {
    // 1. 先查找设备指纹对应的已注册设备
    ClientDeviceEntity existing = repository.findByHostnameAndMacAddress(
        info.hostname, info.macAddress);

    if (existing != null) {
        // 2. 设备已注册，返回原 clientId（即使客户端传的是新 clientId）
        log.info("[DeviceRegistry] 设备已注册，返回原 clientId: {}",
            existing.getClientId());
        existing.setLastSeenAt(Instant.now());
        existing.setIpAddress(info.ipAddress);
        return repository.save(existing);
    }

    // 3. 新设备注册
    // 如果客户端传了 clientId，使用它；否则生成新的
    String clientId = (info.clientId != null && !info.clientId.isBlank())
        ? info.clientId
        : UUID.randomUUID().toString();

    ClientDeviceEntity entity = new ClientDeviceEntity();
    entity.setClientId(clientId);
    entity.setHostname(info.hostname);
    entity.setMacAddress(info.macAddress);
    // ... 其他字段
    return repository.save(entity);
}
```

#### WebSocket 连接响应

```java
// DepartmentWebSocketHandler.afterConnectionEstablished()
public void afterConnectionEstablished(WebSocketSession session) {
    String hostname = extractQueryParam(session.getUri(), "hostname");
    String macAddress = extractQueryParam(session.getUri(), "macAddress");
    String clientId = extractQueryParam(session.getUri(), "clientId");

    // 注册设备，获取真正的 clientId（可能是找回的原 clientId）
    ClientDeviceEntity device = deviceRegistry.registerOrUpdate(
        new ClientDeviceInfo(clientId, hostname, macAddress, ...));

    // 存入 session 属性
    session.getAttributes().put("clientId", device.getClientId());

    // 如果找回的原 clientId 与客户端传的不同，通知客户端更新
    if (!device.getClientId().equals(clientId)) {
        sendJson(session, Map.of(
            "type", "device_registered",
            "clientId", device.getClientId(),
            "message", "您的设备已注册，使用原 clientId: " + device.getClientId()
        ));
    }
}
```

#### 桌面端处理服务器返回的 clientId

```typescript
// ws-client.ts
wsClient.on('device_registered', (data) => {
    // 服务器返回了原 clientId（重新安装后找回）
    if (data.clientId && data.clientId !== currentClientId) {
        // 保存到本地
        saveClientId(data.clientId);
        currentClientId = data.clientId;
        console.log('[WS] 使用服务器返回的原 clientId:', data.clientId);
    }
});
```

#### WindowsAppTool 安全路由逻辑

```java
// WindowsAppTool.execute() 安全路由逻辑
public ToolResult execute(ToolParams params, ToolContext context) {
    String targetNode = params.getString("node");
    String clientId = context.clientId();
    String userId = context.employeeCode(); // 当前登录用户

    // 1. 自动路由：如果 LLM 未指定 node，根据 clientId 查找
    if (targetNode == null || targetNode.isBlank()) {
        targetNode = resolveNodeByClientId(clientId);
    }

    // 2. 安全检查：目标节点是否属于当前 clientId
    String targetClientId = resolveClientIdByNode(targetNode);
    if (!clientId.equals(targetClientId)) {
        // 跨机操作：检查权限
        if (context.accessLevel() < AccessLevel.FULL) {
            return ToolResult.error("安全限制：您没有权限操作其他客户端电脑。" +
                "当前客户端: " + clientId + ", 目标客户端: " + targetClientId);
        }
        // FULL 权限用户跨机操作，记录审计日志
        auditLog.warn("[跨机操作] userId={}, fromClient={}, toClient={}, action={}",
            userId, clientId, targetClientId, params.getString("action"));
    }

    // 3. 权限检查：当前用户是否有权执行此操作
    if (!checkOperationPermission(userId, params.getString("action"))) {
        return ToolResult.error("权限不足：当前用户无权执行 " + params.getString("action") + " 操作");
    }

    // 4. 执行操作
    // ... 原有逻辑
}
```

### 7.1 完整链路现状

```
桌面端 (clientId)
  → HTTP X-Client-Id header ✅ 发送了
  → 后端 Filter/Interceptor ❌ 没有读取，clientId 丢失
  → WebSocket 连接 ❌ 不传 clientId
  → DepartmentChatService / AgentService ❌ 不感知 clientId
  → BrainContext ❌ 无 clientId 字段
  → BrainReActEngine → ToolContext ❌ 无 clientId 字段
  → WindowsAppTool.execute() ❌ 忽略 ToolContext，依赖 LLM 显式传 node 参数
  → HTTP → pywinauto server.py ✅ 通信正常
  → 结果回传 LLM ✅ ReAct 循环正常
```

### 7.2 六个断裂点

| # | 断裂点 | 严重程度 | 代码位置 | 说明 |
|---|--------|---------|----------|------|
| A1 | **后端不读取 X-Client-Id header** | 严重 | 后端无任何 Filter/Interceptor | 桌面端发送了 `X-Client-Id`，但后端完全没有代码读取此 header |
| A2 | **WebSocket 不传 clientId** | 严重 | ws-client.ts / WebSocketConfig | WebSocket 连接和消息中不携带 clientId，部门文本聊天链路无法关联到物理机 |
| A3 | **ToolContext 无 clientId 字段** | 严重 | ToolContext record | 只有 neuronId/sessionId/employeeCode，没有 clientId |
| A4 | **WindowsAppTool 忽略 ToolContext** | 严重 | [WindowsAppTool.java:139-189](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/impl/WindowsAppTool.java#L139) | execute() 接收 context 但完全不用，依赖 LLM 显式传 `node` 参数 |
| A5 | **clientId 到 nodeId 无映射** | 严重 | [WindowsAutomationNodeEntity.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/database/entity/WindowsAutomationNodeEntity.java) | 数据库表无 client_id 列，无法根据 clientId 查找 pywinauto 节点 |
| A6 | **LLM 必须显式传 node 参数** | 中等 | WindowsAppTool schema | LLM 不知道有哪些节点，无法正确路由 |

### 7.3 修复方案：clientId 全链路贯通

#### Step 1: 后端读取 X-Client-Id（A1）

```java
// 新增 ClientIdFilter.java
@Component
public class ClientIdFilter implements Filter {
    public static final ThreadLocal<String> CURRENT_CLIENT_ID = new ThreadLocal<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String clientId = httpReq.getHeader("X-Client-Id");
        if (clientId != null && !clientId.isBlank()) {
            CURRENT_CLIENT_ID.set(clientId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CURRENT_CLIENT_ID.remove();
        }
    }
}
```

#### Step 2: WebSocket 连接携带 clientId（A2）

```java
// AgentWebSocketHandler.afterConnectionEstablished() 中提取
String clientId = extractQueryParam(session.getUri(), "clientId");
if (clientId != null) {
    session.getAttributes().put("clientId", clientId);
}

// DepartmentWebSocketHandler 同理
```

#### Step 3: clientId 传递到 BrainContext（A1+A2 → BrainContext）

```java
// DepartmentChatService / AgentService 中
String clientId = ClientIdFilter.CURRENT_CLIENT_ID.get();
// 或从 WebSocket session 属性获取
brainContext.setClientId(clientId);
```

#### Step 4: ToolContext 增加 clientId（A3）

```java
// ToolContext record 增加字段
public record ToolContext(
    String neuronId,
    String sessionId,
    String securityPolicy,
    long timeout,
    boolean sandboxed,
    String employeeCode,
    String clientId  // 新增
) {}
```

#### Step 5: WindowsAppTool 自动路由（A4+A5+A6）

```java
// WindowsAppTool.execute() 修改
@Override
public ToolResult execute(ToolParams params, ToolContext context) {
    String node = params.getString("node");

    // 如果 LLM 未指定 node，自动根据 clientId 查找
    if (node == null || node.isBlank()) {
        String clientId = context.clientId();
        if (clientId != null && !clientId.isBlank()) {
            // 查询 clientId 对应的 pywinauto 节点
            List<WindowsAutomationNodeEntity> nodes =
                nodeRepository.findByClientIdAndEnabledTrue(clientId);
            if (!nodes.isEmpty()) {
                node = nodes.get(0).getNodeId();
            }
        }
    }

    if (node == null || !this.nodes.containsKey(node)) {
        return ToolResult.error("未找到可用的自动化节点。请在客户端 PC 上启动 pywinauto 服务。");
    }
    // ... 原有逻辑
}
```

#### Step 6: 数据库表增加 client_id 列（A5）

```sql
-- Flyway migration
ALTER TABLE windows_automation_nodes
  ADD COLUMN client_id VARCHAR(100);

-- pywinauto 注册时传入 clientId
-- 建立唯一索引
CREATE UNIQUE INDEX idx_nodes_client_id ON windows_automation_nodes(client_id);
```

#### Step 7: pywinauto 注册时携带 clientId（A5）

```python
# server.py register_to_server() 修改
registration_data = {
    "node_id": node_id,
    "ip": ip,
    "port": port,
    "hostname": hostname,
    "client_id": CLIENT_ID,  # 从配置文件或环境变量读取
    # ... 其他字段
}
```

### 7.4 闭环后的完整链路

```
桌面端 PC-A (clientId=abc-123, userId=user-001, accessLevel=DEPARTMENT)
  → WebSocket ?token=xxx&clientId=abc-123 ✅
  → ClientIdFilter 读取 → ThreadLocal ✅
  → DepartmentWebSocketHandler 解析 clientId → session 属性 ✅
  → 登录时更新 client_user_binding 表（clientId=abc-123 ↔ userId=user-001）✅
  → DepartmentChatService → BrainContext.clientId ✅
  → BrainReActEngine → ToolContext.clientId + accessLevel ✅
  → WindowsAppTool.execute()
    → LLM 未指定 node → 自动查 clientId="abc-123" → 找到 node="pc-finance-01" ✅
    → 安全检查：目标 clientId == 发起 clientId → 同机操作，允许 ✅
    → HTTP → http://pc-finance-01:5001/api/windows/launch ✅
    → pywinauto 在 PC-A 上打开记事本 ✅
  → 结果回传 LLM ✅
  → LLM 回复"已在您的电脑上打开记事本" ✅
  → WebSocket 推送到桌面端 ✅
```

#### 跨机操作场景（FULL 权限管理员）

```
管理员在 PC-A (clientId=abc-123, accessLevel=FULL)
  → 发送"帮我在 PC-B 上打开金蝶"
  → WindowsAppTool.execute()
    → LLM 指定 node="pc-hr-02"（PC-B）
    → 安全检查：目标 clientId ≠ 发起 clientId → 跨机操作
    → accessLevel=FULL → 允许，但记录审计日志 ✅
    → HTTP → http://pc-hr-02:5001/api/windows/launch ✅
```

#### 权限不足场景（DEPARTMENT 用户尝试跨机）

```
普通用户在 PC-A (clientId=abc-123, accessLevel=DEPARTMENT)
  → 发送"帮我在 PC-B 上打开金蝶"
  → WindowsAppTool.execute()
    → 安全检查：目标 clientId ≠ 发起 clientId → 跨机操作
    → accessLevel=DEPARTMENT < FULL → 拒绝 ✅
    → 返回"安全限制：您没有权限操作其他客户端电脑"
```

---

## 8. 与 pywinauto 的关联

根据 [pywinauto README](file:///f:/SoarCloudAI/docker/pywinauto/README.md#L85) 的依赖说明，pywinauto 依赖 `pyWin32` 和 `comtypes`。后端 `WindowsAppTool` 通过 `X-Client-Id` 路由到 pywinauto 节点执行 Windows 自动化。

WebSocket 层的改进与 pywinauto 的关联点：

| 改进项 | 与 pywinauto 的关系 |
|--------|---------------------|
| #4 僵尸连接检测 | 防止 pywinauto 自动化任务的 WebSocket 会话泄漏 |
| #8 HandshakeInterceptor | 在握手阶段验证 clientId 对应的 pywinauto 节点是否在线 |
| #11 消息频率限制 | 防止自动化任务被高频消息触发导致 pywinauto 节点过载 |
| #12 优雅关闭 | 应用重启时通知客户端自动化任务中断，避免 pywinauto 操作半完成 |

---

## 9. 实施检查清单

### WebSocket 基础改进
- [x] #4 AgentWebSocketHandler 新增 sessionLastActive + 心跳检测 + 僵尸清理
- [x] #7 DepartmentWebSocketHandler pong 改为小写 `"pong"`
- [x] #7b 两个 Handler ping 匹配统一为 `equalsIgnoreCase`
- [x] #8 新增 AuthHandshakeInterceptor + WebSocketConfig 注册
- [x] #11 新增 WebSocketRateLimiter + 两个 Handler 接入
- [x] #12 AgentWebSocketHandler 新增 @PreDestroy + DepartmentWebSocketHandler 补充关闭会话
- [x] #13 addHistory() 统一为可配置上限（默认 50）
- [x] #8b CORS 默认值从 "*" 改为 "http://localhost:8382"

### Windows 自动化闭环（clientId 全链路贯通）
- [x] A1: 新增 ClientIdFilter 读取 X-Client-Id header → ThreadLocal
- [x] A2: WebSocket 连接携带 clientId 参数 + Handler 提取到 session 属性
- [x] A3: ToolContext record 增加 clientId + accessLevel 字段
- [x] A4: WindowsAppTool.execute() 从 ToolContext 获取 clientId，自动路由
- [x] A5: windows_automation_nodes 表增加 client_id 列 + Flyway migration
- [x] A5b: WindowsAutomationNodeRepository 增加 findByClientIdAndEnabledTrue()
- [x] A6: WindowsAppTool schema 中 node 参数改为可选（LLM 不指定时自动路由）
- [x] A7: pywinauto server.py 注册时携带 clientId
- [x] A8: 新增 client_user_binding 表（clientId ↔ userId 临时绑定）
- [x] A9: 登录/登出时更新 client_user_binding 绑定关系
- [x] A10: WindowsAppTool 跨机操作安全检查（accessLevel < FULL 拒绝）
- [x] A11: WindowsAppTool 操作审计日志（记录 clientId + userId + targetClientId + action）

### 设备注册与唯一性验证
- [x] D1: 新增 client_device_registry 表（持久化设备信息 + hostname + macAddress）
- [x] D2: 新增 ClientDeviceRegistryService（注册/验证设备）
- [x] D3: 桌面端 client-id.ts 增强：采集 MAC 地址作为硬件指纹
- [x] D4: WebSocket 连接携带 hostname + macAddress 参数
- [x] D5: ClientIdFilter 提取设备信息 → ClientDeviceRegistryService.registerOrUpdate()
- [x] D6: 同一机器（hostname + macAddress）只能有一个 clientId（UNIQUE 约束）
- [x] D7: clientId → node_id 映射（client_device_registry.node_id 字段）
