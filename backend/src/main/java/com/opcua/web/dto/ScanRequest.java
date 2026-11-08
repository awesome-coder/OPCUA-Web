package com.opcua.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScanRequest(
    String nodeId,
    @Min(value = 1, message = "扫描深度至少为 1")
    @Max(value = 15, message = "扫描深度不能超过 15")
    Integer maxDepth,
    @Min(value = 1, message = "扫描节点数至少为 1")
    @Max(value = 10_000, message = "扫描节点数不能超过 10000")
    Integer maxNodes,
    Boolean includeProperties
) {
}
