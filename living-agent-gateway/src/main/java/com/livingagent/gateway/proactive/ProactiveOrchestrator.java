package com.livingagent.gateway.proactive;

import com.livingagent.core.proactive.predictor.PatternPredictor;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProactiveOrchestrator {

    private final PatternPredictor patternPredictor;
    private final RiskPredictor riskPredictor;
    private final ProactiveSuggestionService suggestionService;

    public ProactiveOrchestrator(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            ProactiveSuggestionService suggestionService) {
        this.patternPredictor = patternPredictor;
        this.riskPredictor = riskPredictor;
        this.suggestionService = suggestionService;
    }

    public OrchestrationResult runForUser(String userId) {
        List<ProactiveSuggestionService.Suggestion> suggestions = suggestionService.generateSuggestions(userId);
        List<RiskPredictor.RiskAlert> alerts = riskPredictor.getActiveAlerts();
        return new OrchestrationResult(userId, suggestions, alerts, Map.of("patternStats", patternPredictor.getStatistics()));
    }

    public record OrchestrationResult(
            String userId,
            List<ProactiveSuggestionService.Suggestion> suggestions,
            List<RiskPredictor.RiskAlert> alerts,
            Map<String, Object> metadata
    ) {}
}
