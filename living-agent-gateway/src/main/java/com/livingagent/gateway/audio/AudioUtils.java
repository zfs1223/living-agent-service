package com.livingagent.gateway.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class AudioUtils {
    
    private static final int WAV_HEADER_SIZE = 44;
    
    public static byte[] applyGain(byte[] pcmData, double gainDb) {
        if (pcmData == null || pcmData.length == 0) {
            return pcmData;
        }
        
        double gainFactor = Math.pow(10, gainDb / 20.0);
        byte[] result = new byte[pcmData.length];
        
        for (int i = 0; i < pcmData.length; i += 2) {
            if (i + 1 < pcmData.length) {
                short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
                double amplified = sample * gainFactor;
                
                if (amplified > Short.MAX_VALUE) {
                    amplified = Short.MAX_VALUE;
                } else if (amplified < Short.MIN_VALUE) {
                    amplified = Short.MIN_VALUE;
                }
                
                short newSample = (short) amplified;
                result[i] = (byte) (newSample & 0xFF);
                result[i + 1] = (byte) ((newSample >> 8) & 0xFF);
            }
        }
        
        return result;
    }
    
    public static byte[] extractPcmFromWav(byte[] wavData, int expectedSampleRate) {
        if (wavData == null || wavData.length <= WAV_HEADER_SIZE) {
            return wavData;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(wavData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        String riff = new String(wavData, 0, 4);
        if (!"RIFF".equals(riff)) {
            return wavData;
        }
        
        String wave = new String(wavData, 8, 4);
        if (!"WAVE".equals(wave)) {
            return wavData;
        }
        
        int dataOffset = 12;
        while (dataOffset < wavData.length - 8) {
            String chunkId = new String(wavData, dataOffset, 4);
            int chunkSize = buffer.getInt(dataOffset + 4);
            
            if ("data".equals(chunkId)) {
                int pcmStart = dataOffset + 8;
                int pcmLength = chunkSize;
                
                if (pcmStart + pcmLength <= wavData.length) {
                    byte[] pcmData = new byte[pcmLength];
                    System.arraycopy(wavData, pcmStart, pcmData, 0, pcmLength);
                    return pcmData;
                }
                break;
            }
            
            dataOffset += 8 + chunkSize;
        }
        
        return wavData;
    }
    
    public static byte[] createWavHeader(int pcmLength, int sampleRate, int channels, int bitsPerSample) {
        ByteBuffer header = ByteBuffer.allocate(WAV_HEADER_SIZE);
        header.order(ByteOrder.LITTLE_ENDIAN);
        
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        
        header.put("RIFF".getBytes());
        header.putInt(pcmLength + WAV_HEADER_SIZE - 8);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put("data".getBytes());
        header.putInt(pcmLength);
        
        return header.array();
    }
    
    public static byte[] pcmToWav(byte[] pcmData, int sampleRate, int channels) {
        byte[] header = createWavHeader(pcmData.length, sampleRate, channels, 16);
        byte[] wavData = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, wavData, 0, header.length);
        System.arraycopy(pcmData, 0, wavData, header.length, pcmData.length);
        return wavData;
    }
    
    public static List<byte[]> splitPcmIntoFrames(byte[] pcmData, int frameSize) {
        List<byte[]> frames = new ArrayList<>();
        int frameBytes = frameSize * 2;
        
        for (int i = 0; i < pcmData.length; i += frameBytes) {
            int length = Math.min(frameBytes, pcmData.length - i);
            byte[] frame = new byte[length];
            System.arraycopy(pcmData, i, frame, 0, length);
            frames.add(frame);
        }
        
        return frames;
    }
    
    public static byte[] mergePcmFrames(List<byte[]> frames) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            baos.write(frame, 0, frame.length);
        }
        return baos.toByteArray();
    }
    
    public static int calculateRms(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0;
        }
        
        long sumSquares = 0;
        int sampleCount = 0;
        
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sumSquares += (long) sample * sample;
            sampleCount++;
        }
        
        if (sampleCount == 0) {
            return 0;
        }
        
        double rms = Math.sqrt((double) sumSquares / sampleCount);
        return (int) (rms * 100 / Short.MAX_VALUE);
    }
    
    public static boolean isSilence(byte[] pcmData, int threshold) {
        int rms = calculateRms(pcmData);
        return rms < threshold;
    }
    
    public static double calculateDurationSeconds(byte[] pcmData, int sampleRate, int channels) {
        int bytesPerSample = 2;
        double bytesPerSecond = sampleRate * channels * bytesPerSample;
        return pcmData.length / bytesPerSecond;
    }
}
