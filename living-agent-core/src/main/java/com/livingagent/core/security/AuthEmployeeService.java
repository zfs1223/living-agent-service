package com.livingagent.core.security;

import java.util.List;
import java.util.Optional;

public interface AuthEmployeeService {

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

    void handleAiDetectedChange(String employeeId, ChangeType changeType, String detectedFrom, String details);

    void setVoicePrintId(String employeeId, String voicePrintId);

    void linkOAuthAccount(String employeeId, String provider, String oauthUserId);

    void recordSync(String employeeId, String source, boolean success, String message);

    List<SecurityIdentity> getEmployeesNeedingSync();

    boolean hasAnyEmployee();

    boolean hasFounder();

    enum ChangeType {
        RESIGN("离职"),
        JOIN("入职"),
        TRANSFER("调动"),
        STATUS_CHANGE("状态变更"),
        DEPARTMENT_CHANGE("部门变更");

        private final String description;

        ChangeType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }
}
