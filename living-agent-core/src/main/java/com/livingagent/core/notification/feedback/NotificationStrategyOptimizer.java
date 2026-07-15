package com.livingagent.core.notification.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 闭环44-P44-B: 通知策略优化器
 * 基于触达率数据优化推送策略
 */
public class NotificationStrategyOptimizer {

    private static final Logger log = LoggerFactory.getLogger(NotificationStrategyOptimizer.class);
    private static final double LOW_DELIVERY_RATE_THRESHOLD = 0.50;

    private final NotificationMetricsService metricsService;
    private final CrossLoopEventBus eventBus;

    private volatile double pushFrequencyMultiplier = 1.0;
    private volatile String degradedChannel = null;

    public NotificationStrategyOptimizer(NotificationMetricsService metricsService) {
        this(metricsService, null);
    }

    public NotificationStrategyOptimizer(NotificationMetricsService metricsService, CrossLoopEventBus eventBus) {
        this.metricsService = metricsService;
        this.eventBus = eventBus;
    }

    public void optimize() {
        NotificationMetricsService.NotificationMetricsReport report = metricsService.getReport();
        if (report.totalSent() < 10) return;

        boolean strategyChanged = false;

        if (report.deliveryRate() < LOW_DELIVERY_RATE_THRESHOLD) {
            pushFrequencyMultiplier = Math.max(0.3, pushFrequencyMultiplier * 0.7);
            log.warn("[闭环44] 消息触达率{}%偏低，降低推送频率至{}x",
                String.format("%.0f", report.deliveryRate() * 100),
                String.format("%.1f", pushFrequencyMultiplier));
            strategyChanged = true;
        } else if (report.deliveryRate() > 0.80) {
            pushFrequencyMultiplier = Math.min(1.5, pushFrequencyMultiplier * 1.2);
            log.info("[闭环44] 消息触达率{}%正常，恢复推送频率至{}x",
                String.format("%.0f", report.deliveryRate() * 100),
                String.format("%.1f", pushFrequencyMultiplier));
            strategyChanged = true;
        }

        for (Map.Entry<String, NotificationMetricsService.ChannelMetricsSnapshot> entry :
                report.channelMetrics().entrySet()) {
            if (entry.getValue().deliveryRate() < LOW_DELIVERY_RATE_THRESHOLD) {
                degradedChannel = entry.getKey();
                log.info("[闭环44] 渠道{}触达率{}%偏低，标记降级",
                    entry.getKey(), String.format("%.0f", entry.getValue().deliveryRate() * 100));
                strategyChanged = true;
            }
        }

        if (strategyChanged && eventBus != null) {
            eventBus.publish(44, "notification_strategy_optimized",
                CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("pushFrequencyMultiplier", pushFrequencyMultiplier,
                       "degradedChannel", degradedChannel != null ? degradedChannel : "none",
                       "deliveryRate", report.deliveryRate()),
                300);
        }
    }

    public double getPushFrequencyMultiplier() {
        return pushFrequencyMultiplier;
    }

    public String getDegradedChannel() {
        return degradedChannel;
    }
}
