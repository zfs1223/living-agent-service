package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fixed_employee_definition")
public class FixedEmployeeDefinitionEntity {
    @Id
    @Column(name = "code", length = 16)
    private String code;

    @Column(name = "employee_id", length = 36, unique = true)
    private String employeeId;

    @Column(name = "name_zh", length = 100, nullable = false)
    private String nameZh;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "title_zh", length = 100, nullable = false)
    private String titleZh;

    @Column(name = "title_en", length = 100)
    private String titleEn;

    @Column(name = "department_code", length = 50, nullable = false)
    private String departmentCode;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "neuron_id", length = 100)
    private String neuronId;

    @Column(name = "channel", length = 100)
    private String channel;

    @Column(name = "roles", columnDefinition = "jsonb")
    private String roles;

    @Column(name = "capabilities", columnDefinition = "jsonb")
    private String capabilities;

    @Column(name = "tools", columnDefinition = "jsonb")
    private String tools;

    @Column(name = "required_skills", columnDefinition = "jsonb")
    private String requiredSkills;

    @Column(name = "personality", columnDefinition = "jsonb")
    private String personality;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public FixedEmployeeDefinitionEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.active = true;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public String getPersonality() { return personality; }
    public void setPersonality(String personality) { this.personality = personality; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
