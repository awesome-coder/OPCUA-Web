package com.opcua.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.TagValueView;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpcUaValueConverterTest {

    private OpcUaValueConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OpcUaValueConverter(new ObjectMapper());
    }

    @Test
    void appliesEngineeringScaleAndOffsetOnRead() {
        TagConfig tag = tag("Temperature", "FLOAT");
        tag.setScale(0.1);
        tag.setOffset(2.0);

        TagValueView result = converter.toTagView(tag, DataValue.valueOnly(new Variant(10.0f)));

        assertThat(result.rawValue()).isEqualTo(10.0f);
        assertThat(result.value()).isEqualTo(3.0);
        assertThat(result.scaled()).isTrue();
        assertThat(result.quality()).isEqualTo("GOOD");
    }

    @Test
    void invertsScaleForWrites() {
        Variant variant = converter.toVariant("25.5", TagDataType.FLOAT, false, 2.0, 5.0);

        assertThat(variant.getValue()).isEqualTo(10.25f);
    }

    @Test
    void writesIntegerArraysFromJson() {
        Variant variant = converter.toVariant("[1,2,3]", TagDataType.INT32, true, 1.0, 0.0);

        assertThat(variant.getValue()).isInstanceOf(Integer[].class);
        assertThat((Integer[]) variant.getValue()).containsExactly(1, 2, 3);
    }

    @Test
    void convertsDateTimeAndByteString() {
        Variant dateVariant = converter.toVariant("2026-01-02T03:04:05Z", TagDataType.DATETIME, false, 1, 0);
        assertThat(dateVariant.getValue()).isInstanceOf(DateTime.class);

        String encoded = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        Variant bytesVariant = converter.toVariant(encoded, TagDataType.BYTESTRING, false, 1, 0);
        assertThat(((ByteString) bytesVariant.getValue()).bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void supportsUInt64AboveLongRange() {
        Variant variant = converter.toVariant("18446744073709551615", TagDataType.UINT64, false, 1, 0);
        ULong value = (ULong) variant.getValue();
        assertThat(value.toString()).isEqualTo("18446744073709551615");
    }

    @Test
    void rejectsOutOfRangeAndInvalidValues() {
        assertThatThrownBy(() -> converter.toVariant("300", TagDataType.BYTE, false, 1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("范围");
        assertThatThrownBy(() -> converter.toVariant("nan", TagDataType.DOUBLE, false, 1, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.toVariant("[1,2", TagDataType.INT32, true, 1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数组");
    }

    @Test
    void serializesOpcUaBuiltinsToJson() {
        assertThat(converter.toJson(UInteger.valueOf(42))).isEqualTo(42L);
        assertThat(converter.toJson(ByteString.of(new byte[]{1, 2, 3})))
            .isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        assertThat(converter.toJson(new DateTime(Instant.parse("2026-01-01T00:00:00Z"))))
            .isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void reportsBadQualityAndStatusCode() {
        TagValueView result = converter.toTagView(
            tag("Bad", "INT32"),
            new DataValue(Variant.NULL_VALUE, new StatusCode(0x80340000L), DateTime.now())
        );

        assertThat(result.quality()).isEqualTo("BAD");
        assertThat(result.statusCode()).isEqualTo(0x80340000L);
    }

    @Test
    void exportsArrayValuesAsList() {
        TagConfig tag = tag("Array", "DOUBLE");
        tag.setArray(true);
        TagValueView result = converter.toTagView(
            tag,
            DataValue.valueOnly(new Variant(new Double[]{1.5, 2.5}))
        );

        assertThat(result.array()).isTrue();
        assertThat(result.value()).isEqualTo(List.of(1.5, 2.5));
    }

    private TagConfig tag(String name, String dataType) {
        TagConfig tag = new TagConfig();
        tag.setId("tag_test");
        tag.setName(name);
        tag.setNodeId("ns=2;s=" + name);
        tag.setDataType(dataType);
        tag.setAccessMode("READ_WRITE");
        tag.setScale(1.0);
        tag.setOffset(0.0);
        return tag;
    }
}
