package com.livingagent.core.nativelib;

public class CompactNative {

    static {
        NativeLibrary.isLoaded();
    }

    public static native String summarizeMessagesJson(String messagesJson, int maxLines);

    public static native int estimateTokenCount(String text);
}
