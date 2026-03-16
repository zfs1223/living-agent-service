package com.livingagent.core.evolution.memory.impl;

import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.memory.EvolutionEvent;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEvolutionMemoryGraph implements EvolutionMemoryGraph {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryEvolutionMemoryGraph.class);
    
    private final Map<String, List<EvolutionSignal>> brainSignals = new ConcurrentHashMap<>();
    private final Map<String, List<EvolutionEvent>> brainEvents = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveRepairs = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSignalTime = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<Boolean>>> skillOutcomes = new ConcurrentHashMap<>();
    
    @Override
    public void recordSignal(EvolutionSignal signal) {
        String brainDomain = signal.getBrainDomain();
        brainSignals.computeIfAbsent(brainDomain, k -> new ArrayList<>()).add(signal);
        lastSignalTime.put(brainDomain, Instant.now());
        
        if (signal.getType() == EvolutionSignal.SignalType.ERROR) {
            consecutiveFailures.merge(brainDomain, 1, Integer::sum);
            consecutiveRepairs.put(brainDomain, 0);
        } else if (signal.getType() == EvolutionSignal.SignalType.PERFORMANCE) {
            consecutiveRepairs.merge(brainDomain, 1, Integer::sum);
            consecutiveFailures.put(brainDomain, 0);
        }
        
        log.debug("Recorded signal for brain {}: type={}", brainDomain, signal.getType());
    }
    
    @Override
    public String recordHypothesis(EvolutionSignal signal, String skillId, String mutationCategory) {
        String hypothesisId = "hypothesis-" + UUID.randomUUID().toString().substring(0, 8);
        
        EvolutionEvent event = new EvolutionEvent();
        event.setEventId(hypothesisId);
        event.setKind(EvolutionEvent.EventType.HYPOTHESIS);
        event.setBrainDomain(signal.getBrainDomain());
        event.setTimestamp(Instant.now());
        
        EvolutionEvent.SignalSnapshot signalSnapshot = new EvolutionEvent.SignalSnapshot();
        signalSnapshot.setKey(signal.getSignalId());
        event.setSignal(signalSnapshot);
        
        EvolutionEvent.EvolutionAction action = new EvolutionEvent.EvolutionAction();
        action.setSkillId(skillId);
        action.setMutationCategory(mutationCategory);
        event.setAction(action);
        
        brainEvents.computeIfAbsent(signal.getBrainDomain(), k -> new ArrayList<>()).add(event);
        
        log.info("Recorded hypothesis {} for skill {} in category {}", hypothesisId, skillId, mutationCategory);
        return hypothesisId;
    }
    
    @Override
    public String recordAttempt(EvolutionSignal signal, String hypothesisId, String skillId) {
        String attemptId = "attempt-" + UUID.randomUUID().toString().substring(0, 8);
        
        EvolutionEvent event = new EvolutionEvent();
        event.setEventId(attemptId);
        event.setKind(EvolutionEvent.EventType.ATTEMPT);
        event.setBrainDomain(signal.getBrainDomain());
        event.setTimestamp(Instant.now());
        
        EvolutionEvent.SignalSnapshot signalSnapshot = new EvolutionEvent.SignalSnapshot();
        signalSnapshot.setKey(signal.getSignalId());
        event.setSignal(signalSnapshot);
        
        EvolutionEvent.EvolutionAction action = new EvolutionEvent.EvolutionAction();
        action.setSkillId(skillId);
        event.setAction(action);
        
        EvolutionEvent.Hypothesis hypothesis = new EvolutionEvent.Hypothesis();
        hypothesis.setHypothesisId(hypothesisId);
        event.setHypothesis(hypothesis);
        
        brainEvents.computeIfAbsent(signal.getBrainDomain(), k -> new ArrayList<>()).add(event);
        
        log.info("Recorded attempt {} for hypothesis {} skill {}", attemptId, hypothesisId, skillId);
        return attemptId;
    }
    
    @Override
    public void recordOutcome(String attemptId, String status, double score, String note) {
        log.info("Recorded outcome for attempt {}: status={}, score={}", attemptId, status, score);
    }
    
    @Override
    public List<EvolutionEvent> getRecentEvents(String brainDomain, int limit) {
        List<EvolutionEvent> events = brainEvents.getOrDefault(brainDomain, List.of());
        int start = Math.max(0, events.size() - limit);
        return new ArrayList<>(events.subList(start, events.size()));
    }
    
    @Override
    public List<EvolutionEvent> getEventsBySignal(String signalKey) {
        List<EvolutionEvent> result = new ArrayList<>();
        for (List<EvolutionEvent> events : brainEvents.values()) {
            for (EvolutionEvent event : events) {
                if (event.getSignal() != null && signalKey.equals(event.getSignal().getKey())) {
                    result.add(event);
                }
            }
        }
        return result;
    }
    
    @Override
    public Optional<EvolutionEvent> getLastOutcome(String brainDomain) {
        List<EvolutionEvent> events = brainEvents.get(brainDomain);
        if (events != null && !events.isEmpty()) {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i).getKind() == EvolutionEvent.EventType.OUTCOME) {
                    return Optional.of(events.get(i));
                }
            }
        }
        return Optional.empty();
    }
    
    @Override
    public MemoryAdvice getMemoryAdvice(EvolutionSignal signal) {
        MemoryAdvice advice = new MemoryAdvice();
        String brainDomain = signal.getBrainDomain();
        
        if (hasRepairLoop(brainDomain, 3)) {
            advice.addExplanation("Repair loop detected, consider alternative strategies");
        }
        
        Map<String, Double> successRates = getSkillSuccessRates(brainDomain);
        String bestSkill = successRates.entrySet().stream()
            .filter(e -> e.getValue() > 0.7)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        if (bestSkill != null) {
            advice.setPreferredSkillId(bestSkill);
            advice.setConfidence(successRates.get(bestSkill));
        }
        
        return advice;
    }
    
    @Override
    public Map<String, Double> getSkillSuccessRates(String brainDomain) {
        Map<String, Double> result = new HashMap<>();
        Map<String, List<Boolean>> outcomes = skillOutcomes.getOrDefault(brainDomain, Map.of());
        
        for (Map.Entry<String, List<Boolean>> entry : outcomes.entrySet()) {
            List<Boolean> skillOutcomesList = entry.getValue();
            if (!skillOutcomesList.isEmpty()) {
                long successes = skillOutcomesList.stream().filter(b -> b).count();
                result.put(entry.getKey(), (double) successes / skillOutcomesList.size());
            }
        }
        
        return result;
    }
    
    @Override
    public int getConsecutiveRepairCount(String brainDomain) {
        return consecutiveRepairs.getOrDefault(brainDomain, 0);
    }
    
    @Override
    public int getConsecutiveFailureCount(String brainDomain) {
        return consecutiveFailures.getOrDefault(brainDomain, 0);
    }
    
    @Override
    public boolean hasRepairLoop(String brainDomain, int threshold) {
        return getConsecutiveRepairCount(brainDomain) >= threshold;
    }
    
    @Override
    public void cleanupOldEvents(int daysToKeep) {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(daysToKeep));
        
        for (String brainDomain : brainEvents.keySet()) {
            List<EvolutionEvent> events = brainEvents.get(brainDomain);
            if (events != null) {
                events.removeIf(event -> event.getTimestamp().isBefore(cutoff));
            }
        }
        
        log.info("Cleaned up events older than {} days", daysToKeep);
    }
}
