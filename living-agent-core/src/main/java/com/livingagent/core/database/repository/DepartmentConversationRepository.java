package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DepartmentConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentConversationRepository extends JpaRepository<DepartmentConversationEntity, String> {

    Optional<DepartmentConversationEntity> findByConversationId(String conversationId);

    List<DepartmentConversationEntity> findByOwnerUserIdAndDepartmentCodeAndStatusInOrderByLastActivityAtDesc(
        String ownerUserId, String departmentCode, List<String> statuses);

    List<DepartmentConversationEntity> findByOwnerUserIdAndStatusInOrderByLastActivityAtDesc(
        String ownerUserId, List<String> statuses);

    List<DepartmentConversationEntity> findByDepartmentCodeAndStatusInOrderByLastActivityAtDesc(
        String departmentCode, List<String> statuses);

    List<DepartmentConversationEntity> findByTenantIdAndStatusInOrderByLastActivityAtDesc(
        String tenantId, List<String> statuses);

    Optional<DepartmentConversationEntity> findByOwnerUserIdAndDepartmentCodeAndStatus(
        String ownerUserId, String departmentCode, String status);

    long countByOwnerUserIdAndDepartmentCodeAndStatusIn(
        String ownerUserId, String departmentCode, List<String> statuses);

    List<DepartmentConversationEntity> findByStatusAndLastActivityAtBefore(String status, Instant before);
}
