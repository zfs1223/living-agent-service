package com.livingagent.gateway.controller;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.SecurityIdentity;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.EmployeeAuthService;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.auth.AuthMetricsService;
import com.livingagent.core.security.auth.PhoneVerificationService;
import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class PhoneAuthController {

    private static final Logger log = LoggerFactory.getLogger(PhoneAuthController.class);

    /** access_token Cookie 有效期：30 分钟 */
    private static final long ACCESS_TOKEN_COOKIE_MAX_AGE_SEC = 30 * 60L;

    /** refresh_token Cookie 有效期：7 天 */
    private static final long REFRESH_TOKEN_COOKIE_MAX_AGE_SEC = 7 * 24 * 60 * 60L;

    /** Cookie 路径：access_token 覆盖所有 /api 路径 */
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/api";

    /** Cookie 路径：refresh_token 仅限刷新接口 */
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth/refresh";

    private final PhoneVerificationService phoneVerificationService;
    private final UnifiedAuthService authService;
    private final FounderService founderService;
    private final EnterpriseEmployeeService employeeService;
    private final EmployeeAuthService employeeAuthService;
    private final EnterpriseEmployeeRepository employeeRepository;
    private final Map<String, SecurityIdentity> phoneEmployeeMap = new ConcurrentHashMap<>();
    private final AccessGateService accessGateService;
    private final AuthMetricsService authMetricsService;

    /** 是否启用 Cookie 的 Secure 标志（生产环境 true，开发环境 false） */
    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    public PhoneAuthController(
            PhoneVerificationService phoneVerificationService,
            UnifiedAuthService authService,
            FounderService founderService,
            EnterpriseEmployeeService employeeService,
            EmployeeAuthService employeeAuthService,
            EnterpriseEmployeeRepository employeeRepository,
            AccessGateService accessGateService,
            AuthMetricsService authMetricsService
    ) {
        this.phoneVerificationService = phoneVerificationService;
        this.authService = authService;
        this.founderService = founderService;
        this.employeeService = employeeService;
        this.employeeAuthService = employeeAuthService;
        this.employeeRepository = employeeRepository;
        this.accessGateService = accessGateService;
        this.authMetricsService = authMetricsService;
    }

    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<SendSmsResponse>> sendSmsCode(
            @RequestBody SendSmsRequest request
    ) {
        log.info("Sending SMS code to: {}", maskPhone(request.phone()));

        if (!phoneVerificationService.isValidPhoneFormat(request.phone())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("invalid_phone", "手机号格式不正确"));
        }

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        if ("login".equals(request.type()) || "bind".equals(request.type())) {
            SecurityIdentity employee = findEmployeeByPhone(normalizedPhone);
            if (employee == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.err("phone_not_registered", "该手机号未绑定企业员工，请联系管理员添加"));
            }
        }

        PhoneVerificationService.SendResult result = phoneVerificationService.sendVerificationCode(normalizedPhone);

        if (!result.success()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("send_failed", result.error()));
        }

        return ResponseEntity.ok(ApiResponse.ok(new SendSmsResponse(
                "验证码已发送",
                300,
                result.code()
        )));
    }

    @PostMapping("/phone/login")
    public ResponseEntity<ApiResponse<?>> phoneLogin(
            @RequestBody PhoneLoginRequest request,
            HttpServletResponse httpResponse
    ) {
        log.info("Phone login attempt: {}", maskPhone(request.phone()));

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        // 验证验证码
        PhoneVerificationService.VerifyResult verifyResult = phoneVerificationService.verifyCode(normalizedPhone, request.code());
        if (!verifyResult.isSuccess()) {
            authMetricsService.recordFailure("phone_login", "PhoneAuthController", "invalid_code");
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("invalid_code", verifyResult.error()));
        }

        // 查找该手机号所有员工记录
        List<EnterpriseEmployeeEntity> employees = employeeRepository.findAllByPhone(normalizedPhone);
        if (employees.isEmpty()) {
            authMetricsService.recordFailure("phone_login", "PhoneAuthController", "phone_not_registered");
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("phone_not_registered", "该手机号未绑定企业员工，请联系管理员添加"));
        }

        // 过滤活跃员工
        List<EnterpriseEmployeeEntity> activeEmployees = employees.stream()
                .filter(EnterpriseEmployeeEntity::isActive)
                .toList();
        if (activeEmployees.isEmpty()) {
            authMetricsService.recordFailure("phone_login", "PhoneAuthController", "employee_inactive");
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("employee_inactive", "员工账号已停用"));
        }

        // 判断是否有多个公司
        List<String> tenantIds = activeEmployees.stream()
                .map(EnterpriseEmployeeEntity::getTenantId)
                .distinct()
                .toList();

        if (tenantIds.size() > 1) {
            // 多个公司：返回公司列表
            List<TenantOption> tenantOptions = activeEmployees.stream()
                    .map(e -> new TenantOption(
                            e.getTenantId(),
                            e.getDepartmentName() != null ? e.getDepartmentName() : e.getTenantId(),
                            e.getName(),
                            e.isFounder()
                    ))
                    .toList();
            log.info("Phone login: {} has {} tenants, requiring selection", maskPhone(normalizedPhone), tenantIds.size());
            return ResponseEntity.ok(ApiResponse.ok(new TenantSelectionResponse(
                    "tenant_required", maskPhone(normalizedPhone), tenantOptions
            )));
        }

        // 只有一个公司：直接登录
        EnterpriseEmployeeEntity empEntity = activeEmployees.get(0);
        SecurityIdentity employee = toSecurityIdentity(empEntity, normalizedPhone);

        // 缓存到 phoneEmployeeMap
        phoneEmployeeMap.put(normalizedPhone, employee);

        return (ResponseEntity) completePhoneLogin(employee, normalizedPhone, "phone_login", httpResponse);
    }

    /**
     * 手机验证码 + 多公司选择后登录
     * POST /api/auth/phone/login-with-tenant
     */
    @PostMapping("/phone/login-with-tenant")
    public ResponseEntity<ApiResponse<LoginResponse>> phoneLoginWithTenant(
            @RequestBody PhoneLoginWithTenantRequest request,
            HttpServletResponse httpResponse
    ) {
        log.info("Phone login with tenant: phone={}, tenantId={}", maskPhone(request.phone()), request.tenantId());

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        // 验证验证码
        PhoneVerificationService.VerifyResult verifyResult = phoneVerificationService.verifyCode(normalizedPhone, request.code());
        if (!verifyResult.isSuccess()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("invalid_code", verifyResult.error()));
        }

        // 查找指定手机号+租户的员工
        List<EnterpriseEmployeeEntity> employees = employeeRepository.findAllByPhone(normalizedPhone);
        Optional<EnterpriseEmployeeEntity> empOpt = employees.stream()
                .filter(e -> request.tenantId().equals(e.getTenantId()) && e.isActive())
                .findFirst();

        if (empOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("employee_not_found", "在该公司下未找到员工记录"));
        }

        SecurityIdentity employee = toSecurityIdentity(empOpt.get(), normalizedPhone);
        phoneEmployeeMap.put(normalizedPhone, employee);

        return completePhoneLogin(employee, normalizedPhone, "phone_login_tenant", httpResponse);
    }

    private ResponseEntity<ApiResponse<LoginResponse>> completePhoneLogin(
            SecurityIdentity employee, String phone, String loginMethod,
            HttpServletResponse httpResponse
    ) {
        AuthContext authContext = new AuthContext();
        authContext.setEmployeeId(employee.getEmployeeId());
        authContext.setName(employee.getName());
        authContext.setPhone(phone);
        authContext.setEmail(employee.getEmail());
        authContext.setDepartment(employee.getDepartment());
        authContext.setPosition(employee.getPosition());
        authContext.setIdentity(employee.getIdentity());
        authContext.setAccessLevel(employee.getAccessLevel());
        authContext.setFounder(employee.isFounder());
        authContext.setActive(employee.isActive());
        authContext.setTenantId(employee.getTenantId());
        authContext.setLastSyncTime(Instant.now());
        authContext.setSyncSource(loginMethod);

        AuthResult authResult = authService.createInternalSession(authContext);
        AuthSession session = authResult.session();

        setTokenCookies(httpResponse, session.sessionId(), session.sessionId());

        LoginResponse response = new LoginResponse(
                session.sessionId(),
                null,
                convertToUserInfo(employee)
        );

        log.info("{} successful: {} ({}), tenantId: {}, identity: {}, accessLevel: {}",
                loginMethod, employee.getName(), maskPhone(phone),
                employee.getTenantId(), employee.getIdentity(), employee.getAccessLevel());
        authMetricsService.recordSuccess(loginMethod, "PhoneAuthController");
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Entity → SecurityIdentity 转换 */
    private SecurityIdentity toSecurityIdentity(EnterpriseEmployeeEntity e, String phone) {
        SecurityIdentity employee = new SecurityIdentity();
        employee.setEmployeeId(e.getEmployeeId());
        employee.setName(e.getName());
        employee.setPhone(phone);
        employee.setEmail(e.getEmail());
        try {
            if (e.getIdentity() != null) {
                employee.setIdentity(UserIdentity.valueOf(e.getIdentity()));
            }
        } catch (Exception ignored) {}
        try {
            if (e.getAccessLevel() != null) {
                employee.setAccessLevel(AccessLevel.valueOf(e.getAccessLevel()));
            }
        } catch (Exception ignored) {}
        employee.setFounder(e.isFounder());
        employee.setPosition(e.getPosition());
        employee.setActive(e.isActive());
        if (e.getDepartmentName() != null && !e.getDepartmentName().isBlank()) {
            employee.setDepartment(e.getDepartmentName());
        } else if (e.isFounder()) {
            employee.setDepartment("core");
        }
        if (e.getTenantId() == null || e.getTenantId().isBlank()) {
            employee.setTenantId("tenant_default");
        } else {
            employee.setTenantId(e.getTenantId());
        }
        employee.setStatus("ACTIVE");
        return employee;
    }

    @PostMapping("/phone/bind")
    public ResponseEntity<ApiResponse<Void>> bindPhone(
            @RequestBody BindPhoneRequest request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("session_expired", "会话已过期"));
        }

        com.livingagent.core.security.AuthContext employee = sessionOpt.get().authContext();

        if (!employee.isFounder() && employee.getIdentity() != UserIdentity.INTERNAL_ENTERPRISE) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "只有董事长可以绑定员工手机号"));
        }

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        PhoneVerificationService.VerifyResult verifyResult = phoneVerificationService.verifyCode(normalizedPhone, request.code());
        if (!verifyResult.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("verification_failed", verifyResult.error()));
        }

        SecurityIdentity targetEmployee = phoneEmployeeMap.get(normalizedPhone);
        if (targetEmployee != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("phone_already_bound", "该手机号已被绑定"));
        }

        SecurityIdentity newEmployee = new SecurityIdentity();
        newEmployee.setEmployeeId("emp_" + UUID.randomUUID().toString().substring(0, 8));
        newEmployee.setName(request.name());
        newEmployee.setPhone(normalizedPhone);
        newEmployee.setEmail(request.email());
        newEmployee.setDepartment(request.department());
        newEmployee.setPosition(request.position());
        newEmployee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        newEmployee.setAccessLevel(AccessLevel.DEPARTMENT);
        newEmployee.setJoinDate(Instant.now());
        newEmployee.setActive(true);

        phoneEmployeeMap.put(normalizedPhone, newEmployee);

        log.info("Phone bound to employee: {} -> {}", maskPhone(normalizedPhone), newEmployee.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public void registerEmployeePhone(SecurityIdentity employee, String phone) {
        String normalizedPhone = phoneVerificationService.normalizePhone(phone);
        employee.setPhone(normalizedPhone);
        phoneEmployeeMap.put(normalizedPhone, employee);
        log.info("Registered employee phone: {} -> {}", maskPhone(normalizedPhone), employee.getName());
    }

    public void registerEmployeePhone(AuthContext authContext, String phone) {
        String normalizedPhone = phoneVerificationService.normalizePhone(phone);
        SecurityIdentity employee = new SecurityIdentity();
        employee.setEmployeeId(authContext.getEmployeeId());
        employee.setName(authContext.getName());
        employee.setPhone(normalizedPhone);
        employee.setEmail(authContext.getEmail());
        employee.setIdentity(authContext.getIdentity());
        employee.setAccessLevel(authContext.getAccessLevel());
        employee.setFounder(authContext.isFounder());
        employee.setPosition(authContext.getPosition());
        employee.setActive(authContext.isActive());
        phoneEmployeeMap.put(normalizedPhone, employee);
        log.info("Registered employee phone from AuthContext: {} -> {}", maskPhone(normalizedPhone), authContext.getName());
    }

    public Optional<SecurityIdentity> getEmployeeByPhone(String phone) {
        String normalizedPhone = phoneVerificationService.normalizePhone(phone);
        return Optional.ofNullable(phoneEmployeeMap.get(normalizedPhone));
    }

    private SecurityIdentity findEmployeeByPhone(String normalizedPhone) {
        SecurityIdentity employee = phoneEmployeeMap.get(normalizedPhone);
        if (employee != null) {
            log.info("Found employee in phoneEmployeeMap: {} -> {}", normalizedPhone, employee.getName());
            return employee;
        }

        Optional<AuthContext> authContextOpt = employeeService.findByPhone(normalizedPhone);
        if (authContextOpt.isPresent()) {
            AuthContext ctx = authContextOpt.get();
            employee = new SecurityIdentity();
            employee.setEmployeeId(ctx.getEmployeeId());
            employee.setName(ctx.getName());
            employee.setPhone(normalizedPhone);
            employee.setEmail(ctx.getEmail());
            employee.setIdentity(ctx.getIdentity());
            employee.setAccessLevel(ctx.getAccessLevel());
            employee.setFounder(ctx.isFounder());
            employee.setPosition(ctx.getPosition());
            employee.setActive(ctx.isActive());

            if (ctx.getDepartment() != null && !ctx.getDepartment().isBlank()) {
                employee.setDepartment(ctx.getDepartment());
            } else if (employee.isFounder()) {
                employee.setDepartment("core");
            }
            if (ctx.getTenantId() == null || ctx.getTenantId().isBlank()) {
                employee.setTenantId("tenant_default");
            } else {
                employee.setTenantId(ctx.getTenantId());
            }
            employee.setStatus("ACTIVE");

            phoneEmployeeMap.put(normalizedPhone, employee);

            SecurityIdentity existing = employeeAuthService.findById(ctx.getEmployeeId()).orElse(null);
            if (existing == null) {
                employeeAuthService.createEmployee(employee);
            } else {
                employeeAuthService.updateEmployee(employee);
            }

            log.info("Loaded employee from database: {} -> {} (ID: {})", normalizedPhone, ctx.getName(), ctx.getEmployeeId());
            return employee;
        }

        log.warn("Employee not found for phone: {}", normalizedPhone);
        return null;
    }

    private UserInfo convertToUserInfo(SecurityIdentity employee) {
        return new UserInfo(
                employee.getEmployeeId(),
                employee.getEmail(),
                employee.getName(),
                null,
                employee.getDepartment(),
                employee.getIdentity().name(),
                employee.getAccessLevel().name(),
                employee.isFounder(),
                employee.getTenantId(),
                new ArrayList<>(employee.getAllowedBrains()),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

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

    public record SendSmsRequest(String phone, String type) {}
    public record SendSmsResponse(String message, int expiresIn, String code) {}
    public record PhoneLoginRequest(String phone, String code) {}
    public record PhoneLoginWithTenantRequest(String phone, String code, String tenantId) {}
    public record BindPhoneRequest(String phone, String code, String name, String email, String department, String position) {}
    public record LoginResponse(String accessToken, String refreshToken, UserInfo user) {}
    public record TenantOption(String tenantId, String departmentName, String employeeName, boolean founder) {}
    public record TenantSelectionResponse(String status, String phone, List<TenantOption> tenants) {}
    public record UserInfo(
            String id,
            String email,
            String name,
            String avatar,
            String department,
            String identity,
            String accessLevel,
            boolean founder,
            String tenantId,
            List<String> allowedBrains,
            List<String> capabilities,
            List<String> skills
    ) {}
}
