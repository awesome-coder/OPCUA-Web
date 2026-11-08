package com.opcua.web.dto;

import com.opcua.web.domain.enums.TagDataType;
import jakarta.validation.constraints.NotNull;

public record WriteRequest(
    String tagId,
    String nodeId,
    TagDataType dataType,
    Boolean array,
    Double scale,
    Double offset,
    @NotNull(message = "写入值不能为空")
    String value
) {
}
