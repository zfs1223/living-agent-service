package com.livingagent.core.model.pool;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "brain_model_change_history")
public class BrainModelChangeHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "brain_id", nullable = false, length = 255)
    private String brainId;
    
    @Column(name = "brain_name", nullable = false, length = 255)
    private String brainName;
    
    @Column(name = "brain_type", nullable = false, length = 50)
    private String brainType;
    
    @Column(name = "model_id", nullable = false)
    private UUID modelId;
    
    @Column(name = "model_name", length = 255)
    private String modelName;
    
    @Column(name = "source", nullable = false, length = 50)
    private String source;
    
    @Column(name = "changed_by", length = 255)
    private String changedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    public BrainModelChangeHistory() {
    }
    
    public BrainModelChangeHistory(String brainId, String brainName, String brainType,
                                   UUID modelId, String modelName, String source,
                                   String changedBy, String reason) {
        this.brainId = brainId;
        this.brainName = brainName;
        this.brainType = brainType;
        this.modelId = modelId;
        this.modelName = modelName;
        this.source = source;
        this.changedBy = changedBy;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getBrainId() { return brainId; }
    public void setBrainId(String brainId) { this.brainId = brainId; }
    
    public String getBrainName() { return brainName; }
    public void setBrainName(String brainName) { this.brainName = brainName; }
    
    public String getBrainType() { return brainType; }
    public void setBrainType(String brainType) { this.brainType = brainType; }
    
    public UUID getModelId() { return modelId; }
    public void setModelId(UUID modelId) { this.modelId = modelId; }
    
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
