package com.opcua.web.dto;

import java.time.Instant;

public record DeviceStatusView(
    String deviceId,
    String state,
    boolean connected,
    String endpointUrl,
    String activeSecurityPolicy,
    String activeSecurityMode,
    String serverApplicationName,
    Instant connectedAt,
    String lastError,
    int subscriptionCount
) {
}
