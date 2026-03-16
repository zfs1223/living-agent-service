package com.livingagent.core.scenario;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ScenarioResult {
    
    private String scenarioId;
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private Map<String, Object> metrics;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private String error;
    
    public ScenarioResult() {
        this.data = new HashMap<>();
        this.metrics = new HashMap<>();
    }
    
    public static ScenarioResult success(String message) {
        ScenarioResult result = new ScenarioResult();
        result.success = true;
        result.message = message;
        return result;
    }
    
    public static ScenarioResult success(String message, Map<String, Object> data) {
        ScenarioResult result = success(message);
        result.data = data;
        return result;
    }
    
    public static ScenarioResult failure(String error) {
        ScenarioResult result = new ScenarioResult();
        result.success = false;
        result.error = error;
        return result;
    }
    
    public void start() {
        this.startTime = Instant.now();
    }
    
    public void end() {
        this.endTime = Instant.now();
        if (startTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
    
    public void addData(String key, Object value) {
        data.put(key, value);
    }
    
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }
    
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    @Override
    public String toString() {
        return "ScenarioResult{success=" + success + ", message='" + message + "', duration=" + durationMs + "ms}";
    }
}
