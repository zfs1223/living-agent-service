package com.livingagent.gateway.controller;

import com.livingagent.core.database.repository.DepartmentRepository;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.operation.dashboard.DashboardService;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.SystemConfigService;
import com.livingagent.gateway.service.SystemConfigService.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = {"/api/enterprise", "/api/chairman"})
public class EnterpriseApiController {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseApiController.class);

    @org.springframework.beans.factory.annotation.Value("${living-agent.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    private final UnifiedAuthService authService;
    private final EmployeeService employeeService;
    private final AccessGateService accessGateService;
    private final DashboardService dashboardService;
    private final DepartmentRepository departmentRepository;
    private final SystemConfigService systemConfigService;
    private final com.livingagent.core.database.repository.EnterpriseEmployeeRepository enterpriseEmployeeRepository;

    public EnterpriseApiController(UnifiedAuthService authService, EmployeeService employeeService,
                                   AccessGateService accessGateService, DashboardService dashboardService,
                                   DepartmentRepository departmentRepository,
                                   SystemConfigService systemConfigService,
                                   com.livingagent.core.database.repository.EnterpriseEmployeeRepository enterpriseEmployeeRepository) {
        this.authService = authService;
        this.employeeService = employeeService;
        this.accessGateService = accessGateService;
        this.dashboardService = dashboardService;
        this.departmentRepository = departmentRepository;
        this.systemConfigService = systemConfigService;
        this.enterpriseEmployeeRepository = enterpriseEmployeeRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardOverview> getDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        AuthContext ctx = ctxOpt.get();
        String effectiveEmployeeId = employeeId != null && !employeeId.isBlank() ? employeeId : ctx.getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }
        log.info("Enterprise dashboard accessed by: {}", ctx.getEmployeeId());

        var summary = dashboardService.getEnterpriseSummary(effectiveEmployeeId);

        var departmentMetrics = summary.departmentHealth().stream()
            .map(d -> new DepartmentMetric(d.code(), d.name(), d.memberCount(), d.healthScore(), d.status()))
            .collect(Collectors.toList());

        var alerts = summary.riskAlerts().stream()
            .map(r -> new SystemAlert(r.level(), r.title(), r.message()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(new DashboardOverview(
            summary.departmentHealth().size(),
            summary.employeeMetrics().digitalEmployees(),
            summary.employeeMetrics().totalEmployees(),
            summary.systemHealth().healthScore(),
            departmentMetrics,
            alerts
        ));
    }

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeSummary>> getAllEmployees(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }

        List<EmployeeSummary> employees = new ArrayList<>();

        // 优先从数据库加载所有员工（数据更完整）
        try {
            List<com.livingagent.core.database.entity.EnterpriseEmployeeEntity> dbEmployees =
                enterpriseEmployeeRepository.findByActiveTrue();
            // 补充非活跃员工（停用/离职的用户也需要管理）
            dbEmployees = enterpriseEmployeeRepository.findAll();
            for (var entity : dbEmployees) {
                employees.add(new EmployeeSummary(
                    entity.getEmployeeId(),
                    entity.getName(),
                    entity.getDepartmentId(),
                    entity.getDepartmentName(),
                    entity.getPosition(),
                    entity.getIdentity(),
                    entity.getAccessLevel(),
                    entity.getEmployeeType() != null ? entity.getEmployeeType() : "HUMAN",
                    entity.getOrigin() != null ? entity.getOrigin().toLowerCase() : "human",
                    entity.getPhone(),
                    entity.getEmail(),
                    entity.getAvatarUrl(),
                    entity.getTenantId(),
                    entity.isFounder(),
                    Boolean.TRUE.equals(entity.isActive()),
                    entity.getJoinDate(),
                    entity.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to load employees from database, falling back to memory store", e);
            // 降级：从内存获取
            EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0, null);
            employeeService.listEmployees(query).forEach(emp -> {
                employees.add(new EmployeeSummary(
                    emp.getEmployeeId(),
                    emp.getName(),
                    emp.getDepartmentId(),
                    emp.getDepartment(),
                    emp.getTitle(),
                    emp.getIdentity().name(),
                    emp.getAccessLevel().name(),
                    emp.isDigital() ? "DIGITAL" : "HUMAN",
                    emp.getOrigin() != null ? emp.getOrigin().name().toLowerCase() : "human",
                    emp.getPhone().orElse(null),
                    emp.getEmail().orElse(null),
                    null,
                    null,
                    false,
                    emp.getStatus() == com.livingagent.core.employee.EmployeeStatus.ACTIVE,
                    null,
                    emp.getCreatedAt()
                ));
            });
        }

        return ResponseEntity.ok(employees);
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeDetail> getEmployeeDetail(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        AuthContext ctx = ctxOpt.get();
        String effectiveEmployeeId = headerEmployeeId != null && !headerEmployeeId.isBlank() ? headerEmployeeId : ctx.getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }

        // 优先从数据库获取（数据更完整）
        try {
            var entity = enterpriseEmployeeRepository.findByEmployeeId(employeeId);
            if (entity.isPresent()) {
                var e = entity.get();
                return ResponseEntity.ok(new EmployeeDetail(
                    e.getEmployeeId(),
                    e.getName(),
                    e.getDepartmentId(),
                    e.getDepartmentName(),
                    e.getPosition(),
                    e.getIdentity(),
                    e.getAccessLevel(),
                    e.getOrigin() != null ? e.getOrigin().toLowerCase() : "human",
                    e.getPhone(),
                    e.getEmail(),
                    e.getAvatarUrl(),
                    e.getTenantId(),
                    e.isFounder(),
                    Boolean.TRUE.equals(e.isActive()),
                    e.getJoinDate(),
                    e.getCreatedAt(),
                    e.getVoicePrintId(),
                    e.getOauthProvider(),
                    e.getPermissionScopeType(),
                    e.getOwnerId()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to load employee from database, falling back to memory", e);
        }

        // 降级：从内存获取
        Optional<Employee> optEmp = employeeService.getEmployee(employeeId);
        if (optEmp.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Employee emp = optEmp.get();
        return ResponseEntity.ok(new EmployeeDetail(
            emp.getEmployeeId(),
            emp.getName(),
            emp.getDepartmentId(),
            emp.getDepartment(),
            emp.getTitle(),
            emp.getIdentity().name(),
            emp.getAccessLevel().name(),
            emp.getOrigin() != null ? emp.getOrigin().name().toLowerCase() : "human",
            emp.getPhone().orElse(null),
            emp.getEmail().orElse(null),
            null,
            null,
            false,
            emp.getStatus() == com.livingagent.core.employee.EmployeeStatus.ACTIVE,
            null,
            emp.getCreatedAt(),
            null,
            emp.getAuthProvider(),
            emp.getPermissionScopeType(),
            emp.getOwnerId()
        ));
    }

    @DeleteMapping("/employees/{employeeId}")
    public ResponseEntity<ApiResponse<Object>> deleteEmployee(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        AuthContext ctx = ctxOpt.get();
        String effectiveEmployeeId = headerEmployeeId != null && !headerEmployeeId.isBlank() ? headerEmployeeId : ctx.getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要 AdminBrain 路由权限"));
        }

        // 按当前 schema，每条公司任职是独立行（employeeId 唯一），删除仅移除该公司的任职记录，
        // 不影响该人类员工在其他公司的任职（符合“可跨公司任职”模型）。
        employeeService.deleteEmployee(employeeId);
        log.info("Enterprise admin {} deleted employee {}", ctx.getEmployeeId(), employeeId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("employeeId", employeeId)));
    }

    @PostMapping("/employees/{employeeId}/access-level")
    public ResponseEntity<Map<String, Object>> updateEmployeeAccessLevel(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        String newAccessLevel = request.get("accessLevel");
        if (newAccessLevel == null) {
            Map<String, Object> errorBody = new java.util.LinkedHashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", "bad_request");
            errorBody.put("errorDescription", "缺少 accessLevel 参数");
            return ResponseEntity.badRequest().body(errorBody);
        }

        AuthContext ctx = ctxOpt.get();
        String effectiveEmployeeId = headerEmployeeId != null && !headerEmployeeId.isBlank() ? headerEmployeeId : ctx.getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }
        log.info("Enterprise admin {} updating employee {} access level to {}",
            ctx.getEmployeeId(), employeeId, newAccessLevel);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "employeeId", employeeId,
            "newAccessLevel", newAccessLevel
        ));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentSummary>> getAllDepartments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var departments = dashboardService.getDepartmentSummaries().stream()
            .map(d -> new DepartmentSummary(d.code(), d.name(), d.memberCount(), d.brainCode(), null))
            .collect(Collectors.toList());

        return ResponseEntity.ok(departments);
    }

    @GetMapping("/system/status")
    public ResponseEntity<SystemStatus> getSystemStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        var summary = dashboardService.getEnterpriseSummary(null);

        return ResponseEntity.ok(new SystemStatus(
            summary.systemHealth().status(),
            "1.0.0",
            usedMemory / (1024 * 1024),
            totalMemory / (1024 * 1024),
            runtime.availableProcessors(),
            Thread.activeCount(),
            summary.employeeMetrics().digitalEmployees(),
            summary.departmentHealth().size()
        ));
    }

    @GetMapping("/identity-providers")
    public ResponseEntity<List<IdentityProviderInfo>> listIdentityProviders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "tenant_id", required = false) String tenantId) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var configs = systemConfigService.getProviderConfigs();
        List<IdentityProviderInfo> result = configs.values().stream()
            .filter(c -> tenantId == null || c.providerId().startsWith(tenantId))
            .map(c -> new IdentityProviderInfo(
                c.providerId(),
                c.name(),
                c.apiKey() != null && !c.apiKey().isEmpty(),
                c.enabled(),
                c.baseUrl(),
                c.apiSecret() != null && !c.apiSecret().isEmpty() ? "oauth2" : "api_key"
            ))
            .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/identity-providers")
    public ResponseEntity<Map<String, Object>> createIdentityProvider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody IdentityProviderRequest request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Creating identity provider: {}", request.providerId());
        systemConfigService.updateProviderConfig(
            request.providerId(),
            new SystemConfigService.ProviderConfigUpdateRequest(
                request.apiKey(),
                request.apiSecret(),
                request.baseUrl(),
                true
            )
        );
        return ResponseEntity.ok(Map.of("success", true, "providerId", request.providerId()));
    }

    @PostMapping("/identity-providers/oauth2")
    public ResponseEntity<Map<String, Object>> createOAuth2Provider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OAuth2ProviderRequest request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Creating OAuth2 identity provider: {}", request.providerId());
        systemConfigService.updateProviderConfig(
            request.providerId(),
            new SystemConfigService.ProviderConfigUpdateRequest(
                request.clientId(),
                request.clientSecret(),
                request.authorizationUrl() != null ? request.authorizationUrl() : request.baseUrl(),
                true
            )
        );
        return ResponseEntity.ok(Map.of("success", true, "providerId", request.providerId()));
    }

    @PutMapping("/identity-providers/{id}")
    public ResponseEntity<Map<String, Object>> updateIdentityProvider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody IdentityProviderRequest request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Updating identity provider: {}", id);
        systemConfigService.updateProviderConfig(
            id,
            new SystemConfigService.ProviderConfigUpdateRequest(
                request.apiKey(),
                request.apiSecret(),
                request.baseUrl(),
                request.enabled()
            )
        );
        return ResponseEntity.ok(Map.of("success", true, "providerId", id));
    }

    @PatchMapping("/identity-providers/{id}/oauth2")
    public ResponseEntity<Map<String, Object>> updateOAuth2Provider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody OAuth2ProviderRequest request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Updating OAuth2 identity provider: {}", id);
        systemConfigService.updateProviderConfig(
            id,
            new SystemConfigService.ProviderConfigUpdateRequest(
                request.clientId(),
                request.clientSecret(),
                request.authorizationUrl() != null ? request.authorizationUrl() : request.baseUrl(),
                request.enabled()
            )
        );
        return ResponseEntity.ok(Map.of("success", true, "providerId", id));
    }

    @DeleteMapping("/identity-providers/{id}")
    public ResponseEntity<Map<String, Object>> deleteIdentityProvider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Deleting identity provider: {}", id);
        systemConfigService.removeProviderConfig(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/tenant-quotas")
    public ResponseEntity<Map<String, Object>> getTenantQuotas(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var settings = systemConfigService.getSettings();
        Map<String, Object> quotas = (Map<String, Object>) settings.getOrDefault("tenantQuotas", Map.of(
            "messageLimit", 10000,
            "messagePeriod", "monthly",
            "maxAgents", 10,
            "agentTTL", 30,
            "dailyLLMCalls", 1000,
            "minHeartbeatIntervalMs", 30000,
            "defaultMaxTriggers", 50,
            "minPollIntervalMinutes", 5,
            "maxWebhookRatePerMinute", 60
        ));
        return ResponseEntity.ok(quotas);
    }

    @PatchMapping("/tenant-quotas")
    public ResponseEntity<Map<String, Object>> updateTenantQuotas(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        log.info("Updating tenant quotas: {}", request);
        systemConfigService.getSettings().put("tenantQuotas", request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/invitation-codes")
    public ResponseEntity<Map<String, Object>> listInvitationCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var settings = systemConfigService.getSettings();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes = (List<Map<String, Object>>) settings.getOrDefault("invitationCodes", new ArrayList<>());

        List<Map<String, Object>> filtered = codes.stream()
            .filter(c -> tenantId == null || tenantId.equals(c.get("tenantId")))
            .filter(c -> status == null || status.equals(c.get("status")))
            .toList();

        return ResponseEntity.ok(Map.of(
            "codes", filtered,
            "total", filtered.size()
        ));
    }

    @PostMapping("/invitation-codes")
    public ResponseEntity<Map<String, Object>> createInvitationCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        String code = UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String tenantId = (String) request.getOrDefault("tenantId", "tenant_default");

        // 获取公司名称
        String companyName = "未知公司";
        try {
            var tenantInfo = systemConfigService.getTenant(tenantId);
            if (tenantInfo != null) {
                companyName = tenantInfo.name();
            }
        } catch (Exception e) {
            log.warn("Failed to get tenant name for tenantId: {}", tenantId);
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("id", UUID.randomUUID().toString());
        entry.put("code", code);
        entry.put("tenantId", tenantId);
        entry.put("companyName", companyName);
        entry.put("role", request.getOrDefault("role", "MEMBER"));
        entry.put("maxUses", request.getOrDefault("maxUses", 1));
        entry.put("usedCount", 0);
        entry.put("status", "active");
        entry.put("expiresAt", request.get("expiresAt"));
        entry.put("createdAt", java.time.Instant.now().toString());
        entry.put("createdBy", ctxOpt.get().getEmployeeId());

        // 生成完整邀请链接
        String inviteUrl = String.format("%s/sso/entry?invite_code=%s&company=%s",
            frontendBaseUrl, code, java.net.URLEncoder.encode(companyName, java.nio.charset.StandardCharsets.UTF_8));
        entry.put("inviteUrl", inviteUrl);

        var settings = systemConfigService.getSettings();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes = (List<Map<String, Object>>) settings.getOrDefault("invitationCodes", new ArrayList<>());
        codes.add(entry);
        settings.put("invitationCodes", codes);

        log.info("Created invitation code: {} for tenant: {} (company: {})", code, tenantId, companyName);
        return ResponseEntity.ok(Map.of("success", true, "code", code, "inviteUrl", inviteUrl, "entry", entry));
    }

    @DeleteMapping("/invitation-codes/{id}")
    public ResponseEntity<Map<String, Object>> deleteInvitationCode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var settings = systemConfigService.getSettings();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes = (List<Map<String, Object>>) settings.getOrDefault("invitationCodes", new ArrayList<>());

        codes.removeIf(c -> id.equals(c.get("id")));
        settings.put("invitationCodes", codes);

        log.info("Deleted invitation code: {}", id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/invitation-codes/export")
    public ResponseEntity<String> exportInvitationCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        var settings = systemConfigService.getSettings();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes = (List<Map<String, Object>>) settings.getOrDefault("invitationCodes", new ArrayList<>());

        StringBuilder csv = new StringBuilder("id,code,tenantId,role,maxUses,usedCount,status,createdAt\n");
        for (Map<String, Object> c : codes) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                c.get("id"), c.get("code"), c.get("tenantId"), c.get("role"),
                c.get("maxUses"), c.get("usedCount"), c.get("status"), c.get("createdAt")));
        }

        return ResponseEntity.ok(csv.toString());
    }

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean isEnterpriseAdmin(AuthContext ctx) {
        return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
    }

    public record DashboardOverview(
        int departmentCount,
        int digitalEmployeeCount,
        int totalEmployeeCount,
        double systemHealthScore,
        List<DepartmentMetric> departments,
        List<SystemAlert> alerts
    ) {}

    public record DepartmentMetric(
        String code,
        String name,
        int memberCount,
        double healthScore,
        String status
    ) {}

    public record SystemAlert(
        String level,
        String title,
        String message
    ) {}

    public record EmployeeSummary(
        String employeeId,
        String name,
        String department,
        String departmentName,
        String position,
        String identity,
        String accessLevel,
        String employeeType,  // HUMAN / DIGITAL
        String origin,        // FIXED / PERSONAL / HUMAN / EVOLVED
        String phone,
        String email,
        String avatarUrl,
        String tenantId,
        boolean isFounder,
        boolean active,
        Object joinDate,
        Object createdAt
    ) {}

    public record EmployeeDetail(
        String employeeId,
        String name,
        String department,
        String departmentName,
        String position,
        String identity,
        String accessLevel,
        String origin,
        String phone,
        String email,
        String avatarUrl,
        String tenantId,
        boolean founder,
        boolean active,
        Object joinDate,
        Object createdAt,
        String voicePrintId,
        String oauthProvider,
        String permissionScopeType,
        String ownerId
    ) {}

    public record DepartmentSummary(
        String code,
        String name,
        int memberCount,
        String brain,
        String managerId
    ) {}

    public record SystemStatus(
        String status,
        String version,
        long usedMemoryMB,
        long totalMemoryMB,
        int availableProcessors,
        int activeThreads,
        int digitalEmployeeCount,
        int departmentCount
    ) {}

    public record IdentityProviderInfo(
        String id,
        String name,
        boolean apiKeyConfigured,
        boolean enabled,
        String baseUrl,
        String type
    ) {}

    public record IdentityProviderRequest(
        String providerId,
        String apiKey,
        String apiSecret,
        String baseUrl,
        Boolean enabled
    ) {}

    public record OAuth2ProviderRequest(
        String providerId,
        String clientId,
        String clientSecret,
        String authorizationUrl,
        String baseUrl,
        Boolean enabled
    ) {}
}
