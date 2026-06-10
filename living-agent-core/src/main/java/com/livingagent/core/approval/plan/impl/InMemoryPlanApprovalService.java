package com.livingagent.core.approval.plan.impl;

import com.livingagent.core.approval.plan.PlanApprovalRequest;
import com.livingagent.core.approval.plan.PlanApprovalResponse;
import com.livingagent.core.approval.plan.PlanApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryPlanApprovalService implements PlanApprovalService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPlanApprovalService.class);
    private static final long DEFAULT_DEADLINE_MS = 60_000;

    private final Map<String, PlanApprovalRequest> requests = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    @Override
    public PlanApprovalRequest submitPlan(String submitterNeuronId, String planText,
                                            PlanApprovalRequest.PlanType planType, long deadlineMs) {
        String requestId = "plan_" + idCounter.incrementAndGet();
        PlanApprovalRequest request = PlanApprovalRequest.submit(
            requestId, submitterNeuronId, planText, planType,
            deadlineMs > 0 ? deadlineMs : DEFAULT_DEADLINE_MS);
        requests.put(requestId, request);
        log.info("Plan approval request submitted: {} by {} (type={})", requestId, submitterNeuronId, planType);
        return request;
    }

    @Override
    public Optional<PlanApprovalRequest> getRequest(String requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    @Override
    public List<PlanApprovalRequest> getPendingRequests() {
        return requests.values().stream()
            .filter(r -> r.status() == PlanApprovalRequest.ApprovalStatus.PENDING)
            .collect(Collectors.toList());
    }

    @Override
    public PlanApprovalResponse reviewPlan(String requestId, String reviewerNeuronId,
                                             boolean approved, String feedback) {
        PlanApprovalRequest request = requests.get(requestId);
        if (request == null) {
            throw new IllegalArgumentException("Request not found: " + requestId);
        }
        if (request.status() != PlanApprovalRequest.ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request " + requestId + " is not PENDING (status=" + request.status() + ")");
        }

        PlanApprovalRequest.ApprovalStatus newStatus = approved
            ? PlanApprovalRequest.ApprovalStatus.APPROVED
            : PlanApprovalRequest.ApprovalStatus.REJECTED;
        requests.put(requestId, request.withStatus(newStatus));

        PlanApprovalResponse response = approved
            ? PlanApprovalResponse.approve(requestId, reviewerNeuronId, feedback)
            : PlanApprovalResponse.reject(requestId, reviewerNeuronId, feedback);

        log.info("Plan {} {} by {}: {}", requestId, approved ? "APPROVED" : "REJECTED",
            reviewerNeuronId, feedback != null ? feedback.substring(0, Math.min(50, feedback.length())) : "");
        return response;
    }

    @Override
    public PlanApprovalRequest checkAndExpire(String requestId) {
        PlanApprovalRequest request = requests.get(requestId);
        if (request != null && request.isExpired()) {
            PlanApprovalRequest expired = request.withStatus(PlanApprovalRequest.ApprovalStatus.EXPIRED);
            requests.put(requestId, expired);
            log.info("Plan approval request {} expired, auto-approving", requestId);
            return expired.withStatus(PlanApprovalRequest.ApprovalStatus.APPROVED);
        }
        return request;
    }

    @Override
    public void cancelRequest(String requestId) {
        PlanApprovalRequest request = requests.get(requestId);
        if (request != null && request.status() == PlanApprovalRequest.ApprovalStatus.PENDING) {
            requests.put(requestId, request.withStatus(PlanApprovalRequest.ApprovalStatus.EXPIRED));
            log.info("Plan approval request {} cancelled", requestId);
        }
    }
}
