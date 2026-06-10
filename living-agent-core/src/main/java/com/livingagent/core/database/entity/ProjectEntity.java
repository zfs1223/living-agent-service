package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_project_id", columnList = "project_id"),
    @Index(name = "idx_project_status", columnList = "status"),
    @Index(name = "idx_project_department", columnList = "owner_department"),
    @Index(name = "idx_project_manager", columnList = "manager_id"),
    @Index(name = "idx_project_project_key", columnList = "project_key")
})
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "project_id", nullable = false, length = 100)
    private String projectId;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "current_phase", length = 50)
    private String currentPhase;

    @Column(name = "owner_department", length = 50)
    private String ownerDepartment;

    @Column(name = "manager_id", length = 100)
    private String managerId;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    private double progress;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "creator_user_id", length = 100)
    private String creatorUserId;

    @Column(name = "project_key", length = 200)
    private String projectKey;

    @Column(name = "source_task_key", length = 500)
    private String sourceTaskKey;

    @Column(name = "source_conversation_id", length = 100)
    private String sourceConversationId;

    @Column(name = "data_namespace", length = 500)
    private String dataNamespace;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ProjectEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }

    public String getOwnerDepartment() { return ownerDepartment; }
    public void setOwnerDepartment(String ownerDepartment) { this.ownerDepartment = ownerDepartment; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getSourceTaskKey() { return sourceTaskKey; }
    public void setSourceTaskKey(String sourceTaskKey) { this.sourceTaskKey = sourceTaskKey; }

    public String getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(String sourceConversationId) { this.sourceConversationId = sourceConversationId; }

    public String getDataNamespace() { return dataNamespace; }
    public void setDataNamespace(String dataNamespace) { this.dataNamespace = dataNamespace; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
