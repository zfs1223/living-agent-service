package com.livingagent.core.channel.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class UnicastChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(UnicastChannel.class);

    private final String id;
    private final String name;
    private final List<String> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private volatile boolean closed = false;
    private int messageCount = 0;

    public UnicastChannel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public ChannelType getType() { return ChannelType.UNICAST; }

    @Override
    public void publish(ChannelMessage message) {
        if (closed) {
            log.warn("Channel {} is closed, cannot publish", id);
            return;
        }
        
        if (subscribers.isEmpty()) {
            log.warn("No subscribers for channel {}", id);
            return;
        }
        
        messageCount++;
        String targetNeuron = subscribers.get(0);
        log.debug("Unicast message {} to neuron {}", message.getId(), targetNeuron);
    }

    @Override
    public void subscribe(String neuronId) {
        if (!subscribers.contains(neuronId)) {
            subscribers.add(neuronId);
            log.debug("Neuron {} subscribed to channel {}", neuronId, id);
        }
    }

    @Override
    public void unsubscribe(String neuronId) {
        subscribers.remove(neuronId);
        log.debug("Neuron {} unsubscribed from channel {}", neuronId, id);
    }

    @Override
    public List<String> getSubscribers() {
        return List.copyOf(subscribers);
    }

    @Override
    public int getMessageCount() { return messageCount; }

    @Override
    public void clear() {
        subscribers.clear();
        messageCount = 0;
        roundRobinIndex.set(0);
    }

    @Override
    public void close() {
        closed = true;
        subscribers.clear();
        log.info("Channel {} closed", id);
    }
}
