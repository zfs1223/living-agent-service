package com.livingagent.core.autonomy.review;

import java.util.List;

/**
 * 审查决定枚举。
 */
public enum ReviewDecision {
    /** 审查通过，标注 COMPLETED */
    APPROVED,
    /** 需要修改，回到编写员工 */
    REVISION_NEEDED,
    /** 严重不通过，可能需要换人 */
    REJECTED,
    /** 超出审查能力，上报部门大脑裁决 */
    ESCALATE_TO_BRAIN
}
