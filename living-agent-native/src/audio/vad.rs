const DEFAULT_ENERGY_THRESHOLD: f32 = 0.01;
const DEFAULT_SILENCE_DURATION_MS: u64 = 500;
const DEFAULT_SPEECH_DURATION_MS: u64 = 100;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VoiceActivity {
    Silence,
    Voice,
    Transition,
}

#[derive(Debug, Clone)]
pub struct VadConfig {
    pub energy_threshold: f32,
    pub silence_duration_ms: u64,
    pub speech_duration_ms: u64,
    pub sample_rate: u32,
}

impl Default for VadConfig {
    fn default() -> Self {
        Self {
            energy_threshold: DEFAULT_ENERGY_THRESHOLD,
            silence_duration_ms: DEFAULT_SILENCE_DURATION_MS,
            speech_duration_ms: DEFAULT_SPEECH_DURATION_MS,
            sample_rate: 16000,
        }
    }
}

pub struct VadDetector {
    config: VadConfig,
    state: VadState,
    frame_count: u64,
}

struct VadState {
    current_activity: VoiceActivity,
    consecutive_silence_frames: u64,
    consecutive_voice_frames: u64,
    total_frames: u64,
}

impl VadDetector {
    pub fn new(config: VadConfig) -> Self {
        Self {
            config,
            state: VadState {
                current_activity: VoiceActivity::Silence,
                consecutive_silence_frames: 0,
                consecutive_voice_frames: 0,
                total_frames: 0,
            },
            frame_count: 0,
        }
    }
    
    pub fn detect(&mut self, pcm_data: &[i16]) -> VoiceActivity {
        let energy = self.calculate_energy(pcm_data);
        let is_voice = energy > self.config.energy_threshold;
        
        self.state.total_frames += 1;
        
        if is_voice {
            self.state.consecutive_voice_frames += 1;
            self.state.consecutive_silence_frames = 0;
        } else {
            self.state.consecutive_silence_frames += 1;
            self.state.consecutive_voice_frames = 0;
        }
        
        let frame_duration_ms = (pcm_data.len() as f64 / self.config.sample_rate as f64) * 1000.0;
        
        let silence_duration = self.state.consecutive_silence_frames as f64 * frame_duration_ms;
        let voice_duration = self.state.consecutive_voice_frames as f64 * frame_duration_ms;
        
        let new_activity = match self.state.current_activity {
            VoiceActivity::Silence => {
                if voice_duration >= self.config.speech_duration_ms as f64 {
                    VoiceActivity::Voice
                } else {
                    VoiceActivity::Silence
                }
            }
            VoiceActivity::Voice => {
                if silence_duration >= self.config.silence_duration_ms as f64 {
                    VoiceActivity::Silence
                } else {
                    VoiceActivity::Voice
                }
            }
            VoiceActivity::Transition => {
                if is_voice {
                    VoiceActivity::Voice
                } else {
                    VoiceActivity::Silence
                }
            }
        };
        
        if new_activity != self.state.current_activity {
            self.state.current_activity = VoiceActivity::Transition;
        } else {
            self.state.current_activity = new_activity;
        }
        
        self.state.current_activity
    }
    
    fn calculate_energy(&self, pcm_data: &[i16]) -> f32 {
        if pcm_data.is_empty() {
            return 0.0;
        }
        
        let sum: f64 = pcm_data
            .iter()
            .map(|&s| (s as f64 / i16::MAX as f64).powi(2))
            .sum();
        
        (sum / pcm_data.len() as f64) as f32
    }
    
    pub fn reset(&mut self) {
        self.state = VadState {
            current_activity: VoiceActivity::Silence,
            consecutive_silence_frames: 0,
            consecutive_voice_frames: 0,
            total_frames: 0,
        };
        self.frame_count = 0;
    }
    
    pub fn current_activity(&self) -> VoiceActivity {
        self.state.current_activity
    }
    
    pub fn is_speaking(&self) -> bool {
        self.state.current_activity == VoiceActivity::Voice
    }
}
