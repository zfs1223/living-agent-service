package com.livingagent.core.knowledge;

import java.time.Duration;
import java.time.Instant;

public enum Validity {
    PERMANENT("permanent", Duration.ofDays(365 * 10)),
    LONG_TERM("long_term", Duration.ofDays(365)),
    SHORT_TERM("short_term", Duration.ofDays(30)),
    TEMPORARY("temporary", Duration.ofDays(7));
    
    private final String value;
    private final Duration defaultDuration;
    
    Validity(String value, Duration defaultDuration) {
        this.value = value;
        this.defaultDuration = defaultDuration;
    }
    
    public String getValue() {
        return value;
    }
    
    public Duration getDefaultDuration() {
        return defaultDuration;
    }
    
    public Instant calculateExpiresAt() {
        return Instant.now().plus(defaultDuration);
    }
    
    public static Validity fromString(String value) {
        if (value == null) return LONG_TERM;
        for (Validity v : values()) {
            if (v.value.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return LONG_TERM;
    }
}
