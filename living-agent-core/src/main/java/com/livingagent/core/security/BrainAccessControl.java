package com.livingagent.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BrainAccessControl {

    private static final Logger log = LoggerFactory.getLogger(BrainAccessControl.class);

    private final PermissionService permissionService;
    private final Map<String, BrainAccessPolicy> brainPolicies = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sensitiveKnowledge = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> restrictedTools = new ConcurrentHashMap<>();

    public BrainAccessControl(PermissionService permissionService) {
        this.permissionService = permissionService;
        initializeDefaultPolicies();
    }

    private void initializeDefaultPolicies() {
        brainPolicies.put("TechBrain", new BrainAccessPolicy(
                "TechBrain", 
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("tech", "technology", "技术部", "研发部"),
                true,
                "技术部门大脑，处理代码审查、CI/CD、架构设计等"
        ));

        brainPolicies.put("HrBrain", new BrainAccessPolicy(
                "HrBrain",
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("hr", "human-resources", "人力资源", "人事部"),
                true,
                "人力资源大脑，处理招聘、考勤、绩效等"
        ));

        brainPolicies.put("FinanceBrain", new BrainAccessPolicy(
                "FinanceBrain",
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("finance", "财务部", "财务"),
                true,
                "财务大脑，处理报销、发票、预算等"
        ));

        brainPolicies.put("SalesBrain", new BrainAccessPolicy(
                "SalesBrain",
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("sales", "销售部", "营销"),
                false,
                "销售大脑，处理销售支持、市场营销等"
        ));

        brainPolicies.put("CsBrain", new BrainAccessPolicy(
                "CsBrain",
                Set.of(AccessLevel.LIMITED, AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("cs", "customer-service", "客服部"),
                false,
                "客服大脑，处理工单、问题解答等"
        ));

        brainPolicies.put("AdminBrain", new BrainAccessPolicy(
                "AdminBrain",
                Set.of(AccessLevel.LIMITED, AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("admin", "行政部", "行政"),
                false,
                "行政大脑，处理文档、文案、行政事务等"
        ));

        brainPolicies.put("LegalBrain", new BrainAccessPolicy(
                "LegalBrain",
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("legal", "法务部", "法务"),
                true,
                "法务大脑，处理合同审查、合规检查等"
        ));

        brainPolicies.put("OpsBrain", new BrainAccessPolicy(
                "OpsBrain",
                Set.of(AccessLevel.DEPARTMENT, AccessLevel.FULL),
                Set.of("ops", "运营部", "运营"),
                false,
                "运营大脑，处理数据分析、运营策略等"
        ));

        brainPolicies.put("MainBrain", new BrainAccessPolicy(
                "MainBrain",
                Set.of(AccessLevel.FULL),
                Set.of("main", "mainbrain"),
                true,
                "主大脑，协调跨部门协作"
        ));

        sensitiveKnowledge.put("FinanceBrain", Set.of("salary", "budget", "financial_report", "invoice"));
        sensitiveKnowledge.put("HrBrain", Set.of("salary", "performance", "personal_info", "contract"));
        sensitiveKnowledge.put("LegalBrain", Set.of("contract", "lawsuit", "compliance"));

        restrictedTools.put("TechBrain", Set.of("GitLabTool", "JenkinsTool", "JiraTool"));
        restrictedTools.put("FinanceBrain", Set.of("ErpTool", "FinanceTool"));
        restrictedTools.put("HrBrain", Set.of("HrSystemTool"));
    }

    public AccessCheckResult checkAccess(String employeeId, String brainName) {
        log.debug("Checking brain access: employee={}, brain={}", employeeId, brainName);

        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("Employee not found: {}", employeeId);
            return AccessCheckResult.denied("Employee not found");
        }

        Employee employee = employeeOpt.get();

        if (!employee.isActive()) {
            log.info("Employee is not active: {}", employeeId);
            return AccessCheckResult.denied("Employee is not active");
        }

        if (employee.isChatOnly()) {
            log.info("Chat-only user cannot access brains: {}", employeeId);
            return AccessCheckResult.chatOnly();
        }

        BrainAccessPolicy policy = brainPolicies.get(brainName);
        if (policy == null) {
            log.warn("Brain policy not found: {}", brainName);
            return AccessCheckResult.denied("Brain not found");
        }

        if (!policy.allowedAccessLevels().contains(employee.getAccessLevel())) {
            log.info("Access level not allowed: employee={}, level={}, brain={}", 
                    employeeId, employee.getAccessLevel(), brainName);
            return AccessCheckResult.denied("Access level not allowed for this brain");
        }

        if (!employee.canAccessBrain(brainName)) {
            log.info("Employee not authorized for brain: {} -> {}", employeeId, brainName);
            return AccessCheckResult.denied("Not authorized for this brain");
        }

        permissionService.recordAccess(employeeId, "brain:" + brainName, "access", true);

        log.info("Brain access granted: employee={}, brain={}", employeeId, brainName);
        return AccessCheckResult.granted(brainName, employee.getAccessLevel());
    }

    public String routeToBrain(String employeeId) {
        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty()) {
            return "Qwen3Neuron";
        }

        Employee employee = employeeOpt.get();

        if (employee.isChatOnly()) {
            return "Qwen3Neuron";
        }

        String department = employee.getDepartment();
        if (department == null || department.isEmpty()) {
            return "MainBrain";
        }

        return Department.mapDepartmentToBrain(department);
    }

    public Set<String> getAccessibleBrains(String employeeId) {
        Set<String> accessible = new HashSet<>();

        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty() || employeeOpt.get().isChatOnly()) {
            return accessible;
        }

        Employee employee = employeeOpt.get();

        for (Map.Entry<String, BrainAccessPolicy> entry : brainPolicies.entrySet()) {
            String brainName = entry.getKey();
            BrainAccessPolicy policy = entry.getValue();

            if (policy.allowedAccessLevels().contains(employee.getAccessLevel())) {
                if (employee.canAccessBrain(brainName)) {
                    accessible.add(brainName);
                }
            }
        }

        return accessible;
    }

    public boolean canAccessKnowledge(String employeeId, String brainName, String knowledgeKey) {
        AccessCheckResult result = checkAccess(employeeId, brainName);
        if (!result.isGranted()) {
            return false;
        }

        Set<String> sensitive = sensitiveKnowledge.get(brainName);
        if (sensitive == null || !sensitive.contains(knowledgeKey)) {
            return true;
        }

        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        AccessLevel level = employeeOpt.get().getAccessLevel();
        return level == AccessLevel.DEPARTMENT || level == AccessLevel.FULL;
    }

    public boolean canExecuteTool(String employeeId, String brainName, String toolName) {
        AccessCheckResult result = checkAccess(employeeId, brainName);
        if (!result.isGranted()) {
            return false;
        }

        Set<String> restricted = restrictedTools.get(brainName);
        if (restricted == null || !restricted.contains(toolName)) {
            return true;
        }

        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        return employeeOpt.get().getAccessLevel() == AccessLevel.DEPARTMENT ||
               employeeOpt.get().getAccessLevel() == AccessLevel.FULL;
    }

    public String filterSensitiveContent(String employeeId, String brainName, String content) {
        Set<String> sensitive = sensitiveKnowledge.get(brainName);
        if (sensitive == null) {
            return content;
        }

        Optional<Employee> employeeOpt = permissionService.getEmployeeById(employeeId);
        if (employeeOpt.isEmpty() || 
            employeeOpt.get().getAccessLevel() == AccessLevel.DEPARTMENT ||
            employeeOpt.get().getAccessLevel() == AccessLevel.FULL) {
            return content;
        }

        String filtered = content;
        for (String keyword : sensitive) {
            filtered = filtered.replaceAll("(?i)" + keyword, "[REDACTED]");
        }
        return filtered;
    }

    public void registerBrainPolicy(String brainName, BrainAccessPolicy policy) {
        brainPolicies.put(brainName, policy);
        log.info("Registered brain policy: {}", brainName);
    }

    public void addSensitiveKnowledge(String brainName, String knowledgeKey) {
        sensitiveKnowledge.computeIfAbsent(brainName, k -> ConcurrentHashMap.newKeySet()).add(knowledgeKey);
    }

    public void addRestrictedTool(String brainName, String toolName) {
        restrictedTools.computeIfAbsent(brainName, k -> ConcurrentHashMap.newKeySet()).add(toolName);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBrains", brainPolicies.size());
        stats.put("sensitiveKnowledgeCount", sensitiveKnowledge.values().stream().mapToInt(Set::size).sum());
        stats.put("restrictedToolsCount", restrictedTools.values().stream().mapToInt(Set::size).sum());
        return stats;
    }

    public record BrainAccessPolicy(
            String brainName,
            Set<AccessLevel> allowedAccessLevels,
            Set<String> departmentKeywords,
            boolean requiresDepartmentMatch,
            String description
    ) {}

    public static class AccessCheckResult {
        private final boolean granted;
        private final String brainName;
        private final AccessLevel accessLevel;
        private final String reason;
        private final boolean chatOnly;

        private AccessCheckResult(boolean granted, String brainName, AccessLevel accessLevel, 
                                  String reason, boolean chatOnly) {
            this.granted = granted;
            this.brainName = brainName;
            this.accessLevel = accessLevel;
            this.reason = reason;
            this.chatOnly = chatOnly;
        }

        public static AccessCheckResult granted(String brainName, AccessLevel level) {
            return new AccessCheckResult(true, brainName, level, "Access granted", false);
        }

        public static AccessCheckResult denied(String reason) {
            return new AccessCheckResult(false, null, null, reason, false);
        }

        public static AccessCheckResult chatOnly() {
            return new AccessCheckResult(false, null, AccessLevel.CHAT_ONLY, 
                    "Chat-only user, redirecting to Qwen3Neuron", true);
        }

        public boolean isGranted() { return granted; }
        public String getBrainName() { return brainName; }
        public AccessLevel getAccessLevel() { return accessLevel; }
        public String getReason() { return reason; }
        public boolean isChatOnly() { return chatOnly; }

        public String getRouteTarget() {
            if (chatOnly) {
                return "Qwen3Neuron";
            }
            if (granted) {
                return brainName;
            }
            return "Qwen3Neuron";
        }

        @Override
        public String toString() {
            return String.format("AccessCheckResult{granted=%s, brain=%s, level=%s, reason=%s}",
                    granted, brainName, accessLevel, reason);
        }
    }
}
