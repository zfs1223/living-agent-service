package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingScheduleEntity;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.security.RequireAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 会议预约 REST API - 闭环 67-D 预约管理 / P84 会议预约与通知
 *
 * <p>提供会议预约的创建、查询、更新、取消等 REST API，
 * 权限对齐 P14 八种 identity 权限矩阵。</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>POST   /api/meeting-schedules          - 创建预约（闭环 67-D-1）</li>
 *   <li>GET    /api/meeting-schedules          - 查询预约列表（按部门/状态过滤）</li>
 *   <li>GET    /api/meeting-schedules/{id}     - 获取预约详情</li>
 *   <li>PUT    /api/meeting-schedules/{id}     - 更新预约</li>
 *   <li>DELETE /api/meeting-schedules/{id}     - 取消预约（闭环 67-D-4）</li>
 * </ul>
 *
 * <h3>权限矩阵（对齐 P14）</h3>
 * <ul>
 *   <li>INTERNAL_ENTERPRISE - 可创建/修改/取消任意预约，可查看全部</li>
 *   <li>INTERNAL_ACTIVE - 可创建本部门预约，可修改/取消自己创建的，可查看本部门</li>
 *   <li>INTERNAL_PROBATION - 不可创建，可查看本部门</li>
 *   <li>EXTERNAL_CUSTOMER - 不可创建，可查看参与的</li>
 *   <li>其他 - 不可创建，不可查看</li>
 * </ul>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/meeting-schedules")
public class MeetingScheduleController {

    private static final Logger log = LoggerFactory.getLogger(MeetingScheduleController.class);

    private final MeetingScheduleService scheduleService;
    private final UnifiedAuthService authService;

    public MeetingScheduleController(
            MeetingScheduleService scheduleService,
            UnifiedAuthService authService) {
        this.scheduleService = scheduleService;
        this.authService = authService;
        log.info("[P84] MeetingScheduleController 初始化");
    }

    /**
     * 创建会议预约（闭环 67-D-1）
     *
     * <p>请求体：</p>
     * <pre>
     * {
     *   "title": "技术部周会",
     *   "description": "讨论本周进展",
     *   "department": "tech",
     *   "scheduledStart": "2026-07-25T09:00:00Z",
     *   "scheduledEnd": "2026-07-25T10:00:00Z",
     *   "maxParticipants": 50,
     *   "reminderMinutesBefore": 15,
     *   "enableRecording": false,
     *   "metadataJson": "{\"participantIds\":[\"emp-001\",\"emp-002\"]}"
     * }
     * </pre>
     */
    @PostMapping
    @RequireAccess(resource = "meeting", action = "create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSchedule(
            @RequestBody CreateScheduleRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录，无法创建预约"));
        }

        AuthContext ctx = ctxOpt.get();

        // 权限校验：只有 INTERNAL_ENTERPRISE 和 INTERNAL_ACTIVE 可创建预约
        if (!canCreateSchedule(ctx)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "当前身份无权创建会议预约"));
        }

        // INTERNAL_ACTIVE 只能创建本部门预约
        String department = request.department();
        if (ctx.getIdentity() == UserIdentity.INTERNAL_ACTIVE) {
            if (department != null && !department.equalsIgnoreCase(ctx.getDepartment())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.err("PERMISSION_DENIED", "在职员工只能创建本部门预约"));
            }
            department = ctx.getDepartment();
        }
        if (department == null) {
            department = ctx.getDepartment();
        }

        // 时间校验
        if (request.scheduledStart() == null || request.scheduledEnd() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_INPUT", "开始时间和结束时间不能为空"));
        }
        if (!request.scheduledEnd().isAfter(request.scheduledStart())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_INPUT", "结束时间必须晚于开始时间"));
        }
        if (!request.scheduledStart().isAfter(Instant.now())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_INPUT", "开始时间必须是未来时间"));
        }

        try {
            // 构建实体
            MeetingScheduleEntity schedule = new MeetingScheduleEntity(
                    UUID.randomUUID().toString(),
                    request.title(),
                    request.description(),
                    ctx.getEmployeeId(),
                    department,
                    request.scheduledStart(),
                    request.scheduledEnd()
            );

            schedule.setMaxParticipants(request.maxParticipants() > 0 ? request.maxParticipants() : 50);
            schedule.setReminderMinutesBefore(request.reminderMinutesBefore() > 0 ? request.reminderMinutesBefore() : 15);
            schedule.setEnableRecording(request.enableRecording());
            if (request.metadataJson() != null) {
                schedule.setMetadataJson(request.metadataJson());
            }

            // 创建预约
            MeetingScheduleEntity saved = scheduleService.createSchedule(schedule);

            // 构建响应
            Map<String, Object> result = toScheduleMap(saved);

            log.info("[P84] 会议预约创建成功 - scheduleId={}, title={}, department={}",
                    saved.getScheduleId(), saved.getTitle(), department);

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[P84] 创建会议预约失败 - creatorId={}", ctx.getEmployeeId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("CREATE_FAILED", "创建预约失败: " + e.getMessage()));
        }
    }

    /**
     * 查询预约列表（按部门/状态过滤，闭环 14→67）
     *
     * <p>查询参数：</p>
     * <ul>
     *   <li>department - 部门代码（可选，不填则查所有）</li>
     *   <li>status - 预约状态（可选，SCHEDULED/ACTIVE/COMPLETED/CANCELLED）</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSchedules(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        AuthContext ctx = ctxOpt.get();

        // 非 INTERNAL_ENTERPRISE 只能查看本部门
        String effectiveDepartment = department;
        if (ctx.getIdentity() != UserIdentity.INTERNAL_ENTERPRISE) {
            effectiveDepartment = ctx.getDepartment();
        }

        try {
            List<MeetingScheduleEntity> schedules =
                    scheduleService.findByDepartmentAndStatus(effectiveDepartment, status);

            List<Map<String, Object>> result = schedules.stream()
                    .map(this::toScheduleMap)
                    .toList();

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[P84] 查询预约列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询预约列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取预约详情
     */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchedule(
            @PathVariable String scheduleId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            Optional<MeetingScheduleEntity> scheduleOpt = scheduleService.findByScheduleId(scheduleId);
            if (scheduleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.err("NOT_FOUND", "预约不存在: " + scheduleId));
            }

            MeetingScheduleEntity schedule = scheduleOpt.get();

            // 权限校验：非 INTERNAL_ENTERPRISE 只能查看本部门预约
            AuthContext ctx = ctxOpt.get();
            if (ctx.getIdentity() != UserIdentity.INTERNAL_ENTERPRISE
                    && !schedule.getDepartment().equalsIgnoreCase(ctx.getDepartment())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.err("PERMISSION_DENIED", "无权查看其他部门的预约"));
            }

            return ResponseEntity.ok(ApiResponse.ok(toScheduleMap(schedule)));

        } catch (Exception e) {
            log.error("[P84] 获取预约详情失败 - scheduleId={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "获取预约详情失败: " + e.getMessage()));
        }
    }

    /**
     * 更新预约（仅创建人或 INTERNAL_ENTERPRISE 可修改）
     */
    @PutMapping("/{scheduleId}")
    @RequireAccess(resource = "meeting", action = "update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSchedule(
            @PathVariable String scheduleId,
            @RequestBody UpdateScheduleRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        AuthContext ctx = ctxOpt.get();

        // 权限校验
        Optional<MeetingScheduleEntity> existingOpt = scheduleService.findByScheduleId(scheduleId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.err("NOT_FOUND", "预约不存在: " + scheduleId));
        }

        MeetingScheduleEntity existing = existingOpt.get();
        if (!canModifySchedule(ctx, existing)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "无权修改此预约"));
        }

        try {
            // 构建更新字段 Map
            Map<String, Object> updates = new LinkedHashMap<>();
            if (request.title() != null) updates.put("title", request.title());
            if (request.description() != null) updates.put("description", request.description());
            if (request.scheduledStart() != null) updates.put("scheduledStart", request.scheduledStart());
            if (request.scheduledEnd() != null) updates.put("scheduledEnd", request.scheduledEnd());
            if (request.maxParticipants() > 0) updates.put("maxParticipants", request.maxParticipants());
            if (request.reminderMinutesBefore() > 0) updates.put("reminderMinutesBefore", request.reminderMinutesBefore());
            if (request.enableRecording() != null) updates.put("enableRecording", request.enableRecording());
            if (request.metadataJson() != null) updates.put("metadataJson", request.metadataJson());

            MeetingScheduleEntity updated = scheduleService.updateSchedule(scheduleId, updates);

            log.info("[P84] 预约更新成功 - scheduleId={}, updatedBy={}", scheduleId, ctx.getEmployeeId());
            return ResponseEntity.ok(ApiResponse.ok(toScheduleMap(updated)));

        } catch (Exception e) {
            log.error("[P84] 更新预约失败 - scheduleId={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("UPDATE_FAILED", "更新预约失败: " + e.getMessage()));
        }
    }

    /**
     * 取消预约（闭环 67-D-4）
     */
    @DeleteMapping("/{scheduleId}")
    @RequireAccess(resource = "meeting", action = "delete")
    public ResponseEntity<ApiResponse<Void>> cancelSchedule(
            @PathVariable String scheduleId,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        AuthContext ctx = ctxOpt.get();

        // 权限校验
        Optional<MeetingScheduleEntity> existingOpt = scheduleService.findByScheduleId(scheduleId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.err("NOT_FOUND", "预约不存在: " + scheduleId));
        }

        if (!canModifySchedule(ctx, existingOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "无权取消此预约"));
        }

        try {
            scheduleService.cancelSchedule(scheduleId, reason);
            log.info("[P84] 预约取消成功 - scheduleId={}, cancelledBy={}", scheduleId, ctx.getEmployeeId());
            return ResponseEntity.ok(ApiResponse.ok());

        } catch (Exception e) {
            log.error("[P84] 取消预约失败 - scheduleId={}", scheduleId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("CANCEL_FAILED", "取消预约失败: " + e.getMessage()));
        }
    }

    // ========== 权限判断方法 ==========

    /**
     * 判断用户是否可以创建预约
     * 对齐 P14：INTERNAL_ENTERPRISE + INTERNAL_ACTIVE 可创建
     */
    private boolean canCreateSchedule(AuthContext ctx) {
        if (ctx.getIdentity() == null) return false;
        return ctx.getIdentity() == UserIdentity.INTERNAL_ENTERPRISE
                || ctx.getIdentity() == UserIdentity.INTERNAL_ACTIVE;
    }

    /**
     * 判断用户是否可以修改/取消预约
     * 规则：INTERNAL_ENTERPRISE 可修改所有，其他只能修改自己创建的
     */
    private boolean canModifySchedule(AuthContext ctx, MeetingScheduleEntity schedule) {
        if (ctx.getIdentity() == UserIdentity.INTERNAL_ENTERPRISE) return true;
        return schedule.getCreatorId().equals(ctx.getEmployeeId());
    }

    // ========== 认证解析 ==========

    /**
     * 从 Authorization header 解析 AuthContext
     */
    private Optional<AuthContext> resolveAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        return sessionOpt.map(AuthSession::authContext);
    }

    // ========== 响应构建 ==========

    /**
     * 将实体转换为响应 Map
     */
    private Map<String, Object> toScheduleMap(MeetingScheduleEntity schedule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scheduleId", schedule.getScheduleId());
        map.put("title", schedule.getTitle());
        map.put("description", schedule.getDescription());
        map.put("creatorId", schedule.getCreatorId());
        map.put("department", schedule.getDepartment());
        map.put("roomName", schedule.getRoomName());
        map.put("maxParticipants", schedule.getMaxParticipants());
        map.put("scheduledStart", schedule.getScheduledStart());
        map.put("scheduledEnd", schedule.getScheduledEnd());
        map.put("durationMinutes", schedule.getDurationMinutes());
        map.put("actualStart", schedule.getActualStart());
        map.put("actualEnd", schedule.getActualEnd());
        map.put("status", schedule.getStatus());
        map.put("reminderSent", schedule.isReminderSent());
        map.put("reminderMinutesBefore", schedule.getReminderMinutesBefore());
        map.put("enableRecording", schedule.isEnableRecording());
        map.put("calendarEventId", schedule.getCalendarEventId());
        map.put("calendarSyncAdapter", schedule.getCalendarSyncAdapter());
        map.put("metadataJson", schedule.getMetadataJson());
        map.put("createdAt", schedule.getCreatedAt());
        map.put("updatedAt", schedule.getUpdatedAt());
        return map;
    }

    // ========== 请求体记录 ==========

    /**
     * 创建预约请求体
     */
    public record CreateScheduleRequest(
            String title,
            String description,
            String department,
            Instant scheduledStart,
            Instant scheduledEnd,
            int maxParticipants,
            int reminderMinutesBefore,
            boolean enableRecording,
            String metadataJson
    ) {}

    /**
     * 更新预约请求体
     */
    public record UpdateScheduleRequest(
            String title,
            String description,
            Instant scheduledStart,
            Instant scheduledEnd,
            int maxParticipants,
            int reminderMinutesBefore,
            Boolean enableRecording,
            String metadataJson
    ) {}
}
