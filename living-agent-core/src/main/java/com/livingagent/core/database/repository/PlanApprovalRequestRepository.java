package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.PlanApprovalRequestEntity;
import com.livingagent.core.database.entity.PlanApprovalRequestEntity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanApprovalRequestRepository extends JpaRepository<PlanApprovalRequestEntity, String> {

    Optional<PlanApprovalRequestEntity> findByRequestId(String requestId);

    List<PlanApprovalRequestEntity> findByStatusOrderBySubmittedAtDesc(ApprovalStatus status);

    @Query("SELECT MAX(CAST(SUBSTRING(r.requestId, 6) AS integer)) FROM PlanApprovalRequestEntity r WHERE r.requestId LIKE 'plan_%'")
    Integer findMaxRequestId();

    void deleteByStatus(ApprovalStatus status);
}