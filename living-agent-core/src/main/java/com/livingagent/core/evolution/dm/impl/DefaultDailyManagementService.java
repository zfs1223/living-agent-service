package com.livingagent.core.evolution.dm.impl;

import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import com.livingagent.core.evolution.dm.DailyManagementService;
import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * P11: 日常管理服务默认实现。
 *
 * 功能：
 * - P11-1: 部门级看板数据聚合
 * - P11-2: 异常标红检测
 * - P11-3: 每日数字站会
 */
@Service
public class DefaultDailyManagementService implements DailyManagementService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDailyManagementService.class);
    private static final List<String> DEPARTMENTS = List.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops", "core"
    );

    private final HealthMonitor healthMonitor;
    private final CrossLoopEventBus eventBus;

    /** 看板缓存 */
    private final Map<String, DepartmentDashboard> dashboardCache = new ConcurrentHashMap<>();

    public DefaultDailyManagementService(HealthMonitor healthMonitor, CrossLoopEventBus eventBus) {
        this.healthMonitor = healthMonitor;
        this.eventBus = eventBus;
    }

    @Override
    public DepartmentDashboard getDashboard(String department) {
        return dashboardCache.computeIfAbsent(department, this::buildDashboard);
    }

    @Override
    public Map<String, DepartmentDashboard> getAllDashboards() {
        return DEPARTMENTS.stream()
            .collect(Collectors.toMap(d -> d, this::getDashboard));
    }

    @Override
    public List<AnomalyItem> getAnomalies(String department) {
        DepartmentDashboard dashboard = getDashboard(department);
        return dashboard.anomalies();
    }

    @Override
    public StandupSummary generateStandupSummary() {
        List<StandupItem> items = new ArrayList<>();
        int anomalyCount = 0;
        int kaizenCount = 0;

        for (String dept : DEPARTMENTS) {
            DepartmentDashboard dashboard = getDashboard(dept);

            // 异常项优先
            for (AnomalyItem anomaly : dashboard.anomalies()) {
                items.add(new StandupItem(dept, "anomaly",
                    anomaly.description(), anomaly.level(),
                    anomaly.requiresAction() ? "需要立即处理" : "观察中"));
                anomalyCount++;
            }

            // 关键指标
            if (dashboard.taskCompletionRate() < 0.5) {
                items.add(new StandupItem(dept, "metric",
                    String.format("任务完成率 %.0f%% 低于目标", dashboard.taskCompletionRate() * 100),
                    AnomalyLevel.HIGH, "需分析根因"));
            }

            // 改善事件
            if (dashboard.activeKaizenEvents() > 0) {
                items.add(new StandupItem(dept, "kaizen",
                    String.format("%d 个活跃改善事件", dashboard.activeKaizenEvents()),
                    AnomalyLevel.NORMAL, "持续推进"));
                kaizenCount++;
            }
        }

        // 限制站会摘要为5项关键指标，异常优先
        items.sort(Comparator.comparingInt(i -> switch (i.severity()) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case NORMAL -> 3;
        }));

        List<StandupItem> topItems = items.stream().limit(5).toList();

        log.info("[P11/DM] 数字站会摘要: anomalies={}, kaizen={}, topItems={}",
            anomalyCount, kaizenCount, topItems.size());

        return new StandupSummary(Instant.now(), DEPARTMENTS, topItems, anomalyCount, kaizenCount);
    }

    /**
     * P11-2: 每10分钟更新看板数据并检测异常。
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void refreshDashboards() {
        for (String dept : DEPARTMENTS) {
            try {
                DepartmentDashboard dashboard = buildDashboard(dept);
                dashboardCache.put(dept, dashboard);

                // 检查是否有CRITICAL异常需要发布事件
                for (AnomalyItem anomaly : dashboard.anomalies()) {
                    if (anomaly.level() == AnomalyLevel.CRITICAL && eventBus != null) {
                        eventBus.publish(11, "dm_anomaly_critical",
                            CrossLoopEvent.EventPriority.SELF_HEALING,
                            Map.of("department", dept,
                                "component", anomaly.component(),
                                "description", anomaly.description(),
                                "relatedLoop", anomaly.relatedLoopId()));
                    }
                }
            } catch (Exception e) {
                log.warn("[P11/DM] 看板刷新失败: dept={}, error={}", dept, e.getMessage());
            }
        }
    }

    /**
     * P11-3: 每日数字站会（每24小时）。
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000, initialDelay = 12 * 60 * 60 * 1000)
    public void dailyStandup() {
        StandupSummary summary = generateStandupSummary();

        if (eventBus != null) {
            eventBus.publish(11, "dm_daily_standup",
                CrossLoopEvent.EventPriority.KNOWLEDGE,
                Map.of("anomalyCount", summary.anomalyCount(),
                    "kaizenCount", summary.kaizenCount(),
                    "topItems", summary.items().stream()
                        .map(i -> i.department() + ":" + i.type() + ":" + i.description())
                        .collect(Collectors.joining("; "))));
        }

        log.info("[P11/DM] 数字站会: anomalies={}, kaizen={}, items={}",
            summary.anomalyCount(), summary.kaizenCount(), summary.items().size());
    }

    private DepartmentDashboard buildDashboard(String department) {
        List<AnomalyItem> anomalies = detectAnomalies(department);

        // 获取健康状态
        double healthScore = 100.0;
        try {
            HealthStatus status = healthMonitor.checkComponent(department);
            if (status != null) {
                healthScore = status.getScore();
            }
        } catch (Exception e) {
            log.debug("DM: failed to get health for {}: {}", department, e.getMessage());
        }

        return new DepartmentDashboard(
            department,
            healthScore / 100.0, // taskCompletionRate 近似
            500.0, // avgResponseTimeMs 占位
            0.8,   // customerSatisfactionScore 占位
            anomalies,
            0,     // activeKaizenEvents
            0,     // completedImprovements
            10,    // totalEmployees 占位
            8,     // activeEmployees 占位
            Map.of("NOVICE", 3, "COMPETENT", 4, "EXPERT", 2, "BLACK_BELT", 1),
            Instant.now()
        );
    }

    private List<AnomalyItem> detectAnomalies(String department) {
        List<AnomalyItem> anomalies = new ArrayList<>();

        try {
            HealthStatus status = healthMonitor.checkComponent(department);
            if (status != null && status.getStatus() != HealthStatus.Status.HEALTHY) {
                // HealthStatus.Status 只有 HEALTHY/DEGRADED/UNHEALTHY 三种状态
                // 映射到 AnomalyLevel: UNHEALTHY -> CRITICAL, DEGRADED -> HIGH
                AnomalyLevel level = switch (status.getStatus()) {
                    case UNHEALTHY -> AnomalyLevel.CRITICAL;
                    case DEGRADED -> AnomalyLevel.HIGH;
                    default -> AnomalyLevel.NORMAL;
                };
                anomalies.add(new AnomalyItem(
                    UUID.randomUUID().toString(), department, level,
                    department + "_health", status.getMessage(),
                    8, Instant.now(), level == AnomalyLevel.CRITICAL || level == AnomalyLevel.HIGH));
            }
        } catch (Exception e) {
            log.debug("DM: anomaly detection failed for {}: {}", department, e.getMessage());
        }

        return anomalies;
    }
}
