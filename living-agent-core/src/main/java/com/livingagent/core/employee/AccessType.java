package com.livingagent.core.employee;

public enum AccessType {
    
    PUBLIC("公开访问", true, false),
    
    AUTHENTICATED("需登录", false, false),
    
    DEPARTMENT("需部门权限", false, true);

    private final String description;
    private final boolean publicAccess;
    private final boolean requiresDepartment;

    AccessType(String description, boolean publicAccess, boolean requiresDepartment) {
        this.description = description;
        this.publicAccess = publicAccess;
        this.requiresDepartment = requiresDepartment;
    }

    public String getDescription() { return description; }
    public boolean isPublicAccess() { return publicAccess; }
    public boolean requiresDepartment() { return requiresDepartment; }
}
