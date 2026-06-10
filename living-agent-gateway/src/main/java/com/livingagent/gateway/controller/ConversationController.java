package com.livingagent.gateway.controller;

import com.livingagent.core.conversation.ConversationPermissionService;
import com.livingagent.core.conversation.ConversationService;
import com.livingagent.core.conversation.ConversationStatus;
import com.livingagent.core.database.entity.DepartmentConversationEntity;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private static final List<String> ACTIVE_STATUSES = ConversationStatus.activeDbValues();
    private static final Set<String> VALID_STATUS_VALUES = Arrays.stream(ConversationStatus.values())
            .map(ConversationStatus::getDbValue)
            .collect(Collectors.toSet());

    private final ConversationService conversationService;
    private final ConversationPermissionService permissionService;
    private final UnifiedAuthService authService;

    public ConversationController(ConversationService conversationService,
                                 ConversationPermissionService permissionService,
                                 UnifiedAuthService authService) {
        this.conversationService = conversationService;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentConversationEntity>>> listConversations(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        List<String> statuses;
        if (status == null || status.isBlank()) {
            statuses = ACTIVE_STATUSES;
        } else {
            // 支持逗号分隔的多状态筛选
            String[] parts = status.split(",");
            List<String> parsed = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim().toLowerCase();
                if (!VALID_STATUS_VALUES.contains(trimmed)) {
                    return ResponseEntity.status(400).body(ApiResponse.err(
                            "invalid_status", "Invalid status value: '" + trimmed + "'. Valid values: " + VALID_STATUS_VALUES));
                }
                parsed.add(trimmed);
            }
            statuses = parsed;
        }

        List<DepartmentConversationEntity> conversations = conversationService.listConversations(
                ctx.getEmployeeId(), department, statuses, limit, offset);

        return ResponseEntity.ok(ApiResponse.ok(conversations));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<DepartmentConversationEntity>> getConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        return conversationService.getConversation(conversationId)
                .filter(conv -> permissionService.canViewConversation(conversationId, ctx))
                .map(conv -> ResponseEntity.ok(ApiResponse.ok(conv)))
                .orElse(ResponseEntity.status(404).body(ApiResponse.err("not_found", "Conversation not found")));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentConversationEntity>> createConversation(
            @RequestBody CreateConversationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        DepartmentConversationEntity conv = conversationService.createConversation(
                ctx.getEmployeeId(), request.departmentCode(), ctx.getTenantId(), request.title());

        return ResponseEntity.ok(ApiResponse.ok(conv));
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<DepartmentConversationEntity>> updateConversation(
            @PathVariable String conversationId,
            @RequestBody UpdateConversationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        return conversationService.getConversation(conversationId)
                .filter(conv -> permissionService.canEditConversation(conversationId, ctx))
                .map(conv -> {
                    DepartmentConversationEntity updated = conversationService.updateConversation(
                            conversationId, request.title(), null);
                    return ResponseEntity.ok(ApiResponse.ok(updated));
                })
                .orElse(ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Cannot update this conversation")));
    }

    @PostMapping("/{conversationId}/archive")
    public ResponseEntity<ApiResponse<DepartmentConversationEntity>> archiveConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        return conversationService.getConversation(conversationId)
                .filter(conv -> permissionService.canEditConversation(conversationId, ctx))
                .map(conv -> {
                    DepartmentConversationEntity archived = conversationService.archiveConversation(conversationId);
                    return ResponseEntity.ok(ApiResponse.ok(archived));
                })
                .orElse(ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Cannot archive this conversation")));
    }

    @PostMapping("/{conversationId}/restore")
    public ResponseEntity<ApiResponse<DepartmentConversationEntity>> restoreConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        return conversationService.getConversation(conversationId)
                .filter(conv -> permissionService.canEditConversation(conversationId, ctx))
                .map(conv -> {
                    DepartmentConversationEntity restored = conversationService.restoreConversation(conversationId);
                    return ResponseEntity.ok(ApiResponse.ok(restored));
                })
                .orElse(ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Cannot restore this conversation")));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }

        return conversationService.getConversation(conversationId)
                .filter(conv -> permissionService.canDeleteConversation(conversationId, ctx))
                .map(conv -> {
                    conversationService.deleteConversation(conversationId);
                    return ResponseEntity.ok(ApiResponse.<Void>ok());
                })
                .orElse(ResponseEntity.status(403).body(ApiResponse.<Void>err("forbidden", "Cannot delete this conversation")));
    }

    /**
     * 销毁会话（软删除增强版）：将对话标记为已删除并清除敏感内容，
     * 但不会物理删除数据库记录，以便审计和恢复。
     * 仅管理员/创始人可执行此操作。
     */
    @PostMapping("/{conversationId}/destroy")
    public ResponseEntity<ApiResponse<Void>> destroyConversation(
            @PathVariable String conversationId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx == null) {
            return ResponseEntity.status(401).body(ApiResponse.<Void>err("unauthorized", "Not authenticated"));
        }

        if (!permissionService.canDestroyConversation(conversationId, ctx)) {
            return ResponseEntity.status(403).body(ApiResponse.<Void>err("forbidden",
                    "Only admin or founder can destroy conversations. This is a soft-delete enhancement, not physical destruction."));
        }

        conversationService.destroyConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.<Void>ok());
    }

    private AuthContext resolveAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        return authService.validateSession(token)
                .map(UnifiedAuthService.AuthSession::authContext)
                .orElse(null);
    }

    public record CreateConversationRequest(String departmentCode, String title) {}
    public record UpdateConversationRequest(String title) {}
}
