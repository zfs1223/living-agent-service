package com.livingagent.gateway.interceptor;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.DepartmentAccessService;
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
    private final DepartmentAccessService departmentAccessService;

    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("/api/dept/(\\w+)");
    // 支持 /api/{dept}/** 格式的部门API路径
    private static final Pattern DEPARTMENT_API_PATTERN = Pattern.compile("/api/(tech|hr|finance|sales|admin|cs|legal|ops)/?");
    private static final Pattern ENTERPRISE_PATTERN = Pattern.compile("/api/enterprise");
    // 管理类API：需要FULL权限
    private static final Pattern ADMIN_API_PATTERN = Pattern.compile("/api/(model-pool|brain-models|windows-automation|evolution)/?");
    private static final Pattern PROXY_API_PATTERN = Pattern.compile("/api/v1/proxy/");
    
    private static final Set<String> VALID_DEPARTMENTS = Set.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops"
    );

    public DepartmentPermissionInterceptor(UnifiedAuthService authService,
                                           DepartmentAccessService departmentAccessService) {
        this.authService = authService;
        this.departmentAccessService = departmentAccessService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        
        String uri = request.getRequestURI();
        
        // 管理类API：需要FULL权限
        if (ADMIN_API_PATTERN.matcher(uri).find() || PROXY_API_PATTERN.matcher(uri).find()) {
            return handleAdminAccess(request, response);
        }
        
        // 企业级API：需要FULL权限
        if (ENTERPRISE_PATTERN.matcher(uri).find()) {
            return handleEnterpriseAccess(request, response);
        }
        
        // /api/{dept}/** 格式的部门API
        Matcher deptApiMatcher = DEPARTMENT_API_PATTERN.matcher(uri);
        if (deptApiMatcher.find()) {
            String department = deptApiMatcher.group(1);
            return handleDepartmentAccess(request, response, department);
        }
        
        // /api/dept/{dept} 格式
        Matcher matcher = DEPARTMENT_PATTERN.matcher(uri);
        if (matcher.find()) {
            String department = matcher.group(1);
            return handleDepartmentAccess(request, response, department);
        }
        
        return true;
    }

    /**
     * 处理管理类API访问权限（model-pool, brain-models, windows-automation, evolution, proxy）
     * 要求FULL权限或创始人身份
     */
    private boolean handleAdminAccess(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        Optional<AuthContext> ctxOpt = getAuthContext(request);
        
        if (ctxOpt.isEmpty()) {
            response.sendError(401, "请先登录");
            return false;
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (ctx.getAccessLevel() != AccessLevel.FULL && !ctx.isFounder()) {
            log.warn("Admin API access denied: user={}, accessLevel={}, uri={}", 
                ctx.getEmployeeId(), ctx.getAccessLevel(), request.getRequestURI());
            response.sendError(403, "需要董事长权限访问管理功能");
            return false;
        }
        
        log.debug("Admin API access granted: user={}, uri={}", ctx.getEmployeeId(), request.getRequestURI());
        return true;
    }

    /**
     * 处理部门API访问权限
     */
    private boolean handleDepartmentAccess(HttpServletRequest request, HttpServletResponse response, String department)
            throws Exception {
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
        
        if (!departmentAccessService.hasDepartmentAccess(ctx, department)) {
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

    private boolean handleEnterpriseAccess(HttpServletRequest request, HttpServletResponse response) 
            throws Exception {
        Optional<AuthContext> ctxOpt = getAuthContext(request);
        
        if (ctxOpt.isEmpty()) {
            response.sendError(401, "请先登录");
            return false;
        }
        
        AuthContext ctx = ctxOpt.get();
        
        if (ctx.getAccessLevel() != AccessLevel.FULL && !ctx.isFounder()) {
            log.warn("Enterprise access denied: user={}, accessLevel={}", 
                ctx.getEmployeeId(), ctx.getAccessLevel());
            response.sendError(403, "需要董事长权限");
            return false;
        }
        
        request.setAttribute("isEnterprise", true);
        log.debug("Enterprise access granted: user={}", ctx.getEmployeeId());
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
}
