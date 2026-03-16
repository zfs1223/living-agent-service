package com.livingagent.core.neuron;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ImageAnalysisResult {
    
    private String resultId;
    private String imageUrl;
    private String prompt;
    private String description;
    private double confidence;
    private Map<String, Object> metadata;
    private Instant analyzedAt;
    private long processingTimeMs;
    private String modelUsed;
    
    public ImageAnalysisResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.analyzedAt = Instant.now();
    }
    
    public static ImageAnalysisResult of(String description) {
        ImageAnalysisResult result = new ImageAnalysisResult();
        result.description = description;
        return result;
    }
    
    public static ImageAnalysisResult of(String description, double confidence) {
        ImageAnalysisResult result = of(description);
        result.confidence = confidence;
        return result;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    
    @Override
    public String toString() {
        return "ImageAnalysisResult{description='" + description + "', confidence=" + confidence + "}";
    }
}
