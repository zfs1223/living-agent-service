package com.livingagent.core.channel.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(RoundRobinChannel.class);

    private final String id;
    private final String name;
    private final List<String> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicInteger index = new AtomicInteger(0);
    private volatile boolean closed = false;
    private int messageCount = 0;

    public RoundRobinChannel(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public ChannelType getType() { return ChannelType.ROUND_ROBIN; }

    @Override
    public void publish(ChannelMessage message) {
        if (closed || subscribers.isEmpty()) {
            return;
        }
        
        messageCount++;
        int current = Math.abs(index.getAndIncrement() % subscribers.size());
        String target = subscribers.get(current);
        log.debug("Round-robin message {} to neuron {}", message.getId(), target);
    }

    @Override
    public void subscribe(String neuronId) {
        if (!subscribers.contains(neuronId)) {
            subscribers.add(neuronId);
        }
    }

    @Override
    public void unsubscribe(String neuronId) {
        subscribers.remove(neuronId);
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
        index.set(0);
    }

    @Override
    public void close() {
        closed = true;
        subscribers.clear();
    }
}
