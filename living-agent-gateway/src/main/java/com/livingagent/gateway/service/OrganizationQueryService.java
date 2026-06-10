package com.livingagent.gateway.service;

import com.livingagent.core.employee.EmployeeService.MemberSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizationQueryService {

    Optional<DepartmentSummary> getDepartmentByCode(String code);

    Optional<DepartmentSummary> getDepartmentById(String departmentId);

    List<DepartmentSummary> getAllDepartments();

    List<DepartmentSummary> getActiveDepartments();

    List<MemberSummary> getDepartmentMembers(String departmentCode);

    List<MemberSummary> getActiveDepartmentMembers(String departmentCode);

    Optional<MemberSummary> getEmployeeById(String employeeId);

    Optional<MemberSummary> getEmployeeByAuth(String authProvider, String authId);

    List<BrainSummary> getDepartmentBrains(String departmentCode);

    Optional<BrainSummary> getBrainByName(String brainName);

    Optional<DepartmentSummary> getMyDepartment(String employeeId);

    int getDepartmentMemberCount(String departmentCode);

    DepartmentActivitySummary getDepartmentActivity(String departmentCode, Instant since);

    List<SessionSummary> getRecentSessions(String departmentCode, int limit);

    List<AuditSummary> getRecentAuditLogs(String departmentCode, int limit);

    record DepartmentSummary(
        String departmentId,
        String code,
        String name,
        String brain,
        String brainId,
        boolean brainRunning,
        boolean modelConfigured,
        int memberCount,
        String accessLevel,
        String managerId,
        String managerName
    ) {
        public static DepartmentSummary empty() {
            return new DepartmentSummary(null, null, null, null, null, false, false, 0, null, null, null);
        }
    }

    record BrainSummary(
        String name,
        String displayName,
        String brainId,
        String department,
        String departmentCode,
        boolean available,
        boolean running,
        String state,
        String modelConfigured
    ) {
        public static BrainSummary empty() {
            return new BrainSummary(null, null, null, null, null, false, false, null, null);
        }
    }

    record DepartmentActivitySummary(
        String departmentCode,
        String departmentName,
        int activeMemberCount,
        int totalMemberCount,
        int sessionCount,
        int messageCount,
        int auditEventCount,
        Instant lastActivityAt
    ) {
        public static DepartmentActivitySummary empty() {
            return new DepartmentActivitySummary(null, null, 0, 0, 0, 0, 0, null);
        }
    }

    record SessionSummary(
        String sessionId,
        String employeeId,
        String employeeName,
        String department,
        Instant startedAt,
        Instant lastActivityAt,
        boolean active
    ) {
        public static SessionSummary empty() {
            return new SessionSummary(null, null, null, null, null, null, false);
        }
    }

    record AuditSummary(
        String eventId,
        String eventType,
        String employeeId,
        String employeeName,
        String department,
        String description,
        Instant createdAt
    ) {
        public static AuditSummary empty() {
            return new AuditSummary(null, null, null, null, null, null, null);
        }
    }
}
