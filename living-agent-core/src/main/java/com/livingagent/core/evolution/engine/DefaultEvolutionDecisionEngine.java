package com.livingagent.core.evolution.engine;

import com.livingagent.core.evolution.circuitbreaker.CircuitBreakerReport;
import com.livingagent.core.evolution.circuitbreaker.EvolutionCircuitBreaker;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEvolutionDecisionEngine implements EvolutionDecisionEngine {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultEvolutionDecisionEngine.class);
    
    private final EvolutionMemoryGraph memoryGraph;
    private final EvolutionCircuitBreaker circuitBreaker;
    private final Map<String, BrainPersonality> personalities;
    private final Map<String, EvolutionConstraints> domainConstraints;
    private final Map<String, Integer> dailyInnovationCount;
    private final Map<String, Instant> lastInnovationTime;
    
    public DefaultEvolutionDecisionEngine(EvolutionMemoryGraph memoryGraph, 
                                          EvolutionCircuitBreaker circuitBreaker) {
        this.memoryGraph = memoryGraph;
        this.circuitBreaker = circuitBreaker;
        this.personalities = new ConcurrentHashMap<>();
        this.domainConstraints = new ConcurrentHashMap<>();
        this.dailyInnovationCount = new ConcurrentHashMap<>();
        this.lastInnovationTime = new ConcurrentHashMap<>();
        
        initializeDefaultPersonalities();
        initializeDefaultConstraints();
    }
    
    private void initializeDefaultPersonalities() {
        for (Map.Entry<String, BrainPersonality> entry : BrainPersonality.DEFAULT_PERSONALITIES.entrySet()) {
            personalities.put(entry.getKey(), entry.getValue());
        }
    }
    
    private void initializeDefaultConstraints() {
        domainConstraints.put("FinanceBrain", EvolutionConstraints.strict());
        domainConstraints.put("LegalBrain", EvolutionConstraints.strict());
        domainConstraints.put("TechBrain", EvolutionConstraints.relaxed());
        domainConstraints.put("MainBrain", EvolutionConstraints.relaxed());
    }
    
    @Override
    public EvolutionDecision decide(EvolutionSignal signal) {
        EvolutionConstraints constraints = getDefaultConstraints(signal.getBrainDomain());
        return decideWithConstraints(signal, constraints);
    }
    
    @Override
    public EvolutionDecision decideWithConstraints(EvolutionSignal signal, EvolutionConstraints constraints) {
        String brainDomain = signal.getBrainDomain();
        
        BrainPersonality personality = personalities.computeIfAbsent(
            brainDomain, 
            k -> BrainPersonality.getDefaultForBrain(brainDomain)
        );
        
        CircuitBreakerReport cbReport = circuitBreaker.getReport(brainDomain);
        
        if (cbReport.isTripped()) {
            return handleTrippedCircuit(signal, cbReport, personality);
        }
        
        EvolutionSignal.SignalCategory forcedCategory = circuitBreaker.getForcedCategory(brainDomain);
        if (forcedCategory != null) {
            return createForcedDecision(signal, forcedCategory, cbReport);
        }
        
        EvolutionDecision decision = new EvolutionDecision();
        decision.setSignal(signal);
        decision.setPersonality(personality);
        decision.setCircuitBreakerStatus(cbReport);
        
        EvolutionStrategy strategy = determineStrategy(signal, personality, constraints, cbReport);
        decision.setStrategy(strategy);
        
        double confidence = calculateConfidence(signal, personality, constraints, cbReport);
        decision.setConfidence(confidence);
        
        if (confidence < constraints.getMinConfidenceThreshold()) {
            decision.setStrategy(EvolutionStrategy.DEFER);
            decision.getReasons().add("Confidence below threshold: " + confidence);
            return decision;
        }
        
        String targetId = selectTarget(signal, strategy, personality);
        if (strategy == EvolutionStrategy.REPAIR || strategy == EvolutionStrategy.OPTIMIZE) {
            decision.setTargetSkillId(targetId);
        }
        
        decision.getReasons().addAll(generateReasons(signal, strategy, personality, cbReport));
        
        log.info("Evolution decision: {} for signal {} in {}", 
            strategy, signal.getSignalId(), brainDomain);
        
        return decision;
    }
    
    @Override
    public List<EvolutionDecision> batchDecide(List<EvolutionSignal> signals) {
        List<EvolutionDecision> decisions = new ArrayList<>();
        
        signals.sort((a, b) -> {
            EvolutionPriority pa = getPriority(a);
            EvolutionPriority pb = getPriority(b);
            return Integer.compare(pa.getLevel(), pb.getLevel());
        });
        
        for (EvolutionSignal signal : signals) {
            decisions.add(decide(signal));
        }
        
        return decisions;
    }
    
    @Override
    public boolean shouldTriggerEvolution(String brainDomain) {
        CircuitBreakerReport report = circuitBreaker.getReport(brainDomain);
        if (report.isTripped()) {
            return false;
        }
        
        int pendingSignals = memoryGraph.getConsecutiveRepairCount(brainDomain);
        return pendingSignals > 0;
    }
    
    @Override
    public EvolutionPriority getPriority(EvolutionSignal signal) {
        if (signal.getType() == EvolutionSignal.SignalType.ERROR) {
            return EvolutionPriority.CRITICAL;
        }
        
        if (signal.getConfidence() >= 0.8) {
            return EvolutionPriority.HIGH;
        }
        
        if (signal.getConfidence() >= 0.5) {
            return EvolutionPriority.MEDIUM;
        }
        
        if (signal.getConfidence() >= 0.3) {
            return EvolutionPriority.LOW;
        }
        
        return EvolutionPriority.DEFERRED;
    }
    
    @Override
    public EvolutionConstraints getDefaultConstraints(String brainDomain) {
        return domainConstraints.getOrDefault(brainDomain, new EvolutionConstraints());
    }
    
    private EvolutionStrategy determineStrategy(EvolutionSignal signal, 
                                                BrainPersonality personality,
                                                EvolutionConstraints constraints,
                                                CircuitBreakerReport cbReport) {
        switch (signal.getCategory()) {
            case REPAIR:
                int repairCount = memoryGraph.getConsecutiveRepairCount(signal.getBrainDomain());
                if (repairCount >= constraints.getMaxRepairAttempts()) {
                    if (personality.shouldForceInnovation()) {
                        return EvolutionStrategy.INNOVATE;
                    }
                    return EvolutionStrategy.ESCALATE;
                }
                return EvolutionStrategy.REPAIR;
                
            case OPTIMIZE:
                if (personality.getRigor() >= 0.8 && cbReport.getConsecutiveFailures() > 2) {
                    return EvolutionStrategy.REPAIR;
                }
                return EvolutionStrategy.OPTIMIZE;
                
            case INNOVATE:
                if (!canInnovate(signal.getBrainDomain(), constraints)) {
                    return EvolutionStrategy.DEFER;
                }
                return EvolutionStrategy.INNOVATE;
                
            default:
                return EvolutionStrategy.SKIP;
        }
    }
    
    private double calculateConfidence(EvolutionSignal signal,
                                       BrainPersonality personality,
                                       EvolutionConstraints constraints,
                                       CircuitBreakerReport cbReport) {
        double baseConfidence = signal.getConfidence();
        
        double personalityModifier = (personality.getRigor() * 0.2) + 
                                     (personality.getRiskTolerance() * 0.1);
        
        double historyModifier = 0;
        if (cbReport.getConsecutiveFailures() > 0) {
            historyModifier = -0.1 * cbReport.getConsecutiveFailures();
        }
        if (cbReport.getConsecutiveRepairs() > 2) {
            historyModifier -= 0.1;
        }
        
        double confidence = baseConfidence + personalityModifier + historyModifier;
        
        return Math.max(0, Math.min(1, confidence));
    }
    
    private String selectTarget(EvolutionSignal signal, 
                                EvolutionStrategy strategy,
                                BrainPersonality personality) {
        EvolutionMemoryGraph.MemoryAdvice advice = memoryGraph.getMemoryAdvice(signal);
        
        if (advice.getPreferredSkillId() != null && 
            !advice.getBannedSkillIds().contains(advice.getPreferredSkillId())) {
            return advice.getPreferredSkillId();
        }
        
        return "auto_" + signal.getCategory().name().toLowerCase();
    }
    
    private boolean canInnovate(String brainDomain, EvolutionConstraints constraints) {
        String today = Instant.now().truncatedTo(ChronoUnit.DAYS).toString();
        String key = brainDomain + "_" + today;
        
        int count = dailyInnovationCount.getOrDefault(key, 0);
        if (count >= constraints.getMaxInnovationsPerDay()) {
            return false;
        }
        
        dailyInnovationCount.put(key, count + 1);
        lastInnovationTime.put(brainDomain, Instant.now());
        return true;
    }
    
    private EvolutionDecision handleTrippedCircuit(EvolutionSignal signal, 
                                                   CircuitBreakerReport cbReport,
                                                   BrainPersonality personality) {
        EvolutionDecision decision = new EvolutionDecision();
        decision.setSignal(signal);
        decision.setPersonality(personality);
        decision.setCircuitBreakerStatus(cbReport);
        
        switch (cbReport.getTripReason()) {
            case REPAIR_LOOP:
                decision.setStrategy(EvolutionStrategy.INNOVATE);
                decision.getReasons().add("Circuit breaker: forcing innovation due to repair loop");
                break;
            case FAILURE_STREAK:
                decision.setStrategy(EvolutionStrategy.ESCALATE);
                decision.getReasons().add("Circuit breaker: escalating due to failure streak");
                break;
            case EMPTY_CYCLE:
            case SATURATION:
                decision.setStrategy(EvolutionStrategy.SKIP);
                decision.getReasons().add("Circuit breaker: skipping due to " + cbReport.getTripReason());
                break;
        }
        
        decision.setConfidence(0.9);
        return decision;
    }
    
    private EvolutionDecision createForcedDecision(EvolutionSignal signal,
                                                   EvolutionSignal.SignalCategory forcedCategory,
                                                   CircuitBreakerReport cbReport) {
        EvolutionDecision decision = new EvolutionDecision();
        decision.setSignal(signal);
        decision.setStrategy(mapCategoryToStrategy(forcedCategory));
        decision.setConfidence(0.95);
        decision.setCircuitBreakerStatus(cbReport);
        decision.getReasons().add("Forced by circuit breaker: " + cbReport.getTripReason());
        return decision;
    }
    
    private EvolutionStrategy mapCategoryToStrategy(EvolutionSignal.SignalCategory category) {
        return switch (category) {
            case REPAIR -> EvolutionStrategy.REPAIR;
            case OPTIMIZE -> EvolutionStrategy.OPTIMIZE;
            case INNOVATE -> EvolutionStrategy.INNOVATE;
        };
    }
    
    private List<String> generateReasons(EvolutionSignal signal,
                                         EvolutionStrategy strategy,
                                         BrainPersonality personality,
                                         CircuitBreakerReport cbReport) {
        List<String> reasons = new ArrayList<>();
        
        reasons.add("Signal type: " + signal.getType());
        reasons.add("Signal category: " + signal.getCategory());
        reasons.add("Strategy: " + strategy);
        reasons.add("Personality: rigor=" + personality.getRigor() + 
                   ", creativity=" + personality.getCreativity());
        
        if (cbReport.needsAttention()) {
            reasons.add("Circuit breaker warning: " + cbReport.getRecommendation());
        }
        
        return reasons;
    }
    
    public void setPersonality(String brainDomain, BrainPersonality personality) {
        personalities.put(brainDomain, personality);
    }
    
    public void setConstraints(String brainDomain, EvolutionConstraints constraints) {
        domainConstraints.put(brainDomain, constraints);
    }
}
