mod opus_codec;
mod vad;
mod resampler;
mod processor;

pub use opus_codec::{OpusDecoder, OpusEncoder, OpusConfig, AudioFrame};
pub use vad::{VadDetector, VadConfig, VoiceActivity};
pub use resampler::{Resampler, ResampleConfig};
pub use processor::{AudioProcessor, AudioConfig};

#[derive(Debug, Clone)]
pub struct AudioStats {
    pub frames_processed: u64,
    pub total_duration_ms: u64,
    pub voice_frames: u64,
    pub silence_frames: u64,
}

impl Default for AudioStats {
    fn default() -> Self {
        Self {
            frames_processed: 0,
            total_duration_ms: 0,
            voice_frames: 0,
            silence_frames: 0,
        }
    }
}
