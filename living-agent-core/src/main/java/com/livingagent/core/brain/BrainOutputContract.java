package com.livingagent.core.brain;

import java.util.List;
import java.util.Map;

public record BrainOutputContract(
    BrainOutputStatus status,
    String summary,
    String plan,
    List<String> clarificationQuestions,
    List<String> blockingIssues,
    List<String> assignedWorkers,
    RiskLevel riskLevel,
    List<String> nextSteps,
    String conversationId,
    String taskKey,
    String executionId,
    String traceId,
    List<ArtifactRef> artifacts,
    Map<String, Object> metadata
) {

    public enum BrainOutputStatus {
        READY,
        NEEDS_CLARIFICATION,
        EXECUTING,
        COMPLETED,
        BLOCKED,
        FAILED
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public record ArtifactRef(
        String artifactId,
        String artifactType,
        String name,
        String path
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static BrainOutputContract completed(String summary, String conversationId, String taskKey, String executionId) {
        return builder()
            .status(BrainOutputStatus.COMPLETED)
            .summary(summary)
            .conversationId(conversationId)
            .taskKey(taskKey)
            .executionId(executionId)
            .riskLevel(RiskLevel.LOW)
            .build();
    }

    public static BrainOutputContract needsClarification(String summary, List<String> questions, String conversationId) {
        return builder()
            .status(BrainOutputStatus.NEEDS_CLARIFICATION)
            .summary(summary)
            .clarificationQuestions(questions)
            .conversationId(conversationId)
            .riskLevel(RiskLevel.MEDIUM)
            .build();
    }

    public static BrainOutputContract blocked(String summary, List<String> issues, String conversationId) {
        return builder()
            .status(BrainOutputStatus.BLOCKED)
            .summary(summary)
            .blockingIssues(issues)
            .conversationId(conversationId)
            .riskLevel(RiskLevel.HIGH)
            .build();
    }

    public static BrainOutputContract executing(String summary, List<String> workers, String conversationId, String taskKey, String executionId) {
        return builder()
            .status(BrainOutputStatus.EXECUTING)
            .summary(summary)
            .assignedWorkers(workers)
            .conversationId(conversationId)
            .taskKey(taskKey)
            .executionId(executionId)
            .riskLevel(RiskLevel.LOW)
            .build();
    }

    public static BrainOutputContract failed(String summary, String conversationId) {
        return builder()
            .status(BrainOutputStatus.FAILED)
            .summary(summary)
            .conversationId(conversationId)
            .riskLevel(RiskLevel.HIGH)
            .build();
    }

    public boolean isTerminal() {
        return status == BrainOutputStatus.COMPLETED
            || status == BrainOutputStatus.FAILED
            || status == BrainOutputStatus.BLOCKED;
    }

    public boolean needsUserInput() {
        return status == BrainOutputStatus.NEEDS_CLARIFICATION;
    }

    public static class Builder {
        private BrainOutputStatus status;
        private String summary;
        private String plan;
        private List<String> clarificationQuestions = List.of();
        private List<String> blockingIssues = List.of();
        private List<String> assignedWorkers = List.of();
        private RiskLevel riskLevel = RiskLevel.LOW;
        private List<String> nextSteps = List.of();
        private String conversationId;
        private String taskKey;
        private String executionId;
        private String traceId;
        private List<ArtifactRef> artifacts = List.of();
        private Map<String, Object> metadata = Map.of();

        public Builder status(BrainOutputStatus status) { this.status = status; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder plan(String plan) { this.plan = plan; return this; }
        public Builder clarificationQuestions(List<String> questions) { this.clarificationQuestions = questions; return this; }
        public Builder blockingIssues(List<String> issues) { this.blockingIssues = issues; return this; }
        public Builder assignedWorkers(List<String> workers) { this.assignedWorkers = workers; return this; }
        public Builder riskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; return this; }
        public Builder nextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; return this; }
        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public Builder taskKey(String taskKey) { this.taskKey = taskKey; return this; }
        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder artifacts(List<ArtifactRef> artifacts) { this.artifacts = artifacts; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public BrainOutputContract build() {
            return new BrainOutputContract(
                status, summary, plan, clarificationQuestions, blockingIssues,
                assignedWorkers, riskLevel, nextSteps, conversationId,
                taskKey, executionId, traceId, artifacts, metadata
            );
        }
    }
}
