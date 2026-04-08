package com.livingagent.core.security;

import java.time.Instant;

public class DetectedChange {

    private String changeId;
    private String employeeId;
    private String employeeName;
    private EmployeeService.ChangeType changeType;
    private ChangeStatus status;
    private String detectedFrom;
    private String details;
    private String originalValue;
    private String newValue;
    private double confidence;
    private String confirmedBy;
    private long confirmedAt;
    private Instant createdAt;

    public DetectedChange() {
        this.changeId = "chg_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        this.status = ChangeStatus.PENDING;
        this.createdAt = Instant.now();
        this.confidence = 0.0;
    }

    public String getChangeId() { return changeId; }
    public void setChangeId(String changeId) { this.changeId = changeId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public EmployeeService.ChangeType getChangeType() { return changeType; }
    public void setChangeType(EmployeeService.ChangeType changeType) { this.changeType = changeType; }

    public ChangeStatus getStatus() { return status; }
    public void setStatus(ChangeStatus status) { this.status = status; }

    public String getDetectedFrom() { return detectedFrom; }
    public void setDetectedFrom(String detectedFrom) { this.detectedFrom = detectedFrom; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "DetectedChange{" +
                "changeId='" + changeId + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", employeeName='" + employeeName + '\'' +
                ", changeType=" + changeType +
                ", status=" + status +
                ", confidence=" + confidence +
                '}';
    }
}
