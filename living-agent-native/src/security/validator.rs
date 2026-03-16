use crate::security::{SecurityContext, SecurityLevel};
use regex::Regex;
use std::collections::HashSet;

#[derive(Debug, Clone)]
pub struct ValidationResult {
    pub is_valid: bool,
    pub reason: Option<String>,
    pub risk_level: RiskLevel,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RiskLevel {
    Safe,
    Low,
    Medium,
    High,
    Critical,
}

impl Default for ValidationResult {
    fn default() -> Self {
        Self {
            is_valid: true,
            reason: None,
            risk_level: RiskLevel::Safe,
        }
    }
}

impl ValidationResult {
    pub fn valid() -> Self {
        Self::default()
    }
    
    pub fn invalid(reason: impl Into<String>) -> Self {
        Self {
            is_valid: false,
            reason: Some(reason.into()),
            risk_level: RiskLevel::Medium,
        }
    }
    
    pub fn invalid_with_risk(reason: impl Into<String>, risk: RiskLevel) -> Self {
        Self {
            is_valid: false,
            reason: Some(reason.into()),
            risk_level: risk,
        }
    }
}

pub struct SecurityValidator {
    dangerous_commands: HashSet<String>,
    dangerous_patterns: Vec<Regex>,
    allowed_paths: Vec<String>,
    denied_paths: Vec<String>,
}

impl SecurityValidator {
    pub fn new() -> Self {
        let dangerous_commands = Self::default_dangerous_commands();
        let dangerous_patterns = Self::default_dangerous_patterns();
        
        Self {
            dangerous_commands,
            dangerous_patterns,
            allowed_paths: Vec::new(),
            denied_paths: Vec::new(),
        }
    }
    
    fn default_dangerous_commands() -> HashSet<String> {
        [
            "rm", "rmdir", "del", "format", "fdisk", "mkfs",
            "dd", "shred", "wipe", "sudo", "su", "chmod", "chown",
            "passwd", "useradd", "userdel", "usermod",
            "shutdown", "reboot", "halt", "poweroff",
            "iptables", "ufw", "firewall-cmd",
            "curl", "wget", "nc", "netcat", "telnet",
            "python", "python3", "perl", "ruby", "php",
            "bash", "sh", "zsh", "fish", "cmd", "powershell",
            "eval", "exec", "source",
        ].iter().map(|s| s.to_string()).collect()
    }
    
    fn default_dangerous_patterns() -> Vec<Regex> {
        vec![
            Regex::new(r";\s*rm\s").unwrap(),
            Regex::new(r"\|\s*rm\s").unwrap(),
            Regex::new(r"`[^`]*`").unwrap(),
            Regex::new(r"\$\([^)]*\)").unwrap(),
            Regex::new(r"\$\{[^}]*\}").unwrap(),
            Regex::new(r">\s*/dev/").unwrap(),
            Regex::new(r"<\s*/dev/").unwrap(),
            Regex::new(r"\.\./").unwrap(),
            Regex::new(r"~/" ).unwrap(),
            Regex::new(r"/etc/passwd").unwrap(),
            Regex::new(r"/etc/shadow").unwrap(),
            Regex::new(r"id_rsa").unwrap(),
        ]
    }
    
    pub fn validate_command(&self, command: &str, context: &SecurityContext) -> ValidationResult {
        let parts: Vec<&str> = command.split_whitespace().collect();
        
        if parts.is_empty() {
            return ValidationResult::valid();
        }
        
        let base_command = parts[0].to_lowercase();
        
        if self.dangerous_commands.contains(&base_command) {
            if context.security_level == SecurityLevel::Full {
                return ValidationResult {
                    is_valid: true,
                    reason: Some(format!("Dangerous command '{}' allowed in full mode", base_command)),
                    risk_level: RiskLevel::High,
                };
            }
            
            return ValidationResult::invalid_with_risk(
                format!("Command '{}' is not allowed", base_command),
                RiskLevel::Critical,
            );
        }
        
        for pattern in &self.dangerous_patterns {
            if pattern.is_match(command) {
                return ValidationResult::invalid_with_risk(
                    format!("Command contains dangerous pattern: {}", pattern),
                    RiskLevel::High,
                );
            }
        }
        
        ValidationResult::valid()
    }
    
    pub fn validate_path(&self, path: &str, context: &SecurityContext) -> ValidationResult {
        let normalized = self.normalize_path(path);
        
        for denied in &self.denied_paths {
            if normalized.starts_with(denied) {
                return ValidationResult::invalid_with_risk(
                    format!("Path '{}' is denied", path),
                    RiskLevel::High,
                );
            }
        }
        
        if !self.allowed_paths.is_empty() {
            let is_allowed = self.allowed_paths.iter().any(|allowed| {
                normalized.starts_with(allowed)
            });
            
            if !is_allowed && context.security_level != SecurityLevel::Full {
                return ValidationResult::invalid(
                    format!("Path '{}' is not in allowed paths", path)
                );
            }
        }
        
        if normalized.contains("..") {
            return ValidationResult::invalid_with_risk(
                "Path traversal detected",
                RiskLevel::High,
            );
        }
        
        ValidationResult::valid()
    }
    
    fn normalize_path(&self, path: &str) -> String {
        let mut normalized = path.replace('\\', "/");
        
        while normalized.contains("//") {
            normalized = normalized.replace("//", "/");
        }
        
        if normalized.starts_with("./") {
            normalized = normalized[2..].to_string();
        }
        
        normalized
    }
    
    pub fn add_allowed_path(&mut self, path: impl Into<String>) {
        self.allowed_paths.push(path.into());
    }
    
    pub fn add_denied_path(&mut self, path: impl Into<String>) {
        self.denied_paths.push(path.into());
    }
    
    pub fn add_allowed_command(&mut self, command: impl Into<String>) {
        let cmd = command.into().to_lowercase();
        self.dangerous_commands.remove(&cmd);
    }
}

impl Default for SecurityValidator {
    fn default() -> Self {
        Self::new()
    }
}
