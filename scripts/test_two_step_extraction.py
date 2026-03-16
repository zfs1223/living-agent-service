#!/usr/bin/env python3
"""
测试 Crawl4AI 基本功能
只对接 crawl4ai API，无需关心后端模型
"""

import asyncio
from datetime import datetime
from crawl4ai_client import Crawl4AIClient


async def test_basic_crawl():
    print("=" * 60)
    print("Crawl4AI 基本功能测试")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    client = Crawl4AIClient()
    
    # 检查服务状态
    print("\n[1] 检查服务状态...")
    if await client.check_health():
        print("    crawl4ai: ✓")
    else:
        print("    crawl4ai: ✗")
        print("\n[ERROR] 服务未就绪")
        return

    # 测试1: 获取 Markdown
    print("\n" + "=" * 40)
    print("[测试1] 获取 Markdown 内容")
    print("=" * 40)
    
    result = await client.get_markdown("https://example.com")
    if result.success:
        print(f"[OK] 获取到 {len(result.markdown)} 字符")
        print(f"\n内容预览:\n{result.markdown}")
    else:
        print(f"[ERROR] {result.error}")

    # 测试2: CSS 选择器提取
    print("\n" + "=" * 40)
    print("[测试2] CSS 选择器提取链接")
    print("=" * 40)
    
    schema = {
        "name": "Links",
        "baseSelector": "a",
        "fields": [
            {"name": "text", "type": "text"},
            {"name": "href", "type": "attribute", "attribute": "href"}
        ]
    }
    
    links = await client.extract_with_css("https://example.com", schema)
    print(f"[OK] 提取到 {len(links)} 条链接")
    for link in links[:5]:
        print(f"    - {link.get('text', '')[:40]}")

    # 测试3: LLM 提取
    print("\n" + "=" * 40)
    print("[测试3] LLM 智能提取")
    print("=" * 40)
    
    result = await client.extract_with_llm(
        "https://example.com",
        "请用中文一句话总结这个网页的主要内容"
    )
    
    if result.success:
        print(f"[OK] 提取结果:")
        print(f"    {result.extracted_content}")
    else:
        print(f"[ERROR] {result.error}")

    print("\n" + "=" * 60)
    print("测试完成!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(test_basic_crawl())
