package com.livingagent.core.employee.impl;

import com.livingagent.core.database.entity.CompensationAccountEntity;
import com.livingagent.core.database.entity.CompensationPlanEntity;
import com.livingagent.core.database.entity.CompensationRecordEntity;
import com.livingagent.core.database.repository.CompensationAccountRepository;
import com.livingagent.core.database.repository.CompensationPlanRepository;
import com.livingagent.core.database.repository.CompensationRecordRepository;
import com.livingagent.core.employee.EmployeeCompensationService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Primary
public class JpaEmployeeCompensationService implements EmployeeCompensationService {

    private final CompensationPlanRepository planRepository;
    private final CompensationAccountRepository accountRepository;
    private final CompensationRecordRepository recordRepository;

    public JpaEmployeeCompensationService(CompensationPlanRepository planRepository,
                                          CompensationAccountRepository accountRepository,
                                          CompensationRecordRepository recordRepository) {
        this.planRepository = planRepository;
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    public CompensationPlan definePlan(String departmentId, String employeeType, Map<String, Object> rules) {
        CompensationPlanEntity entity = new CompensationPlanEntity();
        entity.setPlanId("plan_" + System.currentTimeMillis());
        entity.setDepartmentId(departmentId);
        entity.setEmployeeType(employeeType);
        entity.setRulesJson(rules == null ? "{}" : rules.toString());
        CompensationPlanEntity saved = planRepository.save(entity);
        return new CompensationPlan(saved.getPlanId(), saved.getDepartmentId(), saved.getEmployeeType(), rules == null ? Map.of() : Map.copyOf(rules));
    }

    @Override
    public void assignPlan(String employeeId, String planId) {
        CompensationAccountEntity account = accountRepository.findByEmployeeId(employeeId).orElseGet(CompensationAccountEntity::new);
        account.setEmployeeId(employeeId);
        account.setPlanId(planId);
        if (account.getBalance() == null) {
            account.setBalance(0);
        }
        account.setLastUpdatedAt(Instant.now());
        accountRepository.save(account);
    }

    @Override
    public void recordReward(String employeeId, int points, String reason) {
        record(employeeId, Math.max(points, 0), "REWARD", reason, null, null);
    }

    @Override
    public void recordPenalty(String employeeId, int points, String reason) {
        record(employeeId, -Math.abs(points), "PENALTY", reason, null, null);
    }

    @Override
    public int getBalance(String employeeId) {
        return accountRepository.findByEmployeeId(employeeId).map(CompensationAccountEntity::getBalance).orElse(0);
    }

    @Override
    public List<CompensationRecord> getHistory(String employeeId) {
        return recordRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Map<String, Object> summarizeDepartment(String departmentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<CompensationPlanEntity> plans = planRepository.findByDepartmentId(departmentId);
        payload.put("departmentId", departmentId);
        payload.put("plans", plans.size());
        payload.put("employees", plans.stream()
                .map(CompensationPlanEntity::getPlanId)
                .flatMap(planId -> accountRepository.findByPlanId(planId).stream())
                .count());
        payload.put("totalBalance", accountRepository.findAll().stream().mapToInt(a -> a.getBalance() == null ? 0 : a.getBalance()).sum());
        return payload;
    }

    public void record(String employeeId, int points, String type, String reason, String sourceTaskId, String sourceReviewId) {
        CompensationAccountEntity account = accountRepository.findByEmployeeId(employeeId).orElseGet(() -> {
            CompensationAccountEntity created = new CompensationAccountEntity();
            created.setEmployeeId(employeeId);
            created.setBalance(0);
            return created;
        });
        account.setBalance((account.getBalance() == null ? 0 : account.getBalance()) + points);
        account.setLastUpdatedAt(Instant.now());
        accountRepository.save(account);

        CompensationRecordEntity record = new CompensationRecordEntity();
        record.setRecordId("rec_" + System.currentTimeMillis() + "_" + UUID.randomUUID());
        record.setEmployeeId(employeeId);
        record.setPoints(points);
        record.setType(type);
        record.setReason(reason);
        record.setSourceTaskId(sourceTaskId);
        record.setSourceReviewId(sourceReviewId);
        record.setCreatedAt(Instant.now());
        recordRepository.save(record);
    }

    private void record(String employeeId, int points, String type, String reason) {
        record(employeeId, points, type, reason, null, null);
    }

    private CompensationRecord toDomain(CompensationRecordEntity entity) {
        return new CompensationRecord(
                entity.getRecordId(),
                entity.getEmployeeId(),
                entity.getPoints() == null ? 0 : entity.getPoints(),
                entity.getType(),
                entity.getReason(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toEpochMilli() : System.currentTimeMillis()
        );
    }
}
