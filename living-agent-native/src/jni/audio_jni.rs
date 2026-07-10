use jni::objects::{JClass, JObject, JByteArray};
use jni::sys::{jlong, jint, jfloat, jboolean};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::EnvUnowned;
use jni::strings::JNIString;
use crate::audio::{AudioProcessor, AudioConfig};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_createProcessor(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    sample_rate: jint,
    channels: jint,
    frame_size: jint,
    enable_vad: jboolean,
) -> jlong {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jlong> {
            let config = AudioConfig {
                sample_rate: sample_rate as u32,
                channels: channels as u8,
                frame_size: frame_size as usize,
                enable_vad: enable_vad,
                ..Default::default()
            };

            match AudioProcessor::new(config) {
                Ok(processor) => {
                    let boxed = Box::new(processor);
                    Ok(Box::into_raw(boxed) as jlong)
                }
                Err(e) => {
                    env.throw_new(
                        &JNIString::from("java/lang/RuntimeException"),
                        &JNIString::from(&format!("Failed to create processor: {}", e)),
                    )?;
                    Ok(0)
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_destroyProcessor(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut AudioProcessor);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_decodeOpus(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    opus_data: JByteArray<'_>,
) -> jni::sys::jobject {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let processor = unsafe { &mut *(handle as *mut AudioProcessor) };

            let input_bytes: Vec<u8> = match env.convert_byte_array(&opus_data) {
                Ok(b) => b,
                Err(_) => return Ok(JObject::null()),
            };

            match processor.decode_opus(&input_bytes) {
                Ok(frame) => {
                    let output_bytes: Vec<u8> = frame.samples
                        .iter()
                        .flat_map(|s| s.to_le_bytes())
                        .collect();

                    match env.byte_array_from_slice(&output_bytes) {
                        Ok(arr) => Ok(unsafe { JObject::from_raw(env, arr.into_raw()) }),
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
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_encodePcm(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    pcm_data: JByteArray<'_>,
) -> jni::sys::jobject {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let processor = unsafe { &mut *(handle as *mut AudioProcessor) };

            let input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
                Ok(b) => b,
                Err(_) => return Ok(JObject::null()),
            };

            let samples: Vec<i16> = input_bytes
                .chunks_exact(2)
                .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
                .collect();

            match processor.encode_pcm(&samples) {
                Ok(opus_bytes) => {
                    match env.byte_array_from_slice(&opus_bytes) {
                        Ok(arr) => Ok(unsafe { JObject::from_raw(env, arr.into_raw()) }),
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
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_detectVoiceActivity(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
    pcm_data: JByteArray<'_>,
) -> jboolean {
    unowned_env
        .with_env(|env| -> jni::errors::Result<jboolean> {
            if handle == 0 {
                return Ok(false);
            }

            let processor = unsafe { &mut *(handle as *mut AudioProcessor) };

            let input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
                Ok(b) => b,
                Err(_) => return Ok(false),
            };

            let samples: Vec<i16> = input_bytes
                .chunks_exact(2)
                .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
                .collect();

            Ok(processor.detect_voice_activity(&samples))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_applyGain(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    pcm_data: JByteArray<'_>,
    gain_db: jfloat,
) -> jni::sys::jobject {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            let mut input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
                Ok(b) => b,
                Err(_) => return Ok(JObject::null()),
            };

            let samples_len = input_bytes.len() / 2;
            let samples: &mut [i16] = unsafe {
                std::slice::from_raw_parts_mut(input_bytes.as_mut_ptr() as *mut i16, samples_len)
            };

            AudioProcessor::apply_gain(samples, gain_db);

            match env.byte_array_from_slice(&input_bytes) {
                Ok(arr) => Ok(unsafe { JObject::from_raw(env, arr.into_raw()) }),
                Err(_) => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_reset(
    _unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }

    let processor = unsafe { &*(handle as *const AudioProcessor) };
    processor.reset_stats();
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_core_nativelib_AudioNative_getStats(
    mut unowned_env: EnvUnowned<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jni::sys::jobject {
    unowned_env
        .with_env(|env| -> jni::errors::Result<JObject<'_>> {
            if handle == 0 {
                return Ok(JObject::null());
            }

            let processor = unsafe { &*(handle as *const AudioProcessor) };
            let stats = processor.get_stats();

            let result = format!(
                "{{\"frames_processed\":{},\"voice_frames\":{},\"silence_frames\":{}}}",
                stats.frames_processed,
                stats.voice_frames,
                stats.silence_frames
            );

            match env.new_string(&result) {
                Ok(s) => Ok(unsafe { JObject::from_raw(env, s.into_raw()) }),
                Err(_) => Ok(JObject::null()),
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
        .into_raw()
}
