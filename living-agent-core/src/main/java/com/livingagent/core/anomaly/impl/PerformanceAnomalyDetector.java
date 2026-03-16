package com.livingagent.core.anomaly.impl;

import com.livingagent.core.anomaly.AnomalyContext;
import com.livingagent.core.anomaly.AnomalyDetector;
import com.livingagent.core.anomaly.AnomalyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PerformanceAnomalyDetector implements AnomalyDetector {
    
    private static final Logger log = LoggerFactory.getLogger(PerformanceAnomalyDetector.class);
    
    private static final String TYPE = "performance";
    private static final long RESPONSE_TIME_WARNING_THRESHOLD = 3000;
    private static final long RESPONSE_TIME_ERROR_THRESHOLD = 10000;
    private static final double ERROR_RATE_WARNING_THRESHOLD = 0.05;
    private static final double ERROR_RATE_ERROR_THRESHOLD = 0.15;
    
    private boolean enabled = true;
    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalResponseTime = new ConcurrentHashMap<>();
    
    @Override
    public AnomalyResult detect(AnomalyContext context) {
        log.debug("Detecting performance anomalies for session: {}", context.getSessionId());
        
        AnomalyResult result = AnomalyResult.normal(TYPE);
        
        Map<String, Object> metrics = context.getMetrics();
        
        if (metrics.containsKey("responseTime")) {
            long responseTime = ((Number) metrics.get("responseTime")).longValue();
            if (responseTime > RESPONSE_TIME_ERROR_THRESHOLD) {
                result = AnomalyResult.error(TYPE, "响应时间过长: " + responseTime + "ms");
                result.addAnomaly("high_response_time");
                result.addDetail("responseTime", responseTime);
                result.addDetail("threshold", RESPONSE_TIME_ERROR_THRESHOLD);
                result.addRecommendation("scale_out", "考虑扩展服务实例");
                result.addRecommendation("cache", "检查是否可以使用缓存优化");
            } else if (responseTime > RESPONSE_TIME_WARNING_THRESHOLD) {
                result = AnomalyResult.warning(TYPE, "响应时间偏长: " + responseTime + "ms");
                result.addAnomaly("elevated_response_time");
                result.addDetail("responseTime", responseTime);
            }
        }
        
        if (metrics.containsKey("errorRate")) {
            double errorRate = ((Number) metrics.get("errorRate")).doubleValue();
            if (errorRate > ERROR_RATE_ERROR_THRESHOLD) {
                result = AnomalyResult.error(TYPE, "错误率过高: " + String.format("%.2f%%", errorRate * 100));
                result.addAnomaly("high_error_rate");
                result.addDetail("errorRate", errorRate);
                result.addRecommendation("investigate", "检查日志排查错误原因");
                result.addRecommendation("alert", "发送告警通知");
            } else if (errorRate > ERROR_RATE_WARNING_THRESHOLD) {
                result = AnomalyResult.warning(TYPE, "错误率偏高: " + String.format("%.2f%%", errorRate * 100));
                result.addAnomaly("elevated_error_rate");
                result.addDetail("errorRate", errorRate);
            }
        }
        
        if (metrics.containsKey("memoryUsage")) {
            double memoryUsage = ((Number) metrics.get("memoryUsage")).doubleValue();
            if (memoryUsage > 0.9) {
                result = AnomalyResult.critical(TYPE, "内存使用率过高: " + String.format("%.1f%%", memoryUsage * 100));
                result.addAnomaly("high_memory_usage");
                result.addDetail("memoryUsage", memoryUsage);
                result.addRecommendation("restart", "考虑重启服务");
                result.addRecommendation("scale", "增加内存或扩展实例");
            } else if (memoryUsage > 0.8) {
                result = AnomalyResult.warning(TYPE, "内存使用率偏高: " + String.format("%.1f%%", memoryUsage * 100));
                result.addAnomaly("elevated_memory_usage");
                result.addDetail("memoryUsage", memoryUsage);
            }
        }
        
        if (metrics.containsKey("cpuUsage")) {
            double cpuUsage = ((Number) metrics.get("cpuUsage")).doubleValue();
            if (cpuUsage > 0.95) {
                result = AnomalyResult.critical(TYPE, "CPU使用率过高: " + String.format("%.1f%%", cpuUsage * 100));
                result.addAnomaly("high_cpu_usage");
                result.addDetail("cpuUsage", cpuUsage);
                result.addRecommendation("scale_out", "扩展服务实例");
            } else if (cpuUsage > 0.8) {
                result = AnomalyResult.warning(TYPE, "CPU使用率偏高: " + String.format("%.1f%%", cpuUsage * 100));
                result.addAnomaly("elevated_cpu_usage");
                result.addDetail("cpuUsage", cpuUsage);
            }
        }
        
        return result;
    }
    
    @Override
    public String getDetectorType() {
        return TYPE;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void enable() {
        this.enabled = true;
        log.info("Performance anomaly detector enabled");
    }
    
    @Override
    public void disable() {
        this.enabled = false;
        log.info("Performance anomaly detector disabled");
    }
    
    public void recordRequest(String endpoint, long responseTime, boolean error) {
        requestCounts.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        totalResponseTime.computeIfAbsent(endpoint, k -> new AtomicLong()).addAndGet(responseTime);
        if (error) {
            errorCounts.computeIfAbsent(endpoint, k -> new AtomicLong()).incrementAndGet();
        }
    }
    
    public double getErrorRate(String endpoint) {
        long total = requestCounts.getOrDefault(endpoint, new AtomicLong()).get();
        if (total == 0) return 0.0;
        long errors = errorCounts.getOrDefault(endpoint, new AtomicLong()).get();
        return (double) errors / total;
    }
    
    public double getAverageResponseTime(String endpoint) {
        long total = requestCounts.getOrDefault(endpoint, new AtomicLong()).get();
        if (total == 0) return 0.0;
        long totalTime = totalResponseTime.getOrDefault(endpoint, new AtomicLong()).get();
        return (double) totalTime / total;
    }
}
