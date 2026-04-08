package com.livingagent.core.security.impl;

import com.livingagent.core.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EmployeeServiceImpl implements EmployeeService, EmployeeAuthService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final Map<String, Employee> employeeStore = new ConcurrentHashMap<>();
    private final Map<String, String> phoneIndex = new ConcurrentHashMap<>();
    private final Map<String, String> emailIndex = new ConcurrentHashMap<>();
    private final Map<String, String> voicePrintIndex = new ConcurrentHashMap<>();
    private final Map<String, String> oauthIndex = new ConcurrentHashMap<>();

    @Override
    public Employee createEmployee(Employee employee) {
        if (employee.getEmployeeId() == null || employee.getEmployeeId().isEmpty()) {
            employee.setEmployeeId("emp_" + System.currentTimeMillis());
        }

        String id = employee.getEmployeeId();
        employeeStore.put(id, employee);

        updateIndexes(employee);

        log.info("Created employee: {}", employee);
        return employee;
    }

    @Override
    public Employee updateEmployee(Employee employee) {
        String id = employee.getEmployeeId();
        if (!employeeStore.containsKey(id)) {
            throw new IllegalArgumentException("Employee not found: " + id);
        }

        Employee oldEmployee = employeeStore.get(id);
        cleanIndexes(oldEmployee);

        employeeStore.put(id, employee);
        updateIndexes(employee);

        log.info("Updated employee: {}", employee);
        return employee;
    }

    @Override
    public void deleteEmployee(String employeeId) {
        Employee employee = employeeStore.remove(employeeId);
        if (employee != null) {
            cleanIndexes(employee);
            log.info("Deleted employee: {}", employeeId);
        }
    }

    @Override
    public Optional<Employee> findById(String employeeId) {
        return Optional.ofNullable(employeeStore.get(employeeId));
    }

    @Override
    public Optional<Employee> findByPhone(String phone) {
        String employeeId = phoneIndex.get(phone);
        if (employeeId == null) {
            return Optional.empty();
        }
        return findById(employeeId);
    }

    @Override
    public Optional<Employee> findByEmail(String email) {
        String employeeId = emailIndex.get(email.toLowerCase());
        if (employeeId == null) {
            return Optional.empty();
        }
        return findById(employeeId);
    }

    @Override
    public Optional<Employee> findByVoicePrintId(String voicePrintId) {
        String employeeId = voicePrintIndex.get(voicePrintId);
        if (employeeId == null) {
            return Optional.empty();
        }
        return findById(employeeId);
    }

    @Override
    public Optional<Employee> findByOAuth(String provider, String oauthUserId) {
        String key = provider + ":" + oauthUserId;
        String employeeId = oauthIndex.get(key);
        if (employeeId == null) {
            return Optional.empty();
        }
        return findById(employeeId);
    }

    @Override
    public List<Employee> findByDepartment(String department) {
        return employeeStore.values().stream()
            .filter(e -> department.equals(e.getDepartment()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> findByIdentity(UserIdentity identity) {
        return employeeStore.values().stream()
            .filter(e -> e.getIdentity() == identity)
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> findAllActive() {
        return employeeStore.values().stream()
            .filter(Employee::isActive)
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> findAll() {
        return new ArrayList<>(employeeStore.values());
    }

    @Override
    public int importFromExcel(byte[] excelData) {
        log.info("Importing employees from Excel, size: {} bytes", excelData.length);
        return 0;
    }

    @Override
    public int importFromCsv(byte[] csvData) {
        log.info("Importing employees from CSV, size: {} bytes", csvData.length);
        return 0;
    }

    @Override
    public int importFromHrSystem(String hrSystemType) {
        log.info("Importing employees from HR system: {}", hrSystemType);
        return 0;
    }

    @Override
    public int syncFromDingTalk() {
        log.info("Syncing employees from DingTalk");
        return 0;
    }

    @Override
    public int syncFromFeishu() {
        log.info("Syncing employees from Feishu");
        return 0;
    }

    @Override
    public int syncFromWeCom() {
        log.info("Syncing employees from WeCom");
        return 0;
    }

    @Override
    public void updateEmployeeStatus(String employeeId, UserIdentity newIdentity) {
        findById(employeeId).ifPresent(employee -> {
            UserIdentity oldIdentity = employee.getIdentity();
            employee.updateIdentity(newIdentity);
            employee.setLastSyncTime(Instant.now());
            updateEmployee(employee);
            log.info("Updated employee {} status: {} -> {}", employeeId, oldIdentity, newIdentity);
        });
    }

    @Override
    public void handleAiDetectedChange(String employeeId, EmployeeService.ChangeType changeType, String detectedFrom, String details) {
        findById(employeeId).ifPresent(employee -> {
            log.info("AI detected change for employee {}: {} from {}", employeeId, changeType, detectedFrom);

            switch (changeType) {
                case RESIGN:
                    updateEmployeeStatus(employeeId, UserIdentity.INTERNAL_DEPARTED);
                    break;
                case JOIN:
                    employee.setIdentity(UserIdentity.INTERNAL_PROBATION);
                    employee.setJoinDate(Instant.now());
                    break;
                case TRANSFER:
                case DEPARTMENT_CHANGE:
                    employee.getMetadata().put("pending_department_change", details);
                    employee.getMetadata().put("change_detected_from", detectedFrom);
                    break;
                case STATUS_CHANGE:
                    employee.getMetadata().put("pending_status_change", details);
                    break;
            }

            employee.getMetadata().put("last_ai_detected_change", Instant.now().toString());
            employee.getMetadata().put("change_type", changeType.name());
            updateEmployee(employee);
        });
    }

    @Override
    public void setVoicePrintId(String employeeId, String voicePrintId) {
        findById(employeeId).ifPresent(employee -> {
            String oldVoicePrintId = employee.getVoicePrintId();
            if (oldVoicePrintId != null) {
                voicePrintIndex.remove(oldVoicePrintId);
            }
            employee.setVoicePrintId(voicePrintId);
            if (voicePrintId != null) {
                voicePrintIndex.put(voicePrintId, employeeId);
            }
            updateEmployee(employee);
            log.info("Set voice print ID for employee {}: {}", employeeId, voicePrintId);
        });
    }

    @Override
    public void linkOAuthAccount(String employeeId, String provider, String oauthUserId) {
        findById(employeeId).ifPresent(employee -> {
            employee.setOauthProvider(provider);
            employee.setOauthUserId(oauthUserId);
            String key = provider + ":" + oauthUserId;
            oauthIndex.put(key, employeeId);
            updateEmployee(employee);
            log.info("Linked OAuth account for employee {}: {} - {}", employeeId, provider, oauthUserId);
        });
    }

    @Override
    public void recordSync(String employeeId, String source, boolean success, String message) {
        findById(employeeId).ifPresent(employee -> {
            employee.setLastSyncTime(Instant.now());
            employee.setSyncSource(source);
            employee.getMetadata().put("last_sync_success", success);
            employee.getMetadata().put("last_sync_message", message);
            updateEmployee(employee);
        });
    }

    @Override
    public List<Employee> getEmployeesNeedingSync() {
        return employeeStore.values().stream()
            .filter(e -> e.getLastSyncTime() == null || 
                   e.getLastSyncTime().isBefore(Instant.now().minusSeconds(86400)))
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasAnyEmployee() {
        return !employeeStore.isEmpty();
    }

    @Override
    public boolean hasFounder() {
        return employeeStore.values().stream().anyMatch(Employee::isFounder);
    }

    private void updateIndexes(Employee employee) {
        String id = employee.getEmployeeId();

        if (employee.getPhone() != null) {
            phoneIndex.put(employee.getPhone(), id);
        }
        if (employee.getEmail() != null) {
            emailIndex.put(employee.getEmail().toLowerCase(), id);
        }
        if (employee.getVoicePrintId() != null) {
            voicePrintIndex.put(employee.getVoicePrintId(), id);
        }
        if (employee.getOauthProvider() != null && employee.getOauthUserId() != null) {
            String key = employee.getOauthProvider() + ":" + employee.getOauthUserId();
            oauthIndex.put(key, id);
        }
    }

    private void cleanIndexes(Employee employee) {
        if (employee.getPhone() != null) {
            phoneIndex.remove(employee.getPhone());
        }
        if (employee.getEmail() != null) {
            emailIndex.remove(employee.getEmail().toLowerCase());
        }
        if (employee.getVoicePrintId() != null) {
            voicePrintIndex.remove(employee.getVoicePrintId());
        }
        if (employee.getOauthProvider() != null && employee.getOauthUserId() != null) {
            String key = employee.getOauthProvider() + ":" + employee.getOauthUserId();
            oauthIndex.remove(key);
        }
    }
}
