package com.livingagent.core.approval.impl;

import com.livingagent.core.approval.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ApprovalServiceImpl implements ApprovalService {
    
    private final Map<String, ApprovalInstance> approvalStore = new ConcurrentHashMap<>();
    private final Map<String, ApprovalWorkflow> workflowStore = new ConcurrentHashMap<>();
    
    public ApprovalServiceImpl() {
        initializeDefaultWorkflows();
    }
    
    private void initializeDefaultWorkflows() {
        ApprovalWorkflow defaultWorkflow = new ApprovalWorkflow("default", "默认审批流程");
        defaultWorkflow.setDescription("单级审批流程");
        defaultWorkflow.setSteps(List.of(
            new ApprovalStep("step_1", "部门主管审批", 0)
        ));
        workflowStore.put("default", defaultWorkflow);
        
        ApprovalWorkflow projectWorkflow = new ApprovalWorkflow("project_approval", "项目审批流程");
        projectWorkflow.setDescription("三级审批流程：部门主管 → 财务部 → 董事长");
        projectWorkflow.setSteps(List.of(
            new ApprovalStep("step_1", "部门主管审批", 0),
            new ApprovalStep("step_2", "财务部审批", 1),
            new ApprovalStep("step_3", "董事长审批", 2)
        ));
        workflowStore.put("project_approval", projectWorkflow);
        
        ApprovalWorkflow expenseWorkflow = new ApprovalWorkflow("expense_approval", "报销审批流程");
        expenseWorkflow.setDescription("两级审批流程：部门主管 → 财务部");
        expenseWorkflow.setSteps(List.of(
            new ApprovalStep("step_1", "部门主管审批", 0),
            new ApprovalStep("step_2", "财务部审批", 1)
        ));
        workflowStore.put("expense_approval", expenseWorkflow);
    }
    
    @Override
    public ApprovalInstance createApproval(CreateApprovalRequest request) {
        ApprovalInstance instance = new ApprovalInstance(
            request.workflowId(),
            request.businessType(),
            request.businessId()
        );
        instance.setTitle(request.title());
        instance.setDescription(request.description());
        instance.setSubmitterId(request.submitterId());
        instance.start();
        
        approvalStore.put(instance.getInstanceId(), instance);
        return instance;
    }
    
    @Override
    public Optional<ApprovalInstance> getApproval(String instanceId) {
        return Optional.ofNullable(approvalStore.get(instanceId));
    }
    
    @Override
    public List<ApprovalInstance> getPendingApprovals(String approverId) {
        return approvalStore.values().stream()
            .filter(a -> a.getStatus() == ApprovalInstance.ApprovalStatus.IN_PROGRESS 
                      || a.getStatus() == ApprovalInstance.ApprovalStatus.PENDING)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ApprovalInstance> getMyApprovals(String submitterId, String status) {
        return approvalStore.values().stream()
            .filter(a -> submitterId.equals(a.getSubmitterId()))
            .filter(a -> status == null || a.getStatus().name().equals(status))
            .collect(Collectors.toList());
    }
    
    @Override
    public ApprovalInstance approve(String instanceId, String approverId, String comment) {
        ApprovalInstance instance = approvalStore.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Approval instance not found: " + instanceId);
        }
        
        ApprovalWorkflow workflow = workflowStore.get(instance.getWorkflowId());
        if (workflow == null) {
            workflow = workflowStore.get("default");
        }
        
        int currentStep = instance.getCurrentStep();
        List<ApprovalStep> steps = workflow.getSteps();
        
        String stepId = steps.get(currentStep).getStepId();
        instance.approve(stepId, approverId, comment);
        
        if (instance.getCurrentStep() >= steps.size()) {
            instance.complete();
        }
        
        return instance;
    }
    
    @Override
    public ApprovalInstance reject(String instanceId, String approverId, String comment) {
        ApprovalInstance instance = approvalStore.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Approval instance not found: " + instanceId);
        }
        
        ApprovalWorkflow workflow = workflowStore.get(instance.getWorkflowId());
        if (workflow == null) {
            workflow = workflowStore.get("default");
        }
        
        int currentStep = instance.getCurrentStep();
        String stepId = workflow.getSteps().get(currentStep).getStepId();
        instance.reject(stepId, approverId, comment);
        
        return instance;
    }
    
    @Override
    public ApprovalInstance returnToSubmitter(String instanceId, String approverId, String comment) {
        ApprovalInstance instance = approvalStore.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Approval instance not found: " + instanceId);
        }
        
        ApprovalWorkflow workflow = workflowStore.get(instance.getWorkflowId());
        if (workflow == null) {
            workflow = workflowStore.get("default");
        }
        
        int currentStep = instance.getCurrentStep();
        String stepId = workflow.getSteps().get(currentStep).getStepId();
        instance.returnToSubmitter(stepId, approverId, comment);
        
        return instance;
    }
    
    @Override
    public void cancel(String instanceId, String submitterId) {
        ApprovalInstance instance = approvalStore.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Approval instance not found: " + instanceId);
        }
        
        if (!submitterId.equals(instance.getSubmitterId())) {
            throw new IllegalStateException("Only submitter can cancel the approval");
        }
        
        instance.cancel();
    }
    
    @Override
    public List<ApprovalRecord> getHistory(String instanceId) {
        ApprovalInstance instance = approvalStore.get(instanceId);
        if (instance == null) {
            return List.of();
        }
        return instance.getRecords();
    }
    
    @Override
    public ApprovalWorkflow createWorkflow(CreateWorkflowRequest request) {
        ApprovalWorkflow workflow = new ApprovalWorkflow(request.workflowId(), request.name());
        workflow.setDescription(request.description());
        workflow.setSteps(request.steps());
        workflowStore.put(request.workflowId(), workflow);
        return workflow;
    }
    
    @Override
    public Optional<ApprovalWorkflow> getWorkflow(String workflowId) {
        return Optional.ofNullable(workflowStore.get(workflowId));
    }
    
    @Override
    public List<ApprovalWorkflow> listWorkflows() {
        return new ArrayList<>(workflowStore.values());
    }
}
