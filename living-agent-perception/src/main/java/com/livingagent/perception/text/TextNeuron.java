package com.livingagent.perception.text;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TextNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(TextNeuron.class);

    public static final String ID = "neuron://perception/text/001";
    public static final String INPUT_CHANNEL = "channel://input/text";
    public static final String OUTPUT_CHANNEL = "channel://perception/text";

    public TextNeuron() {
        super(
            ID,
            "TextNeuron",
            "文本感知神经元 - 处理文本输入",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            List.of()
        );
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("TextNeuron started, listening to {}", INPUT_CHANNEL);
    }

    @Override
    protected void doStop() {
        log.info("TextNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("TextNeuron processing message: {}", message.getId());

        if (message.getType() != ChannelMessage.MessageType.TEXT) {
            log.warn("TextNeuron received non-text message: {}", message.getType());
            return;
        }

        try {
            String text = preprocessText(message);
            
            ChannelMessage processedMessage = ChannelMessage.text(
                OUTPUT_CHANNEL,
                getId(),
                message.getTargetChannelId(),
                message.getSessionId(),
                text
            );
            processedMessage.addMetadata("original_message_id", message.getId());
            processedMessage.addMetadata("source_type", "text");
            processedMessage.addMetadata("preprocessed", true);
            
            publish(OUTPUT_CHANNEL, processedMessage);
            log.debug("Published processed text to {}", OUTPUT_CHANNEL);
            
        } catch (Exception e) {
            log.error("Failed to process text", e);
        }
    }

    private String preprocessText(ChannelMessage message) {
        Object payload = message.getPayload();
        String text = payload != null ? payload.toString() : "";
        
        text = text.trim();
        text = text.replaceAll("\\s+", " ");
        
        return text;
    }
}
