package com.livingagent.core.evolution.executor;

import java.util.List;
import java.util.Optional;

public interface EvolutionResultRepository {

    EvolutionResult save(EvolutionResult result);

    Optional<EvolutionResult> findById(String resultId);

    List<EvolutionResult> findRecent(int limit);

    List<EvolutionResult> findByStatus(EvolutionResult.Status status);
}
