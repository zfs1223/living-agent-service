package com.livingagent.core.neuron;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentAnalysisResult {
    
    private String resultId;
    private String imageUrl;
    private String extractedText;
    private List<TextBlock> textBlocks;
    private String documentType;
    private Map<String, Object> fields;
    private double confidence;
    private Instant analyzedAt;
    private long processingTimeMs;
    
    public DocumentAnalysisResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.textBlocks = new ArrayList<>();
        this.fields = new HashMap<>();
        this.analyzedAt = Instant.now();
    }
    
    public static DocumentAnalysisResult of(String extractedText) {
        DocumentAnalysisResult result = new DocumentAnalysisResult();
        result.extractedText = extractedText;
        return result;
    }
    
    public void addTextBlock(TextBlock block) {
        this.textBlocks.add(block);
    }
    
    public void addField(String key, Object value) {
        this.fields.put(key, value);
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    
    public List<TextBlock> getTextBlocks() { return textBlocks; }
    public void setTextBlocks(List<TextBlock> textBlocks) { this.textBlocks = textBlocks; }
    
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    
    public Map<String, Object> getFields() { return fields; }
    public void setFields(Map<String, Object> fields) { this.fields = fields; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    @Override
    public String toString() {
        return "DocumentAnalysisResult{type='" + documentType + "', textLength=" + 
               (extractedText != null ? extractedText.length() : 0) + "}";
    }
    
    public static class TextBlock {
        private String text;
        private DetectedObject.BoundingBox boundingBox;
        private double confidence;
        private String blockType;
        
        public TextBlock() {}
        
        public TextBlock(String text, double confidence) {
            this.text = text;
            this.confidence = confidence;
        }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public DetectedObject.BoundingBox getBoundingBox() { return boundingBox; }
        public void setBoundingBox(DetectedObject.BoundingBox boundingBox) { this.boundingBox = boundingBox; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getBlockType() { return blockType; }
        public void setBlockType(String blockType) { this.blockType = blockType; }
    }
}
