package com.livingagent.core.anomaly.feedback;

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
public class AnomalyDetectionFeedbackLoop {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionFeedbackLoop.class);
    private static final double HIGH_FALSE_POSITIVE_RATE = 0.40;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalAlerts = new LongAdder();
    private final LongAdder truePositives = new LongAdder();
    private final LongAdder falsePositives = new LongAdder();
    private final LongAdder missedDetections = new LongAdder();
    private volatile double sensitivityMultiplier = 1.0;

    public AnomalyDetectionFeedbackLoop(@Autowired(required = false) CrossLoopEventBus eventBus) {
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
            log.warn("[闭环59] 误报率过高: {}% > {}%",
                String.format("%.0f", falsePositiveRate * 100),
                String.format("%.0f", HIGH_FALSE_POSITIVE_RATE * 100));
            if (eventBus != null) {
                eventBus.publish(59, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("content", String.format("Anomaly detection false positive rate %.0f%% exceeds %.0f%%, suggest adjusting model parameters", falsePositiveRate * 100, HIGH_FALSE_POSITIVE_RATE * 100)));
            }
        }

        return new AnomalyFeedbackReport(total, tp, fp, missedDetections.sum(), falsePositiveRate);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustSensitivity() {
        long total = totalAlerts.sum();
        if (total < 10) return;
        double falsePositiveRate = (double) falsePositives.sum() / total;

        if (falsePositiveRate > HIGH_FALSE_POSITIVE_RATE && sensitivityMultiplier < 1.5) {
            double old = sensitivityMultiplier;
            sensitivityMultiplier = Math.min(1.5, sensitivityMultiplier + 0.1);
            log.info("[闭环59] 误报率{}%过高，敏感度乘数从{}提升至{}（降低敏感度）",
                String.format("%.0f", falsePositiveRate * 100),
                String.format("%.1f", old), String.format("%.1f", sensitivityMultiplier));
            if (eventBus != null) {
                eventBus.publish(59, "sensitivity_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("sensitivityMultiplier", sensitivityMultiplier, "falsePositiveRate", falsePositiveRate), 300);
            }
        } else if (falsePositiveRate < 0.10 && missedDetections.sum() > 5 && sensitivityMultiplier > 1.0) {
            double old = sensitivityMultiplier;
            sensitivityMultiplier = Math.max(1.0, sensitivityMultiplier - 0.05);
            log.info("[闭环59] 误报率低但漏检多，敏感度乘数从{}降至{}（提高敏感度）",
                String.format("%.1f", old), String.format("%.1f", sensitivityMultiplier));
        }
    }

    public double getSensitivityMultiplier() {
        return sensitivityMultiplier;
    }

    public record AnomalyFeedbackReport(long totalAlerts, long truePositives, long falsePositives,
                                         long missedDetections, double falsePositiveRate) {}
}
