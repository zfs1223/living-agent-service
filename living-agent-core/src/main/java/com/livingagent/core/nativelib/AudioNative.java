package com.livingagent.core.nativelib;

public class AudioNative {
    
    static {
        NativeLibrary.isLoaded();
    }
    
    public static native long createProcessor(int sampleRate, int channels, int frameSize, boolean enableVad);
    
    public static native void destroyProcessor(long handle);
    
    public static native byte[] decodeOpus(long handle, byte[] opusData);
    
    public static native byte[] encodePcm(long handle, byte[] pcmData);
    
    public static native boolean detectVoiceActivity(long handle, byte[] pcmData);
    
    public static native byte[] applyGain(byte[] pcmData, float gainDb);
    
    public static native String getStats(long handle);
    
    public static class Processor implements AutoCloseable {
        
        private final long handle;
        private volatile boolean closed = false;
        
        public Processor(int sampleRate, int channels, int frameSize, boolean enableVad) {
            this.handle = createProcessor(sampleRate, channels, frameSize, enableVad);
            if (this.handle == 0) {
                throw new RuntimeException("Failed to create audio processor");
            }
        }
        
        public Processor() {
            this(16000, 1, 960, true);
        }
        
        public byte[] decodeOpus(byte[] opusData) {
            checkClosed();
            return AudioNative.decodeOpus(handle, opusData);
        }
        
        public byte[] encodePcm(byte[] pcmData) {
            checkClosed();
            return AudioNative.encodePcm(handle, pcmData);
        }
        
        public boolean detectVoiceActivity(byte[] pcmData) {
            checkClosed();
            return AudioNative.detectVoiceActivity(handle, pcmData);
        }
        
        public String getStats() {
            checkClosed();
            return AudioNative.getStats(handle);
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Processor is closed");
            }
        }
        
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                destroyProcessor(handle);
            }
        }
    }
}
