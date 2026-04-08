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
import java.util.stream.Collectors;

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
            @RequestParam(required = false) String tenant_id,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        log.debug("Listing agents, type: {}, department: {}, status: {}, tenant_id: {}", type, department, status, tenant_id);

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

    @GetMapping("/{agentId}/tasks")
    public ResponseEntity<ApiResponse<List<TaskInfo>>> getAgentTasks(
            @PathVariable String agentId,
            @RequestParam(required = false) String status_filter,
            @RequestParam(required = false) String type_filter
    ) {
        log.debug("Getting tasks for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        List<TaskInfo> tasks = new ArrayList<>();
        tasks.add(new TaskInfo(
                "task_example",
                agentId,
                "示例任务",
                "pending",
                5,
                Instant.now(),
                null,
                null
        ));

        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/{agentId}/activity")
    public ResponseEntity<ApiResponse<List<ActivityInfo>>> getAgentActivity(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        log.debug("Getting activity for agent: {}, limit: {}", agentId, limit);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        List<ActivityInfo> activities = new ArrayList<>();
        activities.add(new ActivityInfo(
                "act_" + System.currentTimeMillis(),
                agentId,
                "status_change",
                "智能体状态更新",
                Instant.now(),
                Map.of("from", "idle", "to", "active")
        ));

        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    @GetMapping("/{agentId}/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfo>>> getAgentSessions(
            @PathVariable String agentId,
            @RequestParam(required = false) String scope
    ) {
        log.debug("Getting sessions for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        List<SessionInfo> sessions = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @PostMapping("/{agentId}/sessions")
    public ResponseEntity<ApiResponse<SessionInfo>> createSession(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> body
    ) {
        log.info("Creating session for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        SessionInfo session = new SessionInfo(
                "sess_" + System.currentTimeMillis(),
                agentId,
                "active",
                Instant.now(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentDetail>> getAgentDetail(@PathVariable String agentId) {
        log.debug("Getting agent detail: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        AgentDetail detail = new AgentDetail(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getIcon(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getStatus().name(),
                employee.isDigital() ? "digital" : "human",
                employee.getLastActiveAt(),
                employee.getSkills(),
                employee.getCapabilities(),
                buildMetrics(employee)
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping(params = "id")
    public ResponseEntity<ApiResponse<AgentDetail>> getAgentById(@RequestParam String id) {
        log.debug("Getting agent by query id: {}", id);
        return getAgentDetail(id);
    }

    @PatchMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentDetail>> updateAgent(
            @PathVariable String agentId,
            @RequestBody UpdateAgentRequest request
    ) {
        log.info("Updating agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        try {
            EmployeeService.EmployeeUpdateRequest updateRequest = new EmployeeService.EmployeeUpdateRequest(
                    request.name(),
                    request.title(),
                    null,
                    request.department(),
                    null,
                    null,
                    null,
                    request.capabilities(),
                    request.skills(),
                    null,
                    null
            );
            Employee updated = employeeService.updateEmployee(agentId, updateRequest);
            AgentDetail detail = new AgentDetail(
                    updated.getEmployeeId(),
                    updated.getName(),
                    updated.getIcon(),
                    updated.getDepartment(),
                    updated.getTitle(),
                    updated.getStatus().name(),
                    updated.isDigital() ? "digital" : "human",
                    updated.getLastActiveAt(),
                    updated.getSkills(),
                    updated.getCapabilities(),
                    buildMetrics(updated)
            );
            return ResponseEntity.ok(ApiResponse.success(detail));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("update_failed", e.getMessage()));
        }
    }

    @PostMapping("/{agentId}/start")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> startAgent(@PathVariable String agentId) {
        log.info("Starting agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        try {
            employeeService.updateStatus(agentId, EmployeeStatus.ONLINE);
            Employee employee = employeeService.getEmployee(agentId).get();
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
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("start_failed", e.getMessage()));
        }
    }

    @PostMapping("/{agentId}/stop")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> stopAgent(@PathVariable String agentId) {
        log.info("Stopping agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        try {
            employeeService.updateStatus(agentId, EmployeeStatus.OFFLINE);
            Employee employee = employeeService.getEmployee(agentId).get();
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
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("stop_failed", e.getMessage()));
        }
    }

    @GetMapping("/{agentId}/collaborators")
    public ResponseEntity<ApiResponse<List<CollaboratorInfo>>> getCollaborators(@PathVariable String agentId) {
        log.debug("Getting collaborators for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        List<CollaboratorInfo> collaborators = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.success(collaborators));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<AgentTemplate>>> getTemplates() {
        log.debug("Getting agent templates");

        List<AgentTemplate> templates = List.of(
                new AgentTemplate("code-reviewer", "代码审查员", "技术部", "负责代码审查和质量把控"),
                new AgentTemplate("architect", "架构师", "技术部", "负责系统架构设计"),
                new AgentTemplate("devops", "DevOps工程师", "技术部", "负责CI/CD和运维"),
                new AgentTemplate("accountant", "会计", "财务部", "负责财务核算"),
                new AgentTemplate("recruiter", "招聘专员", "人力资源", "负责人才招聘"),
                new AgentTemplate("cs-agent", "客服代表", "客服部", "负责客户服务")
        );

        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @PostMapping("/{agentId}/api-key")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> generateApiKey(@PathVariable String agentId) {
        log.info("Generating API key for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        String apiKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        ApiKeyResponse response = new ApiKeyResponse(apiKey, "API key generated successfully");

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{agentId}/config")
    public ResponseEntity<ApiResponse<AgentConfig>> getAgentConfig(@PathVariable String agentId) {
        log.debug("Getting config for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        AgentConfig config = new AgentConfig(
                employee.getEmployeeId(),
                employee.getName(),
                5,
                true,
                "09:00-18:00",
                List.of("chat", "task", "email")
        );

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{agentId}/config")
    public ResponseEntity<ApiResponse<AgentConfig>> updateAgentConfig(
            @PathVariable String agentId,
            @RequestBody AgentConfig config
    ) {
        log.info("Updating config for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + agentId));
        }

        return ResponseEntity.ok(ApiResponse.success(config));
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

    public record TaskInfo(
            String id,
            String agent_id,
            String title,
            String status,
            int priority,
            Instant created_at,
            Instant started_at,
            Instant completed_at
    ) {}

    public record ActivityInfo(
            String id,
            String agent_id,
            String type,
            String description,
            Instant created_at,
            Map<String, Object> metadata
    ) {}

    public record SessionInfo(
            String id,
            String agent_id,
            String status,
            Instant created_at,
            Instant last_message_at
    ) {}

    public record AgentDetail(
            String id,
            String name,
            String avatar,
            String department,
            String title,
            String status,
            String type,
            Instant lastActiveAt,
            List<String> skills,
            List<String> capabilities,
            AgentMetrics metrics
    ) {}

    public record UpdateAgentRequest(
            String name,
            String title,
            String department,
            List<String> skills,
            List<String> capabilities
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (name != null) map.put("name", name);
            if (title != null) map.put("title", title);
            if (department != null) map.put("department", department);
            if (skills != null) map.put("skills", skills);
            if (capabilities != null) map.put("capabilities", capabilities);
            return map;
        }
    }

    public record CollaboratorInfo(
            String id,
            String name,
            String type,
            String role
    ) {}

    public record AgentTemplate(
            String id,
            String name,
            String department,
            String description
    ) {}

    public record ApiKeyResponse(
            String apiKey,
            String message
    ) {}

    public record AgentConfig(
            String agentId,
            String name,
            int maxConcurrentTasks,
            boolean autoResponse,
            String workingHours,
            List<String> allowedChannels
    ) {}
}
