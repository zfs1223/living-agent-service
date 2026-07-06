package com.livingagent.core.autonomy.review.impl;

import com.livingagent.core.autonomy.review.InternalReviewService;
import com.livingagent.core.autonomy.review.ReviewHistory;
import com.livingagent.core.autonomy.review.ReviewResult;
import com.livingagent.core.autonomy.review.ReviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认部门内审查服务实现（内存版）。
 *
 * <p>管理审查状态机、轮次计数、终止条件、完成标记。
 * 后续可替换为 JPA 持久化版本。
 */
public class DefaultInternalReviewService implements InternalReviewService {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalReviewService.class);

    private final Map<String, ReviewHistory> reviewsById = new ConcurrentHashMap<>();
    private final Map<String, List<ReviewHistory>> reviewsByTodoItem = new ConcurrentHashMap<>();
    private final Map<String, ReviewState> stateByTodoItem = new ConcurrentHashMap<>();
    private final Map<String, Integer> roundByTodoItem = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxRoundsByTodoItem = new ConcurrentHashMap<>();
    private final Map<String, String> executionIdByTodoItem = new ConcurrentHashMap<>();
    private final List<InternalReviewService.ReviewListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void addReviewListener(InternalReviewService.ReviewListener listener) {
        listeners.add(listener);
    }

    @Override
    public String submitForReview(String todoItemId, String authorCode, String reviewerCode,
                                   String executionId, int maxReviewRounds) {
        String reviewId = "review-" + System.currentTimeMillis() + "-" + todoItemId.hashCode();
        ReviewHistory history = new ReviewHistory(
            reviewId, todoItemId, authorCode, reviewerCode,
            1, ReviewState.SUBMITTED_FOR_REVIEW, null,
            List.of(), Instant.now(), null
        );

        reviewsById.put(reviewId, history);
        reviewsByTodoItem.computeIfAbsent(todoItemId, k -> new ArrayList<>()).add(history);
        stateByTodoItem.put(todoItemId, ReviewState.SUBMITTED_FOR_REVIEW);
        roundByTodoItem.put(todoItemId, 1);
        maxRoundsByTodoItem.put(todoItemId, maxReviewRounds);
        executionIdByTodoItem.put(todoItemId, executionId);

        log.info("Review submitted: reviewId={}, todoItem={}, author={}, reviewer={}, round=1/{}",
            reviewId, todoItemId, authorCode, reviewerCode, maxReviewRounds);

        return reviewId;
    }

    @Override
    public void review(String reviewId, ReviewResult result) {
        ReviewHistory existing = reviewsById.get(reviewId);
        if (existing == null) {
            log.warn("Review not found: reviewId={}", reviewId);
            return;
        }

        String todoItemId = existing.todoItemId();
        int maxRounds = maxRoundsByTodoItem.getOrDefault(todoItemId, 3);

        // 检查是否超过最大轮次
        if (result.reviewRound() >= maxRounds && result.decision() != com.livingagent.core.autonomy.review.ReviewDecision.APPROVED) {
            log.info("Max review rounds reached (round={}/max={}), escalating to brain for todoItem={}",
                result.reviewRound(), maxRounds, todoItemId);
            result = com.livingagent.core.autonomy.review.ReviewResult.escalated(
                result.reviewerCode(), "审查轮次已达上限(" + maxRounds + "轮)", result.reviewRound());
        }

        ReviewState newState = switch (result.decision()) {
            case APPROVED -> ReviewState.COMPLETED;
            case REVISION_NEEDED -> ReviewState.REVISION_NEEDED;
            case REJECTED -> ReviewState.REJECTED;
            case ESCALATE_TO_BRAIN -> ReviewState.ESCALATED;
        };

        ReviewHistory updated = new ReviewHistory(
            existing.reviewId(), existing.todoItemId(), existing.authorCode(),
            existing.reviewerCode(), existing.reviewRound(), newState, result,
            List.of(), existing.submittedAt(), Instant.now()
        );

        reviewsById.put(reviewId, updated);
        stateByTodoItem.put(todoItemId, newState);

        // 更新列表中的记录
        List<ReviewHistory> histories = reviewsByTodoItem.get(todoItemId);
        if (histories != null) {
            for (int i = 0; i < histories.size(); i++) {
                if (histories.get(i).reviewId().equals(reviewId)) {
                    histories.set(i, updated);
                    break;
                }
            }
        }

        log.info("Review completed: reviewId={}, decision={}, qualityScore={}, completionTag={}, round={}",
            reviewId, result.decision(), result.qualityScore(), result.completionTag(), result.reviewRound());

        // 如果需要修改，递增轮次
        if (newState == ReviewState.REVISION_NEEDED) {
            int nextRound = roundByTodoItem.getOrDefault(todoItemId, 1) + 1;
            roundByTodoItem.put(todoItemId, nextRound);
            log.info("Review revision needed, next round={} for todoItem={}", nextRound, todoItemId);
        }

        // 通知监听器
        String authorCode = existing.authorCode();
        String executionId = executionIdByTodoItem.get(todoItemId);
        for (InternalReviewService.ReviewListener listener : listeners) {
            try {
                listener.onReviewResult(todoItemId, reviewId, result, authorCode, executionId);
            } catch (Exception e) {
                log.warn("Review listener callback failed for todoItem={}: {}", todoItemId, e.getMessage());
            }
        }
    }

    @Override
    public Optional<ReviewHistory> getReview(String reviewId) {
        return Optional.ofNullable(reviewsById.get(reviewId));
    }

    @Override
    public List<ReviewHistory> getReviewHistoryByTodoItem(String todoItemId) {
        return reviewsByTodoItem.getOrDefault(todoItemId, List.of());
    }

    @Override
    public Optional<ReviewState> getReviewState(String todoItemId) {
        return Optional.ofNullable(stateByTodoItem.get(todoItemId));
    }

    @Override
    public boolean isCompleted(String todoItemId) {
        ReviewState state = stateByTodoItem.get(todoItemId);
        return state == ReviewState.COMPLETED;
    }

    @Override
    public int getCurrentRound(String todoItemId) {
        return roundByTodoItem.getOrDefault(todoItemId, 0);
    }
}
