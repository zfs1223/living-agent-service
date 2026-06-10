package com.livingagent.core.model.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内置模型目录 - 用于新用户首次使用时的默认模型种子数据
 * 
 * 设计理念：
 * - 本地/本地部署的 Ollama 模型通过 API 自动发现
 * - 云端模型（qwen、anthropic、deepseek 等）由用户通过前端手动添加
 * - 这样避免了硬编码模型信息过时或不准确的问题
 */
public class BuiltinModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(BuiltinModelCatalog.class);

    private static final String[] OLLAMA_BASE_URLS = {
        "http://localhost:11434",
        "http://host.docker.internal:11434"
    };

    /**
     * 获取所有内置模型
     * - Ollama 模型：通过 API 动态发现本地已下载的模型
     * - 其他供应商：返回空列表，由用户通过前端添加
     */
    public static List<LlmModel> getAllModels() {
        List<LlmModel> models = new ArrayList<>();
        
        // 自动发现 Ollama 本地模型
        List<LlmModel> ollamaModels = discoverOllamaModels();
        models.addAll(ollamaModels);
        
        return models;
    }

    /**
     * 通过 Ollama API 自动发现本地已下载的模型
     * 支持本地直接运行或在 Docker 中通过 host.docker.internal 访问宿主机的 Ollama
     */
    private static List<LlmModel> discoverOllamaModels() {
        for (String baseUrl : OLLAMA_BASE_URLS) {
            try {
                String urlStr = baseUrl + "/api/tags";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int status = conn.getResponseCode();
                if (status == 200) {
                    String body = new String(conn.getInputStream().readAllBytes());
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var root = mapper.readTree(body);
                    var modelsNode = root.path("models");
                    
                    List<LlmModel> discovered = new ArrayList<>();
                    for (var modelNode : modelsNode) {
                        String modelName = modelNode.path("name").asText();
                        if (!modelName.isEmpty()) {
                            int contextWindow = 32768;
                            String displayName = modelName + " (本地)";
                            String bestFor = "本地部署、低延迟";
                            boolean recommended = modelName.contains("7b") || modelName.contains("8b");
                            
                            LlmModel model = new LlmModel(
                                "ollama",
                                modelName,
                                displayName,
                                contextWindow,
                                4096,
                                false,
                                false,
                                null,
                                true,
                                recommended,
                                bestFor,
                                "text"
                            );
                            discovered.add(model);
                        }
                    }
                    
                    log.info("Discovered {} Ollama models from {}", discovered.size(), baseUrl);
                    return discovered;
                }
            } catch (Exception e) {
                log.debug("Ollama not available at {}, trying next URL: {}", baseUrl, e.getMessage());
            }
        }
        
        log.debug("Ollama not available at any configured URL, returning empty model list");
        return Collections.emptyList();
    }
}
