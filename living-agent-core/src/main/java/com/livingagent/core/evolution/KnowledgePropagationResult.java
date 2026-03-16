package com.livingagent.core.evolution;

public class KnowledgePropagationResult {
    
    private String sourceAgent;
    private String targetAgent;
    private String knowledgeId;
    private boolean success;
    private String message;
    private long propagationTime;
    
    public KnowledgePropagationResult() {}
    
    public KnowledgePropagationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    public static KnowledgePropagationResult failed(String message) {
        KnowledgePropagationResult result = new KnowledgePropagationResult();
        result.success = false;
        result.message = message;
        return result;
    }
    
    public static KnowledgePropagationResult success(String sourceAgent, String targetAgent, String knowledgeId, double score) {
        KnowledgePropagationResult result = new KnowledgePropagationResult();
        result.success = true;
        result.sourceAgent = sourceAgent;
        result.targetAgent = targetAgent;
        result.knowledgeId = knowledgeId;
        result.message = "Propagation successful with score: " + score;
        return result;
    }
    
    public KnowledgePropagationResult(String sourceAgent, String targetAgent, String knowledgeId, boolean success, String message) {
        this.sourceAgent = sourceAgent;
        this.targetAgent = targetAgent;
        this.knowledgeId = knowledgeId;
        this.success = success;
        this.message = message;
    }
    
    public String getSourceAgent() { return sourceAgent; }
    public void setSourceAgent(String sourceAgent) { this.sourceAgent = sourceAgent; }
    
    public String getTargetAgent() { return targetAgent; }
    public void setTargetAgent(String targetAgent) { this.targetAgent = targetAgent; }
    
    public String getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(String knowledgeId) { this.knowledgeId = knowledgeId; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public long getPropagationTime() { return propagationTime; }
    public void setPropagationTime(long propagationTime) { this.propagationTime = propagationTime; }
    
    @Override
    public String toString() {
        return "KnowledgePropagationResult{" +
                "sourceAgent='" + sourceAgent + '\'' +
                ", targetAgent='" + targetAgent + '\'' +
                ", knowledgeId='" + knowledgeId + '\'' +
                ", success=" + success +
                '}';
    }
}
