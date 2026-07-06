package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.*;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class HealthMonitorImpl implements HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitorImpl.class);

    private final NeuronRegistry neuronRegistry;
    private final BrainRegistry brainRegistry;
    private final ChannelManager channelManager;
    private final ApplicationEventPublisher eventPublisher;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicReference<HealthStatus> currentStatus = new AtomicReference<>();
    private final List<HealthAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final Map<String, HealthCheckResult> lastCheckResults = new ConcurrentHashMap<>();
    private final Map<String, HealthCheck> registeredChecks = new ConcurrentHashMap<>();
    private final Map<String, Double> alertThresholds = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> scheduledCheck;

    private long checkIntervalMs = 30000;
    private double cpuThreshold = 80.0;
    private double memoryThreshold = 85.0;
    private int maxAlerts = 100;

    public HealthMonitorImpl(NeuronRegistry neuronRegistry,
                             BrainRegistry brainRegistry,
                             ChannelManager channelManager,
                             ApplicationEventPublisher eventPublisher) {
        this.neuronRegistry = neuronRegistry;
        this.brainRegistry = brainRegistry;
        this.channelManager = channelManager;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        scheduledCheck = scheduler.scheduleAtFixedRate(() -> {
            try {
                checkHealth();
            } catch (Exception e) {
                log.warn("Scheduled health check failed: {}", e.getMessage());
            }
        }, 30, 60, TimeUnit.SECONDS);
        log.info("P12-C: HealthMonitor periodic check started (60s interval, 30s initial delay)");
    }

    @Override
    public HealthStatus checkHealth() {
        log.debug("Performing health check");

        List<HealthIssue> issues = new ArrayList<>();
        int healthyCount = 0;
        int totalCount = 0;

        HealthCheckResult neuronCheck = checkNeurons();
        lastCheckResults.put("neurons", neuronCheck);
        issues.addAll(neuronCheck.issues);
        healthyCount += neuronCheck.healthyCount;
        totalCount += neuronCheck.totalCount;

        HealthCheckResult brainCheck = checkBrains();
        lastCheckResults.put("brains", brainCheck);
        issues.addAll(brainCheck.issues);
        healthyCount += brainCheck.healthyCount;
        totalCount += brainCheck.totalCount;

        HealthCheckResult channelCheck = checkChannels();
        lastCheckResults.put("channels", channelCheck);
        issues.addAll(channelCheck.issues);
        healthyCount += channelCheck.healthyCount;
        totalCount += channelCheck.totalCount;

        HealthCheckResult systemCheck = checkSystemResources();
        lastCheckResults.put("system", systemCheck);
        issues.addAll(systemCheck.issues);

        HealthStatus status = determineStatus(issues, healthyCount, totalCount);
        currentStatus.set(status);

        // P20-A: 纳入注册的自定义检查项到周期检查
        for (Map.Entry<String, HealthCheck> entry : registeredChecks.entrySet()) {
            try {
                HealthStatus compStatus = entry.getValue().check();
                lastCheckResults.put(entry.getKey(), toCheckResult(compStatus));
                if (compStatus.getStatus() != HealthStatus.Status.HEALTHY) {
                    HealthIssue issue = createIssue(
                        compStatus.getComponentName(),
                        compStatus.getMessage(),
                        compStatus.getStatus() == HealthStatus.Status.UNHEALTHY ? HealthIssue.Severity.HIGH : HealthIssue.Severity.MEDIUM
                    );
                    issues.add(issue);
                }
            } catch (Exception e) {
                HealthIssue issue = createIssue(entry.getKey(), "Custom check error: " + e.getMessage(), HealthIssue.Severity.HIGH);
                issues.add(issue);
            }
        }

        if (status.getStatus() != HealthStatus.Status.HEALTHY) {
            generateAlerts(issues);
            // P24-A: 发布 CRITICAL/HIGH 级别 HealthIssue 事件供 SelfHealingOrchestrator 订阅
            for (HealthIssue issue : issues) {
                fillSuggestedAction(issue);
                if (issue.getSeverity() == HealthIssue.Severity.CRITICAL ||
                    issue.getSeverity() == HealthIssue.Severity.HIGH) {
                    try {
                        eventPublisher.publishEvent(issue);
                        log.debug("Published HealthIssue event: {} ({})", issue.getComponentName(), issue.getSeverity());
                    } catch (Exception e) {
                        log.warn("Failed to publish HealthIssue event: {}", e.getMessage());
                    }
                }
            }
        }

        log.info("Health check completed: {} (issues: {})", status, issues.size());
        return status;
    }

    @Override
    public HealthStatus checkComponent(String componentName) {
        HealthCheckResult result = switch (componentName) {
            case "neurons" -> checkNeurons();
            case "brains" -> checkBrains();
            case "channels" -> checkChannels();
            case "system" -> checkSystemResources();
            default -> {
                HealthCheck check = registeredChecks.get(componentName);
                if (check != null) {
                    HealthStatus status = check.check();
                    HealthCheckResult r = new HealthCheckResult();
                    if (status.getStatus() != HealthStatus.Status.HEALTHY) {
                        HealthIssue issue = createIssue(
                            status.getComponentName(),
                            status.getMessage(),
                            HealthIssue.Severity.valueOf(status.getStatus().name())
                        );
                        r.issues.add(issue);
                        r.healthyCount = 0;
                        r.totalCount = 1;
                    }
                    yield r;
                }
                yield new HealthCheckResult();
            }
        };
        
        lastCheckResults.put(componentName, result);
        
        if (result.issues.isEmpty()) {
            return HealthStatus.healthy(componentName);
        } else if (result.healthyCount > 0) {
            return HealthStatus.degraded(componentName, result.issues.get(0).getMessage());
        } else {
            return HealthStatus.unhealthy(componentName, result.issues.get(0).getMessage());
        }
    }

    @Override
    public List<HealthIssue> detectIssues() {
        List<HealthIssue> allIssues = new ArrayList<>();
        for (HealthCheckResult result : lastCheckResults.values()) {
            allIssues.addAll(result.issues);
        }
        return allIssues;
    }

    @Override
    public void registerCheck(String name, HealthCheck check) {
        registeredChecks.put(name, check);
        log.info("Registered health check: {}", name);
    }

    @Override
    public void unregisterCheck(String name) {
        registeredChecks.remove(name);
        log.info("Unregistered health check: {}", name);
    }

    @Override
    public Map<String, HealthStatus> getAllComponentStatus() {
        Map<String, HealthStatus> statusMap = new HashMap<>();
        for (Map.Entry<String, HealthCheckResult> entry : lastCheckResults.entrySet()) {
            String component = entry.getKey();
            HealthCheckResult result = entry.getValue();
            
            if (result.issues.isEmpty()) {
                statusMap.put(component, HealthStatus.healthy(component));
            } else if (result.healthyCount > 0) {
                statusMap.put(component, HealthStatus.degraded(component, result.issues.get(0).getMessage()));
            } else {
                statusMap.put(component, HealthStatus.unhealthy(component, result.issues.get(0).getMessage()));
            }
        }
        return statusMap;
    }

    @Override
    public void setAlertThreshold(String metric, double threshold) {
        alertThresholds.put(metric, threshold);
        log.info("Set alert threshold for {}: {}", metric, threshold);
    }

    @Override
    public List<HealthAlert> getActiveAlerts() {
        return List.copyOf(activeAlerts);
    }

    @Override
    public void acknowledgeAlert(String alertId) {
        activeAlerts.removeIf(alert -> alert.getAlertId().equals(alertId));
        log.info("Alert acknowledged: {}", alertId);
    }

    private HealthCheckResult checkNeurons() {
        HealthCheckResult result = new HealthCheckResult();
        
        if (neuronRegistry == null) {
            result.issues.add(createIssue("neurons", "NeuronRegistry not available", HealthIssue.Severity.CRITICAL));
            return result;
        }

        List<Neuron> neurons = neuronRegistry.getAll();
        result.totalCount = neurons.size();

        for (Neuron neuron : neurons) {
            try {
                if (neuron.getState() != null) {
                    String stateName = neuron.getState().name();
                    if ("ERROR".equals(stateName) || "STOPPED".equals(stateName)) {
                        result.issues.add(createIssue(
                            "neuron:" + neuron.getId(),
                            "Neuron " + neuron.getName() + " is in " + stateName + " state",
                            HealthIssue.Severity.MEDIUM
                        ));
                    } else {
                        result.healthyCount++;
                    }
                }
            } catch (Exception e) {
                result.issues.add(createIssue(
                    "neuron:" + neuron.getId(),
                    "Failed to check neuron: " + e.getMessage(),
                    HealthIssue.Severity.HIGH
                ));
            }
        }

        return result;
    }

    private HealthCheckResult checkBrains() {
        HealthCheckResult result = new HealthCheckResult();
        
        if (brainRegistry == null) {
            result.issues.add(createIssue("brains", "BrainRegistry not available", HealthIssue.Severity.CRITICAL));
            return result;
        }

        List<Brain> brains = brainRegistry.getAll();
        result.totalCount = brains.size();

        for (Brain brain : brains) {
            try {
                if (brain.getState() != null) {
                    String stateName = brain.getState().name();
                    if ("ERROR".equals(stateName)) {
                        result.issues.add(createIssue(
                            "brain:" + brain.getId(),
                            "Brain " + brain.getName() + " is in ERROR state",
                            HealthIssue.Severity.HIGH
                        ));
                    } else if ("STOPPED".equals(stateName) || "INITIALIZING".equals(stateName)) {
                        result.issues.add(createIssue(
                            "brain:" + brain.getId(),
                            "Brain " + brain.getName() + " is in " + stateName + " state",
                            HealthIssue.Severity.MEDIUM
                        ));
                    } else {
                        result.healthyCount++;
                    }
                }
            } catch (Exception e) {
                result.issues.add(createIssue(
                    "brain:" + brain.getId(),
                    "Failed to check brain: " + e.getMessage(),
                    HealthIssue.Severity.HIGH
                ));
            }
        }

        return result;
    }

    private HealthCheckResult checkChannels() {
        HealthCheckResult result = new HealthCheckResult();

        if (channelManager == null) {
            result.issues.add(createIssue("channels", "ChannelManager not available", HealthIssue.Severity.MEDIUM));
            return result;
        }

        try {
            ChannelManager.ChannelHealthSummary summary = channelManager.getHealthSummary();
            result.totalCount = summary.totalChannels();

            if (summary.totalChannels() == 0) {
                result.healthyCount = 0;
                return result;
            }

            int unhealthy = 0;
            // 无订阅者的通道视为不活跃但不一定不健康
            if (summary.emptyChannels() == summary.totalChannels()) {
                result.issues.add(createIssue("channels",
                    "All " + summary.totalChannels() + " channels have no subscribers",
                    HealthIssue.Severity.LOW));
                unhealthy = summary.totalChannels();
            }

            // 消息积压检测
            if (summary.totalMessages() > 10000) {
                result.issues.add(createIssue("channels",
                    "High message backlog: " + summary.totalMessages() + " messages across " + summary.totalChannels() + " channels",
                    HealthIssue.Severity.MEDIUM));
                unhealthy++;
            }

            result.healthyCount = result.totalCount - unhealthy;
        } catch (Exception e) {
            result.issues.add(createIssue("channels", "Failed to check channels: " + e.getMessage(), HealthIssue.Severity.HIGH));
        }

        return result;
    }

    private HealthCheckResult checkSystemResources() {
        HealthCheckResult result = new HealthCheckResult();
        result.totalCount = 2;

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (usedMemory * 100.0 / totalMemory);

        if (memoryUsage > memoryThreshold) {
            result.issues.add(createIssue(
                "memory",
                String.format("Memory usage high: %.1f%% (threshold: %.1f%%)", memoryUsage, memoryThreshold),
                HealthIssue.Severity.MEDIUM
            ));
        } else {
            result.healthyCount++;
        }

        result.healthyCount++;

        return result;
    }

    private HealthIssue createIssue(String component, String message, HealthIssue.Severity severity) {
        HealthIssue issue = new HealthIssue(component, message, severity);
        issue.setDescription(message);
        // P20修复: 根据 componentName 推断 IssueType，打通 SelfHealingOrchestrator 路由
        issue.setType(inferIssueType(component, message));
        fillSuggestedAction(issue);
        return issue;
    }

    private HealthIssue.IssueType inferIssueType(String component, String message) {
        if (component == null) return HealthIssue.IssueType.LOGIC;
        String lower = component.toLowerCase();
        if (lower.contains("model_daemon") || lower.contains("process") || lower.contains("pipe") || lower.contains("connectivity")) {
            return HealthIssue.IssueType.CONNECTIVITY;
        }
        if (lower.contains("memory") || lower.contains("cache") || lower.contains("resource") || lower.contains("disk")) {
            return HealthIssue.IssueType.RESOURCE;
        }
        if (lower.contains("performance") || lower.contains("latency") || lower.contains("timeout")) {
            return HealthIssue.IssueType.PERFORMANCE;
        }
        if (lower.contains("config") || lower.contains("setting")) {
            return HealthIssue.IssueType.CONFIGURATION;
        }
        if (lower.contains("security") || lower.contains("auth") || lower.contains("permission")) {
            return HealthIssue.IssueType.SECURITY;
        }
        // 默认: 严重级别高的视为连接问题，低的视为逻辑问题
        return HealthIssue.IssueType.LOGIC;
    }

    /** P24-A: 根据 IssueType 填充 suggestedAction，供 SelfHealingOrchestrator 使用 */
    private void fillSuggestedAction(HealthIssue issue) {
        if (issue.getSuggestedAction() != null) return;
        if (issue.getType() == null) return;

        issue.setSuggestedAction(switch (issue.getType()) {
            case CONNECTIVITY -> {
                // 管道丢失但进程可能存活时使用 RECONNECT_PIPE
                String comp = issue.getComponentName();
                if (comp != null && comp.contains("pipe")) {
                    yield "RECONNECT_PIPE";
                }
                yield "RESTART_PROCESS";
            }
            case RESOURCE -> "CLEAR_DEGRADED";
            case PERFORMANCE, CONFIGURATION -> "RECONFIGURE";
            default -> null;
        });
    }

    private HealthStatus determineStatus(List<HealthIssue> issues, int healthyCount, int totalCount) {
        if (totalCount == 0) {
            HealthStatus status = new HealthStatus();
            status.setStatus(HealthStatus.Status.UNKNOWN);
            return status;
        }

        boolean hasCritical = issues.stream().anyMatch(i -> i.getSeverity() == HealthIssue.Severity.CRITICAL);
        boolean hasHigh = issues.stream().anyMatch(i -> i.getSeverity() == HealthIssue.Severity.HIGH);

        HealthStatus status = new HealthStatus();
        status.setComponentName("system");
        
        if (hasCritical) {
            status.setStatus(HealthStatus.Status.UNHEALTHY);
            status.setMessage("Critical issues detected");
        } else if (hasHigh) {
            status.setStatus(HealthStatus.Status.UNHEALTHY);
            status.setMessage("High severity issues detected");
        } else if (!issues.isEmpty()) {
            status.setStatus(HealthStatus.Status.DEGRADED);
            status.setMessage("Minor issues detected");
        } else {
            status.setStatus(HealthStatus.Status.HEALTHY);
            status.setMessage("All components healthy");
        }
        
        return status;
    }

    private void generateAlerts(List<HealthIssue> issues) {
        for (HealthIssue issue : issues) {
            if (shouldGenerateAlert(issue)) {
                HealthAlert alert = new HealthAlert(
                    HealthAlert.AlertType.COMPONENT_DOWN,
                    issue.getComponentName(),
                    issue.getMessage()
                );
                
                activeAlerts.add(alert);
                
                if (activeAlerts.size() > maxAlerts) {
                    activeAlerts.remove(0);
                }
                
                log.warn("Health alert generated: {} - {}", issue.getComponent(), issue.getMessage());
            }
        }
    }

    private boolean shouldGenerateAlert(HealthIssue issue) {
        return activeAlerts.stream()
            .noneMatch(alert -> alert.getComponentName().equals(issue.getComponentName())
                && alert.getMessage().equals(issue.getMessage()));
    }

    @PreDestroy
    public void destroy() {
        if (scheduledCheck != null) {
            scheduledCheck.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("HealthMonitor shutdown complete");
    }

    private static class HealthCheckResult {
        int totalCount = 0;
        int healthyCount = 0;
        List<HealthIssue> issues = new ArrayList<>();
    }

    private HealthCheckResult toCheckResult(HealthStatus status) {
        HealthCheckResult result = new HealthCheckResult();
        result.totalCount = 1;
        if (status.getStatus() == HealthStatus.Status.HEALTHY) {
            result.healthyCount = 1;
        }
        return result;
    }
}
