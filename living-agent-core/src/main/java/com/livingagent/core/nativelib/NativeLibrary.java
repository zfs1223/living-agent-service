package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeLibrary {

    private static final Logger log = LoggerFactory.getLogger(NativeLibrary.class);
    
    private static volatile boolean loaded = false;
    private static volatile String loadError = null;
    private static final String LIBRARY_NAME = "living_agent_native";
    
    static {
        loadLibrary();
    }
    
    private static void loadLibrary() {
        if (!loaded) {
            try {
                System.loadLibrary(LIBRARY_NAME);
                initialize();
                loaded = true;
                log.info("Native library '{}' loaded successfully", LIBRARY_NAME);
            } catch (UnsatisfiedLinkError e) {
                loaded = false;
                loadError = e.getMessage();
                log.error("Failed to load native library '{}': {}", LIBRARY_NAME, e.getMessage());
            }
        }
    }
    
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 检查 native 库是否可用（已加载且无错误）。
     * 调用方应在调用 native 方法前先检查此方法。
     */
    public static boolean isAvailable() {
        return loaded && loadError == null;
    }

    /**
     * 获取加载失败原因，成功时返回 null。
     */
    public static String getLoadError() {
        return loadError;
    }
    
    public static native String getVersion();
    
    private static native void initialize();
}
