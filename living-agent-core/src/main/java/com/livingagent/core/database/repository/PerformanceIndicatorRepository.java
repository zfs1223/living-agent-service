package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.PerformanceIndicatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceIndicatorRepository extends JpaRepository<PerformanceIndicatorEntity, UUID> {

    Optional<PerformanceIndicatorEntity> findByIndicatorId(String indicatorId);

    List<PerformanceIndicatorEntity> findByCategoryOrderByUpdatedAtDesc(String category);

    List<PerformanceIndicatorEntity> findByEnabledTrueOrderByUpdatedAtDesc();
}
