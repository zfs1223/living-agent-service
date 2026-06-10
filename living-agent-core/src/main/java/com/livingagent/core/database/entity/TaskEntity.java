package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_task_task_id", columnList = "task_id"),
    @Index(name = "idx_task_assigned_status", columnList = "assigned_to, status"),
    @Index(name = "idx_task_user_id", columnList = "user_id"),
    @Index(name = "idx_task_task_key", columnList = "task_key"),
    @Index(name = "idx_task_execution_id", columnList = "execution_id"),
    @Index(name = "idx_task_project_id", columnList = "project_id"),
    @Index(name = "idx_task_department_status", columnList = "department_code, status")
})
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    @Column(name = "task_type", length = 50)
    private String taskType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private int priority;

    @Column(name = "required_capability", length = 100)
    private String requiredCapability;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "task_key", length = 500)
    private String taskKey;

    @Column(name = "execution_id", length = 500)
    private String executionId;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "department_code", length = 50)
    private String departmentCode;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_session_id", length = 100)
    private String sourceSessionId;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "submission_result", columnDefinition = "TEXT")
    private String submissionResult;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewer_id", length = 100)
    private String reviewerId;

    @Column(name = "review_conclusion", columnDefinition = "TEXT")
    private String reviewConclusion;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "readiness_status", length = 30)
    private String readinessStatus;

    @Column(name = "clarification_questions", columnDefinition = "TEXT")
    private String clarificationQuestions;

    @Column(name = "clarification_answer", columnDefinition = "TEXT")
    private String clarificationAnswer;

    @Column(name = "clarification_requested_at")
    private Instant clarificationRequestedAt;

    @Column(name = "blocking_issues", columnDefinition = "TEXT")
    private String blockingIssues;

    public TaskEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getRequiredCapability() { return requiredCapability; }
    public void setRequiredCapability(String requiredCapability) { this.requiredCapability = requiredCapability; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(Instant checkedOutAt) { this.checkedOutAt = checkedOutAt; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceSessionId() { return sourceSessionId; }
    public void setSourceSessionId(String sourceSessionId) { this.sourceSessionId = sourceSessionId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSubmissionResult() { return submissionResult; }
    public void setSubmissionResult(String submissionResult) { this.submissionResult = submissionResult; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewConclusion() { return reviewConclusion; }
    public void setReviewConclusion(String reviewConclusion) { this.reviewConclusion = reviewConclusion; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }

    public String getClarificationQuestions() { return clarificationQuestions; }
    public void setClarificationQuestions(String clarificationQuestions) { this.clarificationQuestions = clarificationQuestions; }

    public String getClarificationAnswer() { return clarificationAnswer; }
    public void setClarificationAnswer(String clarificationAnswer) { this.clarificationAnswer = clarificationAnswer; }

    public Instant getClarificationRequestedAt() { return clarificationRequestedAt; }
    public void setClarificationRequestedAt(Instant clarificationRequestedAt) { this.clarificationRequestedAt = clarificationRequestedAt; }

    public String getBlockingIssues() { return blockingIssues; }
    public void setBlockingIssues(String blockingIssues) { this.blockingIssues = blockingIssues; }
}
