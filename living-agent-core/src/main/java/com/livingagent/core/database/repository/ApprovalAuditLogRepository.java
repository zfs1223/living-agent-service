package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ApprovalAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLogEntity, Long> {

    List<ApprovalAuditLogEntity> findByInstanceIdOrderByCreatedAtDesc(String instanceId);

    List<ApprovalAuditLogEntity> findByOperatorIdOrderByCreatedAtDesc(String operatorId);

    List<ApprovalAuditLogEntity> findByActionOrderByCreatedAtDesc(String action);
}
