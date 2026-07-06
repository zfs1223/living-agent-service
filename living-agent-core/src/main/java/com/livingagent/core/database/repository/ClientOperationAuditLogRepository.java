package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ClientOperationAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClientOperationAuditLogRepository extends JpaRepository<ClientOperationAuditLogEntity, Long> {

    /**
     * 根据 clientId 查找操作日志
     */
    List<ClientOperationAuditLogEntity> findByClientId(String clientId);

    /**
     * 根据 userId 查找操作日志
     */
    List<ClientOperationAuditLogEntity> findByUserId(String userId);

    /**
     * 根据 clientId 和时间范围查找操作日志
     */
    @Query("SELECT a FROM ClientOperationAuditLogEntity a WHERE a.clientId = :clientId AND a.executedAt >= :startTime AND a.executedAt <= :endTime ORDER BY a.executedAt DESC")
    List<ClientOperationAuditLogEntity> findByClientIdAndTimeRange(
            @Param("clientId") String clientId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 根据操作类型查找操作日志
     */
    List<ClientOperationAuditLogEntity> findByAction(String action);

    /**
     * 根据结果查找操作日志
     */
    List<ClientOperationAuditLogEntity> findByResult(String result);

    /**
     * 根据目标 clientId 查找操作日志（跨机操作）
     */
    List<ClientOperationAuditLogEntity> findByTargetClientId(String targetClientId);
}
