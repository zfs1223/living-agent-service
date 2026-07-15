package com.livingagent.core.operation.performance.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class PerformanceEvaluationCycle {

    private static final Logger log = LoggerFactory.getLogger(PerformanceEvaluationCycle.class);
    private static final double DEFAULT_LOW_PERFORMANCE_THRESHOLD = 0.4;

    private final CrossLoopEventBus eventBus;
    private final Map<String, EmployeePerformance> performanceMap = new ConcurrentHashMap<>();
    private final LongAdder totalEvaluations = new LongAdder();
    private final LongAdder improvedAfterTraining = new LongAdder();
    private volatile double lowPerformanceThreshold = DEFAULT_LOW_PERFORMANCE_THRESHOLD;

    public PerformanceEvaluationCycle(@Autowired(required = false) CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordEvaluation(String employeeId, double score, String level) {
        EmployeePerformance perf = performanceMap.computeIfAbsent(employeeId, k -> new EmployeePerformance());
        perf.latestScore = score;
        perf.latestLevel = level;
        perf.evaluationCount.increment();
        totalEvaluations.increment();

        if (score < lowPerformanceThreshold) {
            log.warn("[闭环53] 低绩效预警: employee={}, score={}, level={}",
                employeeId, String.format("%.1f", score), level);
            if (eventBus != null) {
                eventBus.publish(53, "capability_gap", CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("content", String.format("Employee %s performance score %.1f (%s) needs improvement", employeeId, score, level)));
            }
        }
    }

    public void recordTrainingCompletion(String employeeId, double postScore) {
        EmployeePerformance perf = performanceMap.get(employeeId);
        if (perf != null && postScore > perf.latestScore) {
            improvedAfterTraining.increment();
            log.info("[闭环53] 培训有效: employee={}, {}->{}", employeeId,
                String.format("%.1f", perf.latestScore), String.format("%.1f", postScore));
        }
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustPerformanceThreshold() {
        if (performanceMap.isEmpty()) return;
        long lowPerfCount = performanceMap.values().stream()
            .filter(p -> p.latestScore < lowPerformanceThreshold)
            .count();
        double lowPerfRate = (double) lowPerfCount / performanceMap.size();
        if (lowPerfRate > 0.30 && lowPerformanceThreshold < 0.6) {
            double old = lowPerformanceThreshold;
            lowPerformanceThreshold = Math.min(0.6, lowPerformanceThreshold + 0.05);
            log.info("[闭环53] 低绩效率{}%过高，阈值从{}提升至{}",
                String.format("%.0f", lowPerfRate * 100),
                String.format("%.1f", old), String.format("%.1f", lowPerformanceThreshold));
            if (eventBus != null) {
                eventBus.publish(53, "performance_threshold_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("lowPerformanceThreshold", lowPerformanceThreshold, "lowPerfRate", lowPerfRate), 300);
            }
        } else if (lowPerfRate < 0.10 && lowPerformanceThreshold > DEFAULT_LOW_PERFORMANCE_THRESHOLD) {
            lowPerformanceThreshold = Math.max(DEFAULT_LOW_PERFORMANCE_THRESHOLD, lowPerformanceThreshold - 0.02);
        }
    }

    public double getLowPerformanceThreshold() {
        return lowPerformanceThreshold;
    }

    public PerformanceCycleReport getReport() {
        long total = totalEvaluations.sum();
        long improved = improvedAfterTraining.sum();
        double trainingEffectiveness = total > 0 ? (double) improved / total : 0;
        return new PerformanceCycleReport(total, improved, trainingEffectiveness, performanceMap.size());
    }

    public static class EmployeePerformance {
        double latestScore;
        String latestLevel;
        LongAdder evaluationCount = new LongAdder();
    }

    public record PerformanceCycleReport(long totalEvaluations, long improvedAfterTraining,
                                          double trainingEffectiveness, int activeEmployees) {}
}
