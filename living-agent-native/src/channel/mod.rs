mod mpsc_channel;
mod broadcast_channel;
mod message;

pub use mpsc_channel::{MpscChannel, MpscSender, MpscReceiver};
pub use broadcast_channel::{BroadcastChannel, BroadcastSender, BroadcastReceiver};
pub use message::{ChannelMessage, MessagePriority, MessageType};

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChannelConfig {
    pub name: String,
    pub capacity: usize,
    pub enable_priority: bool,
    pub timeout_ms: u64,
}

impl Default for ChannelConfig {
    fn default() -> Self {
        Self {
            name: "default".to_string(),
            capacity: 1024,
            enable_priority: false,
            timeout_ms: 5000,
        }
    }
}

pub trait Channel: Send + Sync {
    type Message;
    
    fn name(&self) -> &str;
    fn send(&self, message: Self::Message) -> Result<(), ChannelError>;
    fn try_send(&self, message: Self::Message) -> Result<(), ChannelError>;
    fn recv(&self) -> Result<Self::Message, ChannelError>;
    fn try_recv(&self) -> Result<Self::Message, ChannelError>;
    fn len(&self) -> usize;
    fn is_empty(&self) -> bool;
    fn is_full(&self) -> bool;
    fn close(&self);
    fn is_closed(&self) -> bool;
}

#[derive(Debug, Clone, thiserror::Error)]
pub enum ChannelError {
    #[error("Channel is closed")]
    Closed,
    #[error("Channel is full")]
    Full,
    #[error("Channel is empty")]
    Empty,
    #[error("Send timeout")]
    SendTimeout,
    #[error("Receive timeout")]
    RecvTimeout,
    #[error("IO error: {0}")]
    Io(String),
}
