package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.*;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class HealthMonitorImpl implements HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitorImpl.class);

    private final NeuronRegistry neuronRegistry;
    private final BrainRegistry brainRegistry;
    private final ChannelManager channelManager;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicReference<HealthStatus> currentStatus = new AtomicReference<>();
    private final List<HealthAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final Map<String, HealthCheckResult> lastCheckResults = new ConcurrentHashMap<>();
    private final Map<String, HealthCheck> registeredChecks = new ConcurrentHashMap<>();
    private final Map<String, Double> alertThresholds = new ConcurrentHashMap<>();
    
    private long checkIntervalMs = 30000;
    private double cpuThreshold = 80.0;
    private double memoryThreshold = 85.0;
    private int maxAlerts = 100;

    public HealthMonitorImpl(NeuronRegistry neuronRegistry, 
                             BrainRegistry brainRegistry,
                             ChannelManager channelManager) {
        this.neuronRegistry = neuronRegistry;
        this.brainRegistry = brainRegistry;
        this.channelManager = channelManager;
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

        if (status.getStatus() != HealthStatus.Status.HEALTHY) {
            generateAlerts(issues);
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
                        HealthIssue issue = new HealthIssue();
                        issue.setComponentName(status.getComponentName());
                        issue.setTitle(status.getMessage());
                        issue.setSeverity(HealthIssue.Severity.valueOf(status.getStatus().name()));
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
            result.totalCount = 1;
            result.healthyCount = 1;
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
        return issue;
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

    private static class HealthCheckResult {
        int totalCount = 0;
        int healthyCount = 0;
        List<HealthIssue> issues = new ArrayList<>();
    }
}
