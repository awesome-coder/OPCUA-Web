package com.opcua.web.bacnet;

import static org.assertj.core.api.Assertions.assertThat;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.BacnetObjectType;
import org.junit.jupiter.api.Test;

class DefaultBacnetMappingProviderTest {

    private final DefaultBacnetMappingProvider provider = new DefaultBacnetMappingProvider();
    private final DeviceConfig device = new DeviceConfig();

    @Test
    void mapsNumericAndBooleanByWritability() {
        assertThat(provider.map(tag("FLOAT", "READ_ONLY"), device)).isEqualTo(BacnetObjectType.ANALOG_INPUT);
        assertThat(provider.map(tag("DOUBLE", "READ_WRITE"), device)).isEqualTo(BacnetObjectType.ANALOG_VALUE);
        assertThat(provider.map(tag("BOOLEAN", "READ_ONLY"), device)).isEqualTo(BacnetObjectType.BINARY_INPUT);
        assertThat(provider.map(tag("BOOLEAN", "READ_WRITE"), device)).isEqualTo(BacnetObjectType.BINARY_VALUE);
    }

    @Test
    void mapsEnumeratedIntegerToMultiState() {
        TagConfig tag = tag("INT32", "READ_WRITE");
        tag.setEnumValues("0=Stop;1=Run");
        assertThat(provider.map(tag, device)).isEqualTo(BacnetObjectType.MULTI_STATE_VALUE);
    }

    @Test
    void mapsStringsAndArraysToCharacterString() {
        assertThat(provider.map(tag("STRING", "READ_ONLY"), device))
            .isEqualTo(BacnetObjectType.CHARACTER_STRING_VALUE);

        TagConfig array = tag("FLOAT", "READ_WRITE");
        array.setArray(true);
        assertThat(provider.map(array, device)).isEqualTo(BacnetObjectType.CHARACTER_STRING_VALUE);
    }

    @Test
    void honorsExplicitObjectTypeOverride() {
        TagConfig tag = tag("FLOAT", "READ_WRITE");
        tag.setBacnetObjectType("ANALOG_OUTPUT");
        assertThat(provider.map(tag, device)).isEqualTo(BacnetObjectType.ANALOG_OUTPUT);
    }

    @Test
    void unknownAutoTypeFallsBackToString() {
        assertThat(provider.map(tag("AUTO", "READ_ONLY"), device))
            .isEqualTo(BacnetObjectType.CHARACTER_STRING_VALUE);
    }

    private TagConfig tag(String dataType, String access) {
        TagConfig tag = new TagConfig();
        tag.setName("Test");
        tag.setDataType(dataType);
        tag.setAccessMode(access);
        return tag;
    }
}
