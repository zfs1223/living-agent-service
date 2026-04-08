package com.livingagent.core.workflow;

import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.project.*;
import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.workflow.handlers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrator.class);

    private final ProjectService projectService;
    private final ChannelManager channelManager;
    private final Map<String, PhaseHandler> phaseHandlers = new ConcurrentHashMap<>();
    private final Map<String, WorkflowExecution> activeWorkflows = new ConcurrentHashMap<>();
    private WorkflowMonitor monitor;

    public WorkflowOrchestrator(ProjectService projectService, ChannelManager channelManager) {
        this.projectService = projectService;
        this.channelManager = channelManager;
        registerDefaultHandlers();
    }

    @Autowired(required = false)
    public void setMonitor(WorkflowMonitor monitor) {
        this.monitor = monitor;
        if (monitor != null) {
            log.info("WorkflowOrchestrator: WorkflowMonitor enabled");
        }
    }

    private void registerDefaultHandlers() {
        registerHandler(ProjectPhase.MARKET_ANALYSIS, new MarketAnalysisHandler());
        registerHandler(ProjectPhase.REQUIREMENT, new RequirementAnalysisHandler());
        registerHandler(ProjectPhase.DESIGN, new DesignHandler());
        registerHandler(ProjectPhase.DEVELOPMENT, new DevelopmentHandler());
        registerHandler(ProjectPhase.TESTING, new TestingHandler());
        registerHandler(ProjectPhase.DEPLOYMENT, new DeploymentHandler());
        registerHandler(ProjectPhase.OPERATION, new OperationHandler());
        registerHandler(ProjectPhase.AFTER_SALES, new AfterSalesHandler());
    }

    public void registerHandler(ProjectPhase phase, PhaseHandler handler) {
        phaseHandlers.put(phase.getCode(), handler);
        log.info("Registered phase handler for: {}", phase.getDisplayName());
    }

    public WorkflowExecution startWorkflow(String projectId) {
        Optional<Project> projectOpt = projectService.getProject(projectId);
        if (projectOpt.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }

        Project project = projectOpt.get();
        project.start();
        
        WorkflowExecution execution = new WorkflowExecution(projectId, project.getCurrentPhase());
        activeWorkflows.put(projectId, execution);

        executePhase(projectId, project.getCurrentPhase());
        
        log.info("Started workflow for project: {}", projectId);
        return execution;
    }

    public void advancePhase(String projectId) {
        Optional<Project> projectOpt = projectService.getProject(projectId);
        if (projectOpt.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }

        Project project = projectOpt.get();
        ProjectPhase currentPhase = project.getCurrentPhase();
        ProjectPhase[] phases = ProjectPhase.values();
        int currentIndex = currentPhase.ordinal();

        if (currentIndex >= phases.length - 1) {
            project.complete();
            WorkflowExecution execution = activeWorkflows.remove(projectId);
            if (execution != null) {
                execution.complete();
            }
            log.info("Project completed: {}", projectId);
            return;
        }

        project.advancePhase();
        ProjectPhase nextPhase = project.getCurrentPhase();
        
        WorkflowExecution execution = activeWorkflows.get(projectId);
        if (execution != null) {
            execution.setCurrentPhase(nextPhase);
        }

        executePhase(projectId, nextPhase);
        log.info("Advanced project {} to phase: {}", projectId, nextPhase.getDisplayName());
    }

    public void executePhase(String projectId, ProjectPhase phase) {
        PhaseHandler handler = phaseHandlers.get(phase.getCode());
        if (handler == null) {
            log.warn("No handler registered for phase: {}", phase.getCode());
            return;
        }

        WorkflowContext context = createWorkflowContext(projectId, phase);
        
        if (monitor != null) {
            monitor.registerExecution(projectId, phase, "workflow-orchestrator");
        }
        
        publishPhaseStart(projectId, phase);
        
        try {
            handler.execute(context);
            projectService.setPhaseProgress(projectId, phase.getCode(), 50.0);
        } catch (Exception e) {
            log.error("Phase execution failed for project {} phase {}: {}", 
                projectId, phase.getCode(), e.getMessage());
            context.addError(phase.getCode(), e.getMessage());
            
            if (monitor != null) {
                monitor.failExecution(projectId, e.getMessage());
            }
        }
    }

    public void onPhaseComplete(String projectId, ProjectPhase phase, Map<String, Object> result) {
        Optional<Project> projectOpt = projectService.getProject(projectId);
        if (projectOpt.isEmpty()) {
            log.warn("Project not found for phase completion: {}", projectId);
            return;
        }

        Project project = projectOpt.get();
        project.setPhaseProgress(phase, 100.0);

        WorkflowExecution execution = activeWorkflows.get(projectId);
        if (execution != null) {
            execution.addPhaseResult(phase.getCode(), result);
        }

        if (monitor != null) {
            monitor.completeExecution(projectId);
        }

        publishPhaseComplete(projectId, phase, result);

        if (shouldAutoAdvance(project, phase)) {
            advancePhase(projectId);
        }
    }

    private boolean shouldAutoAdvance(Project project, ProjectPhase completedPhase) {
        ProjectPhaseRecord record = project.getPhases().stream()
            .filter(p -> p.getPhase() == completedPhase)
            .findFirst()
            .orElse(null);
        
        if (record == null) return false;
        
        return record.getProgress() >= 100.0 && !completedPhase.equals(ProjectPhase.AFTER_SALES);
    }

    private WorkflowContext createWorkflowContext(String projectId, ProjectPhase phase) {
        Optional<Project> projectOpt = projectService.getProject(projectId);
        Project project = projectOpt.orElseThrow(() -> 
            new IllegalArgumentException("Project not found: " + projectId));
        
        return new WorkflowContext(
            projectId,
            project.getName(),
            phase,
            project.getMetadata(),
            this
        );
    }

    private void publishPhaseStart(String projectId, ProjectPhase phase) {
        Optional<Channel> channelOpt = channelManager.get("channel://workflow/events");
        if (channelOpt.isPresent()) {
            Channel channel = channelOpt.get();
            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", projectId);
            payload.put("phase", phase.getCode());
            payload.put("phaseName", phase.getDisplayName());
            payload.put("event", "PHASE_START");
            payload.put("timestamp", Instant.now().toString());
            
            ChannelMessage message = new ChannelMessage(
                "channel://workflow/events",
                "workflow-orchestrator",
                "channel://workflow/events",
                projectId,
                ChannelMessage.MessageType.CONTROL,
                payload
            );
            channel.publish(message);
        }
    }

    private void publishPhaseComplete(String projectId, ProjectPhase phase, Map<String, Object> result) {
        Optional<Channel> channelOpt = channelManager.get("channel://workflow/events");
        if (channelOpt.isPresent()) {
            Channel channel = channelOpt.get();
            Map<String, Object> payload = new HashMap<>();
            payload.put("projectId", projectId);
            payload.put("phase", phase.getCode());
            payload.put("phaseName", phase.getDisplayName());
            payload.put("event", "PHASE_COMPLETE");
            payload.put("result", result);
            payload.put("timestamp", Instant.now().toString());
            
            ChannelMessage message = new ChannelMessage(
                "channel://workflow/events",
                "workflow-orchestrator",
                "channel://workflow/events",
                projectId,
                ChannelMessage.MessageType.CONTROL,
                payload
            );
            channel.publish(message);
        }
    }

    public WorkflowExecution getExecution(String projectId) {
        return activeWorkflows.get(projectId);
    }

    public List<WorkflowExecution> getActiveWorkflows() {
        return new ArrayList<>(activeWorkflows.values());
    }

    public void pauseWorkflow(String projectId) {
        WorkflowExecution execution = activeWorkflows.get(projectId);
        if (execution != null) {
            execution.pause();
            log.info("Paused workflow for project: {}", projectId);
        }
    }

    public void resumeWorkflow(String projectId) {
        WorkflowExecution execution = activeWorkflows.get(projectId);
        if (execution != null) {
            execution.resume();
            Optional<Project> projectOpt = projectService.getProject(projectId);
            if (projectOpt.isPresent()) {
                executePhase(projectId, projectOpt.get().getCurrentPhase());
            }
            log.info("Resumed workflow for project: {}", projectId);
        }
    }

    public void cancelWorkflow(String projectId) {
        WorkflowExecution execution = activeWorkflows.remove(projectId);
        if (execution != null) {
            execution.cancel();
            Optional<Project> projectOpt = projectService.getProject(projectId);
            if (projectOpt.isPresent()) {
                projectOpt.get().cancel();
            }
            log.info("Cancelled workflow for project: {}", projectId);
        }
    }
}
