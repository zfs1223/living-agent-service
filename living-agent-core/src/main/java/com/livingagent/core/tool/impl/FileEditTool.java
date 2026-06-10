package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 源码文件编辑工具，允许 LLM 通过 function calling 读写工作区内的文件。
 *
 * <p>支持的操作：
 * <ul>
 *   <li>read_file — 读取文件内容</li>
 *   <li>write_file — 写入文件（需审批）</li>
 *   <li>list_dir — 列出目录内容</li>
 *   <li>search_code — 在文件中搜索文本模式</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>所有路径必须在 WORKSPACE_ROOT 内（默认 /app/workspace）</li>
 *   <li>write_file 需要审批（requiresApproval = true）</li>
 *   <li>禁止访问 .env、credentials 等敏感文件</li>
 * </ul>
 *
 * <p>工作区路径支持热配置：通过 {@link #setWorkspaceRoot(String)} 可在运行时修改，
 * 前端可通过系统设置 API 动态调整。
 */
public class FileEditTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileEditTool.class);

    private static final String NAME = "file_edit";
    private static final String DESCRIPTION = "源码文件编辑工具，支持读取、写入、列出目录和搜索代码。所有路径相对于工作区根目录。";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "tech";

    private static final String WORKSPACE_ROOT_PROPERTY = "livingagent.workspace.root";
    private static final String DEFAULT_WORKSPACE_ROOT = "/app/workspace";

    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        ".env", ".credentials", ".secret", ".key", ".pem", ".p12", ".jks"
    );

    /** 工作区根路径，支持运行时热配置 */
    private volatile Path workspaceRoot;
    private ToolStats stats = ToolStats.empty(NAME);

    public FileEditTool() {
        this.workspaceRoot = Path.of(
            System.getProperty(WORKSPACE_ROOT_PROPERTY, DEFAULT_WORKSPACE_ROOT)
        ).toAbsolutePath().normalize();
        log.info("FileEditTool initialized with workspace root: {}", workspaceRoot);
    }

    public FileEditTool(String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        log.info("FileEditTool initialized with workspace root: {}", workspaceRoot);
    }

    /**
     * 热配置工作区根路径，无需重启服务。
     * 前端可通过系统设置 API 调用此方法。
     */
    public void setWorkspaceRoot(String path) {
        Path newRoot = Path.of(path).toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(newRoot)) {
            log.warn("Workspace root does not exist: {}, but setting anyway", newRoot);
        }
        this.workspaceRoot = newRoot;
        log.info("FileEditTool workspace root updated to: {}", newRoot);
    }

    public String getWorkspaceRoot() {
        return workspaceRoot.toString();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: read_file, write_file, list_dir, search_code", true)
                .parameter("path", "string", "文件或目录路径（相对于工作区根目录）", true)
                .parameter("content", "string", "写入文件的内容（仅 write_file 操作需要）", false)
                .parameter("pattern", "string", "搜索文本模式（仅 search_code 操作需要）", false)
                .parameter("max_results", "integer", "搜索最大返回结果数（默认50）", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("file_read", "file_write", "directory_listing", "code_search", "source_edit");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        String path = params.getString("path");

        if (action == null || action.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action 参数不能为空");
        }
        if (path == null || path.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("path 参数不能为空");
        }

        try {
            Object result = switch (action.toLowerCase()) {
                case "read_file" -> readFile(path);
                case "write_file" -> writeFile(path, params.getString("content"));
                case "list_dir" -> listDir(path);
                case "search_code" -> searchCode(path, params.getString("pattern"),
                    params.getInteger("max_results") != null ? params.getInteger("max_results") : 50);
                default -> throw new IllegalArgumentException("不支持的操作: " + action);
            };

            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (SecurityException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("安全限制: " + e.getMessage());
        } catch (NoSuchFileException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("文件不存在: " + path);
        } catch (IOException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("IO错误: " + e.getMessage());
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action 参数不能为空");
        }

        String path = params.getString("path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 参数不能为空");
        }

        validatePath(path);

        if ("write_file".equalsIgnoreCase(action) && params.getString("content") == null) {
            throw new IllegalArgumentException("write_file 操作需要 content 参数");
        }

        if ("search_code".equalsIgnoreCase(action) && params.getString("pattern") == null) {
            throw new IllegalArgumentException("search_code 操作需要 pattern 参数");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.getAutonomyLevel() != null;
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() { return stats; }

    // ==================== 操作实现 ====================

    private Map<String, Object> readFile(String relativePath) throws IOException {
        Path target = resolveAndValidate(relativePath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(relativePath);
        }
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException("路径是目录，不是文件: " + relativePath);
        }

        String content = Files.readString(target, StandardCharsets.UTF_8);
        long size = Files.size(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("content", content);
        result.put("size", size);
        result.put("lines", content.split("\n").length);
        return result;
    }

    private Map<String, Object> writeFile(String relativePath, String content) throws IOException {
        Path target = resolveAndValidate(relativePath);
        validateWritePath(target);

        Files.createDirectories(target.getParent());
        String contentValue = content != null ? content : "";
        Files.writeString(target, contentValue, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("File written: {} ({} bytes)", relativePath, contentValue.length());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("size", contentValue.length());
        result.put("lines", contentValue.split("\n").length);
        result.put("status", "written");
        return result;
    }

    private Map<String, Object> listDir(String relativePath) throws IOException {
        Path target = resolveAndValidate(relativePath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(relativePath);
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("路径不是目录: " + relativePath);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target)) {
            stream.limit(500).forEach(path -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", path.getFileName().toString());
                    entry.put("type", attrs.isDirectory() ? "directory" : "file");
                    if (!attrs.isDirectory()) {
                        entry.put("size", attrs.size());
                    }
                    entries.add(entry);
                } catch (IOException e) {
                    log.debug("Skip file while listing: {}", e.getMessage());
                }
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("entries", entries);
        result.put("count", entries.size());
        return result;
    }

    private Map<String, Object> searchCode(String relativePath, String pattern, int maxResults) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("搜索模式不能为空");
        }

        Path target = resolveAndValidate(relativePath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(relativePath);
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        searchInPath(target, pattern, maxResults, matches);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("pattern", pattern);
        result.put("matches", matches);
        result.put("count", matches.size());
        result.put("truncated", matches.size() >= maxResults);
        return result;
    }

    private void searchInPath(Path dir, String pattern, int maxResults, List<Map<String, Object>> matches) throws IOException {
        if (matches.size() >= maxResults) return;

        try (Stream<Path> stream = Files.walk(dir, 10)) {
            stream.filter(Files::isRegularFile)
                .filter(this::isSearchableFile)
                .limit(1000)
                .forEach(file -> {
                    if (matches.size() >= maxResults) return;
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String[] lines = content.split("\n");
                        for (int i = 0; i < lines.length && matches.size() < maxResults; i++) {
                            if (lines[i].contains(pattern)) {
                                Map<String, Object> match = new LinkedHashMap<>();
                                match.put("file", workspaceRoot.relativize(file).toString().replace("\\", "/"));
                                match.put("line", i + 1);
                                match.put("content", lines[i].trim());
                                matches.add(match);
                            }
                        }
                    } catch (IOException e) {
                        log.debug("Skip file during search: {}", e.getMessage());
                    }
                });
        }
    }

    // ==================== 安全验证 ====================

    private Path resolveAndValidate(String relativePath) {
        Path resolved = workspaceRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("路径超出工作区范围: " + relativePath);
        }
        return resolved;
    }

    private void validatePath(String relativePath) {
        String lower = relativePath.toLowerCase();
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (lower.contains(forbidden)) {
                throw new SecurityException("禁止访问敏感文件: " + relativePath);
            }
        }
        if (relativePath.contains("..")) {
            throw new SecurityException("路径不允许包含 .. : " + relativePath);
        }
    }

    private void validateWritePath(Path target) {
        String name = target.getFileName().toString().toLowerCase();
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (name.contains(forbidden)) {
                throw new SecurityException("禁止写入敏感文件: " + target.getFileName());
            }
        }
    }

    private boolean isSearchableFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.startsWith(".")) return false;
        if (name.contains(".class")) return false;
        if (name.contains(".jar")) return false;
        if (name.contains(".git")) return false;
        if (name.contains("node_modules")) return false;
        if (name.contains("target")) return false;
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".ico")) return false;
        return true;
    }
}
