package com.opcua.web.dto;

import java.util.List;

public record BrowseNodeView(
    String nodeId,
    String browseName,
    String displayName,
    String nodeClass,
    String dataType,
    String dataTypeName,
    String dataTypeCode,
    Integer valueRank,
    boolean array,
    Integer accessLevel,
    boolean writable,
    String description,
    String typeDefinition,
    boolean hasChildren,
    String path
) {
}
