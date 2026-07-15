package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.EmployeeOrigin;
import com.livingagent.core.employee.EmployeePersonality;
import com.livingagent.core.employee.lifecycle.AgentLifecycleMonitor;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.util.IdUtils;
import com.livingagent.gateway.controller.common.ApiResponse;
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
    private final AccessGateService accessGateService;
    private final AgentLifecycleMonitor agentLifecycleMonitor;

    public AgentApiController(
            EmployeeService employeeService,
            NeuronRegistry neuronRegistry,
            AccessGateService accessGateService,
            AgentLifecycleMonitor agentLifecycleMonitor
    ) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
        this.accessGateService = accessGateService;
        this.agentLifecycleMonitor = agentLifecycleMonitor;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentDetail>> createAgent(
            @RequestBody CreateAgentRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating agent: {}", request.name());

        try {
            EmployeePersonality personality = null;
            if (request.personality() != null && !request.personality().isEmpty()) {
                double rigor = 0.7;
                double creativity = 0.5;
                double riskTolerance = 0.4;
                double obedience = 0.85;
                
                Object traitsObj = request.personality().get("traits");
                if (traitsObj instanceof List<?> traits) {
                    if (traits.contains("严谨") || traits.contains("严谨性")) rigor = 0.9;
                    if (traits.contains("创新") || traits.contains("创造力")) creativity = 0.8;
                    if (traits.contains("冒险") || traits.contains("风险容忍")) riskTolerance = 0.7;
                    if (traits.contains("服从") || traits.contains("执行力")) obedience = 0.9;
                }
                
                Object styleObj = request.personality().get("communication_style");
                if (styleObj instanceof String style) {
                    if (style.contains("creative") || style.contains("创新")) creativity = 0.8;
                    if (style.contains("formal") || style.contains("正式")) {
                        rigor = 0.9;
                        obedience = 0.9;
                    }
                }
                
                personality = EmployeePersonality.of(rigor, creativity, riskTolerance, obedience, 
                        EmployeePersonality.PersonalitySource.MANUAL);
            } else {
                personality = EmployeePersonality.defaultForDepartment(
                        request.department() != null ? request.department() : "default");
            }

            String employeeIdGenerated = request.suggested_id() != null ? request.suggested_id() : "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            EmployeeService.EmployeeCreationRequest creationRequest = new EmployeeService.EmployeeCreationRequest(
                    IdUtils.EmployeeType.DIGITAL,
                    null,
                    null,
                    request.name(),
                    request.role_description() != null ? request.role_description() : request.title(),
                    request.icon(),
                    request.department(),
                    request.department_id(),
                    null,
                    null,
                    request.capabilities(),
                    request.skill_ids(),
                    null,
                    personality,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    EmployeeOrigin.PERSONAL,
                    employeeIdGenerated,
                    request.primary_model_id(),
                    request.fallback_model_id(),
                    request.template_id(),
                    request.permission_scope_type(),
                    request.permission_access_level(),
                    request.max_tokens_per_day(),
                    request.max_tokens_per_month()
            );

            Employee created = employeeService.createEmployee(creationRequest);

            agentLifecycleMonitor.registerAgent(created.getEmployeeId(), "digital");

            AgentDetail detail = new AgentDetail(
                    created.getEmployeeId(),
                    created.getName(),
                    created.getIcon(),
                    created.getDepartment(),
                    created.getTitle(),
                    created.getStatus().name(),
                    created.isDigital() ? "digital" : "human",
                    mapOrigin(created),
                    created.getLastActiveAt(),
                    created.getSkills(),
                    created.getCapabilities(),
                    buildMetrics(created)
            );

            return ResponseEntity.ok(ApiResponse.ok(detail));
        } catch (Exception e) {
            log.error("Failed to create agent: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("create_failed", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentSummary>>> listAgents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenant_id,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
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

        return ResponseEntity.ok(ApiResponse.ok(agents));
    }

    @GetMapping("/{agentId}/status")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> getAgentStatus(@PathVariable String agentId) {
        log.debug("Getting agent status: {}", agentId);

        if (!accessGateService.canRoute(agentId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
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

        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @PostMapping("/{agentId}/action")
    public ResponseEntity<ApiResponse<ActionResponse>> triggerAction(
            @PathVariable String agentId,
            @RequestBody ActionRequest request
    ) {
        log.info("Triggering action for agent {}: {}", agentId, request.action());

        if (!accessGateService.canRoute(agentId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("agent_unavailable", "Agent is not active"));
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

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{agentId}/skills")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> getAgentSkills(@PathVariable String agentId) {
        log.debug("Getting agent skills: {}", agentId);

        if (!accessGateService.canRoute(agentId, "brain", "MainBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        List<SkillInfo> skills = employee.getSkills().stream()
                .map(skill -> new SkillInfo(skill, skill, true, null))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(skills));
    }

    @PostMapping("/{agentId}/skills/{skillName}")
    public ResponseEntity<ApiResponse<Void>> bindSkill(
            @PathVariable String agentId,
            @PathVariable String skillName,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Binding skill {} to agent {}", skillName, agentId);

        try {
            employeeService.bindSkill(agentId, skillName);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("bind_failed", e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}/skills/{skillName}")
    public ResponseEntity<ApiResponse<Void>> unbindSkill(
            @PathVariable String agentId,
            @PathVariable String skillName,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Unbinding skill {} from agent {}", skillName, agentId);

        try {
            employeeService.unbindSkill(agentId, skillName);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("unbind_failed", e.getMessage()));
        }
    }

    @GetMapping("/{agentId}/metrics")
    public ResponseEntity<ApiResponse<AgentMetrics>> getAgentMetrics(@PathVariable String agentId) {
        log.debug("Getting agent metrics: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        Employee employee = employeeOpt.get();
        AgentMetrics metrics = buildMetrics(employee);

        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    // Note: /{agentId}/tasks endpoint 已废弃，任务接口统一使用 TaskController 的 /api/tasks

    @GetMapping("/{agentId}/activity")
    public ResponseEntity<ApiResponse<List<ActivityInfo>>> getAgentActivity(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting activity for agent: {}, limit: {}", agentId, limit);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
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

        return ResponseEntity.ok(ApiResponse.ok(activities));
    }

    @GetMapping("/{agentId}/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfo>>> getAgentSessions(
            @PathVariable String agentId,
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting sessions for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        List<SessionInfo> sessions = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.ok(sessions));
    }

    @PostMapping("/{agentId}/sessions")
    public ResponseEntity<ApiResponse<SessionInfo>> createSession(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating session for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        SessionInfo session = new SessionInfo(
                "sess_" + System.currentTimeMillis(),
                agentId,
                "active",
                Instant.now(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(session));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentDetail>> getAgentDetail(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting agent detail: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
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
                mapOrigin(employee),
                employee.getLastActiveAt(),
                employee.getSkills(),
                employee.getCapabilities(),
                buildMetrics(employee)
        );

        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @GetMapping(params = "id")
    public ResponseEntity<ApiResponse<AgentDetail>> getAgentById(@RequestParam String id) {
        log.debug("Getting agent by query id: {}", id);
        return getAgentDetail(id, null);
    }

    @PatchMapping("/{agentId}")
    public ResponseEntity<ApiResponse<AgentDetail>> updateAgent(
            @PathVariable String agentId,
            @RequestBody UpdateAgentRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
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
                    updated.getOrigin().name().toLowerCase(),
                    updated.getLastActiveAt(),
                    updated.getSkills(),
                    updated.getCapabilities(),
                    buildMetrics(updated)
            );
            return ResponseEntity.ok(ApiResponse.ok(detail));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("update_failed", e.getMessage()));
        }
    }

    @PostMapping("/{agentId}/start")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> startAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Starting agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        try {
            employeeService.updateStatus(agentId, EmployeeStatus.ACTIVE);
            agentLifecycleMonitor.recordHeartbeat(agentId);
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
            return ResponseEntity.ok(ApiResponse.ok(status));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("start_failed", e.getMessage()));
        }
    }

    @PostMapping("/{agentId}/stop")
    public ResponseEntity<ApiResponse<AgentStatusDetail>> stopAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Stopping agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        try {
            employeeService.updateStatus(agentId, EmployeeStatus.OFFLINE);
            agentLifecycleMonitor.recordHeartbeat(agentId);
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
            return ResponseEntity.ok(ApiResponse.ok(status));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("stop_failed", e.getMessage()));
        }
    }

    @GetMapping("/{agentId}/collaborators")
    public ResponseEntity<ApiResponse<List<CollaboratorInfo>>> getCollaborators(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting collaborators for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        List<CollaboratorInfo> collaborators = new ArrayList<>();
        return ResponseEntity.ok(ApiResponse.ok(collaborators));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<AgentTemplate>>> getTemplates(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting agent templates");

        List<AgentTemplate> templates = List.of(
                new AgentTemplate("code-reviewer", "代码审查员", "技术部", "负责代码审查和质量把控"),
                new AgentTemplate("architect", "架构师", "技术部", "负责系统架构设计"),
                new AgentTemplate("devops", "DevOps工程师", "技术部", "负责CI/CD和运维"),
                new AgentTemplate("accountant", "会计", "财务部", "负责财务核算"),
                new AgentTemplate("recruiter", "招聘专员", "人力资源", "负责人才招聘"),
                new AgentTemplate("cs-agent", "客服代表", "客服部", "负责客户服务")
        );

        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    @PostMapping("/{agentId}/api-key")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> generateApiKey(
            @PathVariable String agentId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Generating API key for agent: {}", agentId);

        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        String apiKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        ApiKeyResponse response = new ApiKeyResponse(apiKey, "API key generated successfully");

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping(value = "/{agentId}/config")
    public ResponseEntity<ApiResponse<AgentConfig>> getAgentConfig(@PathVariable String agentId) {
        log.debug("Getting config for agent: {}", agentId);
        return getAgentConfigInternal(agentId);
    }

    @GetMapping(value = "/config", params = "id")
    public ResponseEntity<ApiResponse<AgentConfig>> getAgentConfigById(@RequestParam String id) {
        log.debug("Getting config for agent by query id: {}", id);
        return getAgentConfigInternal(id);
    }

    private ResponseEntity<ApiResponse<AgentConfig>> getAgentConfigInternal(String agentId) {
        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
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

        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping(value = "/{agentId}/config")
    public ResponseEntity<ApiResponse<AgentConfig>> updateAgentConfig(
            @PathVariable String agentId,
            @RequestBody AgentConfig config
    ) {
        log.info("Updating config for agent: {}", agentId);
        return updateAgentConfigInternal(agentId, config);
    }

    @PutMapping(value = "/config", params = "id")
    public ResponseEntity<ApiResponse<AgentConfig>> updateAgentConfigById(
            @RequestParam String id,
            @RequestBody AgentConfig config
    ) {
        log.info("Updating config for agent by query id: {}", id);
        return updateAgentConfigInternal(id, config);
    }

    private ResponseEntity<ApiResponse<AgentConfig>> updateAgentConfigInternal(String agentId, AgentConfig config) {
        Optional<Employee> employeeOpt = employeeService.getEmployee(agentId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Agent not found: " + agentId));
        }

        return ResponseEntity.ok(ApiResponse.ok(config));
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
                mapOrigin(employee),
                employee.getLastActiveAt()
        );
    }

    private String mapOrigin(Employee employee) {
        if (employee == null || employee.getOrigin() == null) {
            return "human";
        }
        return switch (employee.getOrigin()) {
            case FIXED -> "fixed";
            case PERSONAL -> "personal";
            case EVOLVED -> "evolved";
            default -> "human";
        };
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

    public record AgentSummary(
            String id,
            String name,
            String avatar,
            String department,
            String title,
            String status,
            String type,
            String origin,
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
            String origin,
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

    public record CreateAgentRequest(
            String name,
            String title,
            String role_description,
            String icon,
            String department,
            String department_id,
            String agent_type,
            Map<String, Object> personality,
            Map<String, Object> boundaries,
            String primary_model_id,
            String fallback_model_id,
            String template_id,
            String permission_scope_type,
            Long max_tokens_per_day,
            Long max_tokens_per_month,
            List<String> skill_ids,
            List<String> capabilities,
            String permission_access_level,
            String tenant_id,
            String suggested_id
    ) {}
}
