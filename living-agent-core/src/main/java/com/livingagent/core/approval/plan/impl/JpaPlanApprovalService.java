package com.livingagent.core.approval.plan.impl;

import com.livingagent.core.approval.plan.PlanApprovalRequest;
import com.livingagent.core.approval.plan.PlanApprovalResponse;
import com.livingagent.core.approval.plan.PlanApprovalService;
import com.livingagent.core.database.entity.PlanApprovalRequestEntity;
import com.livingagent.core.database.entity.PlanApprovalRequestEntity.ApprovalStatus;
import com.livingagent.core.database.entity.PlanApprovalRequestEntity.PlanType;
import com.livingagent.core.database.repository.PlanApprovalRequestRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * JPA 持久化版 PlanApprovalService - P2-3 修复。
 * 
 * <p>审批请求存储在数据库，重启后不丢失。</p>
 */
@Service
@Primary
public class JpaPlanApprovalService implements PlanApprovalService {

    private static final Logger log = LoggerFactory.getLogger(JpaPlanApprovalService.class);
    private static final long DEFAULT_DEADLINE_MS = 60_000;

    private final PlanApprovalRequestRepository repository;
    private AtomicLong idCounter;

    public JpaPlanApprovalService(PlanApprovalRequestRepository repository) {
        this.repository = repository;
        // 初始化为默认值，避免在构造函数中查询数据库
        this.idCounter = new AtomicLong(0);
    }

    @PostConstruct
    public void initializeIdCounter() {
        try {
            // 从数据库初始化 ID 计数器(Bean 初始化完成后执行)
            Integer maxId = repository.findMaxRequestId();
            if (maxId != null && maxId > 0) {
                this.idCounter = new AtomicLong(maxId);
            }
            log.info("Initialized JpaPlanApprovalService with max requestId={}", idCounter.get());
        } catch (Exception e) {
            // 如果表不存在或其他异常,使用默认值 0
            log.warn("Failed to initialize id counter from database, using default value 0: {}", e.getMessage());
            this.idCounter = new AtomicLong(0);
        }
    }

    @Override
    @Transactional
    public PlanApprovalRequest submitPlan(String submitterNeuronId, String planText,
                                            PlanApprovalRequest.PlanType planType, long deadlineMs) {
        String requestId = "plan_" + idCounter.incrementAndGet();

        PlanApprovalRequestEntity entity = new PlanApprovalRequestEntity();
        entity.setRequestId(requestId);
        entity.setSubmitterNeuronId(submitterNeuronId);
        entity.setPlanText(planText);
        entity.setPlanType(mapPlanType(planType));
        entity.setDeadlineMs(deadlineMs > 0 ? deadlineMs : DEFAULT_DEADLINE_MS);

        repository.save(entity);

        log.info("Plan approval request submitted: {} by {} (type={}) [JPA]", requestId, submitterNeuronId, planType);
        return toPlanApprovalRequest(entity);
    }

    @Override
    public Optional<PlanApprovalRequest> getRequest(String requestId) {
        return repository.findByRequestId(requestId).map(this::toPlanApprovalRequest);
    }

    @Override
    public List<PlanApprovalRequest> getPendingRequests() {
        return repository.findByStatusOrderBySubmittedAtDesc(ApprovalStatus.PENDING).stream()
            .map(this::toPlanApprovalRequest)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlanApprovalResponse reviewPlan(String requestId, String reviewerNeuronId,
                                             boolean approved, String feedback) {
        PlanApprovalRequestEntity entity = repository.findByRequestId(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (entity.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request " + requestId + " is not PENDING (status=" + entity.getStatus() + ")");
        }

        ApprovalStatus newStatus = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        entity.setStatus(newStatus);
        repository.save(entity);

        PlanApprovalResponse response = approved
            ? PlanApprovalResponse.approve(requestId, reviewerNeuronId, feedback)
            : PlanApprovalResponse.reject(requestId, reviewerNeuronId, feedback);

        log.info("Plan {} {} by {}: {} [JPA]", requestId, approved ? "APPROVED" : "REJECTED",
            reviewerNeuronId, feedback != null ? feedback.substring(0, Math.min(50, feedback.length())) : "");
        return response;
    }

    @Override
    @Transactional
    public PlanApprovalRequest checkAndExpire(String requestId) {
        PlanApprovalRequestEntity entity = repository.findByRequestId(requestId).orElse(null);
        if (entity != null && entity.isExpired()) {
            entity.setStatus(ApprovalStatus.EXPIRED);
            repository.save(entity);
            log.info("Plan approval request {} expired [JPA]", requestId);

            // 返回一个 APPROVED 状态的副本（自动审批）
            PlanApprovalRequestEntity approvedCopy = new PlanApprovalRequestEntity();
            approvedCopy.setRequestId(entity.getRequestId());
            approvedCopy.setSubmitterNeuronId(entity.getSubmitterNeuronId());
            approvedCopy.setPlanText(entity.getPlanText());
            approvedCopy.setPlanType(entity.getPlanType());
            approvedCopy.setStatus(ApprovalStatus.APPROVED);
            approvedCopy.setSubmittedAt(entity.getSubmittedAt());
            approvedCopy.setDeadlineMs(entity.getDeadlineMs());
            return toPlanApprovalRequest(approvedCopy);
        }
        return entity != null ? toPlanApprovalRequest(entity) : null;
    }

    @Override
    @Transactional
    public void cancelRequest(String requestId) {
        repository.findByRequestId(requestId).ifPresent(entity -> {
            if (entity.getStatus() == ApprovalStatus.PENDING) {
                entity.setStatus(ApprovalStatus.EXPIRED);
                repository.save(entity);
                log.info("Plan approval request {} cancelled [JPA]", requestId);
            }
        });
    }

    private PlanApprovalRequest toPlanApprovalRequest(PlanApprovalRequestEntity entity) {
        return new PlanApprovalRequest(
            entity.getRequestId(),
            entity.getSubmitterNeuronId(),
            entity.getPlanText(),
            mapPlanTypeFromEntity(entity.getPlanType()),
            mapStatusFromEntity(entity.getStatus()),
            entity.getSubmittedAt().toEpochMilli(),
            entity.getDeadlineMs()
        );
    }

    private PlanType mapPlanType(PlanApprovalRequest.PlanType planType) {
        return switch (planType) {
            case CODE_CHANGE -> PlanType.CODE_CHANGE;
            case ARCHITECTURE_DECISION -> PlanType.ARCHITECTURE_DECISION;
            case DEPLOYMENT_PLAN -> PlanType.DEPLOYMENT_PLAN;
            case DATA_MIGRATION -> PlanType.DATA_MIGRATION;
            case SECURITY_CHANGE -> PlanType.SECURITY_CHANGE;
            case GENERAL -> PlanType.GENERAL;
        };
    }

    private PlanApprovalRequest.PlanType mapPlanTypeFromEntity(PlanType planType) {
        return switch (planType) {
            case CODE_CHANGE -> PlanApprovalRequest.PlanType.CODE_CHANGE;
            case ARCHITECTURE_DECISION -> PlanApprovalRequest.PlanType.ARCHITECTURE_DECISION;
            case DEPLOYMENT_PLAN -> PlanApprovalRequest.PlanType.DEPLOYMENT_PLAN;
            case DATA_MIGRATION -> PlanApprovalRequest.PlanType.DATA_MIGRATION;
            case SECURITY_CHANGE -> PlanApprovalRequest.PlanType.SECURITY_CHANGE;
            case GENERAL -> PlanApprovalRequest.PlanType.GENERAL;
        };
    }

    private PlanApprovalRequest.ApprovalStatus mapStatusFromEntity(ApprovalStatus status) {
        return switch (status) {
            case PENDING -> PlanApprovalRequest.ApprovalStatus.PENDING;
            case APPROVED -> PlanApprovalRequest.ApprovalStatus.APPROVED;
            case REJECTED -> PlanApprovalRequest.ApprovalStatus.REJECTED;
            case EXPIRED -> PlanApprovalRequest.ApprovalStatus.EXPIRED;
        };
    }
}