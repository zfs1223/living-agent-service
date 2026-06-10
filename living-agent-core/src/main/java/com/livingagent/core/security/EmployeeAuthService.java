package com.livingagent.core.security;

import java.util.List;
import java.util.Optional;

public interface EmployeeAuthService {

    SecurityIdentity createEmployee(SecurityIdentity employee);

    SecurityIdentity updateEmployee(SecurityIdentity employee);

    void deleteEmployee(String employeeId);

    Optional<SecurityIdentity> findById(String employeeId);

    Optional<SecurityIdentity> findByPhone(String phone);

    Optional<SecurityIdentity> findByEmail(String email);

    Optional<SecurityIdentity> findByVoicePrintId(String voicePrintId);

    Optional<SecurityIdentity> findByOAuth(String provider, String oauthUserId);

    List<SecurityIdentity> findByDepartment(String department);

    List<SecurityIdentity> findByIdentity(UserIdentity identity);

    List<SecurityIdentity> findAllActive();

    List<SecurityIdentity> findAll();

    int importFromExcel(byte[] excelData);

    int importFromCsv(byte[] csvData);

    int importFromHrSystem(String hrSystemType);

    int syncFromDingTalk();

    int syncFromFeishu();

    int syncFromWeCom();

    void updateEmployeeStatus(String employeeId, UserIdentity newIdentity);

    void handleAiDetectedChange(String employeeId, AuthEmployeeService.ChangeType changeType, String detectedFrom, String details);

    void setVoicePrintId(String employeeId, String voicePrintId);

    void linkOAuthAccount(String employeeId, String provider, String oauthUserId);

    void recordSync(String employeeId, String source, boolean success, String message);

    List<SecurityIdentity> getEmployeesNeedingSync();

    boolean hasAnyEmployee();

    boolean hasFounder();
}
