use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MemoryCategory {
    Core,
    Daily,
    Conversation,
    Custom,
}

impl MemoryCategory {
    pub fn from_str(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "core" => Self::Core,
            "daily" => Self::Daily,
            "conversation" => Self::Conversation,
            _ => Self::Custom,
        }
    }
}

impl std::fmt::Display for MemoryCategory {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Core => write!(f, "CORE"),
            Self::Daily => write!(f, "DAILY"),
            Self::Conversation => write!(f, "CONVERSATION"),
            Self::Custom => write!(f, "CUSTOM"),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryEntry {
    pub id: String,
    pub key: String,
    pub content: String,
    pub category: MemoryCategory,
    pub session_id: Option<String>,
    pub timestamp: u64,
    pub score: Option<f64>,
    pub metadata: HashMap<String, String>,
}

impl MemoryEntry {
    pub fn new(key: impl Into<String>, content: impl Into<String>, category: MemoryCategory) -> Self {
        Self {
            id: uuid::Uuid::new_v4().to_string(),
            key: key.into(),
            content: content.into(),
            category,
            session_id: None,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0),
            score: None,
            metadata: HashMap::new(),
        }
    }
    
    pub fn core(key: impl Into<String>, content: impl Into<String>) -> Self {
        Self::new(key, content, MemoryCategory::Core)
    }
    
    pub fn daily(key: impl Into<String>, content: impl Into<String>, session_id: impl Into<String>) -> Self {
        let mut entry = Self::new(key, content, MemoryCategory::Daily);
        entry.session_id = Some(session_id.into());
        entry
    }
    
    pub fn conversation(key: impl Into<String>, content: impl Into<String>, session_id: impl Into<String>) -> Self {
        let mut entry = Self::new(key, content, MemoryCategory::Conversation);
        entry.session_id = Some(session_id.into());
        entry
    }
    
    pub fn with_score(mut self, score: f64) -> Self {
        self.score = Some(score);
        self
    }
    
    pub fn with_metadata(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.metadata.insert(key.into(), value.into());
        self
    }
}
