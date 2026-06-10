package com.livingagent.gateway.security;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 权限检查AOP切面，统一拦截 @RequireAccess 注解的方法。
 * 从请求头 X-Employee-Id 获取员工ID，调用 AccessGateService 进行权限判断。
 * 越权时统一返回 ApiResponse.err() 格式。
 */
@Aspect
@Component
public class RequireAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireAccessAspect.class);

    private final AccessGateService accessGateService;

    public RequireAccessAspect(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @Around("@annotation(com.livingagent.gateway.security.RequireAccess)")
    public Object checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No request context available for @RequireAccess check");
            return ApiResponse.err("forbidden", "No request context");
        }

        HttpServletRequest request = attributes.getRequest();
        String employeeId = request.getHeader("X-Employee-Id");

        // 获取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireAccess requireAccess = method.getAnnotation(RequireAccess.class);

        // employeeId 为空时拒绝
        if (employeeId == null || employeeId.isBlank()) {
            log.warn("Access denied: missing X-Employee-Id header, resource={}, action={}, uri={}",
                requireAccess.resource(), requireAccess.action(), request.getRequestURI());
            return ApiResponse.err("forbidden", "Authentication required: missing employee ID");
        }

        // 检查是否要求FULL权限
        if (requireAccess.requireFull()) {
            if (!accessGateService.hasFullAccess(employeeId)) {
                log.warn("FULL access denied: employeeId={}, resource={}, action={}",
                    employeeId, requireAccess.resource(), requireAccess.action());
                return ApiResponse.err("forbidden", "FULL access required for this operation");
            }
        }

        // 通用权限检查
        if (!accessGateService.canRoute(employeeId, requireAccess.resource(), requireAccess.action())) {
            log.warn("Access denied: employeeId={}, resource={}, action={}",
                employeeId, requireAccess.resource(), requireAccess.action());
            return ApiResponse.err("forbidden", "Access denied before routing");
        }

        return joinPoint.proceed();
    }
}
