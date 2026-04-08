package com.livingagent.core.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocationEntity, String> {
    
    Optional<BudgetAllocationEntity> findByAllocationId(String allocationId);
    
    Optional<BudgetAllocationEntity> findByBudgetTypeAndOwnerIdAndPeriodAndIsActiveTrue(
        String budgetType, String ownerId, String period);
    
    List<BudgetAllocationEntity> findByOwnerId(String ownerId);
    
    List<BudgetAllocationEntity> findByIsActiveTrue();
    
    List<BudgetAllocationEntity> findByOwnerIdAndIsActiveTrue(String ownerId);
    
    List<BudgetAllocationEntity> findByPeriodEndBeforeAndIsActiveTrue(LocalDate date);
}
