pub mod vector_store;
pub mod sqlite_backend;
pub mod similarity;
pub mod cache;
pub mod types;

pub use vector_store::{VectorStore, VectorSearchResult};
pub use sqlite_backend::SQLiteKnowledgeBackend;
pub use similarity::{cosine_similarity, euclidean_distance, dot_product};
pub use cache::KnowledgeCache;
pub use types::{KnowledgeEntry, KnowledgeType, KnowledgeMetadata, SearchResult, KnowledgeStats, SearchType};
