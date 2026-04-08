package com.livingagent.gateway.controller;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.gateway.service.SystemConfigService;
import com.livingagent.gateway.service.SystemConfigService.*;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final FounderService founderService;
    private final SystemConfigService configService;
    private final UnifiedAuthService authService;
    private final EnterpriseEmployeeService employeeService;
    private final PhoneAuthController phoneAuthController;

    public SystemController(
            FounderService founderService,
            SystemConfigService configService,
            UnifiedAuthService authService,
            EnterpriseEmployeeService employeeService,
            PhoneAuthController phoneAuthController) {
        this.founderService = founderService;
        this.configService = configService;
        this.authService = authService;
        this.employeeService = employeeService;
        this.phoneAuthController = phoneAuthController;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SystemStatus>> getSystemStatus() {
        SystemStatus status = new SystemStatus(
            founderService.hasFounder(),
            founderService.isFirstUser(),
            configService.isConfigured(),
            configService.getConfiguredProviders()
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResult>> registerFounder(
            @RequestBody RegistrationRequest request) {
        
        if (founderService.hasFounder()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("already_registered", "系统已有董事长，无法重复注册"));
        }

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("invalid_name", "姓名不能为空"));
        }

        String employeeId = "founder_" + UUID.randomUUID().toString().substring(0, 8);

        AuthContext founder = new AuthContext();
        founder.setEmployeeId(employeeId);
        founder.setName(request.name());
        founder.setEmail(request.email());
        founder.setPhone(request.phone());
        founder.setIdentity(UserIdentity.INTERNAL_CHAIRMAN);
        founder.setAccessLevel(AccessLevel.FULL);
        founder.setFounder(true);
        founder.setPosition("董事长");
        founder.setJoinDate(Instant.now());
        founder.setActive(true);

        AuthContext savedFounder = employeeService.createAuthContext(founder);

        // 注册手机号到手机认证控制器
        if (request.phone() != null && !request.phone().isBlank()) {
            phoneAuthController.registerEmployeePhone(savedFounder, request.phone());
        }

        founderService.markFounderRegistered();

        if (request.companyName() != null && !request.companyName().isBlank()) {
            configService.updateSystemConfig(new SystemConfigUpdateRequest(
                request.companyName(), null, null, null
            ));
        }

        log.info("Registered founder in database: {} ({})", savedFounder.getName(), savedFounder.getEmail());

        AuthResult authResult = authService.createInternalSession(savedFounder);
        AuthSession session = authResult.session();

        RegistrationResult result = new RegistrationResult(
                savedFounder.getEmployeeId(),
                savedFounder.getName(),
                savedFounder.getIdentity().name(),
                savedFounder.getAccessLevel().name(),
                session.sessionId()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<SystemConfig>> getSystemConfig() {
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("not_initialized", "系统尚未初始化，请先注册董事长"));
        }

        SystemConfig config = configService.getSystemConfig();
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<SystemConfig>> updateSystemConfig(
            @RequestBody SystemConfigUpdateRequest request) {
        
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("not_initialized", "系统尚未初始化"));
        }

        SystemConfig config = configService.updateSystemConfig(request);
        log.info("System config updated by founder");
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping("/config/providers")
    public ResponseEntity<ApiResponse<List<ProviderConfig>>> getProviderConfigs() {
        List<ProviderConfig> providers = configService.getAvailableProviders();
        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    @PutMapping("/config/providers/{providerId}")
    public ResponseEntity<ApiResponse<ProviderConfig>> updateProviderConfig(
            @PathVariable String providerId,
            @RequestBody ProviderConfigUpdateRequest request) {
        
        if (!founderService.hasFounder()) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("not_initialized", "系统尚未初始化"));
        }

        ProviderConfig config = configService.updateProviderConfig(providerId, request);
        log.info("Provider config updated: {}", providerId);
        return ResponseEntity.ok(ApiResponse.success(config));
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
            String sessionId
    ) {}
}
