package com.livingagent.core.cluster.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class ClusterHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, NodeHealth> nodeHealthMap = new ConcurrentHashMap<>();
    private final LongAdder totalRebalances = new LongAdder();

    public ClusterHealthMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void registerNode(String nodeId) {
        nodeHealthMap.computeIfAbsent(nodeId, k -> new NodeHealth());
        log.info("[闭环58] 节点注册: id={}", nodeId);
    }

    public void removeNode(String nodeId) {
        nodeHealthMap.remove(nodeId);
    }

    public void recordNodeHealth(String nodeId, boolean healthy, double loadFactor) {
        NodeHealth health = nodeHealthMap.computeIfAbsent(nodeId, k -> new NodeHealth());
        health.healthy = healthy;
        health.loadFactor = loadFactor;
        health.checkCount.increment();

        if (!healthy) {
            log.warn("[闭环58] 节点故障: id={}", nodeId);
            eventBus.publish(58, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                Map.of("content", String.format("Node %s is unhealthy, triggering rebalance", nodeId), "source", "cluster"));
        }
    }

    public void recordRebalance(String reason) {
        totalRebalances.increment();
        log.info("[闭环58] 触发重平衡: reason={}", reason);
    }

    public ClusterHealthReport getReport() {
        int total = nodeHealthMap.size();
        long unhealthy = nodeHealthMap.values().stream().filter(h -> !h.healthy).count();
        double avgLoad = total > 0
            ? nodeHealthMap.values().stream().mapToDouble(h -> h.loadFactor).average().orElse(0)
            : 0;
        return new ClusterHealthReport(total, (int) unhealthy, avgLoad, totalRebalances.sum());
    }

    public static class NodeHealth {
        boolean healthy = true;
        double loadFactor;
        LongAdder checkCount = new LongAdder();
    }

    public record ClusterHealthReport(int totalNodes, int unhealthyNodes,
                                       double avgLoadFactor, long rebalanceCount) {}
}
