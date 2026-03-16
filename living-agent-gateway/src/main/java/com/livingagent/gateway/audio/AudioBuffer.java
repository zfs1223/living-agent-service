package com.livingagent.gateway.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioBuffer {
    
    private static final Logger log = LoggerFactory.getLogger(AudioBuffer.class);
    
    private final List<byte[]> chunks;
    private int totalBytes;
    private final int maxBytes;
    private final ReentrantLock lock;
    
    public AudioBuffer() {
        this(10 * 1024 * 1024);
    }
    
    public AudioBuffer(int maxBytes) {
        this.chunks = new ArrayList<>();
        this.totalBytes = 0;
        this.maxBytes = maxBytes;
        this.lock = new ReentrantLock();
    }
    
    public void append(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        
        lock.lock();
        try {
            if (totalBytes + data.length > maxBytes) {
                log.warn("Audio buffer overflow, clearing old data");
                clear();
            }
            
            chunks.add(data.clone());
            totalBytes += data.length;
        } finally {
            lock.unlock();
        }
    }
    
    public byte[] drain() {
        lock.lock();
        try {
            if (chunks.isEmpty()) {
                return new byte[0];
            }
            
            byte[] result = new byte[totalBytes];
            int offset = 0;
            
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            
            clear();
            return result;
        } finally {
            lock.unlock();
        }
    }
    
    public void clear() {
        lock.lock();
        try {
            chunks.clear();
            totalBytes = 0;
        } finally {
            lock.unlock();
        }
    }
    
    public int size() {
        lock.lock();
        try {
            return totalBytes;
        } finally {
            lock.unlock();
        }
    }
    
    public int chunkCount() {
        lock.lock();
        try {
            return chunks.size();
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isEmpty() {
        lock.lock();
        try {
            return chunks.isEmpty();
        } finally {
            lock.unlock();
        }
    }
    
    public double getDurationSeconds(int sampleRate, int channels) {
        return AudioUtils.calculateDurationSeconds(new byte[totalBytes], sampleRate, channels);
    }
}
