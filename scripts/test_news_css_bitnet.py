#!/usr/bin/env python3
"""
测试 Crawl4AI 新闻提取功能
只对接 crawl4ai API
"""

import asyncio
from datetime import datetime
from crawl4ai_client import Crawl4AIClient


async def test_news_extraction():
    print("=" * 60)
    print("Crawl4AI 新闻提取测试")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    client = Crawl4AIClient()
    
    # 检查服务状态
    print("\n[1] 检查服务状态...")
    if not await client.check_health():
        print("    crawl4ai: ✗")
        print("\n[ERROR] 服务未就绪")
        return
    print("    crawl4ai: ✓")

    # 测试1: CSS 选择器提取新闻链接
    print("\n" + "=" * 40)
    print("[测试1] CSS 选择器提取新闻链接")
    print("=" * 40)
    
    schema = {
        "name": "NewsLinks",
        "baseSelector": "a",
        "fields": [
            {"name": "text", "type": "text"},
            {"name": "href", "type": "attribute", "attribute": "href"}
        ]
    }
    
    links = await client.extract_with_css("https://news.sina.com.cn", schema)
    print(f"[OK] 提取到 {len(links)} 条链接")
    
    # 过滤有效新闻
    valid_news = []
    for item in links:
        if isinstance(item, dict):
            text = item.get("text", "").strip()
            if len(text) > 5 and "客户端" not in text and "新闻" not in text[:4]:
                valid_news.append(text)
    
    print(f"\n过滤后得到 {len(valid_news)} 条可能的新闻:")
    for i, text in enumerate(valid_news[:10]):
        print(f"    {i+1}. {text[:50]}")

    # 测试2: LLM 提取新闻摘要
    print("\n" + "=" * 40)
    print("[测试2] LLM 智能提取新闻摘要")
    print("=" * 40)
    
    result = await client.extract_with_llm(
        "https://news.sina.com.cn",
        "请提取这个新闻网站首页的前3条新闻标题，每行一个标题",
        max_tokens=300
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
    asyncio.run(test_news_extraction())
