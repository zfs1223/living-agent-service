package com.livingagent.core.security;

import java.util.List;
import java.util.Optional;

public interface EmployeeAuthService {

    Employee createEmployee(Employee employee);

    Employee updateEmployee(Employee employee);

    void deleteEmployee(String employeeId);

    Optional<Employee> findById(String employeeId);

    Optional<Employee> findByPhone(String phone);

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByVoicePrintId(String voicePrintId);

    Optional<Employee> findByOAuth(String provider, String oauthUserId);

    List<Employee> findByDepartment(String department);

    List<Employee> findByIdentity(UserIdentity identity);

    List<Employee> findAllActive();

    List<Employee> findAll();

    int importFromExcel(byte[] excelData);

    int importFromCsv(byte[] csvData);

    int importFromHrSystem(String hrSystemType);

    int syncFromDingTalk();

    int syncFromFeishu();

    int syncFromWeCom();

    void updateEmployeeStatus(String employeeId, UserIdentity newIdentity);

    void handleAiDetectedChange(String employeeId, EmployeeService.ChangeType changeType, String detectedFrom, String details);

    void setVoicePrintId(String employeeId, String voicePrintId);

    void linkOAuthAccount(String employeeId, String provider, String oauthUserId);

    void recordSync(String employeeId, String source, boolean success, String message);

    List<Employee> getEmployeesNeedingSync();

    boolean hasAnyEmployee();

    boolean hasFounder();
}
