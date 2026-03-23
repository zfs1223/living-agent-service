use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum KnowledgeType {
    Fact,
    Process,
    Experience,
    BestPractice,
    Temporary,
}

impl KnowledgeType {
    pub fn as_str(&self) -> &'static str {
        match self {
            KnowledgeType::Fact => "fact",
            KnowledgeType::Process => "process",
            KnowledgeType::Experience => "experience",
            KnowledgeType::BestPractice => "best_practice",
            KnowledgeType::Temporary => "temporary",
        }
    }
    
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "fact" => Some(KnowledgeType::Fact),
            "process" => Some(KnowledgeType::Process),
            "experience" => Some(KnowledgeType::Experience),
            "best_practice" => Some(KnowledgeType::BestPractice),
            "temporary" => Some(KnowledgeType::Temporary),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Importance {
    High,
    Medium,
    Low,
}

impl Importance {
    pub fn as_str(&self) -> &'static str {
        match self {
            Importance::High => "high",
            Importance::Medium => "medium",
            Importance::Low => "low",
        }
    }
    
    pub fn weight(&self) -> f32 {
        match self {
            Importance::High => 1.0,
            Importance::Medium => 0.6,
            Importance::Low => 0.3,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum Validity {
    Permanent,
    LongTerm,
    ShortTerm,
    Temporary,
}

impl Validity {
    pub fn as_str(&self) -> &'static str {
        match self {
            Validity::Permanent => "permanent",
            Validity::LongTerm => "long_term",
            Validity::ShortTerm => "short_term",
            Validity::Temporary => "temporary",
        }
    }
    
    pub fn ttl_seconds(&self) -> Option<u64> {
        match self {
            Validity::Permanent => None,
            Validity::LongTerm => Some(7 * 24 * 3600),
            Validity::ShortTerm => Some(7 * 24 * 3600),
            Validity::Temporary => Some(7 * 24 * 3600),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct KnowledgeMetadata {
    pub brain_domain: Option<String>,
    pub confidence: f32,
    pub source: Option<String>,
    pub validated: bool,
    pub access_count: u32,
    pub created_at: u64,
    pub updated_at: u64,
}

impl KnowledgeMetadata {
    pub fn default() -> Self {
        Self {
            brain_domain: None,
            confidence: 1.0,
            source: None,
            validated: false,
            access_count: 0,
            created_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
            updated_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KnowledgeEntry {
    pub id: String,
    pub key: String,
    pub content: String,
    pub knowledge_type: KnowledgeType,
    pub importance: Importance,
    pub validity: Validity,
    pub metadata: KnowledgeMetadata,
    pub vector: Option<Vec<f32>>,
    pub created_at: u64,
    pub updated_at: u64,
    pub expires_at: Option<u64>,
}

impl KnowledgeEntry {
    pub fn new(key: String, content: String, knowledge_type: KnowledgeType) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            key,
            content,
            knowledge_type,
            importance: Importance::Medium,
            validity: Validity::LongTerm,
            metadata: KnowledgeMetadata::default(),
            vector: None,
            created_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
            updated_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
            expires_at: None,
        }
    }
    
    pub fn with_brain_domain(mut self, domain: String) -> Self {
        self.metadata.brain_domain = Some(domain);
        self
    }
    
    pub fn with_confidence(mut self, confidence: f32) -> Self {
        self.metadata.confidence = confidence;
        self
    }
    
    pub fn with_validated(mut self, validated: bool) -> Self {
        self.metadata.validated = validated;
        self
    }
    
    pub fn with_importance(mut self, importance: Importance) -> Self {
        self.importance = importance;
        self
    }
    
    pub fn with_vector(mut self, vector: Vec<f32>) -> Self {
        self.vector = Some(vector);
        self
    }
    
    pub fn touch(&mut self) {
        self.metadata.access_count += 1;
        self.updated_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
    }
    
    pub fn relevance_score(&self) -> f32 {
        let importance_weight = match self.importance {
            Importance::High => 1.0,
            Importance::Medium => 0.6,
            Importance::Low => 0.3,
        };
        let confidence = self.metadata.confidence;
        let validation_bonus = if self.metadata.validated { 0.2 } else { 0.0 };
        let access_bonus = (self.metadata.access_count as f32 / 100.0).min(0.3);
        
        (importance_weight * 0.4 + confidence * 0.3 + validation_bonus + access_bonus)
            .min(1.0)
    }
}

#[derive(Debug, Clone, Default)]
pub struct KnowledgeStats {
    pub total_entries: u64,
    pub entries_by_type: HashMap<String, u64>,
    pub entries_by_domain: HashMap<String, u64>,
    pub average_confidence: f32,
    pub validated_count: u64,
    pub expired_count: u64,
    pub vectorized_count: u64,
}

#[derive(Debug, Clone)]
pub struct SearchResult {
    pub entry: KnowledgeEntry,
    pub score: f32,
    pub distance: f32,
}

#[derive(Debug, Clone)]
pub enum SearchType {
    Keyword,
    Semantic,
    Vector,
    Hybrid,
}
