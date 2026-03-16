use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommandPolicy {
    pub name: String,
    pub allowed_commands: Vec<String>,
    pub denied_commands: Vec<String>,
    pub requires_approval: bool,
}

impl CommandPolicy {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            allowed_commands: Vec::new(),
            denied_commands: Vec::new(),
            requires_approval: false,
        }
    }
    
    pub fn allow(mut self, command: impl Into<String>) -> Self {
        self.allowed_commands.push(command.into());
        self
    }
    
    pub fn deny(mut self, command: impl Into<String>) -> Self {
        self.denied_commands.push(command.into());
        self
    }
    
    pub fn requires_approval(mut self, requires: bool) -> Self {
        self.requires_approval = requires;
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PathPolicy {
    pub name: String,
    pub allowed_paths: Vec<String>,
    pub denied_paths: Vec<String>,
    pub allow_create: bool,
    pub allow_delete: bool,
}

impl PathPolicy {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            allowed_paths: Vec::new(),
            denied_paths: Vec::new(),
            allow_create: false,
            allow_delete: false,
        }
    }
    
    pub fn allow_path(mut self, path: impl Into<String>) -> Self {
        self.allowed_paths.push(path.into());
        self
    }
    
    pub fn deny_path(mut self, path: impl Into<String>) -> Self {
        self.denied_paths.push(path.into());
        self
    }
    
    pub fn allow_create(mut self, allow: bool) -> Self {
        self.allow_create = allow;
        self
    }
    
    pub fn allow_delete(mut self, allow: bool) -> Self {
        self.allow_delete = allow;
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityPolicy {
    pub name: String,
    pub command_policies: HashMap<String, CommandPolicy>,
    pub path_policies: HashMap<String, PathPolicy>,
    pub default_requires_approval: bool,
    pub max_command_length: usize,
    pub rate_limit_per_minute: u32,
}

impl SecurityPolicy {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            command_policies: HashMap::new(),
            path_policies: HashMap::new(),
            default_requires_approval: false,
            max_command_length: 1000,
            rate_limit_per_minute: 60,
        }
    }
    
    pub fn add_command_policy(mut self, policy: CommandPolicy) -> Self {
        self.command_policies.insert(policy.name.clone(), policy);
        self
    }
    
    pub fn add_path_policy(mut self, policy: PathPolicy) -> Self {
        self.path_policies.insert(policy.name.clone(), policy);
        self
    }
    
    pub fn default_requires_approval(mut self, requires: bool) -> Self {
        self.default_requires_approval = requires;
        self
    }
    
    pub fn max_command_length(mut self, length: usize) -> Self {
        self.max_command_length = length;
        self
    }
    
    pub fn rate_limit(mut self, limit: u32) -> Self {
        self.rate_limit_per_minute = limit;
        self
    }
}

impl Default for SecurityPolicy {
    fn default() -> Self {
        Self::new("default")
            .default_requires_approval(false)
            .max_command_length(1000)
            .rate_limit(60)
    }
}
