package com.livingagent.core.model.pool;

public class ProviderTestResult {
    private final boolean success;
    private final long latencyMs;
    private final String response;
    private final String error;
    private final String message;

    private ProviderTestResult(boolean success, long latencyMs, String response, String error, String message) {
        this.success = success;
        this.latencyMs = latencyMs;
        this.response = response;
        this.error = error;
        this.message = message;
    }

    public static ProviderTestResult success(long latencyMs, String response) {
        return new ProviderTestResult(true, latencyMs, response, null, "连接成功");
    }

    public static ProviderTestResult error(String error, String message) {
        return new ProviderTestResult(false, 0, null, error, message);
    }

    public boolean isSuccess() { return success; }
    public long getLatencyMs() { return latencyMs; }
    public String getResponse() { return response; }
    public String getError() { return error; }
    public String getMessage() { return message; }
}
