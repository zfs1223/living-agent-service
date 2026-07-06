package com.livingagent.core.evolution.codemapper;

import java.util.*;

/**
 * 代码上下文
 * 从异常/信号映射到的代码位置信息
 */
public class CodeContext {
    private String module;           // 模块名，如 core/brain
    private String description;      // 模块描述
    private List<String> files;      // 相关文件列表
    private String docRef;           // 相关文档引用
    private String riskLevel;        // 风险等级
    private String suggestedFix;     // 建议修复

    public CodeContext() {
        this.files = new ArrayList<>();
        this.riskLevel = "MEDIUM";
    }

    // 静态工厂
    public static CodeContext of(String module, String... files) {
        CodeContext ctx = new CodeContext();
        ctx.module = module;
        ctx.files = Arrays.asList(files);
        return ctx;
    }

    public CodeContext withDescription(String desc) { this.description = desc; return this; }
    public CodeContext withDocRef(String ref) { this.docRef = ref; return this; }
    public CodeContext withRiskLevel(String level) { this.riskLevel = level; return this; }
    public CodeContext withSuggestedFix(String fix) { this.suggestedFix = fix; return this; }

    // 所有 getter
    public String getModule() { return module; }
    public String getDescription() { return description; }
    public List<String> getFiles() { return files; }
    public String getDocRef() { return docRef; }
    public String getRiskLevel() { return riskLevel; }
    public String getSuggestedFix() { return suggestedFix; }

    // setter
    public void setModule(String module) { this.module = module; }
    public void setDescription(String description) { this.description = description; }
    public void setFiles(List<String> files) { this.files = files; }
    public void setDocRef(String docRef) { this.docRef = docRef; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setSuggestedFix(String suggestedFix) { this.suggestedFix = suggestedFix; }

    @Override
    public String toString() {
        return String.format("CodeContext{module='%s', files=%s, riskLevel='%s'}", module, files, riskLevel);
    }
}
