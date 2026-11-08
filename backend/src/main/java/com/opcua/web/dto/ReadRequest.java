package com.opcua.web.dto;

public record ReadRequest(
    java.util.List<String> tagIds,
    java.util.List<String> nodeIds
) {
}
