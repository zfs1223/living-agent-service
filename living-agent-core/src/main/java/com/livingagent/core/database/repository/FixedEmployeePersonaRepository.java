package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.FixedEmployeePersonaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixedEmployeePersonaRepository extends JpaRepository<FixedEmployeePersonaEntity, String> {
    Optional<FixedEmployeePersonaEntity> findByEmployeeId(String employeeId);
}
