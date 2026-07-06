package com.livingagent.gateway.controller;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/executions")
public class ExecutionStatusController {

    private final EmployeeExecutionReceiptService receiptService;
    private final AccessGateService accessGateService;

    public ExecutionStatusController(EmployeeExecutionReceiptService receiptService, AccessGateService accessGateService) {
        this.receiptService = receiptService;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<ApiResponse<ExecutionStatusResponse>> getExecutionStatus(
            @PathVariable String executionId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        List<EmployeeExecutionReceipt> receipts = receiptService.getReceipts(executionId);
        boolean complete = receiptService.isExecutionComplete(executionId);
        long completed = receipts.stream().filter(r -> r.status() == ReceiptStatus.COMPLETED).count();
        long failed = receipts.stream().filter(r -> r.status() == ReceiptStatus.FAILED).count();
        String status = complete ? (failed > 0 ? "PARTIAL_OR_FAILED" : "COMPLETED") : "WAITING_RECEIPT";
        return ResponseEntity.ok(ApiResponse.ok(new ExecutionStatusResponse(
                executionId,
                status,
                complete,
                receipts.size(),
                completed,
                failed,
                receipts
        )));
    }

    public record ExecutionStatusResponse(
            String executionId,
            String status,
            boolean complete,
            int receiptCount,
            long completedCount,
            long failedCount,
            List<EmployeeExecutionReceipt> receipts
    ) {}

}
