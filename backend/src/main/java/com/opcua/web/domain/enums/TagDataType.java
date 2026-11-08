package com.opcua.web.domain.enums;

import java.util.Arrays;
import java.util.Optional;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Data types supported by the tag model. AUTO keeps the native OPC UA type and lets
 * the server decide the write type (resolved by reading the DataType attribute).
 */
public enum TagDataType {
    AUTO("自动（保持 OPC UA 原生类型）", null, false, false),
    BOOLEAN("Boolean", Boolean.class, false, false),
    SBYTE("SByte", Byte.class, true, true),
    BYTE("Byte (UInt8)", Short.class, true, true),
    INT16("Int16", Short.class, true, true),
    UINT16("UInt16", Integer.class, true, true),
    INT32("Int32", Integer.class, true, true),
    UINT32("UInt32", Long.class, true, true),
    INT64("Int64", Long.class, true, true),
    UINT64("UInt64", java.math.BigInteger.class, true, true),
    FLOAT("Float", Float.class, true, false),
    DOUBLE("Double", Double.class, true, false),
    STRING("String", String.class, false, false),
    DATETIME("DateTime", java.time.Instant.class, false, false),
    GUID("Guid", java.util.UUID.class, false, false),
    BYTESTRING("ByteString", byte[].class, false, false),
    NODEID("NodeId", NodeId.class, false, false),
    QUALIFIED_NAME("QualifiedName", org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName.class, false, false),
    LOCALIZED_TEXT("LocalizedText", org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText.class, false, false),
    EXTENSION_OBJECT("ExtensionObject / 结构体", String.class, false, false);

    private final String label;
    private final Class<?> javaType;
    private final boolean numeric;
    private final boolean integer;

    TagDataType(String label, Class<?> javaType, boolean numeric, boolean integer) {
        this.label = label;
        this.javaType = javaType;
        this.numeric = numeric;
        this.integer = integer;
    }

    public String getLabel() {
        return label;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public boolean isNumeric() {
        return numeric;
    }

    public boolean isInteger() {
        return integer;
    }

    public static TagDataType fromNodeId(NodeId nodeId) {
        if (nodeId == null || nodeId.isNull()) {
            return AUTO;
        }
        if (nodeId.equals(Identifiers.Boolean)) return BOOLEAN;
        if (nodeId.equals(Identifiers.SByte)) return SBYTE;
        if (nodeId.equals(Identifiers.Byte)) return BYTE;
        if (nodeId.equals(Identifiers.Int16)) return INT16;
        if (nodeId.equals(Identifiers.UInt16)) return UINT16;
        if (nodeId.equals(Identifiers.Int32)) return INT32;
        if (nodeId.equals(Identifiers.UInt32)) return UINT32;
        if (nodeId.equals(Identifiers.Int64)) return INT64;
        if (nodeId.equals(Identifiers.UInt64)) return UINT64;
        if (nodeId.equals(Identifiers.Float)) return FLOAT;
        if (nodeId.equals(Identifiers.Double)) return DOUBLE;
        if (nodeId.equals(Identifiers.String)) return STRING;
        if (nodeId.equals(Identifiers.DateTime)) return DATETIME;
        if (nodeId.equals(Identifiers.Guid)) return GUID;
        if (nodeId.equals(Identifiers.ByteString)) return BYTESTRING;
        if (nodeId.equals(Identifiers.NodeId)) return NODEID;
        if (nodeId.equals(Identifiers.QualifiedName)) return QUALIFIED_NAME;
        if (nodeId.equals(Identifiers.LocalizedText)) return LOCALIZED_TEXT;
        if (nodeId.equals(Identifiers.Enumeration)) return INT32;
        if (nodeId.equals(Identifiers.Structure)) {
            return EXTENSION_OBJECT;
        }
        return AUTO;
    }

    public static Optional<TagDataType> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(v -> v.name().equalsIgnoreCase(name.trim()))
            .findFirst();
    }

    public static String displayName(NodeId nodeId) {
        if (nodeId == null || nodeId.isNull()) {
            return "未知";
        }
        String[] names = {
            "Boolean", "SByte", "Byte", "Int16", "UInt16", "Int32", "UInt32", "Int64",
            "UInt64", "Float", "Double", "String", "DateTime", "Guid", "ByteString",
            "XmlElement", "NodeId", "ExpandedNodeId", "StatusCode", "QualifiedName",
            "LocalizedText", "Structure", "DataValue", "BaseDataType", "DiagnosticInfo",
            "Number", "Integer", "UInteger", "Enumeration"
        };
        int id = -1;
        Object identifier = nodeId.getIdentifier();
        if (identifier instanceof Number n) {
            id = n.intValue();
        } else if (identifier instanceof org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger u) {
            id = u.intValue();
        } else if (identifier instanceof org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort u) {
            id = u.intValue();
        }
        if (nodeId.getNamespaceIndex().intValue() == 0 && id >= 1 && id <= names.length) {
            return names[id - 1];
        }
        return nodeId.toParseableString();
    }
}
