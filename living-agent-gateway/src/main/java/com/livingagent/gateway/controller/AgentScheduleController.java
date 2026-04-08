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
@RequestMapping("/api/agents/{agentId}/schedules")
public class AgentScheduleController {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduleController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleInfo>>> listSchedules(@PathVariable String agentId) {
        log.debug("Listing schedules for agent: {}", agentId);

        List<ScheduleInfo> schedules = new ArrayList<>();
        schedules.add(new ScheduleInfo(
                "sch_001",
                agentId,
                "每日报告",
                "0 9 * * *",
                true,
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleInfo>> createSchedule(
            @PathVariable String agentId,
            @RequestBody CreateScheduleRequest request
    ) {
        log.info("Creating schedule for agent: {}", agentId);

        ScheduleInfo schedule = new ScheduleInfo(
                "sch_" + System.currentTimeMillis(),
                agentId,
                request.name(),
                request.cron(),
                request.enabled(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(schedule));
    }

    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleInfo>> updateSchedule(
            @PathVariable String agentId,
            @PathVariable String scheduleId,
            @RequestBody UpdateScheduleRequest request
    ) {
        log.info("Updating schedule: {} for agent: {}", scheduleId, agentId);

        ScheduleInfo schedule = new ScheduleInfo(
                scheduleId,
                agentId,
                request.name(),
                request.cron(),
                request.enabled(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(schedule));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSchedule(
            @PathVariable String agentId,
            @PathVariable String scheduleId
    ) {
        log.info("Deleting schedule: {} for agent: {}", scheduleId, agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "id", scheduleId)));
    }

    @PostMapping("/{scheduleId}/run")
    public ResponseEntity<ApiResponse<Map<String, String>>> runSchedule(
            @PathVariable String agentId,
            @PathVariable String scheduleId
    ) {
        log.info("Running schedule: {} for agent: {}", scheduleId, agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "triggered", "id", scheduleId)));
    }

    @GetMapping("/{scheduleId}/history")
    public ResponseEntity<ApiResponse<List<ScheduleHistory>>> getScheduleHistory(
            @PathVariable String agentId,
            @PathVariable String scheduleId
    ) {
        log.debug("Getting history for schedule: {} of agent: {}", scheduleId, agentId);

        List<ScheduleHistory> history = new ArrayList<>();
        history.add(new ScheduleHistory(
                "hist_001",
                scheduleId,
                "success",
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(history));
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

    public record ScheduleInfo(
            String id,
            String agent_id,
            String name,
            String cron,
            boolean enabled,
            Instant created_at
    ) {}

    public record CreateScheduleRequest(
            String name,
            String cron,
            boolean enabled
    ) {}

    public record UpdateScheduleRequest(
            String name,
            String cron,
            Boolean enabled
    ) {}

    public record ScheduleHistory(
            String id,
            String schedule_id,
            String status,
            Instant executed_at
    ) {}
}
