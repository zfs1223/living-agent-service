package com.livingagent.gateway.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.websocket.WindowsAutomationClientGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Windows 自动化客户端网关实现
 *
 * 维护 clientId → WebSocketSession 映射，负责：
 * 1. 在 WebSocket 连接建立/关闭时注册/注销客户端
 * 2. 将后端的 Windows 自动化操作通过 WebSocket 转发到桌面端
 * 3. 接收桌面端的响应并完成等待中的 Future
 *
 * 通信协议（与桌面端 ws-client.ts 对齐）：
 * - 后端 → 桌面端：{"type":"win_automation_call","data":{"id":<long>,"operation":<string>,"args":<object>}}
 * - 桌面端 → 后端：{"type":"win_automation_response","data":{"id":<long>,"success":<bool>,"result":<any>,"error":<string>}}
 *
 * 详细设计：docs/WINDOWS_MCP_INTEGRATION_PLAN.md §3.2、§5.1
 */
@Component
public class WindowsAutomationClientGatewayImpl implements WindowsAutomationClientGateway {

    private static final Logger log = LoggerFactory.getLogger(WindowsAutomationClientGatewayImpl.class);
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /** clientId → WebSocketSession 映射 */
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();

    /** requestId → 等待响应的 Future 映射 */
    private final Map<Long, CompletableFuture<WinAutomationResponse>> pendingRequests = new ConcurrentHashMap<>();

    /** 请求 ID 自增器 */
    private final AtomicLong requestIdCounter = new AtomicLong(System.currentTimeMillis());

    private final ObjectMapper objectMapper;

    @Autowired
    public WindowsAutomationClientGatewayImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerClient(String clientId, String sessionId) {
        // 注意：此方法由 AgentWebSocketHandler 调用，但 session 对象需要通过 registerSession 传入
        // 这里仅记录日志，实际 session 注册使用 registerSession
        log.debug("Client registered: clientId={}, sessionId={}", clientId, sessionId);
    }

    /**
     * 注册客户端 WebSocket Session（由 AgentWebSocketHandler 调用）
     *
     * @param clientId 客户端唯一标识
     * @param session  WebSocket 会话
     */
    @Override
    public void registerSession(String clientId, Object session) {
        if (clientId == null || clientId.isBlank() || session == null) {
            return;
        }
        if (session instanceof WebSocketSession wsSession) {
            clientSessions.put(clientId, wsSession);
            log.info("Windows automation client session registered: clientId={}, sessionId={}",
                clientId, wsSession.getId());
        }
    }

    /**
     * 注销客户端 WebSocket Session（由 AgentWebSocketHandler 调用）
     *
     * @param clientId 客户端唯一标识
     */
    @Override
    public void unregisterSession(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        WebSocketSession removed = clientSessions.remove(clientId);
        if (removed != null) {
            log.info("Windows automation client session unregistered: clientId={}, sessionId={}",
                clientId, removed.getId());
        }
    }

    @Override
    public void unregisterClient(String clientId) {
        unregisterSession(clientId);
    }

    @Override
    public boolean isClientOnline(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        WebSocketSession session = clientSessions.get(clientId);
        return session != null && session.isOpen();
    }

    @Override
    public CompletableFuture<WinAutomationResponse> sendOperation(
        String clientId, String operation, Map<String, Object> args) {

        CompletableFuture<WinAutomationResponse> future = new CompletableFuture<>();

        // 1. 查找客户端 session
        WebSocketSession session = clientSessions.get(clientId);
        if (session == null || !session.isOpen()) {
            future.complete(WinAutomationResponse.fail("客户端未连接: " + clientId));
            return future;
        }

        // 2. 生成唯一 requestId
        long requestId = requestIdCounter.incrementAndGet();
        pendingRequests.put(requestId, future);

        // 3. 构造消息
        Map<String, Object> data = new HashMap<>();
        data.put("id", requestId);
        data.put("operation", operation);
        data.put("args", args != null ? args : Map.of());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "win_automation_call");
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        // 4. 发送
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
            log.info("[WinAutomationGateway] Sent operation: requestId={}, clientId={}, operation={}",
                requestId, clientId, operation);
        } catch (JsonProcessingException e) {
            pendingRequests.remove(requestId);
            future.complete(WinAutomationResponse.fail("消息序列化失败: " + e.getMessage()));
            return future;
        } catch (IOException e) {
            pendingRequests.remove(requestId);
            log.error("[WinAutomationGateway] Failed to send message: clientId={}", clientId, e);
            future.complete(WinAutomationResponse.fail("发送消息失败: " + e.getMessage()));
            return future;
        }

        // 5. 超时处理
        future.orTimeout(DEFAULT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .whenComplete((resp, ex) -> {
                if (ex != null) {
                    pendingRequests.remove(requestId);
                    log.warn("[WinAutomationGateway] Request timeout: requestId={}, clientId={}",
                        requestId, clientId);
                }
            });

        return future;
    }

    @Override
    public void handleResponse(long requestId, boolean success, Object result, String error) {
        CompletableFuture<WinAutomationResponse> future = pendingRequests.remove(requestId);
        if (future == null) {
            log.warn("[WinAutomationGateway] No pending request for requestId={}", requestId);
            return;
        }

        if (success) {
            future.complete(WinAutomationResponse.ok(result));
        } else {
            future.complete(WinAutomationResponse.fail(error));
        }

        log.info("[WinAutomationGateway] Response handled: requestId={}, success={}", requestId, success);
    }

    /**
     * 清理指定 clientId 的所有挂起请求（客户端断开时调用）
     */
    public void failPendingRequests(String clientId, String reason) {
        // 当前实现中 pendingRequests 以 requestId 为 key，无法直接按 clientId 清理
        // 客户端断开时，挂起的请求会通过超时机制自动失败
        log.info("[WinAutomationGateway] Client disconnected, pending requests will timeout: clientId={}, reason={}",
            clientId, reason);
    }
}
