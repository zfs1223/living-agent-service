package com.livingagent.core.evolution;

import java.util.List;
import java.util.Map;

public interface CapabilityEvaluator {
    
    EvaluationResult evaluate(String capability);
    
    Map<String, Double> evaluateAll();
    
    List<String> identifyGaps();
    
    List<ImprovementSuggestion> suggestImprovements();
    
    double getOverallScore();
    
    void recordPerformance(String capability, boolean success, long durationMs);
}
