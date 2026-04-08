package com.livingagent.core.heartbeat;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "heartbeat_runs")
public class HeartbeatRun {

    @Id
    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "employee_id", length = 100, nullable = false)
    private String employeeId;

    @Column(name = "wake_source", length = 32, nullable = false)
    private String wakeSource;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "max_duration_seconds")
    private Integer maxDurationSeconds;

    @Column(name = "allowed_actions", columnDefinition = "TEXT[]")
    private String[] allowedActions;

    @Column(name = "require_success")
    private boolean requireSuccess;

    @Column(name = "actions_taken", columnDefinition = "TEXT[]")
    private String[] actionsTaken;

    @Column(name = "actual_duration_seconds")
    private Integer actualDurationSeconds;

    @Column(name = "result_message", columnDefinition = "TEXT")
    private String resultMessage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getWakeSource() { return wakeSource; }
    public void setWakeSource(String wakeSource) { this.wakeSource = wakeSource; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public Integer getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(Integer maxDurationSeconds) { this.maxDurationSeconds = maxDurationSeconds; }

    public String[] getAllowedActions() { return allowedActions; }
    public void setAllowedActions(String[] allowedActions) { this.allowedActions = allowedActions; }

    public boolean isRequireSuccess() { return requireSuccess; }
    public void setRequireSuccess(boolean requireSuccess) { this.requireSuccess = requireSuccess; }

    public String[] getActionsTaken() { return actionsTaken; }
    public void setActionsTaken(String[] actionsTaken) { this.actionsTaken = actionsTaken; }

    public Integer getActualDurationSeconds() { return actualDurationSeconds; }
    public void setActualDurationSeconds(Integer actualDurationSeconds) { this.actualDurationSeconds = actualDurationSeconds; }

    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Duration getActualDuration() {
        return actualDurationSeconds != null ? Duration.ofSeconds(actualDurationSeconds) : null;
    }

    public Duration getMaxDuration() {
        return maxDurationSeconds != null ? Duration.ofSeconds(maxDurationSeconds) : null;
    }
}
