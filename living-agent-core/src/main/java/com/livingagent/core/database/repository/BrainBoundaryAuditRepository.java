package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.BrainBoundaryAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BrainBoundaryAuditRepository extends JpaRepository<BrainBoundaryAuditEntity, Long> {

    List<BrainBoundaryAuditEntity> findByBrainIdOrderByTimestampDesc(String brainId);

    List<BrainBoundaryAuditEntity> findByTimestampAfterOrderByTimestampDesc(Instant since);

    List<BrainBoundaryAuditEntity> findByResultNotOrderByTimestampDesc(String allowedResult);

    long countByBrainIdAndTimestampAfter(String brainId, Instant since);
}
