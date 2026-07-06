package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import com.livingagent.core.model.ModelClient;
import com.livingagent.core.sandbox.ClaudeCliHealthChecker;
import com.livingagent.core.sandbox.ClaudeCliProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * P12-A: 启动依赖检查器。
 * 在 ApplicationReadyEvent 时检查关键依赖（model_daemon.py、模型文件、NamedPipe），
 * 将检查结果注册到 HealthMonitor，检查失败时设置降级模式但不阻止启动。
 *
 * P20-A: 使用 ModelDaemonProcessMonitor（管道 status 探测）替代 ModelDaemonHealthCheck（仅 FIFO 文件存在性）。
 */
public class StartupDependencyChecker implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyChecker.class);

    private final HealthMonitor healthMonitor;
    private final ModelClient modelClient;
    private final ClaudeCliProperties claudeCliProperties;

    public StartupDependencyChecker(HealthMonitor healthMonitor) {
        this(healthMonitor, null, null);
    }

    public StartupDependencyChecker(HealthMonitor healthMonitor, ModelClient modelClient) {
        this(healthMonitor, modelClient, null);
    }

    public StartupDependencyChecker(HealthMonitor healthMonitor, ModelClient modelClient, ClaudeCliProperties claudeCliProperties) {
        this.healthMonitor = healthMonitor;
        this.modelClient = modelClient;
        this.claudeCliProperties = claudeCliProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("P12-A/P20-A: Running startup dependency checks...");

        boolean allHealthy = true;

        // 1. P20-A: model_daemon 进程存活检查（管道 status 探测）
        if (modelClient != null) {
            ModelDaemonProcessMonitor processMonitor = new ModelDaemonProcessMonitor(modelClient);
            healthMonitor.registerCheck("model_daemon_process", processMonitor);
            HealthStatus processStatus = processMonitor.check();
            log.info("model_daemon_process check: {}", processStatus);
            if (processStatus.getStatus() == HealthStatus.Status.UNHEALTHY) {
                allHealthy = false;
                log.warn("P20-A: model_daemon process unhealthy - {}", processStatus.getMessage());
            }
        } else {
            // Fallback: 仅检查 FIFO 文件存在
            log.info("ModelClient not available, using fallback FIFO existence check");
            ModelDaemonHealthCheck daemonCheck = new ModelDaemonHealthCheck();
            healthMonitor.registerCheck("model_daemon", daemonCheck);
            HealthStatus daemonStatus = daemonCheck.check();
            log.info("model_daemon check (fallback): {}", daemonStatus);
            if (daemonStatus.getStatus() == HealthStatus.Status.UNHEALTHY) {
                allHealthy = false;
                log.warn("P12-A: model_daemon unhealthy - {}", daemonStatus.getMessage());
            }
        }

        // 2. P20-B: 模型文件加载 + 推理可用性检查
        ModelLoadHealthCheck modelCheck = modelClient != null
            ? new ModelLoadHealthCheck(modelClient)
            : new ModelLoadHealthCheck();
        healthMonitor.registerCheck("model_load", modelCheck);
        HealthStatus modelStatus = modelCheck.check();
        log.info("model_load check: {}", modelStatus);
        if (modelStatus.getStatus() == HealthStatus.Status.UNHEALTHY) {
            allHealthy = false;
            log.warn("P12-A: model_load unhealthy - {}", modelStatus.getMessage());
        }

        // 3. 设置降级模式
        if (!allHealthy) {
            AppModeUtil.setDegraded("Startup dependency check failed");
        } else {
            log.info("P12-A/P20-A: All startup dependency checks passed");
        }

        // 4. P22-A: Claude CLI 可用性检查
        if (claudeCliProperties != null && claudeCliProperties.isEnabled()) {
            ClaudeCliHealthChecker cliChecker = new ClaudeCliHealthChecker(claudeCliProperties.getCommand());
            healthMonitor.registerCheck("claude_cli", cliChecker);
            HealthStatus cliStatus = cliChecker.check();
            log.info("claude_cli check: {}", cliStatus);
            if (cliStatus.getStatus() == HealthStatus.Status.UNHEALTHY) {
                log.warn("P22-A: Claude CLI unhealthy - {}", cliStatus.getMessage());
            }
        }
    }
}
