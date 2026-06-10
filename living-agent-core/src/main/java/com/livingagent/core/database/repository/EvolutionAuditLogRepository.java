package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EvolutionAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EvolutionAuditLogRepository extends JpaRepository<EvolutionAuditLogEntity, UUID> {

    List<EvolutionAuditLogEntity> findByResultIdOrderByCreatedAtDesc(String resultId);

    List<EvolutionAuditLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<EvolutionAuditLogEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);
}
