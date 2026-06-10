package com.livingagent.core.database.entity;

import com.livingagent.core.evolution.executor.EvolutionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "evolution_results", indexes = {
        @Index(name = "idx_evolution_result_id", columnList = "result_id"),
        @Index(name = "idx_evolution_signal_id", columnList = "signal_id"),
        @Index(name = "idx_evolution_status", columnList = "status"),
        @Index(name = "idx_evolution_created_at", columnList = "created_at")
})
public class EvolutionResultEntity {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "result_id", unique = true, length = 64)
    private String resultId;

    @Column(name = "signal_id", length = 64)
    private String signalId;

    @Column(name = "decision_id", length = 64)
    private String decisionId;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "strategy", length = 128)
    private String strategy;

    @Column(name = "action", length = 128)
    private String action;

    @Column(name = "generated_skill_id", length = 128)
    private String generatedSkillId;

    @Column(name = "immediate_effective")
    private Boolean immediateEffective;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "timestamp")
    private Long timestamp;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "brain_id", length = 255)
    private String brainId;

    @Column(name = "brain_type", length = 50)
    private String brainType;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EvolutionResultEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.metadataJson = "{}";
        this.status = EvolutionResult.Status.SKIPPED.name();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getSignalId() { return signalId; }
    public void setSignalId(String signalId) { this.signalId = signalId; }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getGeneratedSkillId() { return generatedSkillId; }
    public void setGeneratedSkillId(String generatedSkillId) { this.generatedSkillId = generatedSkillId; }

    public Boolean getImmediateEffective() { return immediateEffective; }
    public void setImmediateEffective(Boolean immediateEffective) { this.immediateEffective = immediateEffective; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getBrainId() { return brainId; }
    public void setBrainId(String brainId) { this.brainId = brainId; }

    public String getBrainType() { return brainType; }
    public void setBrainType(String brainType) { this.brainType = brainType; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static EvolutionResultEntity fromDomain(EvolutionResult result) {
        EvolutionResultEntity entity = new EvolutionResultEntity();
        if (result == null) {
            return entity;
        }
        entity.setResultId(result.getResultId());
        entity.setSignalId(result.getSignal() != null ? result.getSignal().getSignalId() : null);
        entity.setDecisionId(result.getDecision() != null ? result.getDecision().getDecisionId() : null);
        entity.setStatus(result.getStatus() != null ? result.getStatus().name() : null);
        entity.setStrategy(result.getDecision() != null && result.getDecision().getStrategy() != null
                ? result.getDecision().getStrategy().name()
                : null);
        entity.setAction(result.getAction());
        entity.setGeneratedSkillId(result.getGeneratedSkillId());
        entity.setImmediateEffective(result.isImmediateEffective());
        entity.setErrorMessage(result.getErrorMessage());
        entity.setExecutionTimeMs(result.getExecutionTimeMs());
        entity.setTimestamp(result.getTimestamp());
        entity.setMetadataJson(toJson(result.getMetadata()));

        if (result.getSignal() != null && result.getSignal().getBrainDomain() != null) {
            String brainDomain = result.getSignal().getBrainDomain();
            entity.setBrainId(brainDomain);
            entity.setBrainType(extractBrainType(brainDomain));
            entity.setDepartment(extractDepartment(brainDomain));
        }

        return entity;
    }

    public EvolutionResult toDomain() {
        EvolutionResult result = EvolutionResult.skipped(null, null);
        result.setResultId(resultId);
        if (status != null) {
            result.setStatus(EvolutionResult.Status.valueOf(status));
        }
        result.setGeneratedSkillId(generatedSkillId);
        result.setAction(action);
        result.setErrorMessage(errorMessage);
        if (timestamp != null) {
            result.setTimestamp(timestamp);
        }
        if (executionTimeMs != null) {
            result.setExecutionTimeMs(executionTimeMs);
        }
        if (metadataJson != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("json", metadataJson);
            result.setMetadata(metadata);
        }
        return result;
    }

    private static String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return metadata.toString();
    }

    private static String extractBrainType(String brainId) {
        if (brainId == null) return "default";
        String lower = brainId.toLowerCase();
        if (lower.contains("main")) return "main";
        if (lower.contains("tech")) return "tech";
        if (lower.contains("admin")) return "admin";
        if (lower.contains("hr")) return "hr";
        if (lower.contains("finance")) return "finance";
        if (lower.contains("sales")) return "sales";
        if (lower.contains("cs")) return "cs";
        if (lower.contains("ops")) return "ops";
        if (lower.contains("legal")) return "legal";
        return "default";
    }

    private static String extractDepartment(String brainId) {
        if (brainId == null) return "unknown";
        if (brainId.startsWith("neuron://")) {
            String[] parts = brainId.split("/");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return extractBrainType(brainId);
    }
}
