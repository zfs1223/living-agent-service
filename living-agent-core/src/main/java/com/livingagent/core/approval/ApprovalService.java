package com.livingagent.core.approval;

import java.util.List;
import java.util.Optional;

public interface ApprovalService {
    
    ApprovalInstance createApproval(CreateApprovalRequest request);
    
    Optional<ApprovalInstance> getApproval(String instanceId);
    
    List<ApprovalInstance> getPendingApprovals(String approverId);
    
    List<ApprovalInstance> getMyApprovals(String submitterId, String status);
    
    ApprovalInstance approve(String instanceId, String approverId, String comment);
    
    ApprovalInstance reject(String instanceId, String approverId, String comment);
    
    ApprovalInstance returnToSubmitter(String instanceId, String approverId, String comment);
    
    void cancel(String instanceId, String submitterId);
    
    List<ApprovalRecord> getHistory(String instanceId);
    
    ApprovalWorkflow createWorkflow(CreateWorkflowRequest request);
    
    Optional<ApprovalWorkflow> getWorkflow(String workflowId);
    
    List<ApprovalWorkflow> listWorkflows();
    
    record CreateApprovalRequest(
        String workflowId,
        String businessType,
        String businessId,
        String title,
        String description,
        String submitterId
    ) {}
    
    record CreateWorkflowRequest(
        String workflowId,
        String name,
        String description,
        List<ApprovalStep> steps
    ) {}
}
