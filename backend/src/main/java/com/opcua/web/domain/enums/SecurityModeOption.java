package com.opcua.web.domain.enums;

import java.util.Arrays;

public enum SecurityModeOption {
    NONE("None", "None", "不签名、不加密"),
    SIGN("Sign", "Sign", "消息签名，不加密"),
    SIGN_AND_ENCRYPT("SignAndEncrypt", "SignAndEncrypt", "消息签名并加密");

    private final String miloName;
    private final String label;
    private final String description;

    SecurityModeOption(String miloName, String label, String description) {
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

    public static SecurityModeOption fromMiloName(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return Arrays.stream(values())
            .filter(v -> v.miloName.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的安全模式: " + value));
    }
}
