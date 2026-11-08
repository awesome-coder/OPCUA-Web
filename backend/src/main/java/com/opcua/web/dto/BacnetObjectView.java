package com.opcua.web.dto;

import java.util.List;

public record BacnetObjectView(
    String tagId,
    String tagName,
    String nodeId,
    String dataType,
    boolean array,
    String accessMode,
    String objectType,
    int objectTypeNumber,
    String objectTypeLabel,
    int instanceNumber,
    String objectId,
    String property,
    String objectName,
    String description,
    String units,
    Double covIncrement,
    Object presentValue,
    String mappingSource,
    List<String> warnings
) {
}
