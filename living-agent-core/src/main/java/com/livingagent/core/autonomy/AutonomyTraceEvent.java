package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AutonomyTraceEvent(
    String traceId,
    String requestId,
    String stage,
    String actor,
    String summary,
    Map<String, Object> data,
    Instant timestamp,
    String taskKey,
    String executionId
) {
    public static AutonomyTraceEvent of(String requestId, String stage, String actor, String summary) {
        return new AutonomyTraceEvent(
            UUID.randomUUID().toString(),
            requestId,
            stage,
            actor,
            summary,
            Map.of(),
            Instant.now(),
            null,
            null
        );
    }

    public static AutonomyTraceEvent of(String requestId, String stage, String actor, String summary, Map<String, Object> data) {
        return new AutonomyTraceEvent(
            UUID.randomUUID().toString(),
            requestId,
            stage,
            actor,
            summary,
            data != null ? data : Map.of(),
            Instant.now(),
            data != null ? (String) data.get("taskKey") : null,
            data != null ? (String) data.get("executionId") : null
        );
    }

    /**
     * P1-6.2: 创建带关联键的 Trace 事件，同时记录 requestId、taskKey、executionId
     */
    public static AutonomyTraceEvent ofWithKeys(String requestId, String stage, String actor, String summary,
                                                  Map<String, Object> data, String taskKey, String executionId) {
        return new AutonomyTraceEvent(
            UUID.randomUUID().toString(),
            requestId,
            stage,
            actor,
            summary,
            data != null ? data : Map.of(),
            Instant.now(),
            taskKey,
            executionId
        );
    }
}
