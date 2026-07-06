package com.livingagent.core.diagnosis;

import com.livingagent.core.evolution.signal.EvolutionSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P17-C: 前端错误反馈闭环服务。
 * 高频错误模式自动生成 EvolutionSignal，驱动改进。
 */
@Service
public class ErrorReportFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportFeedbackService.class);
    private static final int HIGH_FREQUENCY_THRESHOLD = 3;

    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();

    public ErrorReportFeedbackService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void processErrorReports(List<Map<String, Object>> errors) {
        if (errors == null || errors.isEmpty()) return;

        for (Map<String, Object> error : errors) {
            String type = String.valueOf(error.getOrDefault("type", "unknown"));
            String message = String.valueOf(error.getOrDefault("message", ""));
            String url = String.valueOf(error.getOrDefault("url", ""));

            String key = type + "|" + url;
            int count = errorCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

            if (count == HIGH_FREQUENCY_THRESHOLD) {
                EvolutionSignal signal = new EvolutionSignal(
                    EvolutionSignal.SignalType.ERROR,
                    String.format("High-frequency frontend error [%s]: %s (url=%s, count=%d)",
                        type, message, url, count)
                );
                signal.setSource("frontend-error-report");
                signal.setCategory(EvolutionSignal.SignalCategory.REPAIR);
                signal.addTag("frontend-error");
                signal.addTag("auto-detected");

                try {
                    eventPublisher.publishEvent(signal);
                    log.info("P17-C: Published EvolutionSignal for high-frequency error: {} (count={})", key, count);
                } catch (Exception e) {
                    log.warn("P17-C: Failed to publish EvolutionSignal: {}", e.getMessage());
                }
            }
        }
    }
}
