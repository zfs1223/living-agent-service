package com.livingagent.gateway.controller;

import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.auth.FounderService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthResult;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.security.auth.PhoneVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class PhoneAuthController {

    private static final Logger log = LoggerFactory.getLogger(PhoneAuthController.class);

    private final PhoneVerificationService phoneVerificationService;
    private final UnifiedAuthService authService;
    private final FounderService founderService;
    private final Map<String, Employee> phoneEmployeeMap = new ConcurrentHashMap<>();

    public PhoneAuthController(
            PhoneVerificationService phoneVerificationService,
            UnifiedAuthService authService,
            FounderService founderService
    ) {
        this.phoneVerificationService = phoneVerificationService;
        this.authService = authService;
        this.founderService = founderService;
    }

    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<SendSmsResponse>> sendSmsCode(
            @RequestBody SendSmsRequest request
    ) {
        log.info("Sending SMS code to: {}", maskPhone(request.phone()));

        if (!phoneVerificationService.isValidPhoneFormat(request.phone())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("invalid_phone", "手机号格式不正确"));
        }

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        if ("login".equals(request.type()) || "bind".equals(request.type())) {
            Employee employee = phoneEmployeeMap.get(normalizedPhone);
            if (employee == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("phone_not_registered", "该手机号未绑定企业员工，请联系管理员添加"));
            }
        }

        PhoneVerificationService.SendResult result = phoneVerificationService.sendVerificationCode(normalizedPhone);

        if (!result.success()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("send_failed", result.error()));
        }

        return ResponseEntity.ok(ApiResponse.success(new SendSmsResponse(
                "验证码已发送",
                300
        )));
    }

    @PostMapping("/phone/login")
    public ResponseEntity<ApiResponse<LoginResponse>> phoneLogin(
            @RequestBody PhoneLoginRequest request
    ) {
        log.info("Phone login attempt: {}", maskPhone(request.phone()));

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        Employee employee = phoneEmployeeMap.get(normalizedPhone);
        if (employee == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("phone_not_registered", "该手机号未绑定企业员工，请联系管理员添加"));
        }

        AuthResult authResult = authService.authenticateByPhone(normalizedPhone, request.code());

        if (!authResult.success()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(authResult.error(), authResult.errorDescription()));
        }

        AuthSession session = authResult.session();

        LoginResponse response = new LoginResponse(
                session.sessionId(),
                null,
                convertToUserInfo(employee)
        );

        log.info("Phone login successful: {} ({})", employee.getName(), maskPhone(request.phone()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/phone/bind")
    public ResponseEntity<ApiResponse<Void>> bindPhone(
            @RequestBody BindPhoneRequest request,
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        Employee employee = sessionOpt.get().employee();

        if (!employee.isFounder() && employee.getIdentity() != UserIdentity.INTERNAL_CHAIRMAN) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("forbidden", "只有董事长可以绑定员工手机号"));
        }

        String normalizedPhone = phoneVerificationService.normalizePhone(request.phone());

        PhoneVerificationService.VerifyResult verifyResult = phoneVerificationService.verifyCode(normalizedPhone, request.code());
        if (!verifyResult.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("verification_failed", verifyResult.error()));
        }

        Employee targetEmployee = phoneEmployeeMap.get(normalizedPhone);
        if (targetEmployee != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("phone_already_bound", "该手机号已被绑定"));
        }

        Employee newEmployee = new Employee();
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
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public void registerEmployeePhone(Employee employee, String phone) {
        String normalizedPhone = phoneVerificationService.normalizePhone(phone);
        employee.setPhone(normalizedPhone);
        phoneEmployeeMap.put(normalizedPhone, employee);
        log.info("Registered employee phone: {} -> {}", maskPhone(normalizedPhone), employee.getName());
    }

    public Optional<Employee> getEmployeeByPhone(String phone) {
        String normalizedPhone = phoneVerificationService.normalizePhone(phone);
        return Optional.ofNullable(phoneEmployeeMap.get(normalizedPhone));
    }

    private UserInfo convertToUserInfo(Employee employee) {
        return new UserInfo(
                employee.getEmployeeId(),
                employee.getEmail(),
                employee.getName(),
                null,
                employee.getDepartment(),
                employee.getIdentity().name(),
                employee.getAccessLevel().name(),
                employee.isFounder(),
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

    public record SendSmsRequest(String phone, String type) {}
    public record SendSmsResponse(String message, int expiresIn) {}
    public record PhoneLoginRequest(String phone, String code) {}
    public record BindPhoneRequest(String phone, String code, String name, String email, String department, String position) {}
    public record LoginResponse(String accessToken, String refreshToken, UserInfo user) {}
    public record UserInfo(
            String id,
            String email,
            String name,
            String avatar,
            String department,
            String identity,
            String accessLevel,
            boolean founder,
            List<String> allowedBrains,
            List<String> capabilities,
            List<String> skills
    ) {}
}
