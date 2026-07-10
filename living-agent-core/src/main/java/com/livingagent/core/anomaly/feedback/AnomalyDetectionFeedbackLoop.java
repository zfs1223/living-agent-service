package com.livingagent.core.anomaly.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class AnomalyDetectionFeedbackLoop {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionFeedbackLoop.class);
    private static final double HIGH_FALSE_POSITIVE_RATE = 0.40;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalAlerts = new LongAdder();
    private final LongAdder truePositives = new LongAdder();
    private final LongAdder falsePositives = new LongAdder();
    private final LongAdder missedDetections = new LongAdder();

    public AnomalyDetectionFeedbackLoop(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordAlert(String alertId, boolean confirmed) {
        totalAlerts.increment();
        if (confirmed) {
            truePositives.increment();
        } else {
            falsePositives.increment();
        }
    }

    public void recordMissedDetection(String description) {
        missedDetections.increment();
        log.warn("[闭环59] 漏检: {}", description);
    }

    public AnomalyFeedbackReport getReport() {
        long total = totalAlerts.sum();
        long tp = truePositives.sum();
        long fp = falsePositives.sum();
        double falsePositiveRate = total > 0 ? (double) fp / total : 0;

        if (total > 10 && falsePositiveRate > HIGH_FALSE_POSITIVE_RATE) {
            log.warn("[闭环59] 误报率过高: {:.0%} > {:.0%}%", falsePositiveRate, HIGH_FALSE_POSITIVE_RATE);
            eventBus.publish(59, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Anomaly detection false positive rate %.0f%% exceeds %.0f%%, suggest adjusting model parameters", falsePositiveRate * 100, HIGH_FALSE_POSITIVE_RATE * 100)));
        }

        return new AnomalyFeedbackReport(total, tp, fp, missedDetections.sum(), falsePositiveRate);
    }

    public record AnomalyFeedbackReport(long totalAlerts, long truePositives, long falsePositives,
                                         long missedDetections, double falsePositiveRate) {}
}
