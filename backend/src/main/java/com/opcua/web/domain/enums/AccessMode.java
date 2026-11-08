package com.opcua.web.domain.enums;

public enum AccessMode {
    READ_ONLY("只读"),
    READ_WRITE("读写"),
    WRITE_ONLY("只写");

    private final String label;

    AccessMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canRead() {
        return this != WRITE_ONLY;
    }

    public boolean canWrite() {
        return this != READ_ONLY;
    }
}
