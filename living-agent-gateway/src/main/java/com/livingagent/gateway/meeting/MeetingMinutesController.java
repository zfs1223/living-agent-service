package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingMinutesEntity;
import com.livingagent.core.security.AuthContext;
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
 * 会议纪要 REST API 入口 - 闭环 68 录制与纪要自动化 / P82
 *
 * <p>提供会议纪要的查询、手动触发生成、录制文件信息查询等 REST API。</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>GET /api/meeting-minutes - 列出所有纪要</li>
 *   <li>GET /api/meeting-minutes/{roomName} - 获取指定房间的纪要</li>
 *   <li>POST /api/meeting-minutes/{roomName}/generate - 手动触发生成纪要</li>
 *   <li>GET /api/meeting-minutes/{roomName}/recording - 获取录制文件信息</li>
 * </ul>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/meeting-minutes")
public class MeetingMinutesController {

    private static final Logger log = LoggerFactory.getLogger(MeetingMinutesController.class);

    private final MeetingMinutesService minutesService;
    private final LiveKitEgressService egressService;
    private final UnifiedAuthService authService;

    public MeetingMinutesController(
            MeetingMinutesService minutesService,
            LiveKitEgressService egressService,
            UnifiedAuthService authService) {
        this.minutesService = minutesService;
        this.egressService = egressService;
        this.authService = authService;
        log.info("[P82] MeetingMinutesController 初始化");
    }

    /**
     * 列出所有会议纪要
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MeetingMinutesEntity>>> listAllMinutes(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            List<MeetingMinutesEntity> minutes = minutesService.getAllMinutes();
            return ResponseEntity.ok(ApiResponse.ok(minutes));
        } catch (Exception e) {
            log.error("[P82] 查询纪要列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询纪要列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定房间的会议纪要
     *
     * @param roomName 房间名称
     */
    @GetMapping("/{roomName}")
    public ResponseEntity<ApiResponse<List<MeetingMinutesEntity>>> getMinutesByRoom(
            @PathVariable String roomName,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            List<MeetingMinutesEntity> minutes = minutesService.getMinutesByRoom(roomName);
            return ResponseEntity.ok(ApiResponse.ok(minutes));
        } catch (Exception e) {
            log.error("[P82] 查询会议纪要失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询会议纪要失败: " + e.getMessage()));
        }
    }

    /**
     * 手动触发生成会议纪要（闭环 68）
     *
     * <p>请求体：</p>
     * <pre>
     * {
     *   "recordingFilePath": "data/recordings/meeting-xxx.mp4"  // 可选，不填则自动查找
     * }
     * </pre>
     *
     * @param roomName 房间名称
     */
    @PostMapping("/{roomName}/generate")
    @RequireAccess(resource = "meeting", action = "record")
    public ResponseEntity<ApiResponse<MeetingMinutesEntity>> generateMinutes(
            @PathVariable String roomName,
            @RequestBody(required = false) GenerateMinutesRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录，无法生成纪要"));
        }

        try {
            String recordingFilePath = null;
            if (request != null && request.recordingFilePath() != null) {
                recordingFilePath = request.recordingFilePath();
            } else {
                // 未指定录制文件路径，尝试从 Egress 查找
                recordingFilePath = findLatestRecordingPath(roomName);
                if (recordingFilePath == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.err("NO_RECORDING", "未找到录制文件，请手动指定录制文件路径"));
                }
            }

            log.info("[P82] 手动触发生成纪要 - room={}, recording={}, triggeredBy={}",
                    roomName, recordingFilePath, ctxOpt.get().getEmployeeId());

            MeetingMinutesEntity minutes = minutesService.generateMinutes(roomName, recordingFilePath);
            return ResponseEntity.ok(ApiResponse.ok(minutes));

        } catch (Exception e) {
            log.error("[P82] 生成会议纪要失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("GENERATE_FAILED", "生成会议纪要失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定房间的录制文件信息（闭环 68-A）
     *
     * @param roomName 房间名称
     */
    @GetMapping("/{roomName}/recording")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRecordingInfo(
            @PathVariable String roomName,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Optional<AuthContext> ctxOpt = resolveAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.err("UNAUTHORIZED", "未登录"));
        }

        try {
            List<Map<String, Object>> recordings = egressService.listRecordings(roomName);
            return ResponseEntity.ok(ApiResponse.ok(recordings));
        } catch (Exception e) {
            log.error("[P82] 查询录制信息失败 - room={}", roomName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "查询录制信息失败: " + e.getMessage()));
        }
    }

    // ========== 内部方法 ==========

    /**
     * 从 Egress 录制任务中查找最新的录制文件路径
     */
    private String findLatestRecordingPath(String roomName) {
        try {
            List<Map<String, Object>> recordings = egressService.listRecordings(roomName);
            // 查找已完成的录制任务
            Map<String, Object> latestCompleted = recordings.stream()
                    .filter(r -> "EGRESS_COMPLETE".equals(r.get("status"))
                            || "COMPLETE".equals(r.get("status")))
                    .findFirst()
                    .orElse(null);

            if (latestCompleted != null && latestCompleted.containsKey("file")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fileInfo = (Map<String, Object>) latestCompleted.get("file");
                if (fileInfo.containsKey("filepath")) {
                    return (String) fileInfo.get("filepath");
                }
            }

            // 没有找到已完成的录制，返回 null
            return null;
        } catch (Exception e) {
            log.warn("[P82] 查找录制文件路径失败 - room={}", roomName, e);
            return null;
        }
    }

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
     * 生成纪要请求体
     */
    public record GenerateMinutesRequest(
            String recordingFilePath
    ) {}
}
