package com.livingagent.core.proactive.predictor;

import com.livingagent.core.proactive.event.HookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventPredictor {

    private static final Logger log = LoggerFactory.getLogger(EventPredictor.class);

    private final Map<String, List<EventRule>> eventRules = new ConcurrentHashMap<>();
    private final Map<String, EventAggregator> aggregators = new ConcurrentHashMap<>();
    private final Map<String, List<TriggeredAction>> triggeredActions = new ConcurrentHashMap<>();

    public EventPredictor() {
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        addRule(EventRule.create("employee.joined", "新员工入职")
                .withCondition("department", ConditionOperator.NOT_EMPTY)
                .withAction("notify", Map.of("template", "新员工 {name} 已加入 {department}"))
                .withTargetUsers("hr_manager", "department_head"));

        addRule(EventRule.create("employee.departed", "员工离职")
                .withCondition("accessLevel", ConditionOperator.EQUALS, "CHAT_ONLY")
                .withAction("update_permission", Map.of("level", "CHAT_ONLY"))
                .withAction("notify", Map.of("template", "员工 {name} 已离职，权限已调整")));

        addRule(EventRule.create("project.deadline.near", "项目截止日期临近")
                .withCondition("daysRemaining", ConditionOperator.LESS_THAN, 7)
                .withAction("notify", Map.of("template", "项目 {projectName} 将在 {daysRemaining} 天后截止"))
                .withTargetUsers("project_manager", "team_members"));

        addRule(EventRule.create("system.error.high", "系统错误率过高")
                .withCondition("errorRate", ConditionOperator.GREATER_THAN, 0.1)
                .withAction("alert", Map.of("level", "WARNING"))
                .withAction("notify", Map.of("template", "系统错误率过高: {errorRate}%"))
                .withTargetUsers("tech_support"));

        addRule(EventRule.create("contract.expiring", "合同即将到期")
                .withCondition("daysUntilExpiry", ConditionOperator.LESS_THAN_EQUAL, 30)
                .withAction("notify", Map.of("template", "合同 {contractName} 将在 {daysUntilExpiry} 天后到期"))
                .withTargetUsers("contract_owner", "legal_team"));
    }

    public void addRule(EventRule rule) {
        eventRules.computeIfAbsent(rule.eventType(), k -> new ArrayList<>()).add(rule);
        log.info("Added event rule: {} for event type: {}", rule.name(), rule.eventType());
    }

    public void removeRule(String ruleId) {
        eventRules.values().forEach(rules -> rules.removeIf(r -> r.ruleId().equals(ruleId)));
        log.info("Removed event rule: {}", ruleId);
    }

    public List<TriggeredAction> processEvent(HookEvent event) {
        List<TriggeredAction> actions = new ArrayList<>();
        
        String eventType = event.eventType();
        List<EventRule> rules = eventRules.get(eventType);
        
        if (rules == null || rules.isEmpty()) {
            log.debug("No rules found for event type: {}", eventType);
            return actions;
        }

        for (EventRule rule : rules) {
            if (!rule.enabled()) {
                continue;
            }

            if (evaluateConditions(rule, event)) {
                List<TriggeredAction> triggeredActions = createActions(rule, event);
                actions.addAll(triggeredActions);
                
                log.info("Rule {} triggered for event {}", rule.name(), eventType);
            }
        }

        updateAggregators(event);
        
        return actions;
    }

    private boolean evaluateConditions(EventRule rule, HookEvent event) {
        for (EventCondition condition : rule.conditions()) {
            Object eventValue = event.get(condition.field());
            
            if (!evaluateCondition(condition, eventValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateCondition(EventCondition condition, Object value) {
        if (value == null) {
            return condition.operator() == ConditionOperator.IS_NULL;
        }

        return switch (condition.operator()) {
            case EQUALS -> value.toString().equals(condition.value().toString());
            case NOT_EQUALS -> !value.toString().equals(condition.value().toString());
            case GREATER_THAN -> compareNumeric(value, condition.value()) > 0;
            case GREATER_THAN_EQUAL -> compareNumeric(value, condition.value()) >= 0;
            case LESS_THAN -> compareNumeric(value, condition.value()) < 0;
            case LESS_THAN_EQUAL -> compareNumeric(value, condition.value()) <= 0;
            case CONTAINS -> value.toString().contains(condition.value().toString());
            case NOT_CONTAINS -> !value.toString().contains(condition.value().toString());
            case STARTS_WITH -> value.toString().startsWith(condition.value().toString());
            case ENDS_WITH -> value.toString().endsWith(condition.value().toString());
            case NOT_EMPTY -> !value.toString().isEmpty();
            case IS_NULL -> false;
            case IN -> {
                if (condition.value() instanceof Collection<?> collection) {
                    yield collection.contains(value.toString());
                }
                yield false;
            }
        };
    }

    private int compareNumeric(Object value, Object threshold) {
        try {
            double v = Double.parseDouble(value.toString());
            double t = Double.parseDouble(threshold.toString());
            return Double.compare(v, t);
        } catch (NumberFormatException e) {
            return value.toString().compareTo(threshold.toString());
        }
    }

    private List<TriggeredAction> createActions(EventRule rule, HookEvent event) {
        List<TriggeredAction> actions = new ArrayList<>();
        
        for (ActionDefinition actionDef : rule.actions()) {
            Map<String, Object> resolvedParams = resolveParameters(actionDef.parameters(), event);
            List<String> resolvedTargets = resolveTargetUsers(rule.targetUsers(), event);
            
            TriggeredAction action = new TriggeredAction(
                    "action_" + System.currentTimeMillis() + "_" + actions.size(),
                    rule.ruleId(),
                    actionDef.actionType(),
                    resolvedParams,
                    resolvedTargets,
                    Instant.now(),
                    ActionStatus.PENDING
            );
            actions.add(action);
        }
        
        return actions;
    }

    private Map<String, Object> resolveParameters(Map<String, Object> params, HookEvent event) {
        Map<String, Object> resolved = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.startsWith("{") && str.endsWith("}")) {
                String fieldName = str.substring(1, str.length() - 1);
                Object fieldValue = event.get(fieldName);
                resolved.put(entry.getKey(), fieldValue != null ? fieldValue : str);
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        
        return resolved;
    }

    private List<String> resolveTargetUsers(List<String> targetUsers, HookEvent event) {
        List<String> resolved = new ArrayList<>();
        
        for (String target : targetUsers) {
            if (target.startsWith("{") && target.endsWith("}")) {
                String fieldName = target.substring(1, target.length() - 1);
                Object fieldValue = event.get(fieldName);
                if (fieldValue instanceof String str) {
                    resolved.add(str);
                } else if (fieldValue instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) {
                            resolved.add(item.toString());
                        }
                    }
                }
            } else {
                resolved.add(target);
            }
        }
        
        return resolved;
    }

    private void updateAggregators(HookEvent event) {
        String eventType = event.eventType();
        
        EventAggregator aggregator = aggregators.computeIfAbsent(
                eventType, 
                k -> new EventAggregator(eventType, 3600)
        );
        
        aggregator.recordEvent(event);
        
        if (aggregator.shouldTrigger()) {
            log.info("Event aggregator triggered for: {} (count: {})", 
                    eventType, aggregator.getCount());
        }
    }

    public List<EventRule> getRulesForEventType(String eventType) {
        return eventRules.getOrDefault(eventType, List.of());
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", eventRules.values().stream().mapToInt(List::size).sum());
        stats.put("eventTypes", eventRules.keySet());
        
        Map<String, Object> aggregatorStats = new HashMap<>();
        aggregators.forEach((k, v) -> aggregatorStats.put(k, v.getStatistics()));
        stats.put("aggregators", aggregatorStats);
        
        return stats;
    }

    public record EventRule(
            String ruleId,
            String name,
            String eventType,
            List<EventCondition> conditions,
            List<ActionDefinition> actions,
            List<String> targetUsers,
            boolean enabled
    ) {
        public static EventRule create(String eventType, String name) {
            return new EventRule(
                    "rule_" + System.currentTimeMillis(),
                    name,
                    eventType,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    true
            );
        }
        
        public EventRule withCondition(String field, ConditionOperator operator) {
            conditions.add(new EventCondition(field, operator, null));
            return this;
        }
        
        public EventRule withCondition(String field, ConditionOperator operator, Object value) {
            conditions.add(new EventCondition(field, operator, value));
            return this;
        }
        
        public EventRule withAction(String actionType, Map<String, Object> parameters) {
            actions.add(new ActionDefinition(actionType, parameters));
            return this;
        }
        
        public EventRule withTargetUsers(String... users) {
            targetUsers.addAll(Arrays.asList(users));
            return this;
        }
    }

    public record EventCondition(
            String field,
            ConditionOperator operator,
            Object value
    ) {}

    public record ActionDefinition(
            String actionType,
            Map<String, Object> parameters
    ) {}

    public record TriggeredAction(
            String actionId,
            String ruleId,
            String actionType,
            Map<String, Object> parameters,
            List<String> targetUsers,
            Instant triggeredAt,
            ActionStatus status
    ) {}

    public enum ConditionOperator {
        EQUALS, NOT_EQUALS,
        GREATER_THAN, GREATER_THAN_EQUAL,
        LESS_THAN, LESS_THAN_EQUAL,
        CONTAINS, NOT_CONTAINS,
        STARTS_WITH, ENDS_WITH,
        NOT_EMPTY, IS_NULL, IN
    }

    public enum ActionStatus {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    private static class EventAggregator {
        private final String eventType;
        private final long windowSeconds;
        private final List<Instant> eventTimes = Collections.synchronizedList(new ArrayList<>());
        private final int threshold;

        EventAggregator(String eventType, long windowSeconds) {
            this.eventType = eventType;
            this.windowSeconds = windowSeconds;
            this.threshold = 10;
        }

        void recordEvent(HookEvent event) {
            eventTimes.add(Instant.now());
            cleanup();
        }

        private void cleanup() {
            Instant cutoff = Instant.now().minusSeconds(windowSeconds);
            eventTimes.removeIf(t -> t.isBefore(cutoff));
        }

        boolean shouldTrigger() {
            return eventTimes.size() >= threshold;
        }

        int getCount() {
            return eventTimes.size();
        }

        Map<String, Object> getStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("count", eventTimes.size());
            stats.put("threshold", threshold);
            stats.put("windowSeconds", windowSeconds);
            return stats;
        }
    }
}
