package com.livingagent.core.evolution.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * P31-A: 闭环优先级仲裁器。
 * 优先级：安全(30) > 自愈(24) > 降级(27) > 回执(28) > 经济(25) > 知识(26) > 个性(29)
 */
@Service
public class LoopPriorityArbiter {

    private static final Logger log = LoggerFactory.getLogger(LoopPriorityArbiter.class);

    private static final Map<Integer, CrossLoopEvent.EventPriority> LOOP_PRIORITY_MAP = Map.of(
        30, CrossLoopEvent.EventPriority.SECURITY,
        24, CrossLoopEvent.EventPriority.SELF_HEALING,
        27, CrossLoopEvent.EventPriority.DEGRADATION,
        28, CrossLoopEvent.EventPriority.RECEIPT,
        25, CrossLoopEvent.EventPriority.ECONOMY,
        26, CrossLoopEvent.EventPriority.KNOWLEDGE,
        29, CrossLoopEvent.EventPriority.PERSONALITY
    );

    private static final Set<Integer> PARALLEL_SAFE_LOOPS = Set.of(30, 26); // 安全+知识可并行

    public List<CrossLoopEvent> arbitrate(List<CrossLoopEvent> events) {
        if (events == null || events.isEmpty()) return List.of();

        return events.stream()
            .sorted(Comparator.comparingInt(e -> e.getPriority().getOrder()))
            .collect(Collectors.toList());
    }

    public boolean canExecuteInParallel(CrossLoopEvent e1, CrossLoopEvent e2) {
        if (e1 == null || e2 == null) return false;
        // 同优先级可并行
        if (e1.getPriority() == e2.getPriority()) return true;
        // 安全和知识闭环可并行
        return PARALLEL_SAFE_LOOPS.contains(e1.getSourceLoop()) &&
               PARALLEL_SAFE_LOOPS.contains(e2.getSourceLoop());
    }

    public CrossLoopEvent.EventPriority getPriorityForLoop(int loopId) {
        return LOOP_PRIORITY_MAP.getOrDefault(loopId, CrossLoopEvent.EventPriority.PERSONALITY);
    }
}
