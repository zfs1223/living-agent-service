package com.livingagent.core.security.speaker;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "speaker_profiles")
public class SpeakerProfile {

    @Id
    @Column(name = "speaker_id", length = 100)
    private String speakerId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "embedding")
    private float[] embedding;

    @Column(name = "embedding_dimension")
    private int embeddingDimension;

    @Column(name = "employee_id", length = 100)
    private String employeeId;

    @Column(name = "active")
    private boolean active;

    @Column(name = "match_count")
    private int matchCount;

    @Column(name = "last_matched_at")
    private Instant lastMatchedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "metadata", length = 2000)
    private String metadata;

    public SpeakerProfile() {
        this.active = true;
        this.matchCount = 0;
    }

    public String getSpeakerId() { return speakerId; }
    public void setSpeakerId(String speakerId) { this.speakerId = speakerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }

    public Instant getLastMatchedAt() { return lastMatchedAt; }
    public void setLastMatchedAt(Instant lastMatchedAt) { this.lastMatchedAt = lastMatchedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
