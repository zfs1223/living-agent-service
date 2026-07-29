package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_contacts")
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 100)
    private String contactId;

    @Column(nullable = false, length = 20)
    private String contactType = "PRIVATE";

    @Column(length = 100)
    private String roomId;

    @Column
    private Boolean muted = false;

    @Column
    private Boolean pinned = false;

    @Column
    private Boolean hidden = false;

    @Column
    private Boolean shield = false;

    @Column
    private Instant lastReadAt;

    @Column(length = 64)
    private String lastMessageId;

    @Column
    private String lastMessageContent;

    @Column
    private Instant lastMessageTime;

    @Column
    private Integer unreadCount = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UserContact() {}

    public UserContact(String userId, String contactId, String contactType, String roomId,
                       Boolean muted, Boolean pinned, Boolean hidden, Boolean shield,
                       Instant lastReadAt, String lastMessageId, String lastMessageContent,
                       Instant lastMessageTime, Integer unreadCount,
                       Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.contactId = contactId;
        this.contactType = contactType;
        this.roomId = roomId;
        this.muted = muted;
        this.pinned = pinned;
        this.hidden = hidden;
        this.shield = shield;
        this.lastReadAt = lastReadAt;
        this.lastMessageId = lastMessageId;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount = unreadCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getContactId() { return contactId; }
    public String getContactType() { return contactType; }
    public String getRoomId() { return roomId; }
    public Boolean getMuted() { return muted; }
    public Boolean getPinned() { return pinned; }
    public Boolean getHidden() { return hidden; }
    public Boolean getShield() { return shield; }
    public Instant getLastReadAt() { return lastReadAt; }
    public String getLastMessageId() { return lastMessageId; }
    public String getLastMessageContent() { return lastMessageContent; }
    public Instant getLastMessageTime() { return lastMessageTime; }
    public Integer getUnreadCount() { return unreadCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setContactId(String contactId) { this.contactId = contactId; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setMuted(Boolean muted) { this.muted = muted; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public void setHidden(Boolean hidden) { this.hidden = hidden; }
    public void setShield(Boolean shield) { this.shield = shield; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
    public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }
    public void setLastMessageContent(String lastMessageContent) { this.lastMessageContent = lastMessageContent; }
    public void setLastMessageTime(Instant lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
