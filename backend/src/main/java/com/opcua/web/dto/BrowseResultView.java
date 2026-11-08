package com.opcua.web.dto;

import java.util.List;

public record BrowseResultView(
    String parentNodeId,
    String parentDisplayName,
    List<BrowseNodeView> nodes
) {
}
