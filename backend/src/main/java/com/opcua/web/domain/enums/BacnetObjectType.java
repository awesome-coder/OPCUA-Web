package com.opcua.web.domain.enums;

import java.util.Arrays;

/**
 * Standard BACnet object types used by the built-in point mapping.
 * The integer values are the BACnet object-type enumeration values.
 */
public enum BacnetObjectType {
    ANALOG_INPUT(0, "AI", "Analog Input", "模拟量输入"),
    ANALOG_OUTPUT(1, "AO", "Analog Output", "模拟量输出"),
    ANALOG_VALUE(2, "AV", "Analog Value", "模拟量值"),
    BINARY_INPUT(3, "BI", "Binary Input", "二进制输入"),
    BINARY_OUTPUT(4, "BO", "Binary Output", "二进制输出"),
    BINARY_VALUE(5, "BV", "Binary Value", "二进制值"),
    MULTI_STATE_INPUT(13, "MSI", "Multi-state Input", "多态输入"),
    MULTI_STATE_OUTPUT(14, "MSO", "Multi-state Output", "多态输出"),
    MULTI_STATE_VALUE(19, "MSV", "Multi-state Value", "多态值"),
    CHARACTER_STRING_VALUE(40, "CSV", "CharacterString Value", "字符串值");

    private final int objectTypeNumber;
    private final String abbreviation;
    private final String label;
    private final String chineseLabel;

    BacnetObjectType(int objectTypeNumber, String abbreviation, String label, String chineseLabel) {
        this.objectTypeNumber = objectTypeNumber;
        this.abbreviation = abbreviation;
        this.label = label;
        this.chineseLabel = chineseLabel;
    }

    public int getObjectTypeNumber() {
        return objectTypeNumber;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getLabel() {
        return label;
    }

    public String getChineseLabel() {
        return chineseLabel;
    }

    public boolean isAnalog() {
        return this == ANALOG_INPUT || this == ANALOG_OUTPUT || this == ANALOG_VALUE;
    }

    public boolean isBinary() {
        return this == BINARY_INPUT || this == BINARY_OUTPUT || this == BINARY_VALUE;
    }

    public boolean isMultiState() {
        return this == MULTI_STATE_INPUT || this == MULTI_STATE_OUTPUT || this == MULTI_STATE_VALUE;
    }

    public boolean isWritableObjectType() {
        return this == ANALOG_OUTPUT || this == ANALOG_VALUE
            || this == BINARY_OUTPUT || this == BINARY_VALUE
            || this == MULTI_STATE_OUTPUT || this == MULTI_STATE_VALUE
            || this == CHARACTER_STRING_VALUE;
    }

    public static BacnetObjectType fromName(String value) {
        if (value == null || value.isBlank() || "AUTO".equalsIgnoreCase(value)) {
            return null;
        }
        return Arrays.stream(values())
            .filter(v -> v.name().equalsIgnoreCase(value.trim())
                || v.abbreviation.equalsIgnoreCase(value.trim())
                || v.label.equalsIgnoreCase(value.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的 BACnet 对象类型: " + value));
    }
}
