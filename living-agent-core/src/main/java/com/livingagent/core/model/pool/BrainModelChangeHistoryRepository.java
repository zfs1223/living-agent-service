package com.livingagent.core.model.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrainModelChangeHistoryRepository extends JpaRepository<BrainModelChangeHistory, UUID> {
    
    List<BrainModelChangeHistory> findByBrainIdOrderByCreatedAtDesc(String brainId);
    
    List<BrainModelChangeHistory> findByBrainIdAndSource(String brainId, String source);
    
    List<BrainModelChangeHistory> findByBrainIdAndSourceOrderByCreatedAtDesc(String brainId, String source);
}
