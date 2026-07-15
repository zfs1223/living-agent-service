package com.livingagent.core.visitor.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class VisitorConversionTracker {

    private static final Logger log = LoggerFactory.getLogger(VisitorConversionTracker.class);
    private static final double DEFAULT_CONVERSION_THRESHOLD = 0.10;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalVisitors = new LongAdder();
    private final LongAdder registeredVisitors = new LongAdder();
    private final LongAdder chattedVisitors = new LongAdder();
    private final LongAdder leftVisitors = new LongAdder();
    private volatile double conversionWarningThreshold = DEFAULT_CONVERSION_THRESHOLD;

    public VisitorConversionTracker(@Autowired(required = false) CrossLoopEventBus eventBus) {
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

        if (total > 10 && conversionRate < conversionWarningThreshold) {
            log.warn("[闭环51] 访客转化率过低: {}% < {}%",
                String.format("%.1f", conversionRate * 100), String.format("%.0f", conversionWarningThreshold * 100));
            if (eventBus != null) {
                eventBus.publish(51, "improvement_opportunity", CrossLoopEvent.EventPriority.ECONOMY,
                    Map.of("content", String.format("Visitor conversion rate %.1f%% below %.0f%%, suggest optimizing reception scripts", conversionRate * 100, conversionWarningThreshold * 100)));
            }
        }

        return new VisitorConversionReport(total, registered, chatted, leftVisitors.sum(), conversionRate, chatRate);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustConversionThreshold() {
        long total = totalVisitors.sum();
        if (total < 10) return;
        double conversionRate = (double) registeredVisitors.sum() / total;
        if (conversionRate < 0.05 && conversionWarningThreshold > 0.05) {
            double old = conversionWarningThreshold;
            conversionWarningThreshold = Math.max(0.05, conversionWarningThreshold - 0.02);
            log.info("[闭环51] 转化率{}%极低，预警阈值从{}%降至{}%",
                String.format("%.1f", conversionRate * 100), String.format("%.0f", old * 100), String.format("%.0f", conversionWarningThreshold * 100));
            if (eventBus != null) {
                eventBus.publish(51, "conversion_threshold_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("conversionWarningThreshold", conversionWarningThreshold, "conversionRate", conversionRate), 300);
            }
        } else if (conversionRate > 0.20 && conversionWarningThreshold < DEFAULT_CONVERSION_THRESHOLD) {
            conversionWarningThreshold = Math.min(DEFAULT_CONVERSION_THRESHOLD, conversionWarningThreshold + 0.01);
        }
    }

    public double getConversionWarningThreshold() {
        return conversionWarningThreshold;
    }

    public record VisitorConversionReport(long totalVisitors, long registered, long chatted,
                                           long left, double conversionRate, double chatRate) {}
}
