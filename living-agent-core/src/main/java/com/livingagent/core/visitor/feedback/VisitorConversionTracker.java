package com.livingagent.core.visitor.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class VisitorConversionTracker {

    private static final Logger log = LoggerFactory.getLogger(VisitorConversionTracker.class);
    private static final double LOW_CONVERSION_THRESHOLD = 0.10;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalVisitors = new LongAdder();
    private final LongAdder registeredVisitors = new LongAdder();
    private final LongAdder chattedVisitors = new LongAdder();
    private final LongAdder leftVisitors = new LongAdder();

    public VisitorConversionTracker(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordVisit(String visitorId) {
        totalVisitors.increment();
        log.debug("[闭环51] 访客进入: id={}", visitorId);
    }

    public void recordChat(String visitorId) {
        chattedVisitors.increment();
    }

    public void recordRegistration(String visitorId) {
        registeredVisitors.increment();
    }

    public void recordLeave(String visitorId) {
        leftVisitors.increment();
    }

    public VisitorConversionReport getReport() {
        long total = totalVisitors.sum();
        long registered = registeredVisitors.sum();
        long chatted = chattedVisitors.sum();
        double conversionRate = total > 0 ? (double) registered / total : 0;
        double chatRate = total > 0 ? (double) chatted / total : 0;

        if (total > 10 && conversionRate < LOW_CONVERSION_THRESHOLD) {
            log.warn("[闭环51] 访客转化率过低: {:.1%} < {:.0%}%", conversionRate, LOW_CONVERSION_THRESHOLD);
            eventBus.publish(51, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                Map.of("content", String.format("Visitor conversion rate %.1f%% below %.0f%%, suggest optimizing reception scripts", conversionRate * 100, LOW_CONVERSION_THRESHOLD * 100)));
        }

        return new VisitorConversionReport(total, registered, chatted, leftVisitors.sum(), conversionRate, chatRate);
    }

    public record VisitorConversionReport(long totalVisitors, long registered, long chatted,
                                           long left, double conversionRate, double chatRate) {}
}
