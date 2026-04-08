package com.livingagent.core.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetTransactionRepository extends JpaRepository<BudgetTransactionEntity, Long> {
    
    Optional<BudgetTransactionEntity> findByTransactionId(String transactionId);
    
    List<BudgetTransactionEntity> findByAllocationIdOrderByCreatedAtDesc(String allocationId);
    
    List<BudgetTransactionEntity> findByAllocationIdAndTransactionType(String allocationId, String transactionType);
    
    List<BudgetTransactionEntity> findByRelatedEntityTypeAndRelatedEntityId(String entityType, String entityId);
}
