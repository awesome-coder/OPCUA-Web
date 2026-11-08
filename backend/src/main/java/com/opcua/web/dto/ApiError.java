package com.opcua.web.dto;

public record ApiError(
    int status,
    String code,
    String message,
    java.util.Map<String, Object> details,
    String path,
    String timestamp
) {
}
