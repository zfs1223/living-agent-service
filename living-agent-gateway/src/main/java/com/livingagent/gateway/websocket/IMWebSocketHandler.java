package com.livingagent.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.distributed.im.ImRedisService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.gateway.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * IM WebSocket 处理器
 *
 * 职责:
 * - 处理 /ws/im 即时通讯连接
 * - 管理用户多设备 session 索引
 * - 消息路由: SEND_MESSAGE / ACK / MARK_READ / RECALL_MESSAGE
 * - 心跳检测与频率限制
 *
 * 不负责:
 * - 消息持久化 (由 MessageService 处理)
 * - 部门大脑对话 (由 DepartmentWebSocketHandler 负责)
 */
@Component
public class IMWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(IMWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final ImRedisService imRedisService;

    private static final String LOCAL_SERVER_ID = "local";

    /** userId -> 多设备 session 集合 */
    private final Map<String, Set<WebSocketSession>> sessionIndex = new ConcurrentHashMap<>();

    /** sessionId -> userId */
    private final Map<String, String> sessionUserIndex = new ConcurrentHashMap<>();

    /** sessionId -> 最后活跃时间 */
    private final Map<String, Instant> sessionLastActive = new ConcurrentHashMap<>();

    /** 频率限制: sessionId -> 最近发送时间队列 */
    private final Map<String, Deque<Instant>> sendRateTracker = new ConcurrentHashMap<>();

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;
    private static final int RATE_LIMIT_PER_SECOND = 5;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 1;

    public IMWebSocketHandler(ObjectMapper objectMapper, @Lazy MessageService messageService, ImRedisService imRedisService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.imRedisService = imRedisService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AuthContext authContext = (AuthContext) session.getAttributes().get("authContext");
        if (authContext == null || authContext.getEmployeeId() == null) {
            log.warn("[IM] Connection rejected: no auth context, sessionId={}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = authContext.getEmployeeId();
        String sessionId = session.getId();

        sessionIndex.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionUserIndex.put(sessionId, userId);
        sessionLastActive.put(sessionId, Instant.now());

        // P91: 标记用户在线(Redis 心跳续期)
        imRedisService.markOnline(userId, LOCAL_SERVER_ID);

        log.info("[IM] Connection established: userId={}, sessionId={}", userId, sessionId);

        // 推送离线消息
        try {
            messageService.pushOfflineMessages(userId);
        } catch (Exception e) {
            log.warn("[IM] Failed to push offline messages for userId={}: {}", userId, e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String userId = sessionUserIndex.get(sessionId);

        if (userId == null) {
            log.warn("[IM] Message from unregistered session: sessionId={}", sessionId);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        sessionLastActive.put(sessionId, Instant.now());

        String payload = message.getPayload();
        log.debug("[IM] Received message: userId={}, sessionId={}, payloadLength={}",
            userId, sessionId, payload.length());

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.getOrDefault("type", "");

            switch (type.toUpperCase()) {
                case "PING" -> handlePing(session);
                case "SEND_MESSAGE" -> handleSendMessage(session, userId, msg);
                case "ACK" -> handleAck(userId, msg);
                case "MARK_READ" -> handleMarkRead(userId, msg);
                case "RECALL_MESSAGE" -> handleRecallMessage(userId, msg);
                default -> {
                    log.warn("[IM] Unknown message type: {} from userId={}", type, userId);
                    sendJson(session, Map.of("type", "error", "code", "UNKNOWN_TYPE",
                        "message", "未知的消息类型: " + type));
                }
            }
        } catch (Exception e) {
            log.warn("[IM] Failed to parse message from userId={}: {}", userId, e.getMessage());
            sendJson(session, Map.of("type", "error", "code", "PARSE_ERROR",
                "message", "消息格式错误"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        String userId = sessionUserIndex.remove(sessionId);
        sessionLastActive.remove(sessionId);
        sendRateTracker.remove(sessionId);

        if (userId != null) {
            Set<WebSocketSession> sessions = sessionIndex.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionIndex.remove(userId);
                    // P91: 用户所有设备都断开，标记离线
                    imRedisService.markOffline(userId);
                }
            }
            log.info("[IM] Connection closed: userId={}, sessionId={}, status={}", userId, sessionId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        String userId = sessionUserIndex.getOrDefault(sessionId, "unknown");
        log.error("[IM] Transport error: sessionId={}, userId={}, error={}",
            sessionId, userId, exception.getMessage());
    }

    // ---- 消息处理 ----

    private void handlePing(WebSocketSession session) throws Exception {
        sendJson(session, Map.of("type", "PONG", "timestamp", Instant.now().toString()));
    }

    private void handleSendMessage(WebSocketSession session, String userId, Map<String, Object> msg) {
        // P91: 背压检查(Redis 滑动窗口 5条/秒)
        if (!imRedisService.checkBackpressure(userId)) {
            try {
                sendJson(session, Map.of("type", "error", "code", "RATE_LIMITED",
                    "message", "发送过于频繁，请稍后再试"));
            } catch (Exception ignored) {}
            return;
        }

        // 频率限制检查(本地 session 级别)
        if (!checkRateLimit(session.getId())) {
            try {
                sendJson(session, Map.of("type", "error", "code", "RATE_LIMITED",
                    "message", "发送过于频繁，请稍后再试"));
            } catch (Exception ignored) {}
            return;
        }

        String recipientId = (String) msg.get("recipientId");
        String content = (String) msg.get("content");
        String type = (String) msg.getOrDefault("messageType", "TEXT");
        String replyToId = (String) msg.get("replyToId");
        String extra = (String) msg.get("extra");

        if (recipientId == null || recipientId.isBlank()) {
            try {
                sendJson(session, Map.of("type", "error", "code", "MISSING_RECIPIENT",
                    "message", "缺少接收者ID"));
            } catch (Exception ignored) {}
            return;
        }

        if (content == null || content.isBlank()) {
            try {
                sendJson(session, Map.of("type", "error", "code", "EMPTY_CONTENT",
                    "message", "消息内容不能为空"));
            } catch (Exception ignored) {}
            return;
        }

        try {
            MessageService.SendMessageRequest request = new MessageService.SendMessageRequest(
                recipientId, null, content, type, extra, replyToId
            );
            var savedMessage = messageService.sendMessage(userId, request);

            log.info("[IM] Message sent: from={}, to={}, messageId={}",
                userId, recipientId, savedMessage.getMessageId());
        } catch (Exception e) {
            log.error("[IM] Failed to send message: from={}, to={}, error={}",
                userId, recipientId, e.getMessage());
            try {
                sendJson(session, Map.of("type", "error", "code", "SEND_FAILED",
                    "message", "消息发送失败: " + e.getMessage()));
            } catch (Exception ignored) {}
        }
    }

    private void handleAck(String userId, Map<String, Object> msg) {
        String messageId = (String) msg.get("messageId");
        if (messageId == null || messageId.isBlank()) {
            log.warn("[IM] ACK missing messageId from userId={}", userId);
            return;
        }
        try {
            // P90: Redis ACK 由 MessageService.acknowledgeMessage 统一处理
            messageService.acknowledgeMessage(userId, messageId);
        } catch (Exception e) {
            log.warn("[IM] ACK failed: userId={}, messageId={}, error={}", userId, messageId, e.getMessage());
        }
    }

    private void handleMarkRead(String userId, Map<String, Object> msg) {
        String contactId = (String) msg.get("contactId");
        if (contactId == null || contactId.isBlank()) {
            log.warn("[IM] MARK_READ missing contactId from userId={}", userId);
            return;
        }
        try {
            messageService.markAsRead(userId, contactId);
            log.info("[IM] Marked as read: userId={}, contactId={}", userId, contactId);
        } catch (Exception e) {
            log.warn("[IM] Mark read failed: userId={}, contactId={}, error={}", userId, contactId, e.getMessage());
        }
    }

    private void handleRecallMessage(String userId, Map<String, Object> msg) {
        String messageId = (String) msg.get("messageId");
        if (messageId == null || messageId.isBlank()) {
            log.warn("[IM] RECALL_MESSAGE missing messageId from userId={}", userId);
            return;
        }
        try {
            messageService.recallMessage(messageId, userId);
            log.info("[IM] Message recalled: userId={}, messageId={}", userId, messageId);
        } catch (Exception e) {
            log.warn("[IM] Recall message failed: userId={}, messageId={}, error={}",
                userId, messageId, e.getMessage());
        }
    }

    // ---- 公共方法 ----

    /**
     * 向用户所有设备推送消息
     * @return 是否至少一个设备推送成功
     */
    public boolean pushToUser(String userId, Object payload) {
        Set<WebSocketSession> sessions = sessionIndex.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("[IM] User offline, cannot push: userId={}", userId);
            return false;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[IM] Failed to serialize push payload for userId={}: {}", userId, e.getMessage());
            return false;
        }

        boolean anySuccess = false;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json));
                    }
                    anySuccess = true;
                } catch (IOException e) {
                    log.warn("[IM] Failed to push to session sessionId={}: {}", session.getId(), e.getMessage());
                }
            }
        }
        return anySuccess;
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        Set<WebSocketSession> sessions = sessionIndex.get(userId);
        if (sessions == null || sessions.isEmpty()) return false;
        return sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    // ---- 心跳检测 ----

    @Scheduled(fixedRate = 10000)
    public void checkHeartbeat() {
        Instant timeout = Instant.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);
        List<String> expiredSessions = new ArrayList<>();
        Set<String> activeUserIds = new HashSet<>();

        sessionLastActive.forEach((sessionId, lastActive) -> {
            if (lastActive.isBefore(timeout)) {
                expiredSessions.add(sessionId);
            } else {
                // P91: 收集活跃用户，续期 Redis 在线状态
                String userId = sessionUserIndex.get(sessionId);
                if (userId != null) {
                    activeUserIds.add(userId);
                }
            }
        });

        // P91: 为活跃用户续期在线状态(心跳续期)
        for (String userId : activeUserIds) {
            imRedisService.markOnline(userId, LOCAL_SERVER_ID);
        }

        for (String sessionId : expiredSessions) {
            String userId = sessionUserIndex.get(sessionId);
            log.info("[IM] Heartbeat timeout: userId={}, sessionId={}", userId, sessionId);

            Set<WebSocketSession> sessions = userId != null ? sessionIndex.get(userId) : null;
            if (sessions != null) {
                sessions.stream()
                    .filter(s -> s.getId().equals(sessionId))
                    .findFirst()
                    .ifPresent(s -> {
                        try {
                            s.close(new CloseStatus(4000, "HEARTBEAT_TIMEOUT"));
                        } catch (Exception e) {
                            log.debug("[IM] Failed to close timed out session: {}", e.getMessage());
                        }
                    });
            }
        }
    }

    // ---- 频率限制 ----

    private boolean checkRateLimit(String sessionId) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = sendRateTracker.computeIfAbsent(sessionId, k -> new ConcurrentLinkedDeque<>());

        // 清理过期记录
        Instant windowStart = now.minusSeconds(RATE_LIMIT_WINDOW_SECONDS);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= RATE_LIMIT_PER_SECOND) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    // ---- 工具方法 ----

    private void sendJson(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        }
    }
}
