package com.livingagent.gateway.service;

import com.livingagent.core.evolution.KnowledgeQualityReport;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.knowledge.KnowledgeScope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KnowledgeGovernanceService {

    private final KnowledgeManager knowledgeManager;

    public KnowledgeGovernanceService(KnowledgeManager knowledgeManager) {
        this.knowledgeManager = knowledgeManager;
    }

    public Map<String, Object> summary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stats", knowledgeManager.getStatistics());
        payload.put("quality", knowledgeManager.assessQuality());
        payload.put("generatedAt", Instant.now().toString());
        return payload;
    }

    public KnowledgeQualityReport quality() {
        return knowledgeManager.assessQuality();
    }

    public void promote(String key, KnowledgeScope targetScope) {
        Optional<KnowledgeEntry> entry = knowledgeManager.retrieve(key);
        if (entry.isEmpty()) {
            return;
        }
        switch (targetScope) {
            case L1_PRIVATE -> knowledgeManager.moveToLayer(key, KnowledgeManager.KnowledgeLayer.PRIVATE);
            case L2_DEPARTMENT -> knowledgeManager.moveToLayer(key, KnowledgeManager.KnowledgeLayer.DOMAIN);
            case L3_SHARED -> knowledgeManager.moveToLayer(key, KnowledgeManager.KnowledgeLayer.SHARED);
        }
    }

    public void cleanup() {
        knowledgeManager.cleanupExpired();
        knowledgeManager.updateRelevanceScores();
    }

    public List<KnowledgeEntry> search(String query, int limit) {
        return knowledgeManager.search(query, limit);
    }

    /**
     * P2-2: Record knowledge effect feedback
     */
    private final Map<String, FeedbackStats> feedbackStats = new LinkedHashMap<>();

    public void recordFeedback(String knowledgeId, boolean helpful, String employeeId) {
        FeedbackStats stats = feedbackStats.computeIfAbsent(knowledgeId, k -> new FeedbackStats());
        if (helpful) {
            stats.helpfulCount++;
        } else {
            stats.notHelpfulCount++;
        }
        stats.lastFeedbackAt = Instant.now();
        stats.lastFeedbackBy = employeeId;
    }

    public FeedbackStats getFeedbackStats(String knowledgeId) {
        return feedbackStats.getOrDefault(knowledgeId, new FeedbackStats());
    }

    public static class FeedbackStats {
        public int helpfulCount = 0;
        public int notHelpfulCount = 0;
        public Instant lastFeedbackAt = null;
        public String lastFeedbackBy = null;

        public double getHelpfulRate() {
            int total = helpfulCount + notHelpfulCount;
            return total > 0 ? (double) helpfulCount / total : 0;
        }
    }
}
