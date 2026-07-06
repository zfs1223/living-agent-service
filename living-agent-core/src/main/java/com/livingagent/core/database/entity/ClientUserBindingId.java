package com.livingagent.core.database.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * 复合主键类，用于 ClientUserBindingEntity
 */
public class ClientUserBindingId implements Serializable {

    private String clientId;
    private String userId;

    public ClientUserBindingId() {
    }

    public ClientUserBindingId(String clientId, String userId) {
        this.clientId = clientId;
        this.userId = userId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientUserBindingId that = (ClientUserBindingId) o;
        return Objects.equals(clientId, that.clientId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, userId);
    }
}
