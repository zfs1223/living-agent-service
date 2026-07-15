package com.livingagent.core.cluster.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ClusterHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ClusterHealthMonitor.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, NodeHealth> nodeHealthMap = new ConcurrentHashMap<>();
    private final LongAdder totalRebalances = new LongAdder();

    public ClusterHealthMonitor(@Autowired(required = false) CrossLoopEventBus eventBus) {
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
            rebalanceShards(nodeId);
            if (eventBus != null) {
                eventBus.publish(58, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                    Map.of("content", String.format("Node %s is unhealthy, rebalance triggered", nodeId), "source", "cluster"));
            }
        }
    }

    public void recordRebalance(String reason) {
        totalRebalances.increment();
        log.info("[闭环58] 触发重平衡: reason={}", reason);
    }

    public void rebalanceShards(String failedNodeId) {
        NodeHealth failedNode = nodeHealthMap.get(failedNodeId);
        if (failedNode == null) return;

        int totalNodes = nodeHealthMap.size();
        long healthyNodes = nodeHealthMap.values().stream().filter(h -> h.healthy).count();

        if (healthyNodes == 0) {
            log.warn("[闭环58] 无健康节点可用于重平衡");
            return;
        }

        // 将故障节点的负载重分配到健康节点
        double failedLoad = failedNode.loadFactor;
        double loadPerHealthyNode = healthyNodes > 0 ? failedLoad / healthyNodes : 0;

        for (Map.Entry<String, NodeHealth> entry : nodeHealthMap.entrySet()) {
            if (entry.getValue().healthy) {
                entry.getValue().loadFactor += loadPerHealthyNode;
            }
        }
        failedNode.loadFactor = 0;

        totalRebalances.increment();
        log.info("[闭环58] 重平衡完成: failedNode={}, loadRedistributed={} per healthy node, healthyNodes={}",
            failedNodeId, String.format("%.2f", loadPerHealthyNode), healthyNodes);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledClusterHealthCheck() {
        if (nodeHealthMap.isEmpty()) return;
        long unhealthy = nodeHealthMap.values().stream().filter(h -> !h.healthy).count();
        if (unhealthy > 0) {
            for (Map.Entry<String, NodeHealth> entry : nodeHealthMap.entrySet()) {
                if (!entry.getValue().healthy) {
                    rebalanceShards(entry.getKey());
                }
            }
        }
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
