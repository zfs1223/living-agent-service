package com.livingagent.gateway.controller;

import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService.Suggestion;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService.SuggestionType;
import com.livingagent.core.proactive.feedback.ProactiveEffectivenessTracker;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/proactive")
public class ProactiveController {

    private static final Logger log = LoggerFactory.getLogger(ProactiveController.class);

    private final ProactiveSuggestionService suggestionService;
    private final AccessGateService accessGateService;
    private final ProactiveEffectivenessTracker proactiveEffectivenessTracker;

    public ProactiveController(ProactiveSuggestionService suggestionService, AccessGateService accessGateService,
                               ProactiveEffectivenessTracker proactiveEffectivenessTracker) {
        this.suggestionService = suggestionService;
        this.accessGateService = accessGateService;
        this.proactiveEffectivenessTracker = proactiveEffectivenessTracker;
    }

    @GetMapping("/digest")
    public ResponseEntity<ApiResponse<DailyDigest>> getDailyDigest(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting daily digest for user: {}", userId);

        String effectiveUserId = userId != null ? userId : "default";

        DailyDigest digest = new DailyDigest(
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toInstant(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "今日暂无重要事项"
        );

        return ResponseEntity.ok(ApiResponse.ok(digest));
    }

    @GetMapping("/habits")
    public ResponseEntity<ApiResponse<List<HabitInfo>>> getHabits(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting habits for user: {}", userId);

        String effectiveUserId = userId != null ? userId : "default";

        List<HabitInfo> habits = List.of(
                new HabitInfo("habit_1", "每日站会", "DAILY", "09:00", true, 15, 0),
                new HabitInfo("habit_2", "代码审查", "DAILY", "14:00", true, 10, 0),
                new HabitInfo("habit_3", "周报提交", "WEEKLY", "FRIDAY:17:00", true, 4, 0)
        );

        return ResponseEntity.ok(ApiResponse.ok(habits));
    }

    @PostMapping("/habits/{habitId}/checkin")
    public ResponseEntity<ApiResponse<CheckinResult>> checkinHabit(
            @PathVariable String habitId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Habit checkin: {} for user: {}", habitId, userId);

        CheckinResult result = new CheckinResult(
                habitId,
                true,
                Instant.now(),
                "打卡成功！继续保持良好习惯。",
                15
        );

        proactiveEffectivenessTracker.recordAccepted("habit", true);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/habits")
    public ResponseEntity<ApiResponse<HabitInfo>> createHabit(
            @RequestBody CreateHabitRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating habit: {}", request.name());

        HabitInfo habit = new HabitInfo(
                "habit_" + System.currentTimeMillis(),
                request.name(),
                request.frequency(),
                request.time(),
                true,
                0,
                0
        );

        return ResponseEntity.ok(ApiResponse.ok(habit));
    }

    @PutMapping("/habits/{id}")
    public ResponseEntity<ApiResponse<HabitInfo>> updateHabit(
            @PathVariable String id,
            @RequestBody UpdateHabitRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating habit: {}", id);

        HabitInfo habit = new HabitInfo(
                id,
                request.name(),
                request.frequency(),
                request.time(),
                request.enabled(),
                10,
                0
        );

        return ResponseEntity.ok(ApiResponse.ok(habit));
    }

    @DeleteMapping("/habits/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHabit(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting habit: {}", id);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationInfo>>> getNotifications(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting notifications for user: {}", userId);

        String effectiveUserId = userId != null ? userId : "default";

        List<Suggestion> suggestions = suggestionService.getUserSuggestions(effectiveUserId);

        List<NotificationInfo> notifications = new ArrayList<>();
        
        for (Suggestion suggestion : suggestions) {
            notifications.add(new NotificationInfo(
                    suggestion.suggestionId(),
                    suggestion.title(),
                    suggestion.description(),
                    mapSuggestionTypeToNotificationType(suggestion.type()),
                    false,
                    suggestion.createdAt(),
                    suggestion.metadata()
            ));
        }

        if (notifications.isEmpty()) {
            notifications.add(new NotificationInfo(
                    "notif_welcome",
                    "欢迎使用智能助手",
                    "系统已准备就绪，随时为您服务。",
                    "INFO",
                    false,
                    Instant.now(),
                    Map.of()
            ));
        }

        if (unreadOnly) {
            notifications = notifications.stream()
                    .filter(n -> !n.read())
                    .toList();
        }

        return ResponseEntity.ok(ApiResponse.ok(notifications.stream().limit(limit).toList()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markNotificationRead(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Marking notification as read: {}", id);

        String effectiveUserId = userId != null ? userId : "default";
        suggestionService.acknowledgeSuggestion(effectiveUserId, id);
        proactiveEffectivenessTracker.recordAccepted("notification", false);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllNotificationsRead(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Marking all notifications as read for user: {}", userId);

        String effectiveUserId = userId != null ? userId : "default";
        suggestionService.clearUserSuggestions(effectiveUserId);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/meeting-notes")
    public ResponseEntity<ApiResponse<List<MeetingNote>>> getMeetingNotes(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting meeting notes for user: {}", userId);

        List<MeetingNote> notes = List.of();

        return ResponseEntity.ok(ApiResponse.ok(notes));
    }

    @GetMapping("/meeting-notes/{id}")
    public ResponseEntity<ApiResponse<MeetingNote>> getMeetingNote(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting meeting note: {}", id);

        return ResponseEntity.status(404)
                .body(ApiResponse.err("not_found", "Meeting note not found: " + id));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AnalyticsData>> getAnalytics(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting analytics for user: {}, days: {}", userId, days);

        AnalyticsData analytics = new AnalyticsData(
                days,
                42,
                38,
                0.90,
                4.5,
                List.of(
                        new DailyMetric(Instant.now().minusSeconds(86400 * 6), 5, 5, 0.0),
                        new DailyMetric(Instant.now().minusSeconds(86400 * 5), 6, 5, 0.17),
                        new DailyMetric(Instant.now().minusSeconds(86400 * 4), 4, 4, 0.0),
                        new DailyMetric(Instant.now().minusSeconds(86400 * 3), 7, 6, 0.14),
                        new DailyMetric(Instant.now().minusSeconds(86400 * 2), 8, 8, 0.0),
                        new DailyMetric(Instant.now().minusSeconds(86400), 6, 5, 0.17),
                        new DailyMetric(Instant.now(), 6, 5, 0.17)
                ),
                Map.of(
                        "mostProductiveHour", 14,
                        "avgTasksPerDay", 6.0,
                        "streak", 5
                )
        );

        return ResponseEntity.ok(ApiResponse.ok(analytics));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<SuggestionResponse>>> getSuggestions(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "5") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting suggestions for user: {}", userId);

        String effectiveUserId = userId != null ? userId : "default";

        List<Suggestion> suggestions = suggestionService.generateSuggestions(effectiveUserId);

        for (Suggestion s : suggestions) {
            proactiveEffectivenessTracker.recordSuggestion(s.type().name());
        }

        List<SuggestionResponse> response = suggestions.stream()
                .limit(limit)
                .map(this::convertToSuggestionResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/predictions")
    public ResponseEntity<ApiResponse<List<PredictionInfo>>> getPredictions(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting predictions for user: {}", userId);

        List<PredictionInfo> predictions = List.of(
                new PredictionInfo(
                        "pred_001",
                        "任务延迟风险",
                        "根据历史数据分析，当前有3个任务可能延迟",
                        0.75,
                        Instant.now()
                ),
                new PredictionInfo(
                        "pred_002",
                        "系统负载预警",
                        "预计下午2点系统负载将达到峰值",
                        0.82,
                        Instant.now()
                )
        );

        return ResponseEntity.ok(ApiResponse.ok(predictions.stream().limit(limit).toList()));
    }

    private String mapSuggestionTypeToNotificationType(SuggestionType type) {
        return switch (type) {
            case WARNING -> "WARNING";
            case REPORT -> "INFO";
            case REMINDER -> "REMINDER";
            default -> "INFO";
        };
    }

    private SuggestionResponse convertToSuggestionResponse(Suggestion suggestion) {
        return new SuggestionResponse(
                suggestion.suggestionId(),
                suggestion.title(),
                suggestion.description(),
                suggestion.type().name(),
                suggestion.confidence(),
                suggestion.metadata(),
                suggestion.createdAt(),
                suggestion.isActionable(),
                suggestion.getAction()
        );
    }

    public record DailyDigest(
            Instant date,
            List<String> importantTasks,
            List<String> meetings,
            List<String> reminders,
            List<String> insights,
            String summary
    ) {}

    public record HabitInfo(
            String id,
            String name,
            String frequency,
            String time,
            boolean enabled,
            int streak,
            int completedToday
    ) {}

    public record CheckinResult(
            String habitId,
            boolean success,
            Instant checkedAt,
            String message,
            int newStreak
    ) {}

    public record CreateHabitRequest(
            String name,
            String frequency,
            String time,
            String description
    ) {}

    public record UpdateHabitRequest(
            String name,
            String frequency,
            String time,
            boolean enabled
    ) {}

    public record NotificationInfo(
            String id,
            String title,
            String content,
            String type,
            boolean read,
            Instant createdAt,
            Map<String, Object> metadata
    ) {}

    public record MeetingNote(
            String id,
            String title,
            Instant startTime,
            Instant endTime,
            List<String> participants,
            String summary,
            List<String> actionItems
    ) {}

    public record AnalyticsData(
            int periodDays,
            int totalTasks,
            int completedTasks,
            double completionRate,
            double avgProductivityScore,
            List<DailyMetric> dailyMetrics,
            Map<String, Object> insights
    ) {}

    public record DailyMetric(
            Instant date,
            int totalTasks,
            int completedTasks,
            double failureRate
    ) {}

    public record SuggestionResponse(
            String id,
            String title,
            String description,
            String type,
            double confidence,
            Map<String, Object> metadata,
            Instant createdAt,
            boolean actionable,
            String action
    ) {}

    public record PredictionInfo(
            String id,
            String title,
            String description,
            double confidence,
            Instant created_at
    ) {}
}
