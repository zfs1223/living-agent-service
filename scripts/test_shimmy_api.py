#!/usr/bin/env python3
"""
测试 shimmy 服务的 API
"""

import asyncio
import httpx
import json


async def test_shimmy_api():
    print("=" * 60)
    print("测试 shimmy 服务 API")
    print("=" * 60)

    shimmy_url = "http://localhost:11435/v1/chat/completions"

    test_cases = [
        {
            "name": "测试 qwen2.5/3b",
            "model": "registry.ollama.ai/library/qwen2.5/3b"
        },
        {
            "name": "测试 qwen2.5:3b",
            "model": "qwen2.5:3b"
        },
        {
            "name": "测试 ollama:qwen2.5/3b",
            "model": "ollama:qwen2.5/3b"
        }
    ]

    for test in test_cases:
        print(f"\n[{test['name']}]")
        print(f"模型: {test['model']}")

        payload = {
            "model": test["model"],
            "messages": [{"role": "user", "content": "你好，请用中文说一句话。"}],
            "max_tokens": 50,
            "temperature": 0.7
        }

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(shimmy_url, json=payload)
                result = response.json()

                if "choices" in result:
                    content = result["choices"][0]["message"]["content"]
                    print(f"[OK] 回复: {content[:100]}...")
                else:
                    print(f"[ERROR] 响应: {json.dumps(result, ensure_ascii=False)[:200]}")
        except Exception as e:
            print(f"[ERROR] 请求失败: {e}")


if __name__ == "__main__":
    asyncio.run(test_shimmy_api())
