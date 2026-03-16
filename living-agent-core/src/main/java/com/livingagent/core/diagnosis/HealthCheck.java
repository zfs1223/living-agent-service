package com.livingagent.core.diagnosis;

@FunctionalInterface
public interface HealthCheck {
    HealthStatus check();
}
