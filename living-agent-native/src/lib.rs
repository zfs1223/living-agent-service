pub mod audio;
pub mod channel;
pub mod security;
pub mod memory;
pub mod knowledge;
pub mod jni;

pub use audio::{AudioProcessor, AudioConfig, VadDetector};
pub use channel::{MpscChannel, BroadcastChannel, ChannelMessage, Channel};
pub use security::{SecurityValidator, SecurityContext, SecurityLevel};
pub use memory::{MemoryBackend, MemoryEntry, MemoryQuery, MemoryCategory};
pub use knowledge::{
    KnowledgeEntry, KnowledgeType, KnowledgeMetadata,
    KnowledgeStats, SearchResult, SearchType,
    VectorStore, SQLiteKnowledgeBackend, KnowledgeCache,
    cosine_similarity, euclidean_distance, dot_product,
};

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn init() {
    tracing_subscriber::fmt::init();
}

pub fn version() -> &'static str {
    VERSION
}
