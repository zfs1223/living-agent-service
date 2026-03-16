package com.livingagent.core.anomaly;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnomalyResult {
    
    private String resultId;
    private boolean anomalyDetected;
    private AnomalyLevel level;
    private String detectorType;
    private String message;
    private List<String> anomalies;
    private Map<String, Object> details;
    private Map<String, Object> recommendations;
    private Instant detectedAt;
    private double confidence;
    
    public enum AnomalyLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    public AnomalyResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.anomalies = new ArrayList<>();
        this.details = new HashMap<>();
        this.recommendations = new HashMap<>();
        this.detectedAt = Instant.now();
        this.confidence = 0.0;
    }
    
    public static AnomalyResult normal(String detectorType) {
        AnomalyResult result = new AnomalyResult();
        result.detectorType = detectorType;
        result.anomalyDetected = false;
        result.level = AnomalyLevel.INFO;
        result.message = "No anomaly detected";
        return result;
    }
    
    public static AnomalyResult warning(String detectorType, String message) {
        AnomalyResult result = new AnomalyResult();
        result.detectorType = detectorType;
        result.anomalyDetected = true;
        result.level = AnomalyLevel.WARNING;
        result.message = message;
        result.confidence = 0.7;
        return result;
    }
    
    public static AnomalyResult error(String detectorType, String message) {
        AnomalyResult result = new AnomalyResult();
        result.detectorType = detectorType;
        result.anomalyDetected = true;
        result.level = AnomalyLevel.ERROR;
        result.message = message;
        result.confidence = 0.9;
        return result;
    }
    
    public static AnomalyResult critical(String detectorType, String message) {
        AnomalyResult result = new AnomalyResult();
        result.detectorType = detectorType;
        result.anomalyDetected = true;
        result.level = AnomalyLevel.CRITICAL;
        result.message = message;
        result.confidence = 1.0;
        return result;
    }
    
    public void addAnomaly(String anomaly) {
        this.anomalies.add(anomaly);
    }
    
    public void addDetail(String key, Object value) {
        this.details.put(key, value);
    }
    
    public void addRecommendation(String action, String description) {
        this.recommendations.put(action, description);
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public boolean isAnomalyDetected() { return anomalyDetected; }
    public void setAnomalyDetected(boolean anomalyDetected) { this.anomalyDetected = anomalyDetected; }
    
    public AnomalyLevel getLevel() { return level; }
    public void setLevel(AnomalyLevel level) { this.level = level; }
    
    public String getDetectorType() { return detectorType; }
    public void setDetectorType(String detectorType) { this.detectorType = detectorType; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<String> getAnomalies() { return anomalies; }
    public void setAnomalies(List<String> anomalies) { this.anomalies = anomalies; }
    
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    
    public Map<String, Object> getRecommendations() { return recommendations; }
    public void setRecommendations(Map<String, Object> recommendations) { this.recommendations = recommendations; }
    
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    @Override
    public String toString() {
        return "AnomalyResult{detected=" + anomalyDetected + ", level=" + level + 
               ", message='" + message + "', confidence=" + confidence + '}';
    }
}
