package com.livingagent.core.intervention;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InterventionDecisionEngine {

    InterventionDecision evaluate(InterventionRequest request);

    InterventionDecision evaluate(String operationType, Map<String, Object> operationDetails, String sourceNeuronId, String sourceChannelId);

    InterventionDecision determineInterventionType(InterventionDecision decision);

    boolean shouldEscalate(InterventionDecision decision);

    Optional<InterventionDecision> escalate(InterventionDecision decision);

    InterventionDecision applyLearning(InterventionDecision decision, String humanDecision);

    InterventionStatistics getStatistics(String department, long since);

    List<InterventionDecision> getPendingDecisions(String department);

    void registerRule(InterventionRule rule);

    void unregisterRule(String ruleId);

    List<InterventionRule> getApplicableRules(String operationType);

    public static class InterventionRequest {
        private String sessionId;
        private String conversationId;
        private String operationType;
        private Map<String, Object> operationDetails;
        private String sourceNeuronId;
        private String sourceChannelId;
        private String department;
        private String aiDecision;

        public InterventionRequest() {}

        public InterventionRequest(String operationType, Map<String, Object> operationDetails) {
            this.operationType = operationType;
            this.operationDetails = operationDetails;
        }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }

        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }

        public Map<String, Object> getOperationDetails() { return operationDetails; }
        public void setOperationDetails(Map<String, Object> operationDetails) { this.operationDetails = operationDetails; }

        public String getSourceNeuronId() { return sourceNeuronId; }
        public void setSourceNeuronId(String sourceNeuronId) { this.sourceNeuronId = sourceNeuronId; }

        public String getSourceChannelId() { return sourceChannelId; }
        public void setSourceChannelId(String sourceChannelId) { this.sourceChannelId = sourceChannelId; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getAiDecision() { return aiDecision; }
        public void setAiDecision(String aiDecision) { this.aiDecision = aiDecision; }
    }

    public static class InterventionStatistics {
        private long totalEvaluations;
        private long autoExecuted;
        private long humanInterventions;
        private long pendingDecisions;
        private long escalatedDecisions;
        private long timeoutDecisions;
        private double averageRiskScore;
        private double averageImpactScore;
        private double interventionRate;
        private double autoExecutionRate;

        public InterventionStatistics() {}

        public long getTotalEvaluations() { return totalEvaluations; }
        public void setTotalEvaluations(long totalEvaluations) { this.totalEvaluations = totalEvaluations; }

        public long getAutoExecuted() { return autoExecuted; }
        public void setAutoExecuted(long autoExecuted) { this.autoExecuted = autoExecuted; }

        public long getHumanInterventions() { return humanInterventions; }
        public void setHumanInterventions(long humanInterventions) { this.humanInterventions = humanInterventions; }

        public long getPendingDecisions() { return pendingDecisions; }
        public void setPendingDecisions(long pendingDecisions) { this.pendingDecisions = pendingDecisions; }

        public long getEscalatedDecisions() { return escalatedDecisions; }
        public void setEscalatedDecisions(long escalatedDecisions) { this.escalatedDecisions = escalatedDecisions; }

        public long getTimeoutDecisions() { return timeoutDecisions; }
        public void setTimeoutDecisions(long timeoutDecisions) { this.timeoutDecisions = timeoutDecisions; }

        public double getAverageRiskScore() { return averageRiskScore; }
        public void setAverageRiskScore(double averageRiskScore) { this.averageRiskScore = averageRiskScore; }

        public double getAverageImpactScore() { return averageImpactScore; }
        public void setAverageImpactScore(double averageImpactScore) { this.averageImpactScore = averageImpactScore; }

        public double getInterventionRate() { return interventionRate; }
        public void setInterventionRate(double interventionRate) { this.interventionRate = interventionRate; }

        public double getAutoExecutionRate() { return autoExecutionRate; }
        public void setAutoExecutionRate(double autoExecutionRate) { this.autoExecutionRate = autoExecutionRate; }

        public double calculateAutomationLevel() {
            if (totalEvaluations == 0) return 0.0;
            return (double) autoExecuted / totalEvaluations * 100;
        }
    }
}
