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
}
