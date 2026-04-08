package com.livingagent.gateway.controller;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public TenantController(
            EnterpriseEmployeeService employeeService,
            UnifiedAuthService authService
    ) {
        this.employeeService = employeeService;
        this.authService = authService;
    }

    @GetMapping("/registration-config")
    public ResponseEntity<ApiResponse<RegistrationConfig>> getRegistrationConfig() {
        RegistrationConfig config = new RegistrationConfig(true);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping("/self-create")
    public ResponseEntity<ApiResponse<TenantCreateResult>> selfCreateTenant(
            @RequestBody TenantCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        log.info("Self-create tenant request: {}", request.name());

        AuthContext currentUser = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String sessionId = authorization.substring(7);
            Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
            if (sessionOpt.isPresent()) {
                currentUser = sessionOpt.get().authContext();
            }
        }

        if (currentUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String tenantId = "tenant_" + UUID.randomUUID().toString().substring(0, 8);

        TenantCreateResult result = new TenantCreateResult(
                tenantId,
                request.name(),
                currentUser.getEmployeeId(),
                "admin_" + UUID.randomUUID().toString().substring(0, 8)
        );

        log.info("Tenant created: {} by user: {}", request.name(), currentUser.getName());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<TenantJoinResult>> joinTenant(
            @RequestBody TenantJoinRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        log.info("Join tenant request with code: {}", request.invitation_code());

        AuthContext currentUser = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String sessionId = authorization.substring(7);
            Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
            if (sessionOpt.isPresent()) {
                currentUser = sessionOpt.get().authContext();
            }
        }

        if (currentUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        TenantJoinResult result = new TenantJoinResult(
                "tenant_joined",
                "示例公司",
                currentUser.getEmployeeId()
        );

        log.info("User {} joined tenant", currentUser.getName());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/resolve-by-domain")
    public ResponseEntity<ApiResponse<TenantInfo>> resolveByDomain(
            @RequestParam String domain
    ) {
        log.info("Resolve tenant by domain: {}", domain);

        TenantInfo info = new TenantInfo(
                "tenant_default",
                "Living Agent",
                domain
        );

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDetail>> getTenant(@PathVariable String tenantId) {
        log.info("Getting tenant: {}", tenantId);

        TenantDetail detail = new TenantDetail(
                tenantId,
                "Living Agent 企业",
                "Living Agent Enterprise",
                "智能企业管理平台",
                "https://living-agent.example.com",
                Instant.now(),
                true
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantDetail>> updateTenant(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> request
    ) {
        log.info("Updating tenant: {}", tenantId);

        TenantDetail detail = new TenantDetail(
                tenantId,
                (String) request.getOrDefault("name", "Living Agent 企业"),
                (String) request.getOrDefault("name_en", "Living Agent Enterprise"),
                (String) request.getOrDefault("description", "智能企业管理平台"),
                (String) request.getOrDefault("website", "https://living-agent.example.com"),
                Instant.now(),
                true
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    // Admin endpoints
    @GetMapping("/admin/companies")
    public ResponseEntity<ApiResponse<List<CompanyInfo>>> listCompanies() {
        log.debug("Listing all companies (admin)");

        List<CompanyInfo> companies = List.of(
                new CompanyInfo("tenant_001", "示例公司1", true),
                new CompanyInfo("tenant_002", "示例公司2", true),
                new CompanyInfo("tenant_003", "示例公司3", false)
        );

        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @PostMapping("/admin/companies/{id}/toggle")
    public ResponseEntity<ApiResponse<CompanyInfo>> toggleCompany(@PathVariable String id) {
        log.info("Toggling company: {}", id);

        CompanyInfo company = new CompanyInfo(id, "示例公司", true);
        return ResponseEntity.ok(ApiResponse.success(company));
    }

    @GetMapping("/admin/platform-settings")
    public ResponseEntity<ApiResponse<PlatformSettings>> getPlatformSettings() {
        log.debug("Getting platform settings (admin)");

        PlatformSettings settings = new PlatformSettings(
                true,
                1000,
                "v1.0.0"
        );

        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
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

    public record TenantInfo(
            String tenant_id,
            String name,
            String domain
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
            String name,
            boolean active
    ) {}

    public record PlatformSettings(
            boolean registration_enabled,
            int max_tenants,
            String version
    ) {}
}
