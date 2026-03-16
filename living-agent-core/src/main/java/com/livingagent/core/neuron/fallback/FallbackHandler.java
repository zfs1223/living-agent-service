package com.livingagent.core.neuron.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(FallbackHandler.class);

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_RESET_MS = 60_000;
    private static final long ALERT_COOLDOWN_MS = 300_000;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);
    private final AtomicInteger totalFallbacks = new AtomicInteger(0);
    private final AtomicInteger totalRecoveries = new AtomicInteger(0);

    private volatile boolean circuitOpen = false;
    private volatile Instant circuitOpenTime = null;

    private final Map<String, FallbackRecord> fallbackHistory = new ConcurrentHashMap<>();
    private final AlertService alertService;

    public FallbackHandler() {
        this.alertService = null;
    }

    public FallbackHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    public FallbackResult handleFailure(FailureContext context) {
        log.warn("Handling failure: {} - {}", context.getFailureType(), context.getMessage());

        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        totalFallbacks.incrementAndGet();

        FallbackRecord record = new FallbackRecord(context);
        fallbackHistory.put(record.getRecordId(), record);

        if (failures >= MAX_CONSECUTIVE_FAILURES && !circuitOpen) {
            openCircuitBreaker(context);
        }

        sendAlertIfNeeded(context);

        FallbackResult result = generateFallbackResponse(context);
        record.setResult(result);

        log.info("Fallback handled: {} -> {}", context.getRequestId(), result.getStatus());
        return result;
    }

    public boolean shouldUseFallback() {
        if (circuitOpen) {
            if (System.currentTimeMillis() - lastFailureTime.get() > CIRCUIT_BREAKER_RESET_MS) {
                attemptRecovery();
            }
            return true;
        }
        return false;
    }

    public void recordSuccess(String requestId) {
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures > 0) {
            totalRecoveries.incrementAndGet();
            log.info("Recovery detected after {} failures, request: {}", previousFailures, requestId);
        }

        if (circuitOpen) {
            closeCircuitBreaker();
        }
    }

    private void openCircuitBreaker(FailureContext context) {
        circuitOpen = true;
        circuitOpenTime = Instant.now();
        log.error("Circuit breaker OPENED due to {} consecutive failures. Last failure: {}", 
                MAX_CONSECUTIVE_FAILURES, context.getMessage());

        if (alertService != null) {
            alertService.sendCriticalAlert(
                    "Circuit Breaker Opened",
                    String.format("Main brain (Qwen3.5-27B) is experiencing failures. " +
                            "Fallback mode activated. Last error: %s", context.getMessage())
            );
        }
    }

    private void closeCircuitBreaker() {
        circuitOpen = false;
        circuitOpenTime = null;
        log.info("Circuit breaker CLOSED - normal operation resumed");

        if (alertService != null) {
            alertService.sendInfoAlert(
                    "Circuit Breaker Closed",
                    "Main brain has recovered. Normal operation resumed."
            );
        }
    }

    private void attemptRecovery() {
        log.info("Attempting recovery after circuit breaker timeout");
        consecutiveFailures.set(0);
    }

    private void sendAlertIfNeeded(FailureContext context) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime.get() < ALERT_COOLDOWN_MS) {
            return;
        }

        if (alertService != null) {
            lastAlertTime.set(now);

            String severity = circuitOpen ? "CRITICAL" : "WARNING";
            String message = String.format(
                    "Brain failure detected\n" +
                    "Type: %s\n" +
                    "Message: %s\n" +
                    "Consecutive failures: %d\n" +
                    "Circuit breaker: %s",
                    context.getFailureType(),
                    context.getMessage(),
                    consecutiveFailures.get(),
                    circuitOpen ? "OPEN" : "CLOSED"
            );

            if (circuitOpen) {
                alertService.sendCriticalAlert("Brain Failure Alert", message);
            } else {
                alertService.sendWarningAlert("Brain Failure Warning", message);
            }
        }
    }

    private FallbackResult generateFallbackResponse(FailureContext context) {
        FallbackResult result = new FallbackResult();
        result.setRequestId(context.getRequestId());
        result.setFallbackTime(Instant.now());
        result.setCircuitOpen(circuitOpen);

        String response = generateBasicResponse(context);
        result.setResponse(response);
        result.setStatus(FallbackResult.Status.FALLBACK_APPLIED);

        return result;
    }

    private String generateBasicResponse(FailureContext context) {
        StringBuilder response = new StringBuilder();

        response.append("抱歉，系统当前正在处理中，我暂时为您提供一个基础回复。\n\n");

        String query = context.getUserQuery();
        if (query != null) {
            if (containsGreeting(query)) {
                response.append("您好！我是您的企业智能助手。请问有什么可以帮助您的？\n");
            } else if (containsQuestion(query)) {
                response.append("我理解您的问题。由于系统当前处于维护状态，我暂时无法提供详细解答。\n");
                response.append("请您稍后再试，或者联系技术支持。\n");
            } else if (containsRequest(query)) {
                response.append("我已收到您的请求。系统当前正在处理中，请稍候再试。\n");
            } else {
                response.append("感谢您的输入。系统当前处于降级模式，我会尽力为您提供基础服务。\n");
            }
        }

        response.append("\n---\n");
        response.append("系统状态: ").append(circuitOpen ? "降级模式" : "正常").append("\n");
        response.append("如有紧急问题，请联系: tech-support@company.com");

        return response.toString();
    }

    private boolean containsGreeting(String query) {
        String lower = query.toLowerCase();
        return lower.contains("你好") || lower.contains("hello") || 
               lower.contains("hi") || lower.contains("您好");
    }

    private boolean containsQuestion(String query) {
        return query.contains("?") || query.contains("？") ||
               query.contains("怎么") || query.contains("如何") ||
               query.contains("什么") || query.contains("为什么") ||
               query.contains("how") || query.contains("what") || query.contains("why");
    }

    private boolean containsRequest(String query) {
        String lower = query.toLowerCase();
        return lower.contains("帮我") || lower.contains("请") ||
               lower.contains("需要") || lower.contains("想要") ||
               lower.contains("help") || lower.contains("please");
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("consecutiveFailures", consecutiveFailures.get());
        stats.put("totalFallbacks", totalFallbacks.get());
        stats.put("totalRecoveries", totalRecoveries.get());
        stats.put("circuitOpen", circuitOpen);
        stats.put("circuitOpenTime", circuitOpenTime);
        stats.put("lastFailureTime", lastFailureTime.get());
        stats.put("fallbackHistorySize", fallbackHistory.size());
        return stats;
    }

    public void reset() {
        consecutiveFailures.set(0);
        circuitOpen = false;
        circuitOpenTime = null;
        log.info("Fallback handler reset");
    }

    public static class FailureContext {
        private String requestId;
        private String brainName;
        private FailureType failureType;
        private String message;
        private String userQuery;
        private Throwable exception;
        private Instant failureTime;

        public enum FailureType {
            TIMEOUT,
            MODEL_ERROR,
            RATE_LIMIT,
            NETWORK_ERROR,
            INTERNAL_ERROR,
            UNKNOWN
        }

        public FailureContext() {
            this.failureTime = Instant.now();
        }

        public static FailureContext timeout(String requestId, String brainName, String query) {
            FailureContext ctx = new FailureContext();
            ctx.requestId = requestId;
            ctx.brainName = brainName;
            ctx.failureType = FailureType.TIMEOUT;
            ctx.message = "Request timeout";
            ctx.userQuery = query;
            return ctx;
        }

        public static FailureContext modelError(String requestId, String brainName, String query, Throwable e) {
            FailureContext ctx = new FailureContext();
            ctx.requestId = requestId;
            ctx.brainName = brainName;
            ctx.failureType = FailureType.MODEL_ERROR;
            ctx.message = e != null ? e.getMessage() : "Model error";
            ctx.userQuery = query;
            ctx.exception = e;
            return ctx;
        }

        public static FailureContext rateLimit(String requestId, String brainName) {
            FailureContext ctx = new FailureContext();
            ctx.requestId = requestId;
            ctx.brainName = brainName;
            ctx.failureType = FailureType.RATE_LIMIT;
            ctx.message = "Rate limit exceeded";
            return ctx;
        }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public String getBrainName() { return brainName; }
        public void setBrainName(String brainName) { this.brainName = brainName; }

        public FailureType getFailureType() { return failureType; }
        public void setFailureType(FailureType failureType) { this.failureType = failureType; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getUserQuery() { return userQuery; }
        public void setUserQuery(String userQuery) { this.userQuery = userQuery; }

        public Throwable getException() { return exception; }
        public void setException(Throwable exception) { this.exception = exception; }

        public Instant getFailureTime() { return failureTime; }
    }

    public static class FallbackResult {
        public enum Status {
            FALLBACK_APPLIED,
            CIRCUIT_OPEN,
            RECOVERED
        }

        private String requestId;
        private String response;
        private Status status;
        private Instant fallbackTime;
        private boolean circuitOpen;

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }

        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }

        public Instant getFallbackTime() { return fallbackTime; }
        public void setFallbackTime(Instant fallbackTime) { this.fallbackTime = fallbackTime; }

        public boolean isCircuitOpen() { return circuitOpen; }
        public void setCircuitOpen(boolean circuitOpen) { this.circuitOpen = circuitOpen; }
    }

    public static class FallbackRecord {
        private final String recordId;
        private final FailureContext context;
        private FallbackResult result;
        private final Instant timestamp;

        public FallbackRecord(FailureContext context) {
            this.recordId = "fb_" + System.currentTimeMillis();
            this.context = context;
            this.timestamp = Instant.now();
        }

        public String getRecordId() { return recordId; }
        public FailureContext getContext() { return context; }
        public FallbackResult getResult() { return result; }
        public void setResult(FallbackResult result) { this.result = result; }
        public Instant getTimestamp() { return timestamp; }
    }

    public interface AlertService {
        void sendCriticalAlert(String title, String message);
        void sendWarningAlert(String title, String message);
        void sendInfoAlert(String title, String message);
    }
}
