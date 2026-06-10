package com.livingagent.core.evolution.executor;

import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionDecision;
import com.livingagent.core.evolution.signal.EvolutionSignal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class EvolutionResult {
    
    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED,
        DEFERRED,
        ESCALATED
    }
    
    private String resultId;
    private EvolutionSignal signal;
    private EvolutionDecision decision;
    private Status status;
    private String generatedSkillId;
    private String action;
    private String errorMessage;
    private long timestamp;
    private long executionTimeMs;
    private Map<String, Object> metadata;
    private int retryCount = 0;
    
    private EvolutionResult() {
        this.timestamp = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }
    
    public static EvolutionResult success(EvolutionSignal signal, EvolutionDecision decision) {
        EvolutionResult result = new EvolutionResult();
        result.signal = signal;
        result.decision = decision;
        result.status = Status.SUCCESS;
        return result;
    }
    
    public static EvolutionResult failed(EvolutionSignal signal, EvolutionDecision decision, String error) {
        EvolutionResult result = new EvolutionResult();
        result.signal = signal;
        result.decision = decision;
        result.status = Status.FAILED;
        result.errorMessage = error;
        return result;
    }
    
    public static EvolutionResult skipped(EvolutionSignal signal, EvolutionDecision decision) {
        EvolutionResult result = new EvolutionResult();
        result.signal = signal;
        result.decision = decision;
        result.status = Status.SKIPPED;
        return result;
    }
    
    public static EvolutionResult deferred(EvolutionSignal signal, EvolutionDecision decision) {
        EvolutionResult result = new EvolutionResult();
        result.signal = signal;
        result.decision = decision;
        result.status = Status.DEFERRED;
        return result;
    }
    
    public static EvolutionResult escalated(EvolutionSignal signal, EvolutionDecision decision) {
        EvolutionResult result = new EvolutionResult();
        result.signal = signal;
        result.decision = decision;
        result.status = Status.ESCALATED;
        return result;
    }
    
    public EvolutionResult withGeneratedSkill(String skillId) {
        this.generatedSkillId = skillId;
        return this;
    }
    
    public EvolutionResult withAction(String action) {
        this.action = action;
        return this;
    }
    
    public EvolutionResult withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isImmediateEffective() {
        return isSuccess() && generatedSkillId != null;
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public EvolutionSignal getSignal() { return signal; }
    public void setSignal(EvolutionSignal signal) { this.signal = signal; }
    
    public EvolutionDecision getDecision() { return decision; }
    public void setDecision(EvolutionDecision decision) { this.decision = decision; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public String getGeneratedSkillId() { return generatedSkillId; }
    public void setGeneratedSkillId(String generatedSkillId) { this.generatedSkillId = generatedSkillId; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public LocalDateTime getTimestampAsLocalDateTime() {
        return timestamp > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()) : null;
    }
    
    @Override
    public String toString() {
        return String.format("EvolutionResult{id=%s, status=%s, skill=%s, action=%s, time=%dms}",
                resultId, status, generatedSkillId, action, executionTimeMs);
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("resultId", resultId);
        map.put("status", status.name());
        map.put("signalId", signal != null ? signal.getSignalId() : null);
        map.put("brainId", signal != null ? signal.getBrainDomain() : null);
        map.put("brainType", extractBrainType(signal != null ? signal.getBrainDomain() : null));
        map.put("department", extractDepartment(signal != null ? signal.getBrainDomain() : null));
        map.put("strategy", decision != null ? decision.getStrategy() : null);
        map.put("generatedSkillId", generatedSkillId);
        map.put("action", action);
        map.put("errorMessage", errorMessage);
        map.put("executionTimeMs", executionTimeMs);
        map.put("timestamp", timestamp);
        map.put("immediateEffective", isImmediateEffective());
        map.putAll(metadata != null ? metadata : new HashMap<>());
        return map;
    }
    
    private String extractBrainType(String brainId) {
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
    
    private String extractDepartment(String brainId) {
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
