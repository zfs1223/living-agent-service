package com.livingagent.core.neuron.impl;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.project.Project;
import com.livingagent.core.project.ProjectPhase;
import com.livingagent.core.project.ProjectService;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.workflow.WorkflowOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ProjectDevelopmentNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(ProjectDevelopmentNeuron.class);

    private final ProjectService projectService;
    private final WorkflowOrchestrator orchestrator;

    public ProjectDevelopmentNeuron(ProjectService projectService, WorkflowOrchestrator orchestrator) {
        super(
            "tech/project_dev/01",
            "ProjectDevelopmentNeuron",
            "项目开发流程神经元 - 协调项目从需求到上线的全流程",
            List.of("channel://tech/projects", "channel://workflow/events"),
            List.of("channel://tech/tasks", "channel://workflow/commands"),
            Collections.emptyList()
        );
        this.projectService = projectService;
        this.orchestrator = orchestrator;
    }

    public ProjectDevelopmentNeuron(ProjectService projectService, WorkflowOrchestrator orchestrator, List<Tool> tools) {
        super(
            "tech/project_dev/01",
            "ProjectDevelopmentNeuron",
            "项目开发流程神经元 - 协调项目从需求到上线的全流程",
            List.of("channel://tech/projects", "channel://workflow/events"),
            List.of("channel://tech/tasks", "channel://workflow/commands"),
            tools
        );
        this.projectService = projectService;
        this.orchestrator = orchestrator;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("ProjectDevelopmentNeuron started");
    }

    @Override
    protected void doStop() {
        log.info("ProjectDevelopmentNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("ProjectDevelopmentNeuron processing message: {}", message.getType());
        
        Object payloadObj = message.getPayload();
        if (!(payloadObj instanceof Map)) {
            log.warn("Invalid payload type");
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        String action = (String) payload.get("action");
        
        if (action == null) {
            log.warn("No action specified in message");
            return;
        }
        
        try {
            switch (action) {
                case "create_project" -> handleCreateProject(payload);
                case "start_workflow" -> handleStartWorkflow(payload);
                case "advance_phase" -> handleAdvancePhase(payload);
                case "get_status" -> handleGetStatus(payload);
                case "pause_workflow" -> handlePauseWorkflow(payload);
                case "resume_workflow" -> handleResumeWorkflow(payload);
                case "cancel_workflow" -> handleCancelWorkflow(payload);
                default -> log.warn("Unknown action: {}", action);
            }
        } catch (Exception e) {
            log.error("Error processing action {}: {}", action, e.getMessage());
        }
    }

    private void handleCreateProject(Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String description = (String) payload.get("description");
        String department = (String) payload.get("department");
        String managerId = (String) payload.get("managerId");
        
        if (name == null || department == null) {
            log.warn("Missing required fields for project creation");
            return;
        }
        
        ProjectService.CreateProjectRequest request = new ProjectService.CreateProjectRequest(
            name, description, department, managerId
        );
        
        Project project = projectService.createProject(request);
        log.info("Created project: {} ({})", name, project.getProjectId());
    }

    private void handleStartWorkflow(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId == null) {
            log.warn("Missing projectId for workflow start");
            return;
        }
        orchestrator.startWorkflow(projectId);
        log.info("Started workflow for project: {}", projectId);
    }

    private void handleAdvancePhase(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId == null) {
            log.warn("Missing projectId for phase advance");
            return;
        }
        orchestrator.advancePhase(projectId);
        log.info("Advanced phase for project: {}", projectId);
    }

    private void handleGetStatus(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId == null) {
            log.info("Project statistics: {}", projectService.getStatistics());
            return;
        }
        projectService.getProject(projectId).ifPresent(p -> 
            log.info("Project status: {}", p));
    }

    private void handlePauseWorkflow(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId != null) {
            orchestrator.pauseWorkflow(projectId);
            log.info("Paused workflow: {}", projectId);
        }
    }

    private void handleResumeWorkflow(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId != null) {
            orchestrator.resumeWorkflow(projectId);
            log.info("Resumed workflow: {}", projectId);
        }
    }

    private void handleCancelWorkflow(Map<String, Object> payload) {
        String projectId = (String) payload.get("projectId");
        if (projectId != null) {
            orchestrator.cancelWorkflow(projectId);
            log.info("Cancelled workflow: {}", projectId);
        }
    }
}
