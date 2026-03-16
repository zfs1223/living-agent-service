use anyhow::Result;

#[derive(Debug, Clone)]
pub struct OpusConfig {
    pub sample_rate: u32,
    pub channels: u8,
    pub frame_size_ms: u32,
    pub bitrate: u32,
}

impl Default for OpusConfig {
    fn default() -> Self {
        Self {
            sample_rate: 48000,
            channels: 1,
            frame_size_ms: 20,
            bitrate: 24000,
        }
    }
}

pub struct OpusEncoder {
    encoder: opus::Encoder,
    config: OpusConfig,
    frame_size: usize,
}

impl OpusEncoder {
    pub fn new(config: OpusConfig) -> Result<Self> {
        let channels = if config.channels == 1 {
            opus::Channels::Mono
        } else {
            opus::Channels::Stereo
        };
        
        let mut encoder = opus::Encoder::new(
            config.sample_rate,
            channels,
            opus::Application::Audio,
        )?;
        
        encoder.set_bitrate(opus::Bitrate::Bits(config.bitrate as i32))?;
        
        let frame_size = (config.sample_rate as usize * config.frame_size_ms as usize) / 1000;
        
        Ok(Self {
            encoder,
            config,
            frame_size,
        })
    }
    
    pub fn encode(&mut self, pcm_data: &[i16]) -> Result<Vec<u8>> {
        let mut output = vec![0u8; 1024];
        let len = self.encoder.encode(pcm_data, &mut output)?;
        output.truncate(len);
        Ok(output)
    }
    
    pub fn encode_float(&mut self, float_data: &[f32]) -> Result<Vec<u8>> {
        let pcm_data: Vec<i16> = float_data
            .iter()
            .map(|&s| (s * 32767.0).clamp(i16::MIN as f32, i16::MAX as f32) as i16)
            .collect();
        self.encode(&pcm_data)
    }
    
    pub fn frame_size(&self) -> usize {
        self.frame_size
    }
}

pub struct OpusDecoder {
    decoder: opus::Decoder,
    config: OpusConfig,
    frame_size: usize,
}

impl OpusDecoder {
    pub fn new(config: OpusConfig) -> Result<Self> {
        let channels = if config.channels == 1 {
            opus::Channels::Mono
        } else {
            opus::Channels::Stereo
        };
        
        let decoder = opus::Decoder::new(
            config.sample_rate,
            channels,
        )?;
        
        let frame_size = (config.sample_rate as usize * config.frame_size_ms as usize) / 1000;
        
        Ok(Self {
            decoder,
            config,
            frame_size,
        })
    }
    
    pub fn decode(&mut self, opus_data: &[u8]) -> Result<Vec<i16>> {
        let mut output = vec![0i16; self.frame_size * self.config.channels as usize];
        let len = self.decoder.decode(opus_data, &mut output, false)?;
        output.truncate(len);
        Ok(output)
    }
    
    pub fn decode_to_float(&mut self, opus_data: &[u8]) -> Result<Vec<f32>> {
        let pcm_data = self.decode(opus_data)?;
        Ok(pcm_data.iter().map(|&s| s as f32 / 32767.0).collect())
    }
    
    pub fn decode_packet(&mut self, opus_data: &[u8]) -> Result<AudioFrame> {
        let samples = self.decode(opus_data)?;
        Ok(AudioFrame {
            samples,
            sample_rate: self.config.sample_rate,
            channels: self.config.channels,
            timestamp_ms: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64,
        })
    }
    
    pub fn frame_size(&self) -> usize {
        self.frame_size
    }
}

#[derive(Debug, Clone)]
pub struct AudioFrame {
    pub samples: Vec<i16>,
    pub sample_rate: u32,
    pub channels: u8,
    pub timestamp_ms: u64,
}

impl AudioFrame {
    pub fn duration_ms(&self) -> u32 {
        if self.sample_rate == 0 {
            return 0;
        }
        (self.samples.len() as u32 / self.sample_rate * 1000 / self.channels as u32)
    }
    
    pub fn to_mono(&self) -> Self {
        if self.channels == 1 {
            return self.clone();
        }
        
        let mono_samples: Vec<i16> = self.samples
            .chunks_exact(self.channels as usize)
            .map(|chunk| {
                let sum: i32 = chunk.iter().map(|&s| s as i32).sum();
                (sum / self.channels as i32) as i16
            })
            .collect();
        
        AudioFrame {
            samples: mono_samples,
            sample_rate: self.sample_rate,
            channels: 1,
            timestamp_ms: self.timestamp_ms,
        }
    }
}
