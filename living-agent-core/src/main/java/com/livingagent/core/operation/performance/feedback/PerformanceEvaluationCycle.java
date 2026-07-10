package com.livingagent.core.operation.performance.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class PerformanceEvaluationCycle {

    private static final Logger log = LoggerFactory.getLogger(PerformanceEvaluationCycle.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, EmployeePerformance> performanceMap = new ConcurrentHashMap<>();
    private final LongAdder totalEvaluations = new LongAdder();
    private final LongAdder improvedAfterTraining = new LongAdder();

    public PerformanceEvaluationCycle(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordEvaluation(String employeeId, double score, String level) {
        EmployeePerformance perf = performanceMap.computeIfAbsent(employeeId, k -> new EmployeePerformance());
        perf.latestScore = score;
        perf.latestLevel = level;
        perf.evaluationCount.increment();
        totalEvaluations.increment();

        if (score < 0.4) {
            log.warn("[闭环53] 低绩效预警: employee={}, score={:.1f}, level={}", employeeId, score, level);
            eventBus.publish(53, "capability_gap", CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("content", String.format("Employee %s performance score %.1f (%s) needs improvement", employeeId, score, level)));
        }
    }

    public void recordTrainingCompletion(String employeeId, double postScore) {
        EmployeePerformance perf = performanceMap.get(employeeId);
        if (perf != null && postScore > perf.latestScore) {
            improvedAfterTraining.increment();
            log.info("[闭环53] 培训有效: employee={}, {}->{:.1f}", employeeId, perf.latestScore, postScore);
        }
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
