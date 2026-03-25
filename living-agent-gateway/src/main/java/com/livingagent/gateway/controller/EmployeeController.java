package com.livingagent.gateway.controller;

import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeService.EmployeeCreationRequest;
import com.livingagent.core.employee.EmployeeService.EmployeeUpdateRequest;
import com.livingagent.core.employee.EmployeeService.EmployeeQuery;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.EmployeePersonality;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.util.IdUtils;
import com.livingagent.gateway.controller.PhoneAuthController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;
    private final UnifiedAuthService authService;
    private final PhoneAuthController phoneAuthController;

    public EmployeeController(
            EmployeeService employeeService,
            UnifiedAuthService authService,
            PhoneAuthController phoneAuthController
    ) {
        this.employeeService = employeeService;
        this.authService = authService;
        this.phoneAuthController = phoneAuthController;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeInfo>>> listEmployees(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        EmployeeStatus statusFilter = null;
        if ("active".equals(status)) {
            statusFilter = EmployeeStatus.ACTIVE;
        } else if ("inactive".equals(status)) {
            statusFilter = EmployeeStatus.DISABLED;
        }

        EmployeeQuery query = new EmployeeQuery(
                null,
                department,
                statusFilter,
                null,
                100,
                0
        );

        List<com.livingagent.core.employee.Employee> employees = employeeService.listEmployees(query);
        List<EmployeeInfo> employeeInfos = employees.stream()
                .map(this::convertToEmployeeInfo)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(employeeInfos));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeInfo>> addEmployee(
            @RequestBody AddEmployeeRequest request,
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        com.livingagent.core.security.Employee currentUser = sessionOpt.get().employee();

        if (!currentUser.isFounder() && currentUser.getIdentity() != UserIdentity.INTERNAL_CHAIRMAN) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("forbidden", "只有董事长可以添加员工"));
        }

        if (request.phone() != null && !request.phone().isEmpty()) {
            Optional<com.livingagent.core.security.Employee> existingEmployee = phoneAuthController.getEmployeeByPhone(request.phone());
            if (existingEmployee.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("phone_exists", "该手机号已被其他员工使用"));
            }
        }

        EmployeeCreationRequest creationRequest = new EmployeeCreationRequest(
                IdUtils.EmployeeType.HUMAN,
                "internal",
                UUID.randomUUID().toString(),
                request.name(),
                request.position(),
                null,
                request.department(),
                request.department(),
                List.of("employee"),
                null,
                List.of(),
                request.allowedBrains() != null ? request.allowedBrains() : List.of(),
                List.of(),
                EmployeePersonality.of(0.7, 0.5, 0.4, 0.85),
                null,
                List.of(),
                List.of(),
                List.of(),
                request.email(),
                request.phone()
        );

        com.livingagent.core.employee.Employee employee = employeeService.createEmployee(creationRequest);

        if (request.phone() != null && !request.phone().isEmpty()) {
            com.livingagent.core.security.Employee securityEmployee = convertToSecurityEmployee(employee);
            phoneAuthController.registerEmployeePhone(securityEmployee, request.phone());
        }

        log.info("Employee added: {} ({}) by {}", employee.getName(), employee.getEmployeeId(), currentUser.getName());

        return ResponseEntity.ok(ApiResponse.success(convertToEmployeeInfo(employee)));
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<EmployeeInfo>> updateEmployee(
            @PathVariable String employeeId,
            @RequestBody UpdateEmployeeRequest request,
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        com.livingagent.core.security.Employee currentUser = sessionOpt.get().employee();

        if (!currentUser.isFounder() && currentUser.getIdentity() != UserIdentity.INTERNAL_CHAIRMAN) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("forbidden", "只有董事长可以修改员工信息"));
        }

        Optional<com.livingagent.core.employee.Employee> employeeOpt = employeeService.getEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "员工不存在"));
        }

        EmployeeUpdateRequest updateRequest = new EmployeeUpdateRequest(
                request.name(),
                request.position(),
                null,
                request.department(),
                request.department(),
                List.of("employee"),
                null,
                List.of(),
                request.allowedBrains() != null ? request.allowedBrains() : List.of(),
                List.of(),
                null
        );

        com.livingagent.core.employee.Employee employee = employeeService.updateEmployee(employeeId, updateRequest);

        if (request.active() != null) {
            employeeService.updateStatus(employeeId, 
                    request.active() ? EmployeeStatus.ACTIVE : EmployeeStatus.DISABLED);
        }

        log.info("Employee updated: {} ({}) by {}", employee.getName(), employee.getEmployeeId(), currentUser.getName());

        return ResponseEntity.ok(ApiResponse.success(convertToEmployeeInfo(employee)));
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(
            @PathVariable String employeeId,
            @RequestHeader("Authorization") String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        com.livingagent.core.security.Employee currentUser = sessionOpt.get().employee();

        if (!currentUser.isFounder() && currentUser.getIdentity() != UserIdentity.INTERNAL_CHAIRMAN) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("forbidden", "只有董事长可以删除员工"));
        }

        Optional<com.livingagent.core.employee.Employee> employeeOpt = employeeService.getEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "员工不存在"));
        }

        employeeService.deleteEmployee(employeeId);

        log.info("Employee deleted: {} by {}", employeeId, currentUser.getName());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<EmployeeInfo>> getEmployee(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("unauthorized", "请先登录"));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("session_expired", "会话已过期"));
        }

        Optional<com.livingagent.core.employee.Employee> employeeOpt = employeeService.getEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "员工不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success(convertToEmployeeInfo(employeeOpt.get())));
    }

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<DepartmentInfo>>> getDepartments(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Map<String, DepartmentInfo> departments = new LinkedHashMap<>();
        
        departments.put("tech", new DepartmentInfo("tech", "技术部", 0));
        departments.put("hr", new DepartmentInfo("hr", "人力资源部", 0));
        departments.put("finance", new DepartmentInfo("finance", "财务部", 0));
        departments.put("sales", new DepartmentInfo("sales", "销售部", 0));
        departments.put("ops", new DepartmentInfo("ops", "运营部", 0));
        departments.put("admin", new DepartmentInfo("admin", "行政部", 0));
        departments.put("legal", new DepartmentInfo("legal", "法务部", 0));
        departments.put("cs", new DepartmentInfo("cs", "客服部", 0));

        List<com.livingagent.core.employee.Employee> employees = employeeService.listEmployees(
                new EmployeeQuery(null, null, null, null, 1000, 0)
        );

        for (com.livingagent.core.employee.Employee employee : employees) {
            String dept = employee.getDepartment();
            if (dept != null && departments.containsKey(dept)) {
                DepartmentInfo current = departments.get(dept);
                departments.put(dept, new DepartmentInfo(dept, current.name(), current.count() + 1));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(departments.values())));
    }

    private EmployeeInfo convertToEmployeeInfo(com.livingagent.core.employee.Employee employee) {
        return new EmployeeInfo(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getEmail().orElse(null),
                employee.getPhone().map(this::maskPhone).orElse(null),
                employee.getDepartment(),
                employee.getTitle(),
                employee.isDigital() ? "DIGITAL" : "HUMAN",
                employee.getStatus() != null ? employee.getStatus().name() : "ACTIVE",
                employee.getStatus() == EmployeeStatus.ACTIVE,
                false,
                employee.getSkills() != null ? employee.getSkills() : List.of(),
                employee.getCreatedAt() != null ? employee.getCreatedAt().toString() : null
        );
    }

    private com.livingagent.core.security.Employee convertToSecurityEmployee(com.livingagent.core.employee.Employee employee) {
        com.livingagent.core.security.Employee securityEmployee = new com.livingagent.core.security.Employee();
        securityEmployee.setEmployeeId(employee.getEmployeeId());
        securityEmployee.setName(employee.getName());
        securityEmployee.setEmail(employee.getEmail().orElse(null));
        securityEmployee.setPhone(employee.getPhone().orElse(null));
        securityEmployee.setDepartment(employee.getDepartment());
        securityEmployee.setPosition(employee.getTitle());
        securityEmployee.setIdentity(employee.getIdentity());
        securityEmployee.setAccessLevel(employee.getAccessLevel());
        securityEmployee.setJoinDate(employee.getCreatedAt());
        securityEmployee.setActive(employee.getStatus() == EmployeeStatus.ACTIVE);
        return securityEmployee;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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

    public record EmployeeInfo(
            String employeeId,
            String name,
            String email,
            String phone,
            String department,
            String position,
            String identity,
            String accessLevel,
            boolean active,
            boolean founder,
            List<String> allowedBrains,
            String joinDate
    ) {}

    public record AddEmployeeRequest(
            String name,
            String email,
            String phone,
            String department,
            String position,
            List<String> allowedBrains
    ) {}

    public record UpdateEmployeeRequest(
            String name,
            String email,
            String department,
            String position,
            Boolean active,
            List<String> allowedBrains
    ) {}

    public record DepartmentInfo(
            String id,
            String name,
            int count
    ) {}
}
