package com.livingagent.core.neuron;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class FaceAnalysisResult {
    
    private String resultId;
    private String imageUrl;
    private int faceCount;
    private double confidence;
    private Map<String, Object> attributes;
    private Instant analyzedAt;
    
    public FaceAnalysisResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.attributes = new HashMap<>();
        this.analyzedAt = Instant.now();
    }
    
    public static FaceAnalysisResult of(int faceCount) {
        FaceAnalysisResult result = new FaceAnalysisResult();
        result.faceCount = faceCount;
        return result;
    }
    
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public int getFaceCount() { return faceCount; }
    public void setFaceCount(int faceCount) { this.faceCount = faceCount; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    
    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
    
    @Override
    public String toString() {
        return "FaceAnalysisResult{faceCount=" + faceCount + ", confidence=" + confidence + "}";
    }
}
