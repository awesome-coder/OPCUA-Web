package com.opcua.web.dto;

public record ExcelImportResult(
    int devicesCreated,
    int devicesUpdated,
    int devicesSkipped,
    int tagsCreated,
    int tagsUpdated,
    int tagsSkipped,
    int errorCount,
    java.util.List<ExcelImportError> errors,
    String message
) {
    public record ExcelImportError(String sheet, int row, String column, String message) {
    }
}
