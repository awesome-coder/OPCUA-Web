package com.opcua.web.dto;

import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    String name,
    @NotBlank(message = "OPC UA 端点地址不能为空")
    @Size(max = 500, message = "端点地址过长")
    String endpointUrl,
    @NotNull(message = "安全策略不能为空")
    SecurityPolicyOption securityPolicy,
    @NotNull(message = "安全模式不能为空")
    SecurityModeOption securityMode,
    @NotNull(message = "认证方式不能为空")
    AuthMode authMode,
    @Size(max = 100, message = "用户名过长")
    String username,
    String password,
    Boolean trustAllCertificates,
    String serverCertificatePem,
    @Min(value = 1000, message = "连接超时不能小于 1000ms")
    @Max(value = 120_000, message = "连接超时不能大于 120000ms")
    Integer connectTimeoutMs,
    @Min(value = 1000, message = "请求超时不能小于 1000ms")
    @Max(value = 120_000, message = "请求超时不能大于 120000ms")
    Integer requestTimeoutMs,
    @Min(value = 10_000, message = "会话超时不能小于 10000ms")
    @Max(value = 3_600_000, message = "会话超时不能大于 3600000ms")
    Integer sessionTimeoutMs,
    @Min(value = 100, message = "发布周期不能小于 100ms")
    @Max(value = 600_000, message = "发布周期不能大于 600000ms")
    Integer publishingIntervalMs,
    Boolean enabled,
    @Size(max = 500, message = "描述不能超过 500 个字符")
    String description
) {
}
