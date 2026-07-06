package com.livingagent.gateway.websocket;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;

import jakarta.servlet.http.HttpServletRequest;

/**
 * WebSocket 握手拦截器
 * 在握手阶段验证 token，拒绝未认证连接，节省服务器资源
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);

    private final UnifiedAuthService authService;

    public AuthHandshakeInterceptor(UnifiedAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("[WS Handshake] Rejected: not a servlet request");
            return false;
        }

        HttpServletRequest httpReq = servletRequest.getServletRequest();
        String token = extractToken(httpReq);

        if (token == null || token.isBlank()) {
            log.warn("[WS Handshake] Rejected: no token provided, uri={}", httpReq.getRequestURI());
            return false;
        }

        try {
            Optional<AuthSession> sessionOpt = authService.validateSession(token);
            if (sessionOpt.isEmpty() || sessionOpt.get().authContext() == null) {
                log.warn("[WS Handshake] Rejected: invalid token");
                return false;
            }

            AuthSession authSession = sessionOpt.get();

            AuthContext authContext = authSession.authContext();
            attributes.put("authContext", authContext);
            attributes.put("token", token);
            
            log.debug("[WS Handshake] Success: userId={}, accessLevel={}", 
                authContext.getEmployeeId(), authContext.getAccessLevel());
            return true;
        } catch (Exception e) {
            log.warn("[WS Handshake] Rejected: token validation failed, error={}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // 如果握手成功，返回接受的 subprotocol（客户端使用 bearer.<token> 格式）
        if (exception == null && response instanceof org.springframework.http.server.ServletServerHttpResponse servletResponse) {
            String protocol = ((ServletServerHttpRequest) request).getServletRequest().getHeader("Sec-WebSocket-Protocol");
            if (protocol != null && !protocol.isBlank()) {
                for (String p : protocol.split(",")) {
                    String trimmed = p.trim();
                    if (trimmed.startsWith("bearer.")) {
                        // 返回接受的 subprotocol
                        servletResponse.getServletResponse().setHeader("Sec-WebSocket-Protocol", trimmed);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 从请求中提取 token
     * 优先级：Sec-WebSocket-Protocol > Authorization > URL 参数
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 从 Sec-WebSocket-Protocol 头获取（更安全）
        String protocol = request.getHeader("Sec-WebSocket-Protocol");
        if (protocol != null && !protocol.isBlank()) {
            for (String p : protocol.split(",")) {
                String trimmed = p.trim();
                if (trimmed.startsWith("bearer.")) {
                    return trimmed.substring(7);
                }
            }
        }

        // 2. 从 Authorization 头获取
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 3. 从 URL 查询参数获取（向后兼容）
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            log.debug("[WS Handshake] Token passed via URL parameter (deprecated)");
            return token;
        }

        return null;
    }
}
