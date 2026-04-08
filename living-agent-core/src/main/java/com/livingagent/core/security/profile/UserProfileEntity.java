package com.livingagent.core.security.profile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    @Column(name = "profile_id", length = 64)
    private String profileId;

    @Column(name = "employee_id", length = 100, unique = true)
    private String employeeId;

    @Column(name = "speaker_id", length = 100, unique = true)
    private String speakerId;

    @Column(name = "digital_id", length = 200, unique = true)
    private String digitalId;

    @Column(name = "personality_config", columnDefinition = "JSONB")
    private String personalityConfig;

    @Column(name = "behavior_preferences", columnDefinition = "JSONB")
    private String behaviorPreferences;

    @Column(name = "knowledge_association", columnDefinition = "JSONB")
    private String knowledgeAssociation;

    @Column(name = "usage_statistics", columnDefinition = "JSONB")
    private String usageStatistics;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getSpeakerId() { return speakerId; }
    public void setSpeakerId(String speakerId) { this.speakerId = speakerId; }

    public String getDigitalId() { return digitalId; }
    public void setDigitalId(String digitalId) { this.digitalId = digitalId; }

    public String getPersonalityConfig() { return personalityConfig; }
    public void setPersonalityConfig(String personalityConfig) { this.personalityConfig = personalityConfig; }

    public String getBehaviorPreferences() { return behaviorPreferences; }
    public void setBehaviorPreferences(String behaviorPreferences) { this.behaviorPreferences = behaviorPreferences; }

    public String getKnowledgeAssociation() { return knowledgeAssociation; }
    public void setKnowledgeAssociation(String knowledgeAssociation) { this.knowledgeAssociation = knowledgeAssociation; }

    public String getUsageStatistics() { return usageStatistics; }
    public void setUsageStatistics(String usageStatistics) { this.usageStatistics = usageStatistics; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
