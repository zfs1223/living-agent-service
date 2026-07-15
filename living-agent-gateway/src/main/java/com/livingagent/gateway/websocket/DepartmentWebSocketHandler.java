package com.livingagent.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.DepartmentAccessService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.session.EventQueueService;
import com.livingagent.gateway.service.AgentService;
import com.livingagent.gateway.service.DepartmentChatService;
import com.livingagent.gateway.service.DepartmentChatService.DepartmentChatResult;
import com.livingagent.gateway.proactive.ProactiveOrchestrator;
import com.livingagent.gateway.proactive.ProactiveOrchestrator.OrchestrationResult;
import com.livingagent.core.security.client.ClientDeviceRegistryService;
import com.livingagent.core.security.client.ClientDeviceInfo;
import com.livingagent.core.database.entity.ClientDeviceEntity;
import com.livingagent.core.session.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.livingagent.core.security.PermissionChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 部门 WebSocket 处理器
 *
 * 职责边界:
 * - 处理 /ws/dept/{brain} 部门脑会话链路
 * - 处理 /ws/enterprise 董事长频道
 * - 处理 /ws/public 公共访客通道
 *
 * 不负责:
 * - 处理 agent 直连 (由 AgentWebSocketHandler 负责)
 * - 处理无参数软路由 (由 Chat.tsx 前端路由决定)
 *
 * 命名映射:
 * - URI path 中的 department/brain: 实际是部门 code (如 tech, hr)
 * - 通过 mapDepartmentToBrain() 映射到 brain 名称 (如 TechBrain, HrBrain)
 *
 * 错误语义 (与 REST 对齐):
 * - PERMISSION_DENIED: 权限不足，无法路由到部门大脑
 * - FORBIDDEN: 无权访问该部门
 * - UNAUTHORIZED: 未认证
 * - INITIALIZING: 模型会话仍在初始化
 * - SYSTEM_ERROR: 系统错误
 */
@Component
public class DepartmentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DepartmentWebSocketHandler.class);

    private final UnifiedAuthService authService;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final DepartmentChatService departmentChatService;
    private final ConnectionRegistry connectionRegistry;
    private final DepartmentAccessService departmentAccessService;
    private final ClientDeviceRegistryService deviceRegistryService;
    private final WebSocketRateLimiter rateLimiter;
    private final ProactiveOrchestrator proactiveOrchestrator;  // PR-1: 主动汇报编排器
    private final com.livingagent.core.websocket.WindowsAutomationClientGateway winAutomationGateway;

    private final Map<String, Set<WebSocketSession>> departmentChannels = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToDepartment = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionIndex = new ConcurrentHashMap<>();
    private static final Pattern SENSITIVE_QUERY_PATTERN = Pattern.compile("(?i)(token|session|authorization)=([^&]+)");

    private final Map<String, AuthContext> sessionToAuthContext = new ConcurrentHashMap<>();
    private final Map<String, Instant> sessionConnectTime = new ConcurrentHashMap<>();
    private final Map<String, AccessLevel> sessionAccessLevel = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> sessionSendLocks = new ConcurrentHashMap<>();

    private final Map<String, Instant> sessionLastActive = new ConcurrentHashMap<>();
    private final Map<String, Boolean> sessionProactiveReported = new ConcurrentHashMap<>();  // PR-9: 标记本次会话已汇报
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    // 移除心跳超时：客户端持续连接不应被切断，连接只在客户端主动断开时关闭
    // sessionLastActive 仅用于记录最后活动时间，不再用于 zombie 检测
    private static final int MAX_GLOBAL_CONNECTIONS = 500;
    private static final int MAX_DEPARTMENT_CONNECTIONS = 50;
    private volatile java.util.concurrent.ScheduledFuture<?> heartbeatTask;
    private java.util.concurrent.ScheduledExecutorService heartbeatScheduler;

    /** Rate-limited auth failure logging: token hash -> last warn log time */
    private final Map<String, Instant> recentAuthFailures = new ConcurrentHashMap<>();
    private static final long AUTH_FAILURE_LOG_TTL_SECONDS = 60;
    private static final long AUTH_FAILURE_CLEANUP_TTL_SECONDS = 5 * 60; // 5 minutes for cleanup
    /** Track auth failure reason per session so afterConnectionEstablished can use proper close code */
    private final Map<String, String> authFailureReasons = new ConcurrentHashMap<>();

    public DepartmentWebSocketHandler(UnifiedAuthService authService, ObjectMapper objectMapper,
                                       AgentService agentService, DepartmentChatService departmentChatService,
                                       ConnectionRegistry connectionRegistry,
                                       DepartmentAccessService departmentAccessService,
                                       ClientDeviceRegistryService deviceRegistryService,
                                       WebSocketRateLimiter rateLimiter,
                                       ProactiveOrchestrator proactiveOrchestrator,
                                       com.livingagent.core.websocket.WindowsAutomationClientGateway winAutomationGateway) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.departmentChatService = departmentChatService;
        this.connectionRegistry = connectionRegistry;
        this.departmentAccessService = departmentAccessService;
        this.deviceRegistryService = deviceRegistryService;
        this.rateLimiter = rateLimiter;
        this.proactiveOrchestrator = proactiveOrchestrator;
        this.winAutomationGateway = winAutomationGateway;
        startHeartbeat();
    }

    private void startHeartbeat() {
        heartbeatScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-heartbeat");
                t.setDaemon(true);
                return t;
            });
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                Instant now = Instant.now();
                
                // 不再主动关闭连接（移除 zombie 检测）
                // 客户端持续连接不应被切断，连接只在客户端主动断开时关闭
                
                // 仅清理 expired recentAuthFailures entries
                Instant authFailureThreshold = now.minusSeconds(AUTH_FAILURE_CLEANUP_TTL_SECONDS);
                recentAuthFailures.entrySet().removeIf(e -> e.getValue().isBefore(authFailureThreshold));
            } catch (Exception e) {
                log.debug("Heartbeat check error: {}", e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String department = extractDepartment(session.getUri());
        if (department == null) {
            log.warn("Invalid WebSocket connection: no department in URI");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Optional<AuthContext> ctxOpt = getAuthContext(session);

        // public 通道允许匿名访问
        if ("public".equals(department)) {
            if (ctxOpt.isEmpty()) {
                // 匿名用户：构造最小化会话
                String userId = "guest_" + session.getId();
                AccessLevel accessLevel = AccessLevel.CHAT_ONLY;
                registerSessionIndexes(session, department, userId, accessLevel, null);
                log.info("WebSocket connected (anonymous): dept=public, sessionId={}", session.getId());
                return;
            }
            // 已登录用户走 public 通道：正常注册
            AuthContext ctx = ctxOpt.get();
            String userId = ctx.getEmployeeId() != null ? ctx.getEmployeeId() : "user_" + session.getId();
            AccessLevel accessLevel = ctx.getAccessLevel() != null ? ctx.getAccessLevel() : AccessLevel.CHAT_ONLY;
            registerSessionIndexes(session, department, userId, accessLevel, ctx);
            log.info("WebSocket connected (authenticated): user={}, dept=public, sessionId={}", userId, session.getId());
            return;
        }

        if (ctxOpt.isEmpty()) {
            String failureReason = authFailureReasons.remove(session.getId());
            if ("TOKEN_INVALID".equals(failureReason)) {
                session.close(new CloseStatus(4001, "TOKEN_EXPIRED"));
            } else {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            }
            return;
        }
        
        AuthContext ctx = ctxOpt.get();
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
            log.warn("Department WebSocket access denied: user={}, dept={}", 
                ctx.getEmployeeId(), department);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if (!checkConnectionLimits(session, department)) return;

        String userId = ctx.getEmployeeId() != null ? ctx.getEmployeeId() : "visitor_" + session.getId();
        AccessLevel accessLevel = ctx.getAccessLevel() != null ? ctx.getAccessLevel() : AccessLevel.CHAT_ONLY;
        
        String clientId = registerClientDevice(session, department);

        if (clientId != null && !clientId.isBlank()) {
            log.info("Department WebSocket clientId bound: sessionId={}, clientId={}, dept={}", 
                session.getId(), clientId, department);
            winAutomationGateway.registerSession(clientId, session);
        }
        
        registerSessionIndexes(session, department, userId, accessLevel, ctx);

        log.info("WebSocket connected: user={}, dept={}, sessionId={}, accessLevel={}",
            userId, department, session.getId(), accessLevel);

        handleReconnection(session, department, userId);

        broadcastSystemMessage(department, new SystemMessage("USER_JOINED", userId, ctx.getName(), department));
        sendOnlineUsers(department);

        sendProactiveReport(session, userId);
    }

    /** Check global and department connection limits, closing session if exceeded. Returns false if rejected. */
    private boolean checkConnectionLimits(WebSocketSession session, String department) throws Exception {
        if (sessionIndex.size() >= MAX_GLOBAL_CONNECTIONS) {
            log.warn("WebSocket connection rejected: global limit reached ({})", MAX_GLOBAL_CONNECTIONS);
            session.close(new CloseStatus(4029, "GLOBAL_CONNECTION_LIMIT"));
            return false;
        }
        Set<WebSocketSession> deptSessions = departmentChannels.get(department);
        if (deptSessions != null && deptSessions.size() >= MAX_DEPARTMENT_CONNECTIONS) {
            log.warn("WebSocket connection rejected: department {} limit reached ({})", department, MAX_DEPARTMENT_CONNECTIONS);
            session.close(new CloseStatus(4030, "DEPARTMENT_CONNECTION_LIMIT"));
            return false;
        }
        return true;
    }

    /** Register client device from query params. Returns resolved clientId (may differ from input if recovered). */
    private String registerClientDevice(WebSocketSession session, String department) {
        String clientId = extractQueryParam(session.getUri(), "clientId");
        String hostname = extractQueryParam(session.getUri(), "hostname");
        String macAddress = extractQueryParam(session.getUri(), "macAddress");
        String platform = extractQueryParam(session.getUri(), "platform");
        String osUser = extractQueryParam(session.getUri(), "osUser");
        String applications = extractQueryParam(session.getUri(), "applications");
        
        if (clientId != null && !clientId.isBlank() && hostname != null && !hostname.isBlank()) {
            try {
                ClientDeviceInfo deviceInfo = new ClientDeviceInfo(
                    clientId, hostname, platform, osUser, macAddress,
                    session.getRemoteAddress() != null ? session.getRemoteAddress().getAddress().getHostAddress() : null,
                    null, null, applications
                );
                ClientDeviceEntity device = deviceRegistryService.registerOrUpdate(deviceInfo);
                
                if (applications != null && !applications.isBlank()) {
                    deviceRegistryService.updateApplications(clientId, applications);
                    log.info("Device applications updated: clientId={}, apps={}", clientId, applications);
                }
                
                if (!device.getClientId().equals(clientId)) {
                    log.info("Device clientId recovered: original={}, recovered={}", clientId, device.getClientId());
                    clientId = device.getClientId();
                    sendJson(session, Map.of(
                        "type", "device_registered",
                        "clientId", device.getClientId(),
                        "message", "设备已注册，使用原 clientId: " + device.getClientId()
                    ));
                }
            } catch (Exception e) {
                log.warn("Device registration failed: clientId={}, hostname={}, error={}", clientId, hostname, e.getMessage());
            }
        }
        return clientId;
    }

    /** Register session into all internal index maps and connection registry. */
    private void registerSessionIndexes(WebSocketSession session, String department, String userId,
            AccessLevel accessLevel, AuthContext ctx) {
        departmentChannels.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(session);
        sessionToDepartment.put(session.getId(), department);
        sessionToUser.put(session.getId(), userId);
        sessionIndex.put(session.getId(), session);
        sessionLastActive.put(session.getId(), Instant.now());
        // ConcurrentHashMap不允许null值，只有ctx非null时才put
        if (ctx != null) {
            sessionToAuthContext.put(session.getId(), ctx);
        }
        sessionConnectTime.put(session.getId(), Instant.now());
        sessionAccessLevel.put(session.getId(), accessLevel);
        sessionSendLocks.computeIfAbsent(session.getId(), id -> new ReentrantLock());

        if (ctx != null) {
            connectionRegistry.register(session.getId(), userId, ctx.getTenantId(),
                new ConnectionContext(
                    session.getId(), userId, ctx.getTenantId(), ctx.getDepartment(),
                    null, null, null, null, null,
                    Instant.now(), Instant.now(), Map.of()
                ));
        } else {
            // 匿名用户：使用默认 tenantId 和 department
            connectionRegistry.register(session.getId(), userId, "public",
                new ConnectionContext(
                    session.getId(), userId, "public", department,
                    null, null, null, null, null,
                    Instant.now(), Instant.now(), Map.of()
                ));
        }
    }

    /** Handle reconnection: restore conversation binding, replay history and pending events. */
    private void handleReconnection(WebSocketSession session, String department, String userId) {
        String reconnectConversationId = extractQueryParam(session.getUri(), "conversationId");
        if (reconnectConversationId == null || reconnectConversationId.isBlank()) return;

        connectionRegistry.bindConversation(session.getId(), reconnectConversationId);
        log.info("WebSocket reconnected with conversationId: user={}, convId={}", userId, reconnectConversationId);

        try {
            departmentChatService.bindSessionToConversation(session.getId(), reconnectConversationId);
        } catch (Exception bindEx) {
            log.debug("Failed to bind session to conversation for receipt routing: {}", bindEx.getMessage());
        }

        try {
            List<?> history = departmentChatService.getConversationHistory(reconnectConversationId, 20);
            if (!history.isEmpty()) {
                Map<String, Object> reconnectMsg = new HashMap<>();
                reconnectMsg.put("type", "reconnected");
                reconnectMsg.put("conversationId", reconnectConversationId);
                reconnectMsg.put("historyCount", history.size());
                reconnectMsg.put("history", history);
                sendJson(session, reconnectMsg);
            }
            
            replayPendingEvents(session);
        } catch (Exception e) {
            log.debug("Failed to send reconnection data: {}", e.getMessage());
        }
    }

    /** Replay pending events from PersistentConnectionRegistry after reconnection. */
    private void replayPendingEvents(WebSocketSession session) {
        if (!(connectionRegistry instanceof PersistentConnectionRegistry persistent)) return;

        List<EventQueueService.PendingEvent> pendingEvents = persistent.getPendingEvents(session.getId());
        if (pendingEvents.isEmpty()) return;

        log.info("Replaying {} pending events for sessionId={}", pendingEvents.size(), session.getId());
        for (var event : pendingEvents) {
            try {
                Map<String, Object> eventMsg = objectMapper.readValue(event.payload(), Map.class);
                eventMsg.put("type", "replay");
                eventMsg.put("eventId", event.eventId());
                sendJson(session, eventMsg);
                persistent.markEventSent(session.getId(), event.eventId());
            } catch (Exception ex) {
                log.warn("Failed to replay event: {}", ex.getMessage());
            }
        }
        persistent.clearSentEvents(session.getId());
    }

    /** PR-1: Send proactive report on first connection (not reconnection). */
    private void sendProactiveReport(WebSocketSession session, String userId) {
        if (proactiveOrchestrator == null || sessionProactiveReported.get(session.getId()) != null) return;

        try {
            OrchestrationResult proactiveResult = proactiveOrchestrator.runForUser(userId);
            if (proactiveResult != null && !proactiveResult.suggestions().isEmpty()) {
                Map<String, Object> proactiveMsg = new HashMap<>();
                proactiveMsg.put("type", "proactive_report");
                proactiveMsg.put("userId", userId);
                proactiveMsg.put("suggestions", proactiveResult.suggestions());
                proactiveMsg.put("alerts", proactiveResult.alerts());
                proactiveMsg.put("metadata", proactiveResult.metadata());
                proactiveMsg.put("timestamp", Instant.now().toString());
                sendJson(session, proactiveMsg);
                sessionProactiveReported.put(session.getId(), true);
                log.info("Proactive report sent to user {} on login: {} suggestions, {} alerts",
                    userId, proactiveResult.suggestions().size(), proactiveResult.alerts().size());
            }
        } catch (Exception e) {
            log.debug("Failed to generate proactive report for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        
        // 消息频率限制检查
        if (!rateLimiter.tryAcquire(sessionId)) {
            sendJson(session, Map.of("type", "error", "code", "RATE_LIMITED",
                "message", "消息发送过于频繁，请稍后再试"));
            log.warn("Rate limit exceeded: sessionId={}", sessionId);
            return;
        }
        
        sessionLastActive.put(sessionId, Instant.now());
        String department = sessionToDepartment.get(sessionId);
        if (department == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = sessionToUser.get(sessionId);
        String payload = message.getPayload();

        connectionRegistry.updateLastActivity(session.getId());

        log.debug("WebSocket message: user={}, dept={}, message={}", 
            userId, department, payload.length() > 100 ? payload.substring(0, 100) + "..." : payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.getOrDefault("type", "CHAT");

            switch (type) {
                case "CHAT", "chat" -> handleChatMessage(session, department, userId, msg);
                case "TYPING", "typing" -> handleTypingIndicator(department, userId, msg);
                case "PING", "ping" -> sendPong(session);
                case "audio_full" -> handlePublicAudioFullChain(session, department, userId, msg);
                case "win_automation_response" -> handleWinAutomationResponse(msg);
                default -> {
                    if (type.equalsIgnoreCase("CHAT")) {
                        handleChatMessage(session, department, userId, msg);
                    } else if (type.equalsIgnoreCase("TYPING")) {
                        handleTypingIndicator(department, userId, msg);
                    } else if (type.equalsIgnoreCase("PING")) {
                        sendPong(session);
                    } else if (type.equalsIgnoreCase("win_automation_response")) {
                        handleWinAutomationResponse(msg);
                    } else {
                        handleChatMessage(session, department, userId, msg);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", e.getMessage());
            handleChatMessage(session, department, userId,
                Map.of("content", payload, "timestamp", Instant.now().toString()));
        }
    }

    /**
     * 处理 Windows 自动化工具响应
     * 消息格式：{"type":"win_automation_response","data":{"id":<long>,"success":<bool>,"result":<any>,"error":<string>}}
     */
    private void handleWinAutomationResponse(Map<String, Object> msg) {
        Object dataObj = msg.get("data");
        if (dataObj == null || !(dataObj instanceof Map)) {
            log.warn("Invalid win_automation_response: missing data");
            return;
        }
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Long requestId = data.get("id") instanceof Number
            ? ((Number) data.get("id")).longValue() : null;
        if (requestId == null) {
            log.warn("Invalid win_automation_response: missing id");
            return;
        }
        Boolean success = (Boolean) data.getOrDefault("success", false);
        Object result = data.get("result");
        String error = (String) data.get("error");

        winAutomationGateway.handleResponse(requestId, success, result, error);
        log.debug("WinAutomation response processed: requestId={}, success={}", requestId, success);
    }

    private void handleChatMessage(WebSocketSession session, String department, String userId, 
                                   Map<String, Object> msg) throws Exception {
        Optional<AuthContext> ctxOpt = getAuthContext(session);
        String userName = ctxOpt.map(AuthContext::getName).orElse(userId);
        String content = (String) msg.getOrDefault("content", "");
        String conversationId = (String) msg.getOrDefault("conversationId", null);

        if (conversationId != null) {
            connectionRegistry.bindConversation(session.getId(), conversationId);
        }
        
        ChatMessage chatMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            userId,
            userName,
            department,
            content,
            Instant.now(),
            (String) msg.getOrDefault("metadata", "")
        );

        broadcast(department, chatMessage);

        processWithBrain(session, department, userId, content, conversationId);
    }

    private void processWithBrain(WebSocketSession session, String department, String userId, String content, String conversationId) {
        if (content == null || content.isBlank()) return;
        if ("public".equals(department)) {
            processPublicChannel(session, userId, content);
            return;
        }

        String sessionId = session.getId();
        log.info("processWithBrain: dept={}, userId={}, sessionId={}, contentLength={}", 
            department, userId, sessionId, content.length());

        sendThinkingIndicator(session, department);

        String requestId = UUID.randomUUID().toString();
        String brainName = com.livingagent.core.security.Department.mapDepartmentToBrain(department);
        Optional<com.livingagent.core.brain.Brain> brainOpt = departmentChatService.getBrainByDepartment(department);
        if (brainOpt.isEmpty()) {
            sendBrainNotFoundError(session);
            return;
        }

        com.livingagent.core.brain.Brain brain = brainOpt.get();
        updateBrainContext(brain, session, sessionId);

        connectionRegistry.updateLastActivity(sessionId);

        departmentChatService.processDepartmentBrainAsync(
                requestId, department, brainName, brainOpt.get(), content, sessionId, userId, userId, conversationId)
            .thenAccept(chatResult -> handleBrainChatResult(session, department, sessionId, requestId, chatResult))
            .exceptionally(e -> {
                log.error("Brain processing failed for dept={}, user={}: {}", department, userId, e.getMessage());
                try {
                    if (session.isOpen()) {
                        Map<String, Object> errorMsg = Map.of(
                            "type", "error",
                            "code", "SYSTEM_ERROR",
                            "message", "处理失败: " + e.getMessage()
                        );
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
                    }
                } catch (Exception ex) {
                    log.warn("Failed to send error message: {}", ex.getMessage());
                }
                return null;
            });
    }

    private void sendThinkingIndicator(WebSocketSession session, String department) {
        try {
            sendJson(session, Map.of("type", "thinking", "content", ""));
            log.info("Thinking indicator sent for dept={}", department);
        } catch (Exception e) {
            log.warn("Failed to send thinking indicator: {}", e.getMessage());
        }
    }

    private void sendBrainNotFoundError(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                sendJson(session, Map.of("type", "error", "code", "NO_BRAIN", "message", "部门大脑未注册"));
            }
        } catch (Exception e) {
            log.warn("Failed to send no brain message: {}", e.getMessage());
        }
    }

    private void updateBrainContext(com.livingagent.core.brain.Brain brain, WebSocketSession session, String sessionId) {
        if (brain.getContext() == null) return;

        String clientId = extractQueryParam(session.getUri(), "clientId");
        AccessLevel accessLevel = sessionAccessLevel.getOrDefault(sessionId, AccessLevel.CHAT_ONLY);
        if (clientId != null && !clientId.isBlank()) {
            brain.getContext().setClientId(clientId);
        }
        brain.getContext().setAccessLevel(accessLevel.getLevel());
        log.debug("Brain context updated: clientId={}, accessLevel={}", clientId, accessLevel);
    }

    private void handleBrainChatResult(WebSocketSession session, String department, String sessionId,
            String requestId, com.livingagent.gateway.service.DepartmentChatService.DepartmentChatResult chatResult) {
        log.info("thenAccept callback triggered: requestId={}, sessionId={}, sessionOpen={}, success={}",
            requestId, sessionId, session.isOpen(), chatResult.success());
        try {
            if (!session.isOpen()) {
                log.warn("Session already closed when trying to send response: sessionId={}, requestId={}", sessionId, requestId);
                return;
            }
            
            if (chatResult.success()) {
                Map<String, Object> doneMsg = new HashMap<>();
                doneMsg.put("type", "done");
                doneMsg.put("content", chatResult.text());
                doneMsg.put("model", chatResult.model());
                doneMsg.put("department", chatResult.department());
                doneMsg.put("brain", chatResult.brain());
                doneMsg.put("intent", chatResult.intent());
                doneMsg.put("neuron", chatResult.neuron());
                doneMsg.put("accessLevel", sessionAccessLevel.getOrDefault(sessionId, AccessLevel.CHAT_ONLY).name());
                if (chatResult.executionId() != null) {
                    doneMsg.put("executionId", chatResult.executionId());
                }
                if (chatResult.conversationId() != null) {
                    doneMsg.put("conversationId", chatResult.conversationId());
                } else {
                    connectionRegistry.getContext(sessionId).ifPresent(ctx -> {
                        if (ctx.conversationId() != null) {
                            doneMsg.put("conversationId", ctx.conversationId());
                        }
                    });
                }

                log.info("Sending done message to session: sessionId={}, requestId={}, contentLength={}",
                    sessionId, requestId, chatResult.text() != null ? chatResult.text().length() : 0);
                sendJson(session, doneMsg);
                log.info("Done message sent successfully: sessionId={}, requestId={}", sessionId, requestId);
                
                sessionLastActive.put(sessionId, Instant.now());

                BrainResponse brainResponse = new BrainResponse(
                    UUID.randomUUID().toString(),
                    "brain_" + department,
                    department + "Brain",
                    department,
                    chatResult.text(),
                    Instant.now()
                );
                broadcastExcept(department, brainResponse, session.getId());
            } else {
                log.warn("Sending error response: sessionId={}, requestId={}, status={}, reason={}",
                    sessionId, requestId, chatResult.status(), chatResult.reason());
                sendJson(session, Map.of(
                    "type", "error",
                    "code", chatResult.status(),
                    "message", chatResult.reason() != null ? chatResult.reason() : "处理失败"
                ));
            }
        } catch (Exception e) {
            log.error("Failed to process brain result: requestId={}, sessionId={}, error={}",
                requestId, sessionId, e.getMessage());
        }
    }

    /**
     * 推送执行进度到 WebSocket 客户端
     * 用于长任务异步进度推送（阶段4）
     */
    public void pushExecutionProgress(String sessionId, String executionId, String status, 
                                     int receiptCount, int completedCount, int failedCount) {
        WebSocketSession session = sessionIndex.get(sessionId);
        String department = sessionToDepartment.get(sessionId);

        Map<String, Object> progressMsg = Map.of(
            "type", "execution_progress",
            "executionId", executionId != null ? executionId : "",
            "status", status != null ? status : "UNKNOWN",
            "receiptCount", receiptCount,
            "completedCount", completedCount,
            "failedCount", failedCount,
            "updatedAt", Instant.now().toString()
        );

        if (session != null && session.isOpen()) {
            try {
                sendJson(session, progressMsg);
                log.info("Pushed execution progress: executionId={}, status={}, completed={}/{}", 
                    executionId, status, completedCount, receiptCount);
            } catch (Exception e) {
                log.warn("Failed to push execution progress to session {}: {}", sessionId, e.getMessage());
                broadcastFallbackProgress(sessionId, department, progressMsg);
            }
        } else {
            log.debug("Session not found or closed for execution progress: sessionId={}, falling back to department broadcast", sessionId);
            broadcastFallbackProgress(sessionId, department, progressMsg);
        }
    }

    public void pushExecutionEvent(String sessionId, String executionId, String eventType, Map<String, Object> eventData) {
        WebSocketSession session = sessionIndex.get(sessionId);
        String department = sessionToDepartment.get(sessionId);

        Map<String, Object> eventMsg = new LinkedHashMap<>();
        eventMsg.put("type", "execution_event");
        eventMsg.put("eventType", eventType);
        eventMsg.put("executionId", executionId != null ? executionId : "");
        eventMsg.put("timestamp", Instant.now().toString());
        if (eventData != null) {
            eventMsg.putAll(eventData);
        }

        if (session != null && session.isOpen()) {
            try {
                sendJson(session, eventMsg);
                log.debug("Pushed execution event: eventType={}, executionId={}", eventType, executionId);
            } catch (Exception e) {
                log.warn("Failed to push execution event to session {}: {}", sessionId, e.getMessage());
                broadcastFallbackProgress(sessionId, department, eventMsg);
            }
        } else {
            broadcastFallbackProgress(sessionId, department, eventMsg);
        }
    }

    /**
     * 推送员工任务更新到该员工相关的 WebSocket 客户端
     * 用于通知数字员工自己的任务状态变更
     */
    public void pushEmployeeTaskUpdate(String department, String employeeCode, String executionId,
                                       String status, String summary, String modelName) {
        Map<String, Object> updateMsg = new LinkedHashMap<>();
        updateMsg.put("type", "employee_task_update");
        updateMsg.put("employeeCode", employeeCode != null ? employeeCode : "");
        updateMsg.put("executionId", executionId != null ? executionId : "");
        updateMsg.put("status", status != null ? status : "UNKNOWN");
        updateMsg.put("summary", summary != null ? summary : "");
        updateMsg.put("modelName", modelName != null ? modelName : "");
        updateMsg.put("timestamp", Instant.now().toString());

        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null && !sessions.isEmpty()) {
            broadcastToSessions(sessions, updateMsg);
        }
        log.info("Pushed employee task update: employee={}, executionId={}, status={}",
            employeeCode, executionId, status);
    }

    /**
     * 推送员工状态变化到部门 WebSocket 客户端
     * 用于实时更新前端办公室区域的员工位置（工作区/休息区/协作区）
     *
     * @param department 部门代码
     * @param employeeId 员工ID
     * @param employeeName 员工名称
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     */
    public void pushEmployeeStatusChanged(String department, String employeeId, String employeeName,
                                          String oldStatus, String newStatus) {
        Map<String, Object> statusMsg = new LinkedHashMap<>();
        statusMsg.put("type", "employee_status_changed");
        statusMsg.put("employeeId", employeeId != null ? employeeId : "");
        statusMsg.put("employeeName", employeeName != null ? employeeName : "");
        statusMsg.put("oldStatus", oldStatus != null ? oldStatus : "");
        statusMsg.put("newStatus", newStatus != null ? newStatus : "");
        statusMsg.put("timestamp", Instant.now().toString());

        // 推送到指定部门的所有连接
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null && !sessions.isEmpty()) {
            broadcastToSessions(sessions, statusMsg);
            log.info("Pushed employee status changed: employee={}, {} -> {}, dept={}",
                employeeId, oldStatus, newStatus, department);
        }
    }

    private void broadcastFallbackProgress(String originalSessionId, String department, Map<String, Object> progressMsg) {
        if (department == null) {
            for (Map.Entry<String, Set<WebSocketSession>> entry : departmentChannels.entrySet()) {
                broadcastToSessions(entry.getValue(), progressMsg);
            }
        } else {
            Set<WebSocketSession> sessions = departmentChannels.get(department);
            if (sessions != null && !sessions.isEmpty()) {
                broadcastToSessions(sessions, progressMsg);
            }
        }
    }

    private void broadcastToSessions(Set<WebSocketSession> sessions, Map<String, Object> message) {
        if (sessions == null) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("Failed to serialize progress message: {}", e.getMessage());
            return;
        }
        for (WebSocketSession ws : sessions) {
            if (ws.isOpen()) {
                sendTextSafely(sessionToDepartment.get(ws.getId()), ws, json);
            }
        }
    }

    private void handleTypingIndicator(String department, String userId, Map<String, Object> msg) 
            throws Exception {
        boolean isTyping = Boolean.TRUE.equals(msg.get("isTyping"));
        Optional<AuthContext> ctxOpt = getAuthContextFromSessionToUser(userId);
        String userName = ctxOpt.map(AuthContext::getName).orElse(userId);
        
        TypingIndicator indicator = new TypingIndicator(
            userId,
            userName,
            department,
            isTyping,
            Instant.now()
        );

        broadcast(department, indicator);
    }

    private void sendPong(WebSocketSession session) throws Exception {
        Map<String, Object> pong = Map.of(
            "type", "pong",
            "timestamp", Instant.now().toString()
        );
        sendJson(session, pong);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String department = sessionToDepartment.remove(sessionId);
        String userId = sessionToUser.remove(sessionId);
        sessionIndex.remove(sessionId);
        sessionLastActive.remove(sessionId);
        
        // 清理限流器
        rateLimiter.removeSession(sessionId);
        sessionToAuthContext.remove(sessionId);
        sessionConnectTime.remove(sessionId);
        sessionAccessLevel.remove(sessionId);
        sessionSendLocks.remove(sessionId);
        authFailureReasons.remove(sessionId);

        connectionRegistry.unregister(sessionId);

        // 注销 Windows 自动化网关
        String closedClientId = extractQueryParam(session.getUri(), "clientId");
        if (closedClientId != null && !closedClientId.isBlank()) {
            winAutomationGateway.unregisterSession(closedClientId);
        }

        if (department != null) {
            Set<WebSocketSession> sessions = departmentChannels.get(department);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    departmentChannels.remove(department);
                }
            }

            Optional<AuthContext> ctxOpt = getAuthContextFromSessionToUser(userId);
            String userName = ctxOpt.map(AuthContext::getName).orElse(userId);
            
            broadcastSystemMessage(department, 
                new SystemMessage("USER_LEFT", userId, userName, department));
            
            sendOnlineUsers(department);

            log.info("WebSocket disconnected: user={}, dept={}, status={}", 
                userId, department, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = sessionToUser.getOrDefault(session.getId(), "unknown");
        String department = sessionToDepartment.getOrDefault(session.getId(), "unknown");
        String exceptionType = exception.getClass().getSimpleName();
        String exceptionMsg = exception.getMessage();
        
        if (exceptionMsg == null || exceptionMsg.isEmpty()) {
            if (exception.getCause() != null) {
                exceptionMsg = exception.getCause().getClass().getSimpleName() + ": " + exception.getCause().getMessage();
            } else {
                exceptionMsg = "no detail available (exception type: " + exceptionType + ")";
            }
        }
        
        log.error("WebSocket transport error: sessionId={}, user={}, dept={}, exceptionType={}, error={}", 
            session.getId(), userId, department, exceptionType, exceptionMsg);
    }

    private void broadcast(String department, Object message) throws Exception {
        String json = objectMapper.writeValueAsString(message);

        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                sendTextSafely(department, session, json);
            }
        }
    }

    private void broadcastExcept(String department, Object message, String excludedSessionId) throws Exception {
        String json = objectMapper.writeValueAsString(message);

        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null) {
            for (WebSocketSession ws : sessions) {
                if (excludedSessionId != null && excludedSessionId.equals(ws.getId())) {
                    continue;
                }
                sendTextSafely(department, ws, json);
            }
        }
    }

    /**
     * 前台闲聊通道：通过 Qwen3Neuron (model_daemon) 处理对话。
     * 走 agentService.chatPublic() 以复用闲聊神经元。
     */
    private void processPublicChannel(WebSocketSession session, String userId, String content) {
        log.info("Public channel chat: userId={}, contentLength={}", userId, content.length());
        try {
            sendJson(session, Map.of("type", "thinking", "content", ""));
            log.debug("Sent 'thinking' status to public channel session");
        } catch (Exception e) {
            log.warn("Failed to send 'thinking' status: {}", e.getMessage());
        }

        log.debug("Calling agentService.chatPublic() for userId={}", userId);
        agentService.chatPublic(content, userId)
            .thenAccept(response -> {
                log.debug("chatPublic completed with response length: {}", response != null ? response.length() : 0);
                try {
                    if (session.isOpen()) {
                        sendJson(session, Map.of(
                            "type", "done",
                            "content", response,
                            "timestamp", Instant.now().toString()
                        ));
                        log.debug("Sent 'done' response to public channel session");
                    }
                } catch (Exception e) {
                    log.warn("Failed to send public channel response: {}", e.getMessage());
                }
            })
            .exceptionally(ex -> {
                log.error("chatPublic failed with exception: {}", ex.getMessage(), ex);
                try {
                    if (session.isOpen()) {
                        sendJson(session, Map.of(
                            "type", "error",
                            "message", "闲聊服务暂时不可用"
                        ));
                    }
                } catch (Exception ignored) {}
                return null;
            });
    }

    /**
     * 前台闲聊通道音频全链路：ASR → LLM → TTS。
     * 仅对 public 通道启用，其他通道走原有的部门大脑逻辑。
     */
    private void handlePublicAudioFullChain(WebSocketSession session, String department, String userId, Map<String, Object> msg) {
        if (!"public".equals(department)) {
            log.warn("audio_full only supported on public channel, got: {}", department);
            return;
        }

        String audioData = (String) msg.get("audio");
        if (audioData == null || audioData.isEmpty()) {
            try { sendJson(session, Map.of("type", "error", "message", "缺少音频数据")); } catch (Exception ignored) {}
            return;
        }

        log.info("Public channel audio_full: userId={}, audioLength={}", userId, audioData.length());
        try {
            sendJson(session, Map.of("type", "thinking", "content", ""));
        } catch (Exception ignored) {}

        agentService.chatPublicAudio(audioData, userId)
            .thenAccept(response -> {
                try {
                    if (session.isOpen()) {
                        sendJson(session, response);
                    }
                } catch (Exception e) {
                    log.warn("Failed to send audio response: {}", e.getMessage());
                }
            })
            .exceptionally(ex -> {
                try {
                    if (session.isOpen()) {
                        sendJson(session, Map.of("type", "error", "message", "语音处理失败"));
                    }
                } catch (Exception ignored) {}
                return null;
            });
    }

    private void sendJson(WebSocketSession session, Object message) throws IOException {
        sendTextSafely(sessionToDepartment.get(session.getId()), session, objectMapper.writeValueAsString(message));
    }

    private void sendTextSafely(String department, WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            removeClosedSession(department, session);
            return;
        }

        String sessionId = session.getId();
        ReentrantLock lock = sessionSendLocks.computeIfAbsent(sessionId, id -> new ReentrantLock());
        lock.lock();
        try {
            if (!session.isOpen()) {
                removeClosedSession(department, session);
                return;
            }
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
            removeClosedSession(department, session);
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (Exception closeError) {
                log.debug("Failed to close broken WebSocket session {}: {}", sessionId, closeError.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    private void removeClosedSession(String department, WebSocketSession session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getId();
        String resolvedDepartment = department != null ? department : sessionToDepartment.get(sessionId);
        if (resolvedDepartment != null) {
            Set<WebSocketSession> sessions = departmentChannels.get(resolvedDepartment);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    departmentChannels.remove(resolvedDepartment);
                }
            }
        }
        sessionToDepartment.remove(sessionId);
        sessionToUser.remove(sessionId);
        sessionIndex.remove(sessionId);
        sessionToAuthContext.remove(sessionId);
        sessionConnectTime.remove(sessionId);
        sessionAccessLevel.remove(sessionId);
        sessionSendLocks.remove(sessionId);
        authFailureReasons.remove(sessionId);
    }

    private void broadcastSystemMessage(String department, SystemMessage message) throws Exception {
        broadcast(department, message);
    }

    private void sendOnlineUsers(String department) throws Exception {
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions == null) return;

        List<OnlineUser> users = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            String userId = sessionToUser.get(session.getId());
            Instant connectTime = sessionConnectTime.get(session.getId());
            if (userId != null) {
                users.add(new OnlineUser(userId, department, connectTime));
            }
        }

        OnlineUsersList list = new OnlineUsersList(department, users);
        broadcast(department, list);
    }

    private String extractDepartment(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath();

        if (path.startsWith("/ws/dept/")) {
            return path.substring("/ws/dept/".length()).toLowerCase();
        }
        if (path.startsWith("/ws/enterprise")) {
            return "enterprise";
        }
        if (path.startsWith("/ws/public")) {
            return "public";
        }

        return null;
    }

    private Optional<AuthContext> getAuthContext(WebSocketSession session) {
        // 优先从 session attributes 获取（由 AuthHandshakeInterceptor 设置）
        AuthContext cachedContext = (AuthContext) session.getAttributes().get("authContext");
        if (cachedContext != null) {
            log.debug("WebSocket auth - Using cached authContext from handshake: userId={}", cachedContext.getEmployeeId());
            return Optional.of(cachedContext);
        }

        // 兜底：从请求头/URI 获取 token 并验证
        String token = null;

        String authorization = session.getHandshakeHeaders().getFirst("Authorization");
        log.debug("WebSocket auth attempt - Authorization header: {}", authorization != null ? "Bearer ***" : "null");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }

        if (token == null || token.isBlank()) {
            URI uri = session.getUri();
            log.debug("WebSocket auth - URI: {}, Query: {}", uri != null ? uri.getPath() : "null", sanitizeQuery(uri));
            token = extractQueryParam(uri, "token");
            log.debug("WebSocket auth - Extracted token from query param: {}", token != null ? "***" : "null");
        }

        if (token == null || token.isBlank()) {
            log.warn("WebSocket connection without auth, closing");
            authFailureReasons.put(session.getId(), "NO_TOKEN");
            return Optional.empty();
        }

        log.debug("WebSocket auth - Validating token for session: {}", session.getId());
        Optional<AuthSession> sessionOpt = authService.validateSession(token);
        if (sessionOpt.isEmpty()) {
            String tokenKey = "auth_" + token.hashCode();
            Instant now = Instant.now();
            Instant lastLogged = recentAuthFailures.get(tokenKey);
            if (lastLogged == null || lastLogged.isBefore(now.minusSeconds(AUTH_FAILURE_LOG_TTL_SECONDS))) {
                log.warn("WebSocket auth - Token validation failed for session: {}", session.getId());
                recentAuthFailures.put(tokenKey, now);
            } else {
                log.debug("WebSocket auth - Token validation failed for session: {} (suppressed, recently logged)", session.getId());
            }
            authFailureReasons.put(session.getId(), "TOKEN_INVALID");
        }
        return sessionOpt.map(AuthSession::authContext);
    }

    private String sanitizeQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) return "null";
        return SENSITIVE_QUERY_PATTERN.matcher(uri.getQuery()).replaceAll("$1=***");
    }

    private String extractQueryParam(URI uri, String paramName) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            if (param.startsWith(paramName + "=")) {
                return java.net.URLDecoder.decode(param.substring(paramName.length() + 1),
                    java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private Optional<AuthContext> getAuthContextFromSessionToUser(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, AuthContext> entry : sessionToAuthContext.entrySet()) {
            AuthContext ctx = entry.getValue();
            if (ctx != null && userId.equals(ctx.getEmployeeId())) {
                return Optional.of(ctx);
            }
        }
        return Optional.empty();
    }


    public int getDepartmentConnectionCount(String department) {
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 推送产物消息到指定 session
     * 统一 schema：type=artifact，包含 name, url, mimeType 等字段
     */
    public void sendArtifactMessage(String sessionId, String name, String url, String mimeType, Map<String, Object> extra) {
        WebSocketSession targetSession = sessionIndex.get(sessionId);

        if (targetSession == null) {
            log.debug("Cannot send artifact message: session not found or closed, sessionId={}", sessionId);
            return;
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "artifact");
        msg.put("name", name);
        msg.put("url", url);
        msg.put("mimeType", mimeType != null ? mimeType : "application/octet-stream");
        msg.put("timestamp", Instant.now().toString());
        if (extra != null) {
            msg.putAll(extra);
        }

        try {
            sendJson(targetSession, msg);
        } catch (Exception e) {
            log.warn("Failed to send artifact message to session {}: {}", sessionId, e.getMessage());
        }
    }

    public Map<String, Integer> getAllConnectionCounts() {
        Map<String, Integer> counts = new HashMap<>();
        departmentChannels.forEach((dept, sessions) -> counts.put(dept, sessions.size()));
        return counts;
    }

    public record ChatMessage(
        String messageId,
        String userId,
        String userName,
        String department,
        String content,
        Instant timestamp,
        String metadata
    ) {
        public String type() { return "CHAT"; }
    }

    public record SystemMessage(
        String type,
        String userId,
        String userName,
        String department
    ) {}

    public record TypingIndicator(
        String userId,
        String userName,
        String department,
        boolean isTyping,
        Instant timestamp
    ) {
        public String type() { return "TYPING"; }
    }

    public record OnlineUser(
        String userId,
        String department,
        Instant connectedAt
    ) {}

    public record OnlineUsersList(
        String department,
        List<OnlineUser> users
    ) {
        public String type() { return "ONLINE_USERS"; }
    }

    public record BrainResponse(
        String messageId,
        String userId,
        String userName,
        String department,
        String content,
        Instant timestamp
    ) {
        public String type() { return "BRAIN_RESPONSE"; }
    }

    /**
     * 向指定部门广播原始 JSON（用于 PublicTaskEventPublisher 等自定义事件）
     */
    public void broadcastRawJson(String department, String rawJson) {
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            try {
                session.sendMessage(new TextMessage(rawJson));
            } catch (Exception e) {
                log.warn("Failed to send raw json to session {}: {}",
                    session.getId(), e.getMessage());
            }
        }
    }

    /**
     * 向所有部门广播原始 JSON（用于全企业事件）
     */
    public void broadcastToAllDepartments(String rawJson) {
        for (String dept : departmentChannels.keySet()) {
            broadcastRawJson(dept, rawJson);
        }
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        log.info("DepartmentWebSocketHandler shutting down, closing {} active sessions", sessionIndex.size());
        
        // 关闭心跳调度器
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 优雅关闭所有活跃会话
        sessionIndex.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close(new CloseStatus(1001, "Server shutting down"));
                }
            } catch (Exception e) {
                log.debug("Error closing session during shutdown: {}", e.getMessage());
            }
        });
        
        // 清理所有映射
        sessionIndex.clear();
        departmentChannels.clear();
        sessionToDepartment.clear();
        sessionToUser.clear();
        sessionLastActive.clear();
        sessionToAuthContext.clear();
        sessionConnectTime.clear();
        sessionAccessLevel.clear();
        sessionSendLocks.clear();
        recentAuthFailures.clear();
        authFailureReasons.clear();
        
        log.info("DepartmentWebSocketHandler shutdown complete");
    }

    /**
     * P14-B: 监听权限变更事件，实时更新/断连受影响的 WebSocket 连接。
     */
    @EventListener
    public void onPermissionChange(PermissionChangeEvent event) {
        String employeeId = event.getEmployeeId();
        AccessLevel newLevel = event.getNewLevel();
        log.info("P14-B: Permission change received for {}: {} -> {}, isDowngrade={}",
            employeeId, event.getOldLevel(), newLevel, event.isDowngrade());

        // 查找该用户的所有活跃 session
        for (Map.Entry<String, String> entry : sessionToUser.entrySet()) {
            String sessionId = entry.getKey();
            String userId = entry.getValue();

            if (userId.equals(employeeId)) {
                // 更新缓存的权限
                sessionAccessLevel.put(sessionId, newLevel);

                AuthContext ctx = sessionToAuthContext.get(sessionId);
                if (ctx != null) {
                    ctx.setAccessLevel(newLevel);
                }

                // 如果权限降级且用户无权访问当前部门，发送通知后断连
                if (event.isDowngrade()) {
                    String department = sessionToDepartment.get(sessionId);
                    if (department != null && !departmentAccessService.hasDepartmentAccess(
                        sessionToAuthContext.get(sessionId), department)) {
                        WebSocketSession wsSession = sessionIndex.get(sessionId);
                        if (wsSession != null && wsSession.isOpen()) {
                            try {
                                String msg = "{\"type\":\"permission_changed\",\"data\":{\"newLevel\":\"" +
                                    newLevel.name() + "\",\"reason\":\"Access level changed\"}}";
                                wsSession.sendMessage(new TextMessage(msg));
                                Thread.sleep(100); // 给客户端一点时间接收通知
                                wsSession.close(CloseStatus.POLICY_VIOLATION);
                                log.info("P14-B: Disconnected session {} for user {} due to permission downgrade",
                                    sessionId, employeeId);
                            } catch (Exception e) {
                                log.warn("P14-B: Failed to disconnect session {}: {}", sessionId, e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }
}
