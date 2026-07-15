#!/usr/bin/env python3
"""
ASR Runner - Sherpa-NCNN SenseVoice
High-performance speech recognition with multi-language support
"""

import sys
import os
import json
import base64
import tempfile
import wave
import struct
from pathlib import Path

MODEL_PATH = os.environ.get('ASR_MODEL_PATH', 
    '/app/ai-models/sherpa-ncnn/sherpa-ncnn-sense-voice-zh-en-ja-ko-yue-2025-09-09')

def check_sherpa_available():
    try:
        import sherpa_ncnn
        return True
    except ImportError:
        return False

def transcribe_with_sherpa(audio_data: bytes) -> str:
    if not check_sherpa_available():
        return transcribe_with_subprocess(audio_data)
    
    import sherpa_ncnn
    
    recognizer = sherpa_ncnn.OnlineRecognizer(
        tokens=os.path.join(MODEL_PATH, 'tokens.txt'),
        encoder_param=os.path.join(MODEL_PATH, 'encoder.ncnn.param'),
        encoder_bin=os.path.join(MODEL_PATH, 'encoder.ncnn.bin'),
        decoder_param=os.path.join(MODEL_PATH, 'decoder.ncnn.param'),
        decoder_bin=os.path.join(MODEL_PATH, 'decoder.ncnn.bin'),
        joiner_param=os.path.join(MODEL_PATH, 'joiner.ncnn.param'),
        joiner_bin=os.path.join(MODEL_PATH, 'joiner.ncnn.bin'),
        num_threads=4,
        provider='cpu'
    )
    
    stream = recognizer.create_stream()
    
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        f.write(audio_data)
        temp_path = f.name
    
    try:
        import wave
        with wave.open(temp_path, 'rb') as wf:
            sample_rate = wf.getframerate()
            num_frames = wf.getnframes()
            audio = wf.readframes(num_frames)
        
        stream.accept_waveform(sample_rate, audio)
        
        while recognizer.is_ready(stream):
            recognizer.decode_stream(stream)
        
        result = recognizer.get_result(stream)
        return result.text.strip()
    finally:
        os.unlink(temp_path)

def transcribe_with_subprocess(audio_data: bytes) -> str:
    """通过子进程调用ASR（降级方案，优先使用transcribe函数）"""
    # 直接调用主transcribe函数，避免subprocess占位
    return transcribe(audio_data)

def main():
    if len(sys.argv) > 1:
        audio_file = sys.argv[1]
        with open(audio_file, 'rb') as f:
            audio_data = f.read()
    else:
        audio_base64 = sys.stdin.read().strip()
        audio_data = base64.b64decode(audio_base64)
    
    if not audio_data:
        print("Error: No audio data provided", file=sys.stderr)
        sys.exit(1)
    
    result = transcribe_with_sherpa(audio_data)
    print(result)

if __name__ == '__main__':
    main()
