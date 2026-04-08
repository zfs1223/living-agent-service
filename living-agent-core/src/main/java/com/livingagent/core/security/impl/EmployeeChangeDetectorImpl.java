package com.livingagent.core.security.impl;

import com.livingagent.core.security.EmployeeChangeDetector;
import com.livingagent.core.security.EmployeeService;
import com.livingagent.core.security.EmployeeService.ChangeType;
import com.livingagent.core.security.DetectedChange;
import com.livingagent.core.security.ChangeStatus;
import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmployeeChangeDetectorImpl implements EmployeeChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(EmployeeChangeDetectorImpl.class);

    private final EmployeeService employeeService;
    private final Map<String, DetectedChange> pendingChanges = new ConcurrentHashMap<>();

    private static final Pattern RESIGN_PATTERN = Pattern.compile(
            "(离职|辞职|离开|走了|不干了|quit|resign|leave)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "(入职|新来|新员工|刚来|加入|joined|new employee|onboard)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "(调到|调动|转岗|调去|调往|transfer|moved to)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile(
            "(技术部|人力资源|财务部|销售部|客服部|行政部|法务部|运营部|" +
            "tech|hr|finance|sales|cs|admin|legal|ops)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,4})\\s*(离职|入职|调动|调到)"
    );

    public EmployeeChangeDetectorImpl(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    public Optional<DetectedChange> detectFromConversation(String conversationId, String message, String speakerId) {
        log.debug("Detecting employee change from conversation: {}", conversationId);
        
        if (message == null || message.trim().isEmpty()) {
            return Optional.empty();
        }

        DetectedChange change = null;

        Matcher nameMatcher = NAME_PATTERN.matcher(message);
        if (nameMatcher.find()) {
            String employeeName = nameMatcher.group(1);
            String action = nameMatcher.group(2);
            
            change = detectByName(employeeName, action, message, conversationId);
        }

        if (change == null && RESIGN_PATTERN.matcher(message).find()) {
            change = detectResignFromContext(message, conversationId);
        }

        if (change == null && JOIN_PATTERN.matcher(message).find()) {
            change = detectJoinFromContext(message, conversationId);
        }

        if (change == null && TRANSFER_PATTERN.matcher(message).find()) {
            change = detectTransferFromContext(message, conversationId);
        }

        if (change != null) {
            pendingChanges.put(change.getChangeId(), change);
            log.info("Detected potential employee change: {}", change);
            return Optional.of(change);
        }

        return Optional.empty();
    }

    private DetectedChange detectByName(String name, String action, String message, String source) {
        List<Employee> employees = employeeService.findAll();
        Employee employee = employees.stream()
                .filter(e -> name.equals(e.getName()))
                .findFirst()
                .orElse(null);

        if (employee == null) {
            log.debug("Employee not found by name: {}", name);
            return null;
        }

        DetectedChange change = new DetectedChange();
        change.setEmployeeId(employee.getEmployeeId());
        change.setEmployeeName(name);
        change.setDetectedFrom(source);
        change.setDetails(message);

        switch (action) {
            case "离职" -> {
                change.setChangeType(ChangeType.RESIGN);
                change.setConfidence(0.85);
            }
            case "入职" -> {
                change.setChangeType(ChangeType.JOIN);
                change.setConfidence(0.85);
            }
            case "调动", "调到" -> {
                change.setChangeType(ChangeType.TRANSFER);
                change.setConfidence(0.80);
                extractTargetDepartment(message).ifPresent(change::setNewValue);
            }
            default -> {
                change.setChangeType(ChangeType.STATUS_CHANGE);
                change.setConfidence(0.70);
            }
        }

        return change;
    }

    private DetectedChange detectResignFromContext(String message, String source) {
        DetectedChange change = new DetectedChange();
        change.setChangeType(ChangeType.RESIGN);
        change.setDetectedFrom(source);
        change.setDetails(message);
        change.setConfidence(0.65);
        
        Matcher nameMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})").matcher(message);
        if (nameMatcher.find()) {
            String potentialName = nameMatcher.group(1);
            employeeService.findAll().stream()
                    .filter(e -> potentialName.equals(e.getName()))
                    .findFirst()
                    .ifPresent(e -> {
                        change.setEmployeeId(e.getEmployeeId());
                        change.setEmployeeName(e.getName());
                        change.setConfidence(0.75);
                    });
        }
        
        return change;
    }

    private DetectedChange detectJoinFromContext(String message, String source) {
        DetectedChange change = new DetectedChange();
        change.setChangeType(ChangeType.JOIN);
        change.setDetectedFrom(source);
        change.setDetails(message);
        change.setConfidence(0.65);
        
        Matcher nameMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})").matcher(message);
        if (nameMatcher.find()) {
            String potentialName = nameMatcher.group(1);
            change.setEmployeeName(potentialName);
            change.setConfidence(0.70);
        }
        
        return change;
    }

    private DetectedChange detectTransferFromContext(String message, String source) {
        DetectedChange change = new DetectedChange();
        change.setChangeType(ChangeType.TRANSFER);
        change.setDetectedFrom(source);
        change.setDetails(message);
        change.setConfidence(0.65);
        
        Optional<String> targetDept = extractTargetDepartment(message);
        targetDept.ifPresent(change::setNewValue);
        
        Matcher nameMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})").matcher(message);
        if (nameMatcher.find()) {
            String potentialName = nameMatcher.group(1);
            employeeService.findAll().stream()
                    .filter(e -> potentialName.equals(e.getName()))
                    .findFirst()
                    .ifPresent(e -> {
                        change.setEmployeeId(e.getEmployeeId());
                        change.setEmployeeName(e.getName());
                        change.setOriginalValue(e.getDepartment());
                        change.setConfidence(0.75);
                    });
        }
        
        return change;
    }

    private Optional<String> extractTargetDepartment(String message) {
        Matcher deptMatcher = DEPARTMENT_PATTERN.matcher(message);
        if (deptMatcher.find()) {
            return Optional.of(deptMatcher.group(1));
        }
        return Optional.empty();
    }

    @Override
    public DetectedChange createChange(String employeeId, ChangeType changeType, 
                                        String detectedFrom, String details) {
        DetectedChange change = new DetectedChange();
        change.setEmployeeId(employeeId);
        change.setChangeType(changeType);
        change.setDetectedFrom(detectedFrom);
        change.setDetails(details);
        change.setConfidence(1.0);
        
        employeeService.findById(employeeId).ifPresent(e -> {
            change.setEmployeeName(e.getName());
            change.setOriginalValue(e.getDepartment());
        });
        
        pendingChanges.put(change.getChangeId(), change);
        log.info("Created employee change: {}", change);
        return change;
    }

    @Override
    public void handleChange(DetectedChange change) {
        if (change == null || change.getEmployeeId() == null) {
            log.warn("Cannot handle change: invalid change object");
            return;
        }

        log.info("Handling employee change: {}", change);

        switch (change.getChangeType()) {
            case RESIGN -> handleResign(change);
            case JOIN -> handleJoin(change);
            case TRANSFER, DEPARTMENT_CHANGE -> handleTransfer(change);
            case STATUS_CHANGE -> handleStatusChange(change);
        }

        change.setStatus(ChangeStatus.APPLIED);
        pendingChanges.put(change.getChangeId(), change);
    }

    private void handleResign(DetectedChange change) {
        employeeService.updateEmployeeStatus(change.getEmployeeId(), 
                UserIdentity.INTERNAL_DEPARTED);
        log.info("Processed resignation for employee: {}", change.getEmployeeId());
    }

    private void handleJoin(DetectedChange change) {
        employeeService.findById(change.getEmployeeId()).ifPresentOrElse(
                employee -> {
                    employee.setIdentity(UserIdentity.INTERNAL_PROBATION);
                    employeeService.updateEmployee(employee);
                    log.info("Processed join for existing employee: {}", change.getEmployeeId());
                },
                () -> {
                    Employee newEmployee = new Employee();
                    newEmployee.setName(change.getEmployeeName());
                    newEmployee.setIdentity(UserIdentity.INTERNAL_PROBATION);
                    employeeService.createEmployee(newEmployee);
                    log.info("Created new employee from join detection: {}", change.getEmployeeName());
                }
        );
    }

    private void handleTransfer(DetectedChange change) {
        employeeService.findById(change.getEmployeeId()).ifPresent(employee -> {
            String oldDept = employee.getDepartment();
            String newDept = change.getNewValue();
            
            employee.setDepartment(newDept);
            employeeService.updateEmployee(employee);
            
            log.info("Processed transfer for employee {}: {} -> {}", 
                    change.getEmployeeId(), oldDept, newDept);
        });
    }

    private void handleStatusChange(DetectedChange change) {
        log.info("Status change recorded for employee {}: {}", 
                change.getEmployeeId(), change.getDetails());
    }

    @Override
    public boolean confirmChange(String changeId, String confirmedBy) {
        DetectedChange change = pendingChanges.get(changeId);
        if (change == null) {
            log.warn("Change not found: {}", changeId);
            return false;
        }

        change.setStatus(ChangeStatus.CONFIRMED);
        change.setConfirmedBy(confirmedBy);
        change.setConfirmedAt(System.currentTimeMillis());
        
        handleChange(change);
        
        log.info("Change confirmed and applied: {} by {}", changeId, confirmedBy);
        return true;
    }

    @Override
    public boolean rejectChange(String changeId, String rejectedBy, String reason) {
        DetectedChange change = pendingChanges.get(changeId);
        if (change == null) {
            log.warn("Change not found: {}", changeId);
            return false;
        }

        change.setStatus(ChangeStatus.REJECTED);
        change.setConfirmedBy(rejectedBy);
        change.setConfirmedAt(System.currentTimeMillis());
        change.setDetails(change.getDetails() + " [Rejected: " + reason + "]");
        
        log.info("Change rejected: {} by {} - {}", changeId, rejectedBy, reason);
        return true;
    }

    public List<DetectedChange> getPendingChanges() {
        return pendingChanges.values().stream()
                .filter(c -> c.getStatus() == ChangeStatus.PENDING)
                .toList();
    }

    public Optional<DetectedChange> getChange(String changeId) {
        return Optional.ofNullable(pendingChanges.get(changeId));
    }

    public void clearAppliedChanges() {
        pendingChanges.entrySet().removeIf(e -> 
                e.getValue().getStatus() == ChangeStatus.APPLIED ||
                e.getValue().getStatus() == ChangeStatus.REJECTED);
    }
}
