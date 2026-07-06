package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.review.InternalReviewService;
import com.livingagent.core.autonomy.review.ReviewState;
import com.livingagent.core.database.entity.DepartmentDeliverableEntity;
import com.livingagent.core.database.repository.DepartmentDeliverableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JPA 持久化实现的部门聚合服务。
 * 交付物数据存储在 PostgreSQL，重启不丢失。
 */
public class JpaDepartmentAggregationService implements DepartmentAggregationService {

    private static final Logger log = LoggerFactory.getLogger(JpaDepartmentAggregationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final EmployeeExecutionReceiptService receiptService;
    private final InternalReviewService internalReviewService;
    private final DepartmentTodoPool todoPool;
    private final DepartmentDeliverableRepository deliverableRepository;

    public JpaDepartmentAggregationService(EmployeeExecutionReceiptService receiptService,
                                            InternalReviewService internalReviewService,
                                            DepartmentTodoPool todoPool,
                                            DepartmentDeliverableRepository deliverableRepository) {
        this.receiptService = receiptService;
        this.internalReviewService = internalReviewService;
        this.todoPool = todoPool;
        this.deliverableRepository = deliverableRepository;
    }

    @Override
    @Transactional
    public AggregationResult aggregate(String department, String planId, String objective) {
        log.info("Aggregating department results: department={}, planId={}", department, planId);
        List<EmployeeExecutionReceipt> receipts = receiptService.getReceiptsByDepartment(department);
        return aggregateFromReceipts(department, planId, objective, receipts);
    }

    @Transactional
    public AggregationResult aggregateByExecutionId(String department, String planId,
                                                     String objective, String executionId) {
        List<EmployeeExecutionReceipt> receipts = receiptService.getReceipts(executionId);
        return aggregateFromReceipts(department, planId, objective, receipts);
    }

    private AggregationResult aggregateFromReceipts(String department, String planId,
                                                     String objective,
                                                     List<EmployeeExecutionReceipt> receipts) {
        List<DepartmentDeliverable.DeliverableItem> items = new ArrayList<>();
        List<String> uncompletedItems = new ArrayList<>();
        List<String> qualityIssues = new ArrayList<>();

        for (EmployeeExecutionReceipt receipt : receipts) {
            boolean reviewPassed = true;
            double qualityScore = 1.0;

            if (internalReviewService != null) {
                String assignmentId = receipt.assignmentId();
                Optional<ReviewState> reviewState = internalReviewService.getReviewState(assignmentId);
                if (reviewState.isPresent()) {
                    reviewPassed = reviewState.get() == ReviewState.COMPLETED;
                    if (!reviewPassed) {
                        qualityIssues.add("Assignment " + assignmentId + " review state: " + reviewState.get());
                    }
                }
            }

            if (receipt.status() == ReceiptStatus.COMPLETED || receipt.status() == ReceiptStatus.DEGRADED) {
                String taskType = receipt.metadata() != null
                    ? (String) receipt.metadata().getOrDefault("task_type", "unknown")
                    : "unknown";
                @SuppressWarnings("unchecked")
                List<String> artifactPaths = receipt.metadata() != null && receipt.metadata().containsKey("artifact_paths")
                    ? (List<String>) receipt.metadata().get("artifact_paths")
                    : List.of();

                items.add(new DepartmentDeliverable.DeliverableItem(
                    receipt.employeeCode(),
                    receipt.employeeCode(),
                    taskType,
                    receipt.summary(),
                    reviewPassed,
                    qualityScore,
                    artifactPaths
                ));
            } else {
                uncompletedItems.add(receipt.employeeCode() + ":" + receipt.assignmentId());
            }
        }

        List<DepartmentTodoItem> pendingTodos = todoPool.getPendingByDepartment(department);
        for (DepartmentTodoItem todo : pendingTodos) {
            uncompletedItems.add("Todo:" + todo.getId());
        }

        double overallQuality = items.isEmpty() ? 0.0 :
            items.stream().mapToDouble(DepartmentDeliverable.DeliverableItem::qualityScore).average().orElse(0.0);

        DepartmentDeliverable.AggregationStatus status;
        if (items.isEmpty()) {
            status = DepartmentDeliverable.AggregationStatus.INCOMPLETE;
        } else if (!uncompletedItems.isEmpty()) {
            status = DepartmentDeliverable.AggregationStatus.PARTIAL;
        } else if (!qualityIssues.isEmpty()) {
            status = DepartmentDeliverable.AggregationStatus.QUALITY_ISSUES;
        } else {
            status = DepartmentDeliverable.AggregationStatus.COMPLETE;
        }

        String deliverableId = "deliverable-" + department + "-" + System.currentTimeMillis();
        String summary = String.format("部门 %s 聚合结果: %d 项完成, %d 项未完成, %d 个质量问题, 整体质量分=%.2f",
            department, items.size(), uncompletedItems.size(), qualityIssues.size(), overallQuality);

        DepartmentDeliverable deliverable = new DepartmentDeliverable(
            deliverableId, department, planId, objective, status,
            items, summary, qualityIssues, overallQuality, Instant.now()
        );

        // 持久化到 DB
        saveToDb(deliverable);

        log.info("Department aggregation completed: department={}, status={}, items={}", department, status, items.size());

        if (status == DepartmentDeliverable.AggregationStatus.COMPLETE) {
            return AggregationResult.success(deliverable);
        } else if (status == DepartmentDeliverable.AggregationStatus.PARTIAL) {
            return AggregationResult.partial(deliverable, uncompletedItems);
        } else {
            return AggregationResult.qualityIssues(deliverable, qualityIssues);
        }
    }

    @Override
    public Optional<DepartmentDeliverable> getDeliverable(String deliverableId) {
        return deliverableRepository.findByDeliverableId(deliverableId).map(this::toDeliverable);
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByDepartment(String department) {
        return deliverableRepository.findByDepartmentOrderByDeliveredAtDesc(department).stream()
            .map(this::toDeliverable).toList();
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByPlan(String planId) {
        return deliverableRepository.findByPlanIdOrderByDeliveredAtDesc(planId).stream()
            .map(this::toDeliverable).toList();
    }

    private void saveToDb(DepartmentDeliverable deliverable) {
        try {
            DepartmentDeliverableEntity entity = new DepartmentDeliverableEntity();
            entity.setDeliverableId(deliverable.deliverableId());
            entity.setDepartment(deliverable.department());
            entity.setPlanId(deliverable.planId());
            entity.setObjective(deliverable.objective());
            entity.setStatus(deliverable.status().name());
            entity.setItemsJson(objectMapper.writeValueAsString(deliverable.items()));
            entity.setSummary(deliverable.summary());
            entity.setIssuesJson(objectMapper.writeValueAsString(deliverable.issues()));
            entity.setOverallQualityScore(deliverable.overallQualityScore());
            entity.setDeliveredAt(deliverable.deliveredAt());
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            deliverableRepository.save(entity);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize deliverable items/issues: {}", e.getMessage());
        }
    }

    private DepartmentDeliverable toDeliverable(DepartmentDeliverableEntity entity) {
        List<DepartmentDeliverable.DeliverableItem> items = List.of();
        List<String> issues = List.of();
        try {
            if (entity.getItemsJson() != null) {
                items = objectMapper.readValue(entity.getItemsJson(), new TypeReference<>() {});
            }
            if (entity.getIssuesJson() != null) {
                issues = objectMapper.readValue(entity.getIssuesJson(), new TypeReference<>() {});
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize deliverable data: {}", e.getMessage());
        }
        return new DepartmentDeliverable(
            entity.getDeliverableId(), entity.getDepartment(), entity.getPlanId(),
            entity.getObjective(), DepartmentDeliverable.AggregationStatus.valueOf(entity.getStatus()),
            items, entity.getSummary(), issues,
            entity.getOverallQualityScore() != null ? entity.getOverallQualityScore() : 0.0,
            entity.getDeliveredAt()
        );
    }
}
