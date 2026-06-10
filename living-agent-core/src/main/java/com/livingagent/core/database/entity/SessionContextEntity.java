package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** 会话上下文持久化实体 */
@Entity
@Table(name = "session_contexts", indexes = {
    @Index(name = "idx_sess_user_id", columnList = "user_id"),
    @Index(name = "idx_sess_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_sess_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_sess_last_activity", columnList = "last_activity")
})
public class SessionContextEntity {

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "department_code")
    private String departmentCode;

    @Column(name = "task_key")
    private String taskKey;

    @Column(name = "execution_id")
    private String executionId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "project_key")
    private String projectKey;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "last_activity")
    private Instant lastActivity;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String attributesJson) { this.attributesJson = attributesJson; }
}
