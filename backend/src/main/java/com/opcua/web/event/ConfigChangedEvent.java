package com.opcua.web.event;

public record ConfigChangedEvent(String deviceId, ChangeType type) {

    public enum ChangeType {
        DEVICE_CREATED,
        DEVICE_UPDATED,
        DEVICE_DELETED,
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED,
        TAGS_CHANGED
    }
}
