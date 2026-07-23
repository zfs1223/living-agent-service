package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 会议实体 - 闭环 67-C 会议状态持久化 / P81 LiveKit 部署与会议基础
 *
 * <p>对应数据库表 meetings，存储会议的运行时状态信息，
 * 由 LiveKitWebhookHandler 接收事件后持久化，替代内存缓存。</p>
 *
 * <h3>状态机</h3>
 * <pre>
 * ACTIVE → FINISHED
 * </pre>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@Entity
@Table(name = "meetings")
public class MeetingEntity {

    /** 主键（自增ID） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LiveKit 房间名称（业务主键） */
    @Column(name = "room_name", nullable = false, unique = true, length = 100)
    private String roomName;

    /** LiveKit 房间 SID */
    @Column(name = "room_sid", length = 64)
    private String roomSid;

    /** 会议状态：ACTIVE/FINISHED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 所属部门代码（从房间名称解析：dept-{deptCode}-meeting-*） */
    @Column(length = 50)
    private String department;

    /** 创建人ID */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 关联的预约ID（可选，与 meeting_schedules 关联） */
    @Column(name = "schedule_id", length = 64)
    private String scheduleId;

    /** 最大参会人数 */
    @Column(name = "max_participants")
    private int maxParticipants = 50;

    /** 当前参会人数 */
    @Column(name = "participant_count")
    private int participantCount = 0;

    /** 参会者ID列表（JSON数组） */
    @Column(name = "participants_json", columnDefinition = "TEXT")
    private String participantsJson;

    /** 是否正在录制 */
    @Column(name = "recording_active")
    private boolean recordingActive = false;

    /** 录制 Egress ID */
    @Column(name = "egress_id", length = 100)
    private String egressId;

    /** 会议开始时间 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 会议结束时间 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 更新时间 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MeetingEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 创建会议实体的便捷构造方法
     *
     * @param roomName  房间名称
     * @param roomSid   房间SID
     * @param department 部门代码
     */
    public MeetingEntity(String roomName, String roomSid, String department) {
        this.roomName = roomName;
        this.roomSid = roomSid;
        this.department = department;
        this.startedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 从房间名称解析部门代码
     * 房间命名规范: dept-{departmentCode}-meeting-{uuid}
     */
    public static String parseDepartmentFromRoomName(String roomName) {
        if (roomName != null && roomName.startsWith("dept-")) {
            int secondDash = roomName.indexOf('-', 5);
            if (secondDash > 5) {
                return roomName.substring(5, secondDash);
            }
        }
        return null;
    }

    /**
     * 更新时间戳（每次修改后调用）
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRoomSid() { return roomSid; }
    public void setRoomSid(String roomSid) { this.roomSid = roomSid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }

    public String getParticipantsJson() { return participantsJson; }
    public void setParticipantsJson(String participantsJson) { this.participantsJson = participantsJson; }

    public boolean isRecordingActive() { return recordingActive; }
    public void setRecordingActive(boolean recordingActive) { this.recordingActive = recordingActive; }

    public String getEgressId() { return egressId; }
    public void setEgressId(String egressId) { this.egressId = egressId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
