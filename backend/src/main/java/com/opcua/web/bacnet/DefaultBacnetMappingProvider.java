package com.opcua.web.bacnet;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.BacnetObjectType;
import com.opcua.web.domain.enums.TagDataType;
import org.springframework.stereotype.Component;

@Component
public class DefaultBacnetMappingProvider implements BacnetMappingProvider {

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public BacnetObjectType map(TagConfig tag, DeviceConfig device) {
        if (tag.getBacnetObjectType() != null && !tag.getBacnetObjectType().isBlank()
            && !"AUTO".equalsIgnoreCase(tag.getBacnetObjectType())) {
            return BacnetObjectType.fromName(tag.getBacnetObjectType());
        }

        boolean writable = parseAccess(tag.getAccessMode()).canWrite();
        if (tag.isArray()) {
            return BacnetObjectType.CHARACTER_STRING_VALUE;
        }

        TagDataType type = TagDataType.fromName(tag.getDataType()).orElse(TagDataType.AUTO);
        if (type == TagDataType.AUTO) {
            return BacnetObjectType.CHARACTER_STRING_VALUE;
        }
        if (tag.getEnumValues() != null && !tag.getEnumValues().isBlank() && type.isInteger()) {
            return writable ? BacnetObjectType.MULTI_STATE_VALUE : BacnetObjectType.MULTI_STATE_INPUT;
        }
        if (type == TagDataType.BOOLEAN) {
            return writable ? BacnetObjectType.BINARY_VALUE : BacnetObjectType.BINARY_INPUT;
        }
        if (type.isNumeric()) {
            return writable ? BacnetObjectType.ANALOG_VALUE : BacnetObjectType.ANALOG_INPUT;
        }
        return BacnetObjectType.CHARACTER_STRING_VALUE;
    }

    private static AccessMode parseAccess(String value) {
        try {
            return AccessMode.valueOf(value);
        } catch (Exception e) {
            return AccessMode.READ_ONLY;
        }
    }
}
