package com.livingagent.core.knowledge.impl;

import com.livingagent.core.autonomy.KnowledgeQualityEvaluator;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NP2-2: 知识晋升自动化服务。
 * 定期检查知识条目的晋升条件，自动将满足条件的知识晋升到更高层级。
 */
@Component
public class KnowledgePromotionScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePromotionScheduler.class);

    private final KnowledgeManager knowledgeManager;
    private final KnowledgeQualityEvaluator qualityEvaluator;

    public KnowledgePromotionScheduler(KnowledgeManager knowledgeManager,
                                        KnowledgeQualityEvaluator qualityEvaluator) {
        this.knowledgeManager = knowledgeManager;
        this.qualityEvaluator = qualityEvaluator;
    }

    /**
     * 每10分钟检查一次晋升条件。
     * PRIVATE -> DOMAIN: accessCount >= 3 && confidence >= 0.7
     * DOMAIN -> SHARED: accessCount >= 10 && confidence >= 0.8
     */
    @Scheduled(fixedRate = 600_000, initialDelay = 120_000)
    public void checkAndPromote() {
        try {
            int promotedToDomain = 0;
            int promotedToShared = 0;

            // 检查 PRIVATE 层是否有可晋升到 DOMAIN 的知识
            List<KnowledgeEntry> privateEntries = knowledgeManager.searchInLayer(
                "", com.livingagent.core.knowledge.KnowledgeManager.KnowledgeLayer.PRIVATE, 100);
            for (KnowledgeEntry entry : privateEntries) {
                if (knowledgeManager.canPromoteToDomain(entry.getKey())) {
                    double readiness = qualityEvaluator.calculatePromotionReadiness(entry);
                    if (readiness >= 0.6) {
                        try {
                            knowledgeManager.promoteToDomain(entry.getKey());
                            promotedToDomain++;
                            log.info("NP2-2: Auto-promoted knowledge '{}' from PRIVATE to DOMAIN (readiness={})",
                                entry.getKey(), String.format("%.2f", readiness));
                        } catch (Exception e) {
                            log.warn("NP2-2: Failed to promote '{}' to DOMAIN: {}", entry.getKey(), e.getMessage());
                        }
                    }
                }
            }

            // 检查 DOMAIN 层是否有可晋升到 SHARED 的知识
            List<KnowledgeEntry> domainEntries = knowledgeManager.searchInLayer(
                "", com.livingagent.core.knowledge.KnowledgeManager.KnowledgeLayer.DOMAIN, 100);
            for (KnowledgeEntry entry : domainEntries) {
                if (knowledgeManager.canPromoteToShared(entry.getKey())) {
                    double readiness = qualityEvaluator.calculatePromotionReadiness(entry);
                    if (readiness >= 0.75) {
                        try {
                            knowledgeManager.promoteToShared(entry.getKey());
                            promotedToShared++;
                            log.info("NP2-2: Auto-promoted knowledge '{}' from DOMAIN to SHARED (readiness={})",
                                entry.getKey(), String.format("%.2f", readiness));
                        } catch (Exception e) {
                            log.warn("NP2-2: Failed to promote '{}' to SHARED: {}", entry.getKey(), e.getMessage());
                        }
                    }
                }
            }

            if (promotedToDomain > 0 || promotedToShared > 0) {
                log.info("NP2-2: Knowledge promotion completed: {} to DOMAIN, {} to SHARED",
                    promotedToDomain, promotedToShared);
            }
        } catch (Exception e) {
            log.error("NP2-2: Knowledge promotion check failed: {}", e.getMessage());
        }
    }
}
