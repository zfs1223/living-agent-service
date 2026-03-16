mod validator;
mod policy;
mod sandbox;

pub use validator::{SecurityValidator, ValidationResult};
pub use policy::{CommandPolicy, PathPolicy, SecurityPolicy};
pub use sandbox::{Sandbox, SandboxConfig};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SecurityLevel {
    ReadOnly,
    Supervised,
    Full,
}

impl Default for SecurityLevel {
    fn default() -> Self {
        Self::Supervised
    }
}

#[derive(Debug, Clone)]
pub struct SecurityContext {
    pub user_id: String,
    pub session_id: String,
    pub security_level: SecurityLevel,
    pub allowed_commands: Vec<String>,
    pub allowed_paths: Vec<String>,
}

impl SecurityContext {
    pub fn new(user_id: impl Into<String>, session_id: impl Into<String>) -> Self {
        Self {
            user_id: user_id.into(),
            session_id: session_id.into(),
            security_level: SecurityLevel::default(),
            allowed_commands: Vec::new(),
            allowed_paths: Vec::new(),
        }
    }
    
    pub fn with_level(mut self, level: SecurityLevel) -> Self {
        self.security_level = level;
        self
    }
    
    pub fn allow_command(mut self, command: impl Into<String>) -> Self {
        self.allowed_commands.push(command.into());
        self
    }
    
    pub fn allow_path(mut self, path: impl Into<String>) -> Self {
        self.allowed_paths.push(path.into());
        self
    }
}
