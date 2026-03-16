package com.livingagent.core.autonomous.incentive;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CreditAccountService {

    int getBalance(String employeeId);

    void credit(String employeeId, int amountCents);

    boolean debit(String employeeId, int amountCents);

    int getTotalEarned(String employeeId);

    double getPerformanceScore(String employeeId);

    List<String> getEmployeesByDepartment(String departmentId);

    List<CreditTransaction> getTransactionHistory(String employeeId, int limit);

    Map<String, Object> getAccountStats(String employeeId);

    record CreditTransaction(
        String transactionId,
        String employeeId,
        String type,
        int amountCents,
        String description,
        String relatedTaskId,
        Instant createdAt
    ) {}
}
