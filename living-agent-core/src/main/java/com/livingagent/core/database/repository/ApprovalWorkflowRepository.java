package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ApprovalWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflowEntity, Long> {

    Optional<ApprovalWorkflowEntity> findByWorkflowId(String workflowId);

    List<ApprovalWorkflowEntity> findByEnabledTrue();

    boolean existsByWorkflowId(String workflowId);
}
