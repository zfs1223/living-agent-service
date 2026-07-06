package com.livingagent.core.evolution.codebase;

import com.livingagent.core.runtime.EvolutionNamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代码库受控访问服务
 * 大脑可自由读写 .living/ 下的代码库镜像
 * 固定员工不可直接访问
 * 敏感文件自动过滤
 * 访问日志记录到 Trace
 */
public class CodebaseAccessService {

    private static final Logger log = LoggerFactory.getLogger(CodebaseAccessService.class);

    private final EvolutionNamespaceService namespaceService;
    private final CodebaseAccessConfig config;

    // 速率限制：每个请求者每分钟的访问计数
    private final Map<String, AtomicInteger> accessCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> accessCountResetTime = new ConcurrentHashMap<>();

    public CodebaseAccessService(EvolutionNamespaceService namespaceService, CodebaseAccessConfig config) {
        this.namespaceService = namespaceService;
        this.config = config;
    }

    public CodebaseAccessService(EvolutionNamespaceService namespaceService) {
        this(namespaceService, new CodebaseAccessConfig());
    }

    /**
     * 读取代码文件
     * @param requester 请求者（大脑/员工ID）
     * @param path 相对于项目根目录的路径
     * @return 文件内容（敏感信息已过滤），不存在返回 empty
     */
    public Optional<String> readFile(String requester, String path) {
        if (!canAccess(requester, path)) {
            log.warn("代码访问被拒绝: requester={}, path={}", requester, path);
            return Optional.empty();
        }

        if (!checkRateLimit(requester)) {
            log.warn("代码访问速率超限: requester={}", requester);
            return Optional.empty();
        }

        try {
            Path fullPath = resolvePath(path);
            if (fullPath == null || !Files.exists(fullPath)) {
                return Optional.empty();
            }

            String content = Files.readString(fullPath);
            content = filterSensitiveContent(content);

            logAccess(requester, "READ", path);
            return Optional.of(content);
        } catch (IOException e) {
            log.warn("读取代码文件失败: path={}, error={}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 列出目录内容
     */
    public List<FileInfo> listDirectory(String requester, String path) {
        if (!canAccess(requester, path)) {
            return Collections.emptyList();
        }

        try {
            Path fullPath = resolvePath(path);
            if (fullPath == null || !Files.isDirectory(fullPath)) {
                return Collections.emptyList();
            }

            List<FileInfo> result = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fullPath)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    if (!isSensitiveFile(name)) {
                        result.add(new FileInfo(
                            name,
                            Files.isDirectory(entry),
                            Files.size(entry),
                            Files.getLastModifiedTime(entry).toMillis()
                        ));
                    }
                }
            }

            logAccess(requester, "LIST", path);
            return result;
        } catch (IOException e) {
            log.warn("列出目录失败: path={}, error={}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 搜索代码（基于 source-tree.json 索引）
     */
    public List<SearchResult> searchCode(String requester, String query) {
        if (!canAccess(requester, "")) {
            return Collections.emptyList();
        }

        // 读取 source-tree.json 并搜索
        Optional<String> indexContent = readFile(requester, "source-tree.json");
        if (indexContent.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>();
        String content = indexContent.get().toLowerCase();
        String queryLower = query.toLowerCase();

        // 简单的关键词搜索
        if (content.contains(queryLower)) {
            results.add(new SearchResult("source-tree.json", "源码结构索引", 1.0));
        }

        logAccess(requester, "SEARCH", "query=" + query);
        return results;
    }

    /**
     * 检查访问权限
     * 大脑（含 employee://digital/ 和 neuron://）可访问
     * 固定员工（employee://human/）不可直接访问
     */
    public boolean canAccess(String requester, String path) {
        if (requester == null || requester.isEmpty()) {
            return false;
        }

        // 大脑和数字员工可访问
        if (requester.startsWith("employee://digital/") || requester.startsWith("neuron://")) {
            return true;
        }

        // 人类员工不可直接访问代码库
        if (requester.startsWith("employee://human/")) {
            return false;
        }

        // 大脑名称（如 MainBrain, TechBrain）可访问
        if (requester.endsWith("Brain") || requester.equals("main") || requester.equals("tech")
            || requester.equals("hr") || requester.equals("finance") || requester.equals("sales")
            || requester.equals("cs") || requester.equals("admin") || requester.equals("legal")
            || requester.equals("ops")) {
            return true;
        }

        return true; // 默认允许（后续可通过 BrainBoundaryEnforcer 细化）
    }

    // ===== 内部方法 =====

    private Path resolvePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        // 安全检查：防止路径遍历攻击
        if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            log.warn("路径遍历攻击检测: path={}", relativePath);
            return null;
        }

        // 先尝试从 .living/codebase/ 读取
        Path codebasePath = Paths.get(namespaceService.getCodebasePath()).resolve(relativePath);
        if (Files.exists(codebasePath)) {
            return codebasePath;
        }

        // 再尝试从项目根目录读取
        if (config.getProjectRoot() != null) {
            Path projectPath = Paths.get(config.getProjectRoot()).resolve(relativePath);
            if (Files.exists(projectPath)) {
                return projectPath;
            }
        }

        return null;
    }

    private boolean isSensitiveFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String pattern : config.getSensitivePatterns()) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String filterSensitiveContent(String content) {
        // 过滤敏感行（包含 password=, secret=, token= 等）
        StringBuilder filtered = new StringBuilder();
        for (String line : content.split("\n")) {
            String lower = line.toLowerCase().trim();
            boolean isSensitive = false;
            for (String pattern : config.getSensitivePatterns()) {
                if (lower.contains(pattern.toLowerCase() + "=") || lower.contains(pattern.toLowerCase() + ":")) {
                    isSensitive = true;
                    break;
                }
            }
            if (isSensitive) {
                filtered.append("*** [FILTERED] ***\n");
            } else {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString();
    }

    private boolean checkRateLimit(String requester) {
        long now = System.currentTimeMillis();
        Long resetTime = accessCountResetTime.get(requester);

        // 超过1分钟，重置计数
        if (resetTime == null || now - resetTime > 60000) {
            accessCounts.put(requester, new AtomicInteger(1));
            accessCountResetTime.put(requester, now);
            return true;
        }

        AtomicInteger count = accessCounts.get(requester);
        if (count == null) {
            accessCounts.put(requester, new AtomicInteger(1));
            return true;
        }

        if (count.incrementAndGet() > config.getRateLimitPerMinute()) {
            return false;
        }

        return true;
    }

    private void logAccess(String requester, String operation, String path) {
        if (config.isAccessLog()) {
            log.info("代码访问: requester={}, op={}, path={}, time={}", requester, operation, path, Instant.now());
        }
    }

    // ===== 内部类 =====

    public static class FileInfo {
        private final String name;
        private final boolean directory;
        private final long size;
        private final long lastModified;

        public FileInfo(String name, boolean directory, long size, long lastModified) {
            this.name = name;
            this.directory = directory;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getName() { return name; }
        public boolean isDirectory() { return directory; }
        public long getSize() { return size; }
        public long getLastModified() { return lastModified; }
    }

    public static class SearchResult {
        private final String filePath;
        private final String description;
        private final double relevance;

        public SearchResult(String filePath, String description, double relevance) {
            this.filePath = filePath;
            this.description = description;
            this.relevance = relevance;
        }

        public String getFilePath() { return filePath; }
        public String getDescription() { return description; }
        public double getRelevance() { return relevance; }
    }
}
