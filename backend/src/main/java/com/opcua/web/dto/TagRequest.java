package com.opcua.web.dto;

import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.TagDataType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TagRequest(
    @NotBlank(message = "设备 ID 不能为空")
    String deviceId,
    @NotBlank(message = "点位名称不能为空")
    @Size(max = 120, message = "点位名称不能超过 120 个字符")
    String name,
    @NotBlank(message = "NodeId 不能为空")
    @Size(max = 500, message = "NodeId 过长")
    String nodeId,
    @NotNull(message = "数据类型不能为空")
    TagDataType dataType,
    Boolean array,
    @NotNull(message = "访问方式不能为空")
    AccessMode accessMode,
    @Size(max = 60, message = "工程单位过长")
    String unit,
    @Size(max = 500, message = "描述不能超过 500 个字符")
    String description,
    @DecimalMin(value = "-1e12", message = "缩放系数超出范围")
    @DecimalMax(value = "1e12", message = "缩放系数超出范围")
    Double scale,
    @DecimalMin(value = "-1e12", message = "偏移量超出范围")
    @DecimalMax(value = "1e12", message = "偏移量超出范围")
    Double offset,
    @DecimalMin(value = "0", message = "死区不能小于 0")
    Double deadband,
    @Min(value = 0, message = "采样周期不能小于 0")
    @Max(value = 3_600_000, message = "采样周期不能大于 3600000ms")
    Integer samplingIntervalMs,
    @Min(value = 1, message = "队列长度至少为 1")
    @Max(value = 1000, message = "队列长度不能大于 1000")
    Integer queueSize,
    String enumValues,
    String bacnetObjectType,
    @Min(value = 0, message = "BACnet 实例号不能小于 0")
    @Max(value = 4_194_302, message = "BACnet 实例号超出范围")
    Integer bacnetInstance,
    String bacnetProperty,
    Double covIncrement,
    Boolean enabled
) {
}
