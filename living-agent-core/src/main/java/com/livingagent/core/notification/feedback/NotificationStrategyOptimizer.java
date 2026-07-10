package com.livingagent.core.notification.feedback;

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

    public NotificationStrategyOptimizer(NotificationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void optimize() {
        NotificationMetricsService.NotificationMetricsReport report = metricsService.getReport();
        if (report.totalSent() < 10) return;

        if (report.deliveryRate() < LOW_DELIVERY_RATE_THRESHOLD) {
            log.warn("[闭环44] 消息触达率偏低({}/%)，建议调整推送渠道或频率",
                String.format("%.0f%%", report.deliveryRate() * 100));
        }

        for (Map.Entry<String, NotificationMetricsService.ChannelMetricsSnapshot> entry :
                report.channelMetrics().entrySet()) {
            if (entry.getValue().deliveryRate() < LOW_DELIVERY_RATE_THRESHOLD) {
                log.info("[闭环44] 渠道{}触达率{}%偏低，建议降级或替换",
                    entry.getKey(), String.format("%.0f%%", entry.getValue().deliveryRate() * 100));
            }
        }
    }
}
