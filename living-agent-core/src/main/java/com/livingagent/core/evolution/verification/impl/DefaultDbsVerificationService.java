package com.livingagent.core.evolution.verification.impl;

import com.livingagent.core.evolution.verification.DbsVerificationService;
import com.livingagent.core.evolution.tpi.ProcessEfficiencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P14: DBS 验证表增强服务默认实现。
 */
@Service
public class DefaultDbsVerificationService implements DbsVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDbsVerificationService.class);

    /** DBS 八大工具与闭环的映射 */
    private static final Map<String, List<Integer>> DBS_TOOL_LOOP_MAPPING = Map.of(
        "VSM(价值流图)", List.of(1, 3, 25, 27),
        "SW(标准作业)", List.of(30, 26, 42),
        "PSP(问题解决)", List.of(24, 8),
        "VOC(客户之声)", List.of(46, 66, 54),
        "DM(日常管理)", List.of(8, 11, 65),
        "Kaizen(改善)", List.of(4, 31, 42),
        "TPI(事务流程改进)", List.of(1, 3, 25),
        "5S(5S审计)", List.of(26, 42)
    );

    /** DBS 工具 → 主脑技能映射 */
    private static final Map<String, String> DBS_TOOL_SKILL_MAPPING = Map.of(
        "VSM(价值流图)", "dbs-value-stream-mapping",
        "SW(标准作业)", "dbs-standard-work",
        "PSP(问题解决)", "dbs-problem-solving",
        "VOC(客户之声)", "dbs-voice-of-customer",
        "DM(日常管理)", "dbs-visual-management",
        "Kaizen(改善)", "dbs-kaizen",
        "TPI(事务流程改进)", "dbs-transaction-process-improvement",
        "5S(5S审计)", "dbs-5s-audit"
    );

    /** AI 七大浪费检测配置 */
    private static final List<WasteConfig> WASTE_CONFIGS = List.of(
        new WasteConfig("过度生产", "过度推理（不必要的LLM调用）", "llmCallCount/actualNeed", 1.5, 1.0, "3"),
        new WasteConfig("等待", "模型响应延迟", "avgResponseTimeMs", 5000, 3000, "1"),
        new WasteConfig("运输", "跨服务数据搬运", "crossServiceCalls", 3, 2, "3"),
        new WasteConfig("过度加工", "过度复杂的决策链路", "decisionSteps/optimalSteps", 1.5, 1.2, "2"),
        new WasteConfig("库存", "未消费的对话历史", "historyUtilizationRate", 0.5, 0.7, "46"),
        new WasteConfig("动作", "重复的状态查询", "queryRedundancyRate", 0.2, 0.1, "8"),
        new WasteConfig("缺陷", "错误的LLM输出", "hallucinationRate", 0.05, 0.02, "24")
    );

    @Override
    public List<LoopVerificationEntry> generateVerificationTable() {
        List<LoopVerificationEntry> entries = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> toolEntry : DBS_TOOL_LOOP_MAPPING.entrySet()) {
            String tool = toolEntry.getKey();
            String skill = DBS_TOOL_SKILL_MAPPING.getOrDefault(tool, "—");

            for (int loopId : toolEntry.getValue()) {
                entries.add(new LoopVerificationEntry(
                    loopId, "闭环" + loopId, "L1-L4",
                    true, tool, skill, "已关联DBS工具"
                ));
            }
        }

        log.info("[P14/验证] 闭环验证表生成: entries={}", entries.size());
        return entries;
    }

    @Override
    public DbsToolCoverage getToolCoverage() {
        Map<String, Double> coverage = new LinkedHashMap<>();
        int coveredCount = 0;

        for (Map.Entry<String, String> entry : DBS_TOOL_SKILL_MAPPING.entrySet()) {
            boolean hasSkill = entry.getValue() != null && !entry.getValue().equals("—");
            coverage.put(entry.getKey(), hasSkill ? 1.0 : 0.0);
            if (hasSkill) coveredCount++;
        }

        double overall = (double) coveredCount / DBS_TOOL_SKILL_MAPPING.size();
        List<String> uncovered = coverage.entrySet().stream()
            .filter(e -> e.getValue() < 1.0)
            .map(Map.Entry::getKey)
            .toList();

        log.info("[P14/验证] DBS工具覆盖度: overall={:.1f}%, uncovered={}", overall * 100, uncovered);

        return new DbsToolCoverage(coverage, overall, uncovered, Instant.now());
    }

    @Override
    public List<WasteDetectionResult> detectWaste() {
        return WASTE_CONFIGS.stream()
            .map(config -> new WasteDetectionResult(
                config.wasteType,
                config.aiMapping,
                config.detectionMetric,
                config.currentValue,
                config.targetValue,
                isWasteDetected(config),
                config.relatedLoop
            ))
            .collect(Collectors.toList());
    }

    @Override
    public WasteSummary getWasteSummary() {
        List<WasteDetectionResult> results = detectWaste();
        int wasteCount = (int) results.stream().filter(WasteDetectionResult::isWaste).count();
        double wasteScore = (double) wasteCount / results.size();

        log.info("[P14/验证] AI七大浪费检测: detected={}/7, score={:.2f}", wasteCount, wasteScore);

        return new WasteSummary(results.size(), wasteCount, wasteScore, results, Instant.now());
    }

    private boolean isWasteDetected(WasteConfig config) {
        // 当前值为占位实现：所有浪费类型都标记为"未检测到"
        // 后续接入实际运维数据后可实现真实检测
        return false;
    }

    private record WasteConfig(
        String wasteType, String aiMapping, String detectionMetric,
        double currentValue, double targetValue, String relatedLoop
    ) {}
}
