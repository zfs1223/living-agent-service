package com.livingagent.gateway.prompt;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.livingagent.gateway.dialogue.DialogueMessage;

@Component
public class PromptBuilder {
    
    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);
    
    private static final String DEFAULT_TEMPLATE = """
        {{system_prompt}}
        
        {{history}}
        
        用户: {{user_input}}
        """;
    
    private final PromptConfig promptConfig;
    
    public PromptBuilder(PromptConfig promptConfig) {
        this.promptConfig = promptConfig;
        initializeDefaultRoles();
    }
    
    private void initializeDefaultRoles() {
        if (promptConfig.getRoles().isEmpty()) {
            promptConfig.getRoles().put("assistant", RoleConfig.of("assistant", 
                "你是一个友好、专业的AI助手。请用简洁、清晰的语言回答用户的问题。"));
            
            promptConfig.getRoles().put("tech", RoleConfig.of("tech",
                "你是一个技术支持专家。请用专业的技术语言回答问题，必要时提供代码示例。"));
            
            promptConfig.getRoles().put("hr", RoleConfig.of("hr",
                "你是HR部门的智能助手。请用专业、友好的语言回答人力资源相关问题。"));
            
            promptConfig.getRoles().put("finance", RoleConfig.of("finance",
                "你是财务部门的智能助手。请用准确、严谨的语言回答财务相关问题。"));
        }
        
        if (promptConfig.getRoleMapping().getKeywordMapping().isEmpty()) {
            promptConfig.getRoleMapping().getKeywordMapping().put("代码", "tech");
            promptConfig.getRoleMapping().getKeywordMapping().put("bug", "tech");
            promptConfig.getRoleMapping().getKeywordMapping().put("请假", "hr");
            promptConfig.getRoleMapping().getKeywordMapping().put("报销", "finance");
            promptConfig.getRoleMapping().getKeywordMapping().put("工资", "finance");
        }
    }
    
    public String buildPrompt(String userMessage, List<DialogueMessage> history, String role) {
        String detectedRole = role;
        if (detectedRole == null || detectedRole.isEmpty()) {
            detectedRole = promptConfig.getRoleMapping().detectRole(userMessage);
        }
        
        RoleConfig roleConfig = promptConfig.getRole(detectedRole);
        String systemPrompt = roleConfig != null ? roleConfig.getSystemPrompt() : "";
        
        String historyText = formatHistory(history);
        
        String template = promptConfig.getDefaultTemplate();
        if (template == null || template.isEmpty()) {
            template = DEFAULT_TEMPLATE;
        }
        
        return template
            .replace("{{system_prompt}}", systemPrompt)
            .replace("{{history}}", historyText)
            .replace("{{user_input}}", userMessage);
    }
    
    public String buildPromptWithTools(String userMessage, List<DialogueMessage> history, 
            String role, List<Map<String, Object>> tools) {
        String basePrompt = buildPrompt(userMessage, history, role);
        
        if (tools == null || tools.isEmpty()) {
            return basePrompt;
        }
        
        StringBuilder toolsSection = new StringBuilder();
        toolsSection.append("\n\n## 可用工具\n\n");
        toolsSection.append("你可以使用以下工具来完成任务。如果需要使用工具，请用JSON格式回复：\n");
        toolsSection.append("```json\n{\"tool\": \"工具名\", \"arguments\": {\"参数\": \"值\"}}\n```\n\n");
        
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            String toolDesc = (String) tool.get("description");
            toolsSection.append("- **").append(toolName).append("**: ").append(toolDesc).append("\n");
        }
        
        return basePrompt + toolsSection.toString();
    }
    
    private String formatHistory(List<DialogueMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        
        StringJoiner joiner = new StringJoiner("\n");
        
        for (DialogueMessage msg : history) {
            if (msg.isSystem()) {
                continue;
            }
            
            String roleLabel = switch (msg.role()) {
                case "user" -> "用户";
                case "assistant" -> "助手";
                case "tool" -> "工具结果";
                default -> msg.role();
            };
            
            joiner.add(roleLabel + ": " + msg.content());
        }
        
        String result = joiner.toString();
        if (!result.isEmpty()) {
            result = "## 对话历史\n\n" + result;
        }
        
        return result;
    }
    
    public String detectRole(String text) {
        return promptConfig.getRoleMapping().detectRole(text);
    }
    
    public RoleConfig getRoleConfig(String roleName) {
        return promptConfig.getRole(roleName);
    }
}
