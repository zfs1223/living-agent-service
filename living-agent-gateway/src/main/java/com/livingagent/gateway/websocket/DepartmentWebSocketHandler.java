package com.livingagent.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
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

@Component
public class DepartmentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DepartmentWebSocketHandler.class);

    private final UnifiedAuthService authService;
    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> departmentChannels = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToDepartment = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, Instant> sessionConnectTime = new ConcurrentHashMap<>();

    public DepartmentWebSocketHandler(UnifiedAuthService authService) {
        this.authService = authService;
        this.objectMapper = new ObjectMapper();
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
            log.warn("WebSocket connection without auth");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        
        AuthContext ctx = ctxOpt.get();
        if (!hasDepartmentAccess(ctx, department)) {
            log.warn("Department WebSocket access denied: user={}, dept={}", 
                ctx.getEmployeeId(), department);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = ctx.getEmployeeId() != null ? ctx.getEmployeeId() : "visitor_" + session.getId();
        
        departmentChannels.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(session);
        sessionToDepartment.put(session.getId(), department);
        sessionToUser.put(session.getId(), userId);
        sessionConnectTime.put(session.getId(), Instant.now());

        log.info("WebSocket connected: user={}, dept={}, sessionId={}", 
            userId, department, session.getId());

        broadcastSystemMessage(department, 
            new SystemMessage("USER_JOINED", userId, ctx.getName(), department));
        
        sendOnlineUsers(department);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String department = sessionToDepartment.get(session.getId());
        if (department == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = sessionToUser.get(session.getId());
        String payload = message.getPayload();

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
        
        ChatMessage chatMessage = new ChatMessage(
            UUID.randomUUID().toString(),
            userId,
            userName,
            department,
            (String) msg.getOrDefault("content", ""),
            Instant.now(),
            (String) msg.getOrDefault("metadata", "")
        );

        broadcast(department, chatMessage);
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
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String department = sessionToDepartment.remove(session.getId());
        String userId = sessionToUser.remove(session.getId());
        sessionConnectTime.remove(session.getId());

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
        log.error("WebSocket transport error: sessionId={}, error={}", 
            session.getId(), exception.getMessage());
    }

    private void broadcast(String department, Object message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        TextMessage textMessage = new TextMessage(json);

        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Failed to send message to session {}: {}", 
                            session.getId(), e.getMessage());
                    }
                }
            }
        }
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
            return path.substring("/ws/dept/".length());
        }
        if (path.startsWith("/ws/chairman")) {
            return "chairman";
        }
        if (path.startsWith("/ws/public")) {
            return "public";
        }
        
        return null;
    }

    private Optional<AuthContext> getAuthContext(WebSocketSession session) {
        String authorization = session.getHandshakeHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    private Optional<AuthContext> getAuthContextFromSessionToUser(String userId) {
        return Optional.empty();
    }

    private boolean hasDepartmentAccess(AuthContext ctx, String department) {
        if ("public".equals(department)) {
            return true;
        }
        
        if ("chairman".equals(department)) {
            return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
        }
        
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) {
            return true;
        }
        
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) {
            return false;
        }
        
        String userDept = ctx.getDepartment();
        return userDept != null && userDept.equalsIgnoreCase(department);
    }

    public int getDepartmentConnectionCount(String department) {
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        return sessions != null ? sessions.size() : 0;
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
}
