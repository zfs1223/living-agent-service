package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.PerformanceCaptureResult;
import com.livingagent.core.autonomy.PerformanceCaptureService;
import com.livingagent.core.autonomous.bounty.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultPerformanceCaptureService implements PerformanceCaptureService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPerformanceCaptureService.class);

    private static final int TASK_COMPLETION_CREDITS = 100;

    private final LedgerService ledgerService;

    public DefaultPerformanceCaptureService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public List<PerformanceCaptureResult> captureFromExecution(
            String executionId,
            String department,
            String taskType,
            String goal,
            List<String> employeeCodes,
            String resultStatus) {
        if (executionId == null || employeeCodes == null || employeeCodes.isEmpty()) {
            log.debug("Performance capture skipped: executionId or employeeCodes is empty");
            return List.of(PerformanceCaptureResult.skipped("executionId or employeeCodes is empty"));
        }

        List<PerformanceCaptureResult> results = new ArrayList<>();
        for (String employeeCode : employeeCodes) {
            try {
                String contributionType = "COMPLETED".equals(resultStatus) ? "task_completion" : "task_participation";
                int credits = "COMPLETED".equals(resultStatus) ? TASK_COMPLETION_CREDITS : TASK_COMPLETION_CREDITS / 2;

                ledgerService.recordReward(employeeCode, credits,
                    "任务执行贡献: " + taskType + " - " + (goal != null && goal.length() > 50 ? goal.substring(0, 50) : goal));

                log.info("Performance captured: employee={}, executionId={}, type={}, credits={}",
                    employeeCode, executionId, contributionType, credits);
                results.add(PerformanceCaptureResult.success(employeeCode, executionId, contributionType));
            } catch (Exception e) {
                log.warn("Performance capture failed for employee={}: {}", employeeCode, e.getMessage());
                results.add(PerformanceCaptureResult.skipped("capture failed: " + e.getMessage()));
            }
        }
        return results;
    }
}
