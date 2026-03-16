package com.livingagent.core.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Department {

    private String departmentId;
    private String name;
    private String code;
    private String parentDepartmentId;
    private String managerId;
    private String managerName;
    private String targetBrain;
    private int level;
    private Set<String> memberIds;
    private Set<String> subDepartmentIds;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean active;

    public Department() {
        this.memberIds = new HashSet<>();
        this.subDepartmentIds = new HashSet<>();
        this.metadata = new HashMap<>();
        this.level = 1;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Department(String departmentId, String name) {
        this();
        this.departmentId = departmentId;
        this.name = name;
    }

    public String getBrainForDepartment() {
        if (targetBrain != null && !targetBrain.isEmpty()) {
            return targetBrain;
        }
        return mapDepartmentToBrain(name);
    }

    public static String mapDepartmentToBrain(String departmentName) {
        if (departmentName == null) {
            return "MainBrain";
        }
        
        String lower = departmentName.toLowerCase();
        if (lower.contains("技术") || lower.contains("tech") || lower.contains("研发") || lower.contains("rd")) {
            return "TechBrain";
        } else if (lower.contains("人力") || lower.contains("hr") || lower.contains("人事")) {
            return "HrBrain";
        } else if (lower.contains("财务") || lower.contains("finance") || lower.contains("财务")) {
            return "FinanceBrain";
        } else if (lower.contains("销售") || lower.contains("sales") || lower.contains("营销")) {
            return "SalesBrain";
        } else if (lower.contains("客服") || lower.contains("cs") || lower.contains("服务")) {
            return "CsBrain";
        } else if (lower.contains("行政") || lower.contains("admin")) {
            return "AdminBrain";
        } else if (lower.contains("法务") || lower.contains("legal")) {
            return "LegalBrain";
        } else if (lower.contains("运营") || lower.contains("ops")) {
            return "OpsBrain";
        }
        return "MainBrain";
    }

    public static String mapBrainToDepartment(String brainName) {
        if (brainName == null) {
            return "未知部门";
        }
        
        return switch (brainName) {
            case "TechBrain" -> "技术部";
            case "HrBrain" -> "人力资源部";
            case "FinanceBrain" -> "财务部";
            case "SalesBrain" -> "销售部";
            case "CsBrain" -> "客服部";
            case "AdminBrain" -> "行政部";
            case "LegalBrain" -> "法务部";
            case "OpsBrain" -> "运营部";
            case "MainBrain" -> "综合管理";
            default -> "未知部门";
        };
    }

    public void addMember(String employeeId) {
        memberIds.add(employeeId);
        this.updatedAt = Instant.now();
    }

    public void removeMember(String employeeId) {
        memberIds.remove(employeeId);
        this.updatedAt = Instant.now();
    }

    public void addSubDepartment(String subDeptId) {
        subDepartmentIds.add(subDeptId);
        this.updatedAt = Instant.now();
    }

    public void removeSubDepartment(String subDeptId) {
        subDepartmentIds.remove(subDeptId);
        this.updatedAt = Instant.now();
    }

    public int getMemberCount() {
        return memberIds.size();
    }

    public int getTotalMemberCount() {
        return memberIds.size() + subDepartmentIds.size();
    }

    public boolean isTopLevel() {
        return parentDepartmentId == null || parentDepartmentId.isEmpty();
    }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        this.targetBrain = mapDepartmentToBrain(name);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getParentDepartmentId() { return parentDepartmentId; }
    public void setParentDepartmentId(String parentDepartmentId) { this.parentDepartmentId = parentDepartmentId; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getTargetBrain() { return targetBrain; }
    public void setTargetBrain(String targetBrain) { this.targetBrain = targetBrain; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public Set<String> getMemberIds() { return memberIds; }
    public void setMemberIds(Set<String> memberIds) { this.memberIds = memberIds; }

    public Set<String> getSubDepartmentIds() { return subDepartmentIds; }
    public void setSubDepartmentIds(Set<String> subDepartmentIds) { this.subDepartmentIds = subDepartmentIds; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return String.format("Department{id=%s, name=%s, brain=%s, members=%d}",
            departmentId, name, targetBrain, memberIds.size());
    }
}
