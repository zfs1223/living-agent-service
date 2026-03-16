use jni::objects::{JClass, JString};
use jni::sys::{jstring, jlong, jint};
use jni::Env;
use jni::strings::JNIString;
use crate::channel::{MpscChannel, ChannelMessage, ChannelConfig};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_createMpscChannel(
    mut env: Env,
    _class: JClass,
    name: JString,
    capacity: jint,
) -> jlong {
    let name_str = match jstring_to_string(&mut env, name) {
        Ok(s) => s,
        Err(_) => {
            let _ = env.throw_new(&JNIString::from("java/lang/IllegalArgumentException"), &JNIString::from("Invalid name string"));
            return 0;
        }
    };
    
    let config = ChannelConfig {
        name: name_str,
        capacity: capacity as usize,
        ..Default::default()
    };
    
    let channel = MpscChannel::<ChannelMessage>::new(config);
    let boxed = Box::new(channel);
    Box::into_raw(boxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_destroyChannel(
    _env: Env,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut MpscChannel<ChannelMessage>);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_sendMessage(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    source: JString,
    _message_type: JString,
    payload: JString,
) -> bool {
    if handle == 0 {
        return false;
    }
    
    let channel = unsafe { &*(handle as *const MpscChannel<ChannelMessage>) };
    let sender = channel.sender();
    
    let source_str = match jstring_to_string(&mut env, source) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let payload_str = match jstring_to_string(&mut env, payload) {
        Ok(s) => s,
        Err(_) => return false,
    };
    let payload_json = serde_json::from_str(&payload_str).unwrap_or(serde_json::Value::String(payload_str));
    
    let message = ChannelMessage::new(&source_str, payload_json);
    
    match sender.try_send(message) {
        Ok(()) => true,
        Err(_) => false,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_receiveMessage(
    mut env: Env,
    _class: JClass,
    handle: jlong,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let channel = unsafe { &*(handle as *const MpscChannel<ChannelMessage>) };
    let receiver = channel.receiver();
    
    match receiver.try_recv() {
        Ok(message) => {
            let json = message.to_json().unwrap_or_default();
            match string_to_jstring(&mut env, &json) {
                Ok(s) => s,
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_getChannelLength(
    _env: Env,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }
    
    0
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_ChannelNative_isChannelEmpty(
    _env: Env,
    _class: JClass,
    handle: jlong,
) -> bool {
    if handle == 0 {
        return true;
    }
    
    true
}
