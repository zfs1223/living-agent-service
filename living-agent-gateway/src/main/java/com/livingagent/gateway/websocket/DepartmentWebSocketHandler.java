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
import com.livingagent.core.session.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000;
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
                                       DepartmentAccessService departmentAccessService) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.departmentChatService = departmentChatService;
        this.connectionRegistry = connectionRegistry;
        this.departmentAccessService = departmentAccessService;
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
                for (Map.Entry<String, Instant> entry : sessionLastActive.entrySet()) {
                    String sessionId = entry.getKey();
                    Instant lastActive = entry.getValue();
                    if (lastActive != null && now.toEpochMilli() - lastActive.toEpochMilli() > HEARTBEAT_TIMEOUT_MS) {
                        WebSocketSession session = sessionIndex.get(sessionId);
                        if (session != null && session.isOpen()) {
                            log.warn("Closing zombie WebSocket session: sessionId={}, idleMs={}",
                                sessionId, now.toEpochMilli() - lastActive.toEpochMilli());
                            try {
                                session.close(CloseStatus.SERVER_ERROR);
                            } catch (Exception e) {
                                log.debug("Failed to close zombie session: {}", e.getMessage());
                            }
                        }
                    }
                }

                // Cleanup expired recentAuthFailures entries
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

        if (sessionIndex.size() >= MAX_GLOBAL_CONNECTIONS) {
            log.warn("WebSocket connection rejected: global limit reached ({})", MAX_GLOBAL_CONNECTIONS);
            session.close(new CloseStatus(4029, "GLOBAL_CONNECTION_LIMIT"));
            return;
        }
        Set<WebSocketSession> deptSessions = departmentChannels.get(department);
        if (deptSessions != null && deptSessions.size() >= MAX_DEPARTMENT_CONNECTIONS) {
            log.warn("WebSocket connection rejected: department {} limit reached ({})", department, MAX_DEPARTMENT_CONNECTIONS);
            session.close(new CloseStatus(4030, "DEPARTMENT_CONNECTION_LIMIT"));
            return;
        }

        String userId = ctx.getEmployeeId() != null ? ctx.getEmployeeId() : "visitor_" + session.getId();
        AccessLevel accessLevel = ctx.getAccessLevel() != null ? ctx.getAccessLevel() : AccessLevel.CHAT_ONLY;
        
        departmentChannels.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(session);
        sessionToDepartment.put(session.getId(), department);
        sessionToUser.put(session.getId(), userId);
        sessionIndex.put(session.getId(), session);
        sessionLastActive.put(session.getId(), Instant.now());
        sessionToAuthContext.put(session.getId(), ctx);
        sessionConnectTime.put(session.getId(), Instant.now());
        sessionAccessLevel.put(session.getId(), accessLevel);
        sessionSendLocks.computeIfAbsent(session.getId(), id -> new ReentrantLock());

        connectionRegistry.register(session.getId(), userId, ctx.getTenantId(),
            new ConnectionContext(
                session.getId(), userId, ctx.getTenantId(), ctx.getDepartment(),
                null, null, null, null, null,
                Instant.now(), Instant.now(), Map.of()
            ));

        log.info("WebSocket connected: user={}, dept={}, sessionId={}, accessLevel={}",
            userId, department, session.getId(), accessLevel);

        // === 增强重连逻辑：支持断线重连 ===
        String reconnectConversationId = extractQueryParam(session.getUri(), "conversationId");
        if (reconnectConversationId != null && !reconnectConversationId.isBlank()) {
            connectionRegistry.bindConversation(session.getId(), reconnectConversationId);
            log.info("WebSocket reconnected with conversationId: user={}, convId={}", userId, reconnectConversationId);

            try {
                departmentChatService.bindSessionToConversation(session.getId(), reconnectConversationId);
            } catch (Exception bindEx) {
                log.debug("Failed to bind session to conversation for receipt routing: {}", bindEx.getMessage());
            }

            try {
                // 发送对话历史
                List<?> history = departmentChatService.getConversationHistory(reconnectConversationId, 20);
                if (!history.isEmpty()) {
                    Map<String, Object> reconnectMsg = new HashMap<>();
                    reconnectMsg.put("type", "reconnected");
                    reconnectMsg.put("conversationId", reconnectConversationId);
                    reconnectMsg.put("historyCount", history.size());
                    reconnectMsg.put("history", history);
                    sendJson(session, reconnectMsg);
                }
                
                // 补发断线期间的待处理事件（如果支持 PersistentConnectionRegistry）
                if (connectionRegistry instanceof PersistentConnectionRegistry persistent) {
                    List<EventQueueService.PendingEvent> pendingEvents = persistent.getPendingEvents(session.getId());
                    if (!pendingEvents.isEmpty()) {
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
                }
            } catch (Exception e) {
                log.debug("Failed to send reconnection data: {}", e.getMessage());
            }
        }

        broadcastSystemMessage(department, new SystemMessage("USER_JOINED", userId, ctx.getName(), department));
        sendOnlineUsers(department);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        sessionLastActive.put(session.getId(), Instant.now());
        String department = sessionToDepartment.get(session.getId());
        if (department == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = sessionToUser.get(session.getId());
        String payload = message.getPayload();

        connectionRegistry.updateLastActivity(session.getId());

        log.debug("WebSocket message: user={}, dept={}, message={}", 
            userId, department, payload.length() > 100 ? payload.substring(0, 100) + "..." : payload);

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.getOrDefault("type", "CHAT");
            
            switch (type.toUpperCase()) {
                case "CHAT" -> handleChatMessage(session, department, userId, msg);
                case "TYPING" -> handleTypingIndicator(department, userId, msg);
                case "PING" -> sendPong(session);
                default -> handleChatMessage(session, department, userId, msg);
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", e.getMessage());
            handleChatMessage(session, department, userId, 
                Map.of("content", payload, "timestamp", Instant.now().toString()));
        }
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
            log.debug("Skip brain processing for public channel, sessionId={}", session.getId());
            return;
        }

        String sessionId = session.getId();
        log.info("processWithBrain: dept={}, userId={}, sessionId={}, contentLength={}", 
            department, userId, sessionId, content.length());

        // 发送思考指示器
        try {
            Map<String, Object> thinkingMsg = Map.of(
                "type", "thinking",
                "content", ""
            );
            sendJson(session, thinkingMsg);
            log.info("Thinking indicator sent for dept={}", department);
        } catch (Exception e) {
            log.warn("Failed to send thinking indicator: {}", e.getMessage());
        }

        // 异步处理部门文本对话：直接进入部门大脑，不经过 AgentService/Qwen3Neuron/chat。
        String requestId = UUID.randomUUID().toString();
        String brainName = com.livingagent.core.security.Department.mapDepartmentToBrain(department);
        Optional<com.livingagent.core.brain.Brain> brainOpt = departmentChatService.getBrainByDepartment(department);
        if (brainOpt.isEmpty()) {
            try {
                if (session.isOpen()) {
                    Map<String, Object> errorMsg = Map.of(
                        "type", "error",
                        "code", "NO_BRAIN",
                        "message", "部门大脑未注册"
                    );
                    sendJson(session, errorMsg);
                }
            } catch (Exception e) {
                log.warn("Failed to send no brain message: {}", e.getMessage());
            }
            return;
        }
        
        // 提取 executionId（如果存在）
        String executionId = null;
        
        connectionRegistry.updateLastActivity(sessionId);

        departmentChatService.processDepartmentBrainAsync(
                requestId, department, brainName, brainOpt.get(), content, sessionId, userId, userId, conversationId)
            .thenAccept(chatResult -> {
                try {
                    if (!session.isOpen()) return;
                    
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
                        if (executionId != null) {
                            doneMsg.put("executionId", executionId);
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

                        sendJson(session, doneMsg);

                        // 广播给其他用户（除了发送者）
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
                        // 错误响应：使用统一的错误码
                        Map<String, Object> errorMsg = Map.of(
                            "type", "error",
                            "code", chatResult.status(),
                            "message", chatResult.reason() != null ? chatResult.reason() : "处理失败"
                        );
                        sendJson(session, errorMsg);
                    }
                } catch (Exception e) {
                    log.error("Failed to process brain result: {}", e.getMessage());
                }
            })
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
            "type", "PONG",
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
        sessionToAuthContext.remove(sessionId);
        sessionConnectTime.remove(sessionId);
        sessionAccessLevel.remove(sessionId);
        sessionSendLocks.remove(sessionId);
        authFailureReasons.remove(sessionId);

        connectionRegistry.unregister(sessionId);

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
        log.info("DepartmentWebSocketHandler shutdown complete");
    }
}
