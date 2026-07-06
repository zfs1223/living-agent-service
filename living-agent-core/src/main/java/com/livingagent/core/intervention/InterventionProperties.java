package com.livingagent.core.intervention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "intervention")
public class InterventionProperties {

    private ScopeUpgrade upgrade = new ScopeUpgrade();
    private ScopeDowngrade downgrade = new ScopeDowngrade();
    private int defaultRealtimeTimeoutSeconds = 300;
    private int defaultAsyncTimeoutSeconds = 86400;

    public static class ScopeUpgrade {
        private double successRateThreshold = 95.0;
        private int minExecutionCount = 10;

        public double getSuccessRateThreshold() { return successRateThreshold; }
        public void setSuccessRateThreshold(double v) { this.successRateThreshold = v; }
        public int getMinExecutionCount() { return minExecutionCount; }
        public void setMinExecutionCount(int v) { this.minExecutionCount = v; }
    }

    public static class ScopeDowngrade {
        private double successRateThreshold = 80.0;
        private int minExecutionCount = 5;

        public double getSuccessRateThreshold() { return successRateThreshold; }
        public void setSuccessRateThreshold(double v) { this.successRateThreshold = v; }
        public int getMinExecutionCount() { return minExecutionCount; }
        public void setMinExecutionCount(int v) { this.minExecutionCount = v; }
    }

    public ScopeUpgrade getUpgrade() { return upgrade; }
    public void setUpgrade(ScopeUpgrade upgrade) { this.upgrade = upgrade; }

    public ScopeDowngrade getDowngrade() { return downgrade; }
    public void setDowngrade(ScopeDowngrade downgrade) { this.downgrade = downgrade; }

    public int getDefaultRealtimeTimeoutSeconds() { return defaultRealtimeTimeoutSeconds; }
    public void setDefaultRealtimeTimeoutSeconds(int v) { this.defaultRealtimeTimeoutSeconds = v; }

    public int getDefaultAsyncTimeoutSeconds() { return defaultAsyncTimeoutSeconds; }
    public void setDefaultAsyncTimeoutSeconds(int v) { this.defaultAsyncTimeoutSeconds = v; }
}
