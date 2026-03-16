package com.livingagent.core.autonomous.bounty;

import java.time.Instant;
import java.util.List;

public interface LedgerService {

    void recordIncome(String employeeId, String sourceType, String sourceId, 
                     int amountCents, String description);

    void recordPotentialIncome(String employeeId, String sourceType, String sourceId, 
                              int amountCents);

    void recordReward(String employeeId, int credits, String reason);

    int getBalance(String employeeId);

    int getTotalEarned(String employeeId);

    List<IncomeRecord> getIncomeHistory(String employeeId, int limit);

    record IncomeRecord(
        String incomeId,
        String employeeId,
        String sourceType,
        String sourceId,
        int amountCents,
        String status,
        Instant createdAt,
        Instant receivedAt
    ) {}
}
