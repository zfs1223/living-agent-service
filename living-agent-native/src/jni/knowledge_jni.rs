use crate::knowledge::{
    cache::KnowledgeCache,
    similarity::cosine_similarity,
    sqlite_backend::SQLiteKnowledgeBackend,
    types::{Importance, KnowledgeEntry, KnowledgeType},
    vector_store::VectorStore,
};
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::jlong;
use jni::Env;
use jni::strings::JNIString;
use jni::jni_sig;
use std::sync::Mutex;
use std::time::Duration;

use crate::jni::{jstring_to_string, jbyte_array_to_bytes};

lazy_static::lazy_static! {
    static ref KNOWLEDGE_BACKEND: Mutex<Option<SQLiteKnowledgeBackend>> = Mutex::new(None);
    static ref VECTOR_STORE: Mutex<Option<VectorStore>> = Mutex::new(None);
    static ref KNOWLEDGE_CACHE: Mutex<Option<KnowledgeCache>> = Mutex::new(None);
}

fn throw_exception(env: &mut Env, class_name: &str, message: &str) {
    let _ = env.throw_new(&JNIString::from(class_name), &JNIString::from(message));
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_init(
    mut env: Env,
    _class: JClass,
    db_path: JString,
    vector_dimension: i32,
    cache_size: i32,
) {
    let path = match jstring_to_string(&mut env, db_path) {
        Ok(s) => s,
        Err(_) => {
            throw_exception(&mut env, "java/lang/IllegalArgumentException", "Invalid db_path");
            return;
        }
    };
    
    let backend = match SQLiteKnowledgeBackend::new(&path) {
        Ok(b) => b,
        Err(e) => {
            let msg = format!("Failed to init backend: {}", e);
            throw_exception(&mut env, "java/lang/RuntimeException", &msg);
            return;
        }
    };
    
    let vector_store = VectorStore::new(vector_dimension as usize);
    let cache = KnowledgeCache::new(cache_size as usize, Duration::from_secs(3600));
    
    let mut backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    *backend_guard = Some(backend);
    
    let mut vs_guard = VECTOR_STORE.lock().unwrap();
    *vs_guard = Some(vector_store);
    
    let mut cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
    *cache_guard = Some(cache);
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_store(
    mut env: Env,
    _class: JClass,
    key: JString,
    content: JString,
    knowledge_type: JString,
    brain_domain: JString,
    importance: JString,
    validity: JString,
    vector: JByteArray,
) -> bool {
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let content_str = match jstring_to_string(&mut env, content) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let type_str = match jstring_to_string(&mut env, knowledge_type) {
        Ok(s) => s,
        Err(_) => "fact".to_string(),
    };
    
    let k_type = KnowledgeType::from_str(&type_str).unwrap_or(KnowledgeType::Fact);
    
    let mut entry = KnowledgeEntry::new(key_str.clone(), content_str, k_type);
    
    if let Ok(domain) = jstring_to_string(&mut env, brain_domain) {
        if !domain.is_empty() {
            entry = entry.with_brain_domain(domain);
        }
    }
    
    if let Ok(imp) = jstring_to_string(&mut env, importance) {
        entry = entry.with_importance(match imp.as_str() {
            "high" => Importance::High,
            "low" => Importance::Low,
            _ => Importance::Medium,
        });
    }
    
    if let Ok(val) = jstring_to_string(&mut env, validity) {
        entry = entry.with_validated(match val.as_str() {
            "permanent" => true,
            _ => false,
        });
    }
    
    let vector_data: Vec<f32> = if !vector.is_null() {
        let bytes = match jbyte_array_to_bytes(&mut env, vector) {
            Ok(b) => b,
            Err(_) => Vec::new(),
        };
        bytes.chunks(4)
            .filter_map(|chunk| {
                if chunk.len() == 4 {
                    Some(f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]))
                } else {
                    None
                }
            })
            .collect()
    } else {
        Vec::new()
    };
    
    if !vector_data.is_empty() {
        entry = entry.with_vector(vector_data.clone());
        
        let mut metadata = std::collections::HashMap::new();
        metadata.insert("content".to_string(), entry.content.clone());
        metadata.insert("type".to_string(), entry.knowledge_type.as_str().to_string());
        
        let mut vs_guard = VECTOR_STORE.lock().unwrap();
        if let Some(vs) = vs_guard.as_mut() {
            let _ = vs.store(entry.key.clone(), vector_data, metadata);
        }
    }
    
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.store(&entry) {
            Ok(_) => {
                drop(backend_guard);
                let mut cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
                if let Some(cache) = cache_guard.as_mut() {
                    cache.put(entry);
                }
                true
            }
            Err(_) => false,
        }
    } else {
        false
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_retrieve(
    mut env: Env,
    _class: JClass,
    key: JString,
) -> jni::sys::jobject {
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return JObject::default().into_raw(),
    };
    
    {
        let cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
        if let Some(cache) = cache_guard.as_ref() {
            if let Some(entry) = cache.get(&key_str) {
                return entry_to_jobject(&mut env, &entry);
            }
        }
    }
    
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.retrieve(&key_str) {
            Ok(Some(entry)) => {
                drop(backend_guard);
                let mut cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
                if let Some(cache) = cache_guard.as_mut() {
                    cache.put(entry.clone());
                }
                entry_to_jobject(&mut env, &entry)
            }
            _ => JObject::default().into_raw(),
        }
    } else {
        JObject::default().into_raw()
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_search(
    mut env: Env,
    _class: JClass,
    query: JString,
    limit: i32,
) -> jni::sys::jobject {
    let query_str = match jstring_to_string(&mut env, query) {
        Ok(s) => s,
        Err(_) => return JObject::default().into_raw(),
    };
    
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.search(&query_str, limit as usize) {
            Ok(entries) => {
                let list = create_java_list(&mut env);
                
                for entry in entries {
                    let jentry = entry_to_jobject(&mut env, &entry);
                    let jentry_obj = unsafe { JObject::from_raw(&env, jentry) };
                    let _ = env.call_method(
                        &list,
                        &JNIString::from("add"),
                        jni_sig!("(Ljava/lang/Object;)Z"),
                        &[JValue::Object(&jentry_obj)],
                    );
                }
                
                list.into_raw()
            }
            Err(_) => JObject::default().into_raw(),
        }
    } else {
        JObject::default().into_raw()
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_vectorSearch(
    mut env: Env,
    _class: JClass,
    query_vector: JByteArray,
    top_k: i32,
    threshold: f32,
) -> jni::sys::jobject {
    let bytes = match jbyte_array_to_bytes(&mut env, query_vector) {
        Ok(b) => b,
        Err(_) => return JObject::default().into_raw(),
    };
    
    let query: Vec<f32> = bytes.chunks(4)
        .filter_map(|chunk| {
            if chunk.len() == 4 {
                Some(f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]))
            } else {
                None
            }
        })
        .collect();
    
    let vs_guard = VECTOR_STORE.lock().unwrap();
    if let Some(vs) = vs_guard.as_ref() {
        let results = vs.search(&query, top_k as usize, threshold);
        
        let list = create_java_list(&mut env);
        
        for result in results {
            let jentry = entry_to_jobject(&mut env, &result.entry);
            let jentry_obj = unsafe { JObject::from_raw(&env, jentry) };
            let _ = env.call_method(
                &list,
                &JNIString::from("add"),
                jni_sig!("(Ljava/lang/Object;)Z"),
                &[JValue::Object(&jentry_obj)],
            );
        }
        
        list.into_raw()
    } else {
        JObject::default().into_raw()
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_cosineSimilarity(
    mut env: Env,
    _class: JClass,
    vec1: JByteArray,
    vec2: JByteArray,
) -> f32 {
    let bytes1 = match jbyte_array_to_bytes(&mut env, vec1) {
        Ok(b) => b,
        Err(_) => return 0.0,
    };
    
    let bytes2 = match jbyte_array_to_bytes(&mut env, vec2) {
        Ok(b) => b,
        Err(_) => return 0.0,
    };
    
    let v1: Vec<f32> = bytes1.chunks(4)
        .filter_map(|chunk| {
            if chunk.len() == 4 {
                Some(f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]))
            } else {
                None
            }
        })
        .collect();
    
    let v2: Vec<f32> = bytes2.chunks(4)
        .filter_map(|chunk| {
            if chunk.len() == 4 {
                Some(f32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]))
            } else {
                None
            }
        })
        .collect();
    
    cosine_similarity(&v1, &v2)
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_delete(
    mut env: Env,
    _class: JClass,
    key: JString,
) -> bool {
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.delete(&key_str) {
            Ok(true) => {
                drop(backend_guard);
                let mut cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
                if let Some(cache) = cache_guard.as_mut() {
                    cache.remove(&key_str);
                }
                true
            }
            _ => false,
        }
    } else {
        false
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_count(
    _env: Env,
    _class: JClass,
) -> jlong {
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.count() {
            Ok(c) => c as jlong,
            Err(_) => 0,
        }
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_cleanupExpired(
    _env: Env,
    _class: JClass,
) -> jlong {
    let backend_guard = KNOWLEDGE_BACKEND.lock().unwrap();
    if let Some(backend) = backend_guard.as_ref() {
        match backend.cleanup_expired() {
            Ok(c) => c as jlong,
            Err(_) => 0,
        }
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_knowledge_NativeKnowledge_cacheStats(
    mut env: Env,
    _class: JClass,
) -> jni::sys::jobject {
    let cache_guard = KNOWLEDGE_CACHE.lock().unwrap();
    if let Some(cache) = cache_guard.as_ref() {
        let stats = cache.stats();
        
        let map = create_java_map(&mut env);
        
        let _ = put_map_long(&mut env, &map, "hits", stats.hits as i64);
        let _ = put_map_long(&mut env, &map, "misses", stats.misses as i64);
        let _ = put_map_double(&mut env, &map, "hit_rate", stats.hit_rate as f64);
        
        map.into_raw()
    } else {
        JObject::default().into_raw()
    }
}

fn create_java_list<'a>(env: &mut Env<'a>) -> JObject<'a> {
    let class = env.find_class(&JNIString::from("java/util/ArrayList")).unwrap();
    env.new_object(&class, jni_sig!("()V"), &[]).unwrap()
}

fn create_java_map<'a>(env: &mut Env<'a>) -> JObject<'a> {
    let class = env.find_class(&JNIString::from("java/util/HashMap")).unwrap();
    env.new_object(&class, jni_sig!("()V"), &[]).unwrap()
}

fn put_map_long(env: &mut Env, map: &JObject, key: &str, value: i64) -> Result<(), String> {
    let jkey = env.new_string(key).map_err(|e| e.to_string())?;
    let long_class = env.find_class(&JNIString::from("java/lang/Long")).map_err(|e| e.to_string())?;
    let jvalue = env.new_object(&long_class, jni_sig!("(J)V"), &[JValue::Long(value)]).map_err(|e| e.to_string())?;
    let _ = env.call_method(map, &JNIString::from("put"), jni_sig!("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), &[JValue::Object(&jkey), JValue::Object(&jvalue)]);
    Ok(())
}

fn put_map_double(env: &mut Env, map: &JObject, key: &str, value: f64) -> Result<(), String> {
    let jkey = env.new_string(key).map_err(|e| e.to_string())?;
    let double_class = env.find_class(&JNIString::from("java/lang/Double")).map_err(|e| e.to_string())?;
    let jvalue = env.new_object(&double_class, jni_sig!("(D)V"), &[JValue::Double(value)]).map_err(|e| e.to_string())?;
    let _ = env.call_method(map, &JNIString::from("put"), jni_sig!("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), &[JValue::Object(&jkey), JValue::Object(&jvalue)]);
    Ok(())
}

fn entry_to_jobject(env: &mut Env, entry: &KnowledgeEntry) -> jni::sys::jobject {
    let map = create_java_map(env);
    
    let _ = put_map_string(env, &map, "id", &entry.id);
    let _ = put_map_string(env, &map, "key", &entry.key);
    let _ = put_map_string(env, &map, "content", &entry.content);
    let _ = put_map_string(env, &map, "type", entry.knowledge_type.as_str());
    let _ = put_map_string(env, &map, "importance", entry.importance.as_str());
    let _ = put_map_double(env, &map, "relevance_score", entry.relevance_score() as f64);
    
    map.into_raw()
}

fn put_map_string(env: &mut Env, map: &JObject, key: &str, value: &str) -> Result<(), String> {
    let jkey = env.new_string(key).map_err(|e| e.to_string())?;
    let jvalue = env.new_string(value).map_err(|e| e.to_string())?;
    let _ = env.call_method(map, &JNIString::from("put"), jni_sig!("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), &[JValue::Object(&jkey), JValue::Object(&jvalue)]);
    Ok(())
}
