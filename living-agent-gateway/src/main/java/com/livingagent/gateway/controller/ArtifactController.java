package com.livingagent.gateway.controller;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.database.entity.ArtifactRecordEntity;
import com.livingagent.core.database.repository.ArtifactRecordRepository;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.security.ArtifactAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class);
    private static final String ARTIFACTS_BASE_DIR = System.getProperty("livingagent.artifact.dir", "data/artifacts");
    private static final String ARTIFACTS_DEFAULT_DIR = "data/artifacts";

    private final ArtifactRecordService artifactRecordService;
    private final ArtifactRecordRepository artifactRecordRepository;
    private final ArtifactAccessService accessService;
    private final UnifiedAuthService authService;

    public ArtifactController(ArtifactRecordService artifactRecordService,
                              ArtifactRecordRepository artifactRecordRepository,
                              ArtifactAccessService accessService,
                              UnifiedAuthService authService) {
        this.artifactRecordService = artifactRecordService;
        this.artifactRecordRepository = artifactRecordRepository;
        this.accessService = accessService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<ArtifactRecord>> listArtifacts(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<ArtifactRecord> artifacts;
        
        if (taskId != null) {
            artifacts = artifactRecordService.getByTaskId(taskId);
        } else if (projectId != null) {
            artifacts = artifactRecordService.getByProjectId(projectId);
        } else if (executionId != null) {
            artifacts = artifactRecordService.getByExecutionId(executionId);
        } else if (department != null && type != null) {
            artifacts = artifactRecordService.getByDepartmentAndType(department, type);
        } else if (department != null) {
            artifacts = artifactRecordService.getByDepartment(department);
        } else if (employeeCode != null) {
            artifacts = artifactRecordService.getByEmployeeCode(employeeCode);
        } else if (type != null) {
            Page<ArtifactRecord> pageResult = artifactRecordService.getByType(type, 
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            artifacts = pageResult.getContent();
        } else {
            Page<ArtifactRecord> pageResult = artifactRecordService.getAllOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            artifacts = pageResult.getContent();
        }
        
        return ApiResponse.ok(artifacts);
    }

    @GetMapping("/{artifactId}")
    public ApiResponse<ArtifactRecord> getArtifactDetail(@PathVariable String artifactId) {
        return artifactRecordService.getArtifact(artifactId)
            .map(ApiResponse::ok)
            .orElse(ApiResponse.err("NOT_FOUND", "Artifact not found: " + artifactId));
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable String artifactId,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return artifactRecordService.getArtifact(artifactId)
            .map(record -> {
                // ★ 权限校验
                AuthContext user = resolveUser(headerEmployeeId, authorization);
                if (user != null) {
                    var entity = artifactRecordRepository.findByArtifactId(artifactId).orElse(null);
                    if (entity != null && !accessService.canView(entity, user)) {
                        log.warn("User {} denied download of artifact {}",
                            user.getEmployeeId(), artifactId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Resource>build();
                    }
                }
                try {
                    Path filePath = Paths.get(record.path());
                    if (!Files.exists(filePath)) {
                        return ResponseEntity.notFound().<Resource>build();
                    }

                    Resource resource = new FileSystemResource(filePath);
                    String contentType = detectContentType(record.name());

                    return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.name() + "\"")
                        .body(resource);
                } catch (Exception e) {
                    log.error("Failed to download artifact {}: {}", artifactId, e.getMessage());
                    return ResponseEntity.internalServerError().<Resource>build();
                }
            })
            .orElse(ResponseEntity.notFound().<Resource>build());
    }

    @GetMapping("/{artifactId}/preview")
    public ResponseEntity<Resource> previewArtifact(@PathVariable String artifactId) {
        return artifactRecordService.getArtifact(artifactId)
            .map(record -> {
                try {
                    Path filePath = Paths.get(record.path());
                    if (!Files.exists(filePath)) {
                        return ResponseEntity.notFound().<Resource>build();
                    }
                    
                    Resource resource = new FileSystemResource(filePath);
                    String contentType = detectContentType(record.name());
                    
                    return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
                } catch (Exception e) {
                    log.error("Failed to preview artifact {}: {}", artifactId, e.getMessage());
                    return ResponseEntity.internalServerError().<Resource>build();
                }
            })
            .orElse(ResponseEntity.notFound().<Resource>build());
    }

    @GetMapping("/by-execution/{executionId}")
    public ApiResponse<List<ArtifactRecord>> getArtifactsByExecution(@PathVariable String executionId) {
        return ApiResponse.ok(artifactRecordService.getByExecutionId(executionId));
    }

    @GetMapping("/by-task/{taskId}")
    public ApiResponse<List<ArtifactRecord>> getArtifactsByTask(@PathVariable String taskId) {
        return ApiResponse.ok(artifactRecordService.getByTaskId(taskId));
    }

    @GetMapping("/by-project/{projectId}")
    public ApiResponse<List<ArtifactRecord>> getArtifactsByProject(@PathVariable String projectId) {
        return ApiResponse.ok(artifactRecordService.getByProjectId(projectId));
    }

    @GetMapping("/by-department/{department}")
    public ApiResponse<List<ArtifactRecord>> getArtifactsByDepartment(@PathVariable String department) {
        return ApiResponse.ok(artifactRecordService.getByDepartment(department));
    }

    @GetMapping("/by-employee/{employeeCode}")
    public ApiResponse<List<ArtifactRecord>> getArtifactsByEmployee(@PathVariable String employeeCode) {
        return ApiResponse.ok(artifactRecordService.getByEmployeeCode(employeeCode));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String executionId) {
        
        long totalCount;
        if (department != null) {
            totalCount = artifactRecordService.countByDepartment(department);
        } else if (executionId != null) {
            totalCount = artifactRecordService.countByExecutionId(executionId);
        } else {
            totalCount = artifactRecordRepository.count();
        }
        
        return ApiResponse.ok(Map.of(
            "totalCount", totalCount,
            "department", department != null ? department : "all",
            "executionId", executionId != null ? executionId : "all"
        ));
    }

    @PostMapping("/reindex")
    public ApiResponse<Map<String, Object>> reindexArtifacts(
            @RequestParam(defaultValue = ARTIFACTS_DEFAULT_DIR) String baseDir) {

        // 若调用方未显式指定（使用默认占位），则回退到运行时系统属性
        String effectiveDir = ARTIFACTS_DEFAULT_DIR.equals(baseDir) ? ARTIFACTS_BASE_DIR : baseDir;
        log.info("Starting artifact reindex from directory: {}", effectiveDir);
        List<ArtifactRecord> indexed = artifactRecordService.scanAndIndexDirectory(effectiveDir);

        return ApiResponse.ok(Map.of(
            "indexedCount", indexed.size(),
            "baseDir", effectiveDir
        ));
    }

    /* ============ 权限过滤 API（HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.18）============ */

    /**
     * 列出当前用户可见的产物（推荐使用）
     * - 内部走 DB 拿到 ArtifactRecordEntity（带 visibility 字段）
     * - 调用 ArtifactAccessService.canView 过滤
     * - 仅返回有权限的产物
     */
    @GetMapping("/my-visible")
    public ApiResponse<List<ArtifactRecord>> listMyVisibleArtifacts(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        AuthContext user = resolveUser(employeeId, authorization);
        if (user == null) {
            // 未登录：返回空（前端可引导登录）
            return ApiResponse.ok(List.of());
        }

        // 拉取候选集合（按部门/全员）
        List<ArtifactRecordEntity> candidates = loadVisibleCandidates(user,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // 权限过滤
        List<ArtifactRecordEntity> visible = accessService.filterVisible(candidates, user);
        List<ArtifactRecord> result = visible.stream()
            .map(this::toRecord)
            .toList();
        return ApiResponse.ok(result);
    }

    /**
     * 按部门列出产物（需要权限校验）
     * - 仅本部门成员 / 部门领导 / 董事长可调用
     */
    @GetMapping("/by-department-accessible/{department}")
    public ApiResponse<List<ArtifactRecord>> listArtifactsByDepartmentAccessible(
            @PathVariable String department,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        AuthContext user = resolveUser(employeeId, authorization);
        if (user == null) {
            return ApiResponse.err("UNAUTHORIZED", "Authentication required");
        }
        if (!canAccessDepartment(department, user)) {
            return ApiResponse.err("FORBIDDEN", "无权限查看其他部门的产物");
        }
        List<ArtifactRecord> all = artifactRecordService.getByDepartment(department);
        return ApiResponse.ok(all);
    }

    /* ============ 辅助方法 ============ */

    private List<ArtifactRecordEntity> loadVisibleCandidates(AuthContext user, PageRequest pageRequest) {
        try {
            String dept = user.getDepartment();
            boolean isChairman = user.isFounder() || user.getAccessLevel() != null
                && user.getAccessLevel().name().equals("FULL");

            if (isChairman) {
                // 董事长/FULL 拉取所有
                return artifactRecordRepository.findAll(pageRequest).getContent();
            }
            if (dept != null && !dept.isBlank()) {
                // 普通用户/部门领导：拉取本部门 + PUBLIC（一次 SQL）
                return artifactRecordRepository.findByDepartmentOrPublic(dept);
            }
            return List.of();
        } catch (Exception e) {
            log.error("loadVisibleCandidates failed", e);
            return List.of();
        }
    }

    private boolean canAccessDepartment(String department, AuthContext user) {
        if (user == null) return false;
        if (user.isFounder()) return true;
        if (user.getAccessLevel() != null && user.getAccessLevel().name().equals("FULL")) return true;
        return department != null && department.equalsIgnoreCase(user.getDepartment());
    }

    private AuthContext resolveUser(String headerEmployeeId, String authorization) {
        // 优先从 Authorization: Bearer {sessionId} 验证
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String sessionId = authorization.substring(7);
            java.util.Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
            if (sessionOpt.isPresent()) {
                return sessionOpt.get().authContext();
            }
        }
        // 降级：从 X-Employee-Id header 构建 AuthContext
        if (headerEmployeeId != null && !headerEmployeeId.isBlank()) {
            AuthContext ctx = new AuthContext();
            ctx.setEmployeeId(headerEmployeeId);
            return ctx;
        }
        return null;
    }

    private ArtifactRecord toRecord(ArtifactRecordEntity entity) {
        // 转换 Entity → Record（保持兼容）
        return new ArtifactRecord(
            entity.getArtifactId(),
            entity.getExecutionId(),
            entity.getDepartment(),
            entity.getOwnerEmployeeCode(),
            entity.getOwnerEmployeeNeuronId(),
            entity.getType(),
            entity.getPath(),
            entity.getName(),
            entity.getSummary(),
            entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
            entity.getSha256(),
            entity.getTaskId(),
            entity.getProjectId(),
            List.of(),
            entity.getCreatedAt(),
            Map.of()
        );
    }

    private String detectContentType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) 
            return MediaType.TEXT_HTML_VALUE;
        if (fileName.endsWith(".css")) 
            return "text/css";
        if (fileName.endsWith(".js")) 
            return "application/javascript";
        if (fileName.endsWith(".json")) 
            return MediaType.APPLICATION_JSON_VALUE;
        if (fileName.endsWith(".md")) 
            return "text/markdown";
        if (fileName.endsWith(".txt")) 
            return MediaType.TEXT_PLAIN_VALUE;
        if (fileName.endsWith(".png")) 
            return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) 
            return "image/jpeg";
        if (fileName.endsWith(".gif")) 
            return "image/gif";
        if (fileName.endsWith(".svg")) 
            return "image/svg+xml";
        if (fileName.endsWith(".pdf")) 
            return "application/pdf";
        
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
