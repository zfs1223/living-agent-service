package com.livingagent.gateway.controller;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.employee.EmployeeLifecycleService;
import com.livingagent.core.employee.EmployeeOrigin;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.importer.EmployeeImporter;
import com.livingagent.core.security.importer.EmployeeImporter.ImportResult;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeLifecycleService lifecycleService;
    private final EmployeeCompensationService compensationService;
    private final AccessGateService accessGateService;
    private final EmployeeImporter employeeImporter;

    public EmployeeController(EmployeeService employeeService,
                              EmployeeLifecycleService lifecycleService,
                              EmployeeCompensationService compensationService,
                              AccessGateService accessGateService) {
        this.employeeService = employeeService;
        this.lifecycleService = lifecycleService;
        this.compensationService = compensationService;
        this.accessGateService = accessGateService;
        this.employeeImporter = new EmployeeImporter();
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        List<Employee> all = lifecycleService.listAll();
        Map<EmployeeOrigin, Long> byOrigin = all.stream().collect(Collectors.groupingBy(Employee::getOrigin, () -> new EnumMap<>(EmployeeOrigin.class), Collectors.counting()));
        Map<EmployeeStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Employee::getStatus, () -> new EnumMap<>(EmployeeStatus.class), Collectors.counting()));
        return ResponseEntity.ok(Map.of(
                "total", all.size(),
                "byOrigin", byOrigin,
                "byStatus", byStatus,
                "balances", compensationService.summarizeDepartment("all")
        ));
    }

    @GetMapping("/origin/{origin}")
    public ResponseEntity<?> listByOrigin(@PathVariable EmployeeOrigin origin,
                                          @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        List<Employee> employees = lifecycleService.listAll().stream().filter(e -> e.getOrigin() == origin).toList();
        return ResponseEntity.ok(Map.of("origin", origin, "items", employees));
    }

    @PostMapping("/refresh-idle")
    public ResponseEntity<?> refreshIdle(@RequestParam(defaultValue = "PT7D") String maxIdle,
                                         @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }
        lifecycleService.refreshIdleEmployees(Duration.parse(maxIdle));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/import")
    public ResponseEntity<?> importRoster(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "format", defaultValue = "csv") String format,
                                          @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }

        try {
            byte[] data = file.getBytes();
            ImportResult result = "excel".equalsIgnoreCase(format)
                    ? employeeImporter.importFromExcel(data)
                    : employeeImporter.importFromCsv(data);

            List<Map<String, Object>> importedEmployees = result.employees().stream().map(emp -> {
                Map<String, Object> m = new HashMap<>();
                m.put("employeeId", emp.getEmployeeId());
                m.put("name", emp.getName());
                m.put("phone", emp.getPhone());
                m.put("email", emp.getEmail());
                m.put("department", emp.getDepartment());
                m.put("position", emp.getPosition());
                return m;
            }).toList();

            List<Map<String, Object>> departments = employeeImporter.extractDepartments(result.employees()).stream().map(dept -> {
                Map<String, Object> m = new HashMap<>();
                m.put("departmentId", dept.getDepartmentId());
                m.put("name", dept.getName());
                m.put("memberCount", dept.getMemberIds().size());
                return m;
            }).toList();

            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "totalRows", result.totalRows(),
                    "importedCount", result.importedCount(),
                    "employees", importedEmployees,
                    "departments", departments,
                    "errors", result.errors(),
                    "hasWarnings", result.hasWarnings()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("import_error", e.getMessage()));
        }
    }

    @PostMapping("/import/preview")
    public ResponseEntity<?> previewImport(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "format", defaultValue = "csv") String format,
                                           @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.hasFullAccess(employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Full access required"));
        }

        try {
            byte[] data = file.getBytes();
            ImportResult result = "excel".equalsIgnoreCase(format)
                    ? employeeImporter.importFromExcel(data)
                    : employeeImporter.importFromCsv(data);

            List<Map<String, Object>> preview = result.employees().stream().limit(10).map(emp -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", emp.getName());
                m.put("phone", emp.getPhone());
                m.put("email", emp.getEmail());
                m.put("department", emp.getDepartment());
                m.put("position", emp.getPosition());
                return m;
            }).toList();

            return ResponseEntity.ok(Map.of(
                    "totalRows", result.totalRows(),
                    "estimatedCount", result.importedCount(),
                    "preview", preview,
                    "errors", result.errors(),
                    "hasWarnings", result.hasWarnings()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("import_error", e.getMessage()));
        }
    }
}
