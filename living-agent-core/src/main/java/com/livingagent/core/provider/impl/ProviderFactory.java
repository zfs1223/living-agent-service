package com.livingagent.core.provider.impl;

import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.Protocol;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderFactory.class);

    private final BrainModelResolver brainModelResolver;

    public ProviderFactory(BrainModelResolver brainModelResolver) {
        this.brainModelResolver = brainModelResolver;
    }

    public Provider create(String brainId) {
        if (brainModelResolver == null) {
            log.warn("BrainModelResolver 未配置，无法为 brainId={} 创建 Provider", brainId);
            return null;
        }

        ResolvedBrainModel resolvedModel = brainModelResolver.resolve(brainId);
        if (resolvedModel == null) {
            log.warn("无法解析 brainId={} 的模型配置，尝试默认解析", brainId);
            resolvedModel = brainModelResolver.resolveDefault(brainId);
        }

        if (resolvedModel == null) {
            log.warn("brainId={} 没有可用的模型配置", brainId);
            return null;
        }

        return createFromResolvedModel(resolvedModel);
    }

    public Provider createForEmployee(String employeeId, String departmentId, String departmentBrainId) {
        if (brainModelResolver == null) {
            log.warn("BrainModelResolver 未配置，无法为 employeeId={} 创建 Provider", employeeId);
            return null;
        }

        ResolvedBrainModel resolvedModel = brainModelResolver.resolveForEmployee(employeeId, departmentId, departmentBrainId);
        if (resolvedModel == null) {
            log.warn("无法解析 employeeId={} departmentId={} 的模型配置", employeeId, departmentId);
            return null;
        }

        return createFromResolvedModel(resolvedModel);
    }

    public Provider createFromResolvedModel(ResolvedBrainModel resolvedModel) {
        if (resolvedModel == null) {
            return null;
        }

        if (resolvedModel.getBaseUrl() == null || resolvedModel.getBaseUrl().isEmpty()) {
            log.warn("模型 baseUrl 为空，无法创建 Provider: providerId={}, modelName={}",
                resolvedModel.getProviderId(), resolvedModel.getModelName());
            return null;
        }

        if (resolvedModel.getModelName() == null || resolvedModel.getModelName().isBlank()) {
            log.warn("模型名称为空，无法创建 Provider: providerId={}, baseUrl={}",
                resolvedModel.getProviderId(), resolvedModel.getBaseUrl());
            return null;
        }

        Protocol protocol = resolvedModel.getProtocol();
        if (protocol == null) {
            protocol = Protocol.OPENAI_COMPATIBLE;
        }

        try {
            return switch (protocol) {
                case OPENAI_COMPATIBLE, GEMINI, OPENAI_RESPONSES -> new ResolvedBrainModelProvider(resolvedModel);
                case ANTHROPIC -> new AnthropicProvider(resolvedModel);
            };
        } catch (Exception e) {
            log.error("创建 Provider 失败: providerId={}, modelId={}, protocol={}, baseUrl={}",
                resolvedModel.getProviderId(), resolvedModel.getModelId(),
                resolvedModel.getProtocol(), resolvedModel.getBaseUrl(), e);
            return null;
        }
    }
}
