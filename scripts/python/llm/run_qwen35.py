#!/usr/bin/env python3
"""
Local LLM Runner - Qwen3.5-2B GGUF
Supports both llama.cpp and direct GGUF inference
"""

import sys
import os
import json
import subprocess
from pathlib import Path

MODEL_PATH = os.environ.get('MODEL_PATH', '/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf')
LLAMA_CLI_PATH = os.environ.get('LLAMA_CLI_PATH', '/app/ai-models/BitNet/compiled/bin/llama-cli')

def run_with_llama_cpp(prompt: str) -> str:
    try:
        result = subprocess.run(
            [LLAMA_CLI_PATH, '-m', MODEL_PATH, '-p', prompt, '-n', '2048', '--temp', '0.7'],
            capture_output=True,
            text=True,
            timeout=120
        )
        if result.returncode == 0:
            return result.stdout.strip()
        else:
            return f"Error: {result.stderr}"
    except subprocess.TimeoutExpired:
        return "Error: LLM inference timeout"
    except FileNotFoundError:
        return run_with_python(prompt)

def run_with_python(prompt: str) -> str:
    try:
        from llama_cpp import Llama
        
        llm = Llama(
            model_path=MODEL_PATH,
            n_ctx=2048,
            n_threads=4,
            verbose=False
        )
        
        response = llm(
            prompt,
            max_tokens=2048,
            temperature=0.7,
            stop=["</s>", "\n\n\n"]
        )
        
        return response['choices'][0]['text'].strip()
    except ImportError:
        return "Error: llama-cpp-python not installed"
    except Exception as e:
        return f"Error: {str(e)}"

def main():
    if len(sys.argv) > 1:
        prompt = ' '.join(sys.argv[1:])
    else:
        prompt = sys.stdin.read().strip()
    
    if not prompt:
        print("Error: No prompt provided", file=sys.stderr)
        sys.exit(1)
    
    if os.path.exists(LLAMA_CLI_PATH):
        response = run_with_llama_cpp(prompt)
    else:
        response = run_with_python(prompt)
    
    print(response)

if __name__ == '__main__':
    main()
