package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.security.Department;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private static final Logger log = LoggerFactory.getLogger(DepartmentController.class);

    private final EmployeeService employeeService;

    public DepartmentController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentInfo>>> listDepartments() {
        log.debug("Listing all departments");

        List<DepartmentInfo> departments = new ArrayList<>();
        departments.add(new DepartmentInfo("tech", "技术部", "Technology", "负责技术研发、系统架构、代码开发", "💻", 8, 0));
        departments.add(new DepartmentInfo("hr", "人力资源", "Human Resources", "负责招聘、培训、绩效管理", "👥", 3, 0));
        departments.add(new DepartmentInfo("finance", "财务部", "Finance", "负责财务管理、报销审批、预算管理", "💰", 4, 0));
        departments.add(new DepartmentInfo("sales", "销售部", "Sales", "负责销售支持、市场营销、客户开发", "📈", 4, 0));
        departments.add(new DepartmentInfo("admin", "行政部", "Administration", "负责行政事务、文档管理、文案创作", "📋", 4, 0));
        departments.add(new DepartmentInfo("cs", "客服部", "Customer Service", "负责工单处理、客户咨询、问题解答", "🎧", 3, 0));
        departments.add(new DepartmentInfo("legal", "法务部", "Legal", "负责合同审查、合规检查、法律咨询", "⚖️", 3, 0));
        departments.add(new DepartmentInfo("ops", "运营部", "Operations", "负责数据分析、运营策略、日常运营", "📊", 4, 0));
        departments.add(new DepartmentInfo("core", "核心层", "Core", "负责搜索、知识图谱、主动代理", "🔍", 2, 0));
        departments.add(new DepartmentInfo("cross_dept", "跨部门协调", "Cross-Department", "负责跨部门协调、战略规划", "🎯", 2, 0));

        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<DepartmentInfo>> getDepartmentByCode(@PathVariable String code) {
        log.debug("Getting department by code: {}", code);

        Map<String, DepartmentInfo> deptMap = new HashMap<>();
        deptMap.put("tech", new DepartmentInfo("tech", "技术部", "Technology", "负责技术研发、系统架构、代码开发", "💻", 8, 0));
        deptMap.put("hr", new DepartmentInfo("hr", "人力资源", "Human Resources", "负责招聘、培训、绩效管理", "👥", 3, 0));
        deptMap.put("finance", new DepartmentInfo("finance", "财务部", "Finance", "负责财务管理、报销审批、预算管理", "💰", 4, 0));
        deptMap.put("sales", new DepartmentInfo("sales", "销售部", "Sales", "负责销售支持、市场营销、客户开发", "📈", 4, 0));
        deptMap.put("admin", new DepartmentInfo("admin", "行政部", "Administration", "负责行政事务、文档管理、文案创作", "📋", 4, 0));
        deptMap.put("cs", new DepartmentInfo("cs", "客服部", "Customer Service", "负责工单处理、客户咨询、问题解答", "🎧", 3, 0));
        deptMap.put("legal", new DepartmentInfo("legal", "法务部", "Legal", "负责合同审查、合规检查、法律咨询", "⚖️", 3, 0));
        deptMap.put("ops", new DepartmentInfo("ops", "运营部", "Operations", "负责数据分析、运营策略、日常运营", "📊", 4, 0));
        deptMap.put("core", new DepartmentInfo("core", "核心层", "Core", "负责搜索、知识图谱、主动代理", "🔍", 2, 0));
        deptMap.put("cross_dept", new DepartmentInfo("cross_dept", "跨部门协调", "Cross-Department", "负责跨部门协调、战略规划", "🎯", 2, 0));

        DepartmentInfo dept = deptMap.get(code);
        if (dept == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Department not found: " + code));
        }

        return ResponseEntity.ok(ApiResponse.success(dept));
    }

    @GetMapping("/{id}/brain")
    public ResponseEntity<ApiResponse<BrainInfo>> getDepartmentBrain(@PathVariable String id) {
        log.debug("Getting brain for department: {}", id);

        Map<String, String> brainNames = new HashMap<>();
        brainNames.put("tech", "TechBrain");
        brainNames.put("hr", "HrBrain");
        brainNames.put("finance", "FinanceBrain");
        brainNames.put("sales", "SalesBrain");
        brainNames.put("admin", "AdminBrain");
        brainNames.put("cs", "CsBrain");
        brainNames.put("legal", "LegalBrain");
        brainNames.put("ops", "OpsBrain");
        brainNames.put("core", "CoreBrain");
        brainNames.put("cross_dept", "MainBrain");

        String brainName = brainNames.getOrDefault(id, "UnknownBrain");

        BrainInfo brain = new BrainInfo(
                id + "_brain",
                brainName,
                "running",
                Instant.now(),
                100
        );

        return ResponseEntity.ok(ApiResponse.success(brain));
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

    @GetMapping("/{id}/agents")
    public ResponseEntity<ApiResponse<List<AgentInfo>>> getDepartmentAgents(@PathVariable String id) {
        log.debug("Getting agents for department: {}", id);

        List<AgentInfo> agents = new ArrayList<>();
        String deptName = DEPT_CODE_TO_NAME.getOrDefault(id, id);

        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0);
        List<Employee> allEmployees = employeeService.listEmployees(query);

        for (Employee emp : allEmployees) {
            if (emp.isDigital()) {
                String dept = emp.getDepartment();
                if (dept != null && (dept.equals(deptName) || dept.equals(id) || dept.contains(deptName))) {
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
        }

        log.debug("Found {} agents for department: {}", agents.size(), id);
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<MemberInfo>>> getDepartmentMembers(@PathVariable String id) {
        log.debug("Getting members for department: {}", id);

        List<MemberInfo> members = new ArrayList<>();
        String deptName = DEPT_CODE_TO_NAME.getOrDefault(id, id);

        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0);
        List<Employee> allEmployees = employeeService.listEmployees(query);

        for (Employee emp : allEmployees) {
            if (!emp.isDigital()) {
                String dept = emp.getDepartment();
                if (dept != null && (dept.equals(deptName) || dept.equals(id) || dept.contains(deptName))) {
                    members.add(new MemberInfo(
                            emp.getEmployeeId(),
                            emp.getName(),
                            emp.getTitle(),
                            emp.getEmail().orElse(null)
                    ));
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success(members));
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
