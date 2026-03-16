use crate::memory::{MemoryStats, MemoryQuery};
use rusqlite::{Connection, params};
use std::sync::Mutex;
use anyhow::Result;
use std::collections::HashMap;

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

pub struct MemoryBackend {
    conn: Mutex<Connection>,
    config: MemoryConfig,
}

impl MemoryBackend {
    pub fn new(config: MemoryConfig) -> Result<Self> {
        let conn = Connection::open(&config.db_path)?;
        
        conn.execute_batch(
            r#"
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY,
                key TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT NOT NULL,
                session_id TEXT,
                timestamp INTEGER NOT NULL,
                score REAL,
                metadata TEXT
            );
            
            CREATE INDEX IF NOT EXISTS idx_memories_key ON memories(key);
            CREATE INDEX IF NOT EXISTS idx_memories_category ON memories(category);
            CREATE INDEX IF NOT EXISTS idx_memories_session ON memories(session_id);
            CREATE INDEX IF NOT EXISTS idx_memories_timestamp ON memories(timestamp);
            "#,
        )?;
        
        Ok(Self {
            conn: Mutex::new(conn),
            config,
        })
    }
    
    pub fn store(&self, entry: &super::MemoryEntry) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        
        conn.execute(
            "INSERT OR REPLACE INTO memories (id, key, content, category, session_id, timestamp, score, metadata)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                entry.id,
                entry.key,
                entry.content,
                entry.category.to_string(),
                entry.session_id,
                entry.timestamp as i64,
                entry.score,
                serde_json::to_string(&entry.metadata)?
            ],
        )?;
        
        Ok(())
    }
    
    fn row_to_entry(row: &rusqlite::Row) -> rusqlite::Result<super::MemoryEntry> {
        Ok(super::MemoryEntry {
            id: row.get(0)?,
            key: row.get(1)?,
            content: row.get(2)?,
            category: super::MemoryCategory::from_str(&row.get::<_, String>(3)?),
            session_id: row.get(4)?,
            timestamp: row.get::<_, i64>(5)? as u64,
            score: row.get(6)?,
            metadata: serde_json::from_str(&row.get::<_, String>(7)?).unwrap_or_default(),
        })
    }
    
    pub fn recall(&self, query: &MemoryQuery) -> Result<Vec<super::MemoryEntry>> {
        let conn = self.conn.lock().unwrap();
        
        let search_pattern = format!("%{}%", query.query);
        
        let mut stmt = if query.session_id.is_some() {
            conn.prepare(
                "SELECT id, key, content, category, session_id, timestamp, score, metadata 
                 FROM memories 
                 WHERE (session_id = ?1 OR session_id IS NULL) 
                 AND (content LIKE ?2 OR key LIKE ?2)
                 ORDER BY timestamp DESC 
                 LIMIT ?3"
            )?
        } else {
            conn.prepare(
                "SELECT id, key, content, category, session_id, timestamp, score, metadata 
                 FROM memories 
                 WHERE content LIKE ?1 OR key LIKE ?1
                 ORDER BY timestamp DESC 
                 LIMIT ?2"
            )?
        };
        
        let entries = if query.session_id.is_some() {
            stmt.query_map(params![query.session_id, search_pattern, query.limit as i32], Self::row_to_entry)?
                .collect::<Result<Vec<_>, _>>()?
        } else {
            stmt.query_map(params![search_pattern, query.limit as i32], Self::row_to_entry)?
                .collect::<Result<Vec<_>, _>>()?
        };
        
        Ok(entries)
    }
    
    pub fn get(&self, key: &str) -> Result<Option<super::MemoryEntry>> {
        let conn = self.conn.lock().unwrap();
        
        let result = conn.query_row(
            "SELECT id, key, content, category, session_id, timestamp, score, metadata 
             FROM memories WHERE key = ?1",
            params![key],
            Self::row_to_entry,
        );
        
        match result {
            Ok(entry) => Ok(Some(entry)),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
            Err(e) => Err(e.into()),
        }
    }
    
    pub fn forget(&self, key: &str) -> Result<bool> {
        let conn = self.conn.lock().unwrap();
        
        let rows = conn.execute("DELETE FROM memories WHERE key = ?1", params![key])?;
        
        Ok(rows > 0)
    }
    
    pub fn count(&self) -> Result<u64> {
        let conn = self.conn.lock().unwrap();
        
        let count: i64 = conn.query_row("SELECT COUNT(*) FROM memories", [], |row| row.get(0))?;
        
        Ok(count as u64)
    }
    
    pub fn get_stats(&self) -> Result<MemoryStats> {
        let conn = self.conn.lock().unwrap();
        
        let total: i64 = conn.query_row("SELECT COUNT(*) FROM memories", [], |row| row.get(0))?;
        
        let mut entries_by_category = HashMap::new();
        let categories: Vec<(String, i64)> = conn
            .prepare("SELECT category, COUNT(*) FROM memories GROUP BY category")?
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
            .filter_map(|r| r.ok())
            .collect();
        
        for (cat, count) in categories {
            entries_by_category.insert(cat, count as u64);
        }
        
        Ok(MemoryStats {
            total_entries: total as u64,
            entries_by_category,
            total_bytes: 0,
            last_access: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0),
        })
    }
}
