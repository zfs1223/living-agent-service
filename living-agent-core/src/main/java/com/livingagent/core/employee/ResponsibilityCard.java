package com.livingagent.core.employee;

import java.time.Instant;
import java.util.List;

/**
 * 职责卡 - 定义员工的职责范围、能力和可用工具
 *
 * 数字员工通过 FixedEmployeeRegistry 固化职责，
 * 人类员工通过 ResponsibilityCard 动态管理职责。
 */
public record ResponsibilityCard(
    String cardId,
    String employeeId,
    String department,
    String position,
    List<String> roles,
    List<String> capabilities,
    List<String> tools,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
    public static ResponsibilityCard create(
            String employeeId,
            String department,
            String position,
            List<String> roles,
            List<String> capabilities,
            List<String> tools) {
        return new ResponsibilityCard(
            "rc_" + System.currentTimeMillis(),
            employeeId,
            department,
            position,
            List.copyOf(roles),
            List.copyOf(capabilities),
            List.copyOf(tools),
            null,
            Instant.now(),
            Instant.now()
        );
    }

    public ResponsibilityCard withDescription(String description) {
        return new ResponsibilityCard(
            cardId, employeeId, department, position,
            roles, capabilities, tools, description,
            createdAt, Instant.now()
        );
    }

    public ResponsibilityCard withRoles(List<String> roles) {
        return new ResponsibilityCard(
            cardId, employeeId, department, position,
            List.copyOf(roles), capabilities, tools, description,
            createdAt, Instant.now()
        );
    }

    public ResponsibilityCard withCapabilities(List<String> capabilities) {
        return new ResponsibilityCard(
            cardId, employeeId, department, position,
            roles, List.copyOf(capabilities), tools, description,
            createdAt, Instant.now()
        );
    }

    public ResponsibilityCard withTools(List<String> tools) {
        return new ResponsibilityCard(
            cardId, employeeId, department, position,
            roles, capabilities, List.copyOf(tools), description,
            createdAt, Instant.now()
        );
    }
}
