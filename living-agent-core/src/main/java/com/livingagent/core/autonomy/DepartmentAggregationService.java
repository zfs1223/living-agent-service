package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Optional;

/**
 * 部门级聚合服务接口。
 *
 * <p>部门大脑对部门内成果做质量分析和聚合，打包为 DepartmentDeliverable 交付主脑。
 */
public interface DepartmentAggregationService {

    /**
     * 聚合部门内所有已完成的执行成果。
     *
     * @param department 部门代码
     * @param planId     任务计划ID
     * @param objective  部门目标
     * @return 聚合结果
     */
    AggregationResult aggregate(String department, String planId, String objective);

    /**
     * 获取部门交付物。
     */
    Optional<DepartmentDeliverable> getDeliverable(String deliverableId);

    /**
     * 获取部门所有交付物。
     */
    List<DepartmentDeliverable> getDeliverablesByDepartment(String department);

    /**
     * 获取任务计划的所有交付物。
     */
    List<DepartmentDeliverable> getDeliverablesByPlan(String planId);
}
