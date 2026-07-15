package com.livingagent.core.tool.backend;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件系统后端（64-B-1）
 * 发现方式：Java NIO 可写测试
 * 健康检查：创建+删除临时文件
 */
public class FileSystemBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "file_edit";
    private final Path basePath;

    public FileSystemBackend(Path basePath) {
        this.basePath = basePath != null ? basePath : Path.of(System.getProperty("user.dir"));
    }

    public FileSystemBackend() {
        this(null);
    }

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        if (Files.isDirectory(basePath) && Files.isWritable(basePath)) {
            return DiscoveryResult.available("java-nio", basePath.toString());
        }
        return DiscoveryResult.unavailable("路径不可写: " + basePath);
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            Path testFile = basePath.resolve(".health_check_" + System.currentTimeMillis());
            Files.writeString(testFile, "ok");
            String content = Files.readString(testFile);
            Files.deleteIfExists(testFile);
            long latency = System.currentTimeMillis() - start;
            if ("ok".equals(content)) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("读写不一致");
        } catch (Exception e) {
            return HealthStatus.unhealthy("文件系统健康检查失败: " + e.getMessage());
        }
    }

    @Override
    public String installHint() {
        return "文件系统不可写: " + basePath + "。请检查目录权限和磁盘空间。";
    }
}
