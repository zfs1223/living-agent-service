package com.livingagent.core.approval;

import java.util.List;

public class ApprovalStep {
    
    private String stepId;
    private String name;
    private ApprovalType type;
    private List<String> approvers;
    private int order;
    
    public enum ApprovalType {
        SINGLE,
        ALL,
        ANY
    }
    
    public ApprovalStep() {}
    
    public ApprovalStep(String stepId, String name, int order) {
        this.stepId = stepId;
        this.name = name;
        this.order = order;
        this.type = ApprovalType.SINGLE;
    }
    
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public ApprovalType getType() { return type; }
    public void setType(ApprovalType type) { this.type = type; }
    
    public List<String> getApprovers() { return approvers; }
    public void setApprovers(List<String> approvers) { this.approvers = approvers; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
}
