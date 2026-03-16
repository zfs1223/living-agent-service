#!/usr/bin/env python3
"""
Crawl4AI 使用指南

本文件记录了 crawl4ai 的正确使用方法，供 living-agent-service 调用参考。
"""

# =============================================================================
# 1. 服务架构
# =============================================================================

"""
架构说明:
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│ living-agent    │ ───▶ │ crawl4ai        │ ───▶ │ bitnet-server   │
│ (调用方)        │      │ (爬取服务)      │      │ (LLM 后端)      │
└─────────────────┘      └─────────────────┘      └─────────────────┘
        │                        │
        │    只需对接 crawl4ai    │
        └────────────────────────┘

服务地址:
- crawl4ai: http://localhost:11235
- bitnet-server: http://localhost:11236 (内部使用，调用方无需关心)

Docker 网络配置:
- crawl4ai-offline 和 bitnet-server 在同一网络
- crawl4ai 通过服务名 bitnet-server:11236 调用 LLM
"""

# =============================================================================
# 2. 客户端使用
# =============================================================================

"""
from crawl4ai_client import Crawl4AIClient

client = Crawl4AIClient()

# 方法1: 获取原始 Markdown
result = await client.get_markdown("https://example.com")
print(result.markdown)

# 方法2: CSS 选择器提取
schema = {
    "name": "Links",
    "baseSelector": "a",
    "fields": [
        {"name": "text", "type": "text"},
        {"name": "href", "type": "attribute", "attribute": "href"}
    ]
}
links = await client.extract_with_css(url, schema)

# 方法3: LLM 智能提取
result = await client.extract_with_llm(url, "请总结这个网页的内容")
print(result.extracted_content)
"""

# =============================================================================
# 3. API 格式
# =============================================================================

"""
crawl4ai REST API 端点:
- POST /crawl - 爬取网页
- GET /health - 健康检查

请求格式:
{
    "urls": ["https://example.com"],
    "browser_config": {
        "type": "BrowserConfig",
        "params": {
            "headless": true,
            "verbose": false
        }
    },
    "crawler_config": {
        "type": "CrawlerRunConfig",
        "params": {
            "cache_mode": "BYPASS",
            "word_count_threshold": 10
        }
    }
}

响应格式:
{
    "success": true,
    "results": [
        {
            "url": "https://example.com",
            "markdown": {
                "raw_markdown": "# Example Domain\n...",
                "fit_markdown": "..."
            },
            "extracted_content": null
        }
    ]
}
"""

# =============================================================================
# 4. 提取策略
# =============================================================================

"""
4.1 无提取 (默认)
-----------------
用途: 获取原始 Markdown 内容
适用: 所有场景

crawler_config: {
    "type": "CrawlerRunConfig",
    "params": {
        "cache_mode": "BYPASS",
        "word_count_threshold": 10
    }
}


4.2 JsonCssExtractionStrategy
-----------------------------
用途: 使用 CSS 选择器提取结构化数据
适用: 已知页面结构，精确提取特定元素

extraction_strategy: {
    "type": "JsonCssExtractionStrategy",
    "params": {
        "schema": {
            "name": "NewsHeadlines",
            "baseSelector": "a.news-title",
            "fields": [
                {"name": "title", "type": "text"},
                {"name": "link", "type": "attribute", "attribute": "href"}
            ]
        }
    }
}


4.3 LLMExtractionStrategy
-------------------------
用途: 使用 LLM 智能提取内容
适用: 复杂内容理解、结构化提取

extraction_strategy: {
    "type": "LLMExtractionStrategy",
    "params": {
        "instruction": "提取新闻标题",
        "chunk_token_threshold": 1024,
        "extra_args": {
            "temperature": 0.7,
            "max_tokens": 500
        }
    }
}

注意: LLM 提取由 crawl4ai 内部调用 bitnet-server，调用方无需关心。
"""

# =============================================================================
# 5. 常见问题
# =============================================================================

"""
Q: LLMExtractionStrategy 返回模板内容?
A: BitNet 3B 模型可能被 prompt 中的示例混淆。解决方案:
   1. 使用更简单的指令
   2. 使用 CSS 选择器先提取，再让 LLM 处理

Q: 新闻网站内容太长?
A: 使用 CSS 选择器精确定位，或调整 chunk_token_threshold

Q: 如何验证服务状态?
A: 使用 client.check_health() 检查 crawl4ai 服务
"""

# =============================================================================
# 6. 测试示例
# =============================================================================

if __name__ == "__main__":
    import asyncio
    from crawl4ai_client import Crawl4AIClient

    async def main():
        client = Crawl4AIClient()
        
        # 检查服务
        if not await client.check_health():
            print("服务未就绪")
            return
        
        # 获取 Markdown
        result = await client.get_markdown("https://example.com")
        print(f"Markdown: {result.markdown[:100]}...")
        
        # CSS 选择器
        schema = {
            "name": "Links",
            "baseSelector": "a",
            "fields": [{"name": "text", "type": "text"}]
        }
        links = await client.extract_with_css("https://example.com", schema)
        print(f"链接数: {len(links)}")
        
        # LLM 提取
        result = await client.extract_with_llm(
            "https://example.com",
            "总结这个网页"
        )
        print(f"提取: {result.extracted_content}")

    asyncio.run(main())
