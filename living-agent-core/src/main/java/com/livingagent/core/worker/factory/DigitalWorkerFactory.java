package com.livingagent.core.worker.factory;

import com.livingagent.core.worker.DigitalWorker;
import com.livingagent.core.worker.template.WorkerTemplate;

import java.util.Map;
import java.util.Optional;

public interface DigitalWorkerFactory {

    DigitalWorker createWorker(WorkerTemplate template);
    
    DigitalWorker createWorker(WorkerTemplate template, Map<String, Object> overrides);
    
    DigitalWorker createWorker(String templateId);
    
    DigitalWorker createWorker(String templateId, Map<String, Object> overrides);
    
    Optional<DigitalWorker> getWorker(String workerId);
    
    void destroyWorker(String workerId);
    
    boolean workerExists(String workerId);
    
    void registerTemplate(WorkerTemplate template);
    
    void unregisterTemplate(String templateId);
    
    Optional<WorkerTemplate> getTemplate(String templateId);
    
    Map<String, WorkerTemplate> getAvailableTemplates();
    
    WorkerCreationResult validateCreation(WorkerTemplate template, Map<String, Object> overrides);
    
    record WorkerCreationResult(
        boolean success,
        String workerId,
        String errorMessage,
        Map<String, String> validationErrors
    ) {
        public static WorkerCreationResult success(String workerId) {
            return new WorkerCreationResult(true, workerId, null, Map.of());
        }
        
        public static WorkerCreationResult failure(String errorMessage) {
            return new WorkerCreationResult(false, null, errorMessage, Map.of());
        }
        
        public static WorkerCreationResult validationFailed(Map<String, String> errors) {
            return new WorkerCreationResult(false, null, "Validation failed", errors);
        }
    }
}
