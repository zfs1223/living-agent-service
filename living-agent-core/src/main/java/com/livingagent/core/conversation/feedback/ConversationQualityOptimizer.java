package com.livingagent.core.conversation.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConversationQualityOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationQualityOptimizer.class);

    private final ConversationQualityService qualityService;
    private final CrossLoopEventBus eventBus;

    private volatile double resolutionWarningThreshold = 0.50;
    private volatile double clarificationWarningThreshold = 0.30;

    public ConversationQualityOptimizer(ConversationQualityService qualityService, CrossLoopEventBus eventBus) {
        this.qualityService = qualityService;
        this.eventBus = eventBus;
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

    public double getResolutionWarningThreshold() { return resolutionWarningThreshold; }
    public double getClarificationWarningThreshold() { return clarificationWarningThreshold; }
}
