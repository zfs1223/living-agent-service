package com.livingagent.core.autonomy;

/**
 * 任务路由分类结果。
 *
 * <p>将任务分为三种路由类型：
 * <ul>
 *   <li>SINGLE_DEPARTMENT — 单部门任务，直达部门大脑，不经主脑拆解</li>
 *   <li>CROSS_DEPARTMENT — 跨部门任务，需主脑拆解后分发到各部门大脑</li>
 *   <li>CLARIFICATION_NEEDED — 需求不明确，需先澄清才能判断路由</li>
 * </ul>
 *
 * @see TaskRouteClassifier
 */
public record TaskRouteResult(
    RouteType routeType,
    String departmentCode,
    String reason,
    DialogueDecision decision
) {
    public enum RouteType {
        SINGLE_DEPARTMENT,
        CROSS_DEPARTMENT,
        CLARIFICATION_NEEDED
    }

    public static TaskRouteResult singleDepartment(String departmentCode, String reason, DialogueDecision decision) {
        return new TaskRouteResult(RouteType.SINGLE_DEPARTMENT, departmentCode, reason, decision);
    }

    public static TaskRouteResult crossDepartment(String reason, DialogueDecision decision) {
        return new TaskRouteResult(RouteType.CROSS_DEPARTMENT, null, reason, decision);
    }

    public static TaskRouteResult clarificationNeeded(String reason, DialogueDecision decision) {
        return new TaskRouteResult(RouteType.CLARIFICATION_NEEDED, null, reason, decision);
    }

    public boolean isSingleDepartment() {
        return routeType == RouteType.SINGLE_DEPARTMENT;
    }

    public boolean isCrossDepartment() {
        return routeType == RouteType.CROSS_DEPARTMENT;
    }

    public boolean needsClarification() {
        return routeType == RouteType.CLARIFICATION_NEEDED;
    }
}
