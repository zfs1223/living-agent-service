package com.livingagent.core.model.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LlmModelRepository extends JpaRepository<LlmModel, UUID> {
    List<LlmModel> findByEnabledTrue();
    List<LlmModel> findByEnabledFalse();
    List<LlmModel> findByProviderId(String providerId);
    List<LlmModel> findByProviderIdAndEnabledTrue(String providerId);
    boolean existsByProviderIdAndModelName(String providerId, String modelName);
    Optional<LlmModel> findByProviderIdAndModelName(String providerId, String modelName);
}
