use crate::knowledge::types::KnowledgeEntry;
use lru::LruCache;
use parking_lot::RwLock;
use std::collections::HashMap;
use std::num::NonZeroUsize;
use std::sync::Arc;
use std::time::{Duration, Instant};

pub struct CacheEntry {
    pub entry: KnowledgeEntry,
    pub cached_at: Instant,
    pub access_count: u32,
    pub ttl: Duration,
}

impl CacheEntry {
    pub fn is_expired(&self) -> bool {
        self.cached_at.elapsed() > self.ttl
    }
    
    pub fn touch(&mut self) {
        self.access_count += 1;
    }
}

pub struct KnowledgeCache {
    lru: Arc<RwLock<LruCache<String, CacheEntry>>>,
    lookup: Arc<RwLock<HashMap<String, String>>>,
    default_ttl: Duration,
    max_size: usize,
    hits: Arc<RwLock<u64>>,
    misses: Arc<RwLock<u64>>,
}

impl KnowledgeCache {
    pub fn new(max_size: usize, default_ttl: Duration) -> Self {
        Self {
            lru: Arc::new(RwLock::new(LruCache::new(NonZeroUsize::new(max_size).unwrap()))),
            lookup: Arc::new(RwLock::new(HashMap::new())),
            default_ttl,
            max_size,
            hits: Arc::new(RwLock::new(0)),
            misses: Arc::new(RwLock::new(0)),
        }
    }
    
    pub fn with_capacity(capacity: usize) -> Self {
        Self::new(capacity, Duration::from_secs(3600))
    }
    
    pub fn get(&self, key: &str) -> Option<KnowledgeEntry> {
        let mut lru = self.lru.write();
        
        if let Some(cache_entry) = lru.get_mut(key) {
            if cache_entry.is_expired() {
                lru.pop(key);
                self.lookup.write().remove(key);
                *self.misses.write() += 1;
                return None;
            }
            
            cache_entry.touch();
            *self.hits.write() += 1;
            return Some(cache_entry.entry.clone());
        }
        
        *self.misses.write() += 1;
        None
    }
    
    pub fn put(&self, entry: KnowledgeEntry) {
        let cache_entry = CacheEntry {
            entry: entry.clone(),
            cached_at: Instant::now(),
            access_count: 0,
            ttl: self.default_ttl,
        };
        
        let mut lru = self.lru.write();
        
        if let Some(old) = lru.put(entry.key.clone(), cache_entry) {
            self.lookup.write().remove(&old.entry.key);
        }
        
        self.lookup.write().insert(entry.id.clone(), entry.key.clone());
    }
    
    pub fn put_with_ttl(&self, entry: KnowledgeEntry, ttl: Duration) {
        let cache_entry = CacheEntry {
            entry: entry.clone(),
            cached_at: Instant::now(),
            access_count: 0,
            ttl,
        };
        
        let mut lru = self.lru.write();
        
        if let Some(old) = lru.put(entry.key.clone(), cache_entry) {
            self.lookup.write().remove(&old.entry.key);
        }
        
        self.lookup.write().insert(entry.id.clone(), entry.key.clone());
    }
    
    pub fn remove(&self, key: &str) -> bool {
        let mut lru = self.lru.write();
        
        if let Some(cache_entry) = lru.pop(key) {
            self.lookup.write().remove(&cache_entry.entry.id);
            true
        } else {
            false
        }
    }
    
    pub fn contains(&self, key: &str) -> bool {
        let lru = self.lru.read();
        
        if let Some(cache_entry) = lru.peek(key) {
            !cache_entry.is_expired()
        } else {
            false
        }
    }
    
    pub fn clear(&self) {
        let mut lru = self.lru.write();
        lru.clear();
        self.lookup.write().clear();
    }
    
    pub fn len(&self) -> usize {
        self.lru.read().len()
    }
    
    pub fn is_empty(&self) -> bool {
        self.lru.read().is_empty()
    }
    
    pub fn cleanup_expired(&self) -> usize {
        let mut lru = self.lru.write();
        let mut lookup = self.lookup.write();
        
        let expired_keys: Vec<String> = lru
            .iter()
            .filter(|(_, v)| v.is_expired())
            .map(|(k, _)| k.clone())
            .collect();
        
        let count = expired_keys.len();
        
        for key in expired_keys {
            if let Some(cache_entry) = lru.pop(&key) {
                lookup.remove(&cache_entry.entry.id);
            }
        }
        
        count
    }
    
    pub fn stats(&self) -> CacheStats {
        let hits = *self.hits.read();
        let misses = *self.misses.read();
        let total = hits + misses;
        
        CacheStats {
            size: self.len(),
            max_size: self.max_size,
            hits,
            misses,
            hit_rate: if total > 0 { hits as f64 / total as f64 } else { 0.0 },
        }
    }
    
    pub fn get_by_id(&self, id: &str) -> Option<KnowledgeEntry> {
        let key = {
            let lookup = self.lookup.read();
            lookup.get(id).cloned()
        };
        
        if let Some(key) = key {
            self.get(&key)
        } else {
            None
        }
    }
    
    pub fn get_multi(&self, keys: &[&str]) -> Vec<Option<KnowledgeEntry>> {
        keys.iter().map(|k| self.get(k)).collect()
    }
    
    pub fn put_multi(&self, entries: Vec<KnowledgeEntry>) {
        for entry in entries {
            self.put(entry);
        }
    }
    
    pub fn invalidate_pattern(&self, pattern: &str) -> usize {
        let mut lru = self.lru.write();
        let mut lookup = self.lookup.write();
        
        let matching_keys: Vec<String> = lru
            .iter()
            .filter(|(k, _)| k.contains(pattern))
            .map(|(k, _)| k.clone())
            .collect();
        
        let count = matching_keys.len();
        
        for key in matching_keys {
            if let Some(cache_entry) = lru.pop(&key) {
                lookup.remove(&cache_entry.entry.id);
            }
        }
        
        count
    }
    
    pub fn update_ttl(&self, key: &str, new_ttl: Duration) -> bool {
        let mut lru = self.lru.write();
        
        if let Some(cache_entry) = lru.get_mut(key) {
            cache_entry.ttl = new_ttl;
            true
        } else {
            false
        }
    }
}

#[derive(Debug, Clone)]
pub struct CacheStats {
    pub size: usize,
    pub max_size: usize,
    pub hits: u64,
    pub misses: u64,
    pub hit_rate: f64,
}

impl Default for KnowledgeCache {
    fn default() -> Self {
        Self::with_capacity(10000)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::knowledge::types::KnowledgeType;
    
    #[test]
    fn test_cache_basic() {
        let cache = KnowledgeCache::with_capacity(100);
        
        let entry = KnowledgeEntry::new(
            "test_key".to_string(),
            "test content".to_string(),
            KnowledgeType::Fact,
        );
        
        cache.put(entry.clone());
        
        let retrieved = cache.get("test_key");
        assert!(retrieved.is_some());
        assert_eq!(retrieved.unwrap().key, "test_key");
    }
    
    #[test]
    fn test_cache_miss() {
        let cache = KnowledgeCache::with_capacity(100);
        
        let result = cache.get("nonexistent");
        assert!(result.is_none());
        
        let stats = cache.stats();
        assert_eq!(stats.misses, 1);
    }
    
    #[test]
    fn test_cache_expiration() {
        let cache = KnowledgeCache::new(100, Duration::from_millis(10));
        
        let entry = KnowledgeEntry::new(
            "test_key".to_string(),
            "test content".to_string(),
            KnowledgeType::Fact,
        );
        
        cache.put(entry);
        
        std::thread::sleep(Duration::from_millis(20));
        
        let result = cache.get("test_key");
        assert!(result.is_none());
    }
    
    #[test]
    fn test_cache_lru_eviction() {
        let cache = KnowledgeCache::with_capacity(2);
        
        let entry1 = KnowledgeEntry::new("key1".to_string(), "content1".to_string(), KnowledgeType::Fact);
        let entry2 = KnowledgeEntry::new("key2".to_string(), "content2".to_string(), KnowledgeType::Fact);
        let entry3 = KnowledgeEntry::new("key3".to_string(), "content3".to_string(), KnowledgeType::Fact);
        
        cache.put(entry1);
        cache.put(entry2);
        cache.put(entry3);
        
        assert_eq!(cache.len(), 2);
        assert!(cache.get("key1").is_none());
        assert!(cache.get("key2").is_some());
        assert!(cache.get("key3").is_some());
    }
    
    #[test]
    fn test_cache_stats() {
        let cache = KnowledgeCache::with_capacity(100);
        
        let entry = KnowledgeEntry::new(
            "test_key".to_string(),
            "test content".to_string(),
            KnowledgeType::Fact,
        );
        
        cache.put(entry);
        
        cache.get("test_key");
        cache.get("test_key");
        cache.get("nonexistent");
        
        let stats = cache.stats();
        assert_eq!(stats.hits, 2);
        assert_eq!(stats.misses, 1);
        assert!((stats.hit_rate - 0.666).abs() < 0.01);
    }
    
    #[test]
    fn test_cache_cleanup() {
        let cache = KnowledgeCache::new(100, Duration::from_millis(10));
        
        let entry1 = KnowledgeEntry::new("key1".to_string(), "content1".to_string(), KnowledgeType::Fact);
        let entry2 = KnowledgeEntry::new("key2".to_string(), "content2".to_string(), KnowledgeType::Fact);
        
        cache.put(entry1);
        
        std::thread::sleep(Duration::from_millis(20));
        
        cache.put(entry2);
        
        let cleaned = cache.cleanup_expired();
        assert_eq!(cleaned, 1);
        assert_eq!(cache.len(), 1);
    }
}
