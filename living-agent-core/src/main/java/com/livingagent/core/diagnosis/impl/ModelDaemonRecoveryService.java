package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.DegradedTrafficCanary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * P20-A: model_daemon 进程自动恢复服务。
 * 当 ModelDaemonProcessMonitor 检测到进程不存活时，尝试重启守护进程。
 */
@Service
public class ModelDaemonRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ModelDaemonRecoveryService.class);
    private static final long RECOVERY_COOLDOWN_MS = 60_000;
    private static final int MAX_WAIT_FIFO_SECONDS = 30;
    private static final String FIFO_PATH = "/tmp/dialogue_daemon_control_request";

    private volatile long lastRecoveryAttempt = 0;
    private volatile int totalRecoveryAttempts = 0;
    private volatile int successfulRecoveries = 0;

    private final DegradedTrafficCanary canary;

    public ModelDaemonRecoveryService(DegradedTrafficCanary canary) {
        this.canary = canary;
    }

    public boolean attemptRecovery() {
        long now = System.currentTimeMillis();
        if (now - lastRecoveryAttempt < RECOVERY_COOLDOWN_MS) {
            log.debug("Recovery cooldown active, skipping (last attempt {}ms ago)",
                now - lastRecoveryAttempt);
            return false;
        }

        lastRecoveryAttempt = now;
        totalRecoveryAttempts++;
        log.info("P20-A: Attempting to recover model_daemon.py (attempt #{})...", totalRecoveryAttempts);

        try {
            // 1. 启动 model_daemon.py
            ProcessBuilder pb = new ProcessBuilder(
                "python3", "scripts/python/model_daemon.py");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            log.info("model_daemon.py process started, waiting for FIFO...");

            // 2. 等待 FIFO 文件出现（最多 30s）
            boolean fifoReady = false;
            for (int i = 0; i < MAX_WAIT_FIFO_SECONDS; i++) {
                if (Files.exists(Path.of(FIFO_PATH))) {
                    fifoReady = true;
                    break;
                }
                TimeUnit.SECONDS.sleep(1);
            }

            if (!fifoReady) {
                log.error("model_daemon.py FIFO not available after {}s", MAX_WAIT_FIFO_SECONDS);
                AppModeUtil.setDegraded("model_daemon_recovery_fifo_timeout");
                return false;
            }

            // 3. 额外等待让守护进程初始化
            TimeUnit.SECONDS.sleep(3);

            // 4. 检查进程是否仍在运行
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                log.error("model_daemon.py exited immediately with code: {}", exitCode);
                AppModeUtil.setDegraded("model_daemon_recovery_exit_" + exitCode);
                return false;
            }

            // 5. 成功 — 通过Canary小流量回归，而非直接全量恢复
            canary.startProbing("model_daemon", "degraded_mode");
            successfulRecoveries++;
            log.info("P27-A: model_daemon.py recovered, starting canary probing instead of direct clearDegraded (#{}/{} successful)",
                successfulRecoveries, totalRecoveryAttempts);
            return true;

        } catch (Exception e) {
            log.error("P20-A: model_daemon.py recovery failed: {}", e.getMessage());
            AppModeUtil.setDegraded("model_daemon_recovery_failed:" + e.getMessage());
            return false;
        }
    }

    public int getTotalRecoveryAttempts() {
        return totalRecoveryAttempts;
    }

    public int getSuccessfulRecoveries() {
        return successfulRecoveries;
    }

    public long getMsSinceLastAttempt() {
        return System.currentTimeMillis() - lastRecoveryAttempt;
    }
}
