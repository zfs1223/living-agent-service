#![allow(improper_ctypes_definitions)]

mod audio_jni;
mod channel_jni;
mod security_jni;
mod memory_jni;
mod knowledge_jni;

pub use audio_jni::*;
pub use channel_jni::*;
pub use security_jni::*;
pub use memory_jni::*;
pub use knowledge_jni::*;

use jni::objects::{JByteArray, JString};
use jni::sys::jstring;
use jni::Env;

pub fn jstring_to_string(env: &mut Env, jstr: JString) -> Result<String, String> {
    jstr.mutf8_chars(env)
        .map(|s| s.to_string())
        .map_err(|e| format!("Invalid Java string: {}", e))
}

pub fn string_to_jstring(env: &mut Env, s: &str) -> Result<jstring, String> {
    env.new_string(s)
        .map(|jstr| jstr.into_raw())
        .map_err(|e| format!("Failed to create Java string: {}", e))
}

pub fn jbyte_array_to_bytes(env: &mut Env, arr: JByteArray) -> Result<Vec<u8>, String> {
    env.convert_byte_array(&arr)
        .map_err(|e| format!("Failed to convert byte array: {}", e))
}
