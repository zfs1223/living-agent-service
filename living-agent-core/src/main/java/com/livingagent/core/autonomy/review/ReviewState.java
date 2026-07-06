package com.livingagent.core.autonomy.review;

/**
 * 审查状态枚举。
 *
 * <p>审查闭环状态机：
 * <pre>
 * SUBMITTED_FOR_REVIEW → UNDER_REVIEW → COMPLETED
 *                                    → REVISION_NEEDED → SUBMITTED_FOR_REVIEW（循环）
 *                                    → REJECTED
 *                                    → ESCALATED（上报部门大脑）
 * </pre>
 */
public enum ReviewState {
    /** 已提交审查 */
    SUBMITTED_FOR_REVIEW,
    /** 审查中 */
    UNDER_REVIEW,
    /** 需要修改 */
    REVISION_NEEDED,
    /** 审查通过 */
    COMPLETED,
    /** 审查拒绝 */
    REJECTED,
    /** 上报部门大脑裁决 */
    ESCALATED
}
