package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/agents")
public class AgentApiController {

    private static final Logger log = LoggerFactory.getLogger(AgentApiController.class);

    private final EmployeeService employeeService;
    private final NeuronRegistry neuronRegistry;

    public AgentApiController(
            EmployeeService employeeService,
            NeuronRegistry neuronRegistry
    ) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentSummary>>> listAgents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        log.debug("Listing agents, type: {}, department: {}, status: {}", type, department, status);

        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(
                type != null ? parseType(type) : null,
                department,
                status != null ? EmployeeStatus.valueOf(status.toUpperCase()) : null,
                null,
                limit,
                offset
        );

        List<Employee> employees = employeeService.listEmployees(query);
        List<AgentSummary> agents = employees.stream()
                .map(this::convertToSummary)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    @GetMapping("/{agentId}/status")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> getAgentStatus(@PathVariable String agentId) {
        log.debug("Getting agent status: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        AgentStatusDetail status = new AgentStatusDetail(
                agentId,
                employee.getName(),
                employee.getStatus().name(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getLastActiveAt(),
                employee.getTaskCount(),
                employee.getSuccessCount(),
                employee.getSuccessRate(),
                employee.getSkills(),
                employee.getCapabilities(),
                buildCurrentTask(employee),
                buildMetrics(employee)
        );

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/{agentId}/action")
    public ResponseEntity<ApiResponse<ActionResponse>> triggerAction(
            @PathVariable String agentId,
            @RequestBody ActionRequest request
    ) {
        log.info("Triggering action for agent {}: {}", agentId, request.action());

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("agent_unavailable", "Agent is not active"));
        }

        String actionId = "action_" + System.currentTimeMillis();
        ActionResponse response = new ActionResponse(
                actionId,
                agentId,
                request.action(),
                "triggered",
                Instant.now(),
                request.parameters()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{agentId}/skills")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> getAgentSkills(@PathVariable String agentId) {
        log.debug("Getting agent skills: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        List<SkillInfo> skills = employee.getSkills().stream()
                .map(skill -> new SkillInfo(skill, skill, true, null))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    @PostMapping("/{agentId}/skills/{skillName}")
    public ResponseEntity<ApiResponse<Void>> bindSkill(
            @PathVariable String agentId,
            @PathVariable String skillName
    ) {
        log.info("Binding skill {} to agent {}", skillName, agentId);

        try {
            employeeService.bindSkill(agentId, skillName);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("bind_failed", e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}/skills/{skillName}")
    public ResponseEntity<ApiResponse<Void>> unbindSkill(
            @PathVariable String agentId,
            @PathVariable String skillName
    ) {
        log.info("Unbinding skill {} from agent {}", skillName, agentId);

        try {
            employeeService.unbindSkill(agentId, skillName);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("unbind_failed", e.getMessage()));
        }
    }

    @GetMapping("/{agentId}/metrics")
    public ResponseEntity<ApiResponse<AgentMetrics>> getAgentMetrics(@PathVariable String agentId) {
        log.debug("Getting agent metrics: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        AgentMetrics metrics = buildMetrics(employee);

        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    private IdUtils.EmployeeType parseType(String type) {
        return switch (type.toLowerCase()) {
            case "digital" -> IdUtils.EmployeeType.DIGITAL;
            case "human" -> IdUtils.EmployeeType.HUMAN;
            default -> null;
        };
    }

    private AgentSummary convertToSummary(Employee employee) {
        return new AgentSummary(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getIcon(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getStatus().name(),
                employee.isDigital() ? "digital" : "human",
                employee.getLastActiveAt()
        );
    }

    private Map<String, Object> buildCurrentTask(Employee employee) {
        return Map.of(
                "hasTask", false,
                "description", "当前无任务",
                "startedAt", Instant.now()
        );
    }

    private AgentMetrics buildMetrics(Employee employee) {
        return new AgentMetrics(
                employee.getTaskCount(),
                employee.getSuccessCount(),
                employee.getSuccessRate(),
                0,
                0.0,
                employee.getLastActiveAt()
        );
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record AgentSummary(
            String id,
            String name,
            String avatar,
            String department,
            String title,
            String status,
            String type,
            Instant lastActiveAt
    ) {}

    public record AgentStatusDetail(
            String id,
            String name,
            String status,
            String department,
            String title,
            Instant lastActiveAt,
            int totalTasks,
            int successfulTasks,
            double successRate,
            List<String> skills,
            List<String> capabilities,
            Map<String, Object> currentTask,
            AgentMetrics metrics
    ) {}

    public record ActionRequest(
            String action,
            Map<String, Object> parameters
    ) {
        public ActionRequest {
            if (parameters == null) parameters = Map.of();
        }
    }

    public record ActionResponse(
            String actionId,
            String agentId,
            String action,
            String status,
            Instant triggeredAt,
            Map<String, Object> parameters
    ) {}

    public record SkillInfo(
            String id,
            String name,
            boolean enabled,
            String description
    ) {}

    public record AgentMetrics(
            int totalTasks,
            int successfulTasks,
            double successRate,
            int todayTasks,
            double avgResponseTime,
            Instant lastActiveAt
    ) {}
}
