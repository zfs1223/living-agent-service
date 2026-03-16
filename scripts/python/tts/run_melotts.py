#!/usr/bin/env python3
"""
TTS Runner - MeloTTS
Multi-language text-to-speech synthesis
"""

import sys
import os
import json
import base64
import tempfile
from pathlib import Path

MODEL_PATH = os.environ.get('TTS_MODEL_PATH', '/app/ai-models/MeloTTS')

def check_melotts_available():
    try:
        from melo.api import TTS
        return True
    except ImportError:
        return False

def synthesize_with_melotts(text: str, voice: str = 'zh', speed: float = 1.0) -> bytes:
    if not check_melotts_available():
        return synthesize_with_subprocess(text, voice, speed)
    
    from melo.api import TTS
    
    language = 'zh' if voice.startswith('zh') or voice.startswith('zf') else 'en'
    
    tts = TTS(language=language, model_path=MODEL_PATH)
    
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        temp_path = f.name
    
    try:
        tts.tts_to_file(text, temp_path, speed=speed)
        
        with open(temp_path, 'rb') as f:
            audio_data = f.read()
        
        return audio_data
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)

def synthesize_with_subprocess(text: str, voice: str, speed: float) -> bytes:
    import subprocess
    
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        temp_path = f.name
    
    try:
        result = subprocess.run(
            ['python', '-m', 'melo.main', text, temp_path, '--voice', voice, '--speed', str(speed)],
            capture_output=True,
            cwd=MODEL_PATH,
            timeout=60
        )
        
        if result.returncode == 0 and os.path.exists(temp_path):
            with open(temp_path, 'rb') as f:
                return f.read()
        else:
            raise RuntimeError(f"TTS failed: {result.stderr.decode()}")
    finally:
        if os.path.exists(temp_path):
            os.unlink(temp_path)

def main():
    if len(sys.argv) > 1:
        text = sys.argv[1]
        voice = sys.argv[2] if len(sys.argv) > 2 else 'zh'
        speed = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0
    else:
        lines = sys.stdin.read().strip().split('\n')
        text = lines[0] if len(lines) > 0 else ''
        voice = lines[1] if len(lines) > 1 else 'zh'
        speed = float(lines[2]) if len(lines) > 2 else 1.0
    
    if not text:
        print("Error: No text provided", file=sys.stderr)
        sys.exit(1)
    
    audio_data = synthesize_with_melotts(text, voice, speed)
    
    sys.stdout.buffer.write(audio_data)

if __name__ == '__main__':
    main()
