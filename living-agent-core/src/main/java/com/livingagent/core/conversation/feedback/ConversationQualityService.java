package com.livingagent.core.conversation.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环46-P46-A: 对话质量评估服务
 * 评估对话质量(解决率/澄清率/满意度)
 */
@Component
public class ConversationQualityService {

    private static final Logger log = LoggerFactory.getLogger(ConversationQualityService.class);

    private final LongAdder totalConversations = new LongAdder();
    private final LongAdder resolvedConversations = new LongAdder();
    private final LongAdder clarificationNeeded = new LongAdder();
    private final Map<String, ConversationMetrics> convMetrics = new ConcurrentHashMap<>();

    public void recordCompletion(String conversationId, boolean resolved, boolean neededClarification) {
        totalConversations.increment();
        if (resolved) resolvedConversations.increment();
        if (neededClarification) clarificationNeeded.increment();
        convMetrics.computeIfAbsent(conversationId, k -> new ConversationMetrics())
            .recordCompletion(resolved, neededClarification);
    }

    public ConversationQualityReport getReport() {
        long total = totalConversations.sum();
        return new ConversationQualityReport(
            total, resolvedConversations.sum(), clarificationNeeded.sum(),
            total > 0 ? (double) resolvedConversations.sum() / total : 0,
            total > 0 ? (double) clarificationNeeded.sum() / total : 0,
            Instant.now()
        );
    }

    public record ConversationQualityReport(
        long totalConversations, long resolvedConversations, long clarificationNeeded,
        double resolutionRate, double clarificationRate, Instant capturedAt
    ) {}

    private static class ConversationMetrics {
        final LongAdder completions = new LongAdder();
        final LongAdder resolved = new LongAdder();
        final LongAdder clarified = new LongAdder();
        void recordCompletion(boolean isResolved, boolean neededClarification) {
            completions.increment();
            if (isResolved) resolved.increment();
            if (neededClarification) clarified.increment();
        }
    }
}
