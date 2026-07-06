package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.RuntimeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RuntimeEventRepository extends JpaRepository<RuntimeEventEntity, UUID> {

    List<RuntimeEventEntity> findByScopeAndScopeKeyOrderByTimestampDesc(String scope, String scopeKey);

    List<RuntimeEventEntity> findByScopeAndScopeKeyAndEventTypeOrderByTimestampDesc(
        String scope, String scopeKey, String eventType);

    List<RuntimeEventEntity> findByTenantIdOrderByTimestampDesc(String tenantId);

    @Query("SELECT e FROM RuntimeEventEntity e WHERE e.scope = :scope AND e.scopeKey = :scopeKey AND e.timestamp >= :since ORDER BY e.timestamp DESC")
    List<RuntimeEventEntity> findRecentByScopeAndKey(@Param("scope") String scope,
                                                      @Param("scopeKey") String scopeKey,
                                                      @Param("since") Instant since);

    long countByScopeAndScopeKey(String scope, String scopeKey);
}
