package com.livingagent.core.memory.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环48-P48-A: 记忆→知识转化追踪器
 * 追踪记忆→知识转化率/引用率/归档率
 */
public class MemoryConversionTracker {

    private static final Logger log = LoggerFactory.getLogger(MemoryConversionTracker.class);

    private final LongAdder memoriesCreated = new LongAdder();
    private final LongAdder knowledgeExtracted = new LongAdder();
    private final LongAdder knowledgeReferenced = new LongAdder();
    private final LongAdder memoriesArchived = new LongAdder();

    public void recordMemoryCreated() { memoriesCreated.increment(); }
    public void recordKnowledgeExtracted() { knowledgeExtracted.increment(); }
    public void recordKnowledgeReferenced() { knowledgeReferenced.increment(); }
    public void recordMemoryArchived() { memoriesArchived.increment(); }

    public MemoryConversionReport getReport() {
        long memories = memoriesCreated.sum();
        long knowledge = knowledgeExtracted.sum();
        return new MemoryConversionReport(
            memories, knowledge, knowledgeReferenced.sum(), memoriesArchived.sum(),
            memories > 0 ? (double) knowledge / memories : 0,
            knowledge > 0 ? (double) knowledgeReferenced.sum() / knowledge : 0,
            memories > 0 ? (double) memoriesArchived.sum() / memories : 0,
            Instant.now()
        );
    }

    public String getSummary() {
        MemoryConversionReport r = getReport();
        return String.format("Memory→Knowledge: created=%d, extracted=%d, conversionRate=%.0f%%, referenceRate=%.0f%%",
            r.memoriesCreated(), r.knowledgeExtracted(),
            r.conversionRate() * 100, r.referenceRate() * 100);
    }

    public record MemoryConversionReport(
        long memoriesCreated, long knowledgeExtracted, long knowledgeReferenced, long memoriesArchived,
        double conversionRate, double referenceRate, double archiveRate, Instant capturedAt
    ) {}
}
