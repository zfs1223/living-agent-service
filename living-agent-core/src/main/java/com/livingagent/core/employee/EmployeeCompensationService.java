package com.livingagent.core.employee;

import java.util.List;
import java.util.Map;

public interface EmployeeCompensationService {

    CompensationPlan definePlan(String departmentId, String employeeType, Map<String, Object> rules);

    void assignPlan(String employeeId, String planId);

    void recordReward(String employeeId, int points, String reason);

    void recordPenalty(String employeeId, int points, String reason);

    int getBalance(String employeeId);

    List<CompensationRecord> getHistory(String employeeId);

    Map<String, Object> summarizeDepartment(String departmentId);

    record CompensationPlan(
            String planId,
            String departmentId,
            String employeeType,
            Map<String, Object> rules
    ) {}

    record CompensationRecord(
            String recordId,
            String employeeId,
            int points,
            String type,
            String reason,
            long timestamp
    ) {}
}
