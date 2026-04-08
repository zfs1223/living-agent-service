package com.livingagent.core.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigVersionRepository extends JpaRepository<ConfigVersionEntity, String> {
    
    Optional<ConfigVersionEntity> findByConfigTypeAndConfigKeyAndIsActiveTrue(String configType, String configKey);
    
    Optional<ConfigVersionEntity> findByConfigTypeAndConfigKeyAndVersionNumber(String configType, String configKey, int versionNumber);
    
    List<ConfigVersionEntity> findByConfigTypeAndConfigKeyOrderByVersionNumberDesc(String configType, String configKey);
    
    List<ConfigVersionEntity> findByConfigTypeAndIsActiveTrue(String configType);
    
    @Query("SELECT MAX(c.versionNumber) FROM ConfigVersionEntity c WHERE c.configType = ?1 AND c.configKey = ?2")
    Optional<Integer> findMaxVersionNumber(String configType, String configKey);
    
    void deleteByConfigTypeAndConfigKey(String configType, String configKey);
}
