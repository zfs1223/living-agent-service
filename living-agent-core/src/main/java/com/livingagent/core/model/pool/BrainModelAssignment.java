package com.livingagent.core.model.pool;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brain_model_assignments")
public class BrainModelAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(name = "brain_id", length = 100, nullable = false, unique = true)
    private String brainId;

    @Column(name = "brain_name", length = 200)
    private String brainName;

    @Column(name = "brain_type", length = 50)
    private String brainType;

    @Column(name = "model_id")
    private java.util.UUID modelId;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public BrainModelAssignment() {}

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public String getBrainId() { return brainId; }
    public void setBrainId(String brainId) { this.brainId = brainId; }

    public String getBrainName() { return brainName; }
    public void setBrainName(String brainName) { this.brainName = brainName; }

    public String getBrainType() { return brainType; }
    public void setBrainType(String brainType) { this.brainType = brainType; }

    public java.util.UUID getModelId() { return modelId; }
    public void setModelId(java.util.UUID modelId) { this.modelId = modelId; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
