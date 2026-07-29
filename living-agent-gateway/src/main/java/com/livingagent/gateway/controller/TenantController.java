package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.database.entity.TenantEntity;
import com.livingagent.core.database.service.TenantService;
import com.livingagent.gateway.service.SystemConfigService;
import com.livingagent.gateway.service.SystemConfigService.TenantInfo;
import com.livingagent.gateway.controller.common.ApiResponse;  // 添加 import
import com.livingagent.core.tenant.feedback.TenantHealthMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final EnterpriseEmployeeService employeeService;
    private final UnifiedAuthService authService;
    private final SystemConfigService configService;
    private final AccessGateService accessGateService;
    private final TenantService tenantService;

    @Autowired(required = false)
    private TenantHealthMonitor tenantHealthMonitor;

    public TenantController(
            EnterpriseEmployeeService employeeService,
            UnifiedAuthService authService,
            SystemConfigService configService,
            AccessGateService accessGateService,
            TenantService tenantService
    ) {
        this.employeeService = employeeService;
        this.authService = authService;
        this.configService = configService;
        this.accessGateService = accessGateService;
        this.tenantService = tenantService;
    }

    @GetMapping("/registration-config")
    public ResponseEntity<ApiResponse<RegistrationConfig>> getRegistrationConfig() {
        RegistrationConfig config = new RegistrationConfig(true);
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PostMapping("/self-create")
    public ResponseEntity<ApiResponse<TenantCreateResult>> selfCreateTenant(
            @RequestBody TenantCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.info("Self-create tenant request: {}", request.name());

        AuthContext currentUser = null;
        String sessionId = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessionId = authorization.substring(7);
            Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
            if (sessionOpt.isPresent()) {
                currentUser = sessionOpt.get().authContext();
            }
        }

        if (currentUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        // 只有董事长才能创建公司
        if (!currentUser.isFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "只有董事长才能创建公司"));
        }

        String tenantId = "tenant_" + UUID.randomUUID().toString().substring(0, 8);

        // Persist tenant to SystemConfigService
        configService.createTenantWithCompany(tenantId, request.name(), currentUser.getEmployeeId());

        // Also update system config company name
        com.livingagent.gateway.service.SystemConfigService.SystemConfigUpdateRequest updateReq =
            new com.livingagent.gateway.service.SystemConfigService.SystemConfigUpdateRequest(
                request.name(), null, null, null
            );
        configService.updateSystemConfig(updateReq);

        if (sessionId != null) {
            authService.updateSessionTenantId(sessionId, tenantId);
        }
        
        employeeService.updateTenantId(currentUser.getEmployeeId(), tenantId);

        TenantCreateResult result = new TenantCreateResult(
                tenantId,
                request.name(),
                currentUser.getEmployeeId(),
                "admin_" + UUID.randomUUID().toString().substring(0, 8)
        );

        log.info("Tenant created and persisted: {} by user: {}", request.name(), currentUser.getName());

        // 闭环50: 租户创建时记录配额使用和活动
        if (tenantHealthMonitor != null) {
            tenantHealthMonitor.recordQuotaUsage(tenantId, 0.1);
            tenantHealthMonitor.recordActivity(tenantId, "tenant_created");
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<TenantJoinResult>> joinTenant(
            @RequestBody TenantJoinRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.info("Join tenant request with code: {}", request.invitation_code());

        AuthContext currentUser = null;
        String sessionId = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessionId = authorization.substring(7);
            Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
            if (sessionOpt.isPresent()) {
                currentUser = sessionOpt.get().authContext();
            }
        }

        if (currentUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        // 从系统配置中查找邀请码
        var settings = configService.getSettings();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> codes = (List<Map<String, Object>>) settings.getOrDefault("invitationCodes", new ArrayList<>());

        Map<String, Object> matchedCode = null;
        for (Map<String, Object> c : codes) {
            if (request.invitation_code().equals(c.get("code"))) {
                matchedCode = c;
                break;
            }
        }

        if (matchedCode == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("invalid_code", "邀请码不存在"));
        }

        // 验证邀请码状态
        String codeStatus = (String) matchedCode.get("status");
        if (!"active".equals(codeStatus)) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.err("code_inactive", "邀请码已失效"));
        }

        // 验证是否过期
        String expiresAt = (String) matchedCode.get("expiresAt");
        if (expiresAt != null && !expiresAt.isBlank()) {
            try {
                Instant expiry = Instant.parse(expiresAt);
                if (Instant.now().isAfter(expiry)) {
                    matchedCode.put("status", "expired");
                    settings.put("invitationCodes", codes);
                    return ResponseEntity.status(400)
                            .body(ApiResponse.err("code_expired", "邀请码已过期"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse expiresAt: {}", expiresAt);
            }
        }

        // 验证使用次数
        int maxUses = toInt(matchedCode.get("maxUses"), 1);
        int usedCount = toInt(matchedCode.get("usedCount"), 0);
        if (usedCount >= maxUses) {
            matchedCode.put("status", "exhausted");
            settings.put("invitationCodes", codes);
            return ResponseEntity.status(400)
                    .body(ApiResponse.err("code_exhausted", "邀请码使用次数已用尽"));
        }

        // 获取邀请码对应的 tenantId
        String tenantId = (String) matchedCode.get("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.err("invalid_tenant", "邀请码未关联有效的公司"));
        }

        // 获取公司名称
        TenantInfo tenantInfo = configService.getTenant(tenantId);
        String tenantName = tenantInfo != null ? tenantInfo.name() : "未知公司";

        // 更新邀请码使用次数
        matchedCode.put("usedCount", usedCount + 1);
        if (usedCount + 1 >= maxUses) {
            matchedCode.put("status", "exhausted");
        }
        settings.put("invitationCodes", codes);

        // 更新用户会话和 tenantId
        if (sessionId != null) {
            authService.updateSessionTenantId(sessionId, tenantId);
        }
        employeeService.updateTenantId(currentUser.getEmployeeId(), tenantId);

        TenantJoinResult result = new TenantJoinResult(
                tenantId,
                tenantName,
                currentUser.getEmployeeId()
        );

        log.info("User {} joined tenant {} using invitation code {}", currentUser.getName(), tenantId, request.invitation_code());

        // 闭环50: 用户加入租户时记录活动
        if (tenantHealthMonitor != null) {
            tenantHealthMonitor.recordActivity(tenantId, "user_joined");
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 安全地将 Object 转换为 int */
    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @GetMapping("/resolve-by-domain")
    public ResponseEntity<ApiResponse<TenantInfo>> resolveByDomain(
            @RequestParam String domain,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.info("Resolve tenant by domain: {}", domain);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        TenantInfo info = new TenantInfo(
                "tenant_default",
                "Living Agent",
                "Living Agent",
                "智能企业管理平台",
                "https://living-agent.example.com",
                java.time.Instant.now(),
                true,
                "system"
        );

        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDetail>> getTenant(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Getting tenant: {}", tenantId);

        TenantInfo info = configService.getTenant(tenantId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        TenantDetail detail = new TenantDetail(
                tenantId,
                info.name(),
                info.nameEn(),
                info.description(),
                info.website(),
                info.createdAt(),
                info.active()
        );

        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDetail>> updateTenant(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating tenant: {}", tenantId);

        String name = (String) request.getOrDefault("name", "Living Agent 企业");
        TenantInfo updated = configService.updateTenant(tenantId, name);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }

        // Also update system config company name for consistency
        com.livingagent.gateway.service.SystemConfigService.SystemConfigUpdateRequest updateReq =
            new com.livingagent.gateway.service.SystemConfigService.SystemConfigUpdateRequest(
                name, null, null, null
            );
        configService.updateSystemConfig(updateReq);

        TenantDetail detail = new TenantDetail(
                tenantId,
                updated.name(),
                updated.nameEn(),
                updated.description(),
                updated.website(),
                updated.createdAt(),
                updated.active()
        );

        // 闭环50: 租户更新时记录活动
        if (tenantHealthMonitor != null) {
            tenantHealthMonitor.recordActivity(tenantId, "tenant_updated");
        }

        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    // Admin endpoints
    @GetMapping("/admin/companies")
    public ResponseEntity<ApiResponse<List<CompanyInfo>>> listCompanies(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Listing all companies from database");

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        // 从数据库获取真实公司列表
        List<TenantEntity> tenants = tenantService.findAllActive();
        List<CompanyInfo> companies = tenants.stream()
                .map(t -> new CompanyInfo(t.getTenantId(), t.getTenantId(), t.getName(), t.isActive()))
                .toList();

        // 如果数据库无数据，返回默认租户
        if (companies.isEmpty()) {
            companies = List.of(new CompanyInfo("tenant_default", "tenant_default", "Living Agent", true));
        }

        return ResponseEntity.ok(ApiResponse.ok(companies));
    }

    /**
     * 新建公司（董事长可创建多个公司）
     * POST /api/tenants/admin/companies
     */
    @PostMapping("/admin/companies")
    public ResponseEntity<ApiResponse<CompanyInfo>> createCompany(
            @RequestBody TenantCreateRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.info("Creating new company: {}", request.name());

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_name", "公司名称不能为空"));
        }

        String tenantId = "tenant_" + UUID.randomUUID().toString().substring(0, 8);
        TenantEntity entity = tenantService.createTenant(tenantId, request.name(), employeeId);
        CompanyInfo company = new CompanyInfo(entity.getTenantId(), entity.getTenantId(), entity.getName(), entity.isActive());
        return ResponseEntity.ok(ApiResponse.ok(company));
    }

    @PostMapping("/admin/companies/{id}/toggle")
    public ResponseEntity<ApiResponse<CompanyInfo>> toggleCompany(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.info("Toggling company: {}", id);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        TenantEntity saved = tenantService.toggleActive(id);
        if (saved == null) {
            return ResponseEntity.badRequest().body(ApiResponse.err("not_found", "公司不存在"));
        }
        CompanyInfo company = new CompanyInfo(saved.getTenantId(), saved.getTenantId(), saved.getName(), saved.isActive());
        return ResponseEntity.ok(ApiResponse.ok(company));
    }

    @GetMapping("/admin/platform-settings")
    public ResponseEntity<ApiResponse<PlatformSettings>> getPlatformSettings(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting platform settings (admin)");

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        PlatformSettings settings = new PlatformSettings(
                true,
                1000,
                "v1.0.0"
        );

        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    public record RegistrationConfig(boolean allow_self_create_company) {}

    public record TenantCreateRequest(String name) {}

    public record TenantCreateResult(
            String tenant_id,
            String name,
            String owner_id,
            String admin_user_id
    ) {}

    public record TenantJoinRequest(String invitation_code) {}

    public record TenantJoinResult(
            String tenant_id,
            String tenant_name,
            String user_id
    ) {}

    public record TenantDetail(
            String tenant_id,
            String name,
            String name_en,
            String description,
            String website,
            Instant created_at,
            boolean active
    ) {}

    public record CompanyInfo(
            String id,
            String tenantId,
            String name,
            boolean active
    ) {}

    public record PlatformSettings(
            boolean registration_enabled,
            int max_tenants,
            String version
    ) {}
}
