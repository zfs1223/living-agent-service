package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.SessionContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface SessionContextRepository extends JpaRepository<SessionContextEntity, String> {

    List<SessionContextEntity> findByUserIdOrderByLastActivityDesc(String userId);

    @Query("SELECT s FROM SessionContextEntity s WHERE s.userId = :userId ORDER BY s.lastActivity DESC LIMIT :limit")
    List<SessionContextEntity> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    long countByTenantId(String tenantId);

    @Modifying
    @Query("DELETE FROM SessionContextEntity s WHERE s.lastActivity < :threshold")
    void deleteByLastActivityBefore(@Param("threshold") Instant threshold);
}
