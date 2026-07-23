package com.livingagent.gateway.meeting;

import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.meeting.MeetingDashboardService.MeetingStats;
import com.livingagent.gateway.meeting.MeetingDashboardService.MeetingSummary;
import com.livingagent.gateway.security.RequireAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 会议数据看板 REST API - 闭环 32 生命体征仪表盘扩展 / P85 会议深度集成
 *
 * <p>提供会议统计数据的查询接口，供前端仪表盘展示使用。</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>GET /api/meeting-dashboard/stats - 全局会议统计</li>
 *   <li>GET /api/meeting-dashboard/stats/{department} - 部门会议统计</li>
 *   <li>GET /api/meeting-dashboard/recent - 最近会议列表</li>
 * </ul>
 *
 * <h3>权限控制</h3>
 * <ul>
 *   <li>全局统计（/stats）需要 meeting:read 权限</li>
 *   <li>部门统计（/stats/{department}）需要 meeting:read 权限</li>
 *   <li>最近会议（/recent）需要 meeting:read 权限</li>
 * </ul>
 *
 * @author P85 会议深度集成
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/meeting-dashboard")
public class MeetingDashboardController {

    private static final Logger log = LoggerFactory.getLogger(MeetingDashboardController.class);

    private final MeetingDashboardService dashboardService;
    private final UnifiedAuthService authService;

    public MeetingDashboardController(
            MeetingDashboardService dashboardService,
            UnifiedAuthService authService) {
        this.dashboardService = dashboardService;
        this.authService = authService;
        log.info("[P85] MeetingDashboardController 初始化");
    }

    /**
     * 获取全局会议统计
     *
     * <p>聚合所有部门的会议统计数据，包括今日/本周会议数、
     * 平均参会人数、平均时长、纪要生成率、录制覆盖率、按部门拆分等。</p>
     *
     * @return 全局会议统计数据
     */
    @GetMapping("/stats")
    @RequireAccess(resource = "meeting", action = "read")
    public ResponseEntity<ApiResponse<MeetingStats>> getOverallStats() {
        try {
            MeetingStats stats = dashboardService.getOverallMeetingStats();
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            log.error("[P85] 获取全局会议统计失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("STATS_FAILED", "获取全局会议统计失败: " + e.getMessage()));
        }
    }

    /**
     * 获取部门会议统计
     *
     * <p>按部门维度聚合会议统计数据。</p>
     *
     * @param department 部门代码（如 tech、hr、finance）
     * @return 部门会议统计数据
     */
    @GetMapping("/stats/{department}")
    @RequireAccess(resource = "meeting", action = "read")
    public ResponseEntity<ApiResponse<MeetingStats>> getDepartmentStats(
            @PathVariable String department) {
        try {
            MeetingStats stats = dashboardService.getDepartmentMeetingStats(department);
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            log.error("[P85] 获取部门会议统计失败 - department={}", department, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("STATS_FAILED", "获取部门会议统计失败: " + e.getMessage()));
        }
    }

    /**
     * 获取最近会议列表
     *
     * <p>查询最近的会议预约记录，支持按部门过滤和数量限制。</p>
     *
     * @param department 部门代码（可选，不传则查全部）
     * @param limit      返回数量限制（默认10，最大50）
     * @return 最近的会议列表
     */
    @GetMapping("/recent")
    @RequireAccess(resource = "meeting", action = "read")
    public ResponseEntity<ApiResponse<List<MeetingSummary>>> getRecentMeetings(
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            // 限制最大返回数量
            if (limit > 50) {
                limit = 50;
            }
            if (limit < 1) {
                limit = 10;
            }

            List<MeetingSummary> meetings = dashboardService.getRecentMeetings(department, limit);
            return ResponseEntity.ok(ApiResponse.ok(meetings));
        } catch (Exception e) {
            log.error("[P85] 获取最近会议列表失败 - department={}", department, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.err("QUERY_FAILED", "获取最近会议列表失败: " + e.getMessage()));
        }
    }
}
