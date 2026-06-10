package com.livingagent.core.brain.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InstructionFileLoader {

    private static final Logger log = LoggerFactory.getLogger(InstructionFileLoader.class);

    private static final String LIVING_DIR = ".living";
    private static final String INSTRUCTIONS_FILE = "instructions.md";

    private final Path workspaceRoot;

    private final ConcurrentHashMap<String, String> instructionCache = new ConcurrentHashMap<>();

    @Autowired
    public InstructionFileLoader() {
        this.workspaceRoot = resolveWorkspaceRoot();
    }

    public InstructionFileLoader(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot : resolveWorkspaceRoot();
    }

    private Path resolveWorkspaceRoot() {
        String cwd = System.getProperty("user.dir");
        Path root = Paths.get(cwd);
        if (Files.exists(root.resolve(LIVING_DIR))) {
            log.info("Using workspace root: {}", root.toAbsolutePath());
            return root;
        }

        Path parent = root.getParent();
        while (parent != null) {
            if (Files.exists(parent.resolve(LIVING_DIR))) {
                log.info("Using workspace root: {}", parent.toAbsolutePath());
                return parent;
            }
            parent = parent.getParent();
        }

        log.warn("No .living directory found, using current working directory: {}", root.toAbsolutePath());
        return root;
    }

    public Optional<String> loadInstructions(String employeeId) {
        if (employeeId == null || employeeId.isEmpty()) {
            return Optional.empty();
        }

        String cacheKey = employeeId;
        if (instructionCache.containsKey(cacheKey)) {
            return Optional.of(instructionCache.get(cacheKey));
        }

        Path instructionPath = buildInstructionPath(employeeId);

        if (!Files.exists(instructionPath)) {
            log.debug("Instruction file not found for employee {}: {}", employeeId, instructionPath);
            return Optional.empty();
        }

        try {
            String content = Files.readString(instructionPath);
            instructionCache.put(cacheKey, content);
            log.info("Loaded instructions for employee {}: {} bytes", employeeId, content.length());
            return Optional.of(content);
        } catch (IOException e) {
            log.error("Failed to load instructions for employee {}: {}", employeeId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按相对路径（相对 workspaceRoot）加载文件
     * 用于加载职责卡、文档模板等非 employeeId 路径的资源
     */
    public Optional<String> loadFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return Optional.empty();
        }
        String cacheKey = "FILE:" + relativePath;
        if (instructionCache.containsKey(cacheKey)) {
            return Optional.of(instructionCache.get(cacheKey));
        }
        Path filePath = workspaceRoot.resolve(relativePath);
        if (!Files.exists(filePath)) {
            log.debug("File not found: {}", filePath);
            return Optional.empty();
        }
        try {
            String content = Files.readString(filePath);
            instructionCache.put(cacheKey, content);
            log.info("Loaded file {}: {} bytes", relativePath, content.length());
            return Optional.of(content);
        } catch (IOException e) {
            log.error("Failed to load file {}: {}", relativePath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 检查指定相对路径的文件是否存在
     */
    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        Path filePath = workspaceRoot.resolve(relativePath);
        // 尝试添加 .md 后缀
        if (!Files.exists(filePath) && !relativePath.endsWith(".md")) {
            filePath = workspaceRoot.resolve(relativePath + ".md");
        }
        return Files.exists(filePath);
    }

    public List<String> loadInstructionChain(String employeeId) {
        List<String> chain = new ArrayList<>();

        if (employeeId == null || employeeId.isEmpty()) {
            return chain;
        }

        String normalizedId = normalizeEmployeeId(employeeId);

        Path currentPath = workspaceRoot;
        Path livingPath = currentPath.resolve(LIVING_DIR);

        if (!Files.exists(livingPath)) {
            return chain;
        }

        Path employeePath = livingPath.resolve(normalizedId);
        Path instructionFile = employeePath.resolve(INSTRUCTIONS_FILE);

        if (Files.exists(instructionFile)) {
            try {
                String content = Files.readString(instructionFile);
                chain.add(content);
                log.debug("Loaded instruction file: {}", instructionFile);
            } catch (IOException e) {
                log.warn("Failed to load instruction file {}: {}", instructionFile, e.getMessage());
            }
        }

        List<Path> inheritanceChain = buildInheritanceChain(employeeId);
        for (Path inheritedPath : inheritanceChain) {
            if (Files.exists(inheritedPath)) {
                try {
                    String content = Files.readString(inheritedPath);
                    chain.add(0, content);
                    log.debug("Loaded inherited instruction: {}", inheritedPath);
                } catch (IOException e) {
                    log.warn("Failed to load inherited instruction {}: {}", inheritedPath, e.getMessage());
                }
            }
        }

        return chain;
    }

    private List<Path> buildInheritanceChain(String employeeId) {
        List<Path> chain = new ArrayList<>();

        if (employeeId == null || employeeId.isEmpty()) {
            return chain;
        }

        String normalizedId = normalizeEmployeeId(employeeId);

        if (normalizedId.contains("/")) {
            String[] parts = normalizedId.split("/");

            Path basePath = workspaceRoot.resolve(LIVING_DIR);
            for (int i = 0; i < parts.length - 1; i++) {
                basePath = basePath.resolve(parts[i]);
                Path inherited = basePath.resolve(INSTRUCTIONS_FILE);
                if (Files.exists(inherited) && !chain.contains(inherited)) {
                    chain.add(inherited);
                }
            }
        }

        Path globalInstructions = workspaceRoot.resolve(LIVING_DIR).resolve("global").resolve(INSTRUCTIONS_FILE);
        if (Files.exists(globalInstructions) && !chain.contains(globalInstructions)) {
            chain.add(globalInstructions);
        }

        return chain;
    }

    public String mergeInstructions(List<String> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return "";
        }

        StringBuilder merged = new StringBuilder();
        merged.append("# 指令链\n\n");

        for (int i = 0; i < instructions.size(); i++) {
            if (i > 0) {
                merged.append("\n---\n\n");
            }
            merged.append(instructions.get(i));
        }

        return merged.toString();
    }

    public void invalidateCache(String employeeId) {
        if (employeeId != null) {
            instructionCache.remove(employeeId);
            log.debug("Invalidated instruction cache for employee: {}", employeeId);
        }
    }

    public void clearCache() {
        instructionCache.clear();
        log.info("Cleared all instruction cache");
    }

    public List<Path> discoverAllInstructionFiles() {
        List<Path> discovered = new ArrayList<>();
        Path livingPath = workspaceRoot.resolve(LIVING_DIR);

        if (!Files.exists(livingPath)) {
            return discovered;
        }

        try {
            Files.walk(livingPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(INSTRUCTIONS_FILE))
                    .forEach(discovered::add);
        } catch (IOException e) {
            log.error("Failed to discover instruction files: {}", e.getMessage());
        }

        return discovered;
    }

    private Path buildInstructionPath(String employeeId) {
        String normalizedId = normalizeEmployeeId(employeeId);
        return workspaceRoot.resolve(LIVING_DIR).resolve(normalizedId).resolve(INSTRUCTIONS_FILE);
    }

    private String normalizeEmployeeId(String employeeId) {
        if (employeeId == null) {
            return "";
        }

        String normalized = employeeId.trim();

        if (normalized.startsWith("employee://")) {
            normalized = normalized.substring("employee://".length());
        }

        normalized = normalized.replaceAll("[/\\\\]", "/");

        return normalized;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public record InstructionFileInfo(
            Path path,
            String employeeId,
            long size,
            long lastModified
    ) {}

    public Optional<InstructionFileInfo> getInstructionFileInfo(String employeeId) {
        Path path = buildInstructionPath(employeeId);

        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return Optional.of(new InstructionFileInfo(
                    path,
                    employeeId,
                    attrs.size(),
                    attrs.lastModifiedTime().toMillis()
            ));
        } catch (IOException e) {
            log.warn("Failed to get instruction file info for {}: {}", employeeId, e.getMessage());
            return Optional.empty();
        }
    }
}
