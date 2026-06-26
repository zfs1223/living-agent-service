package com.livingagent.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.gateway.controller.common.ApiResponse;

/**
 * 辅助端点控制器。
 *
 * <p>包含版本信息和通知统计等辅助功能。
 * 注意：此类端点应谨慎新增，避免演变成"垃圾桶"端点。</p>
 *
 * <p><b>待实现项</b>：</p>
 * <ul>
 *   <li>未读通知计数 - 当前返回硬编码 0，需接入通知服务</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class MiscController {

    /**
     * 获取服务版本信息。
     *
     * @return 版本信息（version, name, buildDate）
     */
    @GetMapping("/version")
    public ResponseEntity<ApiResponse<VersionInfo>> getVersion() {
        VersionInfo info = new VersionInfo("1.0.0", "Living Agent Service", "2026-04-07");
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    /**
     * 获取未读通知计数 - <b>待实现</b>。
     *
     * <p>当前返回硬编码 0。如需真实数据，需接入通知服务
     * （如 NotificationService 或 WebSocket 推送统计）。</p>
     *
     * @return 未读通知数（当前为 0）
     */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount() {
        // TODO: 接入通知服务获取真实未读数
        UnreadCount count = new UnreadCount(0);
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    public record VersionInfo(String version, String name, String buildDate) {}

    public record UnreadCount(int unread_count) {}
}
