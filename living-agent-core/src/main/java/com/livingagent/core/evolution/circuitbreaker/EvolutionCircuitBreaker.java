package com.livingagent.core.evolution.circuitbreaker;

import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.signal.EvolutionSignal;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class EvolutionCircuitBreaker {
    
    private final EvolutionMemoryGraph memoryGraph;
    private final Map<String, CircuitState> brainCircuits;
    
    private static final int REPAIR_LOOP_THRESHOLD = 3;
    private static final int FAILURE_STREAK_THRESHOLD = 5;
    private static final int EMPTY_CYCLE_THRESHOLD = 4;
    private static final Duration COOLDOWN_PERIOD = Duration.ofMinutes(30);
    
    public EvolutionCircuitBreaker(EvolutionMemoryGraph memoryGraph) {
        this.memoryGraph = memoryGraph;
        this.brainCircuits = new HashMap<>();
    }
    
    public CircuitState checkCircuit(String brainDomain) {
        CircuitState state = brainCircuits.computeIfAbsent(brainDomain, k -> new CircuitState(brainDomain));
        
        int consecutiveRepairs = memoryGraph.getConsecutiveRepairCount(brainDomain);
        int consecutiveFailures = memoryGraph.getConsecutiveFailureCount(brainDomain);
        
        if (consecutiveRepairs >= REPAIR_LOOP_THRESHOLD) {
            state.setTripReason(CircuitTripReason.REPAIR_LOOP);
            state.setTrippedAt(Instant.now());
            state.setForceInnovation(true);
        }
        
        if (consecutiveFailures >= FAILURE_STREAK_THRESHOLD) {
            state.setTripReason(CircuitTripReason.FAILURE_STREAK);
            state.setTrippedAt(Instant.now());
            state.setForceStrategyChange(true);
        }
        
        if (state.isTripped()) {
            if (state.getTimeSinceTripped().compareTo(COOLDOWN_PERIOD) > 0) {
                state.reset();
            }
        }
        
        return state;
    }
    
    public boolean shouldForceInnovation(String brainDomain) {
        CircuitState state = checkCircuit(brainDomain);
        return state.isForceInnovation();
    }
    
    public boolean shouldChangeStrategy(String brainDomain) {
        CircuitState state = checkCircuit(brainDomain);
        return state.isForceStrategyChange();
    }
    
    public boolean isCircuitOpen(String brainDomain) {
        CircuitState state = checkCircuit(brainDomain);
        return state.isTripped();
    }
    
    public void recordSuccess(String brainDomain) {
        CircuitState state = brainCircuits.get(brainDomain);
        if (state != null) {
            state.recordSuccess();
        }
    }
    
    public void recordFailure(String brainDomain) {
        CircuitState state = brainCircuits.get(brainDomain);
        if (state != null) {
            state.recordFailure();
        }
    }
    
    public void recordEmptyCycle(String brainDomain) {
        CircuitState state = brainCircuits.get(brainDomain);
        if (state != null) {
            state.incrementEmptyCycles();
        }
    }
    
    public EvolutionSignal.SignalCategory getForcedCategory(String brainDomain) {
        CircuitState state = checkCircuit(brainDomain);
        
        if (state.isForceInnovation()) {
            return EvolutionSignal.SignalCategory.INNOVATE;
        }
        
        if (state.isForceStrategyChange()) {
            return EvolutionSignal.SignalCategory.INNOVATE;
        }
        
        return null;
    }
    
    public CircuitBreakerReport getReport(String brainDomain) {
        CircuitState state = checkCircuit(brainDomain);
        int consecutiveRepairs = memoryGraph.getConsecutiveRepairCount(brainDomain);
        int consecutiveFailures = memoryGraph.getConsecutiveFailureCount(brainDomain);
        
        return new CircuitBreakerReport(
            brainDomain,
            state.isTripped(),
            state.getTripReason(),
            consecutiveRepairs,
            consecutiveFailures,
            state.getEmptyCycleCount(),
            state.getTrippedAt()
        );
    }
}
