package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DepartmentTodoItem;
import com.livingagent.core.autonomy.DepartmentTodoPool;
import com.livingagent.core.autonomy.EmployeeSelfClaimService;
import com.livingagent.core.autonomy.TodoClaimResult;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认员工自行领取服务实现。
 *
 * <p>校验资格 + 乐观锁领取 + 兜底指派。
 * 
 * <p>注意：此类不使用 @Service 注解，由 GatewayConfig.employeeSelfClaimService() 方法
 * 通过 @Bean 方式注册，确保构造函数参数正确注入。
 */
public class DefaultEmployeeSelfClaimService implements EmployeeSelfClaimService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmployeeSelfClaimService.class);

    private final DepartmentTodoPool todoPool;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final int maxLoad;

    public DefaultEmployeeSelfClaimService(DepartmentTodoPool todoPool,
                                            FixedEmployeeRegistry fixedEmployeeRegistry) {
        this(todoPool, fixedEmployeeRegistry, 3);
    }

    public DefaultEmployeeSelfClaimService(DepartmentTodoPool todoPool,
                                            FixedEmployeeRegistry fixedEmployeeRegistry,
                                            int maxLoad) {
        this.todoPool = todoPool;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.maxLoad = maxLoad;
    }

    @Override
    public TodoClaimResult tryClaim(String todoItemId, String employeeCode) {
        DepartmentTodoItem item = todoPool.get(todoItemId).orElse(null);
        if (item == null) {
            return TodoClaimResult.notFound(todoItemId);
        }

        if (!item.isPending()) {
            return TodoClaimResult.notPending(todoItemId);
        }

        // 校验资格
        if (!isQualified(employeeCode, todoItemId)) {
            return TodoClaimResult.notQualified(todoItemId, employeeCode);
        }

        // 乐观锁领取
        return todoPool.claim(todoItemId, employeeCode);
    }

    @Override
    public TodoClaimResult tryClaimBestMatch(String employeeCode, String department) {
        FixedEmployeeRegistry.FixedEmployeeDefinition def =
            fixedEmployeeRegistry.getDefinitionByCode(employeeCode).orElse(null);
        if (def == null) {
            log.warn("Employee definition not found: {}", employeeCode);
            return TodoClaimResult.notFound("unknown");
        }

        List<DepartmentTodoItem> pending = todoPool.getPendingByDepartment(department);
        if (pending.isEmpty()) {
            return TodoClaimResult.notFound("none-pending");
        }

        // 按匹配度排序：requiredRoles 匹配数 > requiredCapabilities 匹配数 > priority
        DepartmentTodoItem best = pending.stream()
            .filter(item -> isQualifiedForItem(def, item))
            .min((a, b) -> {
                // 优先级高的优先
                int prioCmp = b.getPriority().compareTo(a.getPriority());
                if (prioCmp != 0) return prioCmp;
                // 角色匹配多的优先
                long matchA = a.getRequiredRoles().stream().filter(def.capabilities()::contains).count();
                long matchB = b.getRequiredRoles().stream().filter(def.capabilities()::contains).count();
                return Long.compare(matchB, matchA);
            })
            .orElse(null);

        if (best == null) {
            log.debug("No qualified todo found for employee {} in department {}", employeeCode, department);
            return TodoClaimResult.notQualified("none", employeeCode);
        }

        return tryClaim(best.getId(), employeeCode);
    }

    @Override
    public List<TodoClaimResult> assignUnclaimed(String department) {
        List<DepartmentTodoItem> pending = todoPool.getPendingByDepartment(department);
        List<TodoClaimResult> results = new ArrayList<>();

        for (DepartmentTodoItem item : pending) {
            // 找到部门内最匹配的员工
            FixedEmployeeRegistry.FixedEmployeeDefinition bestEmployee =
                fixedEmployeeRegistry.getDefinitionsByDepartment(department).stream()
                    .filter(def -> isQualifiedForItem(def, item))
                    .filter(def -> getCurrentLoad(def.code()) < maxLoad)
                    .min((a, b) -> {
                        long matchA = item.getRequiredRoles().stream().filter(a.capabilities()::contains).count();
                        long matchB = item.getRequiredRoles().stream().filter(b.capabilities()::contains).count();
                        return Long.compare(matchB, matchA);
                    })
                    .orElse(null);

            if (bestEmployee != null) {
                boolean assigned = todoPool.assign(item.getId(), bestEmployee.code());
                if (assigned) {
                    results.add(TodoClaimResult.success(item.getId(), bestEmployee.code()));
                    log.info("Brain assigned todo {} to employee {} (department={})",
                        item.getId(), bestEmployee.code(), department);
                }
            } else {
                log.warn("No qualified employee found for todo {} in department {}",
                    item.getId(), department);
            }
        }

        return results;
    }

    @Override
    public boolean isQualified(String employeeCode, String todoItemId) {
        FixedEmployeeRegistry.FixedEmployeeDefinition def =
            fixedEmployeeRegistry.getDefinitionByCode(employeeCode).orElse(null);
        if (def == null) return false;

        DepartmentTodoItem item = todoPool.get(todoItemId).orElse(null);
        if (item == null) return false;

        // 负载检查
        if (getCurrentLoad(employeeCode) >= maxLoad) {
            return false;
        }

        return isQualifiedForItem(def, item);
    }

    @Override
    public int getCurrentLoad(String employeeCode) {
        return todoPool.getClaimedByEmployee(employeeCode).size();
    }

    @Override
    public int getMaxLoad() {
        return maxLoad;
    }

    /**
     * 检查员工定义是否有资格领取指定待办。
     */
    private boolean isQualifiedForItem(FixedEmployeeRegistry.FixedEmployeeDefinition def,
                                        DepartmentTodoItem item) {
        // 1. 部门归属
        if (!def.department().equals(item.getDepartment())) {
            return false;
        }

        // 2. 职责匹配：至少一个 requiredRole 在员工 capabilities 中（如果待办指定了 requiredRoles）
        if (!item.getRequiredRoles().isEmpty()) {
            boolean roleMatch = item.getRequiredRoles().stream()
                .anyMatch(role -> def.capabilities() != null && def.capabilities().contains(role));
            if (!roleMatch) return false;
        }

        // 3. 工具白名单：requiredTools 是员工工具白名单的子集
        if (!item.getRequiredTools().isEmpty() && def.tools() != null) {
            boolean toolMatch = def.tools().containsAll(item.getRequiredTools());
            if (!toolMatch) return false;
        }

        return true;
    }
}
