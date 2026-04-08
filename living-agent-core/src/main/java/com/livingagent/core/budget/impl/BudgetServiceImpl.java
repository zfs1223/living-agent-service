package com.livingagent.core.budget.impl;

import com.livingagent.core.budget.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BudgetServiceImpl implements BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetServiceImpl.class);

    private final BudgetAllocationRepository allocationRepository;
    private final BudgetTransactionRepository transactionRepository;

    public BudgetServiceImpl(BudgetAllocationRepository allocationRepository, 
                            BudgetTransactionRepository transactionRepository) {
        this.allocationRepository = allocationRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public BudgetAllocation createAllocation(BudgetCreateRequest request) {
        String allocationId = "budget_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        
        LocalDate periodEnd = calculatePeriodEnd(request.period(), request.periodStart());
        
        BudgetAllocationEntity entity = new BudgetAllocationEntity();
        entity.setAllocationId(allocationId);
        entity.setBudgetType(request.budgetType().name());
        entity.setOwnerId(request.ownerId());
        entity.setOwnerType(request.ownerType() != null ? request.ownerType().name() : OwnerType.DEPARTMENT.name());
        entity.setPeriod(request.period().name());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(periodEnd);
        entity.setAllocatedAmountCents(request.allocatedAmountCents());
        entity.setUsedAmountCents(0);
        entity.setReservedAmountCents(0);
        entity.setAlertThreshold(request.alertThreshold() > 0 ? request.alertThreshold() : 0.8);
        entity.setActive(true);
        
        allocationRepository.save(entity);
        
        createTransaction(allocationId, TransactionType.ALLOCATION, request.allocatedAmountCents(), 
                "Initial allocation", null, null);
        
        log.info("Created budget allocation: {} for {}/{}", allocationId, request.budgetType(), request.ownerId());
        
        return toBudgetAllocation(entity);
    }

    @Override
    public Optional<BudgetAllocation> getAllocation(String allocationId) {
        return allocationRepository.findByAllocationId(allocationId)
                .map(this::toBudgetAllocation);
    }

    @Override
    public Optional<BudgetAllocation> getActiveAllocation(String budgetType, String ownerId, String period) {
        return allocationRepository.findByBudgetTypeAndOwnerIdAndPeriodAndIsActiveTrue(budgetType, ownerId, period)
                .map(this::toBudgetAllocation);
    }

    @Override
    public List<BudgetAllocation> getAllocationsByOwner(String ownerId) {
        return allocationRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toBudgetAllocation)
                .collect(Collectors.toList());
    }

    @Override
    public List<BudgetAllocation> getActiveAllocations() {
        return allocationRepository.findByIsActiveTrue()
                .stream()
                .map(this::toBudgetAllocation)
                .collect(Collectors.toList());
    }

    @Override
    public BudgetAllocation updateAllocation(String allocationId, long newAmountCents) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        long oldAmount = entity.getAllocatedAmountCents();
        entity.setAllocatedAmountCents(newAmountCents);
        allocationRepository.save(entity);
        
        createTransaction(allocationId, TransactionType.ADJUSTMENT, newAmountCents - oldAmount,
                "Budget adjustment from " + oldAmount + " to " + newAmountCents, null, null);
        
        log.info("Updated budget allocation: {} from {} to {}", allocationId, oldAmount, newAmountCents);
        
        return toBudgetAllocation(entity);
    }

    @Override
    public BudgetUsageResult useBudget(String allocationId, long amountCents, String description, 
                                       String entityType, String entityId) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        long available = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        if (available < amountCents) {
            return new BudgetUsageResult(false, "Insufficient budget. Available: " + available, available, null);
        }
        
        entity.setUsedAmountCents(entity.getUsedAmountCents() + amountCents);
        allocationRepository.save(entity);
        
        String transactionId = createTransaction(allocationId, TransactionType.USAGE, amountCents, 
                description, entityType, entityId);
        
        long remaining = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        log.info("Used budget: {} amount: {} remaining: {}", allocationId, amountCents, remaining);
        
        return new BudgetUsageResult(true, "Budget used successfully", remaining, transactionId);
    }

    @Override
    public BudgetUsageResult reserveBudget(String allocationId, long amountCents, String description,
                                           String entityType, String entityId) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        long available = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        if (available < amountCents) {
            return new BudgetUsageResult(false, "Insufficient budget for reservation. Available: " + available, available, null);
        }
        
        entity.setReservedAmountCents(entity.getReservedAmountCents() + amountCents);
        allocationRepository.save(entity);
        
        String transactionId = createTransaction(allocationId, TransactionType.RESERVATION, amountCents, 
                description, entityType, entityId);
        
        long remaining = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        log.info("Reserved budget: {} amount: {} remaining: {}", allocationId, amountCents, remaining);
        
        return new BudgetUsageResult(true, "Budget reserved successfully", remaining, transactionId);
    }

    @Override
    public BudgetUsageResult releaseReservation(String allocationId, String transactionId) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        BudgetTransactionEntity reservation = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        if (!reservation.getTransactionType().equals(TransactionType.RESERVATION.name())) {
            return new BudgetUsageResult(false, "Transaction is not a reservation", 0, null);
        }
        
        long amount = reservation.getAmountCents();
        entity.setReservedAmountCents(entity.getReservedAmountCents() - amount);
        allocationRepository.save(entity);
        
        createTransaction(allocationId, TransactionType.RELEASE, amount, 
                "Released reservation: " + transactionId, null, null);
        
        long remaining = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        log.info("Released reservation: {} amount: {}", transactionId, amount);
        
        return new BudgetUsageResult(true, "Reservation released", remaining, null);
    }

    @Override
    public BudgetUsageResult confirmReservation(String allocationId, String transactionId) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        BudgetTransactionEntity reservation = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        if (!reservation.getTransactionType().equals(TransactionType.RESERVATION.name())) {
            return new BudgetUsageResult(false, "Transaction is not a reservation", 0, null);
        }
        
        long amount = reservation.getAmountCents();
        entity.setReservedAmountCents(entity.getReservedAmountCents() - amount);
        entity.setUsedAmountCents(entity.getUsedAmountCents() + amount);
        allocationRepository.save(entity);
        
        createTransaction(allocationId, TransactionType.CONFIRM, amount, 
                "Confirmed reservation: " + transactionId, 
                reservation.getRelatedEntityType(), reservation.getRelatedEntityId());
        
        long remaining = entity.getAllocatedAmountCents() - entity.getUsedAmountCents() - entity.getReservedAmountCents();
        
        log.info("Confirmed reservation: {} amount: {}", transactionId, amount);
        
        return new BudgetUsageResult(true, "Reservation confirmed", remaining, null);
    }

    @Override
    public BudgetSummary getBudgetSummary(String allocationId) {
        BudgetAllocationEntity entity = allocationRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));
        
        long totalAllocated = entity.getAllocatedAmountCents();
        long totalUsed = entity.getUsedAmountCents();
        long totalReserved = entity.getReservedAmountCents();
        long available = totalAllocated - totalUsed - totalReserved;
        
        double usagePercentage = totalAllocated > 0 ? (double)(totalUsed + totalReserved) / totalAllocated : 0;
        boolean alertTriggered = usagePercentage >= entity.getAlertThreshold();
        
        return new BudgetSummary(allocationId, totalAllocated, totalUsed, totalReserved, 
                available, usagePercentage, alertTriggered);
    }

    @Override
    public List<BudgetTransaction> getTransactions(String allocationId) {
        return transactionRepository.findByAllocationIdOrderByCreatedAtDesc(allocationId)
                .stream()
                .map(this::toBudgetTransaction)
                .collect(Collectors.toList());
    }

    @Override
    @Scheduled(fixedRate = 300000)
    public List<BudgetAlert> checkBudgetAlerts() {
        List<BudgetAlert> alerts = new ArrayList<>();
        
        allocationRepository.findByIsActiveTrue().forEach(entity -> {
            double usagePercentage = entity.getAllocatedAmountCents() > 0 
                    ? (double)(entity.getUsedAmountCents() + entity.getReservedAmountCents()) / entity.getAllocatedAmountCents() 
                    : 0;
            
            if (usagePercentage >= entity.getAlertThreshold()) {
                String severity = usagePercentage >= 0.95 ? "CRITICAL" : 
                                 usagePercentage >= 0.9 ? "WARNING" : "INFO";
                
                alerts.add(new BudgetAlert(
                    entity.getAllocationId(),
                    entity.getBudgetType(),
                    entity.getOwnerId(),
                    usagePercentage,
                    entity.getAlertThreshold(),
                    severity,
                    String.format("Budget %s for %s is at %.1f%% usage (threshold: %.1f%%)",
                            entity.getBudgetType(), entity.getOwnerId(), usagePercentage * 100, entity.getAlertThreshold() * 100)
                ));
            }
        });
        
        if (!alerts.isEmpty()) {
            log.warn("Budget alerts triggered: {} alerts", alerts.size());
        }
        
        return alerts;
    }

    private LocalDate calculatePeriodEnd(Period period, LocalDate periodStart) {
        return switch (period) {
            case DAILY -> periodStart.plusDays(1);
            case WEEKLY -> periodStart.plusWeeks(1);
            case MONTHLY -> periodStart.plusMonths(1);
            case QUARTERLY -> periodStart.plusMonths(3);
            case YEARLY -> periodStart.plusYears(1);
        };
    }

    private String createTransaction(String allocationId, TransactionType type, long amountCents,
                                     String description, String entityType, String entityId) {
        String transactionId = "btx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        
        BudgetTransactionEntity entity = new BudgetTransactionEntity();
        entity.setTransactionId(transactionId);
        entity.setAllocationId(allocationId);
        entity.setTransactionType(type.name());
        entity.setAmountCents(amountCents);
        entity.setDescription(description);
        entity.setRelatedEntityType(entityType);
        entity.setRelatedEntityId(entityId);
        
        transactionRepository.save(entity);
        
        return transactionId;
    }

    private BudgetAllocation toBudgetAllocation(BudgetAllocationEntity entity) {
        return new BudgetAllocation(
            entity.getAllocationId(),
            entity.getBudgetType(),
            entity.getOwnerId(),
            entity.getOwnerType(),
            entity.getPeriod(),
            entity.getPeriodStart(),
            entity.getPeriodEnd(),
            entity.getAllocatedAmountCents(),
            entity.getUsedAmountCents(),
            entity.getReservedAmountCents(),
            entity.getAlertThreshold(),
            entity.isActive()
        );
    }

    private BudgetTransaction toBudgetTransaction(BudgetTransactionEntity entity) {
        return new BudgetTransaction(
            entity.getTransactionId(),
            entity.getAllocationId(),
            TransactionType.valueOf(entity.getTransactionType()),
            entity.getAmountCents(),
            entity.getDescription(),
            entity.getRelatedEntityType(),
            entity.getRelatedEntityId()
        );
    }
}
