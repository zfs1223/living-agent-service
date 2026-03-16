package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, String> {

    Optional<DepartmentEntity> findByCode(String code);

    Optional<DepartmentEntity> findByName(String name);

    List<DepartmentEntity> findByParentId(String parentId);

    List<DepartmentEntity> findByParentIdIsNull();

    Optional<DepartmentEntity> findByTargetBrain(String targetBrain);

    @Query("SELECT d FROM DepartmentEntity d WHERE d.memberCount > 0 ORDER BY d.memberCount DESC")
    List<DepartmentEntity> findDepartmentsWithMembers();

    @Query("SELECT d FROM DepartmentEntity d WHERE d.targetBrain = :brainName")
    Optional<DepartmentEntity> findByBrainName(@Param("brainName") String brainName);

    @Query("SELECT d.targetBrain FROM DepartmentEntity d WHERE d.departmentId = :deptId")
    Optional<String> findTargetBrainByDepartmentId(@Param("deptId") String departmentId);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
