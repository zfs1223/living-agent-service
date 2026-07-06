package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import com.livingagent.core.model.ModelClient;
import com.livingagent.core.model.ModelRequest;
import com.livingagent.core.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * P20-A: model_daemon 进程存活监控。
 * 通过控制管道发送 status 命令探测进程是否真正存活，而非仅检查 FIFO 文件存在。
 */
public class ModelDaemonProcessMonitor implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ModelDaemonProcessMonitor.class);
    private static final long PROBE_TIMEOUT_MS = 5000;

    private final ModelClient modelClient;
    private volatile boolean lastAlive = true;
    private volatile int consecutiveFailures = 0;

    public ModelDaemonProcessMonitor(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    @Override
    public HealthStatus check() {
        if (modelClient == null) {
            return HealthStatus.unhealthy("model_daemon_process", "ModelClient not available");
        }

        try {
            ModelRequest statusRequest = ModelRequest.builder()
                .service("status")
                .build();

            ModelResponse response = modelClient.sendControlRequest(statusRequest)
                .get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (response != null && response.isSuccess()) {
                if (!lastAlive) {
                    log.info("model_daemon process recovered");
                }
                lastAlive = true;
                consecutiveFailures = 0;
                return HealthStatus.healthy("model_daemon_process");
            }

            consecutiveFailures++;
            lastAlive = false;
            String msg = "model_daemon status probe returned failure (consecutive failures: " + consecutiveFailures + ")";
            log.warn(msg);
            return HealthStatus.unhealthy("model_daemon_process", msg);

        } catch (java.util.concurrent.TimeoutException e) {
            consecutiveFailures++;
            lastAlive = false;
            String msg = "model_daemon status probe timed out after " + PROBE_TIMEOUT_MS + "ms (consecutive failures: " + consecutiveFailures + ")";
            log.error(msg);
            return HealthStatus.unhealthy("model_daemon_process", msg);

        } catch (Exception e) {
            consecutiveFailures++;
            lastAlive = false;
            String msg = "model_daemon status probe failed: " + e.getMessage() + " (consecutive failures: " + consecutiveFailures + ")";
            log.error(msg);
            return HealthStatus.unhealthy("model_daemon_process", msg);
        }
    }

    public boolean wasAlive() {
        return lastAlive;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void resetFailures() {
        consecutiveFailures = 0;
        lastAlive = true;
    }
}
