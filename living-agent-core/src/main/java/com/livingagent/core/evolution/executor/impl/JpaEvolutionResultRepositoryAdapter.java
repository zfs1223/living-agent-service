package com.livingagent.core.evolution.executor.impl;

import com.livingagent.core.database.entity.EvolutionResultEntity;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.executor.EvolutionResultRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Primary
public class JpaEvolutionResultRepositoryAdapter implements EvolutionResultRepository {

    private final com.livingagent.core.database.repository.EvolutionResultRepository repository;

    public JpaEvolutionResultRepositoryAdapter(com.livingagent.core.database.repository.EvolutionResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public EvolutionResult save(EvolutionResult result) {
        EvolutionResultEntity entity = EvolutionResultEntity.fromDomain(result);
        if (entity.getResultId() == null || entity.getResultId().isBlank()) {
            entity.setResultId("evo_" + System.currentTimeMillis());
        }
        EvolutionResultEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<EvolutionResult> findById(String resultId) {
        return repository.findByResultId(resultId).map(EvolutionResultEntity::toDomain);
    }

    @Override
    public List<EvolutionResult> findRecent(int limit) {
        return repository.findTop50ByOrderByTimestampDesc().stream()
                .limit(limit)
                .map(EvolutionResultEntity::toDomain)
                .toList();
    }

    @Override
    public List<EvolutionResult> findByStatus(EvolutionResult.Status status) {
        return repository.findByStatusOrderByTimestampDesc(status.name()).stream()
                .map(EvolutionResultEntity::toDomain)
                .toList();
    }
}
