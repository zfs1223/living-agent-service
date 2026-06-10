package com.livingagent.gateway.service;

import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.gateway.proactive.ProactiveOrchestrator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardDataService {

    private final MonitoringService monitoringService;
    private final BackupRecoveryService backupRecoveryService;
    private final ProactiveOrchestrator proactiveOrchestrator;
    private final EmployeeService employeeService;
    private final BrainRegistry brainRegistry;
    private final NeuronRegistry neuronRegistry;
    private final KnowledgeManager knowledgeManager;
    private final TaskCheckout taskCheckout;
    private final TaskEventBridgeService taskEventBridgeService;
    private final PerformanceDashboardService performanceDashboardService;
    private final KnowledgeGovernanceService knowledgeGovernanceService;

    public DashboardDataService(
            MonitoringService monitoringService,
            BackupRecoveryService backupRecoveryService,
            ProactiveOrchestrator proactiveOrchestrator,
            EmployeeService employeeService,
            BrainRegistry brainRegistry,
            NeuronRegistry neuronRegistry,
            KnowledgeManager knowledgeManager,
            TaskCheckout taskCheckout,
            TaskEventBridgeService taskEventBridgeService,
            PerformanceDashboardService performanceDashboardService,
            KnowledgeGovernanceService knowledgeGovernanceService,
            EvolutionFeedbackBridgeService evolutionFeedbackBridgeService) {
        this.monitoringService = monitoringService;
        this.backupRecoveryService = backupRecoveryService;
        this.proactiveOrchestrator = proactiveOrchestrator;
        this.employeeService = employeeService;
        this.brainRegistry = brainRegistry;
        this.neuronRegistry = neuronRegistry;
        this.knowledgeManager = knowledgeManager;
        this.taskCheckout = taskCheckout;
        this.taskEventBridgeService = taskEventBridgeService;
        this.performanceDashboardService = performanceDashboardService;
        this.knowledgeGovernanceService = knowledgeGovernanceService;
    }

    public Map<String, Object> buildOverview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now().toString());
        payload.put("system", monitoringService.summary());
        payload.put("employees", employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, null, null, 1000, 0)).size());
        payload.put("activeEmployees", employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, EmployeeStatus.ACTIVE, null, 1000, 0)).size());
        payload.put("brains", brainRegistry != null ? brainRegistry.getAll().size() : 0);
        payload.put("neurons", neuronRegistry != null ? neuronRegistry.getAll().size() : 0);
        payload.put("knowledge", knowledgeManager != null ? knowledgeManager.getStatistics() : Map.of());
        payload.put("knowledgeGovernance", knowledgeGovernanceService.summary());
        payload.put("proactive", proactiveOrchestrator.runForUser("dashboard"));
        payload.put("backupSnapshots", backupRecoveryService.listSnapshots(null).size());
        payload.put("tasks", taskCheckout.getStatistics());
        payload.put("taskEventBridge", taskEventBridgeService.onTaskReviewed("dashboard", "summary", "dashboard", true, 0, 1.0));
        payload.put("performance", performanceDashboardService.companySummary(null, 10));
        payload.put("evolution", Map.of("enabled", true, "note", "see /api/evolution/feedback/recent for recent evolution feedback"));
        payload.put("metrics", monitoringService.snapshotMetrics());
        return payload;
    }
}
