package com.livingagent.perception.ear;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import com.livingagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EarNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(EarNeuron.class);

    public static final String ID = "neuron://perception/ear/001";
    public static final String INPUT_CHANNEL = "channel://input/audio";
    public static final String OUTPUT_CHANNEL = "channel://perception/text";

    private String asrServiceUrl;

    public EarNeuron() {
        super(
            ID,
            "EarNeuron",
            "听觉感知神经元 - 将语音转换为文本",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            List.of()
        );
    }

    public void setAsrServiceUrl(String url) {
        this.asrServiceUrl = url;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("EarNeuron started, listening to {}", INPUT_CHANNEL);
    }

    @Override
    protected void doStop() {
        log.info("EarNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("EarNeuron processing message: {}", message.getId());

        if (message.getType() != ChannelMessage.MessageType.AUDIO) {
            log.warn("EarNeuron received non-audio message: {}", message.getType());
            return;
        }

        try {
            String text = transcribe(message);
            
            ChannelMessage textMessage = ChannelMessage.text(
                OUTPUT_CHANNEL,
                getId(),
                message.getTargetChannelId(),
                message.getSessionId(),
                text
            );
            textMessage.addMetadata("original_message_id", message.getId());
            textMessage.addMetadata("source_type", "audio");
            
            publish(OUTPUT_CHANNEL, textMessage);
            log.debug("Published transcribed text to {}", OUTPUT_CHANNEL);
            
        } catch (Exception e) {
            log.error("Failed to transcribe audio", e);
            publishError(message, "ASR failed: " + e.getMessage());
        }
    }

    private String transcribe(ChannelMessage audioMessage) {
        Object payload = audioMessage.getPayload();
        
        if (payload instanceof String text) {
            return text;
        }
        
        if (asrServiceUrl != null) {
            return callAsrService(payload);
        }
        
        return payload.toString();
    }

    private String callAsrService(Object audioData) {
        log.debug("Calling ASR service at {}", asrServiceUrl);
        return audioData.toString();
    }

    private void publishError(ChannelMessage original, String error) {
        ChannelMessage errorMessage = ChannelMessage.error(
            OUTPUT_CHANNEL,
            getId(),
            original.getTargetChannelId(),
            original.getSessionId(),
            error
        );
        publish(OUTPUT_CHANNEL, errorMessage);
    }
}
