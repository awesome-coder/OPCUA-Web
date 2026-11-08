package com.opcua.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchTagRequest(
    @NotEmpty(message = "至少选择一个点位")
    @Valid
    List<TagRequest> tags
) {
}
