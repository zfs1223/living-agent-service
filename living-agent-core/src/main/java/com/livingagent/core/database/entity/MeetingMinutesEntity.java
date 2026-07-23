package com.livingagent.core.database.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 会议纪要实体 - 闭环 68 录制与纪要自动化 / P82
 *
 * <p>对应数据库表 meeting_minutes，存储会议纪要的完整信息，
 * 包括转写全文、摘要、决议事项、待办任务、关键数据等。</p>
 *
 * <h3>状态机</h3>
 * <pre>
 * GENERATING → COMPLETED
 *    ↓
 * FAILED
 * </pre>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */
@Entity
@Table(name = "meeting_minutes")
public class MeetingMinutesEntity {

    /** 主键（自增ID） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 纪要唯一标识（UUID格式） */
    @Column(name = "minutes_id", nullable = false, unique = true, length = 64)
    private String minutesId;

    /** LiveKit 房间名称 */
    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    /** 关联的预约ID（可选，与 meeting_schedules 关联） */
    @Column(name = "schedule_id", length = 64)
    private String scheduleId;

    /** 会议标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 转写全文 */
    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText;

    /** 摘要 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 决议事项（JSON数组） */
    @Column(name = "resolutions", columnDefinition = "TEXT")
    private String resolutions;

    /** 待办任务（JSON数组，包含责任人） */
    @Column(name = "action_items", columnDefinition = "TEXT")
    private String actionItems;

    /** 关键数据/金额（JSON数组） */
    @Column(name = "key_data", columnDefinition = "TEXT")
    private String keyData;

    /** 录制文件URL */
    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    /** 纪要文件存储路径（如 data/artifacts/meeting-minutes/{roomName}.md） */
    @Column(name = "minutes_file_path", length = 500)
    private String minutesFilePath;

    /** 纪要状态：GENERATING/COMPLETED/FAILED */
    @Column(nullable = false, length = 20)
    private String status = "GENERATING";

    /** 纪要生成完成时间 */
    @Column(name = "generated_at")
    private Instant generatedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MeetingMinutesEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 创建纪要实体的便捷构造方法
     *
     * @param minutesId  纪要唯一标识
     * @param roomName   房间名称
     * @param title      会议标题
     */
    public MeetingMinutesEntity(String minutesId, String roomName, String title) {
        this.minutesId = minutesId;
        this.roomName = roomName;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 更新时间戳（每次修改后调用）
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }

    public String getMinutesId() { return minutesId; }
    public void setMinutesId(String minutesId) { this.minutesId = minutesId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getResolutions() { return resolutions; }
    public void setResolutions(String resolutions) { this.resolutions = resolutions; }

    public String getActionItems() { return actionItems; }
    public void setActionItems(String actionItems) { this.actionItems = actionItems; }

    public String getKeyData() { return keyData; }
    public void setKeyData(String keyData) { this.keyData = keyData; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public String getMinutesFilePath() { return minutesFilePath; }
    public void setMinutesFilePath(String minutesFilePath) { this.minutesFilePath = minutesFilePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
