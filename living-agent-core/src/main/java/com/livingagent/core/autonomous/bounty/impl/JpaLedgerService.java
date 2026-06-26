package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.LedgerService;
import com.livingagent.core.database.entity.LedgerTransactionEntity;
import com.livingagent.core.database.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LedgerService 的 JPA 持久化实现（P0-1 修复）
 * 
 * 将员工余额、收入记录、奖励记录写入数据库，
 * 实现重启不丢失数据。
 */
public class JpaLedgerService implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(JpaLedgerService.class);

    private final LedgerTransactionRepository repository;

    public JpaLedgerService(LedgerTransactionRepository repository) {
        this.repository = repository;
        log.info("JpaLedgerService initialized - persistence enabled");
    }

    @Override
    @Transactional
    public void recordIncome(String employeeId, String sourceType, String sourceId, 
                            int amountCents, String description) {
        int currentBalance = getBalance(employeeId);
        int newBalance = currentBalance + amountCents;
        
        String transactionId = "inc_" + UUID.randomUUID().toString().substring(0, 12);
        
        LedgerTransactionEntity entity = new LedgerTransactionEntity(
            transactionId,
            employeeId,
            sourceType,
            sourceId,
            amountCents,
            newBalance,
            "RECEIVED",
            description
        );
        
        repository.save(entity);
        log.debug("Recorded income for {}: {} cents (balance: {} -> {})", 
            employeeId, amountCents, currentBalance, newBalance);
    }

    @Override
    @Transactional
    public void recordPotentialIncome(String employeeId, String sourceType, String sourceId, 
                                      int amountCents) {
        // 潜在收入不计入余额，仅记录 PENDING 状态
        String transactionId = "pending_" + UUID.randomUUID().toString().substring(0, 12);
        
        LedgerTransactionEntity entity = new LedgerTransactionEntity(
            transactionId,
            employeeId,
            sourceType,
            sourceId,
            amountCents,
            getBalance(employeeId),  // 余额不变
            "PENDING",
            "Potential income pending confirmation"
        );
        
        repository.save(entity);
        log.debug("Recorded potential income for {}: {} cents (pending)", employeeId, amountCents);
    }

    @Override
    @Transactional
    public void recordReward(String employeeId, int credits, String reason) {
        int currentBalance = getBalance(employeeId);
        int newBalance = currentBalance + credits;
        
        String transactionId = "reward_" + UUID.randomUUID().toString().substring(0, 12);
        
        LedgerTransactionEntity entity = new LedgerTransactionEntity(
            transactionId,
            employeeId,
            "REWARD",
            null,
            credits,
            newBalance,
            "RECEIVED",
            reason
        );
        
        repository.save(entity);
        log.debug("Recorded reward for {}: {} credits (balance: {} -> {})", 
            employeeId, credits, currentBalance, newBalance);
    }

    @Override
    public int getBalance(String employeeId) {
        return repository.findLatestBalance(employeeId).orElse(0);
    }

    @Override
    public int getTotalEarned(String employeeId) {
        Integer total = repository.getTotalEarned(employeeId);
        return total != null ? total : 0;
    }

    @Override
    public List<IncomeRecord> getIncomeHistory(String employeeId, int limit) {
        List<LedgerTransactionEntity> entities = repository.findIncomeHistory(employeeId, limit);
        return entities.stream()
            .map(LedgerTransactionEntity::toIncomeRecord)
            .toList();
    }

    /**
     * 确认潜在收入（将 PENDING 状态改为 RECEIVED）
     */
    @Transactional
    public void confirmPendingIncome(String transactionId) {
        repository.findByTransactionId(transactionId).ifPresent(entity -> {
            if ("PENDING".equals(entity.getStatus())) {
                entity.setStatus("RECEIVED");
                // 重新计算余额
                int newBalance = getBalance(entity.getEmployeeId()) + entity.getAmountCents();
                entity.setBalanceAfter(newBalance);
                repository.save(entity);
                log.info("Confirmed pending income {}: {} cents", transactionId, entity.getAmountCents());
            }
        });
    }
}