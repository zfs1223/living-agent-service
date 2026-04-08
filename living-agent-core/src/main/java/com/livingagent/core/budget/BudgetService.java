package com.livingagent.core.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetService {

    BudgetAllocation createAllocation(BudgetCreateRequest request);
    
    Optional<BudgetAllocation> getAllocation(String allocationId);
    
    Optional<BudgetAllocation> getActiveAllocation(String budgetType, String ownerId, String period);
    
    List<BudgetAllocation> getAllocationsByOwner(String ownerId);
    
    List<BudgetAllocation> getActiveAllocations();
    
    BudgetAllocation updateAllocation(String allocationId, long newAmountCents);
    
    BudgetUsageResult useBudget(String allocationId, long amountCents, String description, String entityType, String entityId);
    
    BudgetUsageResult reserveBudget(String allocationId, long amountCents, String description, String entityType, String entityId);
    
    BudgetUsageResult releaseReservation(String allocationId, String transactionId);
    
    BudgetUsageResult confirmReservation(String allocationId, String transactionId);
    
    BudgetSummary getBudgetSummary(String allocationId);
    
    List<BudgetTransaction> getTransactions(String allocationId);
    
    List<BudgetAlert> checkBudgetAlerts();
    
    enum BudgetType {
        LLM_TOKENS,
        API_CALLS,
        COMPUTE_RESOURCES,
        STORAGE,
        NETWORK,
        LICENSES,
        OPERATIONAL,
        MARKETING
    }
    
    enum Period {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
    
    enum OwnerType {
        DEPARTMENT,
        EMPLOYEE,
        PROJECT,
        SYSTEM
    }
    
    enum TransactionType {
        ALLOCATION,
        USAGE,
        RESERVATION,
        RELEASE,
        CONFIRM,
        REFUND,
        ADJUSTMENT
    }
    
    record BudgetCreateRequest(
        BudgetType budgetType,
        String ownerId,
        OwnerType ownerType,
        Period period,
        LocalDate periodStart,
        long allocatedAmountCents,
        double alertThreshold
    ) {}
    
    record BudgetAllocation(
        String allocationId,
        String budgetType,
        String ownerId,
        String ownerType,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        long allocatedAmountCents,
        long usedAmountCents,
        long reservedAmountCents,
        double alertThreshold,
        boolean isActive
    ) {}
    
    record BudgetTransaction(
        String transactionId,
        String allocationId,
        TransactionType transactionType,
        long amountCents,
        String description,
        String relatedEntityType,
        String relatedEntityId
    ) {}
    
    record BudgetUsageResult(
        boolean success,
        String message,
        long remainingAmountCents,
        String transactionId
    ) {}
    
    record BudgetSummary(
        String allocationId,
        long totalAllocated,
        long totalUsed,
        long totalReserved,
        long available,
        double usagePercentage,
        boolean alertTriggered
    ) {}
    
    record BudgetAlert(
        String allocationId,
        String budgetType,
        String ownerId,
        double usagePercentage,
        double threshold,
        String severity,
        String message
    ) {}
}
