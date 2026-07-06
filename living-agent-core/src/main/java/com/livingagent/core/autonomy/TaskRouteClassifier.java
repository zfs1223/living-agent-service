package com.livingagent.core.autonomy;

/**
 * 任务路由分类器接口。
 *
 * <p>在 DialogueAnalyzer 分析之后、MainBrainTaskDirector 规划之前，
 * 判断任务是单部门直达还是需要主脑拆解。
 *
 * <p>设计原则：
 * <ul>
 *   <li>不替代 DialogueAnalyzer，而是在其输出基础上增加路由判断</li>
 *   <li>单部门任务直达部门大脑，跳过主脑规划，降低延迟和主脑负载</li>
 *   <li>跨部门任务仍走主脑拆解路径</li>
 *   <li>无法判断时走现有主脑分析路径（Fallback 保护）</li>
 * </ul>
 *
 * @see TaskRouteResult
 * @see DialogueDecision
 */
public interface TaskRouteClassifier {

    /**
     * 根据对话决策判断任务路由类型。
     *
     * @param decision   DialogueAnalyzer 的分析结果
     * @param department 用户当前所在部门
     * @return 路由分类结果
     */
    TaskRouteResult classify(DialogueDecision decision, String department);
}
