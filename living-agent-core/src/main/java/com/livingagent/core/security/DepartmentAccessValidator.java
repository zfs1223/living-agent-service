package com.livingagent.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DepartmentAccessValidator {

    private static final Logger log = LoggerFactory.getLogger(DepartmentAccessValidator.class);

    private final Map<String, Set<String>> departmentMembers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> departmentResources = new ConcurrentHashMap<>();
    private final Map<String, AccessPolicy> resourcePolicies = new ConcurrentHashMap<>();
    
    private final Set<String> validDepartments = Set.of(
        "tech", "hr", "finance", "admin", "sales", "cs", "ops", "legal"
    );

    public DepartmentAccessValidator() {
        initializeDefaultPolicies();
    }

    private void initializeDefaultPolicies() {
        addResourcePolicy(new AccessPolicy(
            "api://department/employees",
            ResourceType.API,
            AccessLevel.DEPARTMENT,
            "部门员工列表，仅本部门可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "api://department/knowledge",
            ResourceType.API,
            AccessLevel.DEPARTMENT,
            "部门知识库，仅本部门可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "api://shared/knowledge",
            ResourceType.API,
            AccessLevel.LIMITED,
            "共享知识库，LIMITED及以上可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "api://chairman/*",
            ResourceType.API,
            AccessLevel.FULL,
            "董事长专属API，仅FULL权限可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "brain://tech/*",
            ResourceType.BRAIN,
            AccessLevel.DEPARTMENT,
            "技术部大脑，仅技术部可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "brain://hr/*",
            ResourceType.BRAIN,
            AccessLevel.DEPARTMENT,
            "人力资源大脑，仅HR可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "brain://finance/*",
            ResourceType.BRAIN,
            AccessLevel.DEPARTMENT,
            "财务大脑，仅财务部可访问"
        ));
        
        addResourcePolicy(new AccessPolicy(
            "tool://sensitive/*",
            ResourceType.TOOL,
            AccessLevel.FULL,
            "敏感工具，仅FULL权限可使用"
        ));
    }

    public ValidationResult validate(AuthContext context, String resource, String action) {
        if (context == null) {
            return ValidationResult.failure("认证上下文为空");
        }
        
        if (resource == null || resource.isEmpty()) {
            return ValidationResult.failure("资源标识为空");
        }
        
        AccessPolicy policy = findMatchingPolicy(resource);
        if (policy == null) {
            log.debug("No policy found for resource: {}, allowing access", resource);
            return ValidationResult.success();
        }
        
        AccessLevel requiredLevel = policy.requiredLevel();
        AccessLevel userLevel = context.getAccessLevel();
        
        if (context.isFounder()) {
            log.debug("Founder access granted for resource: {}", resource);
            return ValidationResult.success();
        }
        
        if (!hasSufficientLevel(userLevel, requiredLevel)) {
            log.warn("Access denied: user={}, level={}, required={}, resource={}",
                context.getEmployeeId(), userLevel, requiredLevel, resource);
            return ValidationResult.failure(
                String.format("权限不足: 需要 %s，当前 %s", requiredLevel, userLevel)
            );
        }
        
        if (requiredLevel == AccessLevel.DEPARTMENT) {
            String resourceDept = extractDepartmentFromResource(resource);
            if (resourceDept != null && !isUserDepartment(context, resourceDept)) {
                log.warn("Department access denied: user={}, userDept={}, resourceDept={}",
                    context.getEmployeeId(), context.getDepartment(), resourceDept);
                return ValidationResult.failure(
                    String.format("无权访问其他部门资源: %s", resourceDept)
                );
            }
        }
        
        log.debug("Access granted: user={}, resource={}, action={}", 
            context.getEmployeeId(), resource, action);
        return ValidationResult.success();
    }

    public ValidationResult validateCrossDepartment(
            AuthContext context, 
            String targetDepartment,
            String reason) {
        
        if (context == null) {
            return ValidationResult.failure("认证上下文为空");
        }
        
        if (!validDepartments.contains(targetDepartment.toLowerCase())) {
            return ValidationResult.failure("无效的部门: " + targetDepartment);
        }
        
        if (context.isFounder()) {
            log.info("Founder cross-department access: user={}, target={}, reason={}",
                context.getEmployeeId(), targetDepartment, reason);
            return ValidationResult.success();
        }
        
        if (context.getAccessLevel() == AccessLevel.FULL) {
            log.info("FULL access cross-department: user={}, target={}, reason={}",
                context.getEmployeeId(), targetDepartment, reason);
            return ValidationResult.success();
        }
        
        if (context.getAccessLevel() == AccessLevel.CHAT_ONLY) {
            return ValidationResult.failure("CHAT_ONLY 用户无权跨部门访问");
        }
        
        return ValidationResult.failure(
            String.format("无权跨部门访问 %s，需要审批", targetDepartment)
        );
    }

    public ValidationResult validateDataAccess(
            AuthContext context,
            String dataId,
            String dataType,
            String ownerDepartment) {
        
        if (context == null) {
            return ValidationResult.failure("认证上下文为空");
        }
        
        if (context.isFounder() || context.getAccessLevel() == AccessLevel.FULL) {
            return ValidationResult.success();
        }
        
        if (isUserDepartment(context, ownerDepartment)) {
            return ValidationResult.success();
        }
        
        if ("public".equals(dataType) || "shared".equals(dataType)) {
            if (context.getAccessLevel().ordinal() >= AccessLevel.LIMITED.ordinal()) {
                return ValidationResult.success();
            }
        }
        
        log.warn("Data access denied: user={}, dataId={}, ownerDept={}",
            context.getEmployeeId(), dataId, ownerDepartment);
        return ValidationResult.failure("无权访问该数据");
    }

    public boolean isValidDepartment(String department) {
        return department != null && validDepartments.contains(department.toLowerCase());
    }

    public Set<String> getValidDepartments() {
        return validDepartments;
    }

    public void addResourcePolicy(AccessPolicy policy) {
        resourcePolicies.put(policy.resource(), policy);
    }

    public void addDepartmentMember(String department, String employeeId) {
        departmentMembers.computeIfAbsent(department.toLowerCase(), k -> ConcurrentHashMap.newKeySet())
            .add(employeeId);
    }

    public void removeDepartmentMember(String department, String employeeId) {
        Set<String> members = departmentMembers.get(department.toLowerCase());
        if (members != null) {
            members.remove(employeeId);
        }
    }

    public boolean isDepartmentMember(String department, String employeeId) {
        Set<String> members = departmentMembers.get(department.toLowerCase());
        return members != null && members.contains(employeeId);
    }

    private AccessPolicy findMatchingPolicy(String resource) {
        for (Map.Entry<String, AccessPolicy> entry : resourcePolicies.entrySet()) {
            String pattern = entry.getKey();
            if (matchesPattern(resource, pattern)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesPattern(String resource, String pattern) {
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return resource.startsWith(prefix);
        }
        return resource.equals(pattern);
    }

    private boolean hasSufficientLevel(AccessLevel userLevel, AccessLevel requiredLevel) {
        return userLevel.ordinal() >= requiredLevel.ordinal();
    }

    private boolean isUserDepartment(AuthContext context, String department) {
        String userDept = context.getDepartment();
        return userDept != null && userDept.equalsIgnoreCase(department);
    }

    private String extractDepartmentFromResource(String resource) {
        if (resource.startsWith("api://department/")) {
            String[] parts = resource.split("/");
            if (parts.length > 3) {
                return parts[3];
            }
        }
        if (resource.startsWith("brain://")) {
            String[] parts = resource.split("/");
            if (parts.length > 2) {
                return parts[2];
            }
        }
        return null;
    }

    public record AccessPolicy(
        String resource,
        ResourceType type,
        AccessLevel requiredLevel,
        String description
    ) {}

    public enum ResourceType {
        API,
        BRAIN,
        TOOL,
        DATA,
        KNOWLEDGE
    }

    public static class ValidationResult {
        private final boolean allowed;
        private final String reason;
        private final Map<String, Object> metadata;

        private ValidationResult(boolean allowed, String reason, Map<String, Object> metadata) {
            this.allowed = allowed;
            this.reason = reason;
            this.metadata = metadata != null ? metadata : Map.of();
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "Access granted", null);
        }

        public static ValidationResult success(String reason) {
            return new ValidationResult(true, reason, null);
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, null);
        }

        public static ValidationResult failure(String reason, Map<String, Object> metadata) {
            return new ValidationResult(false, reason, metadata);
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
