package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NativeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NativeExceptionHandler.class);

    public NativeException convert(Throwable nativeError, String operationName) {
        if (nativeError instanceof UnsatisfiedLinkError) {
            log.error("Unrecoverable native error [{}]: native method not linked - {}", operationName, nativeError.getMessage());
            return new NativeException(
                "Native method not linked: " + operationName + " - " + nativeError.getMessage(),
                operationName, false, nativeError);
        }

        if (nativeError instanceof NullPointerException) {
            log.warn("Native call returned null for operation: {}", operationName);
            return new NativeException(
                "Native method returned null: " + operationName,
                operationName, true, nativeError);
        }

        log.error("Native call threw exception [{}]: {}", operationName, nativeError.getMessage());
        return new NativeException(
            "Native call failed: " + operationName + " - " + nativeError.getMessage(),
            operationName, true, nativeError);
    }
}
