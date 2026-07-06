package com.livingagent.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 客户端 ID 过滤器
 * 
 * 职责：
 * 1. 从 HTTP 请求头 X-Client-Id 中提取客户端标识
 * 2. 存储到 ThreadLocal 中，供后续业务逻辑使用
 * 3. 确保请求结束后清理 ThreadLocal，避免内存泄漏
 * 
 * 使用场景：
 * - 桌面端发送 X-Client-Id header 标识自己的身份
 * - 后端通过 ClientIdFilter 提取并存入 ThreadLocal
 * - 业务层通过 ClientIdFilter.getCurrentClientId() 获取当前请求的 clientId
 * - 用于 Windows 自动化工具的路由、审计日志记录等
 * 
 * 优先级：
 * - @Order(1) 确保在 SessionAuthenticationFilter 之前执行
 * - 这样后续 Filter 和业务逻辑都可以使用 clientId
 */
@Component
@Order(1)
public class ClientIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientIdFilter.class);
    
    /** HTTP 请求头名称：客户端 ID */
    public static final String HEADER_CLIENT_ID = "X-Client-Id";
    
    /** ThreadLocal：存储当前请求的客户端 ID */
    private static final ThreadLocal<String> CURRENT_CLIENT_ID = new ThreadLocal<>();

    /**
     * 获取当前请求的客户端 ID
     * 
     * @return 客户端 ID，如果未设置则返回 null
     */
    public static String getCurrentClientId() {
        return CURRENT_CLIENT_ID.get();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求头中提取客户端 ID
            String clientId = request.getHeader(HEADER_CLIENT_ID);
            
            if (clientId != null && !clientId.isBlank()) {
                // 存入 ThreadLocal
                CURRENT_CLIENT_ID.set(clientId);
                log.debug("Client ID extracted from header: {}", clientId);
            } else {
                log.trace("No client ID found in request header");
            }
            
            // 继续过滤器链
            filterChain.doFilter(request, response);
            
        } finally {
            // 清理 ThreadLocal，避免内存泄漏
            CURRENT_CLIENT_ID.remove();
        }
    }

    /**
     * 手动设置当前线程的客户端 ID
     * 用于 WebSocket 等无法通过 HTTP header 传递的场景
     * 
     * @param clientId 客户端 ID
     */
    public static void setCurrentClientId(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            CURRENT_CLIENT_ID.set(clientId);
            log.debug("Client ID manually set: {}", clientId);
        }
    }

    /**
     * 清理当前线程的客户端 ID
     * 用于 WebSocket 连接关闭时的清理
     */
    public static void clearCurrentClientId() {
        CURRENT_CLIENT_ID.remove();
    }
}
