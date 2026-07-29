package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/org")
public class OrgController {

    private static final Logger log = LoggerFactory.getLogger(OrgController.class);

    private final EmployeeService employeeService;
    private final AccessGateService accessGateService;

    public OrgController(EmployeeService employeeService, AccessGateService accessGateService) {
        this.employeeService = employeeService;
        this.accessGateService = accessGateService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserInfo>>> getUsers(
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Getting users for tenant: {}", tenant_id);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<UserInfo> users = new ArrayList<>();

        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0, null);
        List<Employee> employees = employeeService.listEmployees(query);

        for (Employee emp : employees) {
            if (!emp.isDigital()) {
                users.add(new UserInfo(
                        emp.getEmployeeId(),
                        emp.getName(),
                        emp.getName(),
                        emp.getEmail().orElse(null),
                        emp.getDepartment(),
                        emp.getTitle(),
                        emp.getStatus().name()
                ));
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<DepartmentInfo>>> getDepartments(
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Getting departments for tenant: {}", tenant_id);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<DepartmentInfo> departments = Arrays.asList(
                new DepartmentInfo("tech", "技术部", 10, 5),
                new DepartmentInfo("hr", "人力资源", 3, 2),
                new DepartmentInfo("finance", "财务部", 4, 2),
                new DepartmentInfo("sales", "销售部", 4, 3),
                new DepartmentInfo("admin", "行政部", 4, 2),
                new DepartmentInfo("cs", "客服部", 3, 2),
                new DepartmentInfo("legal", "法务部", 3, 1),
                new DepartmentInfo("ops", "运营部", 4, 2),
                new DepartmentInfo("core", "核心层", 2, 1)
        );

        return ResponseEntity.ok(ApiResponse.ok(departments));
    }

    public record UserInfo(
            String id,
            String name,
            String display_name,
            String email,
            String department,
            String title,
            String status
    ) {}

    public record DepartmentInfo(
            String id,
            String name,
            int agent_count,
            int member_count
    ) {}
}
