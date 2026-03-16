package com.livingagent.core.distributed.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class KafkaMessageService {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String NEURON_CHANNEL_TOPIC = "neuron-channel";
    private static final String EVOLUTION_SIGNAL_TOPIC = "evolution-signal";
    private static final String KNOWLEDGE_UPDATE_TOPIC = "knowledge-update";
    private static final String SYSTEM_EVENT_TOPIC = "system-event";
    private static final String TASK_DISPATCH_TOPIC = "task-dispatch";

    private final Map<String, Consumer<NeuronMessage>> messageHandlers = new ConcurrentHashMap<>();

    public KafkaMessageService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishToChannel(String channelId, Object message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(NEURON_CHANNEL_TOPIC, channelId, payload);
            log.debug("Published message to channel: {}", channelId);
        } catch (Exception e) {
            log.error("Failed to publish message to channel {}: {}", channelId, e.getMessage());
        }
    }

    public void publishEvolutionSignal(String brainDomain, Object signal) {
        try {
            String payload = objectMapper.writeValueAsString(signal);
            kafkaTemplate.send(EVOLUTION_SIGNAL_TOPIC, brainDomain, payload);
            log.info("Published evolution signal for brain: {}", brainDomain);
        } catch (Exception e) {
            log.error("Failed to publish evolution signal: {}", e.getMessage());
        }
    }

    public void publishKnowledgeUpdate(String knowledgeId, String action, Object data) {
        try {
            KnowledgeUpdateMessage message = new KnowledgeUpdateMessage(knowledgeId, action, data);
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(KNOWLEDGE_UPDATE_TOPIC, knowledgeId, payload);
            log.debug("Published knowledge update: {} - {}", knowledgeId, action);
        } catch (Exception e) {
            log.error("Failed to publish knowledge update: {}", e.getMessage());
        }
    }

    public void publishSystemEvent(String eventType, Object eventData) {
        try {
            SystemEventMessage message = new SystemEventMessage(eventType, eventData);
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(SYSTEM_EVENT_TOPIC, eventType, payload);
            log.info("Published system event: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to publish system event: {}", e.getMessage());
        }
    }

    public void dispatchTask(String brainDomain, Object task) {
        try {
            String payload = objectMapper.writeValueAsString(task);
            kafkaTemplate.send(TASK_DISPATCH_TOPIC, brainDomain, payload);
            log.debug("Dispatched task to brain: {}", brainDomain);
        } catch (Exception e) {
            log.error("Failed to dispatch task: {}", e.getMessage());
        }
    }

    public void registerHandler(String channelId, Consumer<NeuronMessage> handler) {
        messageHandlers.put(channelId, handler);
        log.info("Registered message handler for channel: {}", channelId);
    }

    public void unregisterHandler(String channelId) {
        messageHandlers.remove(channelId);
        log.info("Unregistered message handler for channel: {}", channelId);
    }

    @KafkaListener(topics = NEURON_CHANNEL_TOPIC, groupId = "living-agent-neurons")
    public void handleNeuronChannelMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String channelId = record.key();
        String payload = record.value();
        
        try {
            NeuronMessage message = objectMapper.readValue(payload, NeuronMessage.class);
            Consumer<NeuronMessage> handler = messageHandlers.get(channelId);
            
            if (handler != null) {
                handler.accept(message);
                log.debug("Processed neuron channel message: {}", channelId);
            }
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process neuron channel message: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = EVOLUTION_SIGNAL_TOPIC, groupId = "living-agent-evolution")
    public void handleEvolutionSignal(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String brainDomain = record.key();
        String payload = record.value();
        
        try {
            log.info("Received evolution signal for brain: {}", brainDomain);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process evolution signal: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = KNOWLEDGE_UPDATE_TOPIC, groupId = "living-agent-knowledge")
    public void handleKnowledgeUpdate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String knowledgeId = record.key();
        String payload = record.value();
        
        try {
            KnowledgeUpdateMessage message = objectMapper.readValue(payload, KnowledgeUpdateMessage.class);
            log.debug("Received knowledge update: {} - {}", knowledgeId, message.action());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process knowledge update: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = SYSTEM_EVENT_TOPIC, groupId = "living-agent-system")
    public void handleSystemEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String eventType = record.key();
        String payload = record.value();
        
        try {
            SystemEventMessage message = objectMapper.readValue(payload, SystemEventMessage.class);
            log.info("Received system event: {}", eventType);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process system event: {}", e.getMessage());
        }
    }

    public CompletableFuture<Void> sendAsync(String topic, String key, Object message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            return kafkaTemplate.send(topic, key, payload)
                .thenApply(result -> null);
        } catch (Exception e) {
            log.error("Failed to send async message: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    public record NeuronMessage(
        String messageId,
        String sourceNeuronId,
        String targetNeuronId,
        String channel,
        Object payload,
        long timestamp
    ) {
        public NeuronMessage {
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
        }
    }

    public record KnowledgeUpdateMessage(
        String knowledgeId,
        String action,
        Object data
    ) {}

    public record SystemEventMessage(
        String eventType,
        Object eventData
    ) {}
}
