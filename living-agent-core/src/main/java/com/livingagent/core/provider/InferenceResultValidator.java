package com.livingagent.core.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

public class InferenceResultValidator {

    private static final Logger log = LoggerFactory.getLogger(InferenceResultValidator.class);

    private static final List<Pattern> ERROR_PATTERNS = List.of(
        Pattern.compile("(?i)^error:\\s*"),
        Pattern.compile("(?i)^exception:\\s*"),
        Pattern.compile("(?i)^traceback\\s*"),
        Pattern.compile("(?i)^runtime\\s*error"),
        Pattern.compile("(?i)^CUDA\\s*out\\s*of\\s*memory"),
        Pattern.compile("(?i)^OOM"),
        Pattern.compile("(?i)^model\\s*overloaded"),
        Pattern.compile("(?i)^timeout"),
        Pattern.compile("(?i)^connection\\s*refused"),
        Pattern.compile("(?i)^internal\\s*server\\s*error")
    );

    private static final int MIN_MEANINGFUL_LENGTH = 2;

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult invalid(String reason) { return new ValidationResult(false, reason); }
    }

    public ValidationResult validate(String text) {
        if (text == null) {
            return ValidationResult.invalid("Response text is null");
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.invalid("Response text is empty");
        }

        if (trimmed.length() < MIN_MEANINGFUL_LENGTH) {
            return ValidationResult.invalid("Response text too short: '" + trimmed + "'");
        }

        for (Pattern pattern : ERROR_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return ValidationResult.invalid("Response matches error pattern: " + pattern.pattern());
            }
        }

        return ValidationResult.ok();
    }

    public String sanitize(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        for (Pattern pattern : ERROR_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                log.warn("LLM response contains error pattern, returning empty: {}",
                    trimmed.substring(0, Math.min(100, trimmed.length())));
                return "";
            }
        }
        return text;
    }
}
