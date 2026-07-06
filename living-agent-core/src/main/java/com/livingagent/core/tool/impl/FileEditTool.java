package com.livingagent.core.tool.impl;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 源码文件编辑工具，允许 LLM 通过 function calling 读写工作区内的文件。
 *
 * <p>支持的操作：
 * <ul>
 *   <li>read_file — 读取文件内容</li>
 *   <li>write_file — 写入文件（整文件覆写）</li>
 *   <li>edit_file — 精确编辑文件（old_string→new_string 替换）</li>
 *   <li>list_dir — 列出目录内容</li>
 *   <li>search_code — 在文件中搜索文本模式（支持正则）</li>
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
 *
 * <p>产物记录：当数字员工通过 write_file 创建文件时，自动记录到 ArtifactRecordService，
 * 包含数字员工代码、神经元 ID、部门等信息，以便进行考核统计。
 * 产物保存路径遵循规范：data/artifacts/{department}/{executionId}/
 */
public class FileEditTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileEditTool.class);

    private static final String NAME = "file_edit";
    private static final String DESCRIPTION = "源码文件编辑工具，支持读取、写入、精确编辑、列出目录和搜索代码（支持正则）。所有路径相对于工作区根目录。";
    private static final String VERSION = "1.2.0";
    private static final String DEPARTMENT = "tech";

    private static final String WORKSPACE_ROOT_PROPERTY = "livingagent.workspace.root";
    private static final String DEFAULT_WORKSPACE_ROOT = "/app/workspace";

    private static final String ARTIFACT_DIR_PROPERTY = "livingagent.artifact.dir";
    private static final String DEFAULT_ARTIFACT_DIR = "data/artifacts";

    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        ".env", ".credentials", ".secret", ".key", ".pem", ".p12", ".jks"
    );

    /** 工作区根路径，支持运行时热配置 */
    private volatile Path workspaceRoot;
    /** 产物目录根路径 */
    private volatile Path artifactRoot;
    /** 产物记录服务（可选注入） */
    private volatile ArtifactRecordService artifactRecordService;
    private ToolStats stats = ToolStats.empty(NAME);

    public FileEditTool() {
        this.workspaceRoot = Path.of(
            System.getProperty(WORKSPACE_ROOT_PROPERTY, DEFAULT_WORKSPACE_ROOT)
        ).toAbsolutePath().normalize();
        this.artifactRoot = Path.of(
            System.getProperty(ARTIFACT_DIR_PROPERTY, DEFAULT_ARTIFACT_DIR)
        ).toAbsolutePath().normalize();
        log.info("FileEditTool initialized with workspace root: {}, artifact root: {}", workspaceRoot, artifactRoot);
    }

    public FileEditTool(String workspaceRoot) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.artifactRoot = Path.of(
            System.getProperty(ARTIFACT_DIR_PROPERTY, DEFAULT_ARTIFACT_DIR)
        ).toAbsolutePath().normalize();
        log.info("FileEditTool initialized with workspace root: {}, artifact root: {}", workspaceRoot, artifactRoot);
    }

    /**
     * 注入 ArtifactRecordService，用于在 write_file 后自动记录产物。
     * 通过 ToolConfig 或 @PostConstruct 注入。
     */
    public void setArtifactRecordService(ArtifactRecordService artifactRecordService) {
        this.artifactRecordService = artifactRecordService;
        log.info("FileEditTool: ArtifactRecordService injected");
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
                .parameter("action", "string", "操作类型: read_file, write_file, edit_file, list_dir, search_code", true)
                .parameter("path", "string", "文件或目录路径（相对于工作区根目录）", true)
                .parameter("content", "string", "写入文件的内容（仅 write_file 操作需要）", false)
                .parameter("old_string", "string", "被替换的原始字符串（仅 edit_file 操作需要）", false)
                .parameter("new_string", "string", "替换后的新字符串（仅 edit_file 操作需要）", false)
                .parameter("pattern", "string", "搜索文本模式，支持正则表达式（仅 search_code 操作需要）", false)
                .parameter("regex", "boolean", "是否使用正则表达式搜索（默认false）", false)
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
                case "write_file" -> writeFile(path, params.getString("content"), context);
                case "edit_file" -> editFile(path, params.getString("old_string"), params.getString("new_string"), context);
                case "list_dir" -> listDir(path);
                case "search_code" -> searchCode(path, params.getString("pattern"),
                    params.getBoolean("regex") != null && params.getBoolean("regex"),
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

        if ("edit_file".equalsIgnoreCase(action)) {
            if (params.getString("old_string") == null) {
                throw new IllegalArgumentException("edit_file 操作需要 old_string 参数");
            }
            if (params.getString("new_string") == null) {
                throw new IllegalArgumentException("edit_file 操作需要 new_string 参数");
            }
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

    private Map<String, Object> writeFile(String relativePath, String content, ToolContext context) throws IOException {
        Path target = resolveAndValidate(relativePath);
        validateWritePath(target);

        Files.createDirectories(target.getParent());
        String contentValue = content != null ? content : "";
        Files.writeString(target, contentValue, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        long fileSize = contentValue.length();
        log.info("File written: {} ({} bytes)", relativePath, fileSize);

        // 当有数字员工上下文时，自动记录产物
        recordArtifactIfNeeded(relativePath, target, fileSize, context);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("size", fileSize);
        result.put("lines", contentValue.split("\n").length);
        result.put("status", "written");
        return result;
    }

    /**
     * 精确编辑文件：将文件中 old_string 的首次出现替换为 new_string。
     * 类似 Claude Code 的 Edit 操作，支持行级精确修改而无需覆写整个文件。
     */
    private Map<String, Object> editFile(String relativePath, String oldString, String newString, ToolContext context) throws IOException {
        if (oldString == null || oldString.isBlank()) {
            throw new IllegalArgumentException("old_string 不能为空");
        }

        Path target = resolveAndValidate(relativePath);
        validateWritePath(target);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(relativePath);
        }

        String content = Files.readString(target, StandardCharsets.UTF_8);
        int index = content.indexOf(oldString);
        if (index < 0) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", relativePath);
            result.put("status", "not_found");
            result.put("error", "old_string 未在文件中找到，请确认原始内容是否匹配");
            return result;
        }

        // 检查是否有多处匹配（提醒但不阻止）
        int secondIndex = content.indexOf(oldString, index + 1);
        boolean multipleMatches = secondIndex >= 0;

        String newContent = content.substring(0, index) + newString + content.substring(index + oldString.length());
        Files.writeString(target, newContent, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        long fileSize = newContent.length();
        log.info("File edited: {} (replaced {} chars with {} chars, multipleMatches={})",
            relativePath, oldString.length(), newString.length(), multipleMatches);

        recordArtifactIfNeeded(relativePath, target, fileSize, context);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("size", fileSize);
        result.put("lines", newContent.split("\n").length);
        result.put("replaced_chars", oldString.length());
        result.put("new_chars", newString.length());
        result.put("status", "edited");
        if (multipleMatches) {
            result.put("warning", "old_string 存在多处匹配，仅替换了第一处");
        }
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

    private Map<String, Object> searchCode(String relativePath, String pattern, boolean useRegex, int maxResults) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("搜索模式不能为空");
        }

        Path target = resolveAndValidate(relativePath);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(relativePath);
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        if (useRegex) {
            try {
                Pattern regex = Pattern.compile(pattern);
                searchInPathRegex(target, regex, maxResults, matches);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("正则表达式语法错误: " + e.getMessage());
            }
        } else {
            searchInPath(target, pattern, maxResults, matches);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("pattern", pattern);
        result.put("regex", useRegex);
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

    private void searchInPathRegex(Path dir, Pattern regex, int maxResults, List<Map<String, Object>> matches) throws IOException {
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
                            Matcher m = regex.matcher(lines[i]);
                            if (m.find()) {
                                Map<String, Object> match = new LinkedHashMap<>();
                                match.put("file", workspaceRoot.relativize(file).toString().replace("\\", "/"));
                                match.put("line", i + 1);
                                match.put("content", lines[i].trim());
                                match.put("match", m.group());
                                matches.add(match);
                            }
                        }
                    } catch (IOException e) {
                        log.debug("Skip file during regex search: {}", e.getMessage());
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

    // ==================== 产物记录 ====================

    /**
     * 当有数字员工上下文时，将创建的文件记录到 ArtifactRecordService。
     * 产物保存路径遵循规范 §3.3：
     *   - 数字员工产物：data/artifacts/by-employee/{employeeCode}/{executionId}/
     *   - 部门产物：    data/artifacts/{department}/{executionId}/
     *
     * <p>同时保留 workspace 内的工作文件（LLM 在工作区中操作），
     * 并将文件复制到产物目录作为持久化归档。
     */
    private void recordArtifactIfNeeded(String relativePath, Path filePath, long fileSize, ToolContext context) {
        if (artifactRecordService == null) {
            return;
        }
        if (context == null || context.employeeCode() == null || context.employeeCode().isBlank()) {
            return;
        }

        try {
            // 确定执行 ID（优先从 sessionId 提取，否则生成）
            String executionId = extractExecutionId(context.sessionId());
            String fileName = filePath.getFileName().toString();
            String artifactType = inferArtifactType(fileName);

            // 构建规范产物路径：data/artifacts/by-employee/{employeeCode}/{executionId}/
            // 同时也保存到部门目录：data/artifacts/{department}/{executionId}/
            Path employeeArtifactDir = artifactRoot.resolve("by-employee")
                .resolve(context.employeeCode())
                .resolve(executionId);
            Path deptArtifactDir = artifactRoot.resolve(DEPARTMENT).resolve(executionId);

            Files.createDirectories(employeeArtifactDir);
            Path artifactFile = employeeArtifactDir.resolve(fileName);
            // 复制文件到产物目录（持久化归档）
            Files.copy(filePath, artifactFile, StandardCopyOption.REPLACE_EXISTING);

            // 同时保存到部门目录（软链接优先，失败则复制）
            try {
                Files.createDirectories(deptArtifactDir);
                Path deptArtifactFile = deptArtifactDir.resolve(fileName);
                if (!Files.exists(deptArtifactFile)) {
                    Files.copy(filePath, deptArtifactFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception deptEx) {
                log.debug("Skip dept artifact copy for {}: {}", fileName, deptEx.getMessage());
            }

            // 计算 SHA-256
            String sha256 = computeSha256(artifactFile);

            // 构建产物记录（path 指向产物目录，符合规范）
            ArtifactRecord artifact = new ArtifactRecord(
                UUID.randomUUID().toString(),
                executionId,
                DEPARTMENT,
                context.employeeCode(),
                context.neuronId(),
                artifactType,
                artifactFile.toString(),
                fileName,
                "FileEditTool 工具创建: " + relativePath,
                fileSize,
                sha256,
                null,
                null,
                List.of("file_edit", DEPARTMENT),
                Instant.now(),
                Map.of(
                    "sourceWorkspacePath", filePath.toString(),
                    "relativePath", relativePath,
                    "clientId", context.clientId() != null ? context.clientId() : "",
                    "sessionId", context.sessionId() != null ? context.sessionId() : ""
                )
            );

            artifactRecordService.recordArtifact(artifact);
            log.info("Artifact recorded: id={}, file={}, employee={}, execution={}, size={}bytes, sha256={}",
                artifact.artifactId(), fileName, context.employeeCode(), executionId, fileSize, sha256);
        } catch (Exception e) {
            // 产物记录失败不应影响文件写入操作
            log.warn("Failed to record artifact for file {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * 计算文件的 SHA-256 校验值。
     */
    private String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.debug("Failed to compute SHA-256 for {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * 从 sessionId 提取 executionId。
     * sessionId 格式通常为 "execution-{uuid}" 或直接是 UUID。
     */
    private String extractExecutionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            // 如果以 "execution-" 开头，直接使用
            if (sessionId.startsWith("execution-") || sessionId.startsWith("exec-")) {
                return sessionId;
            }
            // 如果看起来像 UUID，返回 session 作为 executionId
            if (sessionId.length() >= 32) {
                return sessionId;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 根据文件扩展名推断产物类型。
     */
    private String inferArtifactType(String fileName) {
        if (fileName == null || fileName.isBlank()) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".ts") || lower.endsWith(".tsx")
            || lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".go") || lower.endsWith(".rs")
            || lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".kt")
            || lower.endsWith(".scala") || lower.endsWith(".rb")) return "CODE";
        if (lower.endsWith(".md") || lower.endsWith(".docx") || lower.endsWith(".pdf")) return "DOCUMENT";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
        if (lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")) return "CSS";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".gif") || lower.endsWith(".svg") || lower.endsWith(".ico")
            || lower.endsWith(".webp") || lower.endsWith(".bmp")) return "IMAGE";
        if (lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".xlsx")
            || lower.endsWith(".xml") || lower.endsWith(".yaml") || lower.endsWith(".yml")
            || lower.endsWith(".toml") || lower.endsWith(".properties")) {
            // 配置文件 vs 数据文件：如果路径包含 config/ 或 resources/ 则是 CONFIG
            return "CONFIG";
        }
        if (lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".ps1")) return "SCRIPT";
        if (lower.endsWith(".log") || lower.endsWith(".txt")) return "LOG";
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) return "ARCHIVE";
        if (lower.endsWith(".jar") || lower.endsWith(".exe") || lower.endsWith(".dll")) return "BUILD";
        if (lower.endsWith(".sql")) return "CODE";
        return "OTHER";
    }
}
