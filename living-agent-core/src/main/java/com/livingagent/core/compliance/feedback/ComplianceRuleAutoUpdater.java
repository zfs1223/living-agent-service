package com.livingagent.core.compliance.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 闭环45-P45-B: 合规规则自动更新器
 * 基于违规模式自动调整规则严格度
 */
public class ComplianceRuleAutoUpdater {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleAutoUpdater.class);
    private static final double HIGH_REPEAT_RATE = 0.30;

    private final ComplianceViolationTracker tracker;
    private final CrossLoopEventBus eventBus;

    public ComplianceRuleAutoUpdater(ComplianceViolationTracker tracker, CrossLoopEventBus eventBus) {
        this.tracker = tracker;
        this.eventBus = eventBus;
    }

    public void analyzeAndSuggest() {
        ComplianceViolationTracker.ComplianceReport report = tracker.getReport();
        for (Map.Entry<String, ComplianceViolationTracker.RuleViolationSnapshot> entry :
                report.ruleMetrics().entrySet()) {
            String ruleId = entry.getKey();
            ComplianceViolationTracker.RuleViolationSnapshot snapshot = entry.getValue();
            if (snapshot.violations() < 3) continue;

            if (snapshot.repeatRate() > HIGH_REPEAT_RATE) {
                log.info("[闭环45] 规则{}重复违规率{}%偏高，建议加强拦截",
                    ruleId, String.format("%.0f%%", snapshot.repeatRate() * 100));
                if (eventBus != null) {
                    eventBus.publish(45, "compliance_rule_update", CrossLoopEvent.EventPriority.SECURITY,
                        Map.of("content", String.format("合规规则 %s 重复违规率 %.0f%%，建议加强", ruleId, snapshot.repeatRate() * 100),
                            "ruleId", ruleId));
                }
            }
        }
    }
}
