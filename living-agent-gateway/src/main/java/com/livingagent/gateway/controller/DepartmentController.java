package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.model.pool.BrainModelAssigner;
import com.livingagent.core.model.pool.BrainModelAssignment;
import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import com.livingagent.gateway.service.DepartmentChatService;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private static final Logger log = LoggerFactory.getLogger(DepartmentController.class);

    private final EmployeeService employeeService;
    private final AccessGateService accessGateService;
    private final BrainModelAssigner brainModelAssigner;
    private final ModelPoolManager modelPoolManager;
    private final DepartmentChatService departmentChatService;

    public DepartmentController(EmployeeService employeeService, 
                                AccessGateService accessGateService,
                                BrainModelAssigner brainModelAssigner,
                                ModelPoolManager modelPoolManager,
                                DepartmentChatService departmentChatService) {
        this.employeeService = employeeService;
        this.accessGateService = accessGateService;
        this.brainModelAssigner = brainModelAssigner;
        this.modelPoolManager = modelPoolManager;
        this.departmentChatService = departmentChatService;
    }

    private static final Map<String, String> DEPT_CODE_TO_NAME = Map.ofEntries(
            Map.entry("tech", "技术部"),
            Map.entry("hr", "人力资源"),
            Map.entry("finance", "财务部"),
            Map.entry("sales", "销售部"),
            Map.entry("admin", "行政部"),
            Map.entry("cs", "客服部"),
            Map.entry("legal", "法务部"),
            Map.entry("ops", "运营部"),
            Map.entry("core", "核心层"),
            Map.entry("cross_dept", "跨部门协调")
    );

    private static final Map<String, String> DEPT_ICONS = Map.ofEntries(
            Map.entry("tech", "💻"),
            Map.entry("hr", "👥"),
            Map.entry("finance", "💰"),
            Map.entry("sales", "📈"),
            Map.entry("admin", "📋"),
            Map.entry("cs", "🎧"),
            Map.entry("legal", "⚖️"),
            Map.entry("ops", "📊"),
            Map.entry("core", "🔍"),
            Map.entry("cross_dept", "🎯")
    );

    private static final Map<String, String> DEPT_DESCRIPTIONS = Map.ofEntries(
            Map.entry("tech", "负责技术研发、系统架构、代码开发"),
            Map.entry("hr", "负责招聘、培训、绩效管理"),
            Map.entry("finance", "负责财务管理、报销审批、预算管理"),
            Map.entry("sales", "负责销售支持、市场营销、客户开发"),
            Map.entry("admin", "负责行政事务、文档管理、文案创作"),
            Map.entry("cs", "负责工单处理、客户咨询、问题解答"),
            Map.entry("legal", "负责合同审查、合规检查、法律咨询"),
            Map.entry("ops", "负责数据分析、运营策略、日常运营"),
            Map.entry("core", "负责搜索、知识图谱、主动代理"),
            Map.entry("cross_dept", "负责跨部门协调、战略规划")
    );

    private static final Map<String, String> DEPT_NAMES_EN = Map.ofEntries(
            Map.entry("tech", "Technology"),
            Map.entry("hr", "Human Resources"),
            Map.entry("finance", "Finance"),
            Map.entry("sales", "Sales"),
            Map.entry("admin", "Administration"),
            Map.entry("cs", "Customer Service"),
            Map.entry("legal", "Legal"),
            Map.entry("ops", "Operations"),
            Map.entry("core", "Core"),
            Map.entry("cross_dept", "Cross-Department")
    );

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentInfo>>> listDepartments(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Listing all departments");

        List<DepartmentInfo> departments = new ArrayList<>();
        for (String code : DEPT_CODE_TO_NAME.keySet()) {
            List<Employee> deptEmployees = employeeService.listByDepartment(code);
            int memberCount = (int) deptEmployees.stream().filter(e -> !e.isDigital()).count();
            int agentCount = (int) deptEmployees.stream().filter(Employee::isDigital).count();

            departments.add(new DepartmentInfo(
                    code,
                    DEPT_CODE_TO_NAME.get(code),
                    DEPT_NAMES_EN.getOrDefault(code, code),
                    DEPT_DESCRIPTIONS.getOrDefault(code, ""),
                    DEPT_ICONS.getOrDefault(code, "📁"),
                    agentCount,
                    memberCount
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(departments));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<DepartmentInfo>> getDepartmentByCode(
            @PathVariable String code,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting department by code: {}", code);

        String deptName = DEPT_CODE_TO_NAME.get(code);
        if (deptName == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("not_found", "Department not found: " + code));
        }

        List<Employee> deptEmployees = employeeService.listByDepartment(code);
        int memberCount = (int) deptEmployees.stream().filter(e -> !e.isDigital()).count();
        int agentCount = (int) deptEmployees.stream().filter(Employee::isDigital).count();

        DepartmentInfo dept = new DepartmentInfo(
                code,
                deptName,
                DEPT_NAMES_EN.getOrDefault(code, code),
                DEPT_DESCRIPTIONS.getOrDefault(code, ""),
                DEPT_ICONS.getOrDefault(code, "📁"),
                agentCount,
                memberCount
        );

        return ResponseEntity.ok(ApiResponse.ok(dept));
    }

    @GetMapping("/{id}/brain")
    public ResponseEntity<ApiResponse<BrainInfo>> getDepartmentBrain(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting brain for department: {}", id);

        String brainId = mapDepartmentToBrainId(id);
        BrainModelAssignment assignment = brainModelAssigner.getAssignment(brainId);

        if (assignment != null) {
            LlmModel model = modelPoolManager.getModelById(assignment.getModelId());
            String modelName = model != null ? model.getDisplayName() : assignment.getBrainName();
            boolean isEnabled = model != null && model.isEnabled();

            BrainInfo brain = new BrainInfo(
                    brainId,
                    modelName,
                    "running",
                    assignment.getAssignedAt() != null ? 
                            assignment.getAssignedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : Instant.now(),
                    isEnabled ? 100 : 0
            );
            return ResponseEntity.ok(ApiResponse.ok(brain));
        }

        String brainName = DEPT_CODE_TO_NAME.getOrDefault(id, id) + " Brain";
        BrainInfo brain = new BrainInfo(
                brainId,
                brainName + " (默认)",
                "running",
                null,
                100
        );
        return ResponseEntity.ok(ApiResponse.ok(brain));
    }

    @GetMapping("/{id}/brains")
    public ResponseEntity<ApiResponse<List<BrainInfo>>> getDepartmentBrains(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting all brains for department: {}", id);

        String brainId = mapDepartmentToBrainId(id);
        List<BrainModelAssignment> allAssignments = brainModelAssigner.getAllAssignments();

        List<BrainInfo> brains = allAssignments.stream()
                .filter(a -> a.getBrainId().equals(brainId) || isRelatedBrain(a.getBrainId(), id))
                .map(assignment -> {
                    LlmModel model = modelPoolManager.getModelById(assignment.getModelId());
                    boolean isEnabled = model != null && model.isEnabled();
                    return new BrainInfo(
                            assignment.getBrainId(),
                            model != null ? model.getDisplayName() : assignment.getBrainName(),
                            "running",
                            assignment.getAssignedAt() != null ? 
                                    assignment.getAssignedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : Instant.now(),
                            isEnabled ? 100 : 0
                    );
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(brains));
    }

    @GetMapping("/{id}/agents")
    public ResponseEntity<ApiResponse<List<AgentInfo>>> getDepartmentAgents(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting agents for department: {}", id);

        List<AgentInfo> agents = new ArrayList<>();
        List<Employee> deptEmployees = employeeService.listByDepartment(id);

        for (Employee emp : deptEmployees) {
            if (emp.isDigital()) {
                agents.add(new AgentInfo(
                        emp.getEmployeeId(),
                        emp.getName(),
                        emp.getIcon(),
                        emp.getTitle(),
                        emp.getStatus().name(),
                        "digital",
                        emp.getLastActiveAt()
                ));
            }
        }

        log.debug("Found {} agents for department: {}", agents.size(), id);
        return ResponseEntity.ok(ApiResponse.ok(agents));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<MemberInfo>>> getDepartmentMembers(
            @PathVariable String id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting members for department: {}", id);

        List<MemberInfo> members = new ArrayList<>();
        List<Employee> deptEmployees = employeeService.listByDepartment(id);

        for (Employee emp : deptEmployees) {
            if (!emp.isDigital()) {
                members.add(new MemberInfo(
                        emp.getEmployeeId(),
                        emp.getName(),
                        emp.getTitle(),
                        emp.getEmail().orElse(null)
                ));
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @GetMapping("/{id}/chat-history")
    public ResponseEntity<ApiResponse<List<DepartmentChatService.ChatHistoryEntry>>> getDepartmentChatHistory(
            @PathVariable String id,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "start", required = false) Instant start,
            @RequestParam(value = "end", required = false) Instant end,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Getting chat history for department: {}, limit={}, userId={}, start={}, end={}", id, limit, userId, start, end);
        boolean isFullAccess = hasFullAccess(employeeId);
        String effectiveUserId = isFullAccess ? userId : employeeId;
        List<DepartmentChatService.ChatHistoryEntry> history = departmentChatService.getHistory(id, effectiveUserId, start, end, limit);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    private String mapDepartmentToBrainId(String departmentCode) {
        return switch (departmentCode.toLowerCase()) {
            case "tech" -> "neuron://tech/tech-brain/001";
            case "admin" -> "neuron://admin/admin-brain/001";
            case "hr" -> "neuron://hr/hr-brain/001";
            case "finance" -> "neuron://finance/finance-brain/001";
            case "sales" -> "neuron://sales/sales-brain/001";
            case "cs" -> "neuron://cs/cs-brain/001";
            case "ops" -> "neuron://ops/ops-brain/001";
            case "legal" -> "neuron://legal/legal-brain/001";
            case "core", "cross_dept" -> "neuron://core/main-brain/001";
            default -> "neuron://core/main-brain/001";
        };
    }

    private boolean isRelatedBrain(String brainId, String departmentCode) {
        return brainId.contains("main") || brainId.contains(departmentCode);
    }

    private boolean hasFullAccess(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return false;
        }
        return accessGateService.hasFullAccess(employeeId);
    }

    public record DepartmentInfo(
            String id,
            String name,
            String name_en,
            String description,
            String icon,
            int agent_count,
            int member_count
    ) {}

    public record BrainInfo(
            String id,
            String name,
            String status,
            Instant last_active,
            int tasks_completed
    ) {}

    public record AgentInfo(
            String id,
            String name,
            String avatar,
            String title,
            String status,
            String type,
            Instant last_active_at
    ) {}

    public record MemberInfo(
            String id,
            String name,
            String title,
            String email
    ) {}
}
