package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingMinutesEntity;
import com.livingagent.core.database.entity.MeetingScheduleEntity;
import com.livingagent.core.database.repository.MeetingMinutesRepository;
import com.livingagent.core.database.repository.MeetingScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会议数据看板服务 - 闭环 32 生命体征仪表盘扩展 / P85 会议深度集成
 *
 * <p>聚合会议统计数据，供闭环 32（生命体征仪表盘）使用，
 * 支持全局统计和部门维度统计。</p>
 *
 * <h3>统计指标</h3>
 * <ul>
 *   <li>今日会议数 / 本周会议数</li>
 *   <li>平均参会人数</li>
 *   <li>平均会议时长（分钟）</li>
 *   <li>纪要生成率（有纪要的会议 / 总会议数）</li>
 *   <li>录制覆盖率（有录制的会议 / 总会议数）</li>
 * </ul>
 *
 * <h3>数据来源</h3>
 * <ul>
 *   <li>MeetingScheduleRepository: 会议预约数据（状态、时间、部门、参会人等）</li>
 *   <li>MeetingMinutesRepository: 会议纪要数据（纪要状态、录制文件等）</li>
 * </ul>
 *
 * @author P85 会议深度集成
 * @since 1.0.0
 */
@Service
public class MeetingDashboardService {

    private static final Logger log = LoggerFactory.getLogger(MeetingDashboardService.class);

    /** 所有已知部门代码 */
    private static final Set<String> ALL_DEPARTMENTS = Set.of(
            "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops"
    );

    private final MeetingScheduleRepository scheduleRepository;
    private final MeetingMinutesRepository minutesRepository;

    public MeetingDashboardService(
            MeetingScheduleRepository scheduleRepository,
            MeetingMinutesRepository minutesRepository) {
        this.scheduleRepository = scheduleRepository;
        this.minutesRepository = minutesRepository;
        log.info("[P85] MeetingDashboardService 初始化");
    }

    // ========== 公开接口 ==========

    /**
     * 获取部门会议统计
     *
     * <p>按部门维度聚合会议统计数据，包括今日/本周会议数、
     * 平均参会人数、平均时长、纪要生成率、录制覆盖率等。</p>
     *
     * @param department 部门代码
     * @return 部门会议统计数据
     */
    public MeetingStats getDepartmentMeetingStats(String department) {
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant weekStart = now.minus(7, ChronoUnit.DAYS);

        // 查询部门所有预约（不限状态）
        List<MeetingScheduleEntity> allSchedules = scheduleRepository
                .findByDepartmentOrderByScheduledStartDesc(department);

        // 按时间范围筛选
        List<MeetingScheduleEntity> todaySchedules = allSchedules.stream()
                .filter(s -> s.getScheduledStart() != null && !s.getScheduledStart().isBefore(todayStart))
                .collect(Collectors.toList());

        List<MeetingScheduleEntity> weekSchedules = allSchedules.stream()
                .filter(s -> s.getScheduledStart() != null && !s.getScheduledStart().isBefore(weekStart))
                .collect(Collectors.toList());

        // 已完成的会议（用于计算平均时长和纪要率）
        List<MeetingScheduleEntity> completedSchedules = allSchedules.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()) || "ACTIVE".equals(s.getStatus()))
                .collect(Collectors.toList());

        // 计算统计指标
        int todayCount = todaySchedules.size();
        int weekCount = weekSchedules.size();
        double avgParticipants = calculateAvgParticipants(weekSchedules);
        double avgDurationMinutes = calculateAvgDurationMinutes(completedSchedules);
        double minutesRate = calculateMinutesRate(department, completedSchedules);
        double recordingRate = calculateRecordingRate(weekSchedules);

        // 进行中的会议
        long activeCount = allSchedules.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .count();

        // 已预约的会议
        long scheduledCount = allSchedules.stream()
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .count();

        log.debug("[P85] 部门会议统计 - dept={}, today={}, week={}, active={}",
                department, todayCount, weekCount, activeCount);

        return new MeetingStats(
                department,
                todayCount,
                weekCount,
                activeCount,
                scheduledCount,
                avgParticipants,
                avgDurationMinutes,
                minutesRate,
                recordingRate,
                now
        );
    }

    /**
     * 获取全局会议统计
     *
     * <p>聚合所有部门的会议统计数据，提供全局视角。</p>
     *
     * @return 全局会议统计数据
     */
    public MeetingStats getOverallMeetingStats() {
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant weekStart = now.minus(7, ChronoUnit.DAYS);

        // 查询所有预约
        List<MeetingScheduleEntity> allSchedules = scheduleRepository.findAll();

        // 按时间范围筛选
        List<MeetingScheduleEntity> todaySchedules = allSchedules.stream()
                .filter(s -> s.getScheduledStart() != null && !s.getScheduledStart().isBefore(todayStart))
                .collect(Collectors.toList());

        List<MeetingScheduleEntity> weekSchedules = allSchedules.stream()
                .filter(s -> s.getScheduledStart() != null && !s.getScheduledStart().isBefore(weekStart))
                .collect(Collectors.toList());

        // 已完成的会议
        List<MeetingScheduleEntity> completedSchedules = allSchedules.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()) || "ACTIVE".equals(s.getStatus()))
                .collect(Collectors.toList());

        // 计算统计指标
        int todayCount = todaySchedules.size();
        int weekCount = weekSchedules.size();
        double avgParticipants = calculateAvgParticipants(weekSchedules);
        double avgDurationMinutes = calculateAvgDurationMinutes(completedSchedules);
        double minutesRate = calculateOverallMinutesRate(completedSchedules);
        double recordingRate = calculateRecordingRate(weekSchedules);

        // 进行中和已预约
        long activeCount = allSchedules.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .count();
        long scheduledCount = allSchedules.stream()
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .count();

        // 按部门拆分统计
        Map<String, Integer> byDepartment = allSchedules.stream()
                .filter(s -> s.getScheduledStart() != null && !s.getScheduledStart().isBefore(weekStart))
                .collect(Collectors.groupingBy(
                        s -> s.getDepartment() != null ? s.getDepartment() : "unknown",
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        log.debug("[P85] 全局会议统计 - today={}, week={}, active={}", todayCount, weekCount, activeCount);

        return new MeetingStats(
                "ALL",
                todayCount,
                weekCount,
                activeCount,
                scheduledCount,
                avgParticipants,
                avgDurationMinutes,
                minutesRate,
                recordingRate,
                now,
                byDepartment
        );
    }

    /**
     * 获取最近会议列表
     *
     * <p>按部门查询最近的会议预约记录，按开始时间倒序排列。</p>
     *
     * @param department 部门代码，null 表示查询全部
     * @param limit      返回数量限制
     * @return 最近的会议列表
     */
    public List<MeetingSummary> getRecentMeetings(String department, int limit) {
        if (limit <= 0) {
            limit = 10;
        }

        List<MeetingScheduleEntity> schedules;
        if (department != null && !department.isBlank()) {
            schedules = scheduleRepository.findByDepartmentOrderByScheduledStartDesc(department);
        } else {
            schedules = scheduleRepository.findAll();
        }

        return schedules.stream()
                .sorted((a, b) -> {
                    // 按开始时间倒序（最新的在前）
                    if (a.getScheduledStart() == null && b.getScheduledStart() == null) return 0;
                    if (a.getScheduledStart() == null) return 1;
                    if (b.getScheduledStart() == null) return -1;
                    return b.getScheduledStart().compareTo(a.getScheduledStart());
                })
                .limit(limit)
                .map(this::toMeetingSummary)
                .collect(Collectors.toList());
    }

    // ========== 内部计算方法 ==========

    /**
     * 计算平均参会人数
     *
     * <p>基于预约的 maxParticipants 字段估算。</p>
     */
    private double calculateAvgParticipants(List<MeetingScheduleEntity> schedules) {
        if (schedules == null || schedules.isEmpty()) return 0.0;
        double sum = schedules.stream()
                .mapToInt(MeetingScheduleEntity::getMaxParticipants)
                .sum();
        return Math.round(sum / schedules.size() * 10.0) / 10.0;
    }

    /**
     * 计算平均会议时长（分钟）
     *
     * <p>基于 actualStart/actualEnd 计算，若无实际时间则用 scheduledStart/scheduledEnd。</p>
     */
    private double calculateAvgDurationMinutes(List<MeetingScheduleEntity> schedules) {
        if (schedules == null || schedules.isEmpty()) return 0.0;

        List<Long> durations = new ArrayList<>();
        for (MeetingScheduleEntity s : schedules) {
            Instant start = s.getActualStart() != null ? s.getActualStart() : s.getScheduledStart();
            Instant end = s.getActualEnd() != null ? s.getActualEnd() : s.getScheduledEnd();
            if (start != null && end != null && end.isAfter(start)) {
                durations.add(ChronoUnit.MINUTES.between(start, end));
            }
        }

        if (durations.isEmpty()) return 0.0;
        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return Math.round(avg * 10.0) / 10.0;
    }

    /**
     * 计算部门纪要生成率
     *
     * <p>有纪要的已完成会议 / 总已完成会议数。</p>
     */
    private double calculateMinutesRate(String department, List<MeetingScheduleEntity> completedSchedules) {
        if (completedSchedules == null || completedSchedules.isEmpty()) return 0.0;

        long withMinutes = 0;
        for (MeetingScheduleEntity s : completedSchedules) {
            if (s.getRoomName() != null) {
                List<MeetingMinutesEntity> minutes = minutesRepository
                        .findByRoomNameOrderByCreatedAtDesc(s.getRoomName());
                if (!minutes.isEmpty()) {
                    withMinutes++;
                }
            }
        }

        return Math.round((double) withMinutes / completedSchedules.size() * 1000.0) / 10.0;
    }

    /**
     * 计算全局纪要生成率
     *
     * <p>基于 MeetingMinutesRepository 中 COMPLETED 状态的纪要数
     * 与已完成会议数的比率。</p>
     */
    private double calculateOverallMinutesRate(List<MeetingScheduleEntity> completedSchedules) {
        if (completedSchedules == null || completedSchedules.isEmpty()) return 0.0;

        long completedMinutes = minutesRepository.findByStatusOrderByCreatedAtDesc("COMPLETED").size();
        double rate = (double) completedMinutes / completedSchedules.size() * 100.0;
        return Math.round(rate * 10.0) / 10.0;
    }

    /**
     * 计算录制覆盖率
     *
     * <p>启用录制的会议 / 总会议数。</p>
     */
    private double calculateRecordingRate(List<MeetingScheduleEntity> schedules) {
        if (schedules == null || schedules.isEmpty()) return 0.0;

        long withRecording = schedules.stream()
                .filter(MeetingScheduleEntity::isEnableRecording)
                .count();

        return Math.round((double) withRecording / schedules.size() * 1000.0) / 10.0;
    }

    /**
     * 将预约实体转换为会议摘要
     */
    private MeetingSummary toMeetingSummary(MeetingScheduleEntity entity) {
        // 计算时长
        Long durationMinutes = null;
        Instant start = entity.getActualStart() != null ? entity.getActualStart() : entity.getScheduledStart();
        Instant end = entity.getActualEnd() != null ? entity.getActualEnd() : entity.getScheduledEnd();
        if (start != null && end != null && end.isAfter(start)) {
            durationMinutes = ChronoUnit.MINUTES.between(start, end);
        }

        // 查询纪要状态
        boolean hasMinutes = false;
        if (entity.getRoomName() != null) {
            hasMinutes = minutesRepository
                    .findTopByRoomNameOrderByCreatedAtDesc(entity.getRoomName())
                    .isPresent();
        }

        return new MeetingSummary(
                entity.getScheduleId(),
                entity.getTitle(),
                entity.getDepartment(),
                entity.getRoomName(),
                entity.getStatus(),
                entity.getScheduledStart(),
                entity.getScheduledEnd(),
                entity.getMaxParticipants(),
                durationMinutes,
                entity.isEnableRecording(),
                hasMinutes,
                entity.getCreatorId()
        );
    }

    // ========== 数据记录 ==========

    /**
     * 会议统计数据记录
     *
     * @param department         部门代码（"ALL" 表示全局）
     * @param todayMeetingCount  今日会议数
     * @param weekMeetingCount   本周会议数
     * @param activeMeetingCount 进行中会议数
     * @param scheduledCount     已预约待开始会议数
     * @param avgParticipants    平均参会人数
     * @param avgDurationMinutes 平均会议时长（分钟）
     * @param minutesGenerationRate 纪要生成率（百分比）
     * @param recordingRate      录制覆盖率（百分比）
     * @param calculatedAt       统计计算时间
     * @param byDepartment       按部门拆分统计（仅全局统计时有值）
     */
    public record MeetingStats(
            String department,
            int todayMeetingCount,
            int weekMeetingCount,
            long activeMeetingCount,
            long scheduledCount,
            double avgParticipants,
            double avgDurationMinutes,
            double minutesGenerationRate,
            double recordingRate,
            Instant calculatedAt,
            Map<String, Integer> byDepartment
    ) {
        /**
         * 部门统计的便捷构造（不含 byDepartment）
         */
        public MeetingStats(String department, int todayMeetingCount, int weekMeetingCount,
                            long activeMeetingCount, long scheduledCount,
                            double avgParticipants, double avgDurationMinutes,
                            double minutesGenerationRate, double recordingRate,
                            Instant calculatedAt) {
            this(department, todayMeetingCount, weekMeetingCount, activeMeetingCount, scheduledCount,
                    avgParticipants, avgDurationMinutes, minutesGenerationRate, recordingRate,
                    calculatedAt, null);
        }
    }

    /**
     * 会议摘要记录
     *
     * @param scheduleId      预约ID
     * @param title           会议标题
     * @param department      所属部门
     * @param roomName        LiveKit 房间名称
     * @param status          状态（SCHEDULED/ACTIVE/COMPLETED/CANCELLED）
     * @param scheduledStart  预约开始时间
     * @param scheduledEnd    预约结束时间
     * @param participantCount 参会人数
     * @param durationMinutes 会议时长（分钟，可能为 null）
     * @param enableRecording  是否启用录制
     * @param hasMinutes      是否有纪要
     * @param creatorId       创建人ID
     */
    public record MeetingSummary(
            String scheduleId,
            String title,
            String department,
            String roomName,
            String status,
            Instant scheduledStart,
            Instant scheduledEnd,
            int participantCount,
            Long durationMinutes,
            boolean enableRecording,
            boolean hasMinutes,
            String creatorId
    ) {}
}
