package com.livingagent.core.model.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, String> {
    List<ProviderConfig> findByEnabledTrue();
    boolean existsById(String id);
}
