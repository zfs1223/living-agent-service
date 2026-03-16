package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enterprise_departments")
public class DepartmentEntity {

    @Id
    @Column(name = "department_id", length = 36)
    private String departmentId;
    
    @Column(name = "name", length = 100)
    private String name;
    
    @Column(name = "code", length = 50)
    private String code;
    
    @Column(name = "parent_id", length = 36)
    private String parentId;
    
    @Column(name = "manager_id", length = 36)
    private String managerId;
    
    @Column(name = "manager_name", length = 100)
    private String managerName;
    
    @Column(name = "target_brain", length = 50)
    private String targetBrain;
    
    @Column(name = "member_count")
    private int memberCount;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "sync_source", length = 50)
    private String syncSource;
    
    @Column(name = "last_sync_time")
    private Instant lastSyncTime;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;

    public DepartmentEntity() {
        this.memberCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getTargetBrain() { return targetBrain; }
    public void setTargetBrain(String targetBrain) { this.targetBrain = targetBrain; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public void incrementMemberCount() {
        this.memberCount++;
        touch();
    }

    public void decrementMemberCount() {
        if (this.memberCount > 0) {
            this.memberCount--;
            touch();
        }
    }
}
