package com.livingagent.core.evolution.conflict;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P13: DBS 冲突调和与 5Ps 对齐服务。
 *
 * P13-1: 分级熔断策略代码实现
 *   - REPAIR 级别：自动熔断（100%自动执行）
 *   - OPTIMIZE 级别：自动执行+日志记录
 *   - INNOVATE 级别：人工审批兜底
 *   - VOC 级别：自动放行（低风险客户反馈）
 *
 * P13-2: 跨部门协作通道
 *   - 协作请求必须经 MainBrain 仲裁
 *   - 权限隔离不妥协
 *
 * P13-3: 人机协同改善模式标注
 *   - 数字员工：自动模式
 *   - 人类员工：协作模式（需人工确认）
 *   - 混合模式：数字驱动+人类审核
 *
 * P13-4: 5Ps 对齐 Purpose/Plan 层补齐
 *   - Purpose: 系统存在意义（服务用户+持续进化）
 *   - Philosophy: 核心信念（DBS方法论）
 *   - Process: 执行流程（六步决策法）
 *   - People: 人才体系（四级能力等级）
 *   - Plan: 战略部署（Hoshin Kanri四级联动）
 */
public interface ConflictResolutionService {

    /**
     * 执行分级熔断决策。
     *
     * @param signalCategory 信号分类（REPAIR/OPTIMIZE/INNOVATE/VOC）
     * @param confidence 置信度
     * @param content 信号内容
     * @return 熔断决策结果
     */
    CircuitBreakerDecision evaluateCircuitBreaker(String signalCategory, double confidence, String content);

    /**
     * 仲裁跨部门协作请求。
     *
     * @param requestingBrain 请求方大脑
     * @param targetBrain 目标方大脑
     * @param operation 请求的操作
     * @return 仲裁结果
     */
    CollaborationArbitration arbitrateCollaboration(String requestingBrain, String targetBrain, String operation);

    /**
     * 判断改善模式（自动/协作/混合）。
     */
    ImprovementMode determineImprovementMode(String employeeOrigin, String improvementType);

    /**
     * 评估5Ps对齐度。
     */
    FivePsAlignment assessFivePsAlignment();

    // === 数据模型 ===

    record CircuitBreakerDecision(
        String decisionId,
        String signalCategory,
        double confidence,
        DecisionType type,
        String reason,
        boolean requiresHumanApproval,
        Instant decidedAt
    ) {
        public enum DecisionType {
            AUTO_EXECUTE,        // 自动执行
            AUTO_WITH_LOG,       // 自动执行+日志
            HUMAN_APPROVAL,      // 人工审批
            AUTO_PASS,           // 自动放行
            REJECTED             // 拒绝
        }
    }

    record CollaborationArbitration(
        String arbitrationId,
        String requestingBrain,
        String targetBrain,
        String operation,
        boolean approved,
        String condition,
        Instant arbitratedAt
    ) {}

    enum ImprovementMode {
        AUTO("自动模式", "数字员工自主完成"),
        COLLABORATIVE("协作模式", "需人工确认关键步骤"),
        HYBRID("混合模式", "数字驱动+人类审核");

        private final String label;
        private final String description;

        ImprovementMode(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    record FivePsAlignment(
        double purposeScore,    // Purpose 对齐度
        double philosophyScore, // Philosophy 对齐度
        double processScore,    // Process 对齐度
        double peopleScore,     // People 对齐度
        double planScore,       // Plan 对齐度
        double overallScore,    // 综合对齐度
        List<String> gaps,      // 对齐缺口描述
        Instant assessedAt
    ) {}
}
