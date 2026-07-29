package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import com.livingagent.core.database.entity.InvitationCodeEntity;
import com.livingagent.core.database.repository.DepartmentRepository;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.database.repository.InvitationCodeRepository;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 邀请码服务（INVITATION_CODE_IMPROVEMENT_PLAN.md §3.2）
 * 支持邀请码生成、验证、注册、密码初始化、批量生成
 */
@Service
public class InvitationCodeService {

    private static final Logger log = LoggerFactory.getLogger(InvitationCodeService.class);

    /** 邀请码默认有效期：7 天 */
    private static final long DEFAULT_EXPIRE_DAYS = 7;

    /** 邀请码默认最大使用次数 */
    private static final int DEFAULT_MAX_USES = 1;

    private final InvitationCodeRepository invitationCodeRepository;
    private final EnterpriseEmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public InvitationCodeService(
            InvitationCodeRepository invitationCodeRepository,
            EnterpriseEmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository
    ) {
        this.invitationCodeRepository = invitationCodeRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 生成单个邀请码
     */
    @Transactional
    public InvitationCodeEntity createInvitationCode(CreateInvitationRequest request, String createdBy) {
        log.info("Creating invitation code: tenant={}, company={}, dept={}, phone={}",
                request.getTenantId(), request.getCompanyId(), request.getDepartmentCode(),
                maskPhone(request.getPhone()));

        InvitationCodeEntity entity = new InvitationCodeEntity();
        entity.setCode(generateUniqueCode());
        entity.setTenantId(request.getTenantId());
        entity.setCompanyId(request.getCompanyId());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentCode(request.getDepartmentCode());
        entity.setDepartmentName(request.getDepartmentName());
        entity.setRole(request.getRole() != null ? request.getRole() : "employee");
        entity.setAccessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : AccessLevel.DEPARTMENT.name());
        entity.setPhone(request.getPhone());
        entity.setPhoneHash(request.getPhone() != null ? hashPhone(request.getPhone()) : null);

        // 如果提供初始密码，BCrypt 哈希后存储
        if (request.getInitialPassword() != null && !request.getInitialPassword().isBlank()) {
            entity.setInitialPasswordHash(passwordEncoder.encode(request.getInitialPassword()));
        }

        entity.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : DEFAULT_MAX_USES);
        entity.setUsedCount(0);
        entity.setStatus("PENDING");
        entity.setExpiresAt(request.getExpiresAt() != null ? request.getExpiresAt()
                : Instant.now().plus(DEFAULT_EXPIRE_DAYS, ChronoUnit.DAYS));
        entity.setCreatedBy(createdBy);
        entity.setNote(request.getNote());

        InvitationCodeEntity saved = invitationCodeRepository.save(entity);
        log.info("Invitation code created: {} (id={})", saved.getCode(), saved.getId());
        return saved;
    }

    /**
     * 批量生成邀请码
     */
    @Transactional
    public List<InvitationCodeEntity> batchCreateInvitationCodes(CreateInvitationRequest template, int count, String createdBy) {
        log.info("Batch creating {} invitation codes: tenant={}", count, template.getTenantId());

        List<InvitationCodeEntity> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                created.add(createInvitationCode(template, createdBy));
            } catch (Exception e) {
                log.error("Failed to create invitation code #{}: {}", i + 1, e.getMessage());
            }
        }
        log.info("Batch creation completed: {}/{} success", created.size(), count);
        return created;
    }

    /**
     * 验证邀请码（不消耗）
     */
    public ValidateResult validate(String code) {
        Optional<InvitationCodeEntity> opt = invitationCodeRepository.findByCode(code);
        if (opt.isEmpty()) {
            return ValidateResult.fail("invalid_code", "邀请码不存在");
        }
        InvitationCodeEntity entity = opt.get();

        if (!"PENDING".equals(entity.getStatus())) {
            return ValidateResult.fail("already_used", "邀请码已被使用或禁用");
        }

        if (entity.isExpired()) {
            return ValidateResult.fail("expired", "邀请码已过期");
        }

        if (entity.getUsedCount() >= entity.getMaxUses()) {
            return ValidateResult.fail("max_uses_reached", "邀请码使用次数已达上限");
        }

        return ValidateResult.ok(entity);
    }

    /**
     * 使用邀请码注册新用户
     * - 校验邀请码
     * - 校验手机号匹配（如果邀请码绑定了手机号）
     * - 创建员工记录
     * - 设置初始密码（如果邀请码有初始密码）
     * - 标记邀请码已使用
     */
    @Transactional
    public RegisterResult registerWithInvitation(RegisterRequest request) {
        log.info("Registering with invitation code: {}, phone={}", request.getCode(), maskPhone(request.getPhone()));

        // 1. 校验邀请码
        ValidateResult validateResult = validate(request.getCode());
        if (!validateResult.isSuccess()) {
            return RegisterResult.fail(validateResult.error(), validateResult.errorDescription());
        }
        InvitationCodeEntity invitation = validateResult.entity();

        // 2. 校验手机号
        if (invitation.getPhone() != null && !invitation.getPhone().isBlank()) {
            if (!invitation.getPhone().equals(request.getPhone())) {
                return RegisterResult.fail("phone_mismatch", "手机号与邀请码不匹配");
            }
        }

        // 3. 检查手机号是否已注册
        if (employeeRepository.existsByPhone(request.getPhone())) {
            return RegisterResult.fail("phone_already_registered", "该手机号已注册，请直接登录");
        }

        // 4. 创建员工记录
        EnterpriseEmployeeEntity employee = new EnterpriseEmployeeEntity();
        employee.setEmployeeId("emp_" + UUID.randomUUID().toString().substring(0, 12));
        employee.setName(request.getName());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        // 修复：departmentId 必须引用 enterprise_departments.department_id（如 dept_tech），
        // 不能是部门编码（如 tech），否则违反外键约束 fk_employee_department。
        String deptId = resolveDepartmentId(invitation.getDepartmentCode());
        employee.setDepartmentId(deptId);
        employee.setDepartmentName(invitation.getDepartmentName());
        employee.setPosition(request.getPosition() != null ? request.getPosition() : "员工");
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE.name());
        employee.setAccessLevel(invitation.getAccessLevel() != null ? invitation.getAccessLevel() : AccessLevel.DEPARTMENT.name());
        employee.setActive(true);
        employee.setTenantId(invitation.getTenantId());
        employee.setEmployeeType("HUMAN");
        employee.setStatus("ACTIVE");
        employee.setOrigin("human");
        employee.setPhoneVerified(true);

        // 5. 设置密码：优先使用邀请码中的初始密码哈希，否则使用请求中的密码
        String passwordHash;
        if (invitation.getInitialPasswordHash() != null && !invitation.getInitialPasswordHash().isBlank()) {
            passwordHash = invitation.getInitialPasswordHash();
        } else if (request.getPassword() != null && !request.getPassword().isBlank()) {
            passwordHash = passwordEncoder.encode(request.getPassword());
        } else {
            return RegisterResult.fail("password_required", "请提供初始密码");
        }
        employee.setPasswordHash(passwordHash);
        employee.setPasswordChangedAt(Instant.now());

        EnterpriseEmployeeEntity savedEmployee = employeeRepository.save(employee);

        // 6. 标记邀请码已使用
        invitation.setUsedCount(invitation.getUsedCount() + 1);
        invitation.setUsedAt(Instant.now());
        invitation.setUsedByEmployeeId(savedEmployee.getEmployeeId());
        if (invitation.getUsedCount() >= invitation.getMaxUses()) {
            invitation.setStatus("USED");
        }
        invitationCodeRepository.save(invitation);

        log.info("Registration successful: {} -> {} ({})",
                invitation.getCode(), savedEmployee.getName(), savedEmployee.getEmployeeId());

        return RegisterResult.ok(savedEmployee, invitation);
    }

    /**
     * 管理员直接创建人类员工（无需邀请码）
     * 用于桌面端管理面板"新增用户"场景：所有信息（手机号/姓名/密码/部门/职位）已由管理员填写，
     * 无需经历"生成邀请码 → 使用邀请码"两步流程。
     */
    @Transactional
    public RegisterResult directCreateEmployee(DirectCreateRequest request) {
        log.info("Direct creating employee: phone={}, dept={}", maskPhone(request.getPhone()), request.getDepartmentCode());

        // 1. 基本校验
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return RegisterResult.fail("phone_required", "手机号不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return RegisterResult.fail("name_required", "姓名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return RegisterResult.fail("password_required", "密码不能为空且至少6位");
        }

        // 2. 检查手机号是否已注册
        if (employeeRepository.existsByPhone(request.getPhone())) {
            return RegisterResult.fail("phone_already_registered", "该手机号已注册，请直接登录");
        }

        // 3. 创建员工记录
        EnterpriseEmployeeEntity employee = new EnterpriseEmployeeEntity();
        employee.setEmployeeId("emp_" + UUID.randomUUID().toString().substring(0, 12));
        employee.setName(request.getName());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        String deptId = resolveDepartmentId(request.getDepartmentCode());
        employee.setDepartmentId(deptId);
        employee.setDepartmentName(request.getDepartmentName());
        employee.setPosition(request.getPosition() != null ? request.getPosition() : "员工");
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE.name());
        employee.setAccessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : AccessLevel.DEPARTMENT.name());
        employee.setActive(true);
        employee.setTenantId(request.getTenantId());
        employee.setEmployeeType("HUMAN");
        employee.setStatus("ACTIVE");
        employee.setOrigin("human");
        employee.setPhoneVerified(true);

        // 4. 设置密码
        employee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        employee.setPasswordChangedAt(Instant.now());

        EnterpriseEmployeeEntity savedEmployee = employeeRepository.save(employee);

        log.info("Direct employee creation successful: {} ({})", savedEmployee.getName(), savedEmployee.getEmployeeId());

        return RegisterResult.ok(savedEmployee, null);
    }

    /**
     * 修改用户密码
     */
    @Transactional
    public boolean changePassword(String employeeId, String oldPassword, String newPassword) {
        log.info("Changing password for employee: {}", employeeId);

        Optional<EnterpriseEmployeeEntity> opt = employeeRepository.findByEmployeeId(employeeId);
        if (opt.isEmpty()) {
            log.warn("Employee not found: {}", employeeId);
            return false;
        }

        EnterpriseEmployeeEntity employee = opt.get();

        // 如果用户未设置过密码，允许直接初始化（跳过旧密码校验）
        if (employee.getPasswordHash() == null) {
            log.info("Employee has no password set, allowing initialization: {}", employeeId);
            // 直接设置新密码
            if (newPassword == null || newPassword.length() < 6) {
                log.warn("New password too short for initialization: {}", employeeId);
                return false;
            }
            employee.setPasswordHash(passwordEncoder.encode(newPassword));
            employee.setPasswordChangedAt(Instant.now());
            employeeRepository.save(employee);
            log.info("Password initialized for: {}", employeeId);
            return true;
        }

        // 已设置密码，校验旧密码
        if (!passwordEncoder.matches(oldPassword, employee.getPasswordHash())) {
            log.warn("Old password mismatch for: {}", employeeId);
            return false;
        }

        // 校验新密码长度
        if (newPassword == null || newPassword.length() < 6) {
            log.warn("New password too short for: {}", employeeId);
            return false;
        }

        // 更新密码
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setPasswordChangedAt(Instant.now());
        employeeRepository.save(employee);

        log.info("Password changed successfully for: {}", employeeId);
        return true;
    }

    /**
     * 管理员重置用户密码
     */
    @Transactional
    public String resetPasswordByAdmin(String employeeId, String newPassword) {
        log.info("Admin resetting password for employee: {}", employeeId);

        Optional<EnterpriseEmployeeEntity> opt = employeeRepository.findByEmployeeId(employeeId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Employee not found: " + employeeId);
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        EnterpriseEmployeeEntity employee = opt.get();
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employee.setPasswordChangedAt(Instant.now());
        employeeRepository.save(employee);

        log.info("Admin reset password successful for: {}", employeeId);
        return newPassword;
    }

    /**
     * 验证密码是否正确
     */
    public boolean verifyPassword(String phone, String password) {
        Optional<EnterpriseEmployeeEntity> opt = employeeRepository.findByPhone(phone);
        if (opt.isEmpty()) {
            return false;
        }
        EnterpriseEmployeeEntity employee = opt.get();
        if (employee.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(password, employee.getPasswordHash());
    }

    /**
     * 列出指定租户的所有邀请码
     */
    public List<InvitationCodeEntity> listByTenant(String tenantId) {
        return invitationCodeRepository.findByTenantId(tenantId);
    }

    /**
     * 列出所有邀请码
     */
    public List<InvitationCodeEntity> listAll() {
        return invitationCodeRepository.findAll();
    }

    /**
     * 禁用邀请码
     */
    @Transactional
    public boolean disable(Long id) {
        return invitationCodeRepository.findById(id).map(entity -> {
            entity.setStatus("DISABLED");
            invitationCodeRepository.save(entity);
            log.info("Invitation code disabled: {} (id={})", entity.getCode(), id);
            return true;
        }).orElse(false);
    }

    /**
     * 删除邀请码
     */
    @Transactional
    public boolean delete(Long id) {
        if (invitationCodeRepository.existsById(id)) {
            invitationCodeRepository.deleteById(id);
            log.info("Invitation code deleted: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 清理过期邀请码（标记为 EXPIRED）
     */
    @Transactional
    public int cleanupExpiredCodes() {
        List<InvitationCodeEntity> expired = invitationCodeRepository.findExpiredPendingCodes(Instant.now());
        for (InvitationCodeEntity entity : expired) {
            entity.setStatus("EXPIRED");
            invitationCodeRepository.save(entity);
        }
        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired invitation codes", expired.size());
        }
        return expired.size();
    }

    // ===== 私有辅助方法 =====

    private String generateUniqueCode() {
        String code;
        do {
            // 生成 12 位邀请码：8位UUID + 4位随机
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (invitationCodeRepository.existsByCode(code));
        return code;
    }

    /**
     * 手机号哈希（SHA-256）用于隐私保护
     */
    private String hashPhone(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().substring(0, 64);
        } catch (Exception e) {
            log.warn("Failed to hash phone: {}", e.getMessage());
            return null;
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 将部门编码（如 tech, TECH）解析为 enterprise_departments.department_id（如 dept_tech）。
     * 如果找不到匹配的部门，返回 null（让数据库外键约束报错更清晰）。
     */
    private String resolveDepartmentId(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return null;
        }
        // 先精确匹配 code
        Optional<com.livingagent.core.database.entity.DepartmentEntity> dept =
                departmentRepository.findByCode(departmentCode);
        if (dept.isPresent()) {
            return dept.get().getDepartmentId();
        }
        // 尝试大写匹配（code 列存储为大写，如 TECH）
        dept = departmentRepository.findByCode(departmentCode.toUpperCase());
        if (dept.isPresent()) {
            return dept.get().getDepartmentId();
        }
        // 尝试小写匹配
        dept = departmentRepository.findByCode(departmentCode.toLowerCase());
        if (dept.isPresent()) {
            return dept.get().getDepartmentId();
        }
        // 兜底：直接使用传入值（可能本身就是 department_id）
        return departmentCode;
    }

    // ===== 内部类 =====

    public static class CreateInvitationRequest {
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
        private Instant expiresAt;
        private String note;

        // Getters and Setters
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
        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class RegisterRequest {
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

    public static class DirectCreateRequest {
        private String phone;
        private String name;
        private String email;
        private String password;
        private String departmentCode;
        private String departmentName;
        private String position;
        private String accessLevel;
        private String tenantId;

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
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }

    public static class ValidateResult {
        private final boolean success;
        private final InvitationCodeEntity entity;
        private final String error;
        private final String errorDescription;

        private ValidateResult(boolean success, InvitationCodeEntity entity, String error, String errorDescription) {
            this.success = success;
            this.entity = entity;
            this.error = error;
            this.errorDescription = errorDescription;
        }

        public static ValidateResult ok(InvitationCodeEntity entity) {
            return new ValidateResult(true, entity, null, null);
        }

        public static ValidateResult fail(String error, String desc) {
            return new ValidateResult(false, null, error, desc);
        }

        public boolean isSuccess() { return success; }
        public InvitationCodeEntity entity() { return entity; }
        public String error() { return error; }
        public String errorDescription() { return errorDescription; }
    }

    public static class RegisterResult {
        private final boolean success;
        private final EnterpriseEmployeeEntity employee;
        private final InvitationCodeEntity invitation;
        private final String error;
        private final String errorDescription;

        private RegisterResult(boolean success, EnterpriseEmployeeEntity employee, InvitationCodeEntity invitation,
                               String error, String errorDescription) {
            this.success = success;
            this.employee = employee;
            this.invitation = invitation;
            this.error = error;
            this.errorDescription = errorDescription;
        }

        public static RegisterResult ok(EnterpriseEmployeeEntity employee, InvitationCodeEntity invitation) {
            return new RegisterResult(true, employee, invitation, null, null);
        }

        public static RegisterResult fail(String error, String desc) {
            return new RegisterResult(false, null, null, error, desc);
        }

        public boolean isSuccess() { return success; }
        public EnterpriseEmployeeEntity employee() { return employee; }
        public InvitationCodeEntity invitation() { return invitation; }
        public String error() { return error; }
        public String errorDescription() { return errorDescription; }
    }
}
