package com.opcua.web.dto;

public record NodeValueView(
    String nodeId,
    Object value,
    String dataType,
    boolean array,
    String quality,
    long statusCode,
    String statusCodeName,
    String sourceTime,
    String serverTime,
    String error
) {
}
