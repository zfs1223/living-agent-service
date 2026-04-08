package com.livingagent.gateway.controller;

import com.livingagent.core.approval.*;
import com.livingagent.core.approval.ApprovalInstance.ApprovalStatus;
import com.livingagent.core.approval.ApprovalRecord;
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

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getPendingApprovals(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit
    ) {
        log.debug("Getting pending approvals, type: {}", type);

        String approverId = getCurrentApproverId();
        List<ApprovalInstance> approvals = approvalService.getPendingApprovals(approverId);

        List<ApprovalSummary> summaries = approvals.stream()
                .filter(a -> type == null || a.getBusinessType().equals(type))
                .limit(limit)
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/my-pending")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getMyPendingApprovals(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit
    ) {
        log.debug("Getting my pending approvals, type: {}", type);
        return getPendingApprovals(type, limit);
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> getMyApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        log.debug("Getting my approvals, status: {}", status);

        String submitterId = getCurrentApproverId();
        List<ApprovalInstance> approvals = approvalService.getMyApprovals(submitterId, status);

        List<ApprovalSummary> summaries = approvals.stream()
                .limit(limit)
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalInstance>> createApproval(
            @RequestBody CreateApprovalRequest request
    ) {
        log.info("Creating approval: {} - {}", request.businessType(), request.title());

        ApprovalService.CreateApprovalRequest serviceRequest = new ApprovalService.CreateApprovalRequest(
                request.workflowId() != null ? request.workflowId() : "default",
                request.businessType(),
                request.businessId(),
                request.title(),
                request.description(),
                getCurrentApproverId()
        );

        ApprovalInstance instance = approvalService.createApproval(serviceRequest);
        return ResponseEntity.ok(ApiResponse.success(instance));
    }

    @GetMapping("/{instanceId}")
    public ResponseEntity<ApiResponse<ApprovalDetail>> getApproval(
            @PathVariable String instanceId
    ) {
        log.debug("Getting approval: {}", instanceId);

        return approvalService.getApproval(instanceId)
                .map(a -> ResponseEntity.ok(ApiResponse.success(toDetail(a))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Approval not found: " + instanceId)));
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
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
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
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
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
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
        }
    }

    @PostMapping("/{instanceId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable String instanceId
    ) {
        log.info("Cancelling: {}", instanceId);

        try {
            approvalService.cancel(instanceId, getCurrentApproverId());
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("cancel_failed", e.getMessage()));
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

        return ResponseEntity.ok(ApiResponse.success(details));
    }

    @GetMapping("/{instanceId}/steps")
    public ResponseEntity<ApiResponse<List<ApprovalStepDetail>>> getSteps(
            @PathVariable String instanceId
    ) {
        log.debug("Getting approval steps: {}", instanceId);

        return approvalService.getApproval(instanceId)
                .map(a -> {
                    // 从workflow获取steps
                    List<ApprovalStepDetail> steps = new ArrayList<>();
                    steps.add(new ApprovalStepDetail(
                            "step_1",
                            "第一步",
                            List.of("user1"),
                            a.getStatus().name(),
                            a.getCurrentStep() > 0 ? "user1" : null,
                            null,
                            null
                    ));
                    return ResponseEntity.ok(ApiResponse.success(steps));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Approval not found: " + instanceId)));
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
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
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
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
        }
    }

    @GetMapping("/workflows")
    public ResponseEntity<ApiResponse<List<WorkflowSummary>>> listWorkflows() {
        log.debug("Listing workflows");

        List<ApprovalWorkflow> workflows = approvalService.listWorkflows();
        List<WorkflowSummary> summaries = workflows.stream()
                .map(this::toWorkflowSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowDetail>> getWorkflow(
            @PathVariable String workflowId
    ) {
        log.debug("Getting workflow: {}", workflowId);

        return approvalService.getWorkflow(workflowId)
                .map(w -> ResponseEntity.ok(ApiResponse.success(toWorkflowDetail(w))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Workflow not found: " + workflowId)));
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
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }

    private String getCurrentApproverId() {
        return "current_user";
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

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
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
}
