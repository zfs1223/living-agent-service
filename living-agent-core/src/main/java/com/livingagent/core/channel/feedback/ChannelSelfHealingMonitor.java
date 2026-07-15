package com.livingagent.core.channel.feedback;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelManager.ChannelHealthSummary;
import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChannelSelfHealingMonitor {

    private static final Logger log = LoggerFactory.getLogger(ChannelSelfHealingMonitor.class);

    private final ChannelManager channelManager;
    private final CrossLoopEventBus eventBus;

    private volatile double emptyChannelWarningRate = 0.30;

    public ChannelSelfHealingMonitor(ChannelManager channelManager, CrossLoopEventBus eventBus) {
        this.channelManager = channelManager;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateChannelHealth() {
        ChannelHealthSummary summary = channelManager.getHealthSummary();
        if (summary.totalChannels() == 0) return;

        double emptyRate = (double) summary.emptyChannels() / summary.totalChannels();

        if (emptyRate > emptyChannelWarningRate) {
            emptyChannelWarningRate = Math.max(0.10, emptyChannelWarningRate - 0.05);
            log.info("[闭环11-B] 通道空置率{}%，降低预警阈值至{}%",
                String.format("%.0f", emptyRate * 100),
                String.format("%.0f", emptyChannelWarningRate * 100));

            eventBus.publish(11, "channel_health_degraded",
                CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("emptyRate", emptyRate,
                    "totalChannels", summary.totalChannels(),
                    "emptyChannels", summary.emptyChannels(),
                    "action", "rebalance_subscribers"));
        } else if (emptyRate < 0.05 && emptyChannelWarningRate < 0.40) {
            emptyChannelWarningRate = Math.min(0.40, emptyChannelWarningRate + 0.02);
        }
    }

    public double getEmptyChannelWarningRate() {
        return emptyChannelWarningRate;
    }
}
