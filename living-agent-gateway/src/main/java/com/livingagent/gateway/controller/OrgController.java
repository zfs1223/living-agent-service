package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
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

    public OrgController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserInfo>>> getUsers(
            @RequestParam(required = false) String tenant_id
    ) {
        log.debug("Getting users for tenant: {}", tenant_id);

        List<UserInfo> users = new ArrayList<>();

        EmployeeService.EmployeeQuery query = new EmployeeService.EmployeeQuery(null, null, null, null, 100, 0);
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

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<DepartmentInfo>>> getDepartments(
            @RequestParam(required = false) String tenant_id
    ) {
        log.debug("Getting departments for tenant: {}", tenant_id);

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

        return ResponseEntity.ok(ApiResponse.success(departments));
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
