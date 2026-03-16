use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SandboxConfig {
    pub enabled: bool,
    pub max_memory_mb: u64,
    pub max_cpu_percent: u32,
    pub timeout_seconds: u64,
    pub allowed_networks: Vec<String>,
    pub read_only_paths: Vec<String>,
    pub writable_paths: Vec<String>,
}

impl Default for SandboxConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            max_memory_mb: 512,
            max_cpu_percent: 80,
            timeout_seconds: 300,
            allowed_networks: Vec::new(),
            read_only_paths: Vec::new(),
            writable_paths: Vec::new(),
        }
    }
}

pub struct Sandbox {
    config: SandboxConfig,
}

impl Sandbox {
    pub fn new(config: SandboxConfig) -> Self {
        Self { config }
    }
    
    pub fn is_enabled(&self) -> bool {
        self.config.enabled
    }
    
    pub fn config(&self) -> &SandboxConfig {
        &self.config
    }
    
    pub fn validate_path(&self, path: &str, write: bool) -> bool {
        if write {
            self.config.writable_paths.iter().any(|p| path.starts_with(p))
        } else {
            self.config.read_only_paths.iter().any(|p| path.starts_with(p))
                || self.config.writable_paths.iter().any(|p| path.starts_with(p))
        }
    }
    
    pub fn validate_network(&self, host: &str) -> bool {
        if self.config.allowed_networks.is_empty() {
            return true;
        }
        self.config.allowed_networks.iter().any(|n| host.ends_with(n) || host == n)
    }
}
