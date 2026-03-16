package com.livingagent.core.security;

import java.util.Optional;

public interface EmployeeChangeDetector {

    Optional<DetectedChange> detectFromConversation(String conversationId, String message, String speakerId);

    DetectedChange createChange(String employeeId, EmployeeService.ChangeType changeType, 
                                String detectedFrom, String details);

    void handleChange(DetectedChange change);

    boolean confirmChange(String changeId, String confirmedBy);

    boolean rejectChange(String changeId, String rejectedBy, String reason);

    class DetectedChange {
        private String changeId;
        private String employeeId;
        private String employeeName;
        private EmployeeService.ChangeType changeType;
        private String detectedFrom;
        private String details;
        private String originalValue;
        private String newValue;
        private double confidence;
        private long detectedAt;
        private ChangeStatus status;
        private String confirmedBy;
        private long confirmedAt;

        public DetectedChange() {
            this.changeId = "chg_" + System.currentTimeMillis();
            this.detectedAt = System.currentTimeMillis();
            this.status = ChangeStatus.PENDING;
            this.confidence = 0.5;
        }

        public boolean needsConfirmation() {
            return confidence < 0.9;
        }

        public boolean isHighConfidence() {
            return confidence >= 0.8;
        }

        public String getChangeId() { return changeId; }
        public void setChangeId(String changeId) { this.changeId = changeId; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public EmployeeService.ChangeType getChangeType() { return changeType; }
        public void setChangeType(EmployeeService.ChangeType changeType) { this.changeType = changeType; }

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

        public long getDetectedAt() { return detectedAt; }
        public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }

        public ChangeStatus getStatus() { return status; }
        public void setStatus(ChangeStatus status) { this.status = status; }

        public String getConfirmedBy() { return confirmedBy; }
        public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

        public long getConfirmedAt() { return confirmedAt; }
        public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

        @Override
        public String toString() {
            return String.format("DetectedChange{id=%s, employee=%s, type=%s, confidence=%.2f, status=%s}",
                changeId, employeeName, changeType, confidence, status);
        }
    }

    enum ChangeStatus {
        PENDING,
        CONFIRMED,
        REJECTED,
        APPLIED
    }
}
