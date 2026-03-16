#!/usr/bin/env python3
"""
测试 litellm 如何处理模型名称
"""

import asyncio
import httpx
import json


async def test_litellm_model_names():
    print("=" * 60)
    print("测试 litellm 模型名称处理")
    print("=" * 60)

    shimmy_url = "http://localhost:11435/v1/chat/completions"

    test_cases = [
        {
            "name": "直接调用 shimmy",
            "model": "registry.ollama.ai/library/qwen2.5/3b",
            "api_key": "sk-no-key-required"
        },
        {
            "name": "litellm 格式 (openai/)",
            "model": "openai/registry.ollama.ai/library/qwen2.5/3b",
            "api_key": "sk-no-key-required",
            "base_url": "http://localhost:11435/v1"
        }
    ]

    for test in test_cases:
        print(f"\n[{test['name']}]")
        print(f"模型: {test['model']}")

        payload = {
            "model": test["model"],
            "messages": [{"role": "user", "content": "你好"}],
            "max_tokens": 20,
            "temperature": 0.7
        }

        headers = {"Authorization": f"Bearer {test['api_key']}"}

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(shimmy_url, json=payload, headers=headers)
                result = response.json()

                if "choices" in result:
                    content = result["choices"][0]["message"]["content"]
                    print(f"[OK] 回复: {content[:50]}...")
                else:
                    print(f"[ERROR] 响应: {json.dumps(result, ensure_ascii=False)[:200]}")
        except Exception as e:
            print(f"[ERROR] 请求失败: {e}")

    # 测试 litellm 库
    print("\n" + "=" * 60)
    print("测试 litellm 库")
    print("=" * 60)

    try:
        from litellm import completion

        # 测试 litellm 如何处理模型名称
        response = completion(
            model="openai/registry.ollama.ai/library/qwen2.5/3b",
            messages=[{"role": "user", "content": "你好"}],
            api_key="sk-no-key-required",
            base_url="http://localhost:11435/v1",
            max_tokens=20
        )
        print(f"[OK] litellm 回复: {response.choices[0].message.content[:50]}...")
    except Exception as e:
        print(f"[ERROR] litellm 请求失败: {e}")


if __name__ == "__main__":
    asyncio.run(test_litellm_model_names())
