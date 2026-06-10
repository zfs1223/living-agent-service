package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EvolutionFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EvolutionFeedbackRepository extends JpaRepository<EvolutionFeedbackEntity, UUID> {

    List<EvolutionFeedbackEntity> findByResultIdOrderByCreatedAtDesc(String resultId);

    List<EvolutionFeedbackEntity> findByFeedbackTypeOrderByCreatedAtDesc(String feedbackType);

    List<EvolutionFeedbackEntity> findBySourceOrderByCreatedAtDesc(String source);

    List<EvolutionFeedbackEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);

    List<EvolutionFeedbackEntity> findTop100ByOrderByCreatedAtDesc();
}
