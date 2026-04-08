package com.livingagent.core.project;

public enum ProjectPhase {
    MARKET_ANALYSIS("市场调研", "market_analysis"),
    REQUIREMENT("需求分析", "requirement"),
    DESIGN("方案设计", "design"),
    DEVELOPMENT("开发实施", "development"),
    TESTING("测试验收", "testing"),
    DEPLOYMENT("上线部署", "deployment"),
    OPERATION("运营维护", "operation"),
    AFTER_SALES("售后服务", "after_sales");
    
    private final String displayName;
    private final String code;
    
    ProjectPhase(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public static ProjectPhase fromCode(String code) {
        for (ProjectPhase phase : values()) {
            if (phase.code.equals(code)) {
                return phase;
            }
        }
        return MARKET_ANALYSIS;
    }
}
