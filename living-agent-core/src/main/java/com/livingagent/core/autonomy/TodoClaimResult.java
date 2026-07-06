package com.livingagent.core.autonomy;

/**
 * 待办领取结果。
 */
public record TodoClaimResult(
    boolean success,
    String todoItemId,
    String employeeCode,
    ClaimFailureReason failureReason
) {
    public enum ClaimFailureReason {
        SUCCESS,
        ALREADY_CLAIMED,
        NOT_QUALIFIED,
        NOT_FOUND,
        NOT_PENDING
    }

    public static TodoClaimResult success(String todoItemId, String employeeCode) {
        return new TodoClaimResult(true, todoItemId, employeeCode, ClaimFailureReason.SUCCESS);
    }

    public static TodoClaimResult alreadyClaimed(String todoItemId, String employeeCode) {
        return new TodoClaimResult(false, todoItemId, employeeCode, ClaimFailureReason.ALREADY_CLAIMED);
    }

    public static TodoClaimResult notQualified(String todoItemId, String employeeCode) {
        return new TodoClaimResult(false, todoItemId, employeeCode, ClaimFailureReason.NOT_QUALIFIED);
    }

    public static TodoClaimResult notFound(String todoItemId) {
        return new TodoClaimResult(false, todoItemId, null, ClaimFailureReason.NOT_FOUND);
    }

    public static TodoClaimResult notPending(String todoItemId) {
        return new TodoClaimResult(false, todoItemId, null, ClaimFailureReason.NOT_PENDING);
    }
}
