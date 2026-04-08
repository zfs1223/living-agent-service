package com.livingagent.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.gateway.controller.common.ApiResponse;

@RestController
@RequestMapping("/api")
public class MiscController {

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<VersionInfo>> getVersion() {
        VersionInfo info = new VersionInfo("1.0.0", "Living Agent Service", "2026-04-07");
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<UnreadCount>> getUnreadCount(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        UnreadCount count = new UnreadCount(0);
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    public record VersionInfo(String version, String name, String buildDate) {}

    public record UnreadCount(int unread_count) {}
}
