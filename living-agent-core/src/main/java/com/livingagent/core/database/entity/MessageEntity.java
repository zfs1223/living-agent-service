package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String recipientId;

    @Column(length = 100)
    private String senderId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant readAt;

    protected MessageEntity() {}

    public MessageEntity(String messageId, String recipientId, String senderId,
                         String type, String title, String content,
                         String metadataJson, Instant createdAt, Instant readAt) {
        this.messageId = messageId;
        this.recipientId = recipientId;
        this.senderId = senderId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getRecipientId() { return recipientId; }
    public String getSenderId() { return senderId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public boolean isRead() { return readAt != null; }
}
