package com.livingagent.gateway.audio;

public class AudioConfig {
    
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int DEFAULT_CHANNELS = 1;
    public static final int DEFAULT_FRAME_SIZE = 960;
    public static final int DEFAULT_BITRATE = 24000;
    public static final int DEFAULT_COMPLEXITY = 10;
    
    public static final int OPUS_FRAME_DURATION_MS = 20;
    public static final int PCM_BYTES_PER_SAMPLE = 2;
    
    private int sampleRate;
    private int channels;
    private int frameSize;
    private int bitrate;
    private int complexity;
    
    public AudioConfig() {
        this.sampleRate = DEFAULT_SAMPLE_RATE;
        this.channels = DEFAULT_CHANNELS;
        this.frameSize = DEFAULT_FRAME_SIZE;
        this.bitrate = DEFAULT_BITRATE;
        this.complexity = DEFAULT_COMPLEXITY;
    }
    
    public static AudioConfig defaultConfig() {
        return new AudioConfig();
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
    
    public int getChannels() {
        return channels;
    }
    
    public void setChannels(int channels) {
        this.channels = channels;
    }
    
    public int getFrameSize() {
        return frameSize;
    }
    
    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }
    
    public int getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
    
    public int getComplexity() {
        return complexity;
    }
    
    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }
    
    public int getPcmFrameBytes() {
        return frameSize * channels * PCM_BYTES_PER_SAMPLE;
    }
}
