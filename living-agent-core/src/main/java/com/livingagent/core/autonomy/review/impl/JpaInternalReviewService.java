package com.livingagent.core.autonomy.review.impl;

import com.livingagent.core.autonomy.review.*;
import com.livingagent.core.database.entity.InternalReviewEntity;
import com.livingagent.core.database.repository.InternalReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JPA 持久化实现的部门内审查服务。
 * 审查数据存储在 PostgreSQL，重启不丢失。
 */
public class JpaInternalReviewService implements InternalReviewService {

    private static final Logger log = LoggerFactory.getLogger(JpaInternalReviewService.class);

    private final InternalReviewRepository reviewRepository;
    private final List<ReviewListener> listeners = new CopyOnWriteArrayList<>();

    public JpaInternalReviewService(InternalReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public void addReviewListener(ReviewListener listener) {
        listeners.add(listener);
    }

    @Override
    @Transactional
    public String submitForReview(String todoItemId, String authorCode, String reviewerCode,
                                   String executionId, int maxReviewRounds) {
        String reviewId = "review-" + System.currentTimeMillis() + "-" + todoItemId.hashCode();

        InternalReviewEntity entity = new InternalReviewEntity();
        entity.setReviewId(reviewId);
        entity.setTodoItemId(todoItemId);
        entity.setAuthorCode(authorCode);
        entity.setReviewerCode(reviewerCode);
        entity.setExecutionId(executionId);
        entity.setReviewRound(1);
        entity.setMaxRounds(maxReviewRounds);
        entity.setStatus(ReviewState.SUBMITTED_FOR_REVIEW.name());
        entity.setSubmittedAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        reviewRepository.save(entity);

        log.info("Review submitted: reviewId={}, todoItem={}, author={}, reviewer={}, round=1/{}",
            reviewId, todoItemId, authorCode, reviewerCode, maxReviewRounds);

        return reviewId;
    }

    @Override
    @Transactional
    public void review(String reviewId, ReviewResult result) {
        Optional<InternalReviewEntity> opt = reviewRepository.findByReviewId(reviewId);
        if (opt.isEmpty()) {
            log.warn("Review not found: reviewId={}", reviewId);
            return;
        }

        InternalReviewEntity entity = opt.get();
        String todoItemId = entity.getTodoItemId();
        int maxRounds = entity.getMaxRounds();

        if (result.reviewRound() >= maxRounds && result.decision() != ReviewDecision.APPROVED) {
            log.info("Max review rounds reached (round={}/max={}), escalating to brain for todoItem={}",
                result.reviewRound(), maxRounds, todoItemId);
            result = ReviewResult.escalated(
                result.reviewerCode(), "审查轮次已达上限(" + maxRounds + "轮)", result.reviewRound());
        }

        ReviewState newState = switch (result.decision()) {
            case APPROVED -> ReviewState.COMPLETED;
            case REVISION_NEEDED -> ReviewState.REVISION_NEEDED;
            case REJECTED -> ReviewState.REJECTED;
            case ESCALATE_TO_BRAIN -> ReviewState.ESCALATED;
        };

        entity.setStatus(newState.name());
        entity.setResult(result.decision().name());
        entity.setQualityScore(result.qualityScore());
        entity.setReviewedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (newState == ReviewState.REVISION_NEEDED) {
            int nextRound = entity.getReviewRound() + 1;
            entity.setReviewRound(nextRound);
            log.info("Review revision needed, next round={} for todoItem={}", nextRound, todoItemId);
        }

        reviewRepository.save(entity);

        log.info("Review completed: reviewId={}, decision={}, qualityScore={}, completionTag={}, round={}",
            reviewId, result.decision(), result.qualityScore(), result.completionTag(), result.reviewRound());

        String authorCode = entity.getAuthorCode();
        String executionId = entity.getExecutionId();
        for (ReviewListener listener : listeners) {
            try {
                listener.onReviewResult(todoItemId, reviewId, result, authorCode, executionId);
            } catch (Exception e) {
                log.warn("Review listener callback failed for todoItem={}: {}", todoItemId, e.getMessage());
            }
        }
    }

    @Override
    public Optional<ReviewHistory> getReview(String reviewId) {
        return reviewRepository.findByReviewId(reviewId).map(this::toHistory);
    }

    @Override
    public List<ReviewHistory> getReviewHistoryByTodoItem(String todoItemId) {
        return reviewRepository.findByTodoItemIdOrderByReviewRoundAsc(todoItemId).stream()
            .map(this::toHistory)
            .toList();
    }

    @Override
    public Optional<ReviewState> getReviewState(String todoItemId) {
        return reviewRepository.findTopByTodoItemIdOrderByReviewRoundDesc(todoItemId)
            .map(e -> ReviewState.valueOf(e.getStatus()));
    }

    @Override
    public boolean isCompleted(String todoItemId) {
        return reviewRepository.findTopByTodoItemIdOrderByReviewRoundDesc(todoItemId)
            .map(e -> ReviewState.COMPLETED.name().equals(e.getStatus()))
            .orElse(false);
    }

    @Override
    public int getCurrentRound(String todoItemId) {
        return reviewRepository.findTopByTodoItemIdOrderByReviewRoundDesc(todoItemId)
            .map(InternalReviewEntity::getReviewRound)
            .orElse(0);
    }

    private ReviewHistory toHistory(InternalReviewEntity entity) {
        ReviewResult result = null;
        if (entity.getResult() != null) {
            result = new ReviewResult(
                entity.getReviewerCode(),
                ReviewDecision.valueOf(entity.getResult()),
                entity.getQualityScore() != null ? entity.getQualityScore() : 0.0,
                List.of(),
                List.of(),
                ReviewDecision.APPROVED.name().equals(entity.getResult()),
                entity.getReviewRound()
            );
        }

        return new ReviewHistory(
            entity.getReviewId(),
            entity.getTodoItemId(),
            entity.getAuthorCode(),
            entity.getReviewerCode(),
            entity.getReviewRound(),
            ReviewState.valueOf(entity.getStatus()),
            result,
            entity.getRevisionNotes() != null ? List.of(entity.getRevisionNotes()) : List.of(),
            entity.getSubmittedAt(),
            entity.getReviewedAt()
        );
    }
}
