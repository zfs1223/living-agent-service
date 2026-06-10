package com.livingagent.gateway.security;

import com.livingagent.core.database.entity.ArtifactRecordEntity;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产物访问权限服务
 *
 * 详细参考：HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.18
 *
 * 权限规则：
 * - 董事长/FULL 全部可见
 * - 创建者 / 参与者始终可见
 * - PUBLIC：跨部门公开
 * - DEPARTMENT：本部门全员可见；部门领导可见本部门所有产物
 * - PRIVATE：仅创建者/参与者
 * - RESTRICTED：仅指定 viewerDepartments
 */
@Service
public class ArtifactAccessService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactAccessService.class);

    /**
     * 判断当前用户是否有权查看指定产物
     */
    public boolean canView(ArtifactRecordEntity record, AuthContext user) {
        if (record == null || user == null) return false;

        // 1. 董事长/FULL 全部可见
        if (isChairman(user)) {
            return true;
        }

        // 2. 创建者 / 参与者始终可见
        String userId = user.getEmployeeId() != null ? user.getEmployeeId() : user.getEmail();
        if (record.getCreatedBy() != null && record.getCreatedBy().equals(userId)) {
            return true;
        }
        if (record.getParticipantIds() != null && !record.getParticipantIds().isBlank()) {
            List<String> participants = Arrays.asList(record.getParticipantIds().split(","));
            if (userId != null && participants.contains(userId)) {
                return true;
            }
        }

        // 3. 根据 visibility 判断
        String visibility = record.getVisibility() == null ? "DEPARTMENT" : record.getVisibility();
        switch (visibility) {
            case "PUBLIC":
                return true;
            case "DEPARTMENT":
                if (isSameDepartment(record.getDepartment(), user)) {
                    return true;
                }
                // 部门领导：可见本部门所有产物
                if (Boolean.TRUE.equals(record.getVisibleToLeader())
                    && isDepartmentLeader(user)
                    && isSameDepartment(record.getDepartment(), user)) {
                    return true;
                }
                return false;
            case "PRIVATE":
                return false; // 已在第 2 步处理
            case "RESTRICTED":
                if (record.getViewerDepartments() == null || record.getViewerDepartments().isBlank()) {
                    return false;
                }
                List<String> viewerDepts = Arrays.asList(record.getViewerDepartments().split(","));
                String userDept = user.getDepartment();
                return userDept != null && viewerDepts.contains(userDept);
            default:
                return false;
        }
    }

    /**
     * 批量过滤
     */
    public List<ArtifactRecordEntity> filterVisible(List<ArtifactRecordEntity> records, AuthContext user) {
        if (records == null) return Collections.emptyList();
        return records.stream()
            .filter(r -> canView(r, user))
            .collect(Collectors.toList());
    }

    private boolean isSameDepartment(String recordDept, AuthContext user) {
        if (recordDept == null || user.getDepartment() == null) return false;
        return recordDept.equalsIgnoreCase(user.getDepartment());
    }

    /**
     * 部门领导判断
     * 简化处理：基于 founder 字段（founder=true 视为管理员）
     * 后续可结合 user_role 表
     */
    private boolean isDepartmentLeader(AuthContext user) {
        return user != null && user.isFounder();
    }

    /**
     * 董事长判断
     * 简化处理：FULL 权限 + founder
     */
    private boolean isChairman(AuthContext user) {
        if (user == null) return false;
        return user.getAccessLevel() == AccessLevel.FULL || user.isFounder();
    }
}
