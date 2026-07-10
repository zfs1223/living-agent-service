package com.livingagent.core.notification.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环44-P44-A: 消息通知指标服务
 * 追踪消息触达率/响应时间/已读率
 */
public class NotificationMetricsService {

    private static final Logger log = LoggerFactory.getLogger(NotificationMetricsService.class);
    private static final double LOW_DELIVERY_RATE = 0.50;

    private final LongAdder totalSent = new LongAdder();
    private final LongAdder totalDelivered = new LongAdder();
    private final LongAdder totalRead = new LongAdder();
    private final Map<String, ChannelMetrics> channelMetrics = new ConcurrentHashMap<>();

    public void recordSent(String channel) { totalSent.increment(); channelMetrics.computeIfAbsent(channel, k -> new ChannelMetrics()).sent.increment(); }
    public void recordDelivered(String channel) { totalDelivered.increment(); channelMetrics.computeIfAbsent(channel, k -> new ChannelMetrics()).delivered.increment(); }
    public void recordRead(String channel) { totalRead.increment(); channelMetrics.computeIfAbsent(channel, k -> new ChannelMetrics()).read.increment(); }

    public NotificationMetricsReport getReport() {
        long sent = totalSent.sum();
        Map<String, ChannelMetricsSnapshot> snapshots = new ConcurrentHashMap<>();
        channelMetrics.forEach((k, v) -> snapshots.put(k, new ChannelMetricsSnapshot(
            v.sent.sum(), v.delivered.sum(), v.read.sum(),
            v.sent.sum() > 0 ? (double) v.delivered.sum() / v.sent.sum() : 0,
            v.sent.sum() > 0 ? (double) v.read.sum() / v.sent.sum() : 0
        )));
        return new NotificationMetricsReport(
            sent, totalDelivered.sum(), totalRead.sum(),
            sent > 0 ? (double) totalDelivered.sum() / sent : 0,
            sent > 0 ? (double) totalRead.sum() / sent : 0,
            snapshots, Instant.now()
        );
    }

    public String getSummary() {
        NotificationMetricsReport r = getReport();
        if (r.totalSent() == 0) return "No notifications sent";
        return String.format("Notifications: sent=%d, deliveryRate=%.0f%%, readRate=%.0f%%",
            r.totalSent(), r.deliveryRate() * 100, r.readRate() * 100);
    }

    public record NotificationMetricsReport(
        long totalSent, long totalDelivered, long totalRead,
        double deliveryRate, double readRate,
        Map<String, ChannelMetricsSnapshot> channelMetrics, Instant capturedAt
    ) {}
    public record ChannelMetricsSnapshot(long sent, long delivered, long read, double deliveryRate, double readRate) {}

    private static class ChannelMetrics {
        final LongAdder sent = new LongAdder();
        final LongAdder delivered = new LongAdder();
        final LongAdder read = new LongAdder();
    }
}
