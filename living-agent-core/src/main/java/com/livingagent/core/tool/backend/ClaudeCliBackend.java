package com.livingagent.core.tool.backend;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

/**
 * Claude CLI 后端（64-B-1）
 * 发现方式：PATH 中的 claude 命令
 * 健康检查：claude --version
 */
public class ClaudeCliBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "claude_cli";
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        try {
            ProcessBuilder pb = new ProcessBuilder("claude", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(CHECK_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return DiscoveryResult.unavailable("claude --version 超时");
            }
            String output = new String(p.getInputStream().readAllBytes()).trim();
            if (p.exitValue() == 0 && !output.isBlank()) {
                return DiscoveryResult.available(output.split("\n")[0], "cli");
            }
            return DiscoveryResult.unavailable("claude 命令退出码: " + p.exitValue());
        } catch (Exception e) {
            return DiscoveryResult.unavailable("claude 未安装或不在 PATH 中: " + e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("claude", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(CHECK_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            long latency = System.currentTimeMillis() - start;
            if (!finished) {
                p.destroyForcibly();
                return HealthStatus.unhealthy("claude --version 超时");
            }
            if (p.exitValue() == 0) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("claude 退出码: " + p.exitValue());
        } catch (Exception e) {
            return HealthStatus.unreachable();
        }
    }

    @Override
    public String installHint() {
        return "Claude CLI 未安装。请参考 https://docs.anthropic.com/en/docs/claude-code 安装 Claude CLI，确保 `claude` 命令在 PATH 中可用。";
    }
}
