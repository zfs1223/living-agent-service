package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行动效果追踪器（64-F-1）
 * 追踪工具成功率、验证通过率、行动平均耗时。
 * 为 LLM 调度器提供行动效果上下文。
 */
public class ActionEffectivenessTracker {

    private static final Logger log = LoggerFactory.getLogger(ActionEffectivenessTracker.class);
    private static final int MAX_HISTORY = 200;

    private final Map<String, List<ActionMetric>> employeeMetrics = new ConcurrentHashMap<>();
    private final Map<String, ToolMetricAggregate> toolMetrics = new ConcurrentHashMap<>();
    private final Map<String, ValidationAggregate> validationMetrics = new ConcurrentHashMap<>();

    public record ActionMetric(
        String employeeCode,
        String taskType,
        boolean success,
        boolean validationPassed,
        long durationMs,
        List<String> toolsUsed,
        Instant timestamp
    ) {}

    public record ToolMetricAggregate(
        String toolName,
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        double avgDurationMs
    ) {
        public double successRate() {
            return totalCalls > 0 ? (double) successfulCalls / totalCalls : 0;
        }

        public static ToolMetricAggregate empty(String toolName) {
            return new ToolMetricAggregate(toolName, 0, 0, 0, 0);
        }

        public ToolMetricAggregate record(boolean success, long durationMs) {
            long newTotal = totalCalls + 1;
            long newSuccess = success ? successfulCalls + 1 : successfulCalls;
            long newFailed = success ? failedCalls : failedCalls + 1;
            double newAvg = totalCalls > 0 ? ((avgDurationMs * totalCalls) + durationMs) / newTotal : durationMs;
            return new ToolMetricAggregate(toolName, newTotal, newSuccess, newFailed, newAvg);
        }
    }

    public record ValidationAggregate(
        String taskType,
        long totalValidations,
        long passedValidations,
        long failedValidations
    ) {
        public double passRate() {
            return totalValidations > 0 ? (double) passedValidations / totalValidations : 0;
        }

        public static ValidationAggregate empty(String taskType) {
            return new ValidationAggregate(taskType, 0, 0, 0);
        }

        public ValidationAggregate record(boolean passed) {
            return new ValidationAggregate(taskType,
                totalValidations + 1,
                passed ? passedValidations + 1 : passedValidations,
                passed ? failedValidations : failedValidations + 1);
        }
    }

    /**
     * 追踪执行回执
     */
    public void track(EmployeeExecutionReceipt receipt) {
        if (receipt == null) return;

        final boolean success = receipt.status() != null &&
            (receipt.status().name().equals("COMPLETED") || receipt.status().name().equals("ACCEPTED"));
        final boolean validationPassed = receipt.status() != null &&
            !receipt.status().name().equals("NEEDS_REWORK");

        // 从 metadata 提取 duration 和 taskType（回执本身无这些字段）
        long durationMsRaw = 0;
        String taskTypeRaw = "unknown";
        if (receipt.metadata() != null) {
            Object dur = receipt.metadata().get("durationMs");
            if (dur instanceof Number n) durationMsRaw = n.longValue();
            Object tt = receipt.metadata().get("taskType");
            if (tt instanceof String s) taskTypeRaw = s;
        }
        final long durationMs = durationMsRaw;
        final String taskType = taskTypeRaw;

        List<String> toolsUsedRaw = List.of();
        if (receipt.toolCalls() != null) {
            toolsUsedRaw = receipt.toolCalls().stream()
                .map(EmployeeExecutionReceipt.ToolCallRecord::toolName)
                .distinct()
                .toList();
        }
        final List<String> toolsUsed = toolsUsedRaw;

        ActionMetric metric = new ActionMetric(
            receipt.employeeCode(),
            taskType,
            success,
            validationPassed,
            durationMs,
            toolsUsed,
            Instant.now()
        );

        // 记录员工级指标
        employeeMetrics.computeIfAbsent(receipt.employeeCode(), k -> new ArrayList<>())
            .add(metric);
        trimHistory(receipt.employeeCode());

        // 记录工具级指标
        int toolCount = Math.max(toolsUsed.size(), 1);
        for (String toolName : toolsUsed) {
            ToolMetricAggregate prev = toolMetrics.getOrDefault(toolName, ToolMetricAggregate.empty(toolName));
            toolMetrics.put(toolName, prev.record(success, durationMs / toolCount));
        }

        // 记录验证级指标
        ValidationAggregate prevVal = validationMetrics.getOrDefault(taskType, ValidationAggregate.empty(taskType));
        validationMetrics.put(taskType, prevVal.record(validationPassed));

        log.debug("Tracked action metric: employee={}, task={}, success={}, validation={}",
            receipt.employeeCode(), taskType, success, validationPassed);
    }

    /**
     * 为 LLM 调度器提供行动效果上下文
     */
    public String getEffectivenessContext(String employeeCode) {
        List<ActionMetric> metrics = employeeMetrics.getOrDefault(employeeCode, List.of());
        if (metrics.isEmpty()) {
            return "员工 " + employeeCode + " 暂无行动效果数据";
        }

        long successCount = metrics.stream().filter(ActionMetric::success).count();
        long validationCount = metrics.stream().filter(ActionMetric::validationPassed).count();
        double avgDuration = metrics.stream()
            .mapToLong(ActionMetric::durationMs)
            .average()
            .orElse(0);

        return String.format(
            "员工 %s 近期行动效果：成功率 %.1f%%，验证通过率 %.1f%%，平均耗时 %dms，总执行 %d 次",
            employeeCode,
            successCount * 100.0 / metrics.size(),
            validationCount * 100.0 / metrics.size(),
            (long) avgDuration,
            metrics.size()
        );
    }

    /**
     * 获取员工级效果摘要
     */
    public EffectivenessSummary getSummary(String employeeCode) {
        List<ActionMetric> metrics = employeeMetrics.getOrDefault(employeeCode, List.of());
        if (metrics.isEmpty()) {
            return new EffectivenessSummary(employeeCode, 0, 0, 0, 0, 0, List.of());
        }

        long successCount = metrics.stream().filter(ActionMetric::success).count();
        long validationCount = metrics.stream().filter(ActionMetric::validationPassed).count();
        double avgDuration = metrics.stream().mapToLong(ActionMetric::durationMs).average().orElse(0);

        // 找出最常使用的工具
        Map<String, Long> toolFreq = new ConcurrentHashMap<>();
        metrics.forEach(m -> m.toolsUsed().forEach(t -> toolFreq.merge(t, 1L, Long::sum)));
        List<String> topTools = toolFreq.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();

        return new EffectivenessSummary(
            employeeCode,
            metrics.size(),
            successCount,
            validationCount,
            successCount * 100.0 / metrics.size(),
            (long) avgDuration,
            topTools
        );
    }

    public record EffectivenessSummary(
        String employeeCode,
        long totalExecutions,
        long successCount,
        long validationPassCount,
        double successRate,
        long avgDurationMs,
        List<String> topTools
    ) {}

    private void trimHistory(String employeeCode) {
        List<ActionMetric> list = employeeMetrics.get(employeeCode);
        if (list != null && list.size() > MAX_HISTORY) {
            list.subList(0, list.size() - MAX_HISTORY).clear();
        }
    }
}
