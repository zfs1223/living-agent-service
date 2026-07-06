package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.review.InternalReviewService;
import com.livingagent.core.autonomy.review.ReviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认部门级聚合服务实现（规则版）。
 *
 * <p>检查所有子任务完成情况、审查状态、成果一致性，
 * 打包为 DepartmentDeliverable 交付主脑。
 * 后续可替换为 LLM 版本进行深度质量分析。
 * 
 * <p>注意：此类不使用 @Service 注解，由 GatewayConfig.departmentAggregationService() 方法
 * 通过 @Bean 方式注册，确保构造函数参数正确注入。
 */
public class DefaultDepartmentAggregationService implements DepartmentAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDepartmentAggregationService.class);

    private final EmployeeExecutionReceiptService receiptService;
    private final InternalReviewService internalReviewService;
    private final DepartmentTodoPool todoPool;
    private final Map<String, DepartmentDeliverable> deliverablesById = new ConcurrentHashMap<>();

    public DefaultDepartmentAggregationService(EmployeeExecutionReceiptService receiptService,
                                                InternalReviewService internalReviewService,
                                                DepartmentTodoPool todoPool) {
        this.receiptService = receiptService;
        this.internalReviewService = internalReviewService;
        this.todoPool = todoPool;
    }

    @Override
    public AggregationResult aggregate(String department, String planId, String objective) {
        log.info("Aggregating department results: department={}, planId={}", department, planId);

        // 1. 收集部门内所有回执（通过 department 查询）
        List<EmployeeExecutionReceipt> receipts = receiptService.getReceiptsByDepartment(department);
        return aggregateFromReceipts(department, planId, objective, receipts);
    }

    /**
     * 基于执行ID聚合部门成果。
     */
    public AggregationResult aggregateByExecutionId(String department, String planId,
                                                     String objective, String executionId) {
        log.info("Aggregating department results: department={}, planId={}, executionId={}",
            department, planId, executionId);

        List<EmployeeExecutionReceipt> receipts = receiptService.getReceipts(executionId);
        return aggregateFromReceipts(department, planId, objective, receipts);
    }

    private AggregationResult aggregateFromReceipts(String department, String planId,
                                                     String objective,
                                                     List<EmployeeExecutionReceipt> receipts) {

        // 2. 构建交付项
        List<DepartmentDeliverable.DeliverableItem> items = new ArrayList<>();
        List<String> uncompletedItems = new ArrayList<>();
        List<String> qualityIssues = new ArrayList<>();

        for (EmployeeExecutionReceipt receipt : receipts) {
            // 检查审查状态
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

            // 只包含已完成的回执
            if (receipt.status() == ReceiptStatus.COMPLETED || receipt.status() == ReceiptStatus.DEGRADED) {
                // 从 metadata 中提取 taskType 和 artifactPaths
                String taskType = receipt.metadata() != null
                    ? (String) receipt.metadata().getOrDefault("task_type", "unknown")
                    : "unknown";
                @SuppressWarnings("unchecked")
                List<String> artifactPaths = receipt.metadata() != null && receipt.metadata().containsKey("artifact_paths")
                    ? (List<String>) receipt.metadata().get("artifact_paths")
                    : List.of();

                items.add(new DepartmentDeliverable.DeliverableItem(
                    receipt.employeeCode(),
                    receipt.employeeCode(), // employeeName 不在 receipt 中，用 code 代替
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

        // 3. 检查待办池中是否还有未完成项
        List<DepartmentTodoItem> pendingTodos = todoPool.getPendingByDepartment(department);
        for (DepartmentTodoItem todo : pendingTodos) {
            uncompletedItems.add("Todo:" + todo.getId());
        }

        // 4. 计算整体质量分
        double overallQuality = items.isEmpty() ? 0.0 :
            items.stream().mapToDouble(DepartmentDeliverable.DeliverableItem::qualityScore).average().orElse(0.0);

        // 5. 确定聚合状态
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

        // 6. 构建交付物
        String deliverableId = "deliverable-" + department + "-" + System.currentTimeMillis();
        String summary = String.format("部门 %s 聚合结果: %d 项完成, %d 项未完成, %d 个质量问题, 整体质量分=%.2f",
            department, items.size(), uncompletedItems.size(), qualityIssues.size(), overallQuality);

        DepartmentDeliverable deliverable = new DepartmentDeliverable(
            deliverableId, department, planId, objective, status,
            items, summary, qualityIssues, overallQuality, Instant.now()
        );

        deliverablesById.put(deliverableId, deliverable);

        log.info("Department aggregation completed: department={}, status={}, items={}, uncompleted={}, qualityIssues={}",
            department, status, items.size(), uncompletedItems.size(), qualityIssues.size());

        // 7. 返回结果
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
        return Optional.ofNullable(deliverablesById.get(deliverableId));
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByDepartment(String department) {
        return deliverablesById.values().stream()
            .filter(d -> d.department().equals(department))
            .collect(Collectors.toList());
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByPlan(String planId) {
        return deliverablesById.values().stream()
            .filter(d -> planId.equals(d.planId()))
            .collect(Collectors.toList());
    }
}
