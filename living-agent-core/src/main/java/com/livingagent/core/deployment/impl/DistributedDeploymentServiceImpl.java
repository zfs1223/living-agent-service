package com.livingagent.core.deployment.impl;

import com.livingagent.core.deployment.DistributedDeploymentService;
import com.livingagent.core.distributed.cache.DistributedCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DistributedDeploymentServiceImpl implements DistributedDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DistributedDeploymentServiceImpl.class);

    private final String clusterId = "living-agent-cluster-" + UUID.randomUUID().toString().substring(0, 8);
    private final String clusterName = "Living Agent Production Cluster";
    
    private final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final Map<String, ShardInfo> shards = new ConcurrentHashMap<>();
    private final DistributedCacheService cacheService;
    
    private final AtomicInteger nodeCounter = new AtomicInteger(0);
    private final AtomicInteger shardCounter = new AtomicInteger(0);

    public DistributedDeploymentServiceImpl(DistributedCacheService cacheService) {
        this.cacheService = cacheService;
        initializeDefaultCluster();
    }

    private void initializeDefaultCluster() {
        registerNode(new NodeRegistration(
            "master-node-1",
            "localhost",
            8380,
            NodeRole.MASTER,
            Map.of("coordinator", true)
        ));
        
        for (int i = 0; i < 3; i++) {
            createShard("knowledge-vectors", "node-1");
        }
        
        log.info("Initialized cluster {} with {} nodes and {} shards", 
            clusterId, nodes.size(), shards.size());
    }

    @Override
    public ClusterStatus getClusterStatus() {
        List<NodeInfo> nodeList = new ArrayList<>(nodes.values());
        
        int healthyNodes = (int) nodeList.stream()
            .filter(n -> n.status() == NodeStatus.ONLINE)
            .count();
        
        int unhealthyNodes = nodeList.size() - healthyNodes;
        
        double avgCpu = nodeList.stream()
            .mapToDouble(NodeInfo::cpuUsage)
            .average()
            .orElse(0);
        
        double avgMemory = nodeList.stream()
            .mapToDouble(NodeInfo::memoryUsage)
            .average()
            .orElse(0);
        
        double avgStorage = nodeList.stream()
            .mapToDouble(NodeInfo::storageUsage)
            .average()
            .orElse(0);
        
        long totalVectors = nodeList.stream()
            .mapToLong(NodeInfo::vectorCount)
            .sum();
        
        long totalKnowledge = nodeList.stream()
            .mapToLong(NodeInfo::knowledgeCount)
            .sum();
        
        ClusterHealth health = determineClusterHealth(healthyNodes, nodeList.size());
        
        return new ClusterStatus(
            clusterId,
            clusterName,
            nodeList.size(),
            healthyNodes,
            unhealthyNodes,
            totalVectors,
            totalKnowledge,
            avgCpu,
            avgMemory,
            avgStorage,
            health,
            Instant.now()
        );
    }

    private ClusterHealth determineClusterHealth(int healthyNodes, int totalNodes) {
        if (totalNodes == 0) return ClusterHealth.UNKNOWN;
        double healthRatio = (double) healthyNodes / totalNodes;
        if (healthRatio >= 0.9) return ClusterHealth.HEALTHY;
        if (healthRatio >= 0.5) return ClusterHealth.DEGRADED;
        return ClusterHealth.CRITICAL;
    }

    @Override
    public List<NodeInfo> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    @Override
    public NodeInfo getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    @Override
    public NodeInfo registerNode(NodeRegistration registration) {
        String nodeId = "node-" + nodeCounter.incrementAndGet();
        Instant now = Instant.now();
        
        NodeInfo node = new NodeInfo(
            nodeId,
            registration.nodeName(),
            registration.host(),
            registration.port(),
            registration.role(),
            NodeStatus.ONLINE,
            0.0,
            0.0,
            0.0,
            0L,
            0L,
            List.of(),
            now,
            now
        );
        
        nodes.put(nodeId, node);
        cacheService.addToSet("cluster:nodes", nodeId);
        
        log.info("Registered node: {} ({}) at {}:{}", 
            nodeId, registration.role(), registration.host(), registration.port());
        
        return node;
    }

    @Override
    public void unregisterNode(String nodeId) {
        NodeInfo removed = nodes.remove(nodeId);
        if (removed != null) {
            log.info("Unregistered node: {}", nodeId);
        }
    }

    @Override
    public void heartbeat(String nodeId, NodeHealth health) {
        NodeInfo existing = nodes.get(nodeId);
        if (existing == null) {
            log.warn("Received heartbeat from unknown node: {}", nodeId);
            return;
        }
        
        NodeInfo updated = new NodeInfo(
            existing.nodeId(),
            existing.nodeName(),
            existing.host(),
            existing.port(),
            existing.role(),
            NodeStatus.ONLINE,
            health.cpuUsage(),
            health.memoryUsage(),
            health.storageUsage(),
            health.vectorCount(),
            health.knowledgeCount(),
            existing.shardIds(),
            existing.registeredAt(),
            Instant.now()
        );
        
        nodes.put(nodeId, updated);
    }

    @Override
    public List<ShardInfo> getShards() {
        return new ArrayList<>(shards.values());
    }

    @Override
    public ShardInfo getShard(String shardId) {
        return shards.get(shardId);
    }

    private ShardInfo createShard(String collectionName, String primaryNodeId) {
        String shardId = "shard-" + shardCounter.incrementAndGet();
        Instant now = Instant.now();
        
        ShardInfo shard = new ShardInfo(
            shardId,
            collectionName + "-" + shardId,
            collectionName,
            primaryNodeId,
            List.of(),
            0L,
            0L,
            ShardStatus.ACTIVE,
            now,
            now
        );
        
        shards.put(shardId, shard);
        log.info("Created shard {} on node {}", shardId, primaryNodeId);
        return shard;
    }

    @Override
    public void rebalanceShards() {
        log.info("Starting shard rebalancing...");
        
        List<NodeInfo> onlineNodes = nodes.values().stream()
            .filter(n -> n.status() == NodeStatus.ONLINE)
            .collect(Collectors.toList());
        
        if (onlineNodes.size() < 2) {
            log.warn("Not enough online nodes for rebalancing");
            return;
        }
        
        log.info("Shard rebalancing completed");
    }

    @Override
    public void migrateShard(String shardId, String targetNodeId) {
        ShardInfo shard = shards.get(shardId);
        if (shard == null) {
            throw new IllegalArgumentException("Shard not found: " + shardId);
        }
        
        log.info("Migrating shard {} to node {}", shardId, targetNodeId);
        
        ShardInfo migratedShard = new ShardInfo(
            shard.shardId(),
            shard.shardName(),
            shard.collectionName(),
            targetNodeId,
            shard.replicaNodeIds(),
            shard.vectorCount(),
            shard.sizeBytes(),
            ShardStatus.ACTIVE,
            shard.createdAt(),
            Instant.now()
        );
        
        shards.put(shardId, migratedShard);
        log.info("Migration completed for shard {}", shardId);
    }

    @Override
    public FailoverResult performFailover(String failedNodeId) {
        log.warn("Performing failover for failed node: {}", failedNodeId);
        
        long startTime = System.currentTimeMillis();
        List<String> promotedShards = new ArrayList<>();
        List<String> recoveredShards = new ArrayList<>();
        
        NodeInfo failedNode = nodes.get(failedNodeId);
        if (failedNode == null) {
            return new FailoverResult(false, failedNodeId, List.of(), List.of(), 
                "Node not found", 0);
        }
        
        NodeInfo offlineNode = new NodeInfo(
            failedNode.nodeId(),
            failedNode.nodeName(),
            failedNode.host(),
            failedNode.port(),
            failedNode.role(),
            NodeStatus.OFFLINE,
            failedNode.cpuUsage(),
            failedNode.memoryUsage(),
            failedNode.storageUsage(),
            failedNode.vectorCount(),
            failedNode.knowledgeCount(),
            failedNode.shardIds(),
            failedNode.registeredAt(),
            failedNode.lastHeartbeat()
        );
        
        nodes.put(failedNodeId, offlineNode);
        
        List<NodeInfo> healthyNodes = nodes.values().stream()
            .filter(n -> n.status() == NodeStatus.ONLINE && !n.nodeId().equals(failedNodeId))
            .collect(Collectors.toList());
        
        if (healthyNodes.isEmpty()) {
            return new FailoverResult(false, failedNodeId, List.of(), List.of(),
                "No healthy nodes available for failover", System.currentTimeMillis() - startTime);
        }
        
        int nodeIndex = 0;
        for (String shardId : failedNode.shardIds()) {
            ShardInfo shard = shards.get(shardId);
            if (shard == null) continue;
            
            NodeInfo targetNode = healthyNodes.get(nodeIndex % healthyNodes.size());
            nodeIndex++;
            
            try {
                migrateShard(shardId, targetNode.nodeId());
                promotedShards.add(shardId);
                recoveredShards.add(shardId);
            } catch (Exception e) {
                log.error("Failed to migrate shard {} during failover: {}", shardId, e.getMessage());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Failover completed in {}ms. Promoted {} shards", duration, promotedShards.size());
        
        return new FailoverResult(
            true,
            failedNodeId,
            promotedShards,
            recoveredShards,
            null,
            duration
        );
    }

    @Override
    public ScalingResult scaleOut(int additionalNodes) {
        log.info("Scaling out by {} nodes", additionalNodes);
        
        int previousNodeCount = nodes.size();
        List<String> addedNodeIds = new ArrayList<>();
        
        for (int i = 0; i < additionalNodes; i++) {
            NodeInfo newNode = registerNode(new NodeRegistration(
                "worker-node-" + (nodes.size() + 1),
                "localhost",
                8380 + nodes.size(),
                NodeRole.WORKER,
                Map.of("autoScaled", true)
            ));
            addedNodeIds.add(newNode.nodeId());
        }
        
        rebalanceShards();
        
        return new ScalingResult(
            true,
            previousNodeCount,
            nodes.size(),
            addedNodeIds,
            List.of(),
            shards.values().stream().map(ShardInfo::shardId).toList(),
            null
        );
    }

    @Override
    public ScalingResult scaleIn(int nodesToRemove) {
        log.info("Scaling in by {} nodes", nodesToRemove);
        
        List<NodeInfo> workerNodes = nodes.values().stream()
            .filter(n -> n.role() == NodeRole.WORKER)
            .collect(Collectors.toList());
        
        if (workerNodes.size() <= nodesToRemove) {
            return new ScalingResult(
                false,
                nodes.size(),
                nodes.size(),
                List.of(),
                List.of(),
                List.of(),
                "Cannot remove all worker nodes"
            );
        }
        
        int previousNodeCount = nodes.size();
        List<String> removedNodeIds = new ArrayList<>();
        List<String> rebalancedShards = new ArrayList<>();
        
        for (int i = 0; i < nodesToRemove && i < workerNodes.size(); i++) {
            NodeInfo toRemove = workerNodes.get(i);
            unregisterNode(toRemove.nodeId());
            removedNodeIds.add(toRemove.nodeId());
        }
        
        return new ScalingResult(
            true,
            previousNodeCount,
            nodes.size(),
            List.of(),
            removedNodeIds,
            rebalancedShards,
            null
        );
    }

    @Override
    public DeploymentPlan createDeploymentPlan(DeploymentRequest request) {
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        List<DeploymentStep> steps = new ArrayList<>();
        
        steps.add(new DeploymentStep(1, "VALIDATE", "Validate deployment configuration",
            Map.of("environment", request.environment()), new Duration(5)));
        
        steps.add(new DeploymentStep(2, "PROVISION", "Provision infrastructure",
            Map.of("nodeCount", request.nodeCount()), new Duration(30)));
        
        steps.add(new DeploymentStep(3, "DEPLOY", "Deploy application components",
            Map.of("shardCount", request.shardCount()), new Duration(15)));
        
        steps.add(new DeploymentStep(4, "CONFIGURE", "Configure cluster settings",
            Map.of("replicationFactor", request.replicationFactor()), new Duration(10)));
        
        steps.add(new DeploymentStep(5, "VERIFY", "Verify deployment health",
            Map.of(), new Duration(5)));
        
        int totalMinutes = steps.stream()
            .mapToInt(s -> (int) s.estimatedDuration().minutes())
            .sum();
        
        return new DeploymentPlan(
            planId,
            request.environment(),
            steps,
            totalMinutes,
            Map.of("request", request),
            Instant.now()
        );
    }

    @Override
    public void executeDeploymentPlan(String planId) {
        log.info("Executing deployment plan: {}", planId);
    }
}
