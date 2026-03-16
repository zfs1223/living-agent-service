package com.livingagent.core.evolution;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

public class KnowledgeQualityReport {
    
    private int totalKnowledge;
    private int totalExperiences;
    private int totalBestPractices;
    private int validatedCount;
    private int verifiedCount;
    private int highImportanceCount;
    private int expiredCount;
    private int lowConfidenceCount;
    private int outdatedCount;
    private int duplicateCount;
    private double averageRelevance;
    private double averageConfidence;
    private double coverageScore;
    private double qualityScore;
    private Instant generatedAt;
    private List<KnowledgeIssue> issues;
    
    public KnowledgeQualityReport() {
        this.issues = new ArrayList<>();
    }
    
    public KnowledgeQualityReport(int totalKnowledge, int totalExperiences, int totalBestPractices,
                                   int validatedCount, int outdatedCount, int duplicateCount,
                                   double averageRelevance, double averageConfidence, double coverageScore,
                                   List<KnowledgeIssue> issues) {
        this.totalKnowledge = totalKnowledge;
        this.totalExperiences = totalExperiences;
        this.totalBestPractices = totalBestPractices;
        this.validatedCount = validatedCount;
        this.outdatedCount = outdatedCount;
        this.duplicateCount = duplicateCount;
        this.averageRelevance = averageRelevance;
        this.averageConfidence = averageConfidence;
        this.coverageScore = coverageScore;
        this.issues = issues != null ? issues : new ArrayList<>();
    }
    
    public int getTotalKnowledge() { return totalKnowledge; }
    public void setTotalKnowledge(int totalKnowledge) { this.totalKnowledge = totalKnowledge; }
    
    public int getTotalExperiences() { return totalExperiences; }
    public void setTotalExperiences(int totalExperiences) { this.totalExperiences = totalExperiences; }
    
    public int getTotalBestPractices() { return totalBestPractices; }
    public void setTotalBestPractices(int totalBestPractices) { this.totalBestPractices = totalBestPractices; }
    
    public int getValidatedCount() { return validatedCount; }
    public void setValidatedCount(int validatedCount) { this.validatedCount = validatedCount; }
    
    public int getOutdatedCount() { return outdatedCount; }
    public void setOutdatedCount(int outdatedCount) { this.outdatedCount = outdatedCount; }
    
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
    
    public double getAverageRelevance() { return averageRelevance; }
    public void setAverageRelevance(double averageRelevance) { this.averageRelevance = averageRelevance; }
    
    public double getAverageConfidence() { return averageConfidence; }
    public void setAverageConfidence(double averageConfidence) { this.averageConfidence = averageConfidence; }
    
    public double getCoverageScore() { return coverageScore; }
    public void setCoverageScore(double coverageScore) { this.coverageScore = coverageScore; }
    
    public int getVerifiedCount() { return verifiedCount; }
    public void setVerifiedCount(int verifiedCount) { this.verifiedCount = verifiedCount; }
    
    public int getHighImportanceCount() { return highImportanceCount; }
    public void setHighImportanceCount(int highImportanceCount) { this.highImportanceCount = highImportanceCount; }
    
    public int getExpiredCount() { return expiredCount; }
    public void setExpiredCount(int expiredCount) { this.expiredCount = expiredCount; }
    
    public int getLowConfidenceCount() { return lowConfidenceCount; }
    public void setLowConfidenceCount(int lowConfidenceCount) { this.lowConfidenceCount = lowConfidenceCount; }
    
    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
    
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    
    public List<KnowledgeIssue> getIssues() { return issues; }
    public void setIssues(List<KnowledgeIssue> issues) { this.issues = issues; }
    
    public void addIssue(KnowledgeIssue issue) {
        this.issues.add(issue);
    }
    
    public double getOverallScore() {
        if (totalKnowledge == 0) return 0.0;
        double validationScore = (double) validatedCount / totalKnowledge;
        double relevanceScore = averageRelevance / 100.0;
        double confidenceScore = averageConfidence;
        double issuePenalty = Math.min(issues.size() * 0.05, 0.3);
        return Math.max(0, (validationScore * 0.3 + relevanceScore * 0.3 + confidenceScore * 0.4) - issuePenalty);
    }
    
    @Override
    public String toString() {
        return "KnowledgeQualityReport{" +
                "totalKnowledge=" + totalKnowledge +
                ", validatedCount=" + validatedCount +
                ", averageRelevance=" + averageRelevance +
                ", overallScore=" + getOverallScore() +
                ", issues=" + issues.size() +
                '}';
    }
}
