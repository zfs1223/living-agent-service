package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.FixedEmployeeProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixedEmployeeProfileRepository extends JpaRepository<FixedEmployeeProfileEntity, String> {
    Optional<FixedEmployeeProfileEntity> findByEmployeeId(String employeeId);
}
