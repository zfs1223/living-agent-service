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

    private static final String PUBLIC_PATH = "/ws/public";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.warn("[WS Handshake] Rejected: not a servlet request");
            return false;
        }

        HttpServletRequest httpReq = servletRequest.getServletRequest();
        String uri = httpReq.getRequestURI();

        // /ws/public 通道允许匿名访问
        if (uri.startsWith(PUBLIC_PATH)) {
            String token = extractToken(httpReq);
            if (token != null && !token.isBlank() && !"anonymous".equalsIgnoreCase(token)) {
                // 已登录用户也走 public 通道时，尝试解析身份
                try {
                    Optional<AuthSession> sessionOpt = authService.validateSession(token);
                    if (sessionOpt.isPresent() && sessionOpt.get().authContext() != null) {
                        attributes.put("authContext", sessionOpt.get().authContext());
                        attributes.put("token", token);
                    }
                } catch (Exception ignored) {}
            }
            attributes.put("publicChannel", true);
            log.debug("[WS Handshake] Public channel allowed: uri={}", uri);
            return true;
        }

        String token = extractToken(httpReq);

        if (token == null || token.isBlank()) {
            log.warn("[WS Handshake] Rejected: no token provided, uri={}", uri);
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
        // WebSocket 握手完成后的回调（无需额外处理）
    }

    /**
     * 从请求中提取 token
     * 优先级：URL 参数（桌面端主要方式） > Authorization 头 > Sec-WebSocket-Protocol 头（兼容旧客户端）
     *
     * 注意：桌面端通过 URL 查询参数 ?token=xxx 传递 token，不使用 Sec-WebSocket-Protocol，
     * 因为 Spring DefaultHandshakeHandler 的子协议匹配是严格相等，bearer.<token> 无法匹配 bearer。
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 从 URL 查询参数获取（桌面端主要方式）
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 2. 从 Authorization 头获取
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 3. 从 Sec-WebSocket-Protocol 头获取（兼容旧客户端）
        String protocol = request.getHeader("Sec-WebSocket-Protocol");
        if (protocol != null && !protocol.isBlank()) {
            for (String p : protocol.split(",")) {
                String trimmed = p.trim();
                if (trimmed.startsWith("bearer.")) {
                    return trimmed.substring(7);
                }
            }
        }

        return null;
    }
}
