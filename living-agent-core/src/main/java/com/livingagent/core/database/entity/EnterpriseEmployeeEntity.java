package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "enterprise_employees")
public class EnterpriseEmployeeEntity {

    @Id
    @Column(name = "employee_id", length = 100)
    private String employeeId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "identity", length = 50)
    private String identity;

    @Column(name = "access_level", length = 20)
    private String accessLevel;

    @Column(name = "is_founder")
    private boolean founder;

    @Column(name = "voice_print_id", length = 100)
    private String voicePrintId;

    @Column(name = "oauth_provider", length = 50)
    private String oauthProvider;

    @Column(name = "oauth_user_id", length = 100)
    private String oauthUserId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "join_date")
    private Instant joinDate;

    @Column(name = "leave_date")
    private Instant leaveDate;

    @Column(name = "active")
    private boolean active;

    @Column(name = "roles", length = 1000)
    private String roles;

    @Column(name = "capabilities", length = 2000)
    private String capabilities;

    @Column(name = "tools", length = 2000)
    private String tools;

    @Column(name = "responsibility_card_id", length = 64)
    private String responsibilityCardId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "sync_source", length = 50)
    private String syncSource;

    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    // ========== 从 EmployeeEntity 合并的字段 ==========

    @Column(name = "employee_type", length = 31)
    private String employeeType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "model", length = 255)
    private String model;

    @Column(name = "brain_domain", length = 255)
    private String brainDomain;

    @Column(name = "max_concurrent_tasks")
    private Integer maxConcurrentTasks;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "origin", length = 20)
    private String origin;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EnterpriseEmployeeEntity() {
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getIdentity() { return identity; }
    public void setIdentity(String identity) { this.identity = identity; }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }

    public boolean isFounder() { return founder; }
    public void setFounder(boolean founder) { this.founder = founder; }

    public String getVoicePrintId() { return voicePrintId; }
    public void setVoicePrintId(String voicePrintId) { this.voicePrintId = voicePrintId; }

    public String getOauthProvider() { return oauthProvider; }
    public void setOauthProvider(String oauthProvider) { this.oauthProvider = oauthProvider; }

    public String getOauthUserId() { return oauthUserId; }
    public void setOauthUserId(String oauthUserId) { this.oauthUserId = oauthUserId; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Instant getJoinDate() { return joinDate; }
    public void setJoinDate(Instant joinDate) { this.joinDate = joinDate; }

    public Instant getLeaveDate() { return leaveDate; }
    public void setLeaveDate(Instant leaveDate) { this.leaveDate = leaveDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }

    public String getResponsibilityCardId() { return responsibilityCardId; }
    public void setResponsibilityCardId(String responsibilityCardId) { this.responsibilityCardId = responsibilityCardId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getSyncSource() { return syncSource; }
    public void setSyncSource(String syncSource) { this.syncSource = syncSource; }

    public Instant getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(Instant lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getEmployeeType() { return employeeType; }
    public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }

    public Integer getMaxConcurrentTasks() { return maxConcurrentTasks; }
    public void setMaxConcurrentTasks(Integer maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
}
