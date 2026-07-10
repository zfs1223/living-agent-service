package com.livingagent.core.approval.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.approval.*;
import com.livingagent.core.database.entity.ApprovalInstanceEntity;
import com.livingagent.core.database.entity.ApprovalWorkflowEntity;
import com.livingagent.core.database.entity.ApprovalAuditLogEntity;
import com.livingagent.core.database.repository.ApprovalInstanceRepository;
import com.livingagent.core.database.repository.ApprovalWorkflowRepository;
import com.livingagent.core.database.repository.ApprovalAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 审批服务 DB 持久化实现。
 * 原 ApprovalServiceImpl 使用内存 Map（approvalStore/workflowStore）存储，重启丢失。
 * 现改为 PostgreSQL 持久化：approval_instances / approval_workflows 两张表。
 * records/steps 序列化为 JSON 存储，避免引入额外子表。
 */
@Service
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final ApprovalAuditLogRepository approvalAuditLogRepository;
    private final List<ApprovalCallback> callbacks = new CopyOnWriteArrayList<>();

    public ApprovalServiceImpl(ApprovalInstanceRepository approvalInstanceRepository,
                                ApprovalWorkflowRepository approvalWorkflowRepository,
                                ApprovalAuditLogRepository approvalAuditLogRepository) {
        this.approvalInstanceRepository = approvalInstanceRepository;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.approvalAuditLogRepository = approvalAuditLogRepository;
    }

    /**
     * 启动时确保默认工作流存在（与原 initializeDefaultWorkflows 行为对齐）。
     */
    @PostConstruct
    public void initializeDefaultWorkflows() {
        ensureDefaultWorkflow("default", "默认审批流程", "单级审批流程",
            List.of(new ApprovalStep("step_1", "部门主管审批", 0)));
        ensureDefaultWorkflow("project_approval", "项目审批流程",
            "三级审批流程：部门主管 → 财务部 → 董事长",
            List.of(
                new ApprovalStep("step_1", "部门主管审批", 0),
                new ApprovalStep("step_2", "财务部审批", 1),
                new ApprovalStep("step_3", "董事长审批", 2)
            ));
        ensureDefaultWorkflow("expense_approval", "报销审批流程",
            "两级审批流程：部门主管 → 财务部",
            List.of(
                new ApprovalStep("step_1", "部门主管审批", 0),
                new ApprovalStep("step_2", "财务部审批", 1)
            ));
    }

    private void ensureDefaultWorkflow(String workflowId, String name, String description, List<ApprovalStep> steps) {
        try {
            if (approvalWorkflowRepository.existsByWorkflowId(workflowId)) {
                return;
            }
            ApprovalWorkflow workflow = new ApprovalWorkflow(workflowId, name);
            workflow.setDescription(description);
            workflow.setSteps(steps);
            ApprovalWorkflowEntity entity = toEntity(workflow);
            approvalWorkflowRepository.save(entity);
            log.info("Default approval workflow persisted: {}", workflowId);
        } catch (Exception e) {
            // 表不存在或其他数据库异常,延迟初始化
            log.warn("Failed to ensure default workflow '{}' during startup (table may not exist yet): {}", 
                workflowId, e.getMessage());
        }
    }

    @Override
    @Transactional
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

        ApprovalInstanceEntity entity = toEntity(instance);
        approvalInstanceRepository.save(entity);
        recordAudit(instance, "CREATE", null, request.submitterId(), null);
        log.info("Approval instance created: instanceId={}, workflowId={}", instance.getInstanceId(), request.workflowId());
        return instance;
    }

    @Override
    @Transactional
    public Optional<ApprovalInstance> getApproval(String instanceId) {
        return approvalInstanceRepository.findByInstanceId(instanceId).map(this::toDomain);
    }

    @Override
    @Transactional
    public List<ApprovalInstance> getPendingApprovals(String approverId) {
        // PENDING / IN_PROGRESS 均视为待审批
        List<String> pendingStatuses = List.of(
            ApprovalInstance.ApprovalStatus.PENDING.name(),
            ApprovalInstance.ApprovalStatus.IN_PROGRESS.name()
        );
        return approvalInstanceRepository.findByStatusIn(pendingStatuses).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<ApprovalInstance> getMyApprovals(String submitterId, String status) {
        List<ApprovalInstanceEntity> entities = (status == null || status.isBlank())
            ? approvalInstanceRepository.findBySubmitterId(submitterId)
            : approvalInstanceRepository.findBySubmitterIdAndStatus(submitterId, status);
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApprovalInstance approve(String instanceId, String approverId, String comment) {
        ApprovalInstanceEntity entity = loadInstanceOrThrow(instanceId);
        ApprovalInstance instance = toDomain(entity);
        ApprovalWorkflow workflow = loadWorkflowOrThrow(instance.getWorkflowId());

        int currentStep = instance.getCurrentStep();
        List<ApprovalStep> steps = workflow.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("Workflow has no steps: " + instance.getWorkflowId());
        }
        if (currentStep >= steps.size()) {
            throw new IllegalStateException("Approval already completed: " + instanceId);
        }
        String stepId = steps.get(currentStep).getStepId();
        instance.approve(stepId, approverId, comment);

        if (instance.getCurrentStep() >= steps.size()) {
            instance.complete();
        }
        // 写回 DB
        ApprovalInstanceEntity updated = toEntity(instance);
        updated.setId(entity.getId());
        approvalInstanceRepository.save(updated);

        recordAudit(instance, "APPROVE", stepId, approverId, comment);

        if (instance.getStatus() == ApprovalInstance.ApprovalStatus.APPROVED) {
            fireApprovedCallbacks(instance);
        }
        return instance;
    }

    @Override
    @Transactional
    public ApprovalInstance reject(String instanceId, String approverId, String comment) {
        ApprovalInstanceEntity entity = loadInstanceOrThrow(instanceId);
        ApprovalInstance instance = toDomain(entity);
        ApprovalWorkflow workflow = loadWorkflowOrThrow(instance.getWorkflowId());

        int currentStep = instance.getCurrentStep();
        List<ApprovalStep> steps = workflow.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("Workflow has no steps: " + instance.getWorkflowId());
        }
        String stepId = steps.get(currentStep).getStepId();
        instance.reject(stepId, approverId, comment);

        ApprovalInstanceEntity updated = toEntity(instance);
        updated.setId(entity.getId());
        approvalInstanceRepository.save(updated);

        recordAudit(instance, "REJECT", stepId, approverId, comment);

        fireRejectedCallbacks(instance);
        return instance;
    }

    @Override
    @Transactional
    public ApprovalInstance returnToSubmitter(String instanceId, String approverId, String comment) {
        ApprovalInstanceEntity entity = loadInstanceOrThrow(instanceId);
        ApprovalInstance instance = toDomain(entity);
        ApprovalWorkflow workflow = loadWorkflowOrThrow(instance.getWorkflowId());

        int currentStep = instance.getCurrentStep();
        List<ApprovalStep> steps = workflow.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("Workflow has no steps: " + instance.getWorkflowId());
        }
        String stepId = steps.get(currentStep).getStepId();
        instance.returnToSubmitter(stepId, approverId, comment);

        ApprovalInstanceEntity updated = toEntity(instance);
        updated.setId(entity.getId());
        approvalInstanceRepository.save(updated);

        recordAudit(instance, "RETURN", stepId, approverId, comment);
        return instance;
    }

    @Override
    @Transactional
    public void cancel(String instanceId, String submitterId) {
        ApprovalInstanceEntity entity = loadInstanceOrThrow(instanceId);
        ApprovalInstance instance = toDomain(entity);

        if (!submitterId.equals(instance.getSubmitterId())) {
            throw new IllegalStateException("Only submitter can cancel the approval");
        }
        instance.cancel();
        ApprovalInstanceEntity updated = toEntity(instance);
        updated.setId(entity.getId());
        approvalInstanceRepository.save(updated);

        recordAudit(instance, "CANCEL", null, submitterId, null);
    }

    @Override
    @Transactional
    public List<ApprovalRecord> getHistory(String instanceId) {
        return approvalInstanceRepository.findByInstanceId(instanceId)
            .map(entity -> toDomain(entity).getRecords())
            .orElse(List.of());
    }

    @Override
    @Transactional
    public ApprovalWorkflow createWorkflow(CreateWorkflowRequest request) {
        ApprovalWorkflow workflow = new ApprovalWorkflow(request.workflowId(), request.name());
        workflow.setDescription(request.description());
        workflow.setSteps(request.steps());
        ApprovalWorkflowEntity entity = toEntity(workflow);
        approvalWorkflowRepository.save(entity);
        return workflow;
    }

    @Override
    @Transactional
    public Optional<ApprovalWorkflow> getWorkflow(String workflowId) {
        return approvalWorkflowRepository.findByWorkflowId(workflowId).map(this::toDomain);
    }

    @Override
    @Transactional
    public List<ApprovalWorkflow> listWorkflows() {
        return approvalWorkflowRepository.findAll().stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void registerCallback(ApprovalCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    private void fireApprovedCallbacks(ApprovalInstance instance) {
        for (ApprovalCallback callback : callbacks) {
            try {
                callback.onApproved(instance);
            } catch (Exception e) {
                log.warn("ApprovalCallback.onApproved failed: {}", e.getMessage());
            }
        }
    }

    private void fireRejectedCallbacks(ApprovalInstance instance) {
        for (ApprovalCallback callback : callbacks) {
            try {
                callback.onRejected(instance);
            } catch (Exception e) {
                log.warn("ApprovalCallback.onRejected failed: {}", e.getMessage());
            }
        }
    }

    /** 16.6: 记录审批审计日志 */
    private void recordAudit(ApprovalInstance instance, String action, String stepId, String operatorId, String comment) {
        try {
            approvalAuditLogRepository.save(ApprovalAuditLogEntity.of(
                instance.getInstanceId(),
                instance.getWorkflowId(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                action,
                stepId,
                operatorId,
                comment,
                instance.getStatus() != null ? instance.getStatus().name() : null
            ));
        } catch (Exception e) {
            log.warn("Failed to record approval audit log for instance={}: {}", instance.getInstanceId(), e.getMessage());
        }
    }

    // ========== Domain <-> Entity 转换 ==========

    private ApprovalInstanceEntity loadInstanceOrThrow(String instanceId) {
        return approvalInstanceRepository.findByInstanceId(instanceId)
            .orElseThrow(() -> new IllegalArgumentException("Approval instance not found: " + instanceId));
    }

    private ApprovalWorkflow loadWorkflowOrThrow(String workflowId) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findByWorkflowId(workflowId)
            .map(this::toDomain)
            .orElse(null);
        if (workflow == null) {
            workflow = approvalWorkflowRepository.findByWorkflowId("default")
                .map(this::toDomain)
                .orElse(null);
        }
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return workflow;
    }

    private ApprovalInstanceEntity toEntity(ApprovalInstance instance) {
        ApprovalInstanceEntity entity = new ApprovalInstanceEntity();
        entity.setInstanceId(instance.getInstanceId());
        entity.setWorkflowId(instance.getWorkflowId());
        entity.setBusinessType(instance.getBusinessType());
        entity.setBusinessId(instance.getBusinessId());
        entity.setTitle(instance.getTitle());
        entity.setDescription(instance.getDescription());
        entity.setStatus(instance.getStatus() != null ? instance.getStatus().name() : ApprovalInstance.ApprovalStatus.PENDING.name());
        entity.setCurrentStep(instance.getCurrentStep());
        entity.setSubmitterId(instance.getSubmitterId());
        entity.setRecordsJson(serializeList(instance.getRecords()));
        entity.setContextJson(serializeMap(instance.getContext()));
        entity.setCreatedAt(instance.getCreatedAt() != null ? instance.getCreatedAt() : entity.getCreatedAt());
        entity.setCompletedAt(instance.getCompletedAt());
        return entity;
    }

    private ApprovalInstance toDomain(ApprovalInstanceEntity entity) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceId(entity.getInstanceId());
        instance.setWorkflowId(entity.getWorkflowId());
        instance.setBusinessType(entity.getBusinessType());
        instance.setBusinessId(entity.getBusinessId());
        instance.setTitle(entity.getTitle());
        instance.setDescription(entity.getDescription());
        try {
            instance.setStatus(ApprovalInstance.ApprovalStatus.valueOf(entity.getStatus()));
        } catch (Exception e) {
            log.warn("Invalid approval status in DB: {}, defaulting to PENDING", entity.getStatus());
            instance.setStatus(ApprovalInstance.ApprovalStatus.PENDING);
        }
        instance.setCurrentStep(entity.getCurrentStep());
        instance.setSubmitterId(entity.getSubmitterId());
        instance.setRecords(deserializeRecords(entity.getRecordsJson()));
        instance.setContext(deserializeMap(entity.getContextJson()));
        instance.setCreatedAt(entity.getCreatedAt());
        instance.setCompletedAt(entity.getCompletedAt());
        return instance;
    }

    private ApprovalWorkflowEntity toEntity(ApprovalWorkflow workflow) {
        ApprovalWorkflowEntity entity = new ApprovalWorkflowEntity();
        entity.setWorkflowId(workflow.getWorkflowId());
        entity.setName(workflow.getName());
        entity.setDescription(workflow.getDescription());
        entity.setStepsJson(serializeList(workflow.getSteps()));
        entity.setEnabled(workflow.isEnabled());
        entity.setCreatedAt(workflow.getCreatedAt() != null ? workflow.getCreatedAt() : entity.getCreatedAt());
        return entity;
    }

    private ApprovalWorkflow toDomain(ApprovalWorkflowEntity entity) {
        ApprovalWorkflow workflow = new ApprovalWorkflow(entity.getWorkflowId(), entity.getName());
        workflow.setDescription(entity.getDescription());
        workflow.setSteps(deserializeSteps(entity.getStepsJson()));
        workflow.setEnabled(entity.isEnabled());
        workflow.setCreatedAt(entity.getCreatedAt());
        return workflow;
    }

    // ========== JSON 序列化/反序列化 ==========

    private String serializeList(List<?> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list: {}", e.getMessage());
            return null;
        }
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize map: {}", e.getMessage());
            return null;
        }
    }

    private List<ApprovalRecord> deserializeRecords(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalRecord>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize approval records: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ApprovalStep> deserializeSteps(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalStep>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize approval steps: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize map: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
