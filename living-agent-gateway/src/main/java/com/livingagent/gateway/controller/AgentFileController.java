package com.livingagent.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{agentId}/files")
public class AgentFileController {

    private static final Logger log = LoggerFactory.getLogger(AgentFileController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileInfo>>> listFiles(
            @PathVariable String agentId,
            @RequestParam(required = false) String path
    ) {
        log.debug("Listing files for agent: {}, path: {}", agentId, path);

        List<FileInfo> files = new ArrayList<>();
        files.add(new FileInfo("file1.txt", "file", 1024, Instant.now()));
        files.add(new FileInfo("file2.txt", "file", 2048, Instant.now()));

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/content")
    public ResponseEntity<ApiResponse<FileContent>> readFile(
            @PathVariable String agentId,
            @RequestParam String path
    ) {
        log.debug("Reading file: {} for agent: {}", path, agentId);

        FileContent content = new FileContent(path, "file content here...");
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @PutMapping("/content")
    public ResponseEntity<ApiResponse<FileContent>> writeFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestBody WriteFileRequest request
    ) {
        log.info("Writing file: {} for agent: {}", path, agentId);

        FileContent content = new FileContent(path, request.content());
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @DeleteMapping("/content")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteFile(
            @PathVariable String agentId,
            @RequestParam String path
    ) {
        log.info("Deleting file: {} for agent: {}", path, agentId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "path", path)));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileInfo>> uploadFile(
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Uploading file: {} for agent: {}", path, agentId);

        FileInfo info = new FileInfo(
                file.getOriginalFilename(),
                "file",
                file.getSize(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String agentId,
            @RequestParam String path
    ) {
        log.debug("Downloading file: {} for agent: {}", path, agentId);

        return ResponseEntity.ok("file content".getBytes());
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
