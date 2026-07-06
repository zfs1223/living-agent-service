package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "intervention_decisions", indexes = {
    @Index(name = "idx_intervention_decision_id", columnList = "decision_id", unique = true),
    @Index(name = "idx_intervention_status", columnList = "status"),
    @Index(name = "idx_intervention_department", columnList = "department"),
    @Index(name = "idx_intervention_created", columnList = "created_at")
})
public class InterventionDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, unique = true, length = 100)
    private String decisionId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "operation_type", nullable = false, length = 200)
    private String operationType;

    @Column(name = "operation_details", columnDefinition = "JSONB")
    private String operationDetails;

    @Column(name = "source_neuron_id", length = 200)
    private String sourceNeuronId;

    @Column(name = "source_channel_id", length = 200)
    private String sourceChannelId;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "risk_factors", columnDefinition = "JSONB")
    private String riskFactors;

    @Column(name = "impact_level", length = 20)
    private String impactLevel;

    @Column(name = "impact_score")
    private Double impactScore;

    @Column(name = "impact_scope", columnDefinition = "JSONB")
    private String impactScope;

    @Column(name = "intervention_type", length = 30)
    private String interventionType;

    @Column(name = "ai_decision", columnDefinition = "TEXT")
    private String aiDecision;

    @Column(name = "human_decision", columnDefinition = "TEXT")
    private String humanDecision;

    @Column(name = "final_decision", columnDefinition = "TEXT")
    private String finalDecision;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "department", length = 64)
    private String department;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "responded_by", length = 100)
    private String respondedBy;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "escalation_level")
    private Integer escalationLevel;

    @Column(name = "learning_applied")
    private Boolean learningApplied;

    @Column(name = "learning_notes", columnDefinition = "TEXT")
    private String learningNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public InterventionDecisionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getOperationDetails() { return operationDetails; }
    public void setOperationDetails(String operationDetails) { this.operationDetails = operationDetails; }

    public String getSourceNeuronId() { return sourceNeuronId; }
    public void setSourceNeuronId(String sourceNeuronId) { this.sourceNeuronId = sourceNeuronId; }

    public String getSourceChannelId() { return sourceChannelId; }
    public void setSourceChannelId(String sourceChannelId) { this.sourceChannelId = sourceChannelId; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getRiskFactors() { return riskFactors; }
    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }

    public String getImpactLevel() { return impactLevel; }
    public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }

    public Double getImpactScore() { return impactScore; }
    public void setImpactScore(Double impactScore) { this.impactScore = impactScore; }

    public String getImpactScope() { return impactScope; }
    public void setImpactScope(String impactScope) { this.impactScope = impactScope; }

    public String getInterventionType() { return interventionType; }
    public void setInterventionType(String interventionType) { this.interventionType = interventionType; }

    public String getAiDecision() { return aiDecision; }
    public void setAiDecision(String aiDecision) { this.aiDecision = aiDecision; }

    public String getHumanDecision() { return humanDecision; }
    public void setHumanDecision(String humanDecision) { this.humanDecision = humanDecision; }

    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String respondedBy) { this.respondedBy = respondedBy; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Integer getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(Integer escalationLevel) { this.escalationLevel = escalationLevel; }

    public Boolean getLearningApplied() { return learningApplied; }
    public void setLearningApplied(Boolean learningApplied) { this.learningApplied = learningApplied; }

    public String getLearningNotes() { return learningNotes; }
    public void setLearningNotes(String learningNotes) { this.learningNotes = learningNotes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
