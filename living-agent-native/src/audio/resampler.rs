use anyhow::Result;

#[derive(Debug, Clone)]
pub struct ResampleConfig {
    pub source_rate: u32,
    pub target_rate: u32,
    pub channels: u8,
}

impl Default for ResampleConfig {
    fn default() -> Self {
        Self {
            source_rate: 16000,
            target_rate: 48000,
            channels: 1,
        }
    }
}

pub struct Resampler {
    config: ResampleConfig,
}

impl Resampler {
    pub fn new(config: ResampleConfig) -> Result<Self> {
        Ok(Self { config })
    }
    
    pub fn resample(&self, input: &[i16]) -> Result<Vec<i16>> {
        if self.config.source_rate == self.config.target_rate {
            return Ok(input.to_vec());
        }
        
        let ratio = self.config.target_rate as f64 / self.config.source_rate as f64;
        let output_len = (input.len() as f64 * ratio) as usize;
        let mut output = Vec::with_capacity(output_len);
        
        for i in 0..output_len {
            let src_idx = (i as f64 / ratio) as usize;
            let sample = input.get(src_idx).copied().unwrap_or(0);
            output.push(sample);
        }
        
        Ok(output)
    }
    
    pub fn resample_to_f32(&self, input: &[i16]) -> Vec<f32> {
        input.iter().map(|&s| s as f32 / i16::MAX as f32).collect()
    }
    
    pub fn resample_from_f32(&self, input: &[f32]) -> Vec<i16> {
        input.iter().map(|&s| (s * i16::MAX as f32) as i16).collect()
    }
}
