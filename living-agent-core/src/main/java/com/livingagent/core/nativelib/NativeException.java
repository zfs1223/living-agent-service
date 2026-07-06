package com.livingagent.core.nativelib;

public class NativeException extends RuntimeException {

    private final String operationName;
    private final boolean recoverable;

    public NativeException(String message, String operationName, boolean recoverable) {
        super(message);
        this.operationName = operationName;
        this.recoverable = recoverable;
    }

    public NativeException(String message, String operationName, boolean recoverable, Throwable cause) {
        super(message, cause);
        this.operationName = operationName;
        this.recoverable = recoverable;
    }

    public String getOperationName() {
        return operationName;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
