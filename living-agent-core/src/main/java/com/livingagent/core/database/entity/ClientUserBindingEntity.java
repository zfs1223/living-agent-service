package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 客户端与用户的临时绑定表实体
 * 记录当前登录用户与客户端设备的绑定关系
 */
@Entity
@Table(name = "client_user_binding")
@IdClass(ClientUserBindingId.class)
public class ClientUserBindingEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Id
    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "access_level", nullable = false)
    private Integer accessLevel = 0;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(Integer accessLevel) {
        this.accessLevel = accessLevel;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(LocalDateTime boundAt) {
        this.boundAt = boundAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
