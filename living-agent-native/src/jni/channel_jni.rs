use jni::objects::{JClass, JObject, JString};
use jni::sys::{jstring, jlong, jint};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::EnvUnowned;
use jni::strings::JNIString;
use crate::channel::{MpscChannel, ChannelMessage, ChannelConfig};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_createMpscChannel(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    name: JString<'_>,
    capacity: jint,
) -> jlong {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jlong> {
            let name_str = match jstring_to_string(env, &name) {
                Ok(s) => s,
                Err(_) => {
                    env.throw_new(
                        &JNIString::from("java/lang/IllegalArgumentException"),
                        &JNIString::from("Invalid name string"),
                    )?;
                    return Ok(0);
                }
            };

            let config = ChannelConfig {
                name: name_str,
                capacity: capacity as usize,
                ..Default::default()
            };

            let channel = MpscChannel::<ChannelMessage>::new(config);
            let boxed = Box::new(channel);
            Ok(Box::into_raw(boxed) as jlong)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_destroyChannel(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut MpscChannel<ChannelMessage>);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_sendMessage(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    source: JString<'_>,
    _message_type: JString<'_>,
    payload: JString<'_>,
) -> bool {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }

            let channel = unsafe { &*(handle as *const MpscChannel<ChannelMessage>) };
            let sender = channel.sender();

            let source_str = match jstring_to_string(env, &source) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let payload_str = match jstring_to_string(env, &payload) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let payload_json =
                serde_json::from_str(&payload_str).unwrap_or(serde_json::Value::String(payload_str));

            let message = ChannelMessage::new(&source_str, payload_json);

            match sender.try_send(message) {
                Ok(()) => Ok(true),
                Err(_) => Ok(false),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_receiveMessage(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let channel = unsafe { &*(handle as *const MpscChannel<ChannelMessage>) };
            let receiver = channel.receiver();

            match receiver.try_recv() {
                Ok(message) => {
                    let json = message.to_json().unwrap_or_default();
                    Ok(string_to_jstring(env, &json).unwrap_or(JObject::null()))
                }
                Err(_) => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_getChannelLength(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }

    0
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_ChannelNative_isChannelEmpty(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> bool {
    if handle == 0 {
        return true;
    }

    true
}
