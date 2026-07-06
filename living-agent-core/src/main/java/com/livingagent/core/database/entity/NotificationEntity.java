package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String notificationId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String priority;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean read;

    protected NotificationEntity() {}

    public NotificationEntity(String notificationId, String department, String type,
                              String title, String content, String priority,
                              String metadataJson, Instant timestamp, boolean read) {
        this.notificationId = notificationId;
        this.department = department;
        this.type = type;
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.metadataJson = metadataJson;
        this.timestamp = timestamp;
        this.read = read;
    }

    public Long getId() { return id; }
    public String getNotificationId() { return notificationId; }
    public String getDepartment() { return department; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getPriority() { return priority; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }

    public void setRead(boolean read) { this.read = read; }
}
