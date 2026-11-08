package com.opcua.web.dto;

import java.util.List;

public record LiveStatusView(
    String deviceId,
    boolean active,
    int emitters,
    int monitoredTags,
    String publishingInterval,
    List<String> tagIds,
    String lastError
) {
}
