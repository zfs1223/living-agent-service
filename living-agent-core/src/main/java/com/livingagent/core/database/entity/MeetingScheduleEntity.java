package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 会议预约实体 - 闭环 67-D 预约管理 / P84 会议预约与通知
 *
 * <p>对应数据库表 meeting_schedules，存储会议预约的完整信息，
 * 包括预约时间、参会人员、提醒配置、日历同步状态等。</p>
 *
 * <h3>状态机</h3>
 * <pre>
 * SCHEDULED → ACTIVE → COMPLETED
 *   ↓
 * CANCELLED
 * </pre>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
@Entity
@Table(name = "meeting_schedules")
public class MeetingScheduleEntity {

    /** 主键（自增ID） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 预约唯一标识（UUID格式） */
    @Column(name = "schedule_id", nullable = false, unique = true, length = 64)
    private String scheduleId;

    /** 会议主题（必填，最长200字符） */
    @Column(nullable = false, length = 200)
    private String title;

    /** 会议描述（可选） */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 创建人ID（对应 LAS 用户 employeeId） */
    @Column(name = "creator_id", nullable = false, length = 100)
    private String creatorId;

    /** 会议地点（可选，如"会议室A"、"线上"） */
    @Column(length = 200)
    private String location;

    /** 会议类型（可选，如"regular"、"standup"、"review"） */
    @Column(name = "meeting_type", length = 50)
    private String meetingType;

    /** 所属部门代码（如 tech/hr/finance，对齐 P14 自动填充） */
    @Column(nullable = false, length = 50)
    private String department;

    /** LiveKit 房间名称（预约时自动生成） */
    @Column(name = "room_name", length = 100)
    private String roomName;

    /** 最大参会人数（默认50） */
    @Column(name = "max_participants")
    private int maxParticipants = 50;

    /** 预约开始时间（必填） */
    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    /** 预约结束时间（必填） */
    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    /** 实际开始时间（会议启动时填充） */
    @Column(name = "actual_start")
    private Instant actualStart;

    /** 实际结束时间（会议结束时填充） */
    @Column(name = "actual_end")
    private Instant actualEnd;

    /** 预约状态：SCHEDULED/ACTIVE/COMPLETED/CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    /** 是否已发送提醒通知 */
    @Column(name = "reminder_sent")
    private boolean reminderSent = false;

    /** 会议开始前多少分钟提醒（默认15分钟） */
    @Column(name = "reminder_minutes_before")
    private int reminderMinutesBefore = 15;

    /** 是否启用录制 */
    @Column(name = "enable_recording")
    private boolean enableRecording = false;

    /** 外部日历同步事件ID（飞书/企业微信/Outlook 等返回的事件ID） */
    @Column(name = "calendar_event_id", length = 200)
    private String calendarEventId;

    /** 使用的日历适配器名称（feishu/wechat/dingtalk/outlook/local） */
    @Column(name = "calendar_sync_adapter", length = 50)
    private String calendarSyncAdapter;

    /** 扩展元数据（JSON格式，存储参会人列表等灵活数据） */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MeetingScheduleEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 创建预约实体的便捷构造方法
     *
     * @param scheduleId      预约ID
     * @param title           会议主题
     * @param description     会议描述
     * @param creatorId       创建人ID
     * @param department      所属部门
     * @param scheduledStart  预约开始时间
     * @param scheduledEnd    预约结束时间
     */
    public MeetingScheduleEntity(String scheduleId, String title, String description,
                                  String creatorId, String department,
                                  Instant scheduledStart, Instant scheduledEnd) {
        this.scheduleId = scheduleId;
        this.title = title;
        this.description = description;
        this.creatorId = creatorId;
        this.department = department;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    /** 创建人ID的别名方法（兼容 P84 日历适配器） */
    public String getCreatedBy() { return creatorId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getMeetingType() { return meetingType; }
    public void setMeetingType(String meetingType) { this.meetingType = meetingType; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public Instant getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }

    public Instant getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(Instant scheduledEnd) { this.scheduledEnd = scheduledEnd; }

    public Instant getActualStart() { return actualStart; }
    public void setActualStart(Instant actualStart) { this.actualStart = actualStart; }

    public Instant getActualEnd() { return actualEnd; }
    public void setActualEnd(Instant actualEnd) { this.actualEnd = actualEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }

    public int getReminderMinutesBefore() { return reminderMinutesBefore; }
    public void setReminderMinutesBefore(int reminderMinutesBefore) { this.reminderMinutesBefore = reminderMinutesBefore; }

    public boolean isEnableRecording() { return enableRecording; }
    public void setEnableRecording(boolean enableRecording) { this.enableRecording = enableRecording; }

    public String getCalendarEventId() { return calendarEventId; }
    public void setCalendarEventId(String calendarEventId) { this.calendarEventId = calendarEventId; }

    public String getCalendarSyncAdapter() { return calendarSyncAdapter; }
    public void setCalendarSyncAdapter(String calendarSyncAdapter) { this.calendarSyncAdapter = calendarSyncAdapter; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 更新时间戳（每次修改后调用）
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * 计算预约时长（分钟）
     */
    public long getDurationMinutes() {
        if (scheduledStart == null || scheduledEnd == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(scheduledStart, scheduledEnd);
    }
}
