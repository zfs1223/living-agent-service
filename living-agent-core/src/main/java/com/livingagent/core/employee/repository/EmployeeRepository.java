package com.livingagent.core.employee.repository;

import com.livingagent.core.employee.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, String> {

    List<EmployeeEntity> findByDepartment(String department);

    List<EmployeeEntity> findByStatus(String status);

    @Query("SELECT e FROM EmployeeEntity e WHERE e.department = :dept AND e.status = :status")
    List<EmployeeEntity> findByDepartmentAndStatus(@Param("dept") String department, @Param("status") String status);

    @Query("SELECT e FROM EmployeeEntity e WHERE TYPE(e) = DigitalEmployeeEntity")
    List<EmployeeEntity> findAllDigitalEmployees();

    @Query("SELECT e FROM EmployeeEntity e WHERE TYPE(e) = HumanEmployeeEntity")
    List<EmployeeEntity> findAllHumanEmployees();

    Optional<EmployeeEntity> findByName(String name);

    boolean existsByName(String name);

    long countByDepartment(String department);

    long countByStatus(String status);
}
