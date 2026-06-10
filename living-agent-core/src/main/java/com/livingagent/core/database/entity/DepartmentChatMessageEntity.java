package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "department_chat_messages", indexes = {
    @Index(name = "idx_dept_timestamp", columnList = "department, timestamp"),
    @Index(name = "idx_dept_user", columnList = "department, user_id"),
    @Index(name = "idx_msg_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_msg_conversation_timestamp", columnList = "conversation_id, timestamp")
})
public class DepartmentChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "brain_id", length = 100)
    private String brainId;

    @Column(length = 50)
    private String model;

    @Column(length = 50)
    private String intent;

    @Column(length = 50)
    private String neuron;

    @Column(length = 50)
    private String status;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "task_key", length = 500)
    private String taskKey;

    @Column(name = "execution_id", length = 500)
    private String executionId;

    @Column(name = "message_type", length = 30)
    private String messageType;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public DepartmentChatMessageEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getBrainId() {
        return brainId;
    }

    public void setBrainId(String brainId) {
        this.brainId = brainId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getNeuron() {
        return neuron;
    }

    public void setNeuron(String neuron) {
        this.neuron = neuron;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
