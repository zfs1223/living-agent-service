package com.livingagent.core.evolution.personality;

import com.livingagent.core.evolution.circuitbreaker.EvolutionCircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * P29-A: 满意度采集服务。
 * 记录对话结束时的用户满意度，联动 BrainPersonality.riskTolerance。
 * 
 * P29: 变异前后对比+回滚机制+CircuitBreaker联动
 */
@Service
public class SatisfactionCollector {

    private static final Logger log = LoggerFactory.getLogger(SatisfactionCollector.class);
    private static final double LOW_SATISFACTION_THRESHOLD = 2.5;
    private static final double HIGH_SATISFACTION_THRESHOLD = 4.5;
    private static final int MIN_RECORDS_FOR_ADJUSTMENT = 5;
    private static final double RISK_DECREASE_ON_LOW = -0.1;
    private static final double RISK_INCREASE_ON_HIGH = 0.05;
    
    // P29: 回滚阈值（满意度下降超过此值触发回滚）
    private static final double ROLLBACK_THRESHOLD = -1.0;
    private static final int EVALUATION_WINDOW = 10; // 变异后评估窗口（10次对话）

    private final Map<String, Deque<SatisfactionRecord>> recordsByBrain = new ConcurrentHashMap<>();
    
    // P29: 变异前后对比记录
    private final Map<String, MutationEvaluation> mutationEvaluations = new ConcurrentHashMap<>();

    // P29: CircuitBreaker联动（可选依赖，不强制注入）
    private EvolutionCircuitBreaker circuitBreaker;

    /**
     * P29: 注入CircuitBreaker实现联动。
     * 使用setter注入避免循环依赖。
     */
    public void setCircuitBreaker(EvolutionCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        log.info("P29: CircuitBreaker linked to SatisfactionCollector");
    }

    public record SatisfactionRecord(
        String recordId,
        String brainDomain,
        String sessionId,
        int score,
        String feedback,
        Instant createdAt
    ) {}
    
    // P29: 变异评估记录
    public record MutationEvaluation(
        String mutationId,
        String brainDomain,
        double baselineScore,
        double currentScore,
        int sampleCount,
        Instant mutationTime,
        boolean needsRollback
    ) {
        public MutationEvaluation withSample(double newScore, int count) {
            return new MutationEvaluation(mutationId, brainDomain, baselineScore, newScore, count, mutationTime, needsRollback);
        }
        
        public double delta() {
            return currentScore - baselineScore;
        }
    }

    public SatisfactionRecord recordSatisfaction(String brainDomain, String sessionId, int score, String feedback) {
        if (score < 1) score = 1;
        if (score > 5) score = 5;

        SatisfactionRecord record = new SatisfactionRecord(
            UUID.randomUUID().toString(), brainDomain, sessionId, score, feedback, Instant.now());

        recordsByBrain.computeIfAbsent(brainDomain, k -> new ConcurrentLinkedDeque<>()).addFirst(record);

        log.info("P29-A: Recorded satisfaction for {}: score={} sessionId={}", brainDomain, score, sessionId);

        adjustPersonalityIfNeeded(brainDomain);
        return record;
    }

    public double getAverageScore(String brainDomain) {
        Deque<SatisfactionRecord> records = recordsByBrain.get(brainDomain);
        if (records == null || records.isEmpty()) return 0.0;
        return records.stream().mapToInt(SatisfactionRecord::score).average().orElse(0.0);
    }

    public List<SatisfactionRecord> getRecentRecords(String brainDomain, int limit) {
        Deque<SatisfactionRecord> records = recordsByBrain.get(brainDomain);
        if (records == null) return List.of();
        return records.stream().limit(limit).toList();
    }

    private void adjustPersonalityIfNeeded(String brainDomain) {
        Deque<SatisfactionRecord> records = recordsByBrain.get(brainDomain);
        if (records == null || records.size() < MIN_RECORDS_FOR_ADJUSTMENT) return;

        double avg = records.stream()
            .limit(MIN_RECORDS_FOR_ADJUSTMENT)
            .mapToInt(SatisfactionRecord::score)
            .average().orElse(3.0);

        BrainPersonality personality = BrainPersonality.DEFAULT_PERSONALITIES.get(brainDomain);
        if (personality == null) return;

        // P29: 熔断期间暂停个性变异（防止恶性循环）
        if (circuitBreaker != null && circuitBreaker.isCircuitOpen(brainDomain)) {
            log.info("P29: CircuitBreaker open for {}, skipping personality adjustment", brainDomain);
            return;
        }

        if (avg < LOW_SATISFACTION_THRESHOLD) {
            double oldRisk = personality.getRiskTolerance();
            personality.applyMutation(PersonalityMutation.decreaseRisk(brainDomain));
            log.info("P29-A: Low satisfaction (avg={}) for {}, riskTolerance: {} -> {}",
                String.format("%.1f", avg), brainDomain, String.format("%.2f", oldRisk), String.format("%.2f", personality.getRiskTolerance()));
            // P29: 低满意度变异后通知CircuitBreaker
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure(brainDomain);
            }
        } else if (avg > HIGH_SATISFACTION_THRESHOLD) {
            double oldRisk = personality.getRiskTolerance();
            personality.applyMutation(PersonalityMutation.increaseRisk(brainDomain));
            log.debug("P29-A: High satisfaction (avg={}) for {}, riskTolerance: {} -> {}",
                String.format("%.1f", avg), brainDomain, String.format("%.2f", oldRisk), String.format("%.2f", personality.getRiskTolerance()));
            // P29: 高满意度变异后通知CircuitBreaker
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess(brainDomain);
            }
        }
    }
    
    /**
     * P29: 开始变异评估（记录变异前满意度基线）。
     */
    public void startMutationEvaluation(String mutationId, String brainDomain) {
        double baseline = getAverageScore(brainDomain);
        MutationEvaluation evaluation = new MutationEvaluation(
            mutationId, brainDomain, baseline, baseline, 0, Instant.now(), false);
        mutationEvaluations.put(mutationId, evaluation);
        log.info("P29: Started mutation evaluation for {}, mutationId={}, baselineScore={}",
            brainDomain, mutationId, String.format("%.2f", baseline));
    }
    
    /**
     * P29: 评估变异效果（对比前后满意度）。
     * 如果满意度下降超过阈值，触发回滚。
     */
    public void evaluateMutation(String mutationId) {
        MutationEvaluation evaluation = mutationEvaluations.get(mutationId);
        if (evaluation == null) return;
        
        double currentScore = getAverageScore(evaluation.brainDomain());
        int sampleCount = getRecentRecords(evaluation.brainDomain(), EVALUATION_WINDOW).size();
        
        MutationEvaluation updated = evaluation.withSample(currentScore, sampleCount);
        double delta = updated.delta();
        
        // 检查是否需要回滚
        if (sampleCount >= MIN_RECORDS_FOR_ADJUSTMENT && delta < ROLLBACK_THRESHOLD) {
            updated = new MutationEvaluation(
                evaluation.mutationId(), evaluation.brainDomain(), evaluation.baselineScore(),
                currentScore, sampleCount, evaluation.mutationTime(), true);
            mutationEvaluations.put(mutationId, updated);
            
            log.warn("P29: Mutation {} needs rollback - baseline={}, current={}, delta={}",
                mutationId, String.format("%.2f", evaluation.baselineScore()),
                String.format("%.2f", currentScore), String.format("%.2f", delta));
            
            // 触发回滚
            rollbackMutation(evaluation.brainDomain(), mutationId, delta);
        } else {
            mutationEvaluations.put(mutationId, updated);
            log.debug("P29: Mutation {} evaluation - baseline={}, current={}, delta={}, samples={}",
                mutationId, String.format("%.2f", evaluation.baselineScore()),
                String.format("%.2f", currentScore), String.format("%.2f", delta), sampleCount);
        }
    }
    
    /**
     * P29: 执行变异回滚+CircuitBreaker联动。
     */
    private void rollbackMutation(String brainDomain, String mutationId, double delta) {
        BrainPersonality personality = BrainPersonality.DEFAULT_PERSONALITIES.get(brainDomain);
        if (personality == null) return;

        // 执行反向变异（回滚）
        PersonalityMutation rollback = PersonalityMutation.decreaseRisk(
            "P29 rollback: satisfaction dropped by " + String.format("%.2f", delta));
        personality.applyMutation(rollback);

        // P29: 回滚后通知CircuitBreaker（变异失败，记录失败事件）
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure(brainDomain);
            log.info("P29: Notified CircuitBreaker of failed mutation {} for {}", mutationId, brainDomain);
        }

        log.info("P29: Rolled back mutation {} for {}, satisfaction delta={}",
            mutationId, brainDomain, String.format("%.2f", delta));
    }
    
    /**
     * P29: 获取变异评估结果。
     */
    public Optional<MutationEvaluation> getMutationEvaluation(String mutationId) {
        return Optional.ofNullable(mutationEvaluations.get(mutationId));
    }
}
