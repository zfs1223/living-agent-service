package com.livingagent.core.evolution.executor.impl;

import com.livingagent.core.evolution.executor.EvolutionFeedbackService;
import com.livingagent.core.evolution.executor.EvolutionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation for testing only.
 * Production uses {@link JpaEvolutionFeedbackService} via @Primary.
 */
public class InMemoryEvolutionFeedbackService implements EvolutionFeedbackService {

    private final List<EvolutionResult> results = new CopyOnWriteArrayList<>();

    @Override
    public void record(EvolutionResult result) {
        if (result != null) {
            results.add(result);
        }
    }

    @Override
    public List<EvolutionResult> recent(int limit) {
        List<EvolutionResult> copy = new ArrayList<>(results);
        copy.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        if (copy.size() > limit) {
            copy = copy.subList(0, limit);
        }
        return copy;
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", results.size());
        stats.put("success", results.stream().filter(EvolutionResult::isSuccess).count());
        stats.put("immediateEffective", results.stream().filter(EvolutionResult::isImmediateEffective).count());
        return stats;
    }
}
