package com.livingagent.gateway.controller;

import com.livingagent.core.approval.*;
import com.livingagent.core.approval.ApprovalInstance.ApprovalStatus;
import com.livingagent.core.approval.ApprovalRecord;
import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.task.TaskStatus;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ApprovalService approvalService;
    private final AccessGateService accessGateService;
    private final TaskRepository taskRepository;

    public ApprovalController(ApprovalService approvalService, AccessGateService accessGateService, TaskRepository taskRepository) {
        this.approvalService = approvalService;
        this.accessGateService = accessGateService;
        this.taskRepository = taskRepository;

        // P1-4.2: 注册审批回调，审批通过后自动推进关联任务
        approvalService.registerCallback(new ApprovalService.ApprovalCallback() {
            @Override
            public void onApproved(ApprovalInstance instance) {
                advanceTaskOnApprovalApproved(instance);
            }

            @Override
            public void onRejected(ApprovalInstance instance) {
                advanceTaskOnApprovalRejected(instance);
            }
        });
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> listAllApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Listing all approvals, status: {}, type: {}", status, type);

        String userId = employeeId != null && !employeeId.isBlank() ? employeeId : getCurrentApproverId();
        List<ApprovalInstance> approvals = approvalService.getMyApprovals(userId, status);

        List<ApprovalSummary> summaries = approvals.stream()
                .filter(a -> type == null || a.getBusinessType().equals(type))
                .limit(limit)
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getPendingApprovals(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Getting pending approvals, type: {}", type);

        String approverId = employeeId != null && !employeeId.isBlank() ? employeeId : getCurrentApproverId();
        if (!accessGateService.canRoute(approverId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        List<ApprovalInstance> approvals = approvalService.getPendingApprovals(approverId);

        List<ApprovalSummary> summaries = approvals.stream()
                .filter(a -> type == null || a.getBusinessType().equals(type))
                .limit(limit)
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/my-pending")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getMyPendingApprovals(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Getting my pending approvals, type: {}", type);
        return getPendingApprovals(type, limit, employeeId);
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getMyApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Getting my approvals, status: {}", status);

        String submitterId = employeeId != null && !employeeId.isBlank() ? employeeId : getCurrentApproverId();
        List<ApprovalInstance> approvals = approvalService.getMyApprovals(submitterId, status);

        List<ApprovalSummary> summaries = approvals.stream()
                .limit(limit)
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalInstance>> createApproval(
            @RequestBody CreateApprovalRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.info("Creating approval: {} - {}", request.businessType(), request.title());

        String approverId = employeeId != null && !employeeId.isBlank() ? employeeId : getCurrentApproverId();
        if (!accessGateService.canRoute(approverId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        ApprovalService.CreateApprovalRequest serviceRequest = new ApprovalService.CreateApprovalRequest(
                request.workflowId() != null ? request.workflowId() : "default",
                request.businessType(),
                request.businessId(),
                request.title(),
                request.description(),
                approverId
        );

        ApprovalInstance instance = approvalService.createApproval(serviceRequest);
        return ResponseEntity.ok(ApiResponse.ok(instance));
    }

    @GetMapping("/{instanceId}")
    public ResponseEntity<ApiResponse<ApprovalDetail>> getApproval(
            @PathVariable String instanceId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting approval: {}", instanceId);

        return approvalService.getApproval(instanceId)
                .map(a -> ResponseEntity.ok(ApiResponse.ok(toDetail(a))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Approval not found: " + instanceId)));
    }

    @PostMapping("/{instanceId}/approve")
    public ResponseEntity<ApiResponse<ApprovalInstance>> approve(
            @PathVariable String instanceId,
            @RequestBody ApprovalRequest request
    ) {
        log.info("Approving: {} by {}", instanceId, getCurrentApproverId());

        try {
            ApprovalInstance instance = approvalService.approve(
                    instanceId,
                    getCurrentApproverId(),
                    request.comment()
            );
            return ResponseEntity.ok(ApiResponse.ok(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @PostMapping("/{instanceId}/reject")
    public ResponseEntity<ApiResponse<ApprovalInstance>> reject(
            @PathVariable String instanceId,
            @RequestBody ApprovalRequest request
    ) {
        log.info("Rejecting: {} by {}", instanceId, getCurrentApproverId());

        try {
            ApprovalInstance instance = approvalService.reject(
                    instanceId,
                    getCurrentApproverId(),
                    request.comment()
            );
            return ResponseEntity.ok(ApiResponse.ok(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @PostMapping("/{instanceId}/return")
    public ResponseEntity<ApiResponse<ApprovalInstance>> returnToSubmitter(
            @PathVariable String instanceId,
            @RequestBody ApprovalRequest request
    ) {
        log.info("Returning: {} by {}", instanceId, getCurrentApproverId());

        try {
            ApprovalInstance instance = approvalService.returnToSubmitter(
                    instanceId,
                    getCurrentApproverId(),
                    request.comment()
            );
            return ResponseEntity.ok(ApiResponse.ok(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @PostMapping("/{instanceId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable String instanceId
    ) {
        log.info("Cancelling: {}", instanceId);

        try {
            approvalService.cancel(instanceId, getCurrentApproverId());
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("cancel_failed", e.getMessage()));
        }
    }

    @GetMapping("/{instanceId}/history")
    public ResponseEntity<ApiResponse<List<ApprovalRecordDetail>>> getHistory(
            @PathVariable String instanceId
    ) {
        log.debug("Getting approval history: {}", instanceId);

        List<ApprovalRecord> records = approvalService.getHistory(instanceId);
        List<ApprovalRecordDetail> details = records.stream()
                .map(this::toRecordDetail)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/{instanceId}/steps")
    public ResponseEntity<ApiResponse<List<ApprovalStepDetail>>> getSteps(
            @PathVariable String instanceId
    ) {
        log.debug("Getting approval steps: {}", instanceId);

        return approvalService.getApproval(instanceId)
                .map(a -> {
                    List<ApprovalStepDetail> steps = new ArrayList<>();
                    approvalService.getWorkflow(a.getWorkflowId())
                            .ifPresent(workflow -> {
                                List<ApprovalRecord> records = approvalService.getHistory(instanceId);
                                for (int i = 0; i < workflow.getSteps().size(); i++) {
                                    ApprovalStep ws = workflow.getSteps().get(i);
                                    ApprovalRecord record = i < records.size() ? records.get(i) : null;
                                    steps.add(new ApprovalStepDetail(
                                            ws.getStepId(),
                                            ws.getName(),
                                            ws.getApprovers(),
                                            i < a.getCurrentStep() ? "APPROVED" :
                                                    i == a.getCurrentStep() ? a.getStatus().name() : "PENDING",
                                            record != null ? record.getApproverId() : null,
                                            record != null ? record.getComment() : null,
                                            record != null ? record.getDecidedAt() : null
                                    ));
                                }
                            });
                    if (steps.isEmpty()) {
                        steps.add(new ApprovalStepDetail("step_1", "审批", List.of(), a.getStatus().name(), null, null, null));
                    }
                    return ResponseEntity.ok(ApiResponse.ok(steps));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Approval not found: " + instanceId)));
    }

    @PostMapping("/{instanceId}/steps/{stepId}/approve")
    public ResponseEntity<ApiResponse<ApprovalInstance>> approveStep(
            @PathVariable String instanceId,
            @PathVariable String stepId,
            @RequestBody ApprovalRequest request
    ) {
        log.info("Approving step: {} of {} by {}", stepId, instanceId, getCurrentApproverId());

        try {
            ApprovalInstance instance = approvalService.approve(
                    instanceId,
                    getCurrentApproverId(),
                    request.comment()
            );
            return ResponseEntity.ok(ApiResponse.ok(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @PostMapping("/{instanceId}/steps/{stepId}/reject")
    public ResponseEntity<ApiResponse<ApprovalInstance>> rejectStep(
            @PathVariable String instanceId,
            @PathVariable String stepId,
            @RequestBody ApprovalRequest request
    ) {
        log.info("Rejecting step: {} of {} by {}", stepId, instanceId, getCurrentApproverId());

        try {
            ApprovalInstance instance = approvalService.reject(
                    instanceId,
                    getCurrentApproverId(),
                    request.comment()
            );
            return ResponseEntity.ok(ApiResponse.ok(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @GetMapping("/workflows")
    public ResponseEntity<ApiResponse<List<WorkflowSummary>>> listWorkflows() {
        log.debug("Listing workflows");

        List<ApprovalWorkflow> workflows = approvalService.listWorkflows();
        List<WorkflowSummary> summaries = workflows.stream()
                .map(this::toWorkflowSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDetail>> getWorkflow(
            @PathVariable String workflowId
    ) {
        log.debug("Getting workflow: {}", workflowId);

        return approvalService.getWorkflow(workflowId)
                .map(w -> ResponseEntity.ok(ApiResponse.ok(toWorkflowDetail(w))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Workflow not found: " + workflowId)));
    }

    @PostMapping("/workflows")
    public ResponseEntity<ApiResponse<ApprovalWorkflow>> createWorkflow(
            @RequestBody CreateWorkflowRequest request
    ) {
        log.info("Creating workflow: {}", request.name());

        ApprovalService.CreateWorkflowRequest serviceRequest = new ApprovalService.CreateWorkflowRequest(
                request.workflowId(),
                request.name(),
                request.description(),
                request.steps()
        );

        ApprovalWorkflow workflow = approvalService.createWorkflow(serviceRequest);
        return ResponseEntity.ok(ApiResponse.ok(workflow));
    }

    private String getCurrentApproverId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
            return auth.getName();
        }
        return "system";
    }

    private ApprovalSummary toSummary(ApprovalInstance instance) {
        return new ApprovalSummary(
                instance.getInstanceId(),
                instance.getTitle(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getStatus().name(),
                instance.getCurrentStep(),
                instance.getSubmitterId(),
                instance.getCreatedAt(),
                instance.getCompletedAt()
        );
    }

    private ApprovalDetail toDetail(ApprovalInstance instance) {
        return new ApprovalDetail(
                instance.getInstanceId(),
                instance.getWorkflowId(),
                instance.getTitle(),
                instance.getDescription(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getStatus().name(),
                instance.getCurrentStep(),
                instance.getSubmitterId(),
                instance.getRecords().stream()
                        .map(this::toRecordDetail)
                        .collect(Collectors.toList()),
                instance.getContext(),
                instance.getCreatedAt(),
                instance.getCompletedAt()
        );
    }

    private ApprovalRecordDetail toRecordDetail(ApprovalRecord record) {
        return new ApprovalRecordDetail(
                record.getRecordId(),
                record.getStepId(),
                record.getApproverId(),
                record.getDecision().name(),
                record.getComment(),
                record.getDecidedAt()
        );
    }

    private WorkflowSummary toWorkflowSummary(ApprovalWorkflow workflow) {
        return new WorkflowSummary(
                workflow.getWorkflowId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getSteps() != null ? workflow.getSteps().size() : 0,
                workflow.isEnabled()
        );
    }

    private WorkflowDetail toWorkflowDetail(ApprovalWorkflow workflow) {
        return new WorkflowDetail(
                workflow.getWorkflowId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getSteps(),
                workflow.isEnabled(),
                workflow.getCreatedAt()
        );
    }

    public record ApprovalSummary(
            String instanceId,
            String title,
            String businessType,
            String businessId,
            String status,
            int currentStep,
            String submitterId,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record ApprovalDetail(
            String instanceId,
            String workflowId,
            String title,
            String description,
            String businessType,
            String businessId,
            String status,
            int currentStep,
            String submitterId,
            List<ApprovalRecordDetail> records,
            java.util.Map<String, Object> context,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record ApprovalRecordDetail(
            String recordId,
            String stepId,
            String approverId,
            String decision,
            String comment,
            Instant decidedAt
    ) {}

    public record WorkflowSummary(
            String workflowId,
            String name,
            String description,
            int stepCount,
            boolean enabled
    ) {}

    public record WorkflowDetail(
            String workflowId,
            String name,
            String description,
            List<ApprovalStep> steps,
            boolean enabled,
            Instant createdAt
    ) {}

    public record CreateApprovalRequest(
            String workflowId,
            String businessType,
            String businessId,
            String title,
            String description
    ) {}

    public record ApprovalRequest(
            String comment
    ) {}

    public record CreateWorkflowRequest(
            String workflowId,
            String name,
            String description,
            List<ApprovalStep> steps
    ) {}

    public record ApprovalStepDetail(
            String stepId,
            String stepName,
            List<String> approverIds,
            String status,
            String approvedBy,
            String comment,
            Instant completedAt
    ) {}

    // ─── P1-4.2: 审批回调 - 审批通过/拒绝后自动推进关联任务 ───

    /**
     * P1-4.2: 审批通过回调端点，供外部系统或前端手动触发。
     * 当审批 businessType 为 execution_receipt 时，自动推进关联任务状态。
     */
    @PostMapping("/{instanceId}/callback/approved")
    public ResponseEntity<ApiResponse<CallbackResult>> onApprovalApproved(
            @PathVariable String instanceId
    ) {
        log.info("Approval approved callback triggered: {}", instanceId);
        return approvalService.getApproval(instanceId)
                .map(instance -> {
                    advanceTaskOnApprovalApproved(instance);
                    return ResponseEntity.ok(ApiResponse.ok(
                        new CallbackResult(instanceId, "approved", "Task advanced successfully")));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Approval not found: " + instanceId)));
    }

    /**
     * P1-4.2: 审批拒绝回调端点。
     */
    @PostMapping("/{instanceId}/callback/rejected")
    public ResponseEntity<ApiResponse<CallbackResult>> onApprovalRejected(
            @PathVariable String instanceId
    ) {
        log.info("Approval rejected callback triggered: {}", instanceId);
        return approvalService.getApproval(instanceId)
                .map(instance -> {
                    advanceTaskOnApprovalRejected(instance);
                    return ResponseEntity.ok(ApiResponse.ok(
                        new CallbackResult(instanceId, "rejected", "Task updated with rejection")));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Approval not found: " + instanceId)));
    }

    /**
     * P1-4.2: 审批通过后自动推进关联任务状态。
     * businessType=execution_receipt 时，通过 businessId(executionId) 查找关联任务并推进。
     */
    private void advanceTaskOnApprovalApproved(ApprovalInstance instance) {
        if (!"execution_receipt".equals(instance.getBusinessType())) {
            return;
        }
        String executionId = instance.getBusinessId();
        if (executionId == null || executionId.isBlank()) {
            log.warn("Approval approved but no businessId (executionId) found: instanceId={}", instance.getInstanceId());
            return;
        }
        try {
            taskRepository.findByExecutionId(executionId).stream().findFirst().ifPresent(task -> {
                String currentStatus = task.getStatus();
                if (TaskStatus.NEEDS_HUMAN_REVIEW.getDbValue().equalsIgnoreCase(currentStatus)
                        || "NEEDS_APPROVAL".equalsIgnoreCase(currentStatus)
                        || TaskStatus.PARTIALLY_COMPLETED.getDbValue().equalsIgnoreCase(currentStatus)
                        || TaskStatus.WAITING_RECEIPT.getDbValue().equalsIgnoreCase(currentStatus)) {
                    task.setStatus(TaskStatus.IN_PROGRESS.getDbValue());
                    taskRepository.save(task);
                    log.info("P1-4.2: Approval approved, advanced task {} from {} to IN_PROGRESS (executionId={})",
                        task.getTaskId(), currentStatus, executionId);
                } else {
                    log.info("P1-4.2: Approval approved but task {} already in status {} (executionId={})",
                        task.getTaskId(), currentStatus, executionId);
                }
            });
        } catch (Exception e) {
            log.warn("P1-4.2: Failed to advance task after approval approved: executionId={}, error={}",
                executionId, e.getMessage());
        }
    }

    /**
     * P1-4.2: 审批拒绝后更新关联任务状态为 FAILED。
     */
    private void advanceTaskOnApprovalRejected(ApprovalInstance instance) {
        if (!"execution_receipt".equals(instance.getBusinessType())) {
            return;
        }
        String executionId = instance.getBusinessId();
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        try {
            taskRepository.findByExecutionId(executionId).stream().findFirst().ifPresent(task -> {
                String currentStatus = task.getStatus();
                if (TaskStatus.NEEDS_HUMAN_REVIEW.getDbValue().equalsIgnoreCase(currentStatus)
                        || "NEEDS_APPROVAL".equalsIgnoreCase(currentStatus)
                        || TaskStatus.PARTIALLY_COMPLETED.getDbValue().equalsIgnoreCase(currentStatus)
                        || TaskStatus.WAITING_RECEIPT.getDbValue().equalsIgnoreCase(currentStatus)) {
                    task.setStatus(TaskStatus.FAILED.getDbValue());
                    taskRepository.save(task);
                    log.info("P1-4.2: Approval rejected, updated task {} from {} to FAILED (executionId={})",
                        task.getTaskId(), currentStatus, executionId);
                }
            });
        } catch (Exception e) {
            log.warn("P1-4.2: Failed to update task after approval rejected: executionId={}, error={}",
                executionId, e.getMessage());
        }
    }

    public record CallbackResult(
            String instanceId,
            String action,
            String message
    ) {}
}
