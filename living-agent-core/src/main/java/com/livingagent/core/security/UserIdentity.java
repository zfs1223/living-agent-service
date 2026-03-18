package com.livingagent.core.security;

public enum UserIdentity {
    
    INTERNAL_CHAIRMAN("董事长", AccessLevel.FULL, true),
    INTERNAL_ACTIVE("在职员工", AccessLevel.DEPARTMENT, true),
    INTERNAL_PROBATION("试用期员工", AccessLevel.LIMITED, true),
    INTERNAL_DEPARTED("离职员工", AccessLevel.CHAT_ONLY, false),
    EXTERNAL_VISITOR("外来访客", AccessLevel.CHAT_ONLY, false),
    EXTERNAL_CUSTOMER("客户", AccessLevel.LIMITED, true),
    EXTERNAL_PARTNER("合作伙伴", AccessLevel.LIMITED, true),
    EXTERNAL_CONTRACTOR("外包人员", AccessLevel.LIMITED, true);

    private final String description;
    private final AccessLevel defaultAccessLevel;
    private final boolean canAccessEnterprise;

    UserIdentity(String description, AccessLevel defaultAccessLevel, boolean canAccessEnterprise) {
        this.description = description;
        this.defaultAccessLevel = defaultAccessLevel;
        this.canAccessEnterprise = canAccessEnterprise;
    }

    public String getDescription() { return description; }
    public AccessLevel getDefaultAccessLevel() { return defaultAccessLevel; }
    public boolean canAccessEnterprise() { return canAccessEnterprise; }

    public boolean isInternal() {
        return this == INTERNAL_CHAIRMAN || this == INTERNAL_ACTIVE || this == INTERNAL_PROBATION || this == INTERNAL_DEPARTED;
    }

    public boolean isActiveEmployee() {
        return this == INTERNAL_CHAIRMAN || this == INTERNAL_ACTIVE || this == INTERNAL_PROBATION;
    }

    public boolean canUseMainBrain() {
        return canAccessEnterprise && this != INTERNAL_DEPARTED;
    }
    
    public boolean isExternal() {
        return this == EXTERNAL_VISITOR || this == EXTERNAL_CUSTOMER || 
               this == EXTERNAL_PARTNER || this == EXTERNAL_CONTRACTOR;
    }
    
    public boolean isCustomer() {
        return this == EXTERNAL_CUSTOMER;
    }

    public boolean isChairman() {
        return this == INTERNAL_CHAIRMAN;
    }
}
