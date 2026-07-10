package com.livingagent.core.memory.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 闭环48-P48-B: 记忆整合服务
 * 自动整合相似记忆+低价值记忆归档
 */
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private final MemoryConversionTracker conversionTracker;

    public MemoryConsolidationService(MemoryConversionTracker conversionTracker) {
        this.conversionTracker = conversionTracker;
    }

    public void onMemoryCreated() {
        conversionTracker.recordMemoryCreated();
    }

    public void onKnowledgeExtracted() {
        conversionTracker.recordKnowledgeExtracted();
    }

    public void onMemoryArchived() {
        conversionTracker.recordMemoryArchived();
    }

    public void checkAndOptimize() {
        MemoryConversionTracker.MemoryConversionReport report = conversionTracker.getReport();
        if (report.memoriesCreated() < 10) return;

        if (report.conversionRate() < 0.10) {
            log.info("[闭环48] 记忆→知识转化率{}%偏低，建议降低提取阈值",
                String.format("%.0f%%", report.conversionRate() * 100));
        }
    }
}
