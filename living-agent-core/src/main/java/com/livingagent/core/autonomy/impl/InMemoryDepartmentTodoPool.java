package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DepartmentTodoItem;
import com.livingagent.core.autonomy.DepartmentTodoPool;
import com.livingagent.core.autonomy.TodoClaimResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存版部门待办池实现。
 *
 * <p><b>限制说明</b>：此实现使用 ConcurrentHashMap 存储，仅适用于单节点部署。
 * 重启后待办数据丢失，不支持集群水平扩展。
 * 如需生产部署，请替换为 Redis 实现（需添加 spring-data-redis 依赖）。</p>
 * 
 * <p>注意：此类不使用 @Service 注解，由 GatewayConfig.departmentTodoPool() 方法
 * 通过 @Bean 方式注册，避免与 GatewayConfig 中的 Bean 定义冲突。</p>
 * 
 * @see com.livingagent.gateway.config.GatewayConfig#departmentTodoPool()
 */
public class InMemoryDepartmentTodoPool implements DepartmentTodoPool {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDepartmentTodoPool.class);

    private final ConcurrentHashMap<String, DepartmentTodoItem> itemsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DepartmentTodoItem>> itemsByDepartment = new ConcurrentHashMap<>();

    @Override
    public void publish(DepartmentTodoItem item) {
        itemsById.put(item.getId(), item);
        itemsByDepartment.computeIfAbsent(item.getDepartment(), k -> new java.util.ArrayList<>()).add(item);
        log.info("Todo published: id={}, department={}, objective={}, priority={}",
            item.getId(), item.getDepartment(), item.getObjective(), item.getPriority());
    }

    @Override
    public void publishAll(List<DepartmentTodoItem> items) {
        for (DepartmentTodoItem item : items) {
            publish(item);
        }
        log.info("Published {} todo items", items.size());
    }

    @Override
    public TodoClaimResult claim(String todoItemId, String employeeCode) {
        DepartmentTodoItem item = itemsById.get(todoItemId);
        if (item == null) {
            return TodoClaimResult.notFound(todoItemId);
        }
        if (!item.isPending()) {
            return TodoClaimResult.notPending(todoItemId);
        }
        boolean success = item.claim(employeeCode);
        if (success) {
            log.info("Todo claimed: id={}, employee={}", todoItemId, employeeCode);
            return TodoClaimResult.success(todoItemId, employeeCode);
        } else {
            return TodoClaimResult.alreadyClaimed(todoItemId, employeeCode);
        }
    }

    @Override
    public boolean assign(String todoItemId, String employeeCode) {
        DepartmentTodoItem item = itemsById.get(todoItemId);
        if (item == null) {
            log.warn("Todo not found for assignment: id={}", todoItemId);
            return false;
        }
        item.assign(employeeCode);
        log.info("Todo assigned: id={}, employee={}", todoItemId, employeeCode);
        return true;
    }

    @Override
    public Optional<DepartmentTodoItem> get(String todoItemId) {
        return Optional.ofNullable(itemsById.get(todoItemId));
    }

    @Override
    public List<DepartmentTodoItem> getPendingByDepartment(String department) {
        List<DepartmentTodoItem> deptItems = itemsByDepartment.get(department);
        if (deptItems == null) return List.of();
        return deptItems.stream()
            .filter(DepartmentTodoItem::isPending)
            .collect(Collectors.toList());
    }

    @Override
    public List<DepartmentTodoItem> getAllByDepartment(String department) {
        List<DepartmentTodoItem> deptItems = itemsByDepartment.get(department);
        if (deptItems == null) return List.of();
        return List.copyOf(deptItems);
    }

    @Override
    public List<DepartmentTodoItem> getClaimedByEmployee(String employeeCode) {
        return itemsById.values().stream()
            .filter(item -> employeeCode.equals(item.getClaimedBy()))
            .collect(Collectors.toList());
    }

    @Override
    public void startProgress(String todoItemId) {
        DepartmentTodoItem item = itemsById.get(todoItemId);
        if (item != null) {
            item.startProgress();
        }
    }

    @Override
    public void complete(String todoItemId) {
        DepartmentTodoItem item = itemsById.get(todoItemId);
        if (item != null) {
            item.complete();
        }
    }

    @Override
    public void cancel(String todoItemId) {
        DepartmentTodoItem item = itemsById.get(todoItemId);
        if (item != null) {
            item.cancel();
        }
    }

    @Override
    public int countByDepartment(String department) {
        List<DepartmentTodoItem> deptItems = itemsByDepartment.get(department);
        return deptItems != null ? deptItems.size() : 0;
    }

    @Override
    public int countPendingByDepartment(String department) {
        return getPendingByDepartment(department).size();
    }

    @Override
    public void clearByDepartment(String department) {
        List<DepartmentTodoItem> deptItems = itemsByDepartment.remove(department);
        if (deptItems != null) {
            for (DepartmentTodoItem item : deptItems) {
                itemsById.remove(item.getId());
            }
        }
        log.info("Cleared todo pool for department: {}", department);
    }
}
