package com.livingagent.core.evolution.signal;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.neuron.NeuronRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface SignalExtractor {
    
    List<EvolutionSignal> extractFromConversation(String conversationId);
    
    List<EvolutionSignal> extractFromLogs(String logContent);
    
    List<EvolutionSignal> extractFromMetrics(Map<String, Object> metrics);
    
    List<EvolutionSignal> extractFromUserFeedback(String feedback);
    
    EvolutionSignal.SignalCategory determineCategory(EvolutionSignal signal);
    
    String determineBrainDomain(EvolutionSignal signal);
}
