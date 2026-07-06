package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.project.*;
import com.livingagent.core.runtime.RuntimeEventStore;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.security.WorkItemPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final AccessGateService accessGateService;
    private final TaskRepository taskRepository;
    private final WorkItemPermissionService workItemPermissionService;
    private final RuntimeEventStore runtimeEventStore;
    private final UnifiedAuthService unifiedAuthService;

    public ProjectController(ProjectService projectService, AccessGateService accessGateService, 
                             TaskRepository taskRepository,
                             WorkItemPermissionService workItemPermissionService,
                             RuntimeEventStore runtimeEventStore,
                             UnifiedAuthService unifiedAuthService) {
        this.projectService = projectService;
        this.accessGateService = accessGateService;
        this.taskRepository = taskRepository;
        this.workItemPermissionService = workItemPermissionService;
        this.runtimeEventStore = runtimeEventStore;
        this.unifiedAuthService = unifiedAuthService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummary>>> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String manager,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Listing projects, status: {}, department: {}", status, department);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        ProjectService.ProjectQuery query = new ProjectService.ProjectQuery(
                status,
                department,
                manager,
                limit,
                offset
        );

        List<Project> projects = projectService.listProjects(query);
        List<ProjectSummary> summaries = projects.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Project>> createProject(
            @RequestBody CreateProjectRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.info("Creating project: {}", request.name());

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        ProjectService.CreateProjectRequest serviceRequest = new ProjectService.CreateProjectRequest(
                request.name(),
                request.description(),
                request.ownerDepartment(),
                request.managerId()
        );
        Project project = projectService.createProject(serviceRequest);
        return ResponseEntity.ok(ApiResponse.ok(project));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetail>> getProject(
            @PathVariable String projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        log.debug("Getting project: {}", projectId);

        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx != null && !workItemPermissionService.canViewProject(projectId, ctx)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "No permission to view this project"));
        }

        return projectService.getProject(projectId)
                .map(p -> ResponseEntity.ok(ApiResponse.ok(toDetail(p))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Project>> updateProject(
            @PathVariable String projectId,
            @RequestBody UpdateProjectRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx != null && !workItemPermissionService.canEditProject(projectId, ctx)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "No permission to edit this project"));
        }

        log.info("Updating project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    updateProjectFromRequest(p, request);

                    Map<String, Object> eventData = new java.util.LinkedHashMap<>();
                    eventData.put("projectId", projectId);
                    eventData.put("updatedBy", ctx != null ? ctx.getEmployeeId() : employeeId);
                    runtimeEventStore.appendProjectEvent("_system", projectId, "project_updated", eventData);

                    return ResponseEntity.ok(ApiResponse.ok(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable String projectId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx != null && !workItemPermissionService.canManageProject(projectId, ctx)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "No permission to delete this project"));
        }

        log.info("Deleting project: {}", projectId);

        Map<String, Object> eventData = new java.util.LinkedHashMap<>();
        eventData.put("projectId", projectId);
        eventData.put("deletedBy", ctx != null ? ctx.getEmployeeId() : employeeId);
        runtimeEventStore.appendProjectEvent("_system", projectId, "project_deleted", eventData);

        projectService.deleteProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{projectId}/start")
    public ResponseEntity<ApiResponse<Project>> startProject(
            @PathVariable String projectId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Starting project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.start();
                    return ResponseEntity.ok(ApiResponse.ok(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/complete")
    public ResponseEntity<ApiResponse<Project>> completeProject(
            @PathVariable String projectId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Completing project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.complete();
                    return ResponseEntity.ok(ApiResponse.ok(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/hold")
    public ResponseEntity<ApiResponse<Project>> holdProject(
            @PathVariable String projectId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Holding project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.hold();
                    return ResponseEntity.ok(ApiResponse.ok(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/phases/{phase}/advance")
    public ResponseEntity<ApiResponse<Project>> advancePhase(
            @PathVariable String projectId,
            @PathVariable String phase
    ) {
        log.info("Advancing project {} to phase: {}", projectId, phase);

        try {
            Project project = projectService.advancePhase(projectId, phase);
            return ResponseEntity.ok(ApiResponse.ok(project));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/progress")
    public ResponseEntity<ApiResponse<ProjectProgress>> getProgress(
            @PathVariable String projectId
    ) {
        log.debug("Getting project progress: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> ResponseEntity.ok(ApiResponse.ok(buildProgress(p))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Project not found: " + projectId)));
    }

    @PutMapping("/{projectId}/phases/{phase}/progress")
    public ResponseEntity<ApiResponse<Project>> setPhaseProgress(
            @PathVariable String projectId,
            @PathVariable String phase,
            @RequestBody Map<String, Double> request
    ) {
        log.info("Setting phase progress for project {}: phase={}, progress={}", 
                projectId, phase, request.get("progress"));

        try {
            Project project = projectService.setPhaseProgress(
                    projectId, phase, request.getOrDefault("progress", 0.0));
            return ResponseEntity.ok(ApiResponse.ok(project));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ProjectStatistics>> getStatistics() {
        log.debug("Getting project statistics");
        ProjectStatistics stats = projectService.getStatistics();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    // Project Tasks Sub-resource
    @GetMapping("/{projectId}/tasks")
    public ResponseEntity<ApiResponse<List<ProjectTaskInfo>>> getProjectTasks(
            @PathVariable String projectId
    ) {
        log.debug("Getting tasks for project: {}", projectId);
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<ProjectTaskInfo> taskInfos = tasks.stream()
                .map(t -> new ProjectTaskInfo(t.getTaskId(), projectId, t.getDescription(), t.getStatus().toLowerCase(), (int)(t.getPriority() * 10)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(taskInfos));
    }

    @PostMapping("/{projectId}/tasks")
    public ResponseEntity<ApiResponse<ProjectTaskInfo>> createProjectTask(
            @PathVariable String projectId,
            @RequestBody CreateProjectTaskRequest request
    ) {
        log.info("Creating task for project: {}", projectId);
        TaskEntity entity = new TaskEntity();
        entity.setTaskId("task_" + System.currentTimeMillis());
        entity.setProjectId(projectId);
        entity.setDescription(request.name());
        entity.setStatus("PENDING");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        TaskEntity saved = taskRepository.save(entity);
        ProjectTaskInfo task = new ProjectTaskInfo(saved.getTaskId(), projectId, request.name(), "pending", 0);
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @PutMapping("/{projectId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<ProjectTaskInfo>> updateProjectTask(
            @PathVariable String projectId,
            @PathVariable String taskId,
            @RequestBody UpdateProjectTaskRequest request
    ) {
        log.info("Updating task: {} of project: {}", taskId, projectId);
        TaskEntity entity = taskRepository.findByTaskId(taskId).orElse(null);
        if (entity == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Task not found: " + taskId));
        }
        if (request.name() != null) entity.setDescription(request.name());
        if (request.status() != null) entity.setStatus(request.status().toUpperCase());
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
        ProjectTaskInfo task = new ProjectTaskInfo(taskId, projectId, request.name(), request.status(), request.progress());
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @DeleteMapping("/{projectId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteProjectTask(
            @PathVariable String projectId,
            @PathVariable String taskId
    ) {
        log.info("Deleting task: {} of project: {}", taskId, projectId);
        taskRepository.findByTaskId(taskId).ifPresent(taskRepository::delete);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "taskId", taskId)));
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(
                project.getProjectId(),
                project.getName(),
                project.getStatus().name(),
                project.getCurrentPhase().name(),
                project.getOwnerDepartment(),
                project.getManagerId(),
                project.getProgress(),
                project.getStartDate(),
                project.getEndDate(),
                project.getCreatedAt()
        );
    }

    private ProjectDetail toDetail(Project project) {
        return new ProjectDetail(
                project.getProjectId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getCurrentPhase().name(),
                project.getOwnerDepartment(),
                project.getManagerId(),
                project.getProgress(),
                project.getStartDate(),
                project.getEndDate(),
                project.getPhases().stream()
                        .map(this::toPhaseDetail)
                        .collect(Collectors.toList()),
                project.getMetadata(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private PhaseDetail toPhaseDetail(ProjectPhaseRecord record) {
        return new PhaseDetail(
                record.getPhase().name(),
                record.getPhase().getDisplayName(),
                record.getOrder(),
                record.getStatus().name(),
                record.getProgress(),
                record.getStartedAt(),
                record.getCompletedAt()
        );
    }

    private ProjectProgress buildProgress(Project project) {
        return new ProjectProgress(
                project.getProjectId(),
                project.getProgress(),
                project.getCurrentPhase().name(),
                project.getPhases().stream()
                        .collect(Collectors.toMap(
                                p -> p.getPhase().name(),
                                ProjectPhaseRecord::getProgress
                        ))
        );
    }

    private void updateProjectFromRequest(Project project, UpdateProjectRequest request) {
        if (request.name() != null) project.setName(request.name());
        if (request.description() != null) project.setDescription(request.description());
        if (request.managerId() != null) project.setManagerId(request.managerId());
        if (request.startDate() != null) project.setStartDate(request.startDate());
        if (request.endDate() != null) project.setEndDate(request.endDate());
    }

    public record ProjectSummary(
            String id,
            String name,
            String status,
            String currentPhase,
            String department,
            String managerId,
            double progress,
            Instant startDate,
            Instant endDate,
            Instant createdAt
    ) {}

    public record ProjectDetail(
            String id,
            String name,
            String description,
            String status,
            String currentPhase,
            String department,
            String managerId,
            double progress,
            Instant startDate,
            Instant endDate,
            List<PhaseDetail> phases,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PhaseDetail(
            String phase,
            String displayName,
            int order,
            String status,
            double progress,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record ProjectProgress(
            String projectId,
            double overallProgress,
            String currentPhase,
            Map<String, Double> phaseProgress
    ) {}

    public record CreateProjectRequest(
            String name,
            String description,
            String ownerDepartment,
            String managerId
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String managerId,
            Instant startDate,
            Instant endDate
    ) {}

    public record ProjectTaskInfo(
            String id,
            String projectId,
            String name,
            String status,
            int progress
    ) {}

    public record CreateProjectTaskRequest(
            String name,
            String description
    ) {}

    public record UpdateProjectTaskRequest(
            String name,
            String status,
            int progress
    ) {}

    private AuthContext resolveAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        if (token.isBlank()) {
            return null;
        }
        return unifiedAuthService.validateSession(token)
            .map(AuthSession::authContext)
            .orElse(null);
    }
}
