use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MessagePriority {
    Low = 0,
    Normal = 1,
    High = 2,
    Urgent = 3,
}

impl Default for MessagePriority {
    fn default() -> Self {
        Self::Normal
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MessageType {
    Request,
    Response,
    Event,
    Error,
    Control,
}

impl Default for MessageType {
    fn default() -> Self {
        Self::Request
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChannelMessage {
    pub id: String,
    pub message_type: MessageType,
    pub priority: MessagePriority,
    pub source: String,
    pub target: Option<String>,
    pub payload: serde_json::Value,
    pub metadata: HashMap<String, String>,
    pub timestamp: u64,
    pub correlation_id: Option<String>,
}

impl ChannelMessage {
    pub fn new(source: impl Into<String>, payload: serde_json::Value) -> Self {
        Self {
            id: uuid::Uuid::new_v4().to_string(),
            message_type: MessageType::Request,
            priority: MessagePriority::Normal,
            source: source.into(),
            target: None,
            payload,
            metadata: HashMap::new(),
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0),
            correlation_id: None,
        }
    }
    
    pub fn request(source: impl Into<String>, payload: serde_json::Value) -> Self {
        let mut msg = Self::new(source, payload);
        msg.message_type = MessageType::Request;
        msg
    }
    
    pub fn response(source: impl Into<String>, correlation_id: impl Into<String>, payload: serde_json::Value) -> Self {
        let mut msg = Self::new(source, payload);
        msg.message_type = MessageType::Response;
        msg.correlation_id = Some(correlation_id.into());
        msg
    }
    
    pub fn event(source: impl Into<String>, payload: serde_json::Value) -> Self {
        let mut msg = Self::new(source, payload);
        msg.message_type = MessageType::Event;
        msg
    }
    
    pub fn error(source: impl Into<String>, error_message: impl Into<String>) -> Self {
        let mut msg = Self::new(source, serde_json::json!({ "error": error_message.into() }));
        msg.message_type = MessageType::Error;
        msg
    }
    
    pub fn with_priority(mut self, priority: MessagePriority) -> Self {
        self.priority = priority;
        self
    }
    
    pub fn with_target(mut self, target: impl Into<String>) -> Self {
        self.target = Some(target.into());
        self
    }
    
    pub fn with_metadata(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.metadata.insert(key.into(), value.into());
        self
    }
    
    pub fn with_correlation_id(mut self, id: impl Into<String>) -> Self {
        self.correlation_id = Some(id.into());
        self
    }
    
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }
    
    pub fn from_json(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }
}

impl PartialEq for ChannelMessage {
    fn eq(&self, other: &Self) -> bool {
        self.id == other.id
    }
}

impl std::hash::Hash for ChannelMessage {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.id.hash(state);
    }
}
