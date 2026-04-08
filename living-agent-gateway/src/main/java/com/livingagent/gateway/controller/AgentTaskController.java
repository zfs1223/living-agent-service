package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{agentId}/tasks")
public class AgentTaskController {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskInfo>>> listTasks(
            @PathVariable String agentId,
            @RequestParam(required = false) String status_filter,
            @RequestParam(required = false) String type_filter
    ) {
        log.debug("Listing tasks for agent: {}, status: {}, type: {}", agentId, status_filter, type_filter);

        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(new TaskInfo(
                "task_001",
                agentId,
                "示例任务",
                status_filter != null ? status_filter : "pending",
                5,
                Instant.now(),
                null,
                null
        ));

        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskInfo>> createTask(
            @PathVariable String agentId,
            @RequestBody CreateTaskRequest request
    ) {
        log.info("Creating task for agent: {}", agentId);

        TaskInfo task = new TaskInfo(
                "task_" + System.currentTimeMillis(),
                agentId,
                request.title(),
                "pending",
                request.priority(),
                Instant.now(),
                null,
                null
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskInfo>> getTask(
            @PathVariable String agentId,
            @PathVariable String taskId
    ) {
        log.debug("Getting task: {} for agent: {}", taskId, agentId);

        TaskInfo task = new TaskInfo(
                taskId,
                agentId,
                "任务详情",
                "pending",
                5,
                Instant.now(),
                null,
                null
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskInfo>> updateTask(
            @PathVariable String agentId,
            @PathVariable String taskId,
            @RequestBody UpdateTaskRequest request
    ) {
        log.info("Updating task: {} for agent: {}", taskId, agentId);

        TaskInfo task = new TaskInfo(
                taskId,
                agentId,
                request.title() != null ? request.title() : "任务",
                request.status() != null ? request.status() : "pending",
                5,
                Instant.now(),
                null,
                null
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<ApiResponse<List<TaskLog>>> getTaskLogs(
            @PathVariable String agentId,
            @PathVariable String taskId
    ) {
        log.debug("Getting logs for task: {} of agent: {}", taskId, agentId);

        List<TaskLog> logs = new ArrayList<>();
        logs.add(new TaskLog(
                "log_001",
                taskId,
                "任务创建",
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @PostMapping("/{taskId}/trigger")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerTask(
            @PathVariable String agentId,
            @PathVariable String taskId
    ) {
        log.info("Triggering task: {} for agent: {}", taskId, agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "triggered",
                "taskId", taskId
        )));
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

    public record TaskInfo(
            String id,
            String agent_id,
            String title,
            String status,
            int priority,
            Instant created_at,
            Instant started_at,
            Instant completed_at
    ) {}

    public record CreateTaskRequest(
            String title,
            String description,
            int priority
    ) {}

    public record UpdateTaskRequest(
            String title,
            String description,
            String status,
            Integer priority
    ) {}

    public record TaskLog(
            String id,
            String task_id,
            String content,
            Instant created_at
    ) {}
}
