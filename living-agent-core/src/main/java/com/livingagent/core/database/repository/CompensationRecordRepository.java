package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.CompensationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompensationRecordRepository extends JpaRepository<CompensationRecordEntity, UUID> {

    List<CompensationRecordEntity> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);

    List<CompensationRecordEntity> findByTypeOrderByCreatedAtDesc(String type);

    List<CompensationRecordEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);

    Optional<CompensationRecordEntity> findByRecordId(String recordId);
}
