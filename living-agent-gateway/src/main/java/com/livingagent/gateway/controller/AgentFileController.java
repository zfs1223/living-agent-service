package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{agentId:.+}/files")
public class AgentFileController {

    private static final Logger log = LoggerFactory.getLogger(AgentFileController.class);
    private static final Path ARTIFACT_ROOT = Path.of(
            System.getProperty("livingagent.artifact.dir", "data/artifacts")).toAbsolutePath().normalize();
    private final AccessGateService accessGateService;

    public AgentFileController(AccessGateService accessGateService) {
        this.accessGateService = accessGateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileInfo>>> listFiles(
            @PathVariable String agentId,
            @RequestParam(required = false) String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing files for agent: {}, path: {}", agentId, path);

        try {
            Path target = resolveSafeArtifactPath(path);
            List<FileInfo> files = new ArrayList<>();
            if (Files.exists(target) && Files.isDirectory(target)) {
                try (java.util.stream.Stream<Path> stream = Files.list(target)) {
                    stream.forEach(file -> {
                        try {
                            files.add(new FileInfo(
                                    ARTIFACT_ROOT.relativize(file.toAbsolutePath().normalize()).toString().replace("\\\\", "/"),
                                    Files.isDirectory(file) ? "directory" : "file",
                                    Files.isDirectory(file) ? 0 : Files.size(file),
                                    Files.getLastModifiedTime(file).toInstant()
                            ));
                        } catch (IOException e) {
                            log.debug("Skip artifact file while listing: {}", e.getMessage());
                        }
                    });
                }
            }
            return ResponseEntity.ok(ApiResponse.ok(files));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", e.getMessage()));
        }
    }

    @GetMapping("/content")
    public ResponseEntity<ApiResponse<FileContent>> readFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Reading file: {} for agent: {}", path, agentId);

        try {
            Path target = resolveSafeArtifactPath(path);
            if (!Files.exists(target) || Files.isDirectory(target)) {
                return ResponseEntity.status(404).body(ApiResponse.err("not_found", "File not found"));
            }
            FileContent content = new FileContent(path, Files.readString(target, StandardCharsets.UTF_8));
            return ResponseEntity.ok(ApiResponse.ok(content));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", e.getMessage()));
        }
    }

    @PutMapping("/content")
    public ResponseEntity<ApiResponse<FileContent>> writeFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestBody WriteFileRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Writing file: {} for agent: {}", path, agentId);

        try {
            Path target = resolveSafeArtifactPath(path);
            Files.createDirectories(target.getParent());
            String contentValue = request != null && request.content() != null ? request.content() : "";
            Files.writeString(target, contentValue, StandardCharsets.UTF_8);
            FileContent content = new FileContent(ARTIFACT_ROOT.relativize(target).toString().replace("\\\\", "/"), contentValue);
            return ResponseEntity.ok(ApiResponse.ok(content));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", e.getMessage()));
        }
    }

    @DeleteMapping("/content")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting file: {} for agent: {}", path, agentId);

        try {
            Path target = resolveSafeArtifactPath(path);
            if (!Files.exists(target)) {
                return ResponseEntity.status(404).body(ApiResponse.err("not_found", "File not found"));
            }
            if (Files.isDirectory(target)) {
                return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", "Directory delete is not supported"));
            }
            Files.delete(target);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "path", path)));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileInfo>> uploadFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Uploading file: {} for agent: {}", path, agentId);

        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
            Path targetDir = resolveSafeArtifactPath(path);
            Path target = Files.isDirectory(targetDir) || !originalFilename.equals(targetDir.getFileName().toString())
                    ? targetDir.resolve(originalFilename).toAbsolutePath().normalize()
                    : targetDir;
            if (!target.startsWith(ARTIFACT_ROOT)) {
                throw new IllegalArgumentException("Path escapes artifact root");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            FileInfo info = new FileInfo(
                    ARTIFACT_ROOT.relativize(target).toString().replace("\\\\", "/"),
                    "file",
                    Files.size(target),
                    Files.getLastModifiedTime(target).toInstant()
            );
            return ResponseEntity.ok(ApiResponse.ok(info));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_path", e.getMessage()));
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<byte[]> previewFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body("forbidden".getBytes(StandardCharsets.UTF_8));
        }
        try {
            Path target = resolveSafeArtifactPath(path);
            if (!Files.exists(target) || Files.isDirectory(target)) {
                return ResponseEntity.status(404).body("not found".getBytes(StandardCharsets.UTF_8));
            }
            return ResponseEntity.ok()
                    .contentType(detectMediaType(target))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + target.getFileName() + "\"")
                    .body(Files.readAllBytes(target));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body("forbidden".getBytes());
        }
        log.debug("Downloading file: {} for agent: {}", path, agentId);

        try {
            Path target = resolveSafeArtifactPath(path);
            if (!Files.exists(target) || Files.isDirectory(target)) {
                return ResponseEntity.status(404).body("not found".getBytes(StandardCharsets.UTF_8));
            }
            MediaType mediaType = detectMediaType(target);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + target.getFileName() + "\"")
                    .body(Files.readAllBytes(target));
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    private MediaType detectMediaType(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString().toLowerCase() : "";
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return MediaType.TEXT_HTML;
        }
        if (fileName.endsWith(".md") || fileName.endsWith(".txt") || fileName.endsWith(".json") || fileName.endsWith(".js") || fileName.endsWith(".css")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private Path resolveSafeArtifactPath(String path) throws IOException {
        Path requested = path == null || path.isBlank() ? ARTIFACT_ROOT : ARTIFACT_ROOT.resolve(path);
        Path normalized = requested.toAbsolutePath().normalize();
        if (!normalized.startsWith(ARTIFACT_ROOT)) {
            throw new IllegalArgumentException("Path escapes artifact root");
        }
        Files.createDirectories(ARTIFACT_ROOT);
        return normalized;
    }

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
            String content
    ) {}
}
