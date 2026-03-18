package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.brain.BrainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/office")
public class OfficeController {

    private static final Logger log = LoggerFactory.getLogger(OfficeController.class);

    private final EmployeeService employeeService;
    private final NeuronRegistry neuronRegistry;
    private final BrainRegistry brainRegistry;

    public OfficeController(
            EmployeeService employeeService,
            NeuronRegistry neuronRegistry,
            BrainRegistry brainRegistry
    ) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
        this.brainRegistry = brainRegistry;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<OfficeStatusResponse>> getOfficeStatus() {
        log.debug("Getting office status");

        List<Employee> digitalEmployees = employeeService.listDigitalEmployees();
        List<Employee> humanEmployees = employeeService.listHumanEmployees();

        int activeCount = 0;
        int dormantCount = 0;
        int learningCount = 0;

        for (Employee emp : digitalEmployees) {
            switch (emp.getStatus()) {
                case ACTIVE, ONLINE -> activeCount++;
                case OFFLINE, AWAY -> dormantCount++;
                case LEARNING -> learningCount++;
                default -> {}
            }
        }

        Map<String, DepartmentStatus> departments = buildDepartmentStatuses();

        OfficeStatusResponse response = new OfficeStatusResponse(
                digitalEmployees.size(),
                humanEmployees.size(),
                activeCount,
                dormantCount,
                learningCount,
                departments,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<AgentInfo>>> getAgents(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status
    ) {
        log.debug("Getting agents, department: {}, status: {}", department, status);

        List<Employee> employees;
        if (department != null && !department.isEmpty()) {
            employees = employeeService.listByDepartment(department);
        } else {
            employees = employeeService.listEmployees(
                    new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0)
            );
        }

        if (status != null && !status.isEmpty()) {
            EmployeeStatus filterStatus = EmployeeStatus.valueOf(status.toUpperCase());
            employees = employees.stream()
                    .filter(e -> e.getStatus() == filterStatus)
                    .toList();
        }

        List<AgentInfo> agents = employees.stream()
                .map(this::convertToAgentInfo)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    @GetMapping("/agents/{id}")
    public ResponseEntity<ApiResponse<AgentInfo>> getAgent(@PathVariable String id) {
        log.debug("Getting agent: {}", id);

        Optional<Employee> employeeOpt = employeeService.getEmployee(id);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + id));
        }

        AgentInfo agentInfo = convertToAgentInfo(employeeOpt.get());
        return ResponseEntity.ok(ApiResponse.success(agentInfo));
    }

    @PostMapping("/agent/state")
    public ResponseEntity<ApiResponse<AgentInfo>> updateAgentState(
            @RequestBody UpdateAgentStateRequest request
    ) {
        log.info("Updating agent state: {} -> {}", request.agentId(), request.status());

        Optional<Employee> employeeOpt = employeeService.getEmployee(request.agentId());
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Agent not found: " + request.agentId()));
        }

        EmployeeStatus newStatus = EmployeeStatus.valueOf(request.status().toUpperCase());
        employeeService.updateStatus(request.agentId(), newStatus);

        employeeOpt = employeeService.getEmployee(request.agentId());
        AgentInfo agentInfo = convertToAgentInfo(employeeOpt.get());

        return ResponseEntity.ok(ApiResponse.success(agentInfo));
    }

    @GetMapping("/areas")
    public ResponseEntity<ApiResponse<List<OfficeArea>>> getAreas() {
        log.debug("Getting office areas");

        List<OfficeArea> areas = new ArrayList<>();

        List<String> departments = Arrays.asList(
                "tech", "sales", "hr", "finance", "cs", "legal", "admin", "ops", "core"
        );

        for (String dept : departments) {
            List<Employee> deptEmployees = employeeService.listByDepartment(dept);
            int activeCount = (int) deptEmployees.stream()
                    .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                    .count();

            areas.add(new OfficeArea(
                    dept,
                    getDepartmentDisplayName(dept),
                    deptEmployees.size(),
                    activeCount,
                    activeCount > 0 ? "active" : "quiet"
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(areas));
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<ApiResponse<DepartmentDetail>> getDepartmentStatus(
            @PathVariable String department
    ) {
        log.debug("Getting department status: {}", department);

        List<Employee> employees = employeeService.listByDepartment(department);

        Map<EmployeeStatus, Long> statusCounts = new EnumMap<>(EmployeeStatus.class);
        for (Employee emp : employees) {
            statusCounts.merge(emp.getStatus(), 1L, Long::sum);
        }

        List<AgentInfo> agents = employees.stream()
                .map(this::convertToAgentInfo)
                .toList();

        DepartmentDetail detail = new DepartmentDetail(
                department,
                getDepartmentDisplayName(department),
                employees.size(),
                statusCounts.getOrDefault(EmployeeStatus.ACTIVE, 0L).intValue(),
                statusCounts.getOrDefault(EmployeeStatus.AWAY, 0L).intValue(),
                statusCounts.getOrDefault(EmployeeStatus.LEARNING, 0L).intValue(),
                agents
        );

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/yesterday-memo")
    public ResponseEntity<ApiResponse<YesterdayMemo>> getYesterdayMemo() {
        log.debug("Getting yesterday memo");

        YesterdayMemo memo = new YesterdayMemo(
                Instant.now().minusSeconds(86400),
                List.of(),
                List.of(),
                List.of(),
                "暂无昨日备忘"
        );

        return ResponseEntity.ok(ApiResponse.success(memo));
    }

    private Map<String, DepartmentStatus> buildDepartmentStatuses() {
        Map<String, DepartmentStatus> departments = new LinkedHashMap<>();

        List<String> deptNames = Arrays.asList(
                "tech", "sales", "hr", "finance", "cs", "legal", "admin", "ops", "core"
        );

        for (String dept : deptNames) {
            List<Employee> deptEmployees = employeeService.listByDepartment(dept);
            int active = (int) deptEmployees.stream()
                    .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                    .count();

            departments.put(dept, new DepartmentStatus(
                    dept,
                    getDepartmentDisplayName(dept),
                    deptEmployees.size(),
                    active
            ));
        }

        return departments;
    }

    private String getDepartmentDisplayName(String dept) {
        return switch (dept.toLowerCase()) {
            case "tech" -> "技术部";
            case "sales" -> "销售部";
            case "hr" -> "人力资源";
            case "finance" -> "财务部";
            case "cs" -> "客服部";
            case "legal" -> "法务部";
            case "admin" -> "行政部";
            case "ops" -> "运维部";
            case "core" -> "核心层";
            default -> dept;
        };
    }

    private AgentInfo convertToAgentInfo(Employee employee) {
        return new AgentInfo(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getIcon(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getStatus().name(),
                employee.isDigital() ? "digital" : "human",
                employee.getLastActiveAt(),
                employee.getTaskCount(),
                employee.getSuccessRate(),
                employee.getSkills()
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

    public record OfficeStatusResponse(
            int digitalEmployeeCount,
            int humanEmployeeCount,
            int activeCount,
            int dormantCount,
            int learningCount,
            Map<String, DepartmentStatus> departments,
            Instant timestamp
    ) {}

    public record DepartmentStatus(
            String id,
            String name,
            int totalEmployees,
            int activeEmployees
    ) {}

    public record AgentInfo(
            String id,
            String name,
            String avatar,
            String department,
            String title,
            String status,
            String type,
            Instant lastActiveAt,
            int taskCount,
            double successRate,
            List<String> skills
    ) {}

    public record UpdateAgentStateRequest(String agentId, String status) {}

    public record OfficeArea(
            String id,
            String name,
            int totalAgents,
            int activeAgents,
            String status
    ) {}

    public record DepartmentDetail(
            String id,
            String name,
            int totalEmployees,
            int activeCount,
            int dormantCount,
            int learningCount,
            List<AgentInfo> agents
    ) {}

    public record YesterdayMemo(
            Instant date,
            List<String> completedTasks,
            List<String> pendingTasks,
            List<String> notes,
            String summary
    ) {}
}
