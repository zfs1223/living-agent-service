package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.SystemConfigService;
import com.livingagent.gateway.service.SystemConfigService.*;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    /** access_token Cookie 有效期：30 分钟 */
    private static final long ACCESS_TOKEN_COOKIE_MAX_AGE_SEC = 30 * 60L;

    /** refresh_token Cookie 有效期：7 天 */
    private static final long REFRESH_TOKEN_COOKIE_MAX_AGE_SEC = 7 * 24 * 60 * 60L;

    /** Cookie 路径 */
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/api";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth/refresh";

    private final FounderService founderService;
    private final SystemConfigService configService;
    private final UnifiedAuthService authService;
    private final EnterpriseEmployeeService employeeService;
    private final PhoneAuthController phoneAuthController;
    private final AccessGateService accessGateService;

    /** 是否启用 Cookie 的 Secure 标志（生产环境 true，开发环境 false） */
    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    public SystemController(
            FounderService founderService,
            SystemConfigService configService,
            UnifiedAuthService authService,
            EnterpriseEmployeeService employeeService,
            PhoneAuthController phoneAuthController,
            AccessGateService accessGateService) {
        this.founderService = founderService;
        this.configService = configService;
        this.authService = authService;
        this.employeeService = employeeService;
        this.phoneAuthController = phoneAuthController;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SystemStatus>> getSystemStatus(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        SystemStatus status = new SystemStatus(
            founderService.hasFounder(),
            founderService.isFirstUser(),
            configService.isConfigured(),
            configService.getConfiguredProviders()
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResult>> registerFounder(
            @RequestBody RegistrationRequest request,
            HttpServletResponse httpResponse) {

        founderService.refreshFromDatabase();

        if (founderService.hasFounder()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("already_registered", "系统已有董事长，无法重复注册"));
        }

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("invalid_name", "姓名不能为空"));
        }

        // companyName 必填：董事长注册时必须提供公司名称
        if (request.companyName() == null || request.companyName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("invalid_company_name", "公司名称不能为空"));
        }

        String employeeId = "founder_" + UUID.randomUUID().toString().substring(0, 8);

        AuthContext founder = new AuthContext();
        founder.setEmployeeId(employeeId);
        founder.setName(request.name());
        founder.setEmail(request.email());
        founder.setPhone(request.phone());
        founder.setIdentity(UserIdentity.INTERNAL_ENTERPRISE);
        founder.setAccessLevel(AccessLevel.FULL);
        founder.setFounder(true);
        founder.setPosition("董事长");
        founder.setJoinDate(Instant.now());
        founder.setActive(true);

        AuthContext savedFounder = employeeService.createAuthContext(founder);

        if (request.phone() != null && !request.phone().isBlank()) {
            phoneAuthController.registerEmployeePhone(savedFounder, request.phone());
        }

        founderService.markFounderRegistered();

        // 必须创建 tenantId (因为 companyName 已验证必填)
        String tenantId = "tenant_" + UUID.randomUUID().toString().substring(0, 8);
        configService.createTenantWithCompany(tenantId, request.companyName(), savedFounder.getEmployeeId());
        configService.updateSystemConfig(new SystemConfigUpdateRequest(
            request.companyName(), null, null, null
        ));

        // 更新 founder 的 tenantId
        savedFounder.setTenantId(tenantId);
        employeeService.updateTenantId(savedFounder.getEmployeeId(), tenantId);

        log.info("Registered founder in database: {} ({})", savedFounder.getName(), savedFounder.getEmail());

        AuthResult authResult = authService.createInternalSession(savedFounder);
        AuthSession session = authResult.session();

        // 设置 HttpOnly Cookie
        setTokenCookies(httpResponse, session.sessionId(), session.sessionId());

        RegistrationResult result = new RegistrationResult(
                savedFounder.getEmployeeId(),
                savedFounder.getName(),
                savedFounder.getIdentity().name(),
                savedFounder.getAccessLevel().name(),
                session.sessionId(),
                tenantId
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<SystemConfig>> getSystemConfig(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        founderService.refreshFromDatabase();
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("not_initialized", "系统尚未初始化，请先注册董事长"));
        }

        SystemConfig config = configService.getSystemConfig();
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<SystemConfig>> updateSystemConfig(
            @RequestBody SystemConfigUpdateRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        founderService.refreshFromDatabase();
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("not_initialized", "系统尚未初始化"));
        }
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        SystemConfig config = configService.updateSystemConfig(request);
        log.info("System config updated by founder");
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @GetMapping("/config/providers")
    public ResponseEntity<ApiResponse<List<ProviderConfig>>> getProviderConfigs(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        List<ProviderConfig> providers = configService.getAvailableProviders();
        return ResponseEntity.ok(ApiResponse.ok(providers));
    }

    @PutMapping("/config/providers/{providerId}")
    public ResponseEntity<ApiResponse<ProviderConfig>> updateProviderConfig(
            @PathVariable String providerId,
            @RequestBody ProviderConfigUpdateRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("not_initialized", "系统尚未初始化"));
        }
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        ProviderConfig config = configService.updateProviderConfig(providerId, request);
        log.info("Provider config updated: {}", providerId);
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthSummary>> getHealth(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Runtime runtime = Runtime.getRuntime();
        HealthSummary health = new HealthSummary(
                "UP",
                "running",
                runtime.availableProcessors(),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                runtime.totalMemory() / (1024 * 1024),
                configService.isConfigured(),
                founderService.hasFounder()
        );
        return ResponseEntity.ok(ApiResponse.ok(health));
    }

    @GetMapping("/health/detail")
    public ResponseEntity<ApiResponse<HealthDetail>> getHealthDetail(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Runtime runtime = Runtime.getRuntime();
        HealthDetail detail = new HealthDetail(
                "UP",
                "1.0.0",
                runtime.availableProcessors(),
                runtime.totalMemory() / (1024 * 1024),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                configService.getConfiguredProviders(),
                founderService.hasFounder(),
                Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    public record HealthSummary(
            String status,
            String state,
            int availableProcessors,
            long usedMemoryMB,
            long totalMemoryMB,
            boolean configured,
            boolean hasFounder
    ) {}

    public record HealthDetail(
            String status,
            String version,
            int availableProcessors,
            long totalMemoryMB,
            long usedMemoryMB,
            List<String> configuredProviders,
            boolean hasFounder,
            String timestamp
    ) {}

    public record SystemStatus(
            boolean hasFounder,
            boolean isFirstUser,
            boolean isConfigured,
            List<String> configuredProviders
    ) {}

    public record RegistrationRequest(
            String name,
            String phone,
            String email,
            String companyName
    ) {}

    public record RegistrationResult(
            String employeeId,
            String name,
            String identity,
            String accessLevel,
            String sessionId,
            String tenantId
    ) {}

    /**
     * 设置 HttpOnly Cookie：access_token 和 refresh_token
     */
    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(ACCESS_TOKEN_COOKIE_MAX_AGE_SEC)
                .build();

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(REFRESH_TOKEN_COOKIE_MAX_AGE_SEC)
                .build();

        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
    }
}
