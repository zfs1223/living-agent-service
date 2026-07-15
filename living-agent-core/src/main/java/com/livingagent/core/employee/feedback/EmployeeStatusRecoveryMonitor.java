package com.livingagent.core.employee.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmployeeStatusRecoveryMonitor {

    private static final Logger log = LoggerFactory.getLogger(EmployeeStatusRecoveryMonitor.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, AbnormalStatusRecord> abnormalEmployees = new ConcurrentHashMap<>();
    private volatile double abnormalRateThreshold = 0.20;

    public EmployeeStatusRecoveryMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordAbnormalStatus(String employeeId, String status, String reason) {
        abnormalEmployees.compute(employeeId, (k, v) ->
            v == null ? new AbnormalStatusRecord(status, reason, 1)
                      : v.withIncrement());
        log.info("[闭环11-A] 员工异常状态: id={}, status={}, reason={}", employeeId, status, reason);
    }

    public void recordRecovery(String employeeId) {
        abnormalEmployees.remove(employeeId);
        log.info("[闭环11-A] 员工状态恢复: id={}", employeeId);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateEmployeeHealth() {
        Set<Map.Entry<String, AbnormalStatusRecord>> entries = abnormalEmployees.entrySet();

        for (Map.Entry<String, AbnormalStatusRecord> entry : entries) {
            String employeeId = entry.getKey();
            AbnormalStatusRecord record = entry.getValue();

            if (record.occurrences > 3) {
                log.warn("[闭环11-A] 员工{}异常{}次，触发自愈", employeeId, record.occurrences);
                eventBus.publish(11, "employee_status_abnormal",
                    CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("employeeId", employeeId,
                        "status", record.status,
                        "occurrences", record.occurrences,
                        "action", "auto_recover"));
            }
        }

        if (!abnormalEmployees.isEmpty()) {
            eventBus.publish(11, "employee_health_check",
                CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("abnormalCount", abnormalEmployees.size(),
                    "action", "schedule_recovery"));
        }
    }

    public double getAbnormalRateThreshold() {
        return abnormalRateThreshold;
    }

    private record AbnormalStatusRecord(String status, String reason, int occurrences) {
        AbnormalStatusRecord withIncrement() {
            return new AbnormalStatusRecord(status, reason, occurrences + 1);
        }
    }
}
