package com.livingagent.core.neuron.evolution;

import com.livingagent.core.evolution.executor.EvolutionExecutor;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EvolutionSignalTrigger {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionSignalTrigger.class);
    
    private final EvolutionExecutor evolutionExecutor;
    private final String neuronId;
    private final String brainDomain;
    
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicInteger consecutiveToolFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveCapabilityGaps = new AtomicInteger(0);
    private final AtomicLong lastEvolutionTime = new AtomicLong(0);
    private final Map<String, AtomicInteger> toolUsageCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> toolFailureCount = new ConcurrentHashMap<>();
    
    private static final int ERROR_THRESHOLD = 3;
    private static final int TOOL_FAILURE_THRESHOLD = 5;
    private static final int CAPABILITY_GAP_THRESHOLD = 2;
    private static final long EVOLUTION_COOLDOWN_MS = 60_000;
    private static final int MAX_EVOLUTIONS_PER_HOUR = 10;
    
    private final AtomicInteger hourlyEvolutionCount = new AtomicInteger(0);
    private final AtomicLong hourStartTime = new AtomicLong(System.currentTimeMillis());
    
    public EvolutionSignalTrigger(EvolutionExecutor evolutionExecutor, String neuronId, String brainDomain) {
        this.evolutionExecutor = evolutionExecutor;
        this.neuronId = neuronId;
        this.brainDomain = brainDomain;
    }
    
    public void recordError(String errorType, String errorMessage) {
        int count = consecutiveErrors.incrementAndGet();
        log.debug("Recorded error for {}: {} (consecutive: {})", neuronId, errorType, count);
        
        if (count >= ERROR_THRESHOLD && canTriggerEvolution()) {
            triggerErrorEvolution(errorType, errorMessage);
        }
    }
    
    public void recordToolFailure(String toolName, String errorMessage) {
        consecutiveToolFailures.incrementAndGet();
        toolFailureCount.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
        
        int failureCount = toolFailureCount.get(toolName).get();
        log.debug("Tool {} failed for {} (failures: {})", toolName, neuronId, failureCount);
        
        if (failureCount >= TOOL_FAILURE_THRESHOLD && canTriggerEvolution()) {
            triggerCapabilityGapEvolution(toolName, errorMessage);
        }
    }
    
    public void recordToolSuccess(String toolName) {
        consecutiveErrors.set(0);
        consecutiveToolFailures.set(0);
        toolUsageCount.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    public void recordCapabilityGap(String missingCapability, String context) {
        int count = consecutiveCapabilityGaps.incrementAndGet();
        log.info("Capability gap detected for {}: {} (consecutive: {})", neuronId, missingCapability, count);
        
        if (count >= CAPABILITY_GAP_THRESHOLD && canTriggerEvolution()) {
            triggerCapabilityGapEvolution(missingCapability, context);
        }
    }
    
    public void recordOpportunity(String opportunity, double confidence) {
        if (canTriggerEvolution() && confidence >= 0.7) {
            triggerOpportunityEvolution(opportunity, confidence);
        }
    }
    
    public void recordPerformanceIssue(String issue, double severity) {
        if (canTriggerEvolution() && severity >= 0.6) {
            triggerPerformanceEvolution(issue, severity);
        }
    }
    
    private boolean canTriggerEvolution() {
        long now = System.currentTimeMillis();
        
        if (now - lastEvolutionTime.get() < EVOLUTION_COOLDOWN_MS) {
            return false;
        }
        
        if (now - hourStartTime.get() > 3600_000) {
            hourlyEvolutionCount.set(0);
            hourStartTime.set(now);
        }
        
        return hourlyEvolutionCount.get() < MAX_EVOLUTIONS_PER_HOUR;
    }
    
    private void triggerErrorEvolution(String errorType, String errorMessage) {
        log.info("Triggering ERROR evolution signal for {}: {} - {}", neuronId, errorType, errorMessage);
        
        EvolutionSignal signal = EvolutionSignal.error(
            null,
            neuronId,
            String.format("Error pattern detected: %s - %s", errorType, errorMessage),
            brainDomain
        );
        signal.setNeuronId(neuronId);
        signal.addMetadata("errorType", errorType);
        signal.addMetadata("consecutiveErrors", consecutiveErrors.get());
        
        executeEvolution(signal);
        consecutiveErrors.set(0);
    }
    
    private void triggerCapabilityGapEvolution(String missingCapability, String context) {
        log.info("Triggering CAPABILITY_GAP evolution signal for {}: {}", neuronId, missingCapability);
        
        EvolutionSignal signal = EvolutionSignal.capabilityGap(
            String.format("Missing capability: %s. Context: %s", missingCapability, context),
            brainDomain
        );
        signal.setNeuronId(neuronId);
        signal.addMetadata("missingCapability", missingCapability);
        signal.addMetadata("context", context);
        
        executeEvolution(signal);
        consecutiveCapabilityGaps.set(0);
    }
    
    private void triggerOpportunityEvolution(String opportunity, double confidence) {
        log.info("Triggering OPPORTUNITY evolution signal for {}: {}", neuronId, opportunity);
        
        EvolutionSignal signal = EvolutionSignal.opportunity(opportunity, brainDomain, confidence);
        signal.setNeuronId(neuronId);
        signal.addMetadata("opportunity", opportunity);
        
        executeEvolution(signal);
    }
    
    private void triggerPerformanceEvolution(String issue, double severity) {
        log.info("Triggering PERFORMANCE evolution signal for {}: {}", neuronId, issue);
        
        EvolutionSignal signal = EvolutionSignal.performance(issue, brainDomain, severity);
        signal.setNeuronId(neuronId);
        signal.addMetadata("issue", issue);
        signal.addMetadata("severity", severity);
        
        executeEvolution(signal);
    }
    
    private void executeEvolution(EvolutionSignal signal) {
        lastEvolutionTime.set(System.currentTimeMillis());
        hourlyEvolutionCount.incrementAndGet();
        
        evolutionExecutor.executeAsync(signal)
            .thenAccept(result -> {
                if (result.getStatus() == EvolutionResult.Status.SUCCESS) {
                    log.info("Evolution succeeded for {}: {} - generated skill: {}", 
                        neuronId, signal.getType(), result.getGeneratedSkillId());
                    if (result.getGeneratedSkillId() != null) {
                        onNewSkillGenerated(result.getGeneratedSkillId());
                    }
                } else if (result.getStatus() == EvolutionResult.Status.FAILED) {
                    log.warn("Evolution failed for {}: {}", neuronId, result.getErrorMessage());
                } else {
                    log.info("Evolution {} for {}: {}", result.getStatus(), neuronId, result.getAction());
                }
            })
            .exceptionally(e -> {
                log.error("Evolution execution error for {}: {}", neuronId, e.getMessage());
                return null;
            });
    }
    
    protected void onNewSkillGenerated(String skillName) {
        log.info("New skill generated for {}: {}", neuronId, skillName);
    }
    
    public Map<String, Object> getStatistics() {
        return Map.of(
            "neuronId", neuronId,
            "brainDomain", brainDomain,
            "consecutiveErrors", consecutiveErrors.get(),
            "consecutiveToolFailures", consecutiveToolFailures.get(),
            "consecutiveCapabilityGaps", consecutiveCapabilityGaps.get(),
            "hourlyEvolutionCount", hourlyEvolutionCount.get(),
            "lastEvolutionTime", lastEvolutionTime.get() > 0 ? Instant.ofEpochMilli(lastEvolutionTime.get()) : null,
            "toolUsageCount", toolUsageCount.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())),
            "toolFailureCount", toolFailureCount.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()))
        );
    }
    
    public void reset() {
        consecutiveErrors.set(0);
        consecutiveToolFailures.set(0);
        consecutiveCapabilityGaps.set(0);
        toolUsageCount.clear();
        toolFailureCount.clear();
    }
}
