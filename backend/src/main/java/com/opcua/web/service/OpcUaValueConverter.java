package com.opcua.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.NodeValueView;
import com.opcua.web.dto.TagValueView;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.springframework.stereotype.Component;

@Component
public class OpcUaValueConverter {

    private final ObjectMapper objectMapper;

    public OpcUaValueConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TagValueView toTagView(TagConfig tag, DataValue dataValue) {
        Variant variant = dataValue == null ? Variant.NULL_VALUE : dataValue.getValue();
        Object raw = toJson(variant == null ? null : variant.getValue());
        boolean scaling = tag.getScale() != 1.0d || tag.getOffset() != 0.0d;
        Object engineered = scaling ? applyScale(raw, tag.getScale(), tag.getOffset()) : raw;
        TagDataType actualType = inferType(variant, tag);
        StatusCode statusCode = dataValue == null ? null : dataValue.getStatusCode();
        return new TagValueView(
            tag.getId(),
            tag.getName(),
            tag.getNodeId(),
            engineered,
            raw,
            actualType.name(),
            isArray(variant == null ? null : variant.getValue()),
            quality(statusCode),
            statusCode == null ? 0 : statusCode.getValue(),
            statusName(statusCode),
            time(dataValue == null ? null : dataValue.getSourceTime()),
            time(dataValue == null ? null : dataValue.getServerTime()),
            tag.getUnit(),
            scaling,
            null
        );
    }

    public TagValueView errorTagView(TagConfig tag, String error) {
        return new TagValueView(
            tag.getId(), tag.getName(), tag.getNodeId(), null, null,
            tag.getDataType(), tag.isArray(), "BAD", 0, "Bad", null, null,
            tag.getUnit(), false, error
        );
    }

    public NodeValueView toNodeView(String nodeId, DataValue dataValue) {
        Variant variant = dataValue == null ? Variant.NULL_VALUE : dataValue.getValue();
        StatusCode statusCode = dataValue == null ? null : dataValue.getStatusCode();
        TagDataType inferred = inferFromVariant(variant);
        return new NodeValueView(
            nodeId,
            toJson(variant == null ? null : variant.getValue()),
            inferred.name(),
            isArray(variant == null ? null : variant.getValue()),
            quality(statusCode),
            statusCode == null ? 0 : statusCode.getValue(),
            statusName(statusCode),
            time(dataValue == null ? null : dataValue.getSourceTime()),
            time(dataValue == null ? null : dataValue.getServerTime()),
            null
        );
    }

    public NodeValueView errorNodeView(String nodeId, String error) {
        return new NodeValueView(nodeId, null, "AUTO", false, "BAD", 0, "Bad", null, null, error);
    }

    public Variant toVariant(String text, TagDataType type, boolean array, double scale, double offset) {
        if (type == null) {
            type = TagDataType.AUTO;
        }
        if (type == TagDataType.EXTENSION_OBJECT) {
            throw new IllegalArgumentException("结构体 / ExtensionObject 暂不支持通过 Web 写入，请使用专用接口");
        }
        if (array) {
            List<Object> elements = parseArray(text);
            if (type == TagDataType.AUTO) {
                type = inferFromElements(elements);
            }
            Class<?> component = javaTypeFor(type, true);
            Object result = Array.newInstance(component, elements.size());
            for (int i = 0; i < elements.size(); i++) {
                Array.set(result, i, convertSingle(elements.get(i), type, scale, offset));
            }
            return new Variant(result);
        }

        if (type == TagDataType.AUTO) {
            return new Variant(inferSingle(text));
        }
        return new Variant(convertSingle(text, type, scale, offset));
    }

    public Object applyScale(Object value, double scale, double offset) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(applyScale(element, scale, offset));
            }
            return result;
        }
        if (value instanceof Number number) {
            double scaled = number.doubleValue() * scale + offset;
            if (isIntegral(number) && isIntegral(scale) && isIntegral(offset)) {
                return Math.round(scaled);
            }
            return scaled;
        }
        if (value instanceof String stringValue) {
            try {
                double scaled = Double.parseDouble(stringValue) * scale + offset;
                return Math.round(scaled);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private Object inverseScale(Object value, double scale, double offset) {
        if (!(value instanceof Number number)) {
            return value;
        }
        if (scale == 0.0d) {
            throw new IllegalArgumentException("缩放系数不能为 0");
        }
        return (number.doubleValue() - offset) / scale;
    }

    private Object convertSingle(Object raw, TagDataType type, double scale, double offset) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        BigDecimal numeric = null;
        if (type.isNumeric()) {
            try {
                numeric = new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("值 '" + text + "' 不是有效的 " + type.name());
            }
            if (scale != 1.0d || offset != 0.0d) {
                numeric = numeric.subtract(BigDecimal.valueOf(offset))
                    .divide(BigDecimal.valueOf(scale), 16, RoundingMode.HALF_UP);
            }
        }
        try {
            return switch (type) {
                case BOOLEAN -> parseBoolean(text);
                case SBYTE -> numeric.setScale(0, RoundingMode.HALF_UP).byteValueExact();
                case BYTE -> UByte.valueOf(numeric.setScale(0, RoundingMode.HALF_UP).intValueExact());
                case INT16 -> numeric.setScale(0, RoundingMode.HALF_UP).shortValueExact();
                case UINT16 -> UShort.valueOf(numeric.setScale(0, RoundingMode.HALF_UP).intValueExact());
                case INT32 -> numeric.setScale(0, RoundingMode.HALF_UP).intValueExact();
                case UINT32 -> UInteger.valueOf(numeric.setScale(0, RoundingMode.HALF_UP).longValueExact());
                case INT64 -> numeric.setScale(0, RoundingMode.HALF_UP).longValueExact();
                case UINT64 -> ULong.valueOf(numeric.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact());
                case FLOAT -> numeric.floatValue();
                case DOUBLE -> numeric.doubleValue();
                case STRING -> text;
                case DATETIME -> new DateTime(parseInstant(text));
                case GUID -> UUID.fromString(text);
                case BYTESTRING -> ByteString.of(Base64.getDecoder().decode(text));
                case NODEID -> NodeId.parse(text);
                case QUALIFIED_NAME -> parseQualifiedName(text);
                case LOCALIZED_TEXT -> new LocalizedText(text);
                case EXTENSION_OBJECT -> throw new IllegalArgumentException("结构体类型不支持写入");
                case AUTO -> inferSingle(text);
            };
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("值 '" + text + "' 超出 " + type.name() + " 范围");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("值 '" + text + "' 超出 " + type.name() + " 范围");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("值 '" + text + "' 无法转换为 " + type.name() + ": " + e.getMessage());
        }
    }

    private Object inferSingle(String text) {
        if (text == null) return null;
        String value = text.trim();
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private TagDataType inferFromElements(List<Object> elements) {
        if (elements.isEmpty()) {
            return TagDataType.STRING;
        }
        return inferType(new Variant(elements.get(0)), null);
    }

    private List<Object> parseArray(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<Object>>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException("数组值必须是 JSON 数组或逗号分隔值");
            }
        }
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            result.add(part.trim());
        }
        return result;
    }

    private static boolean parseBoolean(String text) {
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "是".equals(text)
            || "on".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "否".equals(text)
            || "off".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("值 '" + text + "' 不是有效的 Boolean");
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(text));
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("时间格式无效，请使用 ISO-8601，例如 2026-01-01T00:00:00Z");
    }

    private static QualifiedName parseQualifiedName(String text) {
        if (text.startsWith("ns=")) {
            String[] parts = text.split(";", 2);
            int index = Integer.parseInt(parts[0].substring(3));
            String name = parts.length > 1 ? parts[1] : "";
            if (name.startsWith("s=")) {
                name = name.substring(2);
            }
            return new QualifiedName(index, name);
        }
        return QualifiedName.parse(text);
    }

    public Object toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UByte u) return u.intValue();
        if (value instanceof UShort u) return u.intValue();
        if (value instanceof UInteger u) return u.longValue();
        if (value instanceof ULong u) {
            if (u.longValue() >= 0) return u.longValue();
            return u.toString();
        }
        if (value instanceof Boolean || value instanceof String) return value;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof BigInteger) {
            return value;
        }
        if (value instanceof Float f) {
            return Float.isFinite(f) ? f : f.toString();
        }
        if (value instanceof Double d) {
            return Double.isFinite(d) ? d : d.toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        if (value instanceof DateTime dt) {
            return time(dt);
        }
        if (value instanceof Instant instant) {
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        }
        if (value instanceof java.util.Date date) {
            return DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
        }
        if (value instanceof ByteString bs) {
            return bs.isNull() ? null : Base64.getEncoder().encodeToString(bs.bytesOrEmpty());
        }
        if (value instanceof NodeId nodeId) {
            return nodeId.toParseableString();
        }
        if (value instanceof ExpandedNodeId nodeId) {
            return nodeId.toParseableString();
        }
        if (value instanceof QualifiedName qn) {
            return qn.toParseableString();
        }
        if (value instanceof LocalizedText lt) {
            return lt.getText();
        }
        if (value instanceof StatusCode sc) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", sc.getValue());
            map.put("name", statusName(sc));
            return map;
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof ExtensionObject extensionObject) {
            return extensionObject.toString();
        }
        if (value.getClass().isEnum()) {
            return ((Enum<?>) value).name();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(toJson(Array.get(value, i)));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(toJson(item)));
            return result;
        }
        return value.toString();
    }

    public String quality(StatusCode statusCode) {
        if (statusCode == null) return "GOOD";
        if (statusCode.isGood()) return "GOOD";
        if (statusCode.isUncertain()) return "UNCERTAIN";
        if (statusCode.isBad()) return "BAD";
        return "UNKNOWN";
    }

    public String statusName(StatusCode statusCode) {
        if (statusCode == null) {
            return "Good";
        }
        return StatusCodes.lookup(statusCode.getValue())
            .map(parts -> parts.length == 0 ? "0x" + Long.toHexString(statusCode.getValue()) : parts[0])
            .orElse("0x" + Long.toHexString(statusCode.getValue()));
    }

    public String time(DateTime dateTime) {
        if (dateTime == null || dateTime.getUtcTime() <= 0) {
            return null;
        }
        try {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(dateTime.getJavaTime()));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }

    public TagDataType inferFromVariant(Variant variant) {
        if (variant == null || variant.isNull()) {
            return TagDataType.AUTO;
        }
        return fromJavaClass(variant.getValue());
    }

    public TagDataType inferType(Variant variant, TagConfig tag) {
        TagDataType inferred = inferFromVariant(variant);
        if (inferred != TagDataType.AUTO) {
            return inferred;
        }
        if (tag != null) {
            return TagDataType.fromName(tag.getDataType()).orElse(TagDataType.AUTO);
        }
        return TagDataType.AUTO;
    }

    public TagDataType fromJavaClass(Object value) {
        if (value == null) return TagDataType.AUTO;
        Class<?> clazz = value.getClass();
        if (clazz.isArray()) {
            if (Array.getLength(value) == 0) return TagDataType.AUTO;
            return fromJavaClass(Array.get(value, 0));
        }
        if (value instanceof Boolean) return TagDataType.BOOLEAN;
        if (value instanceof Byte) return TagDataType.SBYTE;
        if (value instanceof UByte) return TagDataType.BYTE;
        if (value instanceof Short) return TagDataType.INT16;
        if (value instanceof UShort) return TagDataType.UINT16;
        if (value instanceof Integer) return TagDataType.INT32;
        if (value instanceof UInteger) return TagDataType.UINT32;
        if (value instanceof Long) return TagDataType.INT64;
        if (value instanceof ULong) return TagDataType.UINT64;
        if (value instanceof Float) return TagDataType.FLOAT;
        if (value instanceof Double) return TagDataType.DOUBLE;
        if (value instanceof String) return TagDataType.STRING;
        if (value instanceof DateTime || value instanceof Instant) return TagDataType.DATETIME;
        if (value instanceof UUID) return TagDataType.GUID;
        if (value instanceof ByteString) return TagDataType.BYTESTRING;
        if (value instanceof NodeId) return TagDataType.NODEID;
        if (value instanceof QualifiedName) return TagDataType.QUALIFIED_NAME;
        if (value instanceof LocalizedText) return TagDataType.LOCALIZED_TEXT;
        if (value instanceof ExtensionObject) return TagDataType.EXTENSION_OBJECT;
        return TagDataType.AUTO;
    }

    private static boolean isIntegral(Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer
            || number instanceof Long || number instanceof BigInteger) {
            return true;
        }
        double value = number.doubleValue();
        return value == Math.rint(value);
    }

    private Class<?> javaTypeFor(TagDataType type, boolean array) {
        return switch (type) {
            case BOOLEAN -> Boolean.class;
            case SBYTE -> Byte.class;
            case BYTE -> UByte.class;
            case INT16 -> Short.class;
            case UINT16 -> UShort.class;
            case INT32 -> Integer.class;
            case UINT32 -> UInteger.class;
            case INT64 -> Long.class;
            case UINT64 -> ULong.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case STRING -> String.class;
            case DATETIME -> DateTime.class;
            case GUID -> UUID.class;
            case BYTESTRING -> ByteString.class;
            case NODEID -> NodeId.class;
            case QUALIFIED_NAME -> QualifiedName.class;
            case LOCALIZED_TEXT -> LocalizedText.class;
            case EXTENSION_OBJECT, AUTO -> String.class;
        };
    }
}
