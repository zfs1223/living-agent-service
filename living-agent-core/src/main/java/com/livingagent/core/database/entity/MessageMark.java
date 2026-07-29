package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_marks")
public class MessageMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 20)
    private String markType;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected MessageMark() {}

    public MessageMark(String messageId, String userId, String markType, String status, Instant createdAt) {
        this.messageId = messageId;
        this.userId = userId;
        this.markType = markType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getUserId() { return userId; }
    public String getMarkType() { return markType; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setMarkType(String markType) { this.markType = markType; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
