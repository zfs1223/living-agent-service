package com.livingagent.gateway.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/plaza")
public class PlazaController {

    private static final Logger log = LoggerFactory.getLogger(PlazaController.class);

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<PostInfo>>> getPosts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String tenant_id
    ) {
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

        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlazaStats>> getStats(
            @RequestParam(required = false) String tenant_id
    ) {
        log.debug("Getting plaza stats, tenant_id: {}", tenant_id);

        PlazaStats stats = new PlazaStats(
                1,
                0,
                1,
                Collections.emptyList()
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostInfo>> createPost(
            @RequestBody CreatePostRequest request,
            @RequestParam(required = false) String tenant_id
    ) {
        log.info("Creating plaza post: {}", request.content());

        PostInfo post = new PostInfo(
                "post_" + System.currentTimeMillis(),
                "user_" + System.currentTimeMillis(),
                "human",
                "当前用户",
                request.content(),
                0,
                0,
                Instant.now(),
                request.tags() != null ? request.tags() : Collections.emptyList()
        );

        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<PostInfo>> likePost(
            @PathVariable String postId,
            @RequestParam(required = false) String tenant_id
    ) {
        log.info("Liking post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }
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
