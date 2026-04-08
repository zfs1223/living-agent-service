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
}
