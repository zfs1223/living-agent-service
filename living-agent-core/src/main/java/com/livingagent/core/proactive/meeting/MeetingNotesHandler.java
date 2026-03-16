package com.livingagent.core.proactive.meeting;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.event.EventHookManager;
import com.livingagent.core.proactive.event.HookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MeetingNotesHandler {

    private static final Logger log = LoggerFactory.getLogger(MeetingNotesHandler.class);

    private final EventHookManager eventHookManager;
    private final List<AlertNotifier> notifiers;
    
    private final Map<String, MeetingTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<MeetingNotes>> meetingHistory = new ConcurrentHashMap<>();

    public MeetingNotesHandler(EventHookManager eventHookManager, List<AlertNotifier> notifiers) {
        this.eventHookManager = eventHookManager;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        
        registerDefaultTemplates();
        registerEventHandlers();
    }

    private void registerDefaultTemplates() {
        registerTemplate(new MeetingTemplate(
                "standup",
                "站会纪要模板",
                List.of("昨日完成", "今日计划", "阻塞问题", "需要帮助"),
                15
        ));
        
        registerTemplate(new MeetingTemplate(
                "sprint_review",
                "冲刺回顾模板",
                List.of("冲刺目标", "完成情况", "演示内容", "下个冲刺计划"),
                30
        ));
        
        registerTemplate(new MeetingTemplate(
                "weekly",
                "周会纪要模板",
                List.of("本周进展", "下周计划", "风险预警", "需要决策"),
                60
        ));
        
        registerTemplate(new MeetingTemplate(
                "project",
                "项目会议模板",
                List.of("项目状态", "里程碑", "风险项", "决策项", "行动项"),
                60
        ));
    }

    private void registerEventHandlers() {
        if (eventHookManager != null) {
            eventHookManager.registerHandler(new com.livingagent.core.proactive.event.HookHandler() {
                @Override
                public String[] supportedEvents() {
                    return new String[]{"meeting.started", "meeting.ended", "transcript.ready"};
                }

                @Override
                public int getOrder() {
                    return 10;
                }

                @Override
                public void handle(HookEvent event) {
                    handleMeetingEvent(event);
                }
            });
        }
    }

    public void registerTemplate(MeetingTemplate template) {
        templates.put(template.templateId(), template);
        log.info("Registered meeting template: {}", template.name());
    }

    public MeetingNotes processTranscript(String meetingId, String transcript, String templateId) {
        log.info("Processing meeting transcript: {}", meetingId);

        MeetingTemplate template = templates.getOrDefault(templateId, templates.get("standup"));
        
        MeetingNotes notes = new MeetingNotes(
                "notes_" + System.currentTimeMillis(),
                meetingId,
                templateId,
                template.name(),
                extractSections(transcript, template),
                extractActionItems(transcript),
                extractDecisions(transcript),
                Instant.now(),
                transcript
        );

        meetingHistory.computeIfAbsent(meetingId, k -> new ArrayList<>()).add(notes);

        notifyParticipants(notes);

        log.info("Generated meeting notes: {} with {} action items", 
                meetingId, notes.actionItems().size());
        
        return notes;
    }

    private Map<String, String> extractSections(String transcript, MeetingTemplate template) {
        Map<String, String> sections = new LinkedHashMap<>();
        
        String[] paragraphs = transcript.split("\n\n");
        
        for (String sectionName : template.sections()) {
            StringBuilder content = new StringBuilder();
            
            for (String para : paragraphs) {
                String lowerPara = para.toLowerCase();
                if (lowerPara.contains(sectionName.toLowerCase()) || 
                    lowerPara.contains(getSectionKeywords(sectionName))) {
                    content.append(para).append("\n\n");
                }
            }
            
            if (content.length() > 0) {
                sections.put(sectionName, content.toString().trim());
            } else {
                sections.put(sectionName, "（未在转录中找到相关内容）");
            }
        }
        
        return sections;
    }

    private String getSectionKeywords(String sectionName) {
        return switch (sectionName) {
            case "昨日完成", "本周进展", "完成情况" -> "完成|done|finished|完成";
            case "今日计划", "下周计划" -> "计划|plan|明天|下周";
            case "阻塞问题", "风险预警", "风险项" -> "阻塞|block|问题|风险|issue";
            case "需要帮助", "需要决策", "决策项" -> "帮助|help|决策|decision";
            case "里程碑" -> "milestone|里程碑";
            case "行动项" -> "action|行动|todo";
            default -> "";
        };
    }

    private List<ActionItem> extractActionItems(String transcript) {
        List<ActionItem> items = new ArrayList<>();
        
        String[] lines = transcript.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- []") || 
                trimmed.startsWith("* [ ]") || trimmed.startsWith("* []")) {
                
                String content = trimmed.replaceAll("^[-*]\\s*\\[[\\s\\]]\\s*", "");
                if (!content.isEmpty()) {
                    String[] parts = content.split("\\s*-\\s*|\\s*@");
                    String task = parts[0].trim();
                    String assignee = parts.length > 1 ? parts[1].trim() : null;
                    
                    items.add(new ActionItem(
                            "action_" + System.currentTimeMillis() + "_" + items.size(),
                            task,
                            assignee,
                            ActionStatus.TODO,
                            Instant.now(),
                            null
                    ));
                }
            }
        }
        
        return items;
    }

    private List<DecisionItem> extractDecisions(String transcript) {
        List<DecisionItem> items = new ArrayList<>();
        
        String[] lines = transcript.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().contains("决定:") || 
                trimmed.toLowerCase().contains("decision:") ||
                trimmed.toLowerCase().contains("决议:")) {
                
                String content = trimmed.replaceAll("^[决定|决策|决议|Decision]:\\s*", "");
                if (!content.isEmpty()) {
                    items.add(new DecisionItem(
                            "decision_" + System.currentTimeMillis() + "_" + items.size(),
                            content.trim(),
                            Instant.now()
                    ));
                }
            }
        }
        
        return items;
    }

    private void notifyParticipants(MeetingNotes notes) {
        String content = formatNotesContent(notes);

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable()) {
                try {
                    Alert alert = Alert.info(
                            "会议纪要: " + notes.meetingTitle(),
                            content
                    );
                    notifier.send(alert);
                    log.debug("Meeting notes sent via {}", notifier.getChannelName());
                } catch (Exception e) {
                    log.warn("Failed to send meeting notes via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    private String formatNotesContent(MeetingNotes notes) {
        StringBuilder content = new StringBuilder();
        
        content.append("### ").append(notes.meetingTitle()).append("\n\n");
        content.append("**生成时间**: ").append(notes.generatedAt()).append("\n\n");
        
        content.append("#### 会议内容\n\n");
        for (Map.Entry<String, String> entry : notes.sections().entrySet()) {
            content.append("**").append(entry.getKey()).append("**\n");
            content.append(entry.getValue()).append("\n\n");
        }
        
        if (!notes.actionItems().isEmpty()) {
            content.append("#### 行动项\n\n");
            for (ActionItem item : notes.actionItems()) {
                content.append("- ").append(item.task());
                if (item.assignee() != null) {
                    content.append(" @").append(item.assignee());
                }
                content.append("\n");
            }
            content.append("\n");
        }
        
        if (!notes.decisions().isEmpty()) {
            content.append("#### 决策项\n\n");
            for (DecisionItem decision : notes.decisions()) {
                content.append("- ").append(decision.content()).append("\n");
            }
        }
        
        return content.toString();
    }

    private void handleMeetingEvent(HookEvent event) {
        String eventType = event.eventType();
        
        switch (eventType) {
            case "meeting.started" -> {
                log.info("Meeting started: {}", event.get("meetingId"));
            }
            case "meeting.ended" -> {
                log.info("Meeting ended: {}", event.get("meetingId"));
            }
            case "transcript.ready" -> {
                String meetingId = event.getString("meetingId");
                String transcript = event.getString("transcript");
                String templateId = event.getString("templateId");
                
                if (meetingId != null && transcript != null) {
                    processTranscript(meetingId, transcript, templateId);
                }
            }
        }
    }

    public List<MeetingNotes> getMeetingHistory(String meetingId) {
        return meetingHistory.getOrDefault(meetingId, List.of());
    }

    public List<MeetingTemplate> getAvailableTemplates() {
        return new ArrayList<>(templates.values());
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMeetings", meetingHistory.values().stream().mapToInt(List::size).sum());
        stats.put("availableTemplates", templates.size());
        return stats;
    }

    public record MeetingTemplate(
            String templateId,
            String name,
            List<String> sections,
            int estimatedDurationMinutes
    ) {}

    public record MeetingNotes(
            String notesId,
            String meetingId,
            String templateId,
            String meetingTitle,
            Map<String, String> sections,
            List<ActionItem> actionItems,
            List<DecisionItem> decisions,
            Instant generatedAt,
            String rawTranscript
    ) {}

    public record ActionItem(
            String itemId,
            String task,
            String assignee,
            ActionStatus status,
            Instant createdAt,
            Instant completedAt
    ) {
        public ActionItem complete() {
            return new ActionItem(itemId, task, assignee, ActionStatus.DONE, createdAt, Instant.now());
        }
    }

    public record DecisionItem(
            String decisionId,
            String content,
            Instant decidedAt
    ) {}

    public enum ActionStatus {
        TODO, IN_PROGRESS, DONE, CANCELLED
    }
}
