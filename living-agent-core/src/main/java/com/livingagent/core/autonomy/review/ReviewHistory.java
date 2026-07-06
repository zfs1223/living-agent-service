package com.livingagent.core.autonomy.review;

import java.time.Instant;
import java.util.List;

/**
 * 审查历史记录：每轮审查的完整信息。
 */
public record ReviewHistory(
    String reviewId,
    String todoItemId,
    String authorCode,
    String reviewerCode,
    int reviewRound,
    ReviewState state,
    ReviewResult result,
    List<String> revisionNotes,
    Instant submittedAt,
    Instant reviewedAt
) {}
