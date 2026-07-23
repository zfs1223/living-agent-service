package com.livingagent.core.evolution.tpi.impl;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.evolution.tpi.ProcessEfficiencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * P9-1: 流程效率度量服务默认实现。
 *
 * TPI 核心功能：
 * - 记录每个流程步骤的耗时和成功率
 * - 计算节拍时间、周期时间、VA比、FTY
 * - 定期检测瓶颈并发布 CrossLoopEvent
 */
@Service
public class DefaultProcessEfficiencyService implements ProcessEfficiencyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessEfficiencyService.class);

    // 默认节拍时间（毫秒）：用户可接受最大响应时间
    private static final double DEFAULT_TAKT_TIME_MS = 5000.0;

    private final CrossLoopEventBus eventBus;

    /** 流程步骤数据：processName → stepName → StepAccumulator */
    private final Map<String, Map<String, StepAccumulator>> processData = new ConcurrentHashMap<>();

    /** 流程配置：processName → taktTimeMs */
    private final Map<String, Double> taktTimeConfig = new ConcurrentHashMap<>();

    public DefaultProcessEfficiencyService(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;

        // 初始化核心流程的节拍时间配置
        taktTimeConfig.put("conversation_processing", 5000.0);
        taktTimeConfig.put("task_dispatch", 3000.0);
        taktTimeConfig.put("employee_execution", 10000.0);
        taktTimeConfig.put("websocket_handling", 2000.0);
    }

    @Override
    public ProcessEfficiencyMetrics getMetrics(String processName) {
        Map<String, StepAccumulator> steps = processData.get(processName);
        if (steps == null || steps.isEmpty()) {
            double taktTime = taktTimeConfig.getOrDefault(processName, DEFAULT_TAKT_TIME_MS);
            return new ProcessEfficiencyMetrics(processName, taktTime, 0, 0, 0, 0, 0, 0, Instant.now());
        }

        double totalDuration = 0;
        double valueAddedDuration = 0;
        long totalSuccess = 0;
        long totalSamples = 0;
        int valueAddedSteps = 0;

        for (StepAccumulator acc : steps.values()) {
            totalDuration += acc.avgDurationMs;
            if (acc.isValueAdded) {
                valueAddedDuration += acc.avgDurationMs;
                valueAddedSteps++;
            }
            totalSuccess += acc.successCount.get();
            totalSamples += acc.totalCount.get();
        }

        double taktTime = taktTimeConfig.getOrDefault(processName, DEFAULT_TAKT_TIME_MS);
        double vaRatio = totalDuration > 0 ? valueAddedDuration / totalDuration : 0;
        double fty = totalSamples > 0 ? (double) totalSuccess / totalSamples : 0;

        return new ProcessEfficiencyMetrics(
            processName, taktTime, totalDuration, vaRatio, fty,
            steps.size(), valueAddedSteps, (int) totalSamples, Instant.now());
    }

    @Override
    public Map<String, ProcessEfficiencyMetrics> getMetricsBatch(List<String> processNames) {
        return processNames.stream()
            .collect(Collectors.toMap(name -> name, this::getMetrics));
    }

    @Override
    public void recordStep(String processName, String stepName, boolean isValueAdded, long durationMs, boolean success) {
        processData.computeIfAbsent(processName, k -> new ConcurrentHashMap<>())
            .compute(stepName, (k, existing) -> {
                if (existing == null) {
                    StepAccumulator acc = new StepAccumulator(isValueAdded);
                    acc.record(durationMs, success);
                    return acc;
                }
                existing.record(durationMs, success);
                return existing;
            });
    }

    @Override
    public ProcessEfficiencyOverview getOverview() {
        List<String> allProcesses = new ArrayList<>(processData.keySet());
        List<ProcessEfficiencyMetrics> allMetrics = allProcesses.stream()
            .map(this::getMetrics)
            .toList();

        int bottleneckCount = (int) allMetrics.stream().filter(ProcessEfficiencyMetrics::isBottleneck).count();
        double avgVaRatio = allMetrics.stream().mapToDouble(ProcessEfficiencyMetrics::vaRatio).average().orElse(0);
        double avgFty = allMetrics.stream().mapToDouble(ProcessEfficiencyMetrics::fty).average().orElse(0);
        List<String> bottlenecks = allMetrics.stream()
            .filter(ProcessEfficiencyMetrics::isBottleneck)
            .map(ProcessEfficiencyMetrics::processName)
            .toList();

        return new ProcessEfficiencyOverview(allProcesses.size(), bottleneckCount, avgVaRatio, avgFty, bottlenecks, Instant.now());
    }

    /**
     * P9-2: 定期检测流程瓶颈并告警。
     * 每30分钟执行一次。
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void detectBottlenecks() {
        ProcessEfficiencyOverview overview = getOverview();

        if (overview.bottleneckCount() > 0 && eventBus != null) {
            for (String bottleneck : overview.bottleneckProcesses()) {
                ProcessEfficiencyMetrics metrics = getMetrics(bottleneck);

                // 瓶颈告警级别
                String alertLevel;
                if (metrics.cycleTimeMs() > metrics.taktTimeMs() * 10) {
                    alertLevel = "CRITICAL";
                } else if (metrics.cycleTimeMs() > metrics.taktTimeMs() * 5) {
                    alertLevel = "HIGH";
                } else {
                    alertLevel = "WARN";
                }

                log.warn("[P9-2/TPI] 流程瓶颈检测: process={}, level={}, cycleTime={}ms, taktTime={}ms, VA={:.1f}%",
                    bottleneck, alertLevel,
                    String.format("%.0f", metrics.cycleTimeMs()),
                    String.format("%.0f", metrics.taktTimeMs()),
                    metrics.vaRatio() * 100);

                eventBus.publish(9, "tpi_bottleneck_detected",
                    "CRITICAL".equals(alertLevel) ? CrossLoopEvent.EventPriority.SELF_HEALING :
                    "HIGH".equals(alertLevel) ? CrossLoopEvent.EventPriority.DEGRADATION :
                    CrossLoopEvent.EventPriority.KNOWLEDGE,
                    Map.of("processName", bottleneck,
                        "alertLevel", alertLevel,
                        "cycleTimeMs", metrics.cycleTimeMs(),
                        "taktTimeMs", metrics.taktTimeMs(),
                        "vaRatio", metrics.vaRatio(),
                        "fty", metrics.fty()));
            }
        }
    }

    /** 步骤数据累加器 */
    private static class StepAccumulator {
        final boolean isValueAdded;
        final AtomicLong totalCount = new AtomicLong(0);
        final AtomicLong successCount = new AtomicLong(0);
        final AtomicLong totalDurationMs = new AtomicLong(0);
        volatile double avgDurationMs;

        StepAccumulator(boolean isValueAdded) {
            this.isValueAdded = isValueAdded;
        }

        void record(long durationMs, boolean success) {
            totalCount.incrementAndGet();
            if (success) successCount.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            avgDurationMs = (double) totalDurationMs.get() / totalCount.get();
        }
    }
}
