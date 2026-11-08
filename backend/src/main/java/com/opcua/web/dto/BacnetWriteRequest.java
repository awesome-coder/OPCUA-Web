package com.opcua.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BacnetWriteRequest(
    @NotBlank(message = "deviceId 不能为空")
    String deviceId,
    @NotBlank(message = "objectType 不能为空")
    String objectType,
    Integer instance,
    @NotBlank(message = "property 不能为空")
    String property,
    @NotBlank(message = "value 不能为空")
    String value
) {
}
