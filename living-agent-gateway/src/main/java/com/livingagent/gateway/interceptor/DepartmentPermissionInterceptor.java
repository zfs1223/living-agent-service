package com.livingagent.gateway.interceptor;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DepartmentPermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DepartmentPermissionInterceptor.class);

    private final UnifiedAuthService authService;

    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("/api/dept/(\\w+)");
    private static final Pattern CHAIRMAN_PATTERN = Pattern.compile("/api/chairman");
    
    private static final Set<String> VALID_DEPARTMENTS = Set.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops"
    );

    public DepartmentPermissionInterceptor(UnifiedAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        
        String uri = request.getRequestURI();
        
        if (CHAIRMAN_PATTERN.matcher(uri).find()) {
            return handleChairmanAccess(request, response);
        }
        
        Matcher matcher = DEPARTMENT_PATTERN.matcher(uri);
        if (!matcher.find()) {
            return true;
        }
        
        String department = matcher.group(1);
        
        if (!VALID_DEPARTMENTS.contains(department.toLowerCase())) {
            response.sendError(404, "部门不存在: " + department);
            return false;
        }
        
        Optional<AuthContext> ctxOpt = getAuthContext(request);
        
        if (ctxOpt.isEmpty()) {
            response.sendError(401, "请先登录");
            return false;
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (!hasDepartmentAccess(ctx, department)) {
            log.warn("Department access denied: user={}, dept={}, required={}", 
                ctx.getEmployeeId(), ctx.getDepartment(), department);
            response.sendError(403, "无权访问该部门: " + department);
            return false;
        }
        
        request.setAttribute("department", department);
        request.setAttribute("brainName", Department.mapDepartmentToBrain(department));
        
        log.debug("Department access granted: user={}, dept={}", ctx.getEmployeeId(), department);
        return true;
    }

    private boolean handleChairmanAccess(HttpServletRequest request, HttpServletResponse response) 
            throws Exception {
        Optional<AuthContext> ctxOpt = getAuthContext(request);
        
        if (ctxOpt.isEmpty()) {
            response.sendError(401, "请先登录");
            return false;
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (ctx.getAccessLevel() != AccessLevel.FULL && !ctx.isFounder()) {
            log.warn("Chairman access denied: user={}, accessLevel={}", 
                ctx.getEmployeeId(), ctx.getAccessLevel());
            response.sendError(403, "需要董事长权限");
            return false;
        }
        
        request.setAttribute("isChairman", true);
        log.debug("Chairman access granted: user={}", ctx.getEmployeeId());
        return true;
    }

    private Optional<AuthContext> getAuthContext(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean hasDepartmentAccess(AuthContext ctx, String department) {
        if (ctx.getAccessLevel() == AccessLevel.FULL) {
            return true;
        }
        
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) {
            return false;
        }
        
        if (ctx.isFounder()) {
            return true;
        }
        
        String userDept = ctx.getDepartment();
        if (userDept == null) {
            return false;
        }
        
        return userDept.equalsIgnoreCase(department);
    }
}
