package com.livingagent.core.channel;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ChannelMessageQueue {

    private final String channelId;
    private final BlockingQueue<ChannelMessage> queue;
    private final int capacity;
    private QueueMode mode;

    public enum QueueMode {
        INTERRUPT,
        STEER,
        FOLLOWUP,
        COLLECT,
        DROP
    }

    public ChannelMessageQueue(String channelId, int capacity) {
        this.channelId = channelId;
        this.capacity = capacity;
        this.queue = new java.util.concurrent.LinkedBlockingQueue<>(capacity);
        this.mode = QueueMode.FOLLOWUP;
    }

    public String getChannelId() { return channelId; }

    public int getCapacity() { return capacity; }

    public QueueMode getMode() { return mode; }
    public void setMode(QueueMode mode) { this.mode = mode; }

    public boolean enqueue(ChannelMessage message) {
        return queue.offer(message);
    }
    
    public boolean offer(ChannelMessage message) {
        return queue.offer(message);
    }

    public boolean enqueue(ChannelMessage message, Duration timeout) throws InterruptedException {
        return queue.offer(message, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ChannelMessage dequeue() throws InterruptedException {
        return queue.take();
    }
    
    public ChannelMessage poll() {
        return queue.poll();
    }
    
    public ChannelMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public ChannelMessage dequeue(Duration timeout) throws InterruptedException {
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ChannelMessage peek() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public boolean isFull() {
        return queue.remainingCapacity() == 0;
    }

    public void clear() {
        queue.clear();
    }

    public List<ChannelMessage> drainAll() {
        List<ChannelMessage> messages = new java.util.ArrayList<>();
        queue.drainTo(messages);
        return messages;
    }
}
