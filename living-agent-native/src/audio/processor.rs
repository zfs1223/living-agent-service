use crate::audio::{OpusConfig, VadConfig, AudioStats};
use super::{OpusDecoder, OpusEncoder, VadDetector, AudioFrame};
use anyhow::Result;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

const DEFAULT_SAMPLE_RATE: u32 = 16000;
const DEFAULT_FRAME_SIZE: usize = 960;

#[derive(Debug, Clone)]
pub struct AudioConfig {
    pub sample_rate: u32,
    pub channels: u8,
    pub frame_size: usize,
    pub enable_vad: bool,
    pub vad_config: VadConfig,
}

impl Default for AudioConfig {
    fn default() -> Self {
        Self {
            sample_rate: DEFAULT_SAMPLE_RATE,
            channels: 1,
            frame_size: DEFAULT_FRAME_SIZE,
            enable_vad: true,
            vad_config: VadConfig::default(),
        }
    }
}

#[allow(dead_code)]
pub struct AudioProcessor {
    config: AudioConfig,
    opus_decoder: Option<OpusDecoder>,
    opus_encoder: Option<OpusEncoder>,
    vad_detector: Option<VadDetector>,
    stats: Arc<AudioStatsInner>,
}

struct AudioStatsInner {
    frames_processed: AtomicU64,
    voice_frames: AtomicU64,
    silence_frames: AtomicU64,
    total_bytes: AtomicU64,
}

impl AudioProcessor {
    pub fn new(config: AudioConfig) -> Result<Self> {
        let frame_size_ms = if config.sample_rate > 0 {
            (config.frame_size as u32 * 1000) / config.sample_rate
        } else {
            20
        };
        
        let opus_config = OpusConfig {
            sample_rate: config.sample_rate,
            channels: config.channels,
            frame_size_ms,
            ..Default::default()
        };
        
        let opus_decoder = Some(OpusDecoder::new(opus_config.clone())?);
        let opus_encoder = Some(OpusEncoder::new(opus_config)?);
        
        let vad_detector = if config.enable_vad {
            Some(VadDetector::new(config.vad_config.clone()))
        } else {
            None
        };
        
        Ok(Self {
            config,
            opus_decoder,
            opus_encoder,
            vad_detector,
            stats: Arc::new(AudioStatsInner {
                frames_processed: AtomicU64::new(0),
                voice_frames: AtomicU64::new(0),
                silence_frames: AtomicU64::new(0),
                total_bytes: AtomicU64::new(0),
            }),
        })
    }
    
    pub fn decode_opus(&mut self, opus_data: &[u8]) -> Result<AudioFrame> {
        if opus_data.is_empty() {
            return Err(anyhow::anyhow!("Cannot decode empty opus data"));
        }
        
        let decoder = self.opus_decoder.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Opus decoder not initialized"))?;
        
        let frame = decoder.decode_packet(opus_data)?;
        
        self.stats.frames_processed.fetch_add(1, Ordering::Relaxed);
        self.stats.total_bytes.fetch_add(opus_data.len() as u64, Ordering::Relaxed);
        
        Ok(frame)
    }
    
    pub fn encode_pcm(&mut self, pcm_data: &[i16]) -> Result<Vec<u8>> {
        if pcm_data.is_empty() {
            return Err(anyhow::anyhow!("Cannot encode empty PCM data"));
        }
        
        let encoder = self.opus_encoder.as_mut()
            .ok_or_else(|| anyhow::anyhow!("Opus encoder not initialized"))?;
        
        encoder.encode(pcm_data)
    }
    
    pub fn detect_voice_activity(&mut self, pcm_data: &[i16]) -> bool {
        if let Some(ref mut vad) = self.vad_detector {
            let activity = vad.detect(pcm_data);
            let is_voice = activity == super::VoiceActivity::Voice;
            
            if is_voice {
                self.stats.voice_frames.fetch_add(1, Ordering::Relaxed);
            } else {
                self.stats.silence_frames.fetch_add(1, Ordering::Relaxed);
            }
            
            is_voice
        } else {
            true
        }
    }
    
    pub fn apply_gain(pcm_data: &mut [i16], gain_db: f32) {
        let gain_factor = 10f32.powf(gain_db / 20.0);
        
        for sample in pcm_data.iter_mut() {
            let amplified = (*sample as f32) * gain_factor;
            *sample = amplified.clamp(i16::MIN as f32, i16::MAX as f32) as i16;
        }
    }
    
    pub fn merge_frames(frames: &[AudioFrame]) -> AudioFrame {
        if frames.is_empty() {
            return AudioFrame {
                samples: Vec::new(),
                sample_rate: DEFAULT_SAMPLE_RATE,
                channels: 1,
                timestamp_ms: 0,
            };
        }
        
        let total_samples: usize = frames.iter().map(|f| f.samples.len()).sum();
        let mut merged_samples = Vec::with_capacity(total_samples);
        
        for frame in frames {
            merged_samples.extend_from_slice(&frame.samples);
        }
        
        AudioFrame {
            samples: merged_samples,
            sample_rate: frames[0].sample_rate,
            channels: frames[0].channels,
            timestamp_ms: frames[0].timestamp_ms,
        }
    }
    
    pub fn split_into_frames(pcm_data: &[i16], frame_size: usize) -> Vec<Vec<i16>> {
        pcm_data
            .chunks(frame_size)
            .map(|chunk| chunk.to_vec())
            .collect()
    }
    
    pub fn get_stats(&self) -> AudioStats {
        AudioStats {
            frames_processed: self.stats.frames_processed.load(Ordering::Relaxed),
            total_duration_ms: 0,
            voice_frames: self.stats.voice_frames.load(Ordering::Relaxed),
            silence_frames: self.stats.silence_frames.load(Ordering::Relaxed),
        }
    }
    
    pub fn reset_stats(&self) {
        self.stats.frames_processed.store(0, Ordering::Relaxed);
        self.stats.voice_frames.store(0, Ordering::Relaxed);
        self.stats.silence_frames.store(0, Ordering::Relaxed);
        self.stats.total_bytes.store(0, Ordering::Relaxed);
    }
}
