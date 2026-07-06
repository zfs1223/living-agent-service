package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomous.bounty.LedgerService;
import com.livingagent.core.autonomy.PerformanceStatsService;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NP2-3: 默认绩效统计实现。
 * 从 LedgerService 聚合经济数据，生成结构化绩效指标。
 */
@Component
public class DefaultPerformanceStatsService implements PerformanceStatsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPerformanceStatsService.class);

    private final LedgerService ledgerService;
    private final FixedEmployeeRegistry employeeRegistry;

    private final java.util.concurrent.ConcurrentHashMap<String, Double> dispatchWeights = new java.util.concurrent.ConcurrentHashMap<>();

    public DefaultPerformanceStatsService(LedgerService ledgerService,
                                           FixedEmployeeRegistry employeeRegistry) {
        this.ledgerService = ledgerService;
        this.employeeRegistry = employeeRegistry;
    }

    @Override
    public EmployeePerformanceStats getStats(String employeeCode) {
        int totalCredits = ledgerService.getTotalEarned(employeeCode);
        List<LedgerService.IncomeRecord> history = ledgerService.getIncomeHistory(employeeCode, 1000);

        int completionCount = 0;
        int participationCount = 0;
        double totalReward = 0;

        for (LedgerService.IncomeRecord record : history) {
            if ("task_completion".equals(record.sourceType())) {
                completionCount++;
            } else if ("task_participation".equals(record.sourceType())) {
                participationCount++;
            }
            if ("RECEIVED".equals(record.status())) {
                totalReward += record.amountCents();
            }
        }

        int totalTasks = completionCount + participationCount;
        double avgReward = totalTasks > 0 ? totalReward / totalTasks : 0.0;

        // 构建原始统计并计算标准化绩效评分
        EmployeePerformanceStats rawStats = new EmployeePerformanceStats(
            employeeCode, totalCredits, completionCount, participationCount, avgReward, 0.0
        );
        
        return new EmployeePerformanceStats(
            employeeCode, totalCredits, completionCount, participationCount, avgReward, rawStats.normalizedScore()
        );
    }

    @Override
    public Map<String, EmployeePerformanceStats> getStatsBatch(List<String> employeeCodes) {
        if (employeeCodes == null || employeeCodes.isEmpty()) {
            return Map.of();
        }
        return employeeCodes.stream()
            .collect(Collectors.toMap(
                code -> code,
                this::getStats
            ));
    }

    @Override
    public List<EmployeePerformanceStats> getDepartmentRanking(String department, int limit) {
        // 从 FixedEmployeeRegistry 获取部门内所有员工
        List<String> deptEmployees = employeeRegistry.getDefinitionsByDepartment(department).stream()
            .map(def -> def.code())
            .toList();

        if (deptEmployees.isEmpty()) {
            return List.of();
        }

        return deptEmployees.stream()
            .map(this::getStats)
            .sorted(Comparator.comparingDouble(EmployeePerformanceStats::normalizedScore).reversed())
            .limit(limit > 0 ? limit : 10)
            .toList();
    }

    @Override
    public void adjustWeight(String employeeCode, double delta) {
        double current = dispatchWeights.getOrDefault(employeeCode, 1.0);
        double updated = Math.max(0.0, current + delta);
        dispatchWeights.put(employeeCode, updated);
        if (updated <= 0.0) {
            log.warn("Employee {} dispatch weight dropped to 0, may need human review", employeeCode);
        } else if (updated < 0.3) {
            log.info("Employee {} has low dispatch weight: {:.2f}", employeeCode, updated);
        }
    }

    @Override
    public double getDispatchWeight(String employeeCode) {
        return dispatchWeights.getOrDefault(employeeCode, 1.0);
    }
}
