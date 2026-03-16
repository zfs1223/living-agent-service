use jni::objects::{JClass, JString, JObject, JValue};
use jni::sys::{jstring, jlong, jint};
use jni::Env;
use jni::strings::JNIString;
use crate::security::{SecurityValidator, SecurityContext, SecurityLevel};
use crate::jni::jstring_to_string;

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_createValidator(
    _env: Env,
    _class: JClass,
) -> jlong {
    let validator = SecurityValidator::new();
    let boxed = Box::new(validator);
    Box::into_raw(boxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_destroyValidator(
    _env: Env,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut SecurityValidator);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_validateCommand(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    command: JString,
    user_id: JString,
    session_id: JString,
    security_level: jint,
) -> bool {
    if handle == 0 {
        return false;
    }
    
    let validator = unsafe { &*(handle as *const SecurityValidator) };
    
    let command_str = match jstring_to_string(&mut env, command) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let user_id_str = match jstring_to_string(&mut env, user_id) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let session_id_str = match jstring_to_string(&mut env, session_id) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let level = match security_level {
        0 => SecurityLevel::ReadOnly,
        1 => SecurityLevel::Supervised,
        2 => SecurityLevel::Full,
        _ => SecurityLevel::Supervised,
    };
    
    let context = SecurityContext::new(&user_id_str, &session_id_str).with_level(level);
    
    let result = validator.validate_command(&command_str, &context);
    
    result.is_valid
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_validatePath(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    path: JString,
    user_id: JString,
    session_id: JString,
    security_level: jint,
) -> bool {
    if handle == 0 {
        return false;
    }
    
    let validator = unsafe { &*(handle as *const SecurityValidator) };
    
    let path_str = match jstring_to_string(&mut env, path) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let user_id_str = match jstring_to_string(&mut env, user_id) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let session_id_str = match jstring_to_string(&mut env, session_id) {
        Ok(s) => s,
        Err(_) => return false,
    };
    
    let level = match security_level {
        0 => SecurityLevel::ReadOnly,
        1 => SecurityLevel::Supervised,
        2 => SecurityLevel::Full,
        _ => SecurityLevel::Supervised,
    };
    
    let context = SecurityContext::new(&user_id_str, &session_id_str).with_level(level);
    
    let result = validator.validate_path(&path_str, &context);
    
    result.is_valid
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_addAllowedPath(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    if handle == 0 {
        return;
    }
    
    let validator = unsafe { &mut *(handle as *mut SecurityValidator) };
    let path_str = match jstring_to_string(&mut env, path) {
        Ok(s) => s,
        Err(_) => return,
    };
    validator.add_allowed_path(&path_str);
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_SecurityNative_addDeniedPath(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    path: JString,
) {
    if handle == 0 {
        return;
    }
    
    let validator = unsafe { &mut *(handle as *mut SecurityValidator) };
    let path_str = match jstring_to_string(&mut env, path) {
        Ok(s) => s,
        Err(_) => return,
    };
    validator.add_denied_path(&path_str);
}
