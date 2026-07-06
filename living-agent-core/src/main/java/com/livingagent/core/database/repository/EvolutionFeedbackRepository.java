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

    /**
     * P18-B: 写入验证 — 保存后立即查询确认
     */
    default EvolutionFeedbackEntity saveAndVerify(EvolutionFeedbackEntity entity) {
        EvolutionFeedbackEntity saved = save(entity);
        if (saved.getId() == null) {
            throw new IllegalStateException("EvolutionFeedback save verification failed: id is null");
        }
        if (!existsById(saved.getId())) {
            throw new IllegalStateException("EvolutionFeedback save verification failed: " + saved.getId());
        }
        return saved;
    }
}
