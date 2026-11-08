package com.opcua.web.dto;

public record PkiInfoView(
    String subject,
    String issuer,
    String serialNumber,
    String notBefore,
    String notAfter,
    String applicationUri,
    String sha256Fingerprint,
    String sha1Thumbprint,
    String keyAlgorithm,
    int keySize,
    String certificatePem
) {
}
