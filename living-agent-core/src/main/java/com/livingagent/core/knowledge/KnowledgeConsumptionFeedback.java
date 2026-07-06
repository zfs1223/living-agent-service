package com.livingagent.core.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P26-A: 知识消费效果反馈服务。
 * 记录知识消费后的效果反馈，根据反馈调整 confidence 和 relevance。
 * 闭环：知识消费 → 效果反馈 → confidence调整 → 晋升/降级
 */
@Service
public class KnowledgeConsumptionFeedback {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConsumptionFeedback.class);

    private final KnowledgeBase knowledgeBase;

    private static final double POSITIVE_FEEDBACK_DELTA = 0.05;
    private static final double NEGATIVE_FEEDBACK_DELTA = -0.1;
    private static final double CONFIDENCE_PROMOTION_THRESHOLD = 0.8;
    private static final double CONFIDENCE_DEMOTION_THRESHOLD = 0.3;

    private final Map<String, List<FeedbackRecord>> feedbackHistory = new ConcurrentHashMap<>();

    public record FeedbackRecord(
        String knowledgeKey,
        boolean helpful,
        String context,
        String consumerId,
        Instant timestamp
    ) {}

    public KnowledgeConsumptionFeedback(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * 记录知识消费反馈。
     * @param knowledgeKey 知识键
     * @param helpful 是否有帮助
     * @param context 消费上下文（如任务ID、会话ID）
     * @param consumerId 消费者ID
     */
    public void recordFeedback(String knowledgeKey, boolean helpful, String context, String consumerId) {
        FeedbackRecord record = new FeedbackRecord(knowledgeKey, helpful, context, consumerId, Instant.now());
        feedbackHistory.computeIfAbsent(knowledgeKey, k -> new ArrayList<>()).add(record);

        // 根据反馈调整 relevance
        double delta = helpful ? POSITIVE_FEEDBACK_DELTA : NEGATIVE_FEEDBACK_DELTA;
        try {
            knowledgeBase.updateKnowledgeRelevance(knowledgeKey, delta);
        } catch (Exception e) {
            log.warn("P26-A: Failed to update relevance for key={}: {}", knowledgeKey, e.getMessage());
        }

        log.info("P26-A: Feedback recorded: key={}, helpful={}, consumer={}, delta={}",
            knowledgeKey, helpful, consumerId, delta);

        // 检查是否需要晋升或降级
        checkAndAdjustConfidence(knowledgeKey);
    }

    private void checkAndAdjustConfidence(String knowledgeKey) {
        List<FeedbackRecord> history = feedbackHistory.get(knowledgeKey);
        if (history == null || history.size() < 3) return;

        long recentPositive = history.stream()
            .filter(r -> r.timestamp().isAfter(Instant.now().minusSeconds(86400)))
            .filter(r -> r.helpful())
            .count();
        long recentNegative = history.stream()
            .filter(r -> r.timestamp().isAfter(Instant.now().minusSeconds(86400)))
            .filter(r -> !r.helpful())
            .count();
        long recentTotal = recentPositive + recentNegative;

        if (recentTotal < 3) return;

        double helpfulRate = (double) recentPositive / recentTotal;

        Optional<KnowledgeEntry> entryOpt = knowledgeBase.retrieveEntry(knowledgeKey);
        if (entryOpt.isEmpty()) return;

        KnowledgeEntry entry = entryOpt.get();
        double currentConfidence = entry.getConfidence();

        if (helpfulRate >= 0.8 && currentConfidence < CONFIDENCE_PROMOTION_THRESHOLD) {
            double newConfidence = Math.min(1.0, currentConfidence + POSITIVE_FEEDBACK_DELTA * 3);
            entry.setConfidence(newConfidence);
            log.info("P26-A: Confidence promoted: key={}, {} -> {} (helpfulRate={})",
                knowledgeKey, currentConfidence, newConfidence, helpfulRate);
        } else if (helpfulRate <= 0.3 && currentConfidence > CONFIDENCE_DEMOTION_THRESHOLD) {
            double newConfidence = Math.max(0.0, currentConfidence + NEGATIVE_FEEDBACK_DELTA * 2);
            entry.setConfidence(newConfidence);
            log.warn("P26-A: Confidence demoted: key={}, {} -> {} (helpfulRate={})",
                knowledgeKey, currentConfidence, newConfidence, helpfulRate);
        }
    }

    public List<FeedbackRecord> getFeedbackHistory(String knowledgeKey) {
        return feedbackHistory.getOrDefault(knowledgeKey, Collections.emptyList());
    }

    public double getRecentHelpfulRate(String knowledgeKey) {
        List<FeedbackRecord> history = feedbackHistory.get(knowledgeKey);
        if (history == null) return 1.0;

        long recentPositive = history.stream()
            .filter(r -> r.timestamp().isAfter(Instant.now().minusSeconds(86400)))
            .filter(r -> r.helpful())
            .count();
        long recentTotal = history.stream()
            .filter(r -> r.timestamp().isAfter(Instant.now().minusSeconds(86400)))
            .count();

        return recentTotal > 0 ? (double) recentPositive / recentTotal : 1.0;
    }
}
