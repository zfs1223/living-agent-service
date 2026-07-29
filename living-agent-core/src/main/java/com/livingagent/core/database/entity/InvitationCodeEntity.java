package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 邀请码实体（INVITATION_CODE_IMPROVEMENT_PLAN.md §3.1）
 * 支持邀请码注册、绑定公司/部门、手机号预绑定、密码初始化
 */
@Entity
@Table(name = "invitation_codes")
public class InvitationCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "company_id", length = 64)
    private String companyId;

    @Column(name = "company_name", length = 128)
    private String companyName;

    @Column(name = "department_code", length = 32)
    private String departmentCode;

    @Column(name = "department_name", length = 128)
    private String departmentName;

    @Column(name = "role", length = 32)
    private String role;

    @Column(name = "access_level", length = 16)
    private String accessLevel;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(name = "initial_password_hash", length = 255)
    private String initialPasswordHash;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses = 1;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_employee_id", length = 100)
    private String usedByEmployeeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "invite_url", length = 500)
    private String inviteUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public InvitationCodeEntity() {
        this.createdAt = Instant.now();
        this.maxUses = 1;
        this.usedCount = 0;
        this.status = "PENDING";
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

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

    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }

    public String getInitialPasswordHash() { return initialPasswordHash; }
    public void setInitialPasswordHash(String initialPasswordHash) { this.initialPasswordHash = initialPasswordHash; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public String getUsedByEmployeeId() { return usedByEmployeeId; }
    public void setUsedByEmployeeId(String usedByEmployeeId) { this.usedByEmployeeId = usedByEmployeeId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getInviteUrl() { return inviteUrl; }
    public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /**
     * 检查邀请码是否可用（未使用完、未过期、未禁用）
     */
    public boolean isUsable() {
        if (!"PENDING".equals(status)) return false;
        if (usedCount >= maxUses) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        return true;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
