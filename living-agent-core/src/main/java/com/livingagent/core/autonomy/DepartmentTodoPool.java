package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Optional;

/**
 * 部门待办池接口。
 *
 * <p>管理部门内的待办任务，支持发布、领取、查询操作。
 * 后续可替换为 Redis 实现以支持分布式场景。
 */
public interface DepartmentTodoPool {

    /**
     * 发布待办到部门待办池。
     *
     * @param item 待办项
     */
    void publish(DepartmentTodoItem item);

    /**
     * 批量发布待办。
     */
    void publishAll(List<DepartmentTodoItem> items);

    /**
     * 员工领取待办（乐观锁）。
     *
     * @param todoItemId   待办项ID
     * @param employeeCode 员工代码
     * @return 领取结果
     */
    TodoClaimResult claim(String todoItemId, String employeeCode);

    /**
     * 大脑兜底指派待办。
     *
     * @param todoItemId   待办项ID
     * @param employeeCode 指派员工代码
     * @return true=指派成功
     */
    boolean assign(String todoItemId, String employeeCode);

    /**
     * 获取待办项。
     */
    Optional<DepartmentTodoItem> get(String todoItemId);

    /**
     * 获取部门内所有待领取的待办。
     */
    List<DepartmentTodoItem> getPendingByDepartment(String department);

    /**
     * 获取部门内所有待办（含已领取）。
     */
    List<DepartmentTodoItem> getAllByDepartment(String department);

    /**
     * 获取员工已领取的待办。
     */
    List<DepartmentTodoItem> getClaimedByEmployee(String employeeCode);

    /**
     * 标记待办为进行中。
     */
    void startProgress(String todoItemId);

    /**
     * 标记待办为已完成。
     */
    void complete(String todoItemId);

    /**
     * 取消待办。
     */
    void cancel(String todoItemId);

    /**
     * 获取部门待办数量。
     */
    int countByDepartment(String department);

    /**
     * 获取部门待领取待办数量。
     */
    int countPendingByDepartment(String department);

    /**
     * 清空部门待办池。
     */
    void clearByDepartment(String department);
}
