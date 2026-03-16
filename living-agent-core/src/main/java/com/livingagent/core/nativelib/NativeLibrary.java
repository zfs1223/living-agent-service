package com.livingagent.core.nativelib;

public class NativeLibrary {
    
    private static volatile boolean loaded = false;
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
            } catch (UnsatisfiedLinkError e) {
                System.err.println("Failed to load native library: " + e.getMessage());
            }
        }
    }
    
    public static boolean isLoaded() {
        return loaded;
    }
    
    public static native String getVersion();
    
    private static native void initialize();
}
