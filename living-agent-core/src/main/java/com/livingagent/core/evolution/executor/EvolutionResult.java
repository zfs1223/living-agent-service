package com.livingagent.core.evolution.executor;

import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionDecision;
import com.livingagent.core.evolution.signal.EvolutionSignal;

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
        map.put("strategy", decision != null ? decision.getStrategy() : null);
        map.put("generatedSkillId", generatedSkillId);
        map.put("action", action);
        map.put("errorMessage", errorMessage);
        map.put("executionTimeMs", executionTimeMs);
        map.put("timestamp", timestamp);
        map.put("immediateEffective", isImmediateEffective());
        return map;
    }
}
