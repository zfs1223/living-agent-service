package com.livingagent.gateway.meeting;

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

import java.util.*;

/**
 * 会议 REST API 入口 - 闭环 67-A 会议创建 / 67-B 会议执行 / 67-C 状态管理
 *
 * <p>提供会议的创建、查询、结束、参会 token 获取、参与者查询等 REST API，
 * 作为 LAS 系统与 LiveKit 会议系统的前端入口。</p>
 *
 * <h3>权限控制（对齐 DESKTOP P14 八种 identity 权限矩阵）</h3>
 * <ul>
 *   <li>INTERNAL_ENTERPRISE - 可创建跨部门会议、加入任意会议、录制</li>
 *   <li>INTERNAL_ACTIVE - 可创建本部门会议、加入本部门会议</li>
 *   <li>INTERNAL_PROBATION - 仅可加入本部门会议</li>
 *   <li>EXTERNAL_CUSTOMER - 仅可加入客服部会议</li>
 *   <li>其他 - 不可创建/加入会议</li>
 * </ul>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private static final Logger log = LoggerFactory.getLogger(MeetingController.class);

    private final LiveKitRoomService roomService;
    private final LiveKitTokenService tokenService;
    private final LiveKitConfig liveKitConfig;
    private final UnifiedAuthService authService;

    public MeetingController(
            LiveKitRoomService roomService,
            LiveKitTokenService tokenService,
            LiveKitConfig liveKitConfig,
            UnifiedAuthService authService) {
        this.roomService = roomService;
        this.tokenService = tokenService;
        this.liveKitConfig = liveKitConfig;
        this.authService = authService;
        log.info("[P81] MeetingController 初始化");
    }

    /**
     * 创建会议（闭环 67-A）
     *
     * <p>请求体：</p>
     * <pre>
     * {
     *   "roomName": "dept-tech-meeting-a1b2c3",  // 可选，不填则自动生成
     *   "maxParticipants": 50,                    // 可选，默认50
     *   "department": "tech"                      // 可选，绑定部门
     * }
     * </pre>
     */
    @PostMapping
    @RequireAccess(resource = "meeting", action = "create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMeeting(
            @RequestBody CreateMeetingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录，无法创建会议"));
        }

        AuthContext ctx = ctxOpt.get();

        // 权限校验：只有 INTERNAL_ENTERPRISE 和 INTERNAL_ACTIVE 可创建会议
        if (!canCreateMeeting(ctx)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "当前身份无权创建会议"));
        }

        try {
            // 生成房间名称（如果未提供）
            String roomName = request.roomName();
            if (roomName == null || roomName.isBlank()) {
                String dept = request.department() != null ? request.department() : ctx.getDepartment();
                roomName = "dept-" + (dept != null ? dept : "general") + "-meeting-" + UUID.randomUUID().toString().substring(0, 8);
            }

            int maxParticipants = request.maxParticipants() > 0 ? request.maxParticipants() : 50;

            // INTERNAL_ACTIVE 只能创建本部门会议
            if (ctx.getIdentity() == UserIdentity.INTERNAL_ACTIVE) {
                String requestDept = request.department();
                if (requestDept != null && !requestDept.equalsIgnoreCase(ctx.getDepartment())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResponse.err("PERMISSION_DENIED", "在职员工只能创建本部门会议"));
                }
            }

            // 调用 LiveKit 创建房间
            Map<String, Object> roomInfo = roomService.createRoom(roomName, maxParticipants);

            // 构建响应
            Map<String, Object> result = new LinkedHashMap<>(roomInfo);
            result.put("livekitUrl", liveKitConfig.getApiUrl());
            result.put("department", request.department() != null ? request.department() : ctx.getDepartment());
            result.put("createdBy", ctx.getEmployeeId());

            log.info("[P81] 会议创建成功 - room={}, createdBy={}, department={}",
                    roomName, ctx.getEmployeeId(), request.department());

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[P81] 会议创建失败 - createdBy={}", ctx.getEmployeeId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("CREATE_FAILED", "创建会议失败: " + e.getMessage()));
        }
    }

    /**
     * 获取会议列表（闭环 67-A 查询）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listMeetings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录，无法查看会议列表"));
        }

        try {
            List<Map<String, Object>> rooms = roomService.listRooms();
            return ResponseEntity.ok(ApiResponse.ok(rooms));
        } catch (Exception e) {
            log.error("[P81] 查询会议列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询会议列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取会议详情（闭环 67-A）
     */
    @GetMapping("/{roomName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMeeting(
            @PathVariable String roomName,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            // 从列表中筛选指定房间
            List<Map<String, Object>> rooms = roomService.listRooms();
            Map<String, Object> targetRoom = rooms.stream()
                    .filter(r -> roomName.equals(r.get("name")))
                    .findFirst()
                    .orElse(null);

            if (targetRoom == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.err("NOT_FOUND", "会议房间不存在: " + roomName));
            }

            // 附加参会者信息
            List<Map<String, Object>> participants = roomService.listParticipants(roomName);
            Map<String, Object> result = new LinkedHashMap<>(targetRoom);
            result.put("participants", participants);

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[P81] 获取会议详情失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "获取会议详情失败: " + e.getMessage()));
        }
    }

    /**
     * 结束会议（闭环 67-C）
     */
    @DeleteMapping("/{roomName}")
    @RequireAccess(resource = "meeting", action = "delete")
    public ResponseEntity<ApiResponse<Void>> endMeeting(
            @PathVariable String roomName,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        AuthContext ctx = ctxOpt.get();

        // 只有 INTERNAL_ENTERPRISE 和 INTERNAL_ACTIVE 可结束会议
        if (!canCreateMeeting(ctx)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "当前身份无权结束会议"));
        }

        try {
            roomService.deleteRoom(roomName);
            log.info("[P81] 会议已结束 - room={}, endedBy={}", roomName, ctx.getEmployeeId());
            return ResponseEntity.ok(ApiResponse.ok());

        } catch (Exception e) {
            log.error("[P81] 结束会议失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("DELETE_FAILED", "结束会议失败: " + e.getMessage()));
        }
    }

    /**
     * 获取加入会议的 token（闭环 38→67 认证桥接）
     *
     * <p>请求体：</p>
     * <pre>
     * {
     *   "canPublish": true    // 可选，默认 true（是否发布音视频）
     * }
     * </pre>
     */
    @PostMapping("/{roomName}/token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJoinToken(
            @PathVariable String roomName,
            @RequestBody(required = false) JoinTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录，无法加入会议"));
        }

        AuthContext ctx = ctxOpt.get();

        // 权限校验：判断用户是否可以加入该部门会议
        boolean canPublish = request != null ? request.canPublish() : true;

        // 根据用户身份调整权限
        if (!canJoinMeeting(ctx, roomName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.err("PERMISSION_DENIED", "当前身份无权加入此会议"));
        }

        // 试用期员工、客户、合作伙伴只能订阅（观众模式）
        if (ctx.getIdentity() == UserIdentity.INTERNAL_PROBATION
                || ctx.getIdentity() == UserIdentity.EXTERNAL_CUSTOMER
                || ctx.getIdentity() == UserIdentity.EXTERNAL_PARTNER
                || ctx.getIdentity() == UserIdentity.EXTERNAL_CONTRACTOR) {
            canPublish = false;
        }

        try {
            // 构建元数据（携带 LAS 用户信息）
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("department", ctx.getDepartment());
            metadata.put("accessLevel", ctx.getAccessLevel().name());
            metadata.put("identity", ctx.getIdentity().name());
            metadata.put("name", ctx.getName());

            // 生成 LiveKit token
            String token = tokenService.generateToken(
                    ctx.getEmployeeId(),
                    roomName,
                    canPublish,
                    true,   // canSubscribe 始终为 true
                    metadata
            );

            // 构建响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("livekitUrl", liveKitConfig.getApiUrl());
            result.put("roomName", roomName);
            result.put("identity", ctx.getEmployeeId());
            result.put("canPublish", canPublish);
            result.put("canSubscribe", true);

            log.info("[P81] 生成参会 token - room={}, userId={}, canPublish={}",
                    roomName, ctx.getEmployeeId(), canPublish);

            return ResponseEntity.ok(ApiResponse.ok(result));

        } catch (Exception e) {
            log.error("[P81] 生成参会 token 失败 - room={}, userId={}", roomName, ctx.getEmployeeId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("TOKEN_FAILED", "生成参会 token 失败: " + e.getMessage()));
        }
    }

    /**
     * 获取参与者列表（闭环 67-B）
     */
    @GetMapping("/{roomName}/participants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getParticipants(
            @PathVariable String roomName,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            List<Map<String, Object>> participants = roomService.listParticipants(roomName);
            return ResponseEntity.ok(ApiResponse.ok(participants));
        } catch (Exception e) {
            log.error("[P81] 查询参与者失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询参与者失败: " + e.getMessage()));
        }
    }

    // ========== 权限判断方法 ==========

    /**
     * 判断用户是否可以创建会议
     * 对齐 P14 八种 identity 权限矩阵：
     * - INTERNAL_ENTERPRISE: 可创建跨部门会议
     * - INTERNAL_ACTIVE: 可创建本部门会议
     * - 其他: 不可创建
     */
    private boolean canCreateMeeting(AuthContext ctx) {
        if (ctx.getIdentity() == null) return false;
        return ctx.getIdentity() == UserIdentity.INTERNAL_ENTERPRISE
                || ctx.getIdentity() == UserIdentity.INTERNAL_ACTIVE;
    }

    /**
     * 判断用户是否可以加入指定会议
     * 对齐 P14 八种 identity 权限矩阵：
     * - INTERNAL_ENTERPRISE: 可加入任意会议
     * - INTERNAL_ACTIVE/PROBATION: 可加入本部门会议
     * - EXTERNAL_CUSTOMER: 仅可加入客服部会议
     * - EXTERNAL_PARTNER/CONTRACTOR: 可加入合作部门会议
     * - INTERNAL_DEPARTED/EXTERNAL_VISITOR: 不可加入
     */
    private boolean canJoinMeeting(AuthContext ctx, String roomName) {
        if (ctx.getIdentity() == null) return false;

        return switch (ctx.getIdentity()) {
            case INTERNAL_ENTERPRISE -> true;  // 董事长可加入任意会议
            case INTERNAL_ACTIVE, INTERNAL_PROBATION -> isRoomOfDepartment(roomName, ctx.getDepartment());
            case EXTERNAL_CUSTOMER -> isRoomOfDepartment(roomName, "cs");  // 客户仅可加入客服部会议
            case EXTERNAL_PARTNER, EXTERNAL_CONTRACTOR -> true;  // 合作方可加入合作部门会议（简化处理）
            case INTERNAL_DEPARTED, EXTERNAL_VISITOR -> false;   // 离职/访客不可加入
        };
    }

    /**
     * 判断房间名称是否属于指定部门
     * 房间命名规范: dept-{departmentCode}-meeting-{uuid}
     */
    private boolean isRoomOfDepartment(String roomName, String department) {
        if (department == null) return false;
        return roomName.startsWith("dept-" + department.toLowerCase() + "-");
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

    // ========== 请求体记录 ==========

    /**
     * 创建会议请求体
     */
    public record CreateMeetingRequest(
            String roomName,
            int maxParticipants,
            String department
    ) {}

    /**
     * 获取参会 token 请求体
     */
    public record JoinTokenRequest(
            boolean canPublish
    ) {}
}
