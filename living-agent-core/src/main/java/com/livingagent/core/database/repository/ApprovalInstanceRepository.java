package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ApprovalInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstanceEntity, Long> {

    Optional<ApprovalInstanceEntity> findByInstanceId(String instanceId);

    List<ApprovalInstanceEntity> findBySubmitterId(String submitterId);

    List<ApprovalInstanceEntity> findBySubmitterIdAndStatus(String submitterId, String status);

    List<ApprovalInstanceEntity> findByStatusIn(List<String> statuses);

    List<ApprovalInstanceEntity> findByWorkflowId(String workflowId);

    List<ApprovalInstanceEntity> findByBusinessTypeAndBusinessId(String businessType, String businessId);

    /**
     * P18-C: 写入验证 + 状态流转验证
     */
    default ApprovalInstanceEntity saveAndVerify(ApprovalInstanceEntity entity) {
        // 状态流转验证
        String status = entity.getStatus();
        if (status != null) {
            validateStatusTransition(status);
        }
        ApprovalInstanceEntity saved = save(entity);
        if (saved.getInstanceId() != null) {
            Optional<ApprovalInstanceEntity> verified = findByInstanceId(saved.getInstanceId());
            if (verified.isEmpty()) {
                throw new IllegalStateException("ApprovalInstance save verification failed: " + saved.getInstanceId());
            }
        }
        return saved;
    }

    private void validateStatusTransition(String status) {
        if (!java.util.Set.of("PENDING", "IN_PROGRESS", "APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid approval status: " + status);
        }
    }
}
