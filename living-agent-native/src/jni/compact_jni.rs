use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jstring};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::EnvUnowned;

use crate::compact::{estimate_token_count_text, summarize_messages_json};
use crate::jni::{jstring_to_string, string_to_jstring};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_CompactNative_summarizeMessagesJson(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    messages_json: JString<'_>,
    max_lines: jint,
) -> jstring {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            let input = match jstring_to_string(env, &messages_json) {
                Ok(s) => s,
                Err(e) => {
                    return Ok(string_to_jstring(env, &format!("<summary>jni-error:{}</summary>", e))
                        .unwrap_or(JObject::null()))
                }
            };

            let summary = summarize_messages_json(&input, (max_lines as usize).max(1));
            Ok(string_to_jstring(env, &summary).unwrap_or(JObject::null()))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_CompactNative_estimateTokenCount(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    text: JString<'_>,
) -> jint {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jint> {
            let input = match jstring_to_string(env, &text) {
                Ok(s) => s,
                Err(_) => return Ok(0),
            };

            Ok(estimate_token_count_text(&input) as jint)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
