package com.livingagent.core.intervention;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class InterventionDecision {

    private String decisionId;
    private String sessionId;
    private String conversationId;

    private String operationType;
    private Map<String, Object> operationDetails;
    private String sourceNeuronId;
    private String sourceChannelId;

    private RiskLevel riskLevel;
    private List<String> riskFactors;
    private double riskScore;

    private ImpactLevel impactLevel;
    private List<String> impactScope;
    private double impactScore;

    private InterventionType interventionType;
    private String aiDecision;
    private String humanDecision;
    private String finalDecision;

    private DecisionStatus status;
    private Instant createdAt;
    private Instant respondedAt;
    private Instant completedAt;

    private String assignedTo;
    private String respondedBy;
    private String department;

    private boolean learningApplied;
    private String learningNotes;
    private String learningEntryId;

    private int timeoutSeconds;
    private int escalationLevel;

    public enum RiskLevel {
        LOW(1, "低风险"),
        MEDIUM(2, "中风险"),
        HIGH(3, "高风险"),
        CRITICAL(4, "极高风险");

        private final int level;
        private final String description;

        RiskLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }

    public enum ImpactLevel {
        MINIMAL(1, "影响极小"),
        SMALL(2, "影响较小"),
        MODERATE(3, "影响中等"),
        LARGE(4, "影响较大"),
        CRITICAL(5, "影响重大");

        private final int level;
        private final String description;

        ImpactLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }

    public enum InterventionType {
        AUTO_EXECUTE("AI自主执行"),
        AUTO_WITH_NOTIFICATION("AI执行+通知"),
        ASYNC_APPROVAL("异步审批"),
        REALTIME_CONFIRM("实时确认"),
        MANDATORY_HUMAN("必须人工执行");

        private final String description;

        InterventionType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public enum DecisionStatus {
        PENDING("待处理"),
        AWAITING_RESPONSE("等待响应"),
        RESPONDED("已响应"),
        COMPLETED("已完成"),
        ESCALATED("已升级"),
        TIMEOUT("超时"),
        CANCELLED("已取消");

        private final String description;

        DecisionStatus(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public InterventionDecision() {
        this.operationDetails = new HashMap<>();
        this.riskFactors = new ArrayList<>();
        this.impactScope = new ArrayList<>();
        this.status = DecisionStatus.PENDING;
        this.createdAt = Instant.now();
        this.learningApplied = false;
        this.escalationLevel = 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InterventionDecision decision = new InterventionDecision();

        public Builder decisionId(String decisionId) {
            decision.decisionId = decisionId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            decision.sessionId = sessionId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            decision.conversationId = conversationId;
            return this;
        }

        public Builder operationType(String operationType) {
            decision.operationType = operationType;
            return this;
        }

        public Builder operationDetails(Map<String, Object> details) {
            decision.operationDetails = details;
            return this;
        }

        public Builder sourceNeuronId(String neuronId) {
            decision.sourceNeuronId = neuronId;
            return this;
        }

        public Builder sourceChannelId(String channelId) {
            decision.sourceChannelId = channelId;
            return this;
        }

        public Builder riskLevel(RiskLevel level) {
            decision.riskLevel = level;
            return this;
        }

        public Builder riskScore(double score) {
            decision.riskScore = score;
            return this;
        }

        public Builder riskFactors(List<String> factors) {
            decision.riskFactors = factors;
            return this;
        }

        public Builder impactLevel(ImpactLevel level) {
            decision.impactLevel = level;
            return this;
        }

        public Builder impactScore(double score) {
            decision.impactScore = score;
            return this;
        }

        public Builder impactScope(List<String> scope) {
            decision.impactScope = scope;
            return this;
        }

        public Builder interventionType(InterventionType type) {
            decision.interventionType = type;
            return this;
        }

        public Builder aiDecision(String aiDecision) {
            decision.aiDecision = aiDecision;
            return this;
        }

        public Builder department(String department) {
            decision.department = department;
            return this;
        }

        public Builder timeoutSeconds(int seconds) {
            decision.timeoutSeconds = seconds;
            return this;
        }

        public InterventionDecision build() {
            if (decision.decisionId == null) {
                decision.decisionId = "intv-" + System.currentTimeMillis();
            }
            return decision;
        }
    }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

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

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public List<String> getRiskFactors() { return riskFactors; }
    public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public ImpactLevel getImpactLevel() { return impactLevel; }
    public void setImpactLevel(ImpactLevel impactLevel) { this.impactLevel = impactLevel; }

    public List<String> getImpactScope() { return impactScope; }
    public void setImpactScope(List<String> impactScope) { this.impactScope = impactScope; }

    public double getImpactScore() { return impactScore; }
    public void setImpactScore(double impactScore) { this.impactScore = impactScore; }

    public InterventionType getInterventionType() { return interventionType; }
    public void setInterventionType(InterventionType interventionType) { this.interventionType = interventionType; }

    public String getAiDecision() { return aiDecision; }
    public void setAiDecision(String aiDecision) { this.aiDecision = aiDecision; }

    public String getHumanDecision() { return humanDecision; }
    public void setHumanDecision(String humanDecision) { this.humanDecision = humanDecision; }

    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }

    public DecisionStatus getStatus() { return status; }
    public void setStatus(DecisionStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String respondedBy) { this.respondedBy = respondedBy; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public boolean isLearningApplied() { return learningApplied; }
    public void setLearningApplied(boolean learningApplied) { this.learningApplied = learningApplied; }

    public String getLearningNotes() { return learningNotes; }
    public void setLearningNotes(String learningNotes) { this.learningNotes = learningNotes; }

    public String getLearningEntryId() { return learningEntryId; }
    public void setLearningEntryId(String learningEntryId) { this.learningEntryId = learningEntryId; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(int escalationLevel) { this.escalationLevel = escalationLevel; }

    public boolean needsHumanIntervention() {
        return interventionType == InterventionType.ASYNC_APPROVAL ||
               interventionType == InterventionType.REALTIME_CONFIRM ||
               interventionType == InterventionType.MANDATORY_HUMAN;
    }

    public boolean isHighRisk() {
        return riskLevel != null && riskLevel.getLevel() >= RiskLevel.HIGH.getLevel();
    }

    public boolean isHighImpact() {
        return impactLevel != null && impactLevel.getLevel() >= ImpactLevel.LARGE.getLevel();
    }

    public boolean isTimedOut() {
        if (timeoutSeconds <= 0 || status == DecisionStatus.COMPLETED) {
            return false;
        }
        return Instant.now().isAfter(createdAt.plusSeconds(timeoutSeconds));
    }

    @Override
    public String toString() {
        return "InterventionDecision{" +
                "decisionId='" + decisionId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", riskLevel=" + riskLevel +
                ", impactLevel=" + impactLevel +
                ", interventionType=" + interventionType +
                ", status=" + status +
                '}';
    }
}
