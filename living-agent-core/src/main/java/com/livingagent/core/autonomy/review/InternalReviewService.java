package com.livingagent.core.autonomy.review;

import java.util.List;
import java.util.Optional;

/**
 * 部门内审查服务接口。
 *
 * <p>管理审查状态机、轮次计数、终止条件、完成标记。
 *
 * <p>核心规则：
 * <ul>
 *   <li>编写员工不能自己标记 COMPLETED</li>
 *   <li>审查员工的 APPROVED 决定会自动设置 completionTag = true</li>
 *   <li>审查轮次 >= maxReviewRounds 时，上报部门大脑裁决</li>
 *   <li>部门大脑只关注 completionTag = true 的成果</li>
 * </ul>
 */
public interface InternalReviewService {

    /**
     * 审查结果监听器。
     *
     * <p>当审查结果产生时被回调，用于实现审查不通过时的重试闭环。
     */
    @FunctionalInterface
    interface ReviewListener {
        /**
         * 审查结果回调。
         *
         * @param todoItemId   待办项ID
         * @param reviewId     审查记录ID
         * @param result       审查结果
         * @param authorCode   编写员工代码
         * @param executionId  执行ID
         */
        void onReviewResult(String todoItemId, String reviewId, ReviewResult result,
                            String authorCode, String executionId);
    }

    /**
     * 注册审查结果监听器。
     */
    void addReviewListener(ReviewListener listener);

    /**
     * 提交审查：编写员工完成执行后，提交给审查员。
     *
     * @param todoItemId    待办项ID
     * @param authorCode    编写员工代码
     * @param reviewerCode  审查员工代码
     * @param executionId   执行ID
     * @param maxReviewRounds 最大审查轮次
     * @return 审查记录ID
     */
    String submitForReview(String todoItemId, String authorCode, String reviewerCode,
                           String executionId, int maxReviewRounds);

    /**
     * 执行审查：审查员工对编写员工的成果进行审查。
     *
     * @param reviewId   审查记录ID
     * @param result     审查结果
     */
    void review(String reviewId, ReviewResult result);

    /**
     * 获取审查记录。
     */
    Optional<ReviewHistory> getReview(String reviewId);

    /**
     * 获取待办项的审查历史。
     */
    List<ReviewHistory> getReviewHistoryByTodoItem(String todoItemId);

    /**
     * 获取待办项的最新审查状态。
     */
    Optional<ReviewState> getReviewState(String todoItemId);

    /**
     * 获取待办项是否已通过审查（completionTag = true）。
     */
    boolean isCompleted(String todoItemId);

    /**
     * 获取当前审查轮次。
     */
    int getCurrentRound(String todoItemId);
}
