package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.MeetingScheduleEntity;
import com.livingagent.core.database.repository.MeetingScheduleRepository;
import com.livingagent.gateway.service.DepartmentNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 会议预约核心服务 - 闭环 67-D 预约管理 / P84 会议预约与通知
 *
 * <p>提供会议预约的创建、取消、状态更新等核心功能，
 * 以及定时检查提醒（复用闭环 44 DepartmentNotificationService）和自动启动会议。</p>
 *
 * <h3>定时任务</h3>
 * <ul>
 *   <li>每 60 秒检查：距开始 15 分钟内的预约 → 发送提醒通知（闭环 44）</li>
 *   <li>每 60 秒检查：已到开始时间的预约 → 自动创建 LiveKit 房间 + 更新状态为 ACTIVE</li>
 *   <li>每 60 秒检查：已到结束时间的预约 → 更新状态为 COMPLETED</li>
 * </ul>
 *
 * <h3>通知复用（闭环 44）</h3>
 * <p>会议提醒/开始/取消通知全部通过 {@link DepartmentNotificationService} 发送，
 * 复用现有通知体系，不重复建设。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
@Service
public class MeetingScheduleService {

    private static final Logger log = LoggerFactory.getLogger(MeetingScheduleService.class);

    /** 默认提醒提前量（分钟） */
    private static final int DEFAULT_REMINDER_MINUTES = 15;

    private final MeetingScheduleRepository scheduleRepository;
    private final DepartmentNotificationService notificationService;
    private final LiveKitRoomService liveKitRoomService;
    private final MeetingCalendarSyncService calendarSyncService;
    private final ObjectMapper objectMapper;

    public MeetingScheduleService(
            MeetingScheduleRepository scheduleRepository,
            DepartmentNotificationService notificationService,
            LiveKitRoomService liveKitRoomService,
            MeetingCalendarSyncService calendarSyncService,
            ObjectMapper objectMapper) {
        this.scheduleRepository = scheduleRepository;
        this.notificationService = notificationService;
        this.liveKitRoomService = liveKitRoomService;
        this.calendarSyncService = calendarSyncService;
        this.objectMapper = objectMapper;
        log.info("[P84] MeetingScheduleService 初始化");
    }

    // ========== 预约 CRUD ==========

    /**
     * 创建会议预约（闭环 67-D-1）
     *
     * <p>流程：生成 scheduleId → 生成 roomName → 持久化 → 发送邀请通知</p>
     *
     * @param schedule 预约实体（由 Controller 构建基础字段）
     * @return 保存后的预约实体（含自动生成的字段）
     */
    @Transactional
    public MeetingScheduleEntity createSchedule(MeetingScheduleEntity schedule) {
        // 生成 scheduleId（如果未提供）
        if (schedule.getScheduleId() == null || schedule.getScheduleId().isBlank()) {
            schedule.setScheduleId(UUID.randomUUID().toString());
        }

        // 生成 LiveKit 房间名称（格式: dept-{deptCode}-meeting-{uuid8}）
        if (schedule.getRoomName() == null || schedule.getRoomName().isBlank()) {
            String deptCode = schedule.getDepartment() != null ? schedule.getDepartment() : "general";
            String uuid8 = UUID.randomUUID().toString().substring(0, 8);
            schedule.setRoomName("dept-" + deptCode + "-meeting-" + uuid8);
        }

        // 设置默认值
        if (schedule.getStatus() == null) {
            schedule.setStatus("SCHEDULED");
        }
        if (schedule.getMaxParticipants() <= 0) {
            schedule.setMaxParticipants(50);
        }

        // 持久化
        MeetingScheduleEntity saved = scheduleRepository.save(schedule);

        // 日历同步（闭环 67-D-1：iCal 导出 + 外部 OA 同步）
        calendarSyncService.syncToCalendar(saved);

        // 发送会议邀请通知（复用闭环 44）
        sendInviteNotification(saved);

        log.info("[P84] 会议预约创建成功 - scheduleId={}, title={}, department={}, createdBy={}",
                saved.getScheduleId(), saved.getTitle(), saved.getDepartment(), saved.getCreatorId());

        return saved;
    }

    /**
     * 更新预约信息
     *
     * @param scheduleId 预约ID
     * @param updates    需要更新的字段（Map 形式）
     * @return 更新后的预约实体
     */
    @Transactional
    public MeetingScheduleEntity updateSchedule(String scheduleId, Map<String, Object> updates) {
        MeetingScheduleEntity schedule = findScheduleOrThrow(scheduleId);

        // 逐一更新允许修改的字段
        if (updates.containsKey("title")) {
            schedule.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("description")) {
            schedule.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("scheduledStart")) {
            schedule.setScheduledStart(parseInstant(updates.get("scheduledStart")));
        }
        if (updates.containsKey("scheduledEnd")) {
            schedule.setScheduledEnd(parseInstant(updates.get("scheduledEnd")));
        }
        if (updates.containsKey("maxParticipants")) {
            schedule.setMaxParticipants(((Number) updates.get("maxParticipants")).intValue());
        }
        if (updates.containsKey("reminderMinutesBefore")) {
            schedule.setReminderMinutesBefore(((Number) updates.get("reminderMinutesBefore")).intValue());
        }
        if (updates.containsKey("enableRecording")) {
            schedule.setEnableRecording((Boolean) updates.get("enableRecording"));
        }
        if (updates.containsKey("metadataJson")) {
            schedule.setMetadataJson((String) updates.get("metadataJson"));
        }

        schedule.touch();
        MeetingScheduleEntity saved = scheduleRepository.save(schedule);

        // 更新日历事件（闭环 67-D-2）
        calendarSyncService.updateCalendar(saved);

        log.info("[P84] 会议预约更新 - scheduleId={}", scheduleId);
        return saved;
    }

    /**
     * 取消预约（闭环 67-D-4）
     *
     * <p>流程：更新状态为 CANCELLED → 发送取消通知</p>
     *
     * @param scheduleId 预约ID
     * @param reason     取消原因（可选）
     */
    @Transactional
    public void cancelSchedule(String scheduleId, String reason) {
        MeetingScheduleEntity schedule = findScheduleOrThrow(scheduleId);

        if ("CANCELLED".equals(schedule.getStatus())) {
            log.warn("[P84] 预约已取消，跳过 - scheduleId={}", scheduleId);
            return;
        }

        schedule.setStatus("CANCELLED");
        schedule.touch();
        scheduleRepository.save(schedule);

        // 删除日历事件（闭环 67-D-4）
        calendarSyncService.deleteFromCalendar(scheduleId);

        // 发送取消通知（复用闭环 44）
        sendCancelNotification(schedule, reason);

        log.info("[P84] 会议预约已取消 - scheduleId={}, reason={}", scheduleId, reason);
    }

    /**
     * 更新预约状态
     *
     * @param scheduleId 预约ID
     * @param status     新状态（SCHEDULED/ACTIVE/COMPLETED/CANCELLED）
     */
    @Transactional
    public void updateStatus(String scheduleId, String status) {
        MeetingScheduleEntity schedule = findScheduleOrThrow(scheduleId);
        schedule.setStatus(status);
        schedule.touch();

        // 根据状态设置实际开始/结束时间
        if ("ACTIVE".equals(status) && schedule.getActualStart() == null) {
            schedule.setActualStart(Instant.now());
        }
        if ("COMPLETED".equals(status) && schedule.getActualEnd() == null) {
            schedule.setActualEnd(Instant.now());
        }

        scheduleRepository.save(schedule);
        log.info("[P84] 预约状态更新 - scheduleId={}, status={}", scheduleId, status);
    }

    /**
     * 按预约ID查询详情
     */
    public Optional<MeetingScheduleEntity> findByScheduleId(String scheduleId) {
        return scheduleRepository.findByScheduleId(scheduleId);
    }

    /**
     * 按部门和状态查询预约列表
     */
    public List<MeetingScheduleEntity> findByDepartmentAndStatus(String department, String status) {
        if (department != null && status != null) {
            return scheduleRepository.findByDepartmentAndStatusOrderByScheduledStartAsc(department, status);
        }
        if (department != null) {
            return scheduleRepository.findByDepartmentOrderByScheduledStartDesc(department);
        }
        if (status != null) {
            return scheduleRepository.findByStatusOrderByScheduledStartAsc(status);
        }
        return scheduleRepository.findAll();
    }

    /**
     * 按创建人查询预约
     */
    public List<MeetingScheduleEntity> findByCreatorId(String creatorId) {
        return scheduleRepository.findByCreatorIdOrderByScheduledStartDesc(creatorId);
    }

    /**
     * 按时间段查询预约（日历视图）
     */
    public List<MeetingScheduleEntity> findByTimeRange(String department, Instant rangeStart, Instant rangeEnd) {
        if (department != null) {
            return scheduleRepository.findByDepartmentAndTimeRange(department, rangeStart, rangeEnd);
        }
        return scheduleRepository.findByTimeRange(rangeStart, rangeEnd);
    }

    /**
     * 检查时间冲突
     */
    public boolean hasConflict(String department, Instant start, Instant end) {
        return scheduleRepository.countOverlappingSchedules(department, start, end) > 0;
    }

    // ========== 定时任务 ==========

    /**
     * 定时检查并发送会议提醒通知（闭环 44 延迟通知）
     * 每 60 秒执行一次，查找距开始时间不足 15 分钟且未发送提醒的预约
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkAndSendReminders() {
        try {
            Instant now = Instant.now();
            // 查找距开始时间在 15 分钟内的预约
            Instant reminderThreshold = now.plus(DEFAULT_REMINDER_MINUTES, ChronoUnit.MINUTES);

            List<MeetingScheduleEntity> schedulesNeedingReminder =
                    scheduleRepository.findSchedulesNeedingReminder("SCHEDULED", now, reminderThreshold);

            for (MeetingScheduleEntity schedule : schedulesNeedingReminder) {
                sendReminderNotification(schedule);
                schedule.setReminderSent(true);
                schedule.touch();
                scheduleRepository.save(schedule);
                log.info("[P84] 会议提醒已发送 - scheduleId={}, title={}, start={}",
                        schedule.getScheduleId(), schedule.getTitle(), schedule.getScheduledStart());
            }

            if (!schedulesNeedingReminder.isEmpty()) {
                log.debug("[P84] 本次发送 {} 条会议提醒", schedulesNeedingReminder.size());
            }
        } catch (Exception e) {
            log.error("[P84] 检查会议提醒任务异常", e);
        }
    }

    /**
     * 定时自动启动已到时间的预约会议（闭环 67-D-3 预约执行）
     * 每 60 秒执行一次，查找开始时间已过的 SCHEDULED 预约，自动创建 LiveKit 房间
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void startScheduledMeetings() {
        try {
            Instant now = Instant.now();
            List<MeetingScheduleEntity> schedulesToStart =
                    scheduleRepository.findSchedulesToStart("SCHEDULED", now);

            for (MeetingScheduleEntity schedule : schedulesToStart) {
                try {
                    // 创建 LiveKit 房间
                    liveKitRoomService.createRoom(schedule.getRoomName(), schedule.getMaxParticipants());

                    // 更新状态为 ACTIVE
                    schedule.setStatus("ACTIVE");
                    schedule.setActualStart(Instant.now());
                    schedule.touch();
                    scheduleRepository.save(schedule);

                    // 发送会议开始通知
                    sendStartNotification(schedule);

                    log.info("[P84] 会议自动启动 - scheduleId={}, roomName={}",
                            schedule.getScheduleId(), schedule.getRoomName());
                } catch (Exception e) {
                    log.error("[P84] 自动启动会议失败 - scheduleId={}, roomName={}",
                            schedule.getScheduleId(), schedule.getRoomName(), e);
                }
            }
        } catch (Exception e) {
            log.error("[P84] 自动启动会议任务异常", e);
        }
    }

    /**
     * 定时自动完成已到结束时间的预约会议
     * 每 60 秒执行一次，查找结束时间已过的 ACTIVE 预约，更新状态为 COMPLETED
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void completeExpiredMeetings() {
        try {
            Instant now = Instant.now();
            List<MeetingScheduleEntity> schedulesToEnd =
                    scheduleRepository.findSchedulesToEnd("ACTIVE", now);

            for (MeetingScheduleEntity schedule : schedulesToEnd) {
                schedule.setStatus("COMPLETED");
                schedule.setActualEnd(Instant.now());
                schedule.touch();
                scheduleRepository.save(schedule);

                log.info("[P84] 会议自动完成 - scheduleId={}, roomName={}",
                        schedule.getScheduleId(), schedule.getRoomName());
            }
        } catch (Exception e) {
            log.error("[P84] 自动完成会议任务异常", e);
        }
    }

    // ========== 通知方法（复用闭环 44 DepartmentNotificationService） ==========

    /**
     * 发送会议邀请通知（复用闭环 44）
     */
    private void sendInviteNotification(MeetingScheduleEntity schedule) {
        try {
            String content = String.format(
                    "会议邀请\n主题：%s\n时间：%s - %s\n时长：%d 分钟\n创建人：%s",
                    schedule.getTitle(),
                    formatTime(schedule.getScheduledStart()),
                    formatTime(schedule.getScheduledEnd()),
                    schedule.getDurationMinutes(),
                    schedule.getCreatorId()
            );

            notificationService.sendMeetingNotification(
                    schedule.getDepartment(),
                    "会议邀请：" + schedule.getTitle(),
                    content,
                    schedule.getScheduledStart(),
                    "/meeting/schedule/" + schedule.getScheduleId()
            );
        } catch (Exception e) {
            log.warn("[P84] 发送会议邀请通知失败 - scheduleId={}", schedule.getScheduleId(), e);
        }
    }

    /**
     * 发送会议提醒通知（复用闭环 44）
     */
    private void sendReminderNotification(MeetingScheduleEntity schedule) {
        try {
            String content = String.format(
                    "会议即将开始\n主题：%s\n开始时间：%s\n房间：%s\n\n请准时参会",
                    schedule.getTitle(),
                    formatTime(schedule.getScheduledStart()),
                    schedule.getRoomName()
            );

            notificationService.sendNotification(
                    schedule.getDepartment(),
                    "MEETING_REMINDER",
                    "会议即将开始：" + schedule.getTitle(),
                    content,
                    "HIGH",
                    Map.of(
                            "scheduleId", schedule.getScheduleId(),
                            "roomName", schedule.getRoomName() != null ? schedule.getRoomName() : "",
                            "meetingTime", schedule.getScheduledStart().toString()
                    )
            );
        } catch (Exception e) {
            log.warn("[P84] 发送会议提醒通知失败 - scheduleId={}", schedule.getScheduleId(), e);
        }
    }

    /**
     * 发送会议开始通知（复用闭环 44）
     */
    private void sendStartNotification(MeetingScheduleEntity schedule) {
        try {
            String content = String.format(
                    "会议已开始\n主题：%s\n房间：%s\n\n点击进入会议室",
                    schedule.getTitle(),
                    schedule.getRoomName()
            );

            notificationService.sendNotification(
                    schedule.getDepartment(),
                    "MEETING_STARTED",
                    "会议已开始：" + schedule.getTitle(),
                    content,
                    "URGENT",
                    Map.of(
                            "scheduleId", schedule.getScheduleId(),
                            "roomName", schedule.getRoomName() != null ? schedule.getRoomName() : "",
                            "actionUrl", "/meeting/" + schedule.getRoomName() + "/join"
                    )
            );
        } catch (Exception e) {
            log.warn("[P84] 发送会议开始通知失败 - scheduleId={}", schedule.getScheduleId(), e);
        }
    }

    /**
     * 发送会议取消通知（复用闭环 44）
     */
    private void sendCancelNotification(MeetingScheduleEntity schedule, String reason) {
        try {
            String content = String.format(
                    "会议已取消\n主题：%s\n原定时间：%s - %s\n取消原因：%s",
                    schedule.getTitle(),
                    formatTime(schedule.getScheduledStart()),
                    formatTime(schedule.getScheduledEnd()),
                    reason != null ? reason : "未说明"
            );

            notificationService.sendNotification(
                    schedule.getDepartment(),
                    "MEETING_CANCELLED",
                    "会议已取消：" + schedule.getTitle(),
                    content,
                    "HIGH",
                    Map.of("scheduleId", schedule.getScheduleId())
            );
        } catch (Exception e) {
            log.warn("[P84] 发送会议取消通知失败 - scheduleId={}", schedule.getScheduleId(), e);
        }
    }

    // ========== 内部工具方法 ==========

    /**
     * 按 scheduleId 查询，不存在则抛异常
     */
    private MeetingScheduleEntity findScheduleOrThrow(String scheduleId) {
        return scheduleRepository.findByScheduleId(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在: " + scheduleId));
    }

    /**
     * 格式化时间用于通知内容
     */
    private String formatTime(Instant instant) {
        if (instant == null) return "未设置";
        return instant.toString();
    }

    /**
     * 解析 Instant（兼容字符串和 Instant 类型）
     */
    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof String) return Instant.parse((String) value);
        throw new IllegalArgumentException("无法解析时间: " + value);
    }
}
