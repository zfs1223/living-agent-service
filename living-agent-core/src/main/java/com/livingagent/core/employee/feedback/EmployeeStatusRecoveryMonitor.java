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

/**
 * 闭环11-A: 员工状态恢复监控器。
 *
 * DBS People 工具区分（P4-2）：
 * - origin=fixed → 异常可直接触发自动自愈，无需人工干预
 * - origin=human → 异常需通知管理者，并关联闭环65（周期性汇报）
 */
@Component
public class EmployeeStatusRecoveryMonitor {

    private static final Logger log = LoggerFactory.getLogger(EmployeeStatusRecoveryMonitor.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, AbnormalStatusRecord> abnormalEmployees = new ConcurrentHashMap<>();
    private volatile double abnormalRateThreshold = 0.20;

    public EmployeeStatusRecoveryMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 记录员工异常状态。
     * @param employeeId 员工ID
     * @param status 异常状态
     * @param reason 原因
     * @param origin 员工来源类型（fixed/human/personal）
     */
    public void recordAbnormalStatus(String employeeId, String status, String reason, String origin) {
        abnormalEmployees.compute(employeeId, (k, v) ->
            v == null ? new AbnormalStatusRecord(status, reason, 1, origin != null ? origin : "fixed")
                      : v.withIncrement());
        log.info("[闭环11-A] 员工异常状态: id={}, status={}, reason={}, origin={}", employeeId, status, reason, origin);
    }

    /**
     * 兼容旧接口：默认 origin=fixed
     */
    public void recordAbnormalStatus(String employeeId, String status, String reason) {
        recordAbnormalStatus(employeeId, status, reason, "fixed");
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
                log.warn("[闭环11-A] 员工{}异常{}次(origin={}), 触发恢复流程",
                    employeeId, record.occurrences, record.origin);

                // P4-2: 根据员工 origin 类型选择不同恢复策略
                String action = switch (record.origin) {
                    case "fixed" -> "auto_recover";          // 固定数字员工：自动自愈
                    case "human" -> "notify_manager";         // 人类员工：通知管理者+关联闭环65
                    case "personal" -> "auto_recover_light"; // 个人助手：轻量级自愈
                    default -> "auto_recover";
                };

                eventBus.publish(11, "employee_status_abnormal",
                    CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("employeeId", employeeId,
                        "status", record.status,
                        "occurrences", record.occurrences,
                        "origin", record.origin,
                        "action", action));

                // 人类员工额外关联闭环65（周期性汇报）
                if ("human".equals(record.origin)) {
                    eventBus.publish(65, "human_employee_health_issue",
                        CrossLoopEvent.EventPriority.DEGRADATION,
                        Map.of("employeeId", employeeId,
                            "status", record.status,
                            "reason", record.reason));
                }
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

    private record AbnormalStatusRecord(String status, String reason, int occurrences, String origin) {
        AbnormalStatusRecord withIncrement() {
            return new AbnormalStatusRecord(status, reason, occurrences + 1, origin);
        }
    }
}
