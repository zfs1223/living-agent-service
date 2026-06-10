package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;

import java.util.List;
import java.util.Map;

public class DefaultExecutionResultAggregator implements ExecutionResultAggregator {

    @Override
    public String aggregate(
            String executionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeExecutionReceipt> receipts,
            String brainRawResponse) {

        int completedCount = 0;
        int failedCount = 0;
        StringBuilder summary = new StringBuilder();

        summary.append("**执行汇总**（").append(department).append("）\n\n");

        if (mainBrainTaskPlan != null) {
            summary.append("任务: ").append(mainBrainTaskPlan.goal()).append("\n\n");
        }

        for (EmployeeExecutionReceipt receipt : receipts) {
            if (receipt.status() == ReceiptStatus.COMPLETED) {
                completedCount++;
                summary.append("✅ ").append(receipt.employeeCode())
                    .append(": ").append(receipt.summary()).append("\n");
            } else if (receipt.status() == ReceiptStatus.FAILED) {
                failedCount++;
                summary.append("❌ ").append(receipt.employeeCode())
                    .append(": ").append(receipt.summary()).append("\n");
            }
        }

        int totalCount = receipts.size();
        summary.append("\n完成: ").append(completedCount).append("/").append(totalCount);
        if (failedCount > 0) {
            summary.append(", 失败: ").append(failedCount);
        }

        if (brainRawResponse != null && !brainRawResponse.isBlank()) {
            summary.append("\n\n---\n**大脑响应**：\n").append(brainRawResponse);
        }

        return summary.toString();
    }
}
