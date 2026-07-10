// ===========================================
// NativeLibrary JNI 函数（库初始化和版本信息）
//
// jni 0.22: Env 不是 FFI 安全的，不能直接用于接收 JVM 传递的 JNIEnv*。
// 必须使用 EnvUnowned 作为 extern "system" 函数的第一参数，
// 然后通过 with_env() 获取安全的 Env 引用。
// ===========================================

mod audio_jni;
mod channel_jni;
mod security_jni;
mod memory_jni;
mod knowledge_jni;
mod compact_jni;

pub use audio_jni::*;
pub use channel_jni::*;
pub use security_jni::*;
pub use memory_jni::*;
pub use knowledge_jni::*;
pub use compact_jni::*;

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::jstring;
use jni::{Env, EnvUnowned};

/// 初始化 native 库
/// Java: private static native void initialize();
#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_NativeLibrary_initialize(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
) {
}

/// 获取 native 库版本信息
/// Java: private static native String getVersionNative();
#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_NativeLibrary_getVersionNative(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            env.new_string("0.1.0")
                .map(|s| unsafe { JObject::from_raw(env, s.into_raw()) })
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

// ===========================================
// 辅助函数（在 with_env 闭包内部使用，接收 &mut Env<'_>）
// ===========================================

pub fn jstring_to_string(env: &mut Env<'_>, jstr: &JString<'_>) -> Result<String, String> {
    jstr.mutf8_chars(env)
        .map(|s| s.to_string())
        .map_err(|e| format!("Invalid Java string: {}", e))
}

pub fn string_to_jstring<'local>(env: &mut Env<'local>, s: &str) -> Result<JObject<'local>, String> {
    env.new_string(s)
        .map(|jstr| unsafe { JObject::from_raw(env, jstr.into_raw()) })
        .map_err(|e| format!("Failed to create Java string: {}", e))
}

pub fn jbyte_array_to_bytes(env: &mut Env<'_>, arr: &JByteArray<'_>) -> Result<Vec<u8>, String> {
    env.convert_byte_array(arr)
        .map_err(|e| format!("Failed to convert byte array: {}", e))
}
