package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.AssignmentPreparationService;
import com.livingagent.core.autonomy.DepartmentTaskPlan;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.ExecutionCapability;
import com.livingagent.core.autonomy.ArtifactType;
import com.livingagent.core.autonomy.ExecutionMode;
import com.livingagent.core.autonomy.ExecutionCapabilityRequest;
import com.livingagent.core.autonomy.ExecutionCapabilityResolution;
import com.livingagent.core.autonomy.ExecutionCapabilityResolver;
import com.livingagent.core.autonomy.MainBrainTaskPlan;
import com.livingagent.core.autonomy.PreparedAssignmentBatch;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DefaultAssignmentPreparationService implements AssignmentPreparationService {

    private final ExecutionCapabilityResolver capabilityResolver;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;

    public DefaultAssignmentPreparationService() {
        this.capabilityResolver = null;
        this.fixedEmployeeRegistry = null;
    }

    public DefaultAssignmentPreparationService(ExecutionCapabilityResolver capabilityResolver) {
        this.capabilityResolver = capabilityResolver;
        this.fixedEmployeeRegistry = null;
    }

    public DefaultAssignmentPreparationService(ExecutionCapabilityResolver capabilityResolver,
                                                FixedEmployeeRegistry fixedEmployeeRegistry) {
        this.capabilityResolver = capabilityResolver;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
    }

    @Override
    public PreparedAssignmentBatch prepare(
            String requestId,
            String sessionId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            List<EmployeeWorkAssignment> assignments) {
        List<EmployeeWorkAssignment> safeAssignments = assignments == null ? List.of() : List.copyOf(assignments);

        // P0-5 新增：解析 executionCapability 并写入每个 assignment 的 context
        ExecutionCapability resolvedCapability = mainBrainTaskPlan.executionCapability();
        ArtifactType resolvedArtifactType = mainBrainTaskPlan.artifactType();
        ExecutionMode resolvedExecutionMode = mainBrainTaskPlan.executionCapability() != null
            ? mainBrainTaskPlan.executionMode() : null;

        // 如果 MainBrainTaskPlan 中没有解析过，使用 capabilityResolver 实时解析
        if (resolvedCapability == null && capabilityResolver != null) {
            ExecutionCapabilityRequest capRequest = ExecutionCapabilityRequest.of(
                null, mainBrainTaskPlan.taskType(), null,
                mainBrainTaskPlan.deliverables(), null, department, null);
            ExecutionCapabilityResolution resolution = capabilityResolver.resolve(capRequest);
            if (resolution.executionCapability() != null) {
                resolvedCapability = resolution.executionCapability();
                resolvedArtifactType = resolution.artifactType();
                resolvedExecutionMode = resolution.executionMode();
            }
        }

        // 将 capability 信息和审查配置写入每个 assignment
        final ExecutionCapability cap = resolvedCapability;
        final ArtifactType artType = resolvedArtifactType;
        final ExecutionMode execMode = resolvedExecutionMode;
        List<EmployeeWorkAssignment> enrichedAssignments = safeAssignments.stream()
            .map(a -> enrichAssignmentWithContext(a, cap, artType, execMode))
            .map(a -> enrichAssignmentWithReview(a))
            .collect(Collectors.toList());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planId", mainBrainTaskPlan.planId());
        metadata.put("department", department);
        metadata.put("departmentObjective", departmentTaskPlan.objective());
        metadata.put("assignmentCount", String.valueOf(enrichedAssignments.size()));
        metadata.put("employeeCodes", enrichedAssignments.stream()
            .map(EmployeeWorkAssignment::employeeCode)
            .collect(Collectors.joining(",")));
        metadata.put("employeeNeuronIds", enrichedAssignments.stream()
            .map(EmployeeWorkAssignment::employeeNeuronId)
            .collect(Collectors.joining(",")));
        metadata.put("roles", enrichedAssignments.stream()
            .map(EmployeeWorkAssignment::role)
            .collect(Collectors.joining(",")));
        metadata.put("executionReadiness", enrichedAssignments.isEmpty() ? "NO_ASSIGNMENT" : "READY_FOR_DEPARTMENT_COORDINATION");
        if (cap != null) {
            metadata.put("executionCapability", cap.name());
            metadata.put("artifactType", artType != null ? artType.name() : null);
            metadata.put("executionMode", execMode != null ? execMode.name() : null);
        }

        return new PreparedAssignmentBatch(
            UUID.randomUUID().toString(),
            requestId,
            sessionId,
            department,
            mainBrainTaskPlan.taskType(),
            mainBrainTaskPlan.goal(),
            enrichedAssignments,
            metadata
        );
    }

    /**
     * 将 executionCapability/artifactType/executionMode 写入 assignment context。
     */
    private EmployeeWorkAssignment enrichAssignmentWithContext(
            EmployeeWorkAssignment assignment,
            ExecutionCapability capability,
            ArtifactType artifactType,
            ExecutionMode executionMode) {
        if (capability == null) return assignment;

        Map<String, Object> enrichedContext = new LinkedHashMap<>(assignment.context() != null ? assignment.context() : Map.of());
        enrichedContext.put("executionCapability", capability.name());
        if (artifactType != null) enrichedContext.put("artifactType", artifactType.name());
        if (executionMode != null) enrichedContext.put("executionMode", executionMode.name());

        return new EmployeeWorkAssignment(
            assignment.assignmentId(),
            assignment.department(),
            assignment.employeeCode(),
            assignment.employeeNeuronId(),
            assignment.employeeName(),
            assignment.role(),
            assignment.objective(),
            assignment.instruction(),
            assignment.expectedDeliverables(),
            assignment.allowedTools(),
            enrichedContext,
            assignment.worktreePath(),
            assignment.diffPath(),
            assignment.reviewRequired(),
            assignment.reviewerCode(),
            assignment.maxReviewRounds()
        );
    }

    /**
     * 根据 FixedEmployeeDefinition 的 downstreamReviewers 设置审查字段。
     */
    private EmployeeWorkAssignment enrichAssignmentWithReview(EmployeeWorkAssignment assignment) {
        if (fixedEmployeeRegistry == null) return assignment;
        if (assignment.reviewRequired()) return assignment; // 已设置审查，跳过

        return fixedEmployeeRegistry.getDefinitionByCode(assignment.employeeCode())
            .filter(def -> def.downstreamReviewers() != null && !def.downstreamReviewers().isEmpty())
            .map(def -> assignment.withReview(def.downstreamReviewers().get(0), 3))
            .orElse(assignment);
    }
}
