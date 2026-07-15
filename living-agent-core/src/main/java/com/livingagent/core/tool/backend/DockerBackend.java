package com.livingagent.core.tool.backend;

import java.time.Duration;

/**
 * Docker 后端（64-B-1）
 * 发现方式：Docker socket / docker 命令
 * 健康检查：docker info
 */
public class DockerBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "docker";
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(CHECK_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return DiscoveryResult.unavailable("docker info 超时");
            }
            String output = new String(p.getInputStream().readAllBytes()).trim();
            if (p.exitValue() == 0 && !output.isBlank()) {
                return DiscoveryResult.available(output, "docker-socket");
            }
            return DiscoveryResult.unavailable("docker 命令退出码: " + p.exitValue());
        } catch (Exception e) {
            return DiscoveryResult.unavailable("Docker 未安装或不在 PATH 中: " + e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(CHECK_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            long latency = System.currentTimeMillis() - start;
            if (!finished) {
                p.destroyForcibly();
                return HealthStatus.unhealthy("docker info 超时");
            }
            if (p.exitValue() == 0) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("docker 退出码: " + p.exitValue());
        } catch (Exception e) {
            return HealthStatus.unreachable();
        }
    }

    @Override
    public String installHint() {
        return "Docker 不可用。请确保 Docker 已安装且 Docker daemon 正在运行，当前用户有 docker 命令权限。";
    }
}
