#!/usr/bin/env python3
"""
测试 litellm 如何处理自定义 base_url
"""

import asyncio
import httpx
import json


async def test_litellm_custom_base_url():
    print("=" * 60)
    print("测试 litellm 自定义 base_url")
    print("=" * 60)

    # 测试直接调用 litellm
    try:
        from litellm import completion
        import litellm
        litellm.drop_params = True

        print("\n[测试1] 使用 openai/ 前缀")
        try:
            response = completion(
                model="openai/registry.ollama.ai/library/qwen2.5/3b",
                messages=[{"role": "user", "content": "你好"}],
                api_key="sk-no-key-required",
                base_url="http://localhost:11435/v1",
                max_tokens=20
            )
            print(f"[OK] 回复: {response.choices[0].message.content[:50]}...")
        except Exception as e:
            print(f"[ERROR] {e}")

        print("\n[测试2] 不使用前缀")
        try:
            response = completion(
                model="registry.ollama.ai/library/qwen2.5/3b",
                messages=[{"role": "user", "content": "你好"}],
                api_key="sk-no-key-required",
                base_url="http://localhost:11435/v1",
                max_tokens=20
            )
            print(f"[OK] 回复: {response.choices[0].message.content[:50]}...")
        except Exception as e:
            print(f"[ERROR] {e}")

        print("\n[测试3] 使用 text-completion 模型类型")
        try:
            response = completion(
                model="text-completion-openai/registry.ollama.ai/library/qwen2.5/3b",
                messages=[{"role": "user", "content": "你好"}],
                api_key="sk-no-key-required",
                base_url="http://localhost:11435/v1",
                max_tokens=20
            )
            print(f"[OK] 回复: {response.choices[0].message.content[:50]}...")
        except Exception as e:
            print(f"[ERROR] {e}")

    except ImportError:
        print("[ERROR] litellm 未安装")


if __name__ == "__main__":
    asyncio.run(test_litellm_custom_base_url())
