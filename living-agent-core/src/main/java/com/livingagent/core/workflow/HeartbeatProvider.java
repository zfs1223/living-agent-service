package com.livingagent.core.workflow;

public interface HeartbeatProvider {

    String getProviderId();

    void startHeartbeat(HeartbeatCallback callback);

    void stopHeartbeat();

    interface HeartbeatCallback {
        void onHeartbeat(String providerId);
    }
}
