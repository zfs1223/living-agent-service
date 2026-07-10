package com.livingagent.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * B-2-7: 异步任务指数退避重试工具。
 * 为 CompletableFuture 链提供真正的重试逻辑（而非仅 exceptionally 记日志）。
 */
public final class AsyncRetryHelper {

    private static final Logger log = LoggerFactory.getLogger(AsyncRetryHelper.class);

    private AsyncRetryHelper() {}

    /**
     * 带指数退避的异步重试。
     *
     * @param supplier        异步操作（每次重试重新调用以获取新 CompletableFuture）
     * @param maxRetries      最大重试次数（不含首次调用）
     * @param initialDelayMs  首次重试延迟（毫秒）
     * @param backoffFactor   退避倍数（每次重试延迟 = initialDelayMs * backoffFactor^attempt）
     * @param maxDelayMs      最大延迟上限
     * @param scheduler       用于调度延迟的 ScheduledExecutorService
     * @param operationName   操作名称（用于日志）
     * @param <T>             返回类型
     * @return 最终结果的 CompletableFuture
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> supplier,
            int maxRetries,
            long initialDelayMs,
            double backoffFactor,
            long maxDelayMs,
            ScheduledExecutorService scheduler,
            String operationName) {

        CompletableFuture<T> result = new CompletableFuture<>();
        executeWithRetry(supplier, maxRetries, initialDelayMs, backoffFactor, maxDelayMs,
            scheduler, operationName, 0, result);
        return result;
    }

    /**
     * 简化版：默认 initialDelay=1000ms, backoff=2.0, maxDelay=30000ms
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> supplier,
            int maxRetries,
            ScheduledExecutorService scheduler,
            String operationName) {
        return withRetry(supplier, maxRetries, 1000, 2.0, 30_000, scheduler, operationName);
    }

    private static <T> void executeWithRetry(
            Supplier<CompletableFuture<T>> supplier,
            int maxRetries,
            long initialDelayMs,
            double backoffFactor,
            long maxDelayMs,
            ScheduledExecutorService scheduler,
            String operationName,
            int attempt,
            CompletableFuture<T> result) {

        supplier.get().whenComplete((value, ex) -> {
            if (ex == null) {
                if (attempt > 0) {
                    log.info("[{}] succeeded after {} retries", operationName, attempt);
                }
                result.complete(value);
            } else if (attempt >= maxRetries) {
                log.error("[{}] failed after {} retries: {}", operationName, maxRetries, ex.getMessage());
                result.completeExceptionally(ex);
            } else {
                long delay = (long) Math.min(initialDelayMs * Math.pow(backoffFactor, attempt), maxDelayMs);
                int nextAttempt = attempt + 1;
                log.warn("[{}] attempt {} failed, retrying in {}ms: {}",
                    operationName, nextAttempt, delay, ex.getMessage());
                scheduler.schedule(() ->
                    executeWithRetry(supplier, maxRetries, initialDelayMs, backoffFactor,
                        maxDelayMs, scheduler, operationName, nextAttempt, result),
                    delay, TimeUnit.MILLISECONDS);
            }
        });
    }
}
