package com.livingagent.gateway.service;

import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.knowledge.KnowledgeScope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgePromotionAuditService {

    private final KnowledgeManager knowledgeManager;
    private final Map<String, List<PromotionAuditRecord>> auditHistory = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeScope> lastScope = new ConcurrentHashMap<>();

    public KnowledgePromotionAuditService(KnowledgeManager knowledgeManager) {
        this.knowledgeManager = knowledgeManager;
    }

    public Map<String, Object> promote(String key, KnowledgeScope targetScope, String changedBy, String reason) {
        KnowledgeEntry before = knowledgeManager.retrieve(key).orElse(null);
        if (before == null) {
            return Map.of("ok", false, "error", "not_found");
        }

        KnowledgeScope fromScope = before.getScope();
        lastScope.put(key, fromScope);

        knowledgeManager.moveToLayer(key, switch (targetScope) {
            case L1_PRIVATE -> KnowledgeManager.KnowledgeLayer.PRIVATE;
            case L2_DEPARTMENT -> KnowledgeManager.KnowledgeLayer.DOMAIN;
            case L3_SHARED -> KnowledgeManager.KnowledgeLayer.SHARED;
        });

        KnowledgeEntry after = knowledgeManager.retrieve(key).orElse(null);
        PromotionAuditRecord record = new PromotionAuditRecord(
                "audit_" + System.currentTimeMillis(),
                key,
                fromScope != null ? fromScope.name() : null,
                targetScope.name(),
                changedBy,
                reason,
                Instant.now()
        );
        auditHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(record);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("before", before != null ? before.toString() : null);
        payload.put("after", after != null ? after.toString() : null);
        payload.put("record", record);
        return payload;
    }

    public Map<String, Object> rollback(String key, String changedBy, String reason) {
        KnowledgeScope scope = lastScope.get(key);
        if (scope == null) {
            return Map.of("ok", false, "error", "no_previous_scope");
        }
        return promote(key, scope, changedBy, "rollback: " + reason);
    }

    public List<PromotionAuditRecord> history(String key) {
        return new ArrayList<>(auditHistory.getOrDefault(key, List.of()));
    }

    public record PromotionAuditRecord(
            String auditId,
            String key,
            String fromScope,
            String toScope,
            String changedBy,
            String reason,
            Instant changedAt
    ) {}
}
