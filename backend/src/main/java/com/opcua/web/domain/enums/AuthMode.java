package com.opcua.web.domain.enums;

public enum AuthMode {
    ANONYMOUS("匿名", "无需用户名和密码"),
    USERNAME("用户名 / 密码", "使用 OPC UA UserNameIdentityToken 认证"),
    CERTIFICATE("客户端证书", "使用 X509 用户身份令牌认证");

    private final String label;
    private final String description;

    AuthMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
