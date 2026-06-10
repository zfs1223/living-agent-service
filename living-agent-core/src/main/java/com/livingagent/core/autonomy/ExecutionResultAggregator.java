package com.livingagent.core.autonomy;

import com.livingagent.core.autonomy.ExecutionReceiptReviewer.ReceiptReviewResult;
import com.livingagent.core.employee.EmployeeCompensationService;

import java.util.List;
import java.util.Map;

public interface ExecutionResultAggregator {

    String aggregate(
        String executionId,
        String department,
        MainBrainTaskPlan mainBrainTaskPlan,
        List<EmployeeExecutionReceipt> receipts,
        String brainRawResponse
    );

    /**
     * 聚合执行结果并计算薪酬
     */
    default AggregationResult aggregateWithCompensation(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            String brainRawResponse,
            EmployeeCompensationService compensationService) {
        AggregationResult result = aggregateStructured(
            executionId, department, mainBrainTaskPlan, receipts, brainRawResponse);

        if (compensationService != null) {
            for (EmployeeExecutionReceipt receipt : receipts) {
                if ("COMPLETED".equals(receipt.status())) {
                    try {
                        compensationService.recordReward(
                            receipt.employeeId(),
                            calculateRewardPoints(receipt),
                            "Task completed: " + receipt.summary()
                        );
                    } catch (Exception e) {
                        // 薪酬计算失败不影响主流程
                    }
                } else if ("FAILED".equals(receipt.status())) {
                    try {
                        compensationService.recordPenalty(
                            receipt.employeeId(),
                            1,
                            "Task failed: " + receipt.summary()
                        );
                    } catch (Exception e) {
                        // 薪酬计算失败不影响主流程
                    }
                }
            }
        }

        return result;
    }

    /**
     * 根据回执质量计算奖励积分
     */
    private int calculateRewardPoints(EmployeeExecutionReceipt receipt) {
        int basePoints = 10;
        if (receipt.qualityScore() > 0.8) basePoints += 5;
        if (receipt.qualityScore() > 0.95) basePoints += 5;
        return basePoints;
    }

    default AggregationResult aggregateStructured(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            String brainRawResponse) {
        String summary = aggregate(executionId, department, mainBrainTaskPlan, receipts, brainRawResponse);
        int completed = (int) receipts.stream().filter(r -> "COMPLETED".equals(r.status())).count();
        int failed = (int) receipts.stream().filter(r -> "FAILED".equals(r.status())).count();
        int degraded = (int) receipts.stream().filter(r -> "DEGRADED".equals(r.status())).count();
        String overallStatus = failed > 0 ? "FAILED"
            : degraded > 0 ? "DEGRADED"
            : completed == receipts.size() ? "COMPLETED"
            : "PARTIALLY_COMPLETED";
        return new AggregationResult(
            executionId,
            overallStatus,
            completed,
            failed,
            degraded,
            receipts.size(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            summary,
            Map.of()
        );
    }

    record AggregationResult(
        String executionId,
        String overallStatus,
        int completedCount,
        int failedCount,
        int degradedCount,
        int totalCount,
        List<String> artifactsProduced,
        List<String> unmetCriteria,
        List<String> conflicts,
        List<String> retryAssignments,
        boolean needsHumanIntervention,
        boolean accepted,
        String summaryForUser,
        Map<String, Object> metadata
    ) {
        public double qualityScore() {
            if (totalCount == 0) return 0.0;
            return (double) completedCount / totalCount;
        }

        public boolean needsRetry() {
            return !retryAssignments.isEmpty() || degradedCount > 0;
        }
    }
}
