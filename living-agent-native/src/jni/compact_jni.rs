use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::Env;

use crate::compact::{estimate_token_count_text, summarize_messages_json};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_CompactNative_summarizeMessagesJson(
    mut env: Env,
    _class: JClass,
    messages_json: JString,
    max_lines: jint,
) -> jstring {
    let input = match jstring_to_string(&mut env, messages_json) {
        Ok(s) => s,
        Err(e) => return string_to_jstring(&mut env, &format!("<summary>jni-error:{}</summary>", e)).unwrap_or(std::ptr::null_mut()),
    };

    let summary = summarize_messages_json(&input, (max_lines as usize).max(1));
    string_to_jstring(&mut env, &summary).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_CompactNative_estimateTokenCount(
    mut env: Env,
    _class: JClass,
    text: JString,
) -> jint {
    let input = match jstring_to_string(&mut env, text) {
        Ok(s) => s,
        Err(_) => return 0,
    };

    estimate_token_count_text(&input) as jint
}
