package com.livingagent.core.approval.plan;

import java.util.List;
import java.util.Optional;

public interface PlanApprovalService {

    PlanApprovalRequest submitPlan(String submitterNeuronId, String planText,
                                    PlanApprovalRequest.PlanType planType, long deadlineMs);

    Optional<PlanApprovalRequest> getRequest(String requestId);

    List<PlanApprovalRequest> getPendingRequests();

    PlanApprovalResponse reviewPlan(String requestId, String reviewerNeuronId, boolean approved, String feedback);

    PlanApprovalRequest checkAndExpire(String requestId);

    void cancelRequest(String requestId);
}
