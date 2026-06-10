package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.PerformanceTrendSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceTrendRepository extends JpaRepository<PerformanceTrendSnapshotEntity, UUID> {

    List<PerformanceTrendSnapshotEntity> findByEmployeeIdOrderByDateDesc(String employeeId);

    List<PerformanceTrendSnapshotEntity> findByEmployeeIdAndPeriodOrderByDateDesc(String employeeId, String period);

    List<PerformanceTrendSnapshotEntity> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);
}
