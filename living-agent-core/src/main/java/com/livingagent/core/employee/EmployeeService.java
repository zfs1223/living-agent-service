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

    /**
     * 按部门代码查询成员摘要列表（从数据库加载）
     * @param departmentCode 部门代码（如 "tech"）
     * @return 成员摘要列表
     */
    List<MemberSummary> getDepartmentMembersByCode(String departmentCode);

    /**
     * 根据员工ID查询成员摘要（从数据库加载）
     * @param employeeId 员工ID
     * @return 成员摘要
     */
    Optional<MemberSummary> getMemberSummary(String employeeId);

    /**
     * 成员摘要记录
     */
    record MemberSummary(
        String employeeId,
        String name,
        String departmentName,
        String departmentCode,
        String status,
        String origin,
        String position,
        String avatarUrl,
        String accessLevel
    ) {}

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
        String phone,
        EmployeeOrigin origin,
        String suggestedEmployeeId,
        String primaryModelId,
        String fallbackModelId,
        String templateId,
        String permissionScopeType,
        String permissionAccessLevel,
        Long maxTokensPerDay,
        Long maxTokensPerMonth,
        String ownerId
    ) {
        public EmployeeOrigin origin() {
            return origin != null ? origin : EmployeeOrigin.PERSONAL;
        }
    }

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
