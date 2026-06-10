package com.livingagent.core.approval.plan;

public record PlanApprovalResponse(
    String requestId,
    String reviewerNeuronId,
    boolean approved,
    String feedback,
    long reviewedAt
) {
    public static PlanApprovalResponse approve(String requestId, String reviewerNeuronId, String feedback) {
        return new PlanApprovalResponse(requestId, reviewerNeuronId, true, feedback, System.currentTimeMillis());
    }

    public static PlanApprovalResponse reject(String requestId, String reviewerNeuronId, String feedback) {
        return new PlanApprovalResponse(requestId, reviewerNeuronId, false, feedback, System.currentTimeMillis());
    }
}
