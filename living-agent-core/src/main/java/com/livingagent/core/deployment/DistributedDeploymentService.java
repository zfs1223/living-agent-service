package com.livingagent.core.deployment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DistributedDeploymentService {

    ClusterStatus getClusterStatus();
    
    List<NodeInfo> getNodes();
    
    NodeInfo getNode(String nodeId);
    
    NodeInfo registerNode(NodeRegistration registration);
    
    void unregisterNode(String nodeId);
    
    void heartbeat(String nodeId, NodeHealth health);
    
    List<ShardInfo> getShards();
    
    ShardInfo getShard(String shardId);
    
    void rebalanceShards();
    
    void migrateShard(String shardId, String targetNodeId);
    
    FailoverResult performFailover(String failedNodeId);
    
    ScalingResult scaleOut(int additionalNodes);
    
    ScalingResult scaleIn(int nodesToRemove);
    
    DeploymentPlan createDeploymentPlan(DeploymentRequest request);
    
    void executeDeploymentPlan(String planId);
    
    record ClusterStatus(
        String clusterId,
        String clusterName,
        int totalNodes,
        int healthyNodes,
        int unhealthyNodes,
        long totalVectors,
        long totalKnowledge,
        double overallCpuUsage,
        double overallMemoryUsage,
        double overallStorageUsage,
        ClusterHealth health,
        Instant lastUpdated
    ) {}
    
    enum ClusterHealth {
        HEALTHY,
        DEGRADED,
        CRITICAL,
        UNKNOWN
    }
    
    record NodeInfo(
        String nodeId,
        String nodeName,
        String host,
        int port,
        NodeRole role,
        NodeStatus status,
        double cpuUsage,
        double memoryUsage,
        double storageUsage,
        long vectorCount,
        long knowledgeCount,
        List<String> shardIds,
        Instant registeredAt,
        Instant lastHeartbeat
    ) {}
    
    enum NodeRole {
        MASTER,
        WORKER,
        COORDINATOR,
        STORAGE
    }
    
    enum NodeStatus {
        ONLINE,
        OFFLINE,
        STARTING,
        STOPPING,
        ERROR,
        MAINTENANCE
    }
    
    record NodeRegistration(
        String nodeName,
        String host,
        int port,
        NodeRole role,
        Map<String, Object> capabilities
    ) {}
    
    record NodeHealth(
        double cpuUsage,
        double memoryUsage,
        double storageUsage,
        long vectorCount,
        long knowledgeCount,
        Map<String, Object> metrics
    ) {}
    
    record ShardInfo(
        String shardId,
        String shardName,
        String collectionName,
        String primaryNodeId,
        List<String> replicaNodeIds,
        long vectorCount,
        long sizeBytes,
        ShardStatus status,
        Instant createdAt,
        Instant lastRebalanced
    ) {}
    
    enum ShardStatus {
        ACTIVE,
        REBALANCING,
        MIGRATING,
        OFFLINE,
        ERROR
    }
    
    record FailoverResult(
        boolean success,
        String failedNodeId,
        List<String> promotedShards,
        List<String> recoveredShards,
        String errorMessage,
        long durationMs
    ) {}
    
    record ScalingResult(
        boolean success,
        int previousNodeCount,
        int newNodeCount,
        List<String> addedNodeIds,
        List<String> removedNodeIds,
        List<String> rebalancedShards,
        String errorMessage
    ) {}
    
    record DeploymentRequest(
        String environment,
        int nodeCount,
        int replicationFactor,
        int shardCount,
        Map<String, String> nodeConfigs,
        Map<String, Object> options
    ) {}
    
    record DeploymentPlan(
        String planId,
        String environment,
        List<DeploymentStep> steps,
        int estimatedDurationMinutes,
        Map<String, Object> configuration,
        Instant createdAt
    ) {}
    
    record DeploymentStep(
        int stepNumber,
        String action,
        String description,
        Map<String, Object> parameters,
        Duration estimatedDuration
    ) {}
    
    record Duration(
        long minutes
    ) {}
}
