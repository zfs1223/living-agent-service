package com.livingagent.core.neuron.impl;

import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NeuronRegistryImpl implements NeuronRegistry {

    private static final Logger log = LoggerFactory.getLogger(NeuronRegistryImpl.class);

    private final Map<String, Neuron> neurons = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> channelSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, String> employeeIdIndex = new ConcurrentHashMap<>();

    @Override
    public void register(Neuron neuron) {
        String neuronId = normalizeNeuronId(neuron.getId());
        if (neurons.containsKey(neuronId)) {
            log.warn("Neuron {} already registered, replacing", neuronId);
        }
        neurons.put(neuronId, neuron);
        
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);
        employeeIdIndex.put(employeeId, neuronId);
        
        for (String channelId : neuron.getSubscribedChannels()) {
            channelSubscriptions.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
                .add(neuronId);
        }
        log.info("Registered neuron: {} [employee: {}, channels: {}]", 
            neuronId, employeeId, neuron.getSubscribedChannels());
    }
    
    private String normalizeNeuronId(String id) {
        if (IdUtils.isNeuronId(id)) {
            return id;
        }
        if (IdUtils.isDigitalEmployeeId(id)) {
            return IdUtils.employeeToNeuronId(id);
        }
        if (id.contains("://")) {
            throw new IllegalArgumentException("Invalid neuron ID format: " + id);
        }
        String[] parts = id.split("/");
        if (parts.length == 3) {
            return IdUtils.generateNeuronId(parts[0], parts[1], parts[2]);
        }
        throw new IllegalArgumentException("Cannot normalize neuron ID: " + id);
    }

    @Override
    public void unregister(String neuronId) {
        String normalizedId = normalizeNeuronId(neuronId);
        Neuron neuron = neurons.remove(normalizedId);
        if (neuron != null) {
            for (String channelId : neuron.getSubscribedChannels()) {
                Set<String> subscribers = channelSubscriptions.get(channelId);
                if (subscribers != null) {
                    subscribers.remove(normalizedId);
                }
            }
            String employeeId = employeeIdIndex.remove(normalizedId);
            log.info("Unregistered neuron: {} [employee: {}]", normalizedId, employeeId);
        }
    }

    @Override
    public Optional<Neuron> get(String neuronId) {
        String normalizedId = normalizeNeuronId(neuronId);
        return Optional.ofNullable(neurons.get(normalizedId));
    }
    
    public Optional<Neuron> getByEmployeeId(String employeeId) {
        String neuronId = employeeIdIndex.get(employeeId);
        if (neuronId != null) {
            return Optional.ofNullable(neurons.get(neuronId));
        }
        if (IdUtils.isDigitalEmployeeId(employeeId)) {
            String convertedId = IdUtils.employeeToNeuronId(employeeId);
            return Optional.ofNullable(neurons.get(convertedId));
        }
        return Optional.empty();
    }

    @Override
    public List<Neuron> getAll() {
        return new ArrayList<>(neurons.values());
    }

    @Override
    public List<Neuron> getByChannel(String channelId) {
        Set<String> subscriberIds = channelSubscriptions.get(channelId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return Collections.emptyList();
        }
        return subscriberIds.stream()
            .map(neurons::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<Neuron> getByState(NeuronState state) {
        return neurons.values().stream()
            .filter(n -> n.getState() == state)
            .toList();
    }

    @Override
    public boolean exists(String neuronId) {
        String normalizedId = normalizeNeuronId(neuronId);
        return neurons.containsKey(normalizedId);
    }
    
    public boolean existsByEmployeeId(String employeeId) {
        return getByEmployeeId(employeeId).isPresent();
    }

    @Override
    public int count() {
        return neurons.size();
    }

    @Override
    public void startAll() {
        log.info("Starting all neurons...");
        neurons.values().forEach(neuron -> {
            try {
                NeuronContext context = new NeuronContext(
                    neuron.getId(),
                    neuron.getSubscribedChannels().isEmpty() ? null : neuron.getSubscribedChannels().get(0),
                    (String) null,
                    (com.livingagent.core.channel.ChannelMessageQueue) null
                );
                neuron.start(context);
            } catch (Exception e) {
                log.error("Failed to start neuron: {}", neuron.getId(), e);
            }
        });
    }

    @Override
    public void stopAll() {
        log.info("Stopping all neurons...");
        neurons.values().forEach(neuron -> {
            try {
                neuron.stop();
            } catch (Exception e) {
                log.error("Failed to stop neuron: {}", neuron.getId(), e);
            }
        });
    }

    @Override
    public void healthCheck() {
        log.debug("Health check for {} neurons", neurons.size());
        neurons.values().forEach(neuron -> {
            NeuronState state = neuron.getState();
            if (state == NeuronState.ERROR) {
                log.warn("Neuron {} is in ERROR state", neuron.getId());
            }
        });
    }
}
