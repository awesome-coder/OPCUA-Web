package com.opcua.web.dto;

import java.util.List;

public record ScanResultView(
    String rootNodeId,
    int scannedObjects,
    int scannedVariables,
    boolean truncated,
    long elapsedMs,
    List<BrowseNodeView> tags,
    List<String> warnings
) {
}
