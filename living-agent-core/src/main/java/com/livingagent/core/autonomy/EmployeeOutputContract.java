package com.livingagent.core.autonomy;

import com.livingagent.core.brain.BrainOutputContract;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EmployeeOutputContract(
    String employeeCode,
    String employeeName,
    String departmentCode,
    String assignmentId,
    String taskKey,
    String executionId,
    EmployeeOutputStatus status,
    String summary,
    List<String> completedItems,
    List<String> failedItems,
    List<BrainOutputContract.ArtifactRef> artifacts,
    List<String> blockingIssues,
    List<String> clarificationQuestions,
    BrainOutputContract.RiskLevel riskLevel,
    boolean requiresHumanReview,
    boolean retryable,
    String suggestedNextStep,
    String failedReason,
    String failedStage,
    Instant startedAt,
    Instant completedAt,
    Map<String, Object> metadata
) {

    public enum EmployeeOutputStatus {
        ACCEPTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        BLOCKED,
        NEEDS_CLARIFICATION,
        NEEDS_REWORK
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EmployeeOutputContract completed(String employeeCode, String employeeName, String departmentCode,
                                                    String assignmentId, String taskKey, String executionId,
                                                    String summary, List<String> completedItems) {
        return builder()
            .employeeCode(employeeCode)
            .employeeName(employeeName)
            .departmentCode(departmentCode)
            .assignmentId(assignmentId)
            .taskKey(taskKey)
            .executionId(executionId)
            .status(EmployeeOutputStatus.COMPLETED)
            .summary(summary)
            .completedItems(completedItems)
            .riskLevel(BrainOutputContract.RiskLevel.LOW)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();
    }

    public static EmployeeOutputContract failed(String employeeCode, String departmentCode,
                                                String assignmentId, String taskKey, String executionId,
                                                String failedReason, String failedStage, boolean retryable) {
        return builder()
            .employeeCode(employeeCode)
            .departmentCode(departmentCode)
            .assignmentId(assignmentId)
            .taskKey(taskKey)
            .executionId(executionId)
            .status(EmployeeOutputStatus.FAILED)
            .failedReason(failedReason)
            .failedStage(failedStage)
            .retryable(retryable)
            .riskLevel(BrainOutputContract.RiskLevel.HIGH)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();
    }

    public static EmployeeOutputContract blocked(String employeeCode, String departmentCode,
                                                 String assignmentId, String taskKey, String executionId,
                                                 List<String> blockingIssues) {
        return builder()
            .employeeCode(employeeCode)
            .departmentCode(departmentCode)
            .assignmentId(assignmentId)
            .taskKey(taskKey)
            .executionId(executionId)
            .status(EmployeeOutputStatus.BLOCKED)
            .blockingIssues(blockingIssues)
            .riskLevel(BrainOutputContract.RiskLevel.HIGH)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();
    }

    public static EmployeeOutputContract needsClarification(String employeeCode, String departmentCode,
                                                            String assignmentId, String taskKey, String executionId,
                                                            List<String> clarificationQuestions) {
        return builder()
            .employeeCode(employeeCode)
            .departmentCode(departmentCode)
            .assignmentId(assignmentId)
            .taskKey(taskKey)
            .executionId(executionId)
            .status(EmployeeOutputStatus.NEEDS_CLARIFICATION)
            .clarificationQuestions(clarificationQuestions)
            .riskLevel(BrainOutputContract.RiskLevel.MEDIUM)
            .startedAt(Instant.now())
            .build();
    }

    public boolean isTerminal() {
        return status == EmployeeOutputStatus.COMPLETED
            || status == EmployeeOutputStatus.FAILED
            || status == EmployeeOutputStatus.BLOCKED;
    }

    public static class Builder {
        private String employeeCode;
        private String employeeName;
        private String departmentCode;
        private String assignmentId;
        private String taskKey;
        private String executionId;
        private EmployeeOutputStatus status;
        private String summary;
        private List<String> completedItems = List.of();
        private List<String> failedItems = List.of();
        private List<BrainOutputContract.ArtifactRef> artifacts = List.of();
        private List<String> blockingIssues = List.of();
        private List<String> clarificationQuestions = List.of();
        private BrainOutputContract.RiskLevel riskLevel = BrainOutputContract.RiskLevel.LOW;
        private boolean requiresHumanReview;
        private boolean retryable;
        private String suggestedNextStep;
        private String failedReason;
        private String failedStage;
        private Instant startedAt;
        private Instant completedAt;
        private Map<String, Object> metadata = Map.of();

        public Builder employeeCode(String v) { this.employeeCode = v; return this; }
        public Builder employeeName(String v) { this.employeeName = v; return this; }
        public Builder departmentCode(String v) { this.departmentCode = v; return this; }
        public Builder assignmentId(String v) { this.assignmentId = v; return this; }
        public Builder taskKey(String v) { this.taskKey = v; return this; }
        public Builder executionId(String v) { this.executionId = v; return this; }
        public Builder status(EmployeeOutputStatus v) { this.status = v; return this; }
        public Builder summary(String v) { this.summary = v; return this; }
        public Builder completedItems(List<String> v) { this.completedItems = v; return this; }
        public Builder failedItems(List<String> v) { this.failedItems = v; return this; }
        public Builder artifacts(List<BrainOutputContract.ArtifactRef> v) { this.artifacts = v; return this; }
        public Builder blockingIssues(List<String> v) { this.blockingIssues = v; return this; }
        public Builder clarificationQuestions(List<String> v) { this.clarificationQuestions = v; return this; }
        public Builder riskLevel(BrainOutputContract.RiskLevel v) { this.riskLevel = v; return this; }
        public Builder requiresHumanReview(boolean v) { this.requiresHumanReview = v; return this; }
        public Builder retryable(boolean v) { this.retryable = v; return this; }
        public Builder suggestedNextStep(String v) { this.suggestedNextStep = v; return this; }
        public Builder failedReason(String v) { this.failedReason = v; return this; }
        public Builder failedStage(String v) { this.failedStage = v; return this; }
        public Builder startedAt(Instant v) { this.startedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public EmployeeOutputContract build() {
            return new EmployeeOutputContract(
                employeeCode, employeeName, departmentCode, assignmentId, taskKey, executionId,
                status, summary, completedItems, failedItems, artifacts, blockingIssues,
                clarificationQuestions, riskLevel, requiresHumanReview, retryable,
                suggestedNextStep, failedReason, failedStage, startedAt, completedAt, metadata
            );
        }
    }
}
