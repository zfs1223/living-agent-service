package com.livingagent.perception.mouth;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MouthNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(MouthNeuron.class);

    public static final String ID = "neuron://perception/mouth/001";
    public static final String INPUT_CHANNEL = "channel://output/speech";
    public static final String OUTPUT_CHANNEL = "channel://output/audio";

    private String ttsServiceUrl;

    public MouthNeuron() {
        super(
            ID,
            "MouthNeuron",
            "口语输出神经元 - 将文本转换为语音",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            List.of()
        );
    }

    public void setTtsServiceUrl(String url) {
        this.ttsServiceUrl = url;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("MouthNeuron started, listening to {}", INPUT_CHANNEL);
    }

    @Override
    protected void doStop() {
        log.info("MouthNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("MouthNeuron processing message: {}", message.getId());

        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            log.warn("MouthNeuron received empty text");
            return;
        }

        try {
            byte[] audioData = synthesize(text);
            
            ChannelMessage audioMessage = new ChannelMessage(
                OUTPUT_CHANNEL,
                getId(),
                message.getTargetChannelId(),
                message.getSessionId(),
                ChannelMessage.MessageType.AUDIO,
                audioData
            );
            audioMessage.addMetadata("original_message_id", message.getId());
            audioMessage.addMetadata("text_length", text.length());
            
            publish(OUTPUT_CHANNEL, audioMessage);
            log.debug("Published synthesized audio to {}", OUTPUT_CHANNEL);
            
        } catch (Exception e) {
            log.error("Failed to synthesize speech", e);
        }
    }

    private String extractText(ChannelMessage message) {
        Object payload = message.getPayload();
        if (payload instanceof String text) {
            return text;
        }
        return payload != null ? payload.toString() : null;
    }

    private byte[] synthesize(String text) {
        log.debug("Synthesizing speech for text: {} chars", text.length());
        
        if (ttsServiceUrl != null) {
            return callTtsService(text);
        }
        
        return text.getBytes();
    }

    private byte[] callTtsService(String text) {
        log.debug("Calling TTS service at {}", ttsServiceUrl);
        return text.getBytes();
    }
}
