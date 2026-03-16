use jni::objects::{JClass, JString, JObject, JValue, JByteArray};
use jni::sys::{jstring, jlong, jint, jfloat};
use jni::Env;
use jni::strings::JNIString;
use crate::audio::{AudioProcessor, AudioConfig};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_createProcessor(
    mut env: Env,
    _class: JClass,
    sample_rate: jint,
    channels: jint,
) -> jlong {
    let config = AudioConfig {
        sample_rate: sample_rate as u32,
        channels: channels as u8,
        ..Default::default()
    };
    
    match AudioProcessor::new(config) {
        Ok(processor) => {
            let boxed = Box::new(processor);
            Box::into_raw(boxed) as jlong
        }
        Err(e) => {
            let _ = env.throw_new(&JNIString::from("java/lang/RuntimeException"), &JNIString::from(&format!("Failed to create processor: {}", e)));
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_destroyProcessor(
    _env: Env,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut AudioProcessor);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_processAudio(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    input: JByteArray,
) -> jni::sys::jobject {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    
    match processor.decode_opus(&input_bytes) {
        Ok(frame) => {
            let output_bytes: Vec<u8> = frame.samples
                .iter()
                .flat_map(|s| s.to_le_bytes())
                .collect();
            
            match env.byte_array_from_slice(&output_bytes) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_resample(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    input: JByteArray,
    _target_rate: jint,
) -> jni::sys::jobject {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    
    match processor.decode_opus(&input_bytes) {
        Ok(frame) => {
            let output_bytes: Vec<u8> = frame.samples
                .iter()
                .flat_map(|s| s.to_le_bytes())
                .collect();
            
            match env.byte_array_from_slice(&output_bytes) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_getLevel(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    input: JByteArray,
) -> jfloat {
    if handle == 0 {
        return 0.0;
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return 0.0,
    };
    
    match processor.decode_opus(&input_bytes) {
        Ok(frame) => {
            let is_voice = processor.detect_voice_activity(&frame.samples);
            if is_voice { 1.0 } else { 0.0 }
        }
        Err(_) => 0.0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_setGain(
    _env: Env,
    _class: JClass,
    handle: jlong,
    gain_db: jfloat,
) {
    if handle == 0 {
        return;
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    let _ = processor;
    let _ = gain_db;
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_reset(
    _env: Env,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    
    let processor = unsafe { &*(handle as *const AudioProcessor) };
    processor.reset_stats();
}
