package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.InvitationCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 邀请码 Repository（INVITATION_CODE_IMPROVEMENT_PLAN.md §3.2）
 */
@Repository
public interface InvitationCodeRepository extends JpaRepository<InvitationCodeEntity, Long> {

    Optional<InvitationCodeEntity> findByCode(String code);

    List<InvitationCodeEntity> findByTenantId(String tenantId);

    List<InvitationCodeEntity> findByCompanyId(String companyId);

    List<InvitationCodeEntity> findByDepartmentCode(String departmentCode);

    List<InvitationCodeEntity> findByStatus(String status);

    List<InvitationCodeEntity> findByCreatedBy(String createdBy);

    Optional<InvitationCodeEntity> findByPhoneHash(String phoneHash);

    @Query("SELECT i FROM InvitationCodeEntity i WHERE i.status = 'PENDING' AND i.expiresAt IS NOT NULL AND i.expiresAt < :now")
    List<InvitationCodeEntity> findExpiredPendingCodes(@Param("now") Instant now);

    @Query("SELECT i FROM InvitationCodeEntity i WHERE i.status = :status ORDER BY i.createdAt DESC")
    List<InvitationCodeEntity> findByStatusOrderByCreatedAtDesc(@Param("status") String status);

    @Query("SELECT COUNT(i) FROM InvitationCodeEntity i WHERE i.tenantId = :tenantId")
    long countByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(i) FROM InvitationCodeEntity i WHERE i.tenantId = :tenantId AND i.status = 'USED'")
    long countUsedByTenantId(@Param("tenantId") String tenantId);

    boolean existsByCode(String code);
}
