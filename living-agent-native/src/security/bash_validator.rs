use regex::Regex;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BashThreatType {
    ShellMetachar,
    Sudo,
    RmRf,
    CmdSubstitution,
    IfsInjection,
    Safe,
}

#[derive(Debug, Clone)]
pub struct BashValidationResult {
    pub is_safe: bool,
    pub threat_type: BashThreatType,
    pub reason: Option<String>,
    pub severity: BashSeverity,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BashSeverity {
    Low,
    Medium,
    High,
    Critical,
}

impl BashValidationResult {
    pub fn safe() -> Self {
        Self {
            is_safe: true,
            threat_type: BashThreatType::Safe,
            reason: None,
            severity: BashSeverity::Low,
        }
    }

    pub fn threat(threat_type: BashThreatType, reason: impl Into<String>, severity: BashSeverity) -> Self {
        Self {
            is_safe: false,
            threat_type,
            reason: Some(reason.into()),
            severity,
        }
    }
}

struct BashCheck {
    threat_type: BashThreatType,
    pattern: Regex,
    description: &'static str,
    severity: BashSeverity,
}

pub struct BashSecurityValidator {
    checks: Vec<BashCheck>,
}

impl BashSecurityValidator {
    pub fn new() -> Self {
        let checks = vec![
            BashCheck {
                threat_type: BashThreatType::ShellMetachar,
                pattern: Regex::new(r"[;&|`$]").unwrap(),
                description: "Shell metacharacters detected",
                severity: BashSeverity::Medium,
            },
            BashCheck {
                threat_type: BashThreatType::Sudo,
                pattern: Regex::new(r"\bsudo\b").unwrap(),
                description: "Privilege escalation (sudo) detected",
                severity: BashSeverity::Critical,
            },
            BashCheck {
                threat_type: BashThreatType::RmRf,
                pattern: Regex::new(r"\brm\s+(-[a-zA-Z]*)?r").unwrap(),
                description: "Recursive deletion (rm -r) detected",
                severity: BashSeverity::Critical,
            },
            BashCheck {
                threat_type: BashThreatType::CmdSubstitution,
                pattern: Regex::new(r"\$\(").unwrap(),
                description: "Command substitution detected",
                severity: BashSeverity::High,
            },
            BashCheck {
                threat_type: BashThreatType::IfsInjection,
                pattern: Regex::new(r"\bIFS\s*=").unwrap(),
                description: "IFS manipulation detected",
                severity: BashSeverity::High,
            },
        ];

        Self { checks }
    }

    pub fn validate(&self, command: &str) -> BashValidationResult {
        let mut worst_result: Option<BashValidationResult> = None;

        for check in &self.checks {
            if check.pattern.is_match(command) {
                let result = BashValidationResult::threat(
                    check.threat_type,
                    format!("{}: {}", check.description, command),
                    check.severity,
                );

                match &worst_result {
                    None => worst_result = Some(result),
                    Some(existing) => {
                        if result.severity as u8 > existing.severity as u8 {
                            worst_result = Some(result);
                        }
                    }
                }
            }
        }

        worst_result.unwrap_or_else(BashValidationResult::safe)
    }

    pub fn validate_batch(&self, commands: &[&str]) -> Vec<BashValidationResult> {
        commands.iter().map(|cmd| self.validate(cmd)).collect()
    }
}

impl Default for BashSecurityValidator {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_safe_commands() {
        let validator = BashSecurityValidator::new();
        assert!(validator.validate("ls -la").is_safe);
        assert!(validator.validate("cat file.txt").is_safe);
        assert!(validator.validate("echo hello").is_safe);
        assert!(validator.validate("git status").is_safe);
    }

    #[test]
    fn test_sudo_detection() {
        let validator = BashSecurityValidator::new();
        let result = validator.validate("sudo apt install something");
        assert!(!result.is_safe);
        assert_eq!(result.threat_type, BashThreatType::Sudo);
        assert_eq!(result.severity, BashSeverity::Critical);
    }

    #[test]
    fn test_rm_rf_detection() {
        let validator = BashSecurityValidator::new();
        let result = validator.validate("rm -rf /tmp/test");
        assert!(!result.is_safe);
        assert_eq!(result.threat_type, BashThreatType::RmRf);
        assert_eq!(result.severity, BashSeverity::Critical);
    }

    #[test]
    fn test_cmd_substitution() {
        let validator = BashSecurityValidator::new();
        let result = validator.validate("echo $(whoami)");
        assert!(!result.is_safe);
        assert_eq!(result.threat_type, BashThreatType::CmdSubstitution);
    }

    #[test]
    fn test_shell_metachar() {
        let validator = BashSecurityValidator::new();
        let result = validator.validate("ls ; rm -rf /");
        assert!(!result.is_safe);
    }
}
