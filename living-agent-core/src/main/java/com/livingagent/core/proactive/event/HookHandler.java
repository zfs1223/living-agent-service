package com.livingagent.core.proactive.event;

public interface HookHandler {

    String[] supportedEvents();
    
    default int getOrder() {
        return 100;
    }
    
    default boolean isEnabled() {
        return true;
    }
    
    void handle(HookEvent event);
    
    default boolean canHandle(String eventType) {
        if (!isEnabled()) {
            return false;
        }
        for (String supported : supportedEvents()) {
            if (supported.equals(eventType) || supported.equals("*")) {
                return true;
            }
        }
        return false;
    }
}
