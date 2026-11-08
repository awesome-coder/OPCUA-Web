package com.opcua.web.domain.enums;

import java.util.Arrays;

public enum SecurityPolicyOption {
    NONE("None", "无加密", "不启用 OPC UA 消息安全，仅建议用于隔离网络调试。"),
    BASIC128RSA15("Basic128Rsa15", "Basic128Rsa15", "旧版安全策略（SHA-1），仅用于兼容老设备。"),
    BASIC256("Basic256", "Basic256", "AES-256 对称加密，SHA-1 签名，兼容老设备。"),
    BASIC256SHA256("Basic256Sha256", "Basic256Sha256", "推荐的通用安全策略，AES-256 + SHA-256。"),
    AES128_SHA256_RSAOAEP("Aes128_Sha256_RsaOaep", "Aes128_Sha256_RsaOaep", "较新的 OPC UA 安全策略。"),
    AES256_SHA256_RSAPSS("Aes256_Sha256_RsaPss", "Aes256_Sha256_RsaPss", "高安全等级，使用 RSA-PSS。");

    private final String miloName;
    private final String label;
    private final String description;

    SecurityPolicyOption(String miloName, String label, String description) {
        this.miloName = miloName;
        this.label = label;
        this.description = description;
    }

    public String getMiloName() {
        return miloName;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static SecurityPolicyOption fromMiloName(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return Arrays.stream(values())
            .filter(v -> v.miloName.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的安全策略: " + value));
    }
}
