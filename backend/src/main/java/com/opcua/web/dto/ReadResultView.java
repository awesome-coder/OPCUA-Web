package com.opcua.web.dto;

import java.util.List;

public record ReadResultView(
    String deviceId,
    List<TagValueView> tags,
    List<NodeValueView> nodes,
    String error
) {
}
