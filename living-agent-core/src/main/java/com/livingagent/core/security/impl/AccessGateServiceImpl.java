package com.livingagent.core.security.impl;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.SecurityIdentity;
import com.livingagent.core.security.EmployeeAuthService;
import com.livingagent.core.security.PermissionService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.service.EnterpriseEmployeeService;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccessGateServiceImpl implements AccessGateService {

    private static final Logger log = LoggerFactory.getLogger(AccessGateServiceImpl.class);

    private final EmployeeAuthService employeeAuthService;
    private final PermissionService permissionService;
    private final EnterpriseEmployeeService enterpriseEmployeeService;

    public AccessGateServiceImpl(EmployeeAuthService employeeAuthService, 
                                 PermissionService permissionService,
                                 EnterpriseEmployeeService enterpriseEmployeeService) {
        this.employeeAuthService = employeeAuthService;
        this.permissionService = permissionService;
        this.enterpriseEmployeeService = enterpriseEmployeeService;
    }

    private Optional<SecurityIdentity> findEmployee(String employeeId) {
        Optional<SecurityIdentity> employee = employeeAuthService.findById(employeeId);
        if (employee.isPresent()) {
            return employee;
        }
        
        log.debug("Employee {} not found in memory store, fallback to database", employeeId);
        
        Optional<AuthContext> authContext = enterpriseEmployeeService.findById(employeeId);
        if (authContext.isPresent()) {
            log.debug("Found employee {} in database: {}", employeeId, authContext.get().getName());
            SecurityIdentity converted = toEmployee(authContext.get());
            return Optional.of(converted);
        }
        
        return Optional.empty();
    }
    
    private SecurityIdentity toEmployee(AuthContext ctx) {
        SecurityIdentity employee = new SecurityIdentity();
        employee.setEmployeeId(ctx.getEmployeeId());
        employee.setName(ctx.getName());
        employee.setPhone(ctx.getPhone());
        employee.setEmail(ctx.getEmail());
        employee.setDepartment(ctx.getDepartment() != null && !ctx.getDepartment().isBlank() ? ctx.getDepartment() : (ctx.isFounder() ? "core" : ""));
        employee.setPosition(ctx.getPosition());
        employee.setIdentity(ctx.getIdentity());
        employee.setAccessLevel(ctx.getAccessLevel());
        employee.setFounder(ctx.isFounder());
        employee.setActive(ctx.isActive());
        employee.setTenantId("tenant_default");
        employee.setStatus("ACTIVE");
        return employee;
    }

    @Override
    public Optional<GateDecision> evaluate(String employeeId, String targetType, String targetName) {
        // 安全原则：employeeId 为空时显式拒绝，不允许跳过权限检查
        if (employeeId == null || employeeId.isBlank()) {
            log.warn("Access gate evaluation rejected: employeeId is null or blank, targetType={}, targetName={}", targetType, targetName);
            return Optional.of(new GateDecision(false, AccessLevel.CHAT_ONLY, "Qwen3Neuron", "employeeId is required"));
        }

        Optional<SecurityIdentity> employeeOpt = findEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("Access gate evaluation rejected: employee not found, employeeId={}", employeeId);
            return Optional.of(new GateDecision(false, AccessLevel.CHAT_ONLY, "Qwen3Neuron", "employee not found"));
        }

        SecurityIdentity employee = employeeOpt.get();
        AccessLevel accessLevel = employee.getAccessLevel();
        String routeTarget = permissionService.getRouteTarget(employeeId);

        boolean allowed = switch (targetType == null ? "" : targetType.toLowerCase()) {
            case "brain" -> permissionService.canAccessBrain(employeeId, targetName);
            case "model" -> permissionService.canUseModel(employeeId, targetName);
            case "tool" -> permissionService.canExecuteTool(employeeId, targetName);
            default -> !employee.isChatOnly();
        };

        String reason = allowed ? "allowed" : "blocked by access gate";
        return Optional.of(new GateDecision(allowed, accessLevel, routeTarget, reason));
    }

    @Override
    public boolean canRoute(String employeeId, String targetType, String targetName) {
        if (employeeId == null || employeeId.isBlank()) return false;
        Optional<SecurityIdentity> employee = findEmployee(employeeId);
        if (employee.isEmpty()) return false;
        if (employee.get().getAccessLevel() == AccessLevel.FULL) return true;
        return evaluate(employeeId, targetType, targetName).map(GateDecision::isAllowed).orElse(false);
    }

    @Override
    public String resolveRouteTarget(String employeeId, String targetType, String targetName) {
        return evaluate(employeeId, targetType, targetName)
            .map(GateDecision::getRouteTarget)
            .orElse("Qwen3Neuron");
    }

    @Override
    public boolean hasFullAccess(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            log.warn("hasFullAccess rejected: employeeId is null or blank");
            return false;
        }
        return findEmployee(employeeId)
            .map(e -> e.getAccessLevel() == AccessLevel.FULL)
            .orElse(false);
    }

    @Override
    public boolean belongsToDepartment(String employeeId, String departmentCode) {
        if (employeeId == null || employeeId.isBlank()) {
            log.warn("belongsToDepartment rejected: employeeId is null or blank");
            return false;
        }
        return findEmployee(employeeId)
            .map(e -> departmentCode.equals(e.getDepartment()))
            .orElse(false);
    }
}
