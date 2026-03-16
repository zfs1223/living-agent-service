use crate::knowledge::types::{KnowledgeEntry, KnowledgeStats, KnowledgeType, Importance, Validity, KnowledgeMetadata};
use crate::knowledge::similarity::{cosine_similarity, euclidean_distance};
use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use anyhow::Result;
use serde::{Deserialize, Serialize};

pub struct VectorSearchResult {
    pub entry: KnowledgeEntry,
    pub score: f32,
    pub distance: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VectorEntry {
    pub id: String,
    pub vector: Vec<f32>,
    pub metadata: HashMap<String, String>,
}

pub struct VectorStore {
    entries: Arc<RwLock<HashMap<String, VectorEntry>>>,
    vectors: Arc<RwLock<HashMap<String, Vec<f32>>>>,
    dimension: usize,
}

impl VectorStore {
    pub fn new(dimension: usize) -> Self {
        Self {
            entries: Arc::new(RwLock::new(HashMap::new())),
            vectors: Arc::new(RwLock::new(HashMap::new())),
            dimension,
        }
    }
    
    pub fn store(&self, id: String, vector: Vec<f32>, metadata: HashMap<String, String>) -> Result<()> {
        if vector.len() != self.dimension {
            return Err(anyhow::anyhow!("Vector dimension mismatch: expected {}, got {}", self.dimension, vector.len()));
        }
        
        let entry = VectorEntry { id: id.clone(), vector: vector.clone(), metadata };
        
        self.entries.write().insert(id.clone(), entry);
        self.vectors.write().insert(id, vector);
        
        Ok(())
    }
    
    pub fn search(&self, query: &[f32], top_k: usize, threshold: f32) -> Vec<VectorSearchResult> {
        if query.len() != self.dimension {
            return Vec::new();
        }
        
        let vectors = self.vectors.read();
        let entries = self.entries.read();
        
        let mut results: Vec<(String, f32, f32)> = vectors
            .iter()
            .filter_map(|(id, vec)| {
                let sim = cosine_similarity(query, vec);
                let dist = euclidean_distance(query, vec);
                Some((id.clone(), sim, dist))
            })
            .filter(|(_, sim, _)| *sim >= threshold)
            .collect();
        
        results.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(top_k);
        
        results
            .into_iter()
            .filter_map(|(id, sim, dist)| {
                if let Some(entry) = entries.get(&id) {
                    Some(VectorSearchResult {
                        entry: KnowledgeEntry::new(
                            entry.id.clone(),
                            entry.metadata.get("content").cloned().unwrap_or_default(),
                            KnowledgeType::Fact,
                        )
                        .with_vector(entry.vector.clone()),
                        score: sim,
                        distance: dist,
                    })
                } else {
                    None
                }
            })
            .collect()
    }
    
    pub fn get(&self, id: &str) -> Option<VectorEntry> {
        self.entries.read().get(id).cloned()
    }
    
    pub fn remove(&self, id: &str) -> bool {
        let mut entries = self.entries.write();
        let mut vectors = self.vectors.write();
        
        let existed = entries.remove(id).is_some();
        vectors.remove(id);
        
        existed
    }
    
    pub fn count(&self) -> usize {
        self.entries.read().len()
    }
    
    pub fn clear(&self) {
        self.entries.write().clear();
        self.vectors.write().clear();
    }
}
