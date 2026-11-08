package com.opcua.web.dto;

import java.util.List;

public record ConnectionTestResult(
    boolean success,
    String message,
    long elapsedMs,
    String endpointUrl,
    String securityPolicy,
    String securityMode,
    String authMode,
    String serverApplicationName,
    String serverApplicationUri,
    List<EndpointView> endpoints
) {
}
