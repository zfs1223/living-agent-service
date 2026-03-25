package com.livingagent.core.employee;

import com.livingagent.core.employee.Employee.DigitalConfig;
import com.livingagent.core.employee.Employee.WorkflowBinding;
import com.livingagent.core.util.IdUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EmployeeService {

    Employee createEmployee(EmployeeCreationRequest request);
    
    Optional<Employee> getEmployee(String employeeId);
    
    Employee updateEmployee(String employeeId, EmployeeUpdateRequest request);
    
    void updateStatus(String employeeId, EmployeeStatus status);
    
    void deleteEmployee(String employeeId);
    
    List<Employee> listEmployees(EmployeeQuery query);
    
    List<Employee> listByDepartment(String departmentId);
    
    List<Employee> listByStatus(EmployeeStatus status);
    
    List<Employee> listDigitalEmployees();
    
    List<Employee> listHumanEmployees();
    
    Optional<Employee> findByAuthId(String authProvider, String authId);
    
    void bindSkill(String employeeId, String skillName);
    
    void unbindSkill(String employeeId, String skillName);
    
    List<String> getSkills(String employeeId);
    
    void addCapability(String employeeId, String capability);
    
    void recordTask(String employeeId, boolean success);
    
    void checkAndDormantIdleEmployees();
    
    void wakeupEmployee(String employeeId);
    
    void terminateEmployee(String employeeId, String reason);

    record EmployeeCreationRequest(
        IdUtils.EmployeeType type,
        String authProvider,
        String authId,
        String name,
        String title,
        String icon,
        String department,
        String departmentId,
        List<String> roles,
        String managerId,
        List<String> capabilities,
        List<String> skills,
        List<String> tools,
        EmployeePersonality personality,
        Duration ttl,
        List<String> subscribeChannels,
        List<String> publishChannels,
        List<WorkflowBinding> workflowBindings,
        String email,
        String phone
    ) {}

    record EmployeeUpdateRequest(
        String name,
        String title,
        String icon,
        String department,
        String departmentId,
        List<String> roles,
        String managerId,
        List<String> capabilities,
        List<String> skills,
        List<String> tools,
        EmployeePersonality personality
    ) {}

    record EmployeeQuery(
        IdUtils.EmployeeType type,
        String departmentId,
        EmployeeStatus status,
        String nameKeyword,
        int limit,
        int offset
    ) {
        public EmployeeQuery {
            if (limit <= 0) limit = 100;
            if (offset < 0) offset = 0;
        }
    }
}
