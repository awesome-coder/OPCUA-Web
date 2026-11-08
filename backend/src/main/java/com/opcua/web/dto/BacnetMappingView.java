package com.opcua.web.dto;

import java.util.List;

public record BacnetMappingView(
    String deviceId,
    String deviceName,
    int objectCount,
    List<BacnetObjectView> objects,
    List<String> warnings,
    String integrationStatus
) {
}
