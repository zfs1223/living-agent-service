package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * NP1-1: 员工动态装备服务
 * 实现"工具技能闭环"中的"装备"环节
 * 
 * 闭环流程：寻找→匹配→装备→执行→回收
 * 本服务负责"装备"环节：将工具/技能动态分配给员工
 */
public interface EmployeeEquipmentService {

    /**
     * 为员工装备工具
     * @param employeeCode 员工编码
     * @param toolIds 需要装备的工具ID列表
     * @return 装备结果
     */
    EquipmentResult equipTools(String employeeCode, List<String> toolIds);

    /**
     * 为员工装备技能
     * @param employeeCode 员工编码
     * @param skillIds 需要装备的技能ID列表
     * @return 装备结果
     */
    EquipmentResult equipSkills(String employeeCode, List<String> skillIds);

    /**
     * 回收员工的工具/技能（任务完成后）
     * @param employeeCode 员工编码
     * @param taskType 任务类型
     * @return 回收结果
     */
    EquipmentResult recycle(String employeeCode, String taskType);

    /**
     * 获取员工当前装备的工具
     * @param employeeCode 员工编码
     * @return 工具ID列表
     */
    List<String> getEquippedTools(String employeeCode);

    /**
     * 获取员工当前装备的技能
     * @param employeeCode 员工编码
     * @return 技能ID列表
     */
    List<String> getEquippedSkills(String employeeCode);

    /**
     * 检查员工是否拥有所需工具
     * @param employeeCode 员工编码
     * @param requiredTools 所需工具列表
     * @return 缺少的工具列表（空列表表示全部拥有）
     */
    List<String> checkMissingTools(String employeeCode, List<String> requiredTools);

    /**
     * 装备结果
     */
    record EquipmentResult(
        boolean success,
        String employeeCode,
        List<String> equipped,
        List<String> failed,
        List<String> skipped,
        String message,
        Map<String, Object> metadata
    ) {
        public static EquipmentResult success(String employeeCode, List<String> equipped, List<String> skipped) {
            return new EquipmentResult(true, employeeCode, equipped, List.of(), skipped,
                "装备成功: " + equipped.size() + " 项", Map.of());
        }

        public static EquipmentResult partial(String employeeCode, List<String> equipped, List<String> failed) {
            return new EquipmentResult(true, employeeCode, equipped, failed, List.of(),
                "部分装备成功: " + equipped.size() + " 成功, " + failed.size() + " 失败", Map.of());
        }

        public static EquipmentResult failed(String employeeCode, List<String> failed, String reason) {
            return new EquipmentResult(false, employeeCode, List.of(), failed, List.of(), reason, Map.of());
        }
    }
}