package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EvolutionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvolutionResultRepository extends JpaRepository<EvolutionResultEntity, java.util.UUID> {

    Optional<EvolutionResultEntity> findByResultId(String resultId);

    List<EvolutionResultEntity> findByStatusOrderByTimestampDesc(String status);

    List<EvolutionResultEntity> findTop50ByOrderByTimestampDesc();
    
    List<EvolutionResultEntity> findTop500ByOrderByTimestampDesc();

    List<EvolutionResultEntity> findByTimestampBeforeOrderByTimestampDesc(Long timestamp);

    List<EvolutionResultEntity> findByTimestampBetweenOrderByTimestampDesc(Long startInclusive, Long endInclusive);

    List<EvolutionResultEntity> findBySignalId(String signalId);

    List<EvolutionResultEntity> findByGeneratedSkillId(String generatedSkillId);

    List<EvolutionResultEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);
}
