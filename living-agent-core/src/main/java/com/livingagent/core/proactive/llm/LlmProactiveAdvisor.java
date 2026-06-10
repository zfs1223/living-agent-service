package com.livingagent.core.proactive.llm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LlmProactiveAdvisor {

    Optional<ProactiveSuggestion> generateSuggestion(String userId, Map<String, Object> context);

    List<ProactiveSuggestion> generateSuggestions(String userId, Map<String, Object> context, int maxCount);

    record ProactiveSuggestion(
        String suggestionId,
        String userId,
        String category,
        String title,
        String description,
        String triggerReason,
        String expectedBenefit,
        String riskNote,
        List<String> recommendedActions,
        boolean requiresUserConfirmation,
        double confidence,
        String source
    ) {}
}
