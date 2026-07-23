package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会议 Repository - 闭环 67-C 会议状态持久化 / P81
 *
 * <p>提供会议运行时状态数据的持久化操作。</p>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@Repository
public interface MeetingRepository extends JpaRepository<MeetingEntity, Long> {

    /**
     * 按房间名称查询（roomName 是业务主键）
     */
    Optional<MeetingEntity> findByRoomName(String roomName);

    /**
     * 按状态查询会议列表
     */
    List<MeetingEntity> findByStatusOrderByStartedAtDesc(String status);

    /**
     * 按部门查询会议列表
     */
    List<MeetingEntity> findByDepartmentOrderByStartedAtDesc(String department);

    /**
     * 按部门和状态查询
     */
    List<MeetingEntity> findByDepartmentAndStatusOrderByStartedAtDesc(String department, String status);

    /**
     * 查询正在进行的会议数量
     */
    long countByStatus(String status);

    /**
     * 查询指定部门的正在进行的会议数量
     */
    long countByDepartmentAndStatus(String department, String status);
}
