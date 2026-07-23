package com.livingagent.core.employee.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.signal.EvolutionSignal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 闭环65: 人类员工周期性汇报服务。
 *
 * DBS People 工具核心区分：
 * - 人类员工（origin=HUMAN）: 需周期性汇报个人表现和改进建议，个人+管理可见
 * - 固定数字员工（origin=FIXED）: 内部直接完成问题发现和规范补充，无需手动汇报
 * - 个人助手（origin=PERSONAL）: 允许直连，可选汇报
 *
 * 关联闭环：
 * - → 11-A 员工状态（归档）
 * - → 4 进化调整（改进建议信号）
 * - → 53 绩效考核（表现数据）
 * - → 24 自愈（问题反馈）
 * - → 43 工作流（流程痛点）
 */
@Service
public class HumanEmployeeReportService {

    private static final Logger log = LoggerFactory.getLogger(HumanEmployeeReportService.class);

    private final CrossLoopEventBus eventBus;
    private final ApplicationEventPublisher eventPublisher;

    /** 员工汇报存储：employeeId → 汇报列表 */
    private final Map<String, List<EmployeeReport>> reportStore = new ConcurrentHashMap<>();

    /** 员工汇报提醒追踪：employeeId → 上次汇报时间 */
    private final Map<String, Instant> lastReportTime = new ConcurrentHashMap<>();

    /** 汇报周期配置（小时）：默认每周（168小时） */
    private volatile int reportCycleHours = 168;

    /** 逾期未汇报提醒阈值（天） */
    private volatile int overdueWarningDays = 10;

    public HumanEmployeeReportService(CrossLoopEventBus eventBus, ApplicationEventPublisher eventPublisher) {
        this.eventBus = eventBus;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交员工汇报。
     * 仅人类员工可主动提交，固定数字员工通过 generateDigitalReport() 自动产出。
     */
    public EmployeeReport submitReport(EmployeeReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Report cannot be null");
        }

        // 人类员工周期性汇报
        if (report.getOrigin() == EmployeeReport.EmployeeOrigin.HUMAN) {
            reportStore.computeIfAbsent(report.getEmployeeId(), k -> new ArrayList<>()).add(report);
            lastReportTime.put(report.getEmployeeId(), report.getSubmittedAt());

            log.info("[闭环65] 人类员工汇报提交: employee={}, type={}, id={}",
                report.getEmployeeName(), report.getReportType(), report.getReportId());

            // 提取改进建议 → 闭环4 进化信号
            publishImprovementSignals(report);

            // 提取问题 → 闭环24 自愈
            publishChallengeSignals(report);

            // 同步发送给管理者
            if (report.isNotifyManager()) {
                notifyManager(report);
            }

            // 发布跨闭环事件
            publishCrossLoopEvent(report);
        } else {
            log.warn("[闭环65] 非人类员工不应调用 submitReport: origin={}, employee={}",
                report.getOrigin(), report.getEmployeeName());
        }

        return report;
    }

    /**
     * 固定数字员工自动产出汇报。
     * 内部直接完成问题发现和规范补充，无需手动汇报。
     */
    public EmployeeReport generateDigitalReport(String employeeId, String employeeName,
                                                  String workSummary, List<String> keyAchievements,
                                                  List<String> issuesFound, List<String> standardsProposed) {
        EmployeeReport report = new EmployeeReport(employeeId, employeeName,
            EmployeeReport.EmployeeOrigin.FIXED, EmployeeReport.ReportType.WEEKLY);
        report.setWorkSummary(workSummary);
        report.setKeyAchievements(keyAchievements != null ? keyAchievements : List.of());
        report.setChallenges(issuesFound != null ? issuesFound : List.of());
        report.setImprovementSuggestions(standardsProposed != null ? standardsProposed : List.of());
        report.setVisibility(EmployeeReport.Visibility.SELF_AND_MANAGER);
        report.setNotifyManager(false); // 数字员工不通知管理者

        reportStore.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(report);

        log.info("[闭环65] 数字员工自动产出汇报: employee={}, issues={}, standards={}",
            employeeName,
            issuesFound != null ? issuesFound.size() : 0,
            standardsProposed != null ? standardsProposed.size() : 0);

        // 数字员工的问题发现直接注入闭环24自愈
        if (issuesFound != null && !issuesFound.isEmpty()) {
            for (String issue : issuesFound) {
                EvolutionSignal signal = new EvolutionSignal(SignalType.ERROR, issue);
                signal.setSource("DigitalEmployee:" + employeeName);
                signal.setConfidence(0.8);
                signal.addTag("AUTO_DISCOVERY");
                eventPublisher.publishEvent(signal);
            }
        }

        return report;
    }

    /**
     * 获取员工汇报列表。
     * 根据可见性过滤：管理者可看所有，员工只看自己的。
     */
    public List<EmployeeReport> getReports(String employeeId, String viewerId, boolean isManager) {
        List<EmployeeReport> reports = reportStore.getOrDefault(employeeId, List.of());
        if (isManager || employeeId.equals(viewerId)) {
            return reports;
        }
        // 非管理者且非本人：只看组织公开
        return reports.stream()
            .filter(r -> r.getVisibility() == EmployeeReport.Visibility.ORG_WIDE)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有人类员工的最近汇报。
     */
    public Map<String, EmployeeReport> getLatestHumanReports() {
        Map<String, EmployeeReport> latest = new HashMap<>();
        for (Map.Entry<String, List<EmployeeReport>> entry : reportStore.entrySet()) {
            List<EmployeeReport> reports = entry.getValue();
            if (!reports.isEmpty()) {
                EmployeeReport last = reports.get(reports.size() - 1);
                if (last.isHumanReport()) {
                    latest.put(entry.getKey(), last);
                }
            }
        }
        return latest;
    }

    /**
     * 定时检查人类员工是否逾期未汇报，发送提醒。
     * 每24小时执行一次。
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
    public void checkOverdueReports() {
        Instant overdueThreshold = Instant.now().minusSeconds((long) overdueWarningDays * 24 * 3600);
        List<String> overdueEmployees = new ArrayList<>();

        for (Map.Entry<String, Instant> entry : lastReportTime.entrySet()) {
            if (entry.getValue().isBefore(overdueThreshold)) {
                overdueEmployees.add(entry.getKey());
            }
        }

        if (!overdueEmployees.isEmpty() && eventBus != null) {
            eventBus.publish(65, "human_employee_report_overdue",
                CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("overdueCount", overdueEmployees.size(),
                    "overdueEmployees", String.join(",", overdueEmployees),
                    "thresholdDays", overdueWarningDays));
            log.warn("[闭环65] 人类员工逾期未汇报: count={}, employees={}",
                overdueEmployees.size(), overdueEmployees);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 提取改进建议，发布为进化信号 → 闭环4
     */
    private void publishImprovementSignals(EmployeeReport report) {
        List<String> suggestions = report.getImprovementSuggestions();
        if (suggestions == null || suggestions.isEmpty()) return;

        for (String suggestion : suggestions) {
            EvolutionSignal signal = new EvolutionSignal(SignalType.OPPORTUNITY, suggestion);
            signal.setSource("HumanEmployeeReport:" + report.getEmployeeName());
            signal.setConfidence(0.6);
            signal.addMetadata("reportId", report.getReportId());
            signal.addMetadata("origin", "HUMAN");
            signal.addTag("PERIODIC_REPORT");
            eventPublisher.publishEvent(signal);
        }

        log.debug("[闭环65] 改进建议信号发布: employee={}, count={}", report.getEmployeeName(), suggestions.size());
    }

    /**
     * 提取问题反馈，发布为自愈信号 → 闭环24
     */
    private void publishChallengeSignals(EmployeeReport report) {
        List<String> challenges = report.getChallenges();
        if (challenges == null || challenges.isEmpty()) return;

        for (String challenge : challenges) {
            EvolutionSignal signal = new EvolutionSignal(SignalType.ERROR, challenge);
            signal.setSource("HumanEmployeeReport:" + report.getEmployeeName());
            signal.setConfidence(0.7);
            signal.addMetadata("reportId", report.getReportId());
            signal.addMetadata("origin", "HUMAN");
            signal.addTag("CHALLENGE_REPORT");
            eventPublisher.publishEvent(signal);
        }

        log.debug("[闭环65] 问题反馈信号发布: employee={}, count={}", report.getEmployeeName(), challenges.size());
    }

    /**
     * 通知管理者（模拟：发布跨闭环事件）
     */
    private void notifyManager(EmployeeReport report) {
        if (eventBus == null) return;
        eventBus.publish(65, "human_report_notify_manager",
            CrossLoopEvent.EventPriority.KNOWLEDGE,
            Map.of("employeeId", report.getEmployeeId(),
                "employeeName", report.getEmployeeName(),
                "reportType", report.getReportType().name(),
                "reportId", report.getReportId()));
    }

    /**
     * 发布跨闭环事件 → 关联闭环 4/11-A/53/24/43
     */
    private void publishCrossLoopEvent(EmployeeReport report) {
        if (eventBus == null) return;
        eventBus.publish(65, "human_employee_report_submitted",
            CrossLoopEvent.EventPriority.KNOWLEDGE,
            Map.of("employeeId", report.getEmployeeId(),
                "origin", report.getOrigin().name(),
                "reportType", report.getReportType().name(),
                "achievementCount", report.getKeyAchievements().size(),
                "challengeCount", report.getChallenges().size(),
                "suggestionCount", report.getImprovementSuggestions().size()));
    }

    // === Configuration ===

    public int getReportCycleHours() { return reportCycleHours; }
    public void setReportCycleHours(int hours) { this.reportCycleHours = hours; }

    public int getOverdueWarningDays() { return overdueWarningDays; }
    public void setOverdueWarningDays(int days) { this.overdueWarningDays = days; }
}
