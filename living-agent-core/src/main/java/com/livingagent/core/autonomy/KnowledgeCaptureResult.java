package com.livingagent.core.autonomy;

import java.util.Map;

public record KnowledgeCaptureResult(
    boolean success,
    String knowledgeKey,
    String layer,
    String domain,
    String summary,
    Map<String, Object> metadata
) {
    public static KnowledgeCaptureResult success(String knowledgeKey, String layer, String domain, String summary) {
        return new KnowledgeCaptureResult(true, knowledgeKey, layer, domain, summary, Map.of());
    }

    public static KnowledgeCaptureResult skipped(String reason) {
        return new KnowledgeCaptureResult(false, null, null, null, reason, Map.of());
    }
}
