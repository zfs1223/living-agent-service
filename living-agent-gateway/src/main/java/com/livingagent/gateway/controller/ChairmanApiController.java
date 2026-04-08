package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chairman")
public class ChairmanApiController {

    private static final Logger log = LoggerFactory.getLogger(ChairmanApiController.class);

    private final UnifiedAuthService authService;
    private final EmployeeService employeeService;

    public ChairmanApiController(UnifiedAuthService authService, EmployeeService employeeService) {
        this.authService = authService;
        this.employeeService = employeeService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardOverview> getDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        log.info("Chairman dashboard accessed by: {}", ctx.getEmployeeId());
        
        return ResponseEntity.ok(new DashboardOverview(
            8,
            32,
            156,
            98.5,
            List.of(
                new DepartmentMetric("tech", "技术部", 45, 92.5, "健康"),
                new DepartmentMetric("hr", "人力资源", 12, 88.0, "健康"),
                new DepartmentMetric("finance", "财务部", 18, 95.0, "健康"),
                new DepartmentMetric("sales", "销售部", 35, 78.5, "需关注"),
                new DepartmentMetric("admin", "行政部", 15, 90.0, "健康"),
                new DepartmentMetric("cs", "客服部", 20, 85.0, "健康"),
                new DepartmentMetric("legal", "法务部", 6, 92.0, "健康"),
                new DepartmentMetric("ops", "运营部", 25, 88.5, "健康")
            ),
            List.of(
                new SystemAlert("INFO", "系统运行正常", "所有服务正常运行"),
                new SystemAlert("WARNING", "销售部响应时间较长", "建议检查客服人员配置")
            )
        ));
    }

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeSummary>> getAllEmployees(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        List<EmployeeSummary> employees = new ArrayList<>();
        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0);
        employeeService.listEmployees(query).forEach(emp -> {
            employees.add(new EmployeeSummary(
                emp.getEmployeeId(),
                emp.getName(),
                emp.getDepartment(),
                emp.getTitle(),
                emp.getIdentity().name(),
                emp.getAccessLevel().name(),
                emp.getStatus() == com.livingagent.core.employee.EmployeeStatus.ACTIVE
            ));
        });
        
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<EmployeeDetail> getEmployeeDetail(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        Optional<Employee> optEmp = employeeService.getEmployee(employeeId);
        if (optEmp.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Employee emp = optEmp.get();
        return ResponseEntity.ok(new EmployeeDetail(
            emp.getEmployeeId(),
            emp.getName(),
            emp.getDepartment(),
            emp.getDepartment(),
            emp.getTitle(),
            emp.getIdentity().name(),
            emp.getAccessLevel().name(),
            false,
            emp.getStatus() == com.livingagent.core.employee.EmployeeStatus.ACTIVE,
            emp.getCreatedAt(),
            null,
            emp.getAuthProvider()
        ));
    }

    @PostMapping("/employees/{employeeId}/access-level")
    public ResponseEntity<Map<String, Object>> updateEmployeeAccessLevel(
            @PathVariable String employeeId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        String newAccessLevel = request.get("accessLevel");
        if (newAccessLevel == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "缺少 accessLevel 参数"));
        }
        
        AuthContext ctx = ctxOpt.get();
        log.info("Chairman {} updating employee {} access level to {}", 
            ctx.getEmployeeId(), employeeId, newAccessLevel);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "employeeId", employeeId,
            "newAccessLevel", newAccessLevel
        ));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentSummary>> getAllDepartments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(List.of(
            new DepartmentSummary("tech", "技术部", 45, "TechBrain", "emp_tech_mgr"),
            new DepartmentSummary("hr", "人力资源", 12, "HrBrain", "emp_hr_mgr"),
            new DepartmentSummary("finance", "财务部", 18, "FinanceBrain", "emp_finance_mgr"),
            new DepartmentSummary("sales", "销售部", 35, "SalesBrain", "emp_sales_mgr"),
            new DepartmentSummary("admin", "行政部", 15, "AdminBrain", "emp_admin_mgr"),
            new DepartmentSummary("cs", "客服部", 20, "CsBrain", "emp_cs_mgr"),
            new DepartmentSummary("legal", "法务部", 6, "LegalBrain", "emp_legal_mgr"),
            new DepartmentSummary("ops", "运营部", 25, "OpsBrain", "emp_ops_mgr")
        ));
    }

    @GetMapping("/system/status")
    public ResponseEntity<SystemStatus> getSystemStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isChairman(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return ResponseEntity.ok(new SystemStatus(
            "running",
            "1.0.0",
            usedMemory / (1024 * 1024),
            totalMemory / (1024 * 1024),
            runtime.availableProcessors(),
            Thread.activeCount(),
            32,
            8
        ));
    }

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean isChairman(AuthContext ctx) {
        return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
    }

    public record DashboardOverview(
        int departmentCount,
        int digitalEmployeeCount,
        int totalEmployeeCount,
        double systemHealthScore,
        List<DepartmentMetric> departments,
        List<SystemAlert> alerts
    ) {}

    public record DepartmentMetric(
        String code,
        String name,
        int memberCount,
        double healthScore,
        String status
    ) {}

    public record SystemAlert(
        String level,
        String title,
        String message
    ) {}

    public record EmployeeSummary(
        String employeeId,
        String name,
        String department,
        String position,
        String identity,
        String accessLevel,
        boolean active
    ) {}

    public record EmployeeDetail(
        String employeeId,
        String name,
        String department,
        String departmentName,
        String position,
        String identity,
        String accessLevel,
        boolean founder,
        boolean active,
        Object joinDate,
        String voicePrintId,
        String oauthProvider
    ) {}

    public record DepartmentSummary(
        String code,
        String name,
        int memberCount,
        String brain,
        String managerId
    ) {}

    public record SystemStatus(
        String status,
        String version,
        long usedMemoryMB,
        long totalMemoryMB,
        int availableProcessors,
        int activeThreads,
        int digitalEmployeeCount,
        int departmentCount
    ) {}
}
