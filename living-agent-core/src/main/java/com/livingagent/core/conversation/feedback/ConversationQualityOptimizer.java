package com.livingagent.core.conversation.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 闭环46: 对话质量优化器。
 * 定期评估对话质量指标，动态调整阈值，并提取 VOC（客户之声）信号注入进化链路。
 *
 * DBS VOC 管道增强（P3-2）：
 * - 对话完成时自动提取 VOC 信号（USER_NEED / USER_PAIN / USER_PRAISE）
 * - VOC 信号通过 EvolutionSignal 发布，驱动闭环 4（进化调整）/ 24（自愈）/ 26（知识自进化）
 * - 置信度过滤：仅高置信度 VOC 信号进入进化链路
 */
@Component
public class ConversationQualityOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationQualityOptimizer.class);

    // VOC 关键词规则表
    private static final List<String> PAIN_KEYWORDS = List.of(
        "不满意", "太慢", "无法", "报错", "失败", "崩溃", "卡顿", "不好用",
        "bug", "error", "fail", "crash", "slow", "frustrated", "annoying"
    );
    private static final List<String> NEED_KEYWORDS = List.of(
        "希望", "想要", "能不能", "是否可以", "建议", "需要", "如果可以",
        "wish", "want", "could", "suggest", "need", "feature", "it would be nice"
    );
    private static final List<String> PRAISE_KEYWORDS = List.of(
        "很好", "太棒了", "不错", "喜欢", "感谢", "满意", "优秀", "赞",
        "great", "awesome", "love", "thanks", "excellent", "perfect", "good job"
    );

    private static final double VOC_CONFIDENCE_THRESHOLD = 0.60;

    private final ConversationQualityService qualityService;
    private final CrossLoopEventBus eventBus;
    private final ApplicationEventPublisher eventPublisher;

    private volatile double resolutionWarningThreshold = 0.50;
    private volatile double clarificationWarningThreshold = 0.30;

    public ConversationQualityOptimizer(ConversationQualityService qualityService,
                                         CrossLoopEventBus eventBus,
                                         ApplicationEventPublisher eventPublisher) {
        this.qualityService = qualityService;
        this.eventBus = eventBus;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateConversationQuality() {
        ConversationQualityService.ConversationQualityReport report = qualityService.getReport();
        if (report.totalConversations() < 10) return;

        boolean changed = false;

        if (report.resolutionRate() < resolutionWarningThreshold) {
            clarificationWarningThreshold = Math.min(0.50, clarificationWarningThreshold + 0.05);
            log.info("[闭环46] 解决率{}%偏低，提高澄清预警阈值至{}%",
                String.format("%.0f", report.resolutionRate() * 100),
                String.format("%.0f", clarificationWarningThreshold * 100));
            changed = true;
        } else if (report.resolutionRate() > 0.80 && clarificationWarningThreshold > 0.20) {
            clarificationWarningThreshold = Math.max(0.20, clarificationWarningThreshold - 0.02);
            changed = true;
        }

        if (report.clarificationRate() > clarificationWarningThreshold) {
            resolutionWarningThreshold = Math.max(0.30, resolutionWarningThreshold - 0.05);
            log.info("[闭环46] 澄清率{}%偏高，降低解决率预警阈值至{}%",
                String.format("%.0f", report.clarificationRate() * 100),
                String.format("%.0f", resolutionWarningThreshold * 100));
            changed = true;
        }

        if (changed && eventBus != null) {
            eventBus.publish(46, "conversation_quality_adjusted",
                CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("resolutionRate", report.resolutionRate(),
                    "clarificationRate", report.clarificationRate(),
                    "resolutionWarningThreshold", resolutionWarningThreshold,
                    "clarificationWarningThreshold", clarificationWarningThreshold));
        }
    }

    // ========== DBS VOC 管道增强（P3-2）==========

    /**
     * 从对话内容中提取 VOC 信号并注入进化链路。
     * 在对话完成时调用，自动识别 USER_NEED / USER_PAIN / USER_PRAISE 信号。
     *
     * @param conversationId 对话ID
     * @param content 对话最后一条用户消息内容
     * @param brainDomain 所属大脑域（如 tech/hr/finance）
     * @param resolved 对话是否已解决
     */
    public void extractAndPublishVOC(String conversationId, String content, String brainDomain, boolean resolved) {
        if (content == null || content.isBlank()) return;

        String lowerContent = content.toLowerCase();
        VocClassification classification = classifyVOC(lowerContent, resolved);

        if (classification.type == null || classification.confidence < VOC_CONFIDENCE_THRESHOLD) {
            log.debug("[闭环46-VOC] VOC 信号置信度不足，跳过: convId={}, confidence={:.2f}, content={}",
                conversationId, classification.confidence, abbreviate(content, 50));
            return;
        }

        EvolutionSignal signal = new EvolutionSignal(classification.type, content);
        signal.setSource("ConversationQualityOptimizer");
        signal.setBrainDomain(brainDomain != null ? brainDomain : "unknown");
        signal.setConfidence(classification.confidence);
        signal.addMetadata("conversationId", conversationId);
        signal.addMetadata("vocCategory", classification.type.name());
        signal.addTag("VOC");

        eventPublisher.publishEvent(signal);

        log.info("[闭环46-VOC] VOC 信号发布: type={}, confidence={:.2f}, brainDomain={}, convId={}",
            classification.type, classification.confidence, brainDomain, conversationId);

        // VOC 信号分发规则：同步发布跨闭环事件
        if (eventBus != null) {
            int targetLoop = mapVOCToLoop(classification.type);
            eventBus.publish(46, "voc_" + classification.type.name().toLowerCase(),
                CrossLoopEvent.EventPriority.KNOWLEDGE,
                Map.of("vocType", classification.type.name(),
                    "conversationId", conversationId,
                    "brainDomain", brainDomain != null ? brainDomain : "unknown",
                    "confidence", classification.confidence));
        }
    }

    /**
     * 基于 DBS VOC 规则对用户消息进行分类。
     */
    private VocClassification classifyVOC(String lowerContent, boolean resolved) {
        double painScore = calculateKeywordScore(lowerContent, PAIN_KEYWORDS);
        double needScore = calculateKeywordScore(lowerContent, NEED_KEYWORDS);
        double praiseScore = calculateKeywordScore(lowerContent, PRAISE_KEYWORDS);

        // 未解决的对话提高 PAIN 权重
        if (!resolved) {
            painScore *= 1.3;
        }

        double maxScore = Math.max(Math.max(painScore, needScore), praiseScore);

        if (maxScore < 0.01) {
            return new VocClassification(null, 0.0);
        }

        EvolutionSignal.SignalType type;
        if (painScore >= needScore && painScore >= praiseScore) {
            type = EvolutionSignal.SignalType.USER_PAIN;
        } else if (needScore >= praiseScore) {
            type = EvolutionSignal.SignalType.USER_NEED;
        } else {
            type = EvolutionSignal.SignalType.USER_PRAISE;
        }

        double confidence = Math.min(1.0, maxScore * 2.0 + 0.3);
        return new VocClassification(type, confidence);
    }

    /**
     * 计算内容中关键词命中得分。
     */
    private double calculateKeywordScore(String content, List<String> keywords) {
        long hits = keywords.stream().filter(content::contains).count();
        return (double) hits / keywords.size();
    }

    /**
     * VOC 信号到闭环的映射规则。
     * - USER_PAIN → 闭环24（自愈：问题修复信号）
     * - USER_NEED → 闭环4（进化调整：功能改进信号）
     * - USER_PRAISE → 闭环26（知识自进化：正向反馈）
     */
    private int mapVOCToLoop(EvolutionSignal.SignalType vocType) {
        return switch (vocType) {
            case USER_PAIN -> 24;
            case USER_NEED -> 4;
            case USER_PRAISE -> 26;
            default -> 4;
        };
    }

    private String abbreviate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private record VocClassification(EvolutionSignal.SignalType type, double confidence) {}

    public double getResolutionWarningThreshold() { return resolutionWarningThreshold; }
    public double getClarificationWarningThreshold() { return clarificationWarningThreshold; }
}
