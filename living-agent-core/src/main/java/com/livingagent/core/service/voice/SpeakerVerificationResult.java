package com.livingagent.core.service.voice;

import java.util.List;
import java.util.Map;

public class SpeakerVerificationResult {
    
    private final boolean success;
    private final boolean verified;
    private final String message;
    private String speakerId;
    private String name;
    private double similarity;
    private double threshold;
    private List<Map<String, Object>> allResults;
    
    public SpeakerVerificationResult(boolean success, boolean verified, String message) {
        this.success = success;
        this.verified = verified;
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isVerified() {
        return verified;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getSpeakerId() {
        return speakerId;
    }
    
    public void setSpeakerId(String speakerId) {
        this.speakerId = speakerId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public double getSimilarity() {
        return similarity;
    }
    
    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }
    
    public double getThreshold() {
        return threshold;
    }
    
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
    
    public List<Map<String, Object>> getAllResults() {
        return allResults;
    }
    
    public void setAllResults(List<Map<String, Object>> allResults) {
        this.allResults = allResults;
    }
    
    public static SpeakerVerificationResult success(boolean verified, String speakerId, double score) {
        SpeakerVerificationResult result = new SpeakerVerificationResult(true, verified, "Verification completed");
        result.setSpeakerId(speakerId);
        result.setSimilarity(score);
        return result;
    }
    
    public static SpeakerVerificationResult success(String speakerId, double score) {
        SpeakerVerificationResult result = new SpeakerVerificationResult(true, true, "Speaker verified");
        result.setSpeakerId(speakerId);
        result.setSimilarity(score);
        return result;
    }
    
    public static SpeakerVerificationResult failure(String message) {
        return new SpeakerVerificationResult(false, false, message);
    }
    
    public static SpeakerVerificationResult notVerified(double score) {
        SpeakerVerificationResult result = new SpeakerVerificationResult(true, false, "Speaker not verified");
        result.setSimilarity(score);
        return result;
    }
}
