package com.livingagent.core.workflow;

import com.livingagent.core.project.Project;
import com.livingagent.core.project.ProjectPhase;
import com.livingagent.core.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkflowMonitor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMonitor.class);

    private final ProjectService projectService;
    private final WorkflowOrchestrator orchestrator;
    private final Map<String, PhaseExecution> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCount = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private final List<WorkflowAlert> alerts = new ArrayList<>();

    private final Map<String, Duration> phaseTimeouts = new HashMap<>();
    private int maxRetries = 3;
    private Duration heartbeatTimeout = Duration.ofMinutes(5);
    private Duration checkInterval = Duration.ofMinutes(1);

    public WorkflowMonitor(ProjectService projectService, @Lazy WorkflowOrchestrator orchestrator) {
        this.projectService = projectService;
        this.orchestrator = orchestrator;
        initDefaultTimeouts();
    }

    private void initDefaultTimeouts() {
        phaseTimeouts.put(ProjectPhase.MARKET_ANALYSIS.getCode(), Duration.ofHours(24));
        phaseTimeouts.put(ProjectPhase.REQUIREMENT.getCode(), Duration.ofHours(8));
        phaseTimeouts.put(ProjectPhase.DESIGN.getCode(), Duration.ofHours(16));
        phaseTimeouts.put(ProjectPhase.DEVELOPMENT.getCode(), Duration.ofHours(72));
        phaseTimeouts.put(ProjectPhase.TESTING.getCode(), Duration.ofHours(24));
        phaseTimeouts.put(ProjectPhase.DEPLOYMENT.getCode(), Duration.ofHours(4));
        phaseTimeouts.put(ProjectPhase.OPERATION.getCode(), Duration.ofHours(8));
        phaseTimeouts.put(ProjectPhase.AFTER_SALES.getCode(), Duration.ofHours(48));
    }

    public void registerExecution(String projectId, ProjectPhase phase, String neuronId) {
        PhaseExecution execution = new PhaseExecution(projectId, phase, neuronId);
        activeExecutions.put(projectId, execution);
        lastHeartbeat.put(neuronId, Instant.now());
        retryCount.put(projectId, 0);
        
        log.info("Registered execution: project={}, phase={}, neuron={}", 
            projectId, phase.getCode(), neuronId);
    }

    public void recordHeartbeat(String neuronId) {
        lastHeartbeat.put(neuronId, Instant.now());
        log.debug("Heartbeat received from neuron: {}", neuronId);
    }

    public void completeExecution(String projectId) {
        PhaseExecution execution = activeExecutions.remove(projectId);
        if (execution != null) {
            execution.complete();
            retryCount.remove(projectId);
            log.info("Execution completed: project={}, duration={}", 
                projectId, execution.getDuration());
        }
    }

    public void failExecution(String projectId, String error) {
        PhaseExecution execution = activeExecutions.get(projectId);
        if (execution != null) {
            execution.fail(error);
            
            int retries = retryCount.getOrDefault(projectId, 0);
            if (retries < maxRetries) {
                retryCount.put(projectId, retries + 1);
                addAlert(projectId, execution.getPhase(), AlertType.RETRY, 
                    "Execution failed, retrying (" + (retries + 1) + "/" + maxRetries + "): " + error);
                log.warn("Execution failed, will retry: project={}, error={}", projectId, error);
            } else {
                addAlert(projectId, execution.getPhase(), AlertType.DEADLOCK, 
                    "Execution failed after " + maxRetries + " retries: " + error);
                handleDeadlock(projectId, execution);
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkTimeouts() {
        log.debug("Running timeout check for {} active executions", activeExecutions.size());
        
        Instant now = Instant.now();
        List<String> timedOut = new ArrayList<>();
        List<String> heartbeatsMissed = new ArrayList<>();
        
        for (Map.Entry<String, PhaseExecution> entry : activeExecutions.entrySet()) {
            String projectId = entry.getKey();
            PhaseExecution execution = entry.getValue();
            
            Duration timeout = phaseTimeouts.getOrDefault(
                execution.getPhase().getCode(), Duration.ofHours(24));
            
            if (execution.getElapsedTime().compareTo(timeout) > 0) {
                timedOut.add(projectId);
            }
            
            Instant lastBeat = lastHeartbeat.get(execution.getNeuronId());
            if (lastBeat != null) {
                Duration sinceLastBeat = Duration.between(lastBeat, now);
                if (sinceLastBeat.compareTo(heartbeatTimeout) > 0) {
                    heartbeatsMissed.add(projectId);
                }
            }
        }
        
        for (String projectId : timedOut) {
            handleTimeout(projectId);
        }
        
        for (String projectId : heartbeatsMissed) {
            handleHeartbeatMissed(projectId);
        }
    }

    private void handleTimeout(String projectId) {
        PhaseExecution execution = activeExecutions.get(projectId);
        if (execution == null) return;
        
        log.warn("Execution timeout detected: project={}, phase={}, elapsed={}", 
            projectId, execution.getPhase().getCode(), execution.getElapsedTime());
        
        addAlert(projectId, execution.getPhase(), AlertType.TIMEOUT,
            "Phase execution timed out after " + execution.getElapsedTime().toHours() + " hours");
        
        int retries = retryCount.getOrDefault(projectId, 0);
        if (retries < maxRetries) {
            retryCount.put(projectId, retries + 1);
            retryExecution(projectId, execution);
        } else {
            handleDeadlock(projectId, execution);
        }
    }

    private void handleHeartbeatMissed(String projectId) {
        PhaseExecution execution = activeExecutions.get(projectId);
        if (execution == null) return;
        
        log.warn("Heartbeat missed for project: {}, neuron: {}", 
            projectId, execution.getNeuronId());
        
        addAlert(projectId, execution.getPhase(), AlertType.HEARTBEAT_MISSED,
            "Neuron " + execution.getNeuronId() + " has not sent heartbeat for " + 
            heartbeatTimeout.toMinutes() + " minutes");
        
        retryExecution(projectId, execution);
    }

    private void handleDeadlock(String projectId, PhaseExecution execution) {
        log.error("Deadlock detected: project={}, phase={}", projectId, execution.getPhase().getCode());
        
        addAlert(projectId, execution.getPhase(), AlertType.DEADLOCK,
            "Deadlock detected - execution stuck at phase " + execution.getPhase().getDisplayName());
        
        activeExecutions.remove(projectId);
        
        Optional<Project> projectOpt = projectService.getProject(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setPhaseProgress(execution.getPhase(), -1);
        }
        
        orchestrator.cancelWorkflow(projectId);
    }

    private void retryExecution(String projectId, PhaseExecution execution) {
        log.info("Retrying execution: project={}, phase={}", projectId, execution.getPhase().getCode());
        
        activeExecutions.remove(projectId);
        
        try {
            orchestrator.executePhase(projectId, execution.getPhase());
            registerExecution(projectId, execution.getPhase(), execution.getNeuronId());
        } catch (Exception e) {
            log.error("Retry failed: {}", e.getMessage());
        }
    }

    private void addAlert(String projectId, ProjectPhase phase, AlertType type, String message) {
        WorkflowAlert alert = new WorkflowAlert(projectId, phase, type, message);
        alerts.add(alert);
        
        log.warn("Workflow alert: type={}, project={}, phase={}, message={}", 
            type, projectId, phase.getCode(), message);
    }

    public List<WorkflowAlert> getActiveAlerts() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        return alerts.stream()
            .filter(a -> a.getCreatedAt().isAfter(cutoff))
            .sorted(Comparator.comparing(WorkflowAlert::getCreatedAt).reversed())
            .toList();
    }

    public List<WorkflowAlert> getAlertsForProject(String projectId) {
        return alerts.stream()
            .filter(a -> a.getProjectId().equals(projectId))
            .sorted(Comparator.comparing(WorkflowAlert::getCreatedAt).reversed())
            .toList();
    }

    public Map<String, Object> getMonitoringStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeExecutions", activeExecutions.size());
        stats.put("totalAlerts24h", getActiveAlerts().size());
        stats.put("projects", new ArrayList<>(activeExecutions.keySet()));
        
        List<Map<String, Object>> executionDetails = new ArrayList<>();
        for (Map.Entry<String, PhaseExecution> entry : activeExecutions.entrySet()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("projectId", entry.getKey());
            detail.put("phase", entry.getValue().getPhase().getCode());
            detail.put("elapsed", entry.getValue().getElapsedTime().toString());
            detail.put("neuronId", entry.getValue().getNeuronId());
            executionDetails.add(detail);
        }
        stats.put("executionDetails", executionDetails);
        
        return stats;
    }

    public void setPhaseTimeout(String phaseCode, Duration timeout) {
        phaseTimeouts.put(phaseCode, timeout);
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setHeartbeatTimeout(Duration timeout) {
        this.heartbeatTimeout = timeout;
    }

    public static class PhaseExecution {
        private final String projectId;
        private final ProjectPhase phase;
        private final String neuronId;
        private final Instant startedAt;
        private Instant completedAt;
        private String error;
        private ExecutionStatus status = ExecutionStatus.RUNNING;

        public PhaseExecution(String projectId, ProjectPhase phase, String neuronId) {
            this.projectId = projectId;
            this.phase = phase;
            this.neuronId = neuronId;
            this.startedAt = Instant.now();
        }

        public String getProjectId() { return projectId; }
        public ProjectPhase getPhase() { return phase; }
        public String getNeuronId() { return neuronId; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public String getError() { return error; }
        public ExecutionStatus getStatus() { return status; }

        public Duration getElapsedTime() {
            return Duration.between(startedAt, 
                completedAt != null ? completedAt : Instant.now());
        }

        public Duration getDuration() {
            if (completedAt != null) {
                return Duration.between(startedAt, completedAt);
            }
            return getElapsedTime();
        }

        public void complete() {
            this.completedAt = Instant.now();
            this.status = ExecutionStatus.COMPLETED;
        }

        public void fail(String error) {
            this.error = error;
            this.status = ExecutionStatus.FAILED;
        }

        public enum ExecutionStatus {
            RUNNING,
            COMPLETED,
            FAILED,
            TIMEOUT
        }
    }

    public enum AlertType {
        TIMEOUT,
        HEARTBEAT_MISSED,
        DEADLOCK,
        RETRY,
        MANUAL_INTERVENTION
    }

    public static class WorkflowAlert {
        private final String projectId;
        private final ProjectPhase phase;
        private final AlertType type;
        private final String message;
        private final Instant createdAt;
        private boolean acknowledged = false;

        public WorkflowAlert(String projectId, ProjectPhase phase, AlertType type, String message) {
            this.projectId = projectId;
            this.phase = phase;
            this.type = type;
            this.message = message;
            this.createdAt = Instant.now();
        }

        public String getProjectId() { return projectId; }
        public ProjectPhase getPhase() { return phase; }
        public AlertType getType() { return type; }
        public String getMessage() { return message; }
        public Instant getCreatedAt() { return createdAt; }
        public boolean isAcknowledged() { return acknowledged; }
        public void acknowledge() { this.acknowledged = true; }
    }
}
