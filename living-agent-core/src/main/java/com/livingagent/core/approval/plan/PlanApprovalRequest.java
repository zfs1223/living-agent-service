package com.livingagent.core.approval.plan;

public record PlanApprovalRequest(
    String requestId,
    String submitterNeuronId,
    String planText,
    PlanType planType,
    ApprovalStatus status,
    long submittedAt,
    long deadlineMs
) {
    public enum PlanType {
        CODE_CHANGE,
        ARCHITECTURE_DECISION,
        DEPLOYMENT_PLAN,
        DATA_MIGRATION,
        SECURITY_CHANGE,
        GENERAL
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    public static PlanApprovalRequest submit(String requestId, String submitterNeuronId,
                                               String planText, PlanType planType, long deadlineMs) {
        return new PlanApprovalRequest(requestId, submitterNeuronId, planText, planType,
            ApprovalStatus.PENDING, System.currentTimeMillis(), deadlineMs);
    }

    public PlanApprovalRequest withStatus(ApprovalStatus status) {
        return new PlanApprovalRequest(requestId, submitterNeuronId, planText, planType,
            status, submittedAt, deadlineMs);
    }

    public boolean isExpired() {
        return status == ApprovalStatus.PENDING
            && System.currentTimeMillis() > submittedAt + deadlineMs;
    }
}
