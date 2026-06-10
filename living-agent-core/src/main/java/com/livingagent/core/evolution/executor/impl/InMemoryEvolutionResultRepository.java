package com.livingagent.core.evolution.executor.impl;

import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.executor.EvolutionResultRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation for testing only.
 * Production uses {@link JpaEvolutionResultRepositoryAdapter} via @Primary.
 */
public class InMemoryEvolutionResultRepository implements EvolutionResultRepository {

    private final Map<String, EvolutionResult> store = new ConcurrentHashMap<>();

    @Override
    public EvolutionResult save(EvolutionResult result) {
        if (result == null) {
            return null;
        }
        if (result.getResultId() == null || result.getResultId().isBlank()) {
            result.setResultId("evo_" + System.currentTimeMillis());
        }
        store.put(result.getResultId(), result);
        return result;
    }

    @Override
    public Optional<EvolutionResult> findById(String resultId) {
        return Optional.ofNullable(store.get(resultId));
    }

    @Override
    public List<EvolutionResult> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparingLong(EvolutionResult::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<EvolutionResult> findByStatus(EvolutionResult.Status status) {
        return store.values().stream()
                .filter(r -> r.getStatus() == status)
                .sorted(Comparator.comparingLong(EvolutionResult::getTimestamp).reversed())
                .toList();
    }
}
