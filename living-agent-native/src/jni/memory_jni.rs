use jni::objects::{JClass, JObject, JString};
use jni::sys::{jstring, jlong, jint};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::EnvUnowned;
use crate::memory::{MemoryBackend, MemoryConfig, MemoryEntry, MemoryCategory, MemoryQuery};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_createBackend(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    db_path: JString<'_>,
    max_entries: jint,
) -> jlong {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jlong> {
            let db_path_str = match jstring_to_string(env, &db_path) {
                Ok(s) => s,
                Err(_) => return Ok(0),
            };

            let config = MemoryConfig {
                db_path: db_path_str,
                max_entries: if max_entries > 0 { max_entries as usize } else { 10000 },
                enable_compression: false,
            };

            match MemoryBackend::new(config) {
                Ok(backend) => {
                    let boxed = Box::new(backend);
                    Ok(Box::into_raw(boxed) as jlong)
                }
                Err(_) => Ok(0),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_destroyBackend(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut MemoryBackend);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_store(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    key: JString<'_>,
    content: JString<'_>,
    category: JString<'_>,
    session_id: JString<'_>,
) -> bool {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }

            let backend = unsafe { &*(handle as *const MemoryBackend) };

            let key_str = match jstring_to_string(env, &key) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };

            let content_str = match jstring_to_string(env, &content) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };

            let category_str = match jstring_to_string(env, &category) {
                Ok(s) => s,
                Err(_) => "custom".to_string(),
            };

            let category = MemoryCategory::from_str(&category_str);

            let mut entry = MemoryEntry::new(key_str, content_str, category);

            if let Ok(sid) = jstring_to_string(env, &session_id) {
                if !sid.is_empty() {
                    entry.session_id = Some(sid);
                }
            }

            match backend.store(&entry) {
                Ok(_) => Ok(true),
                Err(_) => Ok(false),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_retrieve(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    key: JString<'_>,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let backend = unsafe { &*(handle as *const MemoryBackend) };

            let key_str = match jstring_to_string(env, &key) {
                Ok(s) => s,
                Err(_) => return Ok(JObject::null()),
            };

            match backend.get(&key_str) {
                Ok(Some(entry)) => {
                    match serde_json::to_string(&entry) {
                        Ok(json) => Ok(string_to_jstring(env, &json).unwrap_or(JObject::null())),
                        Err(_) => Ok(JObject::null()),
                    }
                }
                _ => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_delete(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    key: JString<'_>,
) -> bool {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }

            let backend = unsafe { &*(handle as *const MemoryBackend) };

            let key_str = match jstring_to_string(env, &key) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };

            match backend.forget(&key_str) {
                Ok(_) => Ok(true),
                Err(_) => Ok(false),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_query(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    query: JString<'_>,
    limit: jint,
    session_id: JString<'_>,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let backend = unsafe { &*(handle as *const MemoryBackend) };

            let query_str = match jstring_to_string(env, &query) {
                Ok(s) => s,
                Err(_) => return Ok(JObject::null()),
            };

            let session_id_opt = jstring_to_string(env, &session_id).ok();

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
                        Ok(json) => Ok(string_to_jstring(env, &json).unwrap_or(JObject::null())),
                        Err(_) => Ok(JObject::null()),
                    }
                }
                Err(_) => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_getStats(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let backend = unsafe { &*(handle as *const MemoryBackend) };

            match backend.get_stats() {
                Ok(stats) => {
                    match serde_json::to_string(&stats) {
                        Ok(json) => Ok(string_to_jstring(env, &json).unwrap_or(JObject::null())),
                        Err(_) => Ok(JObject::null()),
                    }
                }
                Err(_) => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_MemoryNative_count(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
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
