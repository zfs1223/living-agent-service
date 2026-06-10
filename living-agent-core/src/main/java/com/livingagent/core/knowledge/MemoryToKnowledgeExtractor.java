package com.livingagent.core.knowledge;

import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆到知识自动提取器
 *
 * 定期扫描高价值记忆（使用频率高、评分高），
 * 自动提取为知识并存储到知识库。
 *
 * 提取条件：
 * - 记忆评分 > 0.7（高相关性）
 * - 记忆类别为 CORE 或 DAILY（非对话临时记忆）
 * - 同一内容被多次召回（频率阈值）
 */
@Service
public class MemoryToKnowledgeExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryToKnowledgeExtractor.class);

    private static final double MIN_SCORE_THRESHOLD = 0.7;
    private static final int MIN_RECALL_COUNT = 3;
    private static final int MAX_EXTRACTIONS_PER_CYCLE = 20;

    private final Memory memoryBackend;
    private final KnowledgeManager knowledgeManager;

    private final Map<String, Integer> recallCountByKey = new ConcurrentHashMap<>();
    private final Set<String> extractedKeys = ConcurrentHashMap.newKeySet();

    public MemoryToKnowledgeExtractor(Memory memoryBackend, KnowledgeManager knowledgeManager) {
        this.memoryBackend = memoryBackend;
        this.knowledgeManager = knowledgeManager;
    }

    /**
     * 记录记忆被召回，用于频率统计
     */
    public void recordRecall(String memoryKey) {
        recallCountByKey.merge(memoryKey, 1, Integer::sum);
    }

    /**
     * 定期扫描并提取高价值记忆为知识
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000)
    public void extractHighValueMemories() {
        log.info("Starting memory-to-knowledge extraction cycle...");

        int extractedCount = 0;
        try {
            List<MemoryEntry> coreMemories = memoryBackend
                .list(MemoryCategory.CORE, null)
                .exceptionally(ex -> List.of()).join();

            List<MemoryEntry> dailyMemories = memoryBackend
                .list(MemoryCategory.DAILY, null)
                .exceptionally(ex -> List.of()).join();

            List<MemoryEntry> candidates = new ArrayList<>();
            candidates.addAll(coreMemories);
            candidates.addAll(dailyMemories);

            candidates.sort((a, b) -> {
                double scoreA = a.hasScore() ? a.score() : 0;
                double scoreB = b.hasScore() ? b.score() : 0;
                return Double.compare(scoreB, scoreA);
            });

            for (MemoryEntry entry : candidates) {
                if (extractedCount >= MAX_EXTRACTIONS_PER_CYCLE) break;
                if (extractedKeys.contains(entry.key())) continue;

                if (shouldExtract(entry)) {
                    extractToKnowledge(entry);
                    extractedCount++;
                }
            }

        } catch (Exception e) {
            log.error("Memory-to-knowledge extraction failed: {}", e.getMessage());
        }

        log.info("Memory-to-knowledge extraction cycle completed. Extracted: {}", extractedCount);
    }

    private boolean shouldExtract(MemoryEntry entry) {
        double score = entry.hasScore() ? entry.score() : 0;
        int recallCount = recallCountByKey.getOrDefault(entry.key(), 0);

        return score >= MIN_SCORE_THRESHOLD && recallCount >= MIN_RECALL_COUNT;
    }

    private void extractToKnowledge(MemoryEntry entry) {
        try {
            String knowledgeKey = "extracted_" + entry.key();
            Map<String, String> metadata = Map.of(
                "source", "memory_extraction",
                "originalMemoryId", entry.id(),
                "originalCategory", entry.category().name(),
                "extractionTime", Instant.now().toString()
            );

            knowledgeManager.storePrivate(knowledgeKey, entry.content(), metadata);
            extractedKeys.add(entry.key());

            log.info("Extracted memory '{}' to knowledge (score={}, recalls={})",
                entry.key(),
                entry.hasScore() ? String.format("%.2f", entry.score()) : "N/A",
                recallCountByKey.getOrDefault(entry.key(), 0));
        } catch (Exception e) {
            log.warn("Failed to extract memory '{}' to knowledge: {}", entry.key(), e.getMessage());
        }
    }

    /**
     * 获取提取统计信息
     */
    public Map<String, Object> getExtractionStats() {
        return Map.of(
            "totalRecallKeys", recallCountByKey.size(),
            "totalExtractedKeys", extractedKeys.size(),
            "minScoreThreshold", MIN_SCORE_THRESHOLD,
            "minRecallCount", MIN_RECALL_COUNT
        );
    }
}
