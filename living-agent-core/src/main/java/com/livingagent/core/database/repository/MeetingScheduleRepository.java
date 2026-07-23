package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MeetingScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会议预约 Repository - 闭环 67-D 预约管理 / P84
 *
 * <p>提供会议预约数据的持久化操作，包含按部门/状态/时间的查询方法，
 * 以及用于定时提醒和自动启动会议的专用查询。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
@Repository
public interface MeetingScheduleRepository extends JpaRepository<MeetingScheduleEntity, Long> {

    /**
     * 按预约ID查询（scheduleId 是业务主键）
     */
    Optional<MeetingScheduleEntity> findByScheduleId(String scheduleId);

    /**
     * 按部门和状态查询预约列表
     * 用于部门内按状态筛选会议（如查看本部门所有已预约的会议）
     */
    List<MeetingScheduleEntity> findByDepartmentAndStatusOrderByScheduledStartAsc(
            String department, String status);

    /**
     * 按状态查询所有预约列表
     */
    List<MeetingScheduleEntity> findByStatusOrderByScheduledStartAsc(String status);

    /**
     * 按部门查询所有预约（不限状态）
     */
    List<MeetingScheduleEntity> findByDepartmentOrderByScheduledStartDesc(String department);

    /**
     * 按创建人查询其创建的所有预约
     */
    List<MeetingScheduleEntity> findByCreatorIdOrderByScheduledStartDesc(String creatorId);

    /**
     * 查询需要发送提醒的预约
     * 条件：状态为 SCHEDULED，未发送提醒，且距开始时间不足 reminderMinutesBefore 分钟
     *
     * @param status    预约状态（SCHEDULED）
     * @param now       当前时间
     * @return 需要提醒的预约列表
     */
    @Query("SELECT m FROM MeetingScheduleEntity m " +
           "WHERE m.status = :status " +
           "AND m.reminderSent = false " +
           "AND m.scheduledStart <= :nowPlusReminder " +
           "AND m.scheduledStart > :now " +
           "ORDER BY m.scheduledStart ASC")
    List<MeetingScheduleEntity> findSchedulesNeedingReminder(
            @Param("status") String status,
            @Param("now") Instant now,
            @Param("nowPlusReminder") Instant nowPlusReminder);

    /**
     * 查询已到开始时间、需要自动启动的预约
     * 条件：状态为 SCHEDULED，且开始时间已过
     *
     * @param status 预约状态（SCHEDULED）
     * @param now    当前时间
     * @return 应启动的预约列表
     */
    @Query("SELECT m FROM MeetingScheduleEntity m " +
           "WHERE m.status = :status " +
           "AND m.scheduledStart <= :now " +
           "AND m.scheduledEnd > :now " +
           "ORDER BY m.scheduledStart ASC")
    List<MeetingScheduleEntity> findSchedulesToStart(
            @Param("status") String status,
            @Param("now") Instant now);

    /**
     * 查询已到结束时间、需要自动完成的预约
     * 条件：状态为 ACTIVE，且结束时间已过
     *
     * @param status 预约状态（ACTIVE）
     * @param now    当前时间
     * @return 应结束的预约列表
     */
    @Query("SELECT m FROM MeetingScheduleEntity m " +
           "WHERE m.status = :status " +
           "AND m.scheduledEnd <= :now " +
           "ORDER BY m.scheduledEnd ASC")
    List<MeetingScheduleEntity> findSchedulesToEnd(
            @Param("status") String status,
            @Param("now") Instant now);

    /**
     * 按部门和时间段查询预约（日历视图使用）
     */
    @Query("SELECT m FROM MeetingScheduleEntity m " +
           "WHERE m.department = :department " +
           "AND m.scheduledStart < :rangeEnd " +
           "AND m.scheduledEnd > :rangeStart " +
           "AND m.status <> 'CANCELLED' " +
           "ORDER BY m.scheduledStart ASC")
    List<MeetingScheduleEntity> findByDepartmentAndTimeRange(
            @Param("department") String department,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    /**
     * 按时间段查询所有预约（董事长查看全部，日历视图使用）
     */
    @Query("SELECT m FROM MeetingScheduleEntity m " +
           "WHERE m.scheduledStart < :rangeEnd " +
           "AND m.scheduledEnd > :rangeStart " +
           "AND m.status <> 'CANCELLED' " +
           "ORDER BY m.scheduledStart ASC")
    List<MeetingScheduleEntity> findByTimeRange(
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    /**
     * 检查指定部门在时间段内是否存在冲突的预约（时间重叠检测）
     */
    @Query("SELECT COUNT(m) FROM MeetingScheduleEntity m " +
           "WHERE m.department = :department " +
           "AND m.status <> 'CANCELLED' " +
           "AND m.scheduledStart < :rangeEnd " +
           "AND m.scheduledEnd > :rangeStart")
    long countOverlappingSchedules(
            @Param("department") String department,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);
}
