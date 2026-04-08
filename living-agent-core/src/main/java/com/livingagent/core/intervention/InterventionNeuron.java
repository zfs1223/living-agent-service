package com.livingagent.core.intervention;

import com.livingagent.core.neuron.impl.AbstractNeuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.channel.ChannelMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InterventionNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(InterventionNeuron.class);

    private static final String ID = "intervention/intervention/001";
    private static final String NAME = "InterventionNeuron";
    private static final String DESCRIPTION = "人工干预决策神经元，处理干预请求和响应";
    private static final String INPUT_CHANNEL = "channel://intervention/request";
    private static final String OUTPUT_CHANNEL = "channel://intervention/response";

    private final InterventionDecisionEngine decisionEngine;
    private final Map<String, InterventionDecision> pendingDecisions = new ConcurrentHashMap<>();

    public InterventionNeuron(InterventionDecisionEngine decisionEngine) {
        super(ID, NAME, DESCRIPTION, 
            List.of(INPUT_CHANNEL), List.of(OUTPUT_CHANNEL), Collections.emptyList());
        this.decisionEngine = decisionEngine;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("InterventionNeuron started");
    }

    @Override
    protected void doStop() {
        log.info("InterventionNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        if (message.getType() != ChannelMessage.MessageType.TEXT) {
            return;
        }

        Object contentObj = message.getContent();
        String content = contentObj != null ? contentObj.toString() : "";
        if (content.isEmpty()) {
            return;
        }

        if (content.contains("干预请求") || content.contains("intervention")) {
            handleInterventionRequest(message);
        } else if (content.contains("干预响应") || content.contains("response")) {
            handleInterventionResponse(message);
        } else if (content.contains("学习信号") || content.contains("learning")) {
            handleLearningSignal(message);
        }
    }

    private void handleInterventionRequest(ChannelMessage message) {
        Object opType = message.getMetadata().get("operationType");
        @SuppressWarnings("unchecked")
        Map<String, Object> opDetails = (Map<String, Object>) message.getMetadata().get("operationDetails");

        InterventionDecision decision = decisionEngine.evaluate(
            opType != null ? opType.toString() : "unknown",
            opDetails != null ? opDetails : new HashMap<>(),
            message.getSourceNeuronId(),
            message.getSourceChannelId()
        );

        pendingDecisions.put(decision.getDecisionId(), decision);

        log.info("Processed intervention request: {} -> {}", 
            decision.getDecisionId(), decision.getInterventionType());
    }

    private void handleInterventionResponse(ChannelMessage message) {
        String decisionId = (String) message.getMetadata().get("decisionId");
        String humanDecision = (String) message.getMetadata().get("humanDecision");
        String respondedBy = (String) message.getMetadata().get("respondedBy");

        InterventionDecision decision = pendingDecisions.get(decisionId);
        if (decision == null) {
            log.warn("Received response for unknown decision: {}", decisionId);
            return;
        }

        decision.setRespondedBy(respondedBy);
        InterventionDecision updated = decisionEngine.applyLearning(decision, humanDecision);

        pendingDecisions.remove(decisionId);

        log.info("Applied learning for decision: {} - Human decision: {}", 
            decisionId, humanDecision);
    }

    private void handleLearningSignal(ChannelMessage message) {
        log.info("Received learning signal: {}", message.getContent());
    }
}
