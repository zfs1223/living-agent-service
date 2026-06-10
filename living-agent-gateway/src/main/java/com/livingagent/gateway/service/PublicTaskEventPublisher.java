package com.livingagent.gateway.service;

import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共任务事件发布器
 *
 * 详细参考：HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.19
 * - 任务发布到公共任务栏时广播
 * - 任务被接取时广播
 * - 桌面端通过 WebSocket 监听，触发托盘红点 + OS 通知
 */
@Service
public class PublicTaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PublicTaskEventPublisher.class);

    private final DepartmentWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public PublicTaskEventPublisher(
            DepartmentWebSocketHandler webSocketHandler,
            ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * 任务发布到公共任务栏
     */
    public void publishTaskCreated(String department, Map<String, Object> taskData) {
        broadcastEvent(department, "public_task_published", taskData);
    }

    /**
     * 任务状态更新（优先级变更、奖励调整等）
     */
    public void publishTaskUpdated(String department, Map<String, Object> taskData) {
        broadcastEvent(department, "public_task_updated", taskData);
    }

    /**
     * 任务被接取
     */
    public void publishTaskClaimed(String department, String taskId, String employeeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("employeeId", employeeId);
        data.put("claimedAt", System.currentTimeMillis());
        broadcastEvent(department, "public_task_claimed", data);
    }

    /**
     * 广播事件到指定部门（"ALL" 表示全企业）
     */
    private void broadcastEvent(String department, String type, Map<String, Object> data) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            String json = objectMapper.writeValueAsString(message);
            if ("ALL".equals(department) || department == null || department.isBlank()) {
                // 全企业广播：复用 departmentChannels 但要遍历所有部门
                webSocketHandler.broadcastToAllDepartments(json);
            } else {
                webSocketHandler.broadcastRawJson(department, json);
            }
            log.info("Published event {} for department {}", type, department);
        } catch (Exception e) {
            log.error("Failed to publish event {}: {}", type, e.getMessage(), e);
        }
    }
}
