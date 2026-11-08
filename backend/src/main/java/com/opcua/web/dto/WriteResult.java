package com.opcua.web.dto;

public record WriteResult(
    boolean success,
    String nodeId,
    String tagId,
    Object value,
    String quality,
    long statusCode,
    String statusCodeName,
    String message
) {
}
