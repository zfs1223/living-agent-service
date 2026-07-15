package com.livingagent.core.memory.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.memory.impl.MemoryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 闭环48-P48-B: 记忆整合服务
 * 自动整合相似记忆+低价值记忆归档
 */
@Component
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private final MemoryConversionTracker conversionTracker;
    private CrossLoopEventBus eventBus;
    private MemoryServiceImpl memoryService;

    private double extractionThreshold = 0.15;

    public MemoryConsolidationService(MemoryConversionTracker conversionTracker) {
        this.conversionTracker = conversionTracker;
    }

    @Autowired(required = false)
    public void setEventBus(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Autowired(required = false)
    public void setMemoryService(MemoryServiceImpl memoryService) {
        this.memoryService = memoryService;
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

    @Scheduled(fixedRate = 30 * 60 * 1000) // 每30分钟
    public void checkAndOptimize() {
        MemoryConversionTracker.MemoryConversionReport report = conversionTracker.getReport();
        if (report.memoriesCreated() < 10) return;

        if (report.conversionRate() < 0.10) {
            // 动态调整提取阈值：降低阈值以提取更多知识
            double oldThreshold = extractionThreshold;
            extractionThreshold = Math.max(0.05, extractionThreshold - 0.02);
            log.info("[闭环48] 记忆→知识转化率{}%偏低，提取阈值从{}降至{}",
                String.format("%.0f", report.conversionRate() * 100), oldThreshold, extractionThreshold);

            if (eventBus != null) {
                eventBus.publish(48, "memory_strategy_adjusted",
                    CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("extractionThreshold", extractionThreshold,
                        "conversionRate", report.conversionRate()), 300);
            }
        } else if (report.conversionRate() > 0.40 && extractionThreshold < 0.15) {
            extractionThreshold = Math.min(0.15, extractionThreshold + 0.01);
            log.info("[闭环48] 记忆→知识转化率正常，提取阈值回升至{}", extractionThreshold);
        }
    }

    public double getExtractionThreshold() {
        return extractionThreshold;
    }
}
