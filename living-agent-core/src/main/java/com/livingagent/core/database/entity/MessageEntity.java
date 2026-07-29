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

    @Column(length = 100, nullable = false)
    private String senderId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant readAt;

    // ---- IM 扩展字段 (P89) ----

    @Column(name = "reply_to_id", length = 64)
    private String replyToId;

    @Column(name = "gap_count")
    private Integer gapCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edited_by", length = 100)
    private String editedBy;

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

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public Integer getGapCount() { return gapCount; }
    public void setGapCount(Integer gapCount) { this.gapCount = gapCount; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public Instant getEditedAt() { return editedAt; }
    public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }

    public String getEditedBy() { return editedBy; }
    public void setEditedBy(String editedBy) { this.editedBy = editedBy; }

    public boolean isRecalled() { return deletedAt != null; }
}
