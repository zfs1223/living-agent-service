#!/usr/bin/env python3
"""
Crawl4AI 客户端封装
调用方只需对接 crawl4ai，无需关心后端模型

使用方法:
    from crawl4ai_client import Crawl4AIClient
    
    client = Crawl4AIClient()
    
    # 方法1: 获取原始 Markdown
    markdown = await client.get_markdown("https://example.com")
    
    # 方法2: CSS 选择器提取
    links = await client.extract_with_css(url, schema)
    
    # 方法3: LLM 智能提取 (crawl4ai 内部调用 BitNet)
    result = await client.extract_with_llm(url, instruction)
"""

import asyncio
import json
import httpx
from typing import Dict, Any, List, Optional
from dataclasses import dataclass


@dataclass
class CrawlResult:
    success: bool
    url: str
    markdown: str
    extracted_content: Any = None
    error: str = None


class Crawl4AIClient:
    """
    Crawl4AI 客户端
    
    服务配置:
    - crawl4ai 服务: http://localhost:11235
    - BitNet 模型已配置为 crawl4ai 的默认 LLM
    
    注意:
    - 调用方只需对接 crawl4ai API
    - LLM 相关功能由 crawl4ai 内部处理
    """
    
    def __init__(
        self,
        crawl4ai_url: str = "http://localhost:11235",
        timeout: float = 180.0
    ):
        self.crawl4ai_url = crawl4ai_url
        self.timeout = httpx.Timeout(timeout, connect=30.0)

    async def check_health(self) -> bool:
        """检查 crawl4ai 服务状态"""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(f"{self.crawl4ai_url}/health")
                return response.status_code == 200
        except:
            return False

    async def _crawl(self, payload: Dict) -> Dict:
        """内部爬取方法"""
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.crawl4ai_url}/crawl",
                json=payload
            )
            response.raise_for_status()
            return response.json()

    async def get_markdown(self, url: str) -> CrawlResult:
        """
        获取网页的原始 Markdown 内容
        
        Args:
            url: 网页 URL
            
        Returns:
            CrawlResult: 包含 markdown 内容的结果
        """
        payload = {
            "urls": [url],
            "browser_config": {
                "type": "BrowserConfig",
                "params": {"headless": True, "verbose": False}
            },
            "crawler_config": {
                "type": "CrawlerRunConfig",
                "params": {
                    "cache_mode": "BYPASS",
                    "word_count_threshold": 10
                }
            }
        }
        
        result = await self._crawl(payload)
        
        if result.get("success") and result.get("results"):
            data = result["results"][0]
            return CrawlResult(
                success=True,
                url=url,
                markdown=data.get("markdown", {}).get("raw_markdown", "")
            )
        
        return CrawlResult(
            success=False,
            url=url,
            markdown="",
            error=result.get("error", "Unknown error")
        )

    async def extract_with_css(
        self,
        url: str,
        schema: Dict,
        cache_mode: str = "BYPASS"
    ) -> List[Dict]:
        """
        使用 CSS 选择器提取结构化数据
        
        Args:
            url: 网页 URL
            schema: CSS 选择器 schema
                示例: {
                    "name": "Links",
                    "baseSelector": "a",
                    "fields": [
                        {"name": "text", "type": "text"},
                        {"name": "href", "type": "attribute", "attribute": "href"}
                    ]
                }
            cache_mode: 缓存模式
            
        Returns:
            List[Dict]: 提取的数据列表
        """
        payload = {
            "urls": [url],
            "browser_config": {
                "type": "BrowserConfig",
                "params": {"headless": True, "verbose": False}
            },
            "crawler_config": {
                "type": "CrawlerRunConfig",
                "params": {
                    "cache_mode": cache_mode,
                    "extraction_strategy": {
                        "type": "JsonCssExtractionStrategy",
                        "params": {"schema": schema}
                    }
                }
            }
        }
        
        result = await self._crawl(payload)
        
        if result.get("success") and result.get("results"):
            extracted = result["results"][0].get("extracted_content", [])
            if isinstance(extracted, str):
                try:
                    extracted = json.loads(extracted)
                except:
                    pass
            return extracted
        
        return []

    async def extract_with_llm(
        self,
        url: str,
        instruction: str,
        chunk_token_threshold: int = 1024,
        temperature: float = 0.7,
        max_tokens: int = 500
    ) -> CrawlResult:
        """
        使用 LLM 智能提取内容
        
        注意: 
        - crawl4ai 内部使用 BitNet 模型
        - LLMExtractionStrategy 的 prompt 模板较复杂
        - 对于简单任务，建议使用 get_markdown 后自行处理
        
        Args:
            url: 网页 URL
            instruction: 提取指令
            chunk_token_threshold: 分块阈值
            temperature: 温度参数
            max_tokens: 最大输出 token 数
            
        Returns:
            CrawlResult: 包含提取内容的结果
        """
        payload = {
            "urls": [url],
            "browser_config": {
                "type": "BrowserConfig",
                "params": {"headless": True, "verbose": False}
            },
            "crawler_config": {
                "type": "CrawlerRunConfig",
                "params": {
                    "cache_mode": "BYPASS",
                    "word_count_threshold": 10,
                    "extraction_strategy": {
                        "type": "LLMExtractionStrategy",
                        "params": {
                            "instruction": instruction,
                            "chunk_token_threshold": chunk_token_threshold,
                            "extra_args": {
                                "temperature": temperature,
                                "max_tokens": max_tokens
                            }
                        }
                    }
                }
            }
        }
        
        result = await self._crawl(payload)
        
        if result.get("success") and result.get("results"):
            data = result["results"][0]
            return CrawlResult(
                success=True,
                url=url,
                markdown=data.get("markdown", {}).get("raw_markdown", ""),
                extracted_content=data.get("extracted_content")
            )
        
        return CrawlResult(
            success=False,
            url=url,
            markdown="",
            error=result.get("error", "Unknown error")
        )

    async def crawl_multiple(
        self,
        urls: List[str],
        cache_mode: str = "BYPASS"
    ) -> List[CrawlResult]:
        """
        批量爬取多个网页
        
        Args:
            urls: URL 列表
            cache_mode: 缓存模式
            
        Returns:
            List[CrawlResult]: 爬取结果列表
        """
        payload = {
            "urls": urls,
            "browser_config": {
                "type": "BrowserConfig",
                "params": {"headless": True, "verbose": False}
            },
            "crawler_config": {
                "type": "CrawlerRunConfig",
                "params": {
                    "cache_mode": cache_mode,
                    "word_count_threshold": 10
                }
            }
        }
        
        result = await self._crawl(payload)
        
        results = []
        if result.get("success") and result.get("results"):
            for data in result["results"]:
                results.append(CrawlResult(
                    success=True,
                    url=data.get("url", ""),
                    markdown=data.get("markdown", {}).get("raw_markdown", "")
                ))
        
        return results


async def demo():
    """演示用法"""
    client = Crawl4AIClient()
    
    print("=" * 60)
    print("Crawl4AI 客户端演示")
    print("=" * 60)
    
    # 检查服务
    print("\n[1] 检查服务状态...")
    if await client.check_health():
        print("    crawl4ai: ✓")
    else:
        print("    crawl4ai: ✗")
        print("\n[ERROR] 服务未就绪")
        return
    
    # 方法1: 获取 Markdown
    print("\n[2] 获取 Markdown 内容...")
    result = await client.get_markdown("https://example.com")
    if result.success:
        print(f"    获取成功，内容长度: {len(result.markdown)} 字符")
        print(f"    预览: {result.markdown[:100]}...")
    
    # 方法2: CSS 选择器提取
    print("\n[3] CSS 选择器提取链接...")
    schema = {
        "name": "Links",
        "baseSelector": "a",
        "fields": [
            {"name": "text", "type": "text"},
            {"name": "href", "type": "attribute", "attribute": "href"}
        ]
    }
    links = await client.extract_with_css("https://example.com", schema)
    print(f"    提取到 {len(links)} 条链接")
    for link in links[:3]:
        print(f"    - {link.get('text', '')[:30]}")
    
    # 方法3: LLM 提取
    print("\n[4] LLM 智能提取...")
    result = await client.extract_with_llm(
        "https://example.com",
        "请用中文总结这个网页的主要内容"
    )
    if result.success:
        print(f"    提取结果: {result.extracted_content}")


if __name__ == "__main__":
    asyncio.run(demo())
