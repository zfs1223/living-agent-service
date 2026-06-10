package com.livingagent.gateway.security;

import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 从 Authorization: Bearer {sessionId} 或 HttpOnly Cookie (access_token) 中提取会话 token，
 * 验证后设置 Spring Security Authentication 并注入 X-Employee-Id header。
 *
 * 优先级：Authorization 头 > access_token Cookie
 *
 * 解决 Spring Security .anyRequest().authenticated() 因缺少认证机制
 * 而导致所有非白名单 API 返回 403 的问题。
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationFilter.class);
    private static final String HEADER_EMPLOYEE_ID = "X-Employee-Id";
    private static final String COOKIE_ACCESS_TOKEN = "access_token";

    private final UnifiedAuthService authService;

    public SessionAuthenticationFilter(UnifiedAuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String sessionId = null;

        // 优先从 Authorization 头获取 token
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessionId = authorization.substring(7);
        }

        // 如果 Authorization 头没有 token，从 Cookie 中获取
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = extractTokenFromCookie(request);
        }

        if (sessionId != null && !sessionId.isBlank()) {
            Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

            if (sessionOpt.isPresent()) {
                AuthSession session = sessionOpt.get();
                String employeeId = session.authContext().getEmployeeId();
                String accessLevel = session.authContext().getAccessLevel() != null
                        ? session.authContext().getAccessLevel().name() : "CHAT_ONLY";

                // 设置 Spring Security Authentication
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + accessLevel));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(employeeId, null, authorities);
                auth.setDetails(session.authContext());
                SecurityContextHolder.getContext().setAuthentication(auth);

                // 包装 request，注入 X-Employee-Id header 供 Controller 层使用
                request = new EmployeeIdRequestWrapper(request, employeeId);

                log.debug("Session authenticated: employeeId={}, accessLevel={}", employeeId, accessLevel);
            } else {
                log.debug("Invalid or expired session token");
                // 未认证请求：剥离客户端可能伪造的 X-Employee-Id 头
                request = new EmployeeIdRequestWrapper(request, null);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 从 HttpOnly Cookie 中提取 access_token
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_ACCESS_TOKEN.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    log.debug("Extracted token from access_token cookie");
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * HttpServletRequestWrapper 重写 getHeader("X-Employee-Id")，
     * 使 Controller 的 @RequestHeader("X-Employee-Id") 能获取到正确的员工ID。
     */
    private static class EmployeeIdRequestWrapper extends HttpServletRequestWrapper {
        private final String employeeId;

        public EmployeeIdRequestWrapper(HttpServletRequest request, String employeeId) {
            super(request);
            this.employeeId = employeeId;
        }

        @Override
        public String getHeader(String name) {
            if (HEADER_EMPLOYEE_ID.equals(name)) {
                // employeeId 为 null 时返回 null，防止客户端伪造 X-Employee-Id
                return employeeId;
            }
            return super.getHeader(name);
        }
    }
}