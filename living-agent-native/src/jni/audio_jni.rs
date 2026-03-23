use jni::objects::{JClass, JByteArray};
use jni::sys::{jlong, jint, jfloat, jboolean};
use jni::Env;
use jni::strings::JNIString;
use crate::audio::{AudioProcessor, AudioConfig};

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_createProcessor(
    mut env: Env,
    _class: JClass,
    sample_rate: jint,
    channels: jint,
    frame_size: jint,
    enable_vad: jboolean,
) -> jlong {
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
pub extern "system" fn Java_com_livingagent_native_AudioNative_decodeOpus(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    opus_data: JByteArray,
) -> jni::sys::jobject {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&opus_data) {
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
pub extern "system" fn Java_com_livingagent_native_AudioNative_encodePcm(
    mut env: Env,
    _class: JClass,
    handle: jlong,
    pcm_data: JByteArray,
) -> jni::sys::jobject {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    
    let samples: Vec<i16> = input_bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect();
    
    match processor.encode_pcm(&samples) {
        Ok(opus_bytes) => {
            match env.byte_array_from_slice(&opus_bytes) {
                Ok(arr) => arr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_detectVoiceActivity(
    env: Env,
    _class: JClass,
    handle: jlong,
    pcm_data: JByteArray,
) -> jboolean {
    if handle == 0 {
        return false;
    }
    
    let processor = unsafe { &mut *(handle as *mut AudioProcessor) };
    
    let input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
        Ok(b) => b,
        Err(_) => return false,
    };
    
    let samples: Vec<i16> = input_bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect();
    
    processor.detect_voice_activity(&samples)
}

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_applyGain(
    mut env: Env,
    _class: JClass,
    pcm_data: JByteArray,
    gain_db: jfloat,
) -> jni::sys::jobject {
    let mut input_bytes: Vec<u8> = match env.convert_byte_array(&pcm_data) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    
    let samples_len = input_bytes.len() / 2;
    let samples: &mut [i16] = unsafe {
        std::slice::from_raw_parts_mut(input_bytes.as_mut_ptr() as *mut i16, samples_len)
    };
    
    AudioProcessor::apply_gain(samples, gain_db);
    
    match env.byte_array_from_slice(&input_bytes) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
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

#[no_mangle]
pub extern "system" fn Java_com_livingagent_native_AudioNative_getStats(
    mut env: Env,
    _class: JClass,
    handle: jlong,
) -> jni::sys::jobject {
    if handle == 0 {
        return std::ptr::null_mut();
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
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
