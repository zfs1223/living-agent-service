package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Experience {
    
    private String experienceId;
    private String profileId;
    private String title;
    private String description;
    private String context;
    private List<String> steps;
    private Map<String, Object> parameters;
    private Map<String, Object> results;
    private boolean successful;
    private String lessonLearned;
    private List<String> tags;
    private Instant occurredAt;
    private Instant recordedAt;
    private String recordedBy;
    private int usefulnessScore;
    
    public Experience() {
        this.experienceId = java.util.UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.parameters = new HashMap<>();
        this.results = new HashMap<>();
        this.tags = new ArrayList<>();
        this.occurredAt = Instant.now();
        this.recordedAt = Instant.now();
        this.usefulnessScore = 0;
    }
    
    public Experience(String title, String description) {
        this();
        this.title = title;
        this.description = description;
    }
    
    public Experience(String profileId, String context, Object content, Instant createdAt) {
        this();
        this.profileId = profileId;
        this.context = context;
        this.description = content != null ? content.toString() : null;
        this.occurredAt = createdAt;
    }

    /**
     * 兼容旧代码中对 experience.getContent() 的调用，
     * 这里将内容等同于描述文本。
     */
    public String getContent() {
        return description;
    }
    
    public void addStep(String step) {
        this.steps.add(step);
    }
    
    public void addTag(String tag) {
        this.tags.add(tag);
    }
    
    public void recordResult(String key, Object value) {
        this.results.put(key, value);
    }
    
    public String getExperienceId() { return experienceId; }
    public void setExperienceId(String experienceId) { this.experienceId = experienceId; }
    
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    public Map<String, Object> getResults() { return results; }
    public void setResults(Map<String, Object> results) { this.results = results; }
    
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    
    public String getLessonLearned() { return lessonLearned; }
    public void setLessonLearned(String lessonLearned) { this.lessonLearned = lessonLearned; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    
    public int getUsefulnessScore() { return usefulnessScore; }
    public void setUsefulnessScore(int usefulnessScore) { this.usefulnessScore = usefulnessScore; }
    
    @Override
    public String toString() {
        return "Experience{title='" + title + "', successful=" + successful + "}";
    }
}
