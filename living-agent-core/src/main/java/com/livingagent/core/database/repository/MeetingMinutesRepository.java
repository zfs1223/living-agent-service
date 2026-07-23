package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MeetingMinutesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会议纪要 Repository - 闭环 68 录制与纪要自动化 / P82
 *
 * <p>提供会议纪要数据的持久化操作，包含按房间名、状态等维度的查询方法。</p>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */
@Repository
public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutesEntity, Long> {

    /**
     * 按纪要ID查询（minutesId 是业务主键）
     */
    Optional<MeetingMinutesEntity> findByMinutesId(String minutesId);

    /**
     * 按房间名称查询纪要（一个房间可能有多份纪要）
     */
    List<MeetingMinutesEntity> findByRoomNameOrderByCreatedAtDesc(String roomName);

    /**
     * 按房间名称查询最新一份纪要
     */
    Optional<MeetingMinutesEntity> findTopByRoomNameOrderByCreatedAtDesc(String roomName);

    /**
     * 按状态查询纪要列表
     */
    List<MeetingMinutesEntity> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 按关联的预约ID查询纪要
     */
    Optional<MeetingMinutesEntity> findByScheduleId(String scheduleId);

    /**
     * 查询所有纪要（按创建时间倒序）
     */
    List<MeetingMinutesEntity> findAllByOrderByCreatedAtDesc();
}
