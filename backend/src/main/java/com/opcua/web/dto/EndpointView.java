package com.opcua.web.dto;

import java.time.Instant;
import java.util.List;

public record EndpointView(
    String endpointUrl,
    String securityPolicy,
    String securityMode,
    int securityLevel,
    List<String> userTokenTypes,
    String serverApplicationName,
    String serverApplicationUri,
    String serverProductUri,
    String certificateFingerprintSha256,
    String serverCertificatePem,
    String transportProfileUri
) {
}
