package com.opcua.web.dto;

import java.util.List;

public record BatchTagResult(
    List<TagView> created,
    List<String> skipped,
    List<String> errors
) {
}
