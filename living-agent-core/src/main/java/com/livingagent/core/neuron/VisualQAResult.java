package com.livingagent.core.neuron;

import java.time.Instant;

public class VisualQAResult {
    
    private String resultId;
    private String imageUrl;
    private String question;
    private String answer;
    private double confidence;
    private Instant answeredAt;
    private long processingTimeMs;
    
    public VisualQAResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.answeredAt = Instant.now();
    }
    
    public static VisualQAResult of(String question, String answer) {
        VisualQAResult result = new VisualQAResult();
        result.question = question;
        result.answer = answer;
        return result;
    }
    
    public static VisualQAResult of(String question, String answer, double confidence) {
        VisualQAResult result = of(question, answer);
        result.confidence = confidence;
        return result;
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    @Override
    public String toString() {
        return "VisualQAResult{question='" + question + "', answer='" + answer + "'}";
    }
}
