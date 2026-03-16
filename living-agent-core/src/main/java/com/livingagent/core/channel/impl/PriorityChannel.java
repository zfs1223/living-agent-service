package com.livingagent.core.channel.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(PriorityChannel.class);

    private final String id;
    private final String name;
    private final PriorityBlockingQueue<PriorityMessage> messageQueue;
    private final List<String> subscribers = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> subscriberPriorities = new ConcurrentHashMap<>();
    private volatile boolean closed = false;
    private final AtomicInteger messageCount = new AtomicInteger(0);

    public PriorityChannel(String id, String name) {
        this.id = id;
        this.name = name;
        this.messageQueue = new PriorityBlockingQueue<>(100, Comparator.comparingInt(PriorityMessage::getPriority).reversed());
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public ChannelType getType() { return ChannelType.PRIORITY; }

    @Override
    public void publish(ChannelMessage message) {
        if (closed) {
            log.warn("Channel {} is closed, cannot publish", id);
            return;
        }

        int priority = extractPriority(message);
        PriorityMessage pm = new PriorityMessage(message, priority);
        messageQueue.offer(pm);
        messageCount.incrementAndGet();
        
        log.debug("Published message {} with priority {} to channel {}", 
            message.getId(), priority, id);
    }

    @Override
    public void subscribe(String neuronId) {
        subscribe(neuronId, 0);
    }

    public void subscribe(String neuronId, int priority) {
        if (!subscribers.contains(neuronId)) {
            subscribers.add(neuronId);
            subscriberPriorities.put(neuronId, priority);
            log.debug("Neuron {} subscribed to channel {} with priority {}", neuronId, id, priority);
        }
    }

    @Override
    public void unsubscribe(String neuronId) {
        subscribers.remove(neuronId);
        subscriberPriorities.remove(neuronId);
        log.debug("Neuron {} unsubscribed from channel {}", neuronId, id);
    }

    @Override
    public List<String> getSubscribers() {
        return List.copyOf(subscribers);
    }

    public Optional<ChannelMessage> poll() {
        PriorityMessage pm = messageQueue.poll();
        if (pm != null) {
            return Optional.of(pm.message);
        }
        return Optional.empty();
    }

    public Optional<ChannelMessage> peek() {
        PriorityMessage pm = messageQueue.peek();
        if (pm != null) {
            return Optional.of(pm.message);
        }
        return Optional.empty();
    }

    public List<ChannelMessage> pollBatch(int maxCount) {
        List<ChannelMessage> batch = new ArrayList<>();
        while (batch.size() < maxCount) {
            Optional<ChannelMessage> msg = poll();
            if (msg.isEmpty()) {
                break;
            }
            batch.add(msg.get());
        }
        return batch;
    }

    public List<ChannelMessage> drainAll() {
        List<ChannelMessage> messages = new ArrayList<>();
        PriorityMessage pm;
        while ((pm = messageQueue.poll()) != null) {
            messages.add(pm.message);
        }
        return messages;
    }

    public int getQueueSize() {
        return messageQueue.size();
    }

    @Override
    public int getMessageCount() { 
        return messageCount.get(); 
    }

    @Override
    public void clear() {
        messageQueue.clear();
        subscribers.clear();
        subscriberPriorities.clear();
        messageCount.set(0);
    }

    @Override
    public void close() {
        closed = true;
        messageQueue.clear();
        subscribers.clear();
        subscriberPriorities.clear();
        log.info("PriorityChannel {} closed", id);
    }

    public void setSubscriberPriority(String neuronId, int priority) {
        if (subscribers.contains(neuronId)) {
            subscriberPriorities.put(neuronId, priority);
            log.debug("Updated priority for {} to {}", neuronId, priority);
        }
    }

    public int getSubscriberPriority(String neuronId) {
        return subscriberPriorities.getOrDefault(neuronId, 0);
    }

    private int extractPriority(ChannelMessage message) {
        Object priorityObj = message.getMetadata("priority");
        if (priorityObj instanceof Number) {
            return ((Number) priorityObj).intValue();
        }
        if (priorityObj instanceof String) {
            try {
                return Integer.parseInt((String) priorityObj);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        String type = message.getMetadata("type");
        if (type != null) {
            return switch (type.toLowerCase()) {
                case "urgent", "critical" -> 100;
                case "high" -> 75;
                case "normal" -> 50;
                case "low" -> 25;
                default -> 0;
            };
        }

        return 0;
    }

    private static class PriorityMessage {
        final ChannelMessage message;
        final int priority;
        final long timestamp;

        PriorityMessage(ChannelMessage message, int priority) {
            this.message = message;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }

        int getPriority() {
            return priority;
        }
    }
}
