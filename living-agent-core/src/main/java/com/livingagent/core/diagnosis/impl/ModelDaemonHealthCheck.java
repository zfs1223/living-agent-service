package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * model_daemon.py 健康检查。
 * 检查 NamedPipe 文件是否存在（Unix: /tmp/dialogue_daemon_control_*，Windows: \\.\pipe\dialogue_daemon_control_*）。
 */
public class ModelDaemonHealthCheck implements HealthCheck {

    private final boolean isWindows;

    public ModelDaemonHealthCheck() {
        this.isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public HealthStatus check() {
        boolean requestPipeExists;
        boolean responsePipeExists;

        if (isWindows) {
            requestPipeExists = checkWindowsNamedPipe("dialogue_daemon_control_request");
            responsePipeExists = checkWindowsNamedPipe("dialogue_daemon_control_response");
        } else {
            requestPipeExists = Files.exists(Path.of("/tmp/dialogue_daemon_control_request"));
            responsePipeExists = Files.exists(Path.of("/tmp/dialogue_daemon_control_response"));
        }

        if (requestPipeExists && responsePipeExists) {
            return HealthStatus.healthy("model_daemon");
        } else if (requestPipeExists || responsePipeExists) {
            return HealthStatus.degraded("model_daemon",
                "Partial NamedPipe found: request=" + requestPipeExists + ", response=" + responsePipeExists);
        } else {
            return HealthStatus.unhealthy("model_daemon",
                "model_daemon NamedPipe not found (isWindows=" + isWindows + ")");
        }
    }

    private boolean checkWindowsNamedPipe(String pipeName) {
        try {
            // P20-A: Java NIO Files.exists 对 Windows Named Pipe 无效，
            // 改用 PowerShell 检测或 socket 连接尝试
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-Command",
                "[System.IO.Directory]::GetFiles('\\\\.\\pipe\\') -match '" + pipeName + "'"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(pipeName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            // Fallback: 尝试 NIO 检查（可能在某些 Windows 版本有效）
            try {
                Path pipePath = Path.of("\\\\.\\pipe\\" + pipeName);
                return Files.exists(pipePath);
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
