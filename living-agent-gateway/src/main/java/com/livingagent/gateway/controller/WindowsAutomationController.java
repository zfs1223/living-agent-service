package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.WindowsAutomationNodeEntity;
import com.livingagent.core.database.repository.WindowsAutomationNodeRepository;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.WindowsAppTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Windows 自动化节点管理 API
 * 供客户端 server.py 注册/心跳，以及前端管理节点
 */
@RestController
@RequestMapping("/api/windows-automation")
public class WindowsAutomationController {

    private static final Logger log = LoggerFactory.getLogger(WindowsAutomationController.class);

    @Autowired
    private WindowsAutomationNodeRepository nodeRepository;

    @Autowired
    private ToolRegistry toolRegistry;

    // ==================== 客户端调用 ====================

    /**
     * 客户端注册：server.py 启动时调用
     */
    @PostMapping("/nodes/register")
    public ResponseEntity<Map<String, Object>> registerNode(@RequestBody Map<String, Object> body) {
        String nodeId = (String) body.get("node_id");
        String ipAddress = (String) body.get("ip");
        Integer port = body.get("port") != null ? ((Number) body.get("port")).intValue() : 8765;

        if (nodeId == null || nodeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "node_id 不能为空"));
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "ip 不能为空"));
        }

        Optional<WindowsAutomationNodeEntity> existing = nodeRepository.findByNodeId(nodeId);
        WindowsAutomationNodeEntity entity;

        if (existing.isPresent()) {
            // 更新已有节点（IP 可能变了）
            entity = existing.get();
            entity.setIpAddress(ipAddress);
            entity.setPort(port);
            entity.setStatus("online");
            entity.setLastHeartbeat(Instant.now());
            log.info("节点更新注册: {} -> {}:{}", nodeId, ipAddress, port);
        } else {
            // 新节点注册
            entity = new WindowsAutomationNodeEntity();
            entity.setNodeId(nodeId);
            entity.setIpAddress(ipAddress);
            entity.setPort(port);
            entity.setStatus("online");
            entity.setLastHeartbeat(Instant.now());
            entity.setRegisteredAt(Instant.now());
            entity.setEnabled(true);
            log.info("新节点注册: {} -> {}:{}", nodeId, ipAddress, port);
        }

        // 可选字段
        if (body.get("hostname") != null) entity.setHostname((String) body.get("hostname"));
        if (body.get("cpu_count") != null) entity.setCpuCount(((Number) body.get("cpu_count")).intValue());
        if (body.get("memory_gb") != null) entity.setMemoryGb(((Number) body.get("memory_gb")).doubleValue());
        if (body.get("applications") != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                entity.setApplications(mapper.writeValueAsString(body.get("applications")));
            } catch (Exception e) {
                entity.setApplications(body.get("applications").toString());
            }
        }
        if (body.get("description") != null) entity.setDescription((String) body.get("description"));
        if (body.get("tenant_id") != null) entity.setTenantId((String) body.get("tenant_id"));
        if (body.get("user_id") != null) entity.setUserId((String) body.get("user_id"));

        nodeRepository.save(entity);

        // 同步到 WindowsAppTool 运行时
        syncNodeToTool(entity);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "注册成功",
            "heartbeat_interval", 60
        ));
    }

    /**
     * 客户端心跳：server.py 定时调用
     */
    @PostMapping("/nodes/{nodeId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> body) {

        Optional<WindowsAutomationNodeEntity> existing = nodeRepository.findByNodeId(nodeId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "节点未注册"));
        }

        WindowsAutomationNodeEntity entity = existing.get();
        entity.setStatus("online");
        entity.setLastHeartbeat(Instant.now());

        // 更新 IP（DHCP 环境下 IP 可能变化）
        if (body.get("ip") != null) {
            String newIp = (String) body.get("ip");
            if (!newIp.equals(entity.getIpAddress())) {
                log.info("节点 {} IP 变更: {} -> {}", nodeId, entity.getIpAddress(), newIp);
                entity.setIpAddress(newIp);
            }
        }

        nodeRepository.save(entity);

        // 同步到 WindowsAppTool
        syncNodeToTool(entity);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ==================== 前端管理调用 ====================

    /**
     * 列出所有节点
     */
    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> listNodes(
            @RequestParam(required = false) String tenantId) {

        List<WindowsAutomationNodeEntity> nodes;
        if (tenantId != null && !tenantId.isBlank()) {
            nodes = nodeRepository.findByTenantId(tenantId);
        } else {
            nodes = nodeRepository.findAll();
        }

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (WindowsAutomationNodeEntity n : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("node_id", n.getNodeId());
            item.put("ip_address", n.getIpAddress());
            item.put("port", n.getPort());
            item.put("hostname", n.getHostname());
            item.put("cpu_count", n.getCpuCount());
            item.put("memory_gb", n.getMemoryGb());
            item.put("description", n.getDescription());
            item.put("status", n.getStatus());
            item.put("last_heartbeat", n.getLastHeartbeat());
            item.put("registered_at", n.getRegisteredAt());
            item.put("tenant_id", n.getTenantId());
            item.put("user_id", n.getUserId());
            item.put("enabled", n.getEnabled());
            nodeList.add(item);
        }

        return ResponseEntity.ok(Map.of("success", true, "nodes", nodeList, "count", nodeList.size()));
    }

    /**
     * 更新节点信息（启用/禁用、描述等）
     */
    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<Map<String, Object>> updateNode(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> body) {

        Optional<WindowsAutomationNodeEntity> existing = nodeRepository.findByNodeId(nodeId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "节点不存在"));
        }

        WindowsAutomationNodeEntity entity = existing.get();

        if (body.get("description") != null) entity.setDescription((String) body.get("description"));
        if (body.get("enabled") != null) entity.setEnabled((Boolean) body.get("enabled"));
        if (body.get("tenant_id") != null) entity.setTenantId((String) body.get("tenant_id"));

        nodeRepository.save(entity);

        // 同步到 WindowsAppTool
        syncNodeToTool(entity);

        return ResponseEntity.ok(Map.of("success", true, "message", "更新成功"));
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Map<String, Object>> deleteNode(@PathVariable String nodeId) {
        Optional<WindowsAutomationNodeEntity> existing = nodeRepository.findByNodeId(nodeId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "节点不存在"));
        }

        nodeRepository.delete(existing.get());

        // 从 WindowsAppTool 移除
        removeNodeFromTool(nodeId);

        log.info("节点已删除: {}", nodeId);
        return ResponseEntity.ok(Map.of("success", true, "message", "节点已删除"));
    }

    /**
     * 检查节点在线状态
     */
    @GetMapping("/nodes/{nodeId}/status")
    public ResponseEntity<Map<String, Object>> getNodeStatus(@PathVariable String nodeId) {
        Optional<WindowsAutomationNodeEntity> existing = nodeRepository.findByNodeId(nodeId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "节点不存在"));
        }

        WindowsAutomationNodeEntity entity = existing.get();

        // 检查心跳是否超时（超过 90 秒无心跳视为离线）
        boolean isOnline = "online".equals(entity.getStatus());
        if (isOnline && entity.getLastHeartbeat() != null) {
            Instant threshold = Instant.now().minusSeconds(90);
            if (entity.getLastHeartbeat().isBefore(threshold)) {
                entity.setStatus("offline");
                nodeRepository.save(entity);
                isOnline = false;
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "node_id", nodeId,
            "status", isOnline ? "online" : "offline",
            "last_heartbeat", entity.getLastHeartbeat()
        ));
    }

    // ==================== 内部方法 ====================

    private void syncNodeToTool(WindowsAutomationNodeEntity entity) {
        try {
            var toolOpt = toolRegistry.get("windows_app_automation");
            if (toolOpt.isPresent() && toolOpt.get() instanceof WindowsAppTool windowsAppTool) {
                if (Boolean.TRUE.equals(entity.getEnabled())) {
                    String url = "http://" + entity.getIpAddress() + ":" + entity.getPort();
                    windowsAppTool.addNode(
                        entity.getNodeId(),
                        url,
                        entity.getDescription() != null ? entity.getDescription() : entity.getHostname()
                    );
                } else {
                    windowsAppTool.removeNode(entity.getNodeId());
                }
            }
        } catch (Exception e) {
            log.warn("同步节点到 WindowsAppTool 失败: {}", e.getMessage());
        }
    }

    private void removeNodeFromTool(String nodeId) {
        try {
            var toolOpt = toolRegistry.get("windows_app_automation");
            if (toolOpt.isPresent() && toolOpt.get() instanceof WindowsAppTool windowsAppTool) {
                windowsAppTool.removeNode(nodeId);
            }
        } catch (Exception e) {
            log.warn("从 WindowsAppTool 移除节点失败: {}", e.getMessage());
        }
    }
}
