use jni::objects::{JClass, JString};
use jni::sys::{jstring, jlong, jint};
use jni::Env;
use crate::memory::{MemoryBackend, MemoryConfig, MemoryEntry, MemoryCategory, MemoryQuery};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_createBackend(
    mut env: Env,
    _class: JClass,
    db_path: JString,
    max_entries: jint,
) -> jlong {
    let db_path_str = match jstring_to_string(&mut env, db_path) {
        Ok(s) => s,
        Err(_) => return 0,
    };
    
    let config = MemoryConfig {
        db_path: db_path_str,
        max_entries: if max_entries > 0 { max_entries as usize } else { 10000 },
        enable_compression: false,
    };
    
    match MemoryBackend::new(config) {
        Ok(backend) => {
            let boxed = Box::new(backend);
            Box::into_raw(boxed) as jlong
        }
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_destroyBackend(
    _env: Env,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut MemoryBackend);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_store(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    key: JString,
    content: JString,
    category: JString,
    session_id: JString,
) -> bool {
    if handle == 0 {
        return false;
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let content_str = match jstring_to_string(&mut env, content) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let category_str = match jstring_to_string(&mut env, category) {
        Ok(s) => s,
        Err(_) => "custom".to_string(),
    };
    
    let category = MemoryCategory::from_str(&category_str);
    
    let mut entry = MemoryEntry::new(key_str, content_str, category);
    
    if let Ok(sid) = jstring_to_string(&mut env, session_id) {
        if !sid.is_empty() {
            entry.session_id = Some(sid);
        }
    }
    
    match backend.store(&entry) {
        Ok(_) => true,
        Err(_) => false,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_retrieve(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    key: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    
    match backend.get(&key_str) {
        Ok(Some(entry)) => {
            match serde_json::to_string(&entry) {
                Ok(json) => match string_to_jstring(&mut env, &json) {
                    Ok(s) => s,
                    Err(_) => std::ptr::null_mut(),
                },
                Err(_) => std::ptr::null_mut(),
            }
        }
        _ => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_delete(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    key: JString,
) -> bool {
    if handle == 0 {
        return false;
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    let key_str = match jstring_to_string(&mut env, key) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    match backend.forget(&key_str) {
        Ok(_) => true,
        Err(_) => false,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_query(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    query: JString,
    limit: jint,
    session_id: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    let query_str = match jstring_to_string(&mut env, query) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    
    let session_id_opt = jstring_to_string(&mut env, session_id).ok();
    
    let memory_query = MemoryQuery::new(query_str)
        .with_limit(if limit > 0 { limit as usize } else { 10 });
    
    let memory_query = if let Some(sid) = session_id_opt {
        memory_query.with_session(sid)
    } else {
        memory_query
    };
    
    match backend.recall(&memory_query) {
        Ok(entries) => {
            match serde_json::to_string(&entries) {
                Ok(json) => match string_to_jstring(&mut env, &json) {
                    Ok(s) => s,
                    Err(_) => std::ptr::null_mut(),
                },
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_getStats(
    mut env: Env,
    _class: JClass,
    handle: jlong,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    match backend.get_stats() {
        Ok(stats) => {
            match serde_json::to_string(&stats) {
                Ok(json) => match string_to_jstring(&mut env, &json) {
                    Ok(s) => s,
                    Err(_) => std::ptr::null_mut(),
                },
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_MemoryNative_count(
    _env: Env,
    _class: JClass,
    handle: jlong,
) -> jlong {
    if handle == 0 {
        return 0;
    }
    
    let backend = unsafe { &*(handle as *const MemoryBackend) };
    
    match backend.count() {
        Ok(c) => c as jlong,
        Err(_) => 0,
    }
}
