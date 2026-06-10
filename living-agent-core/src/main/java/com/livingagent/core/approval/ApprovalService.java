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
    
    /** P1-4.2: 注册审批回调，审批完成时触发 */
    void registerCallback(ApprovalCallback callback);
    
    /** P1-4.2: 审批回调接口 */
    interface ApprovalCallback {
        /** 审批通过时调用 */
        void onApproved(ApprovalInstance instance);
        /** 审批拒绝时调用 */
        void onRejected(ApprovalInstance instance);
    }
    
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
