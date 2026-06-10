package com.livingagent.core.employee.impl;

import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.employee.entity.DigitalEmployeeEntity;
import com.livingagent.core.employee.entity.EmployeeEntity;
import com.livingagent.core.employee.entity.HumanEmployeeEntity;
import com.livingagent.core.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 将 employees 表（V1 单表继承）的数据迁移到 enterprise_employees 表（V4 扁平表）。
 * 仅迁移 enterprise_employees 中不存在的记录，幂等安全。
 */
@Service
public class EmployeeEntityMigrationService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEntityMigrationService.class);

    private final EmployeeRepository employeeRepository;
    private final EnterpriseEmployeeRepository enterpriseEmployeeRepository;

    public EmployeeEntityMigrationService(EmployeeRepository employeeRepository,
                                           EnterpriseEmployeeRepository enterpriseEmployeeRepository) {
        this.employeeRepository = employeeRepository;
        this.enterpriseEmployeeRepository = enterpriseEmployeeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateOnStartup() {
        List<EmployeeEntity> sourceEmployees = employeeRepository.findAll();
        if (sourceEmployees.isEmpty()) {
            log.info("No employees in V1 table, skipping migration");
            return;
        }

        int migrated = 0;
        int skipped = 0;

        for (EmployeeEntity source : sourceEmployees) {
            if (enterpriseEmployeeRepository.existsById(source.getId())) {
                skipped++;
                continue;
            }

            EnterpriseEmployeeEntity target = new EnterpriseEmployeeEntity();
            target.setEmployeeId(source.getId());
            target.setName(source.getName());
            target.setDepartmentId(source.getDepartmentId());
            target.setDepartmentName(source.getDepartment());
            target.setPosition(source.getPosition());
            target.setHireDate(source.getHireDate());
            target.setMetadata(source.getMetadata());
            target.setCreatedAt(source.getCreatedAt() != null ? source.getCreatedAt() : Instant.now());
            target.setUpdatedAt(source.getUpdatedAt() != null ? source.getUpdatedAt() : Instant.now());

            if (source instanceof DigitalEmployeeEntity digital) {
                target.setEmployeeType("DIGITAL");
                target.setStatus(source.getStatus() != null ? source.getStatus() : "ACTIVE");
                target.setActive("ACTIVE".equals(source.getStatus()) || "ONLINE".equals(source.getStatus()));
                target.setIdentity("digital_employee");
                target.setAccessLevel("DEPARTMENT");
                target.setModel(digital.getModel());
                target.setBrainDomain(digital.getBrainDomain());
                target.setMaxConcurrentTasks(digital.getMaxConcurrentTasks());
                target.setSkills(digital.getSkills());
                target.setCapabilities(digital.getCapabilities());
                target.setOrigin(digital.getOrigin());
            } else if (source instanceof HumanEmployeeEntity human) {
                target.setEmployeeType("HUMAN");
                target.setStatus(source.getStatus() != null ? source.getStatus() : "ACTIVE");
                target.setActive("ACTIVE".equals(source.getStatus()) || "ONLINE".equals(source.getStatus()));
                target.setIdentity("human_employee");
                target.setPhone(human.getPhone());
                target.setEmail(human.getEmail());
                target.setAccessLevel(inferAccessLevel(human.getRole()));
                target.setFounder("CHAIRMAN".equals(human.getRole()));
            } else {
                target.setEmployeeType("UNKNOWN");
                target.setStatus(source.getStatus() != null ? source.getStatus() : "ACTIVE");
                target.setActive(true);
                target.setIdentity("unknown");
            }

            enterpriseEmployeeRepository.save(target);
            migrated++;
            log.info("Migrated employee: {} (type={})", source.getId(), target.getEmployeeType());
        }

        if (migrated > 0 || skipped > 0) {
            log.info("Employee migration completed: migrated={}, skipped={}, total={}", migrated, skipped, sourceEmployees.size());
        }
    }

    private String inferAccessLevel(String role) {
        if (role == null) return "CHAT_ONLY";
        if ("CHAIRMAN".equalsIgnoreCase(role)) return "FULL";
        if ("MANAGER".equalsIgnoreCase(role)) return "DEPARTMENT";
        return "DEPARTMENT";
    }
}
