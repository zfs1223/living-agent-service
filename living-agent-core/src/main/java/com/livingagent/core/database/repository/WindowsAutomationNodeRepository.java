package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.WindowsAutomationNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WindowsAutomationNodeRepository extends JpaRepository<WindowsAutomationNodeEntity, String> {

    List<WindowsAutomationNodeEntity> findByEnabledTrue();

    List<WindowsAutomationNodeEntity> findByTenantId(String tenantId);

    List<WindowsAutomationNodeEntity> findByUserId(String userId);

    List<WindowsAutomationNodeEntity> findByStatus(String status);

    Optional<WindowsAutomationNodeEntity> findByNodeId(String nodeId);

    @Query("SELECT n FROM WindowsAutomationNodeEntity n WHERE n.tenantId = :tenantId AND n.enabled = true")
    List<WindowsAutomationNodeEntity> findEnabledByTenantId(@Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE WindowsAutomationNodeEntity n SET n.status = 'offline' WHERE n.lastHeartbeat < :threshold AND n.status = 'online'")
    void markOfflineByLastHeartbeatBefore(@Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE WindowsAutomationNodeEntity n SET n.status = :status, n.lastHeartbeat = :heartbeat WHERE n.nodeId = :nodeId")
    void updateStatusAndHeartbeat(@Param("nodeId") String nodeId, @Param("status") String status, @Param("heartbeat") Instant heartbeat);

    long countByTenantIdAndStatus(String tenantId, String status);
}
