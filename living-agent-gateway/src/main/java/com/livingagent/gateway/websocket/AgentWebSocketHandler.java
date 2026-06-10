package com.livingagent.gateway.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeOrigin;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.service.AgentService;

import jakarta.annotation.PostConstruct;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final UnifiedAuthService authService;
    private final EmployeeService employeeService;
    private final AccessGateService accessGateService;
    private final Map<String, WebSocketSession> sessions;
    private final Map<String, String> sessionToAgent;
    private final Map<String, String> sessionToUser;
    private final Map<String, AccessLevel> sessionAccessLevel;
    private final Map<String, String> sessionDepartment;
    
    public AgentWebSocketHandler(ObjectMapper objectMapper, AgentService agentService, UnifiedAuthService authService, EmployeeService employeeService, AccessGateService accessGateService) {
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.authService = authService;
        this.employeeService = employeeService;
        this.accessGateService = accessGateService;
        this.sessions = new ConcurrentHashMap<>();
        this.sessionToAgent = new ConcurrentHashMap<>();
        this.sessionToUser = new ConcurrentHashMap<>();
        this.sessionAccessLevel = new ConcurrentHashMap<>();
        this.sessionDepartment = new ConcurrentHashMap<>();
    }

    @PostConstruct
    void init() {
        agentService.setAgentWebSocketHandler(this);
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();

        Optional<AuthContext> ctxOpt = getAuthContext(session);
        if (ctxOpt.isEmpty()) {
            log.warn("Agent WebSocket connection without auth");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        AuthContext ctx = ctxOpt.get();
        String userId = ctx.getEmployeeId() != null ? ctx.getEmployeeId() : "visitor_" + sessionId;
        AccessLevel accessLevel = ctx.getAccessLevel() != null ? ctx.getAccessLevel() : AccessLevel.CHAT_ONLY;
        String department = ctx.getDepartment();

        sessions.put(sessionId, session);
        sessionToUser.put(sessionId, userId);
        sessionAccessLevel.put(sessionId, accessLevel);
        if (department != null) {
            sessionDepartment.put(sessionId, department);
        }

        String agentId = extractAgentId(session.getUri());
        if (agentId != null) {
            sessionToAgent.put(sessionId, agentId);

            Optional<Employee> targetOpt = employeeService.getEmployee(agentId);
            if (targetOpt.isEmpty()) {
                log.warn("Agent WebSocket connection rejected, agent not found: {}", agentId);
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Agent not found"));
                return;
            }

            Employee target = targetOpt.get();
            if (target.getOrigin() == EmployeeOrigin.FIXED) {
                log.warn("Fixed employee direct chat rejected: sessionId={}, agentId={}, userId={}", sessionId, agentId, userId);
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Fixed employee direct chat is not allowed"));
                return;
            }

            String targetType = "brain";
            String targetName = department != null ? department : "MainBrain";
            if (!accessGateService.canRoute(userId, targetType, targetName)) {
                log.warn("WebSocket access denied before routing: sessionId={}, userId={}, targetType={}, targetName={}",
                    sessionId, userId, targetType, targetName);
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Access denied before routing"));
                return;
            }
        }

        // 检查是否有可恢复的挂起会话（断线重连）
        String reconnectSessionId = extractQueryParam(session.getUri(), "sessionId");
        boolean resumed = false;
        if (reconnectSessionId != null && !reconnectSessionId.isBlank()) {
            resumed = agentService.resumeSession(reconnectSessionId);
            if (resumed) {
                log.info("Agent WebSocket reconnected: resumed session {}, new WebSocket sessionId={}", reconnectSessionId, sessionId);
                // 将恢复的会话映射到新的 WebSocket session
                sessions.put(sessionId, session);
                sessionToUser.put(sessionId, userId);
                sessionAccessLevel.put(sessionId, accessLevel);
                if (department != null) {
                    sessionDepartment.put(sessionId, department);
                }

                // 发送重连成功消息，附带对话历史
                List<Map<String, String>> history = agentService.getSuspendedSessionHistory(reconnectSessionId);
                Map<String, Object> reconnectMsg = new HashMap<>();
                reconnectMsg.put("type", "reconnected");
                reconnectMsg.put("sessionId", reconnectSessionId);
                reconnectMsg.put("newSessionId", sessionId);
                reconnectMsg.put("historyCount", history != null ? history.size() : 0);
                if (history != null && !history.isEmpty()) {
                    reconnectMsg.put("history", history);
                }
                sendMessage(session, reconnectMsg);
            }
        }

        if (!resumed) {
            log.info("WebSocket connection established: sessionId={}, agentId={}, userId={}, accessLevel={}, department={}",
                sessionId, agentId != null ? agentId : "default", userId, accessLevel, department);

            agentService.startSession(sessionId, accessLevel, department);
            agentService.attachUserIdentity(sessionId, userId);
        }

        sendMessage(session, Map.of(
            "type", "connected",
            "sessionId", sessionId,
            "agentId", agentId != null ? agentId : "",
            "userId", userId,
            "message", resumed ? "Connection re-established" : "Connection established"
        ));
    }
    
    private String extractAgentId(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            if (param.startsWith("agentId=")) {
                return java.net.URLDecoder.decode(param.substring(8), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message: sessionId={}, payload={}", session.getId(), payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            
            String type = (String) request.getOrDefault("type", "unknown");
            
            switch (type) {
                case "text" -> handleTextMessage(session, request);
                case "audio" -> handleAudioMessage(session, request);
                case "audio_full" -> handleAudioFullChainMessage(session, request);
                case "control" -> handleControlMessage(session, request);
                case "ping" -> sendPong(session);
                case "abort" -> handleAbort(session);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    private void handleTextMessage(WebSocketSession session, Map<String, Object> request) {
        String text = (String) request.get("text");
        String channel = (String) request.getOrDefault("channel", "default");
        
        if (text == null || text.isEmpty()) {
            sendError(session, "Missing text content");
            return;
        }
        
        agentService.processTextAsync(session.getId(), text, channel)
            .thenAccept(response -> sendMessage(session, convertToFrontendFormat(response)))
            .exceptionally(e -> {
                log.error("Error processing text message", e);
                sendError(session, "Processing error: " + e.getMessage());
                return null;
            });
    }
    
    private Map<String, Object> convertToFrontendFormat(Map<String, Object> response) {
        String type = (String) response.getOrDefault("type", "response");

        if ("response".equals(type)) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "done");
            msg.put("content", response.get("text"));
            msg.put("model", response.getOrDefault("model", "unknown"));
            if (response.containsKey("intent")) msg.put("intent", response.get("intent"));
            if (response.containsKey("neuron")) msg.put("neuron", response.get("neuron"));
            if (response.containsKey("accessLevel")) msg.put("accessLevel", response.get("accessLevel"));
            // 统一 schema：添加 department 和 executionId 字段（可为 null）
            msg.put("department", response.getOrDefault("department", null));
            msg.put("executionId", response.getOrDefault("executionId", null));
            return msg;
        }

        if ("error".equals(type)) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "error");
            msg.put("code", response.getOrDefault("code", "AGENT_ERROR"));
            msg.put("message", response.getOrDefault("message", "未知错误"));
            return msg;
        }

        return response;
    }
    
    private void sendPong(WebSocketSession session) {
        sendMessage(session, Map.of("type", "pong", "timestamp", String.valueOf(System.currentTimeMillis())));
    }
    
    private void handleAbort(WebSocketSession session) {
        log.info("Abort requested for session: {}", session.getId());
        sendMessage(session, Map.of("type", "aborted", "sessionId", session.getId()));
    }
    
    private void handleAudioMessage(WebSocketSession session, Map<String, Object> request) {
        String audioData = (String) request.get("audio");
        String format = (String) request.getOrDefault("format", "wav");
        
        if (audioData == null || audioData.isEmpty()) {
            sendError(session, "Missing audio data");
            return;
        }
        
        agentService.processAudioAsync(session.getId(), audioData, format)
            .thenAccept(response -> sendMessage(session, convertToFrontendFormat(response)))
            .exceptionally(e -> {
                log.error("Error processing audio message", e);
                sendError(session, "Audio processing error: " + e.getMessage());
                return null;
            });
    }
    
    private void handleAudioFullChainMessage(WebSocketSession session, Map<String, Object> request) {
        String audioData = (String) request.get("audio");
        
        if (audioData == null || audioData.isEmpty()) {
            sendError(session, "Missing audio data");
            return;
        }
        
        log.info("[{}] Processing full audio chain", session.getId());
        
        agentService.processAudioFullChain(session.getId(), audioData)
            .thenAccept(response -> {
                sendMessage(session, convertToFrontendFormat(response));
                log.info("[{}] Full audio chain completed", session.getId());
            })
            .exceptionally(e -> {
                log.error("[{}] Error processing full audio chain", session.getId(), e);
                sendError(session, "Full chain processing error: " + e.getMessage());
                return null;
            });
    }
    
    private void handleControlMessage(WebSocketSession session, Map<String, Object> request) {
        String action = (String) request.get("action");
        
        switch (action) {
            case "start_session" -> {
                AccessLevel level = sessionAccessLevel.getOrDefault(session.getId(), AccessLevel.CHAT_ONLY);
                String dept = sessionDepartment.get(session.getId());
                agentService.startSession(session.getId(), level, dept);
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "session_started",
                    "sessionId", session.getId()
                ));
            }
            case "end_session" -> {
                agentService.endSession(session.getId());
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "session_ended",
                    "sessionId", session.getId()
                ));
            }
            case "get_status" -> {
                Map<String, Object> status = agentService.getStatus();
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "status",
                    "data", status
                ));
            }
            default -> sendError(session, "Unknown control action: " + action);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionToAgent.remove(sessionId);
        sessionToUser.remove(sessionId);
        sessionAccessLevel.remove(sessionId);
        sessionDepartment.remove(sessionId);
        // 挂起会话而非直接销毁，等待5分钟内重连
        agentService.suspendSession(sessionId);
        log.info("WebSocket connection closed: sessionId={}, status={}, session suspended for reconnection", sessionId, status);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
    }
    
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }
    
    private void sendError(WebSocketSession session, String error) {
        sendError(session, "AGENT_ERROR", error);
    }

    private void sendError(WebSocketSession session, String code, String error) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "error");
        msg.put("code", code);
        msg.put("message", error);
        sendMessage(session, msg);
    }
    
    public void broadcastToSession(String sessionId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    /**
     * 推送产物消息到指定 session
     * 统一 schema：type=artifact，包含 name, url, mimeType 等字段
     */
    public void sendArtifactMessage(String sessionId, String name, String url, String mimeType, Map<String, Object> extra) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.debug("Cannot send artifact message: session not found or closed, sessionId={}", sessionId);
            return;
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "artifact");
        msg.put("name", name);
        msg.put("url", url);
        msg.put("mimeType", mimeType != null ? mimeType : "application/octet-stream");
        msg.put("timestamp", java.time.Instant.now().toString());
        if (extra != null) {
            msg.putAll(extra);
        }
        sendMessage(session, msg);
    }

    /**
     * 推送进度消息到指定 session
     * 统一 schema：type=progress，包含 stage, message, progress(0-100) 等字段
     */
    public void sendProgressMessage(String sessionId, String stage, String message, int progress, Map<String, Object> extra) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.debug("Cannot send progress message: session not found or closed, sessionId={}", sessionId);
            return;
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "progress");
        msg.put("stage", stage);
        msg.put("message", message != null ? message : "");
        msg.put("progress", Math.max(0, Math.min(100, progress)));
        msg.put("timestamp", java.time.Instant.now().toString());
        if (extra != null) {
            msg.putAll(extra);
        }
        sendMessage(session, msg);
        log.debug("Progress pushed: sessionId={}, stage={}, progress={}%", sessionId, stage, progress);
    }
    
    public int getActiveSessionCount() {
        return sessions.size();
    }

    private Optional<AuthContext> getAuthContext(WebSocketSession session) {
        String token = null;

        // 优先从 Sec-WebSocket-Protocol 头获取 token（更安全，不暴露在 URL 中）
        String protocol = session.getHandshakeHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocol != null && !protocol.isBlank()) {
            // 格式：bearer.{token} 或直接为 token
            for (String p : protocol.split(",")) {
                String trimmed = p.trim();
                if (trimmed.startsWith("bearer.")) {
                    token = trimmed.substring(7);
                    break;
                }
            }
        }

        // 其次从 Authorization 头获取
        if (token == null || token.isBlank()) {
            String authorization = session.getHandshakeHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }

        // 最后从 URL 查询参数获取（向后兼容，但不推荐）
        if (token == null || token.isBlank()) {
            token = extractQueryParam(session.getUri(), "token");
            if (token != null && !token.isBlank()) {
                log.debug("Token passed via URL query parameter (deprecated, use Sec-WebSocket-Protocol instead)");
            }
        }

        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<AuthSession> sessionOpt = authService.validateSession(token);
        return sessionOpt.map(AuthSession::authContext);
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
}
