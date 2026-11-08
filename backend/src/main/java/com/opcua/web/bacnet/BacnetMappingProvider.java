package com.opcua.web.bacnet;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.BacnetObjectType;

/**
 * Extension point for changing how an OPC UA tag becomes a BACnet object.
 * A custom implementation can be registered as a Spring bean and ordered before the
 * default provider.
 */
public interface BacnetMappingProvider {

    default int order() {
        return 100;
    }

    BacnetObjectType map(TagConfig tag, DeviceConfig device);
}
