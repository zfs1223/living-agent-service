package com.livingagent.gateway.controller;

import com.livingagent.core.project.*;
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

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummary>>> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String manager,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        log.debug("Listing projects, status: {}, department: {}", status, department);

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

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Project>> createProject(
            @RequestBody CreateProjectRequest request
    ) {
        log.info("Creating project: {}", request.name());

        ProjectService.CreateProjectRequest serviceRequest = new ProjectService.CreateProjectRequest(
                request.name(),
                request.description(),
                request.ownerDepartment(),
                request.managerId()
        );
        Project project = projectService.createProject(serviceRequest);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetail>> getProject(
            @PathVariable String projectId
    ) {
        log.debug("Getting project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> ResponseEntity.ok(ApiResponse.success(toDetail(p))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Project>> updateProject(
            @PathVariable String projectId,
            @RequestBody UpdateProjectRequest request
    ) {
        log.info("Updating project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    updateProjectFromRequest(p, request);
                    return ResponseEntity.ok(ApiResponse.success(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable String projectId
    ) {
        log.info("Deleting project: {}", projectId);

        projectService.deleteProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{projectId}/start")
    public ResponseEntity<ApiResponse<Project>> startProject(
            @PathVariable String projectId
    ) {
        log.info("Starting project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.start();
                    return ResponseEntity.ok(ApiResponse.success(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/complete")
    public ResponseEntity<ApiResponse<Project>> completeProject(
            @PathVariable String projectId
    ) {
        log.info("Completing project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.complete();
                    return ResponseEntity.ok(ApiResponse.success(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/hold")
    public ResponseEntity<ApiResponse<Project>> holdProject(
            @PathVariable String projectId
    ) {
        log.info("Holding project: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> {
                    p.hold();
                    return ResponseEntity.ok(ApiResponse.success(p));
                })
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
    }

    @PostMapping("/{projectId}/phases/{phase}/advance")
    public ResponseEntity<ApiResponse<Project>> advancePhase(
            @PathVariable String projectId,
            @PathVariable String phase
    ) {
        log.info("Advancing project {} to phase: {}", projectId, phase);

        try {
            Project project = projectService.advancePhase(projectId, phase);
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/progress")
    public ResponseEntity<ApiResponse<ProjectProgress>> getProgress(
            @PathVariable String projectId
    ) {
        log.debug("Getting project progress: {}", projectId);

        return projectService.getProject(projectId)
                .map(p -> ResponseEntity.ok(ApiResponse.success(buildProgress(p))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("not_found", "Project not found: " + projectId)));
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
            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ProjectStatistics>> getStatistics() {
        log.debug("Getting project statistics");
        ProjectStatistics stats = projectService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // Project Tasks Sub-resource
    @GetMapping("/{projectId}/tasks")
    public ResponseEntity<ApiResponse<List<ProjectTaskInfo>>> getProjectTasks(
            @PathVariable String projectId
    ) {
        log.debug("Getting tasks for project: {}", projectId);

        List<ProjectTaskInfo> tasks = List.of(
                new ProjectTaskInfo("task_001", projectId, "需求分析", "completed", 100),
                new ProjectTaskInfo("task_002", projectId, "系统设计", "in_progress", 60),
                new ProjectTaskInfo("task_003", projectId, "开发实现", "pending", 0)
        );

        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/{projectId}/tasks")
    public ResponseEntity<ApiResponse<ProjectTaskInfo>> createProjectTask(
            @PathVariable String projectId,
            @RequestBody CreateProjectTaskRequest request
    ) {
        log.info("Creating task for project: {}", projectId);

        ProjectTaskInfo task = new ProjectTaskInfo(
                "task_" + System.currentTimeMillis(),
                projectId,
                request.name(),
                "pending",
                0
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PutMapping("/{projectId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<ProjectTaskInfo>> updateProjectTask(
            @PathVariable String projectId,
            @PathVariable String taskId,
            @RequestBody UpdateProjectTaskRequest request
    ) {
        log.info("Updating task: {} of project: {}", taskId, projectId);

        ProjectTaskInfo task = new ProjectTaskInfo(
                taskId,
                projectId,
                request.name(),
                request.status(),
                request.progress()
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @DeleteMapping("/{projectId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteProjectTask(
            @PathVariable String projectId,
            @PathVariable String taskId
    ) {
        log.info("Deleting task: {} of project: {}", taskId, projectId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "taskId", taskId)));
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

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
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
}
