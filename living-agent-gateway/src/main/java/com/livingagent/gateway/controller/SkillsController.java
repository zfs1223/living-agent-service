package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.skill.feedback.SkillEffectivenessTracker;
import com.livingagent.gateway.controller.common.ApiResponse;
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
    private final AccessGateService accessGateService;
    private final SkillEffectivenessTracker skillEffectivenessTracker;
    private final SkillRegistry skillRegistry;

    public SkillsController(AccessGateService accessGateService, 
                           SkillEffectivenessTracker skillEffectivenessTracker,
                           SkillRegistry skillRegistry) {
        this.accessGateService = accessGateService;
        this.skillEffectivenessTracker = skillEffectivenessTracker;
        this.skillRegistry = skillRegistry;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills(
            @RequestParam(required = false) String brain,
            @RequestParam(required = false) String department,
            @RequestParam(required = false, defaultValue = "false") boolean personalAssistant,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing skills, brain: {}, department: {}, personalAssistant: {}", brain, department, personalAssistant);

        // 个人助手视图：只返回 personalSafe 的技能
        List<Skill> skillsToReturn;
        if (personalAssistant) {
            skillsToReturn = skillRegistry.getPersonalAssistantVisibleSkills(employeeId);
        } else {
            skillsToReturn = skillRegistry.getAllSkills();
        }

        List<SkillInfo> skills = new ArrayList<>();
        for (Skill skill : skillsToReturn) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) skill.getMetadata().getOrDefault("tags", new ArrayList<>());
            String version = (String) skill.getMetadata().getOrDefault("version", "1.0.0");
            String displayName = buildDisplayName(skill.getName(), skill.getDescription());

            skills.add(new SkillInfo(
                    skill.getName(),
                    skill.getName(),
                    displayName,
                    skill.getDescription() != null ? skill.getDescription() : "",
                    skill.getTargetBrain() != null ? skill.getTargetBrain() : "global",
                    tags,
                    version,
                    skill.isPersonalSafe(),
                    Instant.now()
            ));
        }

        log.info("Loaded {} skills (personalAssistant={})", skills.size(), personalAssistant);
        return ResponseEntity.ok(ApiResponse.ok(skills));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillInfo>> getSkill(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting skill: {}", id);

        // 从 SkillRegistry 查找技能
        return skillRegistry.getSkill(id)
                .map(skill -> {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) skill.getMetadata().getOrDefault("tags", new ArrayList<>());
                    String version = (String) skill.getMetadata().getOrDefault("version", "1.0.0");

                    String displayName = buildDisplayName(skill.getName(), skill.getDescription());
                    SkillInfo skillInfo = new SkillInfo(
                            skill.getName(),
                            skill.getName(),
                            displayName,
                            skill.getDescription() != null ? skill.getDescription() : "",
                            skill.getTargetBrain() != null ? skill.getTargetBrain() : "global",
                            tags,
                            version,
                            skill.isPersonalSafe(),
                            Instant.now()
                    );
                    return ResponseEntity.ok(ApiResponse.ok(skillInfo));
                })
                .orElse(ResponseEntity.status(404).body(ApiResponse.err("not_found", "Skill not found: " + id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillInfo>> createSkill(
            @RequestBody CreateSkillRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating skill: {}", request.name());

        SkillInfo skill = new SkillInfo(
                "skill_" + System.currentTimeMillis(),
                request.name(),
                request.name(),
                request.description(),
                request.department(),
                request.tags(),
                request.version(),
                false,
                Instant.now()
        );

        skillEffectivenessTracker.recordInvocation(skill.id(), true, 0);
        return ResponseEntity.ok(ApiResponse.ok(skill));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillInfo>> updateSkill(
            @PathVariable String id,
            @RequestBody UpdateSkillRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating skill: {}", id);

        SkillInfo skill = new SkillInfo(
                id,
                request.name(),
                request.name(),
                request.description(),
                request.department(),
                request.tags(),
                request.version(),
                false,
                Instant.now()
        );

        skillEffectivenessTracker.recordInvocation(id, true, 0);
        return ResponseEntity.ok(ApiResponse.ok(skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSkill(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting skill: {}", id);

        skillEffectivenessTracker.recordInvocation(id, true, 0);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "id", id)));
    }

    @GetMapping("/browse/list")
    public ResponseEntity<ApiResponse<List<FileInfo>>> browseList(
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Browsing skills path: {}", path);

        List<FileInfo> files = new ArrayList<>();
        files.add(new FileInfo("skill1.yaml", "file", 1024, Instant.now()));
        files.add(new FileInfo("skill2.yaml", "file", 2048, Instant.now()));

        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    @GetMapping("/browse/read")
    public ResponseEntity<ApiResponse<FileContent>> browseRead(
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Reading skill file: {}", path);

        FileContent content = new FileContent(path, "yaml content here...");
        return ResponseEntity.ok(ApiResponse.ok(content));
    }

    @PutMapping("/browse/write")
    public ResponseEntity<ApiResponse<FileContent>> browseWrite(
            @RequestBody WriteFileRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Writing skill file: {}", request.path());

        FileContent content = new FileContent(request.path(), request.content());
        return ResponseEntity.ok(ApiResponse.ok(content));
    }

    @DeleteMapping("/browse/delete")
    public ResponseEntity<ApiResponse<Map<String, String>>> browseDelete(
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting skill file: {}", path);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "path", path)));
    }

    @GetMapping("/clawhub/search")
    public ResponseEntity<ApiResponse<List<ClawHubSkill>>> searchClawHub(
            @RequestParam String q,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
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

        return ResponseEntity.ok(ApiResponse.ok(skills));
    }

    @GetMapping("/clawhub/detail/{slug}")
    public ResponseEntity<ApiResponse<ClawHubSkillDetail>> getClawHubDetail(
            @PathVariable String slug,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
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

        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @PostMapping("/clawhub/install")
    public ResponseEntity<ApiResponse<Map<String, String>>> installFromClawHub(
            @RequestBody InstallRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Installing from ClawHub: {}", request.slug());

        skillEffectivenessTracker.recordInvocation("clawhub:" + request.slug(), true, 0);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "installed", "slug", request.slug())));
    }

    @PostMapping("/import-from-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> importFromUrl(
            @RequestBody ImportUrlRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Importing skill from URL: {}", request.url());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "imported", "url", request.url())));
    }

    @PostMapping("/import-from-url/preview")
    public ResponseEntity<ApiResponse<SkillPreview>> previewImportFromUrl(
            @RequestBody ImportUrlRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Previewing import from URL: {}", request.url());

        SkillPreview preview = new SkillPreview(
                "预览技能",
                "从URL导入的技能预览",
                "tech",
                List.of("preview")
        );

        return ResponseEntity.ok(ApiResponse.ok(preview));
    }

    @GetMapping("/settings/token")
    public ResponseEntity<ApiResponse<TokenSettings>> getTokenSettings(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting token settings");

        TokenSettings settings = new TokenSettings("ghp_xxxxxxxxxxxx", true);
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/settings/token")
    public ResponseEntity<ApiResponse<TokenSettings>> updateTokenSettings(
            @RequestBody TokenSettings settings,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating token settings");

        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    /**
     * 构建技能展示名称：优先取描述中的中文部分（逗号前），否则返回 name。
     * 例如：description="代码审查技能，自动化代码质量检查" → "代码审查技能"
     *       description="Summarize or extract..." → name
     */
    private String buildDisplayName(String name, String description) {
        if (description == null || description.isBlank()) {
            return name;
        }
        // 中文描述通常以逗号、顿号分隔，取第一部分作为简洁名称
        String desc = description.trim();
        // 去掉引号包裹
        if ((desc.startsWith("\"") && desc.endsWith("\"")) || (desc.startsWith("'") && desc.endsWith("'"))) {
            desc = desc.substring(1, desc.length() - 1).trim();
        }
        // 如果包含中文，取逗号前部分作为展示名
        if (desc.matches(".*[\\u4e00-\\u9fff].*")) {
            int commaIdx = desc.indexOf('，');
            if (commaIdx < 0) commaIdx = desc.indexOf(',');
            if (commaIdx > 0) {
                return desc.substring(0, commaIdx).trim();
            }
            // 无逗号，截取前20个字符
            if (desc.length() > 20) {
                return desc.substring(0, 20) + "...";
            }
            return desc;
        }
        // 英文描述，取逗号前部分
        int commaIdx = desc.indexOf(',');
        if (commaIdx > 0 && commaIdx <= 30) {
            return desc.substring(0, commaIdx).trim();
        }
        // 无合适的截取点，返回 name
        return name;
    }

    public record SkillInfo(
            String id,
            String name,
            String displayName,
            String description,
            String department,
            List<String> tags,
            String version,
            boolean personalSafe,
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
