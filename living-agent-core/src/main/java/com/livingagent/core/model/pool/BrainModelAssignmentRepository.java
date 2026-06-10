package com.livingagent.core.model.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrainModelAssignmentRepository extends JpaRepository<BrainModelAssignment, UUID> {
    Optional<BrainModelAssignment> findByBrainId(String brainId);
}
