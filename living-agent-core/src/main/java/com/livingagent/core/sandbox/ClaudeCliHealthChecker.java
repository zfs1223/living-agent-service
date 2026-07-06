package com.livingagent.core.sandbox;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Claude CLI 可用性健康检查
 * 检查项：CLI 二进制是否安装、是否可执行、版本是否兼容
 */
public class ClaudeCliHealthChecker implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliHealthChecker.class);
    private static final long CHECK_TIMEOUT_MS = 5000;

    private final String command;
    private volatile String cachedVersion;

    public ClaudeCliHealthChecker(String command) {
        this.command = command;
    }

    @Override
    public HealthStatus check() {
        long start = System.currentTimeMillis();
        try {
            boolean installed = checkInstalled();
            if (!installed) {
                return HealthStatus.unhealthy("claude_cli",
                    "Claude CLI binary not found: " + command);
            }

            boolean accessible = checkAccessible();
            if (!accessible) {
                return HealthStatus.degraded("claude_cli",
                    "Claude CLI installed but not accessible");
            }

            long elapsed = System.currentTimeMillis() - start;
            HealthStatus status = HealthStatus.healthy("claude_cli");
            status.setResponseTimeMs(elapsed);
            return status;
        } catch (Exception e) {
            log.error("Claude CLI health check failed", e);
            return HealthStatus.unhealthy("claude_cli", e.getMessage());
        }
    }

    private boolean checkInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("CLI install check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkAccessible() {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                return false;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    cachedVersion = line.trim();
                }
            }
            return true;
        } catch (Exception e) {
            log.debug("CLI accessibility check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getCachedVersion() {
        return cachedVersion;
    }
}
