package com.livingagent.core.neuron;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ComparisonResult {
    
    private String resultId;
    private String imageUrl1;
    private String imageUrl2;
    private double similarity;
    private boolean match;
    private double confidence;
    private List<String> differences;
    private List<String> similarities;
    private Instant comparedAt;
    
    public ComparisonResult() {
        this.resultId = java.util.UUID.randomUUID().toString();
        this.differences = new ArrayList<>();
        this.similarities = new ArrayList<>();
        this.comparedAt = Instant.now();
    }
    
    public static ComparisonResult of(double similarity) {
        ComparisonResult result = new ComparisonResult();
        result.similarity = similarity;
        result.match = similarity > 0.8;
        return result;
    }
    
    public void addDifference(String difference) {
        this.differences.add(difference);
    }
    
    public void addSimilarity(String similarity) {
        this.similarities.add(similarity);
    }
    
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    
    public String getImageUrl1() { return imageUrl1; }
    public void setImageUrl1(String imageUrl1) { this.imageUrl1 = imageUrl1; }
    
    public String getImageUrl2() { return imageUrl2; }
    public void setImageUrl2(String imageUrl2) { this.imageUrl2 = imageUrl2; }
    
    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }
    
    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public List<String> getDifferences() { return differences; }
    public void setDifferences(List<String> differences) { this.differences = differences; }
    
    public List<String> getSimilarities() { return similarities; }
    public void setSimilarities(List<String> similarities) { this.similarities = similarities; }
    
    public Instant getComparedAt() { return comparedAt; }
    public void setComparedAt(Instant comparedAt) { this.comparedAt = comparedAt; }
    
    @Override
    public String toString() {
        return "ComparisonResult{similarity=" + String.format("%.2f", similarity) + ", match=" + match + "}";
    }
}
