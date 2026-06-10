package com.livingagent.gateway.service.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.database.entity.DepartmentEntity;
import com.livingagent.core.database.repository.DepartmentRepository;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeService.MemberSummary;
import com.livingagent.core.model.pool.BrainModelAssigner;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.session.SessionEntity;
import com.livingagent.core.security.session.SessionRepository;
import com.livingagent.gateway.service.OrganizationQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrganizationQueryServiceImpl implements OrganizationQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationQueryServiceImpl.class);

    private final DepartmentRepository departmentRepository;
    private final EmployeeService employeeService;
    private final BrainRegistry brainRegistry;
    private final BrainModelAssigner brainModelAssigner;
    private final SessionRepository sessionRepository;

    public OrganizationQueryServiceImpl(
            DepartmentRepository departmentRepository,
            EmployeeService employeeService,
            BrainRegistry brainRegistry,
            BrainModelAssigner brainModelAssigner,
            SessionRepository sessionRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeService = employeeService;
        this.brainRegistry = brainRegistry;
        this.brainModelAssigner = brainModelAssigner;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Optional<DepartmentSummary> getDepartmentByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        return departmentRepository.findByCode(code.toLowerCase())
            .map(this::toDepartmentSummary);
    }

    @Override
    public Optional<DepartmentSummary> getDepartmentById(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return Optional.empty();
        }

        return departmentRepository.findById(departmentId)
            .map(this::toDepartmentSummary);
    }

    @Override
    public List<DepartmentSummary> getAllDepartments() {
        return departmentRepository.findAll().stream()
            .map(this::toDepartmentSummary)
            .collect(Collectors.toList());
    }

    @Override
    public List<DepartmentSummary> getActiveDepartments() {
        return departmentRepository.findAll().stream()
            .filter(dept -> dept.getMemberCount() > 0)
            .map(this::toDepartmentSummary)
            .collect(Collectors.toList());
    }

    @Override
    public List<MemberSummary> getDepartmentMembers(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return Collections.emptyList();
        }
        return employeeService.getDepartmentMembersByCode(departmentCode);
    }

    @Override
    public List<MemberSummary> getActiveDepartmentMembers(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return Collections.emptyList();
        }
        return employeeService.getDepartmentMembersByCode(departmentCode);
    }

    @Override
    public Optional<MemberSummary> getEmployeeById(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }
        return employeeService.getMemberSummary(employeeId);
    }

    @Override
    public Optional<MemberSummary> getEmployeeByAuth(String authProvider, String authId) {
        if (authProvider == null || authId == null) {
            return Optional.empty();
        }
        return employeeService.findByAuthId(authProvider, authId)
            .flatMap(emp -> employeeService.getMemberSummary(emp.getEmployeeId()));
    }

    @Override
    public List<BrainSummary> getDepartmentBrains(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return Collections.emptyList();
        }

        String brainName = Department.mapDepartmentToBrain(departmentCode);
        List<BrainSummary> brains = new ArrayList<>();

        Optional<Brain> brainOpt = brainRegistry.get(brainName);
        if (brainOpt.isPresent()) {
            brains.add(toBrainSummary(brainOpt.get(), departmentCode));
        } else {
            brains.add(createFallbackBrainSummary(brainName, departmentCode));
        }

        return brains;
    }

    @Override
    public Optional<BrainSummary> getBrainByName(String brainName) {
        if (brainName == null || brainName.isBlank()) {
            return Optional.empty();
        }

        Optional<Brain> brainOpt = brainRegistry.get(brainName);
        if (brainOpt.isPresent()) {
            String departmentCode = mapBrainToDepartmentCode(brainName);
            return Optional.of(toBrainSummary(brainOpt.get(), departmentCode));
        }

        return Optional.empty();
    }

    @Override
    public Optional<DepartmentSummary> getMyDepartment(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }

        Optional<MemberSummary> empOpt = employeeService.getMemberSummary(employeeId);
        if (empOpt.isEmpty()) {
            return Optional.empty();
        }

        MemberSummary employee = empOpt.get();
        String departmentCode = employee.departmentCode();

        if (departmentCode == null || departmentCode.isBlank()) {
            return Optional.empty();
        }

        return getDepartmentByCode(departmentCode);
    }

    @Override
    public int getDepartmentMemberCount(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return 0;
        }
        return employeeService.getDepartmentMembersByCode(departmentCode).size();
    }

    @Override
    public DepartmentActivitySummary getDepartmentActivity(String departmentCode, Instant since) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return DepartmentActivitySummary.empty();
        }

        Optional<DepartmentEntity> deptOpt = departmentRepository.findByCode(departmentCode.toLowerCase());
        if (deptOpt.isEmpty()) {
            return DepartmentActivitySummary.empty();
        }

        DepartmentEntity dept = deptOpt.get();
        int totalMemberCount = dept.getMemberCount();
        List<MemberSummary> members = employeeService.getDepartmentMembersByCode(departmentCode);
        int activeMemberCount = members.size();

        int sessionCount = 0;
        Instant lastActivityAt = null;

        for (MemberSummary member : members) {
            List<SessionEntity> sessions = sessionRepository.findByEmployeeIdAndActiveTrueOrderByStartedAtDesc(member.employeeId());
            sessionCount += sessions.size();
            for (SessionEntity session : sessions) {
                if (lastActivityAt == null || (session.getLastActivityAt() != null && session.getLastActivityAt().isAfter(lastActivityAt))) {
                    lastActivityAt = session.getLastActivityAt();
                }
            }
        }

        return new DepartmentActivitySummary(
            dept.getCode(),
            dept.getName(),
            activeMemberCount,
            totalMemberCount,
            sessionCount,
            0,
            0,
            lastActivityAt
        );
    }

    @Override
    public List<SessionSummary> getRecentSessions(String departmentCode, int limit) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return Collections.emptyList();
        }

        List<MemberSummary> members = employeeService.getDepartmentMembersByCode(departmentCode);

        List<SessionSummary> sessions = new ArrayList<>();
        for (MemberSummary member : members) {
            List<SessionEntity> empSessions = sessionRepository.findByEmployeeIdAndActiveTrueOrderByStartedAtDesc(member.employeeId());
            for (SessionEntity session : empSessions) {
                sessions.add(new SessionSummary(
                    session.getSessionId(),
                    session.getEmployeeId(),
                    member.name(),
                    departmentCode,
                    session.getStartedAt(),
                    session.getLastActivityAt(),
                    session.isActive()
                ));
                if (sessions.size() >= limit) {
                    return sessions;
                }
            }
        }

        return sessions;
    }

    @Override
    public List<AuditSummary> getRecentAuditLogs(String departmentCode, int limit) {
        return Collections.emptyList();
    }

    private DepartmentSummary toDepartmentSummary(DepartmentEntity entity) {
        String brainName = entity.getTargetBrain();
        if (brainName == null || brainName.isBlank()) {
            brainName = Department.mapDepartmentToBrain(entity.getName());
        }

        boolean brainRunning = false;
        String brainId = null;
        boolean modelConfigured = false;

        Optional<Brain> brainOpt = brainRegistry.get(brainName);
        if (brainOpt.isPresent()) {
            Brain brain = brainOpt.get();
            brainRunning = brain.getState() == Brain.BrainState.RUNNING;
            brainId = brain.getId();
            
            try {
                var assignment = brainModelAssigner.getAssignment(brainId);
                modelConfigured = assignment != null && assignment.getModelId() != null;
            } catch (Exception e) {
                log.debug("Failed to check model configuration for brain {}: {}", brainId, e.getMessage());
            }
        }

        int memberCount = entity.getMemberCount();
        if (memberCount <= 0) {
            memberCount = employeeService.getDepartmentMembersByCode(entity.getCode()).size();
        }

        return new DepartmentSummary(
            entity.getDepartmentId(),
            entity.getCode(),
            entity.getName(),
            brainName,
            brainId,
            brainRunning,
            modelConfigured,
            memberCount,
            null,
            entity.getManagerId(),
            entity.getManagerName()
        );
    }

    private BrainSummary toBrainSummary(Brain brain, String departmentCode) {
        String displayName = Department.mapBrainToDepartment(brain.getName()) + "大脑";
        boolean running = brain.getState() == Brain.BrainState.RUNNING;
        
        boolean modelConfigured = false;
        try {
            var assignment = brainModelAssigner.getAssignment(brain.getId());
            modelConfigured = assignment != null && assignment.getModelId() != null;
        } catch (Exception e) {
            log.debug("Failed to check model configuration for brain {}: {}", brain.getId(), e.getMessage());
        }

        return new BrainSummary(
            brain.getName(),
            displayName,
            brain.getId(),
            Department.mapBrainToDepartment(brain.getName()),
            departmentCode,
            true,
            running,
            brain.getState().name(),
            modelConfigured ? "yes" : "no"
        );
    }

    private BrainSummary createFallbackBrainSummary(String brainName, String departmentCode) {
        String displayName = Department.mapBrainToDepartment(brainName) + "大脑";

        return new BrainSummary(
            brainName,
            displayName,
            null,
            Department.mapBrainToDepartment(brainName),
            departmentCode,
            false,
            false,
            Brain.BrainState.STOPPED.name(),
            null
        );
    }

    private String mapBrainToDepartmentCode(String brainName) {
        if (brainName == null) {
            return null;
        }

        return switch (brainName) {
            case "TechBrain" -> "tech";
            case "HrBrain" -> "hr";
            case "FinanceBrain" -> "finance";
            case "SalesBrain" -> "sales";
            case "CsBrain" -> "cs";
            case "AdminBrain" -> "admin";
            case "LegalBrain" -> "legal";
            case "OpsBrain" -> "ops";
            case "MainBrain" -> "main";
            default -> brainName.toLowerCase().replace("Brain", "");
        };
    }
}
