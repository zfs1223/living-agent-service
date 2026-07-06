package com.livingagent.core.autonomy;

import java.util.List;

/**
 * 员工自行领取服务接口。
 *
 * <p>校验员工领取资格 + 乐观锁领取 + 兜底指派。
 */
public interface EmployeeSelfClaimService {

    /**
     * 员工尝试领取待办。
     *
     * <p>校验流程：
     * 1. 职责匹配：待办的 requiredRoles 在员工的 capabilities 中
     * 2. 工具白名单：待办的 requiredTools 是员工工具白名单的子集
     * 3. 部门归属：员工属于发布待办的部门
     * 4. 负载检查：员工当前进行中的任务数未超过上限
     *
     * @param todoItemId   待办项ID
     * @param employeeCode 员工代码
     * @return 领取结果
     */
    TodoClaimResult tryClaim(String todoItemId, String employeeCode);

    /**
     * 员工尝试领取部门内最匹配的待办。
     *
     * @param employeeCode 员工代码
     * @param department   部门代码
     * @return 领取结果
     */
    TodoClaimResult tryClaimBestMatch(String employeeCode, String department);

    /**
     * 大脑兜底指派：将未领取的待办指派给最合适的员工。
     *
     * @param department 部门代码
     * @return 指派结果列表
     */
    List<TodoClaimResult> assignUnclaimed(String department);

    /**
     * 检查员工是否有资格领取指定待办。
     */
    boolean isQualified(String employeeCode, String todoItemId);

    /**
     * 获取员工当前负载（进行中的任务数）。
     */
    int getCurrentLoad(String employeeCode);

    /**
     * 获取员工最大负载上限。
     */
    int getMaxLoad();
}
