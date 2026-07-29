package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnterpriseEmployeeRepository extends JpaRepository<EnterpriseEmployeeEntity, String> {

    Optional<EnterpriseEmployeeEntity> findByPhone(String phone);

    /** 同一手机号在多个租户中存在记录 */
    List<EnterpriseEmployeeEntity> findAllByPhone(String phone);

    Optional<EnterpriseEmployeeEntity> findByEmail(String email);

    Optional<EnterpriseEmployeeEntity> findByOauthProviderAndOauthUserId(String oauthProvider, String oauthUserId);

    List<EnterpriseEmployeeEntity> findByDepartmentId(String departmentId);

    List<EnterpriseEmployeeEntity> findByIdentity(String identity);

    List<EnterpriseEmployeeEntity> findByActiveTrue();

    @Query("SELECT e FROM EnterpriseEmployeeEntity e WHERE e.active = true AND e.departmentId = :deptId")
    List<EnterpriseEmployeeEntity> findActiveByDepartmentId(@Param("deptId") String departmentId);

    @Query("SELECT e FROM EnterpriseEmployeeEntity e WHERE e.active = true AND e.identity IN :identities")
    List<EnterpriseEmployeeEntity> findActiveByIdentityIn(@Param("identities") List<String> identities);

    @Query("SELECT COUNT(e) FROM EnterpriseEmployeeEntity e WHERE e.active = true AND e.departmentId = :deptId")
    int countActiveByDepartmentId(@Param("deptId") String departmentId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM EnterpriseEmployeeEntity e")
    boolean hasAnyEmployee();

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM EnterpriseEmployeeEntity e WHERE e.founder = true")
    boolean hasFounder();

    Optional<EnterpriseEmployeeEntity> findByVoicePrintId(String voicePrintId);

    @Query("SELECT e FROM EnterpriseEmployeeEntity e WHERE e.founder = true")
    Optional<EnterpriseEmployeeEntity> findFounder();

    @Query("SELECT e FROM EnterpriseEmployeeEntity e WHERE e.identity = :identity AND e.active = true")
    List<EnterpriseEmployeeEntity> findActiveByIdentity(@Param("identity") String identity);

    @Query("SELECT e FROM EnterpriseEmployeeEntity e WHERE e.active = true AND e.departmentName = :deptName")
    List<EnterpriseEmployeeEntity> findActiveByDepartmentName(@Param("deptName") String departmentName);

    Optional<EnterpriseEmployeeEntity> findByEmployeeId(String employeeId);

    /**
     * P11-A: 写入验证 + 状态变更验证
     */
    default EnterpriseEmployeeEntity saveAndVerify(EnterpriseEmployeeEntity entity) {
        String status = entity.getStatus();
        if (status != null) {
            validateStatus(status);
        }
        EnterpriseEmployeeEntity saved = save(entity);
        if (saved.getEmployeeId() != null) {
            Optional<EnterpriseEmployeeEntity> verified = findByEmployeeId(saved.getEmployeeId());
            if (verified.isEmpty()) {
                throw new IllegalStateException("Employee save verification failed: " + saved.getEmployeeId());
            }
        }
        return saved;
    }

    private void validateStatus(String status) {
        if (!java.util.Set.of(
            "ACTIVE", "WORKING", "IDLE", "BUSY",
            "OFFLINE", "DORMANT", "DISABLED", "ARCHIVED", "TERMINATED",
            "LEARNING", "EVOLVING"
        ).contains(status)) {
            throw new IllegalArgumentException("Invalid employee status: " + status);
        }
    }
}
