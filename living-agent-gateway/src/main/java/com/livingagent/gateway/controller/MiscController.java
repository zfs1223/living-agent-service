package com.livingagent.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.core.database.repository.NotificationRepository;
import com.livingagent.gateway.controller.common.ApiResponse;

/**
 * 辅助端点控制器。
 *
 * <p>包含版本信息和通知统计等辅助功能。</p>
 */
@RestController
@RequestMapping("/api")
public class MiscController {

    private final NotificationRepository notificationRepository;

    public MiscController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<VersionInfo>> getVersion() {
        VersionInfo info = new VersionInfo("1.0.0", "Living Agent Service", "2026-04-07");
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    /**
     * 获取未读通知计数（全局）。
     *
     * @return 全局未读通知数
     */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount() {
        long unread = notificationRepository.countByReadFalse();
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCount((int) unread)));
    }

    public record VersionInfo(String version, String name, String buildDate) {}

    public record UnreadCount(int unread_count) {}
}
