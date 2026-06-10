package com.livingagent.core.evolution.executor;

import java.util.List;
import java.util.Map;

public interface EvolutionFeedbackService {

    void record(EvolutionResult result);

    List<EvolutionResult> recent(int limit);

    Map<String, Object> statistics();
}
