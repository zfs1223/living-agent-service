package com.livingagent.core.evolution.voc.impl;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.evolution.voc.CustomerValueService;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.signal.EvolutionSignal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * P12: 客户价值服务默认实现。
 *
 * 核心功能：
 * - 监听 VOC EvolutionSignal 事件并聚合客户价值指标
 * - 计算 Net Value Score = (NEED×1.0 + PAIN×1.5 + PRAISE×0.5) / 总量
 * - 关联闭环54能力价值矩阵
 * - 定期发布客户价值 CrossLoopEvent
 */
@Service
public class DefaultCustomerValueService implements CustomerValueService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCustomerValueService.class);

    private static final double NEED_WEIGHT = 1.0;
    private static final double PAIN_WEIGHT = 1.5;
    private static final double PRAISE_WEIGHT = 0.5;

    private final CrossLoopEventBus eventBus;

    /** VOC 聚合数据：brainDomain → VOCAccumulator */
    private final Map<String, VOCAccumulator> vocData = new ConcurrentHashMap<>();

    public DefaultCustomerValueService(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 监听 VOC 类型的 EvolutionSignal，自动聚合。
     */
    @EventListener
    public void onVOCSignal(EvolutionSignal signal) {
        if (signal.getCategory() != EvolutionSignal.SignalCategory.VOC) return;

        String brainDomain = signal.getBrainDomain() != null ? signal.getBrainDomain() : "unknown";
        vocData.computeIfAbsent(brainDomain, k -> new VOCAccumulator()).record(signal);
    }

    @Override
    public void recordVOC(String brainDomain, String vocType, double confidence, String content) {
        SignalType type = switch (vocType.toUpperCase()) {
            case "USER_NEED" -> SignalType.USER_NEED;
            case "USER_PAIN" -> SignalType.USER_PAIN;
            case "USER_PRAISE" -> SignalType.USER_PRAISE;
            default -> SignalType.USER_NEED;
        };
        vocData.computeIfAbsent(brainDomain, k -> new VOCAccumulator()).record(type, confidence);
    }

    @Override
    public CustomerValueMetrics getMetrics(String brainDomain) {
        VOCAccumulator acc = vocData.get(brainDomain);
        if (acc == null) {
            return new CustomerValueMetrics(brainDomain, 0, 0, 0, 0, 0, 0, 0, 0, Instant.now());
        }

        int total = acc.needCount.get() + acc.painCount.get() + acc.praiseCount.get();
        double needScore = acc.needConfidence.get();
        double painScore = acc.painConfidence.get();
        double praiseScore = acc.praiseConfidence.get();

        double netValue = total > 0
            ? (needScore * NEED_WEIGHT + painScore * PAIN_WEIGHT + praiseScore * PRAISE_WEIGHT) / total
            : 0;

        return new CustomerValueMetrics(
            brainDomain, needScore, painScore, praiseScore, netValue,
            total, acc.needCount.get(), acc.painCount.get(), acc.praiseCount.get(),
            Instant.now());
    }

    @Override
    public Map<String, CustomerValueMetrics> getAllMetrics() {
        return vocData.keySet().stream()
            .collect(Collectors.toMap(d -> d, this::getMetrics));
    }

    @Override
    public double getNetValueScore() {
        Map<String, CustomerValueMetrics> all = getAllMetrics();
        if (all.isEmpty()) return 0;
        return all.values().stream()
            .mapToDouble(CustomerValueMetrics::netValueScore)
            .average().orElse(0);
    }

    @Override
    public CapabilityValueMatrix getCapabilityValueMatrix() {
        // P12-2: 简化实现 — 基于客户价值数据构建能力价值矩阵
        Map<String, Double> capabilityScores = new HashMap<>();
        Map<String, Double> valueContributions = new HashMap<>();

        for (Map.Entry<String, CustomerValueMetrics> entry : getAllMetrics().entrySet()) {
            String domain = entry.getKey();
            CustomerValueMetrics m = entry.getValue();
            capabilityScores.put(domain, m.netValueScore());
            valueContributions.put(domain, m.praiseScore() - m.painScore());
        }

        // 识别高价值低能力和低价值高能力
        double avgValue = valueContributions.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgCapability = capabilityScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        List<String> highValueLow = valueContributions.entrySet().stream()
            .filter(e -> e.getValue() > avgValue && capabilityScores.getOrDefault(e.getKey(), 0.0) < avgCapability)
            .map(Map.Entry::getKey)
            .toList();

        List<String> lowValueHigh = valueContributions.entrySet().stream()
            .filter(e -> e.getValue() <= avgValue && capabilityScores.getOrDefault(e.getKey(), 0.0) >= avgCapability)
            .map(Map.Entry::getKey)
            .toList();

        return new CapabilityValueMatrix(capabilityScores, valueContributions, highValueLow, lowValueHigh, Instant.now());
    }

    /**
     * 定期（每小时）计算并发布客户价值 CrossLoopEvent。
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void publishCustomerValueEvent() {
        double netValue = getNetValueScore();
        Map<String, CustomerValueMetrics> allMetrics = getAllMetrics();

        if (eventBus != null && !allMetrics.isEmpty()) {
            eventBus.publish(66, "customer_value_update",
                CrossLoopEvent.EventPriority.KNOWLEDGE,
                Map.of("netValueScore", netValue,
                    "domainCount", allMetrics.size(),
                    "totalVOC", allMetrics.values().stream().mapToInt(CustomerValueMetrics::totalVOCCount).sum()));
        }

        log.debug("[P12/VOC] 客户价值更新: netValue={:.2f}, domains={}", netValue, allMetrics.size());
    }

    /** VOC 累加器 */
    private static class VOCAccumulator {
        final AtomicInteger needCount = new AtomicInteger(0);
        final AtomicInteger painCount = new AtomicInteger(0);
        final AtomicInteger praiseCount = new AtomicInteger(0);
        final AtomicInteger needConfidence = new AtomicInteger(0); // ×100 存储
        final AtomicInteger painConfidence = new AtomicInteger(0);
        final AtomicInteger praiseConfidence = new AtomicInteger(0);

        void record(EvolutionSignal signal) {
            record(signal.getType(), signal.getConfidence());
        }

        void record(SignalType type, double confidence) {
            int confInt = (int) (confidence * 100);
            switch (type) {
                case USER_NEED -> { needCount.incrementAndGet(); needConfidence.addAndGet(confInt); }
                case USER_PAIN -> { painCount.incrementAndGet(); painConfidence.addAndGet(confInt); }
                case USER_PRAISE -> { praiseCount.incrementAndGet(); praiseConfidence.addAndGet(confInt); }
                default -> {}
            }
        }
    }
}
