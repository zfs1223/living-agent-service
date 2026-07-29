package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.InvitationCodeEntity;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.InvitationCodeService;
import com.livingagent.gateway.service.InvitationCodeService.CreateInvitationRequest;
import com.livingagent.gateway.service.InvitationCodeService.RegisterRequest;
import com.livingagent.gateway.service.InvitationCodeService.RegisterResult;
import com.livingagent.gateway.service.InvitationCodeService.ValidateResult;
import com.livingagent.gateway.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 邀请码控制器（INVITATION_CODE_IMPROVEMENT_PLAN.md §3.2）
 *
 * 端点分层：
 * - 公开（无需登录）：/validate, /use
 * - 管理员（需 FULL 权限）：/create, /batch-create, GET /, DELETE /{id}, /{id}/disable, /cleanup
 *
 * 注意：与 EnterpriseApiController 中的 /api/enterprise/invitation-codes 端点并存，
 * 本控制器提供完整功能（公司/部门/手机号绑定、初始密码、批量生成等增强能力）。
 */
@RestController
@RequestMapping("/api/invitation-codes")
public class InvitationCodeController {

    private static final Logger log = LoggerFactory.getLogger(InvitationCodeController.class);

    private final InvitationCodeService invitationCodeService;
    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;
    private final SystemConfigService configService;

    public InvitationCodeController(
            InvitationCodeService invitationCodeService,
            UnifiedAuthService authService,
            AccessGateService accessGateService,
            SystemConfigService configService
    ) {
        this.invitationCodeService = invitationCodeService;
        this.authService = authService;
        this.accessGateService = accessGateService;
        this.configService = configService;
    }

    // ===== 公开端点（注册前调用）=====

    /**
     * 验证邀请码是否可用（注册前预校验）
     * GET /api/invitation-codes/validate?code=XXX
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<ValidateResponse>> validate(@RequestParam String code) {
        ValidateResult result = invitationCodeService.validate(code);
        if (!result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok(new ValidateResponse(
                    false, result.error(), result.errorDescription(), null, null, null, null
            )));
        }
        InvitationCodeEntity entity = result.entity();
        return ResponseEntity.ok(ApiResponse.ok(new ValidateResponse(
                true, null, null,
                entity.getCompanyName(),
                entity.getDepartmentName(),
                entity.getPhone() != null,
                entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null
        )));
    }

    /**
     * 使用邀请码注册新用户
     * POST /api/invitation-codes/use
     */
    @PostMapping("/use")
    public ResponseEntity<ApiResponse<RegisterResponse>> use(@RequestBody RegisterRequestDTO request) {
        RegisterRequest req = new RegisterRequest();
        req.setCode(request.getCode());
        req.setPhone(request.getPhone());
        req.setName(request.getName());
        req.setEmail(request.getEmail());
        req.setPassword(request.getPassword());
        req.setPosition(request.getPosition());

        RegisterResult result = invitationCodeService.registerWithInvitation(req);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err(result.error(), result.errorDescription()));
        }

        // 注册成功后返回基本信息（不直接登录，让用户走登录流程）
        InvitationCodeEntity invitation = result.invitation();
        var employee = result.employee();
        RegisterResponse response = new RegisterResponse(
                true,
                "注册成功，请使用手机号+密码登录",
                employee.getEmployeeId(),
                employee.getName(),
                invitation.getCompanyName(),
                invitation.getDepartmentName()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ===== 管理员端点（需 FULL 权限）=====

    /**
     * 创建单个邀请码（带公司/部门/手机号绑定）
     * POST /api/invitation-codes/create
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<InvitationCodeVO>> create(
            @RequestBody CreateInvitationDTO dto,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        AuthContext ctx = ctxOpt.get();
        CreateInvitationRequest req = toCreateRequest(dto);

        // 从当前用户自动填充 tenantId 和 companyName（如果 DTO 未提供）
        if (req.getTenantId() == null || req.getTenantId().isBlank()) {
            req.setTenantId(ctx.getTenantId());
        }
        if ((req.getCompanyName() == null || req.getCompanyName().isBlank()) && ctx.getTenantId() != null) {
            // 从 SystemConfigService 获取公司名
            try {
                var tenantInfo = configService.getTenant(ctx.getTenantId());
                if (tenantInfo != null && tenantInfo.name() != null) {
                    req.setCompanyName(tenantInfo.name());
                }
            } catch (Exception e) {
                log.warn("Failed to get company name for tenant {}: {}", ctx.getTenantId(), e.getMessage());
            }
        }

        InvitationCodeEntity entity = invitationCodeService.createInvitationCode(req, ctx.getEmployeeId());
        return ResponseEntity.ok(ApiResponse.ok(toVO(entity)));
    }

    /**
     * 管理员直接创建人类员工（无需邀请码）
     * POST /api/invitation-codes/direct-create
     *
     * 适用于桌面端管理面板"新增用户"场景：所有信息（手机号/姓名/密码/部门/职位）
     * 已由管理员填写，无需经历"生成邀请码 → 使用邀请码"两步流程。
     */
    @PostMapping("/direct-create")
    public ResponseEntity<ApiResponse<RegisterResponse>> directCreate(
            @RequestBody DirectCreateDTO dto,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        AuthContext ctx = ctxOpt.get();
        InvitationCodeService.DirectCreateRequest req = new InvitationCodeService.DirectCreateRequest();
        req.setPhone(dto.getPhone());
        req.setName(dto.getName());
        req.setEmail(dto.getEmail());
        req.setPassword(dto.getPassword());
        req.setDepartmentCode(dto.getDepartmentCode());
        req.setDepartmentName(dto.getDepartmentName());
        req.setPosition(dto.getPosition());
        req.setAccessLevel(dto.getAccessLevel());
        // 从当前用户自动填充 tenantId（如果 DTO 未提供）
        req.setTenantId(dto.getTenantId() != null && !dto.getTenantId().isBlank()
                ? dto.getTenantId() : ctx.getTenantId());

        RegisterResult result = invitationCodeService.directCreateEmployee(req);
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err(result.error(), result.errorDescription()));
        }

        var employee = result.employee();
        RegisterResponse response = new RegisterResponse(
                true,
                "用户创建成功",
                employee.getEmployeeId(),
                employee.getName(),
                null,
                employee.getDepartmentName()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 批量生成邀请码
     * POST /api/invitation-codes/batch-create
     */
    @PostMapping("/batch-create")
    public ResponseEntity<ApiResponse<BatchCreateResponse>> batchCreate(
            @RequestBody BatchCreateDTO dto,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        AuthContext ctx = ctxOpt.get();

        if (dto.getCount() == null || dto.getCount() <= 0 || dto.getCount() > 1000) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_count", "批量数量必须在 1-1000 之间"));
        }

        CreateInvitationRequest template = toCreateRequest(dto.getTemplate());

        // 从当前用户自动填充 tenantId 和 companyName
        if (template.getTenantId() == null || template.getTenantId().isBlank()) {
            template.setTenantId(ctx.getTenantId());
        }
        if ((template.getCompanyName() == null || template.getCompanyName().isBlank()) && ctx.getTenantId() != null) {
            try {
                var tenantInfo = configService.getTenant(ctx.getTenantId());
                if (tenantInfo != null && tenantInfo.name() != null) {
                    template.setCompanyName(tenantInfo.name());
                }
            } catch (Exception e) {
                log.warn("Failed to get company name for tenant {}: {}", ctx.getTenantId(), e.getMessage());
            }
        }

        List<InvitationCodeEntity> created = invitationCodeService.batchCreateInvitationCodes(
                template, dto.getCount(), ctx.getEmployeeId()
        );

        List<InvitationCodeVO> voList = created.stream().map(this::toVO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(new BatchCreateResponse(
                dto.getCount(), created.size(), voList
        )));
    }

    /**
     * 列出所有邀请码（支持按 tenant/status 过滤）
     * GET /api/invitation-codes
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationCodeVO>>> list(
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest httpRequest
    ) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        List<InvitationCodeEntity> entities;
        if (tenantId != null && !tenantId.isBlank()) {
            entities = invitationCodeService.listByTenant(tenantId);
        } else {
            entities = invitationCodeService.listAll();
        }

        // 按状态过滤
        if (status != null && !status.isBlank()) {
            entities = entities.stream()
                    .filter(e -> status.equals(e.getStatus()))
                    .collect(Collectors.toList());
        }

        List<InvitationCodeVO> voList = entities.stream().map(this::toVO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(voList));
    }

    /**
     * 删除邀请码
     * DELETE /api/invitation-codes/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        if (invitationCodeService.delete(id)) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        return ResponseEntity.badRequest().body(ApiResponse.err("not_found", "邀请码不存在"));
    }

    /**
     * 禁用邀请码
     * POST /api/invitation-codes/{id}/disable
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(@PathVariable Long id, HttpServletRequest httpRequest) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        if (invitationCodeService.disable(id)) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        return ResponseEntity.badRequest().body(ApiResponse.err("not_found", "邀请码不存在"));
    }

    /**
     * 清理过期邀请码
     * POST /api/invitation-codes/cleanup
     */
    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanup(HttpServletRequest httpRequest) {
        Optional<AuthContext> ctxOpt = getAuthContext(httpRequest);
        if (ctxOpt.isEmpty() || !isEnterpriseAdmin(ctxOpt.get())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "需要董事长权限"));
        }

        int cleaned = invitationCodeService.cleanupExpiredCodes();
        Map<String, Object> result = new HashMap<>();
        result.put("cleaned_count", cleaned);
        result.put("cleaned_at", Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ===== 辅助方法 =====

    private Optional<AuthContext> getAuthContext(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            // 尝试从 Cookie 获取
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if ("access_token".equals(cookie.getName())) {
                        Optional<AuthSession> session = authService.validateSession(cookie.getValue());
                        return session.map(AuthSession::authContext);
                    }
                }
            }
            return Optional.empty();
        }
        String sessionId = auth.substring(7);
        return authService.validateSession(sessionId).map(AuthSession::authContext);
    }

    /**
     * 判断是否为董事长（FULL 权限）
     */
    private boolean isEnterpriseAdmin(AuthContext ctx) {
        if (ctx == null) return false;
        // 董事长 (founder) 或 FULL 权限
        if (ctx.isFounder()) return true;
        return "FULL".equals(ctx.getAccessLevel() != null ? ctx.getAccessLevel().name() : null);
    }

    private CreateInvitationRequest toCreateRequest(CreateInvitationDTO dto) {
        CreateInvitationRequest req = new CreateInvitationRequest();
        req.setTenantId(dto.getTenantId());
        req.setCompanyId(dto.getCompanyId());
        req.setCompanyName(dto.getCompanyName());
        req.setDepartmentCode(dto.getDepartmentCode());
        req.setDepartmentName(dto.getDepartmentName());
        req.setRole(dto.getRole());
        req.setAccessLevel(dto.getAccessLevel());
        req.setPhone(dto.getPhone());
        req.setInitialPassword(dto.getInitialPassword());
        req.setMaxUses(dto.getMaxUses());
        req.setExpiresAt(dto.getExpiresAt() != null ? Instant.parse(dto.getExpiresAt()) : null);
        req.setNote(dto.getNote());
        return req;
    }

    private InvitationCodeVO toVO(InvitationCodeEntity entity) {
        return new InvitationCodeVO(
                entity.getId(),
                entity.getCode(),
                entity.getTenantId(),
                entity.getCompanyId(),
                entity.getCompanyName(),
                entity.getDepartmentCode(),
                entity.getDepartmentName(),
                entity.getRole(),
                entity.getAccessLevel(),
                maskPhone(entity.getPhone()),
                entity.getMaxUses(),
                entity.getUsedCount(),
                entity.getStatus(),
                entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null,
                entity.getUsedAt() != null ? entity.getUsedAt().toString() : null,
                entity.getUsedByEmployeeId(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                entity.getCreatedBy(),
                entity.getNote()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ===== DTO / VO =====

    public record ValidateResponse(
            boolean valid,
            String error,
            String errorDescription,
            String companyName,
            String departmentName,
            Boolean phoneBound,
            String expiresAt
    ) {}

    public record RegisterResponse(
            boolean success,
            String message,
            String employeeId,
            String name,
            String companyName,
            String departmentName
    ) {}

    public record InvitationCodeVO(
            Long id,
            String code,
            String tenantId,
            String companyId,
            String companyName,
            String departmentCode,
            String departmentName,
            String role,
            String accessLevel,
            String phone,
            Integer maxUses,
            Integer usedCount,
            String status,
            String expiresAt,
            String usedAt,
            String usedByEmployeeId,
            String createdAt,
            String createdBy,
            String note
    ) {}

    public record BatchCreateResponse(
            Integer requested,
            Integer created,
            List<InvitationCodeVO> codes
    ) {}

    public static class CreateInvitationDTO {
        private String tenantId;
        private String companyId;
        private String companyName;
        private String departmentCode;
        private String departmentName;
        private String role;
        private String accessLevel;
        private String phone;
        private String initialPassword;
        private Integer maxUses;
        private String expiresAt;
        private String note;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getDepartmentCode() { return departmentCode; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getAccessLevel() { return accessLevel; }
        public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getInitialPassword() { return initialPassword; }
        public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
        public Integer getMaxUses() { return maxUses; }
        public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class BatchCreateDTO {
        private Integer count;
        private CreateInvitationDTO template;

        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public CreateInvitationDTO getTemplate() { return template; }
        public void setTemplate(CreateInvitationDTO template) { this.template = template; }
    }

    public static class DirectCreateDTO {
        private String tenantId;
        private String phone;
        private String name;
        private String email;
        private String password;
        private String departmentCode;
        private String departmentName;
        private String position;
        private String accessLevel;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDepartmentCode() { return departmentCode; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getAccessLevel() { return accessLevel; }
        public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    }

    public static class RegisterRequestDTO {
        private String code;
        private String phone;
        private String name;
        private String email;
        private String password;
        private String position;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
    }
}
