package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "department_conversations", indexes = {
    @Index(name = "idx_conv_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_conv_owner_dept_status", columnList = "owner_user_id, department_code, status"),
    @Index(name = "idx_conv_department_status", columnList = "department_code, status"),
    @Index(name = "idx_conv_tenant", columnList = "tenant_id")
})
public class DepartmentConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "conversation_id", nullable = false, unique = true, length = 100)
    private String conversationId;

    @Column(name = "conversation_key", length = 500)
    private String conversationKey;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "owner_user_id", nullable = false, length = 100)
    private String ownerUserId;

    @Column(name = "department_code", nullable = false, length = 50)
    private String departmentCode;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "active_task_key", length = 500)
    private String activeTaskKey;

    @Column(name = "active_execution_id", length = 500)
    private String activeExecutionId;

    @Column(name = "retention_policy", length = 30)
    private String retentionPolicy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "destroyed_at")
    private Instant destroyedAt;

    public DepartmentConversationEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getConversationKey() { return conversationKey; }
    public void setConversationKey(String conversationKey) { this.conversationKey = conversationKey; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public String getActiveTaskKey() { return activeTaskKey; }
    public void setActiveTaskKey(String activeTaskKey) { this.activeTaskKey = activeTaskKey; }

    public String getActiveExecutionId() { return activeExecutionId; }
    public void setActiveExecutionId(String activeExecutionId) { this.activeExecutionId = activeExecutionId; }

    public String getRetentionPolicy() { return retentionPolicy; }
    public void setRetentionPolicy(String retentionPolicy) { this.retentionPolicy = retentionPolicy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public Instant getDestroyedAt() { return destroyedAt; }
    public void setDestroyedAt(Instant destroyedAt) { this.destroyedAt = destroyedAt; }
}
