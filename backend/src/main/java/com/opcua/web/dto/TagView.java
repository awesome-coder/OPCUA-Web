package com.opcua.web.dto;

import java.time.Instant;

public record TagView(
    String id,
    String deviceId,
    String deviceName,
    String name,
    String nodeId,
    String dataType,
    boolean array,
    String accessMode,
    boolean writable,
    String unit,
    String description,
    double scale,
    double offset,
    double deadband,
    int samplingIntervalMs,
    int queueSize,
    String enumValues,
    String bacnetObjectType,
    Integer bacnetInstance,
    String bacnetProperty,
    Double covIncrement,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
