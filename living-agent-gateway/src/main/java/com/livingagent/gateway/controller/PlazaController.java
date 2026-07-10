package com.livingagent.gateway.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.social.feedback.PlazaEngagementTracker;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/plaza")
public class PlazaController {

    private static final Logger log = LoggerFactory.getLogger(PlazaController.class);
    private final AccessGateService accessGateService;

    @Autowired(required = false)
    private PlazaEngagementTracker plazaEngagementTracker;

    public PlazaController(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<PostInfo>>> getPosts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting plaza posts, limit: {}, tenant_id: {}", limit, tenant_id);

        List<PostInfo> posts = new ArrayList<>();
        posts.add(new PostInfo(
                "post_1",
                "system",
                "agent",
                "系统管理员",
                "欢迎使用 Living Agent，这是一个智能体协作平台。",
                0,
                0,
                Instant.now(),
                Collections.emptyList()
        ));

        return ResponseEntity.ok(ApiResponse.ok(posts));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlazaStats>> getStats(
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting plaza stats, tenant_id: {}", tenant_id);

        PlazaStats stats = new PlazaStats(
                1,
                0,
                1,
                Collections.emptyList()
        );

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostInfo>> createPost(
            @RequestBody CreatePostRequest request,
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating plaza post: {}", request.content());

        String postId = "post_" + System.currentTimeMillis();
        if (plazaEngagementTracker != null) {
            plazaEngagementTracker.recordPost(postId);
        }

        PostInfo post = new PostInfo(
                postId,
                "user_" + System.currentTimeMillis(),
                "human",
                "当前用户",
                request.content(),
                0,
                0,
                Instant.now(),
                request.tags() != null ? request.tags() : Collections.emptyList()
        );

        return ResponseEntity.ok(ApiResponse.ok(post));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<PostInfo>> likePost(
            @PathVariable String postId,
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Liking post: {}", postId);
        if (plazaEngagementTracker != null) {
            plazaEngagementTracker.recordLike(postId);
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record PostInfo(
            String id,
            @JsonProperty("author_id") String authorId,
            @JsonProperty("author_type") String authorType,
            @JsonProperty("author_name") String authorName,
            String content,
            @JsonProperty("likes_count") int likesCount,
            @JsonProperty("comments_count") int commentsCount,
            @JsonProperty("created_at") Instant createdAt,
            List<String> tags
    ) {}

    public record PlazaStats(
            @JsonProperty("total_posts") int totalPosts,
            @JsonProperty("total_comments") int totalComments,
            @JsonProperty("today_posts") int todayPosts,
            @JsonProperty("top_contributors") List<TopContributor> topContributors
    ) {}

    public record TopContributor(
            String name,
            String type,
            int posts
    ) {}

    public record CreatePostRequest(
            String content,
            List<String> tags
    ) {}
}
