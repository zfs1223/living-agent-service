package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.EmployeeCompensationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation for testing only.
 * Production uses {@link JpaEmployeeCompensationService} via @Primary.
 */
public class InMemoryEmployeeCompensationService implements EmployeeCompensationService {

    private final Map<String, CompensationPlan> plans = new ConcurrentHashMap<>();
    private final Map<String, String> employeePlan = new ConcurrentHashMap<>();
    private final Map<String, List<CompensationRecord>> history = new ConcurrentHashMap<>();
    private final Map<String, Integer> balances = new ConcurrentHashMap<>();

    @Override
    public CompensationPlan definePlan(String departmentId, String employeeType, Map<String, Object> rules) {
        CompensationPlan plan = new CompensationPlan("plan_" + System.currentTimeMillis(), departmentId, employeeType, rules == null ? Map.of() : Map.copyOf(rules));
        plans.put(plan.planId(), plan);
        return plan;
    }

    @Override
    public void assignPlan(String employeeId, String planId) {
        employeePlan.put(employeeId, planId);
    }

    @Override
    public void recordReward(String employeeId, int points, String reason) {
        record(employeeId, Math.max(points, 0), "REWARD", reason);
    }

    @Override
    public void recordPenalty(String employeeId, int points, String reason) {
        record(employeeId, -Math.abs(points), "PENALTY", reason);
    }

    @Override
    public int getBalance(String employeeId) {
        return balances.getOrDefault(employeeId, 0);
    }

    @Override
    public List<CompensationRecord> getHistory(String employeeId) {
        return new ArrayList<>(history.getOrDefault(employeeId, List.of()));
    }

    @Override
    public Map<String, Object> summarizeDepartment(String departmentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("departmentId", departmentId);
        payload.put("plans", plans.values().stream().filter(p -> departmentId.equals(p.departmentId())).count());
        payload.put("employees", employeePlan.entrySet().stream().filter(e -> {
            CompensationPlan plan = plans.get(e.getValue());
            return plan != null && departmentId.equals(plan.departmentId());
        }).count());
        payload.put("totalBalance", balances.values().stream().mapToInt(Integer::intValue).sum());
        return payload;
    }

    private void record(String employeeId, int points, String type, String reason) {
        CompensationRecord record = new CompensationRecord("rec_" + System.currentTimeMillis(), employeeId, points, type, reason, System.currentTimeMillis());
        history.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(record);
        balances.merge(employeeId, points, Integer::sum);
    }
}
