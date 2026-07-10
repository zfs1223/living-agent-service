use jni::objects::{JClass, JString};
use jni::sys::{jlong, jint};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::EnvUnowned;
use crate::security::{SecurityValidator, SecurityContext, SecurityLevel};
use crate::jni::jstring_to_string;

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_createValidator(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
) -> jlong {
    let validator = SecurityValidator::new();
    let boxed = Box::new(validator);
    Box::into_raw(boxed) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_destroyValidator(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut SecurityValidator);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_validateCommand(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    command: JString<'_>,
    user_id: JString<'_>,
    session_id: JString<'_>,
    security_level: jint,
) -> bool {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }

            let validator = unsafe { &*(handle as *const SecurityValidator) };

            let command_str = match jstring_to_string(env, &command) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let user_id_str = match jstring_to_string(env, &user_id) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let session_id_str = match jstring_to_string(env, &session_id) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };

            let level = match security_level {
                0 => SecurityLevel::ReadOnly,
                1 => SecurityLevel::Supervised,
                2 => SecurityLevel::Full,
                _ => SecurityLevel::Supervised,
            };

            let context = SecurityContext::new(&user_id_str, &session_id_str).with_level(level);

            let result = validator.validate_command(&command_str, &context);

            Ok(result.is_valid)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_validatePath(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    path: JString<'_>,
    user_id: JString<'_>,
    session_id: JString<'_>,
    security_level: jint,
) -> bool {
    unowned_env
        .with_env(|env| -> jni::errors::Result<bool> {
            if handle == 0 {
                return Ok(false);
            }

            let validator = unsafe { &*(handle as *const SecurityValidator) };

            let path_str = match jstring_to_string(env, &path) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let user_id_str = match jstring_to_string(env, &user_id) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };
            let session_id_str = match jstring_to_string(env, &session_id) {
                Ok(s) => s,
                Err(_) => return Ok(false),
            };

            let level = match security_level {
                0 => SecurityLevel::ReadOnly,
                1 => SecurityLevel::Supervised,
                2 => SecurityLevel::Full,
                _ => SecurityLevel::Supervised,
            };

            let context = SecurityContext::new(&user_id_str, &session_id_str).with_level(level);

            let result = validator.validate_path(&path_str, &context);

            Ok(result.is_valid)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_addAllowedPath(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    path: JString<'_>,
) {
    unowned_env
        .with_env(|env| -> jni::errors::Result<()> {
            if handle == 0 {
                return Ok(());
            }

            let validator = unsafe { &mut *(handle as *mut SecurityValidator) };
            let path_str = match jstring_to_string(env, &path) {
                Ok(s) => s,
                Err(_) => return Ok(()),
            };
            validator.add_allowed_path(&path_str);
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_SecurityNative_addDeniedPath(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    path: JString<'_>,
) {
    unowned_env
        .with_env(|env| -> jni::errors::Result<()> {
            if handle == 0 {
                return Ok(());
            }

            let validator = unsafe { &mut *(handle as *mut SecurityValidator) };
            let path_str = match jstring_to_string(env, &path) {
                Ok(s) => s,
                Err(_) => return Ok(()),
            };
            validator.add_denied_path(&path_str);
            Ok(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
