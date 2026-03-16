package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BestPractice {
    
    private String practiceId;
    private String title;
    private String domain;
    private String description;
    private List<String> steps;
    private List<String> prerequisites;
    private List<String> expectedOutcomes;
    private List<String> commonPitfalls;
    private int applicabilityScore;
    private int successRate;
    private Instant createdAt;
    private Instant lastApplied;
    private int applicationCount;
    private String author;
    
    public BestPractice() {
        this.practiceId = java.util.UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.prerequisites = new ArrayList<>();
        this.expectedOutcomes = new ArrayList<>();
        this.commonPitfalls = new ArrayList<>();
        this.createdAt = Instant.now();
        this.applicationCount = 0;
        this.successRate = 0;
        this.applicabilityScore = 100;
    }
    
    public BestPractice(String title, String domain, String description) {
        this();
        this.title = title;
        this.domain = domain;
        this.description = description;
    }
    
    public void addStep(String step) {
        this.steps.add(step);
    }
    
    public void addPrerequisite(String prerequisite) {
        this.prerequisites.add(prerequisite);
    }
    
    public void addExpectedOutcome(String outcome) {
        this.expectedOutcomes.add(outcome);
    }
    
    public void addCommonPitfall(String pitfall) {
        this.commonPitfalls.add(pitfall);
    }
    
    public void recordApplication(boolean successful) {
        this.applicationCount++;
        this.lastApplied = Instant.now();
        int previousSuccesses = (int) (this.successRate * (applicationCount - 1) / 100.0);
        if (successful) {
            previousSuccesses++;
        }
        this.successRate = (int) ((previousSuccesses * 100.0) / applicationCount);
    }
    
    public String getPracticeId() { return practiceId; }
    public void setPracticeId(String practiceId) { this.practiceId = practiceId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    
    public List<String> getPrerequisites() { return prerequisites; }
    public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }
    
    public List<String> getExpectedOutcomes() { return expectedOutcomes; }
    public void setExpectedOutcomes(List<String> expectedOutcomes) { this.expectedOutcomes = expectedOutcomes; }
    
    public List<String> getCommonPitfalls() { return commonPitfalls; }
    public void setCommonPitfalls(List<String> commonPitfalls) { this.commonPitfalls = commonPitfalls; }
    
    public int getApplicabilityScore() { return applicabilityScore; }
    public void setApplicabilityScore(int applicabilityScore) { this.applicabilityScore = applicabilityScore; }
    
    public int getSuccessRate() { return successRate; }
    public void setSuccessRate(int successRate) { this.successRate = successRate; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getLastApplied() { return lastApplied; }
    public void setLastApplied(Instant lastApplied) { this.lastApplied = lastApplied; }
    
    public int getApplicationCount() { return applicationCount; }
    public void setApplicationCount(int applicationCount) { this.applicationCount = applicationCount; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    @Override
    public String toString() {
        return "BestPractice{title='" + title + "', domain='" + domain + "', successRate=" + successRate + "%}";
    }
}
