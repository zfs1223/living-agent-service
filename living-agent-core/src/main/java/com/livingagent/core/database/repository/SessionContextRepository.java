package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.SessionContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionContextRepository extends JpaRepository<SessionContextEntity, String> {

    List<SessionContextEntity> findByUserIdOrderByLastActivityDesc(String userId);

    @Query("SELECT s FROM SessionContextEntity s WHERE s.userId = :userId ORDER BY s.lastActivity DESC LIMIT :limit")
    List<SessionContextEntity> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    long countByTenantId(String tenantId);

    @Modifying
    @Query("DELETE FROM SessionContextEntity s WHERE s.lastActivity < :threshold")
    void deleteByLastActivityBefore(@Param("threshold") Instant threshold);

    // B-0-2: 反向索引查询（用于服务重启后从 DB 恢复反向索引）

    /** 根据 taskKey 反查最新的会话上下文 */
    Optional<SessionContextEntity> findFirstByTaskKeyOrderByLastActivityDesc(String taskKey);

    /** 根据 executionId 反查最新的会话上下文 */
    Optional<SessionContextEntity> findFirstByExecutionIdOrderByLastActivityDesc(String executionId);

    /** 根据 projectKey 反查最新的会话上下文 */
    Optional<SessionContextEntity> findFirstByProjectKeyOrderByLastActivityDesc(String projectKey);

    /** 根据 conversationId 反查最新的会话上下文 */
    Optional<SessionContextEntity> findFirstByConversationIdOrderByLastActivityDesc(String conversationId);

    /** 根据 tenantId 反查活跃会话 */
    List<SessionContextEntity> findByTenantIdOrderByLastActivityDesc(String tenantId);

    /** 根据 userId 反查活跃会话 */
    List<SessionContextEntity> findByUserId(String userId);

    /** B-0-2: 查询 lastActivity 在指定时间之后的所有活跃会话（启动时加载） */
    List<SessionContextEntity> findByLastActivityAfterOrderByLastActivityDesc(Instant threshold);
}
