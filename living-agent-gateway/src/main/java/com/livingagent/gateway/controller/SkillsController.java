package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private static final Logger log = LoggerFactory.getLogger(SkillsController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills(
            @RequestParam(required = false) String brain,
            @RequestParam(required = false) String department
    ) {
        log.debug("Listing skills, brain: {}, department: {}", brain, department);

        List<SkillInfo> skills = new ArrayList<>();
        skills.add(new SkillInfo(
                "skill_001",
                "代码审查",
                "自动审查代码质量",
                "tech",
                List.of("code-review", "quality"),
                "1.0.0",
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillInfo>> getSkill(@PathVariable String id) {
        log.debug("Getting skill: {}", id);

        SkillInfo skill = new SkillInfo(
                id,
                "代码审查",
                "自动审查代码质量",
                "tech",
                List.of("code-review", "quality"),
                "1.0.0",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillInfo>> createSkill(@RequestBody CreateSkillRequest request) {
        log.info("Creating skill: {}", request.name());

        SkillInfo skill = new SkillInfo(
                "skill_" + System.currentTimeMillis(),
                request.name(),
                request.description(),
                request.department(),
                request.tags(),
                request.version(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillInfo>> updateSkill(
            @PathVariable String id,
            @RequestBody UpdateSkillRequest request
    ) {
        log.info("Updating skill: {}", id);

        SkillInfo skill = new SkillInfo(
                id,
                request.name(),
                request.description(),
                request.department(),
                request.tags(),
                request.version(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSkill(@PathVariable String id) {
        log.info("Deleting skill: {}", id);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "id", id)));
    }

    @GetMapping("/browse/list")
    public ResponseEntity<ApiResponse<List<FileInfo>>> browseList(@RequestParam String path) {
        log.debug("Browsing skills path: {}", path);

        List<FileInfo> files = new ArrayList<>();
        files.add(new FileInfo("skill1.yaml", "file", 1024, Instant.now()));
        files.add(new FileInfo("skill2.yaml", "file", 2048, Instant.now()));

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/browse/read")
    public ResponseEntity<ApiResponse<FileContent>> browseRead(@RequestParam String path) {
        log.debug("Reading skill file: {}", path);

        FileContent content = new FileContent(path, "yaml content here...");
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @PutMapping("/browse/write")
    public ResponseEntity<ApiResponse<FileContent>> browseWrite(@RequestBody WriteFileRequest request) {
        log.info("Writing skill file: {}", request.path());

        FileContent content = new FileContent(request.path(), request.content());
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @DeleteMapping("/browse/delete")
    public ResponseEntity<ApiResponse<Map<String, String>>> browseDelete(@RequestParam String path) {
        log.info("Deleting skill file: {}", path);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "path", path)));
    }

    @GetMapping("/clawhub/search")
    public ResponseEntity<ApiResponse<List<ClawHubSkill>>> searchClawHub(@RequestParam String q) {
        log.debug("Searching ClawHub: {}", q);

        List<ClawHubSkill> skills = new ArrayList<>();
        skills.add(new ClawHubSkill(
                "code-reviewer",
                "代码审查员",
                "自动代码审查",
                "tech",
                100,
                4.5
        ));

        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    @GetMapping("/clawhub/detail/{slug}")
    public ResponseEntity<ApiResponse<ClawHubSkillDetail>> getClawHubDetail(@PathVariable String slug) {
        log.debug("Getting ClawHub detail: {}", slug);

        ClawHubSkillDetail detail = new ClawHubSkillDetail(
                slug,
                "代码审查员",
                "自动代码审查",
                "tech",
                100,
                4.5,
                "详细描述...",
                List.of("v1.0.0", "v1.1.0")
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/clawhub/install")
    public ResponseEntity<ApiResponse<Map<String, String>>> installFromClawHub(@RequestBody InstallRequest request) {
        log.info("Installing from ClawHub: {}", request.slug());

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "installed", "slug", request.slug())));
    }

    @PostMapping("/import-from-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> importFromUrl(@RequestBody ImportUrlRequest request) {
        log.info("Importing skill from URL: {}", request.url());

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "imported", "url", request.url())));
    }

    @PostMapping("/import-from-url/preview")
    public ResponseEntity<ApiResponse<SkillPreview>> previewImportFromUrl(@RequestBody ImportUrlRequest request) {
        log.debug("Previewing import from URL: {}", request.url());

        SkillPreview preview = new SkillPreview(
                "预览技能",
                "从URL导入的技能预览",
                "tech",
                List.of("preview")
        );

        return ResponseEntity.ok(ApiResponse.success(preview));
    }

    @GetMapping("/settings/token")
    public ResponseEntity<ApiResponse<TokenSettings>> getTokenSettings() {
        log.debug("Getting token settings");

        TokenSettings settings = new TokenSettings("ghp_xxxxxxxxxxxx", true);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping("/settings/token")
    public ResponseEntity<ApiResponse<TokenSettings>> updateTokenSettings(@RequestBody TokenSettings settings) {
        log.info("Updating token settings");

        return ResponseEntity.ok(ApiResponse.success(settings));
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

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record SkillInfo(
            String id,
            String name,
            String description,
            String department,
            List<String> tags,
            String version,
            Instant created_at
    ) {}

    public record CreateSkillRequest(
            String name,
            String description,
            String department,
            List<String> tags,
            String version
    ) {}

    public record UpdateSkillRequest(
            String name,
            String description,
            String department,
            List<String> tags,
            String version
    ) {}

    public record FileInfo(
            String name,
            String type,
            long size,
            Instant modified_at
    ) {}

    public record FileContent(
            String path,
            String content
    ) {}

    public record WriteFileRequest(
            String path,
            String content
    ) {}

    public record ClawHubSkill(
            String slug,
            String name,
            String description,
            String department,
            int downloads,
            double rating
    ) {}

    public record ClawHubSkillDetail(
            String slug,
            String name,
            String description,
            String department,
            int downloads,
            double rating,
            String readme,
            List<String> versions
    ) {}

    public record InstallRequest(
            String slug,
            String version
    ) {}

    public record ImportUrlRequest(
            String url
    ) {}

    public record SkillPreview(
            String name,
            String description,
            String department,
            List<String> tags
    ) {}

    public record TokenSettings(
            String github_token,
            boolean enabled
    ) {}
}
