use serde::{Deserialize, Serialize};
use crate::memory::MemoryEntry;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryQuery {
    pub query: String,
    pub limit: usize,
    pub session_id: Option<String>,
    pub category: Option<String>,
    pub min_score: Option<f64>,
}

impl MemoryQuery {
    pub fn new(query: impl Into<String>) -> Self {
        Self {
            query: query.into(),
            limit: 10,
            session_id: None,
            category: None,
            min_score: None,
        }
    }
    
    pub fn with_limit(mut self, limit: usize) -> Self {
        self.limit = limit;
        self
    }
    
    pub fn with_session(mut self, session_id: impl Into<String>) -> Self {
        self.session_id = Some(session_id.into());
        self
    }
    
    pub fn with_category(mut self, category: impl Into<String>) -> Self {
        self.category = Some(category.into());
        self
    }
    
    pub fn with_min_score(mut self, score: f64) -> Self {
        self.min_score = Some(score);
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResult {
    pub entries: Vec<MemoryEntry>,
    pub total: usize,
    pub query: String,
    pub duration_ms: u64,
}

impl SearchResult {
    pub fn new(entries: Vec<MemoryEntry>, query: impl Into<String>, duration_ms: u64) -> Self {
        let total = entries.len();
        Self {
            entries,
            total,
            query: query.into(),
            duration_ms,
        }
    }
    
    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
    
    pub fn first(&self) -> Option<&MemoryEntry> {
        self.entries.first()
    }
}
