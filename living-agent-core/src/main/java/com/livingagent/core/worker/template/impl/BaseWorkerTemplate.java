package com.livingagent.core.worker.template.impl;

import com.livingagent.core.worker.DigitalWorker.WorkerType;
import com.livingagent.core.worker.template.WorkerTemplate;

import java.util.*;

public class BaseWorkerTemplate implements WorkerTemplate {

    private final String templateId;
    private final String name;
    private final String description;
    private final WorkerType workerType;
    private final String defaultDepartment;
    private final String defaultRole;
    private final Set<String> requiredCapabilities;
    private final Set<String> defaultSkills;
    private final List<String> subscribedChannels;
    private final List<String> publishChannels;
    private final Map<String, Object> defaultConfig;
    private final PersonalityTemplate personalityTemplate;
    private final int minExperienceLevel;
    private final int maxExperienceLevel;
    private final boolean autoDiscoverable;
    private final TemplateMetadata metadata;

    private BaseWorkerTemplate(Builder builder) {
        this.templateId = builder.templateId;
        this.name = builder.name;
        this.description = builder.description;
        this.workerType = builder.workerType;
        this.defaultDepartment = builder.defaultDepartment;
        this.defaultRole = builder.defaultRole;
        this.requiredCapabilities = Collections.unmodifiableSet(builder.requiredCapabilities);
        this.defaultSkills = Collections.unmodifiableSet(builder.defaultSkills);
        this.subscribedChannels = Collections.unmodifiableList(builder.subscribedChannels);
        this.publishChannels = Collections.unmodifiableList(builder.publishChannels);
        this.defaultConfig = Collections.unmodifiableMap(builder.defaultConfig);
        this.personalityTemplate = builder.personalityTemplate != null 
            ? builder.personalityTemplate : PersonalityTemplate.DEFAULT;
        this.minExperienceLevel = builder.minExperienceLevel;
        this.maxExperienceLevel = builder.maxExperienceLevel;
        this.autoDiscoverable = builder.autoDiscoverable;
        this.metadata = builder.metadata;
    }

    @Override
    public String getTemplateId() { return templateId; }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public WorkerType getWorkerType() { return workerType; }

    @Override
    public String getDefaultDepartment() { return defaultDepartment; }

    @Override
    public String getDefaultRole() { return defaultRole; }

    @Override
    public Set<String> getRequiredCapabilities() { return requiredCapabilities; }

    @Override
    public Set<String> getDefaultSkills() { return defaultSkills; }

    @Override
    public List<String> getSubscribedChannels() { return subscribedChannels; }

    @Override
    public List<String> getPublishChannels() { return publishChannels; }

    @Override
    public Map<String, Object> getDefaultConfig() { return defaultConfig; }

    @Override
    public PersonalityTemplate getPersonalityTemplate() { return personalityTemplate; }

    @Override
    public int getMinExperienceLevel() { return minExperienceLevel; }

    @Override
    public int getMaxExperienceLevel() { return maxExperienceLevel; }

    @Override
    public boolean isAutoDiscoverable() { return autoDiscoverable; }

    @Override
    public TemplateMetadata getMetadata() { return metadata; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String templateId;
        private String name;
        private String description = "";
        private WorkerType workerType = WorkerType.GENERALIST;
        private String defaultDepartment = "general";
        private String defaultRole = "worker";
        private Set<String> requiredCapabilities = new HashSet<>();
        private Set<String> defaultSkills = new HashSet<>();
        private List<String> subscribedChannels = new ArrayList<>();
        private List<String> publishChannels = new ArrayList<>();
        private Map<String, Object> defaultConfig = new HashMap<>();
        private PersonalityTemplate personalityTemplate;
        private int minExperienceLevel = 1;
        private int maxExperienceLevel = 10;
        private boolean autoDiscoverable = true;
        private TemplateMetadata metadata;

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder workerType(WorkerType workerType) {
            this.workerType = workerType;
            return this;
        }

        public Builder defaultDepartment(String defaultDepartment) {
            this.defaultDepartment = defaultDepartment;
            return this;
        }

        public Builder defaultRole(String defaultRole) {
            this.defaultRole = defaultRole;
            return this;
        }

        public Builder requiredCapabilities(Set<String> requiredCapabilities) {
            this.requiredCapabilities = new HashSet<>(requiredCapabilities);
            return this;
        }

        public Builder addRequiredCapability(String capability) {
            this.requiredCapabilities.add(capability);
            return this;
        }

        public Builder defaultSkills(Set<String> defaultSkills) {
            this.defaultSkills = new HashSet<>(defaultSkills);
            return this;
        }

        public Builder addDefaultSkill(String skill) {
            this.defaultSkills.add(skill);
            return this;
        }

        public Builder subscribedChannels(List<String> subscribedChannels) {
            this.subscribedChannels = new ArrayList<>(subscribedChannels);
            return this;
        }

        public Builder addSubscribedChannel(String channel) {
            this.subscribedChannels.add(channel);
            return this;
        }

        public Builder publishChannels(List<String> publishChannels) {
            this.publishChannels = new ArrayList<>(publishChannels);
            return this;
        }

        public Builder addPublishChannel(String channel) {
            this.publishChannels.add(channel);
            return this;
        }

        public Builder defaultConfig(Map<String, Object> defaultConfig) {
            this.defaultConfig = new HashMap<>(defaultConfig);
            return this;
        }

        public Builder addDefaultConfig(String key, Object value) {
            this.defaultConfig.put(key, value);
            return this;
        }

        public Builder personalityTemplate(PersonalityTemplate personalityTemplate) {
            this.personalityTemplate = personalityTemplate;
            return this;
        }

        public Builder minExperienceLevel(int minExperienceLevel) {
            this.minExperienceLevel = minExperienceLevel;
            return this;
        }

        public Builder maxExperienceLevel(int maxExperienceLevel) {
            this.maxExperienceLevel = maxExperienceLevel;
            return this;
        }

        public Builder autoDiscoverable(boolean autoDiscoverable) {
            this.autoDiscoverable = autoDiscoverable;
            return this;
        }

        public Builder metadata(TemplateMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public BaseWorkerTemplate build() {
            Objects.requireNonNull(templateId, "templateId is required");
            Objects.requireNonNull(name, "name is required");
            
            if (metadata == null) {
                long now = System.currentTimeMillis();
                metadata = new TemplateMetadata("system", "1.0.0", "default", List.of(), now, now);
            }
            
            return new BaseWorkerTemplate(this);
        }
    }
}
