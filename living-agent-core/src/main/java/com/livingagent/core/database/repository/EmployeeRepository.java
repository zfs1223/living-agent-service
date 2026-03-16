package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, String> {

    Optional<EmployeeEntity> findByPhone(String phone);

    Optional<EmployeeEntity> findByEmail(String email);

    Optional<EmployeeEntity> findByOauthProviderAndOauthUserId(String oauthProvider, String oauthUserId);

    List<EmployeeEntity> findByDepartmentId(String departmentId);

    List<EmployeeEntity> findByIdentity(String identity);

    List<EmployeeEntity> findByActiveTrue();

    @Query("SELECT e FROM EmployeeEntity e WHERE e.active = true AND e.departmentId = :deptId")
    List<EmployeeEntity> findActiveByDepartmentId(@Param("deptId") String departmentId);

    @Query("SELECT e FROM EmployeeEntity e WHERE e.active = true AND e.identity IN :identities")
    List<EmployeeEntity> findActiveByIdentityIn(@Param("identities") List<String> identities);

    @Query("SELECT COUNT(e) FROM EmployeeEntity e WHERE e.active = true AND e.departmentId = :deptId")
    int countActiveByDepartmentId(@Param("deptId") String departmentId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
