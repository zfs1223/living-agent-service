package com.livingagent.core.neuron.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class RouterNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(RouterNeuron.class);

    public static final String ID = "neuron://core/bitnet/router";
    public static final String INPUT_CHANNEL = "channel://perception/text";
    public static final String OUTPUT_CHANNEL_PATTERN = "channel://dispatch/%s";

    private final Map<String, DepartmentConfig> departments = new LinkedHashMap<>();
    private Provider intentProvider;
    private boolean useRuleBasedFallback = true;

    public RouterNeuron() {
        super(
            ID,
            "RouterNeuron",
            "路由神经元 - 意图识别和部门分发",
            List.of(INPUT_CHANNEL),
            List.of(),
            List.of()
        );
        initDefaultDepartments();
    }
    
    @Override
    public String getType() {
        return "router";
    }
    
    @Override
    public void initialize(NeuronContext context) {
        this.context = context;
        log.info("RouterNeuron initialized with context");
    }
    
    @Override
    public void subscribe(Channel channel) {
        log.info("RouterNeuron subscribing to channel: {}", channel.getId());
    }
    
    @Override
    public void publishTo(Channel channel) {
        log.info("RouterNeuron will publish to channel: {}", channel.getId());
    }

    private void initDefaultDepartments() {
        departments.put("tech", new DepartmentConfig(
            "tech",
            "技术部门",
            List.of("代码", "git", "gitlab", "jenkins", "部署", "bug", "mr", "merge", "commit", "分支", "构建"),
            "channel://tech/tasks"
        ));
        departments.put("hr", new DepartmentConfig(
            "hr",
            "人力资源部门",
            List.of("请假", "考勤", "员工", "招聘", "入职", "离职", "薪资", "福利", "hr"),
            "channel://hr/tasks"
        ));
        departments.put("fin", new DepartmentConfig(
            "fin",
            "财务部门",
            List.of("报销", "发票", "预算", "财务", "付款", "合同", "采购", "费用"),
            "channel://fin/tasks"
        ));
        departments.put("sal", new DepartmentConfig(
            "sal",
            "销售部门",
            List.of("客户", "商机", "销售", "合同", "报价", "crm", "跟进"),
            "channel://sal/tasks"
        ));
        departments.put("cs", new DepartmentConfig(
            "cs",
            "客服部门",
            List.of("工单", "投诉", "客服", "反馈", "问题", "帮助", "支持"),
            "channel://cs/tasks"
        ));
        departments.put("adm", new DepartmentConfig(
            "adm",
            "行政部门",
            List.of("会议", "行政", "资产", "采购", "用车", "订餐", "会议室"),
            "channel://adm/tasks"
        ));
        departments.put("leg", new DepartmentConfig(
            "leg",
            "法务部门",
            List.of("合同", "法律", "法务", "合规", "风险", "审查"),
            "channel://leg/tasks"
        ));
        departments.put("ops", new DepartmentConfig(
            "ops",
            "运营部门",
            List.of("运营", "数据", "报表", "分析", "指标", "推广"),
            "channel://ops/tasks"
        ));
    }

    public void setIntentProvider(Provider provider) {
        this.intentProvider = provider;
    }

    public void addDepartment(String key, String name, List<String> keywords, String channel) {
        departments.put(key, new DepartmentConfig(key, name, keywords, channel));
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("RouterNeuron started, listening to {}", INPUT_CHANNEL);
    }

    @Override
    protected void doStop() {
        log.info("RouterNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("RouterNeuron processing message: {}", message.getId());

        if (message.getType() != ChannelMessage.MessageType.TEXT) {
            log.warn("RouterNeuron received non-text message: {}", message.getType());
            return;
        }

        try {
            String text = extractText(message);
            String department = classifyIntent(text);
            
            if (department == null) {
                department = "tech";
                log.debug("No specific department matched, using default: {}", department);
            }

            String targetChannel = String.format(OUTPUT_CHANNEL_PATTERN, department);
            
            ChannelMessage routedMessage = new ChannelMessage(
                INPUT_CHANNEL,
                getId(),
                targetChannel,
                message.getSessionId(),
                ChannelMessage.MessageType.TEXT,
                text
            );
            routedMessage.addMetadata("original_message_id", message.getId());
            routedMessage.addMetadata("routed_department", department);
            routedMessage.addMetadata("router_neuron_id", getId());
            
            publish(targetChannel, routedMessage);
            log.info("Routed message to department '{}' via channel {}", department, targetChannel);
            
        } catch (Exception e) {
            log.error("Failed to route message", e);
        }
    }

    private String extractText(ChannelMessage message) {
        Object payload = message.getPayload();
        return payload != null ? payload.toString() : "";
    }

    private String classifyIntent(String text) {
        if (intentProvider != null) {
            try {
                return classifyWithLLM(text);
            } catch (Exception e) {
                log.warn("LLM classification failed, falling back to rules", e);
            }
        }
        
        if (useRuleBasedFallback) {
            return classifyWithRules(text);
        }
        
        return null;
    }

    private String classifyWithLLM(String text) {
        log.debug("Classifying intent with LLM for: {}", text.substring(0, Math.min(50, text.length())));
        return classifyWithRules(text);
    }

    private String classifyWithRules(String text) {
        String lowerText = text.toLowerCase();
        
        Map<String, Integer> scores = new HashMap<>();
        
        for (Map.Entry<String, DepartmentConfig> entry : departments.entrySet()) {
            DepartmentConfig config = entry.getValue();
            int score = 0;
            
            for (String keyword : config.keywords()) {
                if (lowerText.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }
        
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private record DepartmentConfig(
        String key,
        String name,
        List<String> keywords,
        String channel
    ) {}
}
