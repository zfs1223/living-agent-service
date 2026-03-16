package com.livingagent.core.nativelib;

public class SecurityNative {
    
    public static final int SECURITY_LEVEL_READ_ONLY = 0;
    public static final int SECURITY_LEVEL_SUPERVISED = 1;
    public static final int SECURITY_LEVEL_FULL = 2;
    
    static {
        NativeLibrary.isLoaded();
    }
    
    public static native long createValidator();
    
    public static native void destroyValidator(long handle);
    
    public static native boolean validateCommand(long handle, String command, String userId, String sessionId, int securityLevel);
    
    public static native boolean validatePath(long handle, String path, String userId, String sessionId, int securityLevel);
    
    public static native void addAllowedPath(long handle, String path);
    
    public static native void addDeniedPath(long handle, String path);
    
    public static class Validator implements AutoCloseable {
        
        private final long handle;
        private volatile boolean closed = false;
        
        public Validator() {
            this.handle = createValidator();
            if (this.handle == 0) {
                throw new RuntimeException("Failed to create security validator");
            }
        }
        
        public boolean validateCommand(String command, String userId, String sessionId, int securityLevel) {
            checkClosed();
            return SecurityNative.validateCommand(handle, command, userId, sessionId, securityLevel);
        }
        
        public boolean validatePath(String path, String userId, String sessionId, int securityLevel) {
            checkClosed();
            return SecurityNative.validatePath(handle, path, userId, sessionId, securityLevel);
        }
        
        public Validator allowPath(String path) {
            checkClosed();
            addAllowedPath(handle, path);
            return this;
        }
        
        public Validator denyPath(String path) {
            checkClosed();
            addDeniedPath(handle, path);
            return this;
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Validator is closed");
            }
        }
        
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                destroyValidator(handle);
            }
        }
    }
}
