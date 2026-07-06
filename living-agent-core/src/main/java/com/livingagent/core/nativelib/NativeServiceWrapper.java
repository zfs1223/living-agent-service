package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NativeServiceWrapper {

    private static final Logger log = LoggerFactory.getLogger(NativeServiceWrapper.class);

    private final NativeExceptionHandler exceptionHandler;
    private final NativePerformanceMonitor performanceMonitor;

    public NativeServiceWrapper(NativeExceptionHandler exceptionHandler,
                                NativePerformanceMonitor performanceMonitor) {
        this.exceptionHandler = exceptionHandler;
        this.performanceMonitor = performanceMonitor;
    }

    public <T> T callNative(NativeCallable<T> callable, String operationName) {
        long startTime = System.currentTimeMillis();

        try {
            T result = callable.call();
            long elapsed = System.currentTimeMillis() - startTime;

            if (result == null) {
                performanceMonitor.recordFailure(operationName, elapsed, "returned null");
                throw new NativeException("Native method returned null: " + operationName, operationName, true);
            }

            performanceMonitor.recordSuccess(operationName, elapsed);
            log.debug("Native call success: {} ({}ms)", operationName, elapsed);
            return result;
        } catch (NativeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            performanceMonitor.recordFailure(operationName, elapsed, e.getMessage());
            throw e;
        } catch (Throwable nativeError) {
            long elapsed = System.currentTimeMillis() - startTime;
            NativeException ex = exceptionHandler.convert(nativeError, operationName);
            performanceMonitor.recordFailure(operationName, elapsed, ex.getMessage());
            log.error("Native call failed: {} ({}ms) - {}", operationName, elapsed, ex.getMessage());
            throw ex;
        }
    }

    public boolean callNativeBoolean(NativeCallable<Boolean> callable, String operationName) {
        long startTime = System.currentTimeMillis();

        try {
            Boolean result = callable.call();
            long elapsed = System.currentTimeMillis() - startTime;
            boolean value = result != null && result;
            performanceMonitor.recordSuccess(operationName, elapsed);
            log.debug("Native boolean call: {} -> {} ({}ms)", operationName, value, elapsed);
            return value;
        } catch (UnsatisfiedLinkError e) {
            long elapsed = System.currentTimeMillis() - startTime;
            NativeException ex = exceptionHandler.convert(e, operationName);
            performanceMonitor.recordFailure(operationName, elapsed, ex.getMessage());
            throw ex;
        } catch (Throwable nativeError) {
            long elapsed = System.currentTimeMillis() - startTime;
            NativeException ex = exceptionHandler.convert(nativeError, operationName);
            performanceMonitor.recordFailure(operationName, elapsed, ex.getMessage());
            throw ex;
        }
    }

    public <T> Optional<T> callNativeOptional(NativeCallable<T> callable, String operationName) {
        long startTime = System.currentTimeMillis();

        try {
            T result = callable.call();
            long elapsed = System.currentTimeMillis() - startTime;
            performanceMonitor.recordSuccess(operationName, elapsed);
            log.debug("Native optional call: {} -> {} ({}ms)", operationName, result != null ? "present" : "empty", elapsed);
            return Optional.ofNullable(result);
        } catch (NativeException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            performanceMonitor.recordFailure(operationName, elapsed, e.getMessage());
            throw e;
        } catch (Throwable nativeError) {
            long elapsed = System.currentTimeMillis() - startTime;
            NativeException ex = exceptionHandler.convert(nativeError, operationName);
            performanceMonitor.recordFailure(operationName, elapsed, ex.getMessage());
            throw ex;
        }
    }

    public NativePerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    @FunctionalInterface
    public interface NativeCallable<T> {
        T call() throws Throwable;
    }
}
