package com.opcua.web.dto;

public record TagValueView(
    String tagId,
    String tagName,
    String nodeId,
    Object value,
    Object rawValue,
    String dataType,
    boolean array,
    String quality,
    long statusCode,
    String statusCodeName,
    String sourceTime,
    String serverTime,
    String unit,
    boolean scaled,
    String error
) {
}
