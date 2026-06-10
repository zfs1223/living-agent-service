package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutionReceiptReviewer {

    Optional<ReceiptReviewResult> reviewReceipt(
        EmployeeExecutionReceipt receipt,
        EmployeeWorkAssignment assignment,
        List<String> acceptanceCriteria
    );

    record ReceiptReviewResult(
        String receiptId,
        boolean accepted,
        double qualityScore,
        String reviewComment,
        List<String> unmetCriteria,
        boolean needsRetry,
        String retrySuggestion
    ) {}
}
