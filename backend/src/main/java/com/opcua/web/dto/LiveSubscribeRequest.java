package com.opcua.web.dto;

public record LiveSubscribeRequest(
    java.util.List<String> tagIds
) {
}
