package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.FixedEmployeeDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixedEmployeeDefinitionRepository extends JpaRepository<FixedEmployeeDefinitionEntity, String> {
    List<FixedEmployeeDefinitionEntity> findByActiveTrueOrderByCodeAsc();
    List<FixedEmployeeDefinitionEntity> findByDepartmentCodeAndActiveTrueOrderByCodeAsc(String departmentCode);
}
