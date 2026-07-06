package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 员工外部账号映射实体
 * <p>记录员工在各外部服务（GitLab/OpenProject/Jenkins）中的账号信息和访问令牌。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
@Entity
@Table(name = "employee_external_account",
    uniqueConstraints = @UniqueConstraint(columnNames = {"employee_code", "service_type"}))
public class EmployeeExternalAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", length = 16, nullable = false)
    private String employeeCode;

    @Column(name = "service_type", length = 32, nullable = false)
    private String serviceType;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_username", length = 128)
    private String externalUsername;

    @Column(name = "external_token")
    private String externalToken;

    @Column(name = "external_metadata", columnDefinition = "jsonb")
    private String externalMetadata;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EmployeeExternalAccountEntity() {
    }

    public EmployeeExternalAccountEntity(String employeeCode, String serviceType) {
        this.employeeCode = employeeCode;
        this.serviceType = serviceType;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getExternalUserId() { return externalUserId; }
    public void setExternalUserId(String externalUserId) { this.externalUserId = externalUserId; }
    public String getExternalUsername() { return externalUsername; }
    public void setExternalUsername(String externalUsername) { this.externalUsername = externalUsername; }
    public String getExternalToken() { return externalToken; }
    public void setExternalToken(String externalToken) { this.externalToken = externalToken; }
    public String getExternalMetadata() { return externalMetadata; }
    public void setExternalMetadata(String externalMetadata) { this.externalMetadata = externalMetadata; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
