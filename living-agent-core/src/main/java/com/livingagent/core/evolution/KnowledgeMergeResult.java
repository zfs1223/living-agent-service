package com.livingagent.core.evolution;

import com.livingagent.core.knowledge.KnowledgeEntry;

public class KnowledgeMergeResult {
    
    private String sourceId;
    private String targetId;
    private String mergedId;
    private boolean success;
    private String message;
    private double similarityScore;
    private int mergedFields;
    private KnowledgeEntry mergedEntry;
    
    public KnowledgeMergeResult() {}
    
    public KnowledgeMergeResult(String sourceId, String targetId, String mergedId, boolean success, String message) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.mergedId = mergedId;
        this.success = success;
        this.message = message;
    }
    
    public static KnowledgeMergeResult failed(String message) {
        KnowledgeMergeResult result = new KnowledgeMergeResult();
        result.success = false;
        result.message = message;
        return result;
    }
    
    public static KnowledgeMergeResult success(String targetId, String sourceId, KnowledgeEntry mergedEntry, double similarity) {
        KnowledgeMergeResult result = new KnowledgeMergeResult();
        result.success = true;
        result.targetId = targetId;
        result.sourceId = sourceId;
        result.mergedEntry = mergedEntry;
        result.similarityScore = similarity;
        result.message = "Merge successful";
        return result;
    }
    
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    
    public String getMergedId() { return mergedId; }
    public void setMergedId(String mergedId) { this.mergedId = mergedId; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
    
    public int getMergedFields() { return mergedFields; }
    public void setMergedFields(int mergedFields) { this.mergedFields = mergedFields; }
    
    @Override
    public String toString() {
        return "KnowledgeMergeResult{" +
                "sourceId='" + sourceId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", mergedId='" + mergedId + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
}
