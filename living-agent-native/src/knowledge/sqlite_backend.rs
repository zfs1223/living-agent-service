use crate::knowledge::types::{
    KnowledgeEntry, KnowledgeType, Importance, Validity, 
    KnowledgeMetadata, KnowledgeStats,
};
use rusqlite::{Connection, params, OptionalExtension};
use std::path::PathBuf;
use std::sync::Mutex;
use anyhow::Result;

#[derive(Debug, Clone)]
pub struct MemoryConfig {
    pub db_path: String,
    pub max_entries: usize,
    pub enable_compression: bool,
}

impl Default for MemoryConfig {
    fn default() -> Self {
        Self {
            db_path: "memory.db".to_string(),
            max_entries: 10000,
            enable_compression: false,
        }
    }
}

pub struct SQLiteKnowledgeBackend {
    conn: Mutex<Connection>,
    config: MemoryConfig,
}

impl SQLiteKnowledgeBackend {
    pub fn new<P: AsRef<std::path::Path>>(path: P) -> Result<Self> {
        let conn = Connection::open(path.as_ref())?;
        
        let backend = Self {
            conn: Mutex::new(conn),
            config: MemoryConfig::default(),
        };
        
        backend.initialize()?;
        Ok(backend)
    }
    
    pub fn in_memory() -> Result<Self> {
        let conn = Connection::open_in_memory()?;
        let backend = Self {
            conn: Mutex::new(conn),
            config: MemoryConfig::default(),
        };
        
        backend.initialize()?;
        Ok(backend)
    }
    
    fn initialize(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS knowledge_entries (
                id TEXT PRIMARY KEY,
                key TEXT UNIQUE NOT NULL,
                content TEXT NOT NULL,
                knowledge_type TEXT NOT NULL DEFAULT 'fact',
                importance TEXT NOT NULL DEFAULT 'medium',
                validity TEXT NOT NULL DEFAULT 'long_term',
                brain_domain TEXT,
                metadata TEXT,
                vector BLOB,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                expires_at INTEGER,
                access_count INTEGER DEFAULT 1,
                relevance_score REAL DEFAULT 1.0
            );
            CREATE INDEX IF NOT EXISTS idx_key ON knowledge_entries(key);
            CREATE INDEX IF NOT EXISTS idx_type ON knowledge_entries(knowledge_type);
            CREATE INDEX IF NOT EXISTS idx_domain ON knowledge_entries(brain_domain);
            CREATE INDEX IF NOT EXISTS idx_created ON knowledge_entries(created_at);",
        )?;
        
        Ok(())
    }
    
    pub fn store(&self, entry: &KnowledgeEntry) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        
        let metadata_json = serde_json::to_string(&entry.metadata)?;
        let brain_domain = entry.metadata.brain_domain.as_deref();
        let vector_bytes = entry.vector.as_ref().map(|v| {
            let mut bytes = vec![0u8; v.len() * 4];
            for (i, val) in v.iter().enumerate() {
                let bits = val.to_bits().to_le_bytes();
                bytes[i * 4..(i + 1) * 4].copy_from_slice(&bits);
            }
            bytes
        });
        
        conn.execute(
            "INSERT OR REPLACE INTO knowledge_entries 
             (id, key, content, knowledge_type, importance, validity, brain_domain, metadata, vector, created_at, updated_at, expires_at, access_count, relevance_score)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)",
            params![
                &entry.id,
                &entry.key,
                &entry.content,
                &entry.knowledge_type.as_str(),
                &entry.importance.as_str(),
                &entry.validity.as_str(),
                brain_domain,
                &metadata_json,
                vector_bytes.as_deref(),
                entry.created_at as i64,
                entry.updated_at as i64,
                entry.expires_at.map(|e| e as i64),
                entry.metadata.access_count as i64,
                entry.relevance_score() as f64,
            ],
        )?;
        
        Ok(())
    }
    
    pub fn retrieve(&self, key: &str) -> Result<Option<KnowledgeEntry>> {
        let conn = self.conn.lock().unwrap();
        
        let result = conn.query_row(
            "SELECT id, key, content, knowledge_type, importance, validity, brain_domain, metadata, vector, created_at, updated_at, expires_at
             FROM knowledge_entries WHERE key = ?1",
            params![key],
            |row| self.row_to_entry(row),
        ).optional()?;
        
        Ok(result)
    }
    
    pub fn search(&self, query: &str, limit: usize) -> Result<Vec<KnowledgeEntry>> {
        let conn = self.conn.lock().unwrap();
        
        let pattern = format!("%{}%", query.to_lowercase());
        
        let mut stmt = conn.prepare(
            "SELECT id, key, content, knowledge_type, importance, validity, brain_domain, metadata, vector, created_at, updated_at, expires_at
             FROM knowledge_entries 
             WHERE LOWER(key) LIKE ?1 OR LOWER(content) LIKE ?1 OR metadata LIKE ?1
             ORDER BY relevance_score DESC
             LIMIT ?2"
        )?;
        
        let entries = stmt
            .query_map(params![&pattern, limit as i64], |row| {
                self.row_to_entry(row)
            })?
            .collect::<Result<Vec<_>, _>>()?;
        
        Ok(entries)
    }
    
    pub fn delete(&self, key: &str) -> Result<bool> {
        let conn = self.conn.lock().unwrap();
        
        let rows = conn.execute("DELETE FROM knowledge_entries WHERE key = ?1", params![key])?;
        
        Ok(rows > 0)
    }
    
    pub fn count(&self) -> Result<u64> {
        let conn = self.conn.lock().unwrap();
        
        let count: i64 = conn.query_row("SELECT COUNT(*) FROM knowledge_entries", [], |row| row.get(0))?;
        
        Ok(count as u64)
    }
    
    pub fn stats(&self) -> Result<KnowledgeStats> {
        let conn = self.conn.lock().unwrap();
        
        let total: i64 = conn.query_row("SELECT COUNT(*) FROM knowledge_entries", [], |row| row.get(0))?;
        
        let mut entries_by_type = std::collections::HashMap::new();
        let mut stmt = conn.prepare("SELECT knowledge_type, COUNT(*) FROM knowledge_entries GROUP BY knowledge_type")?;
        let type_rows = stmt.query_map([], |row| {
            Ok::<_, rusqlite::Error>((row.get::<_, String>(0)?, row.get::<_, i64>(1)?))
        })?.collect::<Result<Vec<_>, _>>()?;
        
        for (kt, count) in type_rows {
            entries_by_type.insert(kt, count as u64);
        }
        
        Ok(KnowledgeStats {
            total_entries: total as u64,
            entries_by_type,
            entries_by_domain: std::collections::HashMap::new(),
            average_confidence: 1.0,
            validated_count: 0,
            expired_count: 0,
            vectorized_count: 0,
        })
    }
    
    pub fn cleanup_expired(&self) -> Result<u64> {
        let conn = self.conn.lock().unwrap();
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        
        let rows = conn.execute(
            "DELETE FROM knowledge_entries WHERE expires_at IS NOT NULL AND expires_at < ?1",
            params![now],
        )?;
        
        Ok(rows as u64)
    }
    
    fn row_to_entry(&self, row: &rusqlite::Row) -> rusqlite::Result<KnowledgeEntry> {
        let id: String = row.get(0)?;
        let key: String = row.get(1)?;
        let content: String = row.get(2)?;
        let knowledge_type_str: String = row.get(3)?;
        let importance_str: String = row.get(4)?;
        let validity_str: String = row.get(5)?;
        let brain_domain: Option<String> = row.get(6)?;
        let metadata_json: String = row.get(7)?;
        let vector_bytes: Option<Vec<u8>> = row.get(8)?;
        let created_at: i64 = row.get(9)?;
        let updated_at: i64 = row.get(10)?;
        let expires_at: Option<i64> = row.get(11)?;
        
        let knowledge_type = KnowledgeType::from_str(&knowledge_type_str)
            .unwrap_or(KnowledgeType::Fact);
        let importance = match importance_str.as_str() {
            "high" => Importance::High,
            "low" => Importance::Low,
            _ => Importance::Medium,
        };
        let validity = match validity_str.as_str() {
            "permanent" => Validity::Permanent,
            "short_term" => Validity::ShortTerm,
            "temporary" => Validity::Temporary,
            _ => Validity::LongTerm,
        };
        
        let mut metadata: KnowledgeMetadata = serde_json::from_str(&metadata_json).unwrap_or_default();
        metadata.brain_domain = brain_domain;
        
        let vector = vector_bytes.map(|bytes| {
            let len = bytes.len() / 4;
            let mut v = vec![1.0f32; len];
            for i in 0..len {
                let bits = i32::from_le_bytes([bytes[i * 4], bytes[i * 4 + 1], bytes[i * 4 + 2], bytes[i * 4 + 3]]);
                v[i] = f32::from_bits(bits as u32);
            }
            v
        });
        
        Ok(KnowledgeEntry {
            id,
            key,
            content,
            knowledge_type,
            importance,
            validity,
            metadata,
            vector,
            created_at: created_at as u64,
            updated_at: updated_at as u64,
            expires_at: expires_at.map(|e| e as u64),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::knowledge::types::KnowledgeType;
    
    #[test]
    fn test_sqlite_backend_basic() {
        let backend = SQLiteKnowledgeBackend::in_memory().unwrap();
        
        let entry = KnowledgeEntry::new(
            "test_key".to_string(),
            "test content".to_string(),
            KnowledgeType::Fact,
        )
        .with_brain_domain("tech".to_string());
        
        backend.store(&entry).unwrap();
        
        let retrieved = backend.retrieve("test_key").unwrap();
        assert!(retrieved.is_some());
        
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.key, "test_key");
        assert_eq!(retrieved.content, "test content");
    }
}
