package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.CompensationPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompensationPlanRepository extends JpaRepository<CompensationPlanEntity, UUID> {

    Optional<CompensationPlanEntity> findByPlanId(String planId);

    List<CompensationPlanEntity> findByDepartmentId(String departmentId);

    List<CompensationPlanEntity> findByEmployeeType(String employeeType);
}
