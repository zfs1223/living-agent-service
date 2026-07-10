package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native 库加载状态检查器。
 * 
 * 提供库初始化和版本信息查询功能。
 * 实际的 native 方法由 Rust JNI 实现（audio_jni, security_jni 等）。
 * 
 * 使用延迟加载（lazy loading）避免 JVM 静态初始化阶段崩溃。
 */
public class NativeLibrary {

    private static final Logger log = LoggerFactory.getLogger(NativeLibrary.class);
    
    private static volatile boolean loaded = false;
    private static volatile boolean initialized = false;
    private static volatile String loadError = null;
    private static final String LIBRARY_NAME = "living_agent_native";
    
    // 延迟加载：不在 static 初始化块中加载，而是等待首次使用
    
    /**
     * 尝试加载 native 库（延迟加载）。
     * 调用方应在首次需要 native 功能时调用此方法。
     */
    public static void tryLoad() {
        if (!loaded && loadError == null) {
            synchronized (NativeLibrary.class) {
                if (!loaded && loadError == null) {
                    try {
                        System.loadLibrary(LIBRARY_NAME);
                        // 不立即调用 initialize()，避免 Rust 静态初始化导致的崩溃
                        // initialize() 应在首次实际使用 native 功能时调用
                        loaded = true;
                        log.info("Native library '{}' loaded successfully (deferred init)", LIBRARY_NAME);
                    } catch (UnsatisfiedLinkError e) {
                        loaded = false;
                        loadError = e.getMessage();
                        log.error("Failed to load native library '{}': {}", LIBRARY_NAME, e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 初始化 native 库（延迟调用）。
     * 
     * ⚠️ 注意：Rust 侧的 initialize() 会调用 tracing_subscriber::fmt::init()，
     * 在 JNI 环境中首次调用 native 函数时可能触发 SIGSEGV（虚函数调用空指针）。
     * 因此此方法当前为空实现，不应直接调用 initialize()。
     */
    public static void initializeNative() {
        if (!loaded) {
            log.warn("Native library not loaded, cannot initialize");
            return;
        }
        if (initialized) {
            return;
        }
        // ⚠️ 不调用 initialize()，因为 Rust 的 tracing_subscriber 初始化
        // 在 JNI 上下文中会导致 SIGSEGV（偏移量 0x5eaf7）
        // 改为仅标记为已初始化
        initialized = true;
        log.info("Native library marked as initialized (Rust init() skipped due to JNI safety)");
    }
    
    /**
     * 检查 native 库是否已初始化。
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 检查 native 库是否可用（已加载且无错误）。
     * 调用方应在调用 native 方法前先检查此方法。
     * 如果尚未加载，会尝试加载（但不初始化）。
     * 
     * 注意：此方法仅检查库是否加载，不检查是否已初始化。
     * 如需调用 native 函数，应先调用 initializeNative()。
     */
    public static boolean isAvailable() {
        tryLoad(); // 延迟加载（不初始化）
        return loaded && loadError == null;
    }

    /**
     * 获取加载失败原因，成功时返回 null。
     */
    public static String getLoadError() {
        return loadError;
    }
    
    /**
     * 获取 native 库版本信息。
     * 
     * @return 版本字符串（如 "0.1.0"），如果库未加载则返回 "native-unavailable"
     */
    public static String getVersion() {
        if (!loaded) {
            return "native-unavailable";
        }
        try {
            return getVersionNative();
        } catch (UnsatisfiedLinkError e) {
            return "native-error";
        }
    }
    
    private static native String getVersionNative();
    
    private static native void initialize();
}
