package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.CompensationAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompensationAccountRepository extends JpaRepository<CompensationAccountEntity, UUID> {

    Optional<CompensationAccountEntity> findByEmployeeId(String employeeId);

    List<CompensationAccountEntity> findByPlanId(String planId);

    List<CompensationAccountEntity> findByStatus(String status);
}
