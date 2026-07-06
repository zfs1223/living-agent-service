package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "brain_boundary_audit")
public class BrainBoundaryAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String brainId;

    @Column(nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String result;

    private String violationType;

    private String message;

    protected BrainBoundaryAuditEntity() {}

    public BrainBoundaryAuditEntity(Instant timestamp, String brainId, String actionType,
                                    String result, String violationType, String message) {
        this.timestamp = timestamp;
        this.brainId = brainId;
        this.actionType = actionType;
        this.result = result;
        this.violationType = violationType;
        this.message = message;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getBrainId() { return brainId; }
    public String getActionType() { return actionType; }
    public String getResult() { return result; }
    public String getViolationType() { return violationType; }
    public String getMessage() { return message; }
}
