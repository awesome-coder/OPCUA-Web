package com.opcua.web.dto;

import java.time.Instant;

public record DeviceView(
    String id,
    String name,
    String description,
    String endpointUrl,
    String securityPolicy,
    String securityMode,
    String authMode,
    String username,
    boolean hasPassword,
    boolean trustAllCertificates,
    boolean hasServerCertificate,
    int connectTimeoutMs,
    int requestTimeoutMs,
    int sessionTimeoutMs,
    int publishingIntervalMs,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    boolean connected,
    String connectionState,
    String lastError,
    Instant connectedAt,
    int tagCount
) {
}
