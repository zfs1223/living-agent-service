mod backend;
mod entry;
mod query;

pub use backend::{MemoryBackend, MemoryConfig};
pub use entry::{MemoryEntry, MemoryCategory};
pub use query::{MemoryQuery, SearchResult};

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryStats {
    pub total_entries: u64,
    pub entries_by_category: HashMap<String, u64>,
    pub total_bytes: u64,
    pub last_access: u64,
}

impl Default for MemoryStats {
    fn default() -> Self {
        Self {
            total_entries: 0,
            entries_by_category: HashMap::new(),
            total_bytes: 0,
            last_access: 0,
        }
    }
}
