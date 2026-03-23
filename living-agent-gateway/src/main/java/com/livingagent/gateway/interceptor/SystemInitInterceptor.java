package com.livingagent.gateway.interceptor;

import com.livingagent.core.security.auth.FounderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class SystemInitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SystemInitInterceptor.class);

    private static final List<String> ALLOWED_PATHS = Arrays.asList(
        "/api/system/status",
        "/api/system/register",
        "/api/reception",
        "/login",
        "/error"
    );

    private final FounderService founderService;
    private volatile boolean initialized = false;

    public SystemInitInterceptor(FounderService founderService) {
        this.founderService = founderService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        
        if (isAllowedPath(requestURI)) {
            return true;
        }

        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    founderService.initialize();
                    initialized = true;
                }
            }
        }

        if (!founderService.hasFounder()) {
            log.debug("System not initialized, redirecting to registration. URI: {}", requestURI);
            
            if (requestURI.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"error\":\"system_not_initialized\",\"errorDescription\":\"系统尚未初始化，请先注册董事长\",\"redirect\":\"/auth/register\"}");
                return false;
            } else {
                response.sendRedirect("/auth/register");
                return false;
            }
        }

        return true;
    }

    private boolean isAllowedPath(String uri) {
        return ALLOWED_PATHS.stream().anyMatch(uri::startsWith) ||
               uri.equals("/") ||
               uri.startsWith("/auth/register") ||
               uri.startsWith("/auth/callback");
    }
}
